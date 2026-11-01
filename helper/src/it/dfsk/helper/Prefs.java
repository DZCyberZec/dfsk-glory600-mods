package it.dfsk.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

final class Prefs {
    static final String REVERSE_MODE = "reverse_mode", CALL_VOLUME = "call_volume", HOME_FIX = "home_fix",
            WEB = "web_panel", PIN = "web_pin";

    static SharedPreferences get(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    /** ReverseState.PAUSE/ATTENUATE/LOWER. v1.0's boolean "reverse_music" maps to ATTENUATE (on) / PAUSE (off). */
    static int reverseMode(Context c) {
        SharedPreferences p = get(c);
        return p.getInt(REVERSE_MODE, p.getBoolean("reverse_music", true) ? ReverseState.ATTENUATE : ReverseState.PAUSE);
    }

    static boolean callVolume(Context c) { return get(c).getBoolean(CALL_VOLUME, true); }
    static boolean homeFix(Context c) { return get(c).getBoolean(HOME_FIX, true); }
    static boolean web(Context c) { return get(c).getBoolean(WEB, false); }

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
