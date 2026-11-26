package com.vorta.unified;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unified OS-aware acoustic synthesis service supporting both Scenario Mode (S6/S7/A30)
 * and Multi-Sweep / Manual Pro Mode at hardware-native sample rate and 32-bit IEEE float.
 */
public class VortaAudioService extends Service {
    private static final String TAG = "VortaUnifiedAudio";
    private static final String AUDIT_TAG = "VortaAudioAudit";
    private static final String CHANNEL_ID = "vorta_unified_silent_channel";
    private static final int NOTIFICATION_ID = 5001;

    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static final float MAX_FREQ_HZ = 63.0f;
    public static final float MIN_FREQ_HZ = 0.5f;

    // Live runtime telemetry & oscilloscope verification ring
    public static final int SCOPE_RING_SIZE = 4096;
    private static final float[] sScopeRing = new float[SCOPE_RING_SIZE];
    private static final Object sScopeLock = new Object();
    private static int sScopeWritePos = 0;

    public static volatile boolean sIsEngineRunning = false;
    public static volatile boolean sAuxSuspended = false;
    public static volatile int sSampleRate = DEFAULT_SAMPLE_RATE;
    public static volatile boolean sIsFloatPipeline = false;
    public static volatile double sLivePeak = 0.0;
    public static volatile double sLiveRmsDbfs = -120.0;
    public static volatile double sLiveDcOffset = 0.0;
    public static volatile double sLiveThdPercent = -1.0;
    public static volatile boolean sLivePureMode = false;
    public static volatile int sLiveUnderrunCount = 0;
    public static volatile int sLiveClipCount = 0;
    public static volatile int sLiveAudioTrackPlayState = 0;
    public static volatile float sInstantDisplayHz = 0.0f;

    // Active Scenario playback state
    public static volatile String sActiveScenarioId = null;
    public static volatile boolean sScenarioPlaying = false;
    private static volatile ScenarioCatalog.ScenarioSpec sScenarioSpec = null;

    // Active Pro Multi-Sweep / Manual playback state
    public static volatile float sManualFreq = 28.0f;
    public static volatile boolean sManualDualEnabled = false;
    public static volatile float sManualSecondaryFreq = 32.0f;
    public static volatile boolean sManualFreqActive = false;

    public static volatile boolean sFmEnabled = false;
    public static volatile float sFmDepth = 0.0f;
    public static volatile float sFmRateHz = 0.0f;

    public static volatile boolean sAmEnabled = false;
    public static volatile float sAmDepth = 0.0f;
    public static volatile float sAmRateHz = 0.0f;

    public static volatile boolean sRmEnabled = false;
    public static volatile float sRmDepth = 0.0f;
    public static volatile float sRmRateHz = 0.0f;

    public static final List<SweepItem> sSweepItems = new ArrayList<SweepItem>();
    public static final Object sLock = new Object();

    private AudioTrack mAudioTrack;
    private Thread mAudioThread;
    private volatile boolean mThreadRunning = false;
    private volatile boolean mWiredGuardArmed = false;
    private volatile boolean mAuxSuspended = false;
    private final PureDspCore.AuxDebouncePolicy mAuxDebounce = new PureDspCore.AuxDebouncePolicy();

    private PowerManager.WakeLock mWakeLock;
    private BroadcastReceiver mExclusiveReceiver;
    private BroadcastReceiver mServiceVolReceiver;
    private AudioDeviceCallback mAudioDeviceCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mAuxSuspendCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkPendingAuxDisconnect();
        }
    };

    public static void copyScopeSnapshot(float[] dest) {
        if (dest == null || dest.length == 0) return;
        int len = Math.min(dest.length, SCOPE_RING_SIZE);
        synchronized (sScopeLock) {
            int wp = sScopeWritePos;
            for (int i = 0; i < len; i++) {
                dest[i] = sScopeRing[(wp + i) % SCOPE_RING_SIZE];
            }
        }
    }

    public static String buildSelfDiagnosticReport() {
        boolean playing = isAnySourceActive();
        String stateLabel;
        if (!playing) {
            stateLabel = "대기 중 (신호 출력 없음)";
        } else if (sAuxSuspended) {
            stateLabel = "유선 단자 분리로 무음 보호 중";
        } else if (sLiveClipCount > 0 || sLivePeak >= 0.999) {
            stateLabel = "주의: 클리핑/비정상 파형 감지";
        } else if (sLiveAudioTrackPlayState != AudioTrack.PLAYSTATE_PLAYING) {
            stateLabel = "주의: OS 오디오트랙 미재생 상태";
        } else if (sLivePureMode && sLiveThdPercent >= 0.0 && sLiveThdPercent > 0.1) {
            stateLabel = "주의: 순수 정현파 왜곡 임계치 초과";
        } else {
            stateLabel = "정상 실측 (클리핑 0 · -1.0 dBTP 범위 내)";
        }

        String thdStr;
        if (!playing) thdStr = "측정 대기";
        else if (!sLivePureMode) thdStr = "복합/스윕/변조 파형 (단일 THD 비적용)";
        else if (sLiveThdPercent < 0.0) thdStr = "파형 안정화 중 (계측 대기)";
        else thdStr = String.format(Locale.US, "%.4f%%", sLiveThdPercent);

        return String.format(Locale.US,
                "판정: %s\n"
                        + "프로필: %s\n"
                        + "파이프라인: %d Hz / %s\n"
                        + "현재 주파수: %.2f Hz\n"
                        + "현재 %s\n"
                        + "피크 / RMS: %.5f / %.1f dBFS\n"
                        + "DC 오프셋: %.6f\n"
                        + "실측 THD+N: %s\n"
                        + "HAL 버퍼 언더런: %d회\n"
                        + "클리핑·NaN 샘플: %d건\n\n"
                        + "※ 본 진단은 앱이 OS AudioTrack HAL로 전달하는 32-bit Float PCM과 버퍼 언더런 실측값입니다. 외부 앰프·스피커의 물리 출력은 별도 계측기로 확인하세요.",
                stateLabel,
                BuildProfile.TARGET_OS_LABEL,
                sSampleRate,
                sIsFloatPipeline ? "32-bit Float" : "16-bit TPDF",
                playing ? sInstantDisplayHz : 0.0f,
                formatActiveModulationTelemetry(),
                playing ? sLivePeak : 0.0,
                playing ? sLiveRmsDbfs : -120.0,
                playing ? sLiveDcOffset : 0.0,
                thdStr,
                sLiveUnderrunCount,
                sLiveClipCount);
    }

    public static String formatModulationSpec(
            boolean fmOn, float fmDepth, float fmRateHz,
            boolean amOn, float amDepth, float amRateHz,
            boolean rmOn, float rmDepth, float rmRateHz) {
        StringBuilder sb = new StringBuilder();
        if (fmOn && fmDepth > 0.0f) {
            sb.append(String.format(Locale.US, "FM %.1f%%(%.2fHz)", fmDepth * 100.0f, fmRateHz));
        }
        if (amOn && amDepth > 0.0f) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(String.format(Locale.US, "AM %.0f%%(%.2fHz)", amDepth * 100.0f, amRateHz));
        }
        if (rmOn && rmDepth > 0.0f) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(String.format(Locale.US, "RM %.0f%%(%.2fHz)", rmDepth * 100.0f, rmRateHz));
        }
        if (sb.length() == 0) {
            return "변조: OFF (순수 정현파)";
        }
        return "변조: " + sb.toString() + " · 고정";
    }

    public static String formatProModulationTelemetry() {
        return formatModulationSpec(
                sFmEnabled, sFmDepth, sFmRateHz,
                sAmEnabled, sAmDepth, sAmRateHz,
                sRmEnabled, sRmDepth, sRmRateHz);
    }

    public static boolean isActiveModulationOn() {
        ScenarioCatalog.ScenarioSpec spec = (sScenarioPlaying && sScenarioSpec != null) ? sScenarioSpec : null;
        if (spec != null) {
            return (spec.fmOn && spec.fmDepth > 0.0f)
                    || (spec.amOn && spec.amDepth > 0.0f)
                    || (spec.rmOn && spec.rmDepth > 0.0f);
        }
        return (sFmEnabled && sFmDepth > 0.0f)
                || (sAmEnabled && sAmDepth > 0.0f)
                || (sRmEnabled && sRmDepth > 0.0f);
    }

    public static String formatActiveModulationTelemetry() {
        ScenarioCatalog.ScenarioSpec spec = (sScenarioPlaying && sScenarioSpec != null) ? sScenarioSpec : null;
        if (spec != null) {
            return formatModulationSpec(
                    spec.fmOn, spec.fmDepth, spec.fmRateHz,
                    spec.amOn, spec.amDepth, spec.amRateHz,
                    spec.rmOn, spec.rmDepth, spec.rmRateHz);
        }
        return formatProModulationTelemetry();
    }

    public static void applyLockedPureModulation(boolean fmOn, boolean amOn, boolean rmOn) {
        synchronized (sLock) {
            sFmEnabled = fmOn;
            sFmDepth = fmOn ? ModulationProfile.LOCKED_FM_DEPTH : 0.0f;
            sFmRateHz = fmOn ? ModulationProfile.LOCKED_FM_RATE_HZ : 0.0f;
            sAmEnabled = amOn;
            sAmDepth = amOn ? ModulationProfile.LOCKED_AM_DEPTH : 0.0f;
            sAmRateHz = amOn ? ModulationProfile.LOCKED_AM_RATE_HZ : 0.0f;
            sRmEnabled = rmOn;
            sRmDepth = rmOn ? ModulationProfile.LOCKED_RM_DEPTH : 0.0f;
            sRmRateHz = rmOn ? ModulationProfile.LOCKED_RM_RATE_HZ : 0.0f;
        }
    }

    public static void playScenario(Context ctx, ScenarioCatalog.ScenarioSpec spec) {
        if (ctx == null || spec == null) return;
        synchronized (sLock) {
            sManualFreqActive = false;
            for (int i = 0; i < sSweepItems.size(); i++) sSweepItems.get(i).isPlaying = false;
            sScenarioSpec = spec;
            sActiveScenarioId = spec.id;
            sScenarioPlaying = true;
            sInstantDisplayHz = spec.isSweep ? spec.sweepMinHz : spec.primaryHz;
        }
        startServiceCompat(ctx);
    }

    public static void stopAllPlayback() {
        synchronized (sLock) {
            sScenarioPlaying = false;
            sActiveScenarioId = null;
            sScenarioSpec = null;
            sManualFreqActive = false;
            for (int i = 0; i < sSweepItems.size(); i++) sSweepItems.get(i).isPlaying = false;
        }
        sInstantDisplayHz = 0.0f;
        sLivePeak = 0.0;
        sLiveRmsDbfs = -120.0;
        sLiveDcOffset = 0.0;
        sLiveThdPercent = -1.0;
        sLivePureMode = false;
        sLiveClipCount = 0;
        synchronized (sScopeLock) {
            java.util.Arrays.fill(sScopeRing, 0.0f);
            sScopeWritePos = 0;
        }
    }

    public static boolean isAnySourceActive() {
        if (sScenarioPlaying && sScenarioSpec != null) return true;
        if (sManualFreqActive) return true;
        synchronized (sLock) {
            for (int i = 0; i < sSweepItems.size(); i++) {
                if (sSweepItems.get(i).isPlaying) return true;
            }
        }
        return false;
    }

    public static void startServiceCompat(Context ctx) {
        if (ctx == null) return;
        Intent intent = new Intent(ctx, VortaAudioService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createSilentNotificationChannel();
        acquireWakeLock();
        VortaVolumeGuard.syncTargetFromSystem(this);
        VortaVolumeGuard.ensureTargetVolumeIfNeeded(this);
        registerExclusiveReceiver();
        registerVolumeGuardReceiver();
        VortaVolumeGuard.startBackgroundGuards(this);
        registerAuxDeviceCallback();
    }

    private AudioDeviceInfo findWiredOutputDevice(AudioManager am) {
        if (am == null || Build.VERSION.SDK_INT < 23) return null;
        try {
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devs != null) {
                for (AudioDeviceInfo d : devs) {
                    if (VortaVolumeGuard.isWiredDeviceType(d.getType())) return d;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void registerAuxDeviceCallback() {
        if (Build.VERSION.SDK_INT < 23 || mAudioDeviceCallback != null) return;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            mAudioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                    handleWiredStateChange("DEVICE_ADDED");
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                    handleWiredStateChange("DEVICE_REMOVED");
                }
            };
            am.registerAudioDeviceCallback(mAudioDeviceCallback, mHandler);
        } catch (Throwable ignored) {}
    }

    private void handleWiredStateChange(String reason) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
            boolean wiredNow = (wiredDev != null) || VortaVolumeGuard.isWired(this);
            if (wiredNow) {
                mAuxDebounce.onHardwareConnected();
                mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
                if (mThreadRunning) mWiredGuardArmed = true;
                if (mAudioTrack != null && wiredDev != null && BuildProfile.USE_SET_PREFERRED_DEVICE && Build.VERSION.SDK_INT >= 23) {
                    try { mAudioTrack.setPreferredDevice(wiredDev); } catch (Throwable ignored) {}
                }
                if (mAuxSuspended) {
                    mAuxSuspended = false;
                    sAuxSuspended = false;
                    VortaVolumeGuard.onHeadsetPlug(this, 1);
                    if (mThreadRunning) updateNotification(buildTelemetryText());
                    Log.i(TAG, "[AUX_GUARD] Wired reconnected (" + reason + ") -> resumed seamlessly");
                }
            } else if (mThreadRunning && mWiredGuardArmed && !mAuxSuspended) {
                if (mAudioTrack != null && Build.VERSION.SDK_INT >= 23) {
                    try { mAudioTrack.setPreferredDevice(null); } catch (Throwable ignored) {}
                }
                if (mAuxDebounce.onHardwareDisconnected(SystemClock.elapsedRealtime(), mAuxSuspended)) {
                    engageAuxMute(reason);
                } else {
                    mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
                    mHandler.postDelayed(mAuxSuspendCheckRunnable, PureDspCore.AUX_DISCONNECT_DEBOUNCE_MS + 15L);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void checkPendingAuxDisconnect() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            boolean wiredNow = (findWiredOutputDevice(am) != null) || VortaVolumeGuard.isWired(this);
            if (mAuxDebounce.pollDebounceExpired(SystemClock.elapsedRealtime(), wiredNow, mAuxSuspended)) {
                engageAuxMute("DEBOUNCED_DISCONNECT");
            } else if (wiredNow && mAuxSuspended) {
                handleWiredStateChange("SELF_CHECK_RECOVER");
            }
        } catch (Throwable ignored) {}
    }

    private void engageAuxMute(String reason) {
        if (mAuxSuspended) return;
        mAuxSuspended = true;
        sAuxSuspended = true;
        VortaVolumeGuard.onHeadsetPlug(this, 0);
        updateNotification("AUX 분리 감지 - 스피커 차단 / 재연결 대기");
        Log.w(TAG, "[AUX_GUARD] Disconnected (" + reason + ") -> speaker muted");
    }

    private void registerVolumeGuardReceiver() {
        if (VortaVolumeGuard.isPersonalDevice() || mServiceVolReceiver != null) return;
        try {
            mServiceVolReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null || intent.getAction() == null) return;
                    String action = intent.getAction();
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                        VortaVolumeGuard.onVolumeChanged(ctx, intent);
                    } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                        int st = intent.getIntExtra("state", 0);
                        VortaVolumeGuard.enforceSystemFlags(ctx);
                        VortaVolumeGuard.onHeadsetPlug(ctx, st);
                        handleWiredStateChange("HEADSET_PLUG_" + st);
                    } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                        handleWiredStateChange("BECOMING_NOISY");
                    } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)) {
                        VortaVolumeGuard.recoverFromSafeVolumeClamp(ctx, "SAFE_ACTIVE");
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction("android.media.VOLUME_CHANGED_ACTION");
            f.addAction(Intent.ACTION_HEADSET_PLUG);
            f.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            f.addAction("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(mServiceVolReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(mServiceVolReceiver, f);
        } catch (Throwable ignored) {}
    }

    private void registerExclusiveReceiver() {
        if (mExclusiveReceiver != null) return;
        try {
            mExclusiveReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    if ("com.vorta.ACTION_EXCLUSIVE_AUDIO_START".equals(intent.getAction())) {
                        String sender = intent.getStringExtra("sender_pkg");
                        if (sender != null && !sender.equals(getPackageName())) {
                            stopAllPlayback();
                            mThreadRunning = false;
                            stopForeground(true);
                            stopSelf();
                        }
                    }
                }
            };
            IntentFilter f = new IntentFilter("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(mExclusiveReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(mExclusiveReceiver, f);
        } catch (Throwable ignored) {}
    }

    private void createSilentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(CHANNEL_ID, "보르타 통합 무음 채널", NotificationManager.IMPORTANCE_LOW);
            chan.setSound(null, null);
            chan.enableVibration(false);
            chan.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(chan);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorta:UnifiedAudioWake");
                mWakeLock.acquire(24 * 60 * 60 * 1000L);
            }
        } catch (Throwable ignored) {}
    }

    private String buildTelemetryText() {
        return String.format(Locale.US, "%s | %dHz %s | -1.0 dBTP",
                BuildProfile.FLAVOR_ID, sSampleRate, sIsFloatPipeline ? "32-bit Float" : "16-bit TPDF");
    }

    private Notification buildNotification(String text) {
        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, notifyIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("VORTA v5.2 (" + BuildProfile.FLAVOR_ID + ")")
         .setContentText(text)
         .setSmallIcon(R.mipmap.ic_launcher)
         .setContentIntent(pi)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setDefaults(0);
        return b.build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(NOTIFICATION_ID, buildNotification("오디오 엔진 준비 중"));
        }
        if (intent == null && !mThreadRunning) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && "stop".equalsIgnoreCase(intent.getStringExtra("action"))) {
            stopAllPlayback();
            mThreadRunning = false;
            sIsEngineRunning = false;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            excl.putExtra("sender_pkg", getPackageName());
            sendBroadcast(excl);
        } catch (Throwable ignored) {}
        startForeground(NOTIFICATION_ID, buildNotification(buildTelemetryText()));
        sIsEngineRunning = true;
        startAudioLoop();
        return START_STICKY;
    }

    private int queryNativeSampleRate(AudioManager am) {
        if (am != null) {
            try {
                String sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                if (sr != null) {
                    int val = Integer.parseInt(sr.trim());
                    if (val >= 44100 && val <= 192000) return val;
                }
            } catch (Throwable ignored) {}
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private int queryNativeFramesPerBuffer(AudioManager am) {
        if (am != null) {
            try {
                String f = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                if (f != null) {
                    int val = Integer.parseInt(f.trim());
                    if (val >= 64 && val <= 4096) return val;
                }
            } catch (Throwable ignored) {}
        }
        return 960;
    }

    private AudioTrack buildAudioTrack(AudioManager am, int sampleRate, int chunkFrames, boolean tryFloat) {
        AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
        int encoding = (tryFloat && Build.VERSION.SDK_INT >= 21)
                ? AudioFormat.ENCODING_PCM_FLOAT
                : AudioFormat.ENCODING_PCM_16BIT;
        int bytesPerFrame = (encoding == AudioFormat.ENCODING_PCM_FLOAT) ? 8 : 4;
        int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, encoding);
        int targetBuf = Math.max(Math.max(minBuf * 4, chunkFrames * 6 * bytesPerFrame), (sampleRate / 2) * bytesPerFrame);
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            AudioTrack track = new AudioTrack(attrs, fmt, targetBuf, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                return null;
            }
            if (wiredDev != null && BuildProfile.USE_SET_PREFERRED_DEVICE && Build.VERSION.SDK_INT >= 23) {
                try { track.setPreferredDevice(wiredDev); } catch (Throwable ignored) {}
            }
            return track;
        } catch (Throwable t) {
            return null;
        }
    }

    private AudioTrack createBestAudioTrack(AudioManager am, int sampleRate, int chunkFrames) {
        AudioTrack t = buildAudioTrack(am, sampleRate, chunkFrames, true);
        if (t != null) {
            sIsFloatPipeline = true;
            Log.i(TAG, "AudioTrack initialized: SR=" + sampleRate + "Hz chunkFrames=" + chunkFrames
                    + " encoding=ENCODING_PCM_FLOAT");
            return t;
        }
        sIsFloatPipeline = false;
        AudioTrack fallback = buildAudioTrack(am, sampleRate, chunkFrames, false);
        if (fallback != null) {
            Log.i(TAG, "AudioTrack initialized: SR=" + sampleRate + "Hz chunkFrames=" + chunkFrames
                    + " encoding=ENCODING_PCM_16BIT_TPDF");
        } else {
            Log.e(TAG, "AudioTrack initialization failed for SR=" + sampleRate + "Hz");
        }
        return fallback;
    }

    private void releaseTrackQuietly(AudioTrack track) {
        if (track == null) return;
        try { track.pause(); } catch (Throwable ignored) {}
        try { track.flush(); } catch (Throwable ignored) {}
        try { track.stop(); } catch (Throwable ignored) {}
        try { track.release(); } catch (Throwable ignored) {}
    }

    private synchronized void startAudioLoop() {
        if (mThreadRunning) return;
        mThreadRunning = true;

        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int sampleRate = queryNativeSampleRate(am);
        sSampleRate = sampleRate;
        final int halFrames = queryNativeFramesPerBuffer(am);
        final int mult = Math.max(1, (int) Math.ceil((sampleRate * 0.060) / (double) halFrames));
        final int chunkFrames = halFrames * mult;

        mWiredGuardArmed = (findWiredOutputDevice(am) != null) || VortaVolumeGuard.isWired(this);
        mAuxSuspended = false;
        sAuxSuspended = false;
        mAuxDebounce.onHardwareConnected();
        mAudioTrack = createBestAudioTrack(am, sampleRate, chunkFrames);
        updateNotification(buildTelemetryText());

        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                final float[] floatBuf = new float[chunkFrames * 2];
                final short[] pcm16Buf = new short[chunkFrames * 2];
                final float[] scopeSnap = new float[SCOPE_RING_SIZE];
                final PureDspCore.DspState st = new PureDspCore.DspState();
                boolean wasActive = false;

                if (mAudioTrack != null) {
                    writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                    writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                    try { mAudioTrack.play(); } catch (Throwable ignored) {}
                }

                long lastAuditMs = 0L;
                long lastMeasureMs = 0L;
                long lastSelfCheckMs = 0L;

                while (mThreadRunning) {
                    ScenarioCatalog.ScenarioSpec activeScen = sScenarioPlaying ? sScenarioSpec : null;
                    boolean manualOn = (activeScen == null) && sManualFreqActive;
                    boolean anySweepPlaying = false;
                    synchronized (sLock) {
                        for (int i = 0; i < sSweepItems.size(); i++) {
                            if (sSweepItems.get(i).isPlaying) {
                                anySweepPlaying = true;
                                break;
                            }
                        }
                    }
                    boolean chunkHasActive = (activeScen != null) || manualOn || anySweepPlaying;

                    if (chunkHasActive && !wasActive) {
                        wasActive = true;
                        lastMeasureMs = 0L;
                        try {
                            Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
                            excl.putExtra("sender_pkg", getPackageName());
                            sendBroadcast(excl);
                        } catch (Throwable ignored) {}
                    } else if (!chunkHasActive && wasActive) {
                        wasActive = false;
                    }

                    float dispHz = PureDspCore.renderUnifiedChunk(
                            floatBuf, chunkFrames, sampleRate, mAuxSuspended,
                            activeScen, manualOn, sManualFreq, sManualDualEnabled, sManualSecondaryFreq,
                            sSweepItems, sLock,
                            sFmEnabled, sFmDepth, sFmRateHz,
                            sAmEnabled, sAmDepth, sAmRateHz,
                            sRmEnabled, sRmDepth, sRmRateHz,
                            st);
                    if (chunkHasActive && dispHz > 0.0f) {
                        sInstantDisplayHz = dispHz;
                    }

                    int chunkClips = 0;
                    synchronized (sScopeLock) {
                        for (int i = 0; i < chunkFrames; i++) {
                            float lv = floatBuf[i * 2];
                            if (Float.isNaN(lv) || Float.isInfinite(lv) || Math.abs(lv) >= 0.999f) {
                                chunkClips++;
                            }
                            sScopeRing[sScopeWritePos] = lv;
                            sScopeWritePos = (sScopeWritePos + 1) % SCOPE_RING_SIZE;
                        }
                    }
                    if (chunkClips > 0) sLiveClipCount += chunkClips;

                    long nowMs = SystemClock.elapsedRealtime();
                    if (chunkHasActive && !mAuxSuspended && (nowMs - lastMeasureMs >= 480L || lastMeasureMs == 0L)) {
                        lastMeasureMs = nowMs;
                        copyScopeSnapshot(scopeSnap);
                        boolean pureMode;
                        double f1 = 0.0;
                        double f2 = 0.0;
                        if (activeScen != null) {
                            pureMode = !activeScen.isSweep && !activeScen.fmOn && !activeScen.amOn && !activeScen.rmOn;
                            f1 = activeScen.primaryHz;
                            f2 = activeScen.secondaryHz;
                        } else if (manualOn && !anySweepPlaying) {
                            pureMode = !sFmEnabled && !sAmEnabled && !sRmEnabled;
                            f1 = sManualFreq;
                            f2 = sManualDualEnabled ? sManualSecondaryFreq : 0.0;
                        } else {
                            pureMode = false;
                        }
                        sLivePureMode = pureMode;
                        PureDspCore.LiveQualityReport rep = PureDspCore.measureLiveBufferQuality(
                                scopeSnap, SCOPE_RING_SIZE, sampleRate, pureMode && st.envGain >= 0.999, f1, f2);
                        sLivePeak = rep.peak;
                        sLiveRmsDbfs = rep.rmsDbfs;
                        sLiveDcOffset = rep.dcOffset;
                        sLiveThdPercent = rep.thdPlusNPercent;

                        if (mAudioTrack != null) {
                            sLiveAudioTrackPlayState = mAudioTrack.getPlayState();
                            if (Build.VERSION.SDK_INT >= 24) {
                                try { sLiveUnderrunCount = mAudioTrack.getUnderrunCount(); } catch (Throwable ignored) {}
                            }
                        }
                    } else if (!chunkHasActive && (nowMs - lastMeasureMs >= 480L)) {
                        lastMeasureMs = nowMs;
                        sLivePeak = 0.0;
                        sLiveRmsDbfs = -120.0;
                        sLiveDcOffset = 0.0;
                        sLiveThdPercent = -1.0;
                        sLivePureMode = false;
                        if (mAudioTrack != null) sLiveAudioTrackPlayState = mAudioTrack.getPlayState();
                    }

                    if (chunkHasActive && (nowMs - lastAuditMs >= 10000L || lastAuditMs == 0L)) {
                        lastAuditMs = nowMs;
                        Log.i(AUDIT_TAG, String.format(Locale.US,
                                "[VORTA_UNIFIED_V52 %s] SR=%dHz Mode=%s Peak=%.5f RMS=%.2fdBFS DC=%.6f THD=%.4f%% Underrun=%d Clip=%d",
                                BuildProfile.FLAVOR_ID, sampleRate,
                                sIsFloatPipeline ? "PCM_FLOAT_32" : "PCM_16_TPDF",
                                sLivePeak, sLiveRmsDbfs, sLiveDcOffset, sLiveThdPercent, sLiveUnderrunCount, sLiveClipCount));
                    }

                    if ((nowMs - lastSelfCheckMs) >= 600L) {
                        lastSelfCheckMs = nowMs;
                        if (mAuxSuspended && VortaVolumeGuard.isWired(VortaAudioService.this)) {
                            handleWiredStateChange("LOOP_SELF_CHECK_RECONNECT");
                        }
                    }

                    if (mThreadRunning && mAudioTrack != null) {
                        int ret = writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                        if (PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(ret)) {
                            Log.w(TAG, "[AUDIO_RECOVERY] AudioTrack.write=" + ret + " -> recreating track");
                            releaseTrackQuietly(mAudioTrack);
                            st.envGain = 0.0;
                            mAudioTrack = createBestAudioTrack(am, sampleRate, chunkFrames);
                            if (mAudioTrack != null && mThreadRunning) {
                                writeChunk(mAudioTrack, new float[chunkFrames * 2], new short[chunkFrames * 2], chunkFrames, st);
                                try { mAudioTrack.play(); } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
                releaseTrackQuietly(mAudioTrack);
                mAudioTrack = null;
            }
        }, "VortaUnifiedAudioThread");
        mAudioThread.start();
    }

    private int writeChunk(AudioTrack track, float[] floatBuf, short[] pcm16Buf, int frames, PureDspCore.DspState st) {
        int totalSamples = frames * 2;
        if (sIsFloatPipeline && Build.VERSION.SDK_INT >= 21) {
            return track.write(floatBuf, 0, totalSamples, AudioTrack.WRITE_BLOCKING);
        }
        PureDspCore.quantizeToPcm16Tpdf(floatBuf, pcm16Buf, totalSamples, st);
        return track.write(pcm16Buf, 0, totalSamples);
    }

    @Override
    public void onDestroy() {
        mThreadRunning = false;
        mWiredGuardArmed = false;
        mAuxSuspended = false;
        sAuxSuspended = false;
        mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
        if (mAudioDeviceCallback != null && Build.VERSION.SDK_INT >= 23) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            } catch (Throwable ignored) {}
            mAudioDeviceCallback = null;
        }
        if (mExclusiveReceiver != null) {
            try { unregisterReceiver(mExclusiveReceiver); } catch (Throwable ignored) {}
            mExclusiveReceiver = null;
        }
        if (mServiceVolReceiver != null) {
            try { unregisterReceiver(mServiceVolReceiver); } catch (Throwable ignored) {}
            mServiceVolReceiver = null;
        }
        if (!VortaAccessibilityService.isConnected()) {
            VortaVolumeGuard.stopBackgroundGuards(this);
        }
        sIsEngineRunning = false;
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
