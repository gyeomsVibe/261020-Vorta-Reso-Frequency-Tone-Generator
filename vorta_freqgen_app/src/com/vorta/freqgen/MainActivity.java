package com.vorta.freqgen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Standalone Vorta Pro v3.3.0 UI:
 * - Zombie-proof persistent sweep scheduler with named custom preset sets
 * - Precision manual single / dual-sine beat oscillator
 * - Interactive FM/AM/RM modulation SeekBars
 * - All preset drawers collapsed by default + acoustic research verified scenario packs
 */
public class MainActivity extends Activity {
    private static final int BG_ROOT = Color.parseColor("#0B0F14");
    private static final int BG_PANEL = Color.parseColor("#131922");
    private static final int BG_CARD = Color.parseColor("#18202C");
    private static final int BG_INPUT = Color.parseColor("#0D1219");
    private static final int BORDER_SUBTLE = Color.parseColor("#253142");
    private static final int ACCENT_TEAL = Color.parseColor("#2DD4BF");
    private static final int ACCENT_AMBER = Color.parseColor("#F59E0B");
    private static final int ACCENT_CRIMSON = Color.parseColor("#E11D48");
    private static final int ACCENT_GREEN = Color.parseColor("#10B981");
    private static final int ACCENT_BLUE = Color.parseColor("#38BDF8");
    private static final int TXT_PRIMARY = Color.parseColor("#F1F5F9");
    private static final int TXT_MUTED = Color.parseColor("#94A3B8");
    private static final int TXT_DIM = Color.parseColor("#64748B");

    private LinearLayout mTelemetryCard;
    private TextView mTvStatusHeader;
    private TextView mTvStatusGuard;
    private TextView mTvLiveFreqBig;
    private TextView mTvWaveformPill;
    private TextView mTvTelemetryStats;
    private TextView mTvActiveDetails;
    private TextView mBadgeS6;
    private TextView mBadgeS7;
    private TextView mBadgeA30;

    private final Button[] mTabButtons = new Button[4];
    private final LinearLayout[] mTabContainers = new LinearLayout[4];

    private LinearLayout mSweepListContainer;
    private Button mBtnGlobalPlayStop;

    private TextView mTvSingleFreqDisplay;
    private EditText mEtSingleFreqInput;
    private EditText mEtSecondaryFreqInput;
    private Button mBtnDualToggle;
    private Button mBtnSinglePlayToggle;

    private final List<AccordionHandle> mPresetAccordions = new ArrayList<AccordionHandle>();
    private boolean mPresetsAnyExpanded = false;

    private int mCurrentTab = 0;
    private int mNextSweepId = 1;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryRunnable = new Runnable() {
        @Override
        public void run() {
            updateLiveTelemetry();
            mUiHandler.postDelayed(this, 300);
        }
    };

    private static final class AccordionHandle {
        final View content;
        final TextView toggleLabel;

        AccordionHandle(View content, TextView toggleLabel) {
            this.content = content;
            this.toggleLabel = toggleLabel;
        }

        void setExpanded(boolean expanded) {
            content.setVisibility(expanded ? View.VISIBLE : View.GONE);
            toggleLabel.setText(expanded ? "접기 ▲" : "펼치기 ▼");
            toggleLabel.setTextColor(expanded ? ACCENT_TEAL : TXT_MUTED);
        }

        boolean isExpanded() {
            return content.getVisibility() == View.VISIBLE;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            }
        }

        Intent serviceIntent = new Intent(this, AudioEngineService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        FreqGenVolumeHelper.syncTargetFromSystem(this);
        FreqGenVolumeHelper.ensureTargetVolumeIfNeeded(this, false);

        synchronized (AudioEngineService.sLock) {
            mNextSweepId = SweepPresetStore.loadInto(this, AudioEngineService.sSweepItems);
            SweepPresetStore.loadManualAndModStateIntoEngine(this);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setBackgroundColor(BG_ROOT);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(dp(12), dp(10), dp(12), dp(36));

        buildHeaderAndTelemetryCard(root);
        buildTabBar(root);

        buildSweepTab(root);
        buildSingleFreqTab(root);
        buildModulationTab(root);
        buildPresetsTab(root);

        scrollView.addView(root);
        setContentView(scrollView);

        switchTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUiHandler.post(mTelemetryRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryRunnable);
    }

    private void persistSweepsNow() {
        synchronized (AudioEngineService.sLock) {
            SweepPresetStore.saveNow(this, AudioEngineService.sSweepItems);
        }
    }

    private void persistManualAndModNow() {
        SweepPresetStore.saveManualAndModState(
                this,
                AudioEngineService.sManualFreq,
                AudioEngineService.sManualDualEnabled,
                AudioEngineService.sManualSecondaryFreq,
                AudioEngineService.sFmDepth, AudioEngineService.sFmRateHz,
                AudioEngineService.sAmDepth, AudioEngineService.sAmRateHz,
                AudioEngineService.sRmDepth, AudioEngineService.sRmRateHz);
    }

    // -------------------------------------------------------------------------
    // HEADER & LIVE INSTRUMENT TELEMETRY
    // -------------------------------------------------------------------------

    private void buildHeaderAndTelemetryCard(LinearLayout parent) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(2), dp(2), dp(2), dp(8));

        TextView title = new TextView(this);
        title.setText("VORTA PRO v3.3");
        title.setTextColor(TXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(title);

        TextView badge = new TextView(this);
        badge.setText("48kHz FLOAT / -1.0dBTP");
        badge.setTextColor(ACCENT_TEAL);
        badge.setTextSize(10);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(makeBox(BG_PANEL, ACCENT_TEAL, 6, 1));
        titleRow.addView(badge);

        parent.addView(titleRow);

        mTelemetryCard = new LinearLayout(this);
        mTelemetryCard.setOrientation(LinearLayout.VERTICAL);
        mTelemetryCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams teleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        teleLp.bottomMargin = dp(8);
        mTelemetryCard.setLayoutParams(teleLp);
        mTelemetryCard.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 10, 1));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        mTvStatusHeader = new TextView(this);
        mTvStatusHeader.setText("STANDBY");
        mTvStatusHeader.setTextColor(TXT_MUTED);
        mTvStatusHeader.setTextSize(12);
        mTvStatusHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvStatusHeader.setSingleLine(true);
        mTvStatusHeader.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        statusRow.addView(mTvStatusHeader);

        mTvStatusGuard = new TextView(this);
        mTvStatusGuard.setText(buildGuardLabel());
        mTvStatusGuard.setTextColor(ACCENT_AMBER);
        mTvStatusGuard.setTextSize(11);
        mTvStatusGuard.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(mTvStatusGuard);
        mTelemetryCard.addView(statusRow);

        LinearLayout freqRow = new LinearLayout(this);
        freqRow.setOrientation(LinearLayout.HORIZONTAL);
        freqRow.setGravity(Gravity.CENTER_VERTICAL);
        freqRow.setPadding(0, dp(4), 0, dp(2));

        mTvLiveFreqBig = new TextView(this);
        mTvLiveFreqBig.setText("0.00 Hz");
        mTvLiveFreqBig.setTextColor(TXT_DIM);
        mTvLiveFreqBig.setTextSize(30);
        mTvLiveFreqBig.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvLiveFreqBig.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        freqRow.addView(mTvLiveFreqBig);

        mTvWaveformPill = new TextView(this);
        mTvWaveformPill.setText("PURE SINE");
        mTvWaveformPill.setTextColor(ACCENT_BLUE);
        mTvWaveformPill.setTextSize(10);
        mTvWaveformPill.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTvWaveformPill.setPadding(dp(8), dp(4), dp(8), dp(4));
        mTvWaveformPill.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        freqRow.addView(mTvWaveformPill);
        mTelemetryCard.addView(freqRow);

        mTvTelemetryStats = new TextView(this);
        mTvTelemetryStats.setText("SR 48000 Hz | 32-bit Float | DC 0.00000");
        mTvTelemetryStats.setTextColor(TXT_DIM);
        mTvTelemetryStats.setTextSize(11);
        mTvTelemetryStats.setTypeface(Typeface.MONOSPACE);
        mTvTelemetryStats.setPadding(0, dp(2), 0, dp(4));
        mTelemetryCard.addView(mTvTelemetryStats);

        mTvActiveDetails = new TextView(this);
        mTvActiveDetails.setText("하단 탭에서 스윕, 단일 주파수 또는 프리셋을 실행하세요.");
        mTvActiveDetails.setTextColor(TXT_MUTED);
        mTvActiveDetails.setTextSize(12);
        mTvActiveDetails.setLineSpacing(dp(2), 1.0f);
        mTvActiveDetails.setPadding(0, 0, 0, dp(8));
        mTelemetryCard.addView(mTvActiveDetails);

        LinearLayout hwBadgeRow = new LinearLayout(this);
        hwBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        mBadgeS6 = createHwBadge("S6 스마토 x2", false);
        mBadgeS7 = createHwBadge("S7 스마토 x1", false);
        mBadgeA30 = createHwBadge("A30 트랜스+본", true);
        LinearLayout.LayoutParams lpA30 = (LinearLayout.LayoutParams) mBadgeA30.getLayoutParams();
        lpA30.rightMargin = 0;
        mBadgeA30.setLayoutParams(lpA30);
        updateHwBadgeStyle(mBadgeA30, false, "");

        hwBadgeRow.addView(mBadgeS6);
        hwBadgeRow.addView(mBadgeS7);
        hwBadgeRow.addView(mBadgeA30);
        mTelemetryCard.addView(hwBadgeRow);

        parent.addView(mTelemetryCard);
    }

    private String buildGuardLabel() {
        if (FreqGenVolumeHelper.isPersonalDevice()) {
            return "개인기기 보호(볼륨 무개입)";
        }
        if (FreqGenVolumeHelper.isA30Device()) {
            return "A30 순음 고정(볼륨 무개입)";
        }
        int vol = FreqGenVolumeHelper.getTargetVolumeStep(this);
        return String.format(Locale.US, "자유볼륨 %d/15단", vol);
    }

    private TextView createHwBadge(String text, boolean last) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setEllipsize(TextUtils.TruncateAt.END);
        badge.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.rightMargin = last ? 0 : dp(6);
        badge.setLayoutParams(lp);
        updateHwBadgeStyle(badge, false, "");
        return badge;
    }

    private void updateHwBadgeStyle(TextView badge, boolean active, String activeHex) {
        if (active) {
            int col = Color.parseColor(activeHex);
            badge.setTextColor(col);
            badge.setBackground(makeBox(BG_INPUT, col, 6, 1));
        } else {
            badge.setTextColor(TXT_DIM);
            badge.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        }
    }

    // -------------------------------------------------------------------------
    // NAVIGATION TAB BAR
    // -------------------------------------------------------------------------

    private void buildTabBar(LinearLayout parent) {
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(10);
        tabRow.setLayoutParams(lp);

        String[] tabTitles = {"스윕", "단일", "변조", "프리셋"};
        for (int i = 0; i < 4; i++) {
            final int tabIndex = i;
            Button btn = new Button(this);
            btn.setText(tabTitles[i]);
            btn.setTextSize(13);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setAllCaps(false);
            btn.setSingleLine(true);
            btn.setEllipsize(TextUtils.TruncateAt.END);
            btn.setMinWidth(0);
            btn.setMinimumWidth(0);
            btn.setPadding(dp(4), dp(2), dp(4), dp(2));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
            if (i > 0) btnLp.leftMargin = dp(6);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(tabIndex);
                }
            });
            mTabButtons[i] = btn;
            tabRow.addView(btn);
        }
        parent.addView(tabRow);
    }

    public void switchTab(int tabIndex) {
        mCurrentTab = tabIndex;
        for (int i = 0; i < 4; i++) {
            boolean active = (i == tabIndex);
            mTabContainers[i].setVisibility(active ? View.VISIBLE : View.GONE);
            mTabButtons[i].setTextColor(active ? ACCENT_TEAL : TXT_MUTED);
            mTabButtons[i].setBackground(makeBox(active ? BG_CARD : BG_PANEL, active ? ACCENT_TEAL : BORDER_SUBTLE, 8, 1));
        }
        if (tabIndex == 0) {
            refreshSweepListView();
        } else if (tabIndex == 1) {
            updateSinglePlayButtonState();
        }
    }

    // -------------------------------------------------------------------------
    // ACCORDION SECTION HELPER (ALL COLLAPSED BY DEFAULT SUPPORT)
    // -------------------------------------------------------------------------

    private AccordionHandle createAccordionSection(
            String title, String badgeText, int badgeColor, boolean defaultExpanded, View contentLayout, LinearLayout targetParent) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        card.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 10, 1));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(TXT_PRIMARY);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        headerRow.addView(tvTitle);

        if (badgeText != null && !badgeText.isEmpty()) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText(badgeText);
            tvBadge.setTextColor(badgeColor);
            tvBadge.setTextSize(11);
            tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
            tvBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
            tvBadge.setBackground(makeBox(BG_INPUT, badgeColor, 6, 1));
            headerRow.addView(tvBadge);
        }

        TextView tvToggle = new TextView(this);
        tvToggle.setTextSize(12);
        tvToggle.setTypeface(Typeface.DEFAULT_BOLD);
        tvToggle.setPadding(dp(10), 0, 0, 0);
        headerRow.addView(tvToggle);

        card.addView(headerRow);

        contentLayout.setPadding(dp(12), dp(2), dp(12), dp(12));
        card.addView(contentLayout);

        final AccordionHandle handle = new AccordionHandle(contentLayout, tvToggle);
        handle.setExpanded(defaultExpanded);

        headerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handle.setExpanded(!handle.isExpanded());
            }
        });

        targetParent.addView(card);
        return handle;
    }

    // -------------------------------------------------------------------------
    // TAB 0: PERSISTENT SWEEP SCHEDULER
    // -------------------------------------------------------------------------

    private void buildSweepTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mTabContainers[0] = container;

        LinearLayout quickContent = new LinearLayout(this);
        quickContent.setOrientation(LinearLayout.VERTICAL);
        populateSweepBandPresets(quickContent);
        createAccordionSection("대역별 빠른 불러오기", "5대역", ACCENT_BLUE, false, quickContent, container);

        // Primary Action Row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 0, 0, dp(8));

        Button btnAdd = makeActionButton("+ 스윕 추가", Color.parseColor("#0284C7"), 42);
        btnAdd.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewSweepItem();
            }
        });
        actionRow.addView(btnAdd);

        mBtnGlobalPlayStop = makeActionButton("전체 재생", ACCENT_GREEN, 42);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        stopLp.leftMargin = dp(8);
        mBtnGlobalPlayStop.setLayoutParams(stopLp);
        mBtnGlobalPlayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleGlobalPlayStop();
            }
        });
        actionRow.addView(mBtnGlobalPlayStop);
        container.addView(actionRow);

        // User Preset Management Row (Save Set / Load Set / Explicit Restore)
        LinearLayout setRow = new LinearLayout(this);
        setRow.setOrientation(LinearLayout.HORIZONTAL);
        setRow.setPadding(0, 0, 0, dp(10));

        Button btnSaveSet = makeActionButton("세트 저장", Color.parseColor("#334155"), 36);
        btnSaveSet.setTextSize(12);
        btnSaveSet.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnSaveSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSaveSetDialog();
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
                showLoadSetDialog();
            }
        });
        setRow.addView(btnLoadSet);

        Button btnReset = makeActionButton("초기값 복원", Color.parseColor("#1E293B"), 36);
        btnReset.setTextSize(12);
        btnReset.setTextColor(TXT_MUTED);
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        rsLp.leftMargin = dp(6);
        btnReset.setLayoutParams(rsLp);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmResetStarterSweeps();
            }
        });
        setRow.addView(btnReset);
        container.addView(setRow);

        mSweepListContainer = new LinearLayout(this);
        mSweepListContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(mSweepListContainer);

        parent.addView(container);
    }

    private void showSaveSetDialog() {
        final EditText etName = new EditText(this);
        etName.setHint("저장할 세트 이름 (예: 주간 방어)");
        etName.setTextColor(TXT_PRIMARY);
        new AlertDialog.Builder(this)
                .setTitle("사용자 스윕 세트 저장")
                .setView(etName)
                .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) return;
                        synchronized (AudioEngineService.sLock) {
                            SweepPresetStore.saveNamedSet(MainActivity.this, name, AudioEngineService.sSweepItems);
                        }
                        Toast.makeText(MainActivity.this, "세트 저장됨: " + name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showLoadSetDialog() {
        final List<String> names = SweepPresetStore.listNamedSets(this);
        if (names.isEmpty()) {
            Toast.makeText(this, "저장된 사용자 세트가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("저장된 스윕 세트 불러오기")
                .setItems(arr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (AudioEngineService.sLock) {
                            SweepPresetStore.loadNamedSet(MainActivity.this, arr[which], AudioEngineService.sSweepItems);
                        }
                        refreshSweepListView();
                        Toast.makeText(MainActivity.this, "불러옴: " + arr[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void confirmResetStarterSweeps() {
        new AlertDialog.Builder(this)
                .setTitle("초기 스윕 복원")
                .setMessage("현재 스윕 목록을 초기 기본 3종으로 되돌리시겠습니까?")
                .setPositiveButton("복원", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (AudioEngineService.sLock) {
                            mNextSweepId = SweepPresetStore.resetToStarterDefaults(MainActivity.this, AudioEngineService.sSweepItems);
                        }
                        refreshSweepListView();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void refreshSweepListView() {
        mSweepListContainer.removeAllViews();
        boolean anyPlaying = false;

        synchronized (AudioEngineService.sLock) {
            if (AudioEngineService.sSweepItems.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("등록된 스윕이 없습니다.\n[+ 스윕 추가] 또는 [프리셋] 탭에서 프리셋을 추가하세요.");
                empty.setTextColor(TXT_MUTED);
                empty.setTextSize(13);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(16), dp(24), dp(16), dp(24));
                empty.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 10, 1));
                mSweepListContainer.addView(empty);
            } else {
                for (int i = 0; i < AudioEngineService.sSweepItems.size(); i++) {
                    SweepItem item = AudioEngineService.sSweepItems.get(i);
                    if (item.isPlaying) anyPlaying = true;
                    mSweepListContainer.addView(createSweepItemCard(item));
                }
            }
        }

        mBtnGlobalPlayStop.setText(anyPlaying ? "전체 정지" : "전체 재생");
        mBtnGlobalPlayStop.setBackground(makeBox(anyPlaying ? ACCENT_CRIMSON : ACCENT_GREEN, anyPlaying ? ACCENT_CRIMSON : ACCENT_GREEN, 8, 0));
    }

    private View createSweepItemCard(final SweepItem item) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_CARD, item.isPlaying ? ACCENT_GREEN : BORDER_SUBTLE, 10, item.isPlaying ? 2 : 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        final EditText etTitle = new EditText(this);
        etTitle.setText(item.title);
        etTitle.setTextColor(TXT_PRIMARY);
        etTitle.setTextSize(14);
        etTitle.setTypeface(Typeface.DEFAULT_BOLD);
        etTitle.setSingleLine(true);
        etTitle.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        etTitle.setPadding(dp(8), dp(6), dp(8), dp(6));
        etTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(etTitle);

        TextView tvLiveHz = new TextView(this);
        tvLiveHz.setTag("live_hz_" + item.id);
        tvLiveHz.setText(item.secondaryHz > 0.0f
                ? String.format(Locale.US, "%.1f+%.1fHz", item.currentHz, item.secondaryHz)
                : String.format(Locale.US, "%.1f Hz", item.currentHz));
        tvLiveHz.setTextColor(ACCENT_AMBER);
        tvLiveHz.setTextSize(14);
        tvLiveHz.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvLiveHz.setPadding(dp(10), 0, 0, 0);
        row1.addView(tvLiveHz);
        card.addView(row1);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, dp(8), 0, dp(8));
        final EditText etStart = createLabeledInput(inputRow, "시작 (Hz)", String.format(Locale.US, "%.1f", item.startHz));
        final EditText etEnd = createLabeledInput(inputRow, "종료 (Hz)", String.format(Locale.US, "%.1f", item.endHz));
        final EditText etSec = createLabeledInput(inputRow, "주기 (초)", String.format(Locale.US, "%.0f", item.durationSec));
        final EditText etSec2 = createLabeledInput(inputRow, "이중 (Hz)", item.secondaryHz > 0.0f ? String.format(Locale.US, "%.1f", item.secondaryHz) : "0");
        card.addView(inputRow);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);

        final Button btnPlay = makeActionButton(item.isPlaying ? "정지" : "재생",
                item.isPlaying ? ACCENT_GREEN : Color.parseColor("#D97706"), 36);
        btnPlay.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.2f));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applySweepFieldsFromInputs(item, etTitle, etStart, etEnd, etSec, etSec2);
                item.isPlaying = !item.isPlaying;
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        ctrlRow.addView(btnPlay);

        Button btnSave = makeActionButton("저장", Color.parseColor("#0284C7"), 36);
        btnSave.setTextSize(12);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(0, dp(36), 0.7f);
        svLp.leftMargin = dp(6);
        btnSave.setLayoutParams(svLp);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applySweepFieldsFromInputs(item, etTitle, etStart, etEnd, etSec, etSec2);
                persistSweepsNow();
                Toast.makeText(MainActivity.this, "스윕 설정 저장됨", Toast.LENGTH_SHORT).show();
            }
        });
        ctrlRow.addView(btnSave);

        Button btnLoop = makeActionButton(item.isLoop ? "반복" : "1회",
                item.isLoop ? Color.parseColor("#334155") : Color.parseColor("#1E293B"), 36);
        btnLoop.setTextSize(12);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0, dp(36), 0.7f);
        lpBtn.leftMargin = dp(6);
        btnLoop.setLayoutParams(lpBtn);
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
        btnDel.setTextColor(TXT_MUTED);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(56), dp(36));
        delLp.leftMargin = dp(6);
        btnDel.setLayoutParams(delLp);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (AudioEngineService.sLock) {
                    item.isPlaying = false;
                    AudioEngineService.sSweepItems.remove(item);
                }
                persistSweepsNow();
                refreshSweepListView();
            }
        });
        ctrlRow.addView(btnDel);

        card.addView(ctrlRow);
        return card;
    }

    private void applySweepFieldsFromInputs(
            SweepItem item, EditText etTitle, EditText etStart, EditText etEnd, EditText etSec, EditText etSec2) {
        String newTitle = etTitle.getText().toString().trim();
        if (!newTitle.isEmpty()) item.title = newTitle;
        try {
            float sHz = clampFreq(Float.parseFloat(etStart.getText().toString()));
            float eHz = clampFreq(Float.parseFloat(etEnd.getText().toString()));
            float dSec = Math.max(1.0f, Float.parseFloat(etSec.getText().toString()));
            float s2Hz = Float.parseFloat(etSec2.getText().toString());
            if (s2Hz > 0.0f) s2Hz = clampFreq(s2Hz);
            else s2Hz = 0.0f;
            item.startHz = sHz;
            item.endHz = eHz;
            item.durationSec = dSec;
            item.secondaryHz = s2Hz;
        } catch (Exception ignored) {}
    }

    private EditText createLabeledInput(LinearLayout parent, String label, String defaultVal) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        box.setPadding(dp(3), 0, dp(3), 0);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TXT_MUTED);
        tv.setTextSize(10);
        tv.setSingleLine(true);
        box.addView(tv);

        EditText et = new EditText(this);
        et.setText(defaultVal);
        et.setTextColor(TXT_PRIMARY);
        et.setTextSize(13);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        et.setPadding(dp(4), dp(6), dp(4), dp(6));
        et.setGravity(Gravity.CENTER);
        box.addView(et);

        parent.addView(box);
        return et;
    }

    private void addNewSweepItem() {
        synchronized (AudioEngineService.sLock) {
            int id = mNextSweepId++;
            AudioEngineService.sSweepItems.add(
                    new SweepItem(id, "사용자 스윕 #" + id, 10.0f, 25.0f, 300.0f, true)
            );
        }
        persistSweepsNow();
        refreshSweepListView();
    }

    private void toggleGlobalPlayStop() {
        boolean anyPlaying = false;
        synchronized (AudioEngineService.sLock) {
            for (SweepItem item : AudioEngineService.sSweepItems) {
                if (item.isPlaying) {
                    anyPlaying = true;
                    break;
                }
            }
            for (SweepItem item : AudioEngineService.sSweepItems) {
                item.isPlaying = !anyPlaying;
            }
        }
        persistSweepsNow();
        refreshSweepListView();
    }

    // -------------------------------------------------------------------------
    // TAB 1: PRECISION SINGLE & DUAL-SINE OSCILLATOR
    // -------------------------------------------------------------------------

    private void buildSingleFreqTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mTabContainers[1] = container;

        LinearLayout quickMatrix = new LinearLayout(this);
        quickMatrix.setOrientation(LinearLayout.VERTICAL);
        populateMatrixPresets(quickMatrix);
        createAccordionSection("인체 공진 빠른 타겟 불러오기", "12타겟", ACCENT_AMBER, false, quickMatrix, container);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(makeBox(BG_CARD, BORDER_SUBTLE, 10, 1));

        mTvSingleFreqDisplay = new TextView(this);
        mTvSingleFreqDisplay.setText(String.format(Locale.US, "%.2f Hz", AudioEngineService.sManualFreq));
        mTvSingleFreqDisplay.setTextColor(ACCENT_AMBER);
        mTvSingleFreqDisplay.setTextSize(34);
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
                final float qh = rowHz[i];
                String label = (qh == 7.83f || qh == 18.98f)
                        ? String.format(Locale.US, "%.2f", qh)
                        : (qh == 43.2f ? "43.2 Hz" : String.format(Locale.US, "%.0f Hz", qh));
                Button chip = makeActionButton(label, Color.parseColor("#1E293B"), 34);
                chip.setTextSize(12);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
                if (i > 0) cLp.leftMargin = dp(6);
                chip.setLayoutParams(cLp);
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setManualFreqValue(qh);
                    }
                });
                chipRow.addView(chip);
            }
            card.addView(chipRow);
        }

        // Coarse & fine steps
        LinearLayout stepRow1 = new LinearLayout(this);
        stepRow1.setOrientation(LinearLayout.HORIZONTAL);
        stepRow1.setPadding(0, dp(4), 0, dp(4));
        addStepButton(stepRow1, "-5.0", -5.0f);
        addStepButton(stepRow1, "-1.0", -1.0f);
        addStepButton(stepRow1, "+1.0", +1.0f);
        addStepButton(stepRow1, "+5.0", +5.0f);
        card.addView(stepRow1);

        LinearLayout stepRow2 = new LinearLayout(this);
        stepRow2.setOrientation(LinearLayout.HORIZONTAL);
        stepRow2.setPadding(0, dp(4), 0, dp(10));
        addStepButton(stepRow2, "-0.1", -0.1f);
        addStepButton(stepRow2, "-0.01", -0.01f);
        addStepButton(stepRow2, "+0.01", +0.01f);
        addStepButton(stepRow2, "+0.1", +0.1f);
        card.addView(stepRow2);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        mEtSingleFreqInput = new EditText(this);
        mEtSingleFreqInput.setText(String.format(Locale.US, "%.2f", AudioEngineService.sManualFreq));
        mEtSingleFreqInput.setTextColor(TXT_PRIMARY);
        mEtSingleFreqInput.setTextSize(15);
        mEtSingleFreqInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEtSingleFreqInput.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        mEtSingleFreqInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        mEtSingleFreqInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        inputRow.addView(mEtSingleFreqInput);

        Button btnSet = makeActionButton("주파수 적용", Color.parseColor("#0284C7"), 42);
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(dp(96), dp(42));
        setLp.leftMargin = dp(8);
        btnSet.setLayoutParams(setLp);
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    setManualFreqValue(Float.parseFloat(mEtSingleFreqInput.getText().toString()));
                } catch (Exception ignored) {}
            }
        });
        inputRow.addView(btnSet);
        card.addView(inputRow);

        // Dual-sine linear superposition beat option
        LinearLayout dualRow = new LinearLayout(this);
        dualRow.setOrientation(LinearLayout.HORIZONTAL);
        dualRow.setGravity(Gravity.CENTER_VERTICAL);
        dualRow.setPadding(0, dp(10), 0, dp(4));

        mBtnDualToggle = makeActionButton(
                AudioEngineService.sManualDualEnabled ? "이중 비트 ON" : "이중 비트 OFF",
                AudioEngineService.sManualDualEnabled ? ACCENT_TEAL : Color.parseColor("#334155"), 38);
        mBtnDualToggle.setTextSize(12);
        mBtnDualToggle.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        mBtnDualToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEngineService.sManualDualEnabled = !AudioEngineService.sManualDualEnabled;
                try {
                    AudioEngineService.sManualSecondaryFreq = clampFreq(
                            Float.parseFloat(mEtSecondaryFreqInput.getText().toString()));
                } catch (Exception ignored) {}
                persistManualAndModNow();
                updateSinglePlayButtonState();
            }
        });
        dualRow.addView(mBtnDualToggle);

        mEtSecondaryFreqInput = new EditText(this);
        mEtSecondaryFreqInput.setText(String.format(Locale.US, "%.2f", AudioEngineService.sManualSecondaryFreq));
        mEtSecondaryFreqInput.setTextColor(TXT_PRIMARY);
        mEtSecondaryFreqInput.setTextSize(13);
        mEtSecondaryFreqInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEtSecondaryFreqInput.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 6, 1));
        mEtSecondaryFreqInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams dEtLp = new LinearLayout.LayoutParams(0, dp(38), 0.9f);
        dEtLp.leftMargin = dp(8);
        mEtSecondaryFreqInput.setLayoutParams(dEtLp);
        dualRow.addView(mEtSecondaryFreqInput);

        Button btnApplyDual = makeActionButton("f2 적용", Color.parseColor("#334155"), 38);
        btnApplyDual.setTextSize(12);
        LinearLayout.LayoutParams dBtnLp = new LinearLayout.LayoutParams(dp(72), dp(38));
        dBtnLp.leftMargin = dp(6);
        btnApplyDual.setLayoutParams(dBtnLp);
        btnApplyDual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    float f2 = clampFreq(Float.parseFloat(mEtSecondaryFreqInput.getText().toString()));
                    AudioEngineService.sManualSecondaryFreq = f2;
                    AudioEngineService.sManualDualEnabled = true;
                    persistManualAndModNow();
                    updateSinglePlayButtonState();
                } catch (Exception ignored) {}
            }
        });
        dualRow.addView(btnApplyDual);
        card.addView(dualRow);

        mBtnSinglePlayToggle = makeActionButton("단일 주파수 발진 시작", ACCENT_GREEN, 48);
        mBtnSinglePlayToggle.setTextSize(15);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        pLp.topMargin = dp(12);
        mBtnSinglePlayToggle.setLayoutParams(pLp);
        mBtnSinglePlayToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEngineService.sManualFreqActive = !AudioEngineService.sManualFreqActive;
                updateSinglePlayButtonState();
            }
        });
        card.addView(mBtnSinglePlayToggle);

        container.addView(card);
        parent.addView(container);
    }

    private float clampFreq(float hz) {
        return Math.min(Math.max(hz, AudioEngineService.MIN_FREQ_HZ), AudioEngineService.MAX_FREQ_HZ);
    }

    private void setManualFreqValue(float rawHz) {
        float f = clampFreq(rawHz);
        AudioEngineService.sManualFreq = f;
        persistManualAndModNow();
        updateSinglePlayButtonState();
    }

    private void addStepButton(LinearLayout parent, String label, final float delta) {
        Button btn = makeActionButton(label, Color.parseColor("#253142"), 36);
        btn.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setManualFreqValue(AudioEngineService.sManualFreq + delta);
            }
        });
        parent.addView(btn);
    }

    private void updateSinglePlayButtonState() {
        if (mBtnSinglePlayToggle == null) return;
        boolean active = AudioEngineService.sManualFreqActive;
        mBtnSinglePlayToggle.setText(active ? "단일 주파수 정지" : "단일 주파수 발진 시작");
        mBtnSinglePlayToggle.setBackground(makeBox(active ? ACCENT_CRIMSON : ACCENT_GREEN, active ? ACCENT_CRIMSON : ACCENT_GREEN, 8, 0));
        if (AudioEngineService.sManualDualEnabled) {
            mTvSingleFreqDisplay.setText(String.format(Locale.US, "%.2f + %.2f Hz",
                    AudioEngineService.sManualFreq, AudioEngineService.sManualSecondaryFreq));
        } else {
            mTvSingleFreqDisplay.setText(String.format(Locale.US, "%.2f Hz", AudioEngineService.sManualFreq));
        }
        mEtSingleFreqInput.setText(String.format(Locale.US, "%.2f", AudioEngineService.sManualFreq));
        if (mBtnDualToggle != null) {
            boolean dOn = AudioEngineService.sManualDualEnabled;
            mBtnDualToggle.setText(dOn ? "이중 비트 ON" : "이중 비트 OFF");
            mBtnDualToggle.setBackground(makeBox(dOn ? Color.parseColor("#0F766E") : Color.parseColor("#334155"),
                    dOn ? ACCENT_TEAL : BORDER_SUBTLE, 6, 1));
        }
    }

    // -------------------------------------------------------------------------
    // TAB 2: INTERACTIVE MODULATION ENGINE (SEEKBARS + PERSISTENCE)
    // -------------------------------------------------------------------------

    private void buildModulationTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mTabContainers[2] = container;

        addInteractiveModCard(
                container, "FM 주파수 변조 (Vibrato)",
                "깊이", Math.round(AudioEngineService.sFmDepth * 100.0f), 25, "%",
                "속도", Math.round(AudioEngineService.sFmRateHz * 10.0f), 150, 0.1f, "Hz",
                AudioEngineService.sFmEnabled,
                new ModChangeListener() {
                    @Override
                    public void onUpdate(boolean enabled, int p1, int p2) {
                        AudioEngineService.sFmEnabled = enabled;
                        AudioEngineService.sFmDepth = p1 / 100.0f;
                        AudioEngineService.sFmRateHz = Math.max(0.2f, p2 * 0.1f);
                        persistManualAndModNow();
                    }
                });

        addInteractiveModCard(
                container, "AM 진폭 변조 (Tremolo)",
                "깊이", Math.round(AudioEngineService.sAmDepth * 100.0f), 50, "%",
                "속도", Math.round(AudioEngineService.sAmRateHz * 10.0f), 150, 0.1f, "Hz",
                AudioEngineService.sAmEnabled,
                new ModChangeListener() {
                    @Override
                    public void onUpdate(boolean enabled, int p1, int p2) {
                        AudioEngineService.sAmEnabled = enabled;
                        AudioEngineService.sAmDepth = p1 / 100.0f;
                        AudioEngineService.sAmRateHz = Math.max(0.2f, p2 * 0.1f);
                        persistManualAndModNow();
                    }
                });

        addInteractiveModCard(
                container, "RM 링 변조 (사이드밴드 비트)",
                "깊이", Math.round(AudioEngineService.sRmDepth * 100.0f), 50, "%",
                "캐리어", Math.round(AudioEngineService.sRmRateHz), 60, 1.0f, "Hz",
                AudioEngineService.sRmEnabled,
                new ModChangeListener() {
                    @Override
                    public void onUpdate(boolean enabled, int p1, int p2) {
                        AudioEngineService.sRmEnabled = enabled;
                        AudioEngineService.sRmDepth = p1 / 100.0f;
                        AudioEngineService.sRmRateHz = Math.max(2.0f, (float) p2);
                        persistManualAndModNow();
                    }
                });

        parent.addView(container);
    }

    private interface ModChangeListener {
        void onUpdate(boolean enabled, int p1, int p2);
    }

    private void addInteractiveModCard(
            LinearLayout parent, String title,
            final String label1, int init1, int max1, final String unit1,
            final String label2, int init2, int max2, final float scale2, final String unit2,
            final boolean initEnabled, final ModChangeListener listener) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_CARD, BORDER_SUBTLE, 10, 1));

        final boolean[] enabledState = {initEnabled};
        final int[] vals = {Math.min(max1, Math.max(0, init1)), Math.min(max2, Math.max(1, init2))};

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(ACCENT_BLUE);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topRow.addView(tvTitle);

        final Button toggleBtn = new Button(this);
        toggleBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(34)));
        toggleBtn.setTextSize(12);
        toggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        toggleBtn.setTextColor(Color.WHITE);
        updateModulationButtonState(toggleBtn, enabledState[0]);
        topRow.addView(toggleBtn);
        card.addView(topRow);

        final TextView tvRead1 = new TextView(this);
        tvRead1.setTextColor(TXT_MUTED);
        tvRead1.setTextSize(12);
        tvRead1.setPadding(0, dp(8), 0, dp(2));
        tvRead1.setText(String.format(Locale.US, "%s: %d %s", label1, vals[0], unit1));
        card.addView(tvRead1);

        SeekBar sb1 = new SeekBar(this);
        sb1.setMax(max1);
        sb1.setProgress(vals[0]);
        sb1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                vals[0] = progress;
                tvRead1.setText(String.format(Locale.US, "%s: %d %s", label1, progress, unit1));
                listener.onUpdate(enabledState[0], vals[0], vals[1]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sb1);

        final TextView tvRead2 = new TextView(this);
        tvRead2.setTextColor(TXT_MUTED);
        tvRead2.setTextSize(12);
        tvRead2.setPadding(0, dp(4), 0, dp(2));
        tvRead2.setText(String.format(Locale.US, "%s: %.1f %s", label2, vals[1] * scale2, unit2));
        card.addView(tvRead2);

        SeekBar sb2 = new SeekBar(this);
        sb2.setMax(max2);
        sb2.setProgress(vals[1]);
        sb2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                vals[1] = Math.max(1, progress);
                tvRead2.setText(String.format(Locale.US, "%s: %.1f %s", label2, vals[1] * scale2, unit2));
                listener.onUpdate(enabledState[0], vals[0], vals[1]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sb2);

        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enabledState[0] = !enabledState[0];
                updateModulationButtonState(toggleBtn, enabledState[0]);
                listener.onUpdate(enabledState[0], vals[0], vals[1]);
            }
        });

        parent.addView(card);
    }

    private void updateModulationButtonState(Button btn, boolean isEnabled) {
        btn.setText(isEnabled ? "ON" : "OFF");
        btn.setBackground(makeBox(isEnabled ? ACCENT_GREEN : Color.parseColor("#334155"),
                isEnabled ? ACCENT_GREEN : BORDER_SUBTLE, 6, 1));
    }

    // -------------------------------------------------------------------------
    // TAB 3: PRESETS TAB (ALL ACCORDIONS COLLAPSED BY DEFAULT)
    // -------------------------------------------------------------------------

    private void buildPresetsTab(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mTabContainers[3] = container;
        mPresetAccordions.clear();

        // Master header row to toggle all preset accordions cleanly
        LinearLayout hdrRow = new LinearLayout(this);
        hdrRow.setOrientation(LinearLayout.HORIZONTAL);
        hdrRow.setGravity(Gravity.CENTER_VERTICAL);
        hdrRow.setPadding(dp(2), 0, dp(2), dp(8));

        TextView hint = new TextView(this);
        hint.setText("기본 닫힘 상태 (원하는 항목만 펼쳐 사용)");
        hint.setTextColor(TXT_MUTED);
        hint.setTextSize(12);
        hint.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        hdrRow.addView(hint);

        final Button btnToggleAll = makeActionButton("모두 펼치기", Color.parseColor("#1E293B"), 32);
        btnToggleAll.setTextSize(11);
        btnToggleAll.setTextColor(ACCENT_TEAL);
        btnToggleAll.setLayoutParams(new LinearLayout.LayoutParams(dp(92), dp(32)));
        btnToggleAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPresetsAnyExpanded = !mPresetsAnyExpanded;
                for (int i = 0; i < mPresetAccordions.size(); i++) {
                    mPresetAccordions.get(i).setExpanded(mPresetsAnyExpanded);
                }
                btnToggleAll.setText(mPresetsAnyExpanded ? "모두 접기" : "모두 펼치기");
            }
        });
        hdrRow.addView(btnToggleAll);
        container.addView(hdrRow);

        // ACCORDION 1 (COLLAPSED BY DEFAULT): Research-derived Pro packs
        LinearLayout isoContent = new LinearLayout(this);
        isoContent.setOrientation(LinearLayout.VERTICAL);
        populateResearchPresets(isoContent);
        mPresetAccordions.add(createAccordionSection(
                "실증 확장 스윕 프리셋", "확장 3종", ACCENT_TEAL, false, isoContent, container));

        // ACCORDION 2 (FIXED: defaultExpanded = FALSE! Was previously hardcoded true)
        LinearLayout sweepContent = new LinearLayout(this);
        sweepContent.setOrientation(LinearLayout.VERTICAL);
        populateSweepBandPresets(sweepContent);
        mPresetAccordions.add(createAccordionSection(
                "대역별 스윕 프리셋", "5대역", ACCENT_BLUE, false, sweepContent, container));

        // ACCORDION 3 (COLLAPSED BY DEFAULT)
        LinearLayout matrixContent = new LinearLayout(this);
        matrixContent.setOrientation(LinearLayout.VERTICAL);
        populateMatrixPresets(matrixContent);
        mPresetAccordions.add(createAccordionSection(
                "인체 공진 매트릭스 단일 프리셋", "12타겟", ACCENT_AMBER, false, matrixContent, container));

        // ACCORDION 4 (COLLAPSED BY DEFAULT)
        LinearLayout deviceContent = new LinearLayout(this);
        deviceContent.setOrientation(LinearLayout.VERTICAL);
        populateDeviceScenarios(deviceContent);
        mPresetAccordions.add(createAccordionSection(
                "기기별 특화 프리셋", "3종", ACCENT_GREEN, false, deviceContent, container));

        parent.addView(container);
    }

    private void populateResearchPresets(LinearLayout parent) {
        addDeviceScenarioCard(parent,
                "1. 슬래브 굽힘 광역 2구간 (40~50 / 50~63 Hz)",
                "슬래브 1차 굽힘 고유진동 및 공간 정재파 순수 사인 스윕",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "슬래브 40~50Hz", 40.0f, 50.0f, 360.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "룸모드 50~63Hz", 50.0f, 63.0f, 480.0f, true));
                            for (SweepItem it : AudioEngineService.sSweepItems) it.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });

        addDeviceScenarioCard(parent,
                "2. 수직·흉곽·촉각 3구간 (4~8 / 8~12 / 30~50 Hz)",
                "척추·내장(4~8Hz), 흉벽·어깨(8~12Hz), 촉각(30~50Hz) 순수 정현파 스윕",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "수직공진 4~8Hz", 4.0f, 8.0f, 600.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "흉곽전이 8~12Hz", 8.0f, 12.0f, 480.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "촉각타격 30~50Hz", 30.0f, 50.0f, 420.0f, true));
                            for (SweepItem it : AudioEngineService.sSweepItems) it.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });

        addDeviceScenarioCard(parent,
                "3. 저역 상향 마스킹 4Hz 맥놀이 (24+28 / 48+52 Hz)",
                "고조파 왜곡 없는 선형 중첩(24+28 Hz 및 48+52 Hz) 순수 음향 맥놀이",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "저역 4Hz 비트 (24+28Hz)", 24.0f, 24.0f, 600.0f, true, 28.0f));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "마스킹 4Hz 비트 (48+52Hz)", 48.0f, 48.0f, 600.0f, true, 52.0f));
                            for (SweepItem it : AudioEngineService.sSweepItems) it.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });
    }

    private void populateSweepBandPresets(LinearLayout parent) {
        Button btnAllBands = makeActionButton("5대 대역 전체 등록 & 가동", Color.parseColor("#0284C7"), 40);
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        allLp.bottomMargin = dp(10);
        btnAllBands.setLayoutParams(allLp);
        btnAllBands.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (AudioEngineService.sLock) {
                    AudioEngineService.sSweepItems.clear();
                    AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "1~4Hz 극초저주파 (20s)", 1.0f, 4.0f, 20.0f, true));
                    AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "5~11Hz 수면리듬붕괴 (35s)", 5.0f, 11.0f, 35.0f, true));
                    AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "12~22Hz 안구·내장 (55s)", 12.0f, 22.0f, 55.0f, true));
                    AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "27~36Hz 복부·흉부 (50s)", 27.0f, 36.0f, 50.0f, true));
                    AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "38~55Hz 고막압박 (90s)", 38.0f, 55.0f, 90.0f, true));
                    for (SweepItem item : AudioEngineService.sSweepItems) item.isPlaying = true;
                }
                persistSweepsNow();
                switchTab(0);
            }
        });
        parent.addView(btnAllBands);

        addSweepBandCard(parent, "1 ~ 4 Hz", 1.0f, 4.0f, 20.0f, "20s", "극초저주파 심층 진동 대역", "#38BDF8");
        addSweepBandCard(parent, "5 ~ 11 Hz", 5.0f, 11.0f, 35.0f, "35s", "뇌파 간섭 및 수면리듬 교란", "#F43F5E");
        addSweepBandCard(parent, "12 ~ 22 Hz", 12.0f, 22.0f, 55.0f, "55s", "안구 압박 및 내장 공진", "#F59E0B");
        addSweepBandCard(parent, "27 ~ 36 Hz", 27.0f, 36.0f, 50.0f, "50s", "복부·흉부 진동 공진", "#10B981");
        addSweepBandCard(parent, "38 ~ 55 Hz", 38.0f, 55.0f, 90.0f, "90s", "고막 압박 (55Hz 상한)", "#A855F7");
    }

    private void addSweepBandCard(
            LinearLayout parent, final String bandName, final float startHz, final float endHz,
            final float durationSec, final String durationDesc, final String effectDesc, final String colorHex) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 8, 1));

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
        tvDur.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 6, 1));
        row1.addView(tvDur);
        card.addView(row1);

        TextView tvEff = new TextView(this);
        tvEff.setText(effectDesc);
        tvEff.setTextColor(TXT_MUTED);
        tvEff.setTextSize(12);
        tvEff.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvEff);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnInput = makeActionButton("목록 추가", Color.parseColor("#334155"), 36);
        btnInput.setTextSize(12);
        btnInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        btnInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (AudioEngineService.sLock) {
                    AudioEngineService.sSweepItems.add(
                            new SweepItem(mNextSweepId++, bandName + " (" + durationDesc + ")", startHz, endHz, durationSec, true));
                }
                persistSweepsNow();
                switchTab(0);
            }
        });
        btnRow.addView(btnInput);

        Button btnRun = makeActionButton("추가 및 재생", ACCENT_GREEN, 36);
        btnRun.setTextSize(12);
        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        runLp.leftMargin = dp(8);
        btnRun.setLayoutParams(runLp);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (AudioEngineService.sLock) {
                    SweepItem item = new SweepItem(mNextSweepId++, bandName + " (" + durationDesc + ")", startHz, endHz, durationSec, true);
                    item.isPlaying = true;
                    AudioEngineService.sSweepItems.add(item);
                }
                persistSweepsNow();
                switchTab(0);
            }
        });
        btnRow.addView(btnRun);

        card.addView(btnRow);
        parent.addView(card);
    }

    private void populateMatrixPresets(LinearLayout parent) {
        addMatrixItem(parent, 7.0f, "전정기관 / 뇌간", "어지러움 및 기저 긴장", "A30 트랜스+본", false);
        addMatrixItem(parent, 10.5f, "심박변동 / 자율신경", "불규칙 맥박 및 피로", "A30 트랜스+본", false);
        addMatrixItem(parent, 16.0f, "복부 / 위장", "복부 진동 및 압박", "S6 스마토 2기", false);
        addMatrixItem(parent, 18.0f, "전두엽 / 두개골", "골전도 두개 공진", "A30 트랜스+본", true);
        addMatrixItem(parent, 21.0f, "시신경 / 안구", "안구 압박 및 초점 저하", "S6 스마토 2기", true);
        addMatrixItem(parent, 22.0f, "내이 / 평형", "방향감각 교란", "A30 트랜스+본", true);
        addMatrixItem(parent, 24.0f, "흉곽 / 폐", "흉부 공진 고음압", "S6 스마토 2기", true);
        addMatrixItem(parent, 25.0f, "하복부 / 대장", "하부 진동 유지", "S6 / S7", true);
        addMatrixItem(parent, 28.0f, "골반 / 하부 장기", "중심 타격점", "S6 / S7", true);
        addMatrixItem(parent, 31.0f, "위장 / 간", "복부 묵직한 타격", "S7 스마토 1기", false);
        addMatrixItem(parent, 40.0f, "전신 / 슬래브", "벽면·바닥 구조 전달", "S6 / A30", false);
        addMatrixItem(parent, 55.0f, "구조물 / 체표면", "실내 1차 룸모드", "S6 / S7", true);
    }

    private void addMatrixItem(
            LinearLayout parent, final float freqHz, final String targetPart,
            final String symptoms, final String speaker, final boolean isKeyTarget) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, isKeyTarget ? ACCENT_AMBER : BORDER_SUBTLE, 8, isKeyTarget ? 2 : 1));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvFreq = new TextView(this);
        tvFreq.setText(String.format(Locale.US, "%.1f Hz", freqHz) + (isKeyTarget ? " (핵심)" : ""));
        tvFreq.setTextColor(isKeyTarget ? ACCENT_AMBER : ACCENT_BLUE);
        tvFreq.setTextSize(15);
        tvFreq.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFreq.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row1.addView(tvFreq);

        TextView tvSpk = new TextView(this);
        tvSpk.setText(speaker);
        tvSpk.setTextColor(ACCENT_TEAL);
        tvSpk.setTextSize(11);
        tvSpk.setPadding(dp(6), dp(2), dp(6), dp(2));
        tvSpk.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 6, 1));
        row1.addView(tvSpk);
        card.addView(row1);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(targetPart + " · " + symptoms);
        tvDesc.setTextColor(TXT_MUTED);
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
                setManualFreqValue(freqHz);
                switchTab(1);
            }
        });
        btnRow.addView(btnSet);

        Button btnRun = makeActionButton("즉시 발진", Color.parseColor("#D97706"), 36);
        btnRun.setTextSize(12);
        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        runLp.leftMargin = dp(8);
        btnRun.setLayoutParams(runLp);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioEngineService.sManualFreq = freqHz;
                AudioEngineService.sManualFreqActive = true;
                persistManualAndModNow();
                switchTab(1);
            }
        });
        btnRow.addView(btnRun);

        card.addView(btnRow);
        parent.addView(card);
    }

    private void populateDeviceScenarios(LinearLayout parent) {
        addDeviceScenarioCard(parent, "갤럭시 S6 (스마토 2기 · 듀얼 BTL 68W)",
                "17.5~19.8 Hz(440s), 4.0~6.2 Hz(290s), 20.7~27.7 Hz(740s)",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S6 17.5~19.8Hz", 17.5f, 19.8f, 440.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S6 4.0~6.2Hz", 4.0f, 6.2f, 290.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S6 20.7~27.7Hz", 20.7f, 27.7f, 740.0f, true));
                            for (SweepItem item : AudioEngineService.sSweepItems) item.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });

        addDeviceScenarioCard(parent, "갤럭시 S7 (스마토 1기 · 단독 우퍼 240W)",
                "21.0~24.5 Hz(1250s), 2.4~4.6 Hz(1480s), 28.0 Hz 고정",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S7 21.0~24.5Hz", 21.0f, 24.5f, 1250.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S7 2.4~4.6Hz", 2.4f, 4.6f, 1480.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "S7 28.0Hz 고정", 28.0f, 28.0f, 3600.0f, true));
                            for (SweepItem item : AudioEngineService.sSweepItems) item.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });

        addDeviceScenarioCard(parent, "갤럭시 A30 (트랜스+본 · 골전도 120W)",
                "24.0~36.0 Hz(6240s), 5.0~24.5 Hz(12000s), 55.0 Hz 고정",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (AudioEngineService.sLock) {
                            AudioEngineService.sSweepItems.clear();
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "A30 24.0~36.0Hz", 24.0f, 36.0f, 6240.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "A30 5.0~24.5Hz", 5.0f, 24.5f, 12000.0f, true));
                            AudioEngineService.sSweepItems.add(new SweepItem(mNextSweepId++, "A30 55.0Hz 고정", 55.0f, 55.0f, 3600.0f, true));
                            for (SweepItem item : AudioEngineService.sSweepItems) item.isPlaying = true;
                        }
                        persistSweepsNow();
                        switchTab(0);
                    }
                });
    }

    private void addDeviceScenarioCard(LinearLayout parent, String title, String desc, View.OnClickListener clickListener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setBackground(makeBox(BG_INPUT, BORDER_SUBTLE, 8, 1));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(ACCENT_TEAL);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextColor(TXT_MUTED);
        tvDesc.setTextSize(12);
        tvDesc.setLineSpacing(dp(2), 1.0f);
        tvDesc.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvDesc);

        Button btnLoad = makeActionButton("시나리오 불러오기 & 즉시 가동", Color.parseColor("#059669"), 38);
        btnLoad.setTextSize(12);
        btnLoad.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));
        btnLoad.setOnClickListener(clickListener);
        card.addView(btnLoad);

        parent.addView(card);
    }

    // -------------------------------------------------------------------------
    // TELEMETRY LOOP
    // -------------------------------------------------------------------------

    private void updateLiveTelemetry() {
        boolean manualActive = AudioEngineService.sManualFreqActive;
        boolean anyPlaying = manualActive;
        float primaryHz = manualActive ? AudioEngineService.sManualFreq : 0.0f;
        int activeSweepCount = 0;
        StringBuilder activeSweepNames = new StringBuilder();

        synchronized (AudioEngineService.sLock) {
            for (SweepItem item : AudioEngineService.sSweepItems) {
                if (item.isPlaying) {
                    anyPlaying = true;
                    activeSweepCount++;
                    if (primaryHz == 0.0f) primaryHz = item.currentHz;
                    if (activeSweepNames.length() > 0) activeSweepNames.append(" / ");
                    activeSweepNames.append(String.format(Locale.US, "%s(%.1fHz)", item.title, item.currentHz));
                }
                if (mSweepListContainer != null) {
                    TextView tv = mSweepListContainer.findViewWithTag("live_hz_" + item.id);
                    if (tv != null) {
                        tv.setText(item.secondaryHz > 0.0f
                                ? String.format(Locale.US, "%.1f+%.1fHz", item.currentHz, item.secondaryHz)
                                : String.format(Locale.US, "%.1f Hz", item.currentHz));
                    }
                }
            }
        }

        if (mTvStatusGuard != null) {
            mTvStatusGuard.setText(buildGuardLabel());
        }

        if (mTvStatusHeader != null && mTvLiveFreqBig != null) {
            if (anyPlaying) {
                mTvStatusHeader.setText(AudioEngineService.sAuxSuspended ? "AUX MUTED (RECONNECTING)" : "LIVE OUTPUT");
                mTvStatusHeader.setTextColor(AudioEngineService.sAuxSuspended ? ACCENT_CRIMSON : ACCENT_GREEN);
                mTvLiveFreqBig.setText(String.format(Locale.US, "%.2f Hz", primaryHz));
                mTvLiveFreqBig.setTextColor(ACCENT_AMBER);
                mTelemetryCard.setBackground(makeBox(BG_PANEL, AudioEngineService.sAuxSuspended ? ACCENT_CRIMSON : ACCENT_GREEN, 10, 1));
                mTvWaveformPill.setText(AudioEngineService.sManualDualEnabled ? "DUAL SINE" : "PURE SINE");
                mTvTelemetryStats.setText(String.format(Locale.US, "SR %d Hz | %s | RMS %.1f dBFS | DC %.5f",
                        AudioEngineService.sSampleRate,
                        AudioEngineService.sIsFloatPipeline ? "32-bit Float" : "16-bit TPDF",
                        AudioEngineService.sLiveRmsDbfs,
                        AudioEngineService.sLiveDcOffset));
                mTvTelemetryStats.setTextColor(ACCENT_TEAL);

                StringBuilder details = new StringBuilder();
                if (manualActive) {
                    details.append(AudioEngineService.sManualDualEnabled
                            ? String.format(Locale.US, "단일 이중비트 %.2f+%.2f Hz", AudioEngineService.sManualFreq, AudioEngineService.sManualSecondaryFreq)
                            : String.format(Locale.US, "단일 수동 %.2f Hz", AudioEngineService.sManualFreq));
                }
                if (activeSweepCount > 0) {
                    if (details.length() > 0) details.append(" | ");
                    details.append("스윕 ").append(activeSweepCount).append("개: ").append(activeSweepNames);
                }
                mTvActiveDetails.setText(details.toString());
                mTvActiveDetails.setTextColor(TXT_PRIMARY);

                updateHwBadgeStyle(mBadgeA30, primaryHz <= 22.0f, "#38BDF8");
                updateHwBadgeStyle(mBadgeS6, primaryHz >= 12.0f && primaryHz <= 30.0f || primaryHz > 36.0f, "#10B981");
                updateHwBadgeStyle(mBadgeS7, primaryHz >= 25.0f, "#F59E0B");
            } else {
                mTvStatusHeader.setText("STANDBY");
                mTvStatusHeader.setTextColor(TXT_MUTED);
                mTvLiveFreqBig.setText("0.00 Hz");
                mTvLiveFreqBig.setTextColor(TXT_DIM);
                mTelemetryCard.setBackground(makeBox(BG_PANEL, BORDER_SUBTLE, 10, 1));
                mTvTelemetryStats.setText(String.format(Locale.US, "SR %d Hz | %s | -1.0 dBTP Ready",
                        AudioEngineService.sSampleRate,
                        AudioEngineService.sIsFloatPipeline ? "32-bit Float" : "16-bit TPDF"));
                mTvTelemetryStats.setTextColor(TXT_DIM);
                mTvActiveDetails.setText("대기 중: 하단 탭에서 스윕 또는 주파수를 시작하세요.");
                mTvActiveDetails.setTextColor(TXT_MUTED);
                updateHwBadgeStyle(mBadgeS6, false, "");
                updateHwBadgeStyle(mBadgeS7, false, "");
                updateHwBadgeStyle(mBadgeA30, false, "");
            }
        }
    }

    private Button makeActionButton(String text, int fillColor, int heightDp) {
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
        b.setBackground(makeBox(fillColor, fillColor, 6, 0));
        return b;
    }

    private GradientDrawable makeBox(int fillColor, int strokeColor, int cornerDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(cornerDp));
        if (strokeDp > 0) {
            gd.setStroke(dp(strokeDp), strokeColor);
        }
        return gd;
    }

    private int dp(int dips) {
        return (int) (dips * getResources().getDisplayMetrics().density + 0.5f);
    }
}
