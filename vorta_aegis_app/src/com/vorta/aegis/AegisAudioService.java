package com.vorta.aegis;

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
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.Random;

/**
 * Background 48kHz stereo phase-continuous synthesizer designed to layer concurrently with music apps
 * (Samsung Music, Spotify, YouTube Music) by intentionally omitting AudioFocus acquisition, while
 * keeping independent log-dB volume smoothing, zero-pop de-clicking, ultrasonic earphone protection,
 * becoming-noisy safety, and U321 Vorta mutual audio yielding.
 */
public class AegisAudioService extends Service {
    private static final String TAG = "AegisAudioService";
    private static final String CHANNEL_ID = "vorta_aegis_playback_channel";
    private static final int NOTIFICATION_ID = 9001;
    private static final long WAKELOCK_TIMEOUT_MS = 8L * 60L * 60L * 1000L;

    private static final String PREFS_NAME = "aegis_prefs";
    private static final String PREF_VOLUME_PCT = "in_app_volume_pct";
    private static final String PREF_MUTED = "in_app_muted";
    public static final String PREF_LAST_TAB = "last_selected_tab";
    public static final String PREF_LAST_PRESET_ID = "last_preset_id";

    public static final String ACTION_START = "com.vorta.aegis.ACTION_START";
    public static final String ACTION_STOP = "com.vorta.aegis.ACTION_STOP";
    public static final String ACTION_UPDATE_PRESET = "com.vorta.aegis.ACTION_UPDATE_PRESET";
    public static final String ACTION_SET_VOLUME = "com.vorta.aegis.ACTION_SET_VOLUME";
    public static final String ACTION_STATE_CHANGED = "com.vorta.aegis.ACTION_STATE_CHANGED";

    public static final String EXTRA_PRESET_ID = "extra_preset_id";
    public static final String EXTRA_VOLUME_PERCENT = "extra_volume_percent";
    public static final String EXTRA_MUTED = "extra_muted";
    public static final String EXTRA_FOCUS_PLAYING_TAB = "extra_focus_playing_tab";

    // Audio Engine Constants (48kHz native DAC, -1.0 dBTP true-peak ceiling)
    private static final int SAMPLE_RATE = 48000;
    private static final double MAX_AMP = 29200.0;
    // One-pole smoother coefficient (~15ms time constant at 48kHz to prevent zipper noise)
    private static final double GAIN_SMOOTH_ALPHA = 0.0014;

    private AudioTrack mAudioTrack;
    private Thread mRenderThread;
    private volatile boolean mIsRunning = false;
    private volatile boolean mFadeOut = false;

    // Synchronized DSP state
    private volatile PresetModel mCurrentPreset = null;
    private volatile long mPresetFrameCounter = 0L;
    private volatile float mCurrentLiveFreqHz = 0.0f;
    private volatile boolean mUltrasonicGuarded = false;

    // 64-bit continuous oscillator phases
    private double mPhaseL = 0.0;
    private double mPhaseR = 0.0;
    private double mPhaseFm = 0.0;
    private double mPhaseAm = 0.0;

    // Pink Noise Generator (3-pole IIR)
    private double b0 = 0, b1 = 0, b2 = 0;
    private final Random mRandom = new Random();

    // Independent in-app perceptual volume state
    private volatile int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
    private volatile boolean mIsMuted = false;
    private volatile double mTargetLinearGain = VolumeCurve.percentToLinearGain(VolumeCurve.DEFAULT_PERCENT);

    // System lifecycle helpers (AudioManager used strictly read-only for output-device sensing)
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock mWakeLock;
    private AudioManager mAudioManager;
    private BroadcastReceiver mExclusiveAudioReceiver;
    private BroadcastReceiver mBecomingNoisyReceiver;
    private boolean mNoisyRegistered = false;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AegisAudioService getService() {
            return AegisAudioService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        loadPersistedVolume();
        createNotificationChannel();
        registerExclusiveReceiver();
    }

    private void loadPersistedVolume() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int pct = VolumeCurve.clampPercent(prefs.getInt(PREF_VOLUME_PCT, VolumeCurve.DEFAULT_PERCENT));
        boolean muted = prefs.getBoolean(PREF_MUTED, false);
        mVolumePercent = pct;
        mIsMuted = muted;
        mTargetLinearGain = VolumeCurve.percentToLinearGain(pct);
    }

    private void savePersistedVolume() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_VOLUME_PCT, mVolumePercent)
                .putBoolean(PREF_MUTED, mIsMuted)
                .apply();
    }

    private void registerExclusiveReceiver() {
        if (mExclusiveAudioReceiver != null) return;
        try {
            mExclusiveAudioReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    if ("com.vorta.ACTION_EXCLUSIVE_AUDIO_START".equals(intent.getAction())) {
                        String sender = intent.getStringExtra("sender_pkg");
                        if (sender != null && !sender.equals(getPackageName()) && mIsRunning) {
                            Log.i(TAG, "Yielding audio output to sibling Vorta app: " + sender);
                            stopPlayback();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(mExclusiveAudioReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(mExclusiveAudioReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "registerExclusiveReceiver error: " + t.getMessage());
        }
    }

    private synchronized void registerBecomingNoisyReceiver() {
        if (mNoisyRegistered) return;
        try {
            if (mBecomingNoisyReceiver == null) {
                mBecomingNoisyReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent != null && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                            Log.i(TAG, "Audio becoming noisy (output disconnected) -> stopping safely");
                            stopPlayback();
                        }
                    }
                };
            }
            IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(mBecomingNoisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mBecomingNoisyReceiver, noisyFilter);
            }
            mNoisyRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "registerBecomingNoisyReceiver error: " + t.getMessage());
        }
    }

    private synchronized void unregisterBecomingNoisyReceiver() {
        if (!mNoisyRegistered || mBecomingNoisyReceiver == null) return;
        try {
            unregisterReceiver(mBecomingNoisyReceiver);
        } catch (Throwable ignored) {}
        mNoisyRegistered = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else if (ACTION_SET_VOLUME.equals(action)) {
            if (intent.hasExtra(EXTRA_VOLUME_PERCENT)) {
                setVolumePercent(intent.getIntExtra(EXTRA_VOLUME_PERCENT, mVolumePercent));
            }
            if (intent.hasExtra(EXTRA_MUTED)) {
                setMuted(intent.getBooleanExtra(EXTRA_MUTED, mIsMuted));
            }
        } else if (ACTION_START.equals(action) || ACTION_UPDATE_PRESET.equals(action)) {
            if (intent.hasExtra(EXTRA_VOLUME_PERCENT)) {
                setVolumePercent(intent.getIntExtra(EXTRA_VOLUME_PERCENT, mVolumePercent));
            }
            if (intent.hasExtra(EXTRA_MUTED)) {
                setMuted(intent.getBooleanExtra(EXTRA_MUTED, mIsMuted));
            }
            int presetId = intent.getIntExtra(EXTRA_PRESET_ID, 1);
            loadAndPlayPreset(presetId);
        }
        return START_NOT_STICKY;
    }

    public synchronized void loadAndPlayPreset(int presetId) {
        PresetModel target = PresetModel.findById(presetId);
        mCurrentPreset = target;
        // Persist owning tab/preset synchronously so next Activity launch opens directly on this tab without Tab-0 flash.
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_LAST_TAB, target.tabIndex)
                .putInt(PREF_LAST_PRESET_ID, target.id)
                .apply();
        mPresetFrameCounter = 0L;
        mFadeOut = false;
        mCurrentLiveFreqHz = target.isSweep ? target.sweepStart : target.freq1;
        acquireWakeLock();

        try {
            Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
            excl.putExtra("sender_pkg", getPackageName());
            sendBroadcast(excl);
        } catch (Throwable ignored) {}

        startForegroundCompat(buildNotification(target.title));

        if (!mIsRunning) {
            startPlayback();
        } else {
            broadcastStateChanged();
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public synchronized void startPlayback() {
        if (mIsRunning) {
            mFadeOut = false;
            return;
        }

        if (mRenderThread != null && mRenderThread.isAlive()) {
            try {
                mRenderThread.join(600);
            } catch (InterruptedException ignored) {}
        }

        acquireWakeLock();
        registerBecomingNoisyReceiver();

        mIsRunning = true;
        mFadeOut = false;

        int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferSize = Math.max(minBufferSize * 2, 8192);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

        mAudioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        mAudioTrack.play();

        mRenderThread = new Thread(new AudioRenderRunnable(), "AegisAudioRenderThread");
        mRenderThread.setPriority(Thread.MAX_PRIORITY);
        mRenderThread.start();
        broadcastStateChanged();
        Log.i(TAG, "Aegis Audio Engine Started (Concurrent Mix Mode, Vol=" + mVolumePercent + "%)");
    }

    public synchronized void stopPlayback() {
        if (!mIsRunning) {
            postStopCleanup();
            return;
        }
        mFadeOut = true;
        broadcastStateChanged();
    }

    private void postStopCleanup() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (AegisAudioService.this) {
                    if (!mIsRunning) {
                        mUltrasonicGuarded = false;
                        unregisterBecomingNoisyReceiver();
                        releaseWakeLock();
                        stopForeground(true);
                        broadcastStateChanged();
                        stopSelf();
                    }
                }
            }
        });
    }

    private void acquireWakeLock() {
        try {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorta:aegis_audio");
                    mWakeLock.setReferenceCounted(false);
                }
            }
            if (mWakeLock != null) {
                mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
            }
        } catch (Throwable t) {
            Log.w(TAG, "acquireWakeLock error: " + t.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } catch (Throwable ignored) {}
    }

    /** Read-only check whether wired/USB/Bluetooth earphones or headphones are connected. */
    @SuppressWarnings("deprecation")
    public boolean isPersonalAudioConnected() {
        if (mAudioManager == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo[] outputs = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (outputs != null) {
                    for (AudioDeviceInfo dev : outputs) {
                        int type = dev.getType();
                        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || (Build.VERSION.SDK_INT >= 31 && type == 26)) { // TYPE_BLE_HEADSET
                            return true;
                        }
                    }
                }
                return false;
            }
            return mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isUltrasonicGuarded() {
        return isPlaying() && mUltrasonicGuarded;
    }

    private void broadcastStateChanged() {
        try {
            Intent intent = new Intent(ACTION_STATE_CHANGED);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    public boolean isPlaying() {
        return mIsRunning && !mFadeOut;
    }

    public PresetModel getCurrentPreset() {
        return mCurrentPreset;
    }

    public float getCurrentLiveFreqHz() {
        return isPlaying() ? mCurrentLiveFreqHz : 0.0f;
    }

    public int getVolumePercent() {
        return mVolumePercent;
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public void setVolumePercent(int percent) {
        int clamped = VolumeCurve.clampPercent(percent);
        mVolumePercent = clamped;
        mTargetLinearGain = VolumeCurve.percentToLinearGain(clamped);
        savePersistedVolume();
    }

    public void setMuted(boolean muted) {
        mIsMuted = muted;
        savePersistedVolume();
    }

    /** Backwards-compatible linear gain setter (maps linear [0..1] safely). */
    public void setMasterGain(double gain) {
        double clamped = Math.max(0.0, Math.min(1.0, gain));
        mTargetLinearGain = clamped;
        mVolumePercent = (int) Math.round(clamped * 100.0);
        savePersistedVolume();
    }

    private class AudioRenderRunnable implements Runnable {
        @Override
        public void run() {
            final int framesPerBuffer = 1920; // 960 * 2 frames (40ms): exact Exynos 7420 S6 HAL multiple
            final short[] buffer = new short[framesPerBuffer * 2];

            double fadeInGain = 0.0;
            final double fadeInStep = 1.0 / (SAMPLE_RATE * 1.0); // 1.0 sec fade in
            final double fadeOutStep = 1.0 / (SAMPLE_RATE * 0.5); // 0.5 sec fade out
            double fadeOutGain = 1.0;
            double smoothedGain = mIsMuted ? 0.0 : mTargetLinearGain;

            int lastPresetId = -1;
            double prevRawL = 0.0;
            double prevRawR = 0.0;
            double declickL = 0.0;
            double declickR = 0.0;

            try {
                while (mIsRunning) {
                    PresetModel p = mCurrentPreset;
                    if (p == null) {
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        continue;
                    }

                    boolean guard = (p.noiseType == 2) && isPersonalAudioConnected();
                    mUltrasonicGuarded = guard;

                    double durationSamples = Math.max(1.0, p.sweepDurationSec * SAMPLE_RATE);
                    long cycleFrames = Math.max(2L, Math.round(durationSamples * 2.0));
                    int writtenFrames = 0;

                    for (int i = 0; i < framesPerBuffer; i++) {
                        mPresetFrameCounter++;

                        // 1. Base frequencies with smooth triangle ping-pong sweep
                        double fL;
                        double fR;

                        if (p.isSweep) {
                            double cyclePos = (mPresetFrameCounter % cycleFrames) / durationSamples;
                            double tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                            double sweepFreq = p.sweepStart + (p.sweepEnd - p.sweepStart) * tri;
                            fL = sweepFreq;
                            fR = sweepFreq;
                        } else {
                            fL = p.freq1;
                            if (p.binauralBeat > 0) {
                                fR = p.freq1 + p.binauralBeat;
                            } else if (p.freq2 > 0) {
                                fR = p.freq2;
                            } else {
                                fR = p.freq1;
                            }
                        }

                        // Record steady nominal carrier Hz before FM modulation offset
                        if (i == 0) {
                            mCurrentLiveFreqHz = (float) fL;
                        }

                        // 2. Frequency Modulation (FM)
                        if (p.fmMod && p.fmRate > 0) {
                            mPhaseFm += (2.0 * Math.PI * p.fmRate) / SAMPLE_RATE;
                            if (mPhaseFm > 2.0 * Math.PI) mPhaseFm -= 2.0 * Math.PI;
                            double fmOffset = Math.sin(mPhaseFm) * (fL * p.fmDepth);
                            fL += fmOffset;
                            fR += fmOffset;
                        }

                        // 3. Amplitude Modulation (AM)
                        double amGain = 1.0;
                        if (p.amMod && p.amRate > 0) {
                            mPhaseAm += (2.0 * Math.PI * p.amRate) / SAMPLE_RATE;
                            if (mPhaseAm > 2.0 * Math.PI) mPhaseAm -= 2.0 * Math.PI;
                            amGain = 1.0 - (p.amDepth * 0.5 * (1.0 - Math.sin(mPhaseAm)));
                        }

                        // 4. Advance 64-bit continuous phases
                        mPhaseL += (2.0 * Math.PI * fL) / SAMPLE_RATE;
                        if (mPhaseL > 2.0 * Math.PI) mPhaseL -= 2.0 * Math.PI;

                        mPhaseR += (2.0 * Math.PI * fR) / SAMPLE_RATE;
                        if (mPhaseR > 2.0 * Math.PI) mPhaseR -= 2.0 * Math.PI;

                        // 5. Generate waveform (preserving freq2 & avoiding dipole cancellation)
                        double sampleL;
                        double sampleR;
                        if (p.isAntiPhase) {
                            if (p.binauralBeat > 0) {
                                sampleL = -Math.sin(mPhaseL);
                                sampleR = -Math.sin(mPhaseR);
                            } else if (p.freq2 > 0) {
                                double mixed = (-0.65 * Math.sin(mPhaseL)) + (0.35 * Math.sin(mPhaseR));
                                sampleL = mixed;
                                sampleR = mixed;
                            } else {
                                double inv = -Math.sin(mPhaseL);
                                sampleL = inv;
                                sampleR = inv;
                            }
                        } else {
                            sampleL = Math.sin(mPhaseL);
                            sampleR = Math.sin(mPhaseR);
                        }

                        // 6. Special Noise / Bio-Modulation Modes
                        if (p.noiseType == 1) {
                            double white = (mRandom.nextDouble() * 2.0 - 1.0);
                            b0 = 0.99765 * b0 + white * 0.0990460;
                            b1 = 0.96300 * b1 + white * 0.2965164;
                            b2 = 0.57000 * b2 + white * 1.0526913;
                            double pink = (b0 + b1 + b2 + white * 0.1848) * 0.12;
                            sampleL = sampleL * 0.85 + pink * 0.15;
                            sampleR = sampleR * 0.85 + pink * 0.15;
                        } else if (p.noiseType == 3) {
                            double flutterPhase = (2.0 * Math.PI * 38.5 * mPresetFrameCounter) / SAMPLE_RATE;
                            double flutter = 0.6 + 0.4 * Math.sin(flutterPhase);
                            sampleL *= flutter;
                            sampleR *= flutter;
                        }

                        // Zero-pop preset-switch de-clicker
                        if (p.id != lastPresetId) {
                            if (lastPresetId != -1) {
                                declickL = prevRawL - sampleL;
                                declickR = prevRawR - sampleR;
                            }
                            lastPresetId = p.id;
                        }
                        if (Math.abs(declickL) > 1e-6 || Math.abs(declickR) > 1e-6) {
                            sampleL += declickL;
                            sampleR += declickR;
                            declickL *= 0.993;
                            declickR *= 0.993;
                        }
                        prevRawL = sampleL;
                        prevRawR = sampleR;

                        // 7. Apply AM, smoothed perceptual volume (+ ultrasonic earphone guard), and fade envelopes
                        sampleL *= amGain;
                        sampleR *= amGain;

                        if (fadeInGain < 1.0) {
                            fadeInGain += fadeInStep;
                            if (fadeInGain > 1.0) fadeInGain = 1.0;
                        }

                        double targetGain = guard ? (mTargetLinearGain * 0.25) : mTargetLinearGain;
                        double effectiveTarget = mIsMuted ? 0.0 : targetGain;
                        smoothedGain += GAIN_SMOOTH_ALPHA * (effectiveTarget - smoothedGain);

                        double envGain = fadeInGain;
                        if (mFadeOut) {
                            fadeOutGain -= fadeOutStep;
                            if (fadeOutGain <= 0.0) {
                                fadeOutGain = 0.0;
                                mIsRunning = false;
                            }
                            envGain *= fadeOutGain;
                        } else if (fadeOutGain < 1.0) {
                            fadeOutGain = Math.min(1.0, fadeOutGain + fadeInStep);
                            envGain *= fadeOutGain;
                        }

                        double totalGain = smoothedGain * envGain * MAX_AMP;
                        buffer[i * 2] = (short) Math.max(-MAX_AMP, Math.min(MAX_AMP, sampleL * totalGain));
                        buffer[i * 2 + 1] = (short) Math.max(-MAX_AMP, Math.min(MAX_AMP, sampleR * totalGain));
                        writtenFrames++;

                        if (!mIsRunning) break;
                    }

                    if (writtenFrames > 0 && mAudioTrack != null
                            && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        int writeResult = mAudioTrack.write(buffer, 0, writtenFrames * 2);
                        if (writeResult < 0) {
                            Log.w(TAG, "AudioTrack write error: " + writeResult);
                            mIsRunning = false;
                        }
                    }
                }
            } finally {
                try {
                    if (mAudioTrack != null) {
                        mAudioTrack.stop();
                        mAudioTrack.release();
                        mAudioTrack = null;
                    }
                } catch (Exception ignored) {}
                postStopCleanup();
                Log.i(TAG, "Aegis Audio Engine Safely Stopped");
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "보르타 이지스 무음 재생 채널",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("음악 동시재생 지원 안티 주파수 · 생체 음향 재생");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String scenarioTitle) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(EXTRA_FOCUS_PLAYING_TAB, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stopIntent = new Intent(this, AegisAudioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("보르타 이지스 · 재생 중")
                .setContentText(scenarioTitle)
                .setSmallIcon(R.drawable.ic_aegis_shield)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(R.drawable.ic_stop_square, "중지", stopPendingIntent);

        return builder.build();
    }

    @Override
    public void onDestroy() {
        if (mExclusiveAudioReceiver != null) {
            try {
                unregisterReceiver(mExclusiveAudioReceiver);
            } catch (Throwable ignored) {}
            mExclusiveAudioReceiver = null;
        }
        unregisterBecomingNoisyReceiver();
        releaseWakeLock();
        mUltrasonicGuarded = false;
        mFadeOut = false;
        mIsRunning = false;
        super.onDestroy();
    }
}
