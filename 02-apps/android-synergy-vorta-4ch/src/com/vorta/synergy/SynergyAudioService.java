package com.vorta.synergy;

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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Foreground 48kHz 32-bit IEEE Float acoustic synthesis service for Vorta Synergy.
 * Supports Auto-Synergy (20 packages + optional 24h RTC), Semi-Manual multi-step/single,
 * and Basic catalog playback with mutual audio yielding across Vorta family apps.
 */
public class SynergyAudioService extends Service {
    private static final String TAG = "SynergyAudio";
    private static final String CHANNEL_ID = "vorta_synergy_silent_channel";
    private static final int NOTIFICATION_ID = 6101;
    public static final int DEFAULT_SAMPLE_RATE = 48000;

    public static final int MODE_IDLE = 0;
    public static final int MODE_AUTO_SYNERGY = 1;
    public static final int MODE_SEMI_MANUAL = 2;
    public static final int MODE_BASIC = 3;

    public static final int SCOPE_RING_SIZE = 4096;
    private static final float[] sScopeRing = new float[SCOPE_RING_SIZE];
    private static final Object sScopeLock = new Object();
    private static int sScopeWritePos = 0;

    public static final Object sLock = new Object();
    public static final List<ScenarioStep> sManualSteps = new ArrayList<ScenarioStep>();

    public static volatile boolean sIsEngineRunning = false;
    public static volatile boolean sAuxSuspended = false;
    public static volatile int sSampleRate = DEFAULT_SAMPLE_RATE;
    public static volatile boolean sIsFloatPipeline = false;

    public static volatile int sPlayMode = MODE_IDLE;
    public static volatile SynergyChannel sActiveChannel = BuildProfile.DEFAULT_CHANNEL;
    public static volatile boolean sAutoSchedule24h = false;
    public static volatile String sActivePackageCode = "S-01";
    public static volatile SynergyCatalog.SynergyPackage sActivePackage = null;
    public static volatile SynergyCatalog.BasicEntry sActiveBasic = null;
    public static volatile ScenarioStep sQuickManualStep = null;

    public static volatile boolean sGlobalFmOn = false;
    public static volatile boolean sGlobalAmOn = false;
    public static volatile boolean sGlobalRmOn = false;
    public static volatile boolean sGlobalJitter = true;
    public static volatile int sDutyMode = 0; // 0=role default, 1=continuous, 2=20/10, 3=15/15
    public static volatile float sGlobalTrimDb = 0.0f;
    public static volatile boolean sStabilizerEnabled = true;
    public static volatile float sNotchCenterHz = 0.0f;
    public static volatile float sNotchHalfWidthHz = 1.5f;

    public static volatile float sInstantDisplayHz = 0.0f;
    public static volatile double sLivePeak = 0.0;
    public static volatile double sLiveRmsDbfs = -120.0;
    public static volatile double sLiveDcOffset = 0.0;
    public static volatile double sLiveThdPercent = -1.0;
    public static volatile boolean sLivePureMode = false;
    public static volatile int sLiveUnderrunCount = 0;
    public static volatile int sLiveClipCount = 0;
    public static volatile boolean sDutyCoolingNow = false;
    public static volatile int sDutyRemainSec = 0;
    public static volatile int sActiveDutyOnMin = 0;
    public static volatile int sActiveDutyCoolMin = 0;
    public static volatile int sThermalLoadPct = 0;
    public static volatile float sTotalStabilizerCutDb = 0.0f;
    public static volatile float sBatteryTempC = 0.0f;
    public static volatile boolean sBatteryOverheatThrottle = false;

    private AudioTrack mAudioTrack;
    private Thread mAudioThread;
    private volatile boolean mThreadRunning = false;
    private volatile boolean mWiredGuardArmed = false;
    private volatile boolean mAuxSuspended = false;
    private final SynergyDspCore.AuxDebouncePolicy mAuxDebounce = new SynergyDspCore.AuxDebouncePolicy();

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

    public static void setActiveChannel(SynergyChannel ch) {
        if (ch == null) return;
        synchronized (sLock) {
            sActiveChannel = ch;
            if (sPlayMode == MODE_AUTO_SYNERGY && sActivePackage != null) {
                sInstantDisplayHz = sActivePackage.programFor(ch).step.minHz;
            }
        }
    }

    public static void playAutoPackage(Context ctx, SynergyCatalog.SynergyPackage pkg) {
        if (ctx == null || pkg == null) return;
        synchronized (sLock) {
            stopManualStepFlagsLocked();
            sActiveBasic = null;
            sQuickManualStep = null;
            sActivePackage = pkg;
            sActivePackageCode = pkg.code;
            sPlayMode = MODE_AUTO_SYNERGY;
            sInstantDisplayHz = pkg.programFor(sActiveChannel).step.minHz;
        }
        startServiceCompat(ctx);
    }

    public static void playQuickManual(Context ctx, ScenarioStep step) {
        if (ctx == null || step == null) return;
        synchronized (sLock) {
            stopManualStepFlagsLocked();
            sActivePackage = null;
            sActiveBasic = null;
            sQuickManualStep = step;
            sPlayMode = MODE_SEMI_MANUAL;
            sInstantDisplayHz = sActiveChannel.clampHz(step.minHz);
        }
        startServiceCompat(ctx);
    }

    public static void playManualChain(Context ctx) {
        if (ctx == null) return;
        synchronized (sLock) {
            sActivePackage = null;
            sActiveBasic = null;
            sQuickManualStep = null;
            sPlayMode = MODE_SEMI_MANUAL;
        }
        startServiceCompat(ctx);
    }

    public static void playBasicEntry(Context ctx, SynergyCatalog.BasicEntry entry) {
        if (ctx == null || entry == null) return;
        synchronized (sLock) {
            stopManualStepFlagsLocked();
            sActivePackage = null;
            sQuickManualStep = null;
            sActiveBasic = entry;
            sPlayMode = MODE_BASIC;
            sInstantDisplayHz = sActiveChannel.clampHz(entry.step.minHz);
        }
        startServiceCompat(ctx);
    }

    private static void stopManualStepFlagsLocked() {
        for (int i = 0; i < sManualSteps.size(); i++) {
            sManualSteps.get(i).isPlaying = false;
        }
    }

    public static void stopAllPlayback() {
        synchronized (sLock) {
            sPlayMode = MODE_IDLE;
            sActivePackage = null;
            sActiveBasic = null;
            sQuickManualStep = null;
            stopManualStepFlagsLocked();
        }
        sInstantDisplayHz = 0.0f;
        sLivePeak = 0.0;
        sLiveRmsDbfs = -120.0;
        sLiveDcOffset = 0.0;
        sLiveThdPercent = -1.0;
        sLivePureMode = false;
        sLiveClipCount = 0;
        sDutyCoolingNow = false;
        sDutyRemainSec = 0;
        synchronized (sScopeLock) {
            java.util.Arrays.fill(sScopeRing, 0.0f);
            sScopeWritePos = 0;
        }
    }

    public static boolean isAnySourceActive() {
        if (sPlayMode == MODE_AUTO_SYNERGY && sActivePackage != null) return true;
        if (sPlayMode == MODE_BASIC && sActiveBasic != null) return true;
        if (sPlayMode == MODE_SEMI_MANUAL) {
            if (sQuickManualStep != null) return true;
            synchronized (sLock) {
                for (int i = 0; i < sManualSteps.size(); i++) {
                    if (sManualSteps.get(i).isPlaying) return true;
                }
            }
        }
        return false;
    }

    public static String formatActiveModulationText() {
        if (sPlayMode == MODE_AUTO_SYNERGY && sActivePackage != null) {
            ScenarioStep st = sActivePackage.programFor(sActiveChannel).step;
            return ModulationProfile.formatCompact(
                    st.fmOn, st.fmDepth, st.fmRateHz,
                    st.amOn, st.amDepth, st.amRateHz,
                    st.rmOn, st.rmDepth, st.rmRateHz);
        }
        if (sPlayMode == MODE_BASIC && sActiveBasic != null) {
            ScenarioStep st = sActiveBasic.step;
            return ModulationProfile.formatCompact(
                    st.fmOn, st.fmDepth, st.fmRateHz,
                    st.amOn, st.amDepth, st.amRateHz,
                    st.rmOn, st.rmDepth, st.rmRateHz);
        }
        if (sPlayMode == MODE_SEMI_MANUAL && sQuickManualStep != null) {
            ScenarioStep st = sQuickManualStep;
            return ModulationProfile.formatCompact(
                    st.fmOn || sGlobalFmOn, st.fmOn ? st.fmDepth : ModulationProfile.LOCKED_FM_DEPTH, st.fmOn ? st.fmRateHz : ModulationProfile.LOCKED_FM_RATE_HZ,
                    st.amOn || sGlobalAmOn, st.amOn ? st.amDepth : ModulationProfile.LOCKED_AM_DEPTH, st.amOn ? st.amRateHz : ModulationProfile.LOCKED_AM_RATE_HZ,
                    st.rmOn || sGlobalRmOn, st.rmOn ? st.rmDepth : ModulationProfile.LOCKED_RM_DEPTH, st.rmOn ? st.rmRateHz : ModulationProfile.LOCKED_RM_RATE_HZ);
        }
        return ModulationProfile.formatLockedSummary(sGlobalFmOn, sGlobalAmOn, sGlobalRmOn);
    }

    public static String formatDutyStatusText() {
        if (sDutyCoolingNow) {
            int m = sDutyRemainSec / 60;
            int s = sDutyRemainSec % 60;
            return String.format(Locale.US, "냉각 %02d:%02d", m, s);
        }
        if (sActiveDutyCoolMin > 0 && sActiveDutyOnMin > 0) {
            return String.format(Locale.US, "듀티 %d/%d분", sActiveDutyOnMin, sActiveDutyCoolMin);
        }
        return "연속 출력";
    }

    public static String buildSelfDiagnosticReport(Context ctx) {
        String stateStr = isAnySourceActive()
                ? (sAuxSuspended ? "AUX 분리 음소거 대기" : (sDutyCoolingNow ? "열보호 무음 냉각 중" : "정상 진동 출력 중"))
                : "대기 중 (무출력)";
        String pipeStr = sSampleRate + " Hz / " + (sIsFloatPipeline ? "32-bit Float" : "16-bit PCM");
        String thdStr = (sLiveThdPercent >= 0.0)
                ? String.format(Locale.US, "%.3f%% (순음 실측)", sLiveThdPercent)
                : "복합/스윕 파형 (비단일음)";
        String volSummary = SynergyVolumeGuard.buildVolumeHealthSummary(ctx);
        HardwareStabilizer.Profile prof = HardwareStabilizer.profileFor(sActiveChannel);
        String stabStr = sStabilizerEnabled
                ? prof.commercialLabel + String.format(Locale.US, " (보정 %.1f dB)", sTotalStabilizerCutDb)
                : "비활성 (원음 직결)";
        String notchStr = (sNotchCenterHz > 0.0f)
                ? String.format(Locale.US, "%.1f ± %.1f Hz (-12dB 회피)", sNotchCenterHz, sNotchHalfWidthHz)
                : "미설정";
        String tempStr = (sBatteryTempC > 0.0f)
                ? String.format(Locale.US, "%.1f°C%s", sBatteryTempC, sBatteryOverheatThrottle ? " [고온 -3dB 보호]" : " (정상)")
                : "미측정";
        String clockStr = (Calendar.getInstance().get(Calendar.YEAR) < 2024)
                ? "기기 시계 확인 필요 (2024년 미만)"
                : "정상";
        return "● 판정: " + stateStr + "\n"
                + "• 채널 역할: " + sActiveChannel.displayName + " (" + sActiveChannel.bandLabel + ")\n"
                + "• 타겟 OS: " + BuildProfile.TARGET_OS_LABEL + "\n"
                + "• 오디오 파이프라인: " + pipeStr + "\n"
                + "• 현재 주파수: " + String.format(Locale.US, "%.2f Hz", sInstantDisplayHz) + "\n"
                + "• 현재 변조: " + formatActiveModulationText() + "\n"
                + "• 안정화 보호: " + stabStr + "\n"
                + "• 잡음 회피 노치: " + notchStr + "\n"
                + "• 가상 열부하 / 온도: " + sThermalLoadPct + "% / " + tempStr + "\n"
                + "• 열보호·트림: " + formatDutyStatusText() + String.format(Locale.US, " · 트림 %.1f dB", sGlobalTrimDb) + "\n"
                + "• 시계 상태: " + clockStr + "\n"
                + "• 실측 피크 / RMS: " + String.format(Locale.US, "%.4f (%.1f dBFS)", sLivePeak, sLiveRmsDbfs) + "\n"
                + "• DC 오프셋 / 클립: " + String.format(Locale.US, "%.5f / %d건", sLiveDcOffset, sLiveClipCount) + "\n"
                + "• THD+N / 언더런: " + thdStr + " / " + sLiveUnderrunCount + "건\n\n"
                + "[볼륨 및 안전장치 상태]\n" + volSummary + "\n\n"
                + "※ 본 수치는 OS 오디오 HAL 직전 디지털 신호 실측값입니다.";
    }

    public static void startServiceCompat(Context ctx) {
        if (ctx == null) return;
        Intent intent = new Intent(ctx, SynergyAudioService.class);
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
        SynergyVolumeGuard.syncTargetFromSystem(this);
        SynergyVolumeGuard.ensureTargetVolumeIfNeeded(this);
        registerExclusiveReceiver();
        registerVolumeGuardReceiver();
        SynergyVolumeGuard.startBackgroundGuards(this);
        registerAuxDeviceCallback();
    }

    private AudioDeviceInfo findWiredOutputDevice(AudioManager am) {
        if (am == null || Build.VERSION.SDK_INT < 23) return null;
        try {
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devs != null) {
                for (AudioDeviceInfo d : devs) {
                    if (SynergyVolumeGuard.isWiredDeviceType(d.getType())) return d;
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
            boolean wiredNow = (wiredDev != null) || SynergyVolumeGuard.isWired(this);
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
                    SynergyVolumeGuard.onHeadsetPlug(this, 1);
                    if (mThreadRunning) updateNotification();
                }
            } else if (mThreadRunning && mWiredGuardArmed && !mAuxSuspended) {
                if (mAudioTrack != null && Build.VERSION.SDK_INT >= 23) {
                    try { mAudioTrack.setPreferredDevice(null); } catch (Throwable ignored) {}
                }
                if (mAuxDebounce.onHardwareDisconnected(SystemClock.elapsedRealtime(), mAuxSuspended)) {
                    engageAuxMute(reason);
                } else {
                    mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
                    mHandler.postDelayed(mAuxSuspendCheckRunnable, SynergyDspCore.AUX_DISCONNECT_DEBOUNCE_MS + 15L);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void checkPendingAuxDisconnect() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            boolean wiredNow = (findWiredOutputDevice(am) != null) || SynergyVolumeGuard.isWired(this);
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
        SynergyVolumeGuard.onHeadsetPlug(this, 0);
        updateNotification();
        Log.w(TAG, "AUX disconnected (" + reason + ") -> muted");
    }

    private void registerVolumeGuardReceiver() {
        if (!SynergyVolumeGuard.isTargetDevice() || mServiceVolReceiver != null) return;
        try {
            mServiceVolReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null || intent.getAction() == null) return;
                    String action = intent.getAction();
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                        SynergyVolumeGuard.onVolumeChanged(ctx, intent);
                    } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                        int st = intent.getIntExtra("state", 0);
                        SynergyVolumeGuard.enforceSystemFlags(ctx);
                        SynergyVolumeGuard.onHeadsetPlug(ctx, st);
                        handleWiredStateChange("HEADSET_PLUG_" + st);
                    } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                        handleWiredStateChange("BECOMING_NOISY");
                    } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)) {
                        SynergyVolumeGuard.recoverFromSafeVolumeClamp(ctx, "SAFE_ACTIVE");
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
            NotificationChannel chan = new NotificationChannel(CHANNEL_ID, "보르타 시너지 무음 채널", NotificationManager.IMPORTANCE_LOW);
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
            if (pm != null && mWakeLock == null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorta:SynergyAudioLock");
                mWakeLock.setReferenceCounted(false);
            }
            if (mWakeLock != null && !mWakeLock.isHeld()) mWakeLock.acquire();
        } catch (Throwable ignored) {}
    }

    private Notification buildNotification() {
        Intent launch = new Intent(this, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = (Build.VERSION.SDK_INT >= 23)
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, piFlags);

        String text;
        if (mAuxSuspended) text = "AUX 분리 감지 - 출력 음소거 대기";
        else if (sDutyCoolingNow) text = sActiveChannel.shortLabel + " · 열보호 냉각 중";
        else if (isAnySourceActive()) text = String.format(Locale.US, "%s · %.1f Hz 진동 출력 중", sActiveChannel.shortLabel, sInstantDisplayHz);
        else text = sActiveChannel.shortLabel + " · 대기 중";

        Notification.Builder nb = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        nb.setContentTitle("보르타 시너지")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0);
        return nb.build();
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        Intent yieldIntent = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
        yieldIntent.putExtra("sender_pkg", getPackageName());
        sendBroadcast(yieldIntent);

        SynergyVolumeGuard.enforceSystemFlags(this);
        if (SynergyVolumeGuard.isTargetDevice()) {
            SynergyVolumeGuard.ensureTargetVolumeIfNeeded(this);
        }

        if (!mThreadRunning) {
            mThreadRunning = true;
            mAuxSuspended = false;
            sAuxSuspended = false;
            mWiredGuardArmed = SynergyVolumeGuard.isWired(this);
            mAudioThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runAudioLoop();
                }
            }, "SynergyAudioLoop");
            mAudioThread.start();
        } else {
            updateNotification();
        }
        return START_STICKY;
    }

    private void createAudioTrack() {
        releaseTrackQuietly();
        int sr = DEFAULT_SAMPLE_RATE;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                String prop = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                if (prop != null) {
                    int parsed = Integer.parseInt(prop);
                    if (parsed >= 44100 && parsed <= 192000) sr = parsed;
                }
            }
        } catch (Throwable ignored) {}
        sSampleRate = sr;

        int minBytes = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (minBytes <= 0) minBytes = sr / 5 * 8;
        int bufBytes = Math.max(minBytes * 2, 2880 * 8);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat fmt = new AudioFormat.Builder()
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build();
        try {
            mAudioTrack = new AudioTrack(attrs, fmt, bufBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                sIsFloatPipeline = true;
                if (BuildProfile.USE_SET_PREFERRED_DEVICE && Build.VERSION.SDK_INT >= 23) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    AudioDeviceInfo dev = findWiredOutputDevice(am);
                    if (dev != null) {
                        try { mAudioTrack.setPreferredDevice(dev); } catch (Throwable ignored) {}
                    }
                }
                mAudioTrack.play();
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback to PCM16 if device HAL rejects float
        releaseTrackQuietly();
        int min16 = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int buf16 = Math.max(min16 * 2, 2880 * 4);
        AudioFormat fmt16 = new AudioFormat.Builder()
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();
        mAudioTrack = new AudioTrack(attrs, fmt16, buf16, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        sIsFloatPipeline = false;
        if (mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            mAudioTrack.play();
        }
    }

    private void releaseTrackQuietly() {
        if (mAudioTrack != null) {
            try { mAudioTrack.pause(); } catch (Throwable ignored) {}
            try { mAudioTrack.flush(); } catch (Throwable ignored) {}
            try { mAudioTrack.release(); } catch (Throwable ignored) {}
            mAudioTrack = null;
        }
    }

    private void checkAutoScheduleTick() {
        try {
            Intent batt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batt != null) {
                int rawT = batt.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                if (rawT > 0) {
                    float tempC = rawT / 10.0f;
                    sBatteryTempC = tempC;
                    sBatteryOverheatThrottle = HardwareStabilizer.evaluateBatteryThermalThrottle(tempC, sBatteryOverheatThrottle);
                }
            }
        } catch (Throwable ignored) {}
        if (!sAutoSchedule24h || sPlayMode != MODE_AUTO_SYNERGY) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        SynergyCatalog.SynergyPackage target = SynergyCatalog.packageForHour(hour);
        if (target != null && (sActivePackage == null || !target.code.equals(sActivePackage.code))) {
            sActivePackage = target;
            sActivePackageCode = target.code;
        }
    }

    private void runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        createAudioTrack();
        sIsEngineRunning = true;

        final int chunkFrames = 2880;
        float[] floatBuf = new float[chunkFrames * 2];
        float[] monoAudit = new float[chunkFrames];
        short[] pcm16Buf = new short[chunkFrames * 2];

        SynergyDspCore.DspState dspState = new SynergyDspCore.DspState();
        SynergyDspCore.RenderRequest req = new SynergyDspCore.RenderRequest();
        long lastAuditMs = 0L;
        long lastSchedMs = 0L;

        while (mThreadRunning) {
            if (mAudioTrack == null || mAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                SystemClock.sleep(150L);
                createAudioTrack();
                continue;
            }
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastSchedMs >= 30000L) {
                lastSchedMs = nowMs;
                checkAutoScheduleTick();
            }

            boolean anyActive;
            boolean isPureExpected = false;
            double refF1 = 0.0;
            double refF2 = 0.0;

            synchronized (sLock) {
                req.channel = sActiveChannel;
                req.globalFmOn = sGlobalFmOn;
                req.globalFmDepth = ModulationProfile.LOCKED_FM_DEPTH;
                req.globalFmRateHz = ModulationProfile.LOCKED_FM_RATE_HZ;
                req.globalAmOn = sGlobalAmOn;
                req.globalAmDepth = ModulationProfile.LOCKED_AM_DEPTH;
                req.globalAmRateHz = ModulationProfile.LOCKED_AM_RATE_HZ;
                req.globalRmOn = sGlobalRmOn;
                req.globalRmDepth = ModulationProfile.LOCKED_RM_DEPTH;
                req.globalRmRateHz = ModulationProfile.LOCKED_RM_RATE_HZ;
                req.globalJitter = sGlobalJitter;
                req.globalTrimDb = sGlobalTrimDb;
                req.auxSuspended = mAuxSuspended;
                req.stabilizerEnabled = sStabilizerEnabled;
                req.notchCenterHz = sNotchCenterHz;
                req.notchHalfWidthHz = sNotchHalfWidthHz;
                req.batteryThermalThrottle = sBatteryOverheatThrottle;

                if (sPlayMode == MODE_AUTO_SYNERGY && sActivePackage != null) {
                    SynergyCatalog.ChannelProgram prog = sActivePackage.programFor(sActiveChannel);
                    req.singleStep = prog.step;
                    req.multiSteps = null;
                    req.useGlobalModulation = false;
                    resolveDutyCycle(prog.dutyOnMin, prog.dutyCoolMin, req);
                    if (prog.step.type == ScenarioStep.StepType.SINGLE && !prog.step.fmOn && !prog.step.amOn && !prog.step.rmOn) {
                        isPureExpected = true;
                        refF1 = sActiveChannel.clampHz(prog.step.minHz);
                        refF2 = prog.step.secondaryHz > 0f ? sActiveChannel.clampHz(prog.step.secondaryHz) : 0.0;
                    }
                } else if (sPlayMode == MODE_BASIC && sActiveBasic != null) {
                    req.singleStep = sActiveBasic.step;
                    req.multiSteps = null;
                    req.useGlobalModulation = false;
                    resolveDutyCycle(sActiveChannel.defaultDutyOnMin, sActiveChannel.defaultDutyCoolMin, req);
                } else if (sPlayMode == MODE_SEMI_MANUAL && sQuickManualStep != null) {
                    req.singleStep = sQuickManualStep;
                    req.multiSteps = null;
                    req.useGlobalModulation = !(sQuickManualStep.fmOn || sQuickManualStep.amOn || sQuickManualStep.rmOn);
                    resolveDutyCycle(sActiveChannel.defaultDutyOnMin, sActiveChannel.defaultDutyCoolMin, req);
                    if (sQuickManualStep.type == ScenarioStep.StepType.SINGLE
                            && !sQuickManualStep.fmOn && !sQuickManualStep.amOn && !sQuickManualStep.rmOn
                            && !sGlobalFmOn && !sGlobalAmOn && !sGlobalRmOn) {
                        isPureExpected = true;
                        refF1 = sActiveChannel.clampHz(sQuickManualStep.minHz);
                        refF2 = sQuickManualStep.secondaryHz > 0f ? sActiveChannel.clampHz(sQuickManualStep.secondaryHz) : 0.0;
                    }
                } else if (sPlayMode == MODE_SEMI_MANUAL) {
                    req.singleStep = null;
                    req.multiSteps = sManualSteps;
                    req.useGlobalModulation = true;
                    resolveDutyCycle(sActiveChannel.defaultDutyOnMin, sActiveChannel.defaultDutyCoolMin, req);
                } else {
                    req.singleStep = null;
                    req.multiSteps = null;
                    req.dutyOnMin = 0;
                    req.dutyCoolMin = 0;
                }
                anyActive = isAnySourceActive();
            }

            if (!anyActive && dspState.envGain <= 0.0) {
                sInstantDisplayHz = 0.0f;
                sDutyCoolingNow = false;
                sDutyRemainSec = 0;
                SystemClock.sleep(100L);
                continue;
            }

            float instHz;
            synchronized (sLock) {
                instHz = SynergyDspCore.renderChunk(floatBuf, chunkFrames, sSampleRate, dspState, req);
            }
            sInstantDisplayHz = instHz;
            sDutyCoolingNow = dspState.dutyCooling;
            sDutyRemainSec = (int) Math.ceil(dspState.dutyRemainSec);
            sActiveDutyOnMin = req.dutyOnMin;
            sActiveDutyCoolMin = req.dutyCoolMin;
            sThermalLoadPct = (int) Math.round(dspState.thermalLoad * 100.0);
            sTotalStabilizerCutDb = (float) dspState.lastTotalStabDb;

            synchronized (sScopeLock) {
                for (int i = 0; i < chunkFrames; i++) {
                    float m = floatBuf[i * 2];
                    monoAudit[i] = m;
                    sScopeRing[sScopeWritePos] = m;
                    sScopeWritePos = (sScopeWritePos + 1) % SCOPE_RING_SIZE;
                }
            }

            if (nowMs - lastAuditMs >= 480L) {
                lastAuditMs = nowMs;
                SynergyDspCore.LiveQualityReport qr = SynergyDspCore.measureLiveBufferQuality(
                        monoAudit, chunkFrames, sSampleRate, isPureExpected && !dspState.dutyCooling, refF1, refF2);
                sLivePeak = qr.peak;
                sLiveRmsDbfs = qr.rmsDbfs;
                sLiveDcOffset = qr.dcOffset;
                sLiveThdPercent = qr.thdPlusNPercent;
                sLivePureMode = isPureExpected && !dspState.dutyCooling;
                sLiveClipCount += qr.clipOrNanCount;
                if (Build.VERSION.SDK_INT >= 24 && mAudioTrack != null) {
                    try { sLiveUnderrunCount = mAudioTrack.getUnderrunCount(); } catch (Throwable ignored) {}
                }
            }

            int written;
            if (sIsFloatPipeline) {
                written = mAudioTrack.write(floatBuf, 0, chunkFrames * 2, AudioTrack.WRITE_BLOCKING);
            } else {
                SynergyDspCore.quantizeToPcm16Tpdf(floatBuf, chunkFrames * 2, pcm16Buf, dspState);
                written = mAudioTrack.write(pcm16Buf, 0, chunkFrames * 2);
            }
            if (written < 0) {
                Log.w(TAG, "AudioTrack write error=" + written + " -> rebuild");
                createAudioTrack();
            }
        }

        sIsEngineRunning = false;
        releaseTrackQuietly();
    }

    private void resolveDutyCycle(int roleOnMin, int roleCoolMin, SynergyDspCore.RenderRequest req) {
        if (sDutyMode == 1) {
            req.dutyOnMin = 0;
            req.dutyCoolMin = 0;
        } else if (sDutyMode == 2) {
            req.dutyOnMin = 20;
            req.dutyCoolMin = 10;
        } else if (sDutyMode == 3) {
            req.dutyOnMin = 15;
            req.dutyCoolMin = 15;
        } else {
            req.dutyOnMin = roleOnMin;
            req.dutyCoolMin = roleCoolMin;
        }
    }

    @Override
    public void onDestroy() {
        mThreadRunning = false;
        if (mAudioThread != null) {
            try { mAudioThread.join(400L); } catch (InterruptedException ignored) {}
            mAudioThread = null;
        }
        sIsEngineRunning = false;
        mHandler.removeCallbacksAndMessages(null);
        if (mExclusiveReceiver != null) {
            try { unregisterReceiver(mExclusiveReceiver); } catch (Throwable ignored) {}
            mExclusiveReceiver = null;
        }
        if (mServiceVolReceiver != null) {
            try { unregisterReceiver(mServiceVolReceiver); } catch (Throwable ignored) {}
            mServiceVolReceiver = null;
        }
        if (Build.VERSION.SDK_INT >= 23 && mAudioDeviceCallback != null) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            } catch (Throwable ignored) {}
            mAudioDeviceCallback = null;
        }
        if (!SynergyAccessibilityService.isConnected()) {
            SynergyVolumeGuard.stopBackgroundGuards(this);
        }
        if (mWakeLock != null) {
            try { if (mWakeLock.isHeld()) mWakeLock.release(); } catch (Throwable ignored) {}
            mWakeLock = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
