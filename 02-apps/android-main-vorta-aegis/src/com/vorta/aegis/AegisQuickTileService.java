package com.vorta.aegis;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Android 7.0+ (API 24+) Quick Settings Tile for 1-tap Vorta Aegis playback toggle.
 */
public class AegisQuickTileService extends TileService {

    public static void requestTileSync(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 24) {
            return;
        }
        try {
            TileService.requestListeningState(
                    context.getApplicationContext(),
                    new ComponentName(context.getApplicationContext(), AegisQuickTileService.class));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean running = AegisAudioService.isLiveRunning();
        if (running) {
            Intent stopIntent = new Intent(this, AegisAudioService.class);
            stopIntent.setAction(AegisAudioService.ACTION_STOP);
            try {
                startService(stopIntent);
            } catch (Throwable ignored) {}
        } else {
            Intent playIntent = new Intent(this, AegisAudioService.class);
            playIntent.setAction(AegisAudioService.ACTION_TOGGLE_PLAY);
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(playIntent);
                } else {
                    startService(playIntent);
                }
            } catch (Throwable t) {
                launchActivityFallback();
            }
        }
        updateTileState();
    }

    private void launchActivityFallback() {
        try {
            Intent activityIntent = new Intent(this, MainActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activityIntent.putExtra(AegisAudioService.EXTRA_AUTO_RESUME_LAST, true);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pi = PendingIntent.getActivity(
                        this, 701, activityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pi);
            } else {
                startActivityAndCollapse(activityIntent);
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void updateTileState() {
        if (Build.VERSION.SDK_INT < 24) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = AegisAudioService.isLiveRunning();
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("보르타 이지스");
        if (Build.VERSION.SDK_INT >= 29) {
            String title = AegisAudioService.getLivePresetTitle();
            tile.setSubtitle(running && title != null && !title.isEmpty() ? title : "탭하여 바로 재생");
        }
        try {
            tile.updateTile();
        } catch (Throwable ignored) {}
    }
}
