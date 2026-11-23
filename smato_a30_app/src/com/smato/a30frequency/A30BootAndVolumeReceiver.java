package com.smato.a30frequency;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class A30BootAndVolumeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if ("com.smato.a30frequency.ACTION_RESTORE_PERSONAL_AUDIO".equals(action)) {
            if (A30VolumeHelper.isPersonalDevice()) {
                try {
                    android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.setParameters("g_effect_dolby_enable=1");
                    }
                } catch (Throwable ignored) {}
            }
            return;
        }
        if (!A30VolumeHelper.isA30Device()) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "com.smato.a30frequency.ACTION_ENFORCE_VOLUME".equals(action)) {
            A30VolumeHelper.enforceSystemFlags(context);
        }
    }
}
