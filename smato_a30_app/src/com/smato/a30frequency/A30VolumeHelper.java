package com.smato.a30frequency;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Samsung Safe-Volume guard (Free manual volume + safe-volume neutralization) for smato_a30_app.
 * Tracks user manual volume (0..15) in SharedPreferences; restores ONLY OS safe-volume clamps
 * on S6/S7 and locks pure uncolored stereo DSP flags without touching user volume on A30.
 */
public final class A30VolumeHelper {
    private static final String TAG = "A30VolumeHelper";

    public static final int LEVELING_TARGET_STEP = 13;   // reference default if never set
    public static final int MAX_VOLUME_STEP = 15;
    public static final int SAFE_CLAMP_STEP = 9;         // Samsung mSafeMediaVolumeIndex 90 / 10
    private static final int FLAG_SHOW_UI_WARNINGS = 1 << 10; // triggers SystemUI safe dialog
    private static final int TYPE_USB_HEADSET_API26 = 22;
    private static final int TYPE_AUX_LINE_API21 = 19;

    private static final String PREF_NAME = "vorta_a30_vol_prefs";
    private static final String KEY_USER_TARGET_VOL = "user_target_vol";

    private static final long OWN_CHANGE_WINDOW_MS = 1500L;
    private static final long PLUG_WINDOW_MS = 4000L;
    private static final long DIALOG_BURST_SPACING_MS = 2500L;
    private static final long DIALOG_RETRY_MS = 10L * 60L * 1000L;
    private static final int DIALOG_BURST_MAX = 3;
    private static final long[] PLUG_RESTORE_DELAYS_MS = {120L, 350L, 800L, 1600L, 3000L};

    public static volatile int sTargetVolumeStep = -1;
    private static volatile boolean sTargetInitialized = false;
    private static volatile boolean sLastSyncedWired = false;
    private static final String WIRED_DE_PREF_NAME = "vorta_a30_wired_de_prefs";
    private static final String KEY_WIRED_TARGET_VOL = "wired_target_vol";
    private static android.os.Handler sSafeVolWorkerHandler = null;

    private static synchronized android.os.Handler getSafeVolWorkerHandler() {
        if (sSafeVolWorkerHandler == null) {
            android.os.HandlerThread ht = new android.os.HandlerThread("A30SafeVolWorker");
            ht.start();
            sSafeVolWorkerHandler = new android.os.Handler(ht.getLooper());
        }
        return sSafeVolWorkerHandler;
    }

    private static Context getDeStorageContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        Context base = app != null ? app : context;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                Context de = base.createDeviceProtectedStorageContext();
                if (de != null) return de;
            } catch (Throwable ignored) {}
        }
        return base;
    }

    private static int readWiredTargetVol(Context context) {
        try {
            Context de = getDeStorageContext(context);
            if (de == null) return -1;
            return de.getSharedPreferences(WIRED_DE_PREF_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_WIRED_TARGET_VOL, -1);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void persistWiredTargetVol(Context context, int step) {
        if (step < 0 || step > MAX_VOLUME_STEP) return;
        try {
            Context de = getDeStorageContext(context);
            if (de == null) return;
            de.getSharedPreferences(WIRED_DE_PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_WIRED_TARGET_VOL, step)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static String getModelUpper() {
        try {
            String m = (android.os.Build.MODEL != null ? android.os.Build.MODEL : "")
                    + " " + (android.os.Build.DEVICE != null ? android.os.Build.DEVICE : "")
                    + " " + (android.os.Build.PRODUCT != null ? android.os.Build.PRODUCT : "");
            return m.toUpperCase(java.util.Locale.US);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isS6Model() {
        String u = getModelUpper();
        return u.contains("G920") || u.contains("G925") || u.contains("G928")
                || u.contains("ZEROFLTE") || u.contains("ZEROLTE");
    }

    private static boolean isS7Model() {
        String u = getModelUpper();
        return u.contains("G930") || u.contains("G935")
                || u.contains("HEROLTE") || u.contains("HERO2LTE");
    }

    private static String getAuthoritativeTargetPkgIfEnabled(Context context) {
        if (context == null) return null;
        String pkg;
        String clsShort;
        String clsFull;
        if (isS6Model()) {
            pkg = "com.vorta.s6frequency";
            clsShort = "com.vorta.s6frequency/.S6AccessibilityService";
            clsFull = "com.vorta.s6frequency/com.vorta.s6frequency.S6AccessibilityService";
        } else if (isS7Model()) {
            pkg = "com.vorta.s7frequency";
            clsShort = "com.vorta.s7frequency/.S7AccessibilityService";
            clsFull = "com.vorta.s7frequency/com.vorta.s7frequency.S7AccessibilityService";
        } else {
            return null;
        }
        try {
            String enabled = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null && (enabled.contains(clsFull) || enabled.contains(clsShort))) {
                return pkg;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void triggerLocalWakeAndWarningDialog(Context context, final int target, final String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        long now = SystemClock.elapsedRealtime();
        if (now - sDialogBurstWindowStartMs >= DIALOG_RETRY_MS) {
            sDialogBurstWindowStartMs = now;
            sDialogBurstCount = 0;
        }
        if (sDialogBurstCount >= DIALOG_BURST_MAX || (now - sLastDialogMs < DIALOG_BURST_SPACING_MS)) {
            return;
        }
        sLastDialogMs = now;
        sDialogBurstCount++;
        final int attempt = sDialogBurstCount;
        getSafeVolWorkerHandler().post(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = null;
                try {
                    AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
                    if (am == null || !isWired(appCtx)) return;
                    int curNow = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (curNow > SAFE_CLAMP_STEP && curNow >= target) {
                        sDialogBurstCount = 0;
                        return;
                    }
                    PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) {
                        wl = pm.newWakeLock(
                                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "vorta:A30SafeWake");
                        wl.acquire(4000L);
                        long deadline = SystemClock.uptimeMillis() + 1500L;
                        while (!pm.isInteractive() && SystemClock.uptimeMillis() < deadline) {
                            SystemClock.sleep(50L);
                        }
                        SystemClock.sleep(250L);
                    }
                    sLastOwnSetMs = SystemClock.elapsedRealtime();
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS);
                    Log.i(TAG, "safe state ACTIVE (" + reason + ") -> requested SystemUI confirm dialog (" + attempt + "/" + DIALOG_BURST_MAX + ")");
                } catch (Throwable t) {
                    Log.w(TAG, "triggerLocalWakeAndWarningDialog: " + t.getMessage());
                } finally {
                    if (wl != null) {
                        try {
                            if (wl.isHeld()) wl.release();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        });
    }
    private static volatile boolean sIsPlaying = false;
    private static volatile boolean sAudioEffectsLockedOnce = false;
    private static volatile long sLastPlugMs = -100000L;
    private static volatile long sLastOwnSetMs = -100000L;
    private static volatile long sLastDialogMs = -DIALOG_RETRY_MS;
    private static volatile long sDialogBurstWindowStartMs = -DIALOG_RETRY_MS;
    private static volatile int sDialogBurstCount = 0;
    private static volatile long sLastWakeAttemptMs = 0L;
    private static Handler sMain;

    private A30VolumeHelper() {}

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

    private static void persistTargetVolume(Context context, int step) {
        if (context == null || step < 0 || step > MAX_VOLUME_STEP) return;
        try {
            SharedPreferences sp = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_USER_TARGET_VOL, step).apply();
        } catch (Throwable ignored) {}
    }

    private static int readPersistedTargetVolume(Context context) {
        if (context == null) return -1;
        try {
            SharedPreferences sp = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            return sp.getInt(KEY_USER_TARGET_VOL, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static synchronized void syncTargetFromSystem(Context context) {
        if (context == null || isPersonalDevice()) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur < 0 || cur > MAX_VOLUME_STEP) return;
            boolean wired = !isA30Device() && isWired(context);

            if (wired) {
                int saved = readWiredTargetVol(context);
                boolean routeChanged = !sTargetInitialized || !sLastSyncedWired;
                if (routeChanged) {
                    if (saved >= 0 && saved <= MAX_VOLUME_STEP) {
                        if (saved > SAFE_CLAMP_STEP && cur == SAFE_CLAMP_STEP) {
                            sTargetVolumeStep = saved;
                        } else if (cur != saved) {
                            sTargetVolumeStep = cur;
                            persistWiredTargetVol(context, cur);
                        } else {
                            sTargetVolumeStep = saved;
                        }
                    } else {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVol(context, cur);
                    }
                    sTargetInitialized = true;
                    sLastSyncedWired = true;
                } else {
                    boolean clamped = sTargetVolumeStep > SAFE_CLAMP_STEP
                            && cur == SAFE_CLAMP_STEP && cur < sTargetVolumeStep;
                    if (!clamped && cur != sTargetVolumeStep) {
                        sTargetVolumeStep = cur;
                        persistWiredTargetVol(context, cur);
                    }
                }
            } else {
                sTargetVolumeStep = cur;
                sTargetInitialized = true;
                sLastSyncedWired = false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "syncTargetFromSystem: " + t.getMessage());
        }
    }

    public static void saveTargetVolume(Context context, int step) {
        if (step >= 0 && step <= MAX_VOLUME_STEP) {
            sTargetVolumeStep = step;
            sTargetInitialized = true;
            persistTargetVolume(context, step);
        }
    }

    public static int loadTargetVolume(Context context) {
        syncTargetFromSystem(context);
        return sTargetVolumeStep;
    }

    public static int getTargetVolumeStep() { return sTargetVolumeStep >= 0 ? sTargetVolumeStep : 0; }

    public static int getTargetVolumeStep(Context context) {
        syncTargetFromSystem(context);
        return sTargetVolumeStep >= 0 ? sTargetVolumeStep : 0;
    }

    private static synchronized Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    /** Settings flags (read-before-write, so repeated calls cost reads only). */
    public static void enforceSystemFlags(Context context) {
        if (context == null || isPersonalDevice()) return;
        if (isA30Device() && sAudioEffectsLockedOnce) return;
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
                putSystemIntSafe(cr, "dolby_atmos_mode", 0);
                putGlobalIntSafe(cr, "multisound_state", 0);
                try {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.setParameters("g_effect_dolby_enable=0;g_effect_game_dolby_enable=0");
                } catch (Throwable ignored) {}
            }
            if (isA30Device()) return;
            putGlobalIntSafe(cr, "audio_safe_volume_state", 2);
            putSecureIntSafe(cr, "audio_safe_volume_state", 2);
            putSecureIntSafe(cr, "unsafe_volume_music_active_ms", 0);
            putSystemIntSafe(cr, "volume_limiter", 0);
            putSystemIntSafe(cr, "volume_limiter_enable", 0);
            putSystemIntSafe(cr, "volumelimit_on", 0);
            putSystemIntSafe(cr, "volume_limiter_value", 15);
            int t = sTargetVolumeStep;
            if (t > SAFE_CLAMP_STEP && t <= MAX_VOLUME_STEP) {
                putSystemIntSafe(cr, "volume_music_headphone", t);
                putSystemIntSafe(cr, "volume_music_headset", t);
            }
        } catch (Throwable t) {
            Log.w(TAG, "enforceSystemFlags: " + t.getMessage());
        }
    }

    public static synchronized void ensureTargetVolumeIfNeeded(Context context, boolean forceResetTo85) {
        if (isPersonalDevice()) return;
        syncTargetFromSystem(context);
        enforceSystemFlags(context);
        if (!isA30Device()) {
            restoreIfClamped(context, "ensure");
        }
    }

    public static synchronized void enforceDefaultVolume85(Context context) {
        if (isPersonalDevice()) return;
        Log.i(TAG, "enforceDefaultVolume85 neutralized -> delegating to ensureTargetVolumeIfNeeded (free volume)");
        ensureTargetVolumeIfNeeded(context, false);
    }

    /** Explicit calibration helper (only when explicitly requested). */
    public static synchronized void calibrateToStep(Context context, int targetStep) {
        if (context == null || isPersonalDevice()) return;
        enforceSystemFlags(context);
        if (isA30Device()) return;
        saveTargetVolume(context, targetStep);
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            applyWiredIndexes(context, targetStep);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur != targetStep) {
                sLastOwnSetMs = SystemClock.elapsedRealtime();
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0);
                Log.i(TAG, "calibrate " + cur + " -> " + targetStep);
            }
            restoreIfClamped(context, "calibrate");
        } catch (Throwable t) {
            Log.w(TAG, "calibrateToStep: " + t.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    public static void wakeUpScreenForDialog(Context context) {
        if (context == null || isPersonalDevice()) return;
        long now = SystemClock.uptimeMillis();
        if (now - sLastWakeAttemptMs < 1500L) return;
        sLastWakeAttemptMs = now;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP, "vorta:A30SafeWake");
                wl.acquire(3000L);
            }
        } catch (Throwable ignored) {}
    }

    /** Persists headset(0x4)/headphone(0x8) STREAM_MUSIC indexes in Settings.System. */
    public static void applyWiredIndexes(Context context, int step) {
        if (context == null || isPersonalDevice() || isA30Device()) return;
        if (step < 0 || step > MAX_VOLUME_STEP) return;
        ContentResolver cr = context.getContentResolver();
        putSystemIntSafe(cr, "volume_music_headset", step);
        putSystemIntSafe(cr, "volume_music_headphone", step);
    }

    /** Service receiver: ACTION_HEADSET_PLUG. state==1 restores user target (>9) after OS plug reset to 9. */
    public static void onHeadsetPlug(final Context context, int state) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        final Context app = context.getApplicationContext();
        sLastPlugMs = SystemClock.elapsedRealtime();
        if (state == 1) {
            sDialogBurstCount = 0;
            syncTargetFromSystem(app);
            if (sTargetVolumeStep <= SAFE_CLAMP_STEP) return;
            applyWiredIndexes(app, sTargetVolumeStep);
            for (long d : PLUG_RESTORE_DELAYS_MS) {
                main().postDelayed(new Runnable() {
                    @Override public void run() { restoreIfClamped(app, "plug"); }
                }, d);
            }
        } else {
            main().postDelayed(new Runnable() {
                @Override public void run() {
                    enforceSystemFlags(app);
                    if (sTargetVolumeStep > SAFE_CLAMP_STEP) {
                        applyWiredIndexes(app, sTargetVolumeStep);
                    }
                }
            }, 300L);
        }
    }

    /** Service receiver: VOLUME_CHANGED_ACTION. Follows user keys; restores only OS clamps. */
    public static void onVolumeChanged(Context context, Intent intent) {
        if (context == null || intent == null || isPersonalDevice()) return;
        if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != AudioManager.STREAM_MUSIC) return;
        int cur = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
        int prev = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
        if (cur < 0) return;
        long now = SystemClock.elapsedRealtime();
        if (now - sLastOwnSetMs < OWN_CHANGE_WINDOW_MS) return;
        if (sTargetVolumeStep < 0 || (!isA30Device() && isWired(context) != sLastSyncedWired)) {
            syncTargetFromSystem(context);
        }
        int target = sTargetVolumeStep;
        boolean wired = !isA30Device() && isWired(context);
        boolean plugWindow = (now - sLastPlugMs < PLUG_WINDOW_MS);
        boolean osClamp = wired && target > SAFE_CLAMP_STEP
                && cur == SAFE_CLAMP_STEP && cur < target
                && (plugWindow || (prev >= 0 && prev - cur >= 2) || prev <= SAFE_CLAMP_STEP);
        if (osClamp) {
            restoreIfClamped(context, "clamp " + prev + "->" + cur);
        } else if (wired) {
            saveTargetVolume(context, cur);
            persistWiredTargetVol(context, cur);
            if (cur > SAFE_CLAMP_STEP) {
                applyWiredIndexes(context, cur);
            }
        } else {
            sTargetVolumeStep = cur;
            sTargetInitialized = true;
            sLastSyncedWired = false;
        }
    }

    public static void periodicGuardTick(Context context) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        enforceSystemFlags(context);
        restoreIfClamped(context, "tick");
    }

    public static synchronized void restoreIfClamped(Context context, final String reason) {
        if (context == null || isA30Device() || isPersonalDevice()) return;
        syncTargetFromSystem(context);
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || !isWired(context)) return;
            final int target = sTargetVolumeStep;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (target <= SAFE_CLAMP_STEP || cur != SAFE_CLAMP_STEP || cur >= target) return;
            sLastOwnSetMs = SystemClock.elapsedRealtime();
            applyWiredIndexes(context, target);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int after = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            Log.i(TAG, "restore(" + reason + ") " + cur + " -> " + after + " target " + target);
            if (after >= target) {
                sDialogBurstCount = 0;
            } else {
                final String authPkg = getAuthoritativeTargetPkgIfEnabled(context);
                final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
                if (authPkg != null) {
                    try {
                        Intent bIntent = new Intent(authPkg + ".ACTION_SAFE_VOLUME_CHECK");
                        bIntent.setPackage(authPkg);
                        bIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
                        bIntent.putExtra("reason", "delegate_" + TAG + "_" + reason);
                        bIntent.putExtra("target_step", target);
                        appCtx.sendBroadcast(bIntent);
                        Log.i(TAG, "delegated safe-volume defeat to " + authPkg + " (target=" + target + ")");
                    } catch (Throwable bt) {
                        Log.w(TAG, "delegate broadcast failed: " + bt.getMessage());
                    }
                    getSafeVolWorkerHandler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AudioManager wam = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
                                if (wam == null || !isWired(appCtx)) return;
                                int postCur = wam.getStreamVolume(AudioManager.STREAM_MUSIC);
                                if (postCur > SAFE_CLAMP_STEP && postCur >= target) {
                                    sDialogBurstCount = 0;
                                    return;
                                }
                                if (postCur == SAFE_CLAMP_STEP && target > SAFE_CLAMP_STEP) {
                                    triggerLocalWakeAndWarningDialog(appCtx, target, "post_delegate_" + reason);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }, 1500L);
                } else {
                    triggerLocalWakeAndWarningDialog(appCtx, target, reason);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "restoreIfClamped: " + t.getMessage());
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

    private static void putSystemIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.System.getInt(cr, key, Integer.MIN_VALUE) != val) Settings.System.putInt(cr, key, val);
        } catch (Throwable ignored) {}
    }

    private static void putGlobalIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.Global.getInt(cr, key, Integer.MIN_VALUE) != val) Settings.Global.putInt(cr, key, val);
        } catch (Throwable ignored) {}
    }

    private static void putSecureIntSafe(ContentResolver cr, String key, int val) {
        try {
            if (Settings.Secure.getInt(cr, key, Integer.MIN_VALUE) != val) Settings.Secure.putInt(cr, key, val);
        } catch (Throwable ignored) {}
    }
}
