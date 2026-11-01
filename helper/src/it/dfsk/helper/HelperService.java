package it.dfsk.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
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
    static final long HOME_FIX_DELAY_MS = 20_000, HOME_FIX_RETRY_MS = 10_000;
    static final int WEB_PORT = 8091;
    static String[] modeNames() {
        return new String[]{Lang.t("Pausa", "Pause"), Lang.t("Attenuata", "Ducked"), Lang.t("Più bassa", "Lower")};
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private AudioFocusRequest duckRequest;
    private WebPanel web;
    private int homeFixRetries;
    private final Runnable arm = new Runnable() { public void run() { armReverse(HelperService.this); } };
    private final Runnable sessionCheck = new Runnable() { public void run() { updateSession(); } };
    private final Runnable duckCheck = new Runnable() { public void run() { checkDuck(); } };
    private final Runnable homeFix = new Runnable() { public void run() { restartHomeAfterBoot(); } };

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
            } else if (BACKCAR_STOP.equals(a)) {
                ReverseState.INSTANCE.onStop(now);
                event(Lang.t("fine retromarcia", "reverse end"));
                h.postDelayed(duckCheck, ReverseState.STOP_DEBOUNCE_MS + 50);
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
                if ("CONNECTED".equals(status) || "SERVICE_READY".equals(status)) scheduleArm();
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

    /** Launcher starts before the RRO overlays are applied at boot; one force-stop makes it reload them. */
    private void restartHomeAfterBoot() {
        if (!Sys.isSystem() || !Prefs.homeFix(this)) return;
        if (ReverseState.INSTANCE.isReversing() && homeFixRetries++ < 6) {
            Log.i(TAG, "home restart postponed: reversing");
            h.postDelayed(homeFix, HOME_FIX_RETRY_MS);
            return;
        }
        Log.i(TAG, "boot: restarting launcher to apply overlays");
        Sys.forceStop(this, Sys.LAUNCHER);
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
                Log.i(TAG, "web action " + a + "=" + v);
                switch (a) {
                    case "mic": return Sys.setMic(Integer.parseInt(v));
                    case "reverse": setReverseMode(c, Integer.parseInt(v)); return true;
                    case "callvol": Prefs.get(c).edit().putBoolean(Prefs.CALL_VOLUME, "on".equals(v)).apply(); start(c); return true;
                    case "home": return Sys.forceStop(c, Sys.LAUNCHER);
                    case "adb": return Sys.setAdbWifi(c, "on".equals(v));
                    case "voice": return Sys.setWelcomeVoice(c, "on".equals(v));
                    default: return false;
                }
            }
        };
    }

    /** Status lines for the app screen and the web panel. */
    static Map<String, String> status(Context c) {
        Map<String, String> m = new LinkedHashMap<>();
        String ver = "?";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            ver = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception ignored) { }
        boolean sys = Sys.isSystem();
        m.put(Lang.t("Versione", "Version"), ver + (sys ? Lang.t(", variante di sistema", ", system build") : Lang.t(", variante utente", ", user build")));
        String why = unsafeReason();
        m.put(Lang.t("Retromarcia", "Reverse"), why == null ? Lang.t("compatibile", "compatible") : Lang.t("disattivata per sicurezza (", "disabled for safety (") + why + ")");
        m.put(Lang.t("Musica in retromarcia", "Music in reverse"), modeNames()[Prefs.reverseMode(c)] + (ReverseState.INSTANCE.isDucked() ? Lang.t(" (abbassata ora)", " (lowered now)") : ""));
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
        Log.i(TAG, "service created, system=" + Sys.isSystem() + " mode=" + modeNames()[Prefs.reverseMode(this)]);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("boot", false) && Sys.isSystem()) {
            Log.i(TAG, "BOOT_COMPLETED");
            h.removeCallbacks(homeFix);
            homeFixRetries = 0;
            h.postDelayed(homeFix, HOME_FIX_DELAY_MS);
        }
        scheduleArm();
        updateSession();
        checkDuck();
        updateWeb();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        unregisterReceiver(rx);
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
            NotificationChannel ch = new NotificationChannel("helper", "DFSK Helper", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            b = new Notification.Builder(this, "helper");
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        return b.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(Lang.t("DFSK Helper attivo", "DFSK Helper running"))
                .setContentText(Lang.t("Musica in retromarcia e volume chiamata", "Music in reverse and call volume"))
                .setContentIntent(pi).setOngoing(true).build();
    }
}
