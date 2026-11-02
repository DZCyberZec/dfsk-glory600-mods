package it.cyberzec.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/** Settings sent by CyberZec Set (it.cyberzec.home.SET, signature permission it.cyberzec.permission.SETTINGS). */
public final class SettingsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String k = i.getStringExtra("key"), v = i.getStringExtra("value");
        if (k == null || v == null) return;
        SharedPreferences home = c.getSharedPreferences("home", Context.MODE_PRIVATE),
                meteo = c.getSharedPreferences("meteo", Context.MODE_PRIVATE);
        if (k.equals("type")) {   // typed text: goes into the open city search only, never saved or logged
            if (v.matches("[^\\p{Cntrl}]{1,200}") && HomeActivity.live != null) HomeActivity.live.typeFromSet(v);
            return;
        }
        switch (k) {
            case "sky": case "hours":
                if (!v.equals("on") && !v.equals("off")) return;
                home.edit().putBoolean(k, v.equals("on")).apply();
                break;
            case "name":
                if (!Logic.validName(v)) return;
                home.edit().putString("name", v.trim()).apply();
                break;
            case "city":
                if (v.equals("gps")) {
                    HomeActivity.cityFromGps(meteo);
                    meteo.edit().putLong("at", 0).apply();
                } else {
                    double[] ll = Logic.cityValue(v);
                    if (ll == null) return;
                    Weather.Place p = new Weather.Place();
                    p.name = p.label = v.substring(0, v.indexOf('|')).trim();
                    p.lat = ll[0];
                    p.lon = ll[1];
                    HomeActivity.saveCity(meteo, p);
                }
                break;
            default:
                return;
        }
        Log.i(HomeActivity.TAG, "impostazione da CyberZec Set: " + k + "=" + v);
        if (HomeActivity.live != null) HomeActivity.live.settingsChanged(k);   // onReceive runs on the main thread
    }
}
