package com.vorta.unified;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Direct-Boot-aware boot and external safe-volume check receiver.
 */
public class VortaBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        VortaVolumeGuard.onBootOrCheck(context, intent.getAction());
    }
}
