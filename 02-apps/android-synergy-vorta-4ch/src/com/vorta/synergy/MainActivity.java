package com.vorta.synergy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Vorta Synergy v1.0.0 Commercial Acoustic Console:
 * - Side-by-side commercial app with 4-channel band guardrails (CH1~CH4)
 * - 3 Main Tabs: [자동 시너지 (20x4)] | [반수동 (긴급+체인)] | [기초 (22)]
 * - In-app bottom banner (replaces system popup alerts), zero inline EditText ScrollView bounce,
 *   universal read-only live volume badge (AUX/SPK x/15), and dp-based non-clipping typography.
 */
public class MainActivity extends Activity {
    private static final int BG_ROOT = Color.parseColor("#0B0F14");
    private static final int BG_SURFACE = Color.parseColor("#131922");
    private static final int BG_CARD = Color.parseColor("#19222E");
    private static final int BG_INPUT = Color.parseColor("#0D1219");
    private static final int BORDER_LINE = Color.parseColor("#253142");
    private static final int ACCENT_TEAL = Color.parseColor("#2DD4BF");
    private static final int ACCENT_AMBER = Color.parseColor("#F59E0B");
    private static final int ACCENT_BLUE = Color.parseColor("#38BDF8");
    private static final int ACCENT_GREEN = Color.parseColor("#10B981");
    private static final int ACCENT_RED = Color.parseColor("#E11D48");
    private static final int TXT_PRIMARY = Color.parseColor("#F1F5F9");
    private static final int TXT_SEC = Color.parseColor("#94A3B8");
    private static final int TXT_DIM = Color.parseColor("#64748B");

    private SynergyChannel mActiveChannel;
    private boolean mChannelDrawerOpen = false;
    private int mMainTab = 0; // 0: 자동 시너지, 1: 반수동, 2: 기초
    private SynergyCatalog.FilterGroup mFilterGroup = SynergyCatalog.FilterGroup.TIME;
    private boolean mKeepScreenOn = false;
    private int mNextStepId = 10;

    private ScrollView mScrollView;
    private Button mBtnChannelMenu;
    private TextView mTvTopVolumeBadge;
    private LinearLayout mChannelDrawer;
    private final Button[] mChannelDrawerBtns = new Button[SynergyChannel.values().length];

    // Telemetry Console
    private LinearLayout mTelemetryPanel;
    private View mTeleLedDot;
    private TextView mTvTeleState;
    private TextView mTvTeleTag;
    private TextView mTvTeleHz;
    private Button mBtnMasterStop;
    private LiveWaveformView mScopeView;
    private TextView mTvChipProgress;
    private TextView mTvChipMod;
    private TextView mTvChipOutput;
    private TextView mTvTeleTitle;
    private TextView mTvTeleSub;
    private Button mBtnAuto24h;
    private Button mBtnKeepScreenOn;

    // Main tab bar & content host
    private final Button[] mMainTabBtns = new Button[3];
    private LinearLayout mTabHost;

    // In-app bottom banner (replaces system popup completely)
    private LinearLayout mBannerCard;
    private TextView mTvBanner;

    private int mLastTeleBorderColor = 0;
    private int mLastLedColor = 0;
    private boolean mWasPlayingForScope = false;

    private BroadcastReceiver mVolumeUiReceiver;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideBannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBannerCard != null) mBannerCard.setVisibility(View.GONE);
        }
    };
    private final Runnable mTelemetryTick = new Runnable() {
        @Override
        public void run() {
            refreshLiveTelemetry();
            mUiHandler.postDelayed(this, SynergyAudioService.isAnySourceActive() ? 650L : 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mActiveChannel = SynergyStore.loadActiveChannel(this, BuildProfile.DEFAULT_CHANNEL);
        mKeepScreenOn = SynergyStore.loadKeepScreenOn(this);
        applyKeepScreenFlag(mKeepScreenOn);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            }
        }

        SynergyAudioService.setActiveChannel(mActiveChannel);
        SynergyAudioService.sAutoSchedule24h = SynergyStore.loadAutoSchedule24h(this);
        SynergyAudioService.sGlobalJitter = SynergyStore.loadJitterEnabled(this);
        SynergyAudioService.sDutyMode = SynergyStore.loadDutyMode(this);
        SynergyAudioService.sGlobalTrimDb = SynergyStore.loadTrimDb(this);
        SynergyAudioService.sStabilizerEnabled = SynergyStore.loadStabilizerEnabled(this);
        SynergyAudioService.sNotchCenterHz = SynergyStore.loadNotchCenterHz(this, mActiveChannel);
        SynergyAudioService.sNotchHalfWidthHz = SynergyStore.loadNotchWidthHz(this, mActiveChannel);
        boolean[] mods = SynergyStore.loadModulationToggles(this);
        SynergyAudioService.sGlobalFmOn = mods[0];
        SynergyAudioService.sGlobalAmOn = mods[1];
        SynergyAudioService.sGlobalRmOn = mods[2];
        SynergyAudioService.sActivePackageCode = SynergyStore.loadActivePackageCode(this);

        synchronized (SynergyAudioService.sLock) {
            mNextStepId = SynergyStore.loadStepsInto(this, mActiveChannel, SynergyAudioService.sManualSteps);
        }

        SynergyAudioService.startServiceCompat(this);
        SynergyVolumeGuard.syncTargetFromSystem(this);
        SynergyVolumeGuard.ensureTargetVolumeIfNeeded(this);

        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setBackgroundColor(BG_ROOT);

        mScrollView = new ScrollView(this);
        mScrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mScrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(dp(12), dp(10), dp(12), dp(56));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        buildTopChannelBar(root);
        buildTelemetryConsole(root);
        buildMainTabBar(root);

        mTabHost = new LinearLayout(this);
        mTabHost.setOrientation(LinearLayout.VERTICAL);
        mTabHost.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(mTabHost);

        mScrollView.addView(root);
        frame.addView(mScrollView);

        buildInAppBanner(frame);
        setContentView(frame);

        renderActiveTab();
        refreshLiveTelemetry();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerVolumeUiReceiver();
        refreshLiveTelemetry();
        mUiHandler.removeCallbacks(mTelemetryTick);
        mUiHandler.post(mTelemetryTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryTick);
        unregisterVolumeUiReceiver();
    }

    @Override
    protected void onDestroy() {
        mUiHandler.removeCallbacksAndMessages(null);
        unregisterVolumeUiReceiver();
        super.onDestroy();
    }

    private void registerVolumeUiReceiver() {
        if (mVolumeUiReceiver != null) return;
        mVolumeUiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mTvTopVolumeBadge != null) {
                    mTvTopVolumeBadge.setText(SynergyVolumeGuard.formatLiveVolumeBadge(MainActivity.this));
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        f.addAction(Intent.ACTION_HEADSET_PLUG);
        f.addAction("android.media.STREAM_DEVICES_CHANGED_ACTION");
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(mVolumeUiReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(mVolumeUiReceiver, f);
        } catch (Throwable ignored) {}
    }

    private void unregisterVolumeUiReceiver() {
        if (mVolumeUiReceiver != null) {
            try { unregisterReceiver(mVolumeUiReceiver); } catch (Throwable ignored) {}
            mVolumeUiReceiver = null;
        }
    }

    // -------------------------------------------------------------------------
    // TOP CHANNEL SELECTOR & UNIVERSAL READ-ONLY VOLUME BADGE
    // -------------------------------------------------------------------------

    private void buildTopChannelBar(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(8));

        mBtnChannelMenu = new Button(this);
        mBtnChannelMenu.setAllCaps(false);
        setDpTextSize(mBtnChannelMenu, 12.5f);
        mBtnChannelMenu.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnChannelMenu.setTextColor(TXT_PRIMARY);
        mBtnChannelMenu.setSingleLine(true);
        mBtnChannelMenu.setEllipsize(TextUtils.TruncateAt.END);
        mBtnChannelMenu.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        mBtnChannelMenu.setMinWidth(0);
        mBtnChannelMenu.setMinimumWidth(0);
        mBtnChannelMenu.setPadding(dp(10), dp(6), dp(10), dp(6));
        mBtnChannelMenu.setBackground(makeBox(BG_SURFACE, ACCENT_TEAL, 8, 1));
        mBtnChannelMenu.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        mBtnChannelMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleChannelDrawer(!mChannelDrawerOpen);
            }
        });
        bar.addView(mBtnChannelMenu);

        mTvTopVolumeBadge = new TextView(this);
        mTvTopVolumeBadge.setText(SynergyVolumeGuard.formatLiveVolumeBadge(this));
        mTvTopVolumeBadge.setTextColor(ACCENT_AMBER);
        setDpTextSize(mTvTopVolumeBadge, 11.0f);
        mTvTopVolumeBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvTopVolumeBadge.setSingleLine(true);
        mTvTopVolumeBadge.setGravity(Gravity.CENTER);
        mTvTopVolumeBadge.setPadding(dp(9), dp(6), dp(9), dp(6));
        mTvTopVolumeBadge.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        vLp.leftMargin = dp(6);
        mTvTopVolumeBadge.setLayoutParams(vLp);
        mTvTopVolumeBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelfDiagnosticDialog();
            }
        });
        bar.addView(mTvTopVolumeBadge);

        root.addView(bar);

        mChannelDrawer = new LinearLayout(this);
        mChannelDrawer.setOrientation(LinearLayout.VERTICAL);
        mChannelDrawer.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.bottomMargin = dp(10);
        mChannelDrawer.setLayoutParams(dLp);
        mChannelDrawer.setBackground(makeBox(BG_SURFACE, ACCENT_TEAL, 10, 1));
        mChannelDrawer.setVisibility(View.GONE);

        TextView hdr = new TextView(this);
        hdr.setText("출력 채널 역할 선택 (기기당 1개 고정)");
        hdr.setTextColor(TXT_SEC);
        setDpTextSize(hdr, 11.0f);
        hdr.setPadding(dp(2), 0, dp(2), dp(8));
        mChannelDrawer.addView(hdr);

        SynergyChannel[] chs = SynergyChannel.values();
        for (int i = 0; i < chs.length; i++) {
            final SynergyChannel ch = chs[i];
            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            btn.setPadding(dp(12), dp(9), dp(12), dp(9));
            setDpTextSize(btn, 12.0f);
            btn.setLineSpacing(dp(2), 1.0f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < chs.length - 1) bLp.bottomMargin = dp(6);
            btn.setLayoutParams(bLp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActiveChannel = ch;
                    SynergyStore.saveActiveChannel(MainActivity.this, ch);
                    SynergyAudioService.setActiveChannel(ch);
                    SynergyAudioService.sNotchCenterHz = SynergyStore.loadNotchCenterHz(MainActivity.this, ch);
                    SynergyAudioService.sNotchHalfWidthHz = SynergyStore.loadNotchWidthHz(MainActivity.this, ch);
                    toggleChannelDrawer(false);
                    updateChannelDrawerUI();
                    renderActiveTab();
                    showBanner(ch.displayName + " (" + ch.bandLabel + ") 전환됨", true);
                }
            });
            mChannelDrawerBtns[i] = btn;
            mChannelDrawer.addView(btn);
        }
        root.addView(mChannelDrawer);
        updateChannelDrawerUI();
    }

    private void toggleChannelDrawer(boolean open) {
        mChannelDrawerOpen = open;
        mChannelDrawer.setVisibility(open ? View.VISIBLE : View.GONE);
        updateChannelDrawerUI();
    }

    private void updateChannelDrawerUI() {
        String arrow = mChannelDrawerOpen ? "▲" : "▼";
        mBtnChannelMenu.setText("≡ " + mActiveChannel.displayName + " (" + mActiveChannel.bandLabel + ") " + arrow);
        SynergyChannel[] chs = SynergyChannel.values();
        for (int i = 0; i < chs.length; i++) {
            boolean sel = (chs[i] == mActiveChannel);
            Button b = mChannelDrawerBtns[i];
            b.setText((sel ? "●  " : "○  ") + chs[i].displayName + " [" + chs[i].bandLabel + "]\n" + chs[i].roleSubtitle);
            b.setTextColor(sel ? ACCENT_TEAL : TXT_PRIMARY);
            b.setBackground(makeBox(sel ? BG_CARD : BG_INPUT, sel ? ACCENT_TEAL : BORDER_LINE, 8, 1));
        }
    }

    // -------------------------------------------------------------------------
    // TELEMETRY CONSOLE PANEL (Compact 3-chip progress + live oscilloscope)
    // -------------------------------------------------------------------------

    private void buildTelemetryConsole(LinearLayout root) {
        mTelemetryPanel = new LinearLayout(this);
        mTelemetryPanel.setOrientation(LinearLayout.VERTICAL);
        mTelemetryPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        mTelemetryPanel.setLayoutParams(lp);
        mTelemetryPanel.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));

        // Row 1: LED + Live state + Active package tag
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        mTeleLedDot = new View(this);
        LinearLayout.LayoutParams ledLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        ledLp.rightMargin = dp(6);
        mTeleLedDot.setLayoutParams(ledLp);
        mTeleLedDot.setBackground(makeOval(ACCENT_RED));
        row1.addView(mTeleLedDot);

        mTvTeleState = new TextView(this);
        mTvTeleState.setText("대기 중");
        mTvTeleState.setTextColor(TXT_SEC);
        setDpTextSize(mTvTeleState, 11.5f);
        mTvTeleState.setTypeface(Typeface.DEFAULT_BOLD);
        mTvTeleState.setSingleLine(true);
        mTvTeleState.setEllipsize(TextUtils.TruncateAt.END);
        mTvTeleState.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(mTvTeleState);

        mTvTeleTag = new TextView(this);
        mTvTeleTag.setText(mActiveChannel.shortLabel + " · 대기");
        mTvTeleTag.setTextColor(ACCENT_TEAL);
        setDpTextSize(mTvTeleTag, 10.5f);
        mTvTeleTag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvTeleTag.setSingleLine(true);
        row1.addView(mTvTeleTag);
        mTelemetryPanel.addView(row1);

        // Row 2: Live Hz + Master Stop button
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, dp(4), 0, dp(4));

        mTvTeleHz = new TextView(this);
        mTvTeleHz.setText("0.00 Hz");
        mTvTeleHz.setTextColor(TXT_DIM);
        setDpTextSize(mTvTeleHz, 22.0f);
        mTvTeleHz.setSingleLine(true);
        mTvTeleHz.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams hzLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        hzLp.rightMargin = dp(8);
        mTvTeleHz.setLayoutParams(hzLp);
        row2.addView(mTvTeleHz);

        mBtnMasterStop = makeCompactButton("전체 정지", ACCENT_RED, 34);
        mBtnMasterStop.setLayoutParams(new LinearLayout.LayoutParams(dp(80), dp(34)));
        mBtnMasterStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SynergyAudioService.stopAllPlayback();
                if (mScopeView != null) mScopeView.invalidate();
                renderActiveTab();
                refreshLiveTelemetry();
            }
        });
        row2.addView(mBtnMasterStop);
        mTelemetryPanel.addView(row2);

        // Row 3: Live Oscilloscope
        mScopeView = new LiveWaveformView(this);
        LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        scopeLp.topMargin = dp(3);
        scopeLp.bottomMargin = dp(6);
        mScopeView.setLayoutParams(scopeLp);
        mTelemetryPanel.addView(mScopeView);

        // Row 4: 3-Chip Progress Strip (No horizontal text collision)
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        mTvChipProgress = makeInfoChip("대기 48k");
        mTvChipMod = makeInfoChip("변조: 순수 정현파");
        mTvChipOutput = makeInfoChip("연속 출력");
        LinearLayout.LayoutParams c1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f);
        c1Lp.rightMargin = dp(4);
        mTvChipProgress.setLayoutParams(c1Lp);
        LinearLayout.LayoutParams c2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
        c2Lp.rightMargin = dp(4);
        mTvChipMod.setLayoutParams(c2Lp);
        mTvChipOutput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        chipRow.addView(mTvChipProgress);
        chipRow.addView(mTvChipMod);
        chipRow.addView(mTvChipOutput);
        mTelemetryPanel.addView(chipRow);

        // Row 5 & 6: Active Scenario Title + Clean 2-line Role/Effect Description
        mTvTeleTitle = new TextView(this);
        mTvTeleTitle.setText("시너지 조합 또는 반수동 설정을 선택하세요");
        mTvTeleTitle.setTextColor(TXT_PRIMARY);
        setDpTextSize(mTvTeleTitle, 12.5f);
        mTvTeleTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTvTeleTitle.setMaxLines(2);
        configureBalancedWrap(mTvTeleTitle);
        mTvTeleTitle.setPadding(0, dp(6), 0, dp(2));
        mTelemetryPanel.addView(mTvTeleTitle);

        mTvTeleSub = new TextView(this);
        mTvTeleSub.setText(mActiveChannel.roleSubtitle);
        mTvTeleSub.setTextColor(TXT_SEC);
        setDpTextSize(mTvTeleSub, 11.0f);
        mTvTeleSub.setMaxLines(2);
        configureBalancedWrap(mTvTeleSub);
        mTelemetryPanel.addView(mTvTeleSub);

        // Row 7: Utility Actions (24h RTC Schedule / Keep Screen On / Diagnostics)
        LinearLayout utilRow = new LinearLayout(this);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams utilLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        utilLp.topMargin = dp(8);
        utilRow.setLayoutParams(utilLp);

        mBtnAuto24h = makeCompactButton("24h 자동", BG_CARD, 34);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        aLp.rightMargin = dp(5);
        mBtnAuto24h.setLayoutParams(aLp);
        mBtnAuto24h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !SynergyAudioService.sAutoSchedule24h;
                SynergyAudioService.sAutoSchedule24h = next;
                SynergyStore.saveAutoSchedule24h(MainActivity.this, next);
                updateUtilityButtons();
                if (next) {
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    SynergyCatalog.SynergyPackage pkg = SynergyCatalog.packageForHour(hour);
                    if (pkg != null) {
                        SynergyStore.saveActivePackageCode(MainActivity.this, pkg.code);
                        SynergyAudioService.playAutoPackage(MainActivity.this, pkg);
                        renderActiveTab();
                        showBanner("24h 시간대 자동 전환 ON (" + pkg.code + " · " + pkg.title + ")", true);
                    }
                } else {
                    showBanner("24h 시간대 자동 전환 OFF", true);
                }
            }
        });
        utilRow.addView(mBtnAuto24h);

        mBtnKeepScreenOn = makeCompactButton("화면유지", BG_CARD, 34);
        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        kLp.rightMargin = dp(5);
        mBtnKeepScreenOn.setLayoutParams(kLp);
        mBtnKeepScreenOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mKeepScreenOn = !mKeepScreenOn;
                SynergyStore.saveKeepScreenOn(MainActivity.this, mKeepScreenOn);
                applyKeepScreenFlag(mKeepScreenOn);
                updateUtilityButtons();
            }
        });
        utilRow.addView(mBtnKeepScreenOn);

        Button btnDiag = makeCompactButton("실측 진단", BG_CARD, 34);
        btnDiag.setTextColor(ACCENT_TEAL);
        btnDiag.setBackground(makeBox(BG_CARD, BORDER_LINE, 6, 1));
        btnDiag.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        btnDiag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelfDiagnosticDialog();
            }
        });
        utilRow.addView(btnDiag);

        updateUtilityButtons();
        mTelemetryPanel.addView(utilRow);
        root.addView(mTelemetryPanel);
    }

    private void updateUtilityButtons() {
        boolean autoOn = SynergyAudioService.sAutoSchedule24h;
        mBtnAuto24h.setText(autoOn ? "24h ON" : "24h OFF");
        mBtnAuto24h.setTextColor(autoOn ? ACCENT_TEAL : TXT_SEC);
        mBtnAuto24h.setBackground(makeBox(autoOn ? BG_CARD : BG_INPUT, autoOn ? ACCENT_TEAL : BORDER_LINE, 6, 1));

        mBtnKeepScreenOn.setText(mKeepScreenOn ? "화면 ON" : "화면 OFF");
        mBtnKeepScreenOn.setTextColor(mKeepScreenOn ? TXT_PRIMARY : TXT_SEC);
        mBtnKeepScreenOn.setBackground(makeBox(mKeepScreenOn ? BG_CARD : BG_INPUT, mKeepScreenOn ? ACCENT_BLUE : BORDER_LINE, 6, 1));
    }

    private void applyKeepScreenFlag(boolean keepOn) {
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static void setTextIfChanged(TextView tv, String next) {
        if (tv == null) return;
        CharSequence cur = tv.getText();
        String s = next != null ? next : "";
        if (cur == null || !s.equals(cur.toString())) {
            tv.setText(s);
        }
    }

    private static void setTextColorIfChanged(TextView tv, int color) {
        if (tv != null && tv.getCurrentTextColor() != color) {
            tv.setTextColor(color);
        }
    }

    private void refreshLiveTelemetry() {
        setTextIfChanged(mTvTopVolumeBadge, SynergyVolumeGuard.formatLiveVolumeBadge(this));
        boolean active = SynergyAudioService.isAnySourceActive();
        boolean auxMuted = SynergyAudioService.sAuxSuspended;
        boolean cooling = SynergyAudioService.sDutyCoolingNow;

        int ledCol;
        int borderCol;
        if (!active) {
            ledCol = ACCENT_RED;
            setTextIfChanged(mTvTeleState, "대기 중");
            setTextColorIfChanged(mTvTeleState, TXT_SEC);
            setTextIfChanged(mTvTeleTag, mActiveChannel.shortLabel + " · 대기");
            setTextIfChanged(mTvTeleHz, "0.00 Hz");
            setTextColorIfChanged(mTvTeleHz, TXT_DIM);
            setTextIfChanged(mTvChipProgress, "48k Float 대기");
            setTextIfChanged(mTvChipMod, "변조: " + SynergyAudioService.formatActiveModulationText());
            setTextIfChanged(mTvChipOutput, mActiveChannel.recommendVolumeLimitHalf ? "노브 12시 이하" : "대기 상태");
            setTextIfChanged(mTvTeleTitle, "자동 시너지 또는 반수동 모드를 선택하세요");
            setTextIfChanged(mTvTeleSub, mActiveChannel.roleSubtitle);
            borderCol = BORDER_LINE;
        } else if (auxMuted) {
            ledCol = ACCENT_AMBER;
            setTextIfChanged(mTvTeleState, "AUX 분리 음소거 대기");
            setTextColorIfChanged(mTvTeleState, ACCENT_AMBER);
            setTextIfChanged(mTvTeleHz, "0.00 Hz");
            setTextColorIfChanged(mTvTeleHz, ACCENT_AMBER);
            borderCol = ACCENT_AMBER;
        } else if (cooling) {
            ledCol = ACCENT_AMBER;
            setTextIfChanged(mTvTeleState, "열보호 냉각 휴지 중");
            setTextColorIfChanged(mTvTeleState, ACCENT_AMBER);
            setTextIfChanged(mTvTeleHz, "0.00 Hz");
            setTextColorIfChanged(mTvTeleHz, ACCENT_AMBER);
            setTextIfChanged(mTvChipProgress, "무음 냉각");
            setTextIfChanged(mTvChipMod, "재개 대기 중");
            setTextIfChanged(mTvChipOutput, SynergyAudioService.formatDutyStatusText());
            borderCol = ACCENT_AMBER;
        } else {
            ledCol = ACCENT_GREEN;
            setTextIfChanged(mTvTeleState, "진동 출력 중");
            setTextColorIfChanged(mTvTeleState, ACCENT_GREEN);
            setTextIfChanged(mTvTeleHz, String.format(Locale.US, "%.2f Hz", SynergyAudioService.sInstantDisplayHz));
            setTextColorIfChanged(mTvTeleHz, ACCENT_AMBER);

            String qStr = SynergyAudioService.sLiveThdPercent >= 0.0
                    ? String.format(Locale.US, "THD %.2f%%", SynergyAudioService.sLiveThdPercent)
                    : String.format(Locale.US, "%.1fdBFS", SynergyAudioService.sLiveRmsDbfs);
            setTextIfChanged(mTvChipProgress, (SynergyAudioService.sIsFloatPipeline ? "Float " : "PCM16 ") + qStr);
            setTextIfChanged(mTvChipMod, "변조: " + SynergyAudioService.formatActiveModulationText());
            String dutyStr = SynergyAudioService.formatDutyStatusText();
            if (SynergyAudioService.sBatteryOverheatThrottle) {
                dutyStr = "온도주의 -3dB";
            } else if (SynergyAudioService.sThermalLoadPct >= 15) {
                dutyStr = "열부하 " + SynergyAudioService.sThermalLoadPct + "%";
            } else if (SynergyAudioService.sGlobalTrimDb < -0.05f) {
                dutyStr += String.format(Locale.US, " %.0fdB", SynergyAudioService.sGlobalTrimDb);
            }
            setTextIfChanged(mTvChipOutput, dutyStr);

            if (SynergyAudioService.sPlayMode == SynergyAudioService.MODE_AUTO_SYNERGY && SynergyAudioService.sActivePackage != null) {
                SynergyCatalog.SynergyPackage p = SynergyAudioService.sActivePackage;
                SynergyCatalog.ChannelProgram cp = p.programFor(mActiveChannel);
                setTextIfChanged(mTvTeleTag, p.code + " · 자동");
                setTextIfChanged(mTvTeleTitle, p.code + " " + p.title + " (" + cp.step.type.label + ")");
                setTextIfChanged(mTvTeleSub, cp.shortRoleNote);
            } else if (SynergyAudioService.sPlayMode == SynergyAudioService.MODE_BASIC && SynergyAudioService.sActiveBasic != null) {
                SynergyCatalog.BasicEntry be = SynergyAudioService.sActiveBasic;
                setTextIfChanged(mTvTeleTag, mActiveChannel.shortLabel + " · 기초");
                setTextIfChanged(mTvTeleTitle, be.title);
                setTextIfChanged(mTvTeleSub, be.effectDesc);
            } else if (SynergyAudioService.sQuickManualStep != null) {
                setTextIfChanged(mTvTeleTag, mActiveChannel.shortLabel + " · 반수동");
                setTextIfChanged(mTvTeleTitle, SynergyAudioService.sQuickManualStep.title);
                setTextIfChanged(mTvTeleSub, SynergyAudioService.sQuickManualStep.formatCompactSpec());
            } else {
                setTextIfChanged(mTvTeleTag, mActiveChannel.shortLabel + " · 체인");
                setTextIfChanged(mTvTeleTitle, "반수동 다중 체인 실행 중");
                setTextIfChanged(mTvTeleSub, "활성 스텝 혼합 출력 (" + mActiveChannel.bandLabel + ")");
            }
            borderCol = ACCENT_GREEN;
        }
        if (mTeleLedDot != null && ledCol != mLastLedColor) {
            mLastLedColor = ledCol;
            mTeleLedDot.setBackground(makeOval(ledCol));
        }
        if (mTelemetryPanel != null && borderCol != mLastTeleBorderColor) {
            mLastTeleBorderColor = borderCol;
            mTelemetryPanel.setBackground(makeBox(BG_SURFACE, borderCol, 10, borderCol == BORDER_LINE ? 1 : 2));
        }
        if (mScopeView != null && (active || mWasPlayingForScope)) {
            mScopeView.invalidate();
            mWasPlayingForScope = active;
        }
    }

    // -------------------------------------------------------------------------
    // MAIN 3-TAB BAR (자동 시너지 | 반수동 | 기초)
    // -------------------------------------------------------------------------

    private void buildMainTabBar(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        bar.setLayoutParams(lp);

        String[] labels = {"자동 시너지", "반수동", "기초"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = makeCompactButton(labels[i], BG_SURFACE, 38);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            if (i < 2) bLp.rightMargin = dp(6);
            b.setLayoutParams(bLp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mMainTab = idx;
                    renderActiveTab();
                }
            });
            mMainTabBtns[i] = b;
            bar.addView(b);
        }
        root.addView(bar);
    }

    private void updateMainTabButtons() {
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == mMainTab);
            mMainTabBtns[i].setTextColor(sel ? BG_ROOT : TXT_SEC);
            mMainTabBtns[i].setBackground(makeBox(sel ? ACCENT_TEAL : BG_SURFACE, sel ? ACCENT_TEAL : BORDER_LINE, 8, 1));
        }
    }

    private void renderActiveTab() {
        updateMainTabButtons();
        final int prevScrollY = mScrollView != null ? mScrollView.getScrollY() : 0;
        mTabHost.removeAllViews();

        if (mMainTab == 0) {
            renderAutoSynergyTab(mTabHost);
        } else if (mMainTab == 1) {
            renderSemiManualTab(mTabHost);
        } else {
            renderBasicTab(mTabHost);
        }

        if (mScrollView != null) {
            mScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mScrollView.scrollTo(0, prevScrollY);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // TAB 1: 자동 시너지 (5 Filter Chips x 4 Packages = 20 Packages)
    // -------------------------------------------------------------------------

    private void renderAutoSynergyTab(LinearLayout host) {
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.bottomMargin = dp(10);
        filterRow.setLayoutParams(fLp);

        SynergyCatalog.FilterGroup[] groups = SynergyCatalog.FilterGroup.values();
        for (int i = 0; i < groups.length; i++) {
            final SynergyCatalog.FilterGroup fg = groups[i];
            boolean sel = (fg == mFilterGroup);
            Button chip = makeCompactButton(fg.label, sel ? ACCENT_BLUE : BG_SURFACE, 34);
            chip.setTextColor(sel ? BG_ROOT : TXT_SEC);
            if (!sel) chip.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
            if (i < groups.length - 1) cLp.rightMargin = dp(4);
            chip.setLayoutParams(cLp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFilterGroup = fg;
                    renderActiveTab();
                }
            });
            filterRow.addView(chip);
        }
        host.addView(filterRow);

        if (Calendar.getInstance().get(Calendar.YEAR) < 2024 && SynergyAudioService.sAutoSchedule24h) {
            TextView clkWarn = new TextView(this);
            clkWarn.setText("기기 시계 확인 필요: 2024년 미만 시간에서는 24h 시간대 자동 전환이 어긋날 수 있습니다.");
            clkWarn.setTextColor(ACCENT_AMBER);
            setDpTextSize(clkWarn, 11.0f);
            clkWarn.setPadding(dp(10), dp(7), dp(10), dp(7));
            clkWarn.setBackground(makeBox(Color.parseColor("#1F160A"), ACCENT_AMBER, 8, 1));
            LinearLayout.LayoutParams cwLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cwLp.bottomMargin = dp(8);
            clkWarn.setLayoutParams(cwLp);
            host.addView(clkWarn);
        }

        if (mActiveChannel == SynergyChannel.CH4_TACTILE_SUB) {
            TextView note = new TextView(this);
            note.setText("CH 4 열보호 안내: 앰프 노브 12시(50~60%) 이하 권장 · 자동 듀티 휴지 적용");
            note.setTextColor(ACCENT_AMBER);
            setDpTextSize(note, 11.0f);
            note.setPadding(dp(10), dp(7), dp(10), dp(7));
            note.setBackground(makeBox(Color.parseColor("#1F160A"), ACCENT_AMBER, 8, 1));
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nLp.bottomMargin = dp(8);
            note.setLayoutParams(nLp);
            host.addView(note);
        }

        List<SynergyCatalog.SynergyPackage> list = SynergyCatalog.packagesByGroup(mFilterGroup);
        for (int i = 0; i < list.size(); i++) {
            host.addView(buildSynergyPackageCard(list.get(i)));
        }
    }

    private View buildSynergyPackageCard(final SynergyCatalog.SynergyPackage pkg) {
        final boolean isPlayingThis = SynergyAudioService.sPlayMode == SynergyAudioService.MODE_AUTO_SYNERGY
                && SynergyAudioService.sActivePackage != null
                && pkg.code.equals(SynergyAudioService.sActivePackage.code);
        SynergyCatalog.ChannelProgram prog = pkg.programFor(mActiveChannel);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_CARD, isPlayingThis ? ACCENT_GREEN : BORDER_LINE, 10, isPlayingThis ? 2 : 1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(pkg.code + " · " + pkg.title);
        tvTitle.setTextColor(isPlayingThis ? ACCENT_GREEN : TXT_PRIMARY);
        setDpTextSize(tvTitle, 13.0f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setMaxLines(2);
        configureBalancedWrap(tvTitle);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        top.addView(tvTitle);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(prog.formatDutyBadge());
        tvBadge.setTextColor(prog.dutyCoolMin > 0 ? ACCENT_AMBER : ACCENT_TEAL);
        setDpTextSize(tvBadge, 10.0f);
        tvBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvBadge.setSingleLine(true);
        tvBadge.setPadding(dp(7), dp(3), dp(7), dp(3));
        tvBadge.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        top.addView(tvBadge);
        card.addView(top);

        TextView tvMyRole = new TextView(this);
        tvMyRole.setText(mActiveChannel.shortLabel + ": " + prog.step.formatCompactSpec());
        tvMyRole.setTextColor(ACCENT_TEAL);
        setDpTextSize(tvMyRole, 11.5f);
        tvMyRole.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvMyRole.setMaxLines(2);
        configureBalancedWrap(tvMyRole);
        tvMyRole.setPadding(0, dp(5), 0, dp(2));
        card.addView(tvMyRole);

        TextView tvSummary = new TextView(this);
        tvSummary.setText(pkg.summary + " · " + prog.shortRoleNote);
        tvSummary.setTextColor(TXT_SEC);
        setDpTextSize(tvSummary, 11.0f);
        tvSummary.setMaxLines(2);
        configureBalancedWrap(tvSummary);
        card.addView(tvSummary);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlayingThis) {
                    SynergyAudioService.stopAllPlayback();
                } else {
                    SynergyStore.saveActivePackageCode(MainActivity.this, pkg.code);
                    SynergyAudioService.playAutoPackage(MainActivity.this, pkg);
                    showBanner(pkg.code + " (" + mActiveChannel.shortLabel + ") 진동 시작", true);
                }
                renderActiveTab();
                refreshLiveTelemetry();
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // TAB 2: 반수동 (Emergency 1-Tap + Chain Editor + Saved Sets + Mod/Duty/Trim)
    // -------------------------------------------------------------------------

    private void renderSemiManualTab(LinearLayout host) {
        addSectionHeader(host, "긴급 1탭 변조 · 즉시 전환");
        LinearLayout emRow = new LinearLayout(this);
        emRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eLp.bottomMargin = dp(10);
        emRow.setLayoutParams(eLp);

        Button btnBeat4 = makeCompactButton("+4Hz 맥놀이", BG_CARD, 36);
        Button btnDwell = makeCompactButton("스텝 체류", BG_CARD, 36);
        Button btnShift = makeCompactButton("주파수 시프트", BG_CARD, 36);
        Button btnCoolTrim = makeCompactButton("저발열 -3dB", BG_CARD, 36);

        Button[] emBtns = {btnBeat4, btnDwell, btnShift, btnCoolTrim};
        for (int i = 0; i < emBtns.length; i++) {
            emBtns[i].setTextColor(ACCENT_TEAL);
            emBtns[i].setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            if (i < emBtns.length - 1) bLp.rightMargin = dp(4);
            emBtns[i].setLayoutParams(bLp);
            emRow.addView(emBtns[i]);
        }
        host.addView(emRow);

        btnBeat4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float f1 = mActiveChannel.clampHz((mActiveChannel.sweetMinHz + mActiveChannel.sweetMaxHz) * 0.5f);
                float f2 = mActiveChannel.clampHz(f1 + 4.0f);
                if (Math.abs(f2 - f1) < 3.0f) f2 = mActiveChannel.clampHz(f1 - 4.0f);
                ScenarioStep quick = new ScenarioStep(
                        901, "긴급 +4Hz 제어 맥놀이", ScenarioStep.StepType.SINGLE,
                        f1, f1, f2, 300.0f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, 0f);
                SynergyAudioService.playQuickManual(MainActivity.this, quick);
                refreshLiveTelemetry();
                showBanner(String.format(Locale.US, "긴급 4Hz 맥놀이: %.1f + %.1f Hz", f1, f2), true);
            }
        });

        btnDwell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ScenarioStep quick = new ScenarioStep(
                        902, "긴급 4단 스텝 체류", ScenarioStep.StepType.STEPPED_DWELL,
                        mActiveChannel.sweetMinHz, mActiveChannel.sweetMaxHz, 0f, 240.0f, 4,
                        true, ModulationProfile.LOCKED_FM_DEPTH, ModulationProfile.LOCKED_FM_RATE_HZ,
                        false, 0f, 0f, false, 0f, 0f, true, true, 0f);
                SynergyAudioService.playQuickManual(MainActivity.this, quick);
                refreshLiveTelemetry();
                showBanner("긴급 4단 스텝 체류 시작 (" + mActiveChannel.bandLabel + ")", true);
            }
        });

        btnShift.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float cur = SynergyAudioService.sInstantDisplayHz > 0f
                        ? SynergyAudioService.sInstantDisplayHz
                        : mActiveChannel.sweetMinHz;
                float shifted = mActiveChannel.clampHz(cur + 3.5f);
                if (Math.abs(shifted - cur) < 1.0f) shifted = mActiveChannel.clampHz(cur - 3.5f);
                ScenarioStep quick = new ScenarioStep(
                        903, "긴급 주파수 시프트", ScenarioStep.StepType.SINGLE,
                        shifted, shifted, 0f, 300.0f, 2,
                        SynergyAudioService.sGlobalFmOn, ModulationProfile.LOCKED_FM_DEPTH, ModulationProfile.LOCKED_FM_RATE_HZ,
                        SynergyAudioService.sGlobalAmOn, ModulationProfile.LOCKED_AM_DEPTH, ModulationProfile.LOCKED_AM_RATE_HZ,
                        SynergyAudioService.sGlobalRmOn, ModulationProfile.LOCKED_RM_DEPTH, ModulationProfile.LOCKED_RM_RATE_HZ,
                        true, true, 0f);
                SynergyAudioService.playQuickManual(MainActivity.this, quick);
                refreshLiveTelemetry();
                showBanner(String.format(Locale.US, "내성 회피 시프트 -> %.1f Hz", shifted), true);
            }
        });

        btnCoolTrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float nextTrim = (SynergyAudioService.sGlobalTrimDb <= -2.5f) ? 0.0f : -3.0f;
                SynergyAudioService.sGlobalTrimDb = nextTrim;
                SynergyStore.saveTrimDb(MainActivity.this, nextTrim);
                renderActiveTab();
                refreshLiveTelemetry();
                showBanner(nextTrim < 0f ? "저발열 디지털 트림 -3 dB 적용" : "디지털 트림 0 dB 복원", true);
            }
        });

        // Quick channel recommended presets
        addSectionHeader(host, "채널 대역 빠른 주파수 (" + mActiveChannel.bandLabel + ")");
        LinearLayout qRow = new LinearLayout(this);
        qRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qLp.bottomMargin = dp(10);
        qRow.setLayoutParams(qLp);
        float[] qPresets = mActiveChannel.quickPresets();
        for (int i = 0; i < qPresets.length; i++) {
            final float hz = qPresets[i];
            Button qb = makeCompactButton(String.format(Locale.US, "%.1fHz", hz), BG_CARD, 36);
            qb.setTextColor(TXT_PRIMARY);
            qb.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            if (i < qPresets.length - 1) ql.rightMargin = dp(4);
            qb.setLayoutParams(ql);
            qb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ScenarioStep quick = new ScenarioStep(
                            910, String.format(Locale.US, "단일 %.1f Hz", hz), ScenarioStep.StepType.SINGLE,
                            hz, hz, 0f, 300.0f, 2,
                            SynergyAudioService.sGlobalFmOn, ModulationProfile.LOCKED_FM_DEPTH, ModulationProfile.LOCKED_FM_RATE_HZ,
                            SynergyAudioService.sGlobalAmOn, ModulationProfile.LOCKED_AM_DEPTH, ModulationProfile.LOCKED_AM_RATE_HZ,
                            SynergyAudioService.sGlobalRmOn, ModulationProfile.LOCKED_RM_DEPTH, ModulationProfile.LOCKED_RM_RATE_HZ,
                            true, SynergyAudioService.sGlobalJitter, 0f);
                    SynergyAudioService.playQuickManual(MainActivity.this, quick);
                    refreshLiveTelemetry();
                    showBanner(String.format(Locale.US, "%.1f Hz 즉시 재생", hz), true);
                }
            });
            qRow.addView(qb);
        }
        host.addView(qRow);

        // Chain actions: Add / Play All / Save Set / Load Set / Fleet JSON
        addSectionHeader(host, "사용자 혼합 체인 (단일 · 스윕 · 스텝 · 휴지)");
        LinearLayout act1 = new LinearLayout(this);
        act1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a1Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        a1Lp.bottomMargin = dp(6);
        act1.setLayoutParams(a1Lp);

        Button btnAddStep = makeCompactButton("+ 스텝 추가", ACCENT_BLUE, 38);
        btnAddStep.setTextColor(BG_ROOT);
        LinearLayout.LayoutParams asLp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        asLp.rightMargin = dp(5);
        btnAddStep.setLayoutParams(asLp);
        btnAddStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStepEditorDialog(null);
            }
        });
        act1.addView(btnAddStep);

        boolean anyManualPlaying = false;
        synchronized (SynergyAudioService.sLock) {
            for (int i = 0; i < SynergyAudioService.sManualSteps.size(); i++) {
                if (SynergyAudioService.sManualSteps.get(i).isPlaying) {
                    anyManualPlaying = true;
                    break;
                }
            }
        }
        final boolean stopAllChain = anyManualPlaying && SynergyAudioService.sPlayMode == SynergyAudioService.MODE_SEMI_MANUAL;
        Button btnChainPlay = makeCompactButton(stopAllChain ? "체인 정지" : "체인 전체 재생",
                stopAllChain ? ACCENT_RED : ACCENT_GREEN, 38);
        btnChainPlay.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        btnChainPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (SynergyAudioService.sLock) {
                    for (int i = 0; i < SynergyAudioService.sManualSteps.size(); i++) {
                        SynergyAudioService.sManualSteps.get(i).isPlaying = !stopAllChain;
                    }
                    SynergyStore.saveStepsNow(MainActivity.this, SynergyAudioService.sManualSteps);
                }
                if (stopAllChain) SynergyAudioService.stopAllPlayback();
                else SynergyAudioService.playManualChain(MainActivity.this);
                renderActiveTab();
                refreshLiveTelemetry();
            }
        });
        act1.addView(btnChainPlay);
        host.addView(act1);

        LinearLayout act2 = new LinearLayout(this);
        act2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        a2Lp.bottomMargin = dp(10);
        act2.setLayoutParams(a2Lp);

        Button btnSaveSet = makeCompactButton("세트 저장", BG_CARD, 36);
        Button btnLoadSet = makeCompactButton("불러오기", BG_CARD, 36);
        Button btnFleetJson = makeCompactButton("JSON 팩", BG_CARD, 36);
        Button btnResetSet = makeCompactButton("초기화", BG_SURFACE, 36);
        Button[] sBtns = {btnSaveSet, btnLoadSet, btnFleetJson, btnResetSet};
        for (int i = 0; i < sBtns.length; i++) {
            sBtns[i].setTextColor(TXT_PRIMARY);
            sBtns[i].setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            if (i < sBtns.length - 1) l.rightMargin = dp(4);
            sBtns[i].setLayoutParams(l);
            act2.addView(sBtns[i]);
        }
        host.addView(act2);

        btnSaveSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSaveSetDialog();
            }
        });
        btnLoadSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLoadSetDialog();
            }
        });
        btnFleetJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFleetJsonDialog();
            }
        });
        btnResetSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (SynergyAudioService.sLock) {
                    mNextStepId = SynergyStore.resetToStarterDefaults(
                            MainActivity.this, mActiveChannel, SynergyAudioService.sManualSteps);
                }
                renderActiveTab();
                showBanner("현재 채널 기본 스텝 3종으로 초기화됨", true);
            }
        });

        // Read-only custom step cards (zero inline EditText = zero ScrollView jumping!)
        synchronized (SynergyAudioService.sLock) {
            if (SynergyAudioService.sManualSteps.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("등록된 체인이 없습니다. [+ 스텝 추가]로 단일·스윕·스텝을 구성하세요.");
                empty.setTextColor(TXT_SEC);
                setDpTextSize(empty, 12.0f);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(14), dp(18), dp(14), dp(18));
                empty.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));
                LinearLayout.LayoutParams em = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                em.bottomMargin = dp(10);
                empty.setLayoutParams(em);
                host.addView(empty);
            } else {
                for (int i = 0; i < SynergyAudioService.sManualSteps.size(); i++) {
                    host.addView(buildStepCard(SynergyAudioService.sManualSteps.get(i)));
                }
            }
        }

        // Modulation & Protection controls
        addSectionHeader(host, "순수 변조 · 내성 지터 · 열보호 · 트림");
        renderModulationAndProtectionPanel(host);
    }

    private View buildStepCard(final ScenarioStep step) {
        final boolean playing = step.isPlaying && SynergyAudioService.sPlayMode == SynergyAudioService.MODE_SEMI_MANUAL
                && SynergyAudioService.sQuickManualStep == null;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_CARD, playing ? ACCENT_GREEN : BORDER_LINE, 10, playing ? 2 : 1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(step.title + " [" + step.type.label + "]");
        tvTitle.setTextColor(playing ? ACCENT_GREEN : TXT_PRIMARY);
        setDpTextSize(tvTitle, 12.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setMaxLines(2);
        configureBalancedWrap(tvTitle);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        top.addView(tvTitle);

        card.addView(top);

        TextView tvSpec = new TextView(this);
        tvSpec.setText(step.formatCompactSpec());
        tvSpec.setTextColor(ACCENT_AMBER);
        setDpTextSize(tvSpec, 11.0f);
        tvSpec.setTypeface(Typeface.MONOSPACE);
        tvSpec.setMaxLines(2);
        configureBalancedWrap(tvSpec);
        tvSpec.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvSpec);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPlay = makeCompactButton(playing ? "정지" : "재생",
                playing ? ACCENT_GREEN : Color.parseColor("#D97706"), 34);
        btnPlay.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (SynergyAudioService.sLock) {
                    step.isPlaying = !playing;
                    SynergyStore.saveStepsNow(MainActivity.this, SynergyAudioService.sManualSteps);
                }
                if (SynergyAudioService.isAnySourceActive()) {
                    SynergyAudioService.playManualChain(MainActivity.this);
                } else {
                    SynergyAudioService.stopAllPlayback();
                }
                renderActiveTab();
                refreshLiveTelemetry();
            }
        });
        ctrl.addView(btnPlay);

        Button btnEdit = makeCompactButton("편집", Color.parseColor("#0284C7"), 34);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, dp(34), 0.7f);
        eLp.leftMargin = dp(5);
        btnEdit.setLayoutParams(eLp);
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStepEditorDialog(step);
            }
        });
        ctrl.addView(btnEdit);

        Button btnDel = makeCompactButton("삭제", BG_SURFACE, 34);
        btnDel.setTextColor(TXT_SEC);
        btnDel.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 6, 1));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(34), 0.6f);
        dLp.leftMargin = dp(5);
        btnDel.setLayoutParams(dLp);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (SynergyAudioService.sLock) {
                    step.isPlaying = false;
                    SynergyAudioService.sManualSteps.remove(step);
                    SynergyStore.saveStepsNow(MainActivity.this, SynergyAudioService.sManualSteps);
                }
                renderActiveTab();
                showBanner("스텝 삭제 및 저장 완료", true);
            }
        });
        ctrl.addView(btnDel);

        card.addView(ctrl);
        return card;
    }

    private void renderModulationAndProtectionPanel(LinearLayout host) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));

        // 3 locked modulation buttons + OFF
        LinearLayout modRow = new LinearLayout(this);
        modRow.setOrientation(LinearLayout.HORIZONTAL);
        boolean[] flags = {SynergyAudioService.sGlobalFmOn, SynergyAudioService.sGlobalAmOn, SynergyAudioService.sGlobalRmOn};
        String[] mLabels = {"FM 3.5%", "AM 15%", "RM 25%", "변조 OFF"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean on = (i < 3) && flags[i];
            Button mb = makeCompactButton(mLabels[i], on ? ACCENT_TEAL : BG_CARD, 36);
            mb.setTextColor(on ? BG_ROOT : TXT_PRIMARY);
            if (!on) mb.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            if (i < 3) l.rightMargin = dp(4);
            mb.setLayoutParams(l);
            mb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == 0) SynergyAudioService.sGlobalFmOn = !SynergyAudioService.sGlobalFmOn;
                    else if (idx == 1) SynergyAudioService.sGlobalAmOn = !SynergyAudioService.sGlobalAmOn;
                    else if (idx == 2) SynergyAudioService.sGlobalRmOn = !SynergyAudioService.sGlobalRmOn;
                    else {
                        SynergyAudioService.sGlobalFmOn = false;
                        SynergyAudioService.sGlobalAmOn = false;
                        SynergyAudioService.sGlobalRmOn = false;
                    }
                    SynergyStore.saveModulationToggles(MainActivity.this,
                            SynergyAudioService.sGlobalFmOn,
                            SynergyAudioService.sGlobalAmOn,
                            SynergyAudioService.sGlobalRmOn);
                    renderActiveTab();
                    refreshLiveTelemetry();
                }
            });
            modRow.addView(mb);
        }
        box.addView(modRow);

        // Anti-habituation Jitter + Duty Cycle + Digital Trim
        LinearLayout govRow = new LinearLayout(this);
        govRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = dp(8);
        govRow.setLayoutParams(gLp);

        Button btnJitter = makeCompactButton(SynergyAudioService.sGlobalJitter ? "내성 지터 ON" : "내성 지터 OFF",
                SynergyAudioService.sGlobalJitter ? ACCENT_BLUE : BG_CARD, 36);
        btnJitter.setTextColor(SynergyAudioService.sGlobalJitter ? BG_ROOT : TXT_SEC);
        if (!SynergyAudioService.sGlobalJitter) btnJitter.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams jLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        jLp.rightMargin = dp(5);
        btnJitter.setLayoutParams(jLp);
        btnJitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SynergyAudioService.sGlobalJitter = !SynergyAudioService.sGlobalJitter;
                SynergyStore.saveJitterEnabled(MainActivity.this, SynergyAudioService.sGlobalJitter);
                renderActiveTab();
            }
        });
        govRow.addView(btnJitter);

        String[] dutyLabels = {"듀티: 역할기본", "듀티: 연속", "듀티: 20/10분", "듀티: 15/15분"};
        int dm = Math.max(0, Math.min(3, SynergyAudioService.sDutyMode));
        Button btnDuty = makeCompactButton(dutyLabels[dm], BG_CARD, 36);
        btnDuty.setTextColor(ACCENT_AMBER);
        btnDuty.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
        btnDuty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnDuty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int next = (SynergyAudioService.sDutyMode + 1) % 4;
                SynergyAudioService.sDutyMode = next;
                SynergyStore.saveDutyMode(MainActivity.this, next);
                renderActiveTab();
                refreshLiveTelemetry();
            }
        });
        govRow.addView(btnDuty);
        box.addView(govRow);

        // Digital Soft Trim (-12..0 dB)
        LinearLayout trimRow = new LinearLayout(this);
        trimRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(8);
        trimRow.setLayoutParams(tLp);
        float[] trims = {0.0f, -2.0f, -4.0f, -6.0f};
        for (int i = 0; i < trims.length; i++) {
            final float tv = trims[i];
            boolean sel = Math.abs(SynergyAudioService.sGlobalTrimDb - tv) < 0.2f;
            Button tb = makeCompactButton(tv == 0f ? "트림 0dB" : String.format(Locale.US, "%.0fdB", tv),
                    sel ? ACCENT_TEAL : BG_CARD, 34);
            tb.setTextColor(sel ? BG_ROOT : TXT_SEC);
            if (!sel) tb.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
            if (i < trims.length - 1) tl.rightMargin = dp(4);
            tb.setLayoutParams(tl);
            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SynergyAudioService.sGlobalTrimDb = tv;
                    SynergyStore.saveTrimDb(MainActivity.this, tv);
                    renderActiveTab();
                    refreshLiveTelemetry();
                }
            });
            trimRow.addView(tb);
        }
        box.addView(trimRow);

        // Hardware Stabilizer Toggle + Rattle Notch Filter
        LinearLayout stabRow = new LinearLayout(this);
        stabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(8);
        stabRow.setLayoutParams(stLp);

        boolean stabOn = SynergyAudioService.sStabilizerEnabled;
        Button btnStab = makeCompactButton(stabOn ? "하드웨어 보호 ON" : "하드웨어 보호 OFF",
                stabOn ? ACCENT_GREEN : BG_CARD, 36);
        btnStab.setTextColor(stabOn ? BG_ROOT : TXT_SEC);
        if (!stabOn) btnStab.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        sbLp.rightMargin = dp(5);
        btnStab.setLayoutParams(sbLp);
        btnStab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !SynergyAudioService.sStabilizerEnabled;
                SynergyAudioService.sStabilizerEnabled = next;
                SynergyStore.saveStabilizerEnabled(MainActivity.this, next);
                renderActiveTab();
                refreshLiveTelemetry();
                HardwareStabilizer.Profile prof = HardwareStabilizer.profileFor(mActiveChannel);
                showBanner(next ? (prof.commercialLabel + " 활성화") : "하드웨어 보호 비활성화 (원음 직결)", true);
            }
        });
        stabRow.addView(btnStab);

        String notchTxt = (SynergyAudioService.sNotchCenterHz > 0.0f)
                ? String.format(Locale.US, "잡음 회피 %.1fHz", SynergyAudioService.sNotchCenterHz)
                : "잡음 대역 회피";
        Button btnNotch = makeCompactButton(notchTxt,
                SynergyAudioService.sNotchCenterHz > 0.0f ? ACCENT_AMBER : BG_CARD, 36);
        btnNotch.setTextColor(SynergyAudioService.sNotchCenterHz > 0.0f ? BG_ROOT : TXT_PRIMARY);
        if (SynergyAudioService.sNotchCenterHz <= 0.0f) btnNotch.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
        btnNotch.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnNotch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNotchFilterDialog();
            }
        });
        stabRow.addView(btnNotch);
        box.addView(stabRow);

        host.addView(box);
    }

    // -------------------------------------------------------------------------
    // TAB 3: 기초 (22 Concise Entries with Channel Band Guardrail)
    // -------------------------------------------------------------------------

    private void renderBasicTab(LinearLayout host) {
        TextView info = new TextView(this);
        info.setText("기초 대역 스윕(10) · 단독 공진(12) — 현재 채널(" + mActiveChannel.bandLabel + ") 범위 자동 보호");
        info.setTextColor(TXT_SEC);
        setDpTextSize(info, 11.0f);
        info.setPadding(dp(2), 0, dp(2), dp(8));
        host.addView(info);

        List<SynergyCatalog.BasicEntry> list = SynergyCatalog.basicCatalog();
        for (int i = 0; i < list.size(); i++) {
            final SynergyCatalog.BasicEntry entry = list.get(i);
            final boolean isPlaying = SynergyAudioService.sPlayMode == SynergyAudioService.MODE_BASIC
                    && SynergyAudioService.sActiveBasic != null
                    && entry.id.equals(SynergyAudioService.sActiveBasic.id);
            final boolean inBand = mActiveChannel.isInBand(entry.step.minHz)
                    && (entry.step.type == ScenarioStep.StepType.SINGLE || mActiveChannel.isInBand(entry.step.maxHz));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(7);
            card.setLayoutParams(lp);
            card.setBackground(makeBox(BG_CARD, isPlaying ? ACCENT_GREEN : BORDER_LINE, 8, isPlaying ? 2 : 1));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(entry.title);
            tvTitle.setTextColor(isPlaying ? ACCENT_GREEN : TXT_PRIMARY);
            setDpTextSize(tvTitle, 12.5f);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setMaxLines(2);
            configureBalancedWrap(tvTitle);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            top.addView(tvTitle);

            TextView tvRec = new TextView(this);
            tvRec.setText("권장 " + entry.recommendedChannel.shortLabel);
            tvRec.setTextColor(inBand ? ACCENT_TEAL : ACCENT_AMBER);
            setDpTextSize(tvRec, 10.0f);
            tvRec.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvRec.setSingleLine(true);
            tvRec.setPadding(dp(6), dp(2), dp(6), dp(2));
            tvRec.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
            top.addView(tvRec);
            card.addView(top);

            TextView tvDesc = new TextView(this);
            tvDesc.setText(entry.effectDesc);
            tvDesc.setTextColor(TXT_SEC);
            setDpTextSize(tvDesc, 11.0f);
            tvDesc.setMaxLines(2);
            configureBalancedWrap(tvDesc);
            tvDesc.setPadding(0, dp(3), 0, 0);
            card.addView(tvDesc);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isPlaying) {
                        SynergyAudioService.stopAllPlayback();
                        renderActiveTab();
                        refreshLiveTelemetry();
                        return;
                    }
                    if (!inBand) {
                        showBasicGuardDialog(entry);
                        return;
                    }
                    SynergyAudioService.playBasicEntry(MainActivity.this, entry);
                    renderActiveTab();
                    refreshLiveTelemetry();
                    showBanner(entry.title + " 재생 시작", true);
                }
            });
            host.addView(card);
        }
    }

    // -------------------------------------------------------------------------
    // DIALOGS (SynergyDialogTheme + SOFT_INPUT_ADJUST_RESIZE — In-app banner!)
    // -------------------------------------------------------------------------

    private void showBasicGuardDialog(final SynergyCatalog.BasicEntry entry) {
        float cMin = mActiveChannel.clampHz(entry.step.minHz);
        float cMax = mActiveChannel.clampHz(entry.step.maxHz);
        String msg = String.format(Locale.US,
                "선택한 항목은 %s(%s) 권장 대역입니다.\n현재 채널(%s · %s) 범위로 안전 클램프(%.1f~%.1f Hz)하여 재생할까요?",
                entry.recommendedChannel.displayName, entry.recommendedChannel.bandLabel,
                mActiveChannel.displayName, mActiveChannel.bandLabel, cMin, cMax);
        AlertDialog dlg = new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("채널 대역 보호 안내")
                .setMessage(msg)
                .setPositiveButton("보정 재생", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SynergyAudioService.playBasicEntry(MainActivity.this, entry);
                        renderActiveTab();
                        refreshLiveTelemetry();
                        showBanner("채널 대역 안전 보정 재생 시작", true);
                    }
                })
                .setNegativeButton("취소", null)
                .create();
        dlg.show();
    }

    private void showStepEditorDialog(final ScenarioStep existing) {
        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(8));

        final EditText etTitle = makeDialogInput(body, "스텝 이름", existing != null ? existing.title : "사용자 스텝", InputType.TYPE_CLASS_TEXT);
        final ScenarioStep.StepType[] types = ScenarioStep.StepType.values();
        final int[] typeIdx = {existing != null ? existing.type.ordinal() : 1};
        final Button btnType = makeCompactButton("모드: " + types[typeIdx[0]].label, BG_CARD, 36);
        btnType.setTextColor(ACCENT_TEAL);
        btnType.setBackground(makeBox(BG_CARD, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams btLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        btLp.bottomMargin = dp(8);
        btnType.setLayoutParams(btLp);
        btnType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                typeIdx[0] = (typeIdx[0] + 1) % types.length;
                btnType.setText("모드: " + types[typeIdx[0]].label);
            }
        });
        body.addView(btnType);

        final EditText etMin = makeDialogInput(body, "시작/단일 Hz (" + mActiveChannel.bandLabel + ")",
                String.format(Locale.US, "%.1f", existing != null ? existing.minHz : mActiveChannel.sweetMinHz),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText etMax = makeDialogInput(body, "종료 Hz (스윕/스텝용)",
                String.format(Locale.US, "%.1f", existing != null ? existing.maxHz : mActiveChannel.sweetMaxHz),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText etSec = makeDialogInput(body, "주기/시간(초)",
                String.format(Locale.US, "%.0f", existing != null ? existing.durationSec : 300f),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText etSteps = makeDialogInput(body, "스텝 단계 수 (2~12)",
                String.valueOf(existing != null ? existing.stepCount : 4),
                InputType.TYPE_CLASS_NUMBER);
        final EditText etDual = makeDialogInput(body, "이중음 보조 Hz (0=사용안함)",
                String.format(Locale.US, "%.1f", existing != null ? existing.secondaryHz : 0f),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sv.addView(body);

        AlertDialog dlg = new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle(existing != null ? "스텝 편집" : "새 스텝 추가")
                .setView(sv)
                .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = etTitle.getText().toString().trim();
                        if (title.isEmpty()) title = "사용자 스텝";
                        float rawMin = parseFloatSafe(etMin.getText().toString(), mActiveChannel.sweetMinHz);
                        float rawMax = parseFloatSafe(etMax.getText().toString(), mActiveChannel.sweetMaxHz);
                        float dur = Math.max(5.0f, parseFloatSafe(etSec.getText().toString(), 300.0f));
                        int stepN = Math.max(2, Math.min(12, (int) parseFloatSafe(etSteps.getText().toString(), 4f)));
                        float rawDual = parseFloatSafe(etDual.getText().toString(), 0f);

                        float cMin = mActiveChannel.clampHz(rawMin);
                        float cMax = mActiveChannel.clampHz(rawMax);
                        float cDual = rawDual > 0f ? mActiveChannel.clampHz(rawDual) : 0f;
                        boolean clamped = (Math.abs(cMin - rawMin) > 0.05f)
                                || (Math.abs(cMax - rawMax) > 0.05f)
                                || (rawDual > 0f && Math.abs(cDual - rawDual) > 0.05f);

                        boolean savedOk;
                        synchronized (SynergyAudioService.sLock) {
                            if (existing != null) {
                                existing.title = title;
                                existing.type = types[typeIdx[0]];
                                existing.minHz = cMin;
                                existing.maxHz = cMax;
                                existing.durationSec = dur;
                                existing.stepCount = stepN;
                                existing.secondaryHz = cDual;
                            } else {
                                SynergyAudioService.sManualSteps.add(new ScenarioStep(
                                        mNextStepId++, title, types[typeIdx[0]],
                                        cMin, cMax, cDual, dur, stepN,
                                        false, 0f, 0f, false, 0f, 0f, false, 0f, 0f,
                                        true, SynergyAudioService.sGlobalJitter, 0f));
                            }
                            savedOk = SynergyStore.saveStepsNow(MainActivity.this, SynergyAudioService.sManualSteps);
                        }
                        renderActiveTab();
                        if (!savedOk) {
                            showBanner("저장 실패: 기기 저장소 쓰기 오류", false);
                        } else if (clamped) {
                            showBanner("채널 보호(" + mActiveChannel.bandLabel + ") 범위로 보정 저장됨", true);
                        } else {
                            showBanner("스텝 저장 완료: " + title, true);
                        }
                    }
                })
                .setNegativeButton("취소", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    private void showSaveSetDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(6));
        final EditText et = makeDialogInput(box, "저장할 세트 이름", "", InputType.TYPE_CLASS_TEXT);

        AlertDialog dlg = new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("사용자 체인 세트 저장")
                .setView(box)
                .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) {
                            showBanner("세트 이름을 입력하세요", false);
                            return;
                        }
                        boolean ok;
                        synchronized (SynergyAudioService.sLock) {
                            ok = SynergyStore.saveNamedSet(MainActivity.this, name, SynergyAudioService.sManualSteps);
                        }
                        showBanner(ok ? ("세트 저장 완료: " + name) : "세트 저장 실패", ok);
                    }
                })
                .setNegativeButton("취소", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    private void showLoadSetDialog() {
        final List<String> names = SynergyStore.listNamedSets(this);
        if (names.isEmpty()) {
            showBanner("저장된 사용자 세트가 없습니다", false);
            return;
        }
        final String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("저장 세트 불러오기")
                .setItems(arr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String chosen = arr[which];
                        boolean ok;
                        synchronized (SynergyAudioService.sLock) {
                            ok = SynergyStore.loadNamedSet(MainActivity.this, chosen, SynergyAudioService.sManualSteps);
                        }
                        renderActiveTab();
                        showBanner(ok ? ("불러옴: " + chosen) : "불러오기 실패", ok);
                    }
                })
                .setNeutralButton("세트 삭제", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDeleteSetDialog(arr);
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showDeleteSetDialog(final String[] arr) {
        new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("삭제할 세트 선택")
                .setItems(arr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean ok = SynergyStore.deleteNamedSet(MainActivity.this, arr[which]);
                        showBanner(ok ? ("삭제됨: " + arr[which]) : "삭제 실패", ok);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showFleetJsonDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(6));

        final String exported;
        synchronized (SynergyAudioService.sLock) {
            exported = SynergyStore.exportFleetJson(
                    mActiveChannel,
                    SynergyAudioService.sActivePackageCode,
                    SynergyAudioService.sAutoSchedule24h,
                    SynergyAudioService.sGlobalJitter,
                    SynergyAudioService.sDutyMode,
                    SynergyAudioService.sGlobalTrimDb,
                    SynergyAudioService.sStabilizerEnabled,
                    SynergyAudioService.sNotchCenterHz,
                    SynergyAudioService.sNotchHalfWidthHz,
                    SynergyAudioService.sManualSteps);
        }
        final EditText et = makeDialogInput(box, "JSON 설정 팩", exported,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(4);
        et.setMaxLines(7);

        AlertDialog dlg = new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("설정 팩 내보내기 · 가져오기")
                .setView(box)
                .setPositiveButton("가져오기 적용", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SynergyStore.FleetImportResult res = SynergyStore.importFleetJson(et.getText().toString());
                        if (!res.valid) {
                            showBanner("유효하지 않은 JSON 설정 팩입니다", false);
                            return;
                        }
                        mActiveChannel = res.channel;
                        SynergyStore.saveActiveChannel(MainActivity.this, res.channel);
                        SynergyAudioService.setActiveChannel(res.channel);
                        SynergyAudioService.sAutoSchedule24h = res.auto24h;
                        SynergyStore.saveAutoSchedule24h(MainActivity.this, res.auto24h);
                        SynergyAudioService.sGlobalJitter = res.jitter;
                        SynergyStore.saveJitterEnabled(MainActivity.this, res.jitter);
                        SynergyAudioService.sDutyMode = res.dutyMode;
                        SynergyStore.saveDutyMode(MainActivity.this, res.dutyMode);
                        SynergyAudioService.sGlobalTrimDb = res.trimDb;
                        SynergyStore.saveTrimDb(MainActivity.this, res.trimDb);
                        SynergyAudioService.sStabilizerEnabled = res.stabilizerOn;
                        SynergyStore.saveStabilizerEnabled(MainActivity.this, res.stabilizerOn);
                        SynergyAudioService.sNotchCenterHz = res.notchCenterHz;
                        SynergyAudioService.sNotchHalfWidthHz = res.notchHalfWidthHz;
                        SynergyStore.saveNotchFilter(MainActivity.this, res.channel, res.notchCenterHz, res.notchHalfWidthHz);
                        synchronized (SynergyAudioService.sLock) {
                            SynergyAudioService.sManualSteps.clear();
                            SynergyAudioService.sManualSteps.addAll(res.steps);
                            SynergyStore.saveStepsNow(MainActivity.this, SynergyAudioService.sManualSteps);
                        }
                        updateChannelDrawerUI();
                        updateUtilityButtons();
                        renderActiveTab();
                        showBanner("설정 팩 가져오기 완료 (" + res.channel.shortLabel + ")", true);
                    }
                })
                .setNeutralButton("복사", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("vorta_synergy_pack", exported));
                            showBanner("설정 팩이 클립보드에 복사되었습니다", true);
                        }
                    }
                })
                .setNegativeButton("닫기", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    private void showNotchFilterDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(6));

        TextView guide = new TextView(this);
        guide.setText("기계적 덜덜거림이 발생하는 특정 주파수 주변을 -12 dB 부드럽게 감쇄합니다. (0 입력 시 해제)");
        guide.setTextColor(TXT_SEC);
        setDpTextSize(guide, 11.0f);
        guide.setPadding(0, 0, 0, dp(8));
        box.addView(guide);

        final EditText etCenter = makeDialogInput(box, "회피 중심 Hz (" + mActiveChannel.bandLabel + ")",
                SynergyAudioService.sNotchCenterHz > 0f ? String.format(Locale.US, "%.1f", SynergyAudioService.sNotchCenterHz) : "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText etWidth = makeDialogInput(box, "감쇄 폭 ±Hz (기본 1.5)",
                String.format(Locale.US, "%.1f", SynergyAudioService.sNotchHalfWidthHz),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog dlg = new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("잡음 대역 회피 노치 설정")
                .setView(box)
                .setPositiveButton("적용 저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        float rawC = parseFloatSafe(etCenter.getText().toString(), 0.0f);
                        float rawW = Math.max(0.5f, Math.min(5.0f, parseFloatSafe(etWidth.getText().toString(), 1.5f)));
                        float c = rawC > 0.0f ? mActiveChannel.clampHz(rawC) : 0.0f;
                        SynergyAudioService.sNotchCenterHz = c;
                        SynergyAudioService.sNotchHalfWidthHz = rawW;
                        boolean ok = SynergyStore.saveNotchFilter(MainActivity.this, mActiveChannel, c, rawW);
                        renderActiveTab();
                        refreshLiveTelemetry();
                        if (!ok) showBanner("회피 설정 저장 실패", false);
                        else if (c <= 0.0f) showBanner("잡음 대역 회피 해제됨", true);
                        else showBanner(String.format(Locale.US, "%.1f ± %.1f Hz (-12dB) 회피 적용", c, rawW), true);
                    }
                })
                .setNeutralButton("해제", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SynergyAudioService.sNotchCenterHz = 0.0f;
                        SynergyStore.saveNotchFilter(MainActivity.this, mActiveChannel, 0.0f, SynergyAudioService.sNotchHalfWidthHz);
                        renderActiveTab();
                        refreshLiveTelemetry();
                        showBanner("잡음 대역 회피 해제됨", true);
                    }
                })
                .setNegativeButton("취소", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    private void showSelfDiagnosticDialog() {
        new AlertDialog.Builder(this, R.style.SynergyDialogTheme)
                .setTitle("실시간 DSP · 안전장치 진단")
                .setMessage(SynergyAudioService.buildSelfDiagnosticReport(this))
                .setPositiveButton("닫기", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // IN-APP CONFIRMATION BANNER (Never suppressed by API 24/26 Toast blocks)
    // -------------------------------------------------------------------------

    private void buildInAppBanner(FrameLayout frame) {
        mBannerCard = new LinearLayout(this);
        mBannerCard.setOrientation(LinearLayout.HORIZONTAL);
        mBannerCard.setGravity(Gravity.CENTER_VERTICAL);
        mBannerCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        mBannerCard.setBackground(makeBox(Color.parseColor("#0F1D18"), ACCENT_GREEN, 10, 1));
        mBannerCard.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = dp(14);
        lp.rightMargin = dp(14);
        lp.bottomMargin = dp(14);
        mBannerCard.setLayoutParams(lp);

        mTvBanner = new TextView(this);
        mTvBanner.setTextColor(TXT_PRIMARY);
        setDpTextSize(mTvBanner, 12.0f);
        mTvBanner.setTypeface(Typeface.DEFAULT_BOLD);
        mTvBanner.setMaxLines(2);
        configureBalancedWrap(mTvBanner);
        mBannerCard.addView(mTvBanner);
        frame.addView(mBannerCard);
    }

    private void showBanner(String msg, boolean success) {
        if (mBannerCard == null || mTvBanner == null) return;
        mTvBanner.setText((success ? "✓ " : "! ") + msg);
        mBannerCard.setBackground(makeBox(
                success ? Color.parseColor("#0F1F18") : Color.parseColor("#2A1215"),
                success ? ACCENT_GREEN : ACCENT_RED, 10, 1));
        mBannerCard.setVisibility(View.VISIBLE);
        mUiHandler.removeCallbacks(mHideBannerRunnable);
        mUiHandler.postDelayed(mHideBannerRunnable, 2600L);
    }

    // -------------------------------------------------------------------------
    // UI FACTORY HELPERS (DP-locked text sizing + clean typography)
    // -------------------------------------------------------------------------

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(TXT_SEC);
        setDpTextSize(tv, 11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(2), dp(2), dp(2), dp(6));
        parent.addView(tv);
    }

    private TextView makeInfoChip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TXT_SEC);
        setDpTextSize(tv, 10.0f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(5), dp(4), dp(5), dp(4));
        tv.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        return tv;
    }

    private Button makeCompactButton(String text, int bgColor, int heightDp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(TXT_PRIMARY);
        setDpTextSize(b, 11.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(heightDp));
        b.setMinimumHeight(dp(heightDp));
        b.setPadding(dp(6), dp(2), dp(6), dp(2));
        b.setBackground(makeBox(bgColor, bgColor, 8, 0));
        return b;
    }

    private EditText makeDialogInput(LinearLayout parent, String label, String initialVal, int inputType) {
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(TXT_SEC);
        setDpTextSize(lbl, 11.0f);
        lbl.setPadding(0, dp(4), 0, dp(3));
        parent.addView(lbl);

        EditText et = new EditText(this);
        et.setText(initialVal);
        et.setTextColor(TXT_PRIMARY);
        et.setHintTextColor(TXT_DIM);
        setDpTextSize(et, 13.0f);
        et.setInputType(inputType);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        et.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        et.setLayoutParams(lp);
        parent.addView(et);
        return et;
    }

    private static void setDpTextSize(TextView tv, float dpSize) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, dpSize);
    }

    private static void configureBalancedWrap(TextView tv) {
        if (Build.VERSION.SDK_INT >= 23) {
            tv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        }
    }

    private static float parseFloatSafe(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable makeBox(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }

    private GradientDrawable makeOval(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        return gd;
    }
}
