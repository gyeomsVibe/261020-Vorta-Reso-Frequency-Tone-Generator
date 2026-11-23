package com.vorta.s6frequency;

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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // Pinned Status Telemetry
    private LinearLayout mStatusCardLayout;
    private ImageView mStatusTrafficLight;
    private TextView mStatusMode;
    private TextView mStatusScenario;
    private TextView mStatusFreq;
    private TextView mStatusMod;
    private TextView mStatusDsp;
    private TextView mStatusKnob;

    // Split Screen Layout Containers
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
            if (S6AudioGeneratorService.sIsPlaying) {
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

        // Load DSP Config & unlock safe volume without locking volume level
        S6AudioGeneratorService.loadConfig(this);
        S6VolumeHelper.syncTargetFromSystem(this);
        S6VolumeHelper.ensureTargetVolumeIfNeeded(this, false);

        float density = getResources().getDisplayMetrics().density;

        // Unified Full-Screen ScrollView: The entire screen scrolls naturally with wide, expansive visibility!
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0A0E17"));
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout contentPane = new LinearLayout(this);
        contentPane.setOrientation(LinearLayout.VERTICAL);
        contentPane.setPadding((int)(14 * density), (int)(14 * density), (int)(14 * density), (int)(28 * density));

        // Header Title 1 (H1) & Subtitle
        TextView titleTv = new TextView(this);
        titleTv.setText("보르타 레조 S6 v4.2");
        titleTv.setTextSize(20);
        titleTv.setTextColor(Color.parseColor("#00F0FF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        contentPane.addView(titleTv);

        TextView subTitleTv = new TextView(this);
        subTitleTv.setText("갤럭시 S6 + 트랜스2(68W BTL) 맞춤 주파수 발생기 | Vorta DSP v4.2 (-1.0dBTP·안전볼륨 영구해제)");
        subTitleTv.setTextSize(11);
        subTitleTv.setTextColor(Color.parseColor("#E2E8F0"));
        subTitleTv.setPadding(0, (int)(2 * density), 0, (int)(8 * density));
        contentPane.addView(subTitleTv);

        // Realtime Telemetry Status Card
        LinearLayout statusCard = new LinearLayout(this);
        mStatusCardLayout = statusCard;
        statusCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable scBg = new GradientDrawable();
        scBg.setColor(Color.parseColor("#1E293B"));
        scBg.setCornerRadius(8 * density);
        scBg.setStroke((int)(1.5f * density), Color.parseColor("#EF4444"));
        statusCard.setBackground(scBg);
        statusCard.setPadding((int)(14 * density), (int)(10 * density), (int)(14 * density), (int)(10 * density));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scLp.setMargins(0, 0, 0, (int)(8 * density));
        statusCard.setLayoutParams(scLp);

        // Principle 12: Android 7 renders emoji as tofu, so the traffic light is a 16dp oval drawable.
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
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
        statusCard.addView(statusRow);

        mStatusScenario = new TextView(this);
        mStatusScenario.setText("시나리오: 대기 중 (선택 없음)");
        mStatusScenario.setTextSize(13);
        mStatusScenario.setTextColor(Color.parseColor("#64748B"));
        mStatusScenario.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusScenario.setPadding(0, (int)(4 * density), 0, 0);
        statusCard.addView(mStatusScenario);

        mStatusFreq = new TextView(this);
        mStatusFreq.setText("발진: 0.00 Hz (출력 차단 / MUTE)");
        mStatusFreq.setTextSize(12);
        mStatusFreq.setTextColor(Color.parseColor("#94A3B8"));
        mStatusFreq.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusFreq.setPadding(0, (int)(2 * density), 0, (int)(1 * density));
        statusCard.addView(mStatusFreq);

        mStatusMod = new TextView(this);
        mStatusMod.setText("변조: 비활성 (주파수 신호 멈춤) | BTL 대기");
        mStatusMod.setTextSize(11);
        mStatusMod.setTextColor(Color.parseColor("#64748B"));
        mStatusMod.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(mStatusMod);

        mStatusDsp = new TextView(this);
        mStatusDsp.setText(S6VolumeHelper.isPersonalDevice()
                ? "Vorta DSP v4.2: -1.0dBTP(Peak 29200) | 개인기기 보호 모드(음량·음장 무개입)"
                : "Vorta DSP v4.2: 자유볼륨 제어(0~15단) | -1.0dBTP(Peak 29200) | OS 안전볼륨 해제·AUX 가드 ON");
        mStatusDsp.setTextSize(10);
        mStatusDsp.setTextColor(Color.parseColor("#FFA657"));
        mStatusDsp.setPadding(0, (int)(2 * density), 0, (int)(1 * density));
        statusCard.addView(mStatusDsp);

        mStatusKnob = new TextView(this);
        mStatusKnob.setText("앰프 노브: Lepy LP-168S [5개 다이얼] SUB VOL 3~5시 (정격 68W 골전도 풀파워) | X-OVER 80Hz | BASS 0dB");
        mStatusKnob.setTextSize(10);
        mStatusKnob.setTextColor(Color.parseColor("#94A3B8"));
        mStatusKnob.setPadding(0, (int)(2 * density), 0, 0);
        statusCard.addView(mStatusKnob);

        contentPane.addView(statusCard);

        // Emergency Stop Button
        TextView stopBtn = new TextView(this);
        stopBtn.setText("🛑 즉각 정지 (Mute / Stop)");
        stopBtn.setTextSize(14);
        stopBtn.setTextColor(Color.parseColor("#FFFFFF"));
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        stopBtn.setGravity(Gravity.CENTER);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(Color.parseColor("#DC2626"));
        stopBg.setCornerRadius(6 * density);
        stopBtn.setBackground(stopBg);
        stopBtn.setPadding(0, (int)(9 * density), 0, (int)(9 * density));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopLp.setMargins(0, 0, 0, (int)(8 * density));
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, S6AudioGeneratorService.class));
                S6AudioGeneratorService.sIsPlaying = false;
                S6AudioGeneratorService.sModeTitle = "🛑 [작동 중지] 발진 멈춤 (대기 상태)";
                S6AudioGeneratorService.sInstantFreq = 0.0f;
                syncTelemetryUi();
                Toast.makeText(MainActivity.this, "🛑 주파수 발진 정지 완료 (작동 중지)", Toast.LENGTH_SHORT).show();
            }
        });
        contentPane.addView(stopBtn);

        // 🎛️ Split View Console Menu Frame: Exactly [전체 / 메인 / 서브1 / 서브2]
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(42 * density));
        tabLp.setMargins(0, 0, 0, (int)(12 * density));
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
                "24시간 4대 스케줄 + 9대 실전 맞춤 + 전술 요격 프리셋",
                "#00F0FF",
                "#082F49"
        );

        // [파트 1] 24시간 4대 시간대별 룸모드 직타 스케줄
        addSectionHeaderInsideFrame(mFrameMainContainer, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 룸모드 직타 스케줄", "#00F0FF");

        addFullPresetButton(mFrameMainContainer,
                "🌅 1. 아침 (07:00~13:00): 55.0 Hz 직타",
                "🎯 타격효과: 53.1~55Hz 룸모드 피크 관통 (단독 55.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.5%/±1.93Hz, 속도 1.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.035f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 스케줄-1] 아침 55.0Hz 직타 (FM 3.5%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "☀️ 2. 오후 (13:00~19:00): 31.0 ~ 40.0 Hz (360초 스윕)",
                "🎯 타격효과: 흉벽 관통 불규칙 이중 파면 (스윕범위 31.0~40.0Hz, 주기 360초, 분당 1.50Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 20%, 캐리어2 12.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 31.00f, 40.00f, 360.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 12.50f,
                                0.0f, 0, 2, false, 15,
                                "[메인 스케줄-2] 오후 31.0~40.0Hz 스윕 (RM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌆 3. 저녁 (19:00~01:00): 43.2 Hz 직타",
                "🎯 타격효과: 120mm 슬래브 1타 기계 해머 직타 (단독 43.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 2.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 43.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 2.80f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[메인 스케줄-3] 저녁 43.2Hz 슬래브 해머 (AM 18%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌙 4. 취침 (01:00~07:00): 55.0 Hz 락온",
                "🎯 타격효과: 침실 1차 정상파 초밀착 락온 (단독 55.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.5%/±1.38Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.025f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 스케줄-4] 취침 55.0Hz 락온 (FM 2.5% 스텔스)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 2] 사용자 행동별 9대 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameMainContainer, "2️⃣ [파트 2] 🎯 사용자 행동별 9대 실전 맞춤 시나리오", "#00F0FF");

        addFullPresetButton(mFrameMainContainer,
                "⚡ 1. 상대 공격 즉각 요격: 55.0 Hz 직타 (복합 변조)",
                "🎯 타격효과: 쿵쾅거림 즉시 룸모드 충격파 반격 (단독 55.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.5%/±1.93Hz, 속도 1.50Hz) | AM ON(심도 15%, 펄스 2.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.035f, 1.50f, true, 0.150f, 2.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[메인 행동-1] 상대공격 즉각요격 55.0Hz (복합변조)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔨 2. 벌주기 (보복 타격): 40.0 Hz 두경부 직타",
                "🎯 타격효과: 1~2시간 강력 집중 응징 (단독 40.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 25%, 캐리어2 15.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 40.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 15.00f,
                                6.0f, 1, 2, false, 15,
                                "[메인 행동-2] 벌주기 40.0Hz 두경부 타격 (RM 25%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏢 3. 출근 12시간 무인방어: 24.0 Hz / 43.2 Hz 교대 (600초)",
                "🎯 타격효과: 07~19시 부재중 자동 방어 (주주파수 43.20Hz + 기저하모닉 24.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±1.73Hz, 속도 1.20Hz) | AM ON(심도 15%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 43.20f, 24.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.040f, 1.20f, true, 0.150f, 2.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-3] 출근 12h 무인방어 24/43.2Hz 교대",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🚶 4. 일상 외출 자동 순항: 31.0 Hz 상복부 직타",
                "🎯 타격효과: 2~4시간 외출 시 기저 둔통 유지 (단독 31.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#60A5FA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 31.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 1.80f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-4] 일상외출 31.0Hz 상복부 직타 (AM 12%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏰 5. 초장기 외출 (48h+): 55.0 Hz 인터벌 락온",
                "🎯 타격효과: 무인 요새화 (주주파수 55.00Hz + 기저하모닉 27.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.5%/±1.38Hz, 속도 0.65Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 27.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.025f, 0.65f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-5] 초장기 48h+ 55.0Hz 인터벌 락온",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💥 6. 상대 내성 파괴: 24.0 Hz ↔ 55.0 Hz 급변 도약",
                "🎯 타격효과: 비선형 변조 도약으로 상대 신경 적응 무효화 (주주파수 55.00Hz + 기저하모닉 24.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 3.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 24.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 3.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[메인 행동-6] 상대 내성파괴 24↔55Hz 급변 (AM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🧠 7. 상대 정신 집중 차단: 40.0 Hz 뇌파 공진 직타",
                "🎯 타격효과: 재택/공부 방해, 인지 저하 유도 (단독 40.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 20%, 캐리어2 10.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 40.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 10.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-7] 정신집중차단 40.0Hz 뇌파직타 (RM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛌 8. 수면 시간 집중 압박: 55.0 Hz 룸모드 락온",
                "🎯 타격효과: 새벽 1~5시 수면 단계 파괴 (단독 55.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.65Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-8] 수면시간 집중압박 55.0Hz 락온 (FM 3%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛡️ 9. 3시간 이상 기기 보호: 43.2 Hz 보호 모드",
                "🎯 타격효과: 보이스코일 발열 억제 운용 (단독 43.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#2DD4BF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 43.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 행동-9] 3시간+ 기기보호 43.2Hz (AM 12%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 3] 전술 요격 및 룸모드 특수 프리셋
        addSectionHeaderInsideFrame(mFrameMainContainer, "3️⃣ [파트 3] 📊 전술 요격 및 룸모드 특수 프리셋", "#00F0FF");

        addFullPresetButton(mFrameMainContainer,
                "🔨 슬래브 120mm 1타 굽힘파: 43.2 Hz 단독 직타",
                "🎯 타격효과: 원음 무변조 수직 관통 해머 (단독 43.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 43.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[메인 특수-1] 슬래브 120mm 굽힘파 43.2Hz 단독",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🎯 침실 1차 정상파 스텔스: 53.6 Hz 단독 직타",
                "🎯 타격효과: 침실 고유 공진 초밀착 락온 (단독 53.60Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.0%/±1.07Hz, 속도 0.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.60f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.020f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 특수-2] 침실 1차 정상파 53.6Hz 직타 (FM 2%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌊 흉복부 중저역 스윕: 24.0 ~ 38.0 Hz (480초 스윕)",
                "🎯 타격효과: 흉복부 내장 공진 광역 훑기 (스윕범위 24.0~38.0Hz, 주기 480초, 분당 1.75Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 24.00f, 38.00f, 480.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 특수-3] 흉복부 24.0~38.0Hz 스윕 (AM 15%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌪️ 룸모드 고차 훑기: 40.0 ~ 55.0 Hz (300초 스윕)",
                "🎯 타격효과: 천장 전 대역 고유 공진 순환 가진 (스윕범위 40.0~55.0Hz, 주기 300초, 분당 3.00Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±1.90Hz, 속도 1.00Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#6366F1",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 40.00f, 55.00f, 300.0f,
                                true, 0.040f, 1.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[메인 특수-4] 룸모드 40.0~55.0Hz 스윕 (FM 4%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        contentPane.addView(mFrameMainContainer);

        // Divider between Main and Sub1
        mSplitDivider1 = createSplitDivider("▼ ⚡ 서브1 프레임 (통합운용 시나리오 버전2) ▼", "#10B981");
        contentPane.addView(mSplitDivider1);

        // -------------------------------------------------------------------------
        // ⚡ [서브1 프레임] 통합운용 시나리오 버전2 (총 11개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub1Container = createFrameContainer(
                "⚡ [서브1 프레임] 통합운용 시나리오 버전2 (상황 전술)",
                "하악골 골도 공진 + 2차 정상파 + 1Hz 맥놀이 충격파",
                "#10B981",
                "#064E3B"
        );

        // [파트 1] 24시간 4대 시간대별 마스터 스케줄 (v2)
        addSectionHeaderInsideFrame(mFrameSub1Container, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 룸모드 직타 스케줄 (v2)", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "🌅 1. 아침 (07:00~13:00 v2): 46.8 Hz 직타",
                "🎯 타격효과: 하악골 골도 공진 수직 해머 직타 (단독 46.80Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 2.40Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 46.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 2.40f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[서브1 스케줄-1] 아침 46.8Hz 골도해머 (AM 16%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "☀️ 2. 오후 (13:00~19:00 v2): 51.5 Hz 직타",
                "🎯 타격효과: 311호 룸모드 2차 정상파 공진 추적 (단독 51.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±2.06Hz, 속도 1.20Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#2DD4BF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 51.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.040f, 1.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브1 스케줄-2] 오후 51.5Hz 2차정상파 (FM 4%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌆 3. 저녁 (19:00~01:00 v2): 54.0 Hz 직타",
                "🎯 타격효과: 55Hz 인접 1.0Hz 맥놀이 충격파 직타 (단독 54.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 25%, 캐리어2 1.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 1.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브1 스케줄-3] 저녁 54.0Hz 1Hz맥놀이 (RM 25%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌙 4. 취침 (01:00~07:00 v2): 36.5 ~ 44.5 Hz (600초 스윕)",
                "🎯 타격효과: 흉골/갈비뼈 수직 미세 펄스 훑기 (스윕범위 36.5~44.5Hz, 주기 600초, 분당 0.80Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 36.50f, 44.50f, 600.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브1 스케줄-4] 취침 36.5~44.5Hz 흉골스윕 (AM 12%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v2)
        addSectionHeaderInsideFrame(mFrameSub1Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v2)", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "⚡ 1. 상대 공격 즉각 반격 (v2): 54.0 Hz 직타",
                "🎯 타격효과: 하향 충격파 동시 발진 (단독 54.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 2.00Hz) | RM ON(심도 25%, 캐리어2 1.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 2.00f, true, 0.250f, 1.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브1 행동-1] 즉각반격 54.0Hz 충격파 (RM+AM)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🔨 2. 벌주기 (미로기관 파쇄): 46.8 Hz 두개골 직타",
                "🎯 타격효과: 안구/두개골 공진 입체 파쇄 (단독 46.80Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±2.34Hz, 속도 2.00Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 46.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 2.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브1 행동-2] 벌주기 46.8Hz 두개골파쇄 (FM 5%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏢 3. 출근 12시간 무인방어 (v2): 36.5 Hz / 51.5 Hz 교대 (720초)",
                "🎯 타격효과: v2 독자 대역 순환 신경 피로 극대화 (주주파수 51.50Hz + 기저하모닉 36.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.0%/±2.06Hz, 속도 1.20Hz) | AM ON(심도 12%, 펄스 1.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 51.50f, 36.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.040f, 1.20f, true, 0.120f, 1.50f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브1 행동-3] 출근 12h 36.5/51.5Hz 교대 (AM/FM)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "💥 4. 상대 내성 완전파괴 (v2): 33.0 Hz ↔ 54.0 Hz 급변 도약",
                "🎯 타격효과: 주파수 비선형 도약 예측 불허 타격 (주주파수 54.00Hz + 기저하모닉 33.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 3.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.00f, 33.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 3.50f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[서브1 행동-4] 내성완전파괴 33↔54Hz 급변 (AM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧠 5. 상대 정신 집중차단 (v2): 46.8 Hz 뇌파 공진 직타",
                "🎯 타격효과: 골도 공진으로 두통 및 인지 마비 유도 (단독 46.80Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 20%, 캐리어2 8.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 46.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 8.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브1 행동-5] 정신집중차단 46.8Hz 뇌파직타 (RM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 3] v2 고유 음향역학 변조 전술 프리셋
        addSectionHeaderInsideFrame(mFrameSub1Container, "3️⃣ [파트 3] 📊 v2 고유 음향역학 변조 전술 프리셋", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "🦴 하악골 골도 공진 단독: 46.8 Hz 단독 직타",
                "🎯 타격효과: 골도 공진 순수 타격 해머 (주주파수 46.80Hz + 기저하모닉 23.40Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 46.80f, 23.40f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, true, 15,
                                "[서브1 특수-1] 하악골 골도공진 46.8Hz 단독 (AM 16%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🎯 2차 정상파 수직 관통: 51.5 Hz 단독 직타",
                "🎯 타격효과: 침실 2차 정상파 수직 관통 락온 (주주파수 51.50Hz + 기저하모닉 25.75Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.5%/±1.80Hz, 속도 1.20Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#14B8A6",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 51.50f, 25.75f, 0.00f, 0.00f, 0.0f,
                                true, 0.035f, 1.20f, true, 0.120f, 2.40f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[서브1 특수-2] 2차 정상파 51.5Hz 관통 (FM 4%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        contentPane.addView(mFrameSub1Container);

        // Divider between Sub1 and Sub2
        mSplitDivider2 = createSplitDivider("▼ 🛡️ 서브2 프레임 (통합운용 시나리오 버전3) ▼", "#A855F7");
        contentPane.addView(mSplitDivider2);

        // -------------------------------------------------------------------------
        // 🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (총 10개 프리셋)
        // -------------------------------------------------------------------------
        mFrameSub2Container = createFrameContainer(
                "🛡️ [서브2 프레임] 통합운용 시나리오 버전3 (장기 방어)",
                "슬래브 전단파 + 고차 굽힘파 + 1차 정상파 초정밀 락온",
                "#A855F7",
                "#3B0764"
        );

        // [파트 1] 24시간 4대 시간대별 마스터 스케줄 (v3)
        addSectionHeaderInsideFrame(mFrameSub2Container, "1️⃣ [파트 1] 🌅 24시간 4대 시간대별 룸모드 직타 스케줄 (v3)", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "🌅 1. 아침 (07:00~13:00 v3): 37.0 Hz 직타",
                "🎯 타격효과: 슬래브 전단파 요동 수직 관통 (단독 37.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.85Hz, 속도 1.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 37.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브2 스케줄-1] 아침 37.0Hz 전단파 직타 (FM 5%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "☀️ 2. 오후 (13:00~19:00 v3): 48.5 Hz 직타",
                "🎯 타격효과: 슬래브 고차 굽힘파 기계 해머 직타 (단독 48.50Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 3.10Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#E879F9",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 48.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 3.10f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[서브2 스케줄-2] 오후 48.5Hz 고차굽힘파 (AM 18%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌆 3. 저녁 (19:00~01:00 v3): 53.2 Hz 직타",
                "🎯 타격효과: 311호 1차 정상파(53.6Hz) 초정밀 락온 (단독 53.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F472B6",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브2 스케줄-3] 저녁 53.2Hz 정상파 락온 (FM 3%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌙 4. 취침 (01:00~07:00 v3): 42.0 ~ 47.5 Hz (580초 스윕)",
                "🎯 타격효과: 침대 프레임 골도 미세 펄스 훑기 (스윕범위 42.0~47.5Hz, 주기 580초, 분당 0.57Hz 이동)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 10%, 펄스 1.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 42.00f, 47.50f, 580.0f,
                                false, 0.000f, 0.00f, true, 0.100f, 1.20f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브2 스케줄-4] 취침 42.0~47.5Hz 골도스윕 (AM 10%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v3)
        addSectionHeaderInsideFrame(mFrameSub2Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v3)", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "⚡ 1. 상대 공격 즉각 반격 (v3): 53.2 Hz 정상파 직타",
                "🎯 타격효과: 침실 룸모드 즉각 현기증 유발 (단독 53.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 0.80Hz) | AM ON(심도 18%, 펄스 2.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.030f, 0.80f, true, 0.180f, 2.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브2 행동-1] 즉각반격 53.2Hz 정상파 직타 (FM+AM)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🔨 2. 벌주기 (흉곽 슐만 파쇄): 37.0 Hz 슬래브 직타",
                "🎯 타격효과: 슐만 2차 고조파 연동 흉곽/척추 집중 타격 (단독 37.00Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 37.00f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 2.50f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[서브2 행동-2] 벌주기 37.0Hz 흉곽슐만파쇄 (AM 18%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏢 3. 출근 12시간 무인방어 (v3): 37.0 Hz / 53.2 Hz 교대 (600초)",
                "🎯 타격효과: 3주차 고유 주파수 및 3기기 변조 조화 (주주파수 53.20Hz + 기저하모닉 37.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±2.66Hz, 속도 1.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.20f, 37.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브2 행동-3] 출근 12h 37.0/53.2Hz 교대 (FM)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "💥 4. 상대 내성 완전파괴 (v3): 37.0 Hz ↔ 53.2 Hz 급변",
                "🎯 타격효과: 광대역 카오스 도약 복합 조화 (주주파수 53.20Hz + 기저하모닉 37.00Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.20f, 37.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 3.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[서브2 행동-4] 내성완전파괴 37↔53.2Hz 급변 (AM 20%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        // [파트 3] v3 고유 전단파/정상파 특수 프리셋
        addSectionHeaderInsideFrame(mFrameSub2Container, "3️⃣ [파트 3] 📊 v3 고유 전단파/정상파 특수 프리셋", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "🌊 슬래브 전단파 수직 관통: 37.0 Hz 단독 직타",
                "🎯 타격효과: 전단파 요동 단독 관통 (주주파수 37.00Hz + 기저하모닉 18.50Hz 복합 타격)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.85Hz, 속도 1.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#A855F7",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 37.00f, 18.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, true, 15,
                                "[서브2 특수-1] 슬래브 전단파 37.0Hz 단독 (FM 5%)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🎯 침실 1차 정상파 초정밀 락온: 53.2 Hz 단독 직타",
                "🎯 타격효과: 침실 룸모드 피크 극점 단독 직타 (단독 53.20Hz 정밀 위상고정 직타)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 0.80Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EC4899",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 53.20f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[서브2 특수-2] 1차 정상파 53.2Hz 초정밀 락온",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
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
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.0%/±1.60Hz, 속도 1.50Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 52.00f, 55.00f, 180.0f,
                                true, 0.030f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[구버전 스케줄-1] 아침 52.0~55.0Hz 하이브리드 (180s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "☀️ 2. 오후 (13:00~19:00 구버전 정제): 6.7 ~ 9.0 Hz (490초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕2 소화기·복부 연동 및 알파파 하단 교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.55Hz, 속도 0.85Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 6.70f, 9.00f, 490.0f,
                                true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[구버전 스케줄-2] 오후 6.7~9.0Hz 소화기·복부 (490s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌆 3. 저녁 (19:00~01:00 구버전 정제): 3.3 ~ 7.3 Hz (640초 스윕)",
                "🎯 타격효과: 구버전 앱 슬롯2 실측값(10분40초 루프) · 델타-세타 뇌파 동조 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.45Hz, 속도 0.60Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 3.30f, 7.30f, 640.0f,
                                true, 0.085f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, true, 15,
                                "[구버전 스케줄-3] 저녁 3.3~7.3Hz 슬롯2 뇌파차단 (640s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🌙 4. 취침 (01:00~07:00 구버전 정제): 12.0 ~ 24.5 Hz (6580초 스윕)",
                "🎯 타격효과: 구버전 초안 스윕8 야간 서파·장간막 진동 분쇄 초장주기(109분) 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 12%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 12.00f, 24.50f, 6580.0f,
                                false, 0.000f, 0.00f, true, 0.120f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[구버전 스케줄-4] 취침 12.0~24.5Hz 야간분쇄 (6580s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameLegacyContainer, "2️⃣ [파트 2] 🎯 구버전 고유 스윕 & 120mm 슬래브 1·2·3차 공진 (초저주파 → 중저주파 순)", "#FACC15");
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 1. [초저주파 구버전-스윕7] 1.0 ~ 4.0 Hz (2880초/48분 스윕) — 델타 서파 무인스캔",
                "🎯 타격효과: 구버전 초안 스윕7 원전(48분 루프) · 델타 서파 위상 스캔 및 심부 수면 방해",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±0.12Hz, 속도 0.30Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 4.00f, 2880.0f,
                                true, 0.050f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[구버전 전술-1] 초저주파 1.0~4.0Hz 초장주기 (2880s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 2. [초저주파 구버전-스윕1] 4.0 ~ 6.2 Hz (290초 스윕) — 척추·전정계 수직 바운싱",
                "🎯 타격효과: 구버전 초안 스윕1 원전(290초 주기) · 척추·전정계 수직 바운싱 및 내장 흔들림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±0.46Hz, 속도 0.75Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.00f, 6.20f, 290.0f,
                                true, 0.090f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[구버전 전술-2] 초저주파 4.0~6.2Hz 전정계 (290s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 3. [초저주파 슬래브 1차모드] 18.5 Hz 단독 — 120mm 슬래브 1차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 1차 지배공진(18.5Hz) 직타 · 안구·두개골 동시 울림",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.50f, 0.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[구버전 전술-3] 슬래브 1차지배공진 18.5Hz 직타",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 4. [중저주파 구버전-프리셋4] 18.6 ~ 25.1 Hz (900초 스윕, AM 0.50Hz) — 심야 역제압",
                "🎯 타격효과: 구버전 프리셋4 심야 내장진동(20~22Hz 장간막) 역제압 호흡주기 루프",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 15%, 펄스 0.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, true, 0.150f, 0.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[구버전 전술-4] 심야역제압 18.6~25.1Hz (AM 0.5Hz)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 5. [중저주파 구버전-프리셋3] 18.6 ~ 25.1 Hz (900초 스윕, RM 1.80Hz) — 외출 카오스",
                "🎯 타격효과: 구버전 프리셋3 장시간 외출용 카오스 비트 원전(RM 1.80Hz 회전맥놀이)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM OFF(0%) | RM ON(심도 28%, 캐리어2 1.80Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 18.60f, 25.10f, 900.0f,
                                false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.280f, 1.80f,
                                3.0f, 1, 2, false, 15,
                                "[구버전 전술-5] 외출카오스 18.6~25.1Hz (RM 1.8Hz)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 6. [중저주파 구버전-프리셋5] 19.5 Hz + 55.0 Hz 복합 — 안구·흉벽 동시 응징 해머",
                "🎯 타격효과: 구버전 프리셋5 즉각 응징 해머 펄스(19.5Hz 안구공진 + 55.0Hz 슬래브 표면)",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 20%, 펄스 4.10Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.50f, 55.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.200f, 4.10f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, false, 15,
                                "[구버전 전술-6] 즉각응징 19.5+55.0Hz 복합해머",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 7. [중저주파 슬래브 2차모드] 32.4 Hz 단독 — 120mm 슬래브 2차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 2차 지배공진(32.4Hz) 직타 · 흉복부 관통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.94Hz, 속도 1.90Hz) | AM ON(심도 15%, 펄스 3.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 32.40f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.90f, true, 0.150f, 3.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[구버전 전술-7] 슬래브 2차지배공진 32.4Hz 직타",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameLegacyContainer,
                "🏛️ 8. [중저주파 슬래브 3차모드] 54.8 Hz 단독 — 120mm 슬래브 3차 지배공진",
                "🎯 타격효과: 구버전 3.1절 팬더아파트 120mm 슬래브 3차 지배공진(54.8Hz) 직타 · 바닥 표면 진동 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±2.8%/±1.53Hz, 속도 1.40Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 54.80f, 0.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.028f, 1.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[구버전 전술-8] 슬래브 3차지배공진 54.8Hz 직타",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
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
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.10Hz, 속도 0.25Hz) | AM OFF(0%) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +9.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 1.00f, 2.50f, 300.0f,
                                true, 0.060f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f,
                                9.0f, 1, 2, true, 15,
                                "[기초-초저주파스윕01] 1.0~2.5Hz 이석기관·수면후불쾌감 (300s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 02] 2.5 ~ 4.5 Hz (360초 스윕) — 델타파 차단·심층 두통 유도",
                "🎯 타격효과: [조합1 반응1] 델타파(1~4Hz) 숙면 뇌파 동조 차단 · 두개내압 미세 맥동 · 심층 두통 및 수면단계 파괴",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.0%/±0.28Hz, 속도 0.45Hz) | AM ON(심도 12%, 펄스 1.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 2.50f, 4.50f, 360.0f,
                                true, 0.080f, 0.45f, true, 0.120f, 1.80f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, true, 15,
                                "[기초-초저주파스윕02] 2.5~4.5Hz 델타파차단·심층두통 (360s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🌊 [초저주파 스윕 03] 4.5 ~ 7.0 Hz (420초 스윕) — 장간막 공진·복통·수면리듬 분리",
                "🎯 타격효과: [조합1 스윕1하단·반응3] ISO 2631-1 복부 내장 수직 기본공진(4.5~7Hz) · 복통 유발 · 세타파 수면리듬 분리",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 3.90Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 4.50f, 7.00f, 420.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 3.90f, false, 0.000f, 0.00f,
                                6.0f, 1, 2, true, 15,
                                "[기초-초저주파스윕03] 4.5~7.0Hz 복통유발·수면리듬분리 (420s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 04 ★1차주력] 7.0 ~ 11.0 Hz (480초 스윕) — 전정·뇌간·자율신경·알파파",
                "🎯 타격효과: [조합1·3(★5)·4 통합] 전정·뇌간(7Hz)~슈만(7.83Hz)~자율신경·알파파(10.5~11Hz) 현기증·오심·맥박불안정·멍함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.77Hz, 속도 0.78Hz) | AM ON(심도 15%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FFD700",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 7.00f, 11.00f, 480.0f,
                                true, 0.085f, 0.78f, true, 0.150f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[기초-초저주파스윕04] 7.0~11.0Hz 전정·자율신경·현기증 (480s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 05 ★1차주력] 11.0 ~ 16.5 Hz (500초 스윕) — 뇌파간섭·인후두·장연동 교란",
                "🎯 타격효과: [조합1 스윕2·6하단 + 조합3(★5)] SMR·베타파 뇌파간섭 · 인후두·기도 압박 · 장연동 설사/복통 및 내장 울림",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 11.00f, 16.50f, 500.0f,
                                true, 0.070f, 1.25f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 2, false, 15,
                                "[기초-초저주파스윕05] 11.0~16.5Hz 뇌파·장연동·내장울림 (500s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "⭐ [초저주파 스윕 06 ★1차주력] 16.5 ~ 21.5 Hz (450초 스윕) — 두개골·시신경·귀울림(이명)",
                "🎯 타격효과: [조합1 반응4 + 조합3(★5) + 조합4] 두개골·전두엽(18Hz)·시신경·안구(18~21Hz) 공진 · 귀울림(이명)·두통·안구압박",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.71Hz, 속도 6.20Hz) | AM ON(심도 16%, 펄스 4.00Hz) | RM ON(심도 22%, 캐리어2 18.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 16.50f, 21.50f, 450.0f,
                                true, 0.090f, 6.20f, true, 0.160f, 4.00f, true, 0.220f, 18.50f,
                                3.0f, 1, 2, false, 15,
                                "[기초-초저주파스윕06] 16.5~21.5Hz 두개골·시신경·이명 (450s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "1️⃣-B [중저주파 스윕: 21.5 ~ 55.0 Hz] 내이·흉곽·복부·골반·고막·슬래브 공진 (4단계 무중복 오름차순)", "#FACC15");
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 07 ★2차보조] 21.5 ~ 27.0 Hz (500초 스윕) — 내이·석고보드·흉곽·대장",
                "🎯 타격효과: [조합3(★3)하단 + 조합4] 내이 방향감각 상실(22Hz) · 석고보드 한계·흉곽압박(24Hz) · 대장진동·하체무력(25Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.33Hz, 속도 1.15Hz) | AM ON(심도 18%, 펄스 3.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 21.50f, 27.00f, 500.0f,
                                true, 0.055f, 1.15f, true, 0.180f, 3.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[기초-중저주파스윕07] 21.5~27.0Hz 내이·흉곽·대장진동 (500s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 08 ★2차보조] 27.0 ~ 36.0 Hz (520초 스윕) — 골반·하복부·흉곽·위장·간",
                "🎯 타격효과: [조합1 스윕3 + 조합3(★3)상단 + 조합4] 골반·하복부 둔통(28Hz) · 흉곽·위장·간 묵직한 타격(31~36Hz) 주간 집중교란",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±1.89Hz, 속도 1.80Hz) | AM ON(심도 17%, 펄스 3.20Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 27.00f, 36.00f, 520.0f,
                                true, 0.060f, 1.80f, true, 0.170f, 3.20f, true, 0.180f, 13.50f,
                                3.0f, 1, 2, false, 15,
                                "[기초-중저주파스윕08] 27.0~36.0Hz 골반·흉곽·간 집중교란 (520s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 09 ★중광역] 36.0 ~ 46.0 Hz (580초 스윕) — 대뇌공포·귀막힘·집중력저하",
                "🎯 타격효과: [조합1 스윕4·7하단 + 조합3 중광역] 반복 불쾌울림·대뇌 공포감(40Hz) · 귀막힘·고막압박·집중력 저하(38~46Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.84Hz, 속도 1.40Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 36.00f, 46.00f, 580.0f,
                                true, 0.045f, 1.40f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 2, false, 15,
                                "[기초-중저주파스윕09] 36.0~46.0Hz 대뇌공포·귀막힘·고막압박 (580s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🔸 [중저주파 스윕 10 ★3차확산] 46.0 ~ 55.0 Hz (650초 스윕) — 귀먹먹함·콘크리트 슬래브 공진",
                "🎯 타격효과: [조합1 스윕4상단 + 조합3(★2) + 조합4] 고출력 귀 먹먹함(46~51Hz) ~ 콘크리트 벽면·슬래브 공간공진 최적점(55Hz)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.8%/±1.92Hz, 속도 1.15Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM ON(심도 20%, 캐리어2 11.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0.00f, 0.00f, 46.00f, 55.00f, 650.0f,
                                true, 0.038f, 1.15f, true, 0.120f, 2.40f, true, 0.200f, 11.50f,
                                0.0f, 0, 2, false, 15,
                                "[기초-중저주파스윕10] 46.0~55.0Hz 귀먹먹함·슬래브공간공진 (650s)",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-A [초저주파 단독: 7.0 ~ 18.0 Hz] 뇌간·자율신경·두개골·전두엽 직타 (4종 무중복 오름차순)", "#F472B6");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 01 ★1차주력] 7.0 Hz (+기저 3.5Hz) — 구조물 심부공진 & 전정기관·뇌간",
                "🎯 타격효과: [조합2·3·4] 구조물 심부공진 · 전정기관·뇌간 타격 · 현기증·방향감각 혼란·기저 스트레스 극대화",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±7.5%/±0.53Hz, 속도 0.55Hz) | AM ON(심도 12%, 펄스 3.80Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.00f, 3.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.075f, 0.55f, true, 0.120f, 3.80f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[기초-초저주파단독01] 7.0Hz(+3.5Hz) 심부공진·전정뇌간 현기증",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 02 ★1차주력] 10.5 Hz (+기저 5.25Hz) — 심박변동(HRV) & 자율신경 교란",
                "🎯 타격효과: [조합2·3·4] 심박변동(HRV)·자율신경계 직접 교란 · 원인불명 메스꺼움·불안정한 맥박·알파파 차단",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±0.89Hz, 속도 0.95Hz) | AM ON(심도 14%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +6.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음(2f_c) ON | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.50f, 5.25f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 0.95f, true, 0.140f, 4.00f, false, 0.000f, 0.00f,
                                6.0f, 0, 2, true, 15,
                                "[기초-초저주파단독02] 10.5Hz(+5.25Hz) 자율신경·심박교란 메스꺼움",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 03 ★1차주력] 16.0 Hz (+기저 8.0Hz) — 뇌파간섭·위상현기증·장연동 복통",
                "🎯 타격효과: [조합2·3·4] 인체 임계경계(뇌파간섭 수면방해 · 내이-시각 위상어긋남 현기증 · 장연동 설사/복통 · 두개골 공진)",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±6.0%/±0.96Hz, 속도 1.25Hz) | AM ON(심도 20%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +4.5dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.00f, 8.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.060f, 1.25f, true, 0.200f, 4.00f, false, 0.000f, 0.00f,
                                4.5f, 1, 2, false, 15,
                                "[기초-초저주파단독03] 16.0Hz(+8.0Hz) 위상현기증·장연동복통",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [초저주파 단독 04 ★1차주력] 18.0 Hz (+기저 9.0Hz) — 벽 전체 불쾌공명 & 전두엽·삼차신경",
                "🎯 타격효과: [조합2·3·4] 벽 전체 불쾌한 울림 · 두개골 내부 공진 · 극심한 두통 · 삼차신경 자극 · 만성 무기력",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±8.5%/±1.53Hz, 속도 6.30Hz) | AM OFF(0%) | RM ON(심도 22%, 캐리어2 18.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 18.00f, 9.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.085f, 6.30f, false, 0.000f, 0.00f, true, 0.220f, 18.00f,
                                3.0f, 1, 2, false, 15,
                                "[기초-초저주파단독04] 18.0Hz(+9.0Hz) 벽불쾌공명·전두엽두통",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addSectionHeaderInsideFrame(mFrameBasicContainer, "2️⃣-B [중저주파 단독: 21.0 ~ 55.0 Hz] 시신경·내이·흉곽·복부·골반·슬래브 직타 (8종 무중복 오름차순)", "#34D399");
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 05 ★1차주력] 21.0 Hz (+기저 10.5Hz) — 시신경·안구 공진 & 정신적 긴장",
                "🎯 타격효과: [조합2·3·4] NASA 안구·시신경 공진(21Hz) · 안구 압박·초점 저하 · 정신적 긴장 및 고강도 스트레스",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.5%/±1.16Hz, 속도 2.10Hz) | AM ON(심도 18%, 펄스 4.00Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 21.00f, 10.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.055f, 2.10f, true, 0.180f, 4.00f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[기초-중저주파단독05] 21.0Hz(+10.5Hz) 시신경안구압박·정신긴장",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 06 ★2차보조] 22.0 Hz (+기저 11.0Hz) — 귀 안쪽·내이 방향감각 상실",
                "🎯 타격효과: [조합3·4] 귀 안쪽·내이 미로기관 집중 공진 · 방향감각 상실 · 자세 불균형 및 어지러움",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±9.0%/±1.98Hz, 속도 3.40Hz) | AM ON(심도 14%, 펄스 2.50Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.00f, 11.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.090f, 3.40f, true, 0.140f, 2.50f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[기초-중저주파단독06] 22.0Hz(+11.0Hz) 내이 방향감각상실·불균형",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 07 ★2차보조] 24.0 Hz (+기저 12.0Hz) — 석고보드 한계 & 흉곽·폐 압박",
                "🎯 타격효과: [조합2·3·4] 석고보드 벽체 공진 한계점 · 공간 전체 압박감 · 흉곽·폐 진동 및 가슴 답답함",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±4.5%/±1.08Hz, 속도 1.15Hz) | AM ON(심도 16%, 펄스 3.70Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#34D399",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 24.00f, 12.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.045f, 1.15f, true, 0.160f, 3.70f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[기초-중저주파단독07] 24.0Hz(+12.0Hz) 석고보드한계·흉곽폐압박",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 08 ★2차보조] 25.0 Hz (+기저 12.5Hz) — 대장·하복부 장진동 & 하체 무력",
                "🎯 타격효과: [조합2·3·4] 인체 7~25Hz 핵심 영향권 상단 마감 · 대장·하복부 진동 · 복부 팽만 및 하체 무력감",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 18%, 펄스 4.20Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 25.00f, 12.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.180f, 4.20f, false, 0.000f, 0.00f,
                                3.0f, 1, 2, false, 15,
                                "[기초-중저주파단독08] 25.0Hz(+12.5Hz) 대장하복부진동·하체무력",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 3시반(75%) · X-OVER 65Hz(10시반) · MAIN VOL 12시(50%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 09 ★2차보조] 28.0 Hz (+기저 14.0Hz) — 골반·장기 하부 무거움·둔통",
                "🎯 타격효과: [조합3·4] 골반저근·장기 하부 직타 · 하복부 묵직한 무거움 및 지속적 복부 둔통",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±5.0%/±1.40Hz, 속도 0.90Hz) | AM ON(심도 17%, 펄스 3.15Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#60A5FA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.00f, 14.00f, 0.00f, 0.00f, 0.0f,
                                true, 0.050f, 0.90f, true, 0.170f, 3.15f, false, 0.000f, 0.00f,
                                3.0f, 0, 2, false, 15,
                                "[기초-중저주파단독09] 28.0Hz(+14.0Hz) 골반장기하부 무거움·둔통",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 10 ★2차보조] 31.0 Hz (+기저 15.5Hz) — 흉곽·위장·간 묵직한 타격",
                "🎯 타격효과: [조합2·3·4] 흉곽 진동 및 상복부 장기(위장·간) 묵직한 타격감 · 심부 내장 피로 누적",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 16%, 펄스 2.75Hz) | RM ON(심도 18%, 캐리어2 13.50Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#F59E0B",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 31.00f, 15.50f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.160f, 2.75f, true, 0.180f, 13.50f,
                                3.0f, 1, 2, false, 15,
                                "[기초-중저주파단독10] 31.0Hz(+15.5Hz) 흉곽·위장·간 묵직한타격",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 11 ★3차확산] 40.0 Hz (+기저 20.0Hz) — 반복 불쾌울림 & 대뇌 공포감",
                "🎯 타격효과: [조합2·3·4] 타격성 강한 반복 불쾌울림 · 대뇌 감마파 교란 · 원인 모를 공포감 및 정신 혼미",
                "〰️ 변조(FM·AM·RM): FM OFF(0%) | AM ON(심도 14%, 펄스 4.00Hz) | RM ON(심도 24%, 캐리어2 16.00Hz)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +3.0dB | Tanh 소프트 새추레이터(기수배음) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#C084FC",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 40.00f, 20.00f, 0.00f, 0.00f, 0.0f,
                                false, 0.000f, 0.00f, true, 0.140f, 4.00f, true, 0.240f, 16.00f,
                                3.0f, 1, 2, false, 15,
                                "[기초-중저주파단독11] 40.0Hz(+20.0Hz) 반복불쾌울림·대뇌공포혼미",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
                    }
                });
        addFullPresetButton(mFrameBasicContainer,
                "🎯 [중저주파 단독 12 ★3차확산] 55.0 Hz (+기저 27.5Hz) — 콘크리트 벽면·슬래브 공진 최적점",
                "🎯 타격효과: [조합2·3·4] 아파트 콘크리트 벽면·슬래브 공진 최적점 · 체감 진동 및 구조물 전달력 최강 마감",
                "〰️ 변조(FM·AM·RM): FM ON(편이 ±3.2%/±1.76Hz, 속도 1.35Hz) | AM ON(심도 12%, 펄스 2.40Hz) | RM OFF(0%)\n🎛️ DSP 설정: 64-bit NCO | 부스트 +0.0dB | 트루피크 브릭월(-1.0dBTP·Peak 29200) | 2차배음 OFF | 위상: (L+R)/2 BTL 브리지 모노(68W) | 폰볼륨: 13/15단(85%·레벨링)\n🔊 앰프설정 [트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 55.00f, 27.50f, 0.00f, 0.00f, 0.0f,
                                true, 0.032f, 1.35f, true, 0.120f, 2.40f, false, 0.000f, 0.00f,
                                0.0f, 0, 2, false, 15,
                                "[기초-중저주파단독12] 55.0Hz(+27.5Hz) 콘크리트벽면·슬래브 최적점",
                                "[트랜스2 LP-168S 68W BTL 5노브]: SUB VOL 4시(80% 골전도 풀파워) · X-OVER 85Hz(12시반) · MAIN VOL 12시반(55%) · BASS 0dB(12시) · TREBLE 7시(최소)");
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
        Intent it = new Intent(this, S6AudioGeneratorService.class);
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
        S6AudioGeneratorService.sActivePart = partTag;
        S6AudioGeneratorService.sScenarioName = title;

        it.putExtra("title", title);
        it.putExtra("part", partTag);
        it.putExtra("scenario", title);
        it.putExtra("knob_guide", knobGuide);

        S6AudioGeneratorService.sIsPlaying = true;
        S6AudioGeneratorService.sModeTitle = title;
        S6AudioGeneratorService.sKnobGuide = knobGuide;
        S6AudioGeneratorService.sFmEnabled = fmOn;
        S6AudioGeneratorService.sFmDepth = fmDepth;
        S6AudioGeneratorService.sFmRateHz = fmRate;
        S6AudioGeneratorService.sAmEnabled = amOn;
        S6AudioGeneratorService.sAmDepth = amDepth;
        S6AudioGeneratorService.sAmRateHz = amRate;
        S6AudioGeneratorService.sRmEnabled = rmOn;
        S6AudioGeneratorService.sRmDepth = rmDepth;
        S6AudioGeneratorService.sRmRateHz = rmRate;
        S6AudioGeneratorService.sBassBoostDb = boostDb;
        S6AudioGeneratorService.sLimiterMode = limiterMode;
        S6AudioGeneratorService.sChannelMode = channelMode;
        S6AudioGeneratorService.sHarmonicExciter = exciter;
        S6VolumeHelper.syncTargetFromSystem(this);
        if (sweep) {
            S6AudioGeneratorService.sIsSweepMode = true;
            S6AudioGeneratorService.sSweepMin = sMin;
            S6AudioGeneratorService.sSweepMax = sMax;
            S6AudioGeneratorService.sSweepPeriodSec = sSec;
            S6AudioGeneratorService.sInstantFreq = sMin;
        } else {
            S6AudioGeneratorService.sIsSweepMode = false;
            S6AudioGeneratorService.sTargetFreq = freq;
            S6AudioGeneratorService.sSubFreq = subFreq;
            S6AudioGeneratorService.sInstantFreq = freq;
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
        boolean isPlaying = S6AudioGeneratorService.sIsPlaying;
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
            if (isPlaying) {
                setTextIfChanged(mStatusMode, "상태: [발진 중] " + S6AudioGeneratorService.sActivePart, Color.parseColor("#00E676")); // Neon Traffic Light Green
            } else {
                setTextIfChanged(mStatusMode, "상태: [작동 중지] 멈춤 (대기 상태)", Color.parseColor("#FF3366")); // Neon Traffic Light Red
            }
        }

        if (mStatusScenario != null) {
            if (isPlaying) {
                setTextIfChanged(mStatusScenario, "시나리오: " + S6AudioGeneratorService.sScenarioName, Color.parseColor("#00F0FF")); // Electric Cyan
            } else {
                setTextIfChanged(mStatusScenario, "시나리오: 대기 중 (선택 없음)", Color.parseColor("#64748B"));
            }
        }

        if (mStatusFreq != null) {
            if (!isPlaying) {
                setTextIfChanged(mStatusFreq, "발진: 0.00 Hz (출력 차단 / MUTE)", Color.parseColor("#94A3B8")); // Muted Gray
            } else if (S6AudioGeneratorService.sIsSweepMode) {
                setTextIfChanged(mStatusFreq, String.format("발진: %.2f Hz 실시간 (%.1f~%.1fHz 스윕)",
                        S6AudioGeneratorService.sInstantFreq, S6AudioGeneratorService.sSweepMin, S6AudioGeneratorService.sSweepMax), Color.parseColor("#FFD700")); // High-brightness & saturation Green
            } else {
                setTextIfChanged(mStatusFreq, String.format("발진: %.2f Hz 직타 고정 (-1.0dBTP 무왜곡 | Peak 29200)",
                        S6AudioGeneratorService.sTargetFreq), Color.parseColor("#FFD700")); // High-brightness & saturation Green
            }
        }

        if (mStatusMod != null) {
            if (!isPlaying) {
                setTextIfChanged(mStatusMod, "〰️ 변조(FM·AM·RM): 대기 중 (프리셋 선택 시 FM 편이율·AM 펄스심도·RM 측파대 실시간 표시)", Color.parseColor("#64748B"));
            } else {
                String fmS = S6AudioGeneratorService.sFmEnabled ? String.format("ON(±%.1f%%·%.2fHz)", S6AudioGeneratorService.sFmDepth * 100f, S6AudioGeneratorService.sFmRateHz) : "OFF(0%)";
                String amS = S6AudioGeneratorService.sAmEnabled ? String.format("ON(%.0f%%·%.2fHz)", S6AudioGeneratorService.sAmDepth * 100f, S6AudioGeneratorService.sAmRateHz) : "OFF(0%)";
                String rmS = S6AudioGeneratorService.sRmEnabled ? String.format("ON(%.0f%%·%.2fHz)", S6AudioGeneratorService.sRmDepth * 100f, S6AudioGeneratorService.sRmRateHz) : "OFF(0%)";
                setTextIfChanged(mStatusMod, String.format("〰️ 변조(FM·AM·RM): FM %s | AM %s | RM %s", fmS, amS, rmS), Color.parseColor("#F472B6"));
            }
        }

        if (mStatusDsp != null) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int curVol = (am != null) ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : S6VolumeHelper.sTargetVolumeStep;
            // Display only: the guard's target must track user key presses, not an OS clamp to 9.
            if (S6VolumeHelper.isPersonalDevice()) {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 개인기기 보호(무개입)",
                        S6AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            } else if (S6VolumeHelper.isA30Device()) {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 음질·음향효과 최적화 고정",
                        S6AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            } else {
                setTextIfChanged(mStatusDsp, String.format("🎛️ Vorta DSP v4.2: 64-bit NCO | %s | 현재볼륨 %d/15단 | 자유볼륨·OS 안전볼륨 해제 ON",
                        S6AudioGeneratorService.buildVortaDspSummary(), curVol), Color.parseColor(isPlaying ? "#FFA657" : "#94A3B8"));
            }
        }
        if (mStatusKnob != null) {
            setTextIfChanged(mStatusKnob, "🔊 앰프설정: " + S6AudioGeneratorService.sKnobGuide, Color.parseColor(isPlaying ? "#34D399" : "#94A3B8"));
        }
    }
}
