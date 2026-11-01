package it.dfsk.helper;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Secondary method (optional, enabled via adb). On this car the wheel keys are injected events and are
 * expected NOT to reach onKeyEvent; HelperService's MediaSession is the main path. Kept for physical keys.
 * Remaps volume keys to STREAM_VOICE_CALL only while a CarPlay call is active; otherwise never consumes keys. */
public class VolumeService extends AccessibilityService {
    static final String TAG = "DFSKHelper.Vol";
    private int consumingKey = -1; // key whose DOWN we consumed, so we also consume its UP (and only that)

    @Override protected void onServiceConnected() {
        Log.i(TAG, "accessibility service connected");
        HelperService.start(this); // the call-state receiver lives in HelperService
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean inCall = CallState.INSTANCE.isActive(SystemClock.elapsedRealtime());
        Log.d(TAG, "onKeyEvent code=" + code + " action=" + e.getAction() + " inCall=" + inCall
                + " deviceId=" + e.getDeviceId() + " flags=0x" + Integer.toHexString(e.getFlags()));
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (!inCall || !Prefs.callVolume(this)) { consumingKey = -1; return false; }
            consumingKey = code;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    code == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
            Log.i(TAG, "VOICE_CALL volume -> " + am.getStreamVolume(AudioManager.STREAM_VOICE_CALL));
            return true;
        }
        if (code == consumingKey) {
            if (e.getAction() == KeyEvent.ACTION_UP) consumingKey = -1;
            return true;
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
