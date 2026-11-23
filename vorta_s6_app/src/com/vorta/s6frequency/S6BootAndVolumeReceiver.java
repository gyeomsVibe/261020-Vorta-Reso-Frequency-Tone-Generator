package com.vorta.s6frequency;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class S6BootAndVolumeReceiver extends BroadcastReceiver {
    private static final String TAG = "S6BootRcvr";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        if (!S6VolumeHelper.isTargetDevice()) return;
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "com.vorta.s6frequency.ACTION_ENFORCE_VOLUME".equals(action)
                || S6VolumeHelper.ACTION_SAFE_VOLUME_CHECK.equals(action)) {
            Log.i(TAG, "Event " + action + " -> Running S6 safe-volume check (free volume preserved)");
            S6VolumeHelper.onBootOrCheck(context, action);
        }
    }
}
