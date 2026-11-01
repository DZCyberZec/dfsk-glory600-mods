package it.cyberzec.home;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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

    private TextView chipCarplay, chipNet, chipMeteo, chipGps, chipOutside;
    private ImageView chipNetIcon;
    private TextView cpName, cpStatus;
    private TextView climaState, climaLevel, climaUnit, fanText;
    private LinearLayout climaPower;
    private ImageView climaPowerIcon, mIcon;
    private final View[] fanSeg = new View[8];
    private final LinearLayout[] modeCell = new LinearLayout[4];
    private TextView tAc, tRecirc, tDefrost, tRear;
    private TextView mCity, mTemp, mText, mMinMax;
    private final TextView[] mHour = new TextView[6], mHourT = new TextView[6];

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = getResources().getDisplayMetrics().density / 1.5f;   // mockup pixels are 240 dpi pixels
        weather = new Weather(this);
        prefs = getSharedPreferences("meteo", MODE_PRIVATE);
        home = getSharedPreferences("home", MODE_PRIVATE);   // owner's dock and hidden apps
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        FrameLayout root = new FrameLayout(this);
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
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        showWeather(weather.cached());
    }

    @Override protected void onResume() {
        super.onResume();
        startLocation();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        try {
            lm.removeUpdates(location);
        } catch (Exception ignored) {
        }
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        hvac.disconnect();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        pager.goTo(0);   // Home key: back to the cockpit
    }

    @Override public void onBackPressed() {
        pager.goTo(0);   // a launcher never closes itself
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        startLocation();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            updateClima.run();
            h.postDelayed(this, 2000);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refreshApps();
        }
    };

    // ---------------------------------------------------------------- status bar chips, location, weather

    private void updateStatus() {
        boolean carplay = Settings.Secure.getInt(getContentResolver(), "bw.carplay.connect.state", 0) != 0;
        show(chipCarplay, carplay);
        cpStatus.setText(carplay ? Logic.t("Connesso", "Connected") : Logic.t("Non collegato", "Not connected"));
        cpStatus.setTextColor(carplay ? LIME : MUT);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean online = ni != null && ni.isConnected();
        chipNet.setText(Logic.internetShort(online, online && ni.getType() == ConnectivityManager.TYPE_WIFI,
                cm.isActiveNetworkMetered()));
        chipNet.setTextColor(online ? TXT : MUT);
        chipNetIcon.setImageResource(online ? R.drawable.st_wifi : R.drawable.st_offline);
        chipNetIcon.setColorFilter(online ? LIME : MUT, PorterDuff.Mode.SRC_IN);

        long at = weather.fetchedAt();
        show(chipMeteo, at > 0);
        if (at > 0) chipMeteo.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(at)));

        boolean gps = lastGpsAt > 0 && SystemClock.elapsedRealtime() - lastGpsAt < GPS_FRESH_MS;
        show(chipGps, gps);

        float out = hvac.getFloat(Hvac.OUTSIDE_TEMP, 0);
        show(chipOutside, !Float.isNaN(out));
        if (!Float.isNaN(out)) chipOutside.setText(Logic.t("Est. ", "Out ") + Math.round(out) + "°");

        boolean due = weather.stale() || !prefs.contains("city");
        if (online && due && prefs.contains("lat") && SystemClock.elapsedRealtime() - lastWeatherTry > 60_000) {
            lastWeatherTry = SystemClock.elapsedRealtime();   // at most one try a minute when a request fails
            weather.refresh(Double.parseDouble(prefs.getString("lat", "0")), Double.parseDouble(prefs.getString("lon", "0")),
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
                    && System.currentTimeMillis() - l.getTime() < GPS_FRESH_MS)
                lastGpsAt = SystemClock.elapsedRealtime();
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    private void showWeather(Weather.Data d) {
        if (d == null) {
            mCity.setText(prefs.contains("lat") ? Logic.t("Posizione trovata", "Location found") : Logic.t("In attesa del GPS", "Waiting for GPS"));
            mTemp.setText("--°");
            mText.setText(prefs.contains("lat") ? Logic.t("In attesa di Internet", "Waiting for Internet") : Logic.t("Serve la posizione", "Location needed"));
            mMinMax.setText("");
            mIcon.setImageResource(R.drawable.w_nubi);
            for (int i = 0; i < 6; i++) { mHour[i].setText(""); mHourT[i].setText(""); }
            return;
        }
        mCity.setText(d.city != null ? d.city : Logic.t("Posizione GPS", "GPS location"));
        mTemp.setText(d.temp + "°");
        mText.setText(d.text);
        mMinMax.setText(d.max + "° / " + d.min + "°");
        mIcon.setImageResource(getResources().getIdentifier("w_" + Logic.meteoIcon(d.code), "drawable", getPackageName()));
        for (int i = 0; i < 5; i++) {
            mHour[i].setText(d.hours[i] == null ? "" : d.hours[i]);
            mHourT[i].setText(d.hours[i] == null ? "" : d.hourTemps[i] + "°");
        }
        mHour[5].setText(Logic.t("Pioggia", "Rain"));
        mHourT[5].setText(d.rainPct + "%");
    }

    // ---------------------------------------------------------------- climate

    private final Runnable updateClima = new Runnable() {
        @Override public void run() {
            boolean ready = hvac.ready();
            boolean on = ready && hvac.getBool(Hvac.POWER_ON, Hvac.AREA_ROW);
            int fan = on ? Math.max(0, hvac.getInt(Hvac.FAN_SPEED, Hvac.AREA_ROW)) : 0;
            boolean front = ready && hvac.getBool(Hvac.DEFROST, Hvac.AREA_FRONT_WINDOW);
            int mode = on ? hvac.getInt(Hvac.MODE, Hvac.AREA_ROW) : -1;

            climaState.setText(!ready ? Logic.t("Non disponibile", "Not available") : on ? Logic.t("Acceso", "On") : Logic.t("Spento", "Off"));
            climaState.setTextColor(on ? LIME : MUT);
            // same look as the other switches: lime = on
            climaPowerIcon.setColorFilter(on ? DARK : TXT, PorterDuff.Mode.SRC_IN);
            climaPower.setBackground(on ? round(LIME, 999, LIME) : round(0x0DFFFFFF, 999, LINE));

            setFan(fan);
            fanText.setText(on ? Logic.t("Ventola  ", "Fan  ") + fan + "/8" : Logic.t("Ventola", "Fan"));
            float t = ready ? hvac.getFloat(Hvac.TEMP, Hvac.AREA_TEMP) : Float.NaN;
            // this car is level-only (secure offline_hvac_type=0): never degrees
            climaLevel.setText(Float.isNaN(t) ? "—" : Logic.climaLevelText(t));
            for (int i = 0; i < 4; i++) modeSelected(i, i == mode);
            toggle(tDefrost, front);
            String snap = "acceso=" + on + " ventola=" + fan + " modo=" + (ready ? hvac.getInt(Hvac.MODE, Hvac.AREA_ROW) : -1)
                    + " parabrezza=" + front + " livello=" + climaLevel.getText()
                    + " ac=" + (ready && hvac.getBool(Hvac.AC_ON, Hvac.AREA_ROW))
                    + " ricircolo=" + (ready && hvac.getBool(Hvac.RECIRC_ON, Hvac.AREA_RECIRC))
                    + " lunotto=" + (ready && hvac.getBool(Hvac.DEFROST, Hvac.AREA_REAR_WINDOW));
            if (!snap.equals(lastClimaLog)) Log.e(Hvac.TAG, "stato " + (lastClimaLog = snap));   // Log.e: log.tag=M on the car hides lower levels
            toggle(tAc, ready && hvac.getBool(Hvac.AC_ON, Hvac.AREA_ROW));
            toggle(tRecirc, ready && hvac.getBool(Hvac.RECIRC_ON, Hvac.AREA_RECIRC));
            toggle(tRear, ready && hvac.getBool(Hvac.DEFROST, Hvac.AREA_REAR_WINDOW));
        }
    };

    /** Air direction 0..3, same command as the Clima app (does not touch the windscreen defrost). */
    private void setMode(int m) {
        Log.e(Hvac.TAG, "scelta modalita " + m);
        if (!hvac.setMode(m)) { notAvailable(); return; }
        if (hvac.getBool(Hvac.DEFROST, Hvac.AREA_FRONT_WINDOW))   // the unit keeps the air on the windscreen
            Toast.makeText(this, Logic.t("Parabrezza acceso: l'aria resta sul vetro finché non lo spegni", "Windscreen defrost is on: air stays on the glass until you turn it off"), Toast.LENGTH_LONG).show();
        h.postDelayed(updateClima, 900);
        h.postDelayed(updateClima, 1800);
    }

    private void notAvailable() {
        Toast.makeText(this, Logic.t("Clima non disponibile", "Climate not available"), Toast.LENGTH_SHORT).show();
    }

    private void press(int key) {
        if (!hvac.press(key)) {
            notAvailable();
            return;
        }
        h.postDelayed(updateClima, 700);
        h.postDelayed(updateClima, 1500);
    }

    private View.OnClickListener key(final int key) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                press(key);
            }
        };
    }

    private void setFan(int level) {
        for (int i = 0; i < 8; i++)
            fanSeg[i].setBackground(i < level ? round(new int[]{LIME, TEAL}, 5, 0) : round(0x14FFFFFF, 5, 0));
    }

    private void toggle(TextView t, boolean on) {
        t.setTextColor(on ? DARK : TXT);
        t.setBackground(on ? round(LIME, 14, LIME) : round(0x08FFFFFF, 14, LINE));
    }

    private void modeSelected(int i, boolean on) {
        LinearLayout cell = modeCell[i];
        cell.setBackground(on ? round(LIME, 14, LIME) : round(0x08FFFFFF, 14, LINE));
        ((ImageView) cell.getChildAt(0)).setColorFilter(on ? DARK : TEAL, PorterDuff.Mode.SRC_IN);
        ((TextView) cell.getChildAt(1)).setTextColor(on ? DARK : TXT);
    }

    // ---------------------------------------------------------------- page 1: cockpit

    private View cockpit() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(30), px(18), px(30), px(22));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        style(clock, 68, TXT, true);
        top.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE d MMM");
        date.setFormat12Hour("EEE d MMM");
        style(date, 30, MUT, true);
        top.addView(date, margins(-2, -2, 20, 8, 0, 0));
        LinearLayout chips = hbox();
        chips.setGravity(Gravity.CENTER);
        chipCarplay = chip(chips, R.drawable.st_phone, LIME, "CarPlay");
        chipNet = chip(chips, R.drawable.st_wifi, LIME, "Internet");
        chipNetIcon = (ImageView) ((ViewGroup) chipNet.getParent()).getChildAt(0);
        chipMeteo = chip(chips, R.drawable.st_weather, TEAL, Logic.t("Meteo", "Weather"));
        chipGps = chip(chips, R.drawable.st_gps, LIME, "GPS");
        chipOutside = chip(chips, R.drawable.st_temp, TEAL, "");
        top.addView(chips, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(top, new LinearLayout.LayoutParams(-1, px(78)));

        LinearLayout body = hbox();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1);
        blp.topMargin = px(14);
        page.addView(body, blp);
        body.addView(carplayCard(), new LinearLayout.LayoutParams(px(380), -1));

        LinearLayout right = vbox();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1);
        rlp.leftMargin = px(18);
        body.addView(right, rlp);
        LinearLayout row = hbox();
        right.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        row.addView(climaCard(), new LinearLayout.LayoutParams(0, -1, 1.5f));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -1, 1);
        mlp.leftMargin = px(18);
        row.addView(meteoCard(), mlp);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, px(178));
        alp.topMargin = px(16);
        right.addView(dockCard(), alp);
        return page;
    }

    private View carplayCard() {
        LinearLayout c = card(24);
        c.addView(label("CarPlay"));
        cpName = text(iphoneName(), 40, TXT, true);
        c.addView(cpName, margins(-1, -2, 0, 12, 0, 0));
        cpStatus = text("", 28, MUT, true);
        c.addView(cpStatus, margins(-1, -2, 0, 6, 0, 0));

        FrameLayout art = new FrameLayout(this);
        c.addView(art, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout phone = new FrameLayout(this);
        phone.setBackground(round(Color.TRANSPARENT, 30, LIME, 3));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(px(110), px(196), Gravity.CENTER);
        art.addView(phone, plp);
        View notch = new View(this);
        notch.setBackground(round(LIME, 10, 0));
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(px(48), px(14), Gravity.CENTER_HORIZONTAL);
        nlp.topMargin = px(12);
        phone.addView(notch, nlp);
        ImageView waves = new ImageView(this);
        waves.setImageResource(R.drawable.waves);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(px(60), px(90), Gravity.CENTER);
        wlp.leftMargin = px(95);   // just right of the phone
        art.addView(waves, wlp);

        TextView cta = text(Logic.t("Avvia CarPlay", "Start CarPlay"), 36, DARK, true);
        cta.setGravity(Gravity.CENTER);
        cta.setBackground(round(LIME, 18, 0));
        cta.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch("com.suding.speedplay", "com.suding.speedplay.ui.MainActivity");
            }
        });
        c.addView(cta, new LinearLayout.LayoutParams(-1, px(104)));
        return c;
    }

    private View climaCard() {
        LinearLayout c = card(18);
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(Logic.t("Clima", "Climate"));
        title.setOnClickListener(new View.OnClickListener() {   // opens the full climate app
            @Override public void onClick(View v) {
                launch("com.bw.ac", "com.bw.ac.activity.MainActivity");
            }
        });
        head.addView(title);
        climaState = text("", 28, MUT, true);
        head.addView(climaState, weighted(margins(0, -2, 16, 0, 0, 0)));
        climaPower = hbox();
        climaPower.setGravity(Gravity.CENTER);
        climaPowerIcon = new ImageView(this);
        climaPowerIcon.setImageResource(R.drawable.ic_power);
        climaPower.setContentDescription(Logic.t("Accendi o spegni il clima", "Turn climate on or off"));
        climaPower.addView(climaPowerIcon, new LinearLayout.LayoutParams(px(38), px(38)));
        climaPower.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!hvac.ready()) { notAvailable(); return; }
                // off: like the original card, switching on goes through the A/C key
                press(hvac.getBool(Hvac.POWER_ON, Hvac.AREA_ROW) ? Hvac.KEY_OFF : Hvac.KEY_AC);
            }
        });
        head.addView(climaPower, new LinearLayout.LayoutParams(px(96), px(60)));
        c.addView(head);

        LinearLayout row = hbox();
        LinearLayout lvGroup = vbox();
        climaUnit = text(Logic.t("Livello", "Level"), 22, MUT, true);
        lvGroup.addView(climaUnit);
        LinearLayout lv = hbox();
        lv.setGravity(Gravity.CENTER_VERTICAL);
        lv.addView(button("−", key(Hvac.KEY_TEMP_DOWN), COLD), new LinearLayout.LayoutParams(px(78), px(78)));
        climaLevel = text("—", 56, TXT, true);
        climaLevel.setGravity(Gravity.CENTER);
        lv.addView(climaLevel, new LinearLayout.LayoutParams(px(120), -2));
        lv.addView(button("+", key(Hvac.KEY_TEMP_UP), HOT), new LinearLayout.LayoutParams(px(78), px(78)));
        lvGroup.addView(lv, margins(-2, -2, 0, 6, 0, 0));
        row.addView(lvGroup);

        LinearLayout fanGroup = vbox();
        fanText = text(Logic.t("Ventola", "Fan"), 22, MUT, true);
        fanGroup.addView(fanText);
        LinearLayout fanBox = hbox();
        fanBox.setGravity(Gravity.CENTER_VERTICAL);
        fanBox.addView(button("−", Hvac.KEY_FAN_DOWN), new LinearLayout.LayoutParams(px(78), px(78)));
        LinearLayout fan = hbox();
        for (int i = 0; i < 8; i++) {
            fanSeg[i] = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.leftMargin = lp.rightMargin = px(2);
            fan.addView(fanSeg[i], lp);
        }
        fanBox.addView(fan, weighted(margins(0, px(22), 10, 0, 10, 0)));
        fanBox.addView(button("+", Hvac.KEY_FAN_UP), new LinearLayout.LayoutParams(px(78), px(78)));
        fanGroup.addView(fanBox, margins(-1, -2, 0, 6, 0, 0));
        row.addView(fanGroup, weighted(margins(0, -2, 22, 0, 0, 0)));
        c.addView(row, margins(-1, -2, 0, 4, 0, 0));

        LinearLayout modes = hbox();
        for (int i = 0; i < 4; i++) {
            final int m = i;
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageResource(getResources().getIdentifier("mode_" + i, "drawable", getPackageName()));
            cell.addView(iv, new LinearLayout.LayoutParams(px(50), px(50)));
            TextView t = text(Logic.modeName(i), 23, TXT, true);
            t.setSingleLine();
            cell.addView(t, margins(-2, -2, 0, 0, 0, 0));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setMode(m);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) lp.leftMargin = px(10);
            modes.addView(cell, lp);
            modeCell[i] = cell;
            modeSelected(i, false);
        }
        c.addView(modes, margins(-1, px(86), 0, 10, 0, 0));

        LinearLayout tg = hbox();
        tAc = toggleView(tg, "A/C", Hvac.KEY_AC);
        tRecirc = toggleView(tg, Logic.t("Ricircolo", "Recirculation"), Hvac.KEY_RECIRC);
        tDefrost = toggleView(tg, Logic.t("Parabrezza", "Windscreen"), Hvac.KEY_DEFROST);
        tRear = toggleView(tg, Logic.t("Lunotto", "Rear window"), Hvac.KEY_REAR_DEFROST);
        c.addView(tg, margins(-1, px(62), 0, 10, 0, 0));
        return c;
    }

    private TextView toggleView(LinearLayout parent, String s, int key) {
        TextView t = text(s, 26, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setOnClickListener(key(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        if (parent.getChildCount() > 0) lp.leftMargin = px(10);
        parent.addView(t, lp);
        toggle(t, false);
        return t;
    }

    private View button(String s, int key) {
        return button(s, key(key), 0);
    }

    /** color != 0: tinted button (blue = colder, red = warmer). */
    private View button(String s, View.OnClickListener click, int color) {
        TextView t = text(s, 44, color != 0 ? color : TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(color != 0 ? round(color & 0x00FFFFFF | 0x26000000, 18, color, 2) : round(0x0DFFFFFF, 18, LINE));
        t.setOnClickListener(click);
        return t;
    }

    private View meteoCard() {
        LinearLayout c = card(22);
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView pin = new ImageView(this);
        pin.setImageResource(R.drawable.st_gps);
        pin.setColorFilter(LIME, PorterDuff.Mode.SRC_IN);
        head.addView(pin, new LinearLayout.LayoutParams(px(38), px(38)));
        mCity = text("", 34, TXT, true);
        mCity.setSingleLine();
        mCity.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(mCity, weighted(margins(0, -2, 10, 0, 0, 0)));
        c.addView(head);

        LinearLayout r = hbox();
        r.setGravity(Gravity.CENTER_VERTICAL);
        mIcon = new ImageView(this);
        mIcon.setColorFilter(LIME, PorterDuff.Mode.SRC_IN);
        r.addView(mIcon, new LinearLayout.LayoutParams(px(110), px(110)));
        mTemp = text("--°", 96, TXT, true);
        r.addView(mTemp, margins(-2, -2, 16, 0, 0, 0));
        LinearLayout desc = vbox();
        mText = text("", 30, TXT, true);
        mText.setMaxLines(2);
        mText.setEllipsize(TextUtils.TruncateAt.END);
        mMinMax = text("", 26, MUT, true);
        desc.addView(mText);
        desc.addView(mMinMax, margins(-2, -2, 0, 6, 0, 0));
        r.addView(desc, weighted(margins(0, -2, 18, 0, 0, 0)));
        c.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        c.addView(r);

        LinearLayout hours = hbox();
        for (int i = 0; i < 6; i++) {
            LinearLayout col = vbox();
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            mHour[i] = text("", 22, MUT, true);
            mHourT[i] = text("", 32, i == 5 ? LIME : TXT, true);
            col.addView(mHour[i]);
            col.addView(mHourT[i], margins(-2, -2, 0, 6, 0, 0));
            hours.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        }
        View spacer = new View(this);
        c.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        c.addView(hours);
        return c;
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

    private View dockCard() {
        dock = card(14);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        fillDock();
        return dock;
    }

    private void fillDock() {
        dock.removeAllViews();
        final String[] d = dockApps();
        for (int i = 0; i < d.length; i++) {
            final int slot = i;
            View cell = appCell(dockIcon(d[i]), appName(d[i]), 104);
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

            LinearLayout grid = card(18);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1);
            glp.topMargin = px(18);
            page.addView(grid, glp);
            for (int r = 0; r < APPS_PER_PAGE / COLS; r++) {
                LinearLayout line = hbox();
                line.setGravity(Gravity.CENTER_VERTICAL);
                grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
                for (int cc = 0; cc < COLS; cc++) {
                    int i = p * APPS_PER_PAGE + r * COLS + cc;
                    View cell;
                    if (i < apps.size()) {
                        final AppRef ai = new AppRef(apps.get(i));
                        cell = appCell(icon(apps.get(i), pm), apps.get(i).loadLabel(pm).toString(), 128);
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
                    line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
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

    private View appCell(Drawable d, String label, int size) {
        LinearLayout cell = vbox();
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(iv, new LinearLayout.LayoutParams(px(size), px(size)));
        TextView t = text(label, 26, TXT, true);
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

    private String iphoneName() {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null)
                for (BluetoothDevice d : bt.getBondedDevices())
                    if (d.getName() != null && d.getName().toLowerCase(Locale.ROOT).contains("iphone")) return d.getName();
        } catch (Exception ignored) {
        }
        return "iPhone";
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
        c.setPadding(px(20), px(10), px(24), px(10));
        c.setBackground(round(0xCC111B1C, 999, LINE));
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
