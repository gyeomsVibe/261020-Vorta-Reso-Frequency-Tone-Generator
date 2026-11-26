package com.vorta.aegis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commercial Precision Acoustic Instrument UI for Vorta Aegis Pro v3.4.4.
 * Non-expert daily UX + Pro acoustic studio:
 * - Mini Mode (collapsed): Info Card -> Unified Volume Controller (no waveform graph for compact daily simplicity)
 * - Studio Mode (expanded): Info Card -> Auto-Animating Waveform -> Unified Volume -> Acoustic Studio Controls
 * - Time-of-day smart recommendations, 4 Auto-Courses, plain-Korean ELI10 guide, and multi-token search
 * - Zero-churn telemetry loop and 360dp / 1.3x font-scale clip-free layout
 */
public class MainActivity extends Activity {
    private static final String PREFS_NAME = AegisAudioService.PREFS_NAME;
    private static final String PREF_VOLUME_PCT = AegisAudioService.PREF_VOLUME_PCT;
    private static final String PREF_MUTED = AegisAudioService.PREF_MUTED;
    private static final String PREF_FAVORITES = AegisAudioService.PREF_FAVORITES;
    private static final String PREF_FILTER_MODE = "preset_filter_mode";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_CORE = 1;
    private static final int FILTER_PRO = 2;
    private static final int FILTER_JOURNEY = 3;
    private static final int FILTER_FAV = 4;
    private static final int FILTER_RECENT = 5;

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
    private static final int COL_VU_OFF = Color.parseColor("#232730");
    private static final int COL_TEXT_PRIMARY = Color.parseColor("#F2F3F5");
    private static final int COL_TEXT_SECONDARY = Color.parseColor("#9A9FA8");
    private static final int COL_TEXT_TERTIARY = Color.parseColor("#656B76");

    private static final int[] DETENT_VALUES = new int[]{15, 45, 75, 100, 120};
    private static final String[] DETENT_LABELS = new String[]{
            "15%\n영상", "45%\n병행", "75%\n단독", "100%\n최대", "120%\n증폭"
    };
    private static final int[] TIMER_STEPS = new int[]{0, 15, 30, 60, 90};
    private static final String[] PROFILE_LABELS = new String[]{"자동", "스피커", "이어폰", "순수원음"};
    private static final String[] AMBIENT_LABELS = new String[]{"꺼짐", "브라운", "핑크", "빗소리"};
    private static final String[] TAB_NAMES = new String[]{
            "안티방어",
            "수면",
            "집중·공부",
            "명상·안정",
            "두통·통증",
            "운동·피로",
            "기분·힐링",
            "모기·벌레"
    };

    private AegisAudioService mService;
    private boolean mIsBound = false;
    private AudioManager mAudioManager;

    // Console Telemetry & Accordion Widgets
    private ScrollView mMainScrollView;
    private LinearLayout mRootLayout;
    private LinearLayout mScopeSection;
    private LinearLayout mConsoleDetailBody;
    private boolean mConsoleExpanded = false;
    private ImageView mLedIndicator;
    private TextView mTvStatusText;
    private final GradientDrawable[] mVuBarDrawables = new GradientDrawable[12];
    private float mVuSmoothLevel = 0.0f;
    private TextView mBtnAccordionToggle;
    private TextView mTvHeroFreq;
    private TextView mTvHeroSubBadge;
    private TextView mBtnMiniStop;
    private TextView mTvScenarioTitle;
    private TextView mTvJumpCue;
    private TextView mBtnQuickModeBadge;

    // Expanded Pro Studio Widgets
    private TextView mTvMechanismInfo;
    private LiveWaveformView mWaveformView;
    private TextView mBtnScopeFund;
    private TextView mBtnScopeOut;
    private TextView mTvScopeTelemetry;
    private TextView mTvDetectedDevice;
    private final List<TextView> mProfileChips = new ArrayList<>();
    private TextView mTvAmbientReadout;
    private final List<TextView> mAmbientChips = new ArrayList<>();
    private LinearLayout mAmbientMixRow;
    private SeekBar mSeekAmbientMix;
    private TextView mBtnTimerChip;
    private Button mBtnStop;

    // Independent Volume Controller Widgets (Studio)
    private TextView mTvVolumeReadout;
    private TextView mTvSystemVolume;
    private ImageButton mBtnMute;
    private SeekBar mSeekVolume;
    private final List<TextView> mDetentChips = new ArrayList<>();
    private TextView mBtnRecommendedVol;

    // Daily Discovery (Routines + Recommendations + Search + Filters + Tabs)
    private LinearLayout mRoutineBannerRow;
    private TextView mTvRoutineStatus;
    private TextView mBtnRoutineNext;
    private LinearLayout mRoutineCoursesRow;
    private final List<LinearLayout> mRoutineCourseCards = new ArrayList<>();
    private final List<String> mRoutineCourseIds = new ArrayList<>();
    private String mLastActiveRoutineId = "";
    private String mLastRoutineText = "";

    private TextView mTvRecHeader;
    private LinearLayout mRecCardsRow;
    private final List<LinearLayout> mRecCardViews = new ArrayList<>();
    private final List<Integer> mRecPresetIds = new ArrayList<>();
    private EditText mEtSearch;
    private TextView mBtnClearSearch;
    private TextView mTvSearchStatus;
    private String mSearchQuery = "";

    private final List<TextView> mFilterChips = new ArrayList<>();
    private HorizontalScrollView mTabScrollView;
    private LinearLayout mTabLayout;
    private LinearLayout mPresetContainer;
    private View mActivePresetCardView;
    private final List<Button> mTabButtons = new ArrayList<>();
    private int mCurrentSelectedTab = 0;
    private int mCurrentFilterMode = FILTER_ALL;
    private Set<Integer> mFavoriteIds = new HashSet<>();

    // Local mirrored UI state
    private int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
    private boolean mIsMuted = false;
    private int mOutputProfileMode = AegisDspCore.PROFILE_AUTO;
    private int mAmbientType = AegisDspCore.AMBIENT_OFF;
    private int mAmbientMixPercent = 25;
    private boolean mSuppressSeekCallback = false;
    private int mLastHapticDetent = -1;
    private PresetModel mActivePreset = null;
    private boolean mLastDisplayedRunning = false;
    private int mLastHighlightedPresetId = -1;
    private boolean mLastUltrasonicGuarded = false;
    private boolean mPendingEntryTabSync = true;
    private String mSwitchNotice = "";
    private AlertDialog mAboutDialog;
    private AlertDialog mExplainDialog;
    private AlertDialog mGuideDialog;

    // Telemetry change-detection caches (zero-churn UI tick)
    private int mLastVuLitCount = -1;
    private int mLastSysVol = -1;
    private int mLastSysMax = -1;
    private String mLastScopeTelemetry = "";
    private String mLastTimerLabel = "";
    private boolean mLastTimerActive = false;
    private String mLastQuickBadge = "";
    private String mLastDeviceLabel = "";
    private String mLastHeroHzText = "";
    private String mLastHeroBadgeText = "";
    private boolean mLastCompActive = false;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryTick = new Runnable() {
        @Override
        public void run() {
            refreshLiveTelemetry();
            mUiHandler.postDelayed(this, mLastDisplayedRunning ? 150L : 1000L);
        }
    };

    private BroadcastReceiver mStateReceiver;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AegisAudioService.LocalBinder binder = (AegisAudioService.LocalBinder) service;
            mService = binder.getService();
            mIsBound = true;
            if (mWaveformView != null) {
                mWaveformView.bindDspCore(mService.getDspCore());
            }
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            mOutputProfileMode = mService.getOutputProfileMode();
            mAmbientType = mService.getAmbientType();
            mAmbientMixPercent = mService.getAmbientMixPercent();
            syncVolumeUi();
            refreshProfileAndAmbientUi();
            syncFromService(mPendingEntryTabSync);
            mPendingEntryTabSync = false;
            handleAutoResumeExtraIfPresent(getIntent());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mIsBound = false;
            if (mWaveformView != null) {
                mWaveformView.setActive(false);
                mWaveformView.bindDspCore(null);
            }
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
        mOutputProfileMode = prefs.getInt(AegisAudioService.PREF_OUTPUT_PROFILE, AegisDspCore.PROFILE_AUTO);
        mAmbientType = prefs.getInt(AegisAudioService.PREF_AMBIENT_TYPE, AegisDspCore.AMBIENT_OFF);
        mAmbientMixPercent = prefs.getInt(AegisAudioService.PREF_AMBIENT_MIX, 25);
        mCurrentFilterMode = clampFilterMode(prefs.getInt(PREF_FILTER_MODE, FILTER_ALL));
        loadFavoritesFromPrefs(prefs);

        setContentView(buildUnifiedLayout());
        syncVolumeUi();
        refreshProfileAndAmbientUi();
        refreshRecommendationsRow();
        refreshFilterChipsUi();
        registerStateReceiver();

        Intent intent = new Intent(this, AegisAudioService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        int savedTab = TabSyncPolicy.clampTabIndex(prefs.getInt(AegisAudioService.PREF_LAST_TAB, 0));
        mPendingEntryTabSync = true;
        selectTab(savedTab, false, false);
        updateIdleResumeSummary();
    }

    private static int clampFilterMode(int mode) {
        return (mode >= FILTER_ALL && mode <= FILTER_RECENT) ? mode : FILTER_ALL;
    }

    private void loadFavoritesFromPrefs(SharedPreferences prefs) {
        mFavoriteIds.clear();
        Set<String> raw = prefs.getStringSet(PREF_FAVORITES, Collections.<String>emptySet());
        if (raw != null) {
            for (String s : raw) {
                try {
                    mFavoriteIds.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void saveFavoritesToPrefs() {
        Set<String> copy = new HashSet<>(mFavoriteIds.size());
        for (Integer id : mFavoriteIds) {
            copy.add(String.valueOf(id));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_FAVORITES, copy)
                .apply();
    }

    private List<Integer> loadRecentIds() {
        String raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(AegisAudioService.PREF_RECENT_IDS, "");
        return AegisAudioService.parseRecentIds(raw);
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
            handleAutoResumeExtraIfPresent(intent);
        }
    }

    private void handleAutoResumeExtraIfPresent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(AegisAudioService.EXTRA_AUTO_RESUME_LAST, false)) {
            intent.removeExtra(AegisAudioService.EXTRA_AUTO_RESUME_LAST);
            if (mService != null && !mService.isPlaying()) {
                resumeLastPreset();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRootLayout != null) {
            mRootLayout.requestFocus();
        }
        refreshRecommendationsRow();
        if (mIsBound && mService != null) {
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            mOutputProfileMode = mService.getOutputProfileMode();
            mAmbientType = mService.getAmbientType();
            mAmbientMixPercent = mService.getAmbientMixPercent();
            syncVolumeUi();
            refreshProfileAndAmbientUi();
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
        mPendingEntryTabSync = true;
        super.onStop();
    }

    @Override
    protected void onPause() {
        mUiHandler.removeCallbacks(mTelemetryTick);
        if (mWaveformView != null) {
            mWaveformView.setActive(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mUiHandler.removeCallbacks(mTelemetryTick);
        if (mAboutDialog != null && mAboutDialog.isShowing()) {
            try { mAboutDialog.dismiss(); } catch (Throwable ignored) {}
        }
        mAboutDialog = null;
        if (mExplainDialog != null && mExplainDialog.isShowing()) {
            try { mExplainDialog.dismiss(); } catch (Throwable ignored) {}
        }
        mExplainDialog = null;
        if (mGuideDialog != null && mGuideDialog.isShowing()) {
            try { mGuideDialog.dismiss(); } catch (Throwable ignored) {}
        }
        mGuideDialog = null;
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
        root.setPadding(dp(16), dp(12), dp(16), dp(28));
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        mRootLayout = root;

        root.addView(buildEditorialHeader());
        root.addView(buildConsoleCard());
        root.addView(buildRoutineStrip());
        root.addView(buildRecommendationStrip());
        root.addView(buildSearchBar());
        root.addView(buildFilterChipRow());
        root.addView(buildCategoryTabs());

        mTvSearchStatus = new TextView(this);
        mTvSearchStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvSearchStatus.setTextColor(COL_AMBER);
        mTvSearchStatus.setPadding(dp(2), 0, dp(2), dp(8));
        mTvSearchStatus.setVisibility(View.GONE);
        root.addView(mTvSearchStatus);

        mPresetContainer = new LinearLayout(this);
        mPresetContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mPresetContainer);

        TextView tvNotice = new TextView(this);
        tvNotice.setText("타 오디오 앱 동시 재생 · 120% 클린 증폭 (-0.50 dBTP) · 88종");
        tvNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvNotice.setTextColor(COL_TEXT_TERTIARY);
        tvNotice.setLineSpacing(dp(2), 1.15f);
        tvNotice.setPadding(0, dp(10), 0, dp(6));
        root.addView(tvNotice);

        scrollView.addView(root);
        return scrollView;
    }

    private View buildEditorialHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvEyebrow = new TextView(this);
        tvEyebrow.setText("VORTA AEGIS · v3.4.4");
        tvEyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvEyebrow.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(tvEyebrow, true);
        if (Build.VERSION.SDK_INT >= 21) {
            tvEyebrow.setLetterSpacing(0.14f);
        }
        leftCol.addView(tvEyebrow);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("보르타 이지스");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitle.setTextColor(COL_TEXT_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(1), 0, dp(1));
        leftCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        String rawModel = Build.MODEL != null && !Build.MODEL.trim().isEmpty() ? Build.MODEL.trim() : "Android Audio";
        String shortModel = rawModel.contains("N986") ? "Note 20 Ultra" : rawModel;
        tvSub.setText(shortModel + " · 48kHz · 88종");
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSub.setTextColor(COL_TEXT_SECONDARY);
        applyTabularMono(tvSub, false);
        leftCol.addView(tvSub);
        header.addView(leftCol);

        TextView btnGuide = new TextView(this);
        btnGuide.setText("가이드");
        btnGuide.setContentDescription("쉬운 용어와 시나리오 선택 가이드 보기");
        btnGuide.setSingleLine(true);
        btnGuide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnGuide.setTextColor(COL_AMBER);
        btnGuide.setTypeface(Typeface.DEFAULT_BOLD);
        btnGuide.setGravity(Gravity.CENTER);
        btnGuide.setMinHeight(dp(34));
        btnGuide.setPadding(dp(11), dp(6), dp(11), dp(6));
        btnGuide.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(10)));
        LinearLayout.LayoutParams guideBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guideBtnLp.rightMargin = dp(6);
        btnGuide.setLayoutParams(guideBtnLp);
        btnGuide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showBeginnerGuideDialog();
            }
        });
        header.addView(btnGuide);

        TextView btnInfo = new TextView(this);
        btnInfo.setText("설정·정보");
        btnInfo.setContentDescription("보르타 이지스 설정, 진단 및 라이선스 안내");
        btnInfo.setSingleLine(true);
        btnInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnInfo.setTextColor(COL_TEXT_PRIMARY);
        btnInfo.setTypeface(Typeface.DEFAULT_BOLD);
        btnInfo.setGravity(Gravity.CENTER);
        btnInfo.setMinHeight(dp(34));
        btnInfo.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnInfo.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
        btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showAboutLicenseDialog();
            }
        });
        header.addView(btnInfo);

        return header;
    }

    private View buildConsoleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(14)));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams dashLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dashLp.bottomMargin = dp(10);
        card.setLayoutParams(dashLp);

        // --- Accordion Always-Visible Mini Header ---
        LinearLayout miniHeader = new LinearLayout(this);
        miniHeader.setOrientation(LinearLayout.VERTICAL);
        miniHeader.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        miniHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setConsoleExpanded(!mConsoleExpanded);
            }
        });

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        mLedIndicator = new ImageView(this);
        mLedIndicator.setLayoutParams(new LinearLayout.LayoutParams(dp(9), dp(9)));
        mLedIndicator.setImageResource(R.drawable.led_stopped);
        statusRow.addView(mLedIndicator);

        mTvStatusText = new TextView(this);
        mTvStatusText.setText("대기 중");
        mTvStatusText.setSingleLine(true);
        mTvStatusText.setEllipsize(TextUtils.TruncateAt.END);
        mTvStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvStatusText.setTextColor(COL_TEXT_SECONDARY);
        mTvStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        mTvStatusText.setPadding(dp(6), 0, dp(6), 0);
        mTvStatusText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        statusRow.addView(mTvStatusText);

        // 12-Bar LED VU Meter
        LinearLayout vuRow = new LinearLayout(this);
        vuRow.setOrientation(LinearLayout.HORIZONTAL);
        vuRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams vuRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vuRowLp.rightMargin = dp(8);
        vuRow.setLayoutParams(vuRowLp);
        int barW = dp(3);
        int barH = dp(11);
        int barGap = dp(2);
        float barRad = dp(1);
        for (int i = 0; i < 12; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(barW, barH);
            if (i < 11) barLp.rightMargin = barGap;
            bar.setLayoutParams(barLp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(barRad);
            gd.setColor(COL_VU_OFF);
            mVuBarDrawables[i] = gd;
            bar.setBackground(gd);
            vuRow.addView(bar);
        }
        statusRow.addView(vuRow);

        mBtnAccordionToggle = new TextView(this);
        mBtnAccordionToggle.setText("스튜디오 ▼");
        mBtnAccordionToggle.setSingleLine(true);
        mBtnAccordionToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mBtnAccordionToggle.setTextColor(COL_AMBER);
        mBtnAccordionToggle.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnAccordionToggle.setPadding(dp(8), dp(4), dp(8), dp(4));
        mBtnAccordionToggle.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(6)));
        mBtnAccordionToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setConsoleExpanded(!mConsoleExpanded);
            }
        });
        statusRow.addView(mBtnAccordionToggle);
        miniHeader.addView(statusRow);

        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(0, dp(4), 0, dp(2));

        mTvHeroFreq = new TextView(this);
        mTvHeroFreq.setText("0.00 Hz");
        mTvHeroFreq.setSingleLine(true);
        mTvHeroFreq.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        mTvHeroFreq.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvHeroFreq, true);
        heroRow.addView(mTvHeroFreq);

        mTvHeroSubBadge = new TextView(this);
        mTvHeroSubBadge.setText("");
        mTvHeroSubBadge.setSingleLine(true);
        mTvHeroSubBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mTvHeroSubBadge.setTextColor(COL_AMBER);
        mTvHeroSubBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        mTvHeroSubBadge.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(6)));
        applyTabularMono(mTvHeroSubBadge, true);
        LinearLayout.LayoutParams subBadgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subBadgeLp.leftMargin = dp(8);
        mTvHeroSubBadge.setLayoutParams(subBadgeLp);
        mTvHeroSubBadge.setVisibility(View.GONE);
        heroRow.addView(mTvHeroSubBadge);

        View heroSpacer = new View(this);
        heroSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        heroRow.addView(heroSpacer);

        mBtnMiniStop = new TextView(this);
        mBtnMiniStop.setText("▶ 이어듣기");
        mBtnMiniStop.setSingleLine(true);
        mBtnMiniStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnMiniStop.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnMiniStop.setTextColor(COL_AMBER);
        mBtnMiniStop.setGravity(Gravity.CENTER);
        mBtnMiniStop.setPadding(dp(10), dp(5), dp(10), dp(5));
        mBtnMiniStop.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(7)));
        mBtnMiniStop.setEnabled(true);
        mBtnMiniStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (mLastDisplayedRunning) {
                    stopAudio();
                } else {
                    resumeLastPreset();
                }
            }
        });
        heroRow.addView(mBtnMiniStop);
        miniHeader.addView(heroRow);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        mTvScenarioTitle = new TextView(this);
        mTvScenarioTitle.setText("프리셋을 선택하면 즉시 재생됩니다");
        mTvScenarioTitle.setSingleLine(true);
        mTvScenarioTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTvScenarioTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvScenarioTitle.setTextColor(COL_TEXT_PRIMARY);
        mTvScenarioTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTvScenarioTitle.setPadding(0, 0, dp(8), 0);
        mTvScenarioTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(mTvScenarioTitle);

        mTvJumpCue = new TextView(this);
        mTvJumpCue.setText("재생 탭 ›");
        mTvJumpCue.setSingleLine(true);
        mTvJumpCue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mTvJumpCue.setTextColor(COL_ACTIVE);
        mTvJumpCue.setPadding(dp(8), dp(2), dp(8), dp(2));
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

        mBtnQuickModeBadge = new TextView(this);
        mBtnQuickModeBadge.setText("자동·스피커");
        mBtnQuickModeBadge.setSingleLine(true);
        mBtnQuickModeBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        mBtnQuickModeBadge.setTextColor(COL_TEXT_SECONDARY);
        applyTabularMono(mBtnQuickModeBadge, true);
        mBtnQuickModeBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
        mBtnQuickModeBadge.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(6)));
        LinearLayout.LayoutParams qBadgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qBadgeLp.leftMargin = dp(6);
        mBtnQuickModeBadge.setLayoutParams(qBadgeLp);
        mBtnQuickModeBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setConsoleExpanded(!mConsoleExpanded);
            }
        });
        titleRow.addView(mBtnQuickModeBadge);

        miniHeader.addView(titleRow);

        // Active Auto-Routine Progress Banner (visible only when running a multi-stage course)
        mRoutineBannerRow = new LinearLayout(this);
        mRoutineBannerRow.setOrientation(LinearLayout.HORIZONTAL);
        mRoutineBannerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = dp(6);
        mRoutineBannerRow.setLayoutParams(rbLp);
        mRoutineBannerRow.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(8)));
        mRoutineBannerRow.setPadding(dp(9), dp(5), dp(7), dp(5));
        mRoutineBannerRow.setVisibility(View.GONE);

        mTvRoutineStatus = new TextView(this);
        mTvRoutineStatus.setText("");
        mTvRoutineStatus.setSingleLine(true);
        mTvRoutineStatus.setEllipsize(TextUtils.TruncateAt.END);
        mTvRoutineStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mTvRoutineStatus.setTextColor(COL_AMBER);
        applyTabularMono(mTvRoutineStatus, true);
        mTvRoutineStatus.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mRoutineBannerRow.addView(mTvRoutineStatus);

        mBtnRoutineNext = new TextView(this);
        mBtnRoutineNext.setText("다음 단계 ▶");
        mBtnRoutineNext.setSingleLine(true);
        mBtnRoutineNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mBtnRoutineNext.setTextColor(COL_TEXT_PRIMARY);
        mBtnRoutineNext.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnRoutineNext.setPadding(dp(8), dp(3), dp(8), dp(3));
        mBtnRoutineNext.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_AMBER, dp(1), dp(6)));
        LinearLayout.LayoutParams rnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rnLp.leftMargin = dp(6);
        mBtnRoutineNext.setLayoutParams(rnLp);
        mBtnRoutineNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                advanceRoutineFromUi();
            }
        });
        mRoutineBannerRow.addView(mBtnRoutineNext);
        miniHeader.addView(mRoutineBannerRow);

        card.addView(miniHeader);

        // --- Studio-Only Auto Waveform Section (hidden in Mini mode, directly below info in Studio mode) ---
        mScopeSection = new LinearLayout(this);
        mScopeSection.setOrientation(LinearLayout.VERTICAL);
        mScopeSection.setVisibility(View.GONE);

        View scopeDiv = new View(this);
        scopeDiv.setBackgroundColor(COL_OUTLINE);
        LinearLayout.LayoutParams scDivLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        scDivLp.topMargin = dp(8);
        scDivLp.bottomMargin = dp(6);
        scopeDiv.setLayoutParams(scDivLp);
        mScopeSection.addView(scopeDiv);

        LinearLayout scopeHeader = new LinearLayout(this);
        scopeHeader.setOrientation(LinearLayout.HORIZONTAL);
        scopeHeader.setGravity(Gravity.CENTER_VERTICAL);
        scopeHeader.setPadding(0, 0, 0, dp(5));

        mBtnScopeFund = createScopeToggleChip("기본 파동", true);
        scopeHeader.addView(mBtnScopeFund);

        mBtnScopeOut = createScopeToggleChip("실출력", false);
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outLp.leftMargin = dp(6);
        mBtnScopeOut.setLayoutParams(outLp);
        scopeHeader.addView(mBtnScopeOut);

        mTvScopeTelemetry = new TextView(this);
        mTvScopeTelemetry.setText("-96.0 dBFS · 클리핑 0");
        mTvScopeTelemetry.setGravity(Gravity.END);
        mTvScopeTelemetry.setSingleLine(true);
        mTvScopeTelemetry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        mTvScopeTelemetry.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvScopeTelemetry, true);
        mTvScopeTelemetry.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        scopeHeader.addView(mTvScopeTelemetry);
        mScopeSection.addView(scopeHeader);

        mWaveformView = new LiveWaveformView(this);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        waveLp.bottomMargin = dp(0);
        mWaveformView.setLayoutParams(waveLp);
        mScopeSection.addView(mWaveformView);
        refreshScopeToggleUi();
        card.addView(mScopeSection);

        // --- Single Unified Volume Section (directly below auto waveform animation) ---
        View volDivider = new View(this);
        volDivider.setBackgroundColor(COL_OUTLINE);
        LinearLayout.LayoutParams volDivLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        volDivLp.topMargin = dp(8);
        volDivLp.bottomMargin = dp(8);
        volDivider.setLayoutParams(volDivLp);
        card.addView(volDivider);

        card.addView(buildVolumeControlBlock());

        // --- Accordion Collapsible Studio Body (Collapsed by default) ---
        mConsoleDetailBody = new LinearLayout(this);
        mConsoleDetailBody.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(10);
        mConsoleDetailBody.setLayoutParams(detailLp);
        mConsoleDetailBody.setVisibility(View.GONE);

        View studioTopDiv = new View(this);
        studioTopDiv.setBackgroundColor(COL_OUTLINE);
        LinearLayout.LayoutParams stDivLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        stDivLp.bottomMargin = dp(10);
        studioTopDiv.setLayoutParams(stDivLp);
        mConsoleDetailBody.addView(studioTopDiv);

        mTvMechanismInfo = new TextView(this);
        mTvMechanismInfo.setText("위상 연속 무왜곡 출력 대기 · 타 오디오 앱과 동시 재생");
        mTvMechanismInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvMechanismInfo.setTextColor(COL_TEXT_SECONDARY);
        mTvMechanismInfo.setLineSpacing(dp(2), 1.2f);
        mTvMechanismInfo.setPadding(0, 0, 0, dp(10));
        mConsoleDetailBody.addView(mTvMechanismInfo);

        // Output Acoustic Mode Block
        mConsoleDetailBody.addView(buildOutputProfileBlock());

        // Ambient Soundscape Layer Mixer Block
        mConsoleDetailBody.addView(buildAmbientLayerBlock());

        // Compact Transport + Sleep Timer Row (WRAP_CONTENT + minHeight for 1.3x font safety)
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams transLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        transLp.bottomMargin = dp(12);
        transportRow.setLayoutParams(transLp);

        TextView btnPrev = createTransportChip("◀ 이전", "이전 프리셋 재생");
        btnPrev.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                triggerRelativePreset(-1);
            }
        });
        transportRow.addView(btnPrev);

        mBtnTimerChip = createTransportChip("타이머 꺼짐", "자동 정지 타이머 순환 설정");
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f);
        timerLp.leftMargin = dp(6);
        timerLp.rightMargin = dp(6);
        mBtnTimerChip.setLayoutParams(timerLp);
        mBtnTimerChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                cycleSleepTimer();
            }
        });
        transportRow.addView(mBtnTimerChip);

        TextView btnNext = createTransportChip("다음 ▶", "다음 프리셋 재생");
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                triggerRelativePreset(1);
            }
        });
        transportRow.addView(btnNext);
        mConsoleDetailBody.addView(transportRow);

        mBtnStop = new Button(this);
        mBtnStop.setText("대기 중 (프리셋 선택 시 재생)");
        mBtnStop.setAllCaps(false);
        mBtnStop.setTextColor(COL_TEXT_TERTIARY);
        mBtnStop.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        mBtnStop.setMinHeight(dp(44));
        mBtnStop.setPadding(dp(12), dp(8), dp(12), dp(8));
        mBtnStop.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopLp.topMargin = dp(0);
        mBtnStop.setLayoutParams(stopLp);
        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (mLastDisplayedRunning) {
                    stopAudio();
                } else {
                    resumeLastPreset();
                }
            }
        });
        mConsoleDetailBody.addView(mBtnStop);

        card.addView(mConsoleDetailBody);
        return card;
    }

    private View buildOutputProfileBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.bottomMargin = dp(10);
        block.setLayoutParams(blockLp);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, 0, 0, dp(5));

        TextView lbl = new TextView(this);
        lbl.setText("출력 음향 보정");
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        lbl.setTextColor(COL_TEXT_SECONDARY);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        hdr.addView(lbl);

        mTvDetectedDevice = new TextView(this);
        mTvDetectedDevice.setText("감지: 폰 스피커");
        mTvDetectedDevice.setSingleLine(true);
        mTvDetectedDevice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mTvDetectedDevice.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvDetectedDevice, false);
        hdr.addView(mTvDetectedDevice);
        block.addView(hdr);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mProfileChips.clear();
        for (int i = 0; i < PROFILE_LABELS.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setText(PROFILE_LABELS[i]);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setMinHeight(dp(34));
            chip.setPadding(dp(4), dp(6), dp(4), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < PROFILE_LABELS.length - 1) lp.rightMargin = dp(5);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    applyOutputProfileFromUi(mode);
                }
            });
            mProfileChips.add(chip);
            row.addView(chip);
        }
        block.addView(row);
        return block;
    }

    private View buildAmbientLayerBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.bottomMargin = dp(12);
        block.setLayoutParams(blockLp);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, 0, 0, dp(5));

        TextView lbl = new TextView(this);
        lbl.setText("배경 사운드 블렌드");
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        lbl.setTextColor(COL_TEXT_SECONDARY);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        hdr.addView(lbl);

        mTvAmbientReadout = new TextView(this);
        mTvAmbientReadout.setText("꺼짐");
        mTvAmbientReadout.setSingleLine(true);
        mTvAmbientReadout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mTvAmbientReadout.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvAmbientReadout, true);
        hdr.addView(mTvAmbientReadout);
        block.addView(hdr);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mAmbientChips.clear();
        for (int i = 0; i < AMBIENT_LABELS.length; i++) {
            final int ambType = i;
            TextView chip = new TextView(this);
            chip.setText(AMBIENT_LABELS[i]);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setMinHeight(dp(34));
            chip.setPadding(dp(4), dp(6), dp(4), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < AMBIENT_LABELS.length - 1) lp.rightMargin = dp(5);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    int nextMix = (ambType != AegisDspCore.AMBIENT_OFF && mAmbientMixPercent <= 0) ? 25 : mAmbientMixPercent;
                    applyAmbientLayerFromUi(ambType, nextMix);
                }
            });
            mAmbientChips.add(chip);
            row.addView(chip);
        }
        block.addView(row);

        mAmbientMixRow = new LinearLayout(this);
        mAmbientMixRow.setOrientation(LinearLayout.HORIZONTAL);
        mAmbientMixRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mixLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mixLp.topMargin = dp(6);
        mAmbientMixRow.setLayoutParams(mixLp);

        TextView mixLbl = new TextView(this);
        mixLbl.setText("블렌드 강도");
        mixLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mixLbl.setTextColor(COL_TEXT_SECONDARY);
        mixLbl.setPadding(0, 0, dp(8), 0);
        mAmbientMixRow.addView(mixLbl);

        mSeekAmbientMix = new SeekBar(this);
        mSeekAmbientMix.setContentDescription("배경 사운드 블렌드 비율");
        mSeekAmbientMix.setMax(AegisDspCore.AMBIENT_MAX_MIX_PCT);
        mSeekAmbientMix.setProgress(mAmbientMixPercent);
        mSeekAmbientMix.setSplitTrack(false);
        mSeekAmbientMix.setProgressDrawable(getResources().getDrawable(R.drawable.seekbar_track));
        mSeekAmbientMix.setThumb(getResources().getDrawable(R.drawable.seekbar_thumb));
        mSeekAmbientMix.setPadding(dp(8), dp(6), dp(8), dp(6));
        mSeekAmbientMix.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mSeekAmbientMix.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mSuppressSeekCallback) return;
                applyAmbientLayerFromUi(mAmbientType, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mAmbientMixRow.addView(mSeekAmbientMix);
        mAmbientMixRow.setVisibility(mAmbientType == AegisDspCore.AMBIENT_OFF ? View.GONE : View.VISIBLE);
        block.addView(mAmbientMixRow);

        return block;
    }

    private View buildRoutineStrip() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(10);
        box.setLayoutParams(boxLp);

        TextView hdr = new TextView(this);
        hdr.setText("원탭 자동 코스 · 준비→본활동→쿨다운→회복");
        hdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hdr.setTextColor(COL_AMBER);
        hdr.setTypeface(Typeface.DEFAULT_BOLD);
        hdr.setPadding(dp(2), 0, dp(2), dp(5));
        box.addView(hdr);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        mRoutineCoursesRow = new LinearLayout(this);
        mRoutineCoursesRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(mRoutineCoursesRow);
        box.addView(hsv);

        refreshRoutineCoursesRow();
        return box;
    }

    private void refreshRoutineCoursesRow() {
        if (mRoutineCoursesRow == null) return;
        String activeId = (mIsBound && mService != null && mService.getActiveRoutine() != null && mLastDisplayedRunning)
                ? mService.getActiveRoutine().id : "";
        mLastActiveRoutineId = activeId;

        mRoutineCoursesRow.removeAllViews();
        mRoutineCourseCards.clear();
        mRoutineCourseIds.clear();

        List<JourneyOrderPolicy.RoutineCourse> courses = JourneyOrderPolicy.getAllRoutines();
        for (int i = 0; i < courses.size(); i++) {
            final JourneyOrderPolicy.RoutineCourse c = courses.get(i);
            boolean active = c.id.equals(activeId);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(11), dp(8), dp(11), dp(8));
            card.setBackground(createBoxDrawable(
                    active ? COL_SURFACE_HIGH : COL_SURFACE,
                    active ? COL_ACTIVE : COL_OUTLINE,
                    active ? dp(2) : dp(1), dp(10)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < courses.size() - 1) lp.rightMargin = dp(7);
            card.setLayoutParams(lp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(c.title);
            tvTitle.setSingleLine(true);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tvTitle.setTextColor(active ? COL_ACTIVE : COL_TEXT_PRIMARY);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvTitle);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(c.badge);
            tvBadge.setSingleLine(true);
            tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            tvBadge.setTextColor(active ? COL_TEXT_PRIMARY : COL_TEXT_SECONDARY);
            applyTabularMono(tvBadge, false);
            tvBadge.setPadding(0, dp(2), 0, 0);
            card.addView(tvBadge);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    startRoutineCourse(c);
                }
            });
            mRoutineCourseCards.add(card);
            mRoutineCourseIds.add(c.id);
            mRoutineCoursesRow.addView(card);
        }
    }

    private void startRoutineCourse(JourneyOrderPolicy.RoutineCourse course) {
        if (course == null || course.steps.length == 0) return;
        if (mIsBound && mService != null) {
            mService.startRoutine(course.id);
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            syncVolumeUi();
        } else {
            Intent intent = new Intent(this, AegisAudioService.class);
            intent.setAction(AegisAudioService.ACTION_START_ROUTINE);
            intent.putExtra(AegisAudioService.EXTRA_ROUTINE_ID, course.id);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        PresetModel first = JourneyPackCatalog.findAnyById(course.steps[0].presetId);
        mActivePreset = first;
        if (!mSearchQuery.trim().isEmpty()) {
            clearSearchAndHideKeyboard();
        }
        if (mCurrentFilterMode != FILTER_ALL) {
            setFilterMode(FILTER_ALL);
        }
        selectTab(course.tabIndex, true, false);
        updateDashboard(first, true, first.freq1 > 0f ? first.freq1 : first.sweepStart);
        updateRoutineBannerUi();
        refreshRoutineCoursesRow();
    }

    private void advanceRoutineFromUi() {
        if (mIsBound && mService != null) {
            mService.advanceRoutineStep();
            syncFromService(false);
        }
    }

    private void updateRoutineBannerUi() {
        if (mRoutineBannerRow == null || mTvRoutineStatus == null) return;
        JourneyOrderPolicy.RoutineCourse rc = (mIsBound && mService != null && mLastDisplayedRunning)
                ? mService.getActiveRoutine() : null;
        if (rc == null) {
            if (mRoutineBannerRow.getVisibility() != View.GONE) {
                mRoutineBannerRow.setVisibility(View.GONE);
            }
            if (!mLastActiveRoutineId.isEmpty()) {
                refreshRoutineCoursesRow();
            }
            return;
        }
        if (mRoutineBannerRow.getVisibility() != View.VISIBLE) {
            mRoutineBannerRow.setVisibility(View.VISIBLE);
        }
        int stepIdx = Math.max(0, Math.min(rc.steps.length - 1, mService.getRoutineStepIndex()));
        JourneyOrderPolicy.RoutineStep step = rc.steps[stepIdx];
        int remSec = mService.getRoutineStepRemainingSec();
        String text = String.format(Locale.US, "코스 %d/%d · %s (%02d:%02d)",
                stepIdx + 1, rc.steps.length, step.stageLabel, remSec / 60, remSec % 60);
        if (!text.equals(mLastRoutineText)) {
            mLastRoutineText = text;
            mTvRoutineStatus.setText(text);
        }
        if (mBtnRoutineNext != null) {
            mBtnRoutineNext.setText(stepIdx >= rc.steps.length - 1 ? "코스 종료" : "다음 단계 ▶");
        }
        if (!rc.id.equals(mLastActiveRoutineId)) {
            refreshRoutineCoursesRow();
        }
    }

    private View buildRecommendationStrip() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(10);
        box.setLayoutParams(boxLp);

        mTvRecHeader = new TextView(this);
        mTvRecHeader.setText("지금 추천 · 원탭 시작");
        mTvRecHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvRecHeader.setTextColor(COL_AMBER);
        mTvRecHeader.setTypeface(Typeface.DEFAULT_BOLD);
        mTvRecHeader.setPadding(dp(2), 0, dp(2), dp(5));
        box.addView(mTvRecHeader);

        mRecCardsRow = new LinearLayout(this);
        mRecCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(mRecCardsRow);
        return box;
    }

    private void refreshRecommendationsRow() {
        if (mRecCardsRow == null || mTvRecHeader == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        mTvRecHeader.setText(PresetAdvisor.getTimeWindowLabel(hour));
        int[] ids = PresetAdvisor.getTimeOfDayRecommendations(hour);

        mRecCardsRow.removeAllViews();
        mRecCardViews.clear();
        mRecPresetIds.clear();

        int playingId = (mLastDisplayedRunning && mActivePreset != null) ? mActivePreset.id : -1;
        for (int i = 0; i < ids.length; i++) {
            final PresetModel p = JourneyPackCatalog.findAnyById(ids[i]);
            boolean active = (p.id == playingId);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(9), dp(8), dp(9), dp(8));
            card.setBackground(createBoxDrawable(
                    active ? COL_SURFACE_HIGH : COL_SURFACE,
                    active ? COL_ACTIVE : COL_OUTLINE,
                    active ? dp(2) : dp(1), dp(10)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < ids.length - 1) lp.rightMargin = dp(6);
            card.setLayoutParams(lp);

            TextView tvCode = new TextView(this);
            tvCode.setText(JourneyPackCatalog.getShortCode(p) + " · " + getTabShortName(p.tabIndex));
            tvCode.setSingleLine(true);
            tvCode.setEllipsize(TextUtils.TruncateAt.END);
            tvCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            tvCode.setTextColor(active ? COL_ACTIVE : COL_AMBER);
            applyTabularMono(tvCode, true);
            card.addView(tvCode);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(p.title);
            tvTitle.setMinLines(2);
            tvTitle.setMaxLines(2);
            tvTitle.setEllipsize(TextUtils.TruncateAt.END);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvTitle.setTextColor(COL_TEXT_PRIMARY);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setPadding(0, dp(2), 0, 0);
            card.addView(tvTitle);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    playPreset(p);
                }
            });

            mRecCardViews.add(card);
            mRecPresetIds.add(p.id);
            mRecCardsRow.addView(card);
        }
    }

    private View buildSearchBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(11)));
        bar.setPadding(dp(10), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.bottomMargin = dp(8);
        bar.setLayoutParams(barLp);

        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_search);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        ivLp.rightMargin = dp(8);
        iv.setLayoutParams(ivLp);
        bar.addView(iv);

        mEtSearch = new EditText(this);
        mEtSearch.setHint("상황·증상·단어 검색 (예: 포모도로, 잠, 헬스, 두통)");
        mEtSearch.setHintTextColor(COL_TEXT_TERTIARY);
        mEtSearch.setTextColor(COL_TEXT_PRIMARY);
        mEtSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mEtSearch.setSingleLine(true);
        mEtSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mEtSearch.setBackgroundColor(Color.TRANSPARENT);
        mEtSearch.setPadding(0, dp(6), dp(6), dp(6));
        mEtSearch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String next = s != null ? s.toString() : "";
                if (!next.equals(mSearchQuery)) {
                    mSearchQuery = next;
                    if (mBtnClearSearch != null) {
                        mBtnClearSearch.setVisibility(mSearchQuery.trim().isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    renderPresetCards();
                }
            }
        });
        bar.addView(mEtSearch);

        mBtnClearSearch = new TextView(this);
        mBtnClearSearch.setText("지우기");
        mBtnClearSearch.setSingleLine(true);
        mBtnClearSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mBtnClearSearch.setTextColor(COL_AMBER);
        mBtnClearSearch.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnClearSearch.setPadding(dp(8), dp(5), dp(8), dp(5));
        mBtnClearSearch.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(7)));
        mBtnClearSearch.setVisibility(View.GONE);
        mBtnClearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                clearSearchAndHideKeyboard();
            }
        });
        bar.addView(mBtnClearSearch);

        return bar;
    }

    private void clearSearchAndHideKeyboard() {
        if (mEtSearch != null) {
            mEtSearch.setText("");
            mEtSearch.clearFocus();
        }
        if (mRootLayout != null) {
            mRootLayout.requestFocus();
        }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && mEtSearch != null) {
                imm.hideSoftInputFromWindow(mEtSearch.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {}
    }

    private void setConsoleExpanded(boolean expanded) {
        mConsoleExpanded = expanded;
        if (mScopeSection != null) {
            mScopeSection.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (mConsoleDetailBody != null) {
            mConsoleDetailBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (mBtnAccordionToggle != null) {
            mBtnAccordionToggle.setText(expanded ? "접기 ▲" : "스튜디오 ▼");
        }
        if (mTvHeroFreq != null) {
            mTvHeroFreq.setTextSize(TypedValue.COMPLEX_UNIT_SP, expanded ? 25 : 21);
        }
        if (mTvScenarioTitle != null) {
            if (expanded) {
                mTvScenarioTitle.setSingleLine(false);
                mTvScenarioTitle.setEllipsize(null);
            } else {
                mTvScenarioTitle.setSingleLine(true);
                mTvScenarioTitle.setEllipsize(TextUtils.TruncateAt.END);
            }
        }
        if (mWaveformView != null) {
            mWaveformView.setActive(expanded && mLastDisplayedRunning);
            if (expanded && mLastDisplayedRunning) {
                mWaveformView.pokeIfActive();
            }
        }
    }

    private void updateVuMeter(float rmsDbfs, boolean running) {
        int litCount = 0;
        if (running && !mIsMuted && mVolumePercent > 0) {
            float norm = (rmsDbfs + 48.0f) / 47.5f;
            if (norm < 0.0f) norm = 0.0f;
            if (norm > 1.0f) norm = 1.0f;
            if (norm > 0.0f && norm < 0.10f) norm = 0.10f;
            mVuSmoothLevel = (mVuSmoothLevel * 0.45f) + (norm * 0.55f);
            litCount = Math.round(mVuSmoothLevel * 12.0f);
        } else {
            mVuSmoothLevel = 0.0f;
        }
        if (litCount == mLastVuLitCount) {
            return;
        }
        mLastVuLitCount = litCount;
        for (int i = 0; i < 12; i++) {
            GradientDrawable gd = mVuBarDrawables[i];
            if (gd == null) continue;
            if (i < litCount) {
                if (i >= 11) gd.setColor(COL_STOPPED);
                else if (i >= 8) gd.setColor(COL_AMBER);
                else gd.setColor(COL_ACTIVE);
            } else {
                gd.setColor(COL_VU_OFF);
            }
        }
    }

    private TextView createScopeToggleChip(String label, final boolean fundamentalMode) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (mWaveformView != null) {
                    mWaveformView.setFundamentalMode(fundamentalMode);
                    refreshScopeToggleUi();
                }
            }
        });
        return tv;
    }

    private void refreshScopeToggleUi() {
        boolean fund = (mWaveformView == null) || mWaveformView.isFundamentalMode();
        if (mBtnScopeFund != null) {
            mBtnScopeFund.setBackground(createBoxDrawable(
                    fund ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    fund ? COL_AMBER : COL_OUTLINE, dp(1), dp(6)));
            mBtnScopeFund.setTextColor(fund ? COL_AMBER : COL_TEXT_SECONDARY);
        }
        if (mBtnScopeOut != null) {
            mBtnScopeOut.setBackground(createBoxDrawable(
                    !fund ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    !fund ? COL_AMBER : COL_OUTLINE, dp(1), dp(6)));
            mBtnScopeOut.setTextColor(!fund ? COL_AMBER : COL_TEXT_SECONDARY);
        }
    }

    private TextView createTransportChip(String label, String contentDesc) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setContentDescription(contentDesc);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(COL_TEXT_PRIMARY);
        tv.setMinHeight(dp(38));
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        applyTabularMono(tv, true);
        tv.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(8)));
        return tv;
    }

    private void triggerRelativePreset(int delta) {
        if (mIsBound && mService != null) {
            mService.playRelativePreset(delta);
            PresetModel cur = mService.getCurrentPreset();
            if (cur != null) {
                boolean isPro = ProPackCatalog.isProPreset(cur.id);
                boolean isJrn = JourneyPackCatalog.isJourneyPreset(cur.id);
                if ((isPro && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_JOURNEY))
                        || (isJrn && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_PRO))
                        || (!isPro && !isJrn && (mCurrentFilterMode == FILTER_PRO || mCurrentFilterMode == FILTER_JOURNEY))) {
                    setFilterMode(FILTER_ALL);
                }
                if (mCurrentSelectedTab != cur.tabIndex) {
                    selectTab(cur.tabIndex, true, false);
                }
                mVolumePercent = mService.getVolumePercent();
                mIsMuted = mService.isMuted();
                syncVolumeUi();
                updateDashboard(cur, true, cur.freq1 > 0f ? cur.freq1 : cur.sweepStart);
            }
            return;
        }
        int curId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(AegisAudioService.PREF_LAST_PRESET_ID, 1);
        int nextId = JourneyOrderPolicy.getNextOrderedPresetId(curId, delta);
        playPreset(JourneyPackCatalog.findAnyById(nextId));
    }

    private void resumeLastPreset() {
        int lastId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(AegisAudioService.PREF_LAST_PRESET_ID, 1);
        PresetModel target = JourneyPackCatalog.findAnyById(lastId);
        playPreset(target);
    }

    private void cycleSleepTimer() {
        int current = (mIsBound && mService != null) ? mService.getSleepTimerMinutes() : 0;
        int next = TIMER_STEPS[1];
        for (int i = 0; i < TIMER_STEPS.length; i++) {
            if (TIMER_STEPS[i] == current) {
                next = TIMER_STEPS[(i + 1) % TIMER_STEPS.length];
                break;
            }
        }
        if (mIsBound && mService != null) {
            mService.setSleepTimerMinutes(next);
        }
        updateTimerChipUi();
    }

    private void updateTimerChipUi() {
        if (mBtnTimerChip == null) return;
        boolean active = mIsBound && mService != null && mService.getSleepTimerMinutes() > 0;
        String label;
        if (!active) {
            label = "타이머 꺼짐";
        } else {
            int remSec = mService.getSleepRemainingSec();
            if (remSec > 0) {
                int mins = remSec / 60;
                int secs = remSec % 60;
                label = String.format(Locale.US, "타이머 %02d:%02d", mins, secs);
            } else {
                label = mService.getSleepTimerMinutes() + "분 예약";
            }
        }
        if (!label.equals(mLastTimerLabel) || active != mLastTimerActive) {
            mLastTimerLabel = label;
            mLastTimerActive = active;
            mBtnTimerChip.setText(label);
            mBtnTimerChip.setTextColor(active ? COL_AMBER : COL_TEXT_SECONDARY);
            mBtnTimerChip.setBackground(createBoxDrawable(
                    active ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    active ? COL_AMBER : COL_OUTLINE, dp(1), dp(8)));
        }
        updateQuickModeBadge(active, label);
    }

    private void updateQuickModeBadge(boolean timerActive, String timerLabel) {
        if (mBtnQuickModeBadge == null) return;
        String text;
        if (timerActive) {
            text = timerLabel;
        } else {
            String pName = PROFILE_LABELS[Math.max(0, Math.min(3, mOutputProfileMode))];
            String dev = getDetectedDeviceShort();
            text = (mOutputProfileMode == AegisDspCore.PROFILE_AUTO)
                    ? ("자동·" + dev)
                    : pName;
        }
        if (!text.equals(mLastQuickBadge)) {
            mLastQuickBadge = text;
            mBtnQuickModeBadge.setText(text);
            mBtnQuickModeBadge.setTextColor(timerActive ? COL_AMBER : COL_TEXT_SECONDARY);
            mBtnQuickModeBadge.setBackground(createBoxDrawable(
                    timerActive ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    timerActive ? COL_AMBER : COL_OUTLINE, dp(1), dp(7)));
        }
    }

    private String getDetectedDeviceShort() {
        if (mIsBound && mService != null) {
            String full = mService.getOutputDeviceLabel();
            if ("블루투스".equals(full)) return "블루투스";
            if ("유선 이어폰".equals(full)) return "이어폰";
            if ("USB 오디오".equals(full)) return "USB";
            return "스피커";
        }
        return "스피커";
    }

    private View buildVolumeControlBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

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
        mTvVolumeReadout.setSingleLine(true);
        mTvVolumeReadout.setEllipsize(TextUtils.TruncateAt.END);
        mTvVolumeReadout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mTvVolumeReadout.setTextColor(COL_AMBER);
        applyTabularMono(mTvVolumeReadout, true);
        mTvVolumeReadout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        headerRow.addView(mTvVolumeReadout);

        mTvSystemVolume = new TextView(this);
        mTvSystemVolume.setText("시스템 -/-");
        mTvSystemVolume.setSingleLine(true);
        mTvSystemVolume.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvSystemVolume.setTextColor(COL_TEXT_TERTIARY);
        applyTabularMono(mTvSystemVolume, false);
        headerRow.addView(mTvSystemVolume);
        block.addView(headerRow);

        LinearLayout faderRow = new LinearLayout(this);
        faderRow.setOrientation(LinearLayout.HORIZONTAL);
        faderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams faderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
                toggleMuteAction();
            }
        });
        faderRow.addView(mBtnMute);

        TextView btnMinus = createStepperButton("−", "볼륨 5% 감소", -5);
        LinearLayout.LayoutParams minusLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        minusLp.rightMargin = dp(4);
        btnMinus.setLayoutParams(minusLp);
        faderRow.addView(btnMinus);

        mSeekVolume = new SeekBar(this);
        mSeekVolume.setContentDescription("독립 출력 볼륨 슬라이더");
        mSeekVolume.setMax(VolumeCurve.MAX_PERCENT);
        mSeekVolume.setProgress(mVolumePercent);
        mSeekVolume.setSplitTrack(false);
        mSeekVolume.setProgressDrawable(getResources().getDrawable(R.drawable.seekbar_track));
        mSeekVolume.setThumb(getResources().getDrawable(R.drawable.seekbar_thumb));
        mSeekVolume.setPadding(dp(8), dp(10), dp(8), dp(10));
        mSeekVolume.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mSeekVolume.setOnSeekBarChangeListener(createVolumeSeekListener());
        faderRow.addView(mSeekVolume);

        TextView btnPlus = createStepperButton("+", "볼륨 5% 증가", 5);
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        plusLp.leftMargin = dp(4);
        btnPlus.setLayoutParams(plusLp);
        faderRow.addView(btnPlus);

        block.addView(faderRow);

        // 2-line stacked zero-clipping detent chips for 360dp / 1.3x font scale
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
            chip.setSingleLine(false);
            chip.setMaxLines(2);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(2), dp(4), dp(2), dp(4));
            applyTabularMono(chip, true);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i < DETENT_VALUES.length - 1) {
                chipLp.rightMargin = dp(4);
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

        mBtnRecommendedVol = new TextView(this);
        mBtnRecommendedVol.setText("이 프리셋 추천 볼륨 적용");
        mBtnRecommendedVol.setSingleLine(true);
        mBtnRecommendedVol.setGravity(Gravity.CENTER);
        mBtnRecommendedVol.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnRecommendedVol.setTextColor(COL_AMBER);
        mBtnRecommendedVol.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnRecommendedVol.setPadding(dp(10), dp(6), dp(10), dp(6));
        mBtnRecommendedVol.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(8)));
        LinearLayout.LayoutParams recVolLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recVolLp.topMargin = dp(6);
        mBtnRecommendedVol.setLayoutParams(recVolLp);
        mBtnRecommendedVol.setVisibility(View.GONE);
        mBtnRecommendedVol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (mActivePreset != null) {
                    applyVolumeFromUi(PresetAdvisor.getRecommendedVolumePercent(mActivePreset), false);
                }
            }
        });
        block.addView(mBtnRecommendedVol);

        return block;
    }

    private SeekBar.OnSeekBarChangeListener createVolumeSeekListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mSuppressSeekCallback) return;
                boolean nextMuted = (progress == 0);
                if (progress > 0 && mIsMuted) {
                    nextMuted = false;
                }
                int nearestDetent = (progress == 15 || progress == 45 || progress == 75
                        || progress == 100 || progress == 120) ? progress : -1;
                if (nearestDetent > 0 && nearestDetent != mLastHapticDetent) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mLastHapticDetent = nearestDetent;
                } else if (nearestDetent < 0) {
                    mLastHapticDetent = -1;
                }
                applyVolumeFromUi(progress, nextMuted);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void toggleMuteAction() {
        if (mIsMuted && mVolumePercent == 0) {
            applyVolumeFromUi(VolumeCurve.DEFAULT_PERCENT, false);
        } else {
            applyVolumeFromUi(mVolumePercent, !mIsMuted);
        }
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

    private View buildFilterChipRow() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.bottomMargin = dp(8);
        hsv.setLayoutParams(hsvLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        mFilterChips.clear();
        String[] labels = new String[]{"전체 88", "코어 48", "Pro 16", "여정 24", "즐겨찾기 0", "최근 0"};
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chip.setMinHeight(dp(34));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            applyTabularMono(chip, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < labels.length - 1) {
                lp.rightMargin = dp(6);
            }
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setFilterMode(mode);
                }
            });
            mFilterChips.add(chip);
            row.addView(chip);
        }
        hsv.addView(row);
        return hsv;
    }

    private void setFilterMode(int mode) {
        mCurrentFilterMode = clampFilterMode(mode);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_FILTER_MODE, mCurrentFilterMode)
                .apply();
        refreshFilterChipsUi();
        renderPresetCards();
        updateJumpCue();
    }

    private void refreshFilterChipsUi() {
        int recentCount = loadRecentIds().size();
        String[] labels = new String[]{
                "전체 88",
                "코어 48",
                "Pro 16",
                "여정 24",
                "즐겨찾기 " + mFavoriteIds.size(),
                "최근 " + recentCount
        };
        for (int i = 0; i < mFilterChips.size(); i++) {
            TextView chip = mFilterChips.get(i);
            chip.setText(labels[i]);
            boolean active = (i == mCurrentFilterMode);
            if (active) {
                chip.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(8)));
                chip.setTextColor(COL_AMBER);
            } else {
                chip.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(8)));
                chip.setTextColor(COL_TEXT_SECONDARY);
            }
        }
    }

    private View buildCategoryTabs() {
        HorizontalScrollView tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams tabScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabScrollLp.bottomMargin = dp(10);
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
            tabBtn.setSingleLine(true);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
            tabBtn.setMinimumWidth(0);
            tabBtn.setMinWidth(0);
            tabBtn.setMinimumHeight(dp(38));
            tabBtn.setMinHeight(dp(38));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.rightMargin = dp(8);
            tabBtn.setLayoutParams(btnLp);
            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (!mSearchQuery.trim().isEmpty()) {
                        clearSearchAndHideKeyboard();
                    }
                    if (mCurrentFilterMode == FILTER_FAV || mCurrentFilterMode == FILTER_PRO || mCurrentFilterMode == FILTER_RECENT) {
                        setFilterMode(FILTER_ALL);
                    }
                    selectTab(tabIdx, true, true);
                }
            });
            mTabButtons.add(tabBtn);
            tabLayout.addView(tabBtn);
        }

        tabScrollView.addView(tabLayout);
        return tabScrollView;
    }

    private void applyOutputProfileFromUi(int mode) {
        mOutputProfileMode = mode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(AegisAudioService.PREF_OUTPUT_PROFILE, mode).apply();
        if (mIsBound && mService != null) {
            mService.setOutputProfileMode(mode);
        }
        refreshProfileAndAmbientUi();
        if (mActivePreset != null && mLastDisplayedRunning) {
            boolean guarded = mIsBound && mService != null && mService.isUltrasonicGuarded();
            updateMechanismText(mActivePreset, guarded);
        }
    }

    private void applyAmbientLayerFromUi(int type, int mixPct) {
        mAmbientType = type;
        mAmbientMixPercent = mixPct;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(AegisAudioService.PREF_AMBIENT_TYPE, type)
                .putInt(AegisAudioService.PREF_AMBIENT_MIX, mixPct)
                .apply();
        if (mIsBound && mService != null) {
            mService.setAmbientLayer(type, mixPct);
        }
        refreshProfileAndAmbientUi();
    }

    private void refreshProfileAndAmbientUi() {
        for (int i = 0; i < mProfileChips.size(); i++) {
            TextView chip = mProfileChips.get(i);
            boolean active = (i == mOutputProfileMode);
            chip.setBackground(createBoxDrawable(
                    active ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    active ? COL_AMBER : COL_OUTLINE, dp(1), dp(8)));
            chip.setTextColor(active ? COL_AMBER : COL_TEXT_SECONDARY);
        }

        for (int i = 0; i < mAmbientChips.size(); i++) {
            TextView chip = mAmbientChips.get(i);
            boolean active = (i == mAmbientType);
            chip.setBackground(createBoxDrawable(
                    active ? COL_AMBER_BG : COL_SURFACE_HIGH,
                    active ? COL_AMBER : COL_OUTLINE, dp(1), dp(8)));
            chip.setTextColor(active ? COL_AMBER : COL_TEXT_SECONDARY);
        }

        if (mTvAmbientReadout != null) {
            if (mAmbientType == AegisDspCore.AMBIENT_OFF) {
                mTvAmbientReadout.setText("꺼짐");
                mTvAmbientReadout.setTextColor(COL_TEXT_TERTIARY);
            } else {
                String name = AMBIENT_LABELS[Math.max(0, Math.min(3, mAmbientType))];
                mTvAmbientReadout.setText(name + " · " + mAmbientMixPercent + "%");
                mTvAmbientReadout.setTextColor(COL_AMBER);
            }
        }
        if (mAmbientMixRow != null) {
            mAmbientMixRow.setVisibility(mAmbientType == AegisDspCore.AMBIENT_OFF ? View.GONE : View.VISIBLE);
        }
        if (mSeekAmbientMix != null) {
            mSuppressSeekCallback = true;
            mSeekAmbientMix.setProgress(mAmbientMixPercent);
            mSuppressSeekCallback = false;
        }
        if (mTvDetectedDevice != null) {
            String dev = "감지: " + (mIsBound && mService != null ? mService.getOutputDeviceLabel() : "폰 스피커");
            if (!dev.equals(mLastDeviceLabel)) {
                mLastDeviceLabel = dev;
                mTvDetectedDevice.setText(dev);
            }
        }
        updateQuickModeBadge(mLastTimerActive, mLastTimerLabel);
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
        int sliderVal = mIsMuted ? 0 : mVolumePercent;
        mSuppressSeekCallback = true;
        if (mSeekVolume != null) {
            mSeekVolume.setProgress(sliderVal);
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

        int muteIconRes = (mIsMuted || mVolumePercent == 0) ? R.drawable.ic_volume_mute : R.drawable.ic_volume_on;
        if (mBtnMute != null) {
            mBtnMute.setImageResource(muteIconRes);
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

        updateRecommendedVolumeChip();
        updateSystemVolumeReadout();
    }

    private void updateRecommendedVolumeChip() {
        if (mBtnRecommendedVol == null) return;
        if (mLastDisplayedRunning && mActivePreset != null) {
            int recPct = PresetAdvisor.getRecommendedVolumePercent(mActivePreset);
            mBtnRecommendedVol.setVisibility(View.VISIBLE);
            boolean matched = (!mIsMuted && mVolumePercent == recPct);
            mBtnRecommendedVol.setText(matched
                    ? ("추천 볼륨 적용됨 (" + recPct + "%)")
                    : ("현재 프리셋 추천 볼륨으로 맞추기 (" + recPct + "%)"));
            mBtnRecommendedVol.setTextColor(matched ? COL_ACTIVE : COL_AMBER);
            mBtnRecommendedVol.setBackground(createBoxDrawable(
                    matched ? COL_SURFACE_HIGH : COL_AMBER_BG,
                    matched ? COL_ACTIVE : COL_AMBER, dp(1), dp(8)));
        } else {
            mBtnRecommendedVol.setVisibility(View.GONE);
        }
    }

    private void updateSystemVolumeReadout() {
        if (mTvSystemVolume == null || mAudioManager == null) return;
        try {
            int cur = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (cur != mLastSysVol || max != mLastSysMax) {
                mLastSysVol = cur;
                mLastSysMax = max;
                mTvSystemVolume.setText(String.format(Locale.US, "시스템 %d/%d", cur, max));
            }
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

    private int countForTabInFilter(int tabIndex) {
        int count = 0;
        List<PresetModel> all = JourneyPackCatalog.getAllPresets88();
        for (int i = 0; i < all.size(); i++) {
            PresetModel p = all.get(i);
            if (p.tabIndex != tabIndex) continue;
            if (mCurrentFilterMode == FILTER_CORE && (ProPackCatalog.isProPreset(p.id) || JourneyPackCatalog.isJourneyPreset(p.id))) continue;
            if (mCurrentFilterMode == FILTER_PRO && !ProPackCatalog.isProPreset(p.id)) continue;
            if (mCurrentFilterMode == FILTER_JOURNEY && !JourneyPackCatalog.isJourneyPreset(p.id)) continue;
            count++;
        }
        return count;
    }

    private void refreshTabStyles() {
        boolean searchActive = !mSearchQuery.trim().isEmpty();
        int playingTab = (mLastDisplayedRunning && mActivePreset != null) ? mActivePreset.tabIndex : -1;
        for (int i = 0; i < mTabButtons.size(); i++) {
            Button btn = mTabButtons.get(i);
            boolean isSelected = !searchActive
                    && (mCurrentFilterMode != FILTER_FAV && mCurrentFilterMode != FILTER_PRO && mCurrentFilterMode != FILTER_RECENT)
                    && (i == mCurrentSelectedTab);
            boolean isPlayingOwner = (i == playingTab);
            String labelBase = TAB_NAMES[i] + " " + countForTabInFilter(i);

            if (isPlayingOwner) {
                SpannableString label = new SpannableString("\u25cf " + labelBase);
                label.setSpan(
                        new ForegroundColorSpan(isSelected ? COL_ACTIVE_ON_LIGHT : COL_ACTIVE),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                btn.setText(label);
            } else {
                btn.setText(labelBase);
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
        boolean diffContext = !mSearchQuery.trim().isEmpty()
                || (mCurrentFilterMode == FILTER_FAV && !mFavoriteIds.contains(mActivePreset.id))
                || (mCurrentFilterMode == FILTER_PRO && !ProPackCatalog.isProPreset(mActivePreset.id))
                || (mCurrentFilterMode == FILTER_JOURNEY && !JourneyPackCatalog.isJourneyPreset(mActivePreset.id))
                || (mCurrentFilterMode == FILTER_CORE && (ProPackCatalog.isProPreset(mActivePreset.id) || JourneyPackCatalog.isJourneyPreset(mActivePreset.id)))
                || (mCurrentFilterMode == FILTER_RECENT)
                || TabSyncPolicy.isViewingDifferentTab(true, mActivePreset.tabIndex, mCurrentSelectedTab);
        if (diffContext) {
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
        if (!mSearchQuery.trim().isEmpty()) {
            clearSearchAndHideKeyboard();
        }
        boolean isPro = ProPackCatalog.isProPreset(mActivePreset.id);
        boolean isJrn = JourneyPackCatalog.isJourneyPreset(mActivePreset.id);
        if ((isPro && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_JOURNEY))
                || (isJrn && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_PRO))
                || (!isPro && !isJrn && (mCurrentFilterMode == FILTER_PRO || mCurrentFilterMode == FILTER_JOURNEY))
                || (mCurrentFilterMode == FILTER_FAV && !mFavoriteIds.contains(mActivePreset.id))
                || mCurrentFilterMode == FILTER_RECENT) {
            setFilterMode(FILTER_ALL);
        }
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

        String q = mSearchQuery != null ? mSearchQuery.trim() : "";
        boolean searchActive = !q.isEmpty();

        if (!searchActive && mCurrentFilterMode != FILTER_FAV && mCurrentFilterMode != FILTER_RECENT) {
            if (mTvSearchStatus != null) {
                mTvSearchStatus.setVisibility(View.GONE);
            }
            JourneyOrderPolicy.StageGroup[] stages = JourneyOrderPolicy.getStagesForTab(mCurrentSelectedTab);
            boolean showCatPrefix = (mCurrentFilterMode == FILTER_PRO || mCurrentFilterMode == FILTER_JOURNEY);
            int totalAdded = 0;
            for (int s = 0; s < stages.length; s++) {
                JourneyOrderPolicy.StageGroup stage = stages[s];
                List<PresetModel> stageCards = new ArrayList<>();
                for (int k = 0; k < stage.presetIds.length; k++) {
                    PresetModel p = JourneyPackCatalog.findAnyById(stage.presetIds[k]);
                    if (mCurrentFilterMode == FILTER_CORE
                            && (ProPackCatalog.isProPreset(p.id) || JourneyPackCatalog.isJourneyPreset(p.id))) {
                        continue;
                    }
                    if (mCurrentFilterMode == FILTER_PRO && !ProPackCatalog.isProPreset(p.id)) {
                        continue;
                    }
                    if (mCurrentFilterMode == FILTER_JOURNEY && !JourneyPackCatalog.isJourneyPreset(p.id)) {
                        continue;
                    }
                    stageCards.add(p);
                }
                if (stageCards.isEmpty()) continue;

                mPresetContainer.addView(createStageSectionHeader(stage));
                for (int k = 0; k < stageCards.size(); k++) {
                    PresetModel preset = stageCards.get(k);
                    View cardView = createPresetCard(preset, preset.id == activeId, showCatPrefix);
                    if (preset.id == activeId) {
                        mActivePresetCardView = cardView;
                    }
                    mPresetContainer.addView(cardView);
                    totalAdded++;
                }
            }
            if (totalAdded == 0) {
                mPresetContainer.addView(buildEmptyFilterCard(false));
            }
            refreshTabStyles();
            return;
        }

        List<PresetModel> candidates = new ArrayList<>();
        if (!searchActive && mCurrentFilterMode == FILTER_RECENT) {
            List<Integer> recents = loadRecentIds();
            for (int i = 0; i < recents.size(); i++) {
                candidates.add(JourneyPackCatalog.findAnyById(recents.get(i)));
            }
        } else {
            List<PresetModel> all = JourneyPackCatalog.getAllPresets88();
            for (int i = 0; i < all.size(); i++) {
                PresetModel preset = all.get(i);
                if (searchActive) {
                    if (PresetAdvisor.matchesQuery(preset, getTabShortName(preset.tabIndex), q)) {
                        candidates.add(preset);
                    }
                } else if (mCurrentFilterMode == FILTER_FAV) {
                    if (mFavoriteIds.contains(preset.id)) candidates.add(preset);
                }
            }
        }

        if (mTvSearchStatus != null) {
            if (searchActive) {
                mTvSearchStatus.setText("검색 결과 " + candidates.size() + "건 (전체 88종)");
                mTvSearchStatus.setVisibility(View.VISIBLE);
            } else {
                mTvSearchStatus.setVisibility(View.GONE);
            }
        }

        for (int i = 0; i < candidates.size(); i++) {
            PresetModel preset = candidates.get(i);
            View cardView = createPresetCard(preset, preset.id == activeId, true);
            if (preset.id == activeId) {
                mActivePresetCardView = cardView;
            }
            mPresetContainer.addView(cardView);
        }

        if (candidates.isEmpty()) {
            mPresetContainer.addView(buildEmptyFilterCard(searchActive));
        }
        refreshTabStyles();
    }

    private View createStageSectionHeader(JourneyOrderPolicy.StageGroup stage) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(stage.title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.setTextColor(COL_AMBER);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(stage.subtitle);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvSub.setTextColor(COL_TEXT_TERTIARY);
        row.addView(tvSub);

        return row;
    }

    private View buildEmptyFilterCard(final boolean searchActive) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(14)));
        card.setPadding(dp(18), dp(22), dp(18), dp(22));

        TextView tv = new TextView(this);
        if (searchActive) {
            tv.setText("일치하는 프리셋이 없습니다. 예: 수면, 헬스, 두통, 브라운, 40Hz");
        } else if (mCurrentFilterMode == FILTER_FAV) {
            tv.setText("즐겨찾기한 프리셋이 없습니다. 카드 우측 별 아이콘으로 고정하세요.");
        } else if (mCurrentFilterMode == FILTER_RECENT) {
            tv.setText("최근 재생 기록이 없습니다. 원하는 프리셋을 선택해 시작하세요.");
        } else {
            tv.setText("이 카테고리에는 선택한 필터에 맞는 프리셋이 없습니다.");
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(COL_TEXT_SECONDARY);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv);

        TextView btn = new TextView(this);
        btn.setText(searchActive ? "검색 초기화" : "전체 88 프리셋 보기");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(COL_AMBER);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(14), dp(8), dp(14), dp(8));
        btn.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (searchActive) {
                    clearSearchAndHideKeyboard();
                } else {
                    setFilterMode(FILTER_ALL);
                }
            }
        });
        card.addView(btn);
        return card;
    }

    private View createPresetCard(final PresetModel preset, boolean isActiveCard, boolean showCatPrefix) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (isActiveCard) {
            card.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_ACTIVE, dp(2), dp(14)));
        } else {
            card.setBackground(createBoxDrawable(COL_SURFACE, COL_OUTLINE, dp(1), dp(14)));
        }
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout eyebrowRow = new LinearLayout(this);
        eyebrowRow.setOrientation(LinearLayout.HORIZONTAL);
        eyebrowRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBadge = new TextView(this);
        String code = JourneyPackCatalog.getShortCode(preset);
        String catPrefix = showCatPrefix ? (getTabShortName(preset.tabIndex) + " · ") : "";
        tvBadge.setText(code + " · " + catPrefix + preset.badge);
        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        boolean isExpandedPack = ProPackCatalog.isProPreset(preset.id) || JourneyPackCatalog.isJourneyPreset(preset.id);
        tvBadge.setTextColor(isExpandedPack ? COL_AMBER : COL_TEXT_SECONDARY);
        applyTabularMono(tvBadge, false);
        tvBadge.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        eyebrowRow.addView(tvBadge);

        if (isActiveCard) {
            TextView tvActiveFlag = new TextView(this);
            tvActiveFlag.setText("재생 중");
            tvActiveFlag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvActiveFlag.setTextColor(COL_ACTIVE);
            tvActiveFlag.setTypeface(Typeface.DEFAULT_BOLD);
            tvActiveFlag.setPadding(0, 0, dp(8), 0);
            eyebrowRow.addView(tvActiveFlag);
        }

        final ImageButton btnFav = new ImageButton(this);
        final boolean isFav = mFavoriteIds.contains(preset.id);
        btnFav.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        btnFav.setContentDescription(isFav ? "즐겨찾기 해제" : "즐겨찾기 추가");
        btnFav.setBackgroundColor(Color.TRANSPARENT);
        btnFav.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnFav.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnFav.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        btnFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                boolean nowFav;
                if (mFavoriteIds.contains(preset.id)) {
                    mFavoriteIds.remove(preset.id);
                    nowFav = false;
                } else {
                    mFavoriteIds.add(preset.id);
                    nowFav = true;
                }
                saveFavoritesToPrefs();
                refreshFilterChipsUi();
                btnFav.setImageResource(nowFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                btnFav.setContentDescription(nowFav ? "즐겨찾기 해제" : "즐겨찾기 추가");
                if (mCurrentFilterMode == FILTER_FAV) {
                    renderPresetCards();
                }
            }
        });
        eyebrowRow.addView(btnFav);
        card.addView(eyebrowRow);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(preset.title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTextColor(COL_TEXT_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(3), 0, dp(4));
        card.addView(tvTitle);

        TextView tvSpec = new TextView(this);
        tvSpec.setText(buildPresetSpecLine(preset));
        tvSpec.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSpec.setTextColor(COL_AMBER);
        applyTabularMono(tvSpec, true);
        tvSpec.setPadding(0, 0, 0, dp(5));
        card.addView(tvSpec);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(preset.desc);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDesc.setTextColor(COL_TEXT_SECONDARY);
        tvDesc.setLineSpacing(dp(2), 1.22f);
        card.addView(tvDesc);

        LinearLayout bottomMetaRow = new LinearLayout(this);
        bottomMetaRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomMetaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dp(7);
        bottomMetaRow.setLayoutParams(bottomLp);

        TextView tvGuide = new TextView(this);
        tvGuide.setText(PresetAdvisor.getListeningGuideBadge(preset));
        tvGuide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvGuide.setTextColor(preset.noiseType == 2 ? COL_AMBER : COL_TEXT_SECONDARY);
        applyTabularMono(tvGuide, false);
        tvGuide.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvGuide.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(6)));
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        guideLp.rightMargin = dp(6);
        tvGuide.setLayoutParams(guideLp);
        bottomMetaRow.addView(tvGuide);

        TextView btnExplain = new TextView(this);
        btnExplain.setText("설명");
        btnExplain.setContentDescription("이 시나리오 쉬운 설명 보기");
        btnExplain.setSingleLine(true);
        btnExplain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        btnExplain.setTextColor(COL_AMBER);
        btnExplain.setTypeface(Typeface.DEFAULT_BOLD);
        btnExplain.setGravity(Gravity.CENTER);
        btnExplain.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnExplain.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(6)));
        btnExplain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showPresetExplainDialog(preset);
            }
        });
        bottomMetaRow.addView(btnExplain);
        card.addView(bottomMetaRow);

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
        if (preset.isSweep && preset.freq1 > 0f) {
            int mins = Math.max(1, Math.round(preset.sweepDurationSec / 60.0f));
            sb.append(String.format(Locale.US, "바이노럴 램프 %.0fHz · %.1f→%.1f Hz (%d분)",
                    preset.freq1, preset.sweepStart, preset.sweepEnd, mins));
        } else if (preset.isSweep) {
            sb.append(String.format(Locale.US, "스윕 %.1f~%.1f Hz · %.0fs 왕복",
                    preset.sweepStart, preset.sweepEnd, preset.sweepDurationSec));
        } else if (preset.binauralBeat > 0) {
            sb.append(String.format(Locale.US, "바이노럴 %.1f + %.1f Hz (차 %.2f Hz)",
                    preset.freq1, preset.freq2, preset.binauralBeat));
        } else if (preset.freq2 > 0) {
            sb.append(String.format(Locale.US, "듀얼 %.2f + %.2f Hz", preset.freq1, preset.freq2));
        } else if (preset.freq1 > 0) {
            sb.append(String.format(Locale.US, "단일 %.2f Hz", preset.freq1));
        } else if (preset.noiseType == 4) {
            sb.append("스테레오 1/f² 브라운 노이즈");
        } else if (preset.noiseType == 5) {
            sb.append("대역 성형 2.8kHz 마스킹");
        } else {
            sb.append("전용 음향 시나리오");
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
        if (mIsBound && mService != null) {
            mService.loadAndPlayPreset(preset.id);
            mVolumePercent = mService.getVolumePercent();
            mIsMuted = mService.isMuted();
            if (mService.consumeLastSwitchSoftened()) {
                mSwitchNotice = "• 고볼륨 보호: 야간·휴식 프리셋 전환으로 안전 볼륨("
                        + mService.getLastSoftenedTargetPct() + "%)으로 자동 조절됨";
            } else {
                mSwitchNotice = "";
            }
            syncVolumeUi();
        } else {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(AegisAudioService.PREF_LAST_TAB, preset.tabIndex)
                    .putInt(AegisAudioService.PREF_LAST_PRESET_ID, preset.id)
                    .apply();

            Intent intent = new Intent(this, AegisAudioService.class);
            intent.setAction(AegisAudioService.ACTION_START);
            intent.putExtra(AegisAudioService.EXTRA_PRESET_ID, preset.id);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        refreshFilterChipsUi();
        updateDashboard(preset, true, preset.freq1 > 0f ? preset.freq1 : preset.sweepStart);
        if (mSearchQuery.trim().isEmpty()
                && mCurrentSelectedTab != preset.tabIndex
                && mCurrentFilterMode != FILTER_FAV
                && mCurrentFilterMode != FILTER_RECENT) {
            selectTab(preset.tabIndex, true, false);
        }
        updateRoutineBannerUi();
        refreshRoutineCoursesRow();
        refreshRecommendationsRow();
    }

    private void stopAudio() {
        mSwitchNotice = "";
        if (mService != null) {
            mService.stopPlayback();
        } else {
            stopService(new Intent(this, AegisAudioService.class));
        }
        updateDashboard(null, false, 0.0f);
        updateRoutineBannerUi();
        refreshRoutineCoursesRow();
        refreshRecommendationsRow();
    }

    private void syncFromService(boolean allowTabAutoSync) {
        boolean running = (mService != null && mService.isPlaying());
        PresetModel p = running ? mService.getCurrentPreset() : null;
        if (mService != null) {
            int svcVol = mService.getVolumePercent();
            boolean svcMute = mService.isMuted();
            if (svcVol != mVolumePercent || svcMute != mIsMuted) {
                mVolumePercent = svcVol;
                mIsMuted = svcMute;
                syncVolumeUi();
            }
            if (mService.consumeLastSwitchSoftened()) {
                mSwitchNotice = "• 고볼륨 보호: 야간·휴식 프리셋 전환으로 안전 볼륨("
                        + mService.getLastSoftenedTargetPct() + "%)으로 자동 조절됨";
            }
        }
        if (running && p != null) {
            mActivePreset = p;
            boolean isPro = ProPackCatalog.isProPreset(p.id);
            boolean isJrn = JourneyPackCatalog.isJourneyPreset(p.id);
            if (allowTabAutoSync && ((isPro && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_JOURNEY))
                    || (isJrn && (mCurrentFilterMode == FILTER_CORE || mCurrentFilterMode == FILTER_PRO))
                    || (!isPro && !isJrn && (mCurrentFilterMode == FILTER_PRO || mCurrentFilterMode == FILTER_JOURNEY))
                    || mCurrentFilterMode == FILTER_RECENT)) {
                setFilterMode(FILTER_ALL);
            }
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

        if (mSearchQuery.trim().isEmpty()
                && targetTab != mCurrentSelectedTab
                && mCurrentFilterMode != FILTER_FAV
                && mCurrentFilterMode != FILTER_RECENT) {
            selectTab(targetTab, true, false);
        } else if (allowTabAutoSync && mCurrentFilterMode != FILTER_FAV && mCurrentFilterMode != FILTER_RECENT) {
            scrollTabIntoCenter(mCurrentSelectedTab, false);
        }
        refreshFilterChipsUi();
        refreshProfileAndAmbientUi();
        updateRoutineBannerUi();
        refreshRoutineCoursesRow();
        refreshRecommendationsRow();
        updateTimerChipUi();
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
                    liveHz = (p.freq1 > 0f) ? p.freq1 : p.sweepStart;
                }
                boolean guarded = mService.isUltrasonicGuarded();
                boolean compActive = mService.getDspCore().isAcousticCompensationActive();
                if (!mLastDisplayedRunning || mLastHighlightedPresetId != p.id) {
                    updateDashboard(p, true, liveHz);
                } else {
                    updateHeroFreqViews(liveHz, p, true);
                    if (guarded != mLastUltrasonicGuarded || compActive != mLastCompActive) {
                        updateMechanismText(p, guarded);
                    }
                }
                if (mTvScopeTelemetry != null) {
                    String scopeStr = String.format(Locale.US, "%.1f dBFS · 클리핑 %d",
                            mService.getLiveRmsDbfs(), mService.getOvershootCount());
                    if (!scopeStr.equals(mLastScopeTelemetry)) {
                        mLastScopeTelemetry = scopeStr;
                        mTvScopeTelemetry.setText(scopeStr);
                    }
                }
                updateVuMeter(mService.getLiveRmsDbfs(), true);
                if (mWaveformView != null && mConsoleExpanded) {
                    mWaveformView.pokeIfActive();
                }
            } else {
                updateVuMeter(-96.0f, false);
                if (mLastDisplayedRunning) {
                    updateDashboard(null, false, 0.0f);
                }
            }
            updateTimerChipUi();
            updateRoutineBannerUi();
        }
    }

    private void updateMechanismText(PresetModel preset, boolean guarded) {
        mLastUltrasonicGuarded = guarded;
        boolean compActive = mIsBound && mService != null && mService.getDspCore().isAcousticCompensationActive();
        mLastCompActive = compActive;

        StringBuilder sb = new StringBuilder();
        if (!mSwitchNotice.isEmpty()) {
            sb.append(mSwitchNotice).append('\n');
        }
        if (guarded) {
            sb.append("• 이어폰 연결 감지 — 초음파 안전 감쇄(-12dB) 적용 중\n");
        } else if (compActive) {
            int eff = mService.getDspCore().getEffectiveProfile();
            if (eff == AegisDspCore.PROFILE_PHONE_SPEAKER) {
                sb.append("• 폰 스피커 청감 보정 활성 (초저역 배음·펄스 보조)\n");
            } else if (eff == AegisDspCore.PROFILE_HEADPHONE) {
                sb.append("• 이어폰 저역 감성 보정 활성\n");
            }
        }
        sb.append(preset.desc);
        mTvMechanismInfo.setText(sb.toString());
    }

    private void updateHeroFreqViews(float liveHz, PresetModel p, boolean isRunning) {
        String hzText;
        String badgeText = "";
        int hzColor;
        if (!isRunning || p == null) {
            hzText = "0.00 Hz";
            hzColor = COL_TEXT_TERTIARY;
        } else {
            hzColor = COL_AMBER;
            if (liveHz <= 0.0f) {
                hzText = "마스크 노이즈";
            } else if (liveHz >= 1000.0f) {
                hzText = String.format(Locale.US, "%.1f Hz", liveHz);
            } else {
                hzText = String.format(Locale.US, "%.2f Hz", liveHz);
            }

            if (p.isSweep && p.freq1 > 0f) {
                float beat = (mIsBound && mService != null && mService.getCurrentLiveBeatHz() > 0f)
                        ? mService.getCurrentLiveBeatHz() : p.sweepStart;
                badgeText = String.format(Locale.US, "Δ %.2f Hz", beat);
            } else if (p.binauralBeat > 0) {
                badgeText = String.format(Locale.US, "Δ %.2f Hz", p.binauralBeat);
            } else if (p.isSweep) {
                badgeText = "SWEEP";
            } else if (p.freq2 > 0) {
                badgeText = String.format(Locale.US, "+ %.1f Hz", p.freq2);
            } else if (p.fmMod) {
                badgeText = "FM";
            } else if (p.amMod) {
                badgeText = "AM";
            }
        }

        if (!hzText.equals(mLastHeroHzText)) {
            mLastHeroHzText = hzText;
            mTvHeroFreq.setText(hzText);
        }
        mTvHeroFreq.setTextColor(hzColor);

        if (!badgeText.equals(mLastHeroBadgeText)) {
            mLastHeroBadgeText = badgeText;
            mTvHeroSubBadge.setText(badgeText);
        }
        mTvHeroSubBadge.setVisibility(badgeText.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateIdleResumeSummary() {
        if (mLastDisplayedRunning) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lastId = prefs.getInt(AegisAudioService.PREF_LAST_PRESET_ID, -1);
        if (lastId > 0) {
            PresetModel last = JourneyPackCatalog.findAnyById(lastId);
            String code = JourneyPackCatalog.getShortCode(last);
            mTvScenarioTitle.setText("최근: " + code + " · " + last.title);
        } else {
            mTvScenarioTitle.setText("프리셋을 선택하면 즉시 재생됩니다");
        }
    }

    private void updateDashboard(PresetModel preset, boolean isRunning, float liveHz) {
        mLastDisplayedRunning = isRunning;

        if (isRunning && preset != null) {
            mLedIndicator.setImageResource(R.drawable.led_running);
            mTvStatusText.setText("재생 중 · " + getTabShortName(preset.tabIndex));
            mTvStatusText.setTextColor(COL_ACTIVE);

            float displayHz = liveHz > 0.0f ? liveHz : (preset.freq1 > 0f ? preset.freq1 : preset.sweepStart);
            updateHeroFreqViews(displayHz, preset, true);

            String code = JourneyPackCatalog.getShortCode(preset);
            mTvScenarioTitle.setText((code.isEmpty() ? "" : code + " · ") + preset.title);
            boolean guarded = (mIsBound && mService != null && mService.isUltrasonicGuarded());
            updateMechanismText(preset, guarded);

            if (mWaveformView != null) {
                if (mIsBound && mService != null) {
                    mWaveformView.bindDspCore(mService.getDspCore());
                }
                mWaveformView.setActive(mConsoleExpanded);
                if (mConsoleExpanded) {
                    mWaveformView.pokeIfActive();
                }
            }
            updateVuMeter((mIsBound && mService != null) ? mService.getLiveRmsDbfs() : -18.0f, true);

            if (mBtnMiniStop != null) {
                mBtnMiniStop.setText("중지");
                mBtnMiniStop.setTextColor(COL_TEXT_PRIMARY);
                mBtnMiniStop.setBackground(createBoxDrawable(COL_STOP_BTN, COL_STOPPED, dp(1), dp(7)));
                mBtnMiniStop.setEnabled(true);
            }

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
            mLastCompActive = false;

            updateIdleResumeSummary();
            mTvMechanismInfo.setText("위상 연속 무왜곡 출력 대기 · 타 오디오 앱과 동시 재생");
            if (mTvScopeTelemetry != null) {
                mLastScopeTelemetry = "-96.0 dBFS · 클리핑 0";
                mTvScopeTelemetry.setText(mLastScopeTelemetry);
            }
            if (mWaveformView != null) {
                mWaveformView.setActive(false);
            }
            updateVuMeter(-96.0f, false);

            if (mBtnMiniStop != null) {
                mBtnMiniStop.setText("▶ 이어듣기");
                mBtnMiniStop.setTextColor(COL_AMBER);
                mBtnMiniStop.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(7)));
                mBtnMiniStop.setEnabled(true);
            }

            mBtnStop.setText("▶ 마지막 프리셋 이어듣기");
            mBtnStop.setTextColor(COL_AMBER);
            mBtnStop.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(10)));
            mBtnStop.setEnabled(true);
        }

        int desiredHighlightId = (isRunning && preset != null) ? preset.id : -1;
        if (desiredHighlightId != mLastHighlightedPresetId) {
            renderPresetCards();
        }
        updateRecommendedVolumeChip();
        refreshTabStyles();
        updateJumpCue();
        updateTimerChipUi();
        updateRoutineBannerUi();
    }

    private void showAboutLicenseDialog() {
        if (isFinishing()) return;
        if (mAboutDialog != null && mAboutDialog.isShowing()) {
            mAboutDialog.dismiss();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COL_SURFACE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(COL_SURFACE);
        box.setPadding(dp(18), dp(16), dp(18), dp(12));

        TextView tTitle = new TextView(this);
        tTitle.setText("보르타 이지스 v3.4.4 · 설정 및 진단");
        tTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tTitle.setTextColor(COL_AMBER);
        tTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tTitle.setPadding(0, 0, 0, dp(10));
        box.addView(tTitle);

        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean[] perVol = new boolean[]{
                mIsBound && mService != null ? mService.isPerPresetVolumeEnabled()
                        : prefs.getBoolean(AegisAudioService.PREF_PER_PRESET_VOL, true)
        };
        final boolean[] gentleFade = new boolean[]{
                mIsBound && mService != null ? mService.isGentleSleepFadeEnabled()
                        : prefs.getBoolean(AegisAudioService.PREF_GENTLE_SLEEP_FADE, true)
        };

        box.addView(buildSettingToggleRow(
                "프리셋별 볼륨 기억 & 안전 전환",
                "수면·두통 프리셋 전환 시 과볼륨 자동 보호",
                perVol[0],
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        perVol[0] = !perVol[0];
                        if (mIsBound && mService != null) mService.setPerPresetVolumeEnabled(perVol[0]);
                        else prefs.edit().putBoolean(AegisAudioService.PREF_PER_PRESET_VOL, perVol[0]).apply();
                        updateSettingTogglePill((TextView) v, perVol[0]);
                    }
                }));

        box.addView(buildSettingToggleRow(
                "타이머 종료 20초 부드러운 페이드아웃",
                "수면 중 갑작스런 무음 각성 방지",
                gentleFade[0],
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        gentleFade[0] = !gentleFade[0];
                        if (mIsBound && mService != null) mService.setGentleSleepFadeEnabled(gentleFade[0]);
                        else prefs.edit().putBoolean(AegisAudioService.PREF_GENTLE_SLEEP_FADE, gentleFade[0]).apply();
                        updateSettingTogglePill((TextView) v, gentleFade[0]);
                    }
                }));

        // Live Audio Engine & Battery Diagnostics
        LinearLayout diagCard = new LinearLayout(this);
        diagCard.setOrientation(LinearLayout.VERTICAL);
        diagCard.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
        diagCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagLp.topMargin = dp(8);
        diagLp.bottomMargin = dp(10);
        diagCard.setLayoutParams(diagLp);

        String pcmMode = (mIsBound && mService != null)
                ? (mService.isUsingFloatOutput() ? "48kHz 32-bit Float (고정밀)" : "48kHz 16-bit TPDF 디더")
                : "48kHz 스테레오 대기";
        String devName = (mIsBound && mService != null) ? mService.getOutputDeviceLabel() : "폰 스피커";
        int underruns = (mIsBound && mService != null) ? mService.getUnderrunCount() : 0;
        int recoveries = (mIsBound && mService != null) ? mService.getRecoveryCount() : 0;
        long overshoots = (mIsBound && mService != null) ? mService.getOvershootCount() : 0L;
        boolean battIgnored = (mIsBound && mService != null) ? mService.isIgnoringBatteryOptimizations() : false;

        TextView tDiag = new TextView(this);
        tDiag.setText(String.format(Locale.US,
                "실시간 오디오 진단\n• 엔진: %s\n• 출력 장치: %s (언더런 %d · 자동복구 %d · 클리핑 %d)\n• 배터리 최적화: %s",
                pcmMode, devName, underruns, recoveries, overshoots,
                battIgnored ? "제한 해제됨 (장시간 안정)" : "제한 가능 — 예외 설정 권장"));
        tDiag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tDiag.setTextColor(COL_TEXT_PRIMARY);
        tDiag.setLineSpacing(dp(2), 1.25f);
        diagCard.addView(tDiag);

        if (Build.VERSION.SDK_INT >= 23 && !battIgnored) {
            TextView btnBatt = new TextView(this);
            btnBatt.setText("배터리 최적화 제외 설정 열기");
            btnBatt.setGravity(Gravity.CENTER);
            btnBatt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            btnBatt.setTextColor(COL_AMBER);
            btnBatt.setTypeface(Typeface.DEFAULT_BOLD);
            btnBatt.setPadding(dp(10), dp(7), dp(10), dp(7));
            btnBatt.setBackground(createBoxDrawable(COL_AMBER_BG, COL_AMBER, dp(1), dp(8)));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bLp.topMargin = dp(8);
            btnBatt.setLayoutParams(bLp);
            btnBatt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(i);
                    } catch (Throwable ignored) {}
                }
            });
            diagCard.addView(btnBatt);
        }
        box.addView(diagCard);

        TextView tBody = new TextView(this);
        tBody.setText(
                "• 빠른 설정 타일: 상단 알림창 빠른 설정에서 [보르타 이지스] 타일을 추가해 원터치 재생/중지 가능\n"
                + "• 카탈로그: 코어 48종 + Pro 16종 + 여정 24종 (총 88종) · 4대 원탭 코스\n"
                + "• 빌드 정보: v3.4.4 (Build 12) · API 21~33 호환\n"
                + "• 라이선스: Proprietary Commercial / Evaluation EULA (v1.0)\n"
                + "• 저작권: Copyright (c) 2026 gyeomsVibe (김윤겸)\n"
                + "• 안내: 본 앱은 일반 웰니스·집중·환경음 마스킹 보조용이며 의료기기가 아닙니다."
        );
        tBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tBody.setTextColor(COL_TEXT_SECONDARY);
        tBody.setLineSpacing(dp(2), 1.25f);
        box.addView(tBody);

        scroll.addView(box);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scroll);
        builder.setPositiveButton("닫기", null);
        mAboutDialog = builder.create();
        mAboutDialog.show();
    }

    private void showPresetExplainDialog(final PresetModel preset) {
        if (isFinishing() || preset == null) return;
        if (mExplainDialog != null && mExplainDialog.isShowing()) {
            mExplainDialog.dismiss();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COL_SURFACE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(COL_SURFACE);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));

        String code = JourneyPackCatalog.getShortCode(preset);
        TextView tvEyebrow = new TextView(this);
        tvEyebrow.setText(code + " · " + getTabShortName(preset.tabIndex) + " · " + preset.badge);
        tvEyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvEyebrow.setTextColor(COL_AMBER);
        applyTabularMono(tvEyebrow, true);
        box.addView(tvEyebrow);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(preset.title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTextColor(COL_TEXT_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(4), 0, dp(10));
        box.addView(tvTitle);

        TextView tvBody = new TextView(this);
        tvBody.setText(PresetAdvisor.buildPlainExplanation(preset));
        tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvBody.setTextColor(COL_TEXT_SECONDARY);
        tvBody.setLineSpacing(dp(2), 1.28f);
        box.addView(tvBody);

        scroll.addView(box);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scroll);
        builder.setPositiveButton("바로 재생", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                playPreset(preset);
            }
        });
        builder.setNegativeButton("닫기", null);
        mExplainDialog = builder.create();
        mExplainDialog.show();
    }

    private void showBeginnerGuideDialog() {
        if (isFinishing()) return;
        if (mGuideDialog != null && mGuideDialog.isShowing()) {
            mGuideDialog.dismiss();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COL_SURFACE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(COL_SURFACE);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("쉬운 용어 · 시나리오 선택 가이드");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tvTitle.setTextColor(COL_AMBER);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, dp(10));
        box.addView(tvTitle);

        TextView tvBody = new TextView(this);
        tvBody.setText(PresetAdvisor.getBeginnerGlossaryText());
        tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvBody.setTextColor(COL_TEXT_SECONDARY);
        tvBody.setLineSpacing(dp(2), 1.28f);
        box.addView(tvBody);

        scroll.addView(box);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scroll);
        builder.setPositiveButton("닫기", null);
        mGuideDialog = builder.create();
        mGuideDialog.show();
    }

    private View buildSettingToggleRow(String title, String subtitle, boolean enabled, final View.OnClickListener onToggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(createBoxDrawable(COL_SURFACE_HIGH, COL_OUTLINE, dp(1), dp(10)));
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvT.setTextColor(COL_TEXT_PRIMARY);
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(tvT);

        TextView tvS = new TextView(this);
        tvS.setText(subtitle);
        tvS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvS.setTextColor(COL_TEXT_SECONDARY);
        left.addView(tvS);
        row.addView(left);

        final TextView pill = new TextView(this);
        pill.setSingleLine(true);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLp.leftMargin = dp(8);
        pill.setLayoutParams(pLp);
        updateSettingTogglePill(pill, enabled);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onToggle.onClick(v);
            }
        });
        row.addView(pill);
        return row;
    }

    private void updateSettingTogglePill(TextView pill, boolean enabled) {
        pill.setText(enabled ? "켜짐" : "꺼짐");
        pill.setTextColor(enabled ? COL_ACTIVE : COL_TEXT_TERTIARY);
        pill.setBackground(createBoxDrawable(
                enabled ? COL_SURFACE : COL_SURFACE_HIGH,
                enabled ? COL_ACTIVE : COL_OUTLINE, dp(1), dp(8)));
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
