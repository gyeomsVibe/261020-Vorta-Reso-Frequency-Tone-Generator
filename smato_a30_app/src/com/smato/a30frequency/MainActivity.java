package com.smato.a30frequency;

import android.app.Activity;
import android.content.Intent;
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

/**
 * 🎛️ 보르타 레조 A30 v4.2 메인 액티비티
 * - 대상 기기: Samsung Galaxy A30 (SM-A305N, Android 11 One UI 3.1)
 * - 앰프 구성: 스마토 우퍼 전용 앰프 #1 (최대출력 120W 모노 / 정격 60W RMS, 12V 5A)
 * - 물리 결선: 3.5mm AUX 단자 (오디오 전담) + C타입 (24시간 상시 전원 충전 전담)
 * - 영구불변원칙 6대 규격 전수 준수:
 *   1. 안전볼륨 해제만 적용, 볼륨 수치 강제 고정 전면 금지 (수동 조절권 100% 보장)
 *   2. 충전 중 화면 켜짐 유지 강제 해제 (stay_on_while_plugged_in=0, turnScreenOn 배제)
 *   3. 화면 꺼짐 시간 임의 조작 금지 (사용자 설정 10분/시스템 보존)
 *   4. 하단 1/3 분할 스크롤 금지, 전체 통합 단일 스크롤 레이아웃
 *   5. AMOLED 딥 다크 바탕(#111827) + 명도·채도 높은 진한 초록(#00E676) 텍스트 규격 (동색 겹침 전면 금지)
 *   6. 3.5mm/C타입 물리 분리, 120W 앰프 맞춤 세팅, SoundAlive/HearingAdjust/GOS 동결
 */
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
            if (A30AudioGeneratorService.sIsPlaying) {
                mUiHandler.postDelayed(this, 500); // 2Hz smooth UI refresh while active
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lockscreen bypass for easy access on dedicated generator devices only
        if (!A30VolumeHelper.isPersonalDevice()) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            }
        }

        A30AudioGeneratorService.loadConfig(this);
        A30VolumeHelper.syncTargetFromSystem(this);
        A30VolumeHelper.ensureTargetVolumeIfNeeded(this, false);

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
        titleTv.setText("보르타 레조 A30 v4.2");
        titleTv.setTextSize(22);
        titleTv.setTextColor(Color.parseColor("#00F0FF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        contentPane.addView(titleTv);

        TextView subTitleTv = new TextView(this);
        subTitleTv.setText("갤럭시 A30 + 120W 모노 우퍼(스마토 #1) 맞춤 광역 스윕 주파수 발생기 | Vorta DSP v4.2 (-1.0dBTP 무왜곡)");
        subTitleTv.setTextSize(12);
        subTitleTv.setTextColor(Color.parseColor("#94A3B8"));
        subTitleTv.setPadding(0, (int)(2 * density), 0, (int)(12 * density));
        contentPane.addView(subTitleTv);

        // -------------------------------------------------------------------------
        // 2. Real-time Telemetry Dashboard Card (AMOLED Deep Dark #111827)
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
        mStatusMod.setText("변조: 비활성 (주파수 신호 멈춤) | 120W 모노 우퍼");
        mStatusMod.setTextSize(11);
        mStatusMod.setTextColor(Color.parseColor("#64748B"));
        mStatusCardLayout.addView(mStatusMod);

        mStatusDsp = new TextView(this);
        mStatusDsp.setText(A30VolumeHelper.isPersonalDevice()
                ? "Vorta DSP v4.2: 부스트 +3dB | 트루피크 -1.0dBTP(Peak 29200) | L=R 모노 | 개인기기 보호(무개입)"
                : "Vorta DSP v4.2: 부스트 +3dB | 트루피크 -1.0dBTP(Peak 29200) | L=R 모노");
        mStatusDsp.setTextSize(10);
        mStatusDsp.setTextColor(Color.parseColor("#FFA657"));
        mStatusDsp.setPadding(0, (int)(2 * density), 0, (int)(2 * density));
        mStatusCardLayout.addView(mStatusDsp);

        mStatusKnob = new TextView(this);
        mStatusKnob.setText("앰프 노브: 스마토 앰프 #1 [단일 볼륨 다이얼 1개] 11시~1시 (60~75% 무왜곡 순수 파괴력 구동)");
        mStatusKnob.setTextSize(10);
        mStatusKnob.setTextColor(Color.parseColor("#CBD5E1"));
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
                stopService(new Intent(MainActivity.this, A30AudioGeneratorService.class));
                A30AudioGeneratorService.sIsPlaying = false;
                A30AudioGeneratorService.sModeTitle = "주파수 발진 정지됨";
                mUiHandler.removeCallbacks(mTelemetryUpdater);
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
                "24시간 4대 광역 스윕 & 기준 위상 + 9대 실전 맞춤 + 전술 특수 프리셋",
                "#00F0FF",
                "#082F49"
        );

        // [파트 1] 24시간 4대 시간대별 광역 스윕 스케줄
        addSectionHeaderInsideFrame(mFrameMainContainer, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 광역 스윕 스케줄", "#00F0FF");

        addFullPresetButton(mFrameMainContainer,
                "🌅 1. 아침 (07:00~13:00): 17.3 ~ 19.8 Hz (440초 스윕)",
                "🎯 타격효과: 전정기관·소화관 연동 교란 (스윕범위 17.3~19.8Hz, 주기 440초, 분당 0.34Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.86Hz, 속도 7.03Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 17.30f, 19.80f, 440.0f,
                                true, 0.100f, 7.03f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 스케줄-1] 아침 17.3~19.8Hz 스윕 (FM 10%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "☀️ 2. 오후 (13:00~19:00): 20.7 ~ 27.7 Hz (740초 스윕)",
                "🎯 타격효과: 위장관 기계적 펄스 진동 (스윕범위 20.7~27.7Hz, 주기 740초, 분당 0.57Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 5.70Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 20.70f, 27.70f, 740.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 5.70f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 스케줄-2] 오후 20.7~27.7Hz 스윕 (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌆 3. 저녁 (19:00~01:00): 18.6 ~ 25.1 Hz (900초 스윕)",
                "🎯 타격효과: 광역 카오스 측파대 훑기 (스윕범위 18.6~25.1Hz, 주기 900초, 분당 0.43Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 35.95Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 35.95f,
                                3.0f, 1, 0, false, 15,
                                "[메인 스케줄-3] 저녁 18.6~25.1Hz 스윕 (RM 30%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌙 4. 취침 (01:00~07:00): 2.4 ~ 4.6 Hz (1480초 스윕)",
                "🎯 타격효과: 델타파 무음 위상 간섭 훑기 (스윕범위 2.4~4.6Hz, 주기 1480초, 분당 0.09Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 2.40f, 4.60f, 1480.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 스케줄-4] 취침 2.4~4.6Hz 델타스윕 (무변조)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        // [파트 2] 사용자 행동별 9대 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameMainContainer, "2️⃣ [파트 2] 🎯 사용자 행동별 9대 실전 맞춤 시나리오", "#FACC15");

        addFullPresetButton(mFrameMainContainer,
                "⚡ 1. 상대 공격 즉각 요격: 35.0 ~ 45.0 Hz (120초 스윕)",
                "🎯 타격효과: 바닥 전방위 순간 기계적 충격파 (스윕범위 35.0~45.0Hz, 주기 120초, 분당 5.00Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 4.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 35.00f, 45.00f, 120.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 4.50f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[메인 행동-1] 상대공격 즉각요격 35~45Hz (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔨 2. 벌주기 (보복 타격): 18.6 ~ 25.1 Hz (600초 스윕)",
                "🎯 타격효과: 광역 카오스 RM 측파대 탈출 불가 압박 (스윕범위 18.6~25.1Hz, 주기 600초, 분당 0.65Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 35.95Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 600.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 35.95f,
                                6.0f, 1, 0, false, 15,
                                "[메인 행동-2] 벌주기 18.6~25.1Hz 카오스RM",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛡️ 3. 출근 12시간 무인방어: 4대 시간대 광역 스윕 순환",
                "🎯 타격효과: 07~19시 부재중 자동 순환 발진 (발열 30% 저감) (스윕범위 17.3~27.7Hz, 주기 640초, 분당 0.97Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±2.25Hz, 속도 5.00Hz) | AM ON(심도 15%, 펄스 2.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 17.30f, 27.70f, 640.0f,
                                true, 0.100f, 5.00f, true, 0.150f, 2.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 행동-3] 출근 12시간 무인방어 순환",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🚶 4. 일상 외출 자동 순항: 20.7 ~ 27.7 Hz (740초 스윕)",
                "🎯 타격효과: 2~4시간 외출 시 위장관 기계적 펄스 진동 유지 (스윕범위 20.7~27.7Hz, 주기 680초, 분당 0.62Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.45Hz, 속도 1.20Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 20.70f, 27.70f, 680.0f,
                                true, 0.060f, 1.20f, true, 0.160f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 행동-4] 일상 외출 자동순항 (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏰 5. 초장기 외출 (48h+): 1.0 ~ 4.0 Hz (2400초 초장주기 스윕)",
                "🎯 타격효과: 무인 요새화 및 보이스코일 발열 억제 완전 보호 (스윕범위 1.0~4.0Hz, 주기 2400초, 분당 0.07Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 4.00f, 2400.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-5] 초장기외출 48h+ 초저역 스텔스",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💥 6. 상대 내성 파괴: 18.98 Hz 단독 직타 (FM+RM 복합)",
                "🎯 타격효과: 비동기 변조 도약으로 상대 신경 적응 100% 무효화 (단독 18.98Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.90Hz, 속도 7.03Hz) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 35.95Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.98f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 7.03f, false, 0.000f, 0.00f, true, 0.300f, 35.95f,
                                6.0f, 1, 0, false, 15,
                                "[메인 행동-6] 상대내성파괴 18.98Hz (FM+RM)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🧠 7. 상대 정신 집중 차단: 4.0 ~ 6.2 Hz (480초 스윕)",
                "🎯 타격효과: 세타파 동시 간섭 및 인지 저하 유도 (스윕범위 4.0~6.2Hz, 주기 480초, 분당 0.28Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±0.51Hz, 속도 4.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F472B6",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.00f, 6.20f, 480.0f,
                                true, 0.100f, 4.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 행동-7] 상대집중차단 4.0~6.2Hz (FM 10%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛌 8. 수면 시간 집중 압박: 2.4 ~ 4.6 Hz (1480초 스윕)",
                "🎯 타격효과: 새벽 1~5시 수면 단계 파괴, 211호 0dB 완전 무음 스텔스 (스윕범위 2.4~4.6Hz, 주기 1360초, 분당 0.10Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 2.40f, 4.60f, 1360.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, true, 15,
                                "[메인 행동-8] 수면집중압박 2.4~4.6Hz (무변조)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌡️ 9. 3시간 이상 기기 보호: 22.5 Hz 단독 직타 (AM 펄스)",
                "🎯 타격효과: AM 15% 듀티 사이클 동기화로 앰프/스피커 45도 이하 유지 (단독 22.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 행동-9] 기기보호 22.5Hz (AM 15% 펄스)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        // [파트 3] 상황별 인체 공진 특수 전술 프리셋
        addSectionHeaderInsideFrame(mFrameMainContainer, "3️⃣ [파트 3] 🔬 상황별 인체 공진 특수 전술 프리셋", "#38BDF8");

        addFullPresetButton(mFrameMainContainer,
                "👁️ 1. 안구·시야 공진 직타: 18.98 Hz 단독 직타",
                "🎯 타격효과: 안구 공진(18~19Hz) 비선형 합성 시야 떨림 유도 (단독 18.98Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±1.52Hz, 속도 6.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.98f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.080f, 6.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 전술-1] 안구공진 18.98Hz (FM 8%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🩻 2. 늑골·흉벽 진폭 직타: 24.2 Hz 단독 직타",
                "🎯 타격효과: 늑간근 및 흉벽 진동 기계 해머 직타 (단독 24.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 3.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 24.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 3.80f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 전술-2] 늑골흉벽 24.2Hz (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌀 3. 소화관 연동 공진 직타: 5.5 Hz 단독 직타",
                "🎯 타격효과: 위장관 및 소화기계 초저주파 둔통 유발 (단독 5.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 4.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 4.20f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[메인 전술-3] 소화관공진 5.5Hz (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌌 4. 델타파 초저역 스텔스 직타: 3.3 Hz 단독 직타",
                "🎯 타격효과: 211호 0dB 완전 무음 속 델타파 뇌파 간섭 (단독 3.30Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 3.30f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[메인 전술-4] 델타스텔스 3.3Hz (무변조)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        contentPane.addView(mFrameMainContainer);

        mSplitDivider1 = createFrameDivider(density, "──────── ⚡ 서브1 프레임 (버전2 보고서) ────────");
        contentPane.addView(mSplitDivider1);

        // -------------------------------------------------------------------------
        // ⚡ [서브1 프레임] 통합운용 시나리오 버전2 (총 11개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub1Container = createFrameContainer(
                "⚡ [서브1 프레임] 통합운용 시나리오 버전2 (내성 파괴)",
                "v2 24시간 4대 스케줄 + 7대 비선형 변조 충격 시나리오",
                "#38D39F",
                "#064E3B"
        );

        // [파트 1] v2 24시간 4대 시간대별 마스터 스케줄
        addSectionHeaderInsideFrame(mFrameSub1Container, "1️⃣ [파트 1] 🌅 v2 24시간 4대 시간대별 마스터 스케줄", "#38D39F");

        addFullPresetButton(mFrameSub1Container,
                "🌅 1. 아침 (07:00~13:00): 6.0 ~ 8.5 Hz (380초 스윕)",
                "🎯 타격효과: 소화관 상하 진폭 펄스 훑기 (스윕범위 6.0~8.5Hz, 주기 380초, 분당 0.39Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 4.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38D39F",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.00f, 8.50f, 380.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 4.20f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 스케줄-1] v2 아침 6.0~8.5Hz (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "☀️ 2. 오후 (13:00~19:00): 12.0 ~ 16.5 Hz (520초 스윕)",
                "🎯 타격효과: 상복부·신장 지속 진폭 요동 (스윕범위 12.0~16.5Hz, 주기 520초, 분당 0.52Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.43Hz, 속도 6.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 12.00f, 16.50f, 520.0f,
                                true, 0.100f, 6.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 스케줄-2] v2 오후 12.0~16.5Hz (FM 10%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌆 3. 저녁 (19:00~01:00): 25.0 ~ 32.5 Hz (820초 스윕)",
                "🎯 타격효과: 흉요추 관통 카오스 측파대 (스윕범위 25.0~32.5Hz, 주기 820초, 분당 0.55Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 28.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 25.00f, 32.50f, 820.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 28.50f,
                                3.0f, 1, 0, false, 15,
                                "[서브1 스케줄-3] v2 저녁 25.0~32.5Hz (RM 30%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌙 4. 취침 (01:00~07:00): 1.5 ~ 3.8 Hz (1800초 스윕)",
                "🎯 타격효과: 델타파 무음 위상 간섭 훑기 (스윕범위 1.5~3.8Hz, 주기 1800초, 분당 0.08Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.50f, 3.80f, 1800.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브1 스케줄-4] v2 취침 1.5~3.8Hz 델타스윕",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        // [파트 2] v2 내성 파괴 및 행동별 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameSub1Container, "2️⃣ [파트 2] 🎯 v2 내성 파괴 및 행동별 실전 맞춤", "#FACC15");

        addFullPresetButton(mFrameSub1Container,
                "⚡ 1. 상대 공격 즉각 반격 (v2): 22.0 ~ 30.0 Hz (180초 스윕)",
                "🎯 타격효과: 강력한 하향 충격파 동시 발진 (스윕범위 22.0~30.0Hz, 주기 180초, 분당 2.67Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 5.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 22.00f, 30.00f, 180.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 5.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브1 행동-1] v2 즉각반격 22~30Hz (AM 20%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🔨 2. 벌주기 (미로기관 파쇄): 14.0 ~ 20.0 Hz (540초 스윕)",
                "🎯 타격효과: 안구/전정계 3차원 입체 파쇄 (스윕범위 14.0~20.0Hz, 주기 540초, 분당 0.67Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 35%, 캐리어2 31.20Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 14.00f, 20.00f, 540.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.350f, 31.20f,
                                6.0f, 1, 0, false, 15,
                                "[서브1 행동-2] v2 벌주기 14~20Hz (RM 35%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🛡️ 3. 출근 12시간 무인방어 (v2): v2 시간대 자동 순환",
                "🎯 타격효과: v2 독자 대역 순환으로 v1 적응 상대 신경 피로 극대화 (스윕범위 6.0~32.5Hz, 주기 820초, 분당 1.94Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.93Hz, 속도 4.00Hz) | AM ON(심도 15%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.00f, 32.50f, 820.0f,
                                true, 0.100f, 4.00f, true, 0.150f, 2.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 행동-3] v2 출근 12시간 무인방어 순환",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🚶 4. 일상 외출 자동 순항 (v2): 6.0 ~ 8.5 Hz (380초 스윕)",
                "🎯 타격효과: 소화관 펄스 지속으로 기저 둔통 형성 (스윕범위 6.0~8.5Hz, 주기 420초, 분당 0.36Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.51Hz, 속도 0.85Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.00f, 8.50f, 420.0f,
                                true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[서브1 행동-4] v2 일상 외출 자동순항 (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏰 5. 초장기 외출 (48h+ v2): 1.5 ~ 3.8 Hz (1800초 초장주기)",
                "🎯 타격효과: 3대 기기 발열 억제 프로파일 적용으로 48시간 무인 요새화 (스윕범위 1.5~3.8Hz, 주기 2100초, 분당 0.07Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.50f, 3.80f, 2100.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, true, 15,
                                "[서브1 행동-5] v2 초장기외출 48h+ 요새화",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "💥 6. 상대 내성 완전파괴 (v2): 14.25 Hz 단독 직타 (FM+RM)",
                "🎯 타격효과: 비선형 변조 충격파와 예측 불가능한 타격 (단독 14.25Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.43Hz, 속도 6.50Hz) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 28.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 14.25f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 6.50f, false, 0.000f, 0.00f, true, 0.300f, 28.50f,
                                6.0f, 1, 0, false, 15,
                                "[서브1 행동-6] v2 상대내성완전파괴 14.25Hz",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧠 7. 상대 정신 집중차단 (v2): 8.0 ~ 12.5 Hz (450초 스윕)",
                "🎯 타격효과: 8Hz 집중 공격으로 알파파 파괴, 두통 및 인지 마비 유도 (스윕범위 8.0~12.5Hz, 주기 450초, 분당 0.60Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±12.0%/±1.23Hz, 속도 7.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F472B6",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 8.00f, 12.50f, 450.0f,
                                true, 0.120f, 7.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브1 행동-7] v2 상대집중차단 8~12.5Hz",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        contentPane.addView(mFrameSub1Container);

        mSplitDivider2 = createFrameDivider(density, "──────── 🛡️ 서브2 프레임 (버전3 보고서) ────────");
        contentPane.addView(mSplitDivider2);

        // -------------------------------------------------------------------------
        // 🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (총 10개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub2Container = createFrameContainer(
                "🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (맥놀이 공진)",
                "v3 24시간 4대 스케줄 + 6대 룸모드 정상파 결합 시나리오",
                "#D2A8FF",
                "#3B0764"
        );

        // [파트 1] v3 24시간 4대 시간대별 마스터 스케줄
        addSectionHeaderInsideFrame(mFrameSub2Container, "1️⃣ [파트 1] 🌅 v3 24시간 4대 시간대별 마스터 스케줄", "#D2A8FF");

        addFullPresetButton(mFrameSub2Container,
                "🌅 1. 아침 (07:00~13:00): 9.2 ~ 11.8 Hz (410초 스윕)",
                "🎯 타격효과: 알파파 붕괴 및 시야 집중 저하 (스윕범위 9.2~11.8Hz, 주기 410초, 분당 0.38Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.05Hz, 속도 5.20Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#D2A8FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 9.20f, 11.80f, 410.0f,
                                true, 0.100f, 5.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 스케줄-1] v3 아침 9.2~11.8Hz (FM 10%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "☀️ 2. 오후 (13:00~19:00): 29.5 ~ 34.5 Hz (680초 스윕)",
                "🎯 타격효과: 늑간근/갈비뼈 진폭 펄스 해머 (스윕범위 29.5~34.5Hz, 주기 680초, 분당 0.44Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 3.60Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 29.50f, 34.50f, 680.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 3.60f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 스케줄-2] v3 오후 29.5~34.5Hz (AM 15%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌆 3. 저녁 (19:00~01:00): 14.5 ~ 18.2 Hz (790초 스윕)",
                "🎯 타격효과: 이중 측파대 카오스 맥놀이 (스윕범위 14.5~18.2Hz, 주기 790초, 분당 0.28Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 21.40Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 14.50f, 18.20f, 790.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 21.40f,
                                3.0f, 1, 0, false, 15,
                                "[서브2 스케줄-3] v3 저녁 14.5~18.2Hz (RM 30%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌙 4. 취침 (01:00~07:00): 1.0 ~ 2.2 Hz (1950초 스윕)",
                "🎯 타격효과: 초심층 서파 무음 훑기 (스윕범위 1.0~2.2Hz, 주기 1950초, 분당 0.04Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 2.20f, 1950.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[서브2 스케줄-4] v3 취침 1.0~2.2Hz 서파스윕",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        // [파트 2] v3 맥놀이 공진 및 행동별 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameSub2Container, "2️⃣ [파트 2] 🎯 v3 맥놀이 공진 및 행동별 실전 맞춤", "#FACC15");

        addFullPresetButton(mFrameSub2Container,
                "⚡ 1. 상대 공격 즉각 반격 (v3): 28.0 ~ 36.0 Hz (200초 스윕)",
                "🎯 타격효과: 쿵쾅거림 즉시 룸모드 정상파 타격 및 현기증 유발 (스윕범위 28.0~36.0Hz, 주기 200초, 분당 2.40Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 4.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 28.00f, 36.00f, 200.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 4.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[서브2 행동-1] v3 즉각반격 28~36Hz (AM 20%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🔨 2. 벌주기 (흉곽 슐만 파쇄): 15.0 ~ 21.0 Hz (620초 스윕)",
                "🎯 타격효과: 흉곽 및 척추 집중 타격 슐만 응징 (스윕범위 15.0~21.0Hz, 주기 620초, 분당 0.58Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 35%, 캐리어2 25.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 15.00f, 21.00f, 620.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.350f, 25.50f,
                                6.0f, 1, 0, false, 15,
                                "[서브2 행동-2] v3 벌주기 15~21Hz (RM 35%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🛡️ 3. 출근 12시간 무인방어 (v3): v3 시간대 자동 순환",
                "🎯 타격효과: 3주차 고유 주파수와 3기기 변조 조화로 누적 피로 극대화 (스윕범위 9.2~34.5Hz, 주기 790초, 분당 1.92Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±2.19Hz, 속도 3.50Hz) | AM ON(심도 15%, 펄스 2.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 9.20f, 34.50f, 790.0f,
                                true, 0.100f, 3.50f, true, 0.150f, 2.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 행동-3] v3 출근 12시간 무인방어 순환",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🚶 4. 일상 외출 자동 순항 (v3): 9.2 ~ 11.8 Hz (410초 스윕)",
                "🎯 타격효과: 시야 흐림 및 안와 진동 집중 타겟팅 (스윕범위 9.2~11.8Hz, 주기 460초, 분당 0.34Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.94Hz, 속도 5.20Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 9.20f, 11.80f, 460.0f,
                                true, 0.090f, 5.20f, true, 0.140f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[서브2 행동-4] v3 일상 외출 자동순항 (FM 10%)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏰 5. 초장기 외출 (48h+ v3): 1.0 ~ 2.2 Hz (1950초 초장주기)",
                "🎯 타격효과: 초저역 사인파와 3중 고조파 요새화 장기 무인 방어 (스윕범위 1.0~2.2Hz, 주기 2250초, 분당 0.03Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 2.20f, 2250.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[서브2 행동-5] v3 초장기외출 48h+ 요새화",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "💥 6. 상대 내성 완전파괴 (v3): 16.35 Hz 단독 직타 (FM+RM)",
                "🎯 타격효과: 광대역 카오스 RM 측파대와 주파수 도약의 복합 조화 (단독 16.35Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±10.0%/±1.64Hz, 속도 5.20Hz) | AM OFF(0%) | RM ON(심도 30%, 캐리어2 21.40Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.35f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.100f, 5.20f, false, 0.000f, 0.00f, true, 0.300f, 21.40f,
                                6.0f, 1, 0, false, 15,
                                "[서브2 행동-6] v3 상대내성완전파괴 16.35Hz",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
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
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 1.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 52.00f, 55.00f, 180.0f,
                                true, 0.030f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[구버전 스케줄-1] 아침 52.0~55.0Hz 하이브리드 (180s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "☀️ 2. 오후 (13:00~19:00 구버전 정제): 6.7 ~ 9.0 Hz (490초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕2 소화기·복부 연동 및 알파파 하단 교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.55Hz, 속도 0.85Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.70f, 9.00f, 490.0f,
                                true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 스케줄-2] 오후 6.7~9.0Hz 소화기·복부 (490s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌆 3. 저녁 (19:00~01:00 구버전 정제): 3.3 ~ 7.3 Hz (640초 스윕)",
                "🎯 타격효과: 구버전 앱 슬롯2 실측값(10분40초 루프) · 델타-세타 뇌파 동조 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.45Hz, 속도 0.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 3.30f, 7.30f, 640.0f,
                                true, 0.085f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[구버전 스케줄-3] 저녁 3.3~7.3Hz 슬롯2 뇌파차단 (640s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌙 4. 취침 (01:00~07:00 구버전 정제): 12.0 ~ 24.5 Hz (6580초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕8 야간 서파·장간막 진동 분쇄 초장주기(109분) 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 12.00f, 24.50f, 6580.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[구버전 스케줄-4] 취침 12.0~24.5Hz 야간분쇄 (6580s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addSectionHeaderInsideFrame(mFrameLegacyContainer, "2️⃣ [파트 2] 🎯 구버전 고유 스윕 & 120mm 슬래브 1·2·3차 공진 (초저주파 → 중저주파 순)", "#FACC15");
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 1. [초저주파 구버전-스윕7] 1.0 ~ 4.0 Hz (2880초/48분 스윕) — 델타 서파 무인스캔",
                "🎯 타격효과: 구버전 초안 스윕7 원전(48분 루프) · 델타 서파 위상 스캔 및 심부 수면 방해",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.12Hz, 속도 0.30Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 4.00f, 2880.0f,
                                true, 0.050f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 전술-1] 초저주파 1.0~4.0Hz 초장주기 (2880s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 2. [초저주파 구버전-스윕1] 4.0 ~ 6.2 Hz (290초 스윕) — 척추·전정계 수직 바운싱",
                "🎯 타격효과: 구버전 초안 스윕1 원전(290초 주기) · 척추·전정계 수직 바운싱 및 내장 흔들림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.46Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.00f, 6.20f, 290.0f,
                                true, 0.090f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[구버전 전술-2] 초저주파 4.0~6.2Hz 전정계 (290s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 3. [초저주파 슬래브 1차모드] 18.5 Hz 단독 — 120mm 슬래브 1차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 1차 지배공진(18.5Hz) 직타 · 안구·두개골 동시 울림",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[구버전 전술-3] 슬래브 1차지배공진 18.5Hz 직타",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 4. [중저주파 구버전-프리셋4] 18.6 ~ 25.1 Hz (900초 스윕, AM 0.50Hz) — 심야 역제압",
                "🎯 타격효과: 구버전 프리셋4 심야 내장진동(20~22Hz 장간막) 역제압 호흡주기 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[구버전 전술-4] 심야역제압 18.6~25.1Hz (AM 0.5Hz)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 5. [중저주파 구버전-프리셋3] 18.6 ~ 25.1 Hz (900초 스윕, RM 1.80Hz) — 외출 카오스",
                "🎯 타격효과: 구버전 프리셋3 장시간 외출용 카오스 비트 원전(RM 1.80Hz 회전맥놀이)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 28%, 캐리어2 1.80Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.280f, 1.80f,
                                3.0f, 1, 0, false, 15,
                                "[구버전 전술-5] 외출카오스 18.6~25.1Hz (RM 1.8Hz)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 6. [중저주파 구버전-프리셋5] 19.5 Hz + 55.0 Hz 복합 — 안구·흉벽 동시 응징 해머",
                "🎯 타격효과: 구버전 프리셋5 즉각 응징 해머 펄스(19.5Hz 안구공진 + 55.0Hz 슬래브 표면)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 4.10Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.50f, 55.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 4.10f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, false, 15,
                                "[구버전 전술-6] 즉각응징 19.5+55.0Hz 복합해머",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 7. [중저주파 슬래브 2차모드] 32.4 Hz 단독 — 120mm 슬래브 2차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 2차 지배공진(32.4Hz) 직타 · 흉복부 관통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.94Hz, 속도 1.90Hz) | AM ON(심도 15%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 32.40f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.90f, true, 0.150f, 3.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[구버전 전술-7] 슬래브 2차지배공진 32.4Hz 직타",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 8. [중저주파 슬래브 3차모드] 54.8 Hz 단독 — 120mm 슬래브 3차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 3차 지배공진(54.8Hz) 직타 · 바닥 표면 진동 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.8%/±1.53Hz, 속도 1.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.028f, 1.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[구버전 전술-8] 슬래브 3차지배공진 54.8Hz 직타",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
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
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.10Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +9.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 2.50f, 300.0f,
                                true, 0.060f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                9.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕01] 1.0~2.5Hz 이석기관·수면후불쾌감 (300s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 02] 2.5 ~ 4.5 Hz (360초 스윕) — 델타파 차단·심층 두통 유도",
                "🎯 타격효과: [조합1 반응1] 델타파(1~4Hz) 숙면 뇌파 동조 차단 · 두개내압 미세 맥동 · 심층 두통 및 수면단계 파괴",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.28Hz, 속도 0.45Hz) | AM ON(심도 12%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 2.50f, 4.50f, 360.0f,
                                true, 0.080f, 0.45f, true, 0.120f, 1.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕02] 2.5~4.5Hz 델타파차단·심층두통 (360s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 03] 4.5 ~ 7.0 Hz (420초 스윕) — 장간막 공진·복통·수면리듬 분리",
                "🎯 타격효과: [조합1 스윕1하단·반응3] ISO 2631-1 복부 내장 수직 기본공진(4.5~7Hz) · 복통 유발 · 세타파 수면리듬 분리",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 3.90Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.50f, 7.00f, 420.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 3.90f, false, 0.000f, 0.00f,
                                6.0f, 1, 0, true, 15,
                                "[기초-초저주파스윕03] 4.5~7.0Hz 복통유발·수면리듬분리 (420s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 04 ★1차주력] 7.0 ~ 11.0 Hz (480초 스윕) — 전정·뇌간·자율신경·알파파",
                "🎯 타격효과: [조합1·3(★5)·4 통합] 전정·뇌간(7Hz)~슈만(7.83Hz)~자율신경·알파파(10.5~11Hz) 현기증·오심·맥박불안정·멍함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.77Hz, 속도 0.78Hz) | AM ON(심도 15%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FFD700",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 7.00f, 11.00f, 480.0f,
                                true, 0.085f, 0.78f, true, 0.150f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파스윕04] 7.0~11.0Hz 전정·자율신경·현기증 (480s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 05 ★1차주력] 11.0 ~ 16.5 Hz (500초 스윕) — 뇌파간섭·인후두·장연동 교란",
                "🎯 타격효과: [조합1 스윕2·6하단 + 조합3(★5)] SMR·베타파 뇌파간섭 · 인후두·기도 압박 · 장연동 설사/복통 및 내장 울림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 11.00f, 16.50f, 500.0f,
                                true, 0.070f, 1.25f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 0, false, 15,
                                "[기초-초저주파스윕05] 11.0~16.5Hz 뇌파·장연동·내장울림 (500s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 06 ★1차주력] 16.5 ~ 21.5 Hz (450초 스윕) — 두개골·시신경·귀울림(이명)",
                "🎯 타격효과: [조합1 반응4 + 조합3(★5) + 조합4] 두개골·전두엽(18Hz)·시신경·안구(18~21Hz) 공진 · 귀울림(이명)·두통·안구압박",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.71Hz, 속도 6.20Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM ON(심도 22%, 캐리어2 18.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 16.50f, 21.50f, 450.0f,
                                true, 0.090f, 6.20f, true, 0.160f, 4.00f, true, 0.220f, 18.50f,
                                3.0f, 1, 0, false, 15,
                                "[기초-초저주파스윕06] 16.5~21.5Hz 두개골·시신경·이명 (450s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "1️⃣-B [중저주파 스윕: 21.5 ~ 55.0 Hz] 내이·흉곽·복부·골반·고막·슬래브 공진 (4단계 무중복 오름차순)", "#FACC15");
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 07 ★2차보조] 21.5 ~ 27.0 Hz (500초 스윕) — 내이·석고보드·흉곽·대장",
                "🎯 타격효과: [조합3(★3)하단 + 조합4] 내이 방향감각 상실(22Hz) · 석고보드 한계·흉곽압박(24Hz) · 대장진동·하체무력(25Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.33Hz, 속도 1.15Hz) | AM ON(심도 18%, 펄스 3.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.50f, 27.00f, 500.0f,
                                true, 0.055f, 1.15f, true, 0.180f, 3.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파스윕07] 21.5~27.0Hz 내이·흉곽·대장진동 (500s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 08 ★2차보조] 27.0 ~ 36.0 Hz (520초 스윕) — 골반·하복부·흉곽·위장·간",
                "🎯 타격효과: [조합1 스윕3 + 조합3(★3)상단 + 조합4] 골반·하복부 둔통(28Hz) · 흉곽·위장·간 묵직한 타격(31~36Hz) 주간 집중교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.89Hz, 속도 1.80Hz) | AM ON(심도 17%, 펄스 3.20Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 27.00f, 36.00f, 520.0f,
                                true, 0.060f, 1.80f, true, 0.170f, 3.20f, true, 0.180f, 13.50f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파스윕08] 27.0~36.0Hz 골반·흉곽·간 집중교란 (520s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 09 ★중광역] 36.0 ~ 46.0 Hz (580초 스윕) — 대뇌공포·귀막힘·집중력저하",
                "🎯 타격효과: [조합1 스윕4·7하단 + 조합3 중광역] 반복 불쾌울림·대뇌 공포감(40Hz) · 귀막힘·고막압박·집중력 저하(38~46Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.84Hz, 속도 1.40Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 36.00f, 46.00f, 580.0f,
                                true, 0.045f, 1.40f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파스윕09] 36.0~46.0Hz 대뇌공포·귀막힘·고막압박 (580s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 10 ★3차확산] 46.0 ~ 55.0 Hz (650초 스윕) — 귀먹먹함·콘크리트 슬래브 공진",
                "🎯 타격효과: [조합1 스윕4상단 + 조합3(★2) + 조합4] 고출력 귀 먹먹함(46~51Hz) ~ 콘크리트 벽면·슬래브 공간공진 최적점(55Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.8%/±1.92Hz, 속도 1.15Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM ON(심도 20%, 캐리어2 11.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 46.00f, 55.00f, 650.0f,
                                true, 0.038f, 1.15f, true, 0.120f, 2.40f, true, 0.200f, 11.50f,
                                0.0f, 0, 0, false, 15,
                                "[기초-중저주파스윕10] 46.0~55.0Hz 귀먹먹함·슬래브공간공진 (650s)",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-A [초저주파 단독: 7.0 ~ 18.0 Hz] 뇌간·자율신경·두개골·전두엽 직타 (4종 무중복 오름차순)", "#F472B6");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 01 ★1차주력] 7.0 Hz (+기저 3.5Hz) — 구조물 심부공진 & 전정기관·뇌간",
                "🎯 타격효과: [조합2·3·4] 구조물 심부공진 · 전정기관·뇌간 타격 · 현기증·방향감각 혼란·기저 스트레스 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.5%/±0.53Hz, 속도 0.55Hz) | AM ON(심도 12%, 펄스 3.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 3.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.075f, 0.55f, true, 0.120f, 3.80f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파단독01] 7.0Hz(+3.5Hz) 심부공진·전정뇌간 현기증",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 02 ★1차주력] 10.5 Hz (+기저 5.25Hz) — 심박변동(HRV) & 자율신경 교란",
                "🎯 타격효과: [조합2·3·4] 심박변동(HRV)·자율신경계 직접 교란 · 원인불명 메스꺼움·불안정한 맥박·알파파 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.89Hz, 속도 0.95Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 5.25f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 0.95f, true, 0.140f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 0, true, 15,
                                "[기초-초저주파단독02] 10.5Hz(+5.25Hz) 자율신경·심박교란 메스꺼움",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 03 ★1차주력] 16.0 Hz (+기저 8.0Hz) — 뇌파간섭·위상현기증·장연동 복통",
                "🎯 타격효과: [조합2·3·4] 인체 임계경계(뇌파간섭 수면방해 · 내이-시각 위상어긋남 현기증 · 장연동 설사/복통 · 두개골 공진)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 20%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 8.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.25f, true, 0.200f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 0, false, 15,
                                "[기초-초저주파단독03] 16.0Hz(+8.0Hz) 위상현기증·장연동복통",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 04 ★1차주력] 18.0 Hz (+기저 9.0Hz) — 벽 전체 불쾌공명 & 전두엽·삼차신경",
                "🎯 타격효과: [조합2·3·4] 벽 전체 불쾌한 울림 · 두개골 내부 공진 · 극심한 두통 · 삼차신경 자극 · 만성 무기력",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±1.53Hz, 속도 6.30Hz) | AM OFF(0%) | RM ON(심도 22%, 캐리어2 18.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.00f, 9.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 6.30f, false, 0.000f, 0.00f, true, 0.220f, 18.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-초저주파단독04] 18.0Hz(+9.0Hz) 벽불쾌공명·전두엽두통",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 12시(70%) | 트랜스2(LP-168S): SUB 3시반(75%) · X-OVER 60Hz(10시) · BASS +2dB(1시) · MAIN 12시");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-B [중저주파 단독: 21.0 ~ 55.0 Hz] 시신경·내이·흉곽·복부·골반·슬래브 직타 (8종 무중복 오름차순)", "#34D399");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 05 ★1차주력] 21.0 Hz (+기저 10.5Hz) — 시신경·안구 공진 & 정신적 긴장",
                "🎯 타격효과: [조합2·3·4] NASA 안구·시신경 공진(21Hz) · 안구 압박·초점 저하 · 정신적 긴장 및 고강도 스트레스",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.16Hz, 속도 2.10Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 21.00f, 10.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.055f, 2.10f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독05] 21.0Hz(+10.5Hz) 시신경안구압박·정신긴장",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 06 ★2차보조] 22.0 Hz (+기저 11.0Hz) — 귀 안쪽·내이 방향감각 상실",
                "🎯 타격효과: [조합3·4] 귀 안쪽·내이 미로기관 집중 공진 · 방향감각 상실 · 자세 불균형 및 어지러움",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.98Hz, 속도 3.40Hz) | AM ON(심도 14%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 11.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.090f, 3.40f, true, 0.140f, 2.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독06] 22.0Hz(+11.0Hz) 내이 방향감각상실·불균형",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 07 ★2차보조] 24.0 Hz (+기저 12.0Hz) — 석고보드 한계 & 흉곽·폐 압박",
                "🎯 타격효과: [조합2·3·4] 석고보드 벽체 공진 한계점 · 공간 전체 압박감 · 흉곽·폐 진동 및 가슴 답답함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.08Hz, 속도 1.15Hz) | AM ON(심도 16%, 펄스 3.70Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 24.00f, 12.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.045f, 1.15f, true, 0.160f, 3.70f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독07] 24.0Hz(+12.0Hz) 석고보드한계·흉곽폐압박",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 08 ★2차보조] 25.0 Hz (+기저 12.5Hz) — 대장·하복부 장진동 & 하체 무력",
                "🎯 타격효과: [조합2·3·4] 인체 7~25Hz 핵심 영향권 상단 마감 · 대장·하복부 진동 · 복부 팽만 및 하체 무력감",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 25.00f, 12.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독08] 25.0Hz(+12.5Hz) 대장하복부진동·하체무력",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 09 ★2차보조] 28.0 Hz (+기저 14.0Hz) — 골반·장기 하부 무거움·둔통",
                "🎯 타격효과: [조합3·4] 골반저근·장기 하부 직타 · 하복부 묵직한 무거움 및 지속적 복부 둔통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.40Hz, 속도 0.90Hz) | AM ON(심도 17%, 펄스 3.15Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#60A5FA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 14.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.90f, true, 0.170f, 3.15f, false, 0.000f, 0.00f,
                                3.0f, 0, 0, false, 15,
                                "[기초-중저주파단독09] 28.0Hz(+14.0Hz) 골반장기하부 무거움·둔통",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 10 ★2차보조] 31.0 Hz (+기저 15.5Hz) — 흉곽·위장·간 묵직한 타격",
                "🎯 타격효과: [조합2·3·4] 흉곽 진동 및 상복부 장기(위장·간) 묵직한 타격감 · 심부 내장 피로 누적",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 2.75Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 31.00f, 15.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 2.75f, true, 0.180f, 13.50f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독10] 31.0Hz(+15.5Hz) 흉곽·위장·간 묵직한타격",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 11 ★3차확산] 40.0 Hz (+기저 20.0Hz) — 반복 불쾌울림 & 대뇌 공포감",
                "🎯 타격효과: [조합2·3·4] 타격성 강한 반복 불쾌울림 · 대뇌 감마파 교란 · 원인 모를 공포감 및 정신 혼미",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 40.00f, 20.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 0, false, 15,
                                "[기초-중저주파단독11] 40.0Hz(+20.0Hz) 반복불쾌울림·대뇌공포혼미",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 12 ★3차확산] 55.0 Hz (+기저 27.5Hz) — 콘크리트 벽면·슬래브 공진 최적점",
                "🎯 타격효과: [조합2·3·4] 아파트 콘크리트 벽면·슬래브 공진 최적점 · 체감 진동 및 구조물 전달력 최강 마감",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.2%/±1.76Hz, 속도 1.35Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: L=R 동위상 모노(120W) | 폰볼륨: 12/15단(80%·레벨링)\n🔊 앰프설정 [스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 27.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.032f, 1.35f, true, 0.120f, 2.40f, false, 0.000f, 0.00f,
                                0.0f, 0, 0, false, 15,
                                "[기초-중저주파단독12] 55.0Hz(+27.5Hz) 콘크리트벽면·슬래브 최적점",
                                "[스마토#1 120W + 트랜스2 68W]: 스마토 노브 11~1시(65~75%) | 트랜스2(LP-168S): SUB 4시(80%) · X-OVER 80Hz(12시) · BASS 0dB(12시) · MAIN 12시");
                    }
                });
        contentPane.addView(mFrameBasicContainer);



        scroll.addView(contentPane);
        setContentView(scroll);

        switchSplitView(0); // Show ALL by default
        syncTelemetryUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncTelemetryUi();
        if (A30AudioGeneratorService.sIsPlaying) {
            mUiHandler.post(mTelemetryUpdater);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mTelemetryUpdater);
    }

    // -------------------------------------------------------------------------
    // 🎛️ Telemetry Sync (Strict 11:1+ Contrast & Zero Color-Clash)
    // -------------------------------------------------------------------------
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
        boolean isPlaying = A30AudioGeneratorService.sIsPlaying;
        float density = getResources().getDisplayMetrics().density;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111827")); // Pure AMOLED Deep Slate Background
        bg.setCornerRadius(8 * density);

        if (isPlaying) {
            bg.setStroke((int)(2 * density), Color.parseColor("#39FF14")); // Vivid Green Stroke
            mStatusCardLayout.setBackground(bg);

            if (mStatusTrafficLight != null) {
                GradientDrawable greenDot = new GradientDrawable();
                greenDot.setShape(GradientDrawable.OVAL);
                greenDot.setColor(Color.parseColor("#00E676")); // Neon Green Traffic Light
                mStatusTrafficLight.setImageDrawable(greenDot);
            }
            setTextIfChanged(mStatusMode, "상태: [발진 중] " + A30AudioGeneratorService.sActivePart, Color.parseColor("#00E676"));

            if (mStatusScenario != null) {
                setTextIfChanged(mStatusScenario, "시나리오: " + A30AudioGeneratorService.sScenarioName, Color.parseColor("#00F0FF")); // Electric Cyan
            }

            if (A30AudioGeneratorService.sIsSweepMode) {
                mStatusFreq.setText(String.format("발진: %.2f Hz (스윕: %.1f~%.1fHz, %.0fs 주기)",
                        A30AudioGeneratorService.sInstantFreq,
                        A30AudioGeneratorService.sSweepMin,
                        A30AudioGeneratorService.sSweepMax,
                        A30AudioGeneratorService.sSweepPeriodSec));
            } else {
                mStatusFreq.setText(String.format("발진: %.2f Hz (단독 고정 주파수 직타)",
                        A30AudioGeneratorService.sTargetFreq));
            }
            mStatusFreq.setTextColor(Color.parseColor("#FFD700")); // Golden Yellow for Frequency

            String fmS = A30AudioGeneratorService.sFmEnabled ? String.format("ON(±%.1f%%·%.2fHz)", A30AudioGeneratorService.sFmDepth * 100f, A30AudioGeneratorService.sFmRateHz) : "OFF(0%)";
            String amS = A30AudioGeneratorService.sAmEnabled ? String.format("ON(%.0f%%·%.2fHz)", A30AudioGeneratorService.sAmDepth * 100f, A30AudioGeneratorService.sAmRateHz) : "OFF(0%)";
            String rmS = A30AudioGeneratorService.sRmEnabled ? String.format("ON(%.0f%%·%.2fHz)", A30AudioGeneratorService.sRmDepth * 100f, A30AudioGeneratorService.sRmRateHz) : "OFF(0%)";
            setTextIfChanged(mStatusMod, String.format("〰️ 변조(FM·AM·RM): FM %s | AM %s | RM %s", fmS, amS, rmS), Color.parseColor("#F472B6"));

            String guardSuffix = A30VolumeHelper.isPersonalDevice() ? " | 개인기기 보호(무개입)" : "";
            setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | 부스트 +%.1fdB | %s | 2차배음 %s | L=R 모노%s",
                    A30AudioGeneratorService.sBassBoostDb,
                    (A30AudioGeneratorService.sLimiterMode == 1 ? "Tanh 새추레이터" : "트루피크 -1.0dBTP(Peak 29200)"),
                    (A30AudioGeneratorService.sHarmonicExciter ? "ON" : "OFF"),
                    guardSuffix), Color.parseColor("#FFA657"));

            setTextIfChanged(mStatusKnob, "🔊 앰프설정: " + A30AudioGeneratorService.sKnobGuide, Color.parseColor("#34D399"));

        } else {
            bg.setStroke((int)(2 * density), Color.parseColor("#EF4444")); // Crimson Red Stroke
            mStatusCardLayout.setBackground(bg);

            if (mStatusTrafficLight != null) {
                GradientDrawable redDot = new GradientDrawable();
                redDot.setShape(GradientDrawable.OVAL);
                redDot.setColor(Color.parseColor("#FF3366")); // Neon Red Traffic Light
                mStatusTrafficLight.setImageDrawable(redDot);
            }
            setTextIfChanged(mStatusMode, "상태: [작동 중지] 멈춤 (대기 상태)", Color.parseColor("#FF3366"));

            setTextIfChanged(mStatusFreq, "발진: 0.00 Hz (출력 차단 / MUTE)", Color.parseColor("#94A3B8"));

            setTextIfChanged(mStatusMod, "변조: 비활성 (주파수 신호 멈춤) | 120W 모노 우퍼", Color.parseColor("#64748B"));

            setTextIfChanged(mStatusDsp, A30VolumeHelper.isPersonalDevice()
                    ? "Vorta DSP v4.2: 부스트 +3dB | 트루피크 -1.0dBTP(Peak 29200) | L=R 모노 | 개인기기 보호(무개입)"
                    : "Vorta DSP v4.2: 부스트 +3dB | 트루피크 -1.0dBTP(Peak 29200) | L=R 모노", Color.parseColor("#64748B"));

            setTextIfChanged(mStatusKnob, "앰프 노브: 스마토 앰프 #1 [단일 볼륨 다이얼 1개] 11시~1시 (60~75% 무왜곡 순수 파괴력 구동)", Color.parseColor("#94A3B8"));
        }
    }

    // -------------------------------------------------------------------------
    // 🎛️ Split-View Frame Visibility Switcher
    // -------------------------------------------------------------------------
    private void switchSplitView(int mode) {
        mCurrentSplitMode = mode;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 5; i++) {
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(6 * density);
            if (i == mode) {
                btnBg.setColor(Color.parseColor("#1E293B"));
                btnBg.setStroke((int)(1.5f * density), Color.parseColor("#00F0FF"));
                mSplitViewButtons[i].setTextColor(Color.parseColor("#00F0FF"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                btnBg.setColor(Color.parseColor("#0F172A"));
                btnBg.setStroke((int)(1 * density), Color.parseColor("#334155"));
                mSplitViewButtons[i].setTextColor(Color.parseColor("#94A3B8"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT);
            }
            mSplitViewButtons[i].setBackground(btnBg);
        }

        if (mSplitDivider1 != null) mSplitDivider1.setVisibility(View.GONE);
        if (mSplitDivider2 != null) mSplitDivider2.setVisibility(View.GONE);
        mFrameMainContainer.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        mFrameSub1Container.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        mFrameSub2Container.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        mFrameLegacyContainer.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        mFrameBasicContainer.setVisibility(mode == 4 ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------------
    // 🎛️ Audio Generator Service Preset Dispatcher
    // -------------------------------------------------------------------------
    private void startPreset(boolean isSweep, float freq, float subFreq,
                             float sweepMin, float sweepMax, float sweepSec,
                             boolean fmOn, float fmDepth, float fmRate,
                             boolean amOn, float amDepth, float amRate,
                             boolean rmOn, float rmDepth, float rmRate,
                             float bassBoost, int limiterMode, int channelMode, boolean exciter,
                             int targetVol, String title, String knob) {

        Intent it = new Intent(this, A30AudioGeneratorService.class);
        it.putExtra("action", "start");
        it.putExtra("sweep", isSweep);
        it.putExtra("freq", freq);
        it.putExtra("sub_freq", subFreq);
        it.putExtra("sweep_min", sweepMin);
        it.putExtra("sweep_max", sweepMax);
        it.putExtra("sweep_sec", sweepSec);

        it.putExtra("fm_on", fmOn);
        it.putExtra("fm_depth", fmDepth);
        it.putExtra("fm_rate", fmRate);

        it.putExtra("am_on", amOn);
        it.putExtra("am_depth", amDepth);
        it.putExtra("am_rate", amRate);

        it.putExtra("rm_on", rmOn);
        it.putExtra("rm_depth", rmDepth);
        it.putExtra("rm_rate", rmRate);

        it.putExtra("bass_boost_db", bassBoost);
        it.putExtra("limiter_mode", limiterMode);
        it.putExtra("channel_mode", channelMode);
        it.putExtra("harmonic_exciter", exciter);
        it.putExtra("auto_preset_dsp", false);
        it.putExtra("vol_step", targetVol);

        // Automatically determine active part from current tab or title
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
        A30AudioGeneratorService.sActivePart = partTag;
        A30AudioGeneratorService.sScenarioName = title;

        it.putExtra("title", title);
        it.putExtra("part", partTag);
        it.putExtra("scenario", title);
        it.putExtra("knob", knob);

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(it);
        } else {
            startService(it);
        }
        A30AudioGeneratorService.sIsPlaying = true;
        A30AudioGeneratorService.sModeTitle = title;
        A30AudioGeneratorService.sScenarioName = title;
        A30AudioGeneratorService.sKnobGuide = knob;
        A30AudioGeneratorService.sIsSweepMode = isSweep;
        A30AudioGeneratorService.sTargetFreq = freq;
        A30AudioGeneratorService.sSubFreq = subFreq;
        A30AudioGeneratorService.sSweepMin = sweepMin;
        A30AudioGeneratorService.sSweepMax = sweepMax;
        A30AudioGeneratorService.sSweepPeriodSec = sweepSec;
        A30AudioGeneratorService.sInstantFreq = isSweep ? sweepMin : freq;
        A30AudioGeneratorService.sFmEnabled = fmOn;
        A30AudioGeneratorService.sFmDepth = fmDepth;
        A30AudioGeneratorService.sFmRateHz = fmRate;
        A30AudioGeneratorService.sAmEnabled = amOn;
        A30AudioGeneratorService.sAmDepth = amDepth;
        A30AudioGeneratorService.sAmRateHz = amRate;
        A30AudioGeneratorService.sRmEnabled = rmOn;
        A30AudioGeneratorService.sRmDepth = rmDepth;
        A30AudioGeneratorService.sRmRateHz = rmRate;
        A30AudioGeneratorService.sBassBoostDb = bassBoost;
        A30AudioGeneratorService.sLimiterMode = limiterMode;
        A30AudioGeneratorService.sChannelMode = channelMode;
        A30AudioGeneratorService.sHarmonicExciter = exciter;
        A30VolumeHelper.syncTargetFromSystem(this);
        mUiHandler.removeCallbacks(mTelemetryUpdater);
        mUiHandler.post(mTelemetryUpdater);
        Toast.makeText(this, title + " 발진 시작", Toast.LENGTH_SHORT).show();
        syncTelemetryUi();
    }

    // -------------------------------------------------------------------------
    // 🎛️ UI View Builders & Helpers
    // -------------------------------------------------------------------------
    private TextView createSplitTabButton(LinearLayout parent, String text, float weight, View.OnClickListener listener) {
        float density = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(11.5f);
        btn.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins((int)(2 * density), 0, (int)(2 * density), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        parent.addView(btn);
        return btn;
    }

    private LinearLayout createFrameContainer(String title, String subtitle, String accentColor, String bgDarkColor) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(10 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(accentColor));
        frame.setBackground(bg);
        frame.setPadding((int)(12 * density), (int)(14 * density), (int)(12 * density), (int)(14 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(16 * density));
        frame.setLayoutParams(lp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(Color.parseColor(accentColor));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        frame.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11);
        tvSub.setTextColor(Color.parseColor("#94A3B8"));
        tvSub.setPadding(0, (int)(2 * density), 0, (int)(10 * density));
        frame.addView(tvSub);

        return frame;
    }

    private LinearLayout createFrameDivider(float density, String text) {
        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.VERTICAL);
        divider.setPadding(0, (int)(8 * density), 0, (int)(14 * density));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#475569"));
        tv.setGravity(Gravity.CENTER);
        divider.addView(tv);

        return divider;
    }

    private void addSectionHeaderInsideFrame(LinearLayout frame, String title, String color) {
        float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor(color));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int)(8 * density), 0, (int)(6 * density));
        frame.addView(tv);
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
}
