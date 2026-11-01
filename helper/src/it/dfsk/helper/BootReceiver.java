package it.dfsk.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        HelperService.start(c, Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()));
    }
}
