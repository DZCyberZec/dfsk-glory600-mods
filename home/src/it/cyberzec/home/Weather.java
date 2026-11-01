package it.cyberzec.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Open-Meteo (no API key). The last answer is cached so the card still shows it offline. */
final class Weather {
    static final String TAG = "CyberZecMeteo";
    static final long REFRESH_MS = 30 * 60 * 1000L;

    static final class Data {
        int temp, max, min, rainPct, code;
        String text, city;
        String[] hours = new String[5];
        int[] hourTemps = new int[5];
        long fetchedAt;
    }

    private final SharedPreferences prefs;
    private volatile boolean busy;

    Weather(Context c) {
        prefs = c.getSharedPreferences("meteo", Context.MODE_PRIVATE);
    }

    long fetchedAt() {
        return prefs.getLong("at", 0);
    }

    boolean stale() {
        return System.currentTimeMillis() - fetchedAt() > REFRESH_MS;
    }

    Data cached() {
        String json = prefs.getString("json", null);
        if (json == null) return null;
        try {
            Data d = parse(json, fetchedAt());
            d.city = prefs.getString("city", null);
            return d;
        } catch (Exception e) {
            Log.w(TAG, "cache meteo illeggibile", e);
            return null;
        }
    }

    /** Fetches in the background, then runs done (on the background thread) if new data was stored. */
    void refresh(final double lat, final double lon, final Runnable done) {
        if (busy) return;
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code&hourly=temperature_2m"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                            + "&timezone=auto&forecast_days=2", lat, lon);
                    String json = get(url);
                    parse(json, 0);   // store only answers we can read
                    prefs.edit().putString("json", json).putLong("at", System.currentTimeMillis()).apply();
                    try {
                        String c = city(lat, lon);
                        if (c != null) prefs.edit().putString("city", c).apply();
                    } catch (Exception e) {
                        Log.i(TAG, "citta non trovata: " + e);
                    }
                    done.run();
                } catch (Exception e) {
                    Log.i(TAG, "meteo non aggiornato: " + e);
                } finally {
                    busy = false;
                }
            }
        }, "meteo").start();
    }

    /** Town name from OpenStreetMap Nominatim (low rate: once per weather refresh). */
    private static String city(double lat, double lon) throws Exception {
        JSONObject a = new JSONObject(get(String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.4f&lon=%.4f&zoom=10&accept-language=%s",
                lat, lon, Locale.getDefault().getLanguage()))).optJSONObject("address");
        if (a == null) return null;
        for (String k : new String[]{"city", "town", "village", "municipality", "county"})
            if (a.has(k)) return a.getString(k);
        return null;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "HomeCyberZec/0.2 (launcher auto DFSK Glory 600)");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static Data parse(String json, long at) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        JSONObject hourly = o.getJSONObject("hourly");
        Data d = new Data();
        d.fetchedAt = at;
        d.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        d.code = cur.getInt("weather_code");
        d.text = Logic.meteoText(d.code);
        d.max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        d.min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        d.rainPct = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
        JSONArray times = hourly.getJSONArray("time"), temps = hourly.getJSONArray("temperature_2m");
        String now = cur.getString("time");   // local time, same "yyyy-MM-ddTHH:mm" format as hourly
        int k = 0;
        for (int i = 0; i < times.length() && k < 5; i++) {
            String t = times.getString(i);
            if (t.compareTo(now) <= 0) continue;
            d.hours[k] = t.substring(11, 13);
            d.hourTemps[k++] = (int) Math.round(temps.getDouble(i));
        }
        return d;
    }
}
