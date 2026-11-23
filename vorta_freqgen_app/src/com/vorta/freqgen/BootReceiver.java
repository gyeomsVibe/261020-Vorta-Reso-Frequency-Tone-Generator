package com.vorta.freqgen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "FreqGenBootRcvr";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        if (FreqGenVolumeHelper.isPersonalDevice() || FreqGenVolumeHelper.isDedicatedGeneratorDevice()) return;
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            Log.i(TAG, "Event " + action + " -> Enforcing safe volume bypass flags");
            FreqGenVolumeHelper.enforceSystemFlags(context);
        }
    }
}
