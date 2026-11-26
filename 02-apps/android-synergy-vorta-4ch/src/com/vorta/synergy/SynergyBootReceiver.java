package com.vorta.synergy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Direct-Boot-aware boot and safe-volume check receiver.
 */
public class SynergyBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        SynergyVolumeGuard.onBootOrCheck(context, intent.getAction());
    }
}
