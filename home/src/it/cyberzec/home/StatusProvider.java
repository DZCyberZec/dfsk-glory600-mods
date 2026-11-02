package it.cyberzec.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Read-only status for CyberZec Set (authority it.cyberzec.home.status, columns key/value). */
public final class StatusProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        Context c = getContext();
        SharedPreferences home = c.getSharedPreferences("home", Context.MODE_PRIVATE),
                meteo = c.getSharedPreferences("meteo", Context.MODE_PRIVATE);
        Weather.Data d = new Weather(c).cached();
        String ver = "?";
        try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        String city = d != null && d.city != null ? d.city : meteo.getString("fix_city", "");
        MatrixCursor m = new MatrixCursor(new String[]{"key", "value"});
        m.addRow(new Object[]{"version", ver});
        m.addRow(new Object[]{"city", city});
        m.addRow(new Object[]{"cityMode", meteo.contains("fix_city") ? "fixed" : "gps"});
        m.addRow(new Object[]{"temp", d == null ? "" : d.temp + "°"});
        m.addRow(new Object[]{"text", d == null ? "" : d.text});
        m.addRow(new Object[]{"name", home.getString("name", "")});
        m.addRow(new Object[]{"sky", home.getBoolean("sky", true) ? "on" : "off"});
        m.addRow(new Object[]{"hours", home.getBoolean("hours", true) ? "on" : "off"});
        m.addRow(new Object[]{"recent", meteo.getString("recent", "")});
        String pLat = meteo.getString("park_lat", null), pLon = meteo.getString("park_lon", null);
        m.addRow(new Object[]{"park", pLat == null ? "" : String.format(java.util.Locale.ROOT, "%.5f,%.5f,%d",   // lat,lon,epochMs
                Double.parseDouble(pLat), Double.parseDouble(pLon), meteo.getLong("park_at", 0))});
        return m;
    }

    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
