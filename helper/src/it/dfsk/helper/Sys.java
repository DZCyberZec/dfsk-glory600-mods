package it.dfsk.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Property access and the system-uid-only features (platform-signed variant). Hidden APIs via reflection. */
final class Sys {
    static final String TAG = HelperService.TAG;
    static final String LAUNCHER = "com.android.launcher3";
    static final String MIC_DEFAULT = "persist.suding.mic.gainlevel.default", MIC_NOW = "sys.suding.speedplay.audio.apm.agc.gain";
    static final String ADB_PORT = "service.adb.tcp.port"; // same mechanism as BWEngineerMode DialogUtils "enable lan adb"

    static boolean isSystem() { return Process.myUid() == Process.SYSTEM_UID; }

    static String prop(String key) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : (String) v;
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean setProp(String key, String value) {
        try {
            Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class).invoke(null, key, value);
            return value.equals(prop(key));
        } catch (Throwable t) {
            Log.e(TAG, "setprop " + key + " failed: " + t);
            return false;
        }
    }

    /** ActivityManager.forceStopPackage (hidden, needs FORCE_STOP_PACKAGES: granted to uid 1000). */
    static boolean forceStop(Context c, String pkg) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.class.getMethod("forceStopPackage", String.class).invoke(am, pkg);
            Log.i(TAG, "force-stopped " + pkg);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "forceStopPackage " + pkg + " failed: " + t);
            return false;
        }
    }

    static int micLevel() {
        try { return Integer.parseInt(prop(MIC_NOW).trim()); } catch (NumberFormatException e) { return -1; }
    }

    /** Persistent default (used by SpeedPlay at start if its own pref is absent) + the live value. */
    static boolean setMic(int level) {
        if (level < 1 || level > 3) return false;
        String v = Integer.toString(level);
        boolean ok = setProp(MIC_DEFAULT, v) & setProp(MIC_NOW, v);
        Log.i(TAG, "mic gain level -> " + v + (ok ? "" : " (FAILED)"));
        return ok;
    }

    static int adbPort() {
        for (String k : new String[]{ADB_PORT, "persist.adb.tcp.port"}) {
            try { int p = Integer.parseInt(prop(k).trim()); if (p > 0) return p; } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    /** Mirrors BWEngineerMode: set service.adb.tcp.port (5555 / -1), then toggle adb_enabled 0 -> 1 to restart adbd. Not persistent. */
    static boolean setAdbWifi(Context c, boolean on) {
        boolean ok = setProp(ADB_PORT, on ? "5555" : "-1");
        try {
            Settings.Global.putInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            Settings.Global.putInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
        } catch (Throwable t) {
            Log.e(TAG, "adb_enabled toggle failed: " + t);
            ok = false;
        }
        Log.i(TAG, "ADB Wi-Fi -> " + (on ? "on (5555)" : "off") + (ok ? "" : " (FAILED)"));
        return ok;
    }

    /** Voce "Hi, I'm Glory" all'avvio: Global bw_welcome_voice_switch letto da launcher3 DFSK_F517_WarningService (assente = 1 = attiva). */
    static final String WELCOME_VOICE = "bw_welcome_voice_switch";

    static boolean welcomeVoice(Context c) { return Settings.Global.getInt(c.getContentResolver(), WELCOME_VOICE, 1) == 1; }

    static boolean setWelcomeVoice(Context c, boolean on) {
        try {
            Settings.Global.putInt(c.getContentResolver(), WELCOME_VOICE, on ? 1 : 0);
            Log.i(TAG, "welcome voice -> " + (on ? "on" : "off"));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "welcome voice failed: " + t);
            return false;
        }
    }

    /** "it.dfsk.overlay.*" enabled/total via IOverlayManager.getAllOverlays(0); null if not accessible. */
    static String overlays() {
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "overlay");
            Object om = Class.forName("android.content.om.IOverlayManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            Map<?, ?> all = (Map<?, ?>) om.getClass().getMethod("getAllOverlays", int.class).invoke(om, 0);
            int on = 0, tot = 0;
            for (Object list : all.values()) for (Object oi : (List<?>) list) {
                String pkg = (String) oi.getClass().getField("packageName").get(oi);
                if (!pkg.startsWith("it.dfsk.overlay.")) continue;
                tot++;
                if ((Boolean) oi.getClass().getMethod("isEnabled").invoke(oi)) on++;
            }
            return on + Lang.t(" attivi su ", " on of ") + tot;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Car LAN IPv4 for the QR link: wlan0 first (Wi-Fi client on the iPhone hotspot), else any private IPv4; null if none. */
    static String lanIp() {
        String any = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !WebAuth.isLan(a.getAddress())) continue;
                    if ("wlan0".equals(ni.getName())) return a.getHostAddress();
                    if (any == null) any = a.getHostAddress();
                }
            }
        } catch (Throwable ignored) { }
        return any;
    }

    static String ips() {
        StringBuilder sb = new StringBuilder();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && WebAuth.isLan(a.getAddress()))
                        sb.append(sb.length() > 0 ? ", " : "").append(a.getHostAddress()).append(" (").append(ni.getName()).append(')');
            }
        } catch (Throwable ignored) { }
        return sb.length() == 0 ? Lang.t("nessuna rete", "no network") : sb.toString();
    }
}
