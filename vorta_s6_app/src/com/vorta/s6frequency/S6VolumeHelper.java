package com.vorta.s6frequency;

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
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Complete Free-Volume + OS-Tailored Safe-Volume Neutralization Guard for Galaxy S6
 * (SM-G920/G925/G928, Android 7.0 Nougat API 24).
 *
 * Architecture:
 *  1. ZERO 85% (13/15) hard-lock: sTargetVolumeStep initializes as -1 (uninitialized) and
 *     faithfully tracks user's wired manual choice (0..15) in Device-Protected SharedPreferences
 *     ("wired_target_vol"). Speaker volume never overwrites wired_target_vol.
 *  2. Screen-off instant clamp detection on a dedicated HandlerThread ("S6VolGuardThread"):
 *     - Layer 1A: Non-reentrant ContentObserver on volume_music_headset, volume_music_headphone,
 *       audio_safe_volume_state (catches silent 20h enforceSafeMediaVolume + MSG_PERSIST_VOLUME
 *       within ~650ms even while screen is OFF and Keyguard locked).
 *     - Layer 1B: 30s lightweight failsafe heartbeat (1 Binder read / 30s, zero Settings writes
 *       in steady state).
 *  3. Android 7.0 (API 24) Native Silent Direct-Binder Defeat:
 *     - Temporarily sets Settings.Secure.VOLUME_CONTROLLER_SERVICE_COMPONENT so AudioService's
 *       ControllerService authorizes our UID, calls IAudioService.disableSafeMediaVolume()
 *       directly over Binder (resets mSafeMediaVolumeState 3 -> 2 & mMusicActiveMs = 0),
 *       restores user volume silently with ZERO popup and ZERO screen wake, and immediately
 *       restores the previous controller component in finally{}.
 *  4. Lockscreen-piercing dialog confirmer fallback (used only if Direct-Binder is unavailable
 *     or via ACTION_TEST_DIALOG_UNLOCK): wakes screen WITHOUT ON_AFTER_RELEASE, waits for
 *     PowerManager.isInteractive(), raises SystemUI dialog, triggers active Accessibility scan,
 *     and immediately releases WakeLock on confirm.
 */
public final class S6VolumeHelper {
    private static final String TAG = "S6VolumeHelper";

    public static final int LEVELING_TARGET_STEP = 13;   // legacy constant retained for binary compat; NEVER forced
    public static final int MAX_VOLUME_STEP = 15;
    public static final int SAFE_CLAMP_STEP = 9;         // Samsung mSafeMediaVolumeIndex 90 / 10
    public static final int SAFE_VOLUME_CLAMP_MAX_STEP = 10;

    public static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
    public static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
    public static final String ACTION_SAFE_VOLUME_CHECK = "com.vorta.s6frequency.ACTION_SAFE_VOLUME_CHECK";
    public static final String ACTION_TEST_DIALOG_UNLOCK = "com.vorta.s6frequency.ACTION_TEST_DIALOG_UNLOCK";

    private static final int FLAG_SHOW_UI_WARNINGS = 1 << 10; // 1024: triggers SystemUI safe dialog
    private static final int TYPE_USB_HEADSET_API26 = 22;
    private static final int TYPE_AUX_LINE_API21 = 19;

    private static final String PREF_NAME = "vorta_s6_vol_prefs";
    private static final String KEY_WIRED_TARGET_VOL = "wired_target_vol";
    private static final String KEY_LEGACY_USER_TARGET_VOL = "user_target_vol";
    private static final String SECURE_VOL_CTRL_COMP = "volume_controller_service_component";
    private static final String AUTH_CONTROLLER_COMP = "com.vorta.s6frequency/.SafeVolAuth";

    private static final long OWN_CHANGE_WINDOW_MS = 1500L;
    private static final long SELF_WRITE_SUPPRESS_MS = 1600L;
    private static final long OBSERVER_DEBOUNCE_MS = 150L;
    private static final long FAILSAFE_INTERVAL_MS = 30L * 1000L;
    private static final long PLUG_WINDOW_MS = 4000L;
    private static final long DIALOG_BURST_SPACING_MS = 4000L;
    private static final long DIALOG_RETRY_WINDOW_MS = 60L * 1000L;
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
    private static volatile long sLastFlagsCheckMs = 0L;
    private static volatile boolean sIsEnforcing = false;
    private static volatile boolean sBootBinderDisarmed = false;

    private static BroadcastReceiver sDynamicReceiver = null;
    private static ContentObserver sVolumeSettingsObserver = null;
    private static HandlerThread sGuardThread = null;
    private static Handler sGuardHandler = null;
    private static Runnable sObserverCheckRunnable = null;
    private static Runnable sFailsafeRunnable = null;
    private static PowerManager.WakeLock sActiveWakeLock = null;
    private static final Object sWakeLockSync = new Object();
    private static final Object sBinderUnlockSync = new Object();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private S6VolumeHelper() {}

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

    /** Guard runs only on its own phone model so installed Vorta apps never compete. */
    public static boolean isTargetDevice() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        for (String m : new String[]{"G920", "G925", "G928", "ZEROFLTE", "ZEROLTE"}) {
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
            sGuardThread = new HandlerThread("S6VolGuardThread");
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
                // Unplugged (speaker): keep saved wired target intact; never overwrite wired_target_vol with speaker level
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

    private static boolean isScreenInteractive(Context context) {
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) return pm.isInteractive();
        } catch (Throwable ignored) {}
        return false;
    }

    /** Wakes screen for SystemUIDialog ONLY when dialog fallback is required (no ON_AFTER_RELEASE). */
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
                            "vorta:S6SafeVolWake");
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

    private static Object getIAudioService() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "audio");
            if (binder != null) {
                Class<?> stub = Class.forName("android.media.IAudioService$Stub");
                return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            }
        } catch (Throwable ignored) {}
        try {
            Method getService = AudioManager.class.getDeclaredMethod("getService");
            getService.setAccessible(true);
            return getService.invoke(null);
        } catch (Throwable t) {
            return null;
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

    /** 24/7 passive + ContentObserver + 30s failsafe receiver/guards (hosted by S6AccessibilityService). */
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
                } else if (ACTION_TEST_DIALOG_UNLOCK.equals(action)) {
                    triggerDialogFallbackForVerification(ctx);
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
        filter.addAction(ACTION_TEST_DIALOG_UNLOCK);
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
     * Starts non-reentrant ContentObserver + 30s lightweight failsafe on S6VolGuardThread.
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
            Log.i(TAG, "30s failsafe heartbeat scheduled on S6VolGuardThread");
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

    /** Zero-write check used by ContentObserver and 30s failsafe: disarms ACTIVE(3) even while unplugged; recovers volume ONLY if wired & clamped == 9. */
    private static void checkAndRecoverIfClamped(Context context, String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        tryProactiveDirectBinderDisarm(context, reason);
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
                tryProactiveDirectBinderDisarm(appCtx, reason);
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
                tryProactiveDirectBinderDisarm(appCtx, reason + "_INIT");
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
                // Lock pure uncolored sine-wave audio-effect flags once per process
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
            // Never mask audio_safe_volume_state == 3 in DB before Direct-Binder disarms RAM
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

    /** RAM per-device index injection (works while plugged AND pre-seeds while unplugged). */
    public static void enforcePerDeviceVolumesInRam(Context context, int targetStep) {
        if (context == null || isPersonalDevice() || isA30Device()) return;
        if (targetStep <= SAFE_CLAMP_STEP || targetStep > MAX_VOLUME_STEP) return;
        applyWiredIndexes(context, targetStep);
    }

    public static boolean areWiredDeviceVolumesClampedInRam(Context context, int targetStep) {
        if (context == null) return false;
        Object ias = getIAudioService();
        if (ias == null) return false;
        try {
            Method getForDev = ias.getClass().getMethod("getStreamVolumeForDevice", int.class, int.class);
            int hp = (Integer) getForDev.invoke(ias, AudioManager.STREAM_MUSIC, DEVICE_OUT_WIRED_HEADPHONE);
            int hs = (Integer) getForDev.invoke(ias, AudioManager.STREAM_MUSIC, DEVICE_OUT_WIRED_HEADSET);
            return hp < targetStep || hs < targetStep;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Neutralized: 85% (step 13) hard-lock has been permanently removed.
     * Any legacy caller is safely routed to free-volume clamp recovery.
     */
    public static synchronized void enforceDefaultVolume85(Context context) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        Log.i(TAG, "enforceDefaultVolume85 ignored (free-volume mode active)");
        ensureTargetVolumeIfNeeded(context, false);
    }

    private static void tryProactiveDirectBinderDisarm(Context context, String reason) {
        if (context == null || Build.VERSION.SDK_INT > 25 || isPersonalDevice() || isA30Device()) return;
        try {
            int safeState = Settings.Global.getInt(context.getContentResolver(), "audio_safe_volume_state", 0);
            if (safeState == 3 || !sBootBinderDisarmed) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                syncTargetFromSystem(context);
                int eff = effectiveTargetStep();
                int t = (isWired(context) && eff > SAFE_CLAMP_STEP) ? eff : -1;
                if (tryDirectBinderDisableSafeVolume(context, am, t)) {
                    Log.i(TAG, "Proactive Direct-Binder disarm completed (" + reason + ", prevDbState=" + safeState + ")");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "tryProactiveDirectBinderDisarm: " + t.getMessage());
        }
    }

    /**
     * Android 7.0 (API 24/25) Native Silent Direct-Binder Defeat:
     * Uses AudioService$ControllerService (verified in S6 services_0.dex) to authorize
     * our UID temporarily and invoke IAudioService.disableSafeMediaVolume() over Binder
     * with ZERO popup and ZERO screen wake, even while Keyguard is locked or unplugged.
     */
    private static boolean tryDirectBinderDisableSafeVolume(Context context, AudioManager am, int target) {
        if (context == null || am == null || Build.VERSION.SDK_INT > 25) return false;
        synchronized (sBinderUnlockSync) {
            ContentResolver cr = context.getContentResolver();
            Object ias = getIAudioService();
            if (ias == null) return false;

            Method disableMethod;
            try {
                disableMethod = ias.getClass().getMethod("disableSafeMediaVolume", String.class);
            } catch (Throwable t) {
                Log.w(TAG, "disableSafeMediaVolume reflection unavailable: " + t.getMessage());
                return false;
            }

            String prevComp = null;
            boolean wroteComp = false;
            try {
                prevComp = Settings.Secure.getString(cr, SECURE_VOL_CTRL_COMP);
                if (AUTH_CONTROLLER_COMP.equals(prevComp)) {
                    prevComp = null;
                }
                if (!Settings.Secure.putString(cr, SECURE_VOL_CTRL_COMP, AUTH_CONTROLLER_COMP)) {
                    Log.w(TAG, "putString volume_controller_service_component returned false");
                    return false;
                }
                wroteComp = true;

                boolean unlocked = false;
                int attemptUsed = 0;
                for (int attempt = 1; attempt <= 12; attempt++) {
                    attemptUsed = attempt;
                    try {
                        disableMethod.invoke(ias, context.getPackageName());
                        unlocked = true;
                        break;
                    } catch (Throwable invEx) {
                        Throwable cause = invEx.getCause() != null ? invEx.getCause() : invEx;
                        if (cause instanceof SecurityException) {
                            SystemClock.sleep(25L);
                        } else {
                            Log.w(TAG, "Direct-Binder disableSafeMediaVolume error: " + cause);
                            break;
                        }
                    }
                }

                if (!unlocked) {
                    Log.w(TAG, "Direct-Binder disableSafeMediaVolume did not authorize within 12 attempts");
                    return false;
                }

                sBootBinderDisarmed = true;
                markSelfWrite();
                sLastOwnSetMs = SystemClock.elapsedRealtime();
                boolean wiredNow = isWired(context);
                if (target > SAFE_CLAMP_STEP && wiredNow) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                    applyWiredIndexes(context, target);
                }
                putGlobalIntSafe(cr, "audio_safe_volume_state", 2);
                putSecureIntSafe(cr, "audio_safe_volume_state", 2);
                putSecureIntSafe(cr, "unsafe_volume_music_active_ms", 0);

                int after = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                Log.i(TAG, "S6 Direct-Binder disableSafeMediaVolume OK (attempt=" + attemptUsed
                        + ", target=" + target + ", wired=" + wiredNow + ", after=" + after
                        + ", screenInteractive=" + isScreenInteractive(context) + ")");
                return (target > SAFE_CLAMP_STEP && wiredNow) ? (after >= target) : true;
            } catch (Throwable t) {
                Log.w(TAG, "tryDirectBinderDisableSafeVolume failed: " + t.getMessage());
                return false;
            } finally {
                if (wroteComp) {
                    try {
                        Settings.Secure.putString(cr, SECURE_VOL_CTRL_COMP, prevComp);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    /** Fallback: Lockscreen-piercing SystemUI dialog trigger + active accessibility scan (runs on S6VolGuardThread). */
    private static void requestConfirmDialog(Context context, AudioManager am, int target) {
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
        S6AccessibilityService.requestActiveConfirmScan();
        Log.w(TAG, "Fallback FLAG_SHOW_UI|FLAG_SHOW_UI_WARNINGS at " + target
                + " (attempt " + sDialogBurstCount + "/" + DIALOG_BURST_MAX + ")");

        // Safety cleanup in case AccessibilityService is unbound
        ensureGuardHandler().postDelayed(new Runnable() {
            @Override public void run() { releaseDialogWakeLock(); }
        }, 3500L);
    }

    /** Test hook: verifies lockscreen-piercing SystemUI dialog unlock on-device without affecting production path. */
    private static void triggerDialogFallbackForVerification(final Context context) {
        if (context == null || isPersonalDevice() || isA30Device()) return;
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override
            public void run() {
                syncTargetFromSystem(appCtx);
                int target = effectiveTargetStep();
                if (target <= SAFE_CLAMP_STEP) target = 14;
                AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
                if (am == null || !isWired(appCtx)) return;
                sDialogBurstCount = 0;
                sLastUiPopupTriggerMs = 0L;
                am.setStreamVolume(AudioManager.STREAM_MUSIC, SAFE_CLAMP_STEP, 0);
                requestConfirmDialog(appCtx, am, target);
            }
        });
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
        boolean wired = isWired(context);

        if (!wired) {
            // Speaker change: never overwrite saved wired_target_vol
            return;
        }

        // OS safe-volume ONLY clamps to exactly SAFE_CLAMP_STEP (9), never below 9.
        // Also protect saved target (>9) if user pressed VolUp while already clamped at 9 (prev <= 9 && cur == 9).
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
                return;
            }
            if (cur != SAFE_CLAMP_STEP) {
                return;
            }

            // 1) Try direct silent setStreamVolume first (works whenever state != ACTIVE(3))
            markSelfWrite();
            sLastOwnSetMs = SystemClock.elapsedRealtime();
            applyWiredIndexes(context, target);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int after = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (after >= target) {
                sDialogBurstCount = 0;
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                Log.i(TAG, "Silent restore OK (" + reason + "): " + cur + " -> " + after);
                return;
            }

            // 2) Safe state is ACTIVE (3): use Android 7.0 Direct-Binder disableSafeMediaVolume() (screen-off, 0-popup)
            if (tryDirectBinderDisableSafeVolume(context, am, target)) {
                sDialogBurstCount = 0;
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                return;
            }

            // 3) Fallback only if Direct-Binder failed: wake screen & confirm SystemUI dialog
            sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
            requestConfirmDialog(context, am, target);
            Log.i(TAG, "Recovered via dialog fallback (" + reason + ") -> target=" + target);
        } catch (Throwable t) {
            Log.w(TAG, "recoverOnGuardThread: " + t.getMessage());
        } finally {
            sIsEnforcing = false;
        }
    }

    public static void onDialogConfirmed(Context context) {
        releaseDialogWakeLock();
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
