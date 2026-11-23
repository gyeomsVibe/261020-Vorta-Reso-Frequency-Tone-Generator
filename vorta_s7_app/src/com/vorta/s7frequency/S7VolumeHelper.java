package com.vorta.s7frequency;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Complete Free-Volume + Android 8.0 Oreo Safe-Volume Neutralization Guard for Galaxy S7
 * (SM-G930/G935, Android 8.0 API 26).
 *
 * Verified against live S7 services.vdex AudioService bytecode:
 *  1. ZERO 85% (13/15) hard-lock: sTargetVolumeStep starts as -1 (uninitialized) and tracks
 *     user's wired manual choice (0..15) in Device-Protected SharedPreferences ("wired_target_vol").
 *     Speaker volume never overwrites wired_target_vol.
 *  2. Screen-off instant clamp detection on a dedicated HandlerThread ("S7VolGuardThread"):
 *     - Layer 1A: Non-reentrant ContentObserver on volume_music_headset, volume_music_headphone,
 *       audio_safe_volume_state (catches silent 20h enforceSafeMediaVolume + MSG_PERSIST_VOLUME
 *       within ~650ms while screen is OFF and Keyguard locked).
 *     - Layer 1B: 30s lightweight failsafe heartbeat (1 Binder read / 30s, zero Settings writes
 *       in steady state).
 *  3. Android 8.0 Lockscreen-Piercing Auto-Unlock:
 *     - Because ControllerService was removed in Android 8.0 (enforceVolumeController requires
 *       STATUS_BAR_SERVICE), SystemUI's SafetyWarningDialog (SystemUIDialog with
 *       FLAG_SHOW_WHEN_LOCKED + TYPE_VOLUME_OVERLAY) is the unrooted call path to
 *       AudioService.disableSafeMediaVolume().
 *     - Acquires SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP (NO ON_AFTER_RELEASE), waits
 *       until PowerManager.isInteractive() + 250ms settle, triggers FLAG_SHOW_UI |
 *       FLAG_SHOW_UI_WARNINGS + S7AccessibilityService active getWindows() scan, immediately
 *       releases WakeLock on confirm, and verifies unlock (up to 3 fast retries, plus 30s
 *       failsafe fallback).
 *  4. Locks pure stereo DSP (mono_audio=0, master_mono=0, SoundAlive/effects off) without
 *     invalid reloadAudioSettings() or setStreamVolumeForDevice() Binder calls.
 */
public final class S7VolumeHelper {
    private static final String TAG = "S7VolumeHelper";

    public static final int LEVELING_TARGET_STEP = 13;   // legacy constant retained for binary compat; NEVER forced
    public static final int MAX_VOLUME_STEP = 15;
    public static final int SAFE_CLAMP_STEP = 9;         // Samsung mSafeMediaVolumeIndex 90 / 10
    public static final int SAFE_VOLUME_CLAMP_MAX_STEP = 10;

    public static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
    public static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
    public static final String ACTION_SAFE_VOLUME_CHECK = "com.vorta.s7frequency.ACTION_SAFE_VOLUME_CHECK";

    private static final int FLAG_SHOW_UI_WARNINGS = 1 << 10; // 1024: triggers SystemUI safe dialog
    private static final int TYPE_USB_HEADSET_API26 = 22;
    private static final int TYPE_AUX_LINE_API21 = 19;

    private static final String PREF_NAME = "vorta_s7_vol_prefs";
    private static final String KEY_WIRED_TARGET_VOL = "wired_target_vol";
    private static final String KEY_LEGACY_USER_TARGET_VOL = "user_target_vol";

    private static final long OWN_CHANGE_WINDOW_MS = 1500L;
    private static final long SELF_WRITE_SUPPRESS_MS = 1600L;
    private static final long OBSERVER_DEBOUNCE_MS = 150L;
    private static final long FAILSAFE_INTERVAL_MS = 30L * 1000L;
    private static final long PLUG_WINDOW_MS = 4000L;
    private static final long DIALOG_BURST_SPACING_MS = 4000L;
    private static final long DIALOG_RETRY_WINDOW_MS = 30L * 1000L;
    private static final int DIALOG_BURST_MAX = 3;

    public static volatile int sTargetVolumeStep = -1; // -1 = uninitialized (never defaults to 13)
    private static volatile boolean sTargetInitialized = false;
    private static volatile boolean sIsPlaying = false;
    private static volatile boolean sAudioEffectsLockedOnce = false;
    private static volatile boolean sGuardsRunning = false;
    private static volatile long sLastEnforceElapsedMs = 0L;
    private static volatile long sLastPlugMs = -100000L;
    private static volatile long sLastOwnSetMs = -100000L;
    private static volatile long sSelfWriteUntilElapsedMs = 0L;
    private static volatile long sLastUiPopupTriggerMs = -DIALOG_RETRY_WINDOW_MS;
    private static volatile long sDialogBurstWindowStartMs = -DIALOG_RETRY_WINDOW_MS;
    private static volatile int sDialogBurstCount = 0;
    private static volatile int sConsecutiveDialogRetries = 0;
    private static volatile long sLastFlagsCheckMs = 0L;
    private static volatile boolean sIsEnforcing = false;

    private static BroadcastReceiver sDynamicReceiver = null;
    private static ContentObserver sVolumeSettingsObserver = null;
    private static HandlerThread sGuardThread = null;
    private static Handler sGuardHandler = null;
    private static Runnable sObserverCheckRunnable = null;
    private static Runnable sFailsafeRunnable = null;
    private static PowerManager.WakeLock sActiveWakeLock = null;
    private static final Object sWakeLockSync = new Object();

    private S7VolumeHelper() {}

    public static void setPlayingState(boolean playing) { sIsPlaying = playing; }

    public static boolean isPlayingState() { return sIsPlaying; }

    public static boolean isA30Device() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        return model.contains("A30") || device.contains("A30");
    }

    public static boolean isDedicatedGeneratorDevice() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        for (String m : new String[]{
                "G920", "G925", "G928", "ZEROFLTE", "ZEROLTE",
                "G930", "G935", "HEROLTE", "HERO2LTE",
                "A30"
        }) {
            if (model.contains(m) || device.contains(m)) return true;
        }
        return false;
    }

    public static boolean isPersonalDevice() {
        return !isDedicatedGeneratorDevice();
    }

    /** Guard runs only on its own phone model so the installed Vorta apps never compete. */
    public static boolean isTargetDevice() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        for (String m : new String[]{"G930", "G935", "HEROLTE", "HERO2LTE"}) {
            if (model.contains(m) || device.contains(m)) return true;
        }
        return false;
    }

    private static Context getDeviceProtectedCtx(Context context) {
        if (context == null) return null;
        Context appCtx = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                return appCtx.createDeviceProtectedStorageContext();
            } catch (Throwable ignored) {}
        }
        return appCtx;
    }

    private static synchronized Handler ensureGuardHandler() {
        if (sGuardThread == null || !sGuardThread.isAlive()) {
            sGuardThread = new HandlerThread("S7VolGuardThread");
            sGuardThread.start();
            sGuardHandler = new Handler(sGuardThread.getLooper());
        } else if (sGuardHandler == null) {
            sGuardHandler = new Handler(sGuardThread.getLooper());
        }
        return sGuardHandler;
    }

    private static void markSelfWrite() {
        sSelfWriteUntilElapsedMs = SystemClock.elapsedRealtime() + SELF_WRITE_SUPPRESS_MS;
    }

    private static void persistWiredTargetVolume(Context context, int step) {
        if (context == null || step < 0 || step > MAX_VOLUME_STEP) return;
        try {
            Context deCtx = getDeviceProtectedCtx(context);
            if (deCtx != null) {
                SharedPreferences spDe = deCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                spDe.edit()
                        .putInt(KEY_WIRED_TARGET_VOL, step)
                        .putInt(KEY_LEGACY_USER_TARGET_VOL, step)
                        .apply();
            }
        } catch (Throwable ignored) {}
        try {
            Context ceCtx = context.getApplicationContext();
            if (ceCtx != null) {
                SharedPreferences spCe = ceCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                spCe.edit()
                        .putInt(KEY_WIRED_TARGET_VOL, step)
                        .putInt(KEY_LEGACY_USER_TARGET_VOL, step)
                        .apply();
            }
        } catch (Throwable ignored) {}
    }

    private static int readPersistedWiredTargetVolume(Context context) {
        if (context == null) return -1;
        int val = -1;
        try {
            Context deCtx = getDeviceProtectedCtx(context);
            if (deCtx != null) {
                SharedPreferences spDe = deCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                val = spDe.getInt(KEY_WIRED_TARGET_VOL, -1);
                if (val < 0) val = spDe.getInt(KEY_LEGACY_USER_TARGET_VOL, -1);
            }
        } catch (Throwable ignored) {}
        if (val >= 0 && val <= MAX_VOLUME_STEP) return val;

        try {
            Context ceCtx = context.getApplicationContext();
            if (ceCtx != null) {
                SharedPreferences spCe = ceCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                val = spCe.getInt(KEY_WIRED_TARGET_VOL, -1);
                if (val < 0) val = spCe.getInt(KEY_LEGACY_USER_TARGET_VOL, -1);
                if (val >= 0 && val <= MAX_VOLUME_STEP) {
                    persistWiredTargetVolume(context, val);
                    return val;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Synchronizes sTargetVolumeStep with live STREAM_MUSIC volume without fighting user adjustments.
     * On first run, preserves saved >9 wired target if OS boot-clamped to <=9; otherwise adopts cur.
     * Speaker mode never overwrites wired_target_vol.
     */
    public static synchronized void syncTargetFromSystem(Context context) {
        if (context == null || isPersonalDevice()) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur < 0 || cur > MAX_VOLUME_STEP) return;
            boolean wired = isWired(context);

            if (wired) {
                if (!sTargetInitialized || sTargetVolumeStep < 0) {
                    int saved = readPersistedWiredTargetVolume(context);
                    if (saved > SAFE_CLAMP_STEP && saved <= MAX_VOLUME_STEP && cur == SAFE_CLAMP_STEP) {
                        sTargetVolumeStep = saved;
                    } else {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVolume(context, cur);
                    }
                    sTargetInitialized = true;
                } else {
                    boolean clamped = sTargetVolumeStep > SAFE_CLAMP_STEP
                            && cur == SAFE_CLAMP_STEP && cur < sTargetVolumeStep;
                    if (!clamped && cur != sTargetVolumeStep) {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVolume(context, cur);
                    }
                }
            } else {
                if (!sTargetInitialized || sTargetVolumeStep < 0) {
                    int saved = readPersistedWiredTargetVolume(context);
                    if (saved >= 0 && saved <= MAX_VOLUME_STEP) {
                        sTargetVolumeStep = saved;
                        sTargetInitialized = true;
                    } else {
                        sTargetVolumeStep = cur;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "syncTargetFromSystem: " + t.getMessage());
        }
    }

    public static void saveTargetVolume(Context context, int step) {
        if (step >= 0 && step <= MAX_VOLUME_STEP) {
            sTargetVolumeStep = step;
            sTargetInitialized = true;
            if (context != null && isWired(context)) {
                persistWiredTargetVolume(context, step);
            }
        }
    }

    public static int loadTargetVolume(Context context) {
        syncTargetFromSystem(context);
        return getTargetVolumeStep();
    }

    public static int getTargetVolumeStep() {
        return sTargetVolumeStep >= 0 ? sTargetVolumeStep : 0;
    }

    public static int getTargetVolumeStep(Context context) {
        syncTargetFromSystem(context);
        return getTargetVolumeStep();
    }

    /** Wakes screen cleanly WITHOUT ON_AFTER_RELEASE so Keyguard sleeps back off in ~5s after confirm. */
    @SuppressWarnings("deprecation")
    public static void wakeUpScreenForDialog(Context context) {
        if (context == null || isPersonalDevice()) return;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            synchronized (sWakeLockSync) {
                if (sActiveWakeLock == null) {
                    sActiveWakeLock = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "vorta:S7SafeVolWake");
                    sActiveWakeLock.setReferenceCounted(false);
                }
                if (!pm.isInteractive() || !sActiveWakeLock.isHeld()) {
                    sActiveWakeLock.acquire(6000L);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void releaseDialogWakeLock() {
        synchronized (sWakeLockSync) {
            if (sActiveWakeLock != null) {
                try {
                    if (sActiveWakeLock.isHeld()) sActiveWakeLock.release();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static boolean isWiredDeviceType(int type) {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_LINE_ANALOG
                || type == AudioDeviceInfo.TYPE_AUX_LINE
                || type == TYPE_AUX_LINE_API21
                || type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                || type == TYPE_USB_HEADSET_API26;
    }

    @SuppressWarnings("deprecation")
    public static boolean isWired(Context context) {
        if (context == null) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (devs != null) {
                    for (int i = 0; i < devs.length; i++) {
                        if (isWiredDeviceType(devs[i].getType())) return true;
                    }
                    return false;
                }
            }
            return am.isWiredHeadsetOn();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 24/7 passive + ContentObserver + 30s failsafe receiver/guards (hosted by S7AccessibilityService). */
    public static synchronized void registerDynamicReceiver(Context context) {
        if (context == null || sDynamicReceiver != null || !isTargetDevice() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    onVolumeChanged(ctx, intent);
                } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)) {
                    recoverFromSafeVolumeClamp(ctx, "SEC_SAFE_MEDIA_VOLUME_ACTIVE");
                } else if (ACTION_SAFE_VOLUME_CHECK.equals(action)) {
                    recoverFromSafeVolumeClamp(ctx, "EXTERNAL_SAFE_VOLUME_CHECK");
                } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                    onHeadsetPlug(ctx, intent.getIntExtra("state", 0));
                } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                    final int t = effectiveTargetStep();
                    if (t > SAFE_CLAMP_STEP) {
                        ensureGuardHandler().postDelayed(new Runnable() {
                            @Override public void run() { applyWiredIndexes(appCtx, t); }
                        }, 300L);
                    }
                } else if ("android.media.STREAM_DEVICES_CHANGED_ACTION".equals(action)) {
                    if (isWired(ctx)) {
                        scheduleMultiPulseEnforcement(ctx, "DYNAMIC_" + action);
                    } else if (effectiveTargetStep() > SAFE_CLAMP_STEP) {
                        applyWiredIndexes(appCtx, effectiveTargetStep());
                    }
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    onBootOrCheck(ctx, "ACTION_USER_PRESENT");
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction("android.media.STREAM_DEVICES_CHANGED_ACTION");
        filter.addAction("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE");
        filter.addAction(ACTION_SAFE_VOLUME_CHECK);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                appCtx.registerReceiver(sDynamicReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appCtx.registerReceiver(sDynamicReceiver, filter);
            }
            Log.i(TAG, "24/7 volume guard receiver registered");
        } catch (Throwable t) {
            Log.w(TAG, "register receiver failed: " + t.getMessage());
        }
    }

    public static synchronized void unregisterDynamicReceiver(Context context) {
        if (context == null || sDynamicReceiver == null) return;
        try {
            context.getApplicationContext().unregisterReceiver(sDynamicReceiver);
        } catch (Throwable ignored) {
        } finally {
            sDynamicReceiver = null;
        }
    }

    /**
     * Starts non-reentrant ContentObserver + 30s lightweight failsafe on S7VolGuardThread.
     * Detects 20-hour silent clamps within ~650ms even while the screen is OFF and Keyguard locked.
     */
    public static synchronized void startBackgroundGuards(Context context) {
        if (context == null || !isTargetDevice() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        final Handler gh = ensureGuardHandler();
        sGuardsRunning = true;

        if (sObserverCheckRunnable == null) {
            sObserverCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!sGuardsRunning) return;
                    if (SystemClock.elapsedRealtime() < sSelfWriteUntilElapsedMs) return;
                    checkAndRecoverIfClamped(appCtx, "SETTINGS_OBSERVER_CLAMP");
                }
            };
        }

        if (sVolumeSettingsObserver == null) {
            sVolumeSettingsObserver = new ContentObserver(gh) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    if (!sGuardsRunning || selfChange) return;
                    if (SystemClock.elapsedRealtime() < sSelfWriteUntilElapsedMs) return;
                    gh.removeCallbacks(sObserverCheckRunnable);
                    gh.postDelayed(sObserverCheckRunnable, OBSERVER_DEBOUNCE_MS);
                }
            };
            try {
                ContentResolver cr = appCtx.getContentResolver();
                cr.registerContentObserver(Settings.System.getUriFor("volume_music_headset"), false, sVolumeSettingsObserver);
                cr.registerContentObserver(Settings.System.getUriFor("volume_music_headphone"), false, sVolumeSettingsObserver);
                cr.registerContentObserver(Settings.Global.getUriFor("audio_safe_volume_state"), false, sVolumeSettingsObserver);
                Log.i(TAG, "ContentObserver guard started (headset/headphone/safe_state)");
            } catch (Throwable t) {
                Log.w(TAG, "registerContentObserver failed: " + t.getMessage());
            }
        }

        if (sFailsafeRunnable == null) {
            sFailsafeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!sGuardsRunning) return;
                    try {
                        checkAndRecoverIfClamped(appCtx, "FAILSAFE_30S_CLAMP");
                    } finally {
                        if (sGuardsRunning) {
                            gh.postDelayed(this, FAILSAFE_INTERVAL_MS);
                        }
                    }
                }
            };
            gh.removeCallbacks(sFailsafeRunnable);
            gh.postDelayed(sFailsafeRunnable, FAILSAFE_INTERVAL_MS);
            Log.i(TAG, "30s failsafe heartbeat scheduled on S7VolGuardThread");
        }
    }

    public static synchronized void stopBackgroundGuards(Context context) {
        sGuardsRunning = false;
        if (sGuardHandler != null) {
            if (sObserverCheckRunnable != null) sGuardHandler.removeCallbacks(sObserverCheckRunnable);
            if (sFailsafeRunnable != null) sGuardHandler.removeCallbacks(sFailsafeRunnable);
        }
        if (sVolumeSettingsObserver != null && context != null) {
            try {
                context.getApplicationContext().getContentResolver().unregisterContentObserver(sVolumeSettingsObserver);
            } catch (Throwable ignored) {}
            sVolumeSettingsObserver = null;
        }
        releaseDialogWakeLock();
    }

    private static void checkAndRecoverIfClamped(Context context, String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        if (!isWired(context)) return;
        syncTargetFromSystem(context);
        int target = effectiveTargetStep();
        if (target <= SAFE_CLAMP_STEP) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur == SAFE_CLAMP_STEP && cur < target) {
                recoverOnGuardThread(context, reason);
            }
        } catch (Throwable t) {
            Log.w(TAG, "checkAndRecoverIfClamped: " + t.getMessage());
        }
    }

    private static int effectiveTargetStep() {
        int step = sTargetVolumeStep;
        if (step < 0) return -1;
        if (step > MAX_VOLUME_STEP) return MAX_VOLUME_STEP;
        return step;
    }

    public static void onBootOrCheck(Context context, final String reason) {
        if (context == null || isPersonalDevice() || isA30Device()) return;
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override
            public void run() {
                syncTargetFromSystem(appCtx);
                ensureTargetVolumeIfNeeded(appCtx, false);
            }
        });
    }

    /** After AUX plug/route change Samsung resets wired to 9 asynchronously: re-apply target five times if >9. */
    public static void scheduleMultiPulseEnforcement(final Context context, final String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        Handler gh = ensureGuardHandler();
        gh.post(new Runnable() {
            @Override
            public void run() {
                syncTargetFromSystem(appCtx);
                enforceSystemFlags(appCtx);
                int target = effectiveTargetStep();
                if (target <= SAFE_CLAMP_STEP) return;
                applyWiredIndexes(appCtx, target);
                if (!isWired(appCtx)) return;
                checkAndRecoverIfClamped(appCtx, reason + "_INIT");
            }
        });
        for (final int delay : new int[]{120, 350, 800, 1600, 3000}) {
            gh.postDelayed(new Runnable() {
                @Override public void run() { restoreIfClamped(appCtx, reason + "_D" + delay); }
            }, delay);
        }
        Log.i(TAG, "5-pulse enforcement scheduled (" + reason + ")");
    }

    /** Service/receiver entry point for ACTION_HEADSET_PLUG. */
    public static void onHeadsetPlug(final Context context, int state) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        sLastPlugMs = SystemClock.elapsedRealtime();
        if (state == 1) {
            sDialogBurstCount = 0;
            sConsecutiveDialogRetries = 0;
            scheduleMultiPulseEnforcement(appCtx, "HEADSET_PLUG_1");
        } else {
            ensureGuardHandler().postDelayed(new Runnable() {
                @Override public void run() {
                    enforceSystemFlags(appCtx);
                    int target = effectiveTargetStep();
                    if (target > SAFE_CLAMP_STEP) {
                        applyWiredIndexes(appCtx, target);
                    }
                }
            }, 300L);
        }
    }

    public static void restoreIfClamped(Context context, String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        syncTargetFromSystem(context);
        int target = effectiveTargetStep();
        if (target <= SAFE_CLAMP_STEP) return;
        if (!isWired(context)) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur == SAFE_CLAMP_STEP && cur < target) {
                recoverFromSafeVolumeClamp(context, reason);
            }
        } catch (Throwable t) {
            Log.w(TAG, "restoreIfClamped: " + t.getMessage());
        }
    }

    public static void enforceSystemFlags(Context context) {
        if (context == null || isPersonalDevice()) return;
        if (isA30Device() && sAudioEffectsLockedOnce) return;
        long now = SystemClock.uptimeMillis();
        if (sAudioEffectsLockedOnce && (now - sLastFlagsCheckMs) < 5000L) return;
        sLastFlagsCheckMs = now;
        try {
            ContentResolver cr = context.getContentResolver();
            if (!sAudioEffectsLockedOnce) {
                sAudioEffectsLockedOnce = true;
                // Lock pure uncolored stereo sine-wave audio-effect flags once per process
                putSystemIntSafe(cr, "mono_audio", 0);
                putSystemIntSafe(cr, "master_mono", 0);
                putSystemIntSafe(cr, "sound_alive_effect", 0);
                putSystemIntSafe(cr, "SOUNDALIVE_GENRE_INDEX", 0);
                putSystemIntSafe(cr, "SOUNDALIVE_AUDIO_PATH", 0);
                putSystemIntSafe(cr, "k2hd_effect", 0);
                putSystemIntSafe(cr, "uhqa_effect", 0);
                putSystemIntSafe(cr, "tube_amp_effect", 0);
                putSystemIntSafe(cr, "concert_hall_effect", 0);
                putSystemIntSafe(cr, "hearing_aid", 0);
                putSystemIntSafe(cr, "amplify_ambient_sound", 0);
                putSystemIntSafe(cr, "run_amplify_ambient_sound", 0);
                putGlobalIntSafe(cr, "multisound_state", 0);
                try {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.setParameters("g_effect_dolby_enable=0;g_effect_game_dolby_enable=0");
                } catch (Throwable ignored) {}
            }
            if (isA30Device()) return;
            // Never mask audio_safe_volume_state == 3 in DB
            if (Settings.Global.getInt(cr, "audio_safe_volume_state", 0) != 3) {
                putGlobalIntSafe(cr, "audio_safe_volume_state", 2);
                putSecureIntSafe(cr, "audio_safe_volume_state", 2);
            }
            putSecureIntSafe(cr, "unsafe_volume_music_active_ms", 0);
            putSystemIntSafe(cr, "volume_limiter", 0);
            putSystemIntSafe(cr, "volume_limiter_enable", 0);
            putSystemIntSafe(cr, "volumelimit_on", 0);
            putSystemIntSafe(cr, "volume_limiter_value", 15);
            int target = effectiveTargetStep();
            if (target > SAFE_CLAMP_STEP) {
                applyWiredIndexes(context, target);
            }
        } catch (Throwable t) {
            Log.w(TAG, "enforceSystemFlags: " + t.getMessage());
        }
    }

    /** Persists headset(0x4) and headphone(0x8) STREAM_MUSIC indexes in Settings.System. */
    public static void applyWiredIndexes(Context context, int targetStep) {
        if (context == null || isPersonalDevice() || isA30Device()) return;
        if (targetStep < 0 || targetStep > MAX_VOLUME_STEP) return;
        markSelfWrite();
        ContentResolver cr = context.getContentResolver();
        putSystemIntSafe(cr, "volume_music_headset", targetStep);
        putSystemIntSafe(cr, "volume_music_headphone", targetStep);
    }

    /** Neutralized: 85% (step 13) hard-lock permanently removed. */
    public static synchronized void enforceDefaultVolume85(Context context) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        Log.i(TAG, "enforceDefaultVolume85 ignored (free-volume mode active)");
        ensureTargetVolumeIfNeeded(context, false);
    }

    /**
     * Android 8.0 Lockscreen-Piercing Dialog Unlock (runs on S7VolGuardThread):
     * Wakes screen WITHOUT ON_AFTER_RELEASE, waits until PowerManager.isInteractive() + 250ms,
     * raises SafetyWarningDialog (SystemUIDialog with FLAG_SHOW_WHEN_LOCKED + TYPE_VOLUME_OVERLAY),
     * triggers S7AccessibilityService active getWindows() scan, and verifies unlock after 2.2s.
     */
    private static void requestConfirmDialog(final Context context, final AudioManager am, final int target) {
        long now = SystemClock.elapsedRealtime();
        if (now - sDialogBurstWindowStartMs >= DIALOG_RETRY_WINDOW_MS) {
            sDialogBurstWindowStartMs = now;
            sDialogBurstCount = 0;
        }
        if (sDialogBurstCount >= DIALOG_BURST_MAX) return;
        if (now - sLastUiPopupTriggerMs < DIALOG_BURST_SPACING_MS) return;

        sLastUiPopupTriggerMs = now;
        sDialogBurstCount++;
        sLastOwnSetMs = now;
        markSelfWrite();

        wakeUpScreenForDialog(context);
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                long deadline = SystemClock.elapsedRealtime() + 1000L;
                while (!pm.isInteractive() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(40L);
                }
                SystemClock.sleep(250L);
            }
        } catch (Throwable ignored) {}

        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS);
        S7AccessibilityService.requestActiveConfirmScan();
        Log.w(TAG, "FLAG_SHOW_UI|FLAG_SHOW_UI_WARNINGS at " + target
                + " (attempt " + sDialogBurstCount + "/" + DIALOG_BURST_MAX + ")");

        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                releaseDialogWakeLock();
                if (!isWired(appCtx)) return;
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int want = effectiveTargetStep();
                if (want > SAFE_CLAMP_STEP && cur <= SAFE_CLAMP_STEP && cur < want) {
                    if (sConsecutiveDialogRetries < 3) {
                        sConsecutiveDialogRetries++;
                        ensureGuardHandler().postDelayed(new Runnable() {
                            @Override public void run() {
                                recoverOnGuardThread(appCtx, "LOCKSCREEN_RETRY_" + sConsecutiveDialogRetries);
                            }
                        }, 5000L);
                    }
                } else {
                    sConsecutiveDialogRetries = 0;
                    sDialogBurstCount = 0;
                }
            }
        }, 2200L);
    }

    /** Restores OS clamps while preserving free manual volume adjustment (0..15). */
    public static synchronized void ensureTargetVolumeIfNeeded(Context context, boolean forceResetTo85) {
        if (context == null || isPersonalDevice()) return;
        syncTargetFromSystem(context);
        enforceSystemFlags(context);
        if (isA30Device()) return;
        int target = effectiveTargetStep();
        if (target <= SAFE_CLAMP_STEP) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            boolean wired = isWired(context);
            applyWiredIndexes(context, target);
            if (!wired) return;

            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur == SAFE_CLAMP_STEP && cur < target) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    recoverFromSafeVolumeClamp(context, "ENSURE_MAIN");
                } else {
                    recoverOnGuardThread(context, "ENSURE_GUARD");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "ensureTargetVolumeIfNeeded: " + t.getMessage());
        }
    }

    public static void onVolumeChanged(final Context context, Intent intent) {
        if (context == null || intent == null || sIsEnforcing || isA30Device() || isPersonalDevice()) return;
        int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
        if (streamType != AudioManager.STREAM_MUSIC) return;
        int curVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
        int prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", curVol);
        if (curVol < 0) return;

        long now = SystemClock.elapsedRealtime();
        if (now - sLastOwnSetMs < OWN_CHANGE_WINDOW_MS) return;

        syncTargetFromSystem(context);
        int target = effectiveTargetStep();
        if (!isWired(context)) return;

        boolean osClamp = target > SAFE_CLAMP_STEP && curVol == SAFE_CLAMP_STEP && (
                (now - sLastPlugMs < PLUG_WINDOW_MS)
                        || (prevVol >= 0 && prevVol - curVol >= 2)
                        || (prevVol <= SAFE_CLAMP_STEP)
        );

        if (osClamp) {
            recoverFromSafeVolumeClamp(context, "OS_CLAMP_" + prevVol + "_TO_" + curVol);
        } else if (curVol <= MAX_VOLUME_STEP) {
            saveTargetVolume(context, curVol);
            if (curVol > SAFE_CLAMP_STEP) {
                applyWiredIndexes(context, curVol);
            }
        }
    }

    public static void recoverFromSafeVolumeClamp(final Context context, final String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override
            public void run() {
                recoverOnGuardThread(appCtx, reason);
            }
        });
    }

    private static synchronized void recoverOnGuardThread(final Context context, String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        syncTargetFromSystem(context);
        enforceSystemFlags(context);
        int target = effectiveTargetStep();
        if (target <= SAFE_CLAMP_STEP || !isWired(context)) return;

        long nowMs = SystemClock.elapsedRealtime();
        if (nowMs - sLastEnforceElapsedMs < 80L) return;

        sIsEnforcing = true;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur >= target) {
                sDialogBurstCount = 0;
                sConsecutiveDialogRetries = 0;
                return;
            }
            if (cur != SAFE_CLAMP_STEP) {
                return;
            }

            markSelfWrite();
            sLastOwnSetMs = SystemClock.elapsedRealtime();
            applyWiredIndexes(context, target);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int after = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (after >= target) {
                sDialogBurstCount = 0;
                sConsecutiveDialogRetries = 0;
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                Log.i(TAG, "Silent restore OK (" + reason + "): " + cur + " -> " + after);
            } else {
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                requestConfirmDialog(context, am, target);
            }
        } catch (Throwable t) {
            Log.w(TAG, "recoverOnGuardThread: " + t.getMessage());
        } finally {
            sIsEnforcing = false;
        }
    }

    public static void onDialogConfirmed(Context context) {
        releaseDialogWakeLock();
        sConsecutiveDialogRetries = 0;
        sDialogBurstCount = 0;
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        Handler gh = ensureGuardHandler();
        for (long delayMs : new long[]{80L, 350L}) {
            gh.postDelayed(new Runnable() {
                @Override public void run() {
                    recoverOnGuardThread(appCtx, "DIALOG_CONFIRMED");
                }
            }, delayMs);
        }
    }

    public static void periodicGuardTick(Context context) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override public void run() {
                checkAndRecoverIfClamped(appCtx, "PERIODIC_TICK");
            }
        });
    }

    public static boolean isRecentlyEnforced(long windowMs) {
        return sIsEnforcing || (SystemClock.elapsedRealtime() - sLastEnforceElapsedMs < windowMs);
    }

    private static void putSystemIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.System.getInt(cr, key, Integer.MIN_VALUE) != val) {
                markSelfWrite();
                Settings.System.putInt(cr, key, val);
            }
        } catch (Throwable ignored) {}
    }

    private static void putGlobalIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.Global.getInt(cr, key, Integer.MIN_VALUE) != val) {
                markSelfWrite();
                Settings.Global.putInt(cr, key, val);
            }
        } catch (Throwable ignored) {}
    }

    private static void putSecureIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.Secure.getInt(cr, key, Integer.MIN_VALUE) != val) {
                markSelfWrite();
                Settings.Secure.putInt(cr, key, val);
            }
        } catch (Throwable ignored) {}
    }
}
