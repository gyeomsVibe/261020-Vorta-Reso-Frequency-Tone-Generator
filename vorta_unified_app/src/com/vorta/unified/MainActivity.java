package com.vorta.unified;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorta Unified v5.2 Acoustic Console:
 * - Left-top interactive accordion menu bar (`1. 보르타 Pro -> 2. 갤럭시 A30 -> 3. 갤럭시 S7 -> 4. 갤럭시 S6`)
 * - Legacy 1-tap whole-card execution + direct 5-tab bar (`메인 | 서브1 | 서브2 | 공통 | 기초`) + LED beacon
 * - Real-time Canvas oscilloscope + zero-hallucination THD+N / clipping / AudioTrack underrun verifier
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

    private VortaModule mActiveModule;
    private boolean mMenuOpen = false;

    // Left-top accordion menu views
    private Button mBtnLeftModuleMenu;
    private TextView mTvTopAuxBadge;
    private LinearLayout mModuleAccordionDrawer;
    private final Button[] mModuleDrawerButtons = new Button[VortaModule.values().length];

    // Telemetry strip views
    private LinearLayout mTelemetryPanel;
    private View mTeleLedDot;
    private TextView mTvTeleState;
    private TextView mTvTeleGuard;
    private TextView mTvTeleHz;
    private LiveWaveformView mScopeView;
    private TextView mTvTelePipeline;
    private TextView mTvTeleMod;
    private TextView mTvTeleDesc;
    private Button mBtnKeepScreenOn;
    private Button mBtnMasterStop;
    private boolean mKeepScreenOn = false;
    private boolean mWasPlayingForScope = false;

    // Module workspace root
    private LinearLayout mWorkspaceHost;
    private int mDeviceSubTab = 0; // 0:메인, 1:서브1, 2:서브2, 3:공통, 4:기초

    // Pro workspace state
    private int mProSubTab = 0;
    private int mNextSweepId = 1;
    private final Button[] mProTabBtns = new Button[4];
    private final LinearLayout[] mProTabContainers = new LinearLayout[4];
    private LinearLayout mSweepListContainer;
    private TextView mTvSweepModSummary;
    private Button mBtnGlobalPlayStop;
    private TextView mTvSingleFreqDisplay;
    private EditText mEtSingleFreqInput;
    private EditText mEtSecondaryFreqInput;
    private Button mBtnDualToggle;
    private Button mBtnSinglePlayToggle;
    private LinearLayout mProModContainer;
    private LinearLayout mProScenListContainer;

    private final List<AccordionHandle> mCurrentAccordions = new ArrayList<AccordionHandle>();
    private boolean mAllAccordionsExpanded = false;
    private int mLastTeleBorderColor = Integer.MIN_VALUE;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryTick = new Runnable() {
        @Override
        public void run() {
            refreshLiveTelemetry();
            mUiHandler.postDelayed(this, VortaAudioService.isAnySourceActive() ? 650 : 1000);
        }
    };

    private static final class AccordionHandle {
        final View body;
        final TextView indicator;

        AccordionHandle(View body, TextView indicator) {
            this.body = body;
            this.indicator = indicator;
        }

        void setExpanded(boolean expanded) {
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            indicator.setText(expanded ? "접기 ▲" : "펼치기 ▼");
            indicator.setTextColor(expanded ? ACCENT_TEAL : TXT_SEC);
        }

        boolean isExpanded() {
            return body.getVisibility() == View.VISIBLE;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mKeepScreenOn = SweepStore.loadKeepScreenOn(this);
        applyKeepScreenFlag(mKeepScreenOn);
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            }
        }

        VortaAudioService.startServiceCompat(this);
        VortaVolumeGuard.syncTargetFromSystem(this);
        VortaVolumeGuard.ensureTargetVolumeIfNeeded(this);

        synchronized (VortaAudioService.sLock) {
            mNextSweepId = SweepStore.loadInto(this, VortaAudioService.sSweepItems);
            SweepStore.loadManualAndModIntoEngine(this);
        }
        mActiveModule = SweepStore.loadActiveModule(this, BuildProfile.DEFAULT_MODULE);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundColor(BG_ROOT);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(dp(12), dp(10), dp(12), dp(36));

        buildTopAccordionNavBar(root);
        buildTelemetryPanel(root);

        mWorkspaceHost = new LinearLayout(this);
        mWorkspaceHost.setOrientation(LinearLayout.VERTICAL);
        mWorkspaceHost.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(mWorkspaceHost);

        scroll.addView(root);
        setContentView(scroll);

        renderActiveWorkspace();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUiHandler.post(mTelemetryTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryTick);
    }

    // -------------------------------------------------------------------------
    // LEFT-TOP ACCORDION MODULE MENU BAR
    // -------------------------------------------------------------------------

    private void buildTopAccordionNavBar(LinearLayout root) {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(8));

        // Left-top accordion trigger button
        mBtnLeftModuleMenu = new Button(this);
        mBtnLeftModuleMenu.setAllCaps(false);
        mBtnLeftModuleMenu.setTextSize(12.5f);
        mBtnLeftModuleMenu.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnLeftModuleMenu.setTextColor(TXT_PRIMARY);
        mBtnLeftModuleMenu.setSingleLine(true);
        mBtnLeftModuleMenu.setEllipsize(TextUtils.TruncateAt.END);
        mBtnLeftModuleMenu.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        mBtnLeftModuleMenu.setMinWidth(0);
        mBtnLeftModuleMenu.setMinimumWidth(0);
        mBtnLeftModuleMenu.setPadding(dp(10), dp(8), dp(10), dp(8));
        mBtnLeftModuleMenu.setBackground(makeBox(BG_SURFACE, ACCENT_TEAL, 8, 1));
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
        mBtnLeftModuleMenu.setLayoutParams(menuLp);
        mBtnLeftModuleMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleModuleMenu(!mMenuOpen);
            }
        });
        topBar.addView(mBtnLeftModuleMenu);

        // Right-side OS & AUX compact pill
        mTvTopAuxBadge = new TextView(this);
        mTvTopAuxBadge.setText(BuildProfile.FLAVOR_ID.split("_")[0]);
        mTvTopAuxBadge.setTextColor(ACCENT_TEAL);
        mTvTopAuxBadge.setTextSize(10);
        mTvTopAuxBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvTopAuxBadge.setSingleLine(true);
        mTvTopAuxBadge.setGravity(Gravity.CENTER);
        mTvTopAuxBadge.setPadding(dp(7), dp(6), dp(7), dp(6));
        mTvTopAuxBadge.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        badgeLp.leftMargin = dp(6);
        mTvTopAuxBadge.setLayoutParams(badgeLp);
        topBar.addView(mTvTopAuxBadge);

        root.addView(topBar);

        // Collapsible left-aligned accordion drawer for selecting the 4 Vorta modules
        mModuleAccordionDrawer = new LinearLayout(this);
        mModuleAccordionDrawer.setOrientation(LinearLayout.VERTICAL);
        mModuleAccordionDrawer.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams drawerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        drawerLp.bottomMargin = dp(10);
        mModuleAccordionDrawer.setLayoutParams(drawerLp);
        mModuleAccordionDrawer.setBackground(makeBox(BG_SURFACE, ACCENT_TEAL, 10, 1));
        mModuleAccordionDrawer.setVisibility(View.GONE);

        TextView drawerHdr = new TextView(this);
        drawerHdr.setText("보르타 모듈 선택  ·  " + BuildProfile.TARGET_OS_LABEL);
        drawerHdr.setTextColor(TXT_SEC);
        drawerHdr.setTextSize(11);
        drawerHdr.setPadding(dp(2), 0, dp(2), dp(8));
        mModuleAccordionDrawer.addView(drawerHdr);

        VortaModule[] modules = VortaModule.values();
        for (int i = 0; i < modules.length; i++) {
            final VortaModule mod = modules[i];
            Button itemBtn = new Button(this);
            itemBtn.setAllCaps(false);
            itemBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            itemBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            itemBtn.setTextSize(12.5f);
            itemBtn.setLineSpacing(dp(2), 1.0f);
            itemBtn.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < modules.length - 1) bLp.bottomMargin = dp(6);
            itemBtn.setLayoutParams(bLp);
            itemBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActiveModule = mod;
                    SweepStore.saveActiveModule(MainActivity.this, mod);
                    toggleModuleMenu(false);
                    renderActiveWorkspace();
                }
            });
            mModuleDrawerButtons[i] = itemBtn;
            mModuleAccordionDrawer.addView(itemBtn);
        }

        root.addView(mModuleAccordionDrawer);
        updateLeftMenuHeader();
    }

    private void toggleModuleMenu(boolean open) {
        mMenuOpen = open;
        mModuleAccordionDrawer.setVisibility(open ? View.VISIBLE : View.GONE);
        updateLeftMenuHeader();
    }

    private void updateLeftMenuHeader() {
        String arrow = mMenuOpen ? "▲" : "▼";
        mBtnLeftModuleMenu.setText("≡ 메뉴 | " + mActiveModule.shortLabel + " " + arrow);
        VortaModule[] mods = VortaModule.values();
        for (int i = 0; i < mods.length; i++) {
            boolean selected = (mods[i] == mActiveModule);
            Button b = mModuleDrawerButtons[i];
            b.setText((selected ? "●  " : "○  ") + mods[i].fullTitle + "\n" + mods[i].hardwareSubtitle);
            b.setTextColor(selected ? ACCENT_TEAL : TXT_PRIMARY);
            b.setBackground(makeBox(selected ? BG_CARD : BG_INPUT, selected ? ACCENT_TEAL : BORDER_LINE, 8, 1));
        }
    }

    // -------------------------------------------------------------------------
    // TELEMETRY CONSOLE PANEL
    // -------------------------------------------------------------------------

    private void buildTelemetryPanel(LinearLayout root) {
        mTelemetryPanel = new LinearLayout(this);
        mTelemetryPanel.setOrientation(LinearLayout.VERTICAL);
        mTelemetryPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        mTelemetryPanel.setLayoutParams(lp);
        mTelemetryPanel.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));

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
        mTvTeleState.setTextSize(11);
        mTvTeleState.setTypeface(Typeface.DEFAULT_BOLD);
        mTvTeleState.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(mTvTeleState);

        mTvTeleGuard = new TextView(this);
        mTvTeleGuard.setText(buildGuardStatusText());
        mTvTeleGuard.setTextColor(ACCENT_AMBER);
        mTvTeleGuard.setTextSize(11);
        mTvTeleGuard.setTypeface(Typeface.DEFAULT_BOLD);
        row1.addView(mTvTeleGuard);
        mTelemetryPanel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, dp(4), 0, dp(4));

        mTvTeleHz = new TextView(this);
        mTvTeleHz.setText("0.00 Hz");
        mTvTeleHz.setTextColor(TXT_DIM);
        mTvTeleHz.setTextSize(22);
        mTvTeleHz.setSingleLine(true);
        mTvTeleHz.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams hzLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        hzLp.rightMargin = dp(8);
        mTvTeleHz.setLayoutParams(hzLp);
        row2.addView(mTvTeleHz);

        mBtnMasterStop = makeActionButton("전체 정지", ACCENT_RED, 34);
        mBtnMasterStop.setTextSize(11.5f);
        mBtnMasterStop.setLayoutParams(new LinearLayout.LayoutParams(dp(76), dp(34)));
        mBtnMasterStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.stopAllPlayback();
                if (mScopeView != null) mScopeView.invalidate();
                if (mActiveModule == VortaModule.PRO_MANUAL && mProSubTab == 0) refreshSweepListView();
                else if (mActiveModule == VortaModule.PRO_MANUAL && mProSubTab == 3) refreshProScenAccordionCards();
                else renderActiveWorkspace();
            }
        });
        row2.addView(mBtnMasterStop);
        mTelemetryPanel.addView(row2);

        mScopeView = new LiveWaveformView(this);
        LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        scopeLp.topMargin = dp(4);
        scopeLp.bottomMargin = dp(5);
        mScopeView.setLayoutParams(scopeLp);
        mTelemetryPanel.addView(mScopeView);

        mTvTelePipeline = new TextView(this);
        mTvTelePipeline.setText("48k FLOAT · 대기 · 끊김 0 · 클립 0");
        mTvTelePipeline.setTextColor(TXT_DIM);
        mTvTelePipeline.setTextSize(9.5f);
        mTvTelePipeline.setSingleLine(true);
        mTvTelePipeline.setEllipsize(TextUtils.TruncateAt.END);
        mTvTelePipeline.setTypeface(Typeface.MONOSPACE);
        mTelemetryPanel.addView(mTvTelePipeline);

        mTvTeleMod = new TextView(this);
        mTvTeleMod.setText("변조: OFF (순수 정현파)");
        mTvTeleMod.setTextColor(TXT_DIM);
        mTvTeleMod.setTextSize(10f);
        mTvTeleMod.setSingleLine(true);
        mTvTeleMod.setEllipsize(TextUtils.TruncateAt.END);
        mTvTeleMod.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvTeleMod.setPadding(0, dp(2), 0, 0);
        mTelemetryPanel.addView(mTvTeleMod);

        mTvTeleDesc = new TextView(this);
        mTvTeleDesc.setText("좌측 상단 메뉴에서 기기를 선택해 시작하세요.");
        mTvTeleDesc.setTextColor(TXT_SEC);
        mTvTeleDesc.setTextSize(11.5f);
        mTvTeleDesc.setSingleLine(true);
        mTvTeleDesc.setEllipsize(TextUtils.TruncateAt.END);
        mTvTeleDesc.setPadding(0, dp(3), 0, 0);
        mTelemetryPanel.addView(mTvTeleDesc);

        LinearLayout rowUtil = new LinearLayout(this);
        rowUtil.setOrientation(LinearLayout.HORIZONTAL);
        rowUtil.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams utilLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        utilLp.topMargin = dp(6);
        rowUtil.setLayoutParams(utilLp);

        mBtnKeepScreenOn = makeActionButton("화면유지 OFF", BG_CARD, 34);
        mBtnKeepScreenOn.setTextSize(11);
        LinearLayout.LayoutParams keepLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        keepLp.rightMargin = dp(6);
        mBtnKeepScreenOn.setLayoutParams(keepLp);
        mBtnKeepScreenOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mKeepScreenOn = !mKeepScreenOn;
                SweepStore.saveKeepScreenOn(MainActivity.this, mKeepScreenOn);
                applyKeepScreenFlag(mKeepScreenOn);
                updateKeepScreenButton();
            }
        });
        updateKeepScreenButton();
        rowUtil.addView(mBtnKeepScreenOn);

        Button btnSelfCheck = makeActionButton("실측 자가진단", BG_CARD, 34);
        btnSelfCheck.setTextSize(11);
        btnSelfCheck.setTextColor(ACCENT_TEAL);
        btnSelfCheck.setBackground(makeBox(BG_CARD, BORDER_LINE, 6, 1));
        btnSelfCheck.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        btnSelfCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelfDiagnosticDialog();
            }
        });
        rowUtil.addView(btnSelfCheck);
        mTelemetryPanel.addView(rowUtil);

        root.addView(mTelemetryPanel);
    }

    private void showSelfDiagnosticDialog() {
        new AlertDialog.Builder(this)
                .setTitle("실시간 DSP · HAL 실측 자가진단")
                .setMessage(VortaAudioService.buildSelfDiagnosticReport())
                .setPositiveButton("닫기", null)
                .show();
    }

    private String buildGuardStatusText() {
        if (VortaVolumeGuard.isPersonalDevice()) return "개인기기 보호(무개입)";
        if (!BuildProfile.ENABLE_VOLUME_GUARD || VortaVolumeGuard.isA30Device()) return "순음 고정(볼륨 무개입)";
        return String.format(Locale.US, "자유볼륨 %d/15단", VortaVolumeGuard.getTargetVolumeStep(this));
    }

    private void applyKeepScreenFlag(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void updateKeepScreenButton() {
        if (mBtnKeepScreenOn == null) return;
        mBtnKeepScreenOn.setText(mKeepScreenOn ? "화면유지 ON" : "화면유지 OFF");
        mBtnKeepScreenOn.setTextColor(mKeepScreenOn ? TXT_PRIMARY : TXT_SEC);
        mBtnKeepScreenOn.setBackground(makeBox(
                mKeepScreenOn ? Color.parseColor("#0F766E") : BG_CARD,
                mKeepScreenOn ? ACCENT_TEAL : BORDER_LINE, 6, 1));
    }

    // -------------------------------------------------------------------------
    // MODULE WORKSPACE RENDERER
    // -------------------------------------------------------------------------

    private void renderActiveWorkspace() {
        updateLeftMenuHeader();
        mWorkspaceHost.removeAllViews();
        mCurrentAccordions.clear();
        mAllAccordionsExpanded = false;
        mProModContainer = null;
        mProScenListContainer = null;
        mTvSweepModSummary = null;

        if (mActiveModule == VortaModule.PRO_MANUAL) {
            renderProWorkspace(mWorkspaceHost);
        } else {
            renderScenarioWorkspace(mWorkspaceHost, mActiveModule);
        }
        refreshLiveTelemetry();
    }

    private void renderScenarioWorkspace(LinearLayout host, final VortaModule mod) {
        ScenarioCatalog.Category[] cats = ScenarioCatalog.Category.values();
        if (mDeviceSubTab < 0 || mDeviceSubTab >= cats.length) mDeviceSubTab = 0;

        ScenarioCatalog.Category playingCat = null;
        if (VortaAudioService.sScenarioPlaying) {
            ScenarioCatalog.ScenarioSpec activeScen = ScenarioCatalog.findById(VortaAudioService.sActiveScenarioId);
            if (activeScen != null && (activeScen.category == ScenarioCatalog.Category.COMMON
                    || activeScen.category == ScenarioCatalog.Category.BASIC
                    || activeScen.module == mod)) {
                playingCat = activeScen.category;
            }
        }

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabRowLp.bottomMargin = dp(8);
        tabRow.setLayoutParams(tabRowLp);

        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            boolean sel = (i == mDeviceSubTab);
            boolean hasPlaying = (cats[i] == playingCat);
            Button tb = makeActionButton(cats[i].heading, sel ? Color.parseColor("#0F766E") : BG_SURFACE, 36);
            tb.setTextSize(12);
            tb.setTextColor(sel ? TXT_PRIMARY : (hasPlaying ? ACCENT_GREEN : TXT_SEC));
            tb.setBackground(makeBox(
                    sel ? Color.parseColor("#0F766E") : BG_SURFACE,
                    hasPlaying ? ACCENT_GREEN : (sel ? ACCENT_TEAL : BORDER_LINE),
                    8, hasPlaying ? 2 : 1));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            if (i < cats.length - 1) bLp.rightMargin = dp(4);
            tb.setLayoutParams(bLp);
            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeviceSubTab = idx;
                    renderActiveWorkspace();
                }
            });
            tabRow.addView(tb);
        }
        host.addView(tabRow);

        TextView tvKnob = new TextView(this);
        tvKnob.setText("권장 노브: " + ScenarioCatalog.knobGuideForModule(mod));
        tvKnob.setTextColor(TXT_SEC);
        tvKnob.setTextSize(10.5f);
        tvKnob.setSingleLine(true);
        tvKnob.setEllipsize(TextUtils.TruncateAt.END);
        tvKnob.setPadding(dp(10), dp(7), dp(10), dp(7));
        tvKnob.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams knobLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        knobLp.bottomMargin = dp(8);
        tvKnob.setLayoutParams(knobLp);
        host.addView(tvKnob);

        ScenarioCatalog.Category curCat = cats[mDeviceSubTab];
        List<ScenarioCatalog.ScenarioSpec> specs = ScenarioCatalog.forModule(mod, curCat);
        boolean shownSchedHdr = false;
        boolean shownResHdr = false;
        boolean shownActHdr = false;
        boolean shownSpecHdr = false;
        boolean shownComSchedHdr = false;
        boolean shownComExtHdr = false;
        boolean shownBandHdr = false;
        boolean shownToneHdr = false;

        if (curCat == ScenarioCatalog.Category.SUB1 && !specs.isEmpty()) {
            addSectionHeader(host, "버전2 상황 전술 (" + specs.size() + ")");
        } else if (curCat == ScenarioCatalog.Category.SUB2 && !specs.isEmpty()) {
            addSectionHeader(host, "버전3 장기 방어 (" + specs.size() + ")");
        }

        for (int j = 0; j < specs.size(); j++) {
            ScenarioCatalog.ScenarioSpec spec = specs.get(j);
            if (curCat == ScenarioCatalog.Category.MAIN) {
                if (!shownSchedHdr && spec.id.contains("_sched_")) {
                    shownSchedHdr = true;
                    addSectionHeader(host, "시간대별 스케줄 (4)");
                } else if (!shownResHdr && spec.id.contains("_res_")) {
                    shownResHdr = true;
                    addSectionHeader(host, "실증 연구 확장 (3)");
                } else if (!shownActHdr && spec.id.contains("_act_")) {
                    shownActHdr = true;
                    addSectionHeader(host, "실전 상황 대응 (9)");
                } else if (!shownSpecHdr && spec.id.contains("_spec_")) {
                    shownSpecHdr = true;
                    addSectionHeader(host, "전술 특수 (4)");
                }
            } else if (curCat == ScenarioCatalog.Category.COMMON) {
                if (!shownComSchedHdr && spec.id.startsWith("com_sched_")) {
                    shownComSchedHdr = true;
                    addSectionHeader(host, "공통 시간대 정제 (4)");
                } else if (!shownComExtHdr && spec.id.startsWith("com_ext_")) {
                    shownComExtHdr = true;
                    addSectionHeader(host, "공통 정제 · 슬래브 (8)");
                }
            } else if (curCat == ScenarioCatalog.Category.BASIC) {
                if (!shownBandHdr && spec.id.startsWith("bas_s")) {
                    shownBandHdr = true;
                    addSectionHeader(host, "대역 스윕 (10)");
                } else if (!shownToneHdr && spec.id.startsWith("bas_t")) {
                    shownToneHdr = true;
                    addSectionHeader(host, "단독 · 기저 공진 (12)");
                }
            }
            host.addView(buildScenarioCard(spec));
        }
    }

    private void addSectionHeader(LinearLayout host, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ACCENT_TEAL);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(5);
        tv.setLayoutParams(lp);
        host.addView(tv);
    }

    private View buildScenarioCard(final ScenarioCatalog.ScenarioSpec spec) {
        return buildScenarioCard(spec, null);
    }

    private View buildScenarioCard(final ScenarioCatalog.ScenarioSpec spec, final Runnable onAfterToggle) {
        boolean isPlaying = VortaAudioService.sScenarioPlaying && spec.id.equals(VortaAudioService.sActiveScenarioId);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(isPlaying ? BG_CARD : BG_INPUT, isPlaying ? ACCENT_GREEN : BORDER_LINE, 8, isPlaying ? 2 : 1));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        leftLp.rightMargin = dp(8);
        leftCol.setLayoutParams(leftLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(spec.title);
        tvTitle.setTextColor(isPlaying ? ACCENT_TEAL : TXT_PRIMARY);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        leftCol.addView(tvTitle);

        TextView tvSpec = new TextView(this);
        tvSpec.setText(spec.formatSpecLine());
        tvSpec.setTextColor(ACCENT_AMBER);
        tvSpec.setTextSize(10.5f);
        tvSpec.setTypeface(Typeface.MONOSPACE);
        tvSpec.setSingleLine(true);
        tvSpec.setEllipsize(TextUtils.TruncateAt.END);
        tvSpec.setPadding(0, dp(2), 0, dp(2));
        leftCol.addView(tvSpec);

        TextView tvEffect = new TextView(this);
        tvEffect.setText(spec.effectDesc);
        tvEffect.setTextColor(TXT_SEC);
        tvEffect.setTextSize(11);
        tvEffect.setSingleLine(true);
        tvEffect.setEllipsize(TextUtils.TruncateAt.END);
        leftCol.addView(tvEffect);
        card.addView(leftCol);

        Button btnPlay = makeActionButton(
                isPlaying ? "중지" : "재생",
                isPlaying ? ACCENT_RED : ACCENT_GREEN, 34);
        btnPlay.setTextSize(12);
        btnPlay.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(34)));

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentlyThis = VortaAudioService.sScenarioPlaying && spec.id.equals(VortaAudioService.sActiveScenarioId);
                if (currentlyThis) {
                    VortaAudioService.stopAllPlayback();
                } else {
                    VortaAudioService.playScenario(MainActivity.this, spec);
                }
                if (mScopeView != null) mScopeView.invalidate();
                if (onAfterToggle != null) onAfterToggle.run();
                else renderActiveWorkspace();
            }
        };
        card.setOnClickListener(toggleListener);
        btnPlay.setOnClickListener(toggleListener);
        card.addView(btnPlay);
        return card;
    }

    // -------------------------------------------------------------------------
    // PRO WORKSPACE (MULTI-SWEEP SCHEDULER + MANUAL + MODULATION + PRESETS)
    // -------------------------------------------------------------------------

    private void renderProWorkspace(LinearLayout host) {
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        tabRow.setLayoutParams(lp);

        String[] titles = {"스윕", "단일", "변조", "프리셋"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = makeActionButton(titles[i], BG_SURFACE, 38);
            b.setTextSize(13);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            if (i > 0) bLp.leftMargin = dp(6);
            b.setLayoutParams(bLp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchProSubTab(idx);
                }
            });
            mProTabBtns[i] = b;
            tabRow.addView(b);
        }
        host.addView(tabRow);

        buildProSweepTab(host);
        buildProSingleTab(host);
        buildProModTab(host);
        buildProPresetsTab(host);

        switchProSubTab(mProSubTab);
    }

    private void switchProSubTab(int idx) {
        mProSubTab = idx;
        for (int i = 0; i < 4; i++) {
            boolean active = (i == idx);
            if (mProTabContainers[i] != null) {
                mProTabContainers[i].setVisibility(active ? View.VISIBLE : View.GONE);
            }
            if (mProTabBtns[i] != null) {
                mProTabBtns[i].setTextColor(active ? ACCENT_TEAL : TXT_SEC);
                mProTabBtns[i].setBackground(makeBox(active ? BG_CARD : BG_SURFACE, active ? ACCENT_TEAL : BORDER_LINE, 8, 1));
            }
        }
        if (idx == 0) refreshSweepListView();
        else if (idx == 1) updateSingleControls();
        else if (idx == 2) refreshProModTab();
        else if (idx == 3) refreshProScenAccordionCards();
    }

    private void persistSweepsNow() {
        synchronized (VortaAudioService.sLock) {
            SweepStore.saveNow(this, VortaAudioService.sSweepItems);
        }
    }

    private void persistManualAndModNow() {
        SweepStore.saveManualAndModState(
                this,
                VortaAudioService.sManualFreq,
                VortaAudioService.sManualDualEnabled,
                VortaAudioService.sManualSecondaryFreq,
                VortaAudioService.sFmDepth, VortaAudioService.sFmRateHz,
                VortaAudioService.sAmDepth, VortaAudioService.sAmRateHz,
                VortaAudioService.sRmDepth, VortaAudioService.sRmRateHz);
    }

    private void buildProSweepTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        mProTabContainers[0] = container;

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 0, 0, dp(8));

        Button btnAdd = makeActionButton("+ 스윕 추가", Color.parseColor("#0284C7"), 40);
        btnAdd.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (VortaAudioService.sLock) {
                    int id = mNextSweepId++;
                    VortaAudioService.sSweepItems.add(new SweepItem(id, "사용자 스윕 #" + id, 10.0f, 25.0f, 300.0f, true));
                }
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        actionRow.addView(btnAdd);

        mBtnGlobalPlayStop = makeActionButton("전체 재생", ACCENT_GREEN, 40);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
        gLp.leftMargin = dp(8);
        mBtnGlobalPlayStop.setLayoutParams(gLp);
        mBtnGlobalPlayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sScenarioPlaying = false;
                boolean anyPlaying = false;
                synchronized (VortaAudioService.sLock) {
                    for (SweepItem it : VortaAudioService.sSweepItems) {
                        if (it.isPlaying) { anyPlaying = true; break; }
                    }
                    for (SweepItem it : VortaAudioService.sSweepItems) it.isPlaying = !anyPlaying;
                }
                if (!anyPlaying) VortaAudioService.startServiceCompat(MainActivity.this);
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        actionRow.addView(mBtnGlobalPlayStop);
        container.addView(actionRow);

        LinearLayout setRow = new LinearLayout(this);
        setRow.setOrientation(LinearLayout.HORIZONTAL);
        setRow.setPadding(0, 0, 0, dp(10));

        Button btnSaveSet = makeActionButton("세트 저장", Color.parseColor("#334155"), 36);
        btnSaveSet.setTextSize(12);
        btnSaveSet.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnSaveSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText et = new EditText(MainActivity.this);
                et.setHint("저장 이름");
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("사용자 스윕 세트 저장")
                        .setView(et)
                        .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String n = et.getText().toString().trim();
                                if (n.isEmpty()) return;
                                synchronized (VortaAudioService.sLock) {
                                    SweepStore.saveNamedSet(MainActivity.this, n, VortaAudioService.sSweepItems);
                                }
                                Toast.makeText(MainActivity.this, "세트 저장됨: " + n, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("취소", null).show();
            }
        });
        setRow.addView(btnSaveSet);

        Button btnLoadSet = makeActionButton("세트 불러오기", Color.parseColor("#334155"), 36);
        btnLoadSet.setTextSize(12);
        LinearLayout.LayoutParams lsLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        lsLp.leftMargin = dp(6);
        btnLoadSet.setLayoutParams(lsLp);
        btnLoadSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<String> names = SweepStore.listNamedSets(MainActivity.this);
                if (names.isEmpty()) {
                    Toast.makeText(MainActivity.this, "저장된 세트가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String[] arr = names.toArray(new String[0]);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("세트 선택")
                        .setItems(arr, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                synchronized (VortaAudioService.sLock) {
                                    SweepStore.loadNamedSet(MainActivity.this, arr[which], VortaAudioService.sSweepItems);
                                }
                                refreshSweepListView();
                            }
                        })
                        .setNegativeButton("닫기", null).show();
            }
        });
        setRow.addView(btnLoadSet);

        Button btnReset = makeActionButton("초기값 복원", BG_SURFACE, 36);
        btnReset.setTextSize(12);
        btnReset.setTextColor(TXT_SEC);
        btnReset.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 6, 1));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        rLp.leftMargin = dp(6);
        btnReset.setLayoutParams(rLp);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (VortaAudioService.sLock) {
                    mNextSweepId = SweepStore.resetToStarterDefaults(MainActivity.this, VortaAudioService.sSweepItems);
                }
                refreshSweepListView();
            }
        });
        setRow.addView(btnReset);
        container.addView(setRow);

        LinearLayout modStrip = new LinearLayout(this);
        modStrip.setOrientation(LinearLayout.HORIZONTAL);
        modStrip.setGravity(Gravity.CENTER_VERTICAL);
        modStrip.setPadding(dp(10), dp(7), dp(10), dp(7));
        modStrip.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 8, 1));
        LinearLayout.LayoutParams msLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msLp.bottomMargin = dp(8);
        modStrip.setLayoutParams(msLp);

        mTvSweepModSummary = new TextView(this);
        mTvSweepModSummary.setTextSize(10.5f);
        mTvSweepModSummary.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvSweepModSummary.setTextColor(ACCENT_TEAL);
        mTvSweepModSummary.setSingleLine(true);
        mTvSweepModSummary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams smLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        smLp.rightMargin = dp(8);
        mTvSweepModSummary.setLayoutParams(smLp);
        modStrip.addView(mTvSweepModSummary);

        Button btnGoMod = makeActionButton("변조 설정", Color.parseColor("#334155"), 28);
        btnGoMod.setTextSize(11);
        btnGoMod.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(28)));
        btnGoMod.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchProSubTab(2);
            }
        });
        modStrip.addView(btnGoMod);
        container.addView(modStrip);

        mSweepListContainer = new LinearLayout(this);
        mSweepListContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(mSweepListContainer);

        parent.addView(container);
    }

    private void refreshSweepListView() {
        if (mTvSweepModSummary != null) {
            mTvSweepModSummary.setText("변조: " + ModulationProfile.formatActiveSummary(
                    VortaAudioService.sFmEnabled, VortaAudioService.sAmEnabled, VortaAudioService.sRmEnabled));
        }
        if (mSweepListContainer == null) return;
        mSweepListContainer.removeAllViews();
        boolean anyPlaying = false;
        synchronized (VortaAudioService.sLock) {
            if (VortaAudioService.sSweepItems.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("등록된 스윕이 없습니다.\n[+ 스윕 추가] 또는 [프리셋] 탭을 사용하세요.");
                empty.setTextColor(TXT_SEC);
                empty.setTextSize(13);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(16), dp(24), dp(16), dp(24));
                empty.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));
                mSweepListContainer.addView(empty);
            } else {
                for (int i = 0; i < VortaAudioService.sSweepItems.size(); i++) {
                    SweepItem item = VortaAudioService.sSweepItems.get(i);
                    if (item.isPlaying) anyPlaying = true;
                    mSweepListContainer.addView(buildSweepCard(item));
                }
            }
        }
        mBtnGlobalPlayStop.setText(anyPlaying ? "전체 정지" : "전체 재생");
        mBtnGlobalPlayStop.setBackground(makeBox(anyPlaying ? ACCENT_RED : ACCENT_GREEN, anyPlaying ? ACCENT_RED : ACCENT_GREEN, 6, 0));
    }

    private View buildSweepCard(final SweepItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_CARD, item.isPlaying ? ACCENT_GREEN : BORDER_LINE, 8, item.isPlaying ? 2 : 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        final EditText etTitle = new EditText(this);
        etTitle.setText(item.title);
        etTitle.setTextColor(TXT_PRIMARY);
        etTitle.setTextSize(13);
        etTitle.setTypeface(Typeface.DEFAULT_BOLD);
        etTitle.setSingleLine(true);
        etTitle.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        etTitle.setPadding(dp(8), dp(6), dp(8), dp(6));
        etTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(etTitle);

        TextView tvHz = new TextView(this);
        tvHz.setTag("live_hz_" + item.id);
        tvHz.setText(item.secondaryHz > 0f
                ? String.format(Locale.US, "%.1f+%.1fHz", item.currentHz, item.secondaryHz)
                : String.format(Locale.US, "%.1f Hz", item.currentHz));
        tvHz.setTextColor(ACCENT_AMBER);
        tvHz.setTextSize(13);
        tvHz.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvHz.setPadding(dp(8), 0, 0, 0);
        row1.addView(tvHz);
        card.addView(row1);

        LinearLayout inRow = new LinearLayout(this);
        inRow.setOrientation(LinearLayout.HORIZONTAL);
        inRow.setPadding(0, dp(8), 0, dp(8));
        final EditText etStart = makeLabeledInput(inRow, "시작 Hz", String.format(Locale.US, "%.1f", item.startHz));
        final EditText etEnd = makeLabeledInput(inRow, "종료 Hz", String.format(Locale.US, "%.1f", item.endHz));
        final EditText etSec = makeLabeledInput(inRow, "주기 초", String.format(Locale.US, "%.0f", item.durationSec));
        final EditText etSec2 = makeLabeledInput(inRow, "이중 Hz", item.secondaryHz > 0f ? String.format(Locale.US, "%.1f", item.secondaryHz) : "0");
        card.addView(inRow);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPlay = makeActionButton(item.isPlaying ? "정지" : "재생",
                item.isPlaying ? ACCENT_GREEN : Color.parseColor("#D97706"), 36);
        btnPlay.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sScenarioPlaying = false;
                readSweepInputs(item, etTitle, etStart, etEnd, etSec, etSec2);
                item.isPlaying = !item.isPlaying;
                if (item.isPlaying) VortaAudioService.startServiceCompat(MainActivity.this);
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        ctrlRow.addView(btnPlay);

        Button btnSave = makeActionButton("저장", Color.parseColor("#0284C7"), 36);
        btnSave.setTextSize(12);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(0, dp(36), 0.7f);
        svLp.leftMargin = dp(5);
        btnSave.setLayoutParams(svLp);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readSweepInputs(item, etTitle, etStart, etEnd, etSec, etSec2);
                persistSweepsNow();
                Toast.makeText(MainActivity.this, "저장됨", Toast.LENGTH_SHORT).show();
            }
        });
        ctrlRow.addView(btnSave);

        Button btnLoop = makeActionButton(item.isLoop ? "반복" : "1회",
                item.isLoop ? Color.parseColor("#1E3A5F") : Color.parseColor("#334155"), 36);
        btnLoop.setTextSize(12);
        btnLoop.setTextColor(item.isLoop ? ACCENT_BLUE : TXT_SEC);
        LinearLayout.LayoutParams lpLoop = new LinearLayout.LayoutParams(dp(54), dp(36));
        lpLoop.leftMargin = dp(5);
        btnLoop.setLayoutParams(lpLoop);
        btnLoop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item.isLoop = !item.isLoop;
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        ctrlRow.addView(btnLoop);

        Button btnDel = makeActionButton("삭제", Color.parseColor("#334155"), 36);
        btnDel.setTextSize(12);
        btnDel.setTextColor(TXT_SEC);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(54), dp(36));
        dLp.leftMargin = dp(5);
        btnDel.setLayoutParams(dLp);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (VortaAudioService.sLock) {
                    item.isPlaying = false;
                    VortaAudioService.sSweepItems.remove(item);
                }
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        ctrlRow.addView(btnDel);

        card.addView(ctrlRow);
        return card;
    }

    private void readSweepInputs(SweepItem item, EditText etTitle, EditText etS, EditText etE, EditText etD, EditText et2) {
        String t = etTitle.getText().toString().trim();
        if (!t.isEmpty()) item.title = t;
        try {
            item.startHz = clampFreq(Float.parseFloat(etS.getText().toString()));
            item.endHz = clampFreq(Float.parseFloat(etE.getText().toString()));
            item.durationSec = Math.max(1.0f, Float.parseFloat(etD.getText().toString()));
            float s2 = Float.parseFloat(et2.getText().toString());
            item.secondaryHz = s2 > 0f ? clampFreq(s2) : 0f;
        } catch (Exception ignored) {}
    }

    private EditText makeLabeledInput(LinearLayout parent, String label, String val) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        box.setPadding(dp(3), 0, dp(3), 0);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TXT_SEC);
        tv.setTextSize(10);
        tv.setSingleLine(true);
        box.addView(tv);

        EditText et = new EditText(this);
        et.setText(val);
        et.setTextColor(TXT_PRIMARY);
        et.setTextSize(12);
        et.setSingleLine(true);
        et.setGravity(Gravity.CENTER);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        et.setPadding(dp(4), dp(6), dp(4), dp(6));
        box.addView(et);
        parent.addView(box);
        return et;
    }

    private void buildProSingleTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        mProTabContainers[1] = container;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(makeBox(BG_CARD, BORDER_LINE, 10, 1));

        mTvSingleFreqDisplay = new TextView(this);
        mTvSingleFreqDisplay.setTextSize(30);
        mTvSingleFreqDisplay.setTextColor(ACCENT_AMBER);
        mTvSingleFreqDisplay.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvSingleFreqDisplay.setGravity(Gravity.CENTER);
        card.addView(mTvSingleFreqDisplay);

        // Quick acoustic anchors (balanced 4 + 3 rows for zero crowding on 360dp screens)
        float[][] quickRows = {
                {7.83f, 18.98f, 28.0f, 40.0f},
                {43.2f, 55.0f, 63.0f}
        };
        for (int r = 0; r < quickRows.length; r++) {
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setPadding(0, r == 0 ? dp(8) : dp(4), 0, r == quickRows.length - 1 ? dp(8) : 0);
            float[] rowHz = quickRows[r];
            for (int i = 0; i < rowHz.length; i++) {
                final float f = rowHz[i];
                String label = (f == 7.83f || f == 18.98f)
                        ? String.format(Locale.US, "%.2f", f)
                        : (f == 43.2f ? "43.2 Hz" : String.format(Locale.US, "%.0f Hz", f));
                Button c = makeActionButton(label, Color.parseColor("#1E293B"), 34);
                c.setTextSize(12);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
                if (i > 0) cLp.leftMargin = dp(6);
                c.setLayoutParams(cLp);
                c.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { setProManualFreq(f); }
                });
                chipRow.addView(c);
            }
            card.addView(chipRow);
        }

        LinearLayout sRow1 = new LinearLayout(this);
        sRow1.setOrientation(LinearLayout.HORIZONTAL);
        sRow1.setPadding(0, dp(4), 0, dp(4));
        addProStepBtn(sRow1, "-5.0", -5.0f);
        addProStepBtn(sRow1, "-1.0", -1.0f);
        addProStepBtn(sRow1, "+1.0", +1.0f);
        addProStepBtn(sRow1, "+5.0", +5.0f);
        card.addView(sRow1);

        LinearLayout sRow2 = new LinearLayout(this);
        sRow2.setOrientation(LinearLayout.HORIZONTAL);
        sRow2.setPadding(0, dp(4), 0, dp(10));
        addProStepBtn(sRow2, "-0.1", -0.1f);
        addProStepBtn(sRow2, "-0.01", -0.01f);
        addProStepBtn(sRow2, "+0.01", +0.01f);
        addProStepBtn(sRow2, "+0.1", +0.1f);
        card.addView(sRow2);

        LinearLayout inRow = new LinearLayout(this);
        inRow.setOrientation(LinearLayout.HORIZONTAL);
        inRow.setGravity(Gravity.CENTER_VERTICAL);

        mEtSingleFreqInput = new EditText(this);
        mEtSingleFreqInput.setTextColor(TXT_PRIMARY);
        mEtSingleFreqInput.setTextSize(14);
        mEtSingleFreqInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEtSingleFreqInput.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        mEtSingleFreqInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        mEtSingleFreqInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        inRow.addView(mEtSingleFreqInput);

        Button btnSet = makeActionButton("적용", Color.parseColor("#0284C7"), 40);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(dp(76), dp(40));
        stLp.leftMargin = dp(8);
        btnSet.setLayoutParams(stLp);
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try { setProManualFreq(Float.parseFloat(mEtSingleFreqInput.getText().toString())); }
                catch (Exception ignored) {}
            }
        });
        inRow.addView(btnSet);
        card.addView(inRow);

        LinearLayout dualRow = new LinearLayout(this);
        dualRow.setOrientation(LinearLayout.HORIZONTAL);
        dualRow.setGravity(Gravity.CENTER_VERTICAL);
        dualRow.setPadding(0, dp(10), 0, dp(4));

        mBtnDualToggle = makeActionButton("이중 비트", Color.parseColor("#334155"), 36);
        mBtnDualToggle.setTextSize(12);
        mBtnDualToggle.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        mBtnDualToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sManualDualEnabled = !VortaAudioService.sManualDualEnabled;
                try {
                    VortaAudioService.sManualSecondaryFreq = clampFreq(Float.parseFloat(mEtSecondaryFreqInput.getText().toString()));
                } catch (Exception ignored) {}
                persistManualAndModNow();
                updateSingleControls();
            }
        });
        dualRow.addView(mBtnDualToggle);

        mEtSecondaryFreqInput = new EditText(this);
        mEtSecondaryFreqInput.setText(String.format(Locale.US, "%.2f", VortaAudioService.sManualSecondaryFreq));
        mEtSecondaryFreqInput.setTextColor(TXT_PRIMARY);
        mEtSecondaryFreqInput.setTextSize(13);
        mEtSecondaryFreqInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEtSecondaryFreqInput.setBackground(makeBox(BG_INPUT, BORDER_LINE, 6, 1));
        mEtSecondaryFreqInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams dEtLp = new LinearLayout.LayoutParams(0, dp(36), 0.75f);
        dEtLp.leftMargin = dp(6);
        mEtSecondaryFreqInput.setLayoutParams(dEtLp);
        dualRow.addView(mEtSecondaryFreqInput);

        Button btnApplyDual = makeActionButton("f2 적용", Color.parseColor("#334155"), 36);
        btnApplyDual.setTextSize(12);
        LinearLayout.LayoutParams adLp = new LinearLayout.LayoutParams(dp(64), dp(36));
        adLp.leftMargin = dp(6);
        btnApplyDual.setLayoutParams(adLp);
        btnApplyDual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    VortaAudioService.sManualSecondaryFreq = clampFreq(Float.parseFloat(mEtSecondaryFreqInput.getText().toString()));
                } catch (Exception ignored) {}
                persistManualAndModNow();
                updateSingleControls();
            }
        });
        dualRow.addView(btnApplyDual);
        card.addView(dualRow);

        mBtnSinglePlayToggle = makeActionButton("단일 발진 시작", ACCENT_GREEN, 46);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        pLp.topMargin = dp(12);
        mBtnSinglePlayToggle.setLayoutParams(pLp);
        mBtnSinglePlayToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sScenarioPlaying = false;
                VortaAudioService.sManualFreqActive = !VortaAudioService.sManualFreqActive;
                if (VortaAudioService.sManualFreqActive) VortaAudioService.startServiceCompat(MainActivity.this);
                updateSingleControls();
            }
        });
        card.addView(mBtnSinglePlayToggle);

        container.addView(card);
        parent.addView(container);
        updateSingleControls();
    }

    private void addProStepBtn(LinearLayout row, String label, final float delta) {
        Button b = makeActionButton(label, Color.parseColor("#253142"), 34);
        b.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setProManualFreq(VortaAudioService.sManualFreq + delta); }
        });
        row.addView(b);
    }

    private float clampFreq(float hz) {
        return Math.min(Math.max(hz, VortaAudioService.MIN_FREQ_HZ), VortaAudioService.MAX_FREQ_HZ);
    }

    private void setProManualFreq(float hz) {
        VortaAudioService.sManualFreq = clampFreq(hz);
        persistManualAndModNow();
        updateSingleControls();
    }

    private void updateSingleControls() {
        if (mTvSingleFreqDisplay == null) return;
        if (VortaAudioService.sManualDualEnabled) {
            mTvSingleFreqDisplay.setText(String.format(Locale.US, "%.2f + %.2f Hz",
                    VortaAudioService.sManualFreq, VortaAudioService.sManualSecondaryFreq));
        } else {
            mTvSingleFreqDisplay.setText(String.format(Locale.US, "%.2f Hz", VortaAudioService.sManualFreq));
        }
        mEtSingleFreqInput.setText(String.format(Locale.US, "%.2f", VortaAudioService.sManualFreq));
        boolean dOn = VortaAudioService.sManualDualEnabled;
        mBtnDualToggle.setText(dOn ? "이중 비트 ON" : "이중 비트 OFF");
        mBtnDualToggle.setBackground(makeBox(dOn ? Color.parseColor("#0F766E") : Color.parseColor("#334155"),
                dOn ? ACCENT_TEAL : BORDER_LINE, 6, 1));
        boolean act = VortaAudioService.sManualFreqActive;
        mBtnSinglePlayToggle.setText(act ? "단일 발진 정지" : "단일 발진 시작");
        mBtnSinglePlayToggle.setBackground(makeBox(act ? ACCENT_RED : ACCENT_GREEN, act ? ACCENT_RED : ACCENT_GREEN, 8, 0));
    }

    private void buildProModTab(LinearLayout parent) {
        mProModContainer = new LinearLayout(this);
        mProModContainer.setOrientation(LinearLayout.VERTICAL);
        mProTabContainers[2] = mProModContainer;
        refreshProModTab();
        parent.addView(mProModContainer);
    }

    private void refreshProModTab() {
        if (mProModContainer == null) return;
        mProModContainer.removeAllViews();

        LinearLayout lockCard = new LinearLayout(this);
        lockCard.setOrientation(LinearLayout.HORIZONTAL);
        lockCard.setGravity(Gravity.CENTER_VERTICAL);
        lockCard.setPadding(dp(11), dp(9), dp(10), dp(9));
        lockCard.setBackground(makeBox(BG_CARD, ACCENT_TEAL, 8, 1));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.bottomMargin = dp(10);
        lockCard.setLayoutParams(bLp);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lcLp.rightMargin = dp(8);
        leftCol.setLayoutParams(lcLp);

        TextView tvLockTitle = new TextView(this);
        tvLockTitle.setText("순수 변조 최적값 고정 (임의 변경 잠금)");
        tvLockTitle.setTextColor(ACCENT_TEAL);
        tvLockTitle.setTextSize(11.5f);
        tvLockTitle.setTypeface(Typeface.DEFAULT_BOLD);
        leftCol.addView(tvLockTitle);

        TextView tvLockState = new TextView(this);
        tvLockState.setText(ModulationProfile.formatActiveSummary(
                VortaAudioService.sFmEnabled, VortaAudioService.sAmEnabled, VortaAudioService.sRmEnabled));
        tvLockState.setTextColor(ACCENT_AMBER);
        tvLockState.setTextSize(10.5f);
        tvLockState.setTypeface(Typeface.MONOSPACE);
        tvLockState.setPadding(0, dp(2), 0, 0);
        leftCol.addView(tvLockState);
        lockCard.addView(leftCol);

        boolean anyModOn = VortaAudioService.sFmEnabled || VortaAudioService.sAmEnabled || VortaAudioService.sRmEnabled;
        Button btnAllOff = makeActionButton("전체 OFF", anyModOn ? ACCENT_RED : Color.parseColor("#334155"), 32);
        btnAllOff.setTextSize(11);
        btnAllOff.setLayoutParams(new LinearLayout.LayoutParams(dp(68), dp(32)));
        btnAllOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.applyLockedPureModulation(false, false, false);
                SweepStore.savePureModulationToggles(MainActivity.this, false, false, false);
                refreshProModTab();
                refreshLiveTelemetry();
                Toast.makeText(MainActivity.this, "순수 정현파 (변조 전체 OFF)", Toast.LENGTH_SHORT).show();
            }
        });
        lockCard.addView(btnAllOff);
        mProModContainer.addView(lockCard);

        addSectionHeader(mProModContainer, "순수 변조 채널 (최적 파라미터 고정)");
        List<ModulationProfile.ChannelSpec> channels = ModulationProfile.channels();
        for (int i = 0; i < channels.size(); i++) {
            mProModContainer.addView(buildPureLockedModCard(channels.get(i)));
        }

        TextView note = new TextView(this);
        note.setText("※ 스윕·단일 출력에 즉시 적용됩니다. 개별 시나리오는 [프리셋] 및 각 기기 탭에서 실행하세요.");
        note.setTextColor(TXT_SEC);
        note.setTextSize(10.5f);
        note.setLineSpacing(dp(2), 1.0f);
        note.setPadding(dp(2), dp(4), dp(2), 0);
        mProModContainer.addView(note);
    }

    private boolean isPureChannelEnabled(ModulationProfile.Channel ch) {
        if (ch == ModulationProfile.Channel.FM) return VortaAudioService.sFmEnabled;
        if (ch == ModulationProfile.Channel.AM) return VortaAudioService.sAmEnabled;
        return VortaAudioService.sRmEnabled;
    }

    private View buildPureLockedModCard(final ModulationProfile.ChannelSpec spec) {
        final boolean isOn = isPureChannelEnabled(spec.channel);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(isOn ? BG_CARD : BG_INPUT, isOn ? ACCENT_GREEN : BORDER_LINE, 8, isOn ? 2 : 1));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        leftLp.rightMargin = dp(8);
        leftCol.setLayoutParams(leftLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(spec.title);
        tvTitle.setTextColor(isOn ? ACCENT_TEAL : TXT_PRIMARY);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        leftCol.addView(tvTitle);

        TextView tvSpec = new TextView(this);
        tvSpec.setText(spec.lockedSpec);
        tvSpec.setTextColor(ACCENT_AMBER);
        tvSpec.setTextSize(10.5f);
        tvSpec.setTypeface(Typeface.MONOSPACE);
        tvSpec.setSingleLine(true);
        tvSpec.setEllipsize(TextUtils.TruncateAt.END);
        tvSpec.setPadding(0, dp(3), 0, dp(2));
        leftCol.addView(tvSpec);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(spec.desc);
        tvDesc.setTextColor(TXT_SEC);
        tvDesc.setTextSize(11);
        tvDesc.setSingleLine(true);
        tvDesc.setEllipsize(TextUtils.TruncateAt.END);
        leftCol.addView(tvDesc);
        card.addView(leftCol);

        Button btnToggle = makeActionButton(
                isOn ? "ON" : "OFF",
                isOn ? ACCENT_GREEN : Color.parseColor("#334155"), 34);
        btnToggle.setTextSize(12);
        btnToggle.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(34)));

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean fm = (spec.channel == ModulationProfile.Channel.FM) ? !isOn : VortaAudioService.sFmEnabled;
                boolean am = (spec.channel == ModulationProfile.Channel.AM) ? !isOn : VortaAudioService.sAmEnabled;
                boolean rm = (spec.channel == ModulationProfile.Channel.RM) ? !isOn : VortaAudioService.sRmEnabled;
                VortaAudioService.applyLockedPureModulation(fm, am, rm);
                SweepStore.savePureModulationToggles(MainActivity.this, fm, am, rm);
                refreshProModTab();
                refreshLiveTelemetry();
            }
        };
        card.setOnClickListener(toggleListener);
        btnToggle.setOnClickListener(toggleListener);
        card.addView(btnToggle);
        return card;
    }

    private void buildProPresetsTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        mProTabContainers[3] = container;

        LinearLayout hdrRow = new LinearLayout(this);
        hdrRow.setOrientation(LinearLayout.HORIZONTAL);
        hdrRow.setGravity(Gravity.CENTER_VERTICAL);
        hdrRow.setPadding(dp(2), 0, dp(2), dp(8));

        TextView note = new TextView(this);
        note.setText("기본 닫힘 상태 (필요한 그룹만 펼침)");
        note.setTextColor(TXT_SEC);
        note.setTextSize(12);
        note.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        hdrRow.addView(note);

        final Button btnAll = makeActionButton("모두 펼치기", BG_SURFACE, 32);
        btnAll.setTextSize(11);
        btnAll.setTextColor(ACCENT_TEAL);
        btnAll.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 6, 1));
        btnAll.setLayoutParams(new LinearLayout.LayoutParams(dp(88), dp(32)));
        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAllAccordionsExpanded = !mAllAccordionsExpanded;
                for (AccordionHandle h : mCurrentAccordions) h.setExpanded(mAllAccordionsExpanded);
                btnAll.setText(mAllAccordionsExpanded ? "모두 접기" : "모두 펼치기");
            }
        });
        hdrRow.addView(btnAll);
        container.addView(hdrRow);

        // Accordion 1 (Collapsed by default): 12 Time/Situation/Tactic Scenarios (moved out of 변조 tab)
        mProScenListContainer = new LinearLayout(this);
        mProScenListContainer.setOrientation(LinearLayout.VERTICAL);
        refreshProScenAccordionCards();
        mCurrentAccordions.add(createAccordionSection(
                "시간·상황·전술 시나리오", "12종", ACCENT_TEAL, false, mProScenListContainer, container));

        // Accordion 1 (Collapsed by default): Research-derived Pro packs
        LinearLayout resContent = new LinearLayout(this);
        resContent.setOrientation(LinearLayout.VERTICAL);
        addProPresetButton(resContent, "1. 슬래브 굽힘 광역 2구간 (40~50 / 50~63 Hz)",
                "슬래브 굽힘 고유진동 + 룸모드 공간 평균 순수 스윕", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "슬래브 40~50Hz", 40.0f, 50.0f, 360.0f, true),
                                new SweepItem(mNextSweepId++, "룸모드 50~63Hz", 50.0f, 63.0f, 480.0f, true)
                        });
                    }
                });
        addProPresetButton(resContent, "2. 수직·흉곽·촉각 3구간 (4~8 / 8~12 / 30~50 Hz)",
                "척추·내장·어깨·촉각 순수 정현파 스윕", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "수직공진 4~8Hz", 4.0f, 8.0f, 600.0f, true),
                                new SweepItem(mNextSweepId++, "흉곽전이 8~12Hz", 8.0f, 12.0f, 480.0f, true),
                                new SweepItem(mNextSweepId++, "촉각대역 30~50Hz", 30.0f, 50.0f, 420.0f, true)
                        });
                    }
                });
        addProPresetButton(resContent, "3. 저역 상향 마스킹 4Hz 맥놀이 (24+28 / 48+52 Hz)",
                "비선형 고조파 없는 선형 중첩 4Hz 음향 맥놀이", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "4Hz 비트 (24+28Hz)", 24.0f, 24.0f, 600.0f, true, 28.0f),
                                new SweepItem(mNextSweepId++, "4Hz 비트 (48+52Hz)", 48.0f, 48.0f, 600.0f, true, 52.0f)
                        });
                    }
                });
        mCurrentAccordions.add(createAccordionSection(
                "실증 확장 스윕 프리셋", "확장 3종", ACCENT_TEAL, false, resContent, container));

        // Accordion 2 (Collapsed by default): 5 Legacy Schedule Bands (batch + individual cards)
        LinearLayout bandContent = new LinearLayout(this);
        bandContent.setOrientation(LinearLayout.VERTICAL);
        addProPresetButton(bandContent, "5대 대역 일괄 등록 & 가동 (1~55 Hz)",
                "1~4 / 5~11 / 12~22 / 27~36 / 38~55 Hz 일괄 적재", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "1~4Hz 극초저주파 (20s)", 1.0f, 4.0f, 20.0f, true),
                                new SweepItem(mNextSweepId++, "5~11Hz 수면리듬붕괴 (35s)", 5.0f, 11.0f, 35.0f, true),
                                new SweepItem(mNextSweepId++, "12~22Hz 안구·내장 (55s)", 12.0f, 22.0f, 55.0f, true),
                                new SweepItem(mNextSweepId++, "27~36Hz 복부·흉부 (50s)", 27.0f, 36.0f, 50.0f, true),
                                new SweepItem(mNextSweepId++, "38~55Hz 고막압박 (90s)", 38.0f, 55.0f, 90.0f, true)
                        });
                    }
                });
        addProBandCard(bandContent, "1 ~ 4 Hz", 1.0f, 4.0f, 20.0f, "20s", "극초저주파 심층 진동 대역", "#38BDF8");
        addProBandCard(bandContent, "5 ~ 11 Hz", 5.0f, 11.0f, 35.0f, "35s", "뇌파 간섭 및 수면리듬 교란", "#F43F5E");
        addProBandCard(bandContent, "12 ~ 22 Hz", 12.0f, 22.0f, 55.0f, "55s", "안구 압박 및 내장 공진", "#F59E0B");
        addProBandCard(bandContent, "27 ~ 36 Hz", 27.0f, 36.0f, 50.0f, "50s", "복부·흉부 진동 공진", "#10B981");
        addProBandCard(bandContent, "38 ~ 55 Hz", 38.0f, 55.0f, 90.0f, "90s", "고막 압박 (55Hz 상한)", "#A855F7");
        mCurrentAccordions.add(createAccordionSection(
                "대역별 스윕 프리셋", "5대역", ACCENT_BLUE, false, bandContent, container));

        // Accordion 3 (Collapsed by default): 12 Human Body Resonance Matrix Targets
        LinearLayout matrixContent = new LinearLayout(this);
        matrixContent.setOrientation(LinearLayout.VERTICAL);
        addProMatrixCard(matrixContent, 7.0f, "전정기관 / 뇌간", "어지러움 및 기저 긴장", "A30 트랜스+본", false);
        addProMatrixCard(matrixContent, 10.5f, "심박변동 / 자율신경", "불규칙 맥박 및 피로", "A30 트랜스+본", false);
        addProMatrixCard(matrixContent, 16.0f, "복부 / 위장", "복부 진동 및 압박", "S6 스마토 2기", false);
        addProMatrixCard(matrixContent, 18.0f, "전두엽 / 두개골", "골전도 두개 공진", "A30 트랜스+본", true);
        addProMatrixCard(matrixContent, 21.0f, "시신경 / 안구", "안구 압박 및 초점 저하", "S6 스마토 2기", true);
        addProMatrixCard(matrixContent, 22.0f, "내이 / 평형", "방향감각 교란", "A30 트랜스+본", true);
        addProMatrixCard(matrixContent, 24.0f, "흉곽 / 폐", "흉부 공진 고음압", "S6 스마토 2기", true);
        addProMatrixCard(matrixContent, 25.0f, "하복부 / 대장", "하부 진동 유지", "S6 / S7", true);
        addProMatrixCard(matrixContent, 28.0f, "골반 / 하부 장기", "중심 타격점", "S6 / S7", true);
        addProMatrixCard(matrixContent, 31.0f, "위장 / 간", "복부 묵직한 타격", "S7 스마토 1기", false);
        addProMatrixCard(matrixContent, 40.0f, "전신 / 슬래브", "벽면·바닥 구조 전달", "S6 / A30", false);
        addProMatrixCard(matrixContent, 55.0f, "구조물 / 체표면", "실내 1차 룸모드", "S6 / S7", true);
        mCurrentAccordions.add(createAccordionSection(
                "인체 공진 매트릭스 단일 프리셋", "12타겟", ACCENT_AMBER, false, matrixContent, container));

        // Accordion 4 (Collapsed by default): 3 Hardware Special Suites
        LinearLayout hwContent = new LinearLayout(this);
        hwContent.setOrientation(LinearLayout.VERTICAL);
        addProPresetButton(hwContent, "갤럭시 S6 (스마토 2기 · 듀얼 BTL 68W)",
                "17.5~19.8 Hz(440s), 4.0~6.2 Hz(290s), 20.7~27.7 Hz(740s)", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "S6 17.5~19.8Hz", 17.5f, 19.8f, 440.0f, true),
                                new SweepItem(mNextSweepId++, "S6 4.0~6.2Hz", 4.0f, 6.2f, 290.0f, true),
                                new SweepItem(mNextSweepId++, "S6 20.7~27.7Hz", 20.7f, 27.7f, 740.0f, true)
                        });
                    }
                });
        addProPresetButton(hwContent, "갤럭시 S7 (스마토 1기 · 단독 우퍼 240W)",
                "21.0~24.5 Hz(1250s), 2.4~4.6 Hz(1480s), 28.0 Hz 고정", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "S7 21.0~24.5Hz", 21.0f, 24.5f, 1250.0f, true),
                                new SweepItem(mNextSweepId++, "S7 2.4~4.6Hz", 2.4f, 4.6f, 1480.0f, true),
                                new SweepItem(mNextSweepId++, "S7 28.0Hz 고정", 28.0f, 28.0f, 3600.0f, true)
                        });
                    }
                });
        addProPresetButton(hwContent, "갤럭시 A30 (트랜스+본 · 골전도 120W)",
                "24.0~36.0 Hz(6240s), 5.0~24.5 Hz(12000s), 55.0 Hz 고정", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadSweepBatch(new SweepItem[]{
                                new SweepItem(mNextSweepId++, "A30 24.0~36.0Hz", 24.0f, 36.0f, 6240.0f, true),
                                new SweepItem(mNextSweepId++, "A30 5.0~24.5Hz", 5.0f, 24.5f, 12000.0f, true),
                                new SweepItem(mNextSweepId++, "A30 55.0Hz 고정", 55.0f, 55.0f, 3600.0f, true)
                        });
                    }
                });
        mCurrentAccordions.add(createAccordionSection(
                "기기별 특화 프리셋", "3종", ACCENT_GREEN, false, hwContent, container));

        parent.addView(container);
    }

    private void refreshProScenAccordionCards() {
        if (mProScenListContainer == null) return;
        mProScenListContainer.removeAllViews();
        List<ScenarioCatalog.ScenarioSpec> specs = ScenarioCatalog.proScenarios();
        Runnable afterToggle = new Runnable() {
            @Override
            public void run() {
                refreshProScenAccordionCards();
            }
        };
        for (int i = 0; i < specs.size(); i++) {
            if (i == 0) addSectionHeader(mProScenListContainer, "시간대별 (4)");
            else if (i == 4) addSectionHeader(mProScenListContainer, "상황별 (4)");
            else if (i == 8) addSectionHeader(mProScenListContainer, "전술별 (4)");
            mProScenListContainer.addView(buildScenarioCard(specs.get(i), afterToggle));
        }
    }

    private void addProBandCard(
            LinearLayout parent, final String bandName, final float startHz, final float endHz,
            final float durationSec, final String durationDesc, final String effectDesc, final String colorHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, BORDER_LINE, 8, 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvBand = new TextView(this);
        tvBand.setText(bandName);
        tvBand.setTextColor(Color.parseColor(colorHex));
        tvBand.setTextSize(14);
        tvBand.setTypeface(Typeface.DEFAULT_BOLD);
        tvBand.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(tvBand);

        TextView tvDur = new TextView(this);
        tvDur.setText(durationDesc);
        tvDur.setTextColor(TXT_PRIMARY);
        tvDur.setTextSize(11);
        tvDur.setPadding(dp(6), dp(2), dp(6), dp(2));
        tvDur.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 6, 1));
        row1.addView(tvDur);
        card.addView(row1);

        TextView tvEff = new TextView(this);
        tvEff.setText(effectDesc);
        tvEff.setTextColor(TXT_SEC);
        tvEff.setTextSize(12);
        tvEff.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvEff);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnAddOnly = makeActionButton("목록 추가", Color.parseColor("#334155"), 36);
        btnAddOnly.setTextSize(12);
        btnAddOnly.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnAddOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (VortaAudioService.sLock) {
                    VortaAudioService.sSweepItems.add(
                            new SweepItem(mNextSweepId++, bandName + " (" + durationDesc + ")", startHz, endHz, durationSec, true));
                }
                persistSweepsNow();
                switchProSubTab(0);
            }
        });
        btnRow.addView(btnAddOnly);

        Button btnRun = makeActionButton("추가 및 재생", ACCENT_GREEN, 36);
        btnRun.setTextSize(12);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        rLp.leftMargin = dp(8);
        btnRun.setLayoutParams(rLp);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sScenarioPlaying = false;
                synchronized (VortaAudioService.sLock) {
                    SweepItem item = new SweepItem(mNextSweepId++, bandName + " (" + durationDesc + ")", startHz, endHz, durationSec, true);
                    item.isPlaying = true;
                    VortaAudioService.sSweepItems.add(item);
                }
                VortaAudioService.startServiceCompat(MainActivity.this);
                persistSweepsNow();
                switchProSubTab(0);
            }
        });
        btnRow.addView(btnRun);
        card.addView(btnRow);
        parent.addView(card);
    }

    private void addProMatrixCard(
            LinearLayout parent, final float freqHz, final String targetPart,
            final String symptoms, final String speaker, final boolean isKeyTarget) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, isKeyTarget ? ACCENT_AMBER : BORDER_LINE, 8, isKeyTarget ? 2 : 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvFreq = new TextView(this);
        tvFreq.setText(String.format(Locale.US, "%.1f Hz", freqHz) + (isKeyTarget ? " (핵심)" : ""));
        tvFreq.setTextColor(isKeyTarget ? ACCENT_AMBER : ACCENT_BLUE);
        tvFreq.setTextSize(14);
        tvFreq.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFreq.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(tvFreq);

        TextView tvSpk = new TextView(this);
        tvSpk.setText(speaker);
        tvSpk.setTextColor(ACCENT_TEAL);
        tvSpk.setTextSize(11);
        tvSpk.setPadding(dp(6), dp(2), dp(6), dp(2));
        tvSpk.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 6, 1));
        row1.addView(tvSpk);
        card.addView(row1);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(targetPart + " · " + symptoms);
        tvDesc.setTextColor(TXT_SEC);
        tvDesc.setTextSize(12);
        tvDesc.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvDesc);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSet = makeActionButton("주파수 입력", Color.parseColor("#334155"), 36);
        btnSet.setTextSize(12);
        btnSet.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProManualFreq(freqHz);
                switchProSubTab(1);
            }
        });
        btnRow.addView(btnSet);

        Button btnRun = makeActionButton("즉시 발진", Color.parseColor("#D97706"), 36);
        btnRun.setTextSize(12);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        rLp.leftMargin = dp(8);
        btnRun.setLayoutParams(rLp);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VortaAudioService.sScenarioPlaying = false;
                VortaAudioService.sManualFreq = clampFreq(freqHz);
                VortaAudioService.sManualFreqActive = true;
                VortaAudioService.startServiceCompat(MainActivity.this);
                persistManualAndModNow();
                switchProSubTab(1);
            }
        });
        btnRow.addView(btnRun);
        card.addView(btnRow);
        parent.addView(card);
    }

    private void loadSweepBatch(SweepItem[] batch) {
        VortaAudioService.sScenarioPlaying = false;
        synchronized (VortaAudioService.sLock) {
            VortaAudioService.sSweepItems.clear();
            for (SweepItem it : batch) {
                it.isPlaying = true;
                VortaAudioService.sSweepItems.add(it);
            }
        }
        VortaAudioService.startServiceCompat(MainActivity.this);
        persistSweepsNow();
        switchProSubTab(0);
    }

    private void addProPresetButton(LinearLayout parent, String title, String desc, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, BORDER_LINE, 8, 1));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TXT_PRIMARY);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(TXT_SEC);
        d.setTextSize(12);
        d.setPadding(0, dp(3), 0, dp(8));
        card.addView(d);

        Button b = makeActionButton("스윕 등록 & 즉시 가동", ACCENT_GREEN, 36);
        b.setTextSize(12);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
        b.setOnClickListener(l);
        card.addView(b);
        parent.addView(card);
    }

    private AccordionHandle createAccordionSection(
            String title, String badgeText, int badgeColor, boolean defaultExpanded, View content, LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_SURFACE, BORDER_LINE, 10, 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(TXT_PRIMARY);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(tvTitle);

        if (badgeText != null && !badgeText.isEmpty()) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText(badgeText);
            tvBadge.setTextColor(badgeColor);
            tvBadge.setTextSize(11);
            tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
            tvBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            tvBadge.setBackground(makeBox(BG_INPUT, badgeColor, 6, 1));
            header.addView(tvBadge);
        }

        TextView tvInd = new TextView(this);
        tvInd.setTextSize(12);
        tvInd.setTypeface(Typeface.DEFAULT_BOLD);
        tvInd.setPadding(dp(10), 0, 0, 0);
        header.addView(tvInd);

        card.addView(header);
        content.setPadding(dp(12), dp(2), dp(12), dp(12));
        card.addView(content);

        final AccordionHandle handle = new AccordionHandle(content, tvInd);
        handle.setExpanded(defaultExpanded);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handle.setExpanded(!handle.isExpanded());
            }
        });

        parent.addView(card);
        return handle;
    }

    // -------------------------------------------------------------------------
    // LIVE TELEMETRY UPDATE
    // -------------------------------------------------------------------------

    private void refreshLiveTelemetry() {
        setTextIfChanged(mTvTeleGuard, buildGuardStatusText());
        boolean wired = VortaVolumeGuard.isWired(this);
        String devTag = BuildProfile.FLAVOR_ID.split("_")[0];
        setTextIfChanged(mTvTopAuxBadge, devTag + (wired ? " · AUX" : " · SPK"));

        boolean playing = VortaAudioService.isAnySourceActive();
        int borderColor = playing
                ? (VortaAudioService.sAuxSuspended ? ACCENT_RED : ACCENT_GREEN)
                : BORDER_LINE;
        if (mLastTeleBorderColor != borderColor && mTelemetryPanel != null) {
            mLastTeleBorderColor = borderColor;
            mTelemetryPanel.setBackground(makeBox(BG_SURFACE, borderColor, 10, 1));
            if (mTeleLedDot != null) {
                mTeleLedDot.setBackground(makeOval((playing && !VortaAudioService.sAuxSuspended) ? ACCENT_GREEN : ACCENT_RED));
            }
        }

        String srLabel = (VortaAudioService.sSampleRate % 1000 == 0)
                ? (VortaAudioService.sSampleRate / 1000) + "k"
                : (VortaAudioService.sSampleRate + "Hz");
        String pipeTag = srLabel + (VortaAudioService.sIsFloatPipeline ? " FLOAT" : " TPDF");

        if (playing) {
            setTextIfChanged(mTvTeleState, VortaAudioService.sAuxSuspended ? "AUX 음소거" : "출력 중");
            setTextColorIfChanged(mTvTeleState, VortaAudioService.sAuxSuspended ? ACCENT_RED : ACCENT_GREEN);
            setTextIfChanged(mTvTeleHz, String.format(Locale.US, "%.2f Hz", VortaAudioService.sInstantDisplayHz));
            setTextColorIfChanged(mTvTeleHz, ACCENT_AMBER);

            String qTag;
            if (VortaAudioService.sAuxSuspended) {
                qTag = "단자 분리 보호";
            } else if (VortaAudioService.sLiveClipCount > 0) {
                qTag = "클립 " + VortaAudioService.sLiveClipCount;
            } else if (VortaAudioService.sLivePureMode && VortaAudioService.sLiveThdPercent >= 0.0) {
                qTag = String.format(Locale.US, "THD %.3f%%", VortaAudioService.sLiveThdPercent);
            } else if (VortaAudioService.sLivePureMode) {
                qTag = "순음 안정중";
            } else {
                qTag = "복합파형";
            }
            setTextIfChanged(mTvTelePipeline, String.format(Locale.US, "%s · %s · %.1fdB · 끊김 %d",
                    pipeTag, qTag, VortaAudioService.sLiveRmsDbfs, VortaAudioService.sLiveUnderrunCount));
            setTextColorIfChanged(mTvTelePipeline, VortaAudioService.sLiveClipCount > 0 ? ACCENT_RED : ACCENT_TEAL);

            setTextIfChanged(mTvTeleMod, VortaAudioService.formatActiveModulationTelemetry());
            setTextColorIfChanged(mTvTeleMod, VortaAudioService.isActiveModulationOn() ? ACCENT_AMBER : ACCENT_TEAL);

            ScenarioCatalog.ScenarioSpec activeScen = VortaAudioService.sScenarioPlaying
                    ? ScenarioCatalog.findById(VortaAudioService.sActiveScenarioId)
                    : null;
            if (activeScen != null) {
                setTextIfChanged(mTvTeleDesc, activeScen.module.shortLabel + " · " + activeScen.title);
            } else {
                setTextIfChanged(mTvTeleDesc, "보르타 Pro 수동/스윕 출력 중");
            }
            setTextColorIfChanged(mTvTeleDesc, TXT_PRIMARY);

            if (mActiveModule == VortaModule.PRO_MANUAL && mSweepListContainer != null) {
                synchronized (VortaAudioService.sLock) {
                    for (SweepItem it : VortaAudioService.sSweepItems) {
                        TextView tv = mSweepListContainer.findViewWithTag("live_hz_" + it.id);
                        if (tv != null) {
                            setTextIfChanged(tv, it.secondaryHz > 0f
                                    ? String.format(Locale.US, "%.1f+%.1fHz", it.currentHz, it.secondaryHz)
                                    : String.format(Locale.US, "%.1f Hz", it.currentHz));
                        }
                    }
                }
            }
        } else {
            setTextIfChanged(mTvTeleState, "대기 중");
            setTextColorIfChanged(mTvTeleState, TXT_SEC);
            setTextIfChanged(mTvTeleHz, "0.00 Hz");
            setTextColorIfChanged(mTvTeleHz, TXT_DIM);
            setTextIfChanged(mTvTelePipeline, String.format(Locale.US, "%s · 대기 · 끊김 %d · 클립 %d",
                    pipeTag, VortaAudioService.sLiveUnderrunCount, VortaAudioService.sLiveClipCount));
            setTextColorIfChanged(mTvTelePipeline, TXT_DIM);
            if (mActiveModule == VortaModule.PRO_MANUAL) {
                setTextIfChanged(mTvTeleMod, VortaAudioService.formatProModulationTelemetry());
                boolean proModOn = VortaAudioService.sFmEnabled || VortaAudioService.sAmEnabled || VortaAudioService.sRmEnabled;
                setTextColorIfChanged(mTvTeleMod, proModOn ? ACCENT_AMBER : TXT_DIM);
            } else {
                setTextIfChanged(mTvTeleMod, "변조: 시나리오별 최적값 자동 적용");
                setTextColorIfChanged(mTvTeleMod, TXT_DIM);
            }
            setTextIfChanged(mTvTeleDesc, "좌측 상단 메뉴에서 기기를 선택해 시작하세요.");
            setTextColorIfChanged(mTvTeleDesc, TXT_SEC);
        }

        if (playing || mWasPlayingForScope) {
            mWasPlayingForScope = playing;
            if (mScopeView != null) mScopeView.invalidate();
        }
    }

    private static void setTextIfChanged(TextView tv, String text) {
        if (tv == null || text == null) return;
        CharSequence cur = tv.getText();
        if (cur != null && cur.toString().equals(text)) return;
        tv.setText(text);
    }

    private static void setTextColorIfChanged(TextView tv, int color) {
        if (tv == null || tv.getCurrentTextColor() == color) return;
        tv.setTextColor(color);
    }

    private Button makeActionButton(String text, int fill, int heightDp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(4), dp(2), dp(4), dp(2));
        b.setMinimumHeight(dp(heightDp));
        b.setBackground(makeBox(fill, fill, 6, 0));
        return b;
    }

    private GradientDrawable makeOval(int fill) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fill);
        return gd;
    }

    private GradientDrawable makeBox(int fill, int stroke, int cornerDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(cornerDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }

    private int dp(int dips) {
        return (int) (dips * getResources().getDisplayMetrics().density + 0.5f);
    }
}
