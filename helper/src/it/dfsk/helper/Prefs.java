package it.dfsk.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

final class Prefs {
    static final String REVERSE_MODE = "reverse_mode", CALL_VOLUME = "call_volume",
            WEB = "web_panel", PIN = "web_pin", CARPLAY_TRACK = "carplay_track", HOTSPOT_SSID = "hotspot_ssid",
            RETRO_MODE = "retro_mode", RETRO_NORMAL = "retro_normal",
            PARK = "park"; // "lat,lon,epochMs" of the last GPS fix (HelperService.park)
    @Deprecated static final String HOME_FIX = "home_fix"; // unused since 1.6 (BW launcher restart removed); kept so old UI code compiles

    static SharedPreferences get(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    /** ReverseState.PAUSE/ATTENUATE/LOWER. v1.0's boolean "reverse_music" maps to ATTENUATE (on) / PAUSE (off). */
    static int reverseMode(Context c) {
        SharedPreferences p = get(c);
        return p.getInt(REVERSE_MODE, p.getBoolean("reverse_music", true) ? ReverseState.ATTENUATE : ReverseState.PAUSE);
    }

    static boolean callVolume(Context c) { return get(c).getBoolean(CALL_VOLUME, true); }
    /** Music level while reversing, % of normal (RetroVolume.PERCENTS; 100 = unchanged). */
    static int retroPercent(Context c) { return get(c).getInt(RETRO_MODE, RetroVolume.AUTO); }
    static boolean web(Context c) { return get(c).getBoolean(WEB, false); }
    /** Only used by the system variant (starting another app's non-exported service needs uid system). */
    static boolean carplayTrack(Context c) { return get(c).getBoolean(CARPLAY_TRACK, true); }

    /** SSID the service reconnects to every 30 s (HelperService.hotspotCheck); "" = off. */
    static String hotspotSsid(Context c) { return get(c).getString(HOTSPOT_SSID, ""); }

    static void setHotspotSsid(Context c, String ssid) {
        get(c).edit().putString(HOTSPOT_SSID, ssid == null ? "" : ssid).apply();
        HelperService.start(c); // check now
    }

    static String pin(Context c) {
        String p = get(c).getString(PIN, null);
        return p != null ? p : newPin(c);
    }

    /** New random PIN; logs out every web session. */
    static String newPin(Context c) {
        String p = WebAuth.newPin(new SecureRandom());
        get(c).edit().putString(PIN, p).apply();
        WebAuth.INSTANCE.setPin(p);
        return p;
    }
}
