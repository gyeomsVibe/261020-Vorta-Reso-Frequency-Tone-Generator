package com.vorta.aegis;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commercial Precision Acoustic Instrument UI for Vorta Aegis Pro v2.3.0.
 * Mixes cleanly with Samsung Music / Spotify / YouTube Music via focus-free AudioTrack playback,
 * featuring playback-synced category tabs, horizontal tab centering, playing-tab LED badge,
 * one-tap console jump cue, unified playback terminology, and a -48 dB independent fader.
 */
public class MainActivity extends Activity {
    private static final String PREFS_NAME = "aegis_prefs";
    private static final String PREF_VOLUME_PCT = "in_app_volume_pct";
    private static final String PREF_MUTED = "in_app_muted";

    // Design System Tokens (Obsidian + Amber Acoustic Instrument)
    private static final int COL_BG = Color.parseColor("#0E0F11");
    private static final int COL_SURFACE = Color.parseColor("#17181C");
    private static final int COL_SURFACE_HIGH = Color.parseColor("#202228");
    private static final int COL_OUTLINE = Color.parseColor("#2A2D35");
    private static final int COL_AMBER = Color.parseColor("#E5A93C");
    private static final int COL_AMBER_BG = Color.parseColor("#262015");
    private static final int COL_ACTIVE = Color.parseColor("#00E676");
    private static final int COL_ACTIVE_ON_LIGHT = Color.parseColor("#00A854");
    private static final int COL_STOPPED = Color.parseColor("#FF3B5C");
    private static final int COL_STOP_BTN = Color.parseColor("#C82838");
    private static final int COL_TEXT_PRIMARY = Color.parseColor("#F2F3F5");
    private static final int COL_TEXT_SECONDARY = Color.parseColor("#9A9FA8");
    private static final int COL_TEXT_TERTIARY = Color.parseColor("#656B76");

    private static final int[] DETENT_VALUES = new int[]{20, 45, 75, 100};
    private static final String[] DETENT_LABELS = new String[]{"20% 배경", "45% 병행", "75% 단독", "100% 최대"};
    private static final String[] TAB_NAMES = new String[]{
            "안티방어 12",
            "수면 6",
            "집중·공부 6",
            "명상·안정 5",
            "두통·통증 5",
            "운동·피로 5",
            "기분·힐링 5",
            "모기·벌레 4"
    };

    private AegisAudioService mService;
    private boolean mIsBound = false;
    private AudioManager mAudioManager;

    // Console Telemetry Widgets
    private ScrollView mMainScrollView;
    private ImageView mLedIndicator;
    private TextView mTvStatusText;
    private TextView mTvHeroFreq;
    private TextView mTvHeroSubBadge;
    private TextView mTvScenarioTitle;
    private TextView mTvJumpCue;
    private TextView mTvMechanismInfo;
    private Button mBtnStop;

    // Independent Volume Controller Widgets
    private TextView mTvVolumeReadout;
    private TextView mTvSystemVolume;
    private ImageButton mBtnMute;
    private SeekBar mSeekVolume;
    private final List<TextView> mDetentChips = new ArrayList<>();

    // Category & Preset Lists
    private HorizontalScrollView mTabScrollView;
    private LinearLayout mTabLayout;
    private LinearLayout mPresetContainer;
    private View mActivePresetCardView;
    private final List<Button> mTabButtons = new ArrayList<>();
    private int mCurrentSelectedTab = 0;

    // Local mirrored UI state
    private int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
    private boolean mIsMuted = false;
    private boolean mSuppressSeekCallback = false;
    private int mLastHapticDetent = -1;
    private PresetModel mActivePreset = null;
    private boolean mLastDisplayedRunning = false;
    private int mLastHighlightedPresetId = -1;
    private boolean mLastUltrasonicGuarded = false;
    private boolean mPendingEntryTabSync = true;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryTick = new Runnable() {
        @Override
        public void run() {
            refreshLiveTelemetry();
            mUiHandler.postDelayed(this, 250);
        }
    };

    private BroadcastReceiver mStateReceiver;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AegisAudioService.LocalBinder binder = (AegisAudioService.LocalBinder) service;
            mService = binder.getService();
            mIsBound = true;
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            syncVolumeUi();
            syncFromService(mPendingEntryTabSync);
            mPendingEntryTabSync = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mIsBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mVolumePercent = VolumeCurve.clampPercent(prefs.getInt(PREF_VOLUME_PCT, VolumeCurve.DEFAULT_PERCENT));
        mIsMuted = prefs.getBoolean(PREF_MUTED, false);

        setContentView(buildUnifiedLayout());
        syncVolumeUi();
        registerStateReceiver();

        Intent intent = new Intent(this, AegisAudioService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        int savedTab = TabSyncPolicy.clampTabIndex(prefs.getInt(AegisAudioService.PREF_LAST_TAB, 0));
        mPendingEntryTabSync = true;
        selectTab(savedTab, false, false);
    }

    private void registerStateReceiver() {
        mStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                syncFromService(false);
            }
        };
        IntentFilter filter = new IntentFilter(AegisAudioService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mStateReceiver, filter);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mPendingEntryTabSync = true;
        if (mIsBound && mService != null) {
            syncFromService(true);
            mPendingEntryTabSync = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsBound && mService != null) {
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            syncVolumeUi();
            syncFromService(mPendingEntryTabSync);
            mPendingEntryTabSync = false;
        } else {
            updateSystemVolumeReadout();
        }
        mUiHandler.removeCallbacks(mTelemetryTick);
        mUiHandler.post(mTelemetryTick);
    }

    @Override
    protected void onStop() {
        // Re-align the category tab with live playback when returning from background or another app.
        mPendingEntryTabSync = true;
        super.onStop();
    }

    @Override
    protected void onPause() {
        mUiHandler.removeCallbacks(mTelemetryTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mUiHandler.removeCallbacks(mTelemetryTick);
        if (mStateReceiver != null) {
            try { unregisterReceiver(mStateReceiver); } catch (Throwable ignored) {}
            mStateReceiver = null;
        }
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
        super.onDestroy();
    }

    private View buildUnifiedLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setBackgroundColor(COL_BG);
        scrollView.setFillViewport(true);
        mMainScrollView = scrollView;

        LinearLayout root = new LinearLayout(this);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(32));

        root.addView(buildEditorialHeader());
        root.addView(buildConsoleCard());
        root.addView(buildCategoryTabs());

        mPresetContainer = new LinearLayout(this);
        mPresetContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mPresetContainer);

        TextView tvNotice = new TextView(this);
        tvNotice.setText("삼성뮤직·유튜브 등과 동시 재생 지원 · 독립 볼륨으로 음악에 안 묻히게 조절 · -1.0 dBTP 무왜곡 헤드룸");
        tvNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvNotice.setTextColor(COL_TEXT_TERTIARY);
        tvNotice.setLineSpacing(dp(2), 1.15f);
        tvNotice.setPadding(0, dp(14), 0, dp(8));
        root.addView(tvNotice);

        scrollView.addView(root);
        return scrollView;
    }

    private View buildEditorialHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, dp(14));

        TextView tvEyebrow = new TextView(this);
        tvEyebrow.setText("VORTA AEGIS · v2.3");
        tvEyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvEyebrow.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(tvEyebrow, true);
        if (Build.VERSION.SDK_INT >= 21) {
            tvEyebrow.setLetterSpacing(0.14f);
        }
        header.addView(tvEyebrow);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("보르타 이지스");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tvTitle.setTextColor(COL_TEXT_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(2), 0, dp(2));
        header.addView(tvTitle);

        TextView tvSub = new TextView(this);
        String model = Build.MODEL != null && !Build.MODEL.trim().isEmpty() ? Build.MODEL.trim() : "Android Audio";
        tvSub.setText(model + " · 48kHz Stereo · -1.0 dBTP");
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSub.setTextColor(COL_TEXT_SECONDARY);
        applyTabularMono(tvSub, false);
        header.addView(tvSub);

        return header;
    }

    private View buildConsoleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(16)));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams dashLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dashLp.bottomMargin = dp(14);
        card.setLayoutParams(dashLp);

        LinearLayout consoleJumpArea = new LinearLayout(this);
        consoleJumpArea.setOrientation(LinearLayout.VERTICAL);
        consoleJumpArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        consoleJumpArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastDisplayedRunning && mActivePreset != null) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    jumpToPlayingPreset();
                }
            }
        });

        // Row A: Status LED + label + Concurrent Music Mix tag
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        mLedIndicator = new ImageView(this);
        mLedIndicator.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
        mLedIndicator.setImageResource(R.drawable.led_stopped);
        statusRow.addView(mLedIndicator);

        mTvStatusText = new TextView(this);
        mTvStatusText.setText("대기 중");
        mTvStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvStatusText.setTextColor(COL_TEXT_SECONDARY);
        mTvStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        mTvStatusText.setPadding(dp(8), 0, 0, 0);
        mTvStatusText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        statusRow.addView(mTvStatusText);

        TextView tvEngineTag = new TextView(this);
        tvEngineTag.setText("음악 동시재생");
        tvEngineTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvEngineTag.setTextColor(COL_TEXT_SECONDARY);
        tvEngineTag.setPadding(dp(8), dp(3), dp(8), dp(3));
        tvEngineTag.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(6)));
        applyTabularMono(tvEngineTag, true);
        statusRow.addView(tvEngineTag);
        consoleJumpArea.addView(statusRow);

        // Row B: Single-line Hero Frequency Readout + inline Delta/Sweep/Mode badge
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(0, dp(8), 0, dp(3));

        mTvHeroFreq = new TextView(this);
        mTvHeroFreq.setText("0.00 Hz");
        mTvHeroFreq.setSingleLine(true);
        mTvHeroFreq.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        mTvHeroFreq.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvHeroFreq, true);
        heroRow.addView(mTvHeroFreq);

        mTvHeroSubBadge = new TextView(this);
        mTvHeroSubBadge.setText("");
        mTvHeroSubBadge.setSingleLine(true);
        mTvHeroSubBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvHeroSubBadge.setTextColor(COL_AMBER);
        mTvHeroSubBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        mTvHeroSubBadge.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(6)));
        applyTabularMono(mTvHeroSubBadge, true);
        LinearLayout.LayoutParams subBadgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subBadgeLp.leftMargin = dp(10);
        mTvHeroSubBadge.setLayoutParams(subBadgeLp);
        mTvHeroSubBadge.setVisibility(View.GONE);
        heroRow.addView(mTvHeroSubBadge);

        consoleJumpArea.addView(heroRow);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        mTvScenarioTitle = new TextView(this);
        mTvScenarioTitle.setText("프리셋을 선택하면 즉시 재생됩니다");
        mTvScenarioTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mTvScenarioTitle.setTextColor(COL_TEXT_PRIMARY);
        mTvScenarioTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTvScenarioTitle.setPadding(0, 0, dp(8), 0);
        mTvScenarioTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(mTvScenarioTitle);

        mTvJumpCue = new TextView(this);
        mTvJumpCue.setText("재생 탭 ›");
        mTvJumpCue.setSingleLine(true);
        mTvJumpCue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvJumpCue.setTextColor(COL_ACTIVE);
        mTvJumpCue.setPadding(dp(8), dp(3), dp(8), dp(3));
        applyTabularMono(mTvJumpCue, true);
        mTvJumpCue.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_ACTIVE, dp(1), dp(6)));
        mTvJumpCue.setVisibility(View.GONE);
        mTvJumpCue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                jumpToPlayingPreset();
            }
        });
        titleRow.addView(mTvJumpCue);

        consoleJumpArea.addView(titleRow);
        card.addView(consoleJumpArea);

        mTvMechanismInfo = new TextView(this);
        mTvMechanismInfo.setText("• 위상 연속 정현파 무왜곡 출력 대기\n• 삼성 뮤직·유튜브 등 음악 앱과 끊김 없이 동시 재생됩니다");
        mTvMechanismInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvMechanismInfo.setTextColor(COL_TEXT_SECONDARY);
        mTvMechanismInfo.setLineSpacing(dp(2), 1.2f);
        mTvMechanismInfo.setPadding(0, dp(4), 0, dp(12));
        card.addView(mTvMechanismInfo);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(COL_OUTLINE);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divLp.bottomMargin = dp(12);
        divider.setLayoutParams(divLp);
        card.addView(divider);

        // Row C: Independent Volume Control Section
        card.addView(buildVolumeControlBlock());

        // Row D: Stop Button
        mBtnStop = new Button(this);
        mBtnStop.setText("대기 중 (프리셋 선택 시 재생)");
        mBtnStop.setAllCaps(false);
        mBtnStop.setTextColor(COL_TEXT_TERTIARY);
        mBtnStop.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mBtnStop.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        stopLp.topMargin = dp(12);
        mBtnStop.setLayoutParams(stopLp);
        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                stopAudio();
            }
        });
        card.addView(mBtnStop);

        return card;
    }

    private View buildVolumeControlBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        // Header row: Label + dB readout + Read-only system volume badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvVolLabel = new TextView(this);
        tvVolLabel.setText("독립 출력 볼륨");
        tvVolLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVolLabel.setTextColor(COL_TEXT_SECONDARY);
        tvVolLabel.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(tvVolLabel);

        mTvVolumeReadout = new TextView(this);
        mTvVolumeReadout.setText("  45% · -26.4 dB");
        mTvVolumeReadout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvVolumeReadout.setTextColor(COL_AMBER);
        applyTabularMono(mTvVolumeReadout, true);
        mTvVolumeReadout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        headerRow.addView(mTvVolumeReadout);

        mTvSystemVolume = new TextView(this);
        mTvSystemVolume.setText("시스템 -/-");
        mTvSystemVolume.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvSystemVolume.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvSystemVolume, false);
        headerRow.addView(mTvSystemVolume);
        block.addView(headerRow);

        // Fader row: Mute toggle + [-] + SeekBar + [+]
        LinearLayout faderRow = new LinearLayout(this);
        faderRow.setOrientation(LinearLayout.HORIZONTAL);
        faderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams faderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        faderLp.topMargin = dp(6);
        faderRow.setLayoutParams(faderLp);

        mBtnMute = new ImageButton(this);
        mBtnMute.setContentDescription("음소거 전환");
        mBtnMute.setImageResource(R.drawable.ic_volume_on);
        mBtnMute.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mBtnMute.setPadding(dp(8), dp(8), dp(8), dp(8));
        mBtnMute.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(8)));
        LinearLayout.LayoutParams muteLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        muteLp.rightMargin = dp(6);
        mBtnMute.setLayoutParams(muteLp);
        mBtnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (mIsMuted && mVolumePercent == 0) {
                    applyVolumeFromUi(VolumeCurve.DEFAULT_PERCENT, false);
                } else {
                    applyVolumeFromUi(mVolumePercent, !mIsMuted);
                }
            }
        });
        faderRow.addView(mBtnMute);

        TextView btnMinus = createStepperButton("−", "볼륨 5% 감소", -5);
        LinearLayout.LayoutParams minusLp = new LinearLayout.LayoutParams(dp(36), dp(38));
        minusLp.rightMargin = dp(4);
        btnMinus.setLayoutParams(minusLp);
        faderRow.addView(btnMinus);

        mSeekVolume = new SeekBar(this);
        mSeekVolume.setContentDescription("독립 출력 볼륨 슬라이더");
        mSeekVolume.setMax(100);
        mSeekVolume.setProgress(mVolumePercent);
        mSeekVolume.setSplitTrack(false);
        mSeekVolume.setProgressDrawable(getResources().getDrawable(R.drawable.seekbar_track));
        mSeekVolume.setThumb(getResources().getDrawable(R.drawable.seekbar_thumb));
        mSeekVolume.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mSeekVolume.setLayoutParams(seekLp);
        mSeekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mSuppressSeekCallback) return;
                boolean nextMuted = (progress == 0);
                if (progress > 0 && mIsMuted) {
                    nextMuted = false;
                }
                int nearestDetent = (progress == 20 || progress == 45 || progress == 75 || progress == 100) ? progress : -1;
                if (nearestDetent > 0 && nearestDetent != mLastHapticDetent) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mLastHapticDetent = nearestDetent;
                } else if (nearestDetent < 0) {
                    mLastHapticDetent = -1;
                }
                applyVolumeFromUi(progress, nextMuted);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        faderRow.addView(mSeekVolume);

        TextView btnPlus = createStepperButton("+", "볼륨 5% 증가", 5);
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(dp(36), dp(38));
        plusLp.leftMargin = dp(4);
        btnPlus.setLayoutParams(plusLp);
        faderRow.addView(btnPlus);

        block.addView(faderRow);

        // Quick Detent Chips Row (tailored for music co-listening)
        LinearLayout detentRow = new LinearLayout(this);
        detentRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams detRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detRowLp.topMargin = dp(6);
        detentRow.setLayoutParams(detRowLp);

        mDetentChips.clear();
        for (int i = 0; i < DETENT_VALUES.length; i++) {
            final int targetPct = DETENT_VALUES[i];
            TextView chip = new TextView(this);
            chip.setText(DETENT_LABELS[i]);
            chip.setSingleLine(true);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(2), dp(7), dp(2), dp(7));
            applyTabularMono(chip, true);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < DETENT_VALUES.length - 1) {
                chipLp.rightMargin = dp(6);
            }
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    applyVolumeFromUi(targetPct, false);
                }
            });
            mDetentChips.add(chip);
            detentRow.addView(chip);
        }
        block.addView(detentRow);

        return block;
    }

    private TextView createStepperButton(String label, String contentDesc, final int delta) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setContentDescription(contentDesc);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        btn.setTextColor(COL_TEXT_PRIMARY);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(8)));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                int next = VolumeCurve.clampPercent(mVolumePercent + delta);
                applyVolumeFromUi(next, next == 0);
            }
        });
        return btn;
    }

    private View buildCategoryTabs() {
        HorizontalScrollView tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams tabScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabScrollLp.bottomMargin = dp(12);
        tabScrollView.setLayoutParams(tabScrollLp);
        mTabScrollView = tabScrollView;

        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        mTabLayout = tabLayout;

        mTabButtons.clear();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tabIdx = i;
            Button tabBtn = new Button(this);
            tabBtn.setText(TAB_NAMES[i]);
            tabBtn.setAllCaps(false);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
            tabBtn.setMinimumWidth(0);
            tabBtn.setMinWidth(0);
            tabBtn.setMinimumHeight(dp(36));
            tabBtn.setMinHeight(dp(36));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            btnLp.rightMargin = dp(8);
            tabBtn.setLayoutParams(btnLp);
            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    selectTab(tabIdx, true, true);
                }
            });
            mTabButtons.add(tabBtn);
            tabLayout.addView(tabBtn);
        }

        tabScrollView.addView(tabLayout);
        return tabScrollView;
    }

    private void applyVolumeFromUi(int percent, boolean muted) {
        mVolumePercent = VolumeCurve.clampPercent(percent);
        mIsMuted = muted;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_VOLUME_PCT, mVolumePercent)
                .putBoolean(PREF_MUTED, mIsMuted)
                .apply();

        if (mIsBound && mService != null) {
            mService.setVolumePercent(mVolumePercent);
            mService.setMuted(mIsMuted);
        } else if (mLastDisplayedRunning) {
            Intent intent = new Intent(this, AegisAudioService.class);
            intent.setAction(AegisAudioService.ACTION_SET_VOLUME);
            intent.putExtra(AegisAudioService.EXTRA_VOLUME_PERCENT, mVolumePercent);
            intent.putExtra(AegisAudioService.EXTRA_MUTED, mIsMuted);
            try {
                startService(intent);
            } catch (IllegalStateException ignored) {}
        }

        syncVolumeUi();
    }

    private void syncVolumeUi() {
        mSuppressSeekCallback = true;
        if (mSeekVolume != null) {
            mSeekVolume.setProgress(mIsMuted ? 0 : mVolumePercent);
        }
        mSuppressSeekCallback = false;

        if (mTvVolumeReadout != null) {
            String dbStr = VolumeCurve.formatDb(mVolumePercent, mIsMuted);
            if (mIsMuted) {
                mTvVolumeReadout.setText("  음소거 · " + dbStr);
                mTvVolumeReadout.setTextColor(COL_STOPPED);
            } else {
                mTvVolumeReadout.setText(String.format(Locale.US, "  %d%% · %s", mVolumePercent, dbStr));
                mTvVolumeReadout.setTextColor(COL_AMBER);
            }
        }

        if (mBtnMute != null) {
            mBtnMute.setImageResource(mIsMuted || mVolumePercent == 0
                    ? R.drawable.ic_volume_mute
                    : R.drawable.ic_volume_on);
        }

        for (int i = 0; i < mDetentChips.size(); i++) {
            TextView chip = mDetentChips.get(i);
            boolean active = !mIsMuted && (mVolumePercent == DETENT_VALUES[i]);
            if (active) {
                chip.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(8)));
                chip.setTextColor(COL_AMBER);
            } else {
                chip.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(8)));
                chip.setTextColor(COL_TEXT_SECONDARY);
            }
        }

        updateSystemVolumeReadout();
    }

    private void updateSystemVolumeReadout() {
        if (mTvSystemVolume == null || mAudioManager == null) return;
        try {
            int cur = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            mTvSystemVolume.setText(String.format(Locale.US, "시스템 %d/%d", cur, max));
        } catch (Throwable ignored) {}
    }

    private void selectTab(int tabIndex, boolean smoothScroll, boolean persistIfIdle) {
        mCurrentSelectedTab = TabSyncPolicy.clampTabIndex(tabIndex);
        if (persistIfIdle && !mLastDisplayedRunning) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(AegisAudioService.PREF_LAST_TAB, mCurrentSelectedTab)
                    .apply();
        }

        refreshTabStyles();
        renderPresetCards();
        scrollTabIntoCenter(mCurrentSelectedTab, smoothScroll);
        updateJumpCue();
    }

    private void refreshTabStyles() {
        int playingTab = (mLastDisplayedRunning && mActivePreset != null) ? mActivePreset.tabIndex : -1;
        for (int i = 0; i < mTabButtons.size(); i++) {
            Button btn = mTabButtons.get(i);
            boolean isSelected = (i == mCurrentSelectedTab);
            boolean isPlayingOwner = (i == playingTab);

            if (isPlayingOwner) {
                SpannableString label = new SpannableString("\u25cf " + TAB_NAMES[i]);
                label.setSpan(
                        new ForegroundColorSpan(isSelected ? COL_ACTIVE_ON_LIGHT : COL_ACTIVE),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                btn.setText(label);
            } else {
                btn.setText(TAB_NAMES[i]);
            }

            if (isSelected) {
                btn.setBackground(createBoxDrawable(COL_TEXT_PRIMARY, COL_TEXT_PRIMARY, dp(1), dp(18)));
                btn.setTextColor(COL_BG);
            } else if (isPlayingOwner) {
                btn.setBackground(createBoxDrawable(COL_SURFACE, COL_ACTIVE, dp(1), dp(18)));
                btn.setTextColor(COL_TEXT_PRIMARY);
            } else {
                btn.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(18)));
                btn.setTextColor(COL_TEXT_SECONDARY);
            }
        }
    }

    private void scrollTabIntoCenter(final int tabIndex, final boolean smooth) {
        if (mTabScrollView == null || mTabLayout == null) return;
        if (tabIndex < 0 || tabIndex >= mTabButtons.size()) return;
        final Button targetBtn = mTabButtons.get(tabIndex);

        final Runnable performScroll = new Runnable() {
            @Override
            public void run() {
                if (mTabScrollView == null || mTabLayout == null || tabIndex != mCurrentSelectedTab) return;
                int x = TabSyncPolicy.computeCenteredScrollX(
                        targetBtn.getLeft(),
                        targetBtn.getWidth(),
                        mTabScrollView.getWidth(),
                        mTabLayout.getWidth());
                if (smooth) {
                    mTabScrollView.smoothScrollTo(x, 0);
                } else {
                    mTabScrollView.scrollTo(x, 0);
                }
            }
        };

        if (mTabScrollView.getWidth() > 0 && mTabLayout.getWidth() > 0 && targetBtn.getWidth() > 0) {
            mTabScrollView.post(performScroll);
        } else {
            final ViewTreeObserver vto = mTabScrollView.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mTabScrollView == null) return;
                    ViewTreeObserver currentVto = mTabScrollView.getViewTreeObserver();
                    if (currentVto.isAlive()) {
                        currentVto.removeOnGlobalLayoutListener(this);
                    }
                    mTabScrollView.post(performScroll);
                }
            });
        }
    }

    private void updateJumpCue() {
        if (mTvJumpCue == null) return;
        if (!mLastDisplayedRunning || mActivePreset == null) {
            mTvJumpCue.setVisibility(View.GONE);
            return;
        }
        mTvJumpCue.setVisibility(View.VISIBLE);
        boolean diffTab = TabSyncPolicy.isViewingDifferentTab(true, mActivePreset.tabIndex, mCurrentSelectedTab);
        if (diffTab) {
            mTvJumpCue.setText("재생 탭 ›");
            mTvJumpCue.setTextColor(COL_ACTIVE);
            mTvJumpCue.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_ACTIVE, dp(1), dp(6)));
        } else {
            mTvJumpCue.setText("위치 보기 ›");
            mTvJumpCue.setTextColor(COL_TEXT_SECONDARY);
            mTvJumpCue.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(6)));
        }
    }

    private void jumpToPlayingPreset() {
        if (!mLastDisplayedRunning || mActivePreset == null) return;
        int targetTab = TabSyncPolicy.clampTabIndex(mActivePreset.tabIndex);
        if (mCurrentSelectedTab != targetTab) {
            selectTab(targetTab, true, false);
        } else {
            scrollTabIntoCenter(targetTab, true);
        }
        scrollActiveCardIntoView(mActivePresetCardView);
    }

    private void scrollActiveCardIntoView(final View cardView) {
        if (mMainScrollView == null || mPresetContainer == null || cardView == null) return;
        final Runnable performScroll = new Runnable() {
            @Override
            public void run() {
                if (mMainScrollView == null || mPresetContainer == null || cardView != mActivePresetCardView) return;
                if (mMainScrollView.getChildCount() == 0) return;
                int cardTop = mPresetContainer.getTop() + cardView.getTop();
                int rootH = mMainScrollView.getChildAt(0).getHeight();
                int scrollY = TabSyncPolicy.computeCardRevealScrollY(cardTop, mMainScrollView.getHeight(), rootH, dp(84));
                mMainScrollView.smoothScrollTo(0, scrollY);
            }
        };

        if (cardView.getHeight() > 0 && mMainScrollView.getHeight() > 0) {
            mMainScrollView.post(performScroll);
        } else {
            ViewTreeObserver vto = cardView.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    ViewTreeObserver currentVto = cardView.getViewTreeObserver();
                    if (currentVto.isAlive()) {
                        currentVto.removeOnGlobalLayoutListener(this);
                    }
                    if (mMainScrollView != null) {
                        mMainScrollView.post(performScroll);
                    }
                }
            });
        }
    }

    private void renderPresetCards() {
        if (mPresetContainer == null) return;
        mPresetContainer.removeAllViews();
        mActivePresetCardView = null;

        int activeId = (mLastDisplayedRunning && mActivePreset != null) ? mActivePreset.id : -1;
        mLastHighlightedPresetId = activeId;

        for (PresetModel preset : PresetModel.getAllPresets()) {
            if (preset.tabIndex == mCurrentSelectedTab) {
                View cardView = createPresetCard(preset, preset.id == activeId);
                if (preset.id == activeId) {
                    mActivePresetCardView = cardView;
                }
                mPresetContainer.addView(cardView);
            }
        }
    }

    private View createPresetCard(final PresetModel preset, boolean isActiveCard) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (isActiveCard) {
            card.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_ACTIVE, dp(2), dp(14)));
        } else {
            card.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(14)));
        }
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // Eyebrow Row: Badge + Active Status Flag
        LinearLayout eyebrowRow = new LinearLayout(this);
        eyebrowRow.setOrientation(LinearLayout.HORIZONTAL);
        eyebrowRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(preset.getShortCode() + " · " + preset.badge);
        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvBadge.setTextColor(COL_TEXT_SECONDARY);
        applyTabularMono(tvBadge, false);
        tvBadge.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        eyebrowRow.addView(tvBadge);

        if (isActiveCard) {
            TextView tvActiveFlag = new TextView(this);
            tvActiveFlag.setText("재생 중");
            tvActiveFlag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvActiveFlag.setTextColor(COL_ACTIVE);
            tvActiveFlag.setTypeface(Typeface.DEFAULT_BOLD);
            eyebrowRow.addView(tvActiveFlag);
        }
        card.addView(eyebrowRow);

        // Full-Width Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(preset.title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTextColor(COL_TEXT_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(4), 0, dp(4));
        card.addView(tvTitle);

        // Spec Line
        TextView tvSpec = new TextView(this);
        tvSpec.setText(buildPresetSpecLine(preset));
        tvSpec.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSpec.setTextColor(COL_AMBER);
        applyTabularMono(tvSpec, true);
        tvSpec.setPadding(0, 0, 0, dp(5));
        card.addView(tvSpec);

        // Concise 2-3 Line Effect Bullets
        TextView tvDesc = new TextView(this);
        tvDesc.setText(preset.desc);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDesc.setTextColor(COL_TEXT_SECONDARY);
        tvDesc.setLineSpacing(dp(2), 1.22f);
        card.addView(tvDesc);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                playPreset(preset);
            }
        });

        return card;
    }

    private String buildPresetSpecLine(PresetModel preset) {
        StringBuilder sb = new StringBuilder();
        if (preset.isSweep) {
            sb.append(String.format(Locale.US, "스윕 %.1f~%.1f Hz · %.0fs 왕복",
                    preset.sweepStart, preset.sweepEnd, preset.sweepDurationSec));
        } else if (preset.binauralBeat > 0) {
            sb.append(String.format(Locale.US, "바이노럴 %.1f + %.1f Hz (차 %.2f Hz)",
                    preset.freq1, preset.freq2, preset.binauralBeat));
        } else if (preset.freq2 > 0) {
            sb.append(String.format(Locale.US, "듀얼 %.2f + %.2f Hz", preset.freq1, preset.freq2));
        } else {
            sb.append(String.format(Locale.US, "단일 %.2f Hz", preset.freq1));
        }

        if (preset.isAntiPhase) {
            sb.append(" · 180° 상쇄");
        }
        if (preset.fmMod) {
            sb.append(String.format(Locale.US, " · FM ±%.0f%%", preset.fmDepth * 100.0f));
        }
        if (preset.amMod) {
            sb.append(String.format(Locale.US, " · AM %.0f%%", preset.amDepth * 100.0f));
        }
        return sb.toString();
    }

    private void playPreset(PresetModel preset) {
        mActivePreset = preset;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(AegisAudioService.PREF_LAST_TAB, preset.tabIndex)
                .putInt(AegisAudioService.PREF_LAST_PRESET_ID, preset.id)
                .apply();

        Intent intent = new Intent(this, AegisAudioService.class);
        intent.setAction(AegisAudioService.ACTION_START);
        intent.putExtra(AegisAudioService.EXTRA_PRESET_ID, preset.id);
        intent.putExtra(AegisAudioService.EXTRA_VOLUME_PERCENT, mVolumePercent);
        intent.putExtra(AegisAudioService.EXTRA_MUTED, mIsMuted);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        updateDashboard(preset, true, preset.isSweep ? preset.sweepStart : preset.freq1);
        if (mCurrentSelectedTab != preset.tabIndex) {
            selectTab(preset.tabIndex, true, false);
        }
    }

    private void stopAudio() {
        if (mService != null) {
            mService.stopPlayback();
        } else {
            stopService(new Intent(this, AegisAudioService.class));
        }
        updateDashboard(null, false, 0.0f);
    }

    private void syncFromService(boolean allowTabAutoSync) {
        boolean running = (mService != null && mService.isPlaying());
        PresetModel p = running ? mService.getCurrentPreset() : null;
        if (running && p != null) {
            mActivePreset = p;
            updateDashboard(p, true, mService.getCurrentLiveFreqHz());
        } else {
            mActivePreset = null;
            updateDashboard(null, false, 0.0f);
        }

        int persistedTab = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(AegisAudioService.PREF_LAST_TAB, 0);
        int playingTab = (running && p != null) ? p.tabIndex : -1;
        int targetTab = TabSyncPolicy.resolveTabOnEntry(
                running && p != null,
                playingTab,
                persistedTab,
                mCurrentSelectedTab,
                allowTabAutoSync);

        if (targetTab != mCurrentSelectedTab) {
            selectTab(targetTab, true, false);
        } else if (allowTabAutoSync) {
            scrollTabIntoCenter(mCurrentSelectedTab, false);
        }
    }

    private void refreshLiveTelemetry() {
        updateSystemVolumeReadout();
        if (mIsBound && mService != null) {
            boolean running = mService.isPlaying();
            PresetModel p = mService.getCurrentPreset();
            if (running && p != null) {
                mActivePreset = p;
                float liveHz = mService.getCurrentLiveFreqHz();
                if (liveHz <= 0.0f) {
                    liveHz = p.isSweep ? p.sweepStart : p.freq1;
                }
                boolean guarded = mService.isUltrasonicGuarded();
                if (!mLastDisplayedRunning || mLastHighlightedPresetId != p.id) {
                    updateDashboard(p, true, liveHz);
                } else {
                    updateHeroFreqViews(liveHz, p, true);
                    if (guarded != mLastUltrasonicGuarded) {
                        updateMechanismText(p, guarded);
                    }
                }
            } else if (mLastDisplayedRunning) {
                updateDashboard(null, false, 0.0f);
            }
        }
    }

    private void updateMechanismText(PresetModel preset, boolean guarded) {
        mLastUltrasonicGuarded = guarded;
        if (guarded) {
            mTvMechanismInfo.setText("• 이어폰 연결 감지 — 초음파 안전 감쇄(-12dB) 적용 중\n" + preset.desc);
        } else {
            mTvMechanismInfo.setText(preset.desc);
        }
    }

    private void updateHeroFreqViews(float liveHz, PresetModel p, boolean isRunning) {
        if (!isRunning || p == null) {
            mTvHeroFreq.setText("0.00 Hz");
            mTvHeroFreq.setTextColor(COL_TEXT_TERTIARY);
            mTvHeroSubBadge.setVisibility(View.GONE);
            return;
        }

        if (liveHz >= 1000.0f) {
            mTvHeroFreq.setText(String.format(Locale.US, "%.1f Hz", liveHz));
        } else {
            mTvHeroFreq.setText(String.format(Locale.US, "%.2f Hz", liveHz));
        }
        mTvHeroFreq.setTextColor(COL_AMBER);

        if (p.binauralBeat > 0) {
            mTvHeroSubBadge.setText(String.format(Locale.US, "Δ %.2f Hz", p.binauralBeat));
            mTvHeroSubBadge.setVisibility(View.VISIBLE);
        } else if (p.isSweep) {
            mTvHeroSubBadge.setText("SWEEP");
            mTvHeroSubBadge.setVisibility(View.VISIBLE);
        } else if (p.freq2 > 0) {
            mTvHeroSubBadge.setText(String.format(Locale.US, "+ %.1f Hz", p.freq2));
            mTvHeroSubBadge.setVisibility(View.VISIBLE);
        } else if (p.fmMod) {
            mTvHeroSubBadge.setText("FM");
            mTvHeroSubBadge.setVisibility(View.VISIBLE);
        } else if (p.amMod) {
            mTvHeroSubBadge.setText("AM");
            mTvHeroSubBadge.setVisibility(View.VISIBLE);
        } else {
            mTvHeroSubBadge.setVisibility(View.GONE);
        }
    }

    private void updateDashboard(PresetModel preset, boolean isRunning, float liveHz) {
        mLastDisplayedRunning = isRunning;

        if (isRunning && preset != null) {
            mLedIndicator.setImageResource(R.drawable.led_running);
            mTvStatusText.setText("재생 중 · " + getTabShortName(preset.tabIndex));
            mTvStatusText.setTextColor(COL_ACTIVE);

            float displayHz = liveHz > 0.0f ? liveHz : (preset.isSweep ? preset.sweepStart : preset.freq1);
            updateHeroFreqViews(displayHz, preset, true);

            mTvScenarioTitle.setText(preset.title);
            boolean guarded = (mIsBound && mService != null && mService.isUltrasonicGuarded());
            updateMechanismText(preset, guarded);

            mBtnStop.setText("재생 중지");
            mBtnStop.setTextColor(COL_TEXT_PRIMARY);
            mBtnStop.setBackground(createBoxDrawable(COL_STOP_BTN, COL_STOPPED, dp(1), dp(10)));
            mBtnStop.setEnabled(true);
        } else {
            mLedIndicator.setImageResource(R.drawable.led_stopped);
            mTvStatusText.setText("대기 중");
            mTvStatusText.setTextColor(COL_TEXT_SECONDARY);

            updateHeroFreqViews(0.0f, null, false);
            mLastUltrasonicGuarded = false;

            mTvScenarioTitle.setText("프리셋을 선택하면 즉시 재생됩니다");
            mTvMechanismInfo.setText("• 위상 연속 정현파 무왜곡 출력 대기\n• 삼성 뮤직·유튜브 등 음악 앱과 끊김 없이 동시 재생됩니다");

            mBtnStop.setText("대기 중 (프리셋 선택 시 재생)");
            mBtnStop.setTextColor(COL_TEXT_TERTIARY);
            mBtnStop.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
            mBtnStop.setEnabled(false);
        }

        int desiredHighlightId = (isRunning && preset != null) ? preset.id : -1;
        if (desiredHighlightId != mLastHighlightedPresetId) {
            renderPresetCards();
        }
        refreshTabStyles();
        updateJumpCue();
    }

    private String getTabShortName(int tabIndex) {
        switch (tabIndex) {
            case 0: return "안티방어";
            case 1: return "수면";
            case 2: return "집중·공부";
            case 3: return "명상·안정";
            case 4: return "두통·통증";
            case 5: return "운동·피로";
            case 6: return "기분·힐링";
            case 7: return "모기·벌레";
            default: return "이지스";
        }
    }

    private void applyTabularMono(TextView tv, boolean bold) {
        tv.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= 21) {
            tv.setFontFeatureSettings("tnum");
        }
    }

    private GradientDrawable createBoxDrawable(int bgColor, int strokeColor, int strokePx, int cornerRadius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setStroke(strokePx, strokeColor);
        gd.setCornerRadius(cornerRadius);
        return gd;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
