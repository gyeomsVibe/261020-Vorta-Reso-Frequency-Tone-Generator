package com.vorta.freqgen;

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
 * Pure 64-bit NCO acoustic synthesis service with 32-bit float AudioTrack output,
 * TPDF-dithered 16-bit fallback, phase-continuous frequency slewing, 250ms wired AUX debounce,
 * and automatic AudioTrack ERROR_DEAD_OBJECT self-recovery.
 */
public class AudioEngineService extends Service {
    private static final String TAG = "VortaFreqGenEngine";
    private static final String AUDIT_TAG = "VortaAudioAudit";
    private static final String CHANNEL_ID = "vorta_freqgen_silent_channel";
    private static final int NOTIFICATION_ID = 3001;

    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static volatile int sSampleRate = DEFAULT_SAMPLE_RATE;
    public static volatile boolean sIsFloatPipeline = false;

    public static final float MAX_FREQ_HZ = 63.0f; // Supports up to ISO 16283 63 Hz band (55 Hz default ceiling for S6/S7 presets)
    public static final float MIN_FREQ_HZ = 0.5f;
    public static final double TRUE_PEAK_HEADROOM = PureDspEngine.TRUE_PEAK_HEADROOM;
    public static final double MAX_AMPLITUDE = PureDspEngine.MAX_AMPLITUDE_16;
    private static final double FADE_IN_SEC = 0.8;
    private static final double FADE_OUT_SEC = 0.4;
    private static final double AUX_MUTE_SEC = 0.02;

    // Shared runtime engine state
    public static volatile boolean sIsEngineRunning = false;
    public static volatile boolean sAuxSuspended = false;
    public static volatile float sManualFreq = 28.0f;
    public static volatile boolean sManualDualEnabled = false;
    public static volatile float sManualSecondaryFreq = 32.0f;
    public static volatile boolean sManualFreqActive = false;

    // Telemetry snapshot
    public static volatile double sLivePeak = 0.0;
    public static volatile double sLiveRmsDbfs = -120.0;
    public static volatile double sLiveDcOffset = 0.0;

    // Modulation parameters
    public static volatile boolean sFmEnabled = false;
    public static volatile float sFmDepth = 0.18f;
    public static volatile float sFmRateHz = 7.83f;

    public static volatile boolean sAmEnabled = false;
    public static volatile float sAmDepth = 0.15f;
    public static volatile float sAmRateHz = 5.70f;

    public static volatile boolean sRmEnabled = false;
    public static volatile float sRmDepth = 0.30f;
    public static volatile float sRmRateHz = 35.0f;

    // Active sweep items
    public static final List<SweepItem> sSweepItems = new ArrayList<SweepItem>();
    public static final Object sLock = new Object();

    private AudioTrack mAudioTrack;
    private Thread mAudioThread;
    private volatile boolean mThreadRunning = false;
    private volatile boolean mWiredGuardArmed = false;
    private volatile boolean mAuxSuspended = false;
    private final PureDspEngine.AuxDebouncePolicy mAuxDebounce = new PureDspEngine.AuxDebouncePolicy();

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

    @Override
    public void onCreate() {
        super.onCreate();
        createSilentNotificationChannel();
        acquireWakeLock();
        muteSystemNotificationSounds();
        FreqGenVolumeHelper.syncTargetFromSystem(this);
        FreqGenVolumeHelper.ensureTargetVolumeIfNeeded(this, false);
        registerExclusiveReceiver();
        registerVolumeGuardReceiver();
        registerAuxDeviceCallback();
    }

    private AudioDeviceInfo findWiredOutputDevice(AudioManager am) {
        if (am == null || Build.VERSION.SDK_INT < 23) return null;
        try {
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devs != null) {
                for (int i = 0; i < devs.length; i++) {
                    if (FreqGenVolumeHelper.isWiredDeviceType(devs[i].getType())) return devs[i];
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

    private void updateSilentNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
        } catch (Throwable ignored) {}
    }

    private void handleWiredStateChange(String reason) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
            boolean wiredNow = (wiredDev != null) || FreqGenVolumeHelper.isWired(this);
            if (wiredNow) {
                mAuxDebounce.onHardwareConnected();
                mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
                if (mThreadRunning) mWiredGuardArmed = true;
                // Only pin setPreferredDevice on API 26+; API 24 WM1840 routes natively and avoids stale route lockups
                if (mAudioTrack != null && wiredDev != null && Build.VERSION.SDK_INT >= 26) {
                    try {
                        mAudioTrack.setPreferredDevice(wiredDev);
                    } catch (Throwable ignored) {}
                }
                if (mAuxSuspended) {
                    mAuxSuspended = false;
                    sAuxSuspended = false;
                    FreqGenVolumeHelper.onHeadsetPlug(this, 1);
                    if (mThreadRunning) {
                        updateSilentNotification(buildActiveNotifyText());
                    }
                    Log.i(TAG, "[AUX_GUARD] Wired reconnected (" + reason + ") -> resuming seamlessly");
                }
            } else if (mThreadRunning && mWiredGuardArmed && !mAuxSuspended) {
                // Always clear stale preferred device so AudioTrack never gets stuck in an unusable route
                if (mAudioTrack != null && Build.VERSION.SDK_INT >= 23) {
                    try {
                        mAudioTrack.setPreferredDevice(null);
                    } catch (Throwable ignored) {}
                }
                long now = SystemClock.elapsedRealtime();
                if (mAuxDebounce.onHardwareDisconnected(now, mAuxSuspended)) {
                    engageAuxMute(reason);
                } else {
                    mHandler.removeCallbacks(mAuxSuspendCheckRunnable);
                    mHandler.postDelayed(mAuxSuspendCheckRunnable, PureDspEngine.AUX_DISCONNECT_DEBOUNCE_MS + 15L);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "handleWiredStateChange: " + t.getMessage());
        }
    }

    private void checkPendingAuxDisconnect() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            boolean wiredNow = (findWiredOutputDevice(am) != null) || FreqGenVolumeHelper.isWired(this);
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
        FreqGenVolumeHelper.onHeadsetPlug(this, 0);
        updateSilentNotification("AUX 분리 감지 - 스피커 차단 / 재연결 대기");
        Log.w(TAG, "[AUX_GUARD] Wired disconnected (" + reason + ") -> speaker output muted");
    }

    private String buildActiveNotifyText() {
        return String.format(Locale.US, "%dHz %s | -1.0 dBTP",
                sSampleRate, sIsFloatPipeline ? "32-bit Float" : "16-bit TPDF");
    }

    private void registerVolumeGuardReceiver() {
        if (FreqGenVolumeHelper.isPersonalDevice()) return;
        if (mServiceVolReceiver != null) return;
        try {
            mServiceVolReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                        FreqGenVolumeHelper.onVolumeChanged(context, intent);
                    } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                        int st = intent.getIntExtra("state", 0);
                        FreqGenVolumeHelper.enforceSystemFlags(context);
                        FreqGenVolumeHelper.onHeadsetPlug(context, st);
                        handleWiredStateChange("HEADSET_PLUG_" + st);
                    } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                        handleWiredStateChange("BECOMING_NOISY");
                    } else if ("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE".equals(action)) {
                        FreqGenVolumeHelper.enforceSystemFlags(context);
                        FreqGenVolumeHelper.restoreIfClamped(context, "SAFE_MEDIA_VOLUME_ACTIVE");
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.media.VOLUME_CHANGED_ACTION");
            filter.addAction(Intent.ACTION_HEADSET_PLUG);
            filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            filter.addAction("com.sec.android.intent.action.SAFE_MEDIA_VOLUME_ACTIVE");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(mServiceVolReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mServiceVolReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "registerVolumeGuardReceiver error: " + t.getMessage());
        }
    }

    private void registerExclusiveReceiver() {
        if (mExclusiveReceiver != null) return;
        try {
            mExclusiveReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    if ("com.vorta.ACTION_EXCLUSIVE_AUDIO_START".equals(intent.getAction())) {
                        String sender = intent.getStringExtra("sender_pkg");
                        if (sender != null && !sender.equals(getPackageName())) {
                            Log.i(TAG, "Yielding audio output to sibling Vorta app: " + sender);
                            sManualFreqActive = false;
                            synchronized (sLock) {
                                for (SweepItem item : sSweepItems) {
                                    item.isPlaying = false;
                                }
                            }
                            mThreadRunning = false;
                            stopForeground(true);
                            stopSelf();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(mExclusiveReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mExclusiveReceiver, filter);
            }
        } catch (Throwable ignored) {}
    }

    private void createSilentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "보르타 프로 무음 발진 채널",
                    NotificationManager.IMPORTANCE_LOW
            );
            chan.setDescription("주파수 발생기 Pro 무음 백그라운드 출력 채널");
            chan.setSound(null, null);
            chan.enableVibration(false);
            chan.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(chan);
            }
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorta:FreqGenWakeLock");
                mWakeLock.acquire(24 * 60 * 60 * 1000L);
            }
        } catch (Exception e) {
            Log.w(TAG, "WakeLock acquisition notice: " + e.getMessage());
        }
    }

    private void muteSystemNotificationSounds() {
        if (FreqGenVolumeHelper.isPersonalDevice() || FreqGenVolumeHelper.isA30Device()) return;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Mute system sounds notice: " + e.getMessage());
        }
    }

    private Notification buildNotification(String text) {
        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, notifyIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("VORTA PRO | " + buildActiveNotifyText())
         .setContentText(text)
         .setSmallIcon(R.mipmap.ic_launcher)
         .setContentIntent(pi)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setDefaults(0);

        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(NOTIFICATION_ID, buildNotification("엔진 초기화 중"));
        }
        if (intent == null) {
            if (!mThreadRunning) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }
        if ("stop".equalsIgnoreCase(intent.getStringExtra("action"))) {
            sManualFreqActive = false;
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
        startForeground(NOTIFICATION_ID, buildNotification("무왜곡 백그라운드 엔진 대기"));
        sIsEngineRunning = true;
        startAudioLoop();
        return START_STICKY;
    }

    private int queryNativeSampleRate(AudioManager am) {
        if (am != null) {
            try {
                String sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                if (sr != null) {
                    int parsed = Integer.parseInt(sr.trim());
                    if (parsed >= 44100 && parsed <= 192000) return parsed;
                }
            } catch (Throwable ignored) {}
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private int queryNativeFramesPerBuffer(AudioManager am) {
        if (am != null) {
            try {
                String fpb = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                if (fpb != null) {
                    int parsed = Integer.parseInt(fpb.trim());
                    if (parsed >= 64 && parsed <= 4096) return parsed;
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
        int targetBuf = Math.max(Math.max(minBuf * 4, chunkFrames * 6 * bytesPerFrame), sampleRate / 2 * bytesPerFrame);

        AudioTrack track = null;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                AudioFormat fmt = new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(encoding)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build();
                track = new AudioTrack(attrs, fmt, targetBuf, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    track.release();
                    return null;
                }
                if (wiredDev != null && Build.VERSION.SDK_INT >= 26) {
                    try {
                        track.setPreferredDevice(wiredDev);
                    } catch (Throwable ignored) {}
                }
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                        targetBuf, AudioTrack.MODE_STREAM);
            }
        } catch (Throwable t) {
            Log.w(TAG, "AudioTrack init notice (float=" + tryFloat + "): " + t.getMessage());
            if (track != null) {
                try { track.release(); } catch (Throwable ignored) {}
            }
            return null;
        }
        return track;
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
        final int mult = Math.max(1, (int) Math.ceil((sampleRate * 0.060) / (double) halFrames)); // ~60-80ms chunk
        final int chunkFrames = halFrames * mult;

        AudioDeviceInfo wiredDev = findWiredOutputDevice(am);
        mWiredGuardArmed = (wiredDev != null) || FreqGenVolumeHelper.isWired(this);
        mAuxSuspended = false;
        sAuxSuspended = false;
        mAuxDebounce.onHardwareConnected();

        mAudioTrack = createBestAudioTrack(am, sampleRate, chunkFrames);
        updateSilentNotification(buildActiveNotifyText());

        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

                final float[] floatBuf = new float[chunkFrames * 2];
                final short[] pcm16Buf = new short[chunkFrames * 2];
                final PureDspEngine.DspState st = new PureDspEngine.DspState();
                boolean wasActive = false;

                final double twoPi = PureDspEngine.TWO_PI;
                final double invSr = 1.0 / (double) sampleRate;
                final double hzAlpha = PureDspEngine.computeFreqSlewAlpha(sampleRate);
                final double fadeInStep = 1.0 / (FADE_IN_SEC * sampleRate);
                final double fadeOutStep = 1.0 / (FADE_OUT_SEC * sampleRate);
                final double auxMuteStep = 1.0 / (AUX_MUTE_SEC * sampleRate);

                if (mAudioTrack != null) {
                    writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                    writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                    try {
                        mAudioTrack.play();
                    } catch (Throwable e) {
                        Log.e(TAG, "AudioTrack play error", e);
                    }
                }

                long lastAuditMs = 0L;
                long lastSelfCheckMs = 0L;

                while (mThreadRunning) {
                    boolean chunkHasActive = sManualFreqActive;
                    if (!chunkHasActive) {
                        synchronized (sLock) {
                            for (int s = 0; s < sSweepItems.size(); s++) {
                                if (sSweepItems.get(s).isPlaying) {
                                    chunkHasActive = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (chunkHasActive && !wasActive) {
                        wasActive = true;
                        try {
                            Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
                            excl.putExtra("sender_pkg", getPackageName());
                            sendBroadcast(excl);
                        } catch (Throwable ignored) {}
                    } else if (!chunkHasActive && wasActive) {
                        wasActive = false;
                    }

                    double fmStep = twoPi * sFmRateHz * invSr;
                    double amStep = twoPi * sAmRateHz * invSr;
                    double rmStep = twoPi * sRmRateHz * invSr;

                    for (int f = 0; f < chunkFrames; f++) {
                        double sampleSum = 0.0;
                        int activeSources = 0;
                        double lastActiveHz = 28.0;

                        // 1. Manual Single / Dual-Sine Pure Carrier
                        if (sManualFreqActive && sManualFreq >= MIN_FREQ_HZ) {
                            activeSources++;
                            double targetF1 = Math.min(sManualFreq, MAX_FREQ_HZ);
                            st.smoothedManualHz = PureDspEngine.slewFrequency(st.smoothedManualHz, targetF1, hzAlpha);
                            double instFreq = st.smoothedManualHz;
                            if (sFmEnabled && sFmDepth > 0.0f) {
                                instFreq = Math.max(MIN_FREQ_HZ, instFreq * (1.0 + sFmDepth * Math.sin(st.fmPhase)));
                            }
                            instFreq = Math.min(instFreq, (double) MAX_FREQ_HZ);
                            lastActiveHz = instFreq;

                            double val = Math.sin(st.manualPhase);
                            st.manualPhase = PureDspEngine.wrapPhase(st.manualPhase + twoPi * instFreq * invSr);

                            if (sManualDualEnabled && sManualSecondaryFreq >= MIN_FREQ_HZ) {
                                double targetF2 = Math.min(sManualSecondaryFreq, MAX_FREQ_HZ);
                                st.smoothedManualHz2 = PureDspEngine.slewFrequency(st.smoothedManualHz2, targetF2, hzAlpha);
                                double v2 = Math.sin(st.manualPhase2);
                                st.manualPhase2 = PureDspEngine.wrapPhase(st.manualPhase2 + twoPi * st.smoothedManualHz2 * invSr);
                                val = 0.5 * (val + v2);
                                lastActiveHz = 0.5 * (instFreq + st.smoothedManualHz2);
                            }

                            if (sAmEnabled && sAmDepth > 0.0f) {
                                val *= (1.0 - sAmDepth * 0.5 * (1.0 - Math.cos(st.amPhase)));
                            }
                            if (sRmEnabled && sRmDepth > 0.0f) {
                                val *= ((1.0 - sRmDepth) + sRmDepth * Math.cos(st.rmPhase));
                            }
                            sampleSum += val;
                        }

                        // 2. Active Sweeps / Dual-Sine Scenarios
                        synchronized (sLock) {
                            int numSweeps = sSweepItems.size();
                            for (int s = 0; s < numSweeps; s++) {
                                SweepItem item = sSweepItems.get(s);
                                if (!item.isPlaying) continue;

                                activeSources++;
                                double tri = (item.sweepPos <= 1.0) ? item.sweepPos : (2.0 - item.sweepPos);
                                float clampedStart = Math.min(item.startHz, MAX_FREQ_HZ);
                                float clampedEnd = Math.min(item.endHz, MAX_FREQ_HZ);
                                double currentFreq = clampedStart + (clampedEnd - clampedStart) * tri;
                                currentFreq = Math.min(Math.max(currentFreq, (double) MIN_FREQ_HZ), (double) MAX_FREQ_HZ);
                                item.currentHz = (float) currentFreq;

                                if (Math.abs(clampedEnd - clampedStart) > 0.001f) {
                                    double sweepStep = 2.0 / (Math.max(1.0, (double) item.durationSec) * sampleRate);
                                    item.sweepPos += sweepStep;
                                    if (item.sweepPos >= 2.0) {
                                        if (item.isLoop) item.sweepPos -= 2.0;
                                        else {
                                            item.isPlaying = false;
                                            item.sweepPos = 0.0;
                                        }
                                    }
                                }

                                double instSweepFreq = currentFreq;
                                if (sFmEnabled && sFmDepth > 0.0f) {
                                    instSweepFreq = Math.max(MIN_FREQ_HZ, currentFreq * (1.0 + sFmDepth * Math.sin(st.fmPhase)));
                                }
                                instSweepFreq = Math.min(instSweepFreq, (double) MAX_FREQ_HZ);
                                lastActiveHz = instSweepFreq;

                                double sweepVal = Math.sin(item.phase);
                                item.phase = PureDspEngine.wrapPhase(item.phase + twoPi * instSweepFreq * invSr);

                                if (item.secondaryHz >= MIN_FREQ_HZ) {
                                    double secHz = Math.min(item.secondaryHz, MAX_FREQ_HZ);
                                    double secVal = Math.sin(item.phase2);
                                    item.phase2 = PureDspEngine.wrapPhase(item.phase2 + twoPi * secHz * invSr);
                                    sweepVal = 0.5 * (sweepVal + secVal);
                                    lastActiveHz = 0.5 * (instSweepFreq + secHz);
                                }

                                if (sAmEnabled && sAmDepth > 0.0f) {
                                    sweepVal *= (1.0 - sAmDepth * 0.5 * (1.0 - Math.cos(st.amPhase)));
                                }
                                if (sRmEnabled && sRmDepth > 0.0f) {
                                    sweepVal *= ((1.0 - sRmDepth) + sRmDepth * Math.cos(st.rmPhase));
                                }
                                sampleSum += sweepVal;
                            }
                        }

                        if (sFmEnabled) st.fmPhase = PureDspEngine.wrapPhase(st.fmPhase + fmStep);
                        if (sAmEnabled) st.amPhase = PureDspEngine.wrapPhase(st.amPhase + amStep);
                        if (sRmEnabled) st.rmPhase = PureDspEngine.wrapPhase(st.rmPhase + rmStep);

                        double outNormalized = 0.0;
                        if (!mAuxSuspended && activeSources > 0) {
                            double normRaw = PureDspEngine.clamp(sampleSum / (double) activeSources, -1.0, 1.0);
                            st.prevSampleNorm = st.lastSampleNorm;
                            st.lastSampleNorm = normRaw;
                            st.tailHz = PureDspEngine.clamp(lastActiveHz, (double) MIN_FREQ_HZ, (double) MAX_FREQ_HZ);
                            st.tailLocked = false;
                            if (st.envGain < 1.0) {
                                st.envGain = Math.min(1.0, st.envGain + fadeInStep);
                            }
                            outNormalized = normRaw * st.envGain * TRUE_PEAK_HEADROOM;
                        } else if (st.envGain > 0.0) {
                            if (!st.tailLocked) {
                                st.tailPhase = PureDspEngine.matchTailPhase(st.prevSampleNorm, st.lastSampleNorm);
                                st.tailLocked = true;
                            }
                            st.tailPhase = PureDspEngine.wrapPhase(st.tailPhase + twoPi * st.tailHz * invSr);
                            double tailVal = Math.sin(st.tailPhase);
                            st.prevSampleNorm = st.lastSampleNorm;
                            st.lastSampleNorm = tailVal;
                            st.envGain = Math.max(0.0, st.envGain - (mAuxSuspended ? auxMuteStep : fadeOutStep));
                            outNormalized = tailVal * st.envGain * TRUE_PEAK_HEADROOM;
                        } else {
                            st.tailLocked = false;
                            st.prevSampleNorm = 0.0;
                            st.lastSampleNorm = 0.0;
                        }

                        if (outNormalized > TRUE_PEAK_HEADROOM) outNormalized = TRUE_PEAK_HEADROOM;
                        else if (outNormalized < -TRUE_PEAK_HEADROOM) outNormalized = -TRUE_PEAK_HEADROOM;

                        float fSample = (float) outNormalized;
                        int idx = f * 2;
                        floatBuf[idx] = fSample;
                        floatBuf[idx + 1] = fSample;
                    }

                    long nowMs = SystemClock.elapsedRealtime();
                    if ((chunkHasActive && (nowMs - lastAuditMs >= 10000L || lastAuditMs == 0L))) {
                        lastAuditMs = nowMs;
                        PureDspEngine.BufferStats bs = PureDspEngine.measureBufferStats(floatBuf, chunkFrames);
                        sLivePeak = bs.peakAbs;
                        sLiveRmsDbfs = bs.rmsDbfs;
                        sLiveDcOffset = bs.dcOffset;
                        Log.i(AUDIT_TAG, String.format(Locale.US,
                                "[VORTA_PRO_V33] SR=%dHz Mode=%s Peak=%.5f RMS=%.2fdBFS DC=%.6f UnderrunGuard=0",
                                sampleRate, sIsFloatPipeline ? "PCM_FLOAT_32" : "PCM_16_TPDF",
                                bs.peakAbs, bs.rmsDbfs, bs.dcOffset));
                    }

                    // Periodic AUX state self-check ensures missed callbacks never lock silence permanently
                    if ((nowMs - lastSelfCheckMs) >= 600L) {
                        lastSelfCheckMs = nowMs;
                        if (mAuxSuspended && FreqGenVolumeHelper.isWired(AudioEngineService.this)) {
                            handleWiredStateChange("LOOP_SELF_CHECK_RECONNECT");
                        }
                    }

                    if (mThreadRunning && mAudioTrack != null) {
                        int ret = writeChunk(mAudioTrack, floatBuf, pcm16Buf, chunkFrames, st);
                        if (PureDspEngine.AuxDebouncePolicy.shouldRebuildAudioTrack(ret)) {
                            Log.w(TAG, "[AUDIO_RECOVERY] AudioTrack.write returned " + ret + " -> rebuilding track seamlessly");
                            releaseTrackQuietly(mAudioTrack);
                            st.envGain = 0.0; // smooth fade-in upon rebuild
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
        }, "VortaFreqGenAudioThread");

        mAudioThread.start();
    }

    private int writeChunk(AudioTrack track, float[] floatBuf, short[] pcm16Buf, int frames, PureDspEngine.DspState st) {
        int totalSamples = frames * 2;
        if (sIsFloatPipeline && Build.VERSION.SDK_INT >= 21) {
            return track.write(floatBuf, 0, totalSamples, AudioTrack.WRITE_BLOCKING);
        }
        PureDspEngine.quantizeToPcm16Tpdf(floatBuf, pcm16Buf, totalSamples, st);
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
        sIsEngineRunning = false;
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
