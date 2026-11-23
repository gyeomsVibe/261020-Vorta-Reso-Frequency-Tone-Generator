package com.vorta.s7frequency;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // 🎛️ Real-time Telemetry Dashboard Views
    private LinearLayout mStatusCardLayout;
    private ImageView mStatusTrafficLight;
    private TextView mStatusMode;
    private TextView mStatusScenario;
    private TextView mStatusFreq;
    private TextView mStatusMod;
    private TextView mStatusDsp;
    private TextView mStatusKnob;

    // 🎛️ Split View Containers (3 Frames)
    private LinearLayout mFrameMainContainer;
    private LinearLayout mFrameSub1Container;
    private LinearLayout mFrameSub2Container;
    private LinearLayout mFrameLegacyContainer;
    private LinearLayout mFrameBasicContainer;
    private LinearLayout mSplitDivider1;
    private LinearLayout mSplitDivider2;
    private LinearLayout mSplitDivider3;
    private LinearLayout mSplitDivider4;
    private TextView[] mSplitViewButtons = new TextView[5];
    private int mCurrentSplitMode = 0; // 0: 전체, 1: 메인, 2: 서브1, 3: 서브2

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryUpdater = new Runnable() {
        @Override
        public void run() {
            syncTelemetryUi();
            if (S7AudioGeneratorService.sIsPlaying) {
                mUiHandler.postDelayed(this, 600);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            }
        }

        // Lockscreen bypass for easy access on dedicated generator devices only
        if (!S7VolumeHelper.isPersonalDevice()) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        S7AudioGeneratorService.loadConfig(this);
        S7VolumeHelper.syncTargetFromSystem(this);
        S7VolumeHelper.ensureTargetVolumeIfNeeded(this, false);

        float density = getResources().getDisplayMetrics().density;

        // Unified full-screen scroll view (Zero 1/3 viewport split!)
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0A0E17"));
        scroll.setFillViewport(true);

        LinearLayout contentPane = new LinearLayout(this);
        contentPane.setOrientation(LinearLayout.VERTICAL);
        contentPane.setPadding((int)(14 * density), (int)(16 * density), (int)(14 * density), (int)(24 * density));

        // -------------------------------------------------------------------------
        // 1. App Title & Subtitle (Title Large 22sp / Body Small 12sp)
        // -------------------------------------------------------------------------
        TextView titleTv = new TextView(this);
        titleTv.setText("보르타 레조 S7 v4.2");
        titleTv.setTextSize(22);
        titleTv.setTextColor(Color.parseColor("#00F0FF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        contentPane.addView(titleTv);

        TextView subTitleTv = new TextView(this);
        subTitleTv.setText("갤럭시 S7 + 240W 스테레오 앰프(스마토 #2) 맞춤 기저 앵커 주파수 발생기 | Vorta DSP v4.2 (-1.0dBTP·안전볼륨 영구해제)");
        subTitleTv.setTextSize(12);
        subTitleTv.setTextColor(Color.parseColor("#94A3B8"));
        subTitleTv.setPadding(0, (int)(2 * density), 0, (int)(12 * density));
        contentPane.addView(subTitleTv);

        // -------------------------------------------------------------------------
        // 2. Real-time Telemetry Dashboard Card
        // -------------------------------------------------------------------------
        mStatusCardLayout = new LinearLayout(this);
        mStatusCardLayout.setOrientation(LinearLayout.VERTICAL);
        mStatusCardLayout.setPadding((int)(14 * density), (int)(12 * density), (int)(14 * density), (int)(12 * density));

        GradientDrawable scBg = new GradientDrawable();
        scBg.setColor(Color.parseColor("#111827")); // Consistent AMOLED Deep Slate background
        scBg.setCornerRadius(8 * density);
        scBg.setStroke((int)(2 * density), Color.parseColor("#EF4444")); // Stopped by default
        mStatusCardLayout.setBackground(scBg);

        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scLp.setMargins(0, 0, 0, (int)(10 * density));
        mStatusCardLayout.setLayoutParams(scLp);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, 0, 0, (int)(4 * density));

        mStatusTrafficLight = new ImageView(this);
        int dotSize = (int)(16 * density);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.setMargins(0, 0, (int)(8 * density), 0);
        mStatusTrafficLight.setLayoutParams(dotLp);

        GradientDrawable redDot = new GradientDrawable();
        redDot.setShape(GradientDrawable.OVAL);
        redDot.setColor(Color.parseColor("#FF3366"));
        mStatusTrafficLight.setImageDrawable(redDot);
        statusRow.addView(mStatusTrafficLight);

        mStatusMode = new TextView(this);
        mStatusMode.setText("상태: [작동 중지] 멈춤 (대기 상태)");
        mStatusMode.setTextSize(14);
        mStatusMode.setTextColor(Color.parseColor("#FF3366"));
        mStatusMode.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(mStatusMode);

        mStatusCardLayout.addView(statusRow);

        mStatusScenario = new TextView(this);
        mStatusScenario.setText("시나리오: 대기 중 (선택 없음)");
        mStatusScenario.setTextSize(12);
        mStatusScenario.setTextColor(Color.parseColor("#00F0FF"));
        mStatusScenario.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusScenario.setPadding(0, (int)(2 * density), 0, (int)(2 * density));
        mStatusCardLayout.addView(mStatusScenario);

        mStatusFreq = new TextView(this);
        mStatusFreq.setText("발진: 0.00 Hz (출력 차단 / MUTE)");
        mStatusFreq.setTextSize(13);
        mStatusFreq.setTextColor(Color.parseColor("#94A3B8"));
        mStatusFreq.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusFreq.setPadding(0, (int)(3 * density), 0, (int)(3 * density));
        mStatusCardLayout.addView(mStatusFreq);

        mStatusMod = new TextView(this);
        mStatusMod.setText("변조: 비활성 (주파수 신호 멈춤) | 240W 스테레오");
        mStatusMod.setTextSize(11);
        mStatusMod.setTextColor(Color.parseColor("#64748B"));
        mStatusCardLayout.addView(mStatusMod);

        mStatusDsp = new TextView(this);
        mStatusDsp.setText(S7VolumeHelper.isPersonalDevice()
                ? "Vorta DSP v4.2: -1.0dBTP(Peak 29200) | 개인기기 보호 모드(음량·음장 무개입)"
                : "Vorta DSP v4.2: 자유볼륨 제어(0~15단) | -1.0dBTP(Peak 29200) | OS 안전볼륨 해제·AUX 가드 ON");
        mStatusDsp.setTextSize(10);
        mStatusDsp.setTextColor(Color.parseColor("#FFA657"));
        mStatusDsp.setPadding(0, (int)(2 * density), 0, (int)(1 * density));
        mStatusCardLayout.addView(mStatusDsp);

        mStatusKnob = new TextView(this);
        mStatusKnob.setText("앰프 노브: 스마토 앰프 #2 [단일 볼륨 다이얼 1개] 11시~1시 (60~75% 무왜곡 순수 파괴력 구동)");
        mStatusKnob.setTextSize(10);
        mStatusKnob.setTextColor(Color.parseColor("#CBD5E1"));
        mStatusKnob.setPadding(0, (int)(3 * density), 0, 0);
        mStatusCardLayout.addView(mStatusKnob);

        contentPane.addView(mStatusCardLayout);

        // -------------------------------------------------------------------------
        // 3. Emergency Quick Stop Button
        // -------------------------------------------------------------------------
        TextView stopBtn = new TextView(this);
        stopBtn.setText("🛑 주파수 즉각 정지 (MUTE)");
        stopBtn.setTextSize(14);
        stopBtn.setTextColor(Color.parseColor("#FFFFFF"));
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        stopBtn.setGravity(Gravity.CENTER);

        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(Color.parseColor("#DC2626")); // Vivid Red
        stopBg.setCornerRadius(6 * density);
        stopBtn.setBackground(stopBg);
        stopBtn.setPadding(0, (int)(10 * density), 0, (int)(10 * density));

        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopLp.setMargins(0, 0, 0, (int)(14 * density));
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, S7AudioGeneratorService.class));
                S7AudioGeneratorService.sIsPlaying = false;
                S7AudioGeneratorService.sModeTitle = "주파수 발진 정지됨";
                Toast.makeText(MainActivity.this, "주파수 발진 정지", Toast.LENGTH_SHORT).show();
                syncTelemetryUi();
            }
        });
        contentPane.addView(stopBtn);

        // -------------------------------------------------------------------------
        // 4. Split-View Switching Tab Bar: Exactly [전체 / 메인 / 서브1 / 서브2]
        // -------------------------------------------------------------------------
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(38 * density));
        tabLp.setMargins(0, 0, 0, (int)(14 * density));
        tabRow.setLayoutParams(tabLp);

        mSplitViewButtons[0] = createSplitTabButton(tabRow, "메인", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(0);
            }
        });
        mSplitViewButtons[1] = createSplitTabButton(tabRow, "서브1", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(1);
            }
        });
        mSplitViewButtons[2] = createSplitTabButton(tabRow, "서브2", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(2);
            }
        });
        mSplitViewButtons[3] = createSplitTabButton(tabRow, "공통", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(3);
            }
        });
        mSplitViewButtons[4] = createSplitTabButton(tabRow, "기초", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(4);
            }
        });
        contentPane.addView(tabRow);

        // -------------------------------------------------------------------------
        // 🎯 [메인 프레임] 통합운용 시나리오 버전1 (총 17개 프리셋)
        // -------------------------------------------------------------------------
        mFrameMainContainer = createFrameContainer(
                "🎯 [메인 프레임] 통합운용 시나리오 버전1 (최신 정본)",
                "24시간 4대 기저 앵커 + 9대 실전 맞춤 + 전술 특수 프리셋",
                "#00F0FF",
                "#082F49"
        );

        // [파트 1] 24시간 4대 시간대별 기저 앵커 스케줄
        addSectionHeaderInsideFrame(mFrameMainContainer, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 기저 앵커 스케줄", "#00F0FF");

        addFullPresetButton(mFrameMainContainer,
                "🌅 1. 아침 (07:00~13:00): 28.0 Hz 앵커 직타",
                "🎯 타격효과: 골반저근 펄스 해머 타격 (단독 28.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 3.20f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 스케줄-1] 아침 28.0Hz 앵커 (AM 15%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "☀️ 2. 오후 (13:00~19:00): 10.5 Hz 앵커 직타",
                "🎯 타격효과: 자율신경·심박 요동 앵커 (단독 10.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.84Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 스케줄-2] 오후 10.5Hz 앵커 (FM 8%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌆 3. 저녁 (19:00~01:00): 7.0 Hz 앵커 직타",
                "🎯 타격효과: 전정기관 평형 파괴 요동 (단독 7.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.56Hz, 속도 0.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 스케줄-3] 저녁 7.0Hz 앵커 (FM 8%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌙 4. 취침 (01:00~07:00): 21.0 ~ 24.5 Hz (1250초 스윕)",
                "🎯 타격효과: 바닥 미세 맥동 골도 진동 (스윕범위 21.0~24.5Hz, 주기 1250초, 분당 0.17Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 10%, 펄스 0.60Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.00f, 24.50f, 1250.0f,
                                false, 0.000f, 0.00f, true, 0.100f, 0.60f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 스케줄-4] 취침 21.0~24.5Hz 골도스윕 (AM 10%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 2] 사용자 행동별 9대 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameMainContainer, "2️⃣ [파트 2] 🎯 사용자 행동별 9대 실전 맞춤 시나리오", "#FACC15");

        addFullPresetButton(mFrameMainContainer,
                "⚡ 1. 상대 공격 즉각 요격: 7.0Hz + 10.5Hz 복합 앵커",
                "🎯 타격효과: 바닥 2점 충격파 즉각 가진 (주주파수 7.00Hz + 기저하모닉 10.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.56Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 10.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[메인 행동-1] 상대공격 즉각요격 7+10.5Hz 복합",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔨 2. 벌주기 (보복 타격): 28.0 Hz 골반 앵커 직타",
                "🎯 타격효과: 골반저 해머 압박 집중 응징 (단독 28.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 3.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 3.50f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[메인 행동-2] 벌주기 28.0Hz 골반앵커 직타 (AM 18%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏢 3. 출근 12시간 무인방어: 10.5Hz / 28.0Hz 교대 (600초)",
                "🎯 타격효과: 기저 피로 누적 및 발열 30% 저감 (주주파수 28.00Hz + 기저하모닉 10.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±2.24Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 10.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-3] 출근 12h 10.5/28Hz 교대",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🚶 4. 일상 외출 자동 순항: 10.5 Hz 지속 앵커",
                "🎯 타격효과: 2~4시간 외출 시 기저 심박 유지 (단독 10.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.63Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-4] 일상외출 10.5Hz 지속앵커 (FM 6%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏰 5. 초장기 외출 (48h+): 21.0 ~ 24.5 Hz 앵커",
                "🎯 타격효과: 45분 발진 / 15분 휴지 코일 보호 (스윕범위 21.0~24.5Hz, 주기 1500초, 분당 0.14Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±0.91Hz, 속도 0.45Hz) | AM ON(심도 10%, 펄스 0.60Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.00f, 24.50f, 1500.0f,
                                true, 0.040f, 0.45f, true, 0.100f, 0.60f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-5] 초장기 48h+ 21~24.5Hz 보호앵커",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💥 6. 상대 내성 파괴: 7.0Hz ↔ 10.5Hz 도약 (180초)",
                "🎯 타격효과: 비동기 변조 도약으로 상대 신경 적응 무효화 (주주파수 10.50Hz + 기저하모닉 7.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.05Hz, 속도 1.20Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 7.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 1.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[메인 행동-6] 내성파괴 7↔10.5Hz 도약 (FM 10%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🧠 7. 상대 정신 집중 차단: 7.0 Hz 전정계 흔들기",
                "🎯 타격효과: 전정계 평형 교란 및 인지 저하 (단독 7.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.63Hz, 속도 0.65Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.090f, 0.65f, true, 0.140f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 1, true, 15,
                                "[메인 행동-7] 정신집중차단 7.0Hz 전정계흔들기",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛌 8. 수면 시간 집중 압박: 21.0 ~ 24.5 Hz 앵커",
                "🎯 타격효과: 새벽 1~5시 침대 골도 진동 집중 타격 (스윕범위 21.0~24.5Hz, 주기 1100초, 분당 0.19Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 0.85Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.00f, 24.50f, 1100.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 0.85f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, false, 15,
                                "[메인 행동-8] 수면시간 21~24.5Hz 골도앵커",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛡️ 9. 3시간 이상 기기 보호: 28.0 Hz (50분/10분)",
                "🎯 타격효과: AM 15% 듀티 사이클 동기화로 45도 이하 유지 (단독 28.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 14%, 펄스 1.60Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.140f, 1.60f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-9] 3시간 기기보호 28Hz (50/10분)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 3] 고유 바닥 2점 기저/전술 특수 프리셋
        addSectionHeaderInsideFrame(mFrameMainContainer, "3️⃣ [파트 3] 📊 고유 바닥 2점 기저/전술 특수 프리셋", "#38BDF8");

        addFullPresetButton(mFrameMainContainer,
                "🌊 바닥 2점 기저 앵커 직타: 28.0 Hz 단독",
                "🎯 타격효과: 바닥 슬래브 2점 기저 앵커 집중 가진 (주주파수 28.00Hz + 기저하모닉 14.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 14.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 3.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 1, true, 15,
                                "[메인 특수-1] 바닥 2점 앵커 28.0Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌀 공간 요동 FM 앵커: 10.5 Hz 단독",
                "🎯 타격효과: 공간 전체 음압 요동 및 심박 공진 (주주파수 10.50Hz + 기저하모닉 5.25Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.94Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 5.25f, 0.00f, 0.00f, 0.0f,
                                true, 0.090f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 1, true, 15,
                                "[메인 특수-2] 공간요동 10.5Hz FM 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💫 전정기관 파괴 극저역: 7.0 Hz 단독",
                "🎯 타격효과: 미로기관 평형 상실 유발 초저역 (주주파수 7.00Hz + 기저하모닉 3.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±0.70Hz, 속도 0.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 3.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 1, true, 15,
                                "[메인 특수-3] 전정기관파괴 7.0Hz 극저역 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔄 바닥 미세맥동 골도스윕: 21.0 ~ 24.5 Hz (1250초)",
                "🎯 타격효과: 바닥 2점 미세 진동 훑기 (스윕범위 21.0~24.5Hz, 주기 980초, 분당 0.21Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.14Hz, 속도 0.70Hz) | AM ON(심도 12%, 펄스 0.60Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.00f, 24.50f, 980.0f,
                                true, 0.050f, 0.70f, true, 0.120f, 0.60f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, false, 15,
                                "[메인 특수-4] 바닥 미세맥동 21~24.5Hz 골도스윕",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        contentPane.addView(mFrameMainContainer);

        mSplitDivider1 = createSplitDivider("─── ▼ ⚡ 서브1 프레임 (버전2 내성파괴 시나리오) ▼ ───", "#10B981");
        contentPane.addView(mSplitDivider1);

        // -------------------------------------------------------------------------
        // ⚡ [서브1 프레임] 통합운용 시나리오 버전2 (총 16개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub1Container = createFrameContainer(
                "⚡ [서브1 프레임] 통합운용 시나리오 버전2 (내성파괴 전술)",
                "버전1 적응 상대 신경 피로 누적 및 비선형 도약 프리셋",
                "#10B981",
                "#064E3B"
        );

        // [파트 1] 24시간 4대 시간대별 마스터 스케줄 (v2)
        addSectionHeaderInsideFrame(mFrameSub1Container, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 마스터 스케줄 (v2)", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "🌅 1. 아침 (07:00~13:00 v2): 33.0 Hz 앵커 직타",
                "🎯 타격효과: 척추 추간판 압박점 요동 앵커 (단독 33.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.98Hz, 속도 1.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 스케줄-1] 아침 33.0Hz 척추앵커 (FM 6%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "☀️ 2. 오후 (13:00~19:00 v2): 22.0 Hz 앵커 직타",
                "🎯 타격효과: 골반 내 장기 초저속 펄스 타격 (단독 22.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 0.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 0.80f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 스케줄-2] 오후 22.0Hz 골반펄스 (AM 18%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌆 3. 저녁 (19:00~01:00 v2): 5.0 Hz 앵커 직타",
                "🎯 타격효과: 간·담낭 초저역 서파 요동 (단독 5.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±0.50Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 스케줄-3] 저녁 5.0Hz 간담낭서파 (FM 10%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌙 4. 취침 (01:00~07:00 v2): 16.0 Hz 앵커 직타",
                "🎯 타격효과: 음향심리 불쾌감 피크 요동 (단독 16.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.80Hz, 속도 0.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 스케줄-4] 취침 16.0Hz 불쾌감앵커 (FM 5%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v2)
        addSectionHeaderInsideFrame(mFrameSub1Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v2)", "#FACC15");

        addFullPresetButton(mFrameSub1Container,
                "⚡ 1. 상대 공격 즉각 반격 (v2): 5.0 Hz 앵커",
                "🎯 타격효과: 하향 충격파 즉각 발진 (단독 5.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±0.50Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브1 행동-1] 즉각반격 5.0Hz 앵커 (FM 10%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🔨 2. 벌주기 (미로기관 파쇄): 33.0 Hz 척추 압박",
                "🎯 타격효과: 척추 추간판 압박 및 미로기관 파쇄 (단독 33.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 2.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 2.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브1 행동-2] 벌주기 33.0Hz 척추압박 (AM 18%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏢 3. 출근 12시간 무인방어 (v2): 5.0Hz / 22.0Hz 교대",
                "🎯 타격효과: v2 독자 대역 순환 신경 피로 극대화 (주주파수 22.00Hz + 기저하모닉 5.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±2.20Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 5.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 행동-3] 출근 12h 5/22Hz 교대",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🚶 4. 일상 외출 자동 순항 (v2): 16.0 Hz 불쾌감 앵커",
                "🎯 타격효과: 2~4시간 외출 시 기저 둔통 유지 (단독 16.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.96Hz, 속도 0.55Hz) | AM ON(심도 15%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 0.55f, true, 0.150f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 행동-4] 일상외출 16.0Hz 불쾌감앵커 (FM)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏰 5. 초장기 외출 (48h+ v2): 16.0 Hz 초저발열 앵커",
                "🎯 타격효과: 발열 억제 프로파일 48시간 무인 안정 운용 (단독 16.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±0.64Hz, 속도 0.30Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.040f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 행동-5] 초장기 48h+ 16Hz 초저발열",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "💥 6. 상대 내성 완전파괴 (v2): 5.0Hz ↔ 22.0Hz 도약",
                "🎯 타격효과: 주파수 도약 및 예측 불가능 타격 (주주파수 22.00Hz + 기저하모닉 5.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±1.76Hz, 속도 0.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 5.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[서브1 행동-6] 내성완전파괴 5↔22Hz 도약 (FM)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧠 7. 상대 정신 집중차단 (v2): 8.0 Hz 알파파 차단",
                "🎯 타격효과: 알파파 차단 두통 및 인지 마비 유도 (단독 8.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.48Hz, 속도 0.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 행동-7] 정신집중차단 8.0Hz 알파파차단",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🛌 8. 수면 시간 집중압박 (v2): 16.0 Hz 골도 앵커",
                "🎯 타격효과: 바닥 미세 진동 수면 단계 파괴 (주주파수 16.00Hz + 기저하모닉 8.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.80Hz, 속도 0.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 8.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, true, 15,
                                "[서브1 행동-8] 수면시간 16.0Hz 골도앵커",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🛡️ 9. 3시간 이상 기기보호 (v2): 33.0 Hz (45분/15분)",
                "🎯 타격효과: AM 펄스 해머링 전력 효율 극대화 (단독 33.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 행동-9] 3시간 기기보호 33Hz (45/15분)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 3] v2 고유 척추/골반 특수 프리셋
        addSectionHeaderInsideFrame(mFrameSub1Container, "3️⃣ [파트 3] 📊 v2 고유 척추/골반 특수 프리셋", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "🦴 척추 추간판 압박 요동: 33.0 Hz 단독",
                "🎯 타격효과: 척추 추간판 압박 요동 단독 직타 (주주파수 33.00Hz + 기저하모닉 16.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±2.31Hz, 속도 1.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.00f, 16.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.070f, 1.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 1, false, 15,
                                "[서브1 특수-1] 척추추간판압박 33.0Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧬 골반저 초저속 펄스: 22.0 Hz 단독",
                "🎯 타격효과: 골반 내 장기 초저속 펄스 단독 타격 (주주파수 22.00Hz + 기저하모닉 11.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 0.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 11.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 0.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 1, true, 15,
                                "[서브1 특수-2] 골반저 초저속펄스 22.0Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌊 간·담낭 초저역 서파: 5.0 Hz 단독",
                "🎯 타격효과: 간·담낭 초저역 서파 단독 요동 (주주파수 5.00Hz + 기저하모닉 2.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±0.50Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.00f, 2.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 1, true, 15,
                                "[서브1 특수-3] 간담낭초저역서파 5.0Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        contentPane.addView(mFrameSub1Container);

        mSplitDivider2 = createSplitDivider("─── ▼ 🛡️ 서브2 프레임 (버전3 맥놀이공진 시나리오) ▼ ───", "#A855F7");
        contentPane.addView(mSplitDivider2);

        // -------------------------------------------------------------------------
        // 🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (총 16개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub2Container = createFrameContainer(
                "🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (맥놀이 공진 특화)",
                "안구·슐만 고조파·흉골 공진 및 3기기 변조 조화 완결 프리셋",
                "#A855F7",
                "#3B0764"
        );

        // [파트 1] 24시간 4대 시간대별 마스터 스케줄 (v3)
        addSectionHeaderInsideFrame(mFrameSub2Container, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 마스터 스케줄 (v3)", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "🌅 1. 아침 (07:00~13:00 v3): 19.0 Hz 앵커 직타",
                "🎯 타격효과: 안구 안와 떨림 펄스 해머 (단독 19.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 스케줄-1] 아침 19.0Hz 안와펄스 (AM 15%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "☀️ 2. 오후 (13:00~19:00 v3): 13.5 Hz 앵커 직타",
                "🎯 타격효과: 슐만 2차 고조파 척추 요동 앵커 (단독 13.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.95Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 스케줄-2] 오후 13.5Hz 슐만앵커 (FM 7%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌆 3. 저녁 (19:00~01:00 v3): 26.5 Hz 앵커 직타",
                "🎯 타격효과: 흉골/폐 기계 진동 펄스 앵커 (단독 26.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 1.80f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 스케줄-3] 저녁 26.5Hz 흉골펄스 (AM 16%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌙 4. 취침 (01:00~07:00 v3): 8.5 Hz 앵커 직타",
                "🎯 타격효과: 알파파 강제 각성 요동 앵커 (단독 8.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.51Hz, 속도 0.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 스케줄-4] 취침 8.5Hz 각성앵커 (FM 6%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v3)
        addSectionHeaderInsideFrame(mFrameSub2Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v3)", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "⚡ 1. 상대 공격 즉각 반격 (v3): 19.0 Hz 안구 공진 앵커",
                "🎯 타격효과: 안구 19Hz AM 펄스 즉각 가진 (단독 19.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브2 행동-1] 즉각반격 19.0Hz 안구공진 (AM 15%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🔨 2. 벌주기 (흉곽 슐만 파쇄): 13.5 Hz 슐만 앵커",
                "🎯 타격효과: 슐만 2차 고조파 척추/흉곽 집중 타격 (단독 13.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.95Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브2 행동-2] 벌주기 13.5Hz 슐만파쇄 (FM 7%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏢 3. 출근 12시간 무인방어 (v3): 13.5Hz / 26.5Hz 교대 (600초)",
                "🎯 타격효과: 3주차 고유 주파수 및 3기기 변조 조화 (주주파수 26.50Hz + 기저하모닉 13.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±1.86Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.50f, 13.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 행동-3] 출근 12h 13.5/26.5Hz 교대",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🚶 4. 일상 외출 자동 순항 (v3): 19.0 Hz 안와 앵커",
                "🎯 타격효과: 2~4시간 외출 시 시야 피로 가중 (단독 19.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 1.80f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 행동-4] 일상외출 19.0Hz 안와앵커 (AM 12%)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏰 5. 초장기 외출 (48h+ v3): 8.5 Hz 초저발열 앵커",
                "🎯 타격효과: 초저역 앵커 48시간 무인 요새화 (단독 8.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.43Hz, 속도 0.35Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.35f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 행동-5] 초장기 48h+ 8.5Hz 초저발열",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "💥 6. 상대 내성 완전파괴 (v3): 13.5Hz ↔ 26.5Hz 도약",
                "🎯 타격효과: 광대역 카오스 도약 복합 조화 (주주파수 26.50Hz + 기저하모닉 13.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±2.12Hz, 속도 0.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.50f, 13.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[서브2 행동-6] 내성완전파괴 13.5↔26.5Hz 도약",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🧠 7. 상대 정신 집중차단 (v3): 19.0 Hz 안와 공진 앵커",
                "🎯 타격효과: 안구 공진으로 시각/청각 집중 원천 차단 (단독 19.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 행동-7] 정신집중차단 19.0Hz 안와공진",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🛌 8. 수면 시간 집중압박 (v3): 8.5 Hz 각성 유발 앵커",
                "🎯 타격효과: 8.5Hz 각성 유도로 수면 단계 파괴 (주주파수 8.50Hz + 기저하모닉 4.25Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.51Hz, 속도 0.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.50f, 4.25f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, true, 15,
                                "[서브2 행동-8] 수면시간 8.5Hz 각성앵커",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🛡️ 9. 3시간 이상 기기보호 (v3): 19.0 Hz (50분/10분)",
                "🎯 타격효과: AM 펄스 듀티 사이클 동기화로 발열 45도 이하 유지 (단독 19.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 행동-9] 3시간 기기보호 19Hz (50/10분)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });

        // [파트 3] v3 고유 안구/흉골 공진 특수 프리셋
        addSectionHeaderInsideFrame(mFrameSub2Container, "3️⃣ [파트 3] 📊 v3 고유 안구/흉골 공진 특수 프리셋", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "👁️ 안구 안와 공진 펄스: 19.0 Hz 단독",
                "🎯 타격효과: 안구 안와 공진 펄스 해머 단독 타격 (주주파수 19.00Hz + 기저하모닉 9.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.00f, 9.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 1, true, 15,
                                "[서브2 특수-1] 안구안와공진 19.0Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "⚡ 슐만 2차 고조파 척추 락온: 13.5 Hz 단독",
                "🎯 타격효과: 슐만 2차 고조파 척추 요동 앵커 단독 락온 (주주파수 13.50Hz + 기저하모닉 6.75Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.95Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.50f, 6.75f, 0.00f, 0.00f, 0.0f,
                                true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 1, true, 15,
                                "[서브2 특수-2] 슐만 2차고조파 13.5Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🫁 흉골/폐 기계 진동 펄스: 26.5 Hz 단독",
                "🎯 타격효과: 흉골/폐 기계 진동 펄스 앵커 단독 가진 (주주파수 26.50Hz + 기저하모닉 13.25Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.50f, 13.25f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 1.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 1, false, 15,
                                "[서브2 특수-3] 흉골/폐 기계진동 26.5Hz 단독",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });

        contentPane.addView(mFrameSub2Container);

                // -------------------------------------------------------------------------
        // 🏛️ [구버전 시나리오] 구버전 통합 최적화 정제 시나리오 (총 12개 비중복 프리셋)
        // -------------------------------------------------------------------------
        mFrameLegacyContainer = createFrameContainer(
                "[공통] 통합 최적화 정제 (v4.2)",
                "공통 고유 스윕·슬래브 1·2·3차 공진·카운터 전술 (초저주파→중저주파 정렬)",
                "#F59E0B",
                "#451A03"
        );
        addSectionHeaderInsideFrame(mFrameLegacyContainer, "1️⃣ [파트 1] 🌅 구버전 24시간 4대 시간대 최적화 정제 스케줄", "#F59E0B");
        addFullPresetButton(mFrameLegacyContainer,
                "🌅 1. 아침 (07:00~13:00 구버전 정제): 52.0 ~ 55.0 Hz (180초 스윕)",
                "🎯 타격효과: 구버전 프리셋1 주간 하이브리드(55Hz 상한 정제) · 흉부 압박 및 멀미 유도",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 1.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 52.00f, 55.00f, 180.0f,
                                true, 0.030f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[구버전 스케줄-1] 아침 52.0~55.0Hz 하이브리드 (180s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "☀️ 2. 오후 (13:00~19:00 구버전 정제): 6.7 ~ 9.0 Hz (490초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕2 소화기·복부 연동 및 알파파 하단 교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.55Hz, 속도 0.85Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.70f, 9.00f, 490.0f,
                                true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 스케줄-2] 오후 6.7~9.0Hz 소화기·복부 (490s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌆 3. 저녁 (19:00~01:00 구버전 정제): 3.3 ~ 7.3 Hz (640초 스윕)",
                "🎯 타격효과: 구버전 앱 슬롯2 실측값(10분40초 루프) · 델타-세타 뇌파 동조 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.45Hz, 속도 0.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 3.30f, 7.30f, 640.0f,
                                true, 0.085f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[구버전 스케줄-3] 저녁 3.3~7.3Hz 슬롯2 뇌파차단 (640s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌙 4. 취침 (01:00~07:00 구버전 정제): 12.0 ~ 24.5 Hz (6580초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕8 야간 서파·장간막 진동 분쇄 초장주기(109분) 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 12.00f, 24.50f, 6580.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[구버전 스케줄-4] 취침 12.0~24.5Hz 야간분쇄 (6580s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameLegacyContainer, "2️⃣ [파트 2] 🎯 구버전 고유 스윕 & 120mm 슬래브 1·2·3차 공진 (초저주파 → 중저주파 순)", "#FACC15");
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 1. [초저주파 구버전-스윕7] 1.0 ~ 4.0 Hz (2880초/48분 스윕) — 델타 서파 무인스캔",
                "🎯 타격효과: 구버전 초안 스윕7 원전(48분 루프) · 델타 서파 위상 스캔 및 심부 수면 방해",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.12Hz, 속도 0.30Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 4.00f, 2880.0f,
                                true, 0.050f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 전술-1] 초저주파 1.0~4.0Hz 초장주기 (2880s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 2. [초저주파 구버전-스윕1] 4.0 ~ 6.2 Hz (290초 스윕) — 척추·전정계 수직 바운싱",
                "🎯 타격효과: 구버전 초안 스윕1 원전(290초 주기) · 척추·전정계 수직 바운싱 및 내장 흔들림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.46Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.00f, 6.20f, 290.0f,
                                true, 0.090f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 전술-2] 초저주파 4.0~6.2Hz 전정계 (290s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 3. [초저주파 슬래브 1차모드] 18.5 Hz 단독 — 120mm 슬래브 1차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 1차 지배공진(18.5Hz) 직타 · 안구·두개골 동시 울림",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[구버전 전술-3] 슬래브 1차지배공진 18.5Hz 직타",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 4. [중저주파 구버전-프리셋4] 18.6 ~ 25.1 Hz (900초 스윕, AM 0.50Hz) — 심야 역제압",
                "🎯 타격효과: 구버전 프리셋4 심야 내장진동(20~22Hz 장간막) 역제압 호흡주기 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[구버전 전술-4] 심야역제압 18.6~25.1Hz (AM 0.5Hz)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 5. [중저주파 구버전-프리셋3] 18.6 ~ 25.1 Hz (900초 스윕, RM 1.80Hz) — 외출 카오스",
                "🎯 타격효과: 구버전 프리셋3 장시간 외출용 카오스 비트 원전(RM 1.80Hz 회전맥놀이)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 28%, 캐리어2 1.80Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.280f, 1.80f,
                                3.0f, 1, 0, false, 15,
                                "[구버전 전술-5] 외출카오스 18.6~25.1Hz (RM 1.8Hz)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 6. [중저주파 구버전-프리셋5] 19.5 Hz + 55.0 Hz 복합 — 안구·흉벽 동시 응징 해머",
                "🎯 타격효과: 구버전 프리셋5 즉각 응징 해머 펄스(19.5Hz 안구공진 + 55.0Hz 슬래브 표면)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 4.10Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.50f, 55.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 4.10f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[구버전 전술-6] 즉각응징 19.5+55.0Hz 복합해머",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 7. [중저주파 슬래브 2차모드] 32.4 Hz 단독 — 120mm 슬래브 2차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 2차 지배공진(32.4Hz) 직타 · 흉복부 관통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.94Hz, 속도 1.90Hz) | AM ON(심도 15%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 32.40f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.90f, true, 0.150f, 3.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 1, false, 15,
                                "[구버전 전술-7] 슬래브 2차지배공진 32.4Hz 직타",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 8. [중저주파 슬래브 3차모드] 54.8 Hz 단독 — 120mm 슬래브 3차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 3차 지배공진(54.8Hz) 직타 · 바닥 표면 진동 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.8%/±1.53Hz, 속도 1.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.028f, 1.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[구버전 전술-8] 슬래브 3차지배공진 54.8Hz 직타",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        contentPane.addView(mFrameLegacyContainer);

        // -------------------------------------------------------------------------
        // 📐 [기초 시나리오] 초저주파 → 중저주파 무중복 오름차순 스윕(10종) & 단독(12종) (총 22개)
        // -------------------------------------------------------------------------
        mFrameBasicContainer = createFrameContainer(
                "[기초] 초저주파 → 중저주파 무중복 최적화 (v4.2)",
                "조합(1·3·4) 무중복 연속대역 스윕(10종) + 조합(2) 주파수별 단독(12종) | 초저주파→중저주파 완벽 정렬",
                "#EC4899",
                "#500724"
        );
        addSectionHeaderInsideFrame(mFrameBasicContainer, "1️⃣-A [초저주파 스윕: 1.0 ~ 21.5 Hz] 뇌파·전정기관·자율신경·두개골·시신경 (6단계 무중복 오름차순)", "#38BDF8");
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 01] 1.0 ~ 2.5 Hz (300초 스윕) — 이석기관 한계·수면후 불쾌감",
                "🎯 타격효과: [조합1 반응5] 극저역 이석기관(Otolith) 수직가속도 한계 자극 · 기절유도 · 수면 후 극심한 불쾌감·무기력",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.10Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +9.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 2.50f, 300.0f,
                                true, 0.060f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                9.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕01] 1.0~2.5Hz 이석기관·수면후불쾌감 (300s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 02] 2.5 ~ 4.5 Hz (360초 스윕) — 델타파 차단·심층 두통 유도",
                "🎯 타격효과: [조합1 반응1] 델타파(1~4Hz) 숙면 뇌파 동조 차단 · 두개내압 미세 맥동 · 심층 두통 및 수면단계 파괴",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.28Hz, 속도 0.45Hz) | AM ON(심도 12%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 2.50f, 4.50f, 360.0f,
                                true, 0.080f, 0.45f, true, 0.120f, 1.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕02] 2.5~4.5Hz 델타파차단·심층두통 (360s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 03] 4.5 ~ 7.0 Hz (420초 스윕) — 장간막 공진·복통·수면리듬 분리",
                "🎯 타격효과: [조합1 스윕1하단·반응3] ISO 2631-1 복부 내장 수직 기본공진(4.5~7Hz) · 복통 유발 · 세타파 수면리듬 분리",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 3.90Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.50f, 7.00f, 420.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 3.90f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕03] 4.5~7.0Hz 복통유발·수면리듬분리 (420s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 04 ★1차주력] 7.0 ~ 11.0 Hz (480초 스윕) — 전정·뇌간·자율신경·알파파",
                "🎯 타격효과: [조합1·3(★5)·4 통합] 전정·뇌간(7Hz)~슈만(7.83Hz)~자율신경·알파파(10.5~11Hz) 현기증·오심·맥박불안정·멍함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.77Hz, 속도 0.78Hz) | AM ON(심도 15%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FFD700",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 7.00f, 11.00f, 480.0f,
                                true, 0.085f, 0.78f, true, 0.150f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파스윕04] 7.0~11.0Hz 전정·자율신경·현기증 (480s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 05 ★1차주력] 11.0 ~ 16.5 Hz (500초 스윕) — 뇌파간섭·인후두·장연동 교란",
                "🎯 타격효과: [조합1 스윕2·6하단 + 조합3(★5)] SMR·베타파 뇌파간섭 · 인후두·기도 압박 · 장연동 설사/복통 및 내장 울림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 11.00f, 16.50f, 500.0f,
                                true, 0.070f, 1.25f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 0, false, 15,
                                "[기초-초저주파스윕05] 11.0~16.5Hz 뇌파·장연동·내장울림 (500s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 06 ★1차주력] 16.5 ~ 21.5 Hz (450초 스윕) — 두개골·시신경·귀울림(이명)",
                "🎯 타격효과: [조합1 반응4 + 조합3(★5) + 조합4] 두개골·전두엽(18Hz)·시신경·안구(18~21Hz) 공진 · 귀울림(이명)·두통·안구압박",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.71Hz, 속도 6.20Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM ON(심도 22%, 캐리어2 18.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 16.50f, 21.50f, 450.0f,
                                true, 0.090f, 6.20f, true, 0.160f, 4.00f, true, 0.220f, 18.50f,
                                3.0f, 1, 0, false, 15,
                                "[기초-초저주파스윕06] 16.5~21.5Hz 두개골·시신경·이명 (450s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "1️⃣-B [중저주파 스윕: 21.5 ~ 55.0 Hz] 내이·흉곽·복부·골반·고막·슬래브 공진 (4단계 무중복 오름차순)", "#FACC15");
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 07 ★2차보조] 21.5 ~ 27.0 Hz (500초 스윕) — 내이·석고보드·흉곽·대장",
                "🎯 타격효과: [조합3(★3)하단 + 조합4] 내이 방향감각 상실(22Hz) · 석고보드 한계·흉곽압박(24Hz) · 대장진동·하체무력(25Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.33Hz, 속도 1.15Hz) | AM ON(심도 18%, 펄스 3.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.50f, 27.00f, 500.0f,
                                true, 0.055f, 1.15f, true, 0.180f, 3.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, false, 15,
                                "[기초-중저주파스윕07] 21.5~27.0Hz 내이·흉곽·대장진동 (500s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 08 ★2차보조] 27.0 ~ 36.0 Hz (520초 스윕) — 골반·하복부·흉곽·위장·간",
                "🎯 타격효과: [조합1 스윕3 + 조합3(★3)상단 + 조합4] 골반·하복부 둔통(28Hz) · 흉곽·위장·간 묵직한 타격(31~36Hz) 주간 집중교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.89Hz, 속도 1.80Hz) | AM ON(심도 17%, 펄스 3.20Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 27.00f, 36.00f, 520.0f,
                                true, 0.060f, 1.80f, true, 0.170f, 3.20f, true, 0.180f, 13.50f,
                                3.0f, 1, 1, false, 15,
                                "[기초-중저주파스윕08] 27.0~36.0Hz 골반·흉곽·간 집중교란 (520s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 09 ★중광역] 36.0 ~ 46.0 Hz (580초 스윕) — 대뇌공포·귀막힘·집중력저하",
                "🎯 타격효과: [조합1 스윕4·7하단 + 조합3 중광역] 반복 불쾌울림·대뇌 공포감(40Hz) · 귀막힘·고막압박·집중력 저하(38~46Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.84Hz, 속도 1.40Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 36.00f, 46.00f, 580.0f,
                                true, 0.045f, 1.40f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파스윕09] 36.0~46.0Hz 대뇌공포·귀막힘·고막압박 (580s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 10 ★3차확산] 46.0 ~ 55.0 Hz (650초 스윕) — 귀먹먹함·콘크리트 슬래브 공진",
                "🎯 타격효과: [조합1 스윕4상단 + 조합3(★2) + 조합4] 고출력 귀 먹먹함(46~51Hz) ~ 콘크리트 벽면·슬래브 공간공진 최적점(55Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.8%/±1.92Hz, 속도 1.15Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM ON(심도 20%, 캐리어2 11.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 46.00f, 55.00f, 650.0f,
                                true, 0.038f, 1.15f, true, 0.120f, 2.40f, true, 0.200f, 11.50f,
                                0.0f, 0, 0, false, 15,
                                "[기초-중저주파스윕10] 46.0~55.0Hz 귀먹먹함·슬래브공간공진 (650s)",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-A [초저주파 단독: 7.0 ~ 18.0 Hz] 뇌간·자율신경·두개골·전두엽 직타 (4종 무중복 오름차순)", "#F472B6");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 01 ★1차주력] 7.0 Hz (+기저 3.5Hz) — 구조물 심부공진 & 전정기관·뇌간",
                "🎯 타격효과: [조합2·3·4] 구조물 심부공진 · 전정기관·뇌간 타격 · 현기증·방향감각 혼란·기저 스트레스 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.5%/±0.53Hz, 속도 0.55Hz) | AM ON(심도 12%, 펄스 3.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 3.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.075f, 0.55f, true, 0.120f, 3.80f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파단독01] 7.0Hz(+3.5Hz) 심부공진·전정뇌간 현기증",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 02 ★1차주력] 10.5 Hz (+기저 5.25Hz) — 심박변동(HRV) & 자율신경 교란",
                "🎯 타격효과: [조합2·3·4] 심박변동(HRV)·자율신경계 직접 교란 · 원인불명 메스꺼움·불안정한 맥박·알파파 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.89Hz, 속도 0.95Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 5.25f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 0.95f, true, 0.140f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파단독02] 10.5Hz(+5.25Hz) 자율신경·심박교란 메스꺼움",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 03 ★1차주력] 16.0 Hz (+기저 8.0Hz) — 뇌파간섭·위상현기증·장연동 복통",
                "🎯 타격효과: [조합2·3·4] 인체 임계경계(뇌파간섭 수면방해 · 내이-시각 위상어긋남 현기증 · 장연동 설사/복통 · 두개골 공진)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 20%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 8.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.25f, true, 0.200f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 0, false, 15,
                                "[기초-초저주파단독03] 16.0Hz(+8.0Hz) 위상현기증·장연동복통",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 04 ★1차주력] 18.0 Hz (+기저 9.0Hz) — 벽 전체 불쾌공명 & 전두엽·삼차신경",
                "🎯 타격효과: [조합2·3·4] 벽 전체 불쾌한 울림 · 두개골 내부 공진 · 극심한 두통 · 삼차신경 자극 · 만성 무기력",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±1.53Hz, 속도 6.30Hz) | AM OFF(0%) | RM ON(심도 22%, 캐리어2 18.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.00f, 9.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 6.30f, false, 0.000f, 0.00f, true, 0.220f, 18.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-초저주파단독04] 18.0Hz(+9.0Hz) 벽불쾌공명·전두엽두통",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-B [중저주파 단독: 21.0 ~ 55.0 Hz] 시신경·내이·흉곽·복부·골반·슬래브 직타 (8종 무중복 오름차순)", "#34D399");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 05 ★1차주력] 21.0 Hz (+기저 10.5Hz) — 시신경·안구 공진 & 정신적 긴장",
                "🎯 타격효과: [조합2·3·4] NASA 안구·시신경 공진(21Hz) · 안구 압박·초점 저하 · 정신적 긴장 및 고강도 스트레스",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.16Hz, 속도 2.10Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 21.00f, 10.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.055f, 2.10f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독05] 21.0Hz(+10.5Hz) 시신경안구압박·정신긴장",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 06 ★2차보조] 22.0 Hz (+기저 11.0Hz) — 귀 안쪽·내이 방향감각 상실",
                "🎯 타격효과: [조합3·4] 귀 안쪽·내이 미로기관 집중 공진 · 방향감각 상실 · 자세 불균형 및 어지러움",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.98Hz, 속도 3.40Hz) | AM ON(심도 14%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 11.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.090f, 3.40f, true, 0.140f, 2.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독06] 22.0Hz(+11.0Hz) 내이 방향감각상실·불균형",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 07 ★2차보조] 24.0 Hz (+기저 12.0Hz) — 석고보드 한계 & 흉곽·폐 압박",
                "🎯 타격효과: [조합2·3·4] 석고보드 벽체 공진 한계점 · 공간 전체 압박감 · 흉곽·폐 진동 및 가슴 답답함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.08Hz, 속도 1.15Hz) | AM ON(심도 16%, 펄스 3.70Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 24.00f, 12.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.045f, 1.15f, true, 0.160f, 3.70f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, false, 15,
                                "[기초-중저주파단독07] 24.0Hz(+12.0Hz) 석고보드한계·흉곽폐압박",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 08 ★2차보조] 25.0 Hz (+기저 12.5Hz) — 대장·하복부 장진동 & 하체 무력",
                "🎯 타격효과: [조합2·3·4] 인체 7~25Hz 핵심 영향권 상단 마감 · 대장·하복부 진동 · 복부 팽만 및 하체 무력감",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 25.00f, 12.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독08] 25.0Hz(+12.5Hz) 대장하복부진동·하체무력",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 09 ★2차보조] 28.0 Hz (+기저 14.0Hz) — 골반·장기 하부 무거움·둔통",
                "🎯 타격효과: [조합3·4] 골반저근·장기 하부 직타 · 하복부 묵직한 무거움 및 지속적 복부 둔통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.40Hz, 속도 0.90Hz) | AM ON(심도 17%, 펄스 3.15Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L·R 180° 역위상 전단파(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB",
                "#60A5FA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 14.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.90f, true, 0.170f, 3.15f, false, 0.000f, 0.00f,
                                3.0f, 0, 1, false, 15,
                                "[기초-중저주파단독09] 28.0Hz(+14.0Hz) 골반장기하부 무거움·둔통",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 12시~1시(70~75% 무왜곡 한계) · L/R 진동자 1.5~2.0m 이격 바닥밀착(전단파 극대화) · 외부EQ 0dB");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 10 ★2차보조] 31.0 Hz (+기저 15.5Hz) — 흉곽·위장·간 묵직한 타격",
                "🎯 타격효과: [조합2·3·4] 흉곽 진동 및 상복부 장기(위장·간) 묵직한 타격감 · 심부 내장 피로 누적",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 2.75Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 31.00f, 15.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 2.75f, true, 0.180f, 13.50f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독10] 31.0Hz(+15.5Hz) 흉곽·위장·간 묵직한타격",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 11 ★3차확산] 40.0 Hz (+기저 20.0Hz) — 반복 불쾌울림 & 대뇌 공포감",
                "🎯 타격효과: [조합2·3·4] 타격성 강한 반복 불쾌울림 · 대뇌 감마파 교란 · 원인 모를 공포감 및 정신 혼미",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 40.00f, 20.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독11] 40.0Hz(+20.0Hz) 반복불쾌울림·대뇌공포혼미",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 12 ★3차확산] 55.0 Hz (+기저 27.5Hz) — 콘크리트 벽면·슬래브 공진 최적점",
                "🎯 타격효과: [조합2·3·4] 아파트 콘크리트 벽면·슬래브 공진 최적점 · 체감 진동 및 구조물 전달력 최강 마감",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.2%/±1.76Hz, 속도 1.35Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 합산(240W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 27.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.032f, 1.35f, true, 0.120f, 2.40f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[기초-중저주파단독12] 55.0Hz(+27.5Hz) 콘크리트벽면·슬래브 최적점",
                                "[스마토#2 240W 2채널 L/R]: 단일볼륨 노브 11시~1시(65~75% 무왜곡 한계) · L/R 진동자 1.2~1.5m 중앙집중 바닥밀착 · 외부EQ 0dB(DSP 직결)");
                    }
                });
        contentPane.addView(mFrameBasicContainer);



        scroll.addView(contentPane);
        setContentView(scroll);
        switchSplitView(0); // Default: 전체
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUiHandler.post(mTelemetryUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryUpdater);
    }

    // =========================================================================
    // 🎛️ Split View Switching Logic: Exactly [전체 / 메인 / 서브1 / 서브2]
    // =========================================================================
    private void switchSplitView(int modeIndex) {
        mCurrentSplitMode = modeIndex;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 5; i++) {
            GradientDrawable tabBg = new GradientDrawable();
            if (i == modeIndex) {
                tabBg.setColor(Color.parseColor("#0284C7")); // Active Sky Blue
                tabBg.setCornerRadius(6 * density);
                mSplitViewButtons[i].setTextColor(Color.parseColor("#FFFFFF"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                tabBg.setColor(Color.parseColor("#1E293B")); // Inactive Slate
                tabBg.setCornerRadius(6 * density);
                mSplitViewButtons[i].setTextColor(Color.parseColor("#94A3B8"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT);
            }
            mSplitViewButtons[i].setBackground(tabBg);
        }

        if (mSplitDivider1 != null) mSplitDivider1.setVisibility(View.GONE);
        if (mSplitDivider2 != null) mSplitDivider2.setVisibility(View.GONE);
        mFrameMainContainer.setVisibility(modeIndex == 0 ? View.VISIBLE : View.GONE);
        mFrameSub1Container.setVisibility(modeIndex == 1 ? View.VISIBLE : View.GONE);
        mFrameSub2Container.setVisibility(modeIndex == 2 ? View.VISIBLE : View.GONE);
        mFrameLegacyContainer.setVisibility(modeIndex == 3 ? View.VISIBLE : View.GONE);
        mFrameBasicContainer.setVisibility(modeIndex == 4 ? View.VISIBLE : View.GONE);
    }

    private TextView createSplitTabButton(LinearLayout parent, String text, float weight, View.OnClickListener listener) {
        float density = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(11.5f);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins((int)(2 * density), 0, (int)(2 * density), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        parent.addView(btn);
        return btn;
    }

    private LinearLayout createFrameContainer(String title, String desc, String accentColor, String cardBgColor) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(10 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(accentColor));
        frame.setBackground(bg);
        frame.setPadding((int)(14 * density), (int)(12 * density), (int)(14 * density), (int)(14 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(16 * density));
        frame.setLayoutParams(lp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(Color.parseColor(accentColor));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        frame.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(11);
        tvDesc.setTextColor(Color.parseColor("#CBD5E1"));
        tvDesc.setPadding(0, (int)(2 * density), 0, (int)(10 * density));
        frame.addView(tvDesc);

        return frame;
    }

    private void addSectionHeaderInsideFrame(LinearLayout frame, String sectionTitle, String color) {
        float density = getResources().getDisplayMetrics().density;
        TextView header = new TextView(this);
        header.setText(sectionTitle);
        header.setTextSize(13);
        header.setTextColor(Color.parseColor(color));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, (int)(10 * density), 0, (int)(6 * density));
        frame.addView(header);
    }

    private LinearLayout createSplitDivider(String text, String color) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.VERTICAL);
        divider.setGravity(Gravity.CENTER);
        divider.setPadding(0, (int)(4 * density), 0, (int)(12 * density));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor(color));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        divider.addView(tv);

        return divider;
    }

    private void addFullPresetButton(LinearLayout parent, String mainText, String subText, String dspText,
                                     String accentColor, View.OnClickListener listener) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1.2f * density), Color.parseColor("#334155"));
        card.setBackground(bg);
        card.setPadding((int)(12 * density), (int)(10 * density), (int)(12 * density), (int)(10 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(3 * density), 0, (int)(6 * density));
        card.setLayoutParams(lp);

        TextView tvMain = new TextView(this);
        tvMain.setText(mainText);
        tvMain.setTextSize(13.5f);
        tvMain.setTextColor(Color.parseColor("#FFFFFF"));
        tvMain.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvMain);

        TextView tvSub = new TextView(this);
        tvSub.setText(subText);
        tvSub.setTextSize(11.0f);
        tvSub.setTextColor(Color.parseColor("#E2E8F0"));
        tvSub.setPadding(0, (int)(3 * density), 0, (int)(3 * density));
        card.addView(tvSub);

        String[] specLines = dspText.split("\n");
        for (int i = 0; i < specLines.length; i++) {
            TextView tvLine = new TextView(this);
            tvLine.setText(specLines[i]);
            tvLine.setTextSize(10.5f);
            tvLine.setTypeface(Typeface.DEFAULT_BOLD);
            tvLine.setPadding(0, (int)(1.5f * density), 0, (int)(1.5f * density));
            if (i == 0) {
                tvLine.setTextColor(Color.parseColor("#F472B6")); // Neon Pink for FM/AM/RM
            } else if (i == 1) {
                tvLine.setTextColor(Color.parseColor(accentColor)); // Accent Color for Vorta DSP
            } else {
                tvLine.setTextColor(Color.parseColor("#34D399")); // Emerald Green for Amp/Knob
            }
            card.addView(tvLine);
        }

        card.setOnClickListener(listener);
        parent.addView(card);
    }

    private void startPreset(boolean sweep, float freq, float subFreq, float sMin, float sMax, float sSec,
                             boolean fmOn, float fmDepth, float fmRate,
                             boolean amOn, float amDepth, float amRate,
                             boolean rmOn, float rmDepth, float rmRate,
                             float boostDb, int limiterMode, int channelMode, boolean exciter, int volStep,
                             String title, String knobGuide) {
        Intent it = new Intent(this, S7AudioGeneratorService.class);
        it.putExtra("action", "start");
        it.putExtra("sweep", sweep);
        if (sweep) {
            it.putExtra("sweep_min", sMin);
            it.putExtra("sweep_max", sMax);
            it.putExtra("sweep_sec", sSec);
        } else {
            it.putExtra("freq", freq);
            it.putExtra("sub_freq", subFreq);
        }
        it.putExtra("fm_on", fmOn);
        it.putExtra("fm_depth", fmDepth);
        it.putExtra("fm_rate", fmRate);

        it.putExtra("am_on", amOn);
        it.putExtra("am_depth", amDepth);
        it.putExtra("am_rate", amRate);

        it.putExtra("rm_on", rmOn);
        it.putExtra("rm_depth", rmDepth);
        it.putExtra("rm_rate", rmRate);

        it.putExtra("bass_boost_db", boostDb);
        it.putExtra("limiter_mode", limiterMode);
        it.putExtra("channel_mode", channelMode);
        it.putExtra("harmonic_exciter", exciter);
        it.putExtra("vol_step", volStep);

        String partTag = "[메인]";
        if (title.contains("기초") || mCurrentSplitMode == 4) {
            partTag = "[기초]";
        } else if (title.contains("구버전") || title.contains("공통") || mCurrentSplitMode == 3) {
            partTag = "[공통]";
        } else if (title.contains("서브1") || mCurrentSplitMode == 1) {
            partTag = "[서브1]";
        } else if (title.contains("서브2") || mCurrentSplitMode == 2) {
            partTag = "[서브2]";
        }
        S7AudioGeneratorService.sActivePart = partTag;
        S7AudioGeneratorService.sScenarioName = title;

        it.putExtra("title", title);
        it.putExtra("part", partTag);
        it.putExtra("scenario", title);
        it.putExtra("knob_guide", knobGuide);

        S7AudioGeneratorService.sIsPlaying = true;
        S7AudioGeneratorService.sModeTitle = title;
        S7AudioGeneratorService.sKnobGuide = knobGuide;
        S7AudioGeneratorService.sFmEnabled = fmOn;
        S7AudioGeneratorService.sFmDepth = fmDepth;
        S7AudioGeneratorService.sFmRateHz = fmRate;
        S7AudioGeneratorService.sAmEnabled = amOn;
        S7AudioGeneratorService.sAmDepth = amDepth;
        S7AudioGeneratorService.sAmRateHz = amRate;
        S7AudioGeneratorService.sRmEnabled = rmOn;
        S7AudioGeneratorService.sRmDepth = rmDepth;
        S7AudioGeneratorService.sRmRateHz = rmRate;
        S7AudioGeneratorService.sBassBoostDb = boostDb;
        S7AudioGeneratorService.sLimiterMode = limiterMode;
        S7AudioGeneratorService.sChannelMode = channelMode;
        S7AudioGeneratorService.sHarmonicExciter = exciter;
        S7VolumeHelper.syncTargetFromSystem(this);
        if (sweep) {
            S7AudioGeneratorService.sIsSweepMode = true;
            S7AudioGeneratorService.sSweepMin = sMin;
            S7AudioGeneratorService.sSweepMax = sMax;
            S7AudioGeneratorService.sSweepPeriodSec = sSec;
            S7AudioGeneratorService.sInstantFreq = sMin;
        } else {
            S7AudioGeneratorService.sIsSweepMode = false;
            S7AudioGeneratorService.sTargetFreq = freq;
            S7AudioGeneratorService.sSubFreq = subFreq;
            S7AudioGeneratorService.sInstantFreq = freq;
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(it);
        } else {
            startService(it);
        }
        Toast.makeText(this, title + " 발진 시작", Toast.LENGTH_SHORT).show();
        syncTelemetryUi();
    }

    private Boolean mLastPlayingState = null;
    private static void setTextIfChanged(TextView tv, String text, int color) {
        if (tv == null) return;
        CharSequence cur = tv.getText();
        if (cur == null || !cur.toString().equals(text)) {
            tv.setText(text);
        }
        if (tv.getCurrentTextColor() != color) {
            tv.setTextColor(color);
        }
    }

    private void syncTelemetryUi() {
        boolean isPlaying = S7AudioGeneratorService.sIsPlaying;
        float density = getResources().getDisplayMetrics().density;

        if (mLastPlayingState == null || mLastPlayingState.booleanValue() != isPlaying) {
            mLastPlayingState = Boolean.valueOf(isPlaying);
            if (mStatusCardLayout != null) {
                GradientDrawable scBg = new GradientDrawable();
                scBg.setCornerRadius(8 * density);
                scBg.setColor(Color.parseColor("#111827"));
                scBg.setStroke((int)(2 * density), Color.parseColor(isPlaying ? "#10B981" : "#EF4444"));
                mStatusCardLayout.setBackground(scBg);
            }
            if (mStatusTrafficLight != null) {
                GradientDrawable trafficDot = new GradientDrawable();
                trafficDot.setShape(GradientDrawable.OVAL);
                trafficDot.setColor(Color.parseColor(isPlaying ? "#00E676" : "#FF3366"));
                mStatusTrafficLight.setImageDrawable(trafficDot);
            }
        }

        if (mStatusMode != null) {
            if (isPlaying && S7AudioGeneratorService.sAuxSuspended) {
                setTextIfChanged(mStatusMode, "상태: [일시 차단] AUX 분리 감지 - 스피커 보호 대기", Color.parseColor("#F59E0B"));
            } else if (isPlaying) {
                setTextIfChanged(mStatusMode, "상태: [발진 중] " + S7AudioGeneratorService.sActivePart, Color.parseColor("#00E676")); // Neon Traffic Light Green
            } else {
                setTextIfChanged(mStatusMode, "상태: [작동 중지] 멈춤 (대기 상태)", Color.parseColor("#FF3366")); // Neon Traffic Light Red
            }
        }

        if (mStatusScenario != null) {
            if (isPlaying) {
                setTextIfChanged(mStatusScenario, "시나리오: " + S7AudioGeneratorService.sScenarioName, Color.parseColor("#00F0FF")); // Electric Cyan
            } else {
                setTextIfChanged(mStatusScenario, "시나리오: 대기 중 (선택 없음)", Color.parseColor("#64748B"));
            }
        }

        if (mStatusFreq != null) {
            if (!isPlaying) {
                setTextIfChanged(mStatusFreq, "발진: 0.00 Hz (출력 차단 / MUTE)", Color.parseColor("#94A3B8")); // Muted Gray
            } else if (S7AudioGeneratorService.sIsSweepMode) {
                setTextIfChanged(mStatusFreq, String.format("발진: %.2f Hz 실시간 (%.1f~%.1fHz 스윕)",
                        S7AudioGeneratorService.sInstantFreq, S7AudioGeneratorService.sSweepMin, S7AudioGeneratorService.sSweepMax), Color.parseColor("#FFD700")); // High-brightness & saturation Green
            } else {
                setTextIfChanged(mStatusFreq, String.format("발진: %.2f Hz 직타 고정 (-1.0dBTP 무왜곡 | Peak 29200)",
                        S7AudioGeneratorService.sTargetFreq), Color.parseColor("#FFD700")); // High-brightness & saturation Green
            }
        }

        if (mStatusMod != null) {
            if (!isPlaying) {
                setTextIfChanged(mStatusMod, "〰️ 변조(FM·AM·RM): 대기 중 (프리셋 선택 시 FM 편이율·AM 펄스심도·RM 측파대 실시간 표시)", Color.parseColor("#64748B"));
            } else {
                String fmS = S7AudioGeneratorService.sFmEnabled ? String.format("ON(±%.1f%%·%.2fHz)", S7AudioGeneratorService.sFmDepth * 100f, S7AudioGeneratorService.sFmRateHz) : "OFF(0%)";
                String amS = S7AudioGeneratorService.sAmEnabled ? String.format("ON(%.0f%%·%.2fHz)", S7AudioGeneratorService.sAmDepth * 100f, S7AudioGeneratorService.sAmRateHz) : "OFF(0%)";
                String rmS = S7AudioGeneratorService.sRmEnabled ? String.format("ON(%.0f%%·%.2fHz)", S7AudioGeneratorService.sRmDepth * 100f, S7AudioGeneratorService.sRmRateHz) : "OFF(0%)";
                setTextIfChanged(mStatusMod, String.format("〰️ 변조(FM·AM·RM): FM %s | AM %s | RM %s", fmS, amS, rmS), Color.parseColor("#F472B6"));
            }
        }

        if (mStatusDsp != null) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int curVol = (am != null) ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : S7VolumeHelper.sTargetVolumeStep;
            if (S7VolumeHelper.isPersonalDevice()) {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 개인기기 보호(무개입)",
                        S7AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            } else if (S7VolumeHelper.isA30Device()) {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 음질·음향효과 최적화 고정",
                        S7AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            } else {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 자유볼륨·OS 안전볼륨 해제 ON",
                        S7AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            }
        }
        if (mStatusKnob != null) {
            setTextIfChanged(mStatusKnob, "🔊 앰프설정: " + S7AudioGeneratorService.sKnobGuide, Color.parseColor(isPlaying ? "#34D399" : "#94A3B8"));
        }
    }
}
