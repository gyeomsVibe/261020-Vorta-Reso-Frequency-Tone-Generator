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
import android.media.AudioDeviceCallback;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Background 48kHz stereo phase-continuous synthesizer designed to layer concurrently with other media apps
 * without grabbing audio focus, featuring:
 * - API 21+ ENCODING_PCM_FLOAT output with automatic ENCODING_PCM_16BIT TPDF dither fallback
 * - Session-generation cleanup guard & ERROR_DEAD_OBJECT AudioTrack auto-recovery
 * - 0..120% clean boost (-0.50 dBTP limiter), output acoustic profiles & ambient bed mixer
 * - Per-preset volume memory with high-volume safe switch & 20s gentle sleep timer fade-out
 * - Recent preset history, Quick Settings Tile sync, and U321 Vorta mutual yielding
 */
public class AegisAudioService extends Service {
    private static final String TAG = "AegisAudioService";
    private static final String CHANNEL_ID = "vorta_aegis_playback_channel";
    private static final int NOTIFICATION_ID = 9001;
    private static final long WAKELOCK_TIMEOUT_MS = 8L * 60L * 60L * 1000L;
    private static final int RENDER_FRAMES = 960; // 20ms @ 48kHz (5x 192 FastMixer)
    private static final int MAX_RECENT_ITEMS = 8;

    public static final String PREFS_NAME = "aegis_prefs";
    public static final String PREF_VOLUME_PCT = "in_app_volume_pct";
    public static final String PREF_MUTED = "in_app_muted";
    public static final String PREF_LAST_TAB = "last_selected_tab";
    public static final String PREF_LAST_PRESET_ID = "last_preset_id";
    public static final String PREF_FAVORITES = "favorite_ids_set";
    public static final String PREF_RECENT_IDS = "recent_preset_ids";
    public static final String PREF_OUTPUT_PROFILE = "output_profile_mode";
    public static final String PREF_AMBIENT_TYPE = "ambient_layer_type";
    public static final String PREF_AMBIENT_MIX = "ambient_layer_mix";
    public static final String PREF_PER_PRESET_VOL = "per_preset_vol_enabled";
    public static final String PREF_GENTLE_SLEEP_FADE = "gentle_sleep_fade";
    public static final String PREF_PRESET_VOL_PREFIX = "preset_vol_";

    public static final String ACTION_START = "com.vorta.aegis.ACTION_START";
    public static final String ACTION_STOP = "com.vorta.aegis.ACTION_STOP";
    public static final String ACTION_TOGGLE_PLAY = "com.vorta.aegis.ACTION_TOGGLE_PLAY";
    public static final String ACTION_START_ROUTINE = "com.vorta.aegis.ACTION_START_ROUTINE";
    public static final String ACTION_NEXT_ROUTINE_STEP = "com.vorta.aegis.ACTION_NEXT_ROUTINE_STEP";
    public static final String ACTION_PREV = "com.vorta.aegis.ACTION_PREV";
    public static final String ACTION_NEXT = "com.vorta.aegis.ACTION_NEXT";
    public static final String ACTION_UPDATE_PRESET = "com.vorta.aegis.ACTION_UPDATE_PRESET";
    public static final String ACTION_SET_VOLUME = "com.vorta.aegis.ACTION_SET_VOLUME";
    public static final String ACTION_STATE_CHANGED = "com.vorta.aegis.ACTION_STATE_CHANGED";

    public static final String EXTRA_PRESET_ID = "extra_preset_id";
    public static final String EXTRA_ROUTINE_ID = "extra_routine_id";
    public static final String EXTRA_VOLUME_PERCENT = "extra_volume_percent";
    public static final String EXTRA_MUTED = "extra_muted";
    public static final String EXTRA_FOCUS_PLAYING_TAB = "extra_focus_playing_tab";
    public static final String EXTRA_AUTO_RESUME_LAST = "extra_auto_resume_last";

    private static volatile boolean sLiveRunning = false;
    private static volatile String sLivePresetTitle = "";

    private final AegisDspCore mDspCore = new AegisDspCore(AegisDspCore.SAMPLE_RATE);
    private AudioTrack mAudioTrack;
    private boolean mUsingFloatTrack = false;
    private Thread mRenderThread;
    private volatile boolean mIsRunning = false;
    private volatile long mSessionGen = 0L;
    private volatile int mRecoveryCount = 0;

    // Sleep timer & guided routine sequencer state
    private volatile int mSleepTimerMinutes = 0;
    private volatile long mSleepRemainingFrames = 0L;
    private volatile boolean mSleepFadeTriggered = false;
    private volatile JourneyOrderPolicy.RoutineCourse mActiveRoutine = null;
    private volatile int mRoutineStepIndex = 0;
    private volatile long mRoutineStepRemainingFrames = 0L;
    private volatile boolean mRoutineEndFadeTriggered = false;
    private volatile boolean mGentleSleepFade = true;
    private volatile boolean mPerPresetVolumeEnabled = true;
    private volatile boolean mLastSwitchSoftened = false;
    private volatile int mLastSoftenedTargetPct = -1;
    private String mLastNotificationSummary = "";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock mWakeLock;
    private AudioManager mAudioManager;
    private NotificationManager mNotificationManager;
    private Object mDeviceCallbackObj;
    private BroadcastReceiver mExclusiveAudioReceiver;
    private BroadcastReceiver mBecomingNoisyReceiver;
    private BroadcastReceiver mHeadsetLegacyReceiver;
    private boolean mNoisyRegistered = false;
    private int mTpdfRng = 0x3C7E91A5;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AegisAudioService getService() {
            return AegisAudioService.this;
        }
    }

    public static boolean isLiveRunning() {
        return sLiveRunning;
    }

    public static String getLivePresetTitle() {
        return sLivePresetTitle;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        loadPersistedSettings();
        refreshPersonalAudioState();
        registerPersonalAudioObserver();
        createNotificationChannel();
        registerExclusiveReceiver();
        if (Build.MODEL != null && Build.MODEL.contains("N986")) {
            Log.i(TAG, "Galaxy Note 20 Ultra (SM-N986N) detected: Qualcomm Kona HAL (960 frames, 192 FastMixer) optimized");
        }
    }

    private void loadPersistedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int pct = VolumeCurve.clampPercent(prefs.getInt(PREF_VOLUME_PCT, VolumeCurve.DEFAULT_PERCENT));
        boolean muted = prefs.getBoolean(PREF_MUTED, false);
        mDspCore.setVolume(pct, muted);

        int profile = prefs.getInt(PREF_OUTPUT_PROFILE, AegisDspCore.PROFILE_AUTO);
        mDspCore.setOutputProfileMode(profile);

        int ambType = prefs.getInt(PREF_AMBIENT_TYPE, AegisDspCore.AMBIENT_OFF);
        int ambMix = prefs.getInt(PREF_AMBIENT_MIX, 25);
        mDspCore.setAmbientLayer(ambType, ambMix);

        mPerPresetVolumeEnabled = prefs.getBoolean(PREF_PER_PRESET_VOL, true);
        mGentleSleepFade = prefs.getBoolean(PREF_GENTLE_SLEEP_FADE, true);
    }

    private void savePersistedVolume() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_VOLUME_PCT, mDspCore.getVolumePercent())
                .putBoolean(PREF_MUTED, mDspCore.isMuted())
                .apply();
    }

    private void registerPersonalAudioObserver() {
        if (mAudioManager == null) return;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                AudioDeviceCallback cb = new AudioDeviceCallback() {
                    @Override
                    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                        refreshPersonalAudioState();
                        broadcastStateChanged();
                    }

                    @Override
                    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                        refreshPersonalAudioState();
                        broadcastStateChanged();
                    }
                };
                mDeviceCallbackObj = cb;
                mAudioManager.registerAudioDeviceCallback(cb, mMainHandler);
            } catch (Throwable t) {
                Log.w(TAG, "registerAudioDeviceCallback error: " + t.getMessage());
            }
        } else {
            try {
                mHeadsetLegacyReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        refreshPersonalAudioState();
                        broadcastStateChanged();
                    }
                };
                IntentFilter f = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
                registerReceiver(mHeadsetLegacyReceiver, f);
            } catch (Throwable t) {
                Log.w(TAG, "registerLegacyHeadset error: " + t.getMessage());
            }
        }
    }

    private void unregisterPersonalAudioObserver() {
        if (Build.VERSION.SDK_INT >= 23 && mAudioManager != null && mDeviceCallbackObj != null) {
            try {
                mAudioManager.unregisterAudioDeviceCallback((AudioDeviceCallback) mDeviceCallbackObj);
            } catch (Throwable ignored) {}
            mDeviceCallbackObj = null;
        }
        if (mHeadsetLegacyReceiver != null) {
            try {
                unregisterReceiver(mHeadsetLegacyReceiver);
            } catch (Throwable ignored) {}
            mHeadsetLegacyReceiver = null;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean checkPersonalAudioConnected() {
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

    @SuppressWarnings("deprecation")
    public String getOutputDeviceLabel() {
        if (mAudioManager == null) return "폰 스피커";
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo[] outputs = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (outputs != null) {
                    boolean hasWired = false;
                    boolean hasUsb = false;
                    for (AudioDeviceInfo dev : outputs) {
                        int type = dev.getType();
                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || (Build.VERSION.SDK_INT >= 31 && type == 26)) {
                            return "블루투스";
                        }
                        if (type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                            hasUsb = true;
                        } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                            hasWired = true;
                        }
                    }
                    if (hasUsb) return "USB 오디오";
                    if (hasWired) return "유선 이어폰";
                }
                return "폰 스피커";
            }
            if (mAudioManager.isBluetoothA2dpOn()) return "블루투스";
            if (mAudioManager.isWiredHeadsetOn()) return "유선 이어폰";
            return "폰 스피커";
        } catch (Throwable t) {
            return "폰 스피커";
        }
    }

    private void refreshPersonalAudioState() {
        mDspCore.setPersonalAudioConnected(checkPersonalAudioConnected());
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
                            refreshPersonalAudioState();
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
        } else if (ACTION_TOGGLE_PLAY.equals(action)) {
            if (mIsRunning && mDspCore.isPlaying()) {
                stopPlayback();
            } else {
                int fallbackId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_LAST_PRESET_ID, 1);
                loadAndPlayPreset(fallbackId);
            }
        } else if (ACTION_START_ROUTINE.equals(action)) {
            startRoutine(intent.getStringExtra(EXTRA_ROUTINE_ID));
        } else if (ACTION_NEXT_ROUTINE_STEP.equals(action)) {
            advanceRoutineStep();
        } else if (ACTION_PREV.equals(action)) {
            playRelativePreset(-1);
        } else if (ACTION_NEXT.equals(action)) {
            playRelativePreset(1);
        } else if (ACTION_SET_VOLUME.equals(action)) {
            if (intent.hasExtra(EXTRA_VOLUME_PERCENT)) {
                setVolumePercent(intent.getIntExtra(EXTRA_VOLUME_PERCENT, mDspCore.getVolumePercent()));
            }
            if (intent.hasExtra(EXTRA_MUTED)) {
                setMuted(intent.getBooleanExtra(EXTRA_MUTED, mDspCore.isMuted()));
            }
        } else if (ACTION_START.equals(action) || ACTION_UPDATE_PRESET.equals(action)) {
            if (intent.hasExtra(EXTRA_VOLUME_PERCENT)) {
                setVolumePercent(intent.getIntExtra(EXTRA_VOLUME_PERCENT, mDspCore.getVolumePercent()));
            }
            if (intent.hasExtra(EXTRA_MUTED)) {
                setMuted(intent.getBooleanExtra(EXTRA_MUTED, mDspCore.isMuted()));
            }
            int fallbackId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_LAST_PRESET_ID, 1);
            int presetId = intent.getIntExtra(EXTRA_PRESET_ID, fallbackId);
            loadAndPlayPreset(presetId);
        }
        return START_NOT_STICKY;
    }

    public synchronized void playRelativePreset(int delta) {
        PresetModel cur = mDspCore.getCurrentPreset();
        int curId = (cur != null)
                ? cur.id
                : getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_LAST_PRESET_ID, 1);
        int nextId = JourneyOrderPolicy.getNextOrderedPresetId(curId, delta);
        loadAndPlayPresetInternal(nextId, false, false);
    }

    public synchronized void loadAndPlayPreset(int presetId) {
        loadAndPlayPresetInternal(presetId, true, false);
    }

    public synchronized void startRoutine(String routineId) {
        JourneyOrderPolicy.RoutineCourse course = JourneyOrderPolicy.getRoutineById(routineId);
        if (course == null || course.steps.length == 0) {
            return;
        }
        mSleepTimerMinutes = 0;
        mSleepRemainingFrames = 0L;
        mSleepFadeTriggered = false;
        mActiveRoutine = course;
        mRoutineStepIndex = 0;
        mRoutineEndFadeTriggered = false;
        mRoutineStepRemainingFrames = (long) course.steps[0].durationMinutes * 60L * AegisDspCore.SAMPLE_RATE;
        loadAndPlayPresetInternal(course.steps[0].presetId, true, true);
    }

    public synchronized void advanceRoutineStep() {
        JourneyOrderPolicy.RoutineCourse course = mActiveRoutine;
        if (course == null) {
            return;
        }
        int nextIdx = mRoutineStepIndex + 1;
        if (nextIdx >= course.steps.length) {
            stopPlayback();
            return;
        }
        mRoutineStepIndex = nextIdx;
        mRoutineEndFadeTriggered = false;
        mRoutineStepRemainingFrames = (long) course.steps[nextIdx].durationMinutes * 60L * AegisDspCore.SAMPLE_RATE;
        loadAndPlayPresetInternal(course.steps[nextIdx].presetId, false, true);
    }

    private synchronized void advanceRoutineStepFromTimer() {
        if (mIsRunning && mActiveRoutine != null) {
            advanceRoutineStep();
        }
    }

    private void recordRecentPresetId(SharedPreferences prefs, int presetId) {
        String existing = prefs.getString(PREF_RECENT_IDS, "");
        List<Integer> ids = parseRecentIds(existing);
        ids.remove(Integer.valueOf(presetId));
        ids.add(0, presetId);
        if (ids.size() > MAX_RECENT_ITEMS) {
            ids = ids.subList(0, MAX_RECENT_ITEMS);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        prefs.edit().putString(PREF_RECENT_IDS, sb.toString()).apply();
    }

    public static List<Integer> parseRecentIds(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) continue;
            try {
                int id = Integer.parseInt(s);
                if (!out.contains(id)) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private synchronized void loadAndPlayPresetInternal(int presetId, boolean emitYieldBroadcast, boolean keepRoutine) {
        if (!keepRoutine) {
            mActiveRoutine = null;
            mRoutineStepIndex = 0;
            mRoutineStepRemainingFrames = 0L;
            mRoutineEndFadeTriggered = false;
        }
        PresetModel prevPreset = mDspCore.getCurrentPreset();
        PresetModel target = JourneyPackCatalog.findAnyById(presetId);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        mLastSwitchSoftened = false;
        mLastSoftenedTargetPct = -1;
        if (mPerPresetVolumeEnabled && !mDspCore.isMuted()) {
            int savedPct = prefs.getInt(PREF_PRESET_VOL_PREFIX + target.id, -1);
            if (savedPct >= 0) {
                int clamped = VolumeCurve.clampPercent(savedPct);
                mDspCore.setVolume(clamped, false);
                prefs.edit().putInt(PREF_VOLUME_PCT, clamped).apply();
            } else if (PresetAdvisor.shouldWarnHighVolumeSwitch(prevPreset, target, mDspCore.getVolumePercent())) {
                int safePct = PresetAdvisor.getRecommendedVolumePercent(target);
                mDspCore.setVolume(safePct, false);
                prefs.edit().putInt(PREF_VOLUME_PCT, safePct).apply();
                mLastSwitchSoftened = true;
                mLastSoftenedTargetPct = safePct;
            }
        }

        prefs.edit()
                .putInt(PREF_LAST_TAB, target.tabIndex)
                .putInt(PREF_LAST_PRESET_ID, target.id)
                .apply();
        recordRecentPresetId(prefs, target.id);

        if (mSleepTimerMinutes > 0 && mSleepRemainingFrames <= 0L) {
            mSleepRemainingFrames = (long) mSleepTimerMinutes * 60L * AegisDspCore.SAMPLE_RATE;
            mSleepFadeTriggered = false;
        }

        refreshPersonalAudioState();
        mDspCore.playPreset(target);
        acquireWakeLock();

        sLiveRunning = true;
        sLivePresetTitle = target.title;

        if (emitYieldBroadcast && !mIsRunning) {
            try {
                Intent excl = new Intent("com.vorta.ACTION_EXCLUSIVE_AUDIO_START");
                excl.putExtra("sender_pkg", getPackageName());
                sendBroadcast(excl);
            } catch (Throwable ignored) {}
        }

        mLastNotificationSummary = buildNotificationSummary(target);
        startForegroundCompat(buildNotification(target, mLastNotificationSummary));

        if (!mIsRunning) {
            startPlayback();
        } else {
            syncQuickTile();
            broadcastStateChanged();
        }
    }

    private void startForegroundCompat(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Log.w(TAG, "startForegroundCompat fallback: " + t.getMessage());
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Throwable ignored) {}
        }
    }

    private AudioTrack createAndStartAudioTrack() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        // 1. Prefer API 21 native 32-bit IEEE Float PCM
        try {
            int minFloatBytes = AudioTrack.getMinBufferSize(
                    AegisDspCore.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT
            );
            if (minFloatBytes > 0) {
                int minAligned = Math.max(minFloatBytes * 2, RENDER_FRAMES * 2 * 4);
                int frameStride = RENDER_FRAMES * 2 * 4;
                int bufBytes = ((minAligned + frameStride - 1) / frameStride) * frameStride;
                AudioFormat floatFmt = new AudioFormat.Builder()
                        .setSampleRate(AegisDspCore.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build();
                AudioTrack track = new AudioTrack(
                        attributes,
                        floatFmt,
                        bufBytes,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );
                if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                    track.play();
                    mUsingFloatTrack = true;
                    return track;
                }
                track.release();
            }
        } catch (Throwable t) {
            Log.w(TAG, "ENCODING_PCM_FLOAT unavailable, falling back to PCM_16BIT: " + t.getMessage());
        }

        // 2. Fallback: API 21 16-bit PCM with TPDF dither
        try {
            int min16Bytes = AudioTrack.getMinBufferSize(
                    AegisDspCore.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int base16 = (min16Bytes > 0) ? min16Bytes * 2 : 8192;
            int minAligned = Math.max(base16, RENDER_FRAMES * 2 * 2);
            int frameStride = RENDER_FRAMES * 2 * 2;
            int bufBytes = ((minAligned + frameStride - 1) / frameStride) * frameStride;
            AudioFormat pcm16Fmt = new AudioFormat.Builder()
                    .setSampleRate(AegisDspCore.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            AudioTrack track16 = new AudioTrack(
                    attributes,
                    pcm16Fmt,
                    bufBytes,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            if (track16.getState() == AudioTrack.STATE_INITIALIZED) {
                track16.play();
                mUsingFloatTrack = false;
                return track16;
            }
            track16.release();
        } catch (Throwable t) {
            Log.e(TAG, "AudioTrack fallback init failed: " + t.getMessage());
        }
        return null;
    }

    public synchronized void startPlayback() {
        if (mIsRunning) {
            if (mDspCore.getCurrentPreset() != null) {
                mDspCore.playPreset(mDspCore.getCurrentPreset());
            }
            return;
        }

        if (mRenderThread != null && mRenderThread.isAlive()) {
            try {
                mRenderThread.join(600);
            } catch (InterruptedException ignored) {}
        }

        mSessionGen++;
        final long sessionGen = mSessionGen;
        acquireWakeLock();
        registerBecomingNoisyReceiver();

        mAudioTrack = createAndStartAudioTrack();
        if (mAudioTrack == null) {
            mIsRunning = false;
            sLiveRunning = false;
            mDspCore.requestStop();
            postStopCleanup(sessionGen);
            return;
        }

        mIsRunning = true;
        sLiveRunning = true;
        mRenderThread = new Thread(new AudioRenderRunnable(sessionGen), "AegisAudioRenderThread");
        mRenderThread.setPriority(Thread.MAX_PRIORITY);
        mRenderThread.start();
        syncQuickTile();
        broadcastStateChanged();
        Log.i(TAG, "Aegis Audio Engine Started (gen=" + sessionGen + ", float=" + mUsingFloatTrack
                + ", Vol=" + mDspCore.getVolumePercent() + "%)");
    }

    public synchronized void stopPlayback() {
        mSleepTimerMinutes = 0;
        mSleepRemainingFrames = 0L;
        mSleepFadeTriggered = false;
        mActiveRoutine = null;
        mRoutineStepIndex = 0;
        mRoutineStepRemainingFrames = 0L;
        mRoutineEndFadeTriggered = false;
        mActiveRoutine = null;
        mRoutineStepIndex = 0;
        mRoutineStepRemainingFrames = 0L;
        mRoutineEndFadeTriggered = false;
        sLiveRunning = false;
        if (!mIsRunning) {
            mDspCore.requestStop();
            postStopCleanup(mSessionGen);
            return;
        }
        mDspCore.requestStop();
        syncQuickTile();
        broadcastStateChanged();
    }

    public synchronized void setSleepTimerMinutes(int minutes) {
        if (minutes <= 0) {
            mSleepTimerMinutes = 0;
            mSleepRemainingFrames = 0L;
            mSleepFadeTriggered = false;
        } else {
            int clamped = Math.min(480, minutes);
            mSleepTimerMinutes = clamped;
            mSleepRemainingFrames = (long) clamped * 60L * AegisDspCore.SAMPLE_RATE;
            mSleepFadeTriggered = false;
        }
        refreshNotificationIfNeeded();
        broadcastStateChanged();
    }

    public int getSleepTimerMinutes() {
        return mSleepTimerMinutes;
    }

    public int getSleepRemainingSec() {
        long rem = mSleepRemainingFrames;
        if (rem <= 0L || mSleepTimerMinutes <= 0) {
            return 0;
        }
        return (int) Math.ceil(rem / (double) AegisDspCore.SAMPLE_RATE);
    }

    private void postStopCleanup(final long expectedGen) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (AegisAudioService.this) {
                    if (expectedGen != mSessionGen || mIsRunning) {
                        return;
                    }
                    try {
                        if (mAudioTrack != null) {
                            mAudioTrack.stop();
                            mAudioTrack.release();
                            mAudioTrack = null;
                        }
                    } catch (Throwable ignored) {}
                    unregisterBecomingNoisyReceiver();
                    releaseWakeLock();
                    sLiveRunning = false;
                    sLivePresetTitle = "";
                    stopForeground(true);
                    syncQuickTile();
                    broadcastStateChanged();
                    stopSelf();
                }
            }
        });
    }

    private void syncQuickTile() {
        if (Build.VERSION.SDK_INT >= 24) {
            AegisQuickTileService.requestTileSync(this);
        }
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

    public AegisDspCore getDspCore() { return mDspCore; }
    public boolean isUsingFloatOutput() { return mUsingFloatTrack; }
    public boolean isPersonalAudioConnected() { return checkPersonalAudioConnected(); }
    public boolean isUltrasonicGuarded() { return mDspCore.isUltrasonicGuarded(); }
    public int getRecoveryCount() { return mRecoveryCount; }

    public int getUnderrunCount() {
        AudioTrack t = mAudioTrack;
        if (t != null && Build.VERSION.SDK_INT >= 24) {
            try {
                return t.getUnderrunCount();
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return true;
        }
    }

    private void broadcastStateChanged() {
        try {
            Intent intent = new Intent(ACTION_STATE_CHANGED);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    public boolean isPlaying() { return mIsRunning && mDspCore.isPlaying(); }
    public PresetModel getCurrentPreset() { return mDspCore.getCurrentPreset(); }
    public float getCurrentLiveFreqHz() { return mDspCore.getLiveFreqHz(); }
    public float getCurrentLiveBeatHz() { return mDspCore.getLiveBeatHz(); }
    public JourneyOrderPolicy.RoutineCourse getActiveRoutine() { return mActiveRoutine; }
    public int getRoutineStepIndex() { return mRoutineStepIndex; }
    public int getRoutineStepRemainingSec() {
        long rem = mRoutineStepRemainingFrames;
        if (rem <= 0L || mActiveRoutine == null) return 0;
        return (int) Math.ceil(rem / (double) AegisDspCore.SAMPLE_RATE);
    }
    public float getLiveRmsDbfs() { return mDspCore.getLiveRmsDbfs(); }
    public long getOvershootCount() { return mDspCore.getOvershootCount(); }
    public int getVolumePercent() { return mDspCore.getVolumePercent(); }
    public boolean isMuted() { return mDspCore.isMuted(); }
    public int getOutputProfileMode() { return mDspCore.getOutputProfileMode(); }
    public int getAmbientType() { return mDspCore.getAmbientType(); }
    public int getAmbientMixPercent() { return mDspCore.getAmbientMixPercent(); }
    public boolean isPerPresetVolumeEnabled() { return mPerPresetVolumeEnabled; }
    public boolean isGentleSleepFadeEnabled() { return mGentleSleepFade; }

    public boolean consumeLastSwitchSoftened() {
        boolean flag = mLastSwitchSoftened;
        mLastSwitchSoftened = false;
        return flag;
    }

    public int getLastSoftenedTargetPct() {
        return mLastSoftenedTargetPct;
    }

    public void setPerPresetVolumeEnabled(boolean enabled) {
        mPerPresetVolumeEnabled = enabled;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(PREF_PER_PRESET_VOL, enabled).apply();
    }

    public void setGentleSleepFadeEnabled(boolean enabled) {
        mGentleSleepFade = enabled;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(PREF_GENTLE_SLEEP_FADE, enabled).apply();
    }

    public void setOutputProfileMode(int mode) {
        mDspCore.setOutputProfileMode(mode);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(PREF_OUTPUT_PROFILE, mDspCore.getOutputProfileMode()).apply();
        refreshNotificationIfNeeded();
        broadcastStateChanged();
    }

    public void setAmbientLayer(int type, int mixPercent) {
        mDspCore.setAmbientLayer(type, mixPercent);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_AMBIENT_TYPE, mDspCore.getAmbientType())
                .putInt(PREF_AMBIENT_MIX, mDspCore.getAmbientMixPercent())
                .apply();
        refreshNotificationIfNeeded();
        broadcastStateChanged();
    }

    public void setVolumePercent(int percent) {
        mDspCore.setVolume(percent, mDspCore.isMuted());
        savePersistedVolume();
        if (mPerPresetVolumeEnabled) {
            PresetModel cur = mDspCore.getCurrentPreset();
            if (cur != null && isPlaying()) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt(PREF_PRESET_VOL_PREFIX + cur.id, mDspCore.getVolumePercent())
                        .apply();
            }
        }
        refreshNotificationIfNeeded();
    }

    public void setMuted(boolean muted) {
        mDspCore.setVolume(mDspCore.getVolumePercent(), muted);
        savePersistedVolume();
        refreshNotificationIfNeeded();
    }

    public void setMasterGain(double gain) {
        double clamped = Math.max(0.0, Math.min(VolumeCurve.MAX_BOOST_LINEAR, gain));
        int pct = (int) Math.round(clamped * 100.0);
        setVolumePercent(pct);
    }

    private int nextTpdfNoise2() {
        int r1 = nextXorShift();
        int r2 = nextXorShift();
        return (r1 & 1) - (r2 & 1);
    }

    private int nextXorShift() {
        int x = mTpdfRng;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        mTpdfRng = x;
        return x;
    }

    private class AudioRenderRunnable implements Runnable {
        private final long mSessionId;

        AudioRenderRunnable(long sessionId) {
            mSessionId = sessionId;
        }

        @Override
        public void run() {
            final float[] floatBuf = new float[RENDER_FRAMES * 2];
            short[] shortBuf = mUsingFloatTrack ? null : new short[RENDER_FRAMES * 2];
            int deadRecoveries = 0;

            try {
                while (mIsRunning && mSessionId == mSessionGen) {
                    if (mDspCore.getCurrentPreset() == null && mDspCore.isActive()) {
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        continue;
                    }

                    boolean stillActive = mDspCore.renderStereoFloat(floatBuf, RENDER_FRAMES);

                    // Doze-safe frame-counted Guided Auto-Routine Sequencer
                    JourneyOrderPolicy.RoutineCourse rc = mActiveRoutine;
                    if (rc != null && mRoutineStepRemainingFrames > 0L) {
                        mRoutineStepRemainingFrames -= RENDER_FRAMES;
                        boolean isLastStep = (mRoutineStepIndex >= rc.steps.length - 1);
                        if (isLastStep) {
                            long fadeWindow = 20L * AegisDspCore.SAMPLE_RATE;
                            if (mGentleSleepFade && !mRoutineEndFadeTriggered
                                    && mRoutineStepRemainingFrames > 0L && mRoutineStepRemainingFrames <= fadeWindow) {
                                mRoutineEndFadeTriggered = true;
                                double fadeSec = Math.max(1.0, mRoutineStepRemainingFrames / (double) AegisDspCore.SAMPLE_RATE);
                                mDspCore.requestGentleStop(fadeSec);
                            }
                            if (mRoutineStepRemainingFrames <= 0L) {
                                mRoutineStepRemainingFrames = 0L;
                                mActiveRoutine = null;
                                mRoutineStepIndex = 0;
                                mRoutineEndFadeTriggered = false;
                                if (mDspCore.isPlaying()) {
                                    mDspCore.requestStop();
                                }
                                mMainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        broadcastStateChanged();
                                    }
                                });
                            }
                        } else if (mRoutineStepRemainingFrames <= 0L) {
                            mRoutineStepRemainingFrames = 0L;
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    advanceRoutineStepFromTimer();
                                }
                            });
                        }
                    } else if (mSleepTimerMinutes > 0 && mSleepRemainingFrames > 0L) {
                        mSleepRemainingFrames -= RENDER_FRAMES;
                        long fadeWindow = 20L * AegisDspCore.SAMPLE_RATE;
                        if (mGentleSleepFade && !mSleepFadeTriggered
                                && mSleepRemainingFrames > 0L && mSleepRemainingFrames <= fadeWindow) {
                            mSleepFadeTriggered = true;
                            double fadeSec = Math.max(1.0, mSleepRemainingFrames / (double) AegisDspCore.SAMPLE_RATE);
                            mDspCore.requestGentleStop(fadeSec);
                        }
                        if (mSleepRemainingFrames <= 0L) {
                            mSleepRemainingFrames = 0L;
                            mSleepTimerMinutes = 0;
                            mSleepFadeTriggered = false;
                            if (mDspCore.isPlaying()) {
                                mDspCore.requestStop();
                            }
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    broadcastStateChanged();
                                }
                            });
                        }
                    }

                    AudioTrack track = mAudioTrack;
                    if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        int totalSamples = RENDER_FRAMES * 2;
                        boolean needRecover = false;
                        if (mUsingFloatTrack) {
                            int off = 0;
                            while (off < totalSamples && mIsRunning && mSessionId == mSessionGen) {
                                int w = track.write(floatBuf, off, totalSamples - off, AudioTrack.WRITE_BLOCKING);
                                if (w == AudioTrack.ERROR_DEAD_OBJECT) {
                                    needRecover = true;
                                    break;
                                }
                                if (w < 0) {
                                    Log.w(TAG, "Float AudioTrack write error: " + w);
                                    mIsRunning = false;
                                    break;
                                }
                                if (w == 0) break;
                                off += w;
                            }
                        } else {
                            if (shortBuf == null) {
                                shortBuf = new short[RENDER_FRAMES * 2];
                            }
                            boolean allowDither = !mDspCore.isMuted() && mDspCore.getVolumePercent() > 0;
                            for (int i = 0; i < totalSamples; i++) {
                                float f = floatBuf[i];
                                if (Math.abs(f) > 1e-7f) {
                                    double scaled = (f * 32767.0) + (allowDither ? nextTpdfNoise2() : 0);
                                    int iv = (int) Math.round(scaled);
                                    if (iv > 32767) iv = 32767;
                                    if (iv < -32767) iv = -32767;
                                    shortBuf[i] = (short) iv;
                                } else {
                                    shortBuf[i] = 0;
                                }
                            }
                            int off = 0;
                            while (off < totalSamples && mIsRunning && mSessionId == mSessionGen) {
                                int w = track.write(shortBuf, off, totalSamples - off);
                                if (w == AudioTrack.ERROR_DEAD_OBJECT) {
                                    needRecover = true;
                                    break;
                                }
                                if (w < 0) {
                                    Log.w(TAG, "16-bit AudioTrack write error: " + w);
                                    mIsRunning = false;
                                    break;
                                }
                                if (w == 0) break;
                                off += w;
                            }
                        }

                        if (needRecover && mIsRunning && mSessionId == mSessionGen) {
                            if (deadRecoveries < 3) {
                                deadRecoveries++;
                                Log.w(TAG, "AudioTrack ERROR_DEAD_OBJECT: auto-recovering (" + deadRecoveries + "/3)");
                                try { track.stop(); } catch (Throwable ignored) {}
                                try { track.release(); } catch (Throwable ignored) {}
                                mAudioTrack = null;
                                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                                refreshPersonalAudioState();
                                AudioTrack rebuilt = createAndStartAudioTrack();
                                if (rebuilt != null && mIsRunning && mSessionId == mSessionGen) {
                                    mAudioTrack = rebuilt;
                                    if (!mUsingFloatTrack && shortBuf == null) {
                                        shortBuf = new short[RENDER_FRAMES * 2];
                                    }
                                    mRecoveryCount++;
                                    mMainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            broadcastStateChanged();
                                        }
                                    });
                                    continue;
                                }
                            }
                            mIsRunning = false;
                            break;
                        }
                    }

                    if (!stillActive) {
                        if (mSessionId == mSessionGen) {
                            mIsRunning = false;
                        }
                        break;
                    }
                }
            } finally {
                if (mSessionId == mSessionGen) {
                    mIsRunning = false;
                }
                postStopCleanup(mSessionId);
                Log.i(TAG, "Aegis Audio Engine Safely Stopped (gen=" + mSessionId + ")");
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

            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent buildServiceActionPendingIntent(int reqCode, String action) {
        Intent intent = new Intent(this, AegisAudioService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        if (Build.VERSION.SDK_INT >= 26 && !ACTION_STOP.equals(action)) {
            return PendingIntent.getForegroundService(this, reqCode, intent, flags);
        }
        return PendingIntent.getService(this, reqCode, intent, flags);
    }

    private String buildNotificationSummary(PresetModel preset) {
        String code = JourneyPackCatalog.getShortCode(preset);
        String name = (code.isEmpty() ? "" : code + " · ") + (preset != null ? preset.title : "대기 중");
        JourneyOrderPolicy.RoutineCourse rc = mActiveRoutine;
        String routinePrefix = (rc != null)
                ? ("[코스 " + (mRoutineStepIndex + 1) + "/" + rc.steps.length + "] ") : "";
        String volPart = mDspCore.isMuted() ? "음소거" : String.format(Locale.US, "%d%%", mDspCore.getVolumePercent());
        String timerPart = (rc == null && mSleepTimerMinutes > 0) ? (" · 타이머 " + mSleepTimerMinutes + "분") : "";
        return routinePrefix + name + " (" + volPart + timerPart + ")";
    }

    private void refreshNotificationIfNeeded() {
        if (!mIsRunning || mNotificationManager == null) return;
        PresetModel preset = mDspCore.getCurrentPreset();
        if (preset == null) return;
        String summary = buildNotificationSummary(preset);
        if (!summary.equals(mLastNotificationSummary)) {
            mLastNotificationSummary = summary;
            try {
                mNotificationManager.notify(NOTIFICATION_ID, buildNotification(preset, summary));
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(PresetModel preset, String summaryText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(EXTRA_FOCUS_PLAYING_TAB, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        PendingIntent prevPi = buildServiceActionPendingIntent(2, ACTION_PREV);
        PendingIntent stopPi = buildServiceActionPendingIntent(1, ACTION_STOP);
        PendingIntent nextPi = buildServiceActionPendingIntent(3, ACTION_NEXT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("보르타 이지스 · 재생 중")
                .setContentText(summaryText)
                .setSmallIcon(R.drawable.ic_aegis_shield)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(R.drawable.ic_prev, "이전", prevPi)
                .addAction(R.drawable.ic_stop_square, "중지", stopPi)
                .addAction(R.drawable.ic_next, "다음", nextPi);

        return builder.build();
    }

    @Override
    public void onDestroy() {
        mSessionGen++;
        sLiveRunning = false;
        sLivePresetTitle = "";
        unregisterPersonalAudioObserver();
        if (mExclusiveAudioReceiver != null) {
            try {
                unregisterReceiver(mExclusiveAudioReceiver);
            } catch (Throwable ignored) {}
            mExclusiveAudioReceiver = null;
        }
        unregisterBecomingNoisyReceiver();
        releaseWakeLock();
        mSleepTimerMinutes = 0;
        mSleepRemainingFrames = 0L;
        mSleepFadeTriggered = false;
        mActiveRoutine = null;
        mRoutineStepIndex = 0;
        mRoutineStepRemainingFrames = 0L;
        mRoutineEndFadeTriggered = false;
        mDspCore.requestStop();
        mIsRunning = false;
        syncQuickTile();
        super.onDestroy();
    }
}
