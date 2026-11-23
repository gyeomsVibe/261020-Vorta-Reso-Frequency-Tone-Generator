package com.vorta.s6frequency;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.util.Log;

public class S6AudioGeneratorService extends Service {
    private static final String TAG = "S6AudioService";
    private static final String AUDIT_TAG = "S6AudioAudit";
    private static final String CHANNEL_ID = "vorta_reso_s6_silent_v3";
    private static final int NOTIF_ID = 2601;

    public static final String PREF_NAME = "vorta_s6_dsp_prefs";
    public static final String KEY_BASS_BOOST = "bass_boost_db";
    public static final String KEY_LIMITER_MODE = "limiter_mode";
    public static final String KEY_CHANNEL_MODE = "channel_mode";
    public static final String KEY_HARMONIC_EXCITER = "harmonic_exciter";
    public static final String KEY_AUTO_PRESET_DSP = "auto_preset_dsp";

    // Galaxy S6 Native Audio HAL Parameters (Exynos 7420 / Wolfson WM1840):
    public static final int SAMPLE_RATE = 48000;
    public static final int HAL_FRAMES = 960; // S6 AudioOut_D native HAL period

    // DSP Global Persistent States:
    public static volatile float sBassBoostDb = 0.0f;
    public static volatile int sLimiterMode = 0; // 0 = True-Peak 0.0dBFS Limiter, 1 = Analog Soft Saturator
    public static volatile int sChannelMode = 0; // 0 = L=R 동위상, 1 = L=-R 역위상, 2 = (L+R)/2 BTL 모노 서밍, 3 = L 단독, 4 = R 단독
    public static volatile boolean sHarmonicExciter = false; // 2nd harmonic injection
    public static volatile boolean sAutoPresetDsp = true; // Auto-load optimized DSP per situation/schedule

    // Synthesis Parameters:
    public static volatile boolean sIsPlaying = false;
    public static volatile float sTargetFreq = 55.0f;
    public static volatile float sInstantFreq = 55.0f;
    public static volatile float sSubFreq = 0.0f;

    public static volatile boolean sIsSweepMode = false;
    public static volatile float sSweepMin = 38.0f;
    public static volatile float sSweepMax = 68.0f;
    public static volatile float sSweepPeriodSec = 60.0f;

    public static volatile boolean sFmEnabled = false;
    public static volatile float sFmDepth = 0.0f;
    public static volatile float sFmRateHz = 5.0f;

    public static volatile boolean sAmEnabled = false;
    public static volatile float sAmDepth = 0.0f;
    public static volatile float sAmRateHz = 1.0f;

    public static volatile boolean sRmEnabled = false;
    public static volatile float sRmDepth = 0.0f;
    public static volatile float sRmRateHz = 1.0f;

    public static volatile String sActivePart = "[메인 파트]";
    public static volatile String sScenarioName = "[오전 07~13시] 표준 스케줄";
    public static volatile String sModeTitle = "보르타 레조 S6 v4.2 대기 중";
    public static volatile String sKnobGuide = "LP-168S 노브: SUB 3.5(9시반) | X-OVER 65~85Hz(10시) | BASS 12~1시 | TREBLE 7시(-10) | VOL 5.5(12시반)";
    public static volatile int sLastPeak = 29200;
    public static volatile double sLastRmsDb = -3.01;
    public static volatile double sLastDcOffset = 0.0;

    private PowerManager.WakeLock mWakeLock;
    private AudioTrack mAudioTrack;
    private volatile boolean mIsRunning = false;
    private volatile boolean mPhaseResetRequested = false;
    // Pure-sine woofer protection (2026-10-20 user receipt: roar/noise on all 3 devices).
    // Gain ramps 0->1 over 1.0 s on start and 1->0 over 0.5 s on stop: a step from silence to
    // full scale is a broadband click that slams the cone. Below 20 Hz a vented woofer unloads
    // and over-excurses; that is handled by amp X-OVER/SUB, never by rewriting scenario Hz.
    private static final double FREQ_FLOOR_HZ = 1.0; // engine safety only; scenario frequencies are never altered (principle 16)
    private static final double FADE_IN_SEC = 1.0;
    private static final double FADE_OUT_SEC = 0.5;
    private static final double AUX_MUTE_SEC = 0.02; // 20ms click-free mute on unexpected AUX detach
    public static volatile boolean sAuxSuspended = false;
    private volatile boolean mWiredGuardArmed = false;
    private volatile boolean mAuxSuspended = false;
    private AudioDeviceCallback mAudioDeviceCallback;
    private double mGain = 0.0;
    private volatile boolean mFadeOutRequested = false;
    private volatile boolean mFadeOutDone = false;
    private volatile boolean mAuditRequested = true;

    private Thread mAudioThread;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver mServiceVolReceiver;

    public static void loadConfig(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sBassBoostDb = sp.getFloat(KEY_BASS_BOOST, 0.0f);
        sLimiterMode = sp.getInt(KEY_LIMITER_MODE, 0);
        sChannelMode = sp.getInt(KEY_CHANNEL_MODE, 0);
        sHarmonicExciter = sp.getBoolean(KEY_HARMONIC_EXCITER, false);
        sAutoPresetDsp = sp.getBoolean(KEY_AUTO_PRESET_DSP, true);
        Log.i(TAG, String.format("Loaded Persistent DSP Config S6 v2.6: Boost=%.1fdB, Limiter=%d, Ch=%d, Exciter=%b, AutoPreset=%b",
                sBassBoostDb, sLimiterMode, sChannelMode, sHarmonicExciter, sAutoPresetDsp));
    }

    public static void saveConfig(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putFloat(KEY_BASS_BOOST, sBassBoostDb)
                .putInt(KEY_LIMITER_MODE, sLimiterMode)
                .putInt(KEY_CHANNEL_MODE, sChannelMode)
                .putBoolean(KEY_HARMONIC_EXCITER, sHarmonicExciter)
                .putBoolean(KEY_AUTO_PRESET_DSP, sAutoPresetDsp)
                .apply();
    }

    public static void muteSystemAlertStreams(Context context) {
        if (S6VolumeHelper.isPersonalDevice() || S6VolumeHelper.isA30Device()) return;
        if (android.os.Build.MODEL != null && android.os.Build.MODEL.toUpperCase().contains("A30")) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        muteSystemAlertStreams(this);
        super.onCreate();
        loadConfig(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VortaResoS6:BitPerfectAudioLock");
            mWakeLock.acquire();
        }
        S6VolumeHelper.syncTargetFromSystem(this);
        S6VolumeHelper.ensureTargetVolumeIfNeeded(this, false);
        registerVolumeGuardReceiver();
        registerAuxDeviceCallback();
    }

    private AudioDeviceInfo findWiredOutputDevice(AudioManager am) {
        if (am == null || Build.VERSION.SDK_INT < 23) return null;
        try {
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devs != null) {
                for (int i = 0; i < devs.length; i++) {
                    if (S6VolumeHelper.isWiredDeviceType(devs[i].getType())) return devs[i];
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void registerAuxDeviceCallback() {
        if (Build.VERSION.SDK_INT < 23 || mAudioDeviceCallback != null) return;
        try {
            final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            mAudioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    handleWiredStateChange("DEVICE_ADDED");
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    handleWiredStateChange("DEVICE_REMOVED");
                }
            };
            am.registerAudioDeviceCallback(mAudioDeviceCallback, mHandler);
        } catch (Throwable t) {
            Log.w(TAG, "registerAuxDeviceCallback: " + t.getMessage());
        }
    }

    private void handleWiredStateChange(String reason) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
            boolean wiredNow = (wiredDev != null) || S6VolumeHelper.isWired(this);
            if (wiredNow) {
                if (mIsRunning) {
                    mWiredGuardArmed = true;
                }
                if (mAudioTrack != null && wiredDev != null && Build.VERSION.SDK_INT >= 23) {
                    mAudioTrack.setPreferredDevice(wiredDev);
                }
                if (mAuxSuspended) {
                    mAuxSuspended = false;
                    sAuxSuspended = false;
                    S6VolumeHelper.onHeadsetPlug(this, 1);
                    if (mIsRunning) updateNotification();
                    Log.i(TAG, "[AUX_GUARD] Wired reconnected (" + reason + ") -> resuming with 1.0s fade-in");
                }
            } else {
                if (mIsRunning && mWiredGuardArmed && !mAuxSuspended) {
                    mAuxSuspended = true;
                    sAuxSuspended = true;
                    S6VolumeHelper.onHeadsetPlug(this, 0);
                    updateNotification();
                    Log.w(TAG, "[AUX_GUARD] Wired disconnected (" + reason + ") -> muting speaker output (waiting for reconnect)");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "handleWiredStateChange: " + t.getMessage());
        }
    }

    private void registerVolumeGuardReceiver() {
        if (mServiceVolReceiver != null) return;
        try {
            mServiceVolReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                        S6VolumeHelper.onVolumeChanged(context, intent);
                    } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                        int st = intent.getIntExtra("state", 0);
                        S6VolumeHelper.enforceSystemFlags(context);
                        S6VolumeHelper.onHeadsetPlug(context, st);
                        handleWiredStateChange("HEADSET_PLUG_" + st);
                    } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                        handleWiredStateChange("BECOMING_NOISY");
                    } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)) {
                        S6VolumeHelper.enforceSystemFlags(context);
                        S6VolumeHelper.restoreIfClamped(context, "SAFE_MEDIA_VOLUME_ACTIVE");
                    } else if ("com.vorta.ACTION_EXCLUSIVE_AUDIO_START".equals(action)) {
                        String sender = intent.getStringExtra("sender_pkg");
                        if (sender != null && !sender.equals(getPackageName()) && mIsRunning) {
                            Log.i(TAG, "Yielding audio output to sibling Vorta app: " + sender);
                            stopAudio();
                            stopForeground(true);
                            stopSelf();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            if (!S6VolumeHelper.isPersonalDevice()) {
                filter.addAction("android.media.VOLUME_CHANGED_ACTION");
                filter.addAction(Intent.ACTION_HEADSET_PLUG);
                filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
                filter.addAction("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE");
            }
            filter.addAction("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(mServiceVolReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mServiceVolReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "registerVolumeGuardReceiver error: " + t.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            updateNotification();
        }
        muteSystemAlertStreams(this);
        if (intent == null) {
            if (!mIsRunning) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }
        if (intent.hasExtra("part")) {
            sActivePart = intent.getStringExtra("part");
        }
        if (intent.hasExtra("scenario")) {
            sScenarioName = intent.getStringExtra("scenario");
        }

        String action = intent.getStringExtra("action");
        if ("stop".equalsIgnoreCase(action)) {
            stopAudio();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean dspChanged = false;
        if (intent.hasExtra("auto_preset_dsp")) {
            sAutoPresetDsp = intent.getBooleanExtra("auto_preset_dsp", sAutoPresetDsp);
            dspChanged = true;
        }
        if (intent.hasExtra("bass_boost_db")) {
            sBassBoostDb = intent.getFloatExtra("bass_boost_db", sBassBoostDb);
            dspChanged = true;
        }
        if (intent.hasExtra("limiter_mode")) {
            sLimiterMode = intent.getIntExtra("limiter_mode", sLimiterMode);
            dspChanged = true;
        }
        if (intent.hasExtra("channel_mode")) {
            sChannelMode = intent.getIntExtra("channel_mode", sChannelMode);
            dspChanged = true;
        }
        if (intent.hasExtra("harmonic_exciter")) {
            sHarmonicExciter = intent.getBooleanExtra("harmonic_exciter", sHarmonicExciter);
            dspChanged = true;
        }
        if (dspChanged) {
            saveConfig(this);
        }

            boolean hasPresetFreq = intent.hasExtra("sweep") || intent.hasExtra("freq") || intent.hasExtra("mode");
            if (hasPresetFreq) {
                boolean sweep = intent.getBooleanExtra("sweep", false)
                        || "sweep".equalsIgnoreCase(intent.getStringExtra("mode"));
                if (sweep) {
                    sSweepMin = Math.max(1.0f, intent.getFloatExtra("sweep_min", 24.0f));
                    sSweepMax = Math.max(1.0f, intent.getFloatExtra("sweep_max", 55.0f));
                    float sDur = intent.getFloatExtra("sweep_sec", 60.0f);
                    sSweepPeriodSec = (sDur >= 1.0f) ? sDur : 60.0f;
                    sIsSweepMode = true;
                    sSubFreq = Math.max(0.0f, intent.getFloatExtra("sub_freq", 0.0f));
                } else {
                    sTargetFreq = Math.max(1.0f, intent.getFloatExtra("freq", 55.0f));
                    sInstantFreq = sTargetFreq;
                    sSubFreq = Math.max(0.0f, intent.getFloatExtra("sub_freq", 0.0f));
                    sIsSweepMode = false;
                }
                mPhaseResetRequested = true;
            }

            if (intent.hasExtra("fm_on")) {
                sFmEnabled = intent.getBooleanExtra("fm_on", false);
                sFmDepth = clamp(intent.getFloatExtra("fm_depth", 0.10f), 0.0f, 0.95f);
                sFmRateHz = clamp(intent.getFloatExtra("fm_rate", 5.0f), 0.05f, 30.0f);
            }

            if (intent.hasExtra("am_on")) {
                sAmEnabled = intent.getBooleanExtra("am_on", false);
                sAmDepth = clamp(intent.getFloatExtra("am_depth", 0.90f), 0.0f, 1.0f);
                sAmRateHz = clamp(intent.getFloatExtra("am_rate", 1.2f), 0.05f, 30.0f);
            }

            if (intent.hasExtra("rm_on")) {
                sRmEnabled = intent.getBooleanExtra("rm_on", false);
                sRmDepth = clamp(intent.getFloatExtra("rm_depth", 0.40f), 0.0f, 1.0f);
                sRmRateHz = clamp(intent.getFloatExtra("rm_rate", 1.5f), 0.05f, 50.0f);
            }

            String customTitle = intent.getStringExtra("title");
            if (customTitle != null && !customTitle.isEmpty()) {
                sModeTitle = customTitle;
            } else if (hasPresetFreq) {
                sModeTitle = sIsSweepMode
                        ? String.format("보르타 레조 S6 스윕 (%.1f~%.1fHz / %.0f초)", sSweepMin, sSweepMax, sSweepPeriodSec)
                        : String.format("보르타 레조 S6 단독 (%.2fHz)", sTargetFreq);
            }

            String customKnob = intent.getStringExtra("knob_guide"); if (customKnob == null || customKnob.isEmpty()) customKnob = intent.getStringExtra("knob");
            if (customKnob != null && !customKnob.isEmpty()) {
                sKnobGuide = customKnob;
            }

            mAuditRequested = true;
            try {
                Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
                excl.putExtra("sender_pkg", getPackageName());
                sendBroadcast(excl);
            } catch (Throwable ignored) {}
            S6VolumeHelper.syncTargetFromSystem(this);
            S6VolumeHelper.ensureTargetVolumeIfNeeded(this, false);
            startAudioIfNeeded();
            updateNotification();
        return START_STICKY;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static String buildModulationSummary() {
        StringBuilder sb = new StringBuilder();
        if (sFmEnabled && sFmDepth > 0.001f) {
            sb.append(String.format("FM(%.0f%%/%.1fHz) ", sFmDepth * 100.0f, sFmRateHz));
        }
        if (sAmEnabled && sAmDepth > 0.001f) {
            sb.append(String.format("AM(%.0f%%/%.1fHz) ", sAmDepth * 100.0f, sAmRateHz));
        }
        if (sRmEnabled && sRmDepth > 0.001f) {
            sb.append(String.format("RM(%.0f%%/%.1fHz) ", sRmDepth * 100.0f, sRmRateHz));
        }
        if (sSubFreq > 0.1f) {
            sb.append(String.format("+Sub(%.1fHz) ", sSubFreq));
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "무변조 순수 사인파" : res;
    }

    public static String buildVortaDspSummary() {
        StringBuilder sb = new StringBuilder();
        if (sBassBoostDb > 0.01f) {
            sb.append(String.format("부스트 +%.0fdB | ", sBassBoostDb));
        } else {
            sb.append("부스트 0dB(원음) | ");
        }
        sb.append(sLimiterMode == 1 ? "아날로그 소프트 새추레이터 | " : "트루피크 -1.0dBTP 리미터 | ");
        if (sChannelMode == 1) {
            sb.append("L=-R 역위상(180°) | ");
        } else if (sChannelMode == 2) {
            sb.append("BTL 모노 서밍(L+R) | ");
        } else if (sChannelMode == 3) {
            sb.append("L 단독 | ");
        } else if (sChannelMode == 4) {
            sb.append("R 단독 | ");
        } else {
            sb.append("L=R 스테레오 동위상 | ");
        }
        sb.append(sHarmonicExciter ? "🔥 2차 배음 ON" : "배음 OFF");
        return sb.toString();
    }

    private void updateNotification() {
        createNotificationChannelIfNeeded();

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent stopIntent = new Intent(this, S6AudioGeneratorService.class);
        stopIntent.putExtra("action", "stop");
        PendingIntent pStop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        String freqDesc = sIsSweepMode
                ? String.format("스윕 %.1f~%.1fHz (%.0fs) | Vorta DSP", sSweepMin, sSweepMax, sSweepPeriodSec)
                : String.format("발진 %.2fHz (0.00dBFS True-Peak) | Vorta DSP", sTargetFreq);
        if (mAuxSuspended) {
            freqDesc = "🔇 AUX 분리 감지 — 스피커 송출 차단 / 재연결 대기 | " + freqDesc;
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(sModeTitle)
                .setContentText(freqDesc)
                .setSubText("갤럭시 S6 + 트랜스2(Lepy LP-168S 68W BTL)")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .setPriority(Notification.PRIORITY_MAX)
                .addAction(android.R.drawable.ic_media_pause, "정지 (STOP)", pStop);

        startForeground(NOTIF_ID, builder.build());
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
                if (channel == null) {
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            "보르타 레조 S6 백그라운드 발진",
                            NotificationManager.IMPORTANCE_LOW
                    );
                    channel.setDescription("S6 트랜스2 68W BTL 무왜곡 주파수 백그라운드 발진 알림");
                    channel.setSound(null, null);
                    channel.enableVibration(false);
                channel.setShowBadge(false);
                    nm.createNotificationChannel(channel);
                }
            }
        }
    }

    private synchronized void stopAudio() {
        if (mIsRunning && mAudioThread != null) {
            mFadeOutDone = false;
            mFadeOutRequested = true;
            long until = System.currentTimeMillis() + (long) (FADE_OUT_SEC * 1000) + 300;
            while (!mFadeOutDone && System.currentTimeMillis() < until) {
                try { Thread.sleep(20); } catch (InterruptedException e) { break; }
            }
        }
        mIsRunning = false;
        sIsPlaying = false;
        mWiredGuardArmed = false;
        mAuxSuspended = false;
        sAuxSuspended = false;
        sModeTitle = "🛑 [작동 중지] 발진 멈춤 (대기 상태)";
        sInstantFreq = 0.0f;
        if (mAudioThread != null) {
            mAudioThread.interrupt();
            mAudioThread = null;
        }
        if (mAudioTrack != null) {
            try {
                mAudioTrack.stop();
                mAudioTrack.release();
            } catch (Exception ignored) {}
            mAudioTrack = null;
        }
        Log.i(TAG, "S6 Audio Engine Stopped cleanly.");
    }

    private int synthesizeFrameBuffer(
            short[] stereoBuffer,
            int frames,
            double[] state,
            boolean isSweep,
            double sweepMin,
            double sweepMax,
            double sweepPeriodSec,
            double targetFreq,
            double subFreq,
            boolean fmOn,
            double fmDepth,
            double fmRate,
            boolean amOn,
            double amDepth,
            double amRate,
            boolean rmOn,
            double rmDepth,
            double rmRate,
            float bassBoostDb,
            int limiterMode,
            int channelMode,
            boolean harmonicExciter) {

        double carrierPhase = state[0];
        double sweepPhase = state[1];
        double fmPhase = state[2];
        double amPhase = state[3];
        double rmPhase = state[4];
        double subPhase = state[5];

        final double twoPi = 2.0 * Math.PI;
        final double invSr = 1.0 / (double) SAMPLE_RATE;
        final double sweepStep = (sweepPeriodSec > 0.05) ? (twoPi / (sweepPeriodSec * SAMPLE_RATE)) : 0.0;
        final double fmStep = twoPi * fmRate * invSr;
        final double amStep = twoPi * amRate * invSr;
        final double rmStep = twoPi * rmRate * invSr;
        final double subStep = twoPi * subFreq * invSr;

        double driveK = (bassBoostDb > 0.01f) ? Math.pow(10.0, (double) bassBoostDb / 20.0) : 1.0;
        double invTanhK = (driveK > 1.001) ? (1.0 / Math.tanh(driveK)) : 1.0;
        double exciterNorm = 1.0 / 1.09108945118;

        double baseFreq = targetFreq;

        for (int i = 0; i < frames; i++) {
            // 1. Calculate Instantaneous Base Frequency
            if (isSweep) {
                double triangleProgress = 0.5 * (1.0 - Math.cos(sweepPhase));
                baseFreq = sweepMin + (sweepMax - sweepMin) * triangleProgress;
                sweepPhase += sweepStep;
                if (sweepPhase >= twoPi) sweepPhase -= twoPi;
            }
            // Principle 16: preserve scenario frequency without artificial 55Hz cap
            if (baseFreq < FREQ_FLOOR_HZ) baseFreq = FREQ_FLOOR_HZ;

            // 2. Frequency Modulation (FM / Vibrato)
            double instFreq = baseFreq;
            if (fmOn && fmDepth > 0.0) {
                double fmOffset = baseFreq * fmDepth * Math.sin(fmPhase);
                instFreq = Math.max(FREQ_FLOOR_HZ, baseFreq + fmOffset);
                fmPhase += fmStep;
                if (fmPhase >= twoPi) fmPhase -= twoPi;
            }

            // Synthesize Carrier Wave (Exact analytical peak = 1.000000)
            double sig;
            if (subFreq > 0.1f && harmonicExciter) {
                double raw = Math.sin(carrierPhase) + 0.22 * Math.sin(carrierPhase * 2.0);
                sig = 0.78 * (raw * exciterNorm) + 0.22 * Math.sin(subPhase);
                subPhase += subStep;
                if (subPhase >= twoPi) subPhase -= twoPi;
            } else if (subFreq > 0.1f) {
                sig = 0.75 * Math.sin(carrierPhase) + 0.25 * Math.sin(subPhase);
                subPhase += subStep;
                if (subPhase >= twoPi) subPhase -= twoPi;
            } else if (harmonicExciter) {
                sig = (Math.sin(carrierPhase) + 0.22 * Math.sin(carrierPhase * 2.0)) * exciterNorm;
            } else {
                sig = Math.sin(carrierPhase);
            }

            // 3. Amplitude Modulation (AM / Tremolo)
            if (amOn && amDepth > 0.0) {
                double amEnv = 1.0 - (double) amDepth * 0.5 * (1.0 - Math.cos(amPhase));
                sig *= amEnv;
                amPhase += amStep;
                if (amPhase >= twoPi) amPhase -= twoPi;
            }

            // 4. Ring Modulation (RM)
            if (rmOn && rmDepth > 0.0) {
                double rmFactor = (1.0 - (double) rmDepth) + (double) rmDepth * Math.cos(rmPhase);
                sig *= rmFactor;
                rmPhase += rmStep;
                if (rmPhase >= twoPi) rmPhase -= twoPi;
            }

            // 5. Pure Undistorted Sine Wave Engine (-1.0 dBTP True-Peak Ceiling)
            // Zero flat-top hard clipping in limiterMode 0; smooth normalized tanh in limiterMode 1.
            final double TRUE_PEAK_CEILING = 0.89125; // -1.0 dBTP True-Peak Headroom
            if (limiterMode == 1 && driveK > 1.001) {
                sig = Math.tanh(driveK * sig) * invTanhK;
            }
            if (sig > 1.0) sig = 1.0;
            else if (sig < -1.0) sig = -1.0;
            if (mFadeOutRequested) {
                mGain -= invSr / FADE_OUT_SEC;
                if (mGain <= 0.0) { mGain = 0.0; mFadeOutDone = true; }
            } else if (mAuxSuspended) {
                if (mGain > 0.0) {
                    mGain -= invSr / AUX_MUTE_SEC;
                    if (mGain < 0.0) mGain = 0.0;
                }
            } else if (mGain < 1.0) {
                mGain += invSr / FADE_IN_SEC;
                if (mGain > 1.0) mGain = 1.0;
            }
            sig *= mGain;

            long quant = Math.round(sig * 32767.0 * TRUE_PEAK_CEILING);
            if (quant > 29200L) quant = 29200L;
            if (quant < -29200L) quant = -29200L;
            short sample = (short) quant;

            // 7. Vorta DSP Channel Phase & Trans2 BTL Output Matrix
            short sampleL = sample;
            short sampleR = sample;
            if (channelMode == 1) {
                sampleR = (short) (-sample); // 180° out of phase for shearing wave acoustic coupling
            } else if (channelMode == 2) {
                // BTL Mono Summing: both channels output identical in-phase signal
                sampleL = sample;
                sampleR = sample;
            } else if (channelMode == 3) {
                sampleR = 0; // Left channel only
            } else if (channelMode == 4) {
                sampleL = 0; // Right channel only
            }

            int idx = i * 2;
            stereoBuffer[idx] = sampleL;
            stereoBuffer[idx + 1] = sampleR;

            carrierPhase += twoPi * instFreq * invSr;
            if (carrierPhase >= twoPi) {
                carrierPhase -= twoPi * Math.floor(carrierPhase / twoPi);
            }
        }

        state[0] = carrierPhase;
        state[1] = sweepPhase;
        state[2] = fmPhase;
        state[3] = amPhase;
        state[4] = rmPhase;
        state[5] = subPhase;
        sInstantFreq = (float) baseFreq;
        return frames * 2;
    }

    private void auditBuffer(short[] stereoBuffer, int totalShorts) {
        int maxAbs = 0;
        double sumSq = 0.0;
        double sumVal = 0.0;
        int frames = totalShorts / 2;
        for (int i = 0; i < frames; i++) {
            int v = stereoBuffer[i * 2];
            int abs = Math.abs(v);
            if (abs > maxAbs) maxAbs = abs;
            sumSq += (double) v * (double) v;
            sumVal += (double) v;
        }
        double rms = Math.sqrt(sumSq / frames);
        double rmsDb = 20.0 * Math.log10(Math.max(1.0, rms) / 32767.0);
        double dcOffset = (sumVal / frames) / 32767.0;
        sLastPeak = maxAbs;
        sLastRmsDb = rmsDb;
        sLastDcOffset = dcOffset;

        int curVol = S6VolumeHelper.sTargetVolumeStep;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Throwable ignored) {}
        Log.i(AUDIT_TAG, String.format(
                "[VORTA_RESO_S6_V26] SR=%dHz | Ch=2(Trans2 68W BTL) | Freq=%.2fHz | Mod=[%s] | DSP=[%s] | Peak=%d/32767(-1.0dBTP) | RMS=%.1f(%.2fdBFS) | DC=%.5f | Vol=%d/15(FreeVol)",
                SAMPLE_RATE, sInstantFreq, buildModulationSummary(), buildVortaDspSummary(), maxAbs, rms, rmsDb, dcOffset, curVol));
    }

    private synchronized void startAudioIfNeeded() {
        if (mIsRunning) {
            return;
        }
        mIsRunning = true;
        sIsPlaying = true;
        mGain = 0.0;
        mFadeOutRequested = false;
        mFadeOutDone = false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
        mWiredGuardArmed = (wiredDev != null) || S6VolumeHelper.isWired(this);
        mAuxSuspended = false;
        sAuxSuspended = false;

        int minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufSize = Math.max(minBuf * 4, HAL_FRAMES * 24 * 4);

        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            mAudioTrack = new AudioTrack(attrs, fmt, bufSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (wiredDev != null && Build.VERSION.SDK_INT >= 23) {
                mAudioTrack.setPreferredDevice(wiredDev);
            }
        } else {
            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize,
                    AudioTrack.MODE_STREAM
            );
        }

        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                final int frameCount = HAL_FRAMES * 4; // 3,840 stereo frames (80ms, exact 4x S7/S6 HAL_FRAMES)
                short[] pcmBuffer = new short[frameCount * 2]; // 7,680 shorts
                double[] state = new double[6]; // carrier, sweep, fm, am, rm, sub
                long lastAuditTime = 0;

                for (int pre = 0; pre < 2 && mIsRunning; pre++) {
                    int preShorts = synthesizeFrameBuffer(
                            pcmBuffer, frameCount, state,
                            sIsSweepMode, sSweepMin, sSweepMax, sSweepPeriodSec, sTargetFreq, sSubFreq,
                            sFmEnabled, sFmDepth, sFmRateHz,
                            sAmEnabled, sAmDepth, sAmRateHz,
                            sRmEnabled, sRmDepth, sRmRateHz,
                            sBassBoostDb, sLimiterMode, sChannelMode, sHarmonicExciter
                    );
                    if (pre == 0 && mAuditRequested) {
                        mAuditRequested = false;
                        auditBuffer(pcmBuffer, preShorts);
                    }
                    if (mAudioTrack != null) {
                        mAudioTrack.write(pcmBuffer, 0, preShorts);
                    }
                }

                if (mAudioTrack != null && mIsRunning) {
                    mAudioTrack.play();
                    Log.i(TAG, "AudioTrack started at 48000Hz Stereo PCM16 (pre-filled 2 chunks, 0 underrun). Buf=" + bufSize);
                }

                while (mIsRunning && !Thread.currentThread().isInterrupted()) {
                    // Phase stays continuous across preset changes: a phase jump is a click.
                    mPhaseResetRequested = false;

                    boolean isSweep = sIsSweepMode;
                    double sweepMin = sSweepMin;
                    double sweepMax = sSweepMax;
                    double sweepPeriod = sSweepPeriodSec;
                    double targetFreq = sTargetFreq;
                    double subFreq = sSubFreq;

                    boolean fmOn = sFmEnabled;
                    double fmDepth = sFmDepth;
                    double fmRate = sFmRateHz;

                    boolean amOn = sAmEnabled;
                    double amDepth = sAmDepth;
                    double amRate = sAmRateHz;

                    boolean rmOn = sRmEnabled;
                    double rmDepth = sRmDepth;
                    double rmRate = sRmRateHz;

                    float boostDb = sBassBoostDb;
                    int limiter = sLimiterMode;
                    int channel = sChannelMode;
                    boolean exciter = sHarmonicExciter;

                    int writtenShorts = synthesizeFrameBuffer(
                            pcmBuffer,
                            frameCount,
                            state,
                            isSweep,
                            sweepMin,
                            sweepMax,
                            sweepPeriod,
                            targetFreq,
                            subFreq,
                            fmOn,
                            fmDepth,
                            fmRate,
                            amOn,
                            amDepth,
                            amRate,
                            rmOn,
                            rmDepth,
                            rmRate,
                            boostDb,
                            limiter,
                            channel,
                            exciter
                    );

                    long now = System.currentTimeMillis();
                    if (mAuditRequested || (mGain >= 0.999 && (now - lastAuditTime) >= 10000L)) {
                        mAuditRequested = false;
                        lastAuditTime = now;
                        auditBuffer(pcmBuffer, writtenShorts);
                    }

                    if (mAudioTrack != null && mIsRunning) {
                        mAudioTrack.write(pcmBuffer, 0, writtenShorts);
                    }
                }
            }
        }, "VortaResoS6AudioWorker");

        mAudioThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAudioDeviceCallback != null && Build.VERSION.SDK_INT >= 23) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            } catch (Throwable ignored) {}
            mAudioDeviceCallback = null;
        }
        if (mServiceVolReceiver != null) {
            try {
                unregisterReceiver(mServiceVolReceiver);
            } catch (Throwable ignored) {}
            mServiceVolReceiver = null;
        }
        stopAudio();
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
