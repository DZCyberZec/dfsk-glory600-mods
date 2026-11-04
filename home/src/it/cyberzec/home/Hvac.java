package it.cyberzec.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Climate through the car's bw.car library, loaded by reflection (it exists only on the car).
 * Same protocol as BWLauncher3 BwAirConditionCardView: virtual key press on HVAC_KEYEVENT_INPUT
 * ({key,1} then {key,0} after 300 ms). IDs and areas from scansione_2026-11-01/scan_car_service_vhal.txt.
 */
final class Hvac {
    static final String TAG = "CyberZecHvac";
    static final int KEYEVENT = 557909278;          // HVAC_KEYEVENT_INPUT, area 0, Integer[]{key,state}
    static final int KEY_AC = 0, KEY_FAN_UP = 3, KEY_FAN_DOWN = 4, KEY_TEMP_UP = 7, KEY_TEMP_DOWN = 8,
            KEY_MODE = 9, KEY_REAR_DEFROST = 10, KEY_OFF = 11, KEY_DEFROST = 13, KEY_RECIRC = 14;
    static final int MODE = 356517121;                      // area 5: 0 viso, 1 viso+piedi, 2 piedi, 3 piedi+parabrezza
    static final int FAN_SPEED = 356517120, AREA_ROW = 5;   // 0..8
    static final int POWER_ON = 354419984, AC_ON = 354419973;  // area 5
    static final int RECIRC_ON = 354419976, AREA_RECIRC = 117;
    static final int DEFROST = 320865540, AREA_FRONT_WINDOW = 1, AREA_REAR_WINDOW = 2;
    static final int TEMP = 358614275, AREA_TEMP = 4;       // level scale with offline_hvac_type=0

    private final Handler main = new Handler(Looper.getMainLooper());
    private Object car, prop;
    // Emulator only (no bw.car there): simulated climate for demos/videos. Never active on the car.
    private boolean demo, dPower, dAc, dRecirc, dFront, dRear;
    private int dFan = 3, dMode = 0;
    private float dTemp = 28f;

    void connect(Context ctx, final Runnable onChange) {
        try {
            final Class<?> carCls = Class.forName("bw.car.Car");
            ServiceConnection sc = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName n, IBinder b) {
                    try {
                        prop = carCls.getMethod("getCarManager", String.class).invoke(car, "property");
                    } catch (Throwable t) {
                        Log.w(TAG, "getCarManager(property) fallito", t);
                    }
                    onChange.run();
                }
                @Override public void onServiceDisconnected(ComponentName n) {
                    prop = null;
                    onChange.run();
                }
            };
            car = carCls.getMethod("createCar", Context.class, ServiceConnection.class).invoke(null, ctx, sc);
            if (car != null) carCls.getMethod("connect").invoke(car);
            else Log.i(TAG, "createCar null: niente bw.car (emulatore?)");
        } catch (Throwable t) {
            Log.i(TAG, "bw.car non disponibile: " + t);
            demo = "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
            if (demo) onChange.run();
        }
    }

    void disconnect() {
        try {
            if (car != null) car.getClass().getMethod("disconnect").invoke(car);
        } catch (Throwable ignored) {
        }
        car = prop = null;
    }

    boolean ready() {
        return prop != null || demo;
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = prop.getClass().getMethod(name, types);
        return m.invoke(prop, args);
    }

    /** -1 when not readable. */
    int getInt(int id, int area) {
        if (demo) return id == FAN_SPEED ? dFan : id == MODE ? dMode : -1;
        if (prop == null) return -1;
        try {
            return (Integer) call("getIntProperty", new Class<?>[]{int.class, int.class}, id, area);
        } catch (Throwable t) {
            return -1;
        }
    }

    boolean getBool(int id, int area) {
        if (demo) return id == POWER_ON ? dPower : id == AC_ON ? dAc : id == RECIRC_ON ? dRecirc
                : id == DEFROST ? (area == AREA_FRONT_WINDOW ? dFront : dRear) : false;
        if (prop == null) return false;
        try {
            return (Boolean) call("getBooleanProperty", new Class<?>[]{int.class, int.class}, id, area);
        } catch (Throwable t) {
            return false;
        }
    }

    /** NaN when not readable. */
    float getFloat(int id, int area) {
        if (demo) return id == TEMP ? dTemp : Float.NaN;
        if (prop == null) return Float.NaN;
        try {
            Object v = call("getProperty", new Class<?>[]{Class.class, int.class, int.class}, Float.class, id, area);
            Object f = v.getClass().getMethod("getValue").invoke(v);
            return (Float) f;
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    private void sendKey(int key, int state) {
        Log.e(TAG, "tasto " + key + " stato " + state);
        try {
            call("setProperty", new Class<?>[]{Class.class, int.class, int.class, Object.class},
                    Integer[].class, KEYEVENT, 0, new Integer[]{key, state});
        } catch (Throwable t) {
            Log.w(TAG, "setProperty key " + key + "/" + state + " fallito", t);
        }
    }

    /** Air direction, as com.bw.ac AirManager.sendWindModeProperty: {9, 2m+1} then {9, 2m} after 300 ms. */
    boolean setMode(final int m) {
        if (demo) { dMode = m; return true; }
        if (prop == null) return false;
        sendKey(KEY_MODE, 2 * m + 1);
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (prop != null) sendKey(KEY_MODE, 2 * m);
            }
        }, 300);
        return true;
    }

    /** One virtual key press, like the original card. False when the car service is not there. */
    boolean press(final int key) {
        if (demo) { demoKey(key); return true; }
        if (prop == null) return false;
        sendKey(key, 1);
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (prop != null) sendKey(key, 0);
            }
        }, 300);
        return true;
    }

    private void demoKey(int key) {
        if (key == KEY_OFF) { dPower = false; return; }
        boolean wasOn = dPower;
        dPower = true;
        if (key == KEY_AC) dAc = !wasOn || !dAc;
        else if (key == KEY_FAN_UP) dFan = Math.min(8, dFan + 1);
        else if (key == KEY_FAN_DOWN) dFan = Math.max(1, dFan - 1);
        else if (key == KEY_TEMP_UP) dTemp = Math.min(32f, dTemp + 0.5f);
        else if (key == KEY_TEMP_DOWN) dTemp = Math.max(18f, dTemp - 0.5f);
        else if (key == KEY_RECIRC) dRecirc = !dRecirc;
        else if (key == KEY_REAR_DEFROST) dRear = !dRear;
        else if (key == KEY_DEFROST && (dFront = !dFront)) dFan = 5;
    }
}
