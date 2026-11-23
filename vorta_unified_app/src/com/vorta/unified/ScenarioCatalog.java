package com.vorta.unified;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Complete 5-category unified acoustic scenario catalog across S6, S7, and A30 (168 unique entries):
 * Restores 100% of legacy device scenarios (S6=72, S7=83, A30=72) + 3 research packs per device.
 * Categories: 메인 | 서브1 | 서브2 | 공통 | 기초
 */
public final class ScenarioCatalog {

    public enum Category {
        MAIN("메인"),
        SUB1("서브1"),
        SUB2("서브2"),
        COMMON("공통"),
        BASIC("기초");

        public final String heading;

        Category(String heading) {
            this.heading = heading;
        }
    }

    public static final class ScenarioSpec {
        public final String id;
        public final VortaModule module;
        public final Category category;
        public final String title;
        public final String effectDesc;
        public final String knobGuide;
        public final boolean isSweep;
        public final float primaryHz;
        public final float secondaryHz;
        public final float sweepMinHz;
        public final float sweepMaxHz;
        public final float sweepPeriodSec;
        public final boolean fmOn;
        public final float fmDepth;
        public final float fmRateHz;
        public final boolean amOn;
        public final float amDepth;
        public final float amRateHz;
        public final boolean rmOn;
        public final float rmDepth;
        public final float rmRateHz;
        public final int channelMode; // 0: L=R stereo, 2: (L+R)/2 BTL bridge mono

        public ScenarioSpec(
                String id, VortaModule module, Category category,
                String title, String effectDesc, String knobGuide,
                boolean isSweep, float primaryHz, float secondaryHz,
                float sweepMinHz, float sweepMaxHz, float sweepPeriodSec,
                boolean fmOn, float fmDepth, float fmRateHz,
                boolean amOn, float amDepth, float amRateHz,
                boolean rmOn, float rmDepth, float rmRateHz,
                int channelMode) {
            this.id = id;
            this.module = module;
            this.category = category;
            this.title = title;
            this.effectDesc = effectDesc;
            this.knobGuide = knobGuide;
            this.isSweep = isSweep;
            this.primaryHz = primaryHz;
            this.secondaryHz = secondaryHz;
            this.sweepMinHz = sweepMinHz;
            this.sweepMaxHz = sweepMaxHz;
            this.sweepPeriodSec = sweepPeriodSec;
            this.fmOn = fmOn;
            this.fmDepth = fmDepth;
            this.fmRateHz = fmRateHz;
            this.amOn = amOn;
            this.amDepth = amDepth;
            this.amRateHz = amRateHz;
            this.rmOn = rmOn;
            this.rmDepth = rmDepth;
            this.rmRateHz = rmRateHz;
            this.channelMode = channelMode;
        }

        public ScenarioSpec forDevice(VortaModule mod, String deviceKnobGuide) {
            int ch = (mod == VortaModule.S6_BTL) ? 2 : 0;
            return new ScenarioSpec(
                    id, mod, category, title, effectDesc,
                    deviceKnobGuide != null ? deviceKnobGuide : knobGuide,
                    isSweep, primaryHz, secondaryHz, sweepMinHz, sweepMaxHz, sweepPeriodSec,
                    fmOn, fmDepth, fmRateHz, amOn, amDepth, amRateHz, rmOn, rmDepth, rmRateHz, ch);
        }

        public String formatSpecLine() {
            String base = isSweep
                    ? String.format(Locale.US, "%.1f~%.1f Hz (%.0fs)", sweepMinHz, sweepMaxHz, sweepPeriodSec)
                    : (secondaryHz > 0.0f
                        ? String.format(Locale.US, "%.2f+%.2f Hz 이중음", primaryHz, secondaryHz)
                        : String.format(Locale.US, "%.2f Hz 순음", primaryHz));
            StringBuilder mod = new StringBuilder();
            if (fmOn) mod.append(String.format(Locale.US, "FM %.1f%%", fmDepth * 100.0f));
            if (amOn) {
                if (mod.length() > 0) mod.append(" ");
                mod.append(String.format(Locale.US, "AM %.0f%%", amDepth * 100.0f));
            }
            if (rmOn) {
                if (mod.length() > 0) mod.append(" ");
                mod.append(String.format(Locale.US, "RM %.0f%%", rmDepth * 100.0f));
            }
            if (mod.length() == 0) {
                mod.append(secondaryHz > 0.0f ? "순수 합성" : "순수 사인");
            }
            return base + " · " + mod;
        }
    }

    private static final String KNOB_S6 = "LP-168S: SUB 4시(80%) · X-OVER 85Hz · BASS 0dB";
    private static final String KNOB_S7 = "스마토#2 (240W): 노브 12시(65%) · 순음 고정";
    private static final String KNOB_A30 = "스마토#1 12시 + 트랜스2 SUB 4시 · L=R 동위상";
    private static final String KNOB_PRO = "32-bit Float -1.0 dBTP 표준 출력";

    private static final List<ScenarioSpec> ALL = buildCatalog();
    private static final List<ScenarioSpec> PRO_SCENARIOS = buildProScenarios();

    private ScenarioCatalog() {}

    public static List<ScenarioSpec> all() {
        return ALL;
    }

    public static List<ScenarioSpec> proScenarios() {
        return PRO_SCENARIOS;
    }

    public static String knobGuideForModule(VortaModule mod) {
        if (mod == VortaModule.S6_BTL) return KNOB_S6;
        if (mod == VortaModule.S7_ANCHOR) return KNOB_S7;
        if (mod == VortaModule.A30_SWEEP) return KNOB_A30;
        return KNOB_PRO;
    }

    public static List<ScenarioSpec> forModule(VortaModule module, Category category) {
        List<ScenarioSpec> out = new ArrayList<ScenarioSpec>();
        boolean isShared = (category == Category.COMMON || category == Category.BASIC);
        String knob = knobGuideForModule(module);
        for (int i = 0; i < ALL.size(); i++) {
            ScenarioSpec s = ALL.get(i);
            if (isShared) {
                if (s.category == category) out.add(s.forDevice(module, knob));
            } else if (s.module == module && (category == null || s.category == category)) {
                out.add(s);
            }
        }
        return out;
    }

    public static ScenarioSpec findById(String id) {
        if (id == null) return null;
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id.equals(id)) return ALL.get(i);
        }
        for (int i = 0; i < PRO_SCENARIOS.size(); i++) {
            if (PRO_SCENARIOS.get(i).id.equals(id)) return PRO_SCENARIOS.get(i);
        }
        return null;
    }

    private static ScenarioSpec single(
            String id, VortaModule mod, Category cat, String title, String effect, String knob,
            float f1, float f2, boolean fmOn, float fmD, float fmR, boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR, int chMode) {
        return new ScenarioSpec(id, mod, cat, title, effect, knob, false, f1, f2, 0f, 0f, 0f,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, chMode);
    }

    private static ScenarioSpec sweep(
            String id, VortaModule mod, Category cat, String title, String effect, String knob,
            float minHz, float maxHz, float sec, boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR, boolean rmOn, float rmD, float rmR, int chMode) {
        return new ScenarioSpec(id, mod, cat, title, effect, knob, true, minHz, 0f, minHz, maxHz, sec,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, chMode);
    }

    private static List<ScenarioSpec> buildCatalog() {
        List<ScenarioSpec> l = new ArrayList<ScenarioSpec>();

        // S6: MAIN(4 sched + 3 res + 9 act + 4 spec = 20), SUB1(11), SUB2(10)
        l.add(single("s6_sched_1", VortaModule.S6_BTL, Category.MAIN,
                "아침 (07~13): 55.0 Hz 직타", "53.1~55Hz 룸모드 피크 관통", KNOB_S6,
                55.00f, 0.00f, true, 0.035f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(sweep("s6_sched_2", VortaModule.S6_BTL, Category.MAIN,
                "오후 (13~19): 31.0 ~ 40.0 Hz (360s)", "흉벽 관통 불규칙 이중 파면", KNOB_S6,
                31.00f, 40.00f, 360.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 12.50f, 2));
        l.add(single("s6_sched_3", VortaModule.S6_BTL, Category.MAIN,
                "저녁 (19~01): 43.2 Hz 직타", "120mm 슬래브 1타 기계 해머 직타", KNOB_S6,
                43.20f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 2.80f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sched_4", VortaModule.S6_BTL, Category.MAIN,
                "취침 (01~07): 55.0 Hz 락온", "침실 1차 정상파 초밀착 락온", KNOB_S6,
                55.00f, 0.00f, true, 0.025f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(sweep("s6_res_1", VortaModule.S6_BTL, Category.MAIN,
                "슬래브 굽힘 광역 스윕", "40~63 Hz 슬래브 고유진동 무왜곡 스윕", KNOB_S6,
                40.00f, 63.00f, 480.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_res_2", VortaModule.S6_BTL, Category.MAIN,
                "4.5 Hz 순수 이중 맥놀이", "55+59.5 Hz 비선형 측파대 없는 수직 맥놀이", KNOB_S6,
                55.00f, 59.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_res_3", VortaModule.S6_BTL, Category.MAIN,
                "50 + 25 Hz 저역 옥타브", "등청감 기반 저역 상향 마스킹 순수 조화", KNOB_S6,
                50.00f, 25.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_1", VortaModule.S6_BTL, Category.MAIN,
                "상대 공격 즉각 요격: 55.0 Hz 직타 (복합 변조)", "쿵쾅거림 즉시 룸모드 충격파 반격", KNOB_S6,
                55.00f, 0.00f, true, 0.035f, 1.50f, true, 0.150f, 2.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_2", VortaModule.S6_BTL, Category.MAIN,
                "벌주기 (보복 타격): 40.0 Hz 두경부 직타", "1~2시간 강력 집중 응징", KNOB_S6,
                40.00f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 15.00f, 2));
        l.add(single("s6_act_3", VortaModule.S6_BTL, Category.MAIN,
                "출근 12시간 무인방어: 24.0 Hz / 43.2 Hz 교대 (600초)", "07~19시 부재중 자동 방어", KNOB_S6,
                43.20f, 24.00f, true, 0.040f, 1.20f, true, 0.150f, 2.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_4", VortaModule.S6_BTL, Category.MAIN,
                "일상 외출 자동 순항: 31.0 Hz 상복부 직타", "2~4시간 외출 시 기저 둔통 유지", KNOB_S6,
                31.00f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.80f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_5", VortaModule.S6_BTL, Category.MAIN,
                "초장기 외출 (48h+): 55.0 Hz 인터벌 락온", "무인 요새화", KNOB_S6,
                55.00f, 27.50f, true, 0.025f, 0.65f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_6", VortaModule.S6_BTL, Category.MAIN,
                "상대 내성 파괴: 24.0 Hz / 55.0 Hz 급변 도약", "비선형 변조 도약으로 상대 신경 적응 무효화", KNOB_S6,
                55.00f, 24.00f, false, 0.000f, 0.00f, true, 0.200f, 3.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_7", VortaModule.S6_BTL, Category.MAIN,
                "상대 정신 집중 차단: 40.0 Hz 뇌파 공진 직타", "재택/공부 방해, 인지 저하 유도", KNOB_S6,
                40.00f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 10.00f, 2));
        l.add(single("s6_act_8", VortaModule.S6_BTL, Category.MAIN,
                "수면 시간 집중 압박: 55.0 Hz 룸모드 락온", "새벽 1~5시 수면 단계 파괴", KNOB_S6,
                55.00f, 0.00f, true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_act_9", VortaModule.S6_BTL, Category.MAIN,
                "3시간 이상 기기 보호: 43.2 Hz 보호 모드", "보이스코일 발열 억제 운용", KNOB_S6,
                43.20f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_spec_1", VortaModule.S6_BTL, Category.MAIN,
                "슬래브 120mm 1타 굽힘파: 43.2 Hz 단독 직타", "원음 무변조 수직 관통 해머", KNOB_S6,
                43.20f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_spec_2", VortaModule.S6_BTL, Category.MAIN,
                "침실 1차 정상파 스텔스: 53.6 Hz 단독 직타", "침실 고유 공진 초밀착 락온", KNOB_S6,
                53.60f, 0.00f, true, 0.020f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(sweep("s6_spec_3", VortaModule.S6_BTL, Category.MAIN,
                "흉복부 중저역 스윕: 24.0 ~ 38.0 Hz (480s)", "흉복부 내장 공진 광역 훑기", KNOB_S6,
                24.00f, 38.00f, 480.0f, false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f, 2));
        l.add(sweep("s6_spec_4", VortaModule.S6_BTL, Category.MAIN,
                "룸모드 고차 훑기: 40.0 ~ 55.0 Hz (300s)", "천장 전 대역 고유 공진 순환 가진", KNOB_S6,
                40.00f, 55.00f, 300.0f, true, 0.040f, 1.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_1", VortaModule.S6_BTL, Category.SUB1,
                "아침 (07~13 v2): 46.8 Hz 직타", "하악골 골도 공진 수직 해머 직타", KNOB_S6,
                46.80f, 0.00f, false, 0.000f, 0.00f, true, 0.160f, 2.40f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_2", VortaModule.S6_BTL, Category.SUB1,
                "오후 (13~19 v2): 51.5 Hz 직타", "311호 룸모드 2차 정상파 공진 추적", KNOB_S6,
                51.50f, 0.00f, true, 0.040f, 1.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_3", VortaModule.S6_BTL, Category.SUB1,
                "저녁 (19~01 v2): 54.0 Hz 직타", "55Hz 인접 1.0Hz 맥놀이 충격파 직타", KNOB_S6,
                54.00f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 1.00f, 2));
        l.add(sweep("s6_sub1_4", VortaModule.S6_BTL, Category.SUB1,
                "취침 (01~07 v2): 36.5 ~ 44.5 Hz (600s)", "흉골/갈비뼈 수직 미세 펄스 훑기", KNOB_S6,
                36.50f, 44.50f, 600.0f, false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_5", VortaModule.S6_BTL, Category.SUB1,
                "상대 공격 즉각 반격 v2: 54.0 Hz 직타", "하향 충격파 동시 발진", KNOB_S6,
                54.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 2.00f, true, 0.250f, 1.00f, 2));
        l.add(single("s6_sub1_6", VortaModule.S6_BTL, Category.SUB1,
                "벌주기 (미로기관 파쇄): 46.8 Hz 두개골 직타", "안구/두개골 공진 입체 파쇄", KNOB_S6,
                46.80f, 0.00f, true, 0.050f, 2.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_7", VortaModule.S6_BTL, Category.SUB1,
                "출근 12시간 무인방어 v2: 36.5 Hz / 51.5 Hz 교대 (720초)", "v2 독자 대역 순환 신경 피로 극대화", KNOB_S6,
                51.50f, 36.50f, true, 0.040f, 1.20f, true, 0.120f, 1.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_8", VortaModule.S6_BTL, Category.SUB1,
                "상대 내성 완전파괴 v2: 33.0 Hz / 54.0 Hz 급변 도약", "주파수 비선형 도약 예측 불허 타격", KNOB_S6,
                54.00f, 33.00f, false, 0.000f, 0.00f, true, 0.200f, 3.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_9", VortaModule.S6_BTL, Category.SUB1,
                "상대 정신 집중차단 v2: 46.8 Hz 뇌파 공진 직타", "골도 공진으로 두통 및 인지 마비 유도", KNOB_S6,
                46.80f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 8.00f, 2));
        l.add(single("s6_sub1_10", VortaModule.S6_BTL, Category.SUB1,
                "하악골 골도 공진 단독: 46.8 Hz 단독 직타", "골도 공진 순수 타격 해머", KNOB_S6,
                46.80f, 23.40f, false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub1_11", VortaModule.S6_BTL, Category.SUB1,
                "2차 정상파 수직 관통: 51.5 Hz 단독 직타", "침실 2차 정상파 수직 관통 락온", KNOB_S6,
                51.50f, 25.75f, true, 0.035f, 1.20f, true, 0.120f, 2.40f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_1", VortaModule.S6_BTL, Category.SUB2,
                "아침 (07~13 v3): 37.0 Hz 직타", "슬래브 전단파 요동 수직 관통", KNOB_S6,
                37.00f, 0.00f, true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_2", VortaModule.S6_BTL, Category.SUB2,
                "오후 (13~19 v3): 48.5 Hz 직타", "슬래브 고차 굽힘파 기계 해머 직타", KNOB_S6,
                48.50f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 3.10f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_3", VortaModule.S6_BTL, Category.SUB2,
                "저녁 (19~01 v3): 53.2 Hz 직타", "311호 1차 정상파(53.6Hz) 초정밀 락온", KNOB_S6,
                53.20f, 0.00f, true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(sweep("s6_sub2_4", VortaModule.S6_BTL, Category.SUB2,
                "취침 (01~07 v3): 42.0 ~ 47.5 Hz (580s)", "침대 프레임 골도 미세 펄스 훑기", KNOB_S6,
                42.00f, 47.50f, 580.0f, false, 0.000f, 0.00f, true, 0.100f, 1.20f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_5", VortaModule.S6_BTL, Category.SUB2,
                "상대 공격 즉각 반격 v3: 53.2 Hz 정상파 직타", "침실 룸모드 즉각 현기증 유발", KNOB_S6,
                53.20f, 0.00f, true, 0.030f, 0.80f, true, 0.180f, 2.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_6", VortaModule.S6_BTL, Category.SUB2,
                "벌주기 (흉곽 슐만 파쇄): 37.0 Hz 슬래브 직타", "슐만 2차 고조파 연동 흉곽/척추 집중 타격", KNOB_S6,
                37.00f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 2.50f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_7", VortaModule.S6_BTL, Category.SUB2,
                "출근 12시간 무인방어 v3: 37.0 Hz / 53.2 Hz 교대 (600초)", "3주차 고유 주파수 및 3기기 변조 조화", KNOB_S6,
                53.20f, 37.00f, true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_8", VortaModule.S6_BTL, Category.SUB2,
                "상대 내성 완전파괴 v3: 37.0 Hz / 53.2 Hz 급변", "광대역 카오스 도약 복합 조화", KNOB_S6,
                53.20f, 37.00f, false, 0.000f, 0.00f, true, 0.200f, 3.20f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_9", VortaModule.S6_BTL, Category.SUB2,
                "슬래브 전단파 수직 관통: 37.0 Hz 단독 직타", "전단파 요동 단독 관통", KNOB_S6,
                37.00f, 18.50f, true, 0.050f, 1.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));
        l.add(single("s6_sub2_10", VortaModule.S6_BTL, Category.SUB2,
                "침실 1차 정상파 초정밀 락온: 53.2 Hz 단독 직타", "침실 룸모드 피크 극점 단독 직타", KNOB_S6,
                53.20f, 0.00f, true, 0.030f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 2));

        // S7: MAIN(4 sched + 3 res + 9 act + 4 spec = 20), SUB1(16), SUB2(16)
        l.add(single("s7_sched_1", VortaModule.S7_ANCHOR, Category.MAIN,
                "아침 (07~13): 28.0 Hz 앵커 직타", "골반저근 펄스 해머 타격", KNOB_S7,
                28.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 3.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sched_2", VortaModule.S7_ANCHOR, Category.MAIN,
                "오후 (13~19): 10.5 Hz 앵커 직타", "자율신경·심박 요동 앵커", KNOB_S7,
                10.50f, 0.00f, true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sched_3", VortaModule.S7_ANCHOR, Category.MAIN,
                "저녁 (19~01): 7.0 Hz 앵커 직타", "전정기관 평형 파괴 요동", KNOB_S7,
                7.00f, 0.00f, true, 0.080f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_sched_4", VortaModule.S7_ANCHOR, Category.MAIN,
                "취침 (01~07): 21.0 ~ 24.5 Hz (1250s)", "바닥 미세 맥동 골도 진동", KNOB_S7,
                21.00f, 24.50f, 1250.0f, false, 0.000f, 0.00f, true, 0.100f, 0.60f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_res_1", VortaModule.S7_ANCHOR, Category.MAIN,
                "척추·내장 수직 스윕", "4.5~7.8 Hz 수직 공진 구간 900s 순음 스윕", KNOB_S7,
                4.50f, 7.80f, 900.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_res_2", VortaModule.S7_ANCHOR, Category.MAIN,
                "흉곽·전정 전이 스윕", "8.0~12.5 Hz 전이 공진 720s 순음 스윕", KNOB_S7,
                8.00f, 12.50f, 720.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_res_3", VortaModule.S7_ANCHOR, Category.MAIN,
                "두개골 4 Hz 이중 맥놀이", "24+28 Hz 안면·두개 진동 순수 맥놀이", KNOB_S7,
                24.00f, 28.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_1", VortaModule.S7_ANCHOR, Category.MAIN,
                "상대 공격 즉각 요격: 7.0Hz + 10.5Hz 복합 앵커", "바닥 2점 충격파 즉각 가진", KNOB_S7,
                7.00f, 10.50f, true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_2", VortaModule.S7_ANCHOR, Category.MAIN,
                "벌주기 (보복 타격): 28.0 Hz 골반 앵커 직타", "골반저 해머 압박 집중 응징", KNOB_S7,
                28.00f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 3.50f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_3", VortaModule.S7_ANCHOR, Category.MAIN,
                "출근 12시간 무인방어: 10.5Hz / 28.0Hz 교대 (600초)", "기저 피로 누적 및 발열 30% 저감", KNOB_S7,
                28.00f, 10.50f, true, 0.080f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_4", VortaModule.S7_ANCHOR, Category.MAIN,
                "일상 외출 자동 순항: 10.5 Hz 지속 앵커", "2~4시간 외출 시 기저 심박 유지", KNOB_S7,
                10.50f, 0.00f, true, 0.060f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_act_5", VortaModule.S7_ANCHOR, Category.MAIN,
                "초장기 외출 (48h+): 21.0 ~ 24.5 Hz 앵커", "45분 발진 / 15분 휴지 코일 보호", KNOB_S7,
                21.00f, 24.50f, 1500.0f, true, 0.040f, 0.45f, true, 0.100f, 0.60f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_6", VortaModule.S7_ANCHOR, Category.MAIN,
                "상대 내성 파괴: 7.0Hz / 10.5Hz 도약 (180초)", "비동기 변조 도약으로 상대 신경 적응 무효화", KNOB_S7,
                10.50f, 7.00f, true, 0.100f, 1.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_7", VortaModule.S7_ANCHOR, Category.MAIN,
                "상대 정신 집중 차단: 7.0 Hz 전정계 흔들기", "전정계 평형 교란 및 인지 저하", KNOB_S7,
                7.00f, 0.00f, true, 0.090f, 0.65f, true, 0.140f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_act_8", VortaModule.S7_ANCHOR, Category.MAIN,
                "수면 시간 집중 압박: 21.0 ~ 24.5 Hz 앵커", "새벽 1~5시 침대 골도 진동 집중 타격", KNOB_S7,
                21.00f, 24.50f, 1100.0f, false, 0.000f, 0.00f, true, 0.120f, 0.85f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_act_9", VortaModule.S7_ANCHOR, Category.MAIN,
                "3시간 이상 기기 보호: 28.0 Hz (50분/10분)", "AM 15% 듀티 사이클 동기화로 45도 이하 유지", KNOB_S7,
                28.00f, 0.00f, false, 0.000f, 0.00f, true, 0.140f, 1.60f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_spec_1", VortaModule.S7_ANCHOR, Category.MAIN,
                "바닥 2점 기저 앵커 직타: 28.0 Hz 단독", "바닥 슬래브 2점 기저 앵커 집중 가진", KNOB_S7,
                28.00f, 14.00f, false, 0.000f, 0.00f, true, 0.160f, 3.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_spec_2", VortaModule.S7_ANCHOR, Category.MAIN,
                "공간 요동 FM 앵커: 10.5 Hz 단독", "공간 전체 음압 요동 및 심박 공진", KNOB_S7,
                10.50f, 5.25f, true, 0.090f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_spec_3", VortaModule.S7_ANCHOR, Category.MAIN,
                "전정기관 파괴 극저역: 7.0 Hz 단독", "미로기관 평형 상실 유발 초저역", KNOB_S7,
                7.00f, 3.50f, true, 0.100f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("s7_spec_4", VortaModule.S7_ANCHOR, Category.MAIN,
                "바닥 미세맥동 골도스윕: 21.0 ~ 24.5 Hz (1250초)", "바닥 2점 미세 진동 훑기", KNOB_S7,
                21.00f, 24.50f, 980.0f, true, 0.050f, 0.70f, true, 0.120f, 0.60f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_1", VortaModule.S7_ANCHOR, Category.SUB1,
                "아침 (07~13 v2): 33.0 Hz 앵커 직타", "척추 추간판 압박점 요동 앵커", KNOB_S7,
                33.00f, 0.00f, true, 0.060f, 1.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_2", VortaModule.S7_ANCHOR, Category.SUB1,
                "오후 (13~19 v2): 22.0 Hz 앵커 직타", "골반 내 장기 초저속 펄스 타격", KNOB_S7,
                22.00f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 0.80f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_3", VortaModule.S7_ANCHOR, Category.SUB1,
                "저녁 (19~01 v2): 5.0 Hz 앵커 직타", "간·담낭 초저역 서파 요동", KNOB_S7,
                5.00f, 0.00f, true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_4", VortaModule.S7_ANCHOR, Category.SUB1,
                "취침 (01~07 v2): 16.0 Hz 앵커 직타", "음향심리 불쾌감 피크 요동", KNOB_S7,
                16.00f, 0.00f, true, 0.050f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_5", VortaModule.S7_ANCHOR, Category.SUB1,
                "상대 공격 즉각 반격 v2: 5.0 Hz 앵커", "하향 충격파 즉각 발진", KNOB_S7,
                5.00f, 0.00f, true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_6", VortaModule.S7_ANCHOR, Category.SUB1,
                "벌주기 (미로기관 파쇄): 33.0 Hz 척추 압박", "척추 추간판 압박 및 미로기관 파쇄", KNOB_S7,
                33.00f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 2.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_7", VortaModule.S7_ANCHOR, Category.SUB1,
                "출근 12시간 무인방어 v2: 5.0Hz / 22.0Hz 교대", "v2 독자 대역 순환 신경 피로 극대화", KNOB_S7,
                22.00f, 5.00f, true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_8", VortaModule.S7_ANCHOR, Category.SUB1,
                "일상 외출 자동 순항 v2: 16.0 Hz 불쾌감 앵커", "2~4시간 외출 시 기저 둔통 유지", KNOB_S7,
                16.00f, 0.00f, true, 0.060f, 0.55f, true, 0.150f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_9", VortaModule.S7_ANCHOR, Category.SUB1,
                "초장기 외출 (48h+ v2): 16.0 Hz 초저발열 앵커", "발열 억제 프로파일 48시간 무인 안정 운용", KNOB_S7,
                16.00f, 0.00f, true, 0.040f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_10", VortaModule.S7_ANCHOR, Category.SUB1,
                "상대 내성 완전파괴 v2: 5.0Hz / 22.0Hz 도약", "주파수 도약 및 예측 불가능 타격", KNOB_S7,
                22.00f, 5.00f, true, 0.080f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_11", VortaModule.S7_ANCHOR, Category.SUB1,
                "상대 정신 집중차단 v2: 8.0 Hz 알파파 차단", "알파파 차단 두통 및 인지 마비 유도", KNOB_S7,
                8.00f, 0.00f, true, 0.060f, 0.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_12", VortaModule.S7_ANCHOR, Category.SUB1,
                "수면 시간 집중압박 v2: 16.0 Hz 골도 앵커", "바닥 미세 진동 수면 단계 파괴", KNOB_S7,
                16.00f, 8.00f, true, 0.050f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_13", VortaModule.S7_ANCHOR, Category.SUB1,
                "3시간 이상 기기보호 v2: 33.0 Hz (45분/15분)", "AM 펄스 해머링 전력 효율 극대화", KNOB_S7,
                33.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 1.50f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_14", VortaModule.S7_ANCHOR, Category.SUB1,
                "척추 추간판 압박 요동: 33.0 Hz 단독", "척추 추간판 압박 요동 단독 직타", KNOB_S7,
                33.00f, 16.50f, true, 0.070f, 1.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_15", VortaModule.S7_ANCHOR, Category.SUB1,
                "골반저 초저속 펄스: 22.0 Hz 단독", "골반 내 장기 초저속 펄스 단독 타격", KNOB_S7,
                22.00f, 11.00f, false, 0.000f, 0.00f, true, 0.180f, 0.80f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub1_16", VortaModule.S7_ANCHOR, Category.SUB1,
                "간·담낭 초저역 서파: 5.0 Hz 단독", "간·담낭 초저역 서파 단독 요동", KNOB_S7,
                5.00f, 2.50f, true, 0.100f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_1", VortaModule.S7_ANCHOR, Category.SUB2,
                "아침 (07~13 v3): 19.0 Hz 앵커 직타", "안구 안와 떨림 펄스 해머", KNOB_S7,
                19.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_2", VortaModule.S7_ANCHOR, Category.SUB2,
                "오후 (13~19 v3): 13.5 Hz 앵커 직타", "슐만 2차 고조파 척추 요동 앵커", KNOB_S7,
                13.50f, 0.00f, true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_3", VortaModule.S7_ANCHOR, Category.SUB2,
                "저녁 (19~01 v3): 26.5 Hz 앵커 직타", "흉골/폐 기계 진동 펄스 앵커", KNOB_S7,
                26.50f, 0.00f, false, 0.000f, 0.00f, true, 0.160f, 1.80f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_4", VortaModule.S7_ANCHOR, Category.SUB2,
                "취침 (01~07 v3): 8.5 Hz 앵커 직타", "알파파 강제 각성 요동 앵커", KNOB_S7,
                8.50f, 0.00f, true, 0.060f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_5", VortaModule.S7_ANCHOR, Category.SUB2,
                "상대 공격 즉각 반격 v3: 19.0 Hz 안구 공진 앵커", "안구 19Hz AM 펄스 즉각 가진", KNOB_S7,
                19.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_6", VortaModule.S7_ANCHOR, Category.SUB2,
                "벌주기 (흉곽 슐만 파쇄): 13.5 Hz 슐만 앵커", "슐만 2차 고조파 척추/흉곽 집중 타격", KNOB_S7,
                13.50f, 0.00f, true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_7", VortaModule.S7_ANCHOR, Category.SUB2,
                "출근 12시간 무인방어 v3: 13.5Hz / 26.5Hz 교대 (600초)", "3주차 고유 주파수 및 3기기 변조 조화", KNOB_S7,
                26.50f, 13.50f, true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_8", VortaModule.S7_ANCHOR, Category.SUB2,
                "일상 외출 자동 순항 v3: 19.0 Hz 안와 앵커", "2~4시간 외출 시 시야 피로 가중", KNOB_S7,
                19.00f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.80f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_9", VortaModule.S7_ANCHOR, Category.SUB2,
                "초장기 외출 (48h+ v3): 8.5 Hz 초저발열 앵커", "초저역 앵커 48시간 무인 요새화", KNOB_S7,
                8.50f, 0.00f, true, 0.050f, 0.35f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_10", VortaModule.S7_ANCHOR, Category.SUB2,
                "상대 내성 완전파괴 v3: 13.5Hz / 26.5Hz 도약", "광대역 카오스 도약 복합 조화", KNOB_S7,
                26.50f, 13.50f, true, 0.080f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_11", VortaModule.S7_ANCHOR, Category.SUB2,
                "상대 정신 집중차단 v3: 19.0 Hz 안와 공진 앵커", "안구 공진으로 시각/청각 집중 원천 차단", KNOB_S7,
                19.00f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_12", VortaModule.S7_ANCHOR, Category.SUB2,
                "수면 시간 집중압박 v3: 8.5 Hz 각성 유발 앵커", "8.5Hz 각성 유도로 수면 단계 파괴", KNOB_S7,
                8.50f, 4.25f, true, 0.060f, 0.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_13", VortaModule.S7_ANCHOR, Category.SUB2,
                "3시간 이상 기기보호 v3: 19.0 Hz (50분/10분)", "AM 펄스 듀티 사이클 동기화로 발열 45도 이하 유지", KNOB_S7,
                19.00f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_14", VortaModule.S7_ANCHOR, Category.SUB2,
                "안구 안와 공진 펄스: 19.0 Hz 단독", "안구 안와 공진 펄스 해머 단독 타격", KNOB_S7,
                19.00f, 9.50f, false, 0.000f, 0.00f, true, 0.150f, 2.20f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_15", VortaModule.S7_ANCHOR, Category.SUB2,
                "슐만 2차 고조파 척추 락온: 13.5 Hz 단독", "슐만 2차 고조파 척추 요동 앵커 단독 락온", KNOB_S7,
                13.50f, 6.75f, true, 0.070f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("s7_sub2_16", VortaModule.S7_ANCHOR, Category.SUB2,
                "흉골/폐 기계 진동 펄스: 26.5 Hz 단독", "흉골/폐 기계 진동 펄스 앵커 단독 가진", KNOB_S7,
                26.50f, 13.25f, false, 0.000f, 0.00f, true, 0.160f, 1.80f, false, 0.000f, 0.00f, 0));

        // A30: MAIN(4 sched + 3 res + 9 act + 4 spec = 20), SUB1(11), SUB2(10)
        l.add(sweep("a30_sched_1", VortaModule.A30_SWEEP, Category.MAIN,
                "아침 (07~13): 17.3 ~ 19.8 Hz (440s)", "전정기관·소화관 연동 교란", KNOB_A30,
                17.30f, 19.80f, 440.0f, true, 0.100f, 7.03f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sched_2", VortaModule.A30_SWEEP, Category.MAIN,
                "오후 (13~19): 20.7 ~ 27.7 Hz (740s)", "위장관 기계적 펄스 진동", KNOB_A30,
                20.70f, 27.70f, 740.0f, false, 0.000f, 0.00f, true, 0.150f, 5.70f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sched_3", VortaModule.A30_SWEEP, Category.MAIN,
                "저녁 (19~01): 18.6 ~ 25.1 Hz (900s)", "광역 카오스 측파대 훑기", KNOB_A30,
                18.60f, 25.10f, 900.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 35.95f, 0));
        l.add(sweep("a30_sched_4", VortaModule.A30_SWEEP, Category.MAIN,
                "취침 (01~07): 2.4 ~ 4.6 Hz (1480s)", "델타파 무음 위상 간섭 훑기", KNOB_A30,
                2.40f, 4.60f, 1480.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_res_1", VortaModule.A30_SWEEP, Category.MAIN,
                "공간 평균 광역 스윕", "36~63 Hz 저주파 공간 편차 평균 1200s 스윕", KNOB_A30,
                36.00f, 63.00f, 1200.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_res_2", VortaModule.A30_SWEEP, Category.MAIN,
                "촉각 고효율 순수 스윕", "32~50 Hz 피부 기계 수용체 최적 600s 스윕", KNOB_A30,
                32.00f, 50.00f, 600.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_res_3", VortaModule.A30_SWEEP, Category.MAIN,
                "초저역 3.5 Hz 이중 맥놀이", "18+21.5 Hz 극저역 선형 음향 맥놀이", KNOB_A30,
                18.00f, 21.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_act_1", VortaModule.A30_SWEEP, Category.MAIN,
                "상대 공격 즉각 요격: 35.0 ~ 45.0 Hz (120s)", "바닥 전방위 순간 기계적 충격파", KNOB_A30,
                35.00f, 45.00f, 120.0f, false, 0.000f, 0.00f, true, 0.150f, 4.50f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_act_2", VortaModule.A30_SWEEP, Category.MAIN,
                "벌주기 (보복 타격): 18.6 ~ 25.1 Hz (600s)", "광역 카오스 RM 측파대 탈출 불가 압박", KNOB_A30,
                18.60f, 25.10f, 600.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 35.95f, 0));
        l.add(sweep("a30_act_3", VortaModule.A30_SWEEP, Category.MAIN,
                "출근 12시간 무인방어: 4대 시간대 광역 스윕 순환", "07~19시 부재중 자동 순환 발진 (발열 30% 저감)", KNOB_A30,
                17.30f, 27.70f, 640.0f, true, 0.100f, 5.00f, true, 0.150f, 2.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_act_4", VortaModule.A30_SWEEP, Category.MAIN,
                "일상 외출 자동 순항: 20.7 ~ 27.7 Hz (740s)", "2~4시간 외출 시 위장관 기계적 펄스 진동 유지", KNOB_A30,
                20.70f, 27.70f, 680.0f, true, 0.060f, 1.20f, true, 0.160f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_act_5", VortaModule.A30_SWEEP, Category.MAIN,
                "초장기 외출 (48h+): 1.0 ~ 4.0 Hz (2400s)", "무인 요새화 및 보이스코일 발열 억제 완전 보호", KNOB_A30,
                1.00f, 4.00f, 2400.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_act_6", VortaModule.A30_SWEEP, Category.MAIN,
                "상대 내성 파괴: 18.98 Hz 단독 직타 (FM+RM 복합)", "비동기 변조 도약으로 상대 신경 적응 100% 무효화", KNOB_A30,
                18.98f, 0.00f, true, 0.100f, 7.03f, false, 0.000f, 0.00f, true, 0.300f, 35.95f, 0));
        l.add(sweep("a30_act_7", VortaModule.A30_SWEEP, Category.MAIN,
                "상대 정신 집중 차단: 4.0 ~ 6.2 Hz (480s)", "세타파 동시 간섭 및 인지 저하 유도", KNOB_A30,
                4.00f, 6.20f, 480.0f, true, 0.100f, 4.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_act_8", VortaModule.A30_SWEEP, Category.MAIN,
                "수면 시간 집중 압박: 2.4 ~ 4.6 Hz (1480s)", "새벽 1~5시 수면 단계 파괴, 211호 0dB 완전 무음 스텔스", KNOB_A30,
                2.40f, 4.60f, 1360.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_act_9", VortaModule.A30_SWEEP, Category.MAIN,
                "3시간 이상 기기 보호: 22.5 Hz 단독 직타 (AM 펄스)", "AM 15% 듀티 사이클 동기화로 앰프/스피커 45도 이하 유지", KNOB_A30,
                22.50f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 1.50f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_spec_1", VortaModule.A30_SWEEP, Category.MAIN,
                "안구·시야 공진 직타: 18.98 Hz 단독 직타", "안구 공진(18~19Hz) 비선형 합성 시야 떨림 유도", KNOB_A30,
                18.98f, 0.00f, true, 0.080f, 6.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_spec_2", VortaModule.A30_SWEEP, Category.MAIN,
                "늑골·흉벽 진폭 직타: 24.2 Hz 단독 직타", "늑간근 및 흉벽 진동 기계 해머 직타", KNOB_A30,
                24.20f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 3.80f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_spec_3", VortaModule.A30_SWEEP, Category.MAIN,
                "소화관 연동 공진 직타: 5.5 Hz 단독 직타", "위장관 및 소화기계 초저주파 둔통 유발", KNOB_A30,
                5.50f, 0.00f, false, 0.000f, 0.00f, true, 0.150f, 4.20f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_spec_4", VortaModule.A30_SWEEP, Category.MAIN,
                "델타파 초저역 스텔스 직타: 3.3 Hz 단독 직타", "211호 0dB 완전 무음 속 델타파 뇌파 간섭", KNOB_A30,
                3.30f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_1", VortaModule.A30_SWEEP, Category.SUB1,
                "아침 (07~13): 6.0 ~ 8.5 Hz (380s)", "소화관 상하 진폭 펄스 훑기", KNOB_A30,
                6.00f, 8.50f, 380.0f, false, 0.000f, 0.00f, true, 0.150f, 4.20f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_2", VortaModule.A30_SWEEP, Category.SUB1,
                "오후 (13~19): 12.0 ~ 16.5 Hz (520s)", "상복부·신장 지속 진폭 요동", KNOB_A30,
                12.00f, 16.50f, 520.0f, true, 0.100f, 6.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_3", VortaModule.A30_SWEEP, Category.SUB1,
                "저녁 (19~01): 25.0 ~ 32.5 Hz (820s)", "흉요추 관통 카오스 측파대", KNOB_A30,
                25.00f, 32.50f, 820.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 28.50f, 0));
        l.add(sweep("a30_sub1_4", VortaModule.A30_SWEEP, Category.SUB1,
                "취침 (01~07): 1.5 ~ 3.8 Hz (1800s)", "델타파 무음 위상 간섭 훑기", KNOB_A30,
                1.50f, 3.80f, 1800.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_5", VortaModule.A30_SWEEP, Category.SUB1,
                "상대 공격 즉각 반격 v2: 22.0 ~ 30.0 Hz (180s)", "강력한 하향 충격파 동시 발진", KNOB_A30,
                22.00f, 30.00f, 180.0f, false, 0.000f, 0.00f, true, 0.200f, 5.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_6", VortaModule.A30_SWEEP, Category.SUB1,
                "벌주기 (미로기관 파쇄): 14.0 ~ 20.0 Hz (540s)", "안구/전정계 3차원 입체 파쇄", KNOB_A30,
                14.00f, 20.00f, 540.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.350f, 31.20f, 0));
        l.add(sweep("a30_sub1_7", VortaModule.A30_SWEEP, Category.SUB1,
                "출근 12시간 무인방어 v2: v2 시간대 자동 순환", "v2 독자 대역 순환으로 v1 적응 상대 신경 피로 극대화", KNOB_A30,
                6.00f, 32.50f, 820.0f, true, 0.100f, 4.00f, true, 0.150f, 2.50f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_8", VortaModule.A30_SWEEP, Category.SUB1,
                "일상 외출 자동 순항 v2: 6.0 ~ 8.5 Hz (380s)", "소화관 펄스 지속으로 기저 둔통 형성", KNOB_A30,
                6.00f, 8.50f, 420.0f, true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub1_9", VortaModule.A30_SWEEP, Category.SUB1,
                "초장기 외출 (48h+ v2): 1.5 ~ 3.8 Hz (1800s)", "3대 기기 발열 억제 프로파일 적용으로 48시간 무인 요새화", KNOB_A30,
                1.50f, 3.80f, 2100.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_sub1_10", VortaModule.A30_SWEEP, Category.SUB1,
                "상대 내성 완전파괴 v2: 14.25 Hz 단독 직타 (FM+RM)", "비선형 변조 충격파와 예측 불가능한 타격", KNOB_A30,
                14.25f, 0.00f, true, 0.100f, 6.50f, false, 0.000f, 0.00f, true, 0.300f, 28.50f, 0));
        l.add(sweep("a30_sub1_11", VortaModule.A30_SWEEP, Category.SUB1,
                "상대 정신 집중차단 v2: 8.0 ~ 12.5 Hz (450s)", "8Hz 집중 공격으로 알파파 파괴, 두통 및 인지 마비 유도", KNOB_A30,
                8.00f, 12.50f, 450.0f, true, 0.120f, 7.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_1", VortaModule.A30_SWEEP, Category.SUB2,
                "아침 (07~13): 9.2 ~ 11.8 Hz (410s)", "알파파 붕괴 및 시야 집중 저하", KNOB_A30,
                9.20f, 11.80f, 410.0f, true, 0.100f, 5.20f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_2", VortaModule.A30_SWEEP, Category.SUB2,
                "오후 (13~19): 29.5 ~ 34.5 Hz (680s)", "늑간근/갈비뼈 진폭 펄스 해머", KNOB_A30,
                29.50f, 34.50f, 680.0f, false, 0.000f, 0.00f, true, 0.150f, 3.60f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_3", VortaModule.A30_SWEEP, Category.SUB2,
                "저녁 (19~01): 14.5 ~ 18.2 Hz (790s)", "이중 측파대 카오스 맥놀이", KNOB_A30,
                14.50f, 18.20f, 790.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.300f, 21.40f, 0));
        l.add(sweep("a30_sub2_4", VortaModule.A30_SWEEP, Category.SUB2,
                "취침 (01~07): 1.0 ~ 2.2 Hz (1950s)", "초심층 서파 무음 훑기", KNOB_A30,
                1.00f, 2.20f, 1950.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_5", VortaModule.A30_SWEEP, Category.SUB2,
                "상대 공격 즉각 반격 v3: 28.0 ~ 36.0 Hz (200s)", "쿵쾅거림 즉시 룸모드 정상파 타격 및 현기증 유발", KNOB_A30,
                28.00f, 36.00f, 200.0f, false, 0.000f, 0.00f, true, 0.200f, 4.80f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_6", VortaModule.A30_SWEEP, Category.SUB2,
                "벌주기 (흉곽 슐만 파쇄): 15.0 ~ 21.0 Hz (620s)", "흉곽 및 척추 집중 타격 슐만 응징", KNOB_A30,
                15.00f, 21.00f, 620.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.350f, 25.50f, 0));
        l.add(sweep("a30_sub2_7", VortaModule.A30_SWEEP, Category.SUB2,
                "출근 12시간 무인방어 v3: v3 시간대 자동 순환", "3주차 고유 주파수와 3기기 변조 조화로 누적 피로 극대화", KNOB_A30,
                9.20f, 34.50f, 790.0f, true, 0.100f, 3.50f, true, 0.150f, 2.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_8", VortaModule.A30_SWEEP, Category.SUB2,
                "일상 외출 자동 순항 v3: 9.2 ~ 11.8 Hz (410s)", "시야 흐림 및 안와 진동 집중 타겟팅", KNOB_A30,
                9.20f, 11.80f, 460.0f, true, 0.090f, 5.20f, true, 0.140f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("a30_sub2_9", VortaModule.A30_SWEEP, Category.SUB2,
                "초장기 외출 (48h+ v3): 1.0 ~ 2.2 Hz (1950s)", "초저역 사인파와 3중 고조파 요새화 장기 무인 방어", KNOB_A30,
                1.00f, 2.20f, 2250.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("a30_sub2_10", VortaModule.A30_SWEEP, Category.SUB2,
                "상대 내성 완전파괴 v3: 16.35 Hz 단독 직타 (FM+RM)", "광대역 카오스 RM 측파대와 주파수 도약의 복합 조화", KNOB_A30,
                16.35f, 0.00f, true, 0.100f, 5.20f, false, 0.000f, 0.00f, true, 0.300f, 21.40f, 0));

        // Shared COMMON (12 = 4 schedule refined + 8 common sweeps/tones)
        l.add(sweep("com_sched_1", VortaModule.A30_SWEEP, Category.COMMON,
                "아침 (07~13 공통 정제): 52.0 ~ 55.0 Hz (180s)", "공통 프리셋1 주간 하이브리드(55Hz 상한 정제) · 흉부 압박 및 멀미 유도", KNOB_A30,
                52.00f, 55.00f, 180.0f, true, 0.030f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_sched_2", VortaModule.A30_SWEEP, Category.COMMON,
                "오후 (13~19 공통 정제): 6.7 ~ 9.0 Hz (490s)", "공통 초안 스윕2 소화기·복부 연동 및 알파파 하단 교란", KNOB_A30,
                6.70f, 9.00f, 490.0f, true, 0.070f, 0.85f, true, 0.160f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_sched_3", VortaModule.A30_SWEEP, Category.COMMON,
                "저녁 (19~01 공통 정제): 3.3 ~ 7.3 Hz (640s)", "공통 앱 슬롯2 실측값(10분40초 루프) · 델타-세타 뇌파 동조 차단", KNOB_A30,
                3.30f, 7.30f, 640.0f, true, 0.085f, 0.60f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_sched_4", VortaModule.A30_SWEEP, Category.COMMON,
                "취침 (01~07 공통 정제): 12.0 ~ 24.5 Hz (6580s)", "공통 초안 스윕8 야간 서파·장간막 진동 분쇄 초장주기(109분) 루프", KNOB_A30,
                12.00f, 24.50f, 6580.0f, false, 0.000f, 0.00f, true, 0.120f, 0.50f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_ext_1", VortaModule.A30_SWEEP, Category.COMMON,
                "1.0 ~ 4.0 Hz (2880s) — 델타 서파 무인스캔", "공통 초안 스윕7 원전(48분 루프) · 델타 서파 위상 스캔 및 심부 수면 방해", KNOB_A30,
                1.00f, 4.00f, 2880.0f, true, 0.050f, 0.30f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_ext_2", VortaModule.A30_SWEEP, Category.COMMON,
                "4.0 ~ 6.2 Hz (290s) — 척추·전정계 수직 바운싱", "공통 초안 스윕1 원전(290초 주기) · 척추·전정계 수직 바운싱 및 내장 흔들림", KNOB_A30,
                4.00f, 6.20f, 290.0f, true, 0.090f, 0.75f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(single("com_ext_3", VortaModule.A30_SWEEP, Category.COMMON,
                "슬래브 1차모드: 18.5 Hz 단독 — 120mm 슬래브 1차 지배공진", "공통 3.1절 팬더아파트 120mm 슬래브 1차 지배공진(18.5Hz) 직타 · 안구·두개골 동시 울림", KNOB_A30,
                18.50f, 0.00f, false, 0.000f, 0.00f, true, 0.180f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_ext_4", VortaModule.A30_SWEEP, Category.COMMON,
                "18.6 ~ 25.1 Hz (900초 스윕, AM 0.50Hz) — 심야 역제압", "공통 프리셋4 심야 내장진동(20~22Hz 장간막) 역제압 호흡주기 루프", KNOB_A30,
                18.60f, 25.10f, 900.0f, false, 0.000f, 0.00f, true, 0.150f, 0.50f, false, 0.000f, 0.00f, 0));
        l.add(sweep("com_ext_5", VortaModule.A30_SWEEP, Category.COMMON,
                "18.6 ~ 25.1 Hz (900초 스윕, RM 1.80Hz) — 외출 카오스", "공통 프리셋3 장시간 외출용 카오스 비트 원전(RM 1.80Hz 회전맥놀이)", KNOB_A30,
                18.60f, 25.10f, 900.0f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.280f, 1.80f, 0));
        l.add(single("com_ext_6", VortaModule.A30_SWEEP, Category.COMMON,
                "19.5 Hz + 55.0 Hz 복합 — 안구·흉벽 동시 응징 해머", "공통 프리셋5 즉각 응징 해머 펄스(19.5Hz 안구공진 + 55.0Hz 슬래브 표면)", KNOB_A30,
                19.50f, 55.00f, false, 0.000f, 0.00f, true, 0.200f, 4.10f, false, 0.000f, 0.00f, 0));
        l.add(single("com_ext_7", VortaModule.A30_SWEEP, Category.COMMON,
                "슬래브 2차모드: 32.4 Hz 단독 — 120mm 슬래브 2차 지배공진", "공통 3.1절 팬더아파트 120mm 슬래브 2차 지배공진(32.4Hz) 직타 · 흉복부 관통", KNOB_A30,
                32.40f, 0.00f, true, 0.060f, 1.90f, true, 0.150f, 3.20f, false, 0.000f, 0.00f, 0));
        l.add(single("com_ext_8", VortaModule.A30_SWEEP, Category.COMMON,
                "슬래브 3차모드: 54.8 Hz 단독 — 120mm 슬래브 3차 지배공진", "공통 3.1절 팬더아파트 120mm 슬래브 3차 지배공진(54.8Hz) 직타 · 바닥 표면 진동 극대화", KNOB_A30,
                54.80f, 0.00f, true, 0.028f, 1.40f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));

        // Shared BASIC (22 = 10 band sweeps + 12 pure + sub-harmonic anchor tones)
        l.add(sweep("bas_s1", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 01: 1.0 ~ 2.5 Hz (300s) — 이석기관 한계·수면후 불쾌감", "[조합1 반응5] 극저역 이석기관(Otolith) 수직가속도 한계 자극 · 기절유도 · 수면 후 극심한 불쾌감·무기력", KNOB_A30,
                1.00f, 2.50f, 300.0f, true, 0.060f, 0.25f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s2", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 02: 2.5 ~ 4.5 Hz (360s) — 델타파 차단·심층 두통 유도", "[조합1 반응1] 델타파(1~4Hz) 숙면 뇌파 동조 차단 · 두개내압 미세 맥동 · 심층 두통 및 수면단계 파괴", KNOB_A30,
                2.50f, 4.50f, 360.0f, true, 0.080f, 0.45f, true, 0.120f, 1.80f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s3", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 03: 4.5 ~ 7.0 Hz (420s) — 장간막 공진·복통·수면리듬 분리", "[조합1 스윕1하단·반응3] 2631-1 복부 내장 수직 기본공진(4.5~7Hz) · 복통 유발 · 세타파 수면리듬 분리", KNOB_A30,
                4.50f, 7.00f, 420.0f, false, 0.000f, 0.00f, true, 0.180f, 3.90f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s4", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 04: 7.0 ~ 11.0 Hz (480s) — 전정·뇌간·자율신경·알파파", "[조합1·3(5)·4 통합] 전정·뇌간(7Hz)~슈만(7.83Hz)~자율신경·알파파(10.5~11Hz) 현기증·오심·맥박불안정·멍함", KNOB_A30,
                7.00f, 11.00f, 480.0f, true, 0.085f, 0.78f, true, 0.150f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s5", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 05: 11.0 ~ 16.5 Hz (500s) — 뇌파간섭·인후두·장연동 교란", "[조합1 스윕2·6하단 + 조합3(5)] SMR·베타파 뇌파간섭 · 인후두·기도 압박 · 장연동 설사/복통 및 내장 울림", KNOB_A30,
                11.00f, 16.50f, 500.0f, true, 0.070f, 1.25f, true, 0.180f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s6", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 06: 16.5 ~ 21.5 Hz (450s) — 두개골·시신경·귀울림(이명)", "[조합1 반응4 + 조합3(5) + 조합4] 두개골·전두엽(18Hz)·시신경·안구(18~21Hz) 공진 · 귀울림(이명)·두통·안구압박", KNOB_A30,
                16.50f, 21.50f, 450.0f, true, 0.090f, 6.20f, true, 0.160f, 4.00f, true, 0.220f, 18.50f, 0));
        l.add(sweep("bas_s7", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 07: 21.5 ~ 27.0 Hz (500s) — 내이·석고보드·흉곽·대장", "[조합3(3)하단 + 조합4] 내이 방향감각 상실(22Hz) · 석고보드 한계·흉곽압박(24Hz) · 대장진동·하체무력(25Hz)", KNOB_A30,
                21.50f, 27.00f, 500.0f, true, 0.055f, 1.15f, true, 0.180f, 3.50f, false, 0.000f, 0.00f, 0));
        l.add(sweep("bas_s8", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 08: 27.0 ~ 36.0 Hz (520s) — 골반·하복부·흉곽·위장·간", "[조합1 스윕3 + 조합3(3)상단 + 조합4] 골반·하복부 둔통(28Hz) · 흉곽·위장·간 묵직한 타격(31~36Hz) 주간 집중교란", KNOB_A30,
                27.00f, 36.00f, 520.0f, true, 0.060f, 1.80f, true, 0.170f, 3.20f, true, 0.180f, 13.50f, 0));
        l.add(sweep("bas_s9", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 09: 36.0 ~ 46.0 Hz (580s) — 대뇌공포·귀막힘·집중력저하", "[조합1 스윕4·7하단 + 조합3 중광역] 반복 불쾌울림·대뇌 공포감(40Hz) · 귀막힘·고막압박·집중력 저하(38~46Hz)", KNOB_A30,
                36.00f, 46.00f, 580.0f, true, 0.045f, 1.40f, true, 0.140f, 4.00f, true, 0.240f, 16.00f, 0));
        l.add(sweep("bas_s10", VortaModule.A30_SWEEP, Category.BASIC,
                "대역 10: 46.0 ~ 55.0 Hz (650s) — 귀먹먹함·콘크리트 슬래브 공진", "[조합1 스윕4상단 + 조합3(2) + 조합4] 고출력 귀 먹먹함(46~51Hz) ~ 콘크리트 벽면·슬래브 공간공진 최적점(55Hz)", KNOB_A30,
                46.00f, 55.00f, 650.0f, true, 0.038f, 1.15f, true, 0.120f, 2.40f, true, 0.200f, 11.50f, 0));
        l.add(single("bas_t1", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 01: 7.0 Hz (+3.5Hz) — 구조물 심부공진 & 전정기관·뇌간", "[조합2·3·4] 구조물 심부공진 · 전정기관·뇌간 타격 · 현기증·방향감각 혼란·기저 스트레스 극대화", KNOB_A30,
                7.00f, 3.50f, true, 0.075f, 0.55f, true, 0.120f, 3.80f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t2", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 02: 10.5 Hz (+5.25Hz) — 심박변동(HRV) & 자율신경 교란", "[조합2·3·4] 심박변동(HRV)·자율신경계 직접 교란 · 원인불명 메스꺼움·불안정한 맥박·알파파 차단", KNOB_A30,
                10.50f, 5.25f, true, 0.085f, 0.95f, true, 0.140f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t3", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 03: 16.0 Hz (+8.0Hz) — 뇌파간섭·위상현기증·장연동 복통", "[조합2·3·4] 인체 임계경계(뇌파간섭 수면방해 · 내이-시각 위상어긋남 현기증 · 장연동 설사/복통 · 두개골 공진)", KNOB_A30,
                16.00f, 8.00f, true, 0.060f, 1.25f, true, 0.200f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t4", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 04: 18.0 Hz (+9.0Hz) — 벽 전체 불쾌공명 & 전두엽·삼차신경", "[조합2·3·4] 벽 전체 불쾌한 울림 · 두개골 내부 공진 · 극심한 두통 · 삼차신경 자극 · 만성 무기력", KNOB_A30,
                18.00f, 9.00f, true, 0.085f, 6.30f, false, 0.000f, 0.00f, true, 0.220f, 18.00f, 0));
        l.add(single("bas_t5", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 05: 21.0 Hz (+10.5Hz) — 시신경·안구 공진 & 정신적 긴장", "[조합2·3·4] NASA 안구·시신경 공진(21Hz) · 안구 압박·초점 저하 · 정신적 긴장 및 고강도 스트레스", KNOB_A30,
                21.00f, 10.50f, true, 0.055f, 2.10f, true, 0.180f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t6", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 06: 22.0 Hz (+11.0Hz) — 귀 안쪽·내이 방향감각 상실", "[조합3·4] 귀 안쪽·내이 미로기관 집중 공진 · 방향감각 상실 · 자세 불균형 및 어지러움", KNOB_A30,
                22.00f, 11.00f, true, 0.090f, 3.40f, true, 0.140f, 2.50f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t7", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 07: 24.0 Hz (+12.0Hz) — 석고보드 한계 & 흉곽·폐 압박", "[조합2·3·4] 석고보드 벽체 공진 한계점 · 공간 전체 압박감 · 흉곽·폐 진동 및 가슴 답답함", KNOB_A30,
                24.00f, 12.00f, true, 0.045f, 1.15f, true, 0.160f, 3.70f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t8", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 08: 25.0 Hz (+12.5Hz) — 대장·하복부 장진동 & 하체 무력", "[조합2·3·4] 인체 7~25Hz 핵심 영향권 상단 마감 · 대장·하복부 진동 · 복부 팽만 및 하체 무력감", KNOB_A30,
                25.00f, 12.50f, false, 0.000f, 0.00f, true, 0.180f, 4.20f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t9", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 09: 28.0 Hz (+14.0Hz) — 골반·장기 하부 무거움·둔통", "[조합3·4] 골반저근·장기 하부 직타 · 하복부 묵직한 무거움 및 지속적 복부 둔통", KNOB_A30,
                28.00f, 14.00f, true, 0.050f, 0.90f, true, 0.170f, 3.15f, false, 0.000f, 0.00f, 0));
        l.add(single("bas_t10", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 10: 31.0 Hz (+15.5Hz) — 흉곽·위장·간 묵직한 타격", "[조합2·3·4] 흉곽 진동 및 상복부 장기(위장·간) 묵직한 타격감 · 심부 내장 피로 누적", KNOB_A30,
                31.00f, 15.50f, false, 0.000f, 0.00f, true, 0.160f, 2.75f, true, 0.180f, 13.50f, 0));
        l.add(single("bas_t11", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 11: 40.0 Hz (+20.0Hz) — 반복 불쾌울림 & 대뇌 공포감", "[조합2·3·4] 타격성 강한 반복 불쾌울림 · 대뇌 감마파 교란 · 원인 모를 공포감 및 정신 혼미", KNOB_A30,
                40.00f, 20.00f, false, 0.000f, 0.00f, true, 0.140f, 4.00f, true, 0.240f, 16.00f, 0));
        l.add(single("bas_t12", VortaModule.A30_SWEEP, Category.BASIC,
                "단독 12: 55.0 Hz (+27.5Hz) — 콘크리트 벽면·슬래브 공진 최적점", "[조합2·3·4] 아파트 콘크리트 벽면·슬래브 공진 최적점 · 체감 진동 및 구조물 전달력 최강 마감", KNOB_A30,
                55.00f, 27.50f, true, 0.032f, 1.35f, true, 0.120f, 2.40f, false, 0.000f, 0.00f, 0));

        return Collections.unmodifiableList(l);
    }

    private static List<ScenarioSpec> buildProScenarios() {
        List<ScenarioSpec> l = new ArrayList<ScenarioSpec>();

        // 1. 시간대별 (4)
        l.add(single("pro_scen_morning", VortaModule.PRO_MANUAL, Category.MAIN,
                "아침 (07~13시): 55.0 Hz 직타", "주간 1차 룸모드 집중 직타 최적", KNOB_PRO,
                55.00f, 0.00f, true, 0.035f, 1.50f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("pro_scen_afternoon", VortaModule.PRO_MANUAL, Category.MAIN,
                "오후 (13~19시): 31.0 ~ 40.0 Hz (360s)", "복부·체성 펄스 진동 유지 최적", KNOB_PRO,
                31.00f, 40.00f, 360.0f, false, 0.000f, 0.00f, true, 0.150f, 4.00f, false, 0.000f, 0.00f, 0));
        l.add(single("pro_scen_evening", VortaModule.PRO_MANUAL, Category.MAIN,
                "저녁 (19~01시): 43.2 Hz 직타", "이중 파면·측파대 압박 최적", KNOB_PRO,
                43.20f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 12.50f, 0));
        l.add(single("pro_scen_sleep", VortaModule.PRO_MANUAL, Category.MAIN,
                "취침 (01~07시): 28.0 Hz 락온", "저변동 침실 밀착 야간 락온 최적", KNOB_PRO,
                28.00f, 0.00f, true, 0.025f, 0.80f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, 0));

        // 2. 상황별 (4)
        l.add(single("pro_scen_counter", VortaModule.PRO_MANUAL, Category.MAIN,
                "즉각 요격: 55.0 + 40.0 Hz 복합", "소음 발생 즉시 복합 충격파 대응", KNOB_PRO,
                55.00f, 40.00f, true, 0.035f, 1.50f, true, 0.150f, 2.00f, false, 0.000f, 0.00f, 0));
        l.add(sweep("pro_scen_commute", VortaModule.PRO_MANUAL, Category.MAIN,
                "출근 12h: 24.0 ~ 43.2 Hz (600s)", "부재중 장시간 발열 저감 순환", KNOB_PRO,
                24.00f, 43.20f, 600.0f, true, 0.040f, 1.20f, true, 0.150f, 2.50f, false, 0.000f, 0.00f, 0));
        l.add(single("pro_scen_outing", VortaModule.PRO_MANUAL, Category.MAIN,
                "일상 외출: 32.0 Hz 순항", "2~4시간 외출 기저 진동 유지", KNOB_PRO,
                32.00f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.80f, false, 0.000f, 0.00f, 0));
        l.add(single("pro_scen_protect", VortaModule.PRO_MANUAL, Category.MAIN,
                "3시간+ 기기 보호: 43.2 Hz 듀티", "보이스코일 열관리 안정 운용", KNOB_PRO,
                43.20f, 0.00f, false, 0.000f, 0.00f, true, 0.120f, 1.50f, false, 0.000f, 0.00f, 0));

        // 3. 전술별 (4)
        l.add(single("pro_scen_anti_adapt", VortaModule.PRO_MANUAL, Category.MAIN,
                "내성 파괴: 55.0 + 24.0 Hz 비동기", "신경 적응 무력화 비동기 도약", KNOB_PRO,
                55.00f, 24.00f, true, 0.100f, 7.03f, false, 0.000f, 0.00f, true, 0.300f, 35.95f, 0));
        l.add(single("pro_scen_focus_cut", VortaModule.PRO_MANUAL, Category.MAIN,
                "인지 차단: 40.0 Hz 뇌파 교란", "알파·감마 대역 인지 간섭 최적", KNOB_PRO,
                40.00f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.200f, 10.00f, 0));
        l.add(single("pro_scen_hammer", VortaModule.PRO_MANUAL, Category.MAIN,
                "집중 응징: 50.0 Hz 고밀도 해머", "1~2시간 단기 고밀도 응징 타격", KNOB_PRO,
                50.00f, 0.00f, false, 0.000f, 0.00f, false, 0.000f, 0.00f, true, 0.250f, 15.00f, 0));
        l.add(single("pro_scen_vestibular", VortaModule.PRO_MANUAL, Category.MAIN,
                "전정 교란: 18.5 Hz 평형 요동", "저속 위상 흔들기 평형 교란", KNOB_PRO,
                18.50f, 0.00f, true, 0.080f, 0.65f, true, 0.140f, 4.00f, false, 0.000f, 0.00f, 0));

        return Collections.unmodifiableList(l);
    }
}
