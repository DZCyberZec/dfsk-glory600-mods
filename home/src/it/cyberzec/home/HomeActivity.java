package it.cyberzec.home;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends Activity {
    static final String TAG = "CyberZec";
    static final int BG = 0xFF080E10, LIME = 0xFFC5F56C, TEAL = 0xFF2FD6A6, TXT = 0xFFF1F4EE,
            MUT = 0xFFA3B1AF, LINE = 0x24CDE9DB, DARK = 0xFF080E10,
            COLD = 0xFF4DA3FF, HOT = 0xFFFF5A52;
    static final int APPS_PER_PAGE = 18, COLS = 6;
    static final long GPS_FRESH_MS = 120_000;

    /** The 6 apps of the bottom bar: package, activity (from BWLauncher3 AppUtil), label, Aurora icon. */
    static final Object[][] DOCK = {
            {"com.bw.adas", "com.bw.adas.avm.AvmActivity", Logic.t("Vista 360°", "360° view"), R.drawable.ic_avm},
            {"com.bw.phone", "com.bw.phone.MainActivity", Logic.t("Telefono", "Phone"), R.drawable.ic_phone},
            {"com.bw.mediaplayer", "com.bw.mediaplayer.activity.MainActivity", "Media", R.drawable.ic_media},
            {"com.bw.driverassistance", "com.bw.driverassistance.DvrActivity", "Dashcam", R.drawable.ic_dvr},
            {"com.bw.settings", "com.bw.settings.CarSetting", Logic.t("Veicolo", "Vehicle"), R.drawable.ic_car_setting},
            {"com.bw.settings", "com.bw.settings.MainActivity", Logic.t("Impostazioni", "Settings"), R.drawable.ic_setting},
    };
    /** Hidden by default (owner's list, 01/11); the owner can show them again from the grid. */
    static final String[] HIDDEN = {"com.longsys.smart", "com.bw.dualinteraction", "com.navitel",
            "com.navngo.copy_tool", "com.nng.igo.primong", "com.astrob.turbodog",
            "com.bw.upgrade", "com.android.documentsui"};   // Aggiornamento software, File
    /** Aurora icons for the app grid, by package. */
    static final Object[][] AURORA = {
            {"com.bw.ac", R.drawable.ic_ac}, {"com.bw.gallery", R.drawable.ic_gallery},
            {"com.bw.video", R.drawable.ic_video}, {"com.eryanet.vlink", R.drawable.ic_erya},
            {"com.bw.mediaplayer", R.drawable.ic_media}, {"com.bw.phone", R.drawable.ic_phone},
            {"com.bw.adas", R.drawable.ic_avm}, {"com.bw.driverassistance", R.drawable.ic_dvr},
            {"com.bw.settings", R.drawable.ic_setting}, {"it.dfsk.helper", R.drawable.ic_dfsk_helper},
            {"com.suding.speedplay", R.drawable.ic_speedplay},
    };

    private float k;
    private final Handler h = new Handler();
    private final Hvac hvac = new Hvac();
    private Weather weather;
    private SharedPreferences prefs, home;
    private LinearLayout dock;
    private LocationManager lm;
    private long lastGpsAt, lastWeatherTry;
    private String lastClimaLog = "";
    private Pager pager;
    static HomeActivity live;   // the open Home, for SettingsReceiver (main thread only)
    private boolean resumed;
    private int shownPhase = -1;   // sky phase the night dimming was last applied for

    private TextView chipNet, chipGps;
    private ImageView chipNetIcon;
    private SkyView sky;
    private double[] sun;   // today's sunrise/sunset in minutes, null without a position (updateStatus)
    private LinearLayout hoursRow;
    private View climaDot;
    private ImageView thumb;
    private String coverFor;   // title whose cover is shown or being looked up
    private boolean coverShown;
    private TextView greet, greetName, alert, wCity, wTemp, wText, climaSub;
    private ImageView wIcon, wPin, mPlay;
    private TextView mChannel, mSub, mTitle, mPos, mDur;
    private LinearLayout mBar, mControls, mProgress, cControls;
    private ImageView cAnswer;
    private boolean mediaDenied, demoPlaying = true;
    private long demoPos = 418_000;

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = getResources().getDisplayMetrics().density / 1.5f;   // mockup pixels are 240 dpi pixels
        weather = new Weather(this);
        prefs = getSharedPreferences("meteo", MODE_PRIVATE);
        home = getSharedPreferences("home", MODE_PRIVATE);   // owner's dock and hidden apps
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        root = new FrameLayout(this);
        root.setBackground(background());
        pager = new Pager(this);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        pager.row.addView(cockpit(), new LinearLayout.LayoutParams(-1, -1));
        buildAppPages();
        setContentView(root);

        hvac.connect(this, new Runnable() {
            @Override public void run() {
                runOnUiThread(updateClima);
            }
        });
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        IntentFilter cpf = new IntentFilter(CP_PHONE);
        cpf.addAction(SPEEDPLAY);
        registerReceiver(carplayPhone, cpf);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        showWeather(weather.cached());
        live = this;
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        startLocation();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        h.removeCallbacks(tick);
        sky.setRunning(false);
        try {
            lm.removeUpdates(location);
        } catch (Exception ignored) {
        }
    }

    @Override protected void onDestroy() {
        if (live == this) live = null;
        unregisterReceiver(packages);
        unregisterReceiver(carplayPhone);
        hvac.disconnect();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeSheet();
        pager.goTo(0);   // Home key: back to the cockpit
    }

    @Override public void onBackPressed() {
        closeSheet();
        pager.goTo(0);   // a launcher never closes itself
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        startLocation();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            updateClima.run();
            updateMedia();
            h.postDelayed(this, 2000);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refreshApps();
        }
    };

    // ---------------------------------------------------------------- status line, location, weather

    private void updateStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean online = ni != null && ni.isConnected();
        chipNet.setText(Logic.internetShort(online, online && ni.getType() == ConnectivityManager.TYPE_WIFI,
                cm.isActiveNetworkMetered()));
        chipNet.setTextColor(online ? TXT : MUT);
        chipNetIcon.setImageResource(online ? R.drawable.st_wifi : R.drawable.st_offline);
        chipNetIcon.setColorFilter(online ? LIME : MUT, PorterDuff.Mode.SRC_IN);

        boolean gps = lastGpsAt > 0 && SystemClock.elapsedRealtime() - lastGpsAt < GPS_FRESH_MS;
        show(chipGps, gps);

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        Weather.Data d = weather.cached();
        String place = prefs.getString("fix_city", null);   // city chosen by the owner, else the GPS position
        String lat = prefs.getString(place != null ? "fix_lat" : "lat", null), lon = prefs.getString(place != null ? "fix_lon" : "lon", "0");
        sun = lat == null ? null : Logic.sunTimes(cal.get(Calendar.DAY_OF_YEAR), Double.parseDouble(lat),
                Double.parseDouble(lon), cal.getTimeZone().getOffset(cal.getTimeInMillis()) / 3_600_000.0);
        int phase = Logic.sunPhase(hour * 60 + cal.get(Calendar.MINUTE), sun);
        sky.set(phase, d == null ? 1 : Logic.skyKind(Logic.meteoIcon(d.code)));
        if (phase != shownPhase) {   // night: everything about 18% darker (lime C5F56C -> A2C959), same layout
            shownPhase = phase;
            root.setForeground(phase == 3 ? new ColorDrawable(0x2E000000) : null);
        }
        sky.setRunning(resumed && home.getBoolean("sky", true));   // also while driving (owner's choice, 02/11); off = still sky
        String name = home.getString("name", "");
        greet.setText(Logic.greeting(hour));
        greetName.setText(name);
        greetName.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        if (d == null) showWeather(null);   // "waiting for GPS" -> "waiting for Internet" once a fix arrives
        String rain = d == null ? null : Logic.rainText(d.rainAt, System.currentTimeMillis());
        if (rain == null && EMULATOR) rain = Logic.t("Pioggia tra 20 min", "Rain in 20 min");   // emulator: show the alert line
        show(alert, rain != null);
        if (rain != null) alert.setText(rain);

        boolean due = weather.stale();   // not "|| no city": a failing reverse lookup refetched every minute
        if (online && due && lat != null && SystemClock.elapsedRealtime() - lastWeatherTry > 60_000) {
            lastWeatherTry = SystemClock.elapsedRealtime();   // at most one try a minute when a request fails
            weather.refresh(Double.parseDouble(lat), Double.parseDouble(lon), place,
                    new Runnable() {
                        @Override public void run() {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    showWeather(weather.cached());
                                    updateStatus();
                                }
                            });
                        }
                    });
        }
    }

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
                if (!lm.getAllProviders().contains(p)) continue;
                Location l = lm.getLastKnownLocation(p);
                if (l != null) location.onLocationChanged(l);
                lm.requestLocationUpdates(p, 30_000, 0, location);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "posizione negata", e);
        }
    }

    private final LocationListener location = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            prefs.edit().putString("lat", String.valueOf(l.getLatitude()))
                    .putString("lon", String.valueOf(l.getLongitude())).apply();
            if (LocationManager.GPS_PROVIDER.equals(l.getProvider())
                    && System.currentTimeMillis() - l.getTime() < GPS_FRESH_MS) {
                lastGpsAt = SystemClock.elapsedRealtime();
                prefs.edit().putString("park_lat", String.valueOf(l.getLatitude()))   // last fix before switch-off = parked car
                        .putString("park_lon", String.valueOf(l.getLongitude())).putLong("park_at", l.getTime()).apply();
            }
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };


    private void showWeather(Weather.Data d) {
        boolean fixed = prefs.contains("fix_city");
        wPin.setColorFilter(fixed ? MUT : LIME, PorterDuff.Mode.SRC_IN);   // lime pin = city from GPS
        if (d == null) {
            wCity.setText(prefs.contains("lat") ? Logic.t("Senza Internet", "No Internet") : Logic.t("In attesa del GPS", "Waiting for GPS"));
            wTemp.setText("--°");
            wText.setText("");
            wIcon.setImageResource(R.drawable.w_nubi);
            wIcon.setColorFilter(Logic.meteoColor("nubi"), PorterDuff.Mode.SRC_IN);
            hoursRow.setVisibility(View.INVISIBLE);
            return;
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minute = hour * 60 + Calendar.getInstance().get(Calendar.MINUTE);
        String icon = Logic.meteoIconAt(d.code, minute, sun);
        hoursRow.setVisibility(home.getBoolean("hours", true) ? View.VISIBLE : View.INVISIBLE);
        for (int i = 0; i < 6; i++) {
            LinearLayout cell = (LinearLayout) hoursRow.getChildAt(i);
            String hh = i == 0 ? null : d.hours[i - 1];
            int code = i == 0 ? d.code : d.hourCodes[i - 1];
            String ic = code < 0 ? "nubi" : Logic.meteoIconAt(code, hh == null ? minute : Integer.parseInt(hh) * 60 + 30, sun);
            boolean wet = Logic.skyKind(ic) == 2;
            ((TextView) cell.getChildAt(0)).setText(i == 0 ? Logic.t("Ora", "Now") : hh == null ? "" : hh);
            ((TextView) cell.getChildAt(0)).setTextColor(i == 0 ? LIME : wet ? 0xFFBFE0FF : 0xFFD5DCEA);
            ImageView iv = (ImageView) cell.getChildAt(1);
            iv.setVisibility(code < 0 ? View.INVISIBLE : View.VISIBLE);
            iv.setImageResource(getResources().getIdentifier("w_" + ic, "drawable", getPackageName()));
            iv.setColorFilter(Logic.meteoColor(ic), PorterDuff.Mode.SRC_IN);
            ((TextView) cell.getChildAt(2)).setText((i == 0 ? d.temp : d.hourTemps[i - 1]) + "°");
            cell.setBackground(i == 0 ? round(0x24C5F56C, 18, 0) : wet ? round(0x384FA3F0, 18, 0) : null);
        }
        wCity.setText(d.city != null ? d.city : Logic.t("Posizione GPS", "GPS location"));
        wTemp.setText(d.temp + "°");
        wText.setText(d.text);
        wIcon.setImageResource(getResources().getIdentifier("w_" + icon, "drawable", getPackageName()));
        wIcon.setColorFilter(Logic.meteoColor(icon), PorterDuff.Mode.SRC_IN);
    }

    /** Tap on the alert line: the next hours. */
    private void weatherHours() {
        Weather.Data d = weather.cached();
        if (d == null) { meteoMenu(); return; }
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (d.hours[i] != null) rows.add(d.hours[i] + ":00   " + d.hourTemps[i] + "°");
        rows.add(Logic.t("Pioggia oggi: ", "Rain today: ") + d.rainPct + "%");
        choose((d.city != null ? d.city : "Meteo") + " · " + d.text, rows.toArray(new String[0]), new Choice() {
            @Override public void on(int which) {}
        });
    }

    private void askName() {
        final EditText e = new EditText(this);
        style(e, 34, TXT, false);
        e.setSingleLine();
        e.setText(home.getString("name", ""));
        e.setPadding(px(24), px(22), px(24), px(22));
        FrameLayout box = new FrameLayout(this);
        box.setPadding(px(30), px(10), px(30), 0);
        box.addView(e);
        TextView head = text(Logic.t("Il tuo nome nel saluto", "Your name in the greeting"), 34, LIME, true);
        head.setPadding(px(30), px(26), px(30), px(10));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setCustomTitle(head).setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) {
                        home.edit().putString("name", e.getText().toString().trim()).apply();
                        updateStatus();
                    }
                })
                .setNegativeButton(Logic.t("Annulla", "Cancel"), null)
                .show();
    }

    // ---------------------------------------------------------------- climate button (full climate screen: roadmap)

    private final Runnable updateClima = new Runnable() {
        @Override public void run() {
            boolean ready = hvac.ready();
            boolean on = ready && hvac.getBool(Hvac.POWER_ON, Hvac.AREA_ROW);
            float t = ready ? hvac.getFloat(Hvac.TEMP, Hvac.AREA_TEMP) : Float.NaN;
            // this car is level-only (secure offline_hvac_type=0): never degrees
            String level = Float.isNaN(t) ? "" : " · " + Logic.t("liv. ", "lvl ") + Logic.climaLevelText(t);
            climaSub.setText(!ready ? Logic.t("non disponibile", "not available") : on ? Logic.t("acceso", "on") + level : Logic.t("spento", "off"));
            climaSub.setTextColor(on ? LIME : MUT);
            climaDot.setBackground(round(on ? LIME : 0x33FFFFFF, 999, 0));
            String snap = "acceso=" + on + " livello=" + level;
            if (!snap.equals(lastClimaLog)) Log.e(Hvac.TAG, "stato " + (lastClimaLog = snap));   // Log.e: log.tag=M on the car hides lower levels
        }
    };

    // ---------------------------------------------------------------- CarPlay media (session opened by DFSK Helper, see ANALISI_CARPLAY_BRANO.md)

    static final String SPEEDPLAY = "com.suding.speedplay";
    static final String CP_PHONE = "com.suding.speedplay.intent.action.CarPlayPhone";   // SpeedPlay, every CarPlay call change

    private String callWho;
    private int callStatus;
    private long callSince;   // elapsedRealtime when the call became active, 0 = not yet

    private final BroadcastReceiver carplayPhone = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (SPEEDPLAY.equals(i.getAction())) {   // plain status, always sent (CarPlayPhone is skipped by a SpeedPlay bug
                String st = i.getStringExtra("status");   // once a call ended without status 0)
                if ("PHONE_CALL_ON".equals(st) && callStatus == 0) { callStatus = 4; callSince = SystemClock.elapsedRealtime(); }
                else if ("PHONE_CALL_OFF".equals(st) || "DISCONNECT".equals(st)) { callStatus = 0; callSince = 0; callWho = null; }
                else return;
                updateMedia();
                return;
            }
            int st = i.getIntExtra("CarPlayPhoneStatus", 0);
            String name = i.getStringExtra("CarPlayPhoneName"), num = i.getStringExtra("CarPlayPhoneNumber");
            callWho = name != null && !name.isEmpty() ? name : num;   // never logged: it is a contact
            if (st == 4 && callStatus != 4) callSince = SystemClock.elapsedRealtime();
            if (st != 4 && st != 5) callSince = 0;
            if (st == 0) callWho = null;
            callStatus = st;
            updateMedia();
        }
    };
    static final boolean EMULATOR = "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);

    /** What the media card shows: CarPlay while it is connected, otherwise whatever app is playing (NewPipe...),
     *  else the last paused one with a title. Null if nothing (or no MEDIA_CONTENT_CONTROL outside the car). */
    private MediaController media() {
        try {
            boolean carplay = Settings.Secure.getInt(getContentResolver(), "bw.carplay.connect.state", 0) != 0;
            MediaSessionManager m = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            MediaController paused = null;
            for (MediaController c : m.getActiveSessions(null)) {
                boolean sp = SPEEDPLAY.equals(c.getPackageName());
                if (sp && carplay) return c;
                if (sp || c.getMetadata() == null) continue;
                String t = c.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
                if (t == null || t.isEmpty()) continue;
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) return c;
                if (paused == null) paused = c;
            }
            return carplay ? null : paused;
        } catch (SecurityException e) {
            if (!mediaDenied) Log.w(TAG, "sessioni media non leggibili: " + e);
            mediaDenied = true;
        }
        return null;
    }

    private MediaController spCtl;
    private final long[] spPlays = {-9999, -9999, -9999};   // last 3 "playing" updates from SpeedPlay (elapsedRealtime)
    private int spIdx;
    private final MediaController.Callback spCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState st) {
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) spPlays[spIdx++ % 3] = SystemClock.elapsedRealtime();
        }
    };

    /** Follows SpeedPlay's session (it is recreated when its service restarts). */
    private void watchCarPlay(MediaController c) {
        if (c == null || spCtl != null && spCtl.getSessionToken().equals(c.getSessionToken())) return;
        if (spCtl != null) spCtl.unregisterCallback(spCb);
        spCtl = c;
        c.registerCallback(spCb, h);
    }

    private void updateMedia() {
        boolean carplay = Settings.Secure.getInt(getContentResolver(), "bw.carplay.connect.state", 0) != 0;
        String call = carplay ? Logic.callText(callStatus) : null;
        if (call != null) {   // a CarPlay call takes the card; the call itself is handled in CarPlay
            coverFor = null;
            coverShown = false;
            mTitle.setText(callWho != null && !callWho.isEmpty() ? callWho : Logic.t("Chiamata", "Call"));
            mChannel.setText(call + (callSince > 0 ? " · " + Logic.clock(SystemClock.elapsedRealtime() - callSince) : ""));
            mSub.setText("CarPlay · " + Logic.t("Telefono", "Phone"));
            thumb.setPadding(px(80), px(28), px(80), px(28));
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumb.setImageResource(R.drawable.st_phone);
            thumb.setBackground(round(0xFF182329, 22, 0xFF2E3A3D, 2));
            mControls.setVisibility(View.GONE);
            mProgress.setVisibility(View.INVISIBLE);
            cAnswer.setVisibility(callStatus == 2 ? View.VISIBLE : View.GONE);
            cControls.setVisibility(View.VISIBLE);
            return;
        }
        MediaController c = media();
        boolean cp = c == null || SPEEDPLAY.equals(c.getPackageName());
        String source = cp ? "CarPlay" : appLabel(c.getPackageName());
        MediaMetadata md = c == null ? null : c.getMetadata();
        PlaybackState ps = c == null ? null : c.getPlaybackState();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        boolean demo = EMULATOR && (title == null || title.isEmpty());   // emulator: show a sample so the layout can be checked
        if (demo) {
            title = "Come ho trasformato la mia DFSK Glory 600 – guida completa CarPlay e clima";
            if (demoPlaying) demoPos += 2000;
        }
        boolean has = title != null && !title.isEmpty(), timed = false;
        // CarPlay audio without a title (WhatsApp voice notes...): the iPhone only sends the elapsed time
        // (about 2 updates a second). SpeedPlay also says "playing" once, with no data, each time its service (re)starts,
        // and the framework rewrites position/update time, so count the real updates instead.
        watchCarPlay(cp ? c : null);
        long now = SystemClock.elapsedRealtime();
        boolean untitled = !has && cp && c != null && ps != null && ps.getState() == PlaybackState.STATE_PLAYING
                && now - spPlays[0] < 3000 && now - spPlays[1] < 3000 && now - spPlays[2] < 3000;
        if (has) {
            boolean playing = demo ? demoPlaying : ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            long dur = demo ? 1_104_000 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long pos = demo ? demoPos % dur : ps == null ? 0 : ps.getPosition()
                    + (playing && ps.getLastPositionUpdateTime() > 0 ? (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed()) : 0);
            String artist = demo ? "CyberZec" : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            mTitle.setText(title);
            mChannel.setText(artist != null && !artist.isEmpty() ? artist : source);
            mSub.setText(source + " · " + (playing ? Logic.t("in riproduzione", "playing") : Logic.t("in pausa", "paused")));
            mPlay.setImageResource(playing ? R.drawable.m_pause : R.drawable.m_play);
            timed = dur > 0;
            if (timed) {
                mPos.setText(Logic.clock(Math.min(pos, dur)));
                mDur.setText(Logic.clock(dur));
                float f = Math.max(0f, Math.min(1f, pos / (float) dur));
                ((LinearLayout.LayoutParams) mBar.getChildAt(0).getLayoutParams()).weight = f;
                ((LinearLayout.LayoutParams) mBar.getChildAt(1).getLayoutParams()).weight = 1 - f;
                mBar.requestLayout();
            }
            String album = demo ? null : md.getString(MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap art = demo ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ART);   // apps like NewPipe send the thumbnail
            if (art == null && !demo) art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && !demo) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (art != null) {
                coverFor = title;
                coverShown = true;
                thumb.setPadding(0, 0, 0, 0);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setImageBitmap(art);
            } else if (!title.equals(coverFor)) {
                coverFor = title;
                coverShown = false;
                Cover.find(title, artist, album,
                        getSharedPreferences("cover", MODE_PRIVATE).getString("yt_key", null), new Cover.Done() {
                    @Override public void cover(final String t, final Bitmap b) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (b == null || !t.equals(coverFor)) return;
                                coverShown = true;
                                thumb.setPadding(0, 0, 0, 0);
                                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                thumb.setImageBitmap(b);
                            }
                        });
                    }
                });
            }
            if (!coverShown) {   // until (or if no) cover arrives: music note when there is an album, else a video
                thumb.setPadding(px(80), px(28), px(80), px(28));
                thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (!cp) thumb.setImageDrawable(appIcon(c.getPackageName()));   // e.g. the NewPipe icon
                else thumb.setImageResource(album != null && !album.isEmpty() ? R.drawable.src_music : R.drawable.src_video);
            }
            thumb.setBackground(round(0xFF182329, 22, 0xFF2E3A3D, 2));
        } else if (untitled) {
            coverFor = null;
            coverShown = false;
            mTitle.setText(Logic.t("Audio in riproduzione", "Audio playing"));
            mChannel.setText(Logic.t("Vocale o audio senza titolo", "Voice message or untitled audio"));
            mSub.setText("CarPlay · " + Logic.t("in riproduzione", "playing"));
            mPlay.setImageResource(R.drawable.m_pause);
            thumb.setPadding(px(80), px(28), px(80), px(28));
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumb.setImageResource(R.drawable.src_music);
            thumb.setBackground(round(0xFF182329, 22, 0xFF2E3A3D, 2));
        } else {
            coverFor = null;
            coverShown = false;
            thumb.setImageDrawable(null);
            mTitle.setText(carplay ? Logic.t("Niente in riproduzione", "Nothing playing") : Logic.t("CarPlay non collegato", "CarPlay not connected"));
            mChannel.setText(carplay ? Logic.t("Avvia un video o la musica su CarPlay", "Start a video or music in CarPlay")
                    : Logic.t("Collega l'iPhone a CarPlay", "Connect the iPhone to CarPlay"));
            mSub.setText("CarPlay");
            thumb.setBackground(round(0x14FFFFFF, 22, LINE));
        }
        mControls.setVisibility(has || untitled ? View.VISIBLE : View.GONE);
        cControls.setVisibility(View.GONE);
        mProgress.setVisibility(has && timed ? View.VISIBLE : View.INVISIBLE);
    }


    /** Tap on the media card: the app that is playing (CarPlay when nothing else). */
    private void openPlaying() {
        MediaController c = media();
        if (c != null && !SPEEDPLAY.equals(c.getPackageName())) {
            try {
                if (c.getSessionActivity() != null) { c.getSessionActivity().send(); return; }
                Intent i = getPackageManager().getLaunchIntentForPackage(c.getPackageName());
                if (i != null) { startActivity(i); return; }
            } catch (Exception e) {
                Log.i(TAG, "apertura app media fallita: " + e);
            }
        }
        launch(SPEEDPLAY, "com.suding.speedplay.ui.MainActivity");
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return getDrawable(R.drawable.src_video);
        }
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private View.OnClickListener mediaKey(final int code) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                MediaController c = media();
                if (c != null) {
                    c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));   // SpeedPlay passes media keys on to CarPlay
                    c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
                } else if (EMULATOR) {
                    if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) demoPlaying = !demoPlaying;
                    else demoPos = code == KeyEvent.KEYCODE_MEDIA_NEXT ? 0 : Math.max(0, demoPos - 10_000);
                } else launch(SPEEDPLAY, "com.suding.speedplay.ui.MainActivity");
                h.postDelayed(new Runnable() {
                    @Override public void run() {
                        updateMedia();
                    }
                }, 400);
            }
        };
    }

    // ---------------------------------------------------------------- page 1: clock and weather, CarPlay media, bottom bar

    private View cockpit() {
        LinearLayout page = vbox();
        page.setPadding(px(16), px(16), px(18), px(16));

        LinearLayout body = hbox();
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(skyCard(), new LinearLayout.LayoutParams(px(720), -1));
        LinearLayout right = vbox();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1);
        rlp.leftMargin = px(22);
        body.addView(right, rlp);

        LinearLayout chips = hbox();
        chips.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        chipNet = chip(chips, R.drawable.st_wifi, LIME, "Wi-Fi");
        chipNetIcon = (ImageView) ((ViewGroup) chipNet.getParent()).getChildAt(0);
        chipGps = chip(chips, R.drawable.st_gps, LIME, "GPS");
        right.addView(chips, new LinearLayout.LayoutParams(-1, px(60)));
        right.addView(mediaCard(), new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, px(118));
        alp.topMargin = px(14);
        page.addView(bar, alp);
        bar.addView(climaButton(), new LinearLayout.LayoutParams(px(270), px(96)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(px(230), px(96));
        clp.leftMargin = px(18);
        bar.addView(carplayButton(), clp);
        dock = hbox();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        fillDock();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -1, 1);
        dlp.leftMargin = px(10);
        bar.addView(dock, dlp);
        return page;
    }

    private View skyCard() {
        FrameLayout c = new FrameLayout(this);
        sky = new SkyView(this, px(30));
        c.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        c.setPadding(0, 0, 0, 0);
        FrameLayout in = new FrameLayout(this);
        in.setPadding(px(30), px(30), px(30), px(20));
        c.addView(in, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = vbox();
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        style(clock, 136, TXT, true);
        clock.setLetterSpacing(-0.03f);
        left.addView(clock, margins(-2, -2, 14, 0, 0, 0));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        style(date, 32, 0xFFC9D2D6, true);
        left.addView(date, margins(-2, -2, 18, 10, 0, 0));
        LinearLayout g = vbox();
        greet = text("", 44, TXT, true);
        greetName = text("", 44, LIME, true);
        g.addView(greet, new LinearLayout.LayoutParams(-1, -2));   // full width: wrap_content kept an old, shorter width
        g.addView(greetName, margins(-1, -2, 0, 6, 0, 0));
        g.setOnLongClickListener(new View.OnLongClickListener() {   // set the name in the greeting
            @Override public boolean onLongClick(View v) {
                askName();
                return true;
            }
        });
        left.addView(g, margins(-1, -2, 18, 16, 0, 0));
        left.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout a = hbox();
        a.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ai = new ImageView(this);
        ai.setImageResource(R.drawable.w_pioggia);
        ai.setColorFilter(Logic.meteoColor("pioggia"), PorterDuff.Mode.SRC_IN);
        a.addView(ai, new LinearLayout.LayoutParams(px(36), px(36)));
        alert = text("", 30, 0xFFBFE0FF, true);
        a.addView(alert, margins(-2, -2, 12, 0, 0, 0));
        a.addView(text("  ›", 32, MUT, true));
        a.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                weatherHours();
            }
        });
        left.addView(a, margins(-2, px(46), 18, 0, 0, 6));
        hoursRow = hbox();
        hoursRow.setPadding(px(6), px(6), px(6), px(6));
        hoursRow.setBackground(round(0x47000000, 24, 0));
        hoursRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                weatherHours();
            }
        });
        for (int i = 0; i < 6; i++) {
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER);
            cell.addView(text("", 22, 0xFFD5DCEA, true), new LinearLayout.LayoutParams(-2, -2));
            ImageView ic = new ImageView(this);
            cell.addView(ic, margins(px(38), px(38), 0, 4, 0, 4));
            cell.addView(text("", 28, TXT, true), new LinearLayout.LayoutParams(-2, -2));
            hoursRow.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        }
        left.addView(hoursRow, new LinearLayout.LayoutParams(-1, px(116)));
        in.addView(left, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout w = vbox();
        w.setGravity(Gravity.END);
        w.setPadding(px(16), 0, 0, px(16));
        wIcon = new ImageView(this);
        w.addView(wIcon, new LinearLayout.LayoutParams(px(124), px(124)));
        wTemp = text("--°", 84, TXT, true);
        w.addView(wTemp, margins(-2, -2, 0, 4, 0, 0));
        wText = text("", 30, TXT, true);
        w.addView(wText, margins(-2, -2, 0, 8, 0, 0));
        LinearLayout city = hbox();
        city.setGravity(Gravity.CENTER_VERTICAL);
        wPin = new ImageView(this);
        wPin.setImageResource(R.drawable.st_gps);
        city.addView(wPin, new LinearLayout.LayoutParams(px(32), px(32)));
        wCity = text("", 30, 0xFFC9D2D6, true);
        wCity.setSingleLine();
        wCity.setEllipsize(TextUtils.TruncateAt.END);
        wCity.setMaxWidth(px(230));
        city.addView(wCity, margins(-2, -2, 8, 0, 0, 0));
        city.addView(text(" ›", 32, MUT, true));
        w.addView(city, margins(-2, -2, 0, 10, 0, 0));
        w.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                meteoMenu();
            }
        });
        in.addView(w, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        return c;
    }

    private View mediaCard() {
        LinearLayout c = card(30);
        c.setPadding(px(34), px(30), px(34), px(30));
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openPlaying();
            }
        });
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);   // YouTube hqdefault is 4:3 with black bars: the 16:9 crop drops them
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(22));
            }
        });
        thumb.setClipToOutline(true);
        head.addView(thumb, new LinearLayout.LayoutParams(px(240), px(135)));
        LinearLayout who = vbox();
        mChannel = text("", 34, TXT, true);
        mChannel.setSingleLine();
        mChannel.setEllipsize(TextUtils.TruncateAt.END);
        mSub = text("", 26, MUT, true);
        who.addView(mChannel);
        who.addView(mSub, margins(-2, -2, 0, 10, 0, 0));
        head.addView(who, weighted(margins(0, -2, 28, 0, 0, 0)));
        c.addView(head);

        mTitle = text("", 44, TXT, true);
        mTitle.setMaxLines(2);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setLineSpacing(px(4), 1f);
        c.addView(mTitle, margins(-1, -2, 0, 26, 0, 0));
        c.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        mProgress = hbox();
        mProgress.setGravity(Gravity.CENTER_VERTICAL);
        mPos = text("", 28, MUT, true);
        mProgress.addView(mPos);
        mBar = hbox();   // not draggable: a touch while driving must not jump in the video
        View fill = new View(this);
        fill.setBackground(round(LIME, 999, 0));
        View rest = new View(this);
        rest.setBackground(round(0x24FFFFFF, 999, 0));
        mBar.addView(fill, new LinearLayout.LayoutParams(0, -1, 0));
        mBar.addView(rest, new LinearLayout.LayoutParams(0, -1, 1));
        mProgress.addView(mBar, weighted(margins(0, px(8), 18, 0, 18, 0)));
        mDur = text("", 28, MUT, true);
        mProgress.addView(mDur);
        row.addView(mProgress, new LinearLayout.LayoutParams(0, -2, 1));
        mControls = hbox();
        mControls.addView(roundKey(R.drawable.m_prev, KeyEvent.KEYCODE_MEDIA_PREVIOUS, false));
        mPlay = roundKey(R.drawable.m_pause, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true);
        mControls.addView(mPlay, margins(px(112), px(112), 22, 0, 0, 0));
        mControls.addView(roundKey(R.drawable.m_next, KeyEvent.KEYCODE_MEDIA_NEXT, false), margins(px(112), px(112), 22, 0, 0, 0));
        ((LinearLayout.LayoutParams) mControls.getChildAt(0).getLayoutParams()).width = px(112);
        ((LinearLayout.LayoutParams) mControls.getChildAt(0).getLayoutParams()).height = px(112);
        row.addView(mControls, margins(-2, -2, 26, 0, 0, 0));
        cControls = hbox();   // CarPlay call: same keys as the steering wheel, sent to SpeedPlay
        cAnswer = callKey(R.drawable.c_answer, 0xFF2FB36A, 5);
        cControls.addView(cAnswer);
        cControls.addView(callKey(R.drawable.c_end, 0xFFE5484D, 6), margins(px(112), px(112), 22, 0, 0, 0));
        cControls.setVisibility(View.GONE);
        row.addView(cControls, margins(-2, -2, 26, 0, 0, 0));
        c.addView(row);
        return c;
    }

    /** SpeedPlay's exported vendor receiver: REQ_SPEC_FUNC_CMD 5 = answer, 6 = end call (steering-wheel keys). */
    private ImageView callKey(int icon, int color, final int code) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
        b.setPadding(px(28), px(28), px(28), px(28));
        b.setBackground(round(color, 999, 0));
        b.setContentDescription(code == 5 ? Logic.t("Rispondi", "Answer") : Logic.t("Chiudi chiamata", "End call"));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendBroadcast(new Intent("com.suding.speedplay.receive").setPackage(SPEEDPLAY)
                        .putExtra("command", "REQ_SPEC_FUNC_CMD").putExtra("specFuncCode", code));
            }
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(px(112), px(112)));
        return b;
    }

    private ImageView roundKey(int icon, int code, boolean main) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(main ? DARK : TXT, PorterDuff.Mode.SRC_IN);
        b.setPadding(px(30), px(30), px(30), px(30));
        b.setBackground(main ? round(LIME, 999, 0) : round(0x0DFFFFFF, 999, 0x40FFFFFF, 2));
        b.setOnClickListener(mediaKey(code));
        b.setLayoutParams(new LinearLayout.LayoutParams(px(112), px(112)));
        return b;
    }

    private View climaButton() {
        LinearLayout c = hbox();
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(28), 0, px(20), 0);
        c.setBackground(round(0x142FD6A6, 999, 0x732FD6A6, 2));
        climaDot = new View(this);
        c.addView(climaDot, new LinearLayout.LayoutParams(px(22), px(22)));
        LinearLayout t = vbox();
        t.addView(text(Logic.t("Clima", "Climate"), 30, TXT, true));
        climaSub = text("", 22, MUT, true);
        climaSub.setSingleLine();
        t.addView(climaSub, margins(-2, -2, 0, 4, 0, 0));
        c.addView(t, weighted(margins(0, -2, 16, 0, 0, 0)));
        c.setOnClickListener(new View.OnClickListener() {   // roadmap: our full-screen climate panel
            @Override public void onClick(View v) {
                launch("com.bw.ac", "com.bw.ac.activity.MainActivity");
            }
        });
        return c;
    }

    private View carplayButton() {
        LinearLayout c = hbox();
        c.setGravity(Gravity.CENTER);
        c.setBackground(round(0x0FC5F56C, 999, LIME, 3));
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.st_phone);
        iv.setColorFilter(LIME, PorterDuff.Mode.SRC_IN);
        c.addView(iv, new LinearLayout.LayoutParams(px(42), px(42)));
        c.addView(text("CarPlay", 32, LIME, true), margins(-2, -2, 10, 0, 0, 0));
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(SPEEDPLAY, "com.suding.speedplay.ui.MainActivity");
            }
        });
        return c;
    }

    // ---------------------------------------------------------------- weather screen: city from GPS, search, last cities

    private FrameLayout root, overlay;
    private LinearLayout sheet;
    private TextView cityShow;   // typed city, shown above our big keyboard (the unit's IME is small and Chinese-first)
    private String typed = "";
    private final Runnable liveSearch = new Runnable() {
        @Override public void run() {
            searchCity(typed.trim());
        }
    };

    /** Tap on the weather: a big in-car screen instead of a small dialog. */
    private void meteoMenu() {
        closeSheet();
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
            }
        });
        sheet = vbox();
        sheet.setClickable(true);
        sheet.setPadding(px(48), px(36), px(48), px(40));
        sheet.setBackground(round(0xFF101A1D, 30, 0x40FFFFFF, 2));
        overlay.addView(sheet, new FrameLayout.LayoutParams(px(1320), -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((FrameLayout.LayoutParams) sheet.getLayoutParams()).topMargin = px(40);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        fillSheet(null, false);
    }

    private void closeSheet() {
        if (overlay == null) return;
        h.removeCallbacks(liveSearch);
        root.removeView(overlay);
        overlay = null;
        cityShow = null;
    }

    private void fillSheet(List<Weather.Place> results, boolean searching) {
        sheet.removeAllViews();
        if (searching) { searchSheet(); return; }
        sheet.setPadding(px(48), px(36), px(48), px(40));
        boolean fixed = prefs.contains("fix_city");
        Weather.Data d = weather.cached();

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tt = vbox();
        tt.addView(text(Logic.t("Meteo", "Weather"), 46, LIME, true));
        String now = d != null && d.city != null ? d.city : Logic.t("posizione non ancora nota", "location not known yet");
        tt.addView(text(now + " · " + (fixed ? Logic.t("scelta a mano", "chosen by hand") : Logic.t("dal GPS", "from GPS")), 28, MUT, true), margins(-2, -2, 0, 8, 0, 0));
        head.addView(tt, weighted(margins(0, -2, 0, 0, 0, 0)));
        head.addView(pill("‹  Home", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
            }
        }), new LinearLayout.LayoutParams(px(220), px(96)));
        sheet.addView(head);

        LinearLayout acts = hbox();
        acts.addView(pill(Logic.t("Città dal GPS", "City from GPS"), !fixed, new View.OnClickListener() {
            @Override public void onClick(View v) {
                cityFromGps(prefs);
                refreshWeatherNow();
                closeSheet();
            }
        }), new LinearLayout.LayoutParams(0, px(104), 1));
        acts.addView(pill(Logic.t("Cerca una città", "Search a city"), searching, new View.OnClickListener() {
            @Override public void onClick(View v) {
                typed = "";   // no speed lock: the owner's choice (02/11)
                fillSheet(null, true);
            }
        }), weighted(margins(0, px(104), 18, 0, 0, 0)));
        acts.addView(pill(Logic.t("Aggiorna", "Refresh"), false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                refreshWeatherNow();
                closeSheet();
            }
        }), weighted(margins(0, px(104), 18, 0, 0, 0)));
        String pLat = prefs.getString("park_lat", null), pLon = prefs.getString("park_lon", null);
        final Intent park = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + pLat + "," + pLon + "?q=" + pLat + "," + pLon + "(Auto)"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pLat != null && park.resolveActivity(getPackageManager()) != null)   // hidden without a map app or a position
            acts.addView(pill(Logic.t("Dove ho parcheggiato", "Where I parked"), false, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSheet();
                    startActivity(park);
                }
            }), weighted(margins(0, px(104), 18, 0, 0, 0)));
        sheet.addView(acts, margins(-1, -2, 0, 30, 0, 0));

        List<Weather.Place> list = results != null ? results : recentCities(prefs);
        if (!list.isEmpty()) {
            sheet.addView(text(results != null ? Logic.t("Risultati", "Results") : Logic.t("Ultime città", "Recent cities"), 28, MUT, true), margins(-2, -2, 0, 30, 0, 0));
            LinearLayout grid = hbox();
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                final Weather.Place p = list.get(i);
                View b = pill(results != null ? p.label : p.name, false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        setCity(p);
                        closeSheet();
                    }
                });
                grid.addView(b, i == 0 ? new LinearLayout.LayoutParams(0, px(104), 1) : weighted(margins(0, px(104), 18, 0, 0, 0)));
            }
            sheet.addView(grid, margins(-1, -2, 0, 14, 0, 0));
        }
    }

    /** Search screen: what you typed, the cities found so far, and a big keyboard (search starts after 3 letters). */
    private void searchSheet() {
        sheet.setPadding(px(36), px(22), px(36), px(24));
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        cityShow = text("", 40, TXT, true);
        cityShow.setSingleLine();
        cityShow.setEllipsize(TextUtils.TruncateAt.START);
        cityShow.setGravity(Gravity.CENTER_VERTICAL);
        cityShow.setPadding(px(26), 0, px(26), 0);
        cityShow.setBackground(round(0x14FFFFFF, 20, 0x40FFFFFF, 2));
        head.addView(cityShow, new LinearLayout.LayoutParams(0, px(84), 1));
        head.addView(pill(Logic.t("‹  Indietro", "‹  Back"), false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                h.removeCallbacks(liveSearch);
                fillSheet(null, false);
            }
        }), margins(px(240), px(84), 18, 0, 0, 0));
        sheet.addView(head);
        found = hbox();
        sheet.addView(found, margins(-1, px(84), 0, 14, 0, 0));
        showTyped();
        String[] rows = {"QWERTYUIOP", "ASDFGHJKL'", "ZXCVBNM"};
        for (String row : rows) {
            LinearLayout kr = hbox();
            for (final char ch : row.toCharArray()) kr.addView(key(String.valueOf(ch), false, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    type(typed + (typed.isEmpty() || typed.endsWith(" ") ? ch : Character.toLowerCase(ch)));
                }
            }), weighted(margins(0, px(80), 0, 0, 8, 0)));
            if (row.length() == 7) {   // last letter row: delete key on the right
                kr.addView(key("⌫", false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (!typed.isEmpty()) type(typed.substring(0, typed.length() - 1));
                    }
                }), margins(0, px(80), 0, 0, 8, 0));
                ((LinearLayout.LayoutParams) kr.getChildAt(kr.getChildCount() - 1).getLayoutParams()).weight = 3;
            }
            sheet.addView(kr, margins(-1, -2, 0, 10, -8, 0));
        }
        LinearLayout last = hbox();
        last.addView(key(Logic.t("spazio", "space"), false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!typed.isEmpty() && !typed.endsWith(" ")) type(typed + " ");
            }
        }), new LinearLayout.LayoutParams(0, px(80), 3));
        last.addView(key(Logic.t("Cerca", "Search"), true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                h.removeCallbacks(liveSearch);
                searchCity(typed.trim());
            }
        }), weighted(margins(0, px(80), 10, 0, 0, 0)));
        sheet.addView(last, margins(-1, -2, 0, 10, 0, 0));
    }

    private LinearLayout found;

    private TextView key(String s, boolean on, View.OnClickListener click) {
        TextView t = pill(s, on, click);
        style(t, 34, on ? DARK : TXT, true);
        t.setPadding(0, 0, 0, 0);
        return t;
    }

    private void type(String s) {
        typed = s;
        showTyped();
        h.removeCallbacks(liveSearch);
        if (typed.trim().length() >= 3) h.postDelayed(liveSearch, 600);   // search while typing, once the finger stops
    }

    /** "Scrivi in auto" from the iPhone (via CyberZec Set): fills the open city search and searches. False if it is not open. */
    boolean typeFromSet(String s) {
        if (!resumed || cityShow == null) return false;
        h.removeCallbacks(liveSearch);
        typed = s;
        showTyped();
        searchCity(typed.trim());
        return true;
    }

    private void showTyped() {
        if (cityShow == null) return;
        cityShow.setText(typed.isEmpty() ? Logic.t("Scrivi la città", "Type the city") : typed + "_");
        cityShow.setTextColor(typed.isEmpty() ? MUT : TXT);
        if (typed.trim().length() < 3) showFound(null);
    }

    /** Up to 3 cities under the field; null = hint. */
    private void showFound(List<Weather.Place> res) {
        found.removeAllViews();
        if (res == null || res.isEmpty()) {
            TextView hint = text(res == null ? Logic.t("Dopo 3 lettere compaiono le città", "Cities appear after 3 letters")
                    : Logic.t("Nessuna città trovata", "No city found"), 28, MUT, true);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            found.addView(hint, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        for (int i = 0; i < Math.min(3, res.size()); i++) {
            final Weather.Place p = res.get(i);
            found.addView(pill(p.label, i == 0, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setCity(p);
                    closeSheet();
                }
            }), i == 0 ? new LinearLayout.LayoutParams(0, -1, 1) : weighted(margins(0, -1, 14, 0, 0, 0)));
        }
    }

    /** Big rounded button; on = lime. */
    private TextView pill(String s, boolean on, View.OnClickListener click) {
        TextView t = text(s, 30, on ? DARK : TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(18), 0, px(18), 0);
        t.setBackground(on ? round(LIME, 22, 0) : round(0x0FFFFFFF, 22, 0x40FFFFFF, 2));
        t.setOnClickListener(click);
        return t;
    }

    private void searchCity(final String q) {
        if (q.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                List<Weather.Place> found = null;
                try {
                    found = Weather.search(q);
                } catch (Exception e) {
                    Log.i(Weather.TAG, "ricerca citta fallita: " + e);
                }
                final List<Weather.Place> res = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (cityShow == null || !q.equals(typed.trim())) return;   // closed, or the text changed meanwhile
                        if (res == null) toast(Logic.t("Serve Internet per cercare", "Internet is needed to search"));
                        else showFound(res);
                    }
                });
            }
        }, "cerca-citta").start();
    }

    private void setCity(Weather.Place p) {
        saveCity(prefs, p);
        refreshWeatherNow();
    }

    /** Also used by SettingsReceiver (CyberZec Set), with or without the Home open. */
    static void cityFromGps(SharedPreferences prefs) {
        prefs.edit().remove("fix_city").remove("fix_lat").remove("fix_lon").remove("city").apply();
    }

    static void saveCity(SharedPreferences prefs, Weather.Place p) {
        p.name = p.name.replaceAll("[|;]", " ");   // separators of the "recent" list
        prefs.edit().putString("fix_city", p.name).putString("fix_lat", String.valueOf(p.lat))
                .putString("fix_lon", String.valueOf(p.lon)).putString("city", p.name).apply();
        List<Weather.Place> r = recentCities(prefs);
        StringBuilder b = new StringBuilder(p.name + "|" + p.lat + "|" + p.lon);
        int n = 1;
        for (Weather.Place q : r) if (!q.name.equals(p.name) && n++ < 3) b.append(";").append(q.name).append("|").append(q.lat).append("|").append(q.lon);
        prefs.edit().putString("recent", b.toString()).putLong("at", 0).apply();
    }

    /** Last 3 chosen cities, newest first ("name|lat|lon;..."). */
    static List<Weather.Place> recentCities(SharedPreferences prefs) {
        List<Weather.Place> out = new ArrayList<>();
        for (String e : prefs.getString("recent", "").split(";")) {
            String[] f = e.split("\\|");
            if (f.length != 3) continue;
            Weather.Place p = new Weather.Place();
            p.name = p.label = f[0];
            try {
                p.lat = Double.parseDouble(f[1]);
                p.lon = Double.parseDouble(f[2]);
                out.add(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** A setting changed from CyberZec Set (SettingsReceiver). */
    void settingsChanged(String key) {
        if ("city".equals(key)) lastWeatherTry = 0;
        showWeather(weather.cached());
        if (resumed) updateStatus();   // sky, name, and the new city's weather
    }

    private void refreshWeatherNow() {
        prefs.edit().putLong("at", 0).apply();
        lastWeatherTry = 0;
        showWeather(weather.cached());
        updateStatus();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- bottom bar: 6 apps chosen by the owner (long press)

    private String[] dockApps() {
        String saved = home.getString("dock", null);
        if (saved != null) return saved.split(",");
        String[] d = new String[DOCK.length];
        for (int i = 0; i < DOCK.length; i++) d[i] = DOCK[i][0] + "/" + DOCK[i][1];
        return d;
    }

    private Object[] dockDefault(String comp) {
        for (Object[] a : DOCK) if ((a[0] + "/" + a[1]).equals(comp)) return a;
        return null;
    }

    /** comp = "pkg" or "pkg/cls". */
    private String appName(String comp) {
        Object[] d = dockDefault(comp);
        if (d != null) return (String) d[2];
        PackageManager pm = getPackageManager();
        try {
            int s = comp.indexOf('/');
            if (s < 0) return pm.getApplicationLabel(pm.getApplicationInfo(comp, 0)).toString();
            return pm.getActivityInfo(new ComponentName(comp.substring(0, s), comp.substring(s + 1)), 0).loadLabel(pm).toString();
        } catch (Exception e) {
            return comp;
        }
    }

    private Drawable dockIcon(String comp) {
        Object[] d = dockDefault(comp);
        if (d != null) return getDrawable((Integer) d[3]);
        int s = comp.indexOf('/');
        int a = aurora(comp.substring(0, s));
        if (a != 0) return getDrawable(a);
        try {
            return getPackageManager().getActivityIcon(new ComponentName(comp.substring(0, s), comp.substring(s + 1)));
        } catch (Exception e) {
            return getDrawable(R.mipmap.ic_launcher);
        }
    }

    private void launch(String comp) {
        int s = comp.indexOf('/');
        launch(comp.substring(0, s), comp.substring(s + 1));
    }

    private void fillDock() {
        dock.removeAllViews();
        final String[] d = dockApps();
        for (int i = 0; i < d.length; i++) {
            final int slot = i;
            View cell = appCell(dockIcon(d[i]), appName(d[i]), 64, 26);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    launch(d[slot]);
                }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    replaceDockSlot(slot);
                    return true;
                }
            });
            dock.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void replaceDockSlot(final int slot) {
        final String[] d = dockApps();
        final List<String> comps = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ResolveInfo ri : launchables()) {
            String c = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
            if (Arrays.asList(d).contains(c)) continue;
            comps.add(c);
            names.add(appName(c));
        }
        choose(Logic.t("Al posto di ", "Instead of ") + appName(d[slot]), names.toArray(new String[0]), new Choice() {
            @Override public void on(int which) {
                d[slot] = comps.get(which);
                home.edit().putString("dock", TextUtils.join(",", d)).apply();
                refreshApps();
            }
        });
    }

    // ---------------------------------------------------------------- hidden apps

    private Set<String> hidden() {
        return new HashSet<>(home.getStringSet("hidden", new HashSet<>(Arrays.asList(HIDDEN))));
    }

    /** Hidden entries whose app is installed (the default list names apps that may be missing). */
    private List<String> hiddenInstalled() {
        List<String> out = new ArrayList<>();
        for (String e : hidden()) {
            try {
                getPackageManager().getApplicationInfo(e.contains("/") ? e.substring(0, e.indexOf('/')) : e, 0);
                out.add(e);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return out;
    }

    private static boolean isHidden(Set<String> h, String pkg, String cls) {
        return h.contains(pkg) || h.contains(pkg + "/" + cls);
    }

    private void appMenu(final String pkg, final String cls) {
        final String[] d = dockApps();
        String[] items = new String[d.length + 1];
        for (int i = 0; i < d.length; i++) items[i] = Logic.t("Nella barra al posto di ", "In the bar instead of ") + appName(d[i]);
        items[d.length] = Logic.t("Nascondi questa app", "Hide this app");
        choose(appName(pkg + "/" + cls), items, new Choice() {
            @Override public void on(int which) {
                if (which < d.length) {
                    d[which] = pkg + "/" + cls;
                    home.edit().putString("dock", TextUtils.join(",", d)).apply();
                } else {
                    Set<String> h = hidden();
                    h.add(pkg + "/" + cls);
                    home.edit().putStringSet("hidden", h).apply();
                }
                refreshApps();
            }
        });
    }

    private void showHiddenMenu() {
        final List<String> h = hiddenInstalled();
        String[] names = new String[h.size()];
        for (int i = 0; i < names.length; i++) names[i] = appName(h.get(i));
        choose(Logic.t("Tocca per far ricomparire", "Tap to show again"), names, new Choice() {
            @Override public void on(int which) {
                Set<String> set = hidden();
                set.remove(h.get(which));
                home.edit().putStringSet("hidden", set).apply();
                refreshApps();
            }
        });
    }

    private void refreshApps() {
        fillDock();
        buildAppPages();
    }

    interface Choice {
        void on(int which);
    }

    /** List dialog with in-car sized rows. */
    private void choose(String title, String[] items, final Choice cb) {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                style(t, 30, TXT, true);
                t.setPadding(px(30), px(22), px(30), px(22));
                return t;
            }
        };
        TextView head = text(title, 34, LIME, true);
        head.setPadding(px(30), px(26), px(30), px(10));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                .setCustomTitle(head)
                .setAdapter(ad, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int which) {
                        cb.on(which);
                    }
                })
                .setNegativeButton(Logic.t("Annulla", "Cancel"), null)
                .show();
    }

    // ---------------------------------------------------------------- pages 2..n: all other apps

    private List<ResolveInfo> launchables() {
        List<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            if (!ri.activityInfo.packageName.equals(getPackageName())) out.add(ri);
        return out;
    }

    private void buildAppPages() {
        while (pager.row.getChildCount() > 1) pager.row.removeViewAt(1);
        final PackageManager pm = getPackageManager();
        Set<String> h = hidden();
        List<String> inDock = Arrays.asList(dockApps());
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : launchables()) {
            String p = ri.activityInfo.packageName, cls = ri.activityInfo.name;
            if (!isHidden(h, p, cls) && !inDock.contains(p + "/" + cls)) apps.add(ri);
        }
        final Collator col = Collator.getInstance(Locale.ITALIAN);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {   // Aurora icons first, then by name
                int byIcon = Boolean.compare(aurora(b.activityInfo.packageName) != 0, aurora(a.activityInfo.packageName) != 0);
                return byIcon != 0 ? byIcon : col.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString());
            }
        });
        int pages = Logic.pages(apps.size(), APPS_PER_PAGE);
        for (int p = 0; p < pages; p++) {
            LinearLayout page = vbox();
            page.setPadding(px(36), px(28), px(36), px(28));
            LinearLayout head = hbox();
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(Logic.t("Tutte le app", "All apps"), 44, TXT, true);
            head.addView(title);
            head.addView(text((p + 1) + " / " + pages, 28, MUT, true), margins(0, -2, 20, 0, 0, 0));
            ((LinearLayout.LayoutParams) head.getChildAt(1).getLayoutParams()).weight = 1;
            int nHidden = hiddenInstalled().size();
            if (nHidden > 0) {
                TextView hid = text(Logic.t("Nascoste (", "Hidden (") + nHidden + ")", 28, MUT, true);
                hid.setGravity(Gravity.CENTER);
                hid.setPadding(px(22), 0, px(22), 0);
                hid.setBackground(round(0xCC111B1C, 999, LINE));
                hid.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showHiddenMenu();
                    }
                });
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, px(72));
                hlp.rightMargin = px(14);
                head.addView(hid, hlp);
            }
            TextView back = text("‹  Home", 30, TXT, true);
            back.setGravity(Gravity.CENTER);
            back.setPadding(px(22), 0, px(22), 0);
            back.setBackground(round(0xCC111B1C, 999, LINE));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pager.goTo(0);
                }
            });
            head.addView(back, new LinearLayout.LayoutParams(-2, px(72)));
            page.addView(head, new LinearLayout.LayoutParams(-1, px(78)));

            LinearLayout grid = vbox();   // one dark tile per app, like the cards of page 1
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1);
            glp.topMargin = px(10);
            page.addView(grid, glp);
            for (int r = 0; r < APPS_PER_PAGE / COLS; r++) {
                LinearLayout line = hbox();
                grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
                for (int cc = 0; cc < COLS; cc++) {
                    int i = p * APPS_PER_PAGE + r * COLS + cc;
                    View cell;
                    if (i < apps.size()) {
                        final AppRef ai = new AppRef(apps.get(i));
                        LinearLayout tile = card(8);
                        tile.setGravity(Gravity.CENTER);
                        tile.addView(appCell(icon(apps.get(i), pm), apps.get(i).loadLabel(pm).toString(), 100, 28));
                        cell = tile;
                        cell.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                launch(ai.pkg, ai.cls);
                            }
                        });
                        cell.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override public boolean onLongClick(View v) {
                                appMenu(ai.pkg, ai.cls);
                                return true;
                            }
                        });
                    } else cell = new View(this);
                    line.addView(cell, weighted(margins(0, -1, 7, 7, 7, 7)));
                }
            }
            pager.row.addView(page, new LinearLayout.LayoutParams(pager.getWidth() > 0 ? pager.getWidth() : -1, -1));
        }
        pager.fitPages();
    }

    static final class AppRef {
        final String pkg, cls;
        AppRef(ResolveInfo ri) {
            pkg = ri.activityInfo.packageName;
            cls = ri.activityInfo.name;
        }
    }

    private static int aurora(String pkg) {
        for (Object[] a : AURORA)
            if (a[0].equals(pkg)) return (Integer) a[1];
        return 0;
    }

    private Drawable icon(ResolveInfo ri, PackageManager pm) {
        int a = aurora(ri.activityInfo.packageName);
        return a != 0 ? getDrawable(a) : ri.loadIcon(pm);
    }

    private View appCell(Drawable d, String label, int size, int textSize) {
        LinearLayout cell = vbox();
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(iv, new LinearLayout.LayoutParams(px(size), px(size)));
        TextView t = text(label, textSize, TXT, true);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        cell.addView(t, margins(-1, -2, 0, 8, 0, 0));
        return cell;
    }

    private void launch(String pkg, String cls) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return;
        } catch (Exception e) {
            Log.i(TAG, "avvio " + pkg + "/" + cls + " fallito: " + e);
        }
        Intent l = getPackageManager().getLaunchIntentForPackage(pkg);
        try {
            if (l != null) {
                startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        } catch (Exception e) {
            Log.i(TAG, "avvio " + pkg + " fallito: " + e);
        }
        Toast.makeText(this, Logic.t("App non disponibile", "App not available"), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- view helpers (sizes in 240 dpi mockup pixels)

    int px(float v) {
        return Math.round(v * k);
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams margins(int w, int hgt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, hgt);
        lp.setMargins(px(l), px(t), px(r), px(b));
        return lp;
    }

    private static LinearLayout.LayoutParams weighted(LinearLayout.LayoutParams lp) {
        lp.weight = 1;
        lp.width = 0;
        return lp;
    }

    private void style(TextView t, float size, int color, boolean bold) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        style(t, size, color, bold);
        return t;
    }

    private TextView label(String s) {
        return label(s, 24);
    }

    private TextView label(String s, float size) {
        TextView t = text(s.toUpperCase(Locale.ITALY), size, MUT, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView chip(LinearLayout parent, int icon, int tint, String s) {
        LinearLayout c = hbox();
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(18), 0, px(6), 0);   // plain indicator, not a button
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        c.addView(iv, new LinearLayout.LayoutParams(px(34), px(34)));
        TextView t = text(s, 28, TXT, true);
        c.addView(t, margins(-2, -2, 10, 0, 0, 0));
        parent.addView(c, margins(-2, -2, 7, 0, 7, 0));
        return t;
    }

    /** A chip is shown or hidden as a whole. */
    private static void show(TextView chipText, boolean on) {
        ((View) chipText.getParent()).setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private LinearLayout card(int pad) {
        LinearLayout c = vbox();
        c.setPadding(px(pad + 2), px(pad), px(pad + 2), px(pad));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xEB172224, 0xEB0D1517});
        g.setCornerRadius(px(22));
        g.setStroke(Math.max(1, px(1)), LINE);
        c.setBackground(g);
        return c;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        return round(color, radius, stroke, 1);
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (stroke != 0) g.setStroke(Math.max(1, px(strokeW)), stroke);
        return g;
    }

    private GradientDrawable round(int[] colors, int radius, int unused) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(px(radius));
        return g;
    }

    private Drawable background() {
        GradientDrawable a = new GradientDrawable();
        a.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        a.setColors(new int[]{0x1AC5F56C, 0x00C5F56C});
        a.setGradientCenter(0.91f, 0.11f);
        a.setGradientRadius(px(600));
        GradientDrawable b = new GradientDrawable();
        b.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        b.setColors(new int[]{0x142FD6A6, 0x002FD6A6});
        b.setGradientCenter(0.15f, 0.97f);
        b.setGradientRadius(px(700));
        GradientDrawable base = new GradientDrawable();
        base.setColor(BG);
        return new LayerDrawable(new Drawable[]{base, a, b});
    }

    // ---------------------------------------------------------------- horizontal pages with snap

    static final class Pager extends HorizontalScrollView {
        final LinearLayout row;
        private int startPage, page;
        private boolean flung;

        Pager(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setFillViewport(true);
            row = new LinearLayout(c);
            addView(row, new FrameLayout.LayoutParams(-2, -1));
        }

        void fitPages() {
            int w = getWidth();
            if (w == 0) return;
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).getLayoutParams().width = w;
            row.requestLayout();
            page = Math.min(page, row.getChildCount() - 1);
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            super.onSizeChanged(w, hh, ow, oh);
            post(new Runnable() {
                @Override public void run() {
                    fitPages();
                    scrollTo(page * getWidth(), 0);
                }
            });
        }

        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && getWidth() > 0) {
                startPage = Math.round(getScrollX() / (float) getWidth());
                flung = false;
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            boolean r = super.onTouchEvent(ev);
            int a = ev.getActionMasked();
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !flung) snap(0);
            return r;
        }

        @Override public void fling(int velocityX) {
            flung = true;
            snap(velocityX);
        }

        private void snap(int v) {
            goTo(Logic.snapPage(startPage, getScrollX(), getWidth(), v, row.getChildCount()));
        }

        void goTo(int p) {
            page = p;
            smoothScrollTo(p * getWidth(), 0);
        }
    }
}
