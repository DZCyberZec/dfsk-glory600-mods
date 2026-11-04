package it.dfsk.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight foreground service: keeps the dynamic receivers alive (Android 8+ does not deliver
 * SpeedPlay/BwCarSource implicit broadcasts to manifest receivers).
 */
public class HelperService extends Service {
    static final String TAG = "DFSKHelper";
    static final String SP = "com.suding.speedplay";
    static final String BACKCAR_START = SP + ".action.BACKCAR_START";
    static final String BACKCAR_STOP = SP + ".action.BACKCAR_STOP";
    static final long ARM_DELAY_MS = 800; // after the last STOP (reverse + AVM both send one), see README
    static final long HOTSPOT_MS = 30_000;
    static final String HOME_SET = "it.cyberzec.home.SET", HOME_PERM = "it.cyberzec.permission.SETTINGS";
    static final Uri HOME_STATUS = Uri.parse("content://it.cyberzec.home.status");
    static final int WEB_PORT = 8091;
    static final String CARPLAY_STATE = "bw.carplay.connect.state"; // Settings.Secure: 0 = off, 2 = wired (verified 01/11)
    static final long CP_WATCH_MS = 15_000;
    static final ComponentName SP_MEDIA = new ComponentName(SP, "org.texustek.mirror.media.session.MediaPlaybackService");
    static String[] modeNames() {
        return new String[]{Lang.t("Pausa", "Pause"), Lang.t("Attenuata", "Ducked"), Lang.t("Più bassa", "Lower")};
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private AudioFocusRequest duckRequest;
    private WebPanel web;
    private Boolean spMedia; // null = unknown (fresh start), else whether we started SpeedPlay's media service
    private final Runnable cpWatch = new Runnable() { public void run() { watchCarPlayMedia(); } };
    private final ContentObserver carplayObs = new ContentObserver(h) {
        @Override public void onChange(boolean self) { updateCarPlayMedia(false); }
    };
    private final Runnable arm = new Runnable() { public void run() { armReverse(HelperService.this); } };
    private final Runnable sessionCheck = new Runnable() { public void run() { updateSession(); } };
    private final LocationListener park = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            Prefs.get(HelperService.this).edit().putString(Prefs.PARK,
                    String.format(Locale.ROOT, "%.5f,%.5f,%d", l.getLatitude(), l.getLongitude(), l.getTime())).apply();
        }
        @Override public void onStatusChanged(String p, int s, android.os.Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };
    private final Runnable retroEnd = new Runnable() { public void run() { retroStop(); } };
    private final Runnable duckCheck = new Runnable() { public void run() { checkDuck(); } };
    private final Runnable hotspot = new Runnable() { public void run() { hotspotCheck(); } };

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            long now = SystemClock.elapsedRealtime();
            if (BACKCAR_START.equals(a)) {
                // Implicit broadcast from BwCarSource (MCU -> VHAL SYSTEM_[AVM_]REVERSE_STATUS). Our own arm is package-targeted, so it never arrives here.
                boolean duck = ReverseState.INSTANCE.onStart(now, Prefs.reverseMode(c), inCall());
                event(Lang.t("inizio retromarcia", "reverse start"));
                if (duck) requestDuck();
                h.postDelayed(duckCheck, ReverseState.TIMEOUT_MS + 50);
                retroStart();
            } else if (BACKCAR_STOP.equals(a)) {
                ReverseState.INSTANCE.onStop(now);
                event(Lang.t("fine retromarcia", "reverse end"));
                h.postDelayed(duckCheck, ReverseState.STOP_DEBOUNCE_MS + 50);
                h.postDelayed(retroEnd, ReverseState.STOP_DEBOUNCE_MS + 50);
                scheduleArm();
            } else if (SP.equals(a)) {
                String status = i.getStringExtra("status");
                Log.i(TAG, "SpeedPlay status=" + status + " phoneMode=" + i.getStringExtra("phoneMode"));
                if (CallState.INSTANCE.onStatus(status, now)) {
                    Log.i(TAG, "CarPlay call active=" + CallState.INSTANCE.isActive(now));
                    updateSession();
                    checkDuck();
                }
                // SpeedPlay (re)started or phone connected: its backing flag may have been reset
                if ("CONNECTING".equals(status)) {
                    // the iPhone sends the title once, right at connection: the service must already be listening
                    if (Sys.isSystem() && Prefs.carplayTrack(HelperService.this) && !spSessionActive()) startCarPlayMedia("started early");
                    h.postDelayed(new Runnable() { public void run() { updateCarPlayMedia(false); } }, 60_000); // connection failed: stop it
                } else if ("CONNECTED".equals(status) || "SERVICE_READY".equals(status)) {
                    scheduleArm();
                    updateCarPlayMedia(!spSessionActive()); // SpeedPlay may have restarted and lost its media service
                } else if ("DISCONNECT".equals(status) || "EXIT".equals(status)) updateCarPlayMedia(false);
            }
        }
    };

    static void start(Context c) { start(c, false); }

    static void start(Context c, boolean afterBoot) {
        Intent i = new Intent(c, HelperService.class).putExtra("boot", afterBoot);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void sendToSpeedPlay(Context c, String action, boolean noPause) {
        Intent i = new Intent(action).setPackage(SP); // only SpeedPlay receives it
        if (noPause) i.putExtra("extra_mute", 0);
        c.sendBroadcast(i);
    }

    /** Used by the app screen and the web panel. */
    static void setReverseMode(Context c, int mode) {
        int old = Prefs.reverseMode(c);
        Prefs.get(c).edit().putInt(Prefs.REVERSE_MODE, mode).apply();
        Log.i(TAG, "reverse mode " + modeNames()[old] + " -> " + modeNames()[mode]);
        if (mode == ReverseState.PAUSE && old != ReverseState.PAUSE) {
            // disarm: clears SpeedPlay's flag (no PLAY is sent because the arm never recorded audio focus)
            sendToSpeedPlay(c, BACKCAR_STOP, false);
            Log.i(TAG, "disarmed: sent BACKCAR_STOP to SpeedPlay");
        }
        start(c); // re-arm / release focus if needed
    }

    /** Pre-sets SpeedPlay's "reversing" flag without pausing, so the firmware's later BACKCAR_START is ignored. */
    static void armReverse(Context c) {
        if (Prefs.reverseMode(c) == ReverseState.PAUSE) return;
        String why = unsafeReason();
        if (why != null) { Log.w(TAG, "reverse-music NOT armed: " + why); return; }
        sendToSpeedPlay(c, BACKCAR_START, true);
        Log.i(TAG, "armed: sent BACKCAR_START extra_mute=0");
    }

    /** Branches in SpeedPlay where the backing flag does more than skip the pause (SpeedPlayManager.setMpuCarBacking, ProjectionRenderer.isAllowedUseCodec). */
    static String unsafeReason() {
        String customer = Sys.prop("sys.suding.custom");
        String platform = Sys.prop("ro.board.platform.nwd");
        if (platform.isEmpty()) platform = Sys.prop("ro.board.platform");
        if ("dingwei".equals(customer) || "tengshi".equals(customer)) return "sys.suding.custom=" + customer;
        if (platform.contains("mt6768")) return "platform=" + platform;
        return null;
    }

    private void event(String what) {
        String s = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date()) + " " + what;
        ReverseState.INSTANCE.lastEvent = s;
        Log.i(TAG, "reverse event: " + what + " (mode=" + modeNames()[Prefs.reverseMode(this)] + ")");
    }

    private boolean inCall() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        return CallState.INSTANCE.isActive(SystemClock.elapsedRealtime()) || am.getMode() != AudioManager.MODE_NORMAL;
    }

    /**
     * "Più bassa": a transient MAY_DUCK request makes the framework duck SpeedPlay's music track to 0.2
     * (PlaybackActivityMonitor auto-duck; SpeedPlay targetSdk 29) or, if dispatched, SpeedPlay's own -3 handler
     * fades AudioTrackManagerMusic to 0.2f. We never play any sound.
     */
    private void requestDuck() {
        if (Build.VERSION.SDK_INT < 26) return;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        duckRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    public void onAudioFocusChange(int f) { Log.i(TAG, "duck focus change " + f); }
                }, h).build();
        int r = am.requestAudioFocus(duckRequest);
        Log.i(TAG, "duck focus requested (GAIN_TRANSIENT_MAY_DUCK) result=" + r);
    }

    static void setRetroPercent(Context c, int percent) {
        Prefs.get(c).edit().putInt(Prefs.RETRO_MODE, percent).apply();
        if (Sys.isSystem()) Sys.setBackcarMusic(RetroVolume.policyFor(percent));
    }

    /** Reverse starts: note the normal music level and lower it as chosen. The knob does not reach Android while
     *  reversing (the MCU handles it, seen 02/11), so the level is picked in the app instead of remembered. */
    private void retroStart() {
        SharedPreferences p = Prefs.get(this);
        int pct = Prefs.retroPercent(this);
        if (pct >= 100 || p.getInt(Prefs.RETRO_NORMAL, -1) >= 0) return; // unchanged, or already in reverse (R-D-R)
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int normal = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        p.edit().putInt(Prefs.RETRO_NORMAL, normal).commit(); // saved first: restored even if we die mid-reverse
        int set = RetroVolume.onStart(pct, normal);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, set, 0);
        Log.e(TAG, "retro volume: normal=" + normal + " reverse=" + set);
    }

    /** Reverse ended (debounced): put the normal level back. */
    private void retroStop() {
        SharedPreferences p = Prefs.get(this);
        int normal = p.getInt(Prefs.RETRO_NORMAL, -1);
        if (normal < 0 || ReverseState.INSTANCE.isReversing()) return;
        p.edit().putInt(Prefs.RETRO_NORMAL, -1).apply();
        ((AudioManager) getSystemService(AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_MUSIC, normal, 0);
        Log.e(TAG, "retro volume: back to " + normal);
    }

    private void checkDuck() {
        if (ReverseState.INSTANCE.shouldRelease(SystemClock.elapsedRealtime(), Prefs.reverseMode(this), inCall())) abandonDuck();
    }

    private void abandonDuck() {
        if (duckRequest == null) return;
        int r = ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocusRequest(duckRequest);
        duckRequest = null;
        Log.i(TAG, "duck focus abandoned result=" + r);
    }

    private void scheduleArm() {
        h.removeCallbacks(arm);
        if (Prefs.reverseMode(this) != ReverseState.PAUSE) h.postDelayed(arm, ARM_DELAY_MS);
    }

    static boolean carplayOn(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), CARPLAY_STATE, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** "Hotspot automatico": Wi-Fi on, not connected, CarPlay not connected -> connect to the chosen saved network. */
    private void hotspotCheck() {
        h.removeCallbacks(hotspot);
        h.postDelayed(hotspot, HOTSPOT_MS);
        String ssid = Prefs.hotspotSsid(this);
        if (ssid.isEmpty() || carplayOn(this)) return;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            if (!wm.isWifiEnabled() || wi != null && wi.getNetworkId() != -1) return; // off, connected or connecting
            for (WifiConfiguration w : wm.getConfiguredNetworks()) {
                if (!("\"" + ssid + "\"").equals(w.SSID)) continue;
                boolean ok = wm.enableNetwork(w.networkId, true) & wm.reconnect();
                Log.i(TAG, "hotspot: reconnect to " + ssid + (ok ? "" : " (FAILED)"));
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "hotspot: " + t);
        }
    }

    /** SSIDs of the saved Wi-Fi networks (for the hotspot choice); empty if not readable. */
    static List<String> savedSsids(Context c) {
        List<String> out = new ArrayList<>();
        try {
            List<WifiConfiguration> l = ((WifiManager) c.getApplicationContext().getSystemService(WIFI_SERVICE)).getConfiguredNetworks();
            if (l != null) for (WifiConfiguration w : l) {
                String s = w.SSID == null ? "" : w.SSID.replaceAll("^\"|\"$", "");
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        } catch (Throwable t) {
            Log.e(TAG, "saved networks: " + t);
        }
        return out;
    }

    /** Explicit broadcast to Home CyberZec's SettingsReceiver (signature permission on both sides). */
    static boolean sendToHome(Context c, String key, String value) {
        c.sendBroadcast(new Intent(HOME_SET).setPackage(Sys.HOME).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("key", key).putExtra("value", value), HOME_PERM);
        return true; // fire and forget: the Home validates again
    }

    /** Home CyberZec status provider (key -> value); empty if the Home is missing or the permission was not granted. */
    static Map<String, String> homeStatus(Context c) {
        Map<String, String> m = new LinkedHashMap<>();
        try (Cursor cu = c.getContentResolver().query(HOME_STATUS, null, null, null, null)) {
            while (cu != null && cu.moveToNext()) m.put(cu.getString(0), cu.getString(1));
        } catch (Throwable t) {
            Log.e(TAG, "home status: " + t);
        }
        return m;
    }

    static String versionOf(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return Lang.t("non installata", "not installed");
        }
    }

    /** "Title — artist" from SpeedPlay's media session, or null. */
    static String nowPlaying(Context c) {
        try {
            for (MediaController mc : ((MediaSessionManager) c.getSystemService(MEDIA_SESSION_SERVICE)).getActiveSessions(null)) {
                MediaMetadata md = mc.getMetadata();
                if (!SP.equals(mc.getPackageName()) || md == null) continue;
                String t = md.getString(MediaMetadata.METADATA_KEY_TITLE), a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (t != null && !t.isEmpty()) return a != null && !a.isEmpty() ? t + " — " + a : t;
            }
        } catch (Throwable ignored) { } // needs MEDIA_CONTENT_CONTROL (system variant)
        return null;
    }

    /**
     * Main call-volume method. The wheel keys are injected by BwCarService (policyFlags 0x6b000000, no FILTERED bit),
     * so they bypass the accessibility key filter and reach MediaSessionService. A PLAYING local session with
     * voice-call attributes becomes the default volume session, so those keys adjust STREAM_VOICE_CALL.
     */
    private void updateSession() {
        boolean want = Prefs.callVolume(this) && CallState.INSTANCE.isActive(SystemClock.elapsedRealtime());
        h.removeCallbacks(sessionCheck);
        if (want) {
            if (session == null) {
                session = new MediaSession(this, "DFSKCallVolume");
                session.setPlaybackToLocal(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                session.setPlaybackState(new PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
                session.setActive(true);
                Log.i(TAG, "call MediaSession active");
            }
            h.postDelayed(sessionCheck, 60_000); // re-check so the 2 h CallState timeout also ends the session
        } else if (session != null) {
            session.release();
            session = null;
            Log.i(TAG, "call MediaSession released");
        }
    }

    /**
     * "Brano CarPlay nella Home": SpeedPlay publishes CarPlay now-playing in its own MediaSession
     * (CarPlayPlaybackService) only for some customers. Uid system may start that non-exported service;
     * it calls startForeground in onCreate, so startForegroundService is safe and avoids the background-start check.
     * Started only on transitions: each onStartCommand blanks the title until the next track update.
     */
    private void updateCarPlayMedia(boolean force) {
        if (!Sys.isSystem()) return;
        boolean connected = false;
        try {
            connected = Settings.Secure.getInt(getContentResolver(), CARPLAY_STATE, 0) != 0;
        } catch (Throwable t) {
            Log.e(TAG, "carplay track: cannot read " + CARPLAY_STATE + ": " + t);
        }
        boolean want = connected && Prefs.carplayTrack(this);
        if (want && (force || !Boolean.TRUE.equals(spMedia))) {
            startCarPlayMedia("started");
        } else if (!want && !Boolean.FALSE.equals(spMedia)) {
            stopCarPlayMedia();
        }
    }

    private void startCarPlayMedia(String what) {
        try {
            Intent i = new Intent().setComponent(SP_MEDIA);
            ComponentName r = Build.VERSION.SDK_INT >= 26 ? startForegroundService(i) : startService(i);
            Log.e(TAG, "carplay track: " + (r != null ? what + " SpeedPlay media service" : "SpeedPlay media service not found"));
            spMedia = r != null;
        } catch (Throwable t) { // SecurityException, IllegalStateException...
            Log.e(TAG, "carplay track: start failed: " + t);
            spMedia = false;
        }
        h.removeCallbacks(cpWatch);
        h.postDelayed(cpWatch, CP_WATCH_MS); // also retries a failed start, at most once per CP_WATCH_MS
    }

    /** Watchdog: SpeedPlay's service stops itself (onStop, 30 s after pause); restart it if its session is gone. */
    private void watchCarPlayMedia() {
        boolean want = false;
        try {
            want = Sys.isSystem() && Prefs.carplayTrack(this)
                    && Settings.Secure.getInt(getContentResolver(), CARPLAY_STATE, 0) != 0;
            if (!want) return; // updateCarPlayMedia stops the service
            if (!spSessionActive()) { startCarPlayMedia("restarted"); return; } // reschedules itself
        } catch (Throwable t) {
            Log.e(TAG, "carplay track: watchdog failed: " + t);
        }
        if (want) h.postDelayed(cpWatch, CP_WATCH_MS);
    }

    /** SpeedPlay's media session exists. Starting its service again would blank the title (onStartCommand resets it). */
    private boolean spSessionActive() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            for (MediaController c : msm.getActiveSessions(null)) if (SP.equals(c.getPackageName())) return true;
        } catch (Throwable t) {
            Log.e(TAG, "carplay track: sessions: " + t);
        }
        return false;
    }

    private void stopCarPlayMedia() {
        h.removeCallbacks(cpWatch);
        try {
            boolean r = stopService(new Intent().setComponent(SP_MEDIA));
            Log.e(TAG, "carplay track: stopService SpeedPlay media service, was running=" + r);
        } catch (Throwable t) {
            Log.e(TAG, "carplay track: stop failed: " + t);
        }
        spMedia = false;
    }

    private void updateWeb() {
        boolean want = Sys.isSystem() && Prefs.web(this);
        if (want && web == null) {
            WebAuth.INSTANCE.setPin(Prefs.pin(this));
            web = new WebPanel(webBackend(), WebAuth.INSTANCE);
            try {
                web.start(WEB_PORT);
                Log.i(TAG, "web panel listening on :" + WEB_PORT + " " + Sys.ips());
            } catch (Exception e) {
                Log.e(TAG, "web panel failed: " + e);
                web = null;
            }
        } else if (!want && web != null) {
            web.stop();
            web = null;
            Log.i(TAG, "web panel stopped");
        }
    }

    private WebPanel.Backend webBackend() {
        final Context c = getApplicationContext();
        return new WebPanel.Backend() {
            public Map<String, String> status() { return HelperService.status(c); }
            public boolean act(String a, String v) {
                boolean ok = actNow(a, v);
                MainActivity.changed(); // Set may be on screen: show the new choice
                return ok;
            }
            private boolean actNow(String a, String v) {
                Log.i(TAG, "web action " + a + ("type".equals(a) ? "" : "=" + v)); // never log typed text
                switch (a) {
                    case "mic": return Sys.setMic(Integer.parseInt(v));
                    case "reverse": setReverseMode(c, Integer.parseInt(v)); return true;
                    case "callvol": Prefs.get(c).edit().putBoolean(Prefs.CALL_VOLUME, "on".equals(v)).apply(); start(c); return true;
                    case "retrovol": setRetroPercent(c, Integer.parseInt(v)); return true; // whitelist: 0(auto)/100/75/50/25
                    case "home": return Sys.forceStop(c, Sys.HOME) && Sys.key(c, "home"); // reopen it now, also from CarPlay
                    case "adb": return Sys.setAdbWifi(c, "on".equals(v));
                    case "voice": return Sys.setWelcomeVoice(c, "on".equals(v));
                    case "cptrack": Prefs.get(c).edit().putBoolean(Prefs.CARPLAY_TRACK, "on".equals(v)).apply(); start(c); return true;
                    case "sky": case "hours": case "name": case "city": return sendToHome(c, a, v);
                    case "cityset": return sendToHome(c, "city", v);
                    case "key": return Sys.key(c, v);
                    case "type": // "Scrivi in auto": Tastiera CyberZec commits it into the focused field (result code 1 = typed)
                        c.sendOrderedBroadcast(new Intent("it.cyberzec.keyboard.TYPE").setPackage("it.cyberzec.keyboard")
                                .putExtra("text", v).putExtra("enter", true), HOME_PERM);
                        return sendToHome(c, "type", v); // the Home weather search has its own keyboard, so it takes the text directly
                    case "hotspot": Prefs.setHotspotSsid(c, "off".equals(v) ? "" : v); return true;
                    default: return false;
                }
            }
            public String[][] dynamic() {
                List<String> city = new ArrayList<>();
                city.add("city"); city.add(Lang.t("Città meteo", "Weather city")); city.add("gps"); city.add("GPS");
                for (String e : String.valueOf(homeStatus(c).get("recent")).split(";")) {
                    if (e.split("\\|").length == 3) { city.add(e); city.add(e.substring(0, e.indexOf('|'))); }
                }
                List<String> hs = new ArrayList<>();
                hs.add("hotspot"); hs.add(Lang.t("Hotspot automatico", "Automatic hotspot")); hs.add("off"); hs.add(Lang.t("Spento", "Off"));
                for (String s : savedSsids(c)) { hs.add(s); hs.add(s); }
                return new String[][]{city.toArray(new String[0]), hs.toArray(new String[0])};
            }
            public String current(String a) {
                Map<String, String> hm = homeStatus(c);
                if ("type".equals(a)) return "";
                if ("name".equals(a)) return hm.containsKey("name") ? hm.get("name") : "";
                String r = String.valueOf(hm.get("recent")); // fixed city = first recent one
                return "fixed".equals(hm.get("cityMode")) && r.contains("|") ? r.split(";")[0] : "";
            }
        };
    }

    /** Connected Wi-Fi name, null if not connected. */
    static String wifiSsid(Context c) {
        try {
            WifiInfo wi = ((WifiManager) c.getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();
            if (wi != null && wi.getNetworkId() != -1) return wi.getSSID().replaceAll("^\"|\"$", "");
        } catch (Throwable ignored) { }
        return null;
    }

    /** Status lines for the app screen and the web panel. */
    static Map<String, String> status(Context c) {
        Map<String, String> m = new LinkedHashMap<>();
        boolean sys = Sys.isSystem();
        m.put(Lang.t("Versione", "Version"), versionOf(c, c.getPackageName()) + (sys ? Lang.t(", variante di sistema", ", system build") : Lang.t(", variante utente", ", user build")));
        m.put(Lang.t("Versione Home", "Home version"), versionOf(c, Sys.HOME));
        String np = nowPlaying(c);
        m.put(Lang.t("In riproduzione", "Now playing"), np != null ? np : "—");
        Map<String, String> hm = homeStatus(c);
        m.put(Lang.t("Meteo", "Weather"), hm.isEmpty() ? Lang.t("Home non raggiungibile", "Home not reachable")
                : hm.get("city") + " · " + hm.get("temp") + " · " + hm.get("text"));
        String park = Prefs.get(c).getString(Prefs.PARK, null); // ours: recorded even when the Home is not on screen
        if (park == null) park = hm.get("park"); // "lat,lon,epochMs": exact title/value parsed by the iPhone app
        if (park != null && park.matches("^-?\\d+\\.\\d+,-?\\d+\\.\\d+,\\d+$")) m.put("Parcheggio", park);
        String ssid = wifiSsid(c);
        m.put("Wi-Fi", ssid != null ? ssid : Lang.t("non connesso", "not connected"));
        String hsid = Prefs.hotspotSsid(c);
        m.put(Lang.t("Hotspot automatico", "Automatic hotspot"), hsid.isEmpty() ? Lang.t("spento", "off") : hsid);
        String why = unsafeReason();
        m.put(Lang.t("Retromarcia", "Reverse"), why == null ? Lang.t("compatibile", "compatible") : Lang.t("disattivata per sicurezza (", "disabled for safety (") + why + ")");
        m.put(Lang.t("Musica in retromarcia", "Music in reverse"), modeNames()[Prefs.reverseMode(c)] + (ReverseState.INSTANCE.isDucked() ? Lang.t(" (abbassata ora)", " (lowered now)") : ""));
        int rp = Prefs.retroPercent(c); // exact title of the command: the iPhone app lights up the active choice from it
        m.put(Lang.t("Volume in retromarcia", "Volume in reverse"), rp == RetroVolume.AUTO ? "Auto" : rp + "%");
        m.put(Lang.t("Ultimo evento retro", "Last reverse event"), ReverseState.INSTANCE.lastEvent);
        m.put(Lang.t("Chiamata CarPlay attiva", "CarPlay call active"), CallState.INSTANCE.isActive(SystemClock.elapsedRealtime()) ? Lang.t("sì", "yes") : "no");
        m.put(Lang.t("Volume chiamata dal volante", "Call volume from steering wheel"), Prefs.callVolume(c) ? Lang.t("attivo", "on") : Lang.t("spento", "off"));
        m.put("Log DFSKHelper", Log.isLoggable(TAG, Log.INFO) ? Lang.t("visibili (livello INFO)", "visible (INFO level)")
                : Lang.t("filtrati dal sistema: adb shell setprop persist.log.tag.DFSKHelper I", "filtered by the system: adb shell setprop persist.log.tag.DFSKHelper I"));
        if (sys) {
            int mic = Sys.micLevel();
            m.put(Lang.t("Microfono CarPlay", "CarPlay microphone"), mic > 0 ? Integer.toString(mic) : Lang.t("predefinito", "default"));
            int port = Sys.adbPort();
            m.put("ADB Wi-Fi", port > 0 ? Lang.t("attivo (porta ", "on (port ") + port + ")" : Lang.t("spento", "off"));
            m.put(Lang.t("Voce di benvenuto", "Welcome voice"), Sys.welcomeVoice(c) ? Lang.t("attiva", "on") : Lang.t("spenta", "off"));
            m.put(Lang.t("Brano CarPlay nella Home", "CarPlay track on Home"), Prefs.carplayTrack(c) ? Lang.t("attivo", "on") : Lang.t("spento", "off"));
            String ov = Sys.overlays();
            if (ov != null) m.put(Lang.t("Traduzioni (overlay)", "Translations (overlays)"), ov);
            m.put(Lang.t("Indirizzo IP", "IP address"), Sys.ips());
        }
        String acc = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        m.put(Lang.t("Accessibilità (facoltativa)", "Accessibility (optional)"), acc != null && acc.contains("it.dfsk.helper/")
                ? Lang.t("attiva", "on") : Lang.t("non attiva", "off"));
        return m;
    }

    @Override public void onCreate() {
        super.onCreate();
        CallState.INSTANCE.reset();
        startForeground(1, buildNotification());
        // This build drops liblog messages below ERROR globally (field logs: no INFO from any Java app).
        // A per-tag property overrides that; the system variant may set it itself.
        if (Sys.isSystem() && !Log.isLoggable(TAG, Log.INFO)) Sys.setProp("log.tag." + TAG, "I");
        if (!Log.isLoggable(TAG, Log.INFO))
            Log.e(TAG, "INFO logs are filtered on this unit; run: adb shell setprop persist.log.tag.DFSKHelper I");
        IntentFilter f = new IntentFilter();
        f.addAction(BACKCAR_START);
        f.addAction(BACKCAR_STOP);
        f.addAction(SP);
        registerReceiver(rx, f);
        try {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(CARPLAY_STATE), false, carplayObs);
        } catch (Throwable t) {
            Log.e(TAG, "carplay track: observer failed: " + t);
        }
        try { // parking spot: the last GPS fix before the car is switched off, also while CarPlay is on screen
            if (Sys.isSystem()) ((LocationManager) getSystemService(LOCATION_SERVICE)).requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000, 30, park);
        } catch (Throwable t) {
            Log.e(TAG, "parking: GPS not available: " + t);
        }
        retroStop(); // service restarted after dying mid-reverse: put the normal level back
        if (Sys.isSystem() && Prefs.retroPercent(this) != RetroVolume.AUTO) Sys.setBackcarMusic(100); // policy resets at boot
        Log.i(TAG, "service created, system=" + Sys.isSystem() + " mode=" + modeNames()[Prefs.reverseMode(this)]);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("boot", false)) Log.i(TAG, "BOOT_COMPLETED");
        scheduleArm();
        hotspotCheck();
        updateSession();
        checkDuck();
        updateWeb();
        updateCarPlayMedia(false);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        unregisterReceiver(rx);
        getContentResolver().unregisterContentObserver(carplayObs);
        ((LocationManager) getSystemService(LOCATION_SERVICE)).removeUpdates(park);
        if (Boolean.TRUE.equals(spMedia)) stopCarPlayMedia();
        h.removeCallbacksAndMessages(null);
        abandonDuck();
        if (session != null) session.release();
        if (web != null) web.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("helper", "CyberZec Set", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            b = new Notification.Builder(this, "helper");
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        return b.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(Lang.t("CyberZec Set attivo", "CyberZec Set running"))
                .setContentText(Lang.t("Musica in retromarcia e volume chiamata", "Music in reverse and call volume"))
                .setContentIntent(pi).setOngoing(true).build();
    }
}
