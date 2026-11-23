import os
import sys

java_code = r'''package com.vorta.s7frequency;

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

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // 🎛️ Real-time Telemetry Dashboard Views
    private LinearLayout mStatusCardLayout;
    private ImageView mStatusTrafficLight;
    private TextView mStatusMode;
    private TextView mStatusScenario;
    private TextView mStatusFreq;
    private TextView mStatusMod;
    private TextView mStatusKnob;

    // 🎛️ Split View Containers (3 Frames)
    private LinearLayout mFrameMainContainer;
    private LinearLayout mFrameSub1Container;
    private LinearLayout mFrameSub2Container;
    private LinearLayout mSplitDivider1;
    private LinearLayout mSplitDivider2;
    private TextView[] mSplitViewButtons = new TextView[4];
    private int mCurrentSplitMode = 0; // 0: 전체, 1: 메인, 2: 서브1, 3: 서브2

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTelemetryUpdater = new Runnable() {
        @Override
        public void run() {
            syncTelemetryUi();
            mUiHandler.postDelayed(this, 100); // 10Hz fast UI refresh
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lockscreen bypass for easy access without screen-on locking (respects user screen off timeout)
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        S7AudioGeneratorService.loadConfig(this);
        S7VolumeHelper.ensureTargetVolumeIfNeeded(this, true);

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
        titleTv.setText("보르타 레조 S7 v3.0");
        titleTv.setTextSize(22);
        titleTv.setTextColor(Color.parseColor("#00F0FF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        contentPane.addView(titleTv);

        TextView subTitleTv = new TextView(this);
        subTitleTv.setText("갤럭시 S7 + 240W 스테레오 앰프(스마토 #2) 맞춤 기저 앵커 주파수 발생기 | Vorta DSP");
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
                Intent it = new Intent(MainActivity.this, S7AudioGeneratorService.class);
                it.putExtra("action", "stop");
                startService(it);
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

        mSplitViewButtons[0] = createSplitTabButton(tabRow, "🌐 전체", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(0);
            }
        });
        mSplitViewButtons[1] = createSplitTabButton(tabRow, "🎯 메인", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(1);
            }
        });
        mSplitViewButtons[2] = createSplitTabButton(tabRow, "⚡ 서브1", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(2);
            }
        });
        mSplitViewButtons[3] = createSplitTabButton(tabRow, "🛡️ 서브2", 1.0f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSplitView(3);
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
                "골반저근 펄스 해머 타격 | AM 15% (3.20Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 3.20f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[메인 스케줄-1] 아침 28.0Hz 앵커 (AM 15%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "☀️ 2. 오후 (13:00~19:00): 10.5 Hz 앵커 직타",
                "자율신경·심박 요동 앵커 | FM 8% (0.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.5f, 0f, 0f, 0f, 0f,
                                true, 0.08f, 0.80f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[메인 스케줄-2] 오후 10.5Hz 앵커 (FM 8%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌆 3. 저녁 (19:00~01:00): 7.0 Hz 앵커 직타",
                "전정기관 평형 파괴 요동 | FM 8% (0.50Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.0f, 0f, 0f, 0f, 0f,
                                true, 0.08f, 0.50f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[메인 스케줄-3] 저녁 7.0Hz 앵커 (FM 8%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌙 4. 취침 (01:00~07:00): 21.0 ~ 24.5 Hz (1250초 스윕)",
                "바닥 미세 맥동 골도 진동 | AM 10% (0.60Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0f, 0f, 21.0f, 24.5f, 1250.0f,
                                false, 0f, 0f, true, 0.10f, 0.60f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[메인 스케줄-4] 취침 21.0~24.5Hz 골도스윕 (AM 10%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 2] 사용자 행동별 9대 실전 맞춤 시나리오
        addSectionHeaderInsideFrame(mFrameMainContainer, "2️⃣ [파트 2] 🎯 사용자 행동별 9대 실전 맞춤 시나리오", "#FACC15");

        addFullPresetButton(mFrameMainContainer,
                "⚡ 1. 상대 공격 즉각 요격: 7.0Hz + 10.5Hz 복합 앵커",
                "바닥 2점 충격파 즉각 가진 | FM 8% (0.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.0f, 10.5f, 0f, 0f, 0f,
                                true, 0.08f, 0.80f, false, 0f, 0f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[메인 행동-1] 상대공격 즉각요격 7+10.5Hz 복합",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔨 2. 벌주기 (보복 타격): 28.0 Hz 골반 앵커 직타",
                "골반저 해머 압박 집중 응징 | AM 18% (3.50Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.18f, 3.50f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[메인 행동-2] 벌주기 28.0Hz 골반앵커 직타 (AM 18%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏢 3. 출근 12시간 무인방어: 10.5Hz / 28.0Hz 교대 (600초)",
                "기저 피로 누적 및 발열 30% 저감 | FM 8% / AM 15% | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.0f, 10.5f, 0f, 0f, 0f,
                                true, 0.08f, 0.80f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[메인 행동-3] 출근 12h 10.5/28Hz 교대",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🚶 4. 일상 외출 자동 순항: 10.5 Hz 지속 앵커",
                "2~4시간 외출 시 기저 심박 유지 | FM 6% (0.80Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.5f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 0.80f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[메인 행동-4] 일상외출 10.5Hz 지속앵커 (FM 6%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🏰 5. 초장기 외출 (48h+): 21.0 ~ 24.5 Hz 앵커",
                "45분 발진 / 15분 휴지 코일 보호 | AM 10% (0.60Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0f, 0f, 21.0f, 24.5f, 1250.0f,
                                false, 0f, 0f, true, 0.10f, 0.60f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[메인 행동-5] 초장기 48h+ 21~24.5Hz 보호앵커",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💥 6. 상대 내성 파괴: 7.0Hz ↔ 10.5Hz 도약 (180초)",
                "비동기 변조 도약으로 상대 신경 적응 무효화 | FM 10% (1.20Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.5f, 7.0f, 0f, 0f, 0f,
                                true, 0.10f, 1.20f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 1, 0, false, 13,
                                "[메인 행동-6] 내성파괴 7↔10.5Hz 도약 (FM 10%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🧠 7. 상대 정신 집중 차단: 7.0 Hz 전정계 흔들기",
                "전정계 평형 교란 및 인지 저하 | FM 8% (0.50Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.0f, 0f, 0f, 0f, 0f,
                                true, 0.08f, 0.50f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[메인 행동-7] 정신집중차단 7.0Hz 전정계흔들기",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛌 8. 수면 시간 집중 압박: 21.0 ~ 24.5 Hz 앵커",
                "새벽 1~5시 침대 골도 진동 집중 타격 | AM 10% (0.60Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0f, 0f, 21.0f, 24.5f, 1250.0f,
                                false, 0f, 0f, true, 0.10f, 0.60f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[메인 행동-8] 수면시간 21~24.5Hz 골도앵커",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🛡️ 9. 3시간 이상 기기 보호: 28.0 Hz (50분/10분)",
                "AM 15% 듀티 사이클 동기화로 45도 이하 유지 | AM 15% (3.20Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 3.20f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[메인 행동-9] 3시간 기기보호 28Hz (50/10분)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 3] 고유 바닥 2점 기저/전술 특수 프리셋
        addSectionHeaderInsideFrame(mFrameMainContainer, "3️⃣ [파트 3] 📊 고유 바닥 2점 기저/전술 특수 프리셋", "#38BDF8");

        addFullPresetButton(mFrameMainContainer,
                "🌊 바닥 2점 기저 앵커 직타: 28.0 Hz 단독",
                "바닥 슬래브 2점 기저 앵커 집중 가진 | AM 15% (3.20Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#00F0FF",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 28.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 3.20f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[메인 특수-1] 바닥 2점 앵커 28.0Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🌀 공간 요동 FM 앵커: 10.5 Hz 단독",
                "공간 전체 음압 요동 및 심박 공진 | FM 8% (0.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 10.5f, 0f, 0f, 0f, 0f,
                                true, 0.08f, 0.80f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[메인 특수-2] 공간요동 10.5Hz FM 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "💫 전정기관 파괴 극저역: 7.0 Hz 단독",
                "미로기관 평형 상실 유발 초저역 | FM 8% (0.50Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 7.0f, 0f, 0f, 0f, 0f,
                                true, 0.08f, 0.50f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[메인 특수-3] 전정기관파괴 7.0Hz 극저역 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameMainContainer,
                "🔄 바닥 미세맥동 골도스윕: 21.0 ~ 24.5 Hz (1250초)",
                "바닥 2점 미세 진동 훑기 | AM 10% (0.60Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(true, 0f, 0f, 21.0f, 24.5f, 1250.0f,
                                false, 0f, 0f, true, 0.10f, 0.60f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[메인 특수-4] 바닥 미세맥동 21~24.5Hz 골도스윕",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
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
                "척추 추간판 압박점 요동 앵커 | FM 6% (1.80Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.0f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 1.80f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브1 스케줄-1] 아침 33.0Hz 척추앵커 (FM 6%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "☀️ 2. 오후 (13:00~19:00 v2): 22.0 Hz 앵커 직타",
                "골반 내 장기 초저속 펄스 타격 | AM 18% (0.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.18f, 0.80f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브1 스케줄-2] 오후 22.0Hz 골반펄스 (AM 18%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌆 3. 저녁 (19:00~01:00 v2): 5.0 Hz 앵커 직타",
                "간·담낭 초저역 서파 요동 | FM 10% (0.25Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.0f, 0f, 0f, 0f, 0f,
                                true, 0.10f, 0.25f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브1 스케줄-3] 저녁 5.0Hz 간담낭서파 (FM 10%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌙 4. 취침 (01:00~07:00 v2): 16.0 Hz 앵커 직타",
                "음향심리 불쾌감 피크 요동 | FM 5% (0.40Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.0f, 0f, 0f, 0f, 0f,
                                true, 0.05f, 0.40f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브1 스케줄-4] 취침 16.0Hz 불쾌감앵커 (FM 5%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v2)
        addSectionHeaderInsideFrame(mFrameSub1Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v2)", "#FACC15");

        addFullPresetButton(mFrameSub1Container,
                "⚡ 1. 상대 공격 즉각 반격 (v2): 5.0 Hz 앵커",
                "하향 충격파 즉각 발진 | FM 10% (0.25Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.0f, 0f, 0f, 0f, 0f,
                                true, 0.10f, 0.25f, false, 0f, 0f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[서브1 행동-1] 즉각반격 5.0Hz 앵커 (FM 10%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🔨 2. 벌주기 (미로기관 파쇄): 33.0 Hz 척추 압박",
                "척추 추간판 압박 및 미로기관 파쇄 | AM 18% (2.00Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.18f, 2.00f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[서브1 행동-2] 벌주기 33.0Hz 척추압박 (AM 18%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏢 3. 출근 12시간 무인방어 (v2): 5.0Hz / 22.0Hz 교대",
                "v2 독자 대역 순환 신경 피로 극대화 | FM 10% / AM 18% | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.0f, 5.0f, 0f, 0f, 0f,
                                true, 0.10f, 0.25f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브1 행동-3] 출근 12h 5/22Hz 교대",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🚶 4. 일상 외출 자동 순항 (v2): 16.0 Hz 불쾌감 앵커",
                "2~4시간 외출 시 기저 둔통 유지 | FM 5% (0.40Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.0f, 0f, 0f, 0f, 0f,
                                true, 0.05f, 0.40f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브1 행동-4] 일상외출 16.0Hz 불쾌감앵커 (FM)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🏰 5. 초장기 외출 (48h+ v2): 16.0 Hz 초저발열 앵커",
                "발열 억제 프로파일 48시간 무인 안정 운용 | FM 4% (0.30Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.0f, 0f, 0f, 0f, 0f,
                                true, 0.04f, 0.30f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브1 행동-5] 초장기 48h+ 16Hz 초저발열",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "💥 6. 상대 내성 완전파괴 (v2): 5.0Hz ↔ 22.0Hz 도약",
                "주파수 도약 및 예측 불가능 타격 | FM 8% (0.60Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.0f, 5.0f, 0f, 0f, 0f,
                                true, 0.08f, 0.60f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 1, 0, false, 13,
                                "[서브1 행동-6] 내성완전파괴 5↔22Hz 도약 (FM)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧠 7. 상대 정신 집중차단 (v2): 8.0 Hz 알파파 차단",
                "알파파 차단 두통 및 인지 마비 유도 | FM 6% (0.50Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.0f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 0.50f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브1 행동-7] 정신집중차단 8.0Hz 알파파차단",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🛌 8. 수면 시간 집중압박 (v2): 16.0 Hz 골도 앵커",
                "바닥 미세 진동 수면 단계 파괴 | FM 5% (0.40Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 16.0f, 0f, 0f, 0f, 0f,
                                true, 0.05f, 0.40f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브1 행동-8] 수면시간 16.0Hz 골도앵커",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🛡️ 9. 3시간 이상 기기보호 (v2): 33.0 Hz (45분/15분)",
                "AM 펄스 해머링 전력 효율 극대화 | AM 15% (1.50Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 1.50f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브1 행동-9] 3시간 기기보호 33Hz (45/15분)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 3] v2 고유 척추/골반 특수 프리셋
        addSectionHeaderInsideFrame(mFrameSub1Container, "3️⃣ [파트 3] 📊 v2 고유 척추/골반 특수 프리셋", "#10B981");

        addFullPresetButton(mFrameSub1Container,
                "🦴 척추 추간판 압박 요동: 33.0 Hz 단독",
                "척추 추간판 압박 요동 단독 직타 | FM 6% (1.80Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 33.0f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 1.80f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브1 특수-1] 척추추간판압박 33.0Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🧬 골반저 초저속 펄스: 22.0 Hz 단독",
                "골반 내 장기 초저속 펄스 단독 타격 | AM 18% (0.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 22.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.18f, 0.80f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브1 특수-2] 골반저 초저속펄스 22.0Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub1Container,
                "🌊 간·담낭 초저역 서파: 5.0 Hz 단독",
                "간·담낭 초저역 서파 단독 요동 | FM 10% (0.25Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 5.0f, 0f, 0f, 0f, 0f,
                                true, 0.10f, 0.25f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브1 특수-3] 간담낭초저역서파 5.0Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
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
                "안구 안와 떨림 펄스 해머 | AM 15% (2.20Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 2.20f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브2 스케줄-1] 아침 19.0Hz 안와펄스 (AM 15%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "☀️ 2. 오후 (13:00~19:00 v3): 13.5 Hz 앵커 직타",
                "슐만 2차 고조파 척추 요동 앵커 | FM 7% (0.75Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.5f, 0f, 0f, 0f, 0f,
                                true, 0.07f, 0.75f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브2 스케줄-2] 오후 13.5Hz 슐만앵커 (FM 7%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌆 3. 저녁 (19:00~01:00 v3): 26.5 Hz 앵커 직타",
                "흉골/폐 기계 진동 펄스 앵커 | AM 16% (1.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FB923C",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.5f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.16f, 1.80f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브2 스케줄-3] 저녁 26.5Hz 흉골펄스 (AM 16%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🌙 4. 취침 (01:00~07:00 v3): 8.5 Hz 앵커 직타",
                "알파파 강제 각성 요동 앵커 | FM 6% (0.40Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.5f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 0.40f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브2 스케줄-4] 취침 8.5Hz 각성앵커 (FM 6%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 2] 사용자 행동별 실전 맞춤 시나리오 (v3)
        addSectionHeaderInsideFrame(mFrameSub2Container, "2️⃣ [파트 2] 🎯 사용자 행동별 실전 맞춤 시나리오 (v3)", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "⚡ 1. 상대 공격 즉각 반격 (v3): 19.0 Hz 안구 공진 앵커",
                "안구 19Hz AM 펄스 즉각 가진 | AM 15% (2.20Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 2.20f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[서브2 행동-1] 즉각반격 19.0Hz 안구공진 (AM 15%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🔨 2. 벌주기 (흉곽 슐만 파쇄): 13.5 Hz 슐만 앵커",
                "슐만 2차 고조파 척추/흉곽 집중 타격 | FM 7% (0.75Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +6dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F87171",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.5f, 0f, 0f, 0f, 0f,
                                true, 0.07f, 0.75f, false, 0f, 0f, false, 0f, 0f,
                                6.0f, 1, 0, false, 13,
                                "[서브2 행동-2] 벌주기 13.5Hz 슐만파쇄 (FM 7%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏢 3. 출근 12시간 무인방어 (v3): 13.5Hz / 26.5Hz 교대 (600초)",
                "3주차 고유 주파수 및 3기기 변조 조화 | FM 7% / AM 16% | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.5f, 13.5f, 0f, 0f, 0f,
                                true, 0.07f, 0.75f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브2 행동-3] 출근 12h 13.5/26.5Hz 교대",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🚶 4. 일상 외출 자동 순항 (v3): 19.0 Hz 안와 앵커",
                "2~4시간 외출 시 시야 피로 가중 | AM 12% (1.80Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#4ADE80",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.12f, 1.80f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브2 행동-4] 일상외출 19.0Hz 안와앵커 (AM 12%)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🏰 5. 초장기 외출 (48h+ v3): 8.5 Hz 초저발열 앵커",
                "초저역 앵커 48시간 무인 요새화 | FM 5% (0.35Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#818CF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.5f, 0f, 0f, 0f, 0f,
                                true, 0.05f, 0.35f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브2 행동-5] 초장기 48h+ 8.5Hz 초저발열",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "💥 6. 상대 내성 완전파괴 (v3): 13.5Hz ↔ 26.5Hz 도약",
                "광대역 카오스 도약 복합 조화 | FM 8% (0.60Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 아날로그 소프트 새추레이터 | 240W 스테레오",
                "#F43F5E",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.5f, 13.5f, 0f, 0f, 0f,
                                true, 0.08f, 0.60f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 1, 0, false, 13,
                                "[서브2 행동-6] 내성완전파괴 13.5↔26.5Hz 도약",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🧠 7. 상대 정신 집중차단 (v3): 19.0 Hz 안와 공진 앵커",
                "안구 공진으로 시각/청각 집중 원천 차단 | AM 15% (2.20Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#A78BFA",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 2.20f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브2 행동-7] 정신집중차단 19.0Hz 안와공진",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🛌 8. 수면 시간 집중압박 (v3): 8.5 Hz 각성 유발 앵커",
                "8.5Hz 각성 유도로 수면 단계 파괴 | FM 6% (0.40Hz) | Vol 11",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 8.5f, 0f, 0f, 0f, 0f,
                                true, 0.06f, 0.40f, false, 0f, 0f, false, 0f, 0f,
                                0f, 0, 0, false, 11,
                                "[서브2 행동-8] 수면시간 8.5Hz 각성앵커",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🛡️ 9. 3시간 이상 기기보호 (v3): 19.0 Hz (50분/10분)",
                "AM 펄스 듀티 사이클 동기화로 발열 45도 이하 유지 | AM 12% (1.50Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#10B981",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.12f, 1.50f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브2 행동-9] 3시간 기기보호 19Hz (50/10분)",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        // [파트 3] v3 고유 안구/흉골 공진 특수 프리셋
        addSectionHeaderInsideFrame(mFrameSub2Container, "3️⃣ [파트 3] 📊 v3 고유 안구/흉골 공진 특수 프리셋", "#A855F7");

        addFullPresetButton(mFrameSub2Container,
                "👁️ 안구 안와 공진 펄스: 19.0 Hz 단독",
                "안구 안와 공진 펄스 해머 단독 타격 | AM 15% (2.20Hz) | Vol 12",
                "🎛️ Vorta DSP: 부스트 0dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#38BDF8",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 19.0f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.15f, 2.20f, false, 0f, 0f,
                                0f, 0, 0, false, 12,
                                "[서브2 특수-1] 안구안와공진 19.0Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "⚡ 슐만 2차 고조파 척추 락온: 13.5 Hz 단독",
                "슐만 2차 고조파 척추 요동 앵커 단독 락온 | FM 7% (0.75Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#FACC15",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 13.5f, 0f, 0f, 0f, 0f,
                                true, 0.07f, 0.75f, false, 0f, 0f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브2 특수-2] 슐만 2차고조파 13.5Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        addFullPresetButton(mFrameSub2Container,
                "🫁 흉골/폐 기계 진동 펄스: 26.5 Hz 단독",
                "흉골/폐 기계 진동 펄스 앵커 단독 가진 | AM 16% (1.80Hz) | Vol 13",
                "🎛️ Vorta DSP: 부스트 +3dB | 트루피크 -1.5dBFS | 240W 스테레오",
                "#EF4444",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPreset(false, 26.5f, 0f, 0f, 0f, 0f,
                                false, 0f, 0f, true, 0.16f, 1.80f, false, 0f, 0f,
                                3.0f, 0, 0, false, 13,
                                "[서브2 특수-3] 흉골/폐 기계진동 26.5Hz 단독",
                                "스마토 앰프 #2: 단일 볼륨 11시~1시 (순수 사인파 240W 스테레오)");
                    }
                });

        contentPane.addView(mFrameSub2Container);

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

        for (int i = 0; i < 4; i++) {
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

        if (modeIndex == 0) { // 전체
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.VISIBLE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mSplitDivider2.setVisibility(View.VISIBLE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
        } else if (modeIndex == 1) { // 메인
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.GONE);
        } else if (modeIndex == 2) { // 서브1
            mFrameMainContainer.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.GONE);
        } else if (modeIndex == 3) { // 서브2
            mFrameMainContainer.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
        }
    }

    private TextView createSplitTabButton(LinearLayout parent, String text, float weight, View.OnClickListener listener) {
        float density = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(13);
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
        bg.setStroke((int)(1 * density), Color.parseColor("#334155"));
        card.setBackground(bg);
        card.setPadding((int)(12 * density), (int)(10 * density), (int)(12 * density), (int)(10 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(3 * density), 0, (int)(5 * density));
        card.setLayoutParams(lp);

        TextView tvMain = new TextView(this);
        tvMain.setText(mainText);
        tvMain.setTextSize(13);
        tvMain.setTextColor(Color.parseColor("#FFFFFF"));
        tvMain.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvMain);

        TextView tvSub = new TextView(this);
        tvSub.setText(subText);
        tvSub.setTextSize(11);
        tvSub.setTextColor(Color.parseColor("#CBD5E1"));
        tvSub.setPadding(0, (int)(2 * density), 0, (int)(2 * density));
        card.addView(tvSub);

        TextView tvDsp = new TextView(this);
        tvDsp.setText(dspText);
        tvDsp.setTextSize(10);
        tvDsp.setTextColor(Color.parseColor(accentColor));
        tvDsp.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvDsp);

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

        String partTag = "[메인 파트]";
        if (mCurrentSplitMode == 2 || title.contains("서브1") || title.contains("상황") || title.contains("보복") || title.contains("심리")) {
            partTag = "[서브1 파트]";
        } else if (mCurrentSplitMode == 3 || title.contains("서브2") || title.contains("장기") || title.contains("취침") || title.contains("출근")) {
            partTag = "[서브2 파트]";
        } else if (mCurrentSplitMode == 1) {
            partTag = "[메인 파트]";
        }
        S7AudioGeneratorService.sActivePart = partTag;
        S7AudioGeneratorService.sScenarioName = title;

        it.putExtra("title", title);
        it.putExtra("part", partTag);
        it.putExtra("scenario", title);
        it.putExtra("knob_guide", knobGuide);

        S7AudioGeneratorService.sIsPlaying = true;
        S7AudioGeneratorService.sModeTitle = title;
        if (sweep) {
            S7AudioGeneratorService.sIsSweepMode = true;
            S7AudioGeneratorService.sSweepMin = sMin;
            S7AudioGeneratorService.sSweepMax = sMax;
            S7AudioGeneratorService.sInstantFreq = sMin;
        } else {
            S7AudioGeneratorService.sIsSweepMode = false;
            S7AudioGeneratorService.sTargetFreq = freq;
            S7AudioGeneratorService.sInstantFreq = freq;
        }

        startService(it);
        Toast.makeText(this, title + " 발진 시작", Toast.LENGTH_SHORT).show();
        syncTelemetryUi();
    }

    private void syncTelemetryUi() {
        boolean isPlaying = S7AudioGeneratorService.sIsPlaying;
        float density = getResources().getDisplayMetrics().density;

        if (mStatusCardLayout != null) {
            GradientDrawable scBg = new GradientDrawable();
            scBg.setCornerRadius(8 * density);
            scBg.setColor(Color.parseColor("#111827")); // AMOLED Deep Slate (Never green background!)
            if (isPlaying) {
                scBg.setStroke((int)(2 * density), Color.parseColor("#10B981")); // Vivid Emerald Green border
            } else {
                scBg.setStroke((int)(2 * density), Color.parseColor("#EF4444")); // Warning Crimson Red border
            }
            mStatusCardLayout.setBackground(scBg);
        }

        if (mStatusTrafficLight != null) {
            GradientDrawable trafficDot = new GradientDrawable();
            trafficDot.setShape(GradientDrawable.OVAL);
            if (isPlaying) {
                trafficDot.setColor(Color.parseColor("#00E676")); // Neon Green Traffic Light
            } else {
                trafficDot.setColor(Color.parseColor("#FF3366")); // Neon Red Traffic Light
            }
            mStatusTrafficLight.setImageDrawable(trafficDot);
        }

        if (mStatusMode != null) {
            if (isPlaying) {
                mStatusMode.setText("상태: [발진 중] " + S7AudioGeneratorService.sActivePart);
                mStatusMode.setTextColor(Color.parseColor("#00E676")); // Neon Traffic Light Green
            } else {
                mStatusMode.setText("상태: [작동 중지] 멈춤 (대기 상태)");
                mStatusMode.setTextColor(Color.parseColor("#FF3366")); // Neon Traffic Light Red
            }
        }

        if (mStatusScenario != null) {
            if (isPlaying) {
                mStatusScenario.setText("시나리오: " + S7AudioGeneratorService.sScenarioName);
                mStatusScenario.setTextColor(Color.parseColor("#00F0FF")); // Electric Cyan
            } else {
                mStatusScenario.setText("시나리오: 대기 중 (선택 없음)");
                mStatusScenario.setTextColor(Color.parseColor("#64748B"));
            }
        }

        if (mStatusFreq != null) {
            if (!isPlaying) {
                mStatusFreq.setText("발진: 0.00 Hz (출력 차단 / MUTE)");
                mStatusFreq.setTextColor(Color.parseColor("#94A3B8")); // Muted Gray
            } else if (S7AudioGeneratorService.sIsSweepMode) {
                mStatusFreq.setText(String.format("발진: %.2f Hz 실시간 (%.1f~%.1fHz 스윕)",
                        S7AudioGeneratorService.sInstantFreq, S7AudioGeneratorService.sSweepMin, S7AudioGeneratorService.sSweepMax));
                mStatusFreq.setTextColor(Color.parseColor("#FFD700")); // High-brightness & saturation Green
            } else {
                mStatusFreq.setText(String.format("발진: %.2f Hz 직타 고정 (-1.5dBFS True-Peak)",
                        S7AudioGeneratorService.sTargetFreq));
                mStatusFreq.setTextColor(Color.parseColor("#FFD700")); // High-brightness & saturation Green
            }
        }

        if (mStatusMod != null) {
            if (!isPlaying) {
                mStatusMod.setText("변조: 비활성 (주파수 신호 멈춤) | 240W 스테레오");
                mStatusMod.setTextColor(Color.parseColor("#64748B"));
            } else {
                StringBuilder sb = new StringBuilder("변조: ");
                boolean hasMod = false;
                if (S7AudioGeneratorService.sFmEnabled) {
                    sb.append(String.format("FM %.1f%%(%.2fHz) ", S7AudioGeneratorService.sFmDepth * 100f, S7AudioGeneratorService.sFmRateHz));
                    hasMod = true;
                }
                if (S7AudioGeneratorService.sAmEnabled) {
                    sb.append(String.format("AM %.1f%%(%.2fHz) ", S7AudioGeneratorService.sAmDepth * 100f, S7AudioGeneratorService.sAmRateHz));
                    hasMod = true;
                }
                if (S7AudioGeneratorService.sRmEnabled) {
                    sb.append(String.format("RM %.1f%%(%.2fHz) ", S7AudioGeneratorService.sRmDepth * 100f, S7AudioGeneratorService.sRmRateHz));
                    hasMod = true;
                }
                if (!hasMod) sb.append("무변조 순수 사인파 ");
                sb.append("| 240W 스테레오");
                mStatusMod.setText(sb.toString());
                mStatusMod.setTextColor(Color.parseColor("#F472B6"));
            }
        }

        if (mStatusKnob != null) {
            mStatusKnob.setText("앰프 노브: 스마토 앰프 #2 [단일 볼륨 다이얼 1개] 11시~1시 (60~75% 무왜곡 순수 파괴력 구동)");
        }
    }
}
'''

target_path = r"d:\D_Workspace_NB\주파수\vorta_s7_app\src\com\vorta\s7frequency\MainActivity.java"
with open(target_path, "w", encoding="utf-8") as f:
    f.write(java_code)

print(f"Generated clean MainActivity.java for S7 v2.9 at: {target_path}")
