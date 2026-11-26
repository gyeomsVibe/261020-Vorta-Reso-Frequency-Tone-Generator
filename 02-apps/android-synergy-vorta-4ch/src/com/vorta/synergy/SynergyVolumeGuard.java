package com.vorta.synergy;

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
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Red-team hardened OS-flavor-aware audio effects & safe-volume guard:
 * - Read-only live volume badge across ALL devices (A30 included, zero volume writes on A30).
 * - S6_API24: Direct-Binder IAudioService.disableSafeMediaVolume() (skipped when already state==2 & unlocked).
 * - S7_API26: Screen-off ContentObserver + Lockscreen-piercing dialog unlock + 5-min retry cooldown reset.
 */
public final class SynergyVolumeGuard {
    private static final String TAG = "SynergyVolGuard";

    public static final int MAX_VOLUME_STEP = 15;
    public static final int SAFE_CLAMP_STEP = 9;
    public static final String ACTION_SAFE_VOLUME_CHECK = "com.vorta.synergy.ACTION_SAFE_VOLUME_CHECK";

    private static final int FLAG_SHOW_UI_WARNINGS = 1 << 10;
    private static final int TYPE_USB_HEADSET_API26 = 22;
    private static final int TYPE_AUX_LINE_API21 = 19;

    private static final String PREF_NAME = "vorta_synergy_vol_prefs";
    private static final String KEY_WIRED_TARGET_VOL = "wired_target_vol";
    private static final String SECURE_VOL_CTRL_COMP = "volume_controller_service_component";
    private static final String AUTH_CONTROLLER_COMP = "com.vorta.synergy/.SafeVolAuth";

    private static final long OWN_CHANGE_WINDOW_MS = 1500L;
    private static final long SELF_WRITE_SUPPRESS_MS = 1600L;
    private static final long OBSERVER_DEBOUNCE_MS = 150L;
    private static final long FAILSAFE_INTERVAL_MS = 30L * 1000L;
    private static final long PLUG_WINDOW_MS = 4000L;
    private static final long DIALOG_BURST_SPACING_MS = 4000L;
    private static final long DIALOG_RETRY_WINDOW_MS = 30L * 1000L;
    public static final long DIALOG_COOLDOWN_RESET_MS = 5L * 60L * 1000L;
    private static final int DIALOG_BURST_MAX = 3;

    public static volatile int sTargetVolumeStep = -1;
    private static volatile boolean sTargetInitialized = false;
    private static volatile boolean sAudioEffectsLockedOnce = false;
    private static volatile boolean sGuardsRunning = false;
    private static volatile boolean sBootBinderDisarmed = false;
    private static volatile boolean sIsEnforcing = false;
    private static volatile long sLastEnforceElapsedMs = 0L;
    private static volatile long sLastPlugMs = -100000L;
    private static volatile long sLastOwnSetMs = -100000L;
    private static volatile long sLastManualLowerMs = -100000L;
    private static volatile long sSelfWriteUntilElapsedMs = 0L;
    private static volatile long sLastUiPopupTriggerMs = -DIALOG_RETRY_WINDOW_MS;
    private static volatile long sDialogBurstWindowStartMs = -DIALOG_RETRY_WINDOW_MS;
    private static volatile int sDialogBurstCount = 0;
    private static volatile int sConsecutiveDialogRetries = 0;
    private static volatile long sConsecutiveRetryWindowStartMs = 0L;
    private static volatile long sLastFlagsCheckMs = 0L;

    private static BroadcastReceiver sDynamicReceiver = null;
    private static ContentObserver sVolumeSettingsObserver = null;
    private static HandlerThread sGuardThread = null;
    private static Handler sGuardHandler = null;
    private static Runnable sObserverCheckRunnable = null;
    private static Runnable sFailsafeRunnable = null;
    private static PowerManager.WakeLock sActiveWakeLock = null;
    private static final Object sWakeLockSync = new Object();
    private static final Object sBinderUnlockSync = new Object();

    private SynergyVolumeGuard() {}

    // -------------------------------------------------------------------------
    // PURE-JAVA REDTEAM POLICY HELPERS (Host JVM testable)
    // -------------------------------------------------------------------------

    public static int computeSafeClampStep(int maxVolume) {
        if (maxVolume <= 1) return 1;
        return Math.max(1, (int) Math.floor(maxVolume * 0.6));
    }

    public static boolean shouldResetDialogRetryCounter(long nowMs, long windowStartMs, int consecutiveRetries) {
        return consecutiveRetries >= 3 && windowStartMs > 0L && (nowMs - windowStartMs) >= DIALOG_COOLDOWN_RESET_MS;
    }

    public static boolean isOsSafeVolumeClamp(
            int curVol,
            int prevVol,
            int targetVol,
            int safeClampStep,
            int safeVolumeDbState,
            boolean withinPlugWindow,
            boolean recentlyLoweredManually) {
        if (targetVol <= safeClampStep || curVol != safeClampStep || curVol >= targetVol) {
            return false;
        }
        // If user manually stepped down 10 -> 9 and OS safe volume is already inactive (state==2), honor manual choice.
        if (safeVolumeDbState == 2 && !withinPlugWindow && prevVol == safeClampStep + 1) {
            return false;
        }
        if (recentlyLoweredManually && prevVol == safeClampStep + 1 && !withinPlugWindow) {
            return false;
        }
        return withinPlugWindow
                || safeVolumeDbState == 3
                || (prevVol >= 0 && prevVol - curVol >= 2)
                || (prevVol <= safeClampStep);
    }

    public static String sanitizeRestoredControllerComponent(String prevComp) {
        if (prevComp == null || prevComp.trim().isEmpty()) return null;
        if (prevComp.contains(".SafeVolAuth")) return null;
        return prevComp;
    }

    // -------------------------------------------------------------------------
    // DEVICE DETECTION & READ-ONLY VOLUME BADGE
    // -------------------------------------------------------------------------

    public static boolean isA30Device() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase(Locale.US) : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase(Locale.US) : "";
        return model.contains("A30") || device.contains("A30");
    }

    public static boolean isDedicatedGeneratorDevice() {
        String combined = ((Build.MODEL != null ? Build.MODEL : "") + " "
                + (Build.DEVICE != null ? Build.DEVICE : "")).toUpperCase(Locale.US);
        for (String m : new String[]{"G920", "G925", "G928", "ZEROFLTE", "ZEROLTE", "G930", "G935", "HEROLTE", "HERO2LTE", "A30"}) {
            if (combined.contains(m)) return true;
        }
        return false;
    }

    public static boolean isTargetDevice() {
        if (!isDedicatedGeneratorDevice()) return false;
        if (!BuildProfile.ENABLE_VOLUME_GUARD || isA30Device()) return false;
        return true;
    }

    /** Read-only universal media volume & output path indicator across ALL devices. */
    public static String formatLiveVolumeBadge(Context context) {
        if (context == null) return "SPK · --/15";
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return "SPK · --/15";
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            String path = isWired(context) ? "AUX" : "SPK";
            return String.format(Locale.US, "%s · %d/%d", path, cur, max);
        } catch (Throwable ignored) {
            return "SPK · --/15";
        }
    }

    public static String buildVolumeHealthSummary(Context context) {
        if (context == null) return "정보 없음";
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int cur = am != null ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : -1;
            int max = am != null ? am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : MAX_VOLUME_STEP;
            String path = isWired(context) ? "유선 AUX" : "내장 스피커";
            int safeState = Settings.Global.getInt(context.getContentResolver(), "audio_safe_volume_state", -1);
            String safeLabel;
            if (!BuildProfile.ENABLE_VOLUME_GUARD || isA30Device()) {
                safeLabel = "OS 제한 없음 (순수 읽기 모니터링)";
            } else if (safeState == 2) {
                safeLabel = "해제 완료 (안전 가드 활성)";
            } else if (safeState == 3) {
                safeLabel = "OS 제한 대기 (해제 시도 중)";
            } else {
                safeLabel = "상태 코드 " + safeState;
            }
            String a11yLabel = (!BuildProfile.ENABLE_VOLUME_GUARD || isA30Device())
                    ? "미사용"
                    : (SynergyAccessibilityService.isConnected() ? "연결됨" : "미연결 (접근성 권장)");
            return String.format(Locale.US,
                    "미디어 볼륨: %d / %d (%s)\n안전볼륨 상태: %s\n자동확인 서비스: %s",
                    cur, max, path, safeLabel, a11yLabel);
        } catch (Throwable t) {
            return "볼륨 점검 오류: " + t.getMessage();
        }
    }

    private static Context getDeviceProtectedCtx(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Context de = app.createDeviceProtectedStorageContext();
                if (de != null) return de;
            } catch (Throwable ignored) {}
        }
        return app;
    }

    private static synchronized Handler ensureGuardHandler() {
        if (sGuardThread == null || !sGuardThread.isAlive()) {
            sGuardThread = new HandlerThread("SynergyVolGuardThread");
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
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void persistWiredTargetVolume(Context context, int step) {
        if (context == null || step < 0 || step > MAX_VOLUME_STEP) return;
        try {
            Context de = getDeviceProtectedCtx(context);
            if (de != null) {
                de.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_WIRED_TARGET_VOL, step).apply();
            }
        } catch (Throwable ignored) {}
    }

    private static int readPersistedWiredTargetVolume(Context context) {
        if (context == null) return -1;
        try {
            Context de = getDeviceProtectedCtx(context);
            if (de != null) {
                return de.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getInt(KEY_WIRED_TARGET_VOL, -1);
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public static synchronized void syncTargetFromSystem(Context context) {
        if (context == null || !isDedicatedGeneratorDevice()) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int clampStep = computeSafeClampStep(max);
            if (cur < 0 || cur > max) return;
            boolean wired = BuildProfile.ENABLE_VOLUME_GUARD && !isA30Device() && isWired(context);
            if (wired) {
                if (!sTargetInitialized || sTargetVolumeStep < 0) {
                    int saved = readPersistedWiredTargetVolume(context);
                    if (saved > clampStep && saved <= max && cur == clampStep) {
                        sTargetVolumeStep = saved;
                    } else {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVolume(context, cur);
                    }
                    sTargetInitialized = true;
                } else {
                    boolean clamped = sTargetVolumeStep > clampStep
                            && cur == clampStep && cur < sTargetVolumeStep;
                    if (!clamped && cur != sTargetVolumeStep) {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVolume(context, cur);
                    }
                }
            } else {
                if (!sTargetInitialized || sTargetVolumeStep < 0) {
                    int saved = readPersistedWiredTargetVolume(context);
                    if (saved >= 0 && saved <= max) {
                        sTargetVolumeStep = saved;
                        sTargetInitialized = true;
                    } else {
                        sTargetVolumeStep = cur;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void saveTargetVolume(Context context, int step) {
        if (step >= 0 && step <= MAX_VOLUME_STEP) {
            sTargetVolumeStep = step;
            sTargetInitialized = true;
            if (context != null && isWired(context) && BuildProfile.ENABLE_VOLUME_GUARD && !isA30Device()) {
                persistWiredTargetVolume(context, step);
            }
        }
    }

    private static int effectiveTargetStep() {
        if (sTargetVolumeStep < 0) return -1;
        return Math.min(sTargetVolumeStep, MAX_VOLUME_STEP);
    }

    public static void enforceSystemFlags(Context context) {
        if (context == null || !isDedicatedGeneratorDevice()) return;
        if (isA30Device() && sAudioEffectsLockedOnce) return;
        long now = SystemClock.uptimeMillis();
        if (sAudioEffectsLockedOnce && (now - sLastFlagsCheckMs) < 5000L) return;
        sLastFlagsCheckMs = now;
        try {
            ContentResolver cr = context.getContentResolver();
            if (!sAudioEffectsLockedOnce) {
                sAudioEffectsLockedOnce = true;
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
                putSystemIntSafe(cr, "dolby_atmos_mode", 0);
                putGlobalIntSafe(cr, "dolby_atmos_mode", 0);
                putGlobalIntSafe(cr, "multisound_state", 0);
                try {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.setParameters("g_effect_dolby_enable=0;g_effect_game_dolby_enable=0");
                } catch (Throwable ignored) {}
            }
            if (!BuildProfile.ENABLE_VOLUME_GUARD || isA30Device()) return;
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

    public static void applyWiredIndexes(Context context, int targetStep) {
        if (context == null || !isTargetDevice()) return;
        if (targetStep < 0 || targetStep > MAX_VOLUME_STEP) return;
        markSelfWrite();
        ContentResolver cr = context.getContentResolver();
        putSystemIntSafe(cr, "volume_music_headset", targetStep);
        putSystemIntSafe(cr, "volume_music_headphone", targetStep);
    }

    public static synchronized void ensureTargetVolumeIfNeeded(Context context) {
        if (context == null || !isDedicatedGeneratorDevice()) return;
        syncTargetFromSystem(context);
        enforceSystemFlags(context);
        if (!isTargetDevice()) return;
        int target = effectiveTargetStep();
        if (target <= SAFE_CLAMP_STEP || !isWired(context)) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int clampStep = computeSafeClampStep(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            if (cur == clampStep && cur < target) {
                recoverFromSafeVolumeClamp(context, "ENSURE_GUARD");
            }
        } catch (Throwable ignored) {}
    }

    public static void onVolumeChanged(Context context, Intent intent) {
        if (context == null || intent == null || sIsEnforcing || !isTargetDevice()) return;
        int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
        if (streamType != AudioManager.STREAM_MUSIC) return;
        int curVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
        int prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", curVol);
        if (curVol < 0) return;

        long now = SystemClock.elapsedRealtime();
        if (now - sLastOwnSetMs < OWN_CHANGE_WINDOW_MS) return;

        syncTargetFromSystem(context);
        if (!isWired(context)) return;
        int target = effectiveTargetStep();
        int safeState = 0;
        try {
            safeState = Settings.Global.getInt(context.getContentResolver(), "audio_safe_volume_state", 0);
        } catch (Throwable ignored) {}

        if (prevVol > curVol) {
            sLastManualLowerMs = now;
        }
        boolean withinPlug = (now - sLastPlugMs) < PLUG_WINDOW_MS;
        boolean recentlyLowered = (now - sLastManualLowerMs) < 3000L;

        if (isOsSafeVolumeClamp(curVol, prevVol, target, SAFE_CLAMP_STEP, safeState, withinPlug, recentlyLowered)) {
            recoverFromSafeVolumeClamp(context, "OS_CLAMP_" + prevVol + "_TO_" + curVol);
        } else if (curVol <= MAX_VOLUME_STEP) {
            saveTargetVolume(context, curVol);
            if (curVol > SAFE_CLAMP_STEP) applyWiredIndexes(context, curVol);
        }
    }

    public static void onHeadsetPlug(final Context context, int state) {
        if (context == null || !isTargetDevice()) return;
        final Context appCtx = context.getApplicationContext();
        sLastPlugMs = SystemClock.elapsedRealtime();
        if (state == 1) {
            sDialogBurstCount = 0;
            sConsecutiveDialogRetries = 0;
            sConsecutiveRetryWindowStartMs = 0L;
            scheduleMultiPulseEnforcement(appCtx, "HEADSET_PLUG_1");
        } else {
            ensureGuardHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    enforceSystemFlags(appCtx);
                    int target = effectiveTargetStep();
                    if (target > SAFE_CLAMP_STEP) {
                        applyWiredIndexes(appCtx, target);
                    }
                }
            }, 300L);
        }
    }

    public static void onBootOrCheck(Context context, final String reason) {
        if (context == null || !isDedicatedGeneratorDevice()) return;
        if (!isTargetDevice()) {
            enforceSystemFlags(context);
            return;
        }
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override
            public void run() {
                syncTargetFromSystem(appCtx);
                tryProactiveDirectBinderDisarm(appCtx, reason);
                ensureTargetVolumeIfNeeded(appCtx);
            }
        });
    }

    public static void scheduleMultiPulseEnforcement(final Context context, final String reason) {
        if (context == null || !isTargetDevice()) return;
        final Context appCtx = context.getApplicationContext();
        Handler gh = ensureGuardHandler();
        gh.post(new Runnable() {
            @Override
            public void run() {
                syncTargetFromSystem(appCtx);
                tryProactiveDirectBinderDisarm(appCtx, reason + "_INIT");
                enforceSystemFlags(appCtx);
                int target = effectiveTargetStep();
                if (target > SAFE_CLAMP_STEP && isWired(appCtx)) {
                    checkAndRecoverIfClamped(appCtx, reason + "_INIT");
                }
            }
        });
        for (final int delay : new int[]{120, 350, 800, 1600, 3000}) {
            gh.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkAndRecoverIfClamped(appCtx, reason + "_D" + delay);
                }
            }, delay);
        }
    }

    public static synchronized void registerDynamicReceiver(Context context) {
        if (context == null || sDynamicReceiver != null || !isTargetDevice()) return;
        final Context appCtx = context.getApplicationContext();
        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    onVolumeChanged(ctx, intent);
                } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)
                        || ACTION_SAFE_VOLUME_CHECK.equals(action)) {
                    recoverFromSafeVolumeClamp(ctx, action);
                } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                    onHeadsetPlug(ctx, intent.getIntExtra("state", 0));
                } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                    final int t = effectiveTargetStep();
                    if (t > SAFE_CLAMP_STEP) {
                        ensureGuardHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                applyWiredIndexes(appCtx, t);
                            }
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
        } catch (Throwable ignored) {}
    }

    public static synchronized void unregisterDynamicReceiver(Context context) {
        if (context == null || sDynamicReceiver == null) return;
        try { context.getApplicationContext().unregisterReceiver(sDynamicReceiver); } catch (Throwable ignored) {}
        sDynamicReceiver = null;
    }

    public static synchronized void startBackgroundGuards(Context context) {
        if (context == null || !isTargetDevice()) return;
        final Context appCtx = context.getApplicationContext();
        final Handler gh = ensureGuardHandler();
        sGuardsRunning = true;

        if (sObserverCheckRunnable == null) {
            sObserverCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!sGuardsRunning || SystemClock.elapsedRealtime() < sSelfWriteUntilElapsedMs) return;
                    checkAndRecoverIfClamped(appCtx, "SETTINGS_OBSERVER_CLAMP");
                }
            };
        }
        if (sVolumeSettingsObserver == null) {
            sVolumeSettingsObserver = new ContentObserver(gh) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    if (!sGuardsRunning || selfChange || SystemClock.elapsedRealtime() < sSelfWriteUntilElapsedMs) return;
                    gh.removeCallbacks(sObserverCheckRunnable);
                    gh.postDelayed(sObserverCheckRunnable, OBSERVER_DEBOUNCE_MS);
                }
            };
            try {
                ContentResolver cr = appCtx.getContentResolver();
                cr.registerContentObserver(Settings.System.getUriFor("volume_music_headset"), false, sVolumeSettingsObserver);
                cr.registerContentObserver(Settings.System.getUriFor("volume_music_headphone"), false, sVolumeSettingsObserver);
                cr.registerContentObserver(Settings.Global.getUriFor("audio_safe_volume_state"), false, sVolumeSettingsObserver);
            } catch (Throwable ignored) {}
        }
        if (sFailsafeRunnable == null) {
            sFailsafeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!sGuardsRunning) return;
                    try { checkAndRecoverIfClamped(appCtx, "FAILSAFE_30S_CLAMP"); }
                    finally { if (sGuardsRunning) gh.postDelayed(this, FAILSAFE_INTERVAL_MS); }
                }
            };
            gh.removeCallbacks(sFailsafeRunnable);
            gh.postDelayed(sFailsafeRunnable, FAILSAFE_INTERVAL_MS);
        }
    }

    public static synchronized void stopBackgroundGuards(Context context) {
        sGuardsRunning = false;
        if (sGuardHandler != null) {
            if (sObserverCheckRunnable != null) sGuardHandler.removeCallbacks(sObserverCheckRunnable);
            if (sFailsafeRunnable != null) sGuardHandler.removeCallbacks(sFailsafeRunnable);
        }
        if (sVolumeSettingsObserver != null && context != null) {
            try { context.getApplicationContext().getContentResolver().unregisterContentObserver(sVolumeSettingsObserver); } catch (Throwable ignored) {}
            sVolumeSettingsObserver = null;
        }
        releaseDialogWakeLock();
    }

    private static void checkAndRecoverIfClamped(Context context, String reason) {
        if (!isTargetDevice() || context == null) return;
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
        } catch (Throwable ignored) {}
    }

    public static void recoverFromSafeVolumeClamp(final Context context, final String reason) {
        if (context == null || !isTargetDevice()) return;
        final Context appCtx = context.getApplicationContext();
        ensureGuardHandler().post(new Runnable() {
            @Override
            public void run() {
                recoverOnGuardThread(appCtx, reason);
            }
        });
    }

    private static synchronized void recoverOnGuardThread(final Context context, String reason) {
        if (context == null || !isTargetDevice()) return;
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
                sConsecutiveRetryWindowStartMs = 0L;
                return;
            }
            if (cur != SAFE_CLAMP_STEP) return;

            markSelfWrite();
            sLastOwnSetMs = SystemClock.elapsedRealtime();
            applyWiredIndexes(context, target);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int after = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (after >= target) {
                sDialogBurstCount = 0;
                sConsecutiveDialogRetries = 0;
                sConsecutiveRetryWindowStartMs = 0L;
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                return;
            }
            if (BuildProfile.ENABLE_S6_DIRECT_BINDER && tryDirectBinderDisableSafeVolume(context, am, target)) {
                sDialogBurstCount = 0;
                sConsecutiveDialogRetries = 0;
                sConsecutiveRetryWindowStartMs = 0L;
                sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
                return;
            }
            sLastEnforceElapsedMs = SystemClock.elapsedRealtime();
            requestConfirmDialog(context, am, target);
        } finally {
            sIsEnforcing = false;
        }
    }

    private static void tryProactiveDirectBinderDisarm(Context context, String reason) {
        if (context == null || !BuildProfile.ENABLE_S6_DIRECT_BINDER || Build.VERSION.SDK_INT > 25 || !isTargetDevice()) return;
        try {
            int safeState = Settings.Global.getInt(context.getContentResolver(), "audio_safe_volume_state", 0);
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            syncTargetFromSystem(context);
            int eff = effectiveTargetStep();
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            // Skip unnecessary Settings.Secure touch when already unlocked and not clamped.
            if (safeState == 2 && sBootBinderDisarmed && (eff <= SAFE_CLAMP_STEP || cur >= eff)) {
                return;
            }
            if (safeState == 3 || !sBootBinderDisarmed) {
                int t = (isWired(context) && eff > SAFE_CLAMP_STEP) ? eff : -1;
                if (tryDirectBinderDisableSafeVolume(context, am, t)) {
                    Log.i(TAG, "Direct-Binder disarm OK (" + reason + ", prevDb=" + safeState + ")");
                }
            }
        } catch (Throwable ignored) {}
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
        return null;
    }

    private static boolean tryDirectBinderDisableSafeVolume(Context context, AudioManager am, int target) {
        if (context == null || am == null || !BuildProfile.ENABLE_S6_DIRECT_BINDER || Build.VERSION.SDK_INT > 25) return false;
        synchronized (sBinderUnlockSync) {
            ContentResolver cr = context.getContentResolver();
            Object ias = getIAudioService();
            if (ias == null) return false;
            Method disableMethod;
            try {
                disableMethod = ias.getClass().getMethod("disableSafeMediaVolume", String.class);
            } catch (Throwable t) {
                return false;
            }
            String prevComp = null;
            boolean wroteComp = false;
            try {
                prevComp = sanitizeRestoredControllerComponent(Settings.Secure.getString(cr, SECURE_VOL_CTRL_COMP));
                if (!Settings.Secure.putString(cr, SECURE_VOL_CTRL_COMP, AUTH_CONTROLLER_COMP)) return false;
                wroteComp = true;

                boolean unlocked = false;
                for (int attempt = 1; attempt <= 12; attempt++) {
                    try {
                        disableMethod.invoke(ias, context.getPackageName());
                        unlocked = true;
                        break;
                    } catch (Throwable invEx) {
                        Throwable cause = invEx.getCause() != null ? invEx.getCause() : invEx;
                        if (cause instanceof SecurityException) SystemClock.sleep(25L);
                        else break;
                    }
                }
                if (!unlocked) return false;

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
                return (target > SAFE_CLAMP_STEP && wiredNow) ? (after >= target) : true;
            } catch (Throwable t) {
                return false;
            } finally {
                if (wroteComp) {
                    try { Settings.Secure.putString(cr, SECURE_VOL_CTRL_COMP, prevComp); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void wakeUpScreenForDialog(Context context) {
        if (context == null || !isDedicatedGeneratorDevice()) return;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            synchronized (sWakeLockSync) {
                if (sActiveWakeLock == null) {
                    sActiveWakeLock = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "vorta:SynergySafeWake");
                    sActiveWakeLock.setReferenceCounted(false);
                }
                if (!pm.isInteractive() || !sActiveWakeLock.isHeld()) sActiveWakeLock.acquire(7000L);
            }
        } catch (Throwable ignored) {}
    }

    public static void releaseDialogWakeLock() {
        synchronized (sWakeLockSync) {
            if (sActiveWakeLock != null) {
                try { if (sActiveWakeLock.isHeld()) sActiveWakeLock.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void requestConfirmDialog(final Context context, AudioManager am, final int target) {
        long now = SystemClock.elapsedRealtime();
        if (shouldResetDialogRetryCounter(now, sConsecutiveRetryWindowStartMs, sConsecutiveDialogRetries)) {
            sConsecutiveDialogRetries = 0;
            sConsecutiveRetryWindowStartMs = 0L;
        }
        if (now - sDialogBurstWindowStartMs >= DIALOG_RETRY_WINDOW_MS) {
            sDialogBurstWindowStartMs = now;
            sDialogBurstCount = 0;
        }
        if (sDialogBurstCount >= DIALOG_BURST_MAX || (now - sLastUiPopupTriggerMs < DIALOG_BURST_SPACING_MS)) return;
        sLastUiPopupTriggerMs = now;
        sDialogBurstCount++;
        sLastOwnSetMs = now;
        markSelfWrite();

        wakeUpScreenForDialog(context);
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                long deadline = SystemClock.elapsedRealtime() + 1200L;
                while (!pm.isInteractive() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(40L);
                }
                SystemClock.sleep(250L);
            }
        } catch (Throwable ignored) {}

        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS);
        SynergyAccessibilityService.requestActiveConfirmScan();

        final Context appCtx = context.getApplicationContext();
        final Handler gh = ensureGuardHandler();
        gh.postDelayed(new Runnable() {
            @Override
            public void run() {
                releaseDialogWakeLock();
                if (!isWired(appCtx)) return;
                AudioManager cam = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
                if (cam == null) return;
                int cur = cam.getStreamVolume(AudioManager.STREAM_MUSIC);
                int want = effectiveTargetStep();
                if (want > SAFE_CLAMP_STEP && cur <= SAFE_CLAMP_STEP && cur < want) {
                    if (sConsecutiveDialogRetries < 3) {
                        if (sConsecutiveDialogRetries == 0) {
                            sConsecutiveRetryWindowStartMs = SystemClock.elapsedRealtime();
                        }
                        sConsecutiveDialogRetries++;
                        gh.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                recoverOnGuardThread(appCtx, "LOCKSCREEN_RETRY_" + sConsecutiveDialogRetries);
                            }
                        }, 5000L);
                    }
                } else {
                    sConsecutiveDialogRetries = 0;
                    sConsecutiveRetryWindowStartMs = 0L;
                    sDialogBurstCount = 0;
                }
            }
        }, 4500L);
    }

    public static void onDialogConfirmed(Context context) {
        releaseDialogWakeLock();
        sConsecutiveDialogRetries = 0;
        sConsecutiveRetryWindowStartMs = 0L;
        sDialogBurstCount = 0;
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        Handler gh = ensureGuardHandler();
        for (long d : new long[]{80L, 350L}) {
            gh.postDelayed(new Runnable() {
                @Override public void run() { recoverOnGuardThread(appCtx, "DIALOG_CONFIRMED"); }
            }, d);
        }
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
