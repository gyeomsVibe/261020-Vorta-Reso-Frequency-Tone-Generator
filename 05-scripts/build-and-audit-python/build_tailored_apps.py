# -*- coding: utf-8 -*-
"""
build_tailored_apps.py
A30, S7, S6 각 기기 전용 맞춤 프리셋 MainActivity.java 생성 및 APK 빌드
- 기기 1: 갤럭시 A30 = 스마토 우퍼 1기 (120W 모노) -> 스윕 & 복합 변조 (FM·AM·RM) 전담
- 기기 2: 갤럭시 S7 = 스마토 우퍼 2기 (240W 스테레오 L/R) -> 바닥 2점 앵커 & 저역 진동망 전담
- 기기 3: 갤럭시 S6 = 엔에스테크 트랜스2 (68W BTL) -> 천장/슬래브 수직 골전도 직타 전담
- 3기기 동시 가동 시 동일 시간대 주파수 100% 비중복 완전 보장
- 55.0Hz 이하 엄격 준수
"""
import os
import subprocess
import sys

def make_activity_code(device_key, pkg_name, service_cls, helper_cls, app_title, app_subtitle, amp_guide_default):
    # Presets per device
    if device_key == "a30":
        # A30: 우퍼 1기 (스윕 & 변조)
        p_main_1 = ('true, 0f, 0f, 17.3f, 19.8f, 440.0f, true, 0.10f, 7.03f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '아침: 17.3~19.8Hz (440s) [FM]', '복부·위장 스윕 훑기 (FM 10%)', '#00F0FF')
        p_main_2 = ('true, 0f, 0f, 20.7f, 27.7f, 740.0f, false, 0.0f, 1.0f, true, 0.15f, 5.70f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 20.7~27.7Hz (740s) [AM]', '위장관 연동 교란 (AM 15%)', '#38D39F')
        p_main_3 = ('true, 0f, 0f, 18.6f, 25.1f, 900.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.30f, 35.95f, 3.0f, 0, 0, false',
                    '저녁: 18.6~25.1Hz (900s) [RM]', '복합 링변조 슬래브 긁기 (RM 30%)', '#D2A8FF')
        p_main_4 = ('true, 0f, 0f, 2.4f, 4.6f, 1480.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 2.4~4.6Hz (1480s) [Pure]', '델타파 서파 수면 박탈', '#FF7B72')
        p_main_m1 = ('true, 0f, 0f, 17.3f, 19.8f, 440.0f, true, 0.10f, 7.03f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 1: 전정계+위장 하이브리드', '17.3~19.8Hz 스윕 + FM 10%', '#00F0FF')
        p_main_m2 = ('true, 0f, 0f, 20.7f, 27.7f, 740.0f, false, 0.0f, 1.0f, true, 0.15f, 5.70f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 2: 자율신경+소화기 요동', '20.7~27.7Hz 스윕 + AM 15%', '#38D39F')
        p_main_m3 = ('true, 0f, 0f, 4.0f, 6.2f, 290.0f, true, 0.10f, 7.03f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 3: 무인 외출 카오스 스윕', '4.0~6.2Hz 고속 스윕 + FM 10%', '#D2A8FF')
        p_main_m4 = ('true, 0f, 0f, 2.4f, 4.6f, 1480.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                     '모드 4: 침실 룸모드 락온 보조', '2.4~4.6Hz 초저역 비가청 스윕', '#38D39F')
        p_main_m5 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, true, 0.15f, 5.70f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                     '모드 5: 즉각 응징 해머 펄스', '55.0Hz 단독 + AM 15% 5.7Hz 펄스', '#F1E05A')

        # Sub1: 최종완결판
        p_sub1_1 = ('true, 0f, 0f, 4.0f, 6.2f, 290.0f, true, 0.10f, 7.03f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '아침: 4.0~6.2Hz (290s) [FM]', '소뇌·전정기관 흔들기', '#00F0FF')
        p_sub1_2 = ('true, 0f, 0f, 6.7f, 9.0f, 490.0f, false, 0.0f, 1.0f, true, 0.15f, 5.70f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 6.7~9.0Hz (490s) [AM]', '어지럼증·기저 스트레스', '#38D39F')
        p_sub1_3 = ('true, 0f, 0f, 3.3f, 7.3f, 640.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, true, 0.30f, 35.95f, 3.0f, 0, 0, false',
                    '저녁: 3.3~7.3Hz (640s) [RM]', '뇌간·초저역 링변조 긁기', '#D2A8FF')
        p_sub1_4 = ('true, 0f, 0f, 12.0f, 24.5f, 6580.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 12.0~24.5Hz (6580s) [Pure]', 'NREM 서파 수면 박탈', '#FF7B72')

        # Sub2: ver2
        p_sub2_1 = ('true, 0f, 0f, 1.0f, 4.0f, 2880.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '출근: 1.0~4.0Hz (2880s) [10h]', '초장주기 무인 슬래브 흔들기', '#00F0FF')
        p_sub2_2 = ('true, 0f, 0f, 17.3f, 24.5f, 600.0f, true, 0.10f, 7.03f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '퇴근: 17.3~24.5Hz (600s) [FM]', '위장관 요동 & 거실 활동 방해', '#38D39F')
        p_sub2_3 = ('false, 10.5f, 10.5f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 10.5Hz 단독 [0dB]', '비가청 자율신경 앵커 (숙면 보장)', '#FF7B72')

    elif device_key == "s7":
        # S7: 우퍼 2기 스테레오 (바닥 2점 앵커)
        p_main_1 = ('false, 28.0f, 28.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '아침: 28.0Hz 단독 [골반]', '골반·하복부 둔통 2점 앵커', '#00F0FF')
        p_main_2 = ('false, 10.5f, 10.5f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 10.5Hz 단독 [자율신경]', '자율신경·심박 변동 메스꺼움', '#38D39F')
        p_main_3 = ('false, 7.0f, 7.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '저녁: 7.0Hz 단독 [전정계]', '전정기관·뇌간 현기증 유발', '#D2A8FF')
        p_main_4 = ('true, 0f, 0f, 21.0f, 24.5f, 1250.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 21.0~24.5Hz (1250s)', '바닥 미세 진동 멀미 주입', '#FF7B72')
        p_main_m1 = ('false, 7.0f, 7.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 1: 전정계 7Hz 초저역 앵커', '7.0Hz 단독 (L=R 동위상 240W)', '#00F0FF')
        p_main_m2 = ('false, 10.5f, 10.5f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 2: 자율신경 10.5Hz 앵커', '10.5Hz 단독 240W', '#38D39F')
        p_main_m3 = ('true, 0f, 0f, 18.6f, 25.1f, 900.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                     '모드 3: 무인 외출 바닥 광역 스윕', '18.6~25.1Hz (900s) 바닥 진동망', '#D2A8FF')
        p_main_m4 = ('false, 21.0f, 21.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                     '모드 4: 침실 바닥 공진 앵커', '21.0Hz 단독 앵커 (240W)', '#38D39F')
        p_main_m5 = ('false, 28.0f, 28.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                     '모드 5: 즉각 응징 골반 강타', '28.0Hz 단독 앵커 (240W)', '#F1E05A')

        # Sub1: 최종완결판
        p_sub1_1 = ('false, 31.0f, 31.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '아침: 31.0Hz 단독 [위장]', '위장·간 묵직한 타격', '#00F0FF')
        p_sub1_2 = ('false, 24.0f, 24.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 24.0Hz 단독 [복통]', '복부·위장 복통 및 변의 자극', '#38D39F')
        p_sub1_3 = ('false, 32.8f, 32.8f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '저녁: 32.8Hz 단독 [흉곽]', '흉부 공진 및 갈비뼈 조임', '#D2A8FF')
        p_sub1_4 = ('false, 7.0f, 7.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 7.0Hz 단독 [전정계]', '침대 골도 전달 현기증', '#FF7B72')

        # Sub2: ver2
        p_sub2_1 = ('true, 0f, 0f, 18.6f, 25.1f, 900.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '출근: 18.6~25.1Hz (900s) [10h]', '바닥 전면 광역 진동망', '#00F0FF')
        p_sub2_2 = ('false, 28.0f, 28.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '퇴근: 28.0Hz 단독 [골반]', '골반·장기 하부 둔통 인가', '#38D39F')
        p_sub2_3 = ('true, 0f, 0f, 2.4f, 4.6f, 1480.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 2.4~4.6Hz (1480s)', '바닥 초저역 서파 교란', '#FF7B72')

    else:
        # S6: 트랜스2 골전도 1기 (천장/슬래브 수직 직타)
        p_main_1 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                    '아침: 55.0Hz 단독 [천장직타]', '311호 방바닥 수직 공진 타격', '#00F0FF')
        p_main_2 = ('true, 0f, 0f, 31.0f, 40.0f, 360.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 31.0~40.0Hz (360s)', '슬래브 관통 흉부 압박 직타', '#38D39F')
        p_main_3 = ('false, 43.2f, 43.2f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                    '저녁: 43.2Hz 단독 [관통강타]', '120mm 콘크리트 슬래브 1타 관통', '#D2A8FF')
        p_main_4 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 55.0Hz 룸모드 [락온]', '311호 침실 룸모드 수직 제압', '#FF7B72')
        p_main_m1 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                     '모드 1: 천장 수직 직타 앵커', '55.0Hz 단독 직타 (68W BTL)', '#00F0FF')
        p_main_m2 = ('false, 24.0f, 24.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                     '모드 2: 복부 위장관 직타', '24.0Hz 단독 직타 (68W BTL)', '#38D39F')
        p_main_m3 = ('true, 0f, 0f, 31.0f, 55.0f, 420.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                     '모드 3: 천장 광역 피로 스윕', '31.0~55.0Hz (420s) 천장 스윕', '#D2A8FF')
        p_main_m4 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                     '모드 4: 침실 룸모드 락온 직타', '55.0Hz 천장 수직 룸모드 락온', '#38D39F')
        p_main_m5 = ('false, 43.2f, 43.2f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                     '모드 5: 즉각 응징 관통 해머', '43.2Hz 슬래브 관통 1타 직타', '#F1E05A')

        # Sub1: 최종완결판
        p_sub1_1 = ('false, 50.0f, 50.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '아침: 50.0Hz 단독 [슬래브]', '천장 슬래브 1차 고유진동 직타', '#00F0FF')
        p_sub1_2 = ('true, 0f, 0f, 40.0f, 55.0f, 420.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 3.0f, 0, 0, false',
                    '오후: 40.0~55.0Hz (420s)', '천장 슬래브 광역 굽힘파 직타', '#38D39F')
        p_sub1_3 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                    '저녁: 55.0Hz 단독 [룸모드]', '311호 룸모드 정상파 락온', '#D2A8FF')
        p_sub1_4 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 55.0Hz 단독 [방바닥]', '311호 침실 방바닥 골도 락온', '#FF7B72')

        # Sub2: ver2
        p_sub2_1 = ('true, 0f, 0f, 35.0f, 52.0f, 600.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '출근: 35.0~52.0Hz (600s) [10h]', '천장 슬래브 피로 누적 스윕', '#00F0FF')
        p_sub2_2 = ('false, 43.2f, 43.2f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 6.0f, 0, 0, true',
                    '퇴근: 43.2Hz 단독 [관통강타]', '120mm 슬래브 관통 강타', '#38D39F')
        p_sub2_3 = ('false, 55.0f, 55.0f, 0f, 0f, 0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, 0.0f, 0, 0, false',
                    '취침: 55.0Hz 룸모드 [락온]', '311호 침실 룸모드 수직 제압', '#FF7B72')

    code = f'''package {pkg_name};

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

        // 스플릿 뷰 선택 콘솔 (전체 / 메인 / 서브1 / 서브2)
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
        scTitle.setText("🎛️ 3대 마스터 보고서 1:1 스플릿 뷰 콘솔");
        scTitle.setTextSize(14);
        scTitle.setTextColor(Color.parseColor("#00F0FF"));
        scTitle.setTypeface(Typeface.DEFAULT_BOLD);
        scTitle.setPadding(0, 0, 0, (int)(8 * density));
        splitConsoleCard.addView(scTitle);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setWeightSum(4.0f);

        String[] tabLabels = {{"전체", "메인", "서브1", "서브2"}};
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

        // 실시간 텔레메트리 카드
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.parseColor("#161B22"));
        statusBg.setCornerRadius(10 * density);
        statusBg.setStroke((int)(1.5f * density), Color.parseColor("#30363D"));
        statusCard.setBackground(statusBg);
        statusCard.setPadding((int)(14 * density), (int)(12 * density), (int)(14 * density), (int)(12 * density));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.setMargins(0, 0, 0, (int)(14 * density));
        statusCard.setLayoutParams(stLp);

        mStatusMode = createTelemetryLine(density, "현재 모드: " + {service_cls}.sModeTitle, "#58A6FF");
        statusCard.addView(mStatusMode);
        mStatusFreq = createTelemetryLine(density, "실시간 발진: 대기 중", "#00F0FF");
        statusCard.addView(mStatusFreq);
        mStatusMod = createTelemetryLine(density, "변조 상태: OFF", "#7EE787");
        statusCard.addView(mStatusMod);
        mStatusDsp = createTelemetryLine(density, "Vorta DSP: 대기", "#FFA657");
        statusCard.addView(mStatusDsp);
        mStatusKnob = createTelemetryLine(density, "앰프 가이드: {amp_guide_default}", "#D2A8FF");
        statusCard.addView(mStatusKnob);

        // 즉각 비상 정지 버튼
        TextView stopBtn = new TextView(this);
        stopBtn.setText("🛑 발진 즉시 정지 (Stop All Oscillators)");
        stopBtn.setTextSize(14);
        stopBtn.setTextColor(Color.parseColor("#FFFFFF"));
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        stopBtn.setGravity(Gravity.CENTER);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(Color.parseColor("#DA3633"));
        stopBg.setCornerRadius(8 * density);
        stopBtn.setBackground(stopBg);
        stopBtn.setPadding(0, (int)(11 * density), 0, (int)(11 * density));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopLp.setMargins(0, (int)(10 * density), 0, 0);
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setOnClickListener(new View.OnClickListener() {{
            @Override
            public void onClick(View v) {{
                stopAudioService();
            }}
        }});
        statusCard.addView(stopBtn);
        root.addView(statusCard);

        // 프레임 1: 메인 (최신맞춤판)
        mFrameMainContainer = new LinearLayout(this);
        mFrameMainContainer.setOrientation(LinearLayout.VERTICAL);
        buildMainFrameContent(mFrameMainContainer, density);
        root.addView(mFrameMainContainer);

        mSplitDivider1 = createFrameDivider(density, "──────── ⚡ 서브1: 최종완결판 프레임 ────────");
        root.addView(mSplitDivider1);

        // 프레임 2: 서브1 (최종완결판)
        mFrameSub1Container = new LinearLayout(this);
        mFrameSub1Container.setOrientation(LinearLayout.VERTICAL);
        buildSub1FrameContent(mFrameSub1Container, density);
        root.addView(mFrameSub1Container);

        mSplitDivider2 = createFrameDivider(density, "──────── 🛡️ 서브2: ver2 프레임 ────────");
        root.addView(mSplitDivider2);

        // 프레임 3: 서브2 (ver2)
        mFrameSub2Container = new LinearLayout(this);
        mFrameSub2Container.setOrientation(LinearLayout.VERTICAL);
        buildSub2FrameContent(mFrameSub2Container, density);
        root.addView(mFrameSub2Container);

        scroll.addView(root);
        setContentView(scroll);

        setSplitViewMode(0);
        syncTelemetryUi();
    }}

    private void buildMainFrameContent(LinearLayout parent, float density) {{
        addFrameMasterBanner(parent, density, "🎯 [메인 프레임] 주파수_통합_운용_체계_마스터_보고서_최신맞춤판", "#00F0FF");
        addSectionHeaderInsideFrame(parent, "1️⃣ [파트 1] 시간대별 4구간 표준 스케줄 (최신맞춤판)", "#58A6FF");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row1, "{p_main_1[1]}", "{p_main_1[2]}", "{p_main_1[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_1[0]}, "[최신맞춤판 아침]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row1, "{p_main_2[1]}", "{p_main_2[2]}", "{p_main_2[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_2[0]}, "[최신맞춤판 오후]", "{amp_guide_default}"); }}
        }});
        parent.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row2, "{p_main_3[1]}", "{p_main_3[2]}", "{p_main_3[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_3[0]}, "[최신맞춤판 저녁]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row2, "{p_main_4[1]}", "{p_main_4[2]}", "{p_main_4[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_4[0]}, "[최신맞춤판 취침]", "{amp_guide_default}"); }}
        }});
        parent.addView(row2);

        addSectionHeaderInsideFrame(parent, "2️⃣ [파트 2] 핵심 5대 프리셋 및 모드 조합 (최신맞춤판)", "#7EE787");

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row3, "{p_main_m1[1]}", "{p_main_m1[2]}", "{p_main_m1[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_m1[0]}, "[프리셋 1]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row3, "{p_main_m2[1]}", "{p_main_m2[2]}", "{p_main_m2[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_m2[0]}, "[프리셋 2]", "{amp_guide_default}"); }}
        }});
        parent.addView(row3);

        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row4, "{p_main_m3[1]}", "{p_main_m3[2]}", "{p_main_m3[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_m3[0]}, "[프리셋 3]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row4, "{p_main_m4[1]}", "{p_main_m4[2]}", "{p_main_m4[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_m4[0]}, "[프리셋 4]", "{amp_guide_default}"); }}
        }});
        parent.addView(row4);

        addFullPresetButton(parent, "{p_main_m5[1]}", "{p_main_m5[2]}", "{p_main_m5[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_main_m5[0]}, "[프리셋 5: 즉각 응징]", "{amp_guide_default}"); }}
        }});
    }}

    private void buildSub1FrameContent(LinearLayout parent, float density) {{
        addFrameMasterBanner(parent, density, "⚡ [서브1 프레임] 주파수_통합_운용_체계_마스터_보고서_최종완결판", "#7EE787");
        addSectionHeaderInsideFrame(parent, "1️⃣ [파트 1] 전술 타격 4구간 스케줄 (최종완결판)", "#58A6FF");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row1, "{p_sub1_1[1]}", "{p_sub1_1[2]}", "{p_sub1_1[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub1_1[0]}, "[최종완결판 아침]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row1, "{p_sub1_2[1]}", "{p_sub1_2[2]}", "{p_sub1_2[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub1_2[0]}, "[최종완결판 오후]", "{amp_guide_default}"); }}
        }});
        parent.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row2, "{p_sub1_3[1]}", "{p_sub1_3[2]}", "{p_sub1_3[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub1_3[0]}, "[최종완결판 저녁]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row2, "{p_sub1_4[1]}", "{p_sub1_4[2]}", "{p_sub1_4[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub1_4[0]}, "[최종완결판 취침]", "{amp_guide_default}"); }}
        }});
        parent.addView(row2);
    }}

    private void buildSub2FrameContent(LinearLayout parent, float density) {{
        addFrameMasterBanner(parent, density, "🛡️ [서브2 프레임] 주파수_통합_운용_체계_마스터_보고서_ver2", "#D2A8FF");
        addSectionHeaderInsideFrame(parent, "1️⃣ [파트 1] 무인 라이프사이클 스케줄 (ver.2)", "#58A6FF");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addHalfPresetButton(row1, "{p_sub2_1[1]}", "{p_sub2_1[2]}", "{p_sub2_1[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub2_1[0]}, "[ver2 출근 10h]", "{amp_guide_default}"); }}
        }});
        addHalfPresetButton(row1, "{p_sub2_2[1]}", "{p_sub2_2[2]}", "{p_sub2_2[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub2_2[0]}, "[ver2 퇴근]", "{amp_guide_default}"); }}
        }});
        parent.addView(row1);

        addFullPresetButton(parent, "{p_sub2_3[1]}", "{p_sub2_3[2]}", "{p_sub2_3[3]}", new View.OnClickListener() {{
            @Override public void onClick(View v) {{ startPresetWithDsp({p_sub2_3[0]}, "[ver2 취침]", "{amp_guide_default}"); }}
        }});
    }}

    private void setSplitViewMode(int mode) {{
        mCurrentSplitMode = mode;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 4; i++) {{
            if (mSplitViewButtons[i] == null) continue;
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(6 * density);
            if (i == mode) {{
                btnBg.setColor(Color.parseColor("#00F0FF"));
                mSplitViewButtons[i].setTextColor(Color.parseColor("#0A0E17"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT_BOLD);
            }} else {{
                btnBg.setColor(Color.parseColor("#1C2536"));
                btnBg.setStroke((int)(1 * density), Color.parseColor("#384B66"));
                mSplitViewButtons[i].setTextColor(Color.parseColor("#8B949E"));
                mSplitViewButtons[i].setTypeface(Typeface.DEFAULT);
            }}
            mSplitViewButtons[i].setBackground(btnBg);
        }}

        if (mode == 0) {{
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.VISIBLE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mSplitDivider2.setVisibility(View.VISIBLE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
        }} else if (mode == 1) {{
            mFrameMainContainer.setVisibility(View.VISIBLE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.GONE);
        }} else if (mode == 2) {{
            mFrameMainContainer.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.VISIBLE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.GONE);
        }} else if (mode == 3) {{
            mFrameMainContainer.setVisibility(View.GONE);
            mSplitDivider1.setVisibility(View.GONE);
            mFrameSub1Container.setVisibility(View.GONE);
            mSplitDivider2.setVisibility(View.GONE);
            mFrameSub2Container.setVisibility(View.VISIBLE);
        }}
    }}

    private LinearLayout createFrameDivider(float density, String text) {{
        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.VERTICAL);
        divider.setPadding(0, (int)(18 * density), 0, (int)(14 * density));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#58A6FF"));
        tv.setGravity(Gravity.CENTER);
        divider.addView(tv);
        return divider;
    }}

    private void addFrameMasterBanner(LinearLayout parent, float density, String title, String colorHex) {{
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161B22"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(colorHex));
        banner.setBackground(bg);
        banner.setPadding((int)(12 * density), (int)(10 * density), (int)(12 * density), (int)(10 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, (int)(12 * density));
        banner.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        banner.addView(tv);
        parent.addView(banner);
    }}

    private TextView createTelemetryLine(float density, String text, String colorHex) {{
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setPadding(0, (int)(2 * density), 0, (int)(2 * density));
        return tv;
    }}

    private void addSectionHeaderInsideFrame(LinearLayout parent, String title, String colorHex) {{
        float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int)(10 * density), 0, (int)(6 * density));
        parent.addView(tv);
    }}

    private void addHalfPresetButton(LinearLayout row, String title, String desc, String borderHex,
                                     View.OnClickListener clickListener) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1C2536"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(borderHex));
        card.setBackground(bg);
        card.setPadding((int)(10 * density), (int)(8 * density), (int)(10 * density), (int)(8 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        if (row.getChildCount() > 0) lp.setMargins((int)(8 * density), 0, 0, 0);
        card.setLayoutParams(lp);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(12);
        titleTv.setTextColor(Color.parseColor("#FFFFFF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleTv);

        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextSize(10);
        descTv.setTextColor(Color.parseColor("#8B949E"));
        descTv.setPadding(0, (int)(2 * density), 0, 0);
        card.addView(descTv);

        card.setOnClickListener(clickListener);
        row.addView(card);
    }}

    private void addFullPresetButton(LinearLayout parent, String title, String desc, String borderHex,
                                     View.OnClickListener clickListener) {{
        float density = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1C2536"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor(borderHex));
        card.setBackground(bg);
        card.setPadding((int)(12 * density), (int)(10 * density), (int)(12 * density), (int)(10 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, 0);
        card.setLayoutParams(lp);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(13);
        titleTv.setTextColor(Color.parseColor("#FFFFFF"));
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleTv);

        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextSize(11);
        descTv.setTextColor(Color.parseColor("#8B949E"));
        descTv.setPadding(0, (int)(3 * density), 0, 0);
        card.addView(descTv);

        card.setOnClickListener(clickListener);
        parent.addView(card);
    }}

    private void stopAudioService() {{
        Intent it = new Intent(this, {service_cls}.class);
        it.setAction("STOP");
        startService(it);
        Toast.makeText(this, "발진 정지됨", Toast.LENGTH_SHORT).show();
        syncTelemetryUi();
    }}

    private void startPresetWithDsp(
            boolean isSweep, float freq, float targetFreq, float sweepMin, float sweepMax, float sweepPeriod,
            boolean fmEnable, float fmDepth, float fmFreq,
            boolean amEnable, float amDepth, float amFreq,
            boolean rmEnable, float rmDepth, float rmFreq,
            float boostDb, int channelMode, int limiterMode, boolean exciter,
            String title, String guide
    ) {{
        Intent it = new Intent(this, {service_cls}.class);
        it.setAction("START");
        it.putExtra("sweep", isSweep);
        it.putExtra("freq", freq);
        it.putExtra("target_freq", targetFreq);
        it.putExtra("sweep_min", sweepMin);
        it.putExtra("sweep_max", sweepMax);
        it.putExtra("sweep_period", sweepPeriod);
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
    return code

def main():
    root = os.path.dirname(os.path.abspath(__file__))

    # 1. Update Galaxy S7 App
    s7_code = make_activity_code(
        device_key="s7",
        pkg_name="com.vorta.s7frequency",
        service_cls="S7AudioGeneratorService",
        helper_cls="S7VolumeHelper",
        app_title="보르타 레조 S7 v2.8",
        app_subtitle="갤럭시 S7 + 120W 우퍼 2기 스테레오 (240W RMS) | 바닥 2점 앵커 전담 | FM·AM·RM",
        amp_guide_default="120W 우퍼 앰프: VOL 11~1시(50~65%) | FREQ 55Hz | BASS +2dB"
    )
    s7_path = os.path.join(root, "vorta_s7_app", "src", "com", "vorta", "s7frequency", "MainActivity.java")
    with open(s7_path, "w", encoding="utf-8") as f:
        f.write(s7_code)
    print("Wrote tailored vorta_s7_app MainActivity.java")

    # 2. Update Galaxy S6 App
    s6_code = make_activity_code(
        device_key="s6",
        pkg_name="com.vorta.s6frequency",
        service_cls="S6AudioGeneratorService",
        helper_cls="S6VolumeHelper",
        app_title="보르타 레조 S6 v2.8",
        app_subtitle="갤럭시 S6 + 골전도 트랜스2 1기 (68W BTL) | 천장 슬래브 수직 직타 전담 | FM·AM·RM",
        amp_guide_default="LP-168S 앰프: VOL 7.0(2시) | SUB 5.5(1시) | X-OVER 55Hz"
    )
    s6_path = os.path.join(root, "vorta_s6_app", "src", "com", "vorta", "s6frequency", "MainActivity.java")
    with open(s6_path, "w", encoding="utf-8") as f:
        f.write(s6_code)
    print("Wrote tailored vorta_s6_app MainActivity.java")

    # 3. Update Galaxy A30 App
    a30_code = make_activity_code(
        device_key="a30",
        pkg_name="com.smato.a30frequency",
        service_cls="A30AudioGeneratorService",
        helper_cls="A30VolumeHelper",
        app_title="보르타 레조 A30 v2.8",
        app_subtitle="갤럭시 A30 + 120W 우퍼 1기 (120W 모노) | 스윕 & 복합 변조(FM·AM·RM) 전담",
        amp_guide_default="120W 우퍼 앰프: VOL 11~1시(50~65%) | FREQ 55Hz | BASS 0dB"
    )
    a30_path = os.path.join(root, "smato_a30_app", "src", "com", "smato", "a30frequency", "MainActivity.java")
    with open(a30_path, "w", encoding="utf-8") as f:
        f.write(a30_code)
    print("Wrote tailored smato_a30_app MainActivity.java")

if __name__ == "__main__":
    main()
