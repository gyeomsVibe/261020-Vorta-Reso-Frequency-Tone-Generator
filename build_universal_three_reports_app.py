# -*- coding: utf-8 -*-
import os
import sys

def get_activity_code(pkg_name, service_cls, helper_cls, app_title, app_subtitle, amp_guide_default):
    return f'''package {pkg_name};

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {{
    private static final String TAG = "MainActivity";

    private TextView mStatusMode;
    private TextView mStatusFreq;
    private TextView mStatusMod;
    private TextView mStatusDsp;
    private TextView mStatusSignal;
    private TextView mStatusKnob;

    // 3-Way Tri-Frameset / Split Screen Layout Containers (전체 / 메인 / 서브1 / 서브2)
    private LinearLayout mFrameMainContainer;
    private LinearLayout mFrameSub1Container;
    private LinearLayout mFrameSub2Container;
    private LinearLayout mSplitDivider1;
    private LinearLayout mSplitDivider2;
    private TextView[] mSplitViewButtons = new TextView[4];
    private int mCurrentSplitMode = 0; // 0: 전체, 1: 메인, 2: 서브1, 3: 서브2

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryUpdater = new Runnable() {{
        @Override
        public void run() {{
            syncTelemetryUi();
            mUiHandler.postDelayed(this, 1000);
        }}
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);

        // AMOLED 보호 및 배터리 절전을 위해 화면 정상 끄기/절전 허용
        // 백그라운드 무중단 발진은 Foreground Service + PARTIAL_WAKE_LOCK으로 24시간 완벽 유지
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        {service_cls}.loadConfig(this);
        {helper_cls}.ensureTargetVolumeIfNeeded(this, true);

        float density = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0A0E17"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(16 * density), (int)(18 * density), (int)(16 * density), (int)(28 * density));

        // Header Title
        TextView titleTv = new TextView(this);
        titleTv.setText("{app_title}");
        titleTv.setTextSize(22);
        titleTv.setTextColor(Color.parseColor("#00F0FF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setPadding(0, 0, 0, (int)(4 * density));
        root.addView(titleTv);

        TextView subTitleTv = new TextView(this);
        subTitleTv.setText("{app_subtitle}");
        subTitleTv.setTextSize(12);
        subTitleTv.setTextColor(Color.parseColor("#E6EDF3"));
        subTitleTv.setPadding(0, 0, 0, (int)(14 * density));
        root.addView(subTitleTv);

        // =========================================================================
        // 🎛️ 스플릿 뷰 선택 콘솔 (전체 / 메인 / 서브1 / 서브2)
        // =========================================================================
        LinearLayout splitConsoleCard = new LinearLayout(this);
        splitConsoleCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable scBg = new GradientDrawable();
        scBg.setColor(Color.parseColor("#131D2E"));
        scBg.setCornerRadius(10 * density);
        scBg.setStroke((int)(1.5f * density), Color.parseColor("#00E5FF"));
        splitConsoleCard.setBackground(scBg);
        splitConsoleCard.setPadding((int)(12 * density), (int)(12 * density), (int)(12 * density), (int)(12 * density));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scLp.setMargins(0, 0, 0, (int)(14 * density));
        splitConsoleCard.setLayoutParams(scLp);

        TextView scTitle = new TextView(this);
        scTitle.setText("🎛️ 3대 마스터 보고서 1:1 스플릿 뷰 선택 콘솔");
        scTitle.setTextSize(14);
        scTitle.setTextColor(Color.parseColor("#00F0FF"));
        scTitle.setTypeface(Typeface.DEFAULT_BOLD);
        scTitle.setPadding(0, 0, 0, (int)(8 * density));
        splitConsoleCard.addView(scTitle);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setWeightSum(4.0f);

        String[] tabLabels = {{"🎛️ 전체", "🎯 메인", "⚡ 서브1", "🛡️ 서브2"}};
        for (int i = 0; i < 4; i++) {{
            final int modeIdx = i;
            TextView btn = new TextView(this);
            btn.setText(tabLabels[i]);
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, (int)(10 * density), 0, (int)(10 * density));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            if (i > 0) btnLp.setMargins((int)(4 * density), 0, 0, 0);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(new View.OnClickListener() {{
                @Override
                public void onClick(View v) {{
                    setSplitViewMode(modeIdx);
                }}
            }});
            mSplitViewButtons[i] = btn;
            tabRow.addView(btn);
        }}
        splitConsoleCard.addView(tabRow);
        root.addView(splitConsoleCard);

        // Realtime Status Dashboard Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#161B22"));
        cardBg.setCornerRadius(10 * density);
        cardBg.setStroke((int)(1 * density), Color.parseColor("#30363D"));
        card.setBackground(cardBg);
        card.setPadding((int)(16 * density), (int)(14 * density), (int)(16 * density), (int)(14 * density));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, (int)(14 * density));
        card.setLayoutParams(cardLp);

        mStatusMode = new TextView(this);
        mStatusMode.setText("현재 모드: " + {service_cls}.sModeTitle);
        mStatusMode.setTextSize(14);
        mStatusMode.setTextColor(Color.parseColor("#00F0FF"));
        mStatusMode.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(mStatusMode);

        mStatusFreq = new TextView(this);
        mStatusFreq.setText("실시간 발진: 55.00 Hz 고정 (0.00 dBFS True-Peak)");
        mStatusFreq.setTextSize(13);
        mStatusFreq.setTextColor(Color.parseColor("#FFFF00"));
        mStatusFreq.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusFreq.setPadding(0, (int)(4 * density), 0, (int)(2 * density));
        card.addView(mStatusFreq);

        mStatusMod = new TextView(this);
        mStatusMod.setText("변조 상태: " + {service_cls}.buildModulationSummary());
        mStatusMod.setTextSize(12);
        mStatusMod.setTextColor(Color.parseColor("#FF79C6"));
        mStatusMod.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusMod.setPadding(0, 0, 0, (int)(2 * density));
        card.addView(mStatusMod);

        mStatusDsp = new TextView(this);
        mStatusDsp.setText("Vorta DSP: " + {service_cls}.buildVortaDspSummary());
        mStatusDsp.setTextSize(12);
        mStatusDsp.setTextColor(Color.parseColor("#38D39F"));
        mStatusDsp.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusDsp.setPadding(0, 0, 0, (int)(2 * density));
        card.addView(mStatusDsp);

        mStatusSignal = new TextView(this);
        mStatusSignal.setText("검수: 48,000Hz Native Direct PCM 가동 중 | 화면 꺼짐 절전 지원");
        mStatusSignal.setTextSize(11);
        mStatusSignal.setTextColor(Color.parseColor("#8B949E"));
        mStatusSignal.setPadding(0, 0, 0, (int)(2 * density));
        card.addView(mStatusSignal);

        mStatusKnob = new TextView(this);
        mStatusKnob.setText("앰프 가이드: " + {service_cls}.sKnobGuide);
        mStatusKnob.setTextSize(11);
        mStatusKnob.setTextColor(Color.parseColor("#F1E05A"));
        card.addView(mStatusKnob);

        root.addView(card);

        // =========================================================================
        // 🎯 1. 메인 프레임: 보고서 1 [주파수 통합 운용 체계 마스터 보고서 (최신맞춤판)]
        // =========================================================================
        mFrameMainContainer = createFrameContainer(
                "🎯 [메인 프레임] 주파수 통합 운용 체계 마스터 보고서 (최신맞춤판)",
                "보고서 1: 24시간 4구간 표준 스케줄 & 5대 핵심 맞춤 프리셋 & 8대 전술 상황 매뉴얼",
                "#00E5FF",
                "#0D1926"
        );

        // 파트 1: 24시간 4구간 표준 맞춤 스케줄
        addSectionHeaderInsideFrame(mFrameMainContainer, "1️⃣ [파트 1] 24시간 4구간 표준 맞춤 스케줄 (최신맞춤판)", "#00E5FF");

        LinearLayout mRow1 = new LinearLayout(this);
        mRow1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow1,
                "🌅 오전 (07~12시) [AM+FM]",
                "4.0~7.5Hz (440s) [AM+FM]",
                "기상 직후 내장 뒤틀림·어지럼증 유발",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 4.0f, 7.5f, 440.0f,
                                true, 0.10f, 5.0f, true, 0.80f, 1.5f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[최신맞춤판 오전 07~12시] 4.0~7.5Hz 스윕 (AM 80% + FM 10%)",
                                "앰프 가이드: VOL 10~12시(왜곡 0%) | FREQ 80Hz | BASS +2dB");
                    }}
                }});

        addHalfPresetButton(mRow1,
                "☀️ 오후 (12~18:30시) [RM+FM]",
                "18.6~25.1Hz (900s) [RM+FM]",
                "브레인 포그·무기력증·집중력 마비",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                true, 0.12f, 2.5f, false, 0.0f, 1.0f, true, 0.45f, 1.8f,
                                3.0f, 0, 0, false,
                                "[최신맞춤판 오후 12~18:30시] 18.6~25.1Hz 스윕 (RM 45% + FM 12%)",
                                "앰프 가이드: VOL 10~12시(왜곡 0%) | FREQ 70Hz | BASS 0dB");
                    }}
                }});
        mFrameMainContainer.addView(mRow1);

        LinearLayout mRow2 = new LinearLayout(this);
        mRow2.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow2,
                "🌆 저녁 (18:30~23시) [FM 단독]",
                "55.0Hz 단독 [FM 10%]",
                "귀가 직후 흉곽 압박·거실 휴식 분쇄",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                true, 0.10f, 3.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최신맞춤판 저녁 18:30~23시] 55.0Hz 단독 톤 (FM 10% 지터)",
                                "앰프 가이드: VOL 10~12시(왜곡 0%) | FREQ 85Hz | BASS +3dB");
                    }}
                }});

        addHalfPresetButton(mRow2,
                "🌙 취침 (23~07시) [순수 사인파]",
                "55.0Hz 단독 앵커 [Pure]",
                "311호 정상파 장악·211호 0dB 무음",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[최신맞춤판 취침 23~07시] 55.0Hz 단독 앵커 (순수 무변조 사인파)",
                                "앰프 가이드: VOL 10~12시(왜곡 0%) | FREQ 60Hz | BASS 0dB");
                    }}
                }});
        mFrameMainContainer.addView(mRow2);

        // 파트 2: 5대 핵심 맞춤 프리셋 조합
        addSectionHeaderInsideFrame(mFrameMainContainer, "2️⃣ [파트 2] 5대 핵심 맞춤 프리셋 조합 (최신맞춤판)", "#58A6FF");

        LinearLayout mRow3 = new LinearLayout(this);
        mRow3.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow3,
                "모드 1: 하이브리드 표준형 [FM]",
                "52.0~55.0Hz (180s) [FM]",
                "내성 박멸과 파괴력 황금 밸런스",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 52.0f, 55.0f, 180.0f,
                                true, 0.10f, 1.5f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[최신맞춤판 모드 1] 52.0~55.0Hz 스윕 (FM 비브라토 10%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 80Hz | BASS +2dB");
                    }}
                }});

        addHalfPresetButton(mRow3,
                "모드 2: 트리플 단독 집중형 [Pure]",
                "55.0Hz 단독 올인 [Pure]",
                "단일 공진점 100% 전력 올인 결전용",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최신맞춤판 모드 2] 55.0Hz 단독 집중 파괴형 (순수 사인파)",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz | BASS +3dB");
                    }}
                }});
        mFrameMainContainer.addView(mRow3);

        LinearLayout mRow4 = new LinearLayout(this);
        mRow4.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow4,
                "모드 3: 광대역 스윕형 [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "무인 외출용 무한 카오스 비트",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.40f, 1.8f,
                                3.0f, 0, 0, false,
                                "[최신맞춤판 모드 3] 18.6~25.1Hz 광대역 스윕 (RM 40%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz | BASS 0dB");
                    }}
                }});

        addHalfPresetButton(mRow4,
                "모드 4: 침실 룸모드 제압형 [Pure]",
                "19.5Hz 단독 안구 공진 [Pure]",
                "수면 뇌파 분쇄 및 안구 공진 제압",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 19.5f, 19.5f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[최신맞춤판 모드 4] 19.5Hz 안구 공진 제압형 (순수 사인파)",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz | BASS 0dB");
                    }}
                }});
        mFrameMainContainer.addView(mRow4);

        addFullPresetButton(mFrameMainContainer,
                "모드 5: 즉각 응징 해머 펄스형 [AM 85%]",
                "55.0Hz 단독 펄스 (AM 85% 2.0Hz 해머) - 도발 시 1~2초 내 즉시 중단 타격",
                "#F1E05A",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, true, 0.85f, 2.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최신맞춤판 모드 5] 55.0Hz 해머 펄스형 (AM 85%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz | BASS +3dB");
                    }}
                }});

        // 파트 3: 8대 전술 상황별 매뉴얼 & 무인 스케줄
        addSectionHeaderInsideFrame(mFrameMainContainer, "3️⃣ [파트 3] 8대 전술 상황별 매뉴얼 & 무인 스케줄 (최신맞춤판)", "#38D39F");

        LinearLayout mRow5 = new LinearLayout(this);
        mRow5.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow5,
                "상황 1: 공격 즉각 요격 [AM]",
                "55.0Hz 단독 펄스 (AM 85%)",
                "상대 보복 시 2초 내 보복 분쇄",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, true, 0.85f, 2.5f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[상황 1: 즉각 요격] 55.0Hz AM 펄스",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        addHalfPresetButton(mRow5,
                "상황 2: 출근 10h 무인 자동 [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "출근 시 1회 터치 후 10h 무인 가동",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.40f, 1.5f,
                                3.0f, 0, 0, false,
                                "[상황 2: 출근 10h 무인] 18.6~25.1Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 70Hz");
                    }}
                }});
        mFrameMainContainer.addView(mRow5);

        LinearLayout mRow6 = new LinearLayout(this);
        mRow6.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow6,
                "상황 3: 귀가자 즉각 기습 [Pure]",
                "55.0Hz 단독 (10분 결전) [Pure]",
                "퇴근 직후 10분 기습 흉곽 압박",
                "#F1E05A",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[상황 3: 귀가자 기습] 55.0Hz 단독",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        addHalfPresetButton(mRow6,
                "상황 4: 2일 이상 장기 외출 [FM]",
                "3.3~7.3Hz (640s) [FM]",
                "이틀 이상 부재 시 무인 요새화",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 3.3f, 7.3f, 640.0f,
                                true, 0.15f, 1.2f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[상황 4: 장기 외출] 3.3~7.3Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 65Hz");
                    }}
                }});
        mFrameMainContainer.addView(mRow6);

        LinearLayout mRow7 = new LinearLayout(this);
        mRow7.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow7,
                "상황 5: 상대 내성 파괴 [FM+AM]",
                "4.0~12.0Hz (360s) [FM+AM]",
                "불규칙 진동으로 신경 피로 극대화",
                "#FF79C6",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 4.0f, 12.0f, 360.0f,
                                true, 0.12f, 2.0f, true, 0.50f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[상황 5: 내성 파괴] 4.0~12.0Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});

        addHalfPresetButton(mRow7,
                "상황 6: 3시간 연속 기기 보호 [Pure]",
                "22.0Hz 단독 연속 [Pure]",
                "코일 발열 억제형 장시간 방어",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 22.0f, 22.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[상황 6: 기기 보호] 22.0Hz 단독",
                                "앰프 가이드: VOL 10~12시 | FREQ 70Hz");
                    }}
                }});
        mFrameMainContainer.addView(mRow7);

        LinearLayout mRow8 = new LinearLayout(this);
        mRow8.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(mRow8,
                "상황 7: 야간 숙면 보장 [Pure]",
                "55.0Hz 단독 앵커 [Pure]",
                "211호 무음 & 311호 수면 박탈",
                "#00F0FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[상황 7: 야간 숙면] 55.0Hz 단독 앵커",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});

        addHalfPresetButton(mRow8,
                "상황 8: 긴급 침실 스텔스 [Pure]",
                "19.0Hz 단독 무음 [Pure]",
                "완벽한 스텔스 상태로 안구 공진",
                "#A371F7",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 19.0f, 19.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[상황 8: 침실 스텔스] 19.0Hz 단독",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameMainContainer.addView(mRow8);

        root.addView(mFrameMainContainer);

        // Divider 1
        mSplitDivider1 = createSplitDivider("════════════════════════════════════════════════════════");
        root.addView(mSplitDivider1);

        // =========================================================================
        // ⚡ 2. 서브1 프레임: 보고서 2 [주파수 통합 운용 체계 마스터 보고서 (최종완결판)]
        // =========================================================================
        mFrameSub1Container = createFrameContainer(
                "⚡ [서브1 프레임] 주파수 통합 운용 체계 마스터 보고서 (최종완결판)",
                "보고서 2: 실사 정제 4구간 스케줄 & 5대 하이브리드 조합 & 상황별 1타 전술",
                "#D2A8FF",
                "#191224"
        );

        // 파트 1: 4구간 시간대별 정통 스케줄
        addSectionHeaderInsideFrame(mFrameSub1Container, "1️⃣ [파트 1] 최종완결판 4구간 시간대별 정통 스케줄", "#D2A8FF");

        LinearLayout s1Row1 = new LinearLayout(this);
        s1Row1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row1,
                "오전 구간 (07~12시) [FM]",
                "52.0~55.0Hz (180s) [FM]",
                "기상 직후 전정기관 자극·구역감 고착",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 52.0f, 55.0f, 180.0f,
                                true, 0.12f, 1.5f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[최종완결판 오전 07~12시] 52.0~55.0Hz 스윕 (FM 12%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 80Hz");
                    }}
                }});

        addHalfPresetButton(s1Row1,
                "오후 구간 (12~18:30시) [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "무인 외출 6시간 비조화 카오스 누적",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.45f, 1.8f,
                                3.0f, 0, 0, false,
                                "[최종완결판 오후 12~18:30시] 18.6~25.1Hz 스윕 (RM 45%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row1);

        LinearLayout s1Row2 = new LinearLayout(this);
        s1Row2.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row2,
                "저녁 구간 (18:30~23시) [Pure]",
                "55.0Hz 단독 기습 [Pure]",
                "귀가 직후 흉곽 압박·대화 분쇄",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최종완결판 저녁 18:30~23시] 55.0Hz 단독 기습",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        addHalfPresetButton(s1Row2,
                "심야 구간 (23~07시) [Pure]",
                "55.0Hz 단독 앵커 [Pure]",
                "311호 정상파 장악 역제압·수면 박탈",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[최종완결판 심야 23~07시] 55.0Hz 단독 앵커",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row2);

        // 파트 2: 5대 핵심 하이브리드 조합
        addSectionHeaderInsideFrame(mFrameSub1Container, "2️⃣ [파트 2] 최종완결판 5대 핵심 하이브리드 조합", "#FF79C6");

        LinearLayout s1Row3 = new LinearLayout(this);
        s1Row3.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row3,
                "조합 1: 스텔스 하이브리드 [FM]",
                "52.0~55.0Hz (180s) [FM]",
                "일상 소음에 묻히는 스텔스 흉부 압박",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 52.0f, 55.0f, 180.0f,
                                true, 0.10f, 1.5f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[최종완결판 조합 1] 52.0~55.0Hz 스텔스 하이브리드",
                                "앰프 가이드: VOL 10~12시 | FREQ 80Hz");
                    }}
                }});

        addHalfPresetButton(s1Row3,
                "조합 2: 428W 동위상 집중 [Pure]",
                "55.0Hz 단독 풀파워 [Pure]",
                "전력 올인 단일 공진점 수직 타격",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최종완결판 조합 2] 55.0Hz 동위상 집중 타격",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row3);

        LinearLayout s1Row4 = new LinearLayout(this);
        s1Row4.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row4,
                "조합 3: 무인 카오스 스윕 [FM]",
                "3.3~7.3Hz (640s) [FM]",
                "무한 회전 공간 맥놀이 비트 형성",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 3.3f, 7.3f, 640.0f,
                                true, 0.15f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[최종완결판 조합 3] 3.3~7.3Hz 무인 카오스 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 65Hz");
                    }}
                }});

        addHalfPresetButton(s1Row4,
                "조합 4: 야간 55Hz 역제압 [Pure]",
                "55.0Hz 단독 앵커 [Pure]",
                "상대방 55Hz 정상파 흡수 및 안방 장악",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[최종완결판 조합 4] 55.0Hz 야간 정상파 역제압",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row4);

        addFullPresetButton(mFrameSub1Container,
                "조합 5: 즉각 응징 해머 펄스 [AM 80%]",
                "55.0Hz 단독 펄스 (AM 80% 2.0Hz) - 도발 감지 즉시 슬래브 수직 강타",
                "#F1E05A",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, true, 0.80f, 2.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[최종완결판 조합 5] 55.0Hz 해머 펄스 (AM 80%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        // 파트 3: 현장 실사 상황별 1타 맞춤 전술
        addSectionHeaderInsideFrame(mFrameSub1Container, "3️⃣ [파트 3] 현장 실사 상황별 1타 맞춤 전술", "#F1E05A");

        LinearLayout s1Row5 = new LinearLayout(this);
        s1Row5.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row5,
                "전술 1: 보복형 즉각 응징 [AM]",
                "55.0Hz 단독 펄스 (AM 80%)",
                "상대 보복 충격에 즉각 맞대응 해머",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, true, 0.80f, 2.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[전술 1: 보복형 응징] 55.0Hz AM 펄스",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        addHalfPresetButton(s1Row5,
                "전술 2: 내성 파괴형 타격 [RM+FM]",
                "12.0~24.5Hz (480s) [RM+FM]",
                "전정기관 교란 및 생체 내성 원천 차단",
                "#FF79C6",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 12.0f, 24.5f, 480.0f,
                                true, 0.12f, 2.0f, false, 0.0f, 1.0f, true, 0.40f, 1.5f,
                                3.0f, 0, 0, false,
                                "[전술 2: 내성 파괴] 12.0~24.5Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row5);

        LinearLayout s1Row6 = new LinearLayout(this);
        s1Row6.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s1Row6,
                "전술 3: 장시간 외출 무인 [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "출근·외출 시 6시간 무인 자동 가동",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.45f, 1.8f,
                                3.0f, 0, 0, false,
                                "[전술 3: 장시간 외출] 18.6~25.1Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});

        addHalfPresetButton(s1Row6,
                "전술 4: 야간 수면 박탈 [Pure]",
                "19.5Hz 단독 안구 공진 [Pure]",
                "미세 안구 진동으로 렘수면 영구 박탈",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 19.5f, 19.5f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[전술 4: 수면 박탈] 19.5Hz 단독 안구 공진",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameSub1Container.addView(s1Row6);

        root.addView(mFrameSub1Container);

        // Divider 2
        mSplitDivider2 = createSplitDivider("════════════════════════════════════════════════════════");
        root.addView(mSplitDivider2);

        // =========================================================================
        // 🛡️ 3. 서브2 프레임: 보고서 3 [주파수 통합 운용 체계 마스터 보고서 (ver.2)]
        // =========================================================================
        mFrameSub2Container = createFrameContainer(
                "🛡️ [서브2 프레임] 주파수 통합 운용 체계 마스터 보고서 (ver.2)",
                "보고서 3: 스케줄 A(버전1 스타일) · 스케줄 B(24h 정밀형) · 스케줄 C(라이프사이클 무인)",
                "#38D39F",
                "#0F241A"
        );

        // 파트 1: 스케줄 A: 기존 버전 1 스타일 운용 스케줄
        addSectionHeaderInsideFrame(mFrameSub2Container, "1️⃣ [파트 1] 스케줄 A: 기존 버전 1 스타일 운용 스케줄 (ver.2)", "#38D39F");

        LinearLayout s2Row1 = new LinearLayout(this);
        s2Row1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s2Row1,
                "아침 타격 (07~12시) [FM]",
                "18.6~25.1Hz (900s) [FM]",
                "버전 1 정통 아침 전정기관 타격",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                true, 0.10f, 1.5f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[스케줄 A 아침 07~12시] 18.6~25.1Hz 스윕 (FM 10%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});

        addHalfPresetButton(s2Row1,
                "낮 순환 (12~18시) [RM]",
                "30.0~45.0Hz (600s) [RM]",
                "버전 1 중저역 순환 확장 비트",
                "#D2A8FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 30.0f, 45.0f, 600.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.40f, 1.5f,
                                3.0f, 0, 0, false,
                                "[스케줄 A 낮 12~18시] 30.0~45.0Hz 스윕 (RM 40%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 80Hz");
                    }}
                }});
        mFrameSub2Container.addView(s2Row1);

        LinearLayout s2Row2 = new LinearLayout(this);
        s2Row2.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s2Row2,
                "저녁 결전 (18~23시) [Pure]",
                "55.0Hz 단독 결전 [Pure]",
                "버전 1 정통 저녁 슬래브 집중 타격",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[스케줄 A 저녁 18~23시] 55.0Hz 단독 결전",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});

        addHalfPresetButton(s2Row2,
                "심야 취침 (23~07시) [Pure]",
                "19.5Hz 단독 앵커 [Pure]",
                "버전 1 정통 야간 안구 공진 제압",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 19.5f, 19.5f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[스케줄 A 심야 23~07시] 19.5Hz 단독 앵커",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameSub2Container.addView(s2Row2);

        // 파트 2: 스케줄 B: 원본 24시간 실전 마스터 운용 스케줄
        addSectionHeaderInsideFrame(mFrameSub2Container, "2️⃣ [파트 2] 스케줄 B: 원본 24시간 실전 마스터 운용 스케줄 (ver.2)", "#58A6FF");

        LinearLayout s2Row3 = new LinearLayout(this);
        s2Row3.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s2Row3,
                "외출 10h (08:30~18:30) [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "부재 중 무내성 융단 타격 순환",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.45f, 1.5f,
                                3.0f, 0, 0, false,
                                "[스케줄 B 외출 10h] 18.6~25.1Hz 스윕 (RM 45%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});

        addHalfPresetButton(s2Row3,
                "기습 10m (18:30~19:00) [Pure]",
                "55.0Hz 단독 톤 [Pure]",
                "귀가 직후 10분 기습 흉곽 압박",
                "#F1E05A",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[스케줄 B 기습 10m] 55.0Hz 단독 기습",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});
        mFrameSub2Container.addView(s2Row3);

        LinearLayout s2Row4 = new LinearLayout(this);
        s2Row4.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s2Row4,
                "응징 4h (19:00~23:00) [FM]",
                "52.0~55.0Hz (180s) [FM]",
                "저녁 활동 시간대 흉곽 맥놀이 인가",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 52.0f, 55.0f, 180.0f,
                                true, 0.10f, 1.5f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                3.0f, 0, 0, false,
                                "[스케줄 B 응징 4h] 52.0~55.0Hz 스윕 (FM 10%)",
                                "앰프 가이드: VOL 10~12시 | FREQ 80Hz");
                    }}
                }});

        addHalfPresetButton(s2Row4,
                "수면 9.5h (23:00~08:30) [Pure]",
                "55.0Hz 단독 앵커 [Pure]",
                "211호 0dB 무음 & 311호 정상파 장악",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[스케줄 B 수면 9.5h] 55.0Hz 단독 앵커",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});
        mFrameSub2Container.addView(s2Row4);

        // 파트 3: 스케줄 C: 사용자 수면 & 출근 100% 무인 자율 스케줄
        addSectionHeaderInsideFrame(mFrameSub2Container, "3️⃣ [파트 3] 스케줄 C: 사용자 수면 & 출근 100% 무인 자율 스케줄 (ver.2)", "#FF79C6");

        LinearLayout s2Row5 = new LinearLayout(this);
        s2Row5.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(s2Row5,
                "블록 1: 출근 10h 무인 [RM]",
                "18.6~25.1Hz (900s) [RM]",
                "출근 시 1회 터치 10시간 자동 순환",
                "#58A6FF",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(true, 0f, 0f, 18.6f, 25.1f, 900.0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.40f, 1.5f,
                                3.0f, 0, 0, false,
                                "[스케줄 C 블록 1: 출근 10h] 18.6~25.1Hz 스윕",
                                "앰프 가이드: VOL 10~12시 | FREQ 75Hz");
                    }}
                }});

        addHalfPresetButton(s2Row5,
                "블록 2: 저녁 4.5h 집중 [Pure]",
                "55.0Hz 단독 결전 [Pure]",
                "퇴근 후 보일러실 문 닫고 집중 응징",
                "#FF7B72",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                6.0f, 0, 0, true,
                                "[스케줄 C 블록 2: 저녁 4.5h] 55.0Hz 단독",
                                "앰프 가이드: VOL 10~12시 | FREQ 85Hz");
                    }}
                }});
        mFrameSub2Container.addView(s2Row5);

        addFullPresetButton(mFrameSub2Container,
                "블록 3: 취침 및 수면 9.5시간 쾌적 숙면 보장 [Pure 55Hz]",
                "55.0Hz 단독 앵커 - 211호 0dB 완전 무음 8시간 이상 숙면! 311호는 침대 골도 진동으로 렘수면 박탈",
                "#38D39F",
                new View.OnClickListener() {{
                    @Override
                    public void onClick(View v) {{
                        startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                                false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                                0.0f, 0, 0, false,
                                "[스케줄 C 블록 3: 취침 9.5h] 55.0Hz 단독 앵커",
                                "앰프 가이드: VOL 10~12시 | FREQ 60Hz");
                    }}
                }});

        root.addView(mFrameSub2Container);

        // Emergency Stop & Reset Bar
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setWeightSum(2.0f);
        actionRow.setPadding(0, (int)(16 * density), 0, (int)(16 * density));

        TextView stopBtn = new TextView(this);
        stopBtn.setText("⏹️ 즉시 발진 중지");
        stopBtn.setTextSize(14);
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        stopBtn.setGravity(Gravity.CENTER);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(Color.parseColor("#DA3633"));
        stopBg.setCornerRadius(8 * density);
        stopBtn.setBackground(stopBg);
        stopBtn.setPadding(0, (int)(12 * density), 0, (int)(12 * density));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        stopLp.setMargins(0, 0, (int)(6 * density), 0);
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setOnClickListener(new View.OnClickListener() {{
            @Override
            public void onClick(View v) {{
                Intent it = new Intent(MainActivity.this, {service_cls}.class);
                it.putExtra("action", "stop");
                startService(it);
                Toast.makeText(MainActivity.this, "주파수 발진이 즉시 중지되었습니다.", Toast.LENGTH_SHORT).show();
            }}
        }});
        actionRow.addView(stopBtn);

        TextView resetBtn = new TextView(this);
        resetBtn.setText("🔄 55Hz 기본 복귀");
        resetBtn.setTextSize(14);
        resetBtn.setTextColor(Color.parseColor("#C9D1D9"));
        resetBtn.setTypeface(Typeface.DEFAULT_BOLD);
        resetBtn.setGravity(Gravity.CENTER);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(Color.parseColor("#21262D"));
        resetBg.setCornerRadius(8 * density);
        resetBg.setStroke((int)(1 * density), Color.parseColor("#30363D"));
        resetBtn.setBackground(resetBg);
        resetBtn.setPadding(0, (int)(12 * density), 0, (int)(12 * density));
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        resetLp.setMargins((int)(6 * density), 0, 0, 0);
        resetBtn.setLayoutParams(resetLp);
        resetBtn.setOnClickListener(new View.OnClickListener() {{
            @Override
            public void onClick(View v) {{
                startPresetWithDsp(false, 55.0f, 55.0f, 0f, 0f, 0f,
                        false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f,
                        0.0f, 0, 0, false,
                        "[디폴트 앵커] 55.0Hz 단독 톤 (0.00 dBFS True-Peak)",
                        "{amp_guide_default}");
            }}
        }});
        actionRow.addView(resetBtn);
        root.addView(actionRow);

        scroll.addView(root);
        setContentView(scroll);

        // Apply initial split view mode (default: 0 - 전체)
        setSplitViewMode(0);
    }}

    private void setSplitViewMode(int mode) {{
        mCurrentSplitMode = mode;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 4; i++) {{
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(6 * density);
            if (i == mode) {{
                btnBg.setColor(Color.parseColor("#1F6FEB"));
                btnBg.setStroke((int)(1.5f * density), Color.parseColor("#58A6FF"));
                mSplitViewButtons[i].setTextColor(Color.WHITE);
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT_BOLD);
            }} else {{
                btnBg.setColor(Color.parseColor("#21262D"));
                btnBg.setStroke((int)(1 * density), Color.parseColor("#30363D"));
                mSplitViewButtons[i].setTextColor(Color.parseColor("#8B949E"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT);
            }}
            mSplitViewButtons[i].setBackground(btnBg);
        }}

        if (mode == 0) {{
            // 전체 3대 보고서 동시 표시
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.VISIBLE);
            mSplitDivider2.setVisibility(View.VISIBLE);
        }} else if (mode == 1) {{
            // 보고서 1 (최신맞춤판) 전용 표시
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mFrameSub1Container.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
        }} else if (mode == 2) {{
            // 보고서 2 (최종완결판) 전용 표시
            mFrameMainContainer.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mFrameSub2Container.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
        }} else if (mode == 3) {{
            // 보고서 3 (ver.2) 전용 표시
            mFrameMainContainer.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
        }}
    }}

    private LinearLayout createFrameContainer(String title, String subtitle, String accentColor, String bgColor) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgColor));
        bg.setCornerRadius(12 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(accentColor));
        container.setBackground(bg);
        container.setPadding((int)(14 * density), (int)(14 * density), (int)(14 * density), (int)(14 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, (int)(8 * density));
        container.setLayoutParams(lp);

        TextView tTv = new TextView(this);
        tTv.setText(title);
        tTv.setTextSize(16);
        tTv.setTextColor(Color.parseColor(accentColor));
        tTv.setTypeface(Typeface.DEFAULT_BOLD);
        tTv.setPadding(0, 0, 0, (int)(3 * density));
        container.addView(tTv);

        TextView sTv = new TextView(this);
        sTv.setText(subtitle);
        sTv.setTextSize(12);
        sTv.setTextColor(Color.parseColor("#C9D1D9"));
        sTv.setPadding(0, 0, 0, (int)(10 * density));
        container.addView(sTv);

        return container;
    }}

    private LinearLayout createSplitDivider(String text) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout div = new LinearLayout(this);
        div.setOrientation(LinearLayout.VERTICAL);
        div.setPadding(0, (int)(10 * density), 0, (int)(10 * density));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.parseColor("#30363D"));
        tv.setGravity(Gravity.CENTER);
        div.addView(tv);
        return div;
    }}

    private void addSectionHeaderInsideFrame(LinearLayout frame, String title, String colorHex) {{
        float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int)(12 * density), 0, (int)(8 * density));
        frame.addView(tv);
    }}

    private void addHalfPresetButton(LinearLayout row, String title, String freq, String desc, String colorHex, View.OnClickListener listener) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setPadding((int)(10 * density), (int)(10 * density), (int)(10 * density), (int)(10 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161B22"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1 * density), Color.parseColor(colorHex));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.setMargins((int)(3 * density), (int)(3 * density), (int)(3 * density), (int)(3 * density));
        btn.setLayoutParams(lp);

        TextView tTv = new TextView(this);
        tTv.setText(title);
        tTv.setTextSize(12);
        tTv.setTextColor(Color.parseColor(colorHex));
        tTv.setTypeface(Typeface.DEFAULT_BOLD);
        btn.addView(tTv);

        TextView fTv = new TextView(this);
        fTv.setText(freq);
        fTv.setTextSize(11);
        fTv.setTextColor(Color.parseColor("#E6EDF3"));
        fTv.setPadding(0, (int)(2 * density), 0, (int)(2 * density));
        btn.addView(fTv);

        TextView dTv = new TextView(this);
        dTv.setText(desc);
        dTv.setTextSize(10);
        dTv.setTextColor(Color.parseColor("#8B949E"));
        btn.addView(dTv);

        btn.setOnClickListener(listener);
        row.addView(btn);
    }}

    private void addFullPresetButton(LinearLayout frame, String title, String desc, String colorHex, View.OnClickListener listener) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setPadding((int)(12 * density), (int)(10 * density), (int)(12 * density), (int)(10 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161B22"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1 * density), Color.parseColor(colorHex));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int)(3 * density), (int)(4 * density), (int)(3 * density), (int)(4 * density));
        btn.setLayoutParams(lp);

        TextView tTv = new TextView(this);
        tTv.setText(title);
        tTv.setTextSize(13);
        tTv.setTextColor(Color.parseColor(colorHex));
        tTv.setTypeface(Typeface.DEFAULT_BOLD);
        btn.addView(tTv);

        TextView dTv = new TextView(this);
        dTv.setText(desc);
        dTv.setTextSize(11);
        dTv.setTextColor(Color.parseColor("#C9D1D9"));
        dTv.setPadding(0, (int)(2 * density), 0, 0);
        btn.addView(dTv);

        btn.setOnClickListener(listener);
        frame.addView(btn);
    }}

    private void startPresetWithDsp(boolean isDualSweep, float fixedLeft, float fixedRight,
                                    float sweepStart, float sweepEnd, float sweepDurSec,
                                    boolean fmEnable, float fmDepth, float fmFreq,
                                    boolean amEnable, float amDepth, float amFreq,
                                    boolean rmEnable, float rmDepth, float rmFreq,
                                    float boostDb, int channelMode, int limiterMode, boolean exciter,
                                    String title, String guide) {{
        Intent it = new Intent(this, {service_cls}.class);
        it.putExtra("action", "start");
        it.putExtra("sweep", isDualSweep);
        if (isDualSweep) {{
            it.putExtra("sweep_min", sweepStart);
            it.putExtra("sweep_max", sweepEnd);
            it.putExtra("sweep_sec", sweepDurSec);
        }} else {{
            it.putExtra("freq", fixedLeft);
        }}
        it.putExtra("fm_on", fmEnable);
        it.putExtra("fm_depth", fmDepth);
        it.putExtra("fm_rate", fmFreq);
        it.putExtra("am_on", amEnable);
        it.putExtra("am_depth", amDepth);
        it.putExtra("am_rate", amFreq);
        it.putExtra("rm_on", rmEnable);
        it.putExtra("rm_depth", rmDepth);
        it.putExtra("rm_rate", rmFreq);
        it.putExtra("bass_boost_db", boostDb);
        it.putExtra("channel_mode", channelMode);
        it.putExtra("limiter_mode", limiterMode);
        it.putExtra("harmonic_exciter", exciter);
        it.putExtra("title", title);
        it.putExtra("guide", guide);
        startService(it);
        Toast.makeText(this, "가동: " + title, Toast.LENGTH_SHORT).show();
        syncTelemetryUi();
    }}

    private void syncTelemetryUi() {{
        if (mStatusMode != null) {{
            mStatusMode.setText("현재 모드: " + {service_cls}.sModeTitle);
        }}
        if (mStatusFreq != null) {{
            if ({service_cls}.sIsPlaying) {{
                if ({service_cls}.sIsSweepMode) {{
                    mStatusFreq.setText(String.format("실시간 발진: %.2f Hz ~ %.2f Hz 스윕 (%.0fs 순환)",
                            {service_cls}.sSweepMin, {service_cls}.sSweepMax, {service_cls}.sSweepPeriodSec));
                }} else {{
                    mStatusFreq.setText(String.format("실시간 발진: %.2f Hz 고정 (0.00 dBFS True-Peak)",
                            {service_cls}.sTargetFreq));
                }}
            }} else {{
                mStatusFreq.setText("실시간 발진: 대기 중 (정지됨)");
            }}
        }}
        if (mStatusMod != null) {{
            mStatusMod.setText("변조 상태: " + {service_cls}.buildModulationSummary());
        }}
        if (mStatusDsp != null) {{
            mStatusDsp.setText("Vorta DSP: " + {service_cls}.buildVortaDspSummary());
        }}
        if (mStatusKnob != null) {{
            mStatusKnob.setText("앰프 가이드: " + {service_cls}.sKnobGuide);
        }}
    }}

    @Override
    protected void onResume() {{
        super.onResume();
        mUiHandler.post(mTelemetryUpdater);
    }}

    @Override
    protected void onPause() {{
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryUpdater);
    }}
}}
'''

def main():
    # 1. Update Galaxy S7 App
    s7_code = get_activity_code(
        pkg_name="com.vorta.s7frequency",
        service_cls="S7AudioGeneratorService",
        helper_cls="S7VolumeHelper",
        app_title="보르타 레조 S7 v2.8",
        app_subtitle="갤럭시 S7 + 120W 우퍼 2기 스테레오 (240W RMS) | 3대 마스터 보고서 1:1 탑재 | FM·AM·RM 변조",
        amp_guide_default="120W 우퍼 앰프: VOL 10~12시(왜곡 0%) | FREQ 80Hz | BASS +2dB"
    )
    with open("vorta_s7_app/src/com/vorta/s7frequency/MainActivity.java", "w", encoding="utf-8") as f:
        f.write(s7_code)
    print("Wrote vorta_s7_app MainActivity.java")

    # 2. Update Galaxy S6 App
    s6_code = get_activity_code(
        pkg_name="com.vorta.s6frequency",
        service_cls="S6AudioGeneratorService",
        helper_cls="S6VolumeHelper",
        app_title="보르타 레조 S6 v2.8",
        app_subtitle="갤럭시 S6 + 골전도 트랜스2 1기 (68W BTL) | 3대 마스터 보고서 1:1 탑재 | FM·AM·RM 변조",
        amp_guide_default="LP-168S 앰프: VOL 12시(왜곡 0%) | SUB 1시(55%) | X-OVER 85Hz"
    )
    with open("vorta_s6_app/src/com/vorta/s6frequency/MainActivity.java", "w", encoding="utf-8") as f:
        f.write(s6_code)
    print("Wrote vorta_s6_app MainActivity.java")

    # 3. Update Galaxy A30 App (smato_a30_app)
    a30_code = get_activity_code(
        pkg_name="com.smato.a30frequency",
        service_cls="A30AudioGeneratorService",
        helper_cls="A30VolumeHelper",
        app_title="보르타 레조 A30 v2.8",
        app_subtitle="갤럭시 A30 + 120W 우퍼 1기 (120W 모노) | 3대 마스터 보고서 1:1 탑재 | FM·AM·RM 변조",
        amp_guide_default="120W 우퍼 앰프: VOL 10~12시(왜곡 0%) | FREQ 80Hz | BASS 0dB"
    )
    with open("smato_a30_app/src/com/smato/a30frequency/MainActivity.java", "w", encoding="utf-8") as f:
        f.write(a30_code)
    print("Wrote smato_a30_app MainActivity.java")

if __name__ == "__main__":
    main()
