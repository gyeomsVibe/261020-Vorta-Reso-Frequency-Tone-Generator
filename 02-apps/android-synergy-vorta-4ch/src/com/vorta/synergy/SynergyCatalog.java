package com.vorta.synergy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 20 synchronized 4-channel synergy packages (80 programs across CH1~CH4)
 * plus 22 concise basic band sweeps & pure tones.
 * Commercial terminology only; zero device/location-specific jargon.
 */
public final class SynergyCatalog {

    public enum FilterGroup {
        TIME("시간대"),
        SITUATION("상황"),
        EFFECT("효과"),
        FUNCTION("기능"),
        ROLE("역할");

        public final String label;

        FilterGroup(String label) {
            this.label = label;
        }
    }

    public static final class ChannelProgram {
        public final SynergyChannel channel;
        public final ScenarioStep step;
        public final int dutyOnMin;
        public final int dutyCoolMin;
        public final String shortRoleNote;

        public ChannelProgram(
                SynergyChannel channel,
                ScenarioStep step,
                int dutyOnMin,
                int dutyCoolMin,
                String shortRoleNote) {
            this.channel = channel;
            this.step = step;
            this.dutyOnMin = Math.max(0, dutyOnMin);
            this.dutyCoolMin = Math.max(0, dutyCoolMin);
            this.shortRoleNote = shortRoleNote;
        }

        public String formatDutyBadge() {
            if (dutyCoolMin > 0 && dutyOnMin > 0) {
                return String.format(Locale.US, "듀티 %d/%d분", dutyOnMin, dutyCoolMin);
            }
            return "연속 가동";
        }
    }

    public static final class SynergyPackage {
        public final String code;
        public final FilterGroup group;
        public final String title;
        public final String summary;
        public final ChannelProgram ch1;
        public final ChannelProgram ch2;
        public final ChannelProgram ch3;
        public final ChannelProgram ch4;

        public SynergyPackage(
                String code,
                FilterGroup group,
                String title,
                String summary,
                ChannelProgram ch1,
                ChannelProgram ch2,
                ChannelProgram ch3,
                ChannelProgram ch4) {
            this.code = code;
            this.group = group;
            this.title = title;
            this.summary = summary;
            this.ch1 = ch1;
            this.ch2 = ch2;
            this.ch3 = ch3;
            this.ch4 = ch4;
        }

        public ChannelProgram programFor(SynergyChannel ch) {
            if (ch == SynergyChannel.CH1_SUB_LOW) return ch1;
            if (ch == SynergyChannel.CH2_LOW_MID) return ch2;
            if (ch == SynergyChannel.CH3_TACTILE_MAIN) return ch3;
            return ch4;
        }

        public String formatFourChannelLine() {
            return "CH1 " + ch1.step.minHz + "~" + (ch1.step.type == ScenarioStep.StepType.SINGLE && ch1.step.secondaryHz <= 0f ? "단일" : (ch1.step.secondaryHz > 0f ? ch1.step.secondaryHz : ch1.step.maxHz))
                    + " · CH2 " + ch2.step.minHz + (ch2.step.type == ScenarioStep.StepType.SINGLE ? (ch2.step.secondaryHz > 0f ? "+" + ch2.step.secondaryHz : "") : "~" + ch2.step.maxHz)
                    + " · CH3 " + ch3.step.minHz + (ch3.step.type == ScenarioStep.StepType.SINGLE ? "" : "~" + ch3.step.maxHz)
                    + " · CH4 " + ch4.step.minHz + (ch4.step.type == ScenarioStep.StepType.SINGLE ? "" : "~" + ch4.step.maxHz);
        }
    }

    public static final class BasicEntry {
        public final String id;
        public final String title;
        public final String effectDesc;
        public final SynergyChannel recommendedChannel;
        public final ScenarioStep step;

        public BasicEntry(
                String id,
                String title,
                String effectDesc,
                SynergyChannel recommendedChannel,
                ScenarioStep step) {
            this.id = id;
            this.title = title;
            this.effectDesc = effectDesc;
            this.recommendedChannel = recommendedChannel;
            this.step = step;
        }
    }

    private static final List<SynergyPackage> PACKAGES = buildPackages();
    private static final List<BasicEntry> BASIC = buildBasicCatalog();

    private SynergyCatalog() {}

    public static List<SynergyPackage> packages() {
        return PACKAGES;
    }

    public static List<SynergyPackage> packagesByGroup(FilterGroup group) {
        List<SynergyPackage> out = new ArrayList<SynergyPackage>();
        for (int i = 0; i < PACKAGES.size(); i++) {
            SynergyPackage p = PACKAGES.get(i);
            if (group == null || p.group == group) out.add(p);
        }
        return out;
    }

    public static SynergyPackage findPackage(String code) {
        if (code == null) return null;
        for (int i = 0; i < PACKAGES.size(); i++) {
            if (PACKAGES.get(i).code.equals(code)) return PACKAGES.get(i);
        }
        return null;
    }

    public static SynergyPackage packageForHour(int hour24) {
        int h = ((hour24 % 24) + 24) % 24;
        if (h >= 7 && h < 13) return PACKAGES.get(0);  // S-01
        if (h >= 13 && h < 19) return PACKAGES.get(1); // S-02
        if (h >= 19) return PACKAGES.get(2);           // S-03
        return PACKAGES.get(3);                        // S-04 (00~06)
    }

    public static List<BasicEntry> basicCatalog() {
        return BASIC;
    }

    public static BasicEntry findBasic(String id) {
        if (id == null) return null;
        for (int i = 0; i < BASIC.size(); i++) {
            if (BASIC.get(i).id.equals(id)) return BASIC.get(i);
        }
        return null;
    }

    private static ChannelProgram progSingle(
            SynergyChannel ch, int id, String title, float f1, float f2,
            boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR,
            int dutyOnMin, int dutyCoolMin, float trimDb, boolean jitter, String note) {
        ScenarioStep st = new ScenarioStep(
                id, title, ScenarioStep.StepType.SINGLE, f1, f1, f2, 300.0f, 2,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, true, jitter, trimDb);
        return new ChannelProgram(ch, st, dutyOnMin, dutyCoolMin, note);
    }

    private static ChannelProgram progSweep(
            SynergyChannel ch, int id, String title, float minHz, float maxHz, float sec,
            boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR,
            int dutyOnMin, int dutyCoolMin, float trimDb, boolean jitter, String note) {
        ScenarioStep st = new ScenarioStep(
                id, title, ScenarioStep.StepType.SWEEP, minHz, maxHz, 0.0f, sec, 2,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, true, jitter, trimDb);
        return new ChannelProgram(ch, st, dutyOnMin, dutyCoolMin, note);
    }

    private static ChannelProgram progStep(
            SynergyChannel ch, int id, String title, float minHz, float maxHz, int steps, float sec,
            boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR,
            int dutyOnMin, int dutyCoolMin, float trimDb, boolean jitter, String note) {
        ScenarioStep st = new ScenarioStep(
                id, title, ScenarioStep.StepType.STEPPED_DWELL, minHz, maxHz, 0.0f, sec, steps,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, true, jitter, trimDb);
        return new ChannelProgram(ch, st, dutyOnMin, dutyCoolMin, note);
    }

    private static List<SynergyPackage> buildPackages() {
        List<SynergyPackage> l = new ArrayList<SynergyPackage>();
        int id = 100;

        // 1. TIME (S-01 ~ S-04) — CH3(52~63Hz 최대 출력 메인) + CH4(40~51Hz / -4Hz 열보호 보조)
        l.add(new SynergyPackage("S-01", FilterGroup.TIME, "오전 활성 (07~13시)", "주간 활동·4Hz 맥놀이 복합 시너지",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 오전 스윕", 14.0f, 20.5f, 421.0f, true, 0.040f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "전정·안와 대역 서로소 스윕"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 오전 이중음", 28.0f, 31.5f, false, 0f, 0f, true, 0.150f, 3.2f, false, 0f, 0f, 0, 0, 0f, false, "구조 하단 이중음 맥동"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 55Hz 메인", 55.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "최대 출력 55Hz 핵심 연속 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 51Hz 보조", 51.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, 0f, false, "CH3-4Hz 맥놀이 · 20/10분 열보호")));

        l.add(new SynergyPackage("S-02", FilterGroup.TIME, "오후 광역 (13~19시)", "전대역 비충돌 스윕·스텝 압박",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 오후 스윕", 8.0f, 16.5f, 547.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "심박·자율신경 대역 스윕"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 오후 스윕", 23.0f, 38.5f, 467.0f, false, 0f, 0f, false, 0f, 0f, true, 0.200f, 12.5f, 0, 0, 0f, true, "중저역 구조 공진 광역 훑기"),
                progStep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 상단 5단 메인", 52.5f, 62.5f, 5, 313.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "최대 출력 5단 상단 공진 체류"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 보조", 40.5f, 51.0f, 240.0f, false, 0f, 0f, true, 0.120f, 3.0f, false, 0f, 0f, 20, 10, -1.0f, false, "하단 보강 스윕 · 20/10분 열보호")));

        l.add(new SynergyPackage("S-03", FilterGroup.TIME, "저녁 집중 (19~01시)", "핵심 공진 앵커 + 4Hz 제어 맥놀이",
                progSingle(SynergyChannel.CH1_SUB_LOW, id++, "CH1 저녁 이중음", 7.0f, 14.0f, true, 0.050f, 0.8f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "극저역 평형 대역 이중음"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 저녁 앵커", 32.4f, 0.0f, false, 0f, 0f, true, 0.150f, 3.8f, false, 0f, 0f, 0, 0, 0f, false, "구조 2차 공진 피크 직타"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 59Hz 메인", 59.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "고출력 59Hz 정상파 락온"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 55Hz 보조", 55.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, false, "CH3-4Hz 맥놀이 · 20/10분 열보호")));

        l.add(new SynergyPackage("S-04", FilterGroup.TIME, "심야 정밀 (01~07시)", "장주기 저변동·저발열 심야 락온",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 심야 스윕", 2.5f, 6.5f, 719.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "초저역 719초 저속 무음 스윕"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 심야 스윕", 22.0f, 29.0f, 613.0f, false, 0f, 0f, true, 0.120f, 0.8f, false, 0f, 0f, 0, 0, 0f, true, "저변동 613초 심야 맥동"),
                progStep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 심야 4단 메인", 53.5f, 61.0f, 4, 593.0f, true, 0.025f, 0.6f, false, 0f, 0f, false, 0f, 0f, 0, 0, -1.0f, true, "상단 4단계 정밀 체류 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 심야 보조", 46.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -2.5f, false, "15/15분 저발열 · -2.5dB 보호")));

        // 2. SITUATION (S-05 ~ S-08)
        l.add(new SynergyPackage("S-05", FilterGroup.SITUATION, "단기 외출 순환 (2~4h)", "서로소 2스윕 + 4Hz 맥놀이 균형",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 외출 스윕", 4.5f, 19.5f, 421.0f, true, 0.040f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "전 체성대역 421초 순환"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 외출 스윕", 24.0f, 38.0f, 467.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "중저역 467초 펄스 순환"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 54Hz 메인", 54.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "핵심 54Hz 풀출력 연속 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 50Hz 보조", 50.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, 0f, false, "CH3-4Hz 맥놀이 · 20/10분")));

        l.add(new SynergyPackage("S-06", FilterGroup.SITUATION, "장시간 무인 (12h)", "자발적 회복 휴지·발열 억제 순환",
                progStep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 12h 스텝", 6.0f, 18.0f, 5, 503.0f, true, 0.040f, 1.2f, false, 0f, 0f, false, 0f, 0f, 45, 15, 0f, true, "45/15분 듀티 · 스텝 체류"),
                progStep(SynergyChannel.CH2_LOW_MID, id++, "CH2 12h 스텝", 23.0f, 37.0f, 5, 557.0f, false, 0f, 0f, true, 0.140f, 2.5f, false, 0f, 0f, 45, 15, -1.0f, true, "45/15분 듀티 · 저발열"),
                progSweep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 12h 메인", 53.0f, 62.5f, 389.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "고내구 연속 389초 상단 스윕"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 12h 보조", 40.0f, 51.0f, 311.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -2.0f, false, "15/15분 쿨링 · -2dB 보호")));

        l.add(new SynergyPackage("S-07", FilterGroup.SITUATION, "초장기 저발열 (48h+)", "전채널 -2~-4dB 감쇄·장주기 보호",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 장기 저발열", 2.0f, 7.0f, 1201.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 45, 15, -3.0f, true, "1201초 극저부하 · -3dB"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 장기 저발열", 22.0f, 30.0f, 907.0f, false, 0f, 0f, true, 0.100f, 1.2f, false, 0f, 0f, 45, 15, -3.0f, true, "907초 저변위 · -3dB"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 53.6Hz 메인", 53.6f, 0.0f, false, 0f, 0f, true, 0.120f, 1.5f, false, 0f, 0f, 0, 0, -2.0f, false, "고내구 연속 53.6Hz · -2dB"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 43.2Hz 보조", 43.2f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -4.0f, false, "15/15분 냉각 · -4dB 보호")));

        l.add(new SynergyPackage("S-08", FilterGroup.SITUATION, "고출력 대역 회피", "중심 간섭대 우회·비차폐 4분할",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 비차폐 저역", 7.0f, 19.0f, 397.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "상향 차폐 회피 극저역"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 비차폐 중저", 26.0f, 36.0f, 449.0f, true, 0.040f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "독립 임계대역 449초"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 61.5Hz 메인", 61.5f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "상단 임계대역 61.5Hz 직타"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 42Hz 우회", 42.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -1.5f, false, "하단 42Hz 우회 · 15/15분")));

        // 3. EFFECT (S-09 ~ S-12)
        l.add(new SynergyPackage("S-09", FilterGroup.EFFECT, "4Hz 맥놀이 체감 극대화", "전 대역 4Hz 변동강도 피크 동기",
                progSingle(SynergyChannel.CH1_SUB_LOW, id++, "CH1 4Hz 차음", 8.0f, 12.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "8+12Hz 선형 4Hz 맥놀이"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 4Hz 차음", 28.0f, 32.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "28+32Hz 선형 4Hz 맥놀이"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 54Hz 메인", 54.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "54Hz 풀출력 기준 반송파"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 50Hz 맥놀이", 50.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, 0f, false, "CH3-4Hz 공간 맥놀이 · 20/10")));

        l.add(new SynergyPackage("S-10", FilterGroup.EFFECT, "슬래브 공진 스텝-드웰", "전채널 5~6단 정지 공진 축적",
                progStep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 5단 스텝", 5.0f, 19.0f, 5, 353.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "5개 체성 공진점 순차 체류"),
                progStep(SynergyChannel.CH2_LOW_MID, id++, "CH2 6단 스텝", 24.0f, 39.0f, 6, 419.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "6개 저역 공진점 순차 체류"),
                progStep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 상단 5단 메인", 52.5f, 62.5f, 5, 331.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "고출력 상단 5단 공진 빌드업"),
                progStep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 5단 보조", 40.5f, 50.5f, 5, 293.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, false, "하단 5단 보조 · 20/10분")));

        l.add(new SynergyPackage("S-11", FilterGroup.EFFECT, "전대역 내성 리셋", "4기 지터+서로소+복합 변조 풀가동",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 지터 FM+AM", 4.0f, 20.0f, 307.0f, true, 0.035f, 1.5f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "307초 지터 · FM+AM 복합"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 지터 FM+RM", 22.5f, 39.5f, 379.0f, true, 0.035f, 1.5f, false, 0f, 0f, true, 0.250f, 12.5f, 0, 0, 0f, true, "379초 지터 · FM+RM 복합"),
                progSweep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 지터 메인", 52.5f, 63.0f, 433.0f, false, 0f, 0f, true, 0.150f, 4.0f, true, 0.250f, 12.5f, 0, 0, 0f, true, "433초 상단 AM+RM 풀출력"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 지터 보조", 40.5f, 51.0f, 281.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 15, 10, -1.0f, true, "281초 하단 지터 · 15/10분")));

        l.add(new SynergyPackage("S-12", FilterGroup.EFFECT, "극저역 심층 무음 진동", "청감 최소·물리 진동 중심 저역 편성",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 무음 스윕", 1.5f, 8.5f, 641.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "1.5~8.5Hz 순수 초저역"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 심층 스윕", 22.0f, 27.5f, 569.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "22~27.5Hz 저청감 순수"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 44.5Hz 메인", 44.5f, 0.0f, true, 0.025f, 0.7f, false, 0f, 0f, false, 0f, 0f, 0, 0, -2.0f, false, "저청감 44.5Hz 메인 · -2dB"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 40.5Hz 보조", 40.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -3.0f, false, "CH3-4Hz 맥놀이 · 15/15분")));

        // 4. FUNCTION (S-13 ~ S-16)
        l.add(new SynergyPackage("S-13", FilterGroup.FUNCTION, "표준 혼합 (2단일+2스윕)", "단일 앵커 2기 + 서로소 스윕 2기",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 혼합 스윕", 5.0f, 18.5f, 431.0f, true, 0.040f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "저역 431초 지터 스윕"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 31.5Hz 앵커", 31.5f, 0.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, false, "31.5Hz 중량음 단일 앵커"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 55Hz 메인", 55.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "55Hz 고출력 슬래브 메인"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 보조", 41.5f, 50.5f, 347.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, true, "41.5~50.5Hz 스윕 · 20/10분")));

        l.add(new SynergyPackage("S-14", FilterGroup.FUNCTION, "전채널 서로소 광역 스윕", "4대역 빈틈없는 무중복 스윕 스캔",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 광역 스윕", 3.0f, 20.5f, 409.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "409초 전대역 하단 스캔"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 광역 스윕", 22.0f, 39.5f, 487.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "487초 중저역 풀스캔"),
                progSweep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 상단 메인", 52.5f, 63.0f, 359.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "359초 상단 고출력 풀스캔"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 보조", 40.0f, 51.0f, 277.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, true, "277초 하단 스캔 · 20/10분")));

        l.add(new SynergyPackage("S-15", FilterGroup.FUNCTION, "공진 축적 스텝 체인", "전채널 4단 정지·에너지 누적 모드",
                progStep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 4단 체류", 7.0f, 19.0f, 4, 283.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "7/11/15/19Hz 70초씩 체류"),
                progStep(SynergyChannel.CH2_LOW_MID, id++, "CH2 4단 체류", 25.0f, 37.0f, 4, 337.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, false, "25/29/33/37Hz 84초씩 체류"),
                progStep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 상단 4단 메인", 53.0f, 62.0f, 4, 317.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "53/56/59/62Hz 고출력 체류"),
                progStep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 4단 보조", 41.0f, 50.0f, 4, 263.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, false, "41/44/47/50Hz · 20/10분")));

        l.add(new SynergyPackage("S-16", FilterGroup.FUNCTION, "열보호 교대 릴레이", "발열 부하 최소화·안정 교대 운전",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 교대 스윕", 6.0f, 16.0f, 461.0f, false, 0f, 0f, true, 0.120f, 2.0f, false, 0f, 0f, 0, 0, 0f, true, "저부하 461초 스윕"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 29.5Hz 보호", 29.5f, 0.0f, false, 0f, 0f, true, 0.150f, 3.0f, false, 0f, 0f, 0, 0, -1.0f, false, "듀티형 맥동 앵커 · -1dB"),
                progSweep(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 상단 연속 메인", 52.0f, 62.5f, 373.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "고내구 연속 풀출력 전담 스윕"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 45.5Hz 냉각", 45.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -2.0f, false, "15분 가동 / 15분 완전 냉각")));

        // 5. ROLE (S-17 ~ S-20)
        l.add(new SynergyPackage("S-17", FilterGroup.ROLE, "극저역 체성 중심 편성", "CH1 체성 4~12.5Hz 집중 + 상단 보조",
                progStep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 체성 5단", 4.5f, 12.5f, 5, 367.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "ISO Wk 4~12.5Hz 집중 체류"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 24Hz 서포트", 24.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "24Hz 하부 앵커 보조"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 52.5Hz 메인", 52.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, -2.0f, false, "52.5Hz -2dB 균형 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 48.5Hz 맥놀이", 48.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 15, 15, -3.0f, false, "CH3-4Hz 맥놀이 · 15/15분")));

        l.add(new SynergyPackage("S-18", FilterGroup.ROLE, "중저역 공진 중심 편성", "CH2 24~38Hz 구조 공진 집중",
                progSingle(SynergyChannel.CH1_SUB_LOW, id++, "CH1 10.5Hz 서포트", 10.5f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "10.5Hz 자율신경 서포트"),
                progStep(SynergyChannel.CH2_LOW_MID, id++, "CH2 5단 집중", 24.0f, 38.0f, 5, 401.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "24~38Hz 구조 공진 집중"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 56Hz 메인", 56.0f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "상단 56Hz 고출력 공진 메인"),
                progSweep(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 하단 보조", 42.0f, 50.5f, 307.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, true, "42~50.5Hz 하단 보강 · 20/10")));

        l.add(new SynergyPackage("S-19", FilterGroup.ROLE, "골전도 4Hz 듀얼 중심", "CH3·CH4 4Hz 맥놀이 주력 + 저역 보조",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 저역 서포트", 6.0f, 15.0f, 521.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 0, 0, -2.0f, true, "6~15Hz -2dB 저역 보조"),
                progSingle(SynergyChannel.CH2_LOW_MID, id++, "CH2 28Hz 서포트", 28.0f, 0.0f, false, 0f, 0f, true, 0.120f, 2.0f, false, 0f, 0f, 0, 0, -2.0f, false, "28Hz -2dB 중저역 보조"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 57Hz 메인", 57.0f, 0.0f, true, 0.025f, 1.0f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "57Hz 고출력 골전도 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 53Hz 맥놀이", 53.0f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, false, "CH3-4Hz 강맥동 · 20/10분")));

        l.add(new SynergyPackage("S-20", FilterGroup.ROLE, "4대역 균등 분산 표준", "전체 4임계대역 가장 균형 잡힌 기본값",
                progSweep(SynergyChannel.CH1_SUB_LOW, id++, "CH1 표준 스윕", 5.0f, 20.0f, 439.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, true, "5~20Hz 균등 439초 스윕"),
                progSweep(SynergyChannel.CH2_LOW_MID, id++, "CH2 표준 스윕", 22.5f, 39.0f, 499.0f, false, 0f, 0f, true, 0.150f, 4.0f, false, 0f, 0f, 0, 0, 0f, true, "22.5~39Hz 균등 499초 스윕"),
                progSingle(SynergyChannel.CH3_TACTILE_MAIN, id++, "CH3 55.5Hz 메인", 55.5f, 0.0f, true, 0.035f, 1.5f, false, 0f, 0f, false, 0f, 0f, 0, 0, 0f, false, "55.5Hz 고출력 골전도 메인"),
                progSingle(SynergyChannel.CH4_TACTILE_SUB, id++, "CH4 46.5Hz 보조", 46.5f, 0.0f, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 20, 10, -1.0f, false, "46.5Hz 하단 · 20/10분 보호")));

        return Collections.unmodifiableList(l);
    }

    private static BasicEntry basicSweep(
            int idNum, String title, String effect, SynergyChannel ch,
            float minHz, float maxHz, float sec,
            boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR) {
        ScenarioStep st = new ScenarioStep(
                idNum, title, ScenarioStep.StepType.SWEEP, minHz, maxHz, 0.0f, sec, 2,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, true, false, 0.0f);
        return new BasicEntry("bas_s" + idNum, title, effect, ch, st);
    }

    private static BasicEntry basicSingle(
            int idNum, String title, String effect, SynergyChannel ch,
            float f1, float f2,
            boolean fmOn, float fmD, float fmR,
            boolean amOn, float amD, float amR,
            boolean rmOn, float rmD, float rmR) {
        ScenarioStep st = new ScenarioStep(
                idNum, title, ScenarioStep.StepType.SINGLE, f1, f1, f2, 300.0f, 2,
                fmOn, fmD, fmR, amOn, amD, amR, rmOn, rmD, rmR, true, false, 0.0f);
        return new BasicEntry("bas_t" + idNum, title, effect, ch, st);
    }

    private static List<BasicEntry> buildBasicCatalog() {
        List<BasicEntry> l = new ArrayList<BasicEntry>();
        // 10 Basic Band Sweeps
        l.add(basicSweep(1, "대역 01: 1.0 ~ 2.5 Hz (300s)", "이석기관 자극 · 수면 후 무기력감", SynergyChannel.CH1_SUB_LOW,
                1.0f, 2.5f, 300.0f, true, 0.060f, 0.25f, false, 0f, 0f, false, 0f, 0f));
        l.add(basicSweep(2, "대역 02: 2.5 ~ 4.5 Hz (360s)", "델타파 동조 차단 · 수면 리듬 교란", SynergyChannel.CH1_SUB_LOW,
                2.5f, 4.5f, 360.0f, true, 0.080f, 0.45f, true, 0.120f, 1.80f, false, 0f, 0f));
        l.add(basicSweep(3, "대역 03: 4.5 ~ 7.0 Hz (420s)", "복부 내장 기본 공진 · 세타파 분리", SynergyChannel.CH1_SUB_LOW,
                4.5f, 7.0f, 420.0f, false, 0f, 0f, true, 0.180f, 3.90f, false, 0f, 0f));
        l.add(basicSweep(4, "대역 04: 7.0 ~ 11.0 Hz (480s)", "전정·뇌간 자극 · 알파파 멍함 유도", SynergyChannel.CH1_SUB_LOW,
                7.0f, 11.0f, 480.0f, true, 0.085f, 0.78f, true, 0.150f, 4.00f, false, 0f, 0f));
        l.add(basicSweep(5, "대역 05: 11.0 ~ 16.5 Hz (500s)", "베타파 간섭 · 인후·장연동 교란", SynergyChannel.CH1_SUB_LOW,
                11.0f, 16.5f, 500.0f, true, 0.070f, 1.25f, true, 0.180f, 4.00f, false, 0f, 0f));
        l.add(basicSweep(6, "대역 06: 16.5 ~ 21.0 Hz (450s)", "두개·안구 공진 · 시야 피로 유도", SynergyChannel.CH1_SUB_LOW,
                16.5f, 21.0f, 450.0f, true, 0.090f, 6.20f, true, 0.160f, 4.00f, true, 0.220f, 18.50f));
        l.add(basicSweep(7, "대역 07: 21.5 ~ 27.0 Hz (500s)", "내이 방향감각 저하 · 흉곽 압박", SynergyChannel.CH2_LOW_MID,
                21.5f, 27.0f, 500.0f, true, 0.055f, 1.15f, true, 0.180f, 3.50f, false, 0f, 0f));
        l.add(basicSweep(8, "대역 08: 27.0 ~ 36.0 Hz (520s)", "골반·흉곽·상복부 묵직한 타격", SynergyChannel.CH2_LOW_MID,
                27.0f, 36.0f, 520.0f, true, 0.060f, 1.80f, true, 0.170f, 3.20f, true, 0.180f, 13.50f));
        l.add(basicSweep(9, "대역 09: 36.0 ~ 46.0 Hz (580s)", "반복 불쾌 울림 · 집중력 저하", SynergyChannel.CH4_TACTILE_SUB,
                36.0f, 46.0f, 580.0f, true, 0.045f, 1.40f, true, 0.140f, 4.00f, true, 0.240f, 16.00f));
        l.add(basicSweep(10, "대역 10: 46.0 ~ 55.0 Hz (650s)", "구조 벽면 공진 · 공간 전달력 극대화", SynergyChannel.CH3_TACTILE_MAIN,
                46.0f, 55.0f, 650.0f, true, 0.038f, 1.15f, true, 0.120f, 2.40f, true, 0.200f, 11.50f));

        // 12 Basic Pure / Sub-harmonic Single Tones
        l.add(basicSingle(1, "단독 01: 7.0 Hz (+3.5Hz)", "구조 심부 공진 · 전정 평형 교란", SynergyChannel.CH1_SUB_LOW,
                7.0f, 3.5f, true, 0.075f, 0.55f, true, 0.120f, 3.80f, false, 0f, 0f));
        l.add(basicSingle(2, "단독 02: 10.5 Hz (+5.25Hz)", "심박 변동·자율신경 교란 · 오심 유도", SynergyChannel.CH1_SUB_LOW,
                10.5f, 5.25f, true, 0.085f, 0.95f, true, 0.140f, 4.00f, false, 0f, 0f));
        l.add(basicSingle(3, "단독 03: 16.0 Hz (+8.0Hz)", "위상 어긋남 현기증 · 복부 압박", SynergyChannel.CH1_SUB_LOW,
                16.0f, 8.0f, true, 0.060f, 1.25f, true, 0.200f, 4.00f, false, 0f, 0f));
        l.add(basicSingle(4, "단독 04: 18.0 Hz (+9.0Hz)", "벽면 불쾌 공명 · 두통·무기력", SynergyChannel.CH1_SUB_LOW,
                18.0f, 9.0f, true, 0.085f, 6.30f, false, 0f, 0f, true, 0.220f, 18.00f));
        l.add(basicSingle(5, "단독 05: 21.0 Hz (+10.5Hz)", "시신경·안구 공진 · 시각 긴장", SynergyChannel.CH1_SUB_LOW,
                21.0f, 10.5f, true, 0.055f, 2.10f, true, 0.180f, 4.00f, false, 0f, 0f));
        l.add(basicSingle(6, "단독 06: 22.0 Hz (+11.0Hz)", "내이 미로기관 공진 · 방향감 저하", SynergyChannel.CH2_LOW_MID,
                22.0f, 11.0f, true, 0.090f, 3.40f, true, 0.140f, 2.50f, false, 0f, 0f));
        l.add(basicSingle(7, "단독 07: 24.0 Hz (+12.0Hz)", "벽체 공진 · 흉곽·호흡 압박감", SynergyChannel.CH2_LOW_MID,
                24.0f, 12.0f, true, 0.045f, 1.15f, true, 0.160f, 3.70f, false, 0f, 0f));
        l.add(basicSingle(8, "단독 08: 25.0 Hz (+12.5Hz)", "하복부 진동 · 하체 무력감 유도", SynergyChannel.CH2_LOW_MID,
                25.0f, 12.5f, false, 0f, 0f, true, 0.180f, 4.20f, false, 0f, 0f));
        l.add(basicSingle(9, "단독 09: 28.0 Hz (+14.0Hz)", "골반저근 직타 · 하복부 둔통", SynergyChannel.CH2_LOW_MID,
                28.0f, 14.0f, true, 0.050f, 0.90f, true, 0.170f, 3.15f, false, 0f, 0f));
        l.add(basicSingle(10, "단독 10: 31.0 Hz (+15.5Hz)", "흉곽·상복부 장기 묵직한 타격", SynergyChannel.CH2_LOW_MID,
                31.0f, 15.5f, false, 0f, 0f, true, 0.160f, 2.75f, true, 0.180f, 13.50f));
        l.add(basicSingle(11, "단독 11: 40.0 Hz (+20.0Hz)", "반복 저주파 울림 · 인지 집중 방해", SynergyChannel.CH4_TACTILE_SUB,
                40.0f, 20.0f, false, 0f, 0f, true, 0.140f, 4.00f, true, 0.240f, 16.00f));
        l.add(basicSingle(12, "단독 12: 55.0 Hz (+27.5Hz)", "콘크리트 슬래브 1차 공진 최적점", SynergyChannel.CH3_TACTILE_MAIN,
                55.0f, 27.5f, true, 0.032f, 1.35f, true, 0.120f, 2.40f, false, 0f, 0f));
        return Collections.unmodifiableList(l);
    }
}
