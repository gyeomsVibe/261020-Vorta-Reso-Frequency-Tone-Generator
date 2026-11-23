package com.vorta.s7frequency;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class S7BootAndVolumeReceiver extends BroadcastReceiver {
    private static final String TAG = "S7BootRcvr";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        if (!S7VolumeHelper.isTargetDevice()) return;
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "com.vorta.s7frequency.ACTION_ENFORCE_VOLUME".equals(action)
                || S7VolumeHelper.ACTION_SAFE_VOLUME_CHECK.equals(action)) {
            Log.i(TAG, "Event " + action + " -> Running S7 safe-volume check (free volume preserved)");
            S7VolumeHelper.onBootOrCheck(context, action);
        }
    }
}
