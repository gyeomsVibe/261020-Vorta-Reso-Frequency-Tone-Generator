package com.vorta.aegis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java chronological stage ordering across all 8 tabs (covering all 88 presets)
 * and 4 one-tap multi-stage Auto-Course Routines.
 * Zero Android framework imports for deterministic host JVM verification.
 */
public final class JourneyOrderPolicy {

    public static final class StageGroup {
        public final int tabIndex;
        public final int stageNum;
        public final String title;
        public final String subtitle;
        public final int[] presetIds;

        public StageGroup(int tabIndex, int stageNum, String title, String subtitle, int[] presetIds) {
            this.tabIndex = tabIndex;
            this.stageNum = stageNum;
            this.title = title;
            this.subtitle = subtitle;
            this.presetIds = presetIds;
        }
    }

    public static final class RoutineStep {
        public final int presetId;
        public final int durationMinutes;
        public final String stageLabel;

        public RoutineStep(int presetId, int durationMinutes, String stageLabel) {
            this.presetId = presetId;
            this.durationMinutes = durationMinutes;
            this.stageLabel = stageLabel;
        }
    }

    public static final class RoutineCourse {
        public final String id;
        public final String title;
        public final String badge;
        public final int tabIndex;
        public final RoutineStep[] steps;

        public RoutineCourse(String id, String title, String badge, int tabIndex, RoutineStep[] steps) {
            this.id = id;
            this.title = title;
            this.badge = badge;
            this.tabIndex = tabIndex;
            this.steps = steps;
        }

        public int getTotalMinutes() {
            int sum = 0;
            for (int i = 0; i < steps.length; i++) {
                sum += steps[i].durationMinutes;
            }
            return sum;
        }
    }

    private static final StageGroup[][] TAB_STAGES = new StageGroup[][]{
            // Tab 0: 안티방어 (16)
            {
                    new StageGroup(0, 1, "1단계 · 신체 증상별 즉시 방어", "눈·어지러움·가슴·척추 대응",
                            new int[]{1, 2, 3, 7, 8, 5, 6}),
                    new StageGroup(0, 2, "2단계 · 야간 수면·실내 험 상쇄", "야간 저주파·55/60Hz 완충",
                            new int[]{4, 222, 9, 115}),
                    new StageGroup(0, 3, "3단계 · 자동 추적 스윕 방어", "변동형·전 대역 순환 분산",
                            new int[]{10, 11, 12, 223}),
                    new StageGroup(0, 4, "4단계 · 스피커·이어폰 점검", "L/R 위상·밸런스 확인",
                            new int[]{116})
            },
            // Tab 1: 수면 (12)
            {
                    new StageGroup(1, 1, "1단계 · 취침 전 준비·긴장 풀기", "이완 호흡·몸 긴장 해소",
                            new int[]{108, 16, 207}),
                    new StageGroup(1, 2, "2단계 · 입면 유도", "알파→델타 하강·잡념 진정",
                            new int[]{208, 14, 15}),
                    new StageGroup(1, 3, "3단계 · 깊은 숙면·소음 차폐", "서파 숙면·야간 소음 실드",
                            new int[]{13, 17, 18, 107}),
                    new StageGroup(1, 4, "4단계 · 낮잠·아침 기상", "파워 낮잠·기상 관성 깨우기",
                            new int[]{106, 209})
            },
            // Tab 2: 집중·공부 (14)
            {
                    new StageGroup(2, 1, "1단계 · 집중 워밍업·시동", "산만함 정리·작업 착수",
                            new int[]{210, 21, 104}),
                    new StageGroup(2, 2, "2단계 · 본 작업·학습 몰입", "독서·암기·코딩·깊은 몰입",
                            new int[]{20, 22, 19, 102, 211, 23}),
                    new StageGroup(2, 3, "3단계 · 소음 차폐·영상 병행", "브라운·스피치 마스킹",
                            new int[]{101, 103, 24, 105}),
                    new StageGroup(2, 4, "4단계 · 집중 후 두뇌 쿨다운", "과열된 뇌 피로 리셋",
                            new int[]{212})
            },
            // Tab 3: 명상·안정 (11)
            {
                    new StageGroup(3, 1, "1단계 · 호흡 정돈·긴장 해소", "4-6 호흡·심박 안정",
                            new int[]{109, 213, 27}),
                    new StageGroup(3, 2, "2단계 · 마음 가라앉히기·화음", "슈만 공명·정서 안정",
                            new int[]{25, 28, 111}),
                    new StageGroup(3, 3, "3단계 · 깊은 명상·내면 몰입", "세타 진입·창의 영감",
                            new int[]{214, 26, 110}),
                    new StageGroup(3, 4, "4단계 · 퇴근·하루 마무리", "번아웃·저녁 감정 정리",
                            new int[]{29, 215})
            },
            // Tab 4: 두통·통증 (10)
            {
                    new StageGroup(4, 1, "1단계 · 눈·목·어깨 피로 풀기", "화면 피로·거북목 이완",
                            new int[]{113, 33, 216}),
                    new StageGroup(4, 2, "2단계 · 두통·편두통 감압", "저자극 진정·감압 휴식",
                            new int[]{30, 32, 34, 217}),
                    new StageGroup(4, 3, "3단계 · 근육·허리·이명 보조", "온열 저역·대역 마스킹",
                            new int[]{31, 218, 112})
            },
            // Tab 5: 운동·피로 (12)
            {
                    new StageGroup(5, 1, "1단계 · 운동 전 준비", "몸·마음 워밍업 & 신경 점화",
                            new int[]{201, 202}),
                    new StageGroup(5, 2, "2단계 · 본 운동 (상황별)", "고중량·근비대·유산소·인터벌",
                            new int[]{35, 203, 36, 204}),
                    new StageGroup(5, 3, "3단계 · 운동 직후 쿨다운", "심박 하강·부교감 전환",
                            new int[]{205, 37}),
                    new StageGroup(5, 4, "4단계 · 근육 회복·피로 리셋", "근육통 복구·일상 활력",
                            new int[]{206, 114, 38, 39})
            },
            // Tab 6: 기분·힐링 (8)
            {
                    new StageGroup(6, 1, "1단계 · 불안·화·스트레스 진정", "치미는 감정 온도 낮추기",
                            new int[]{219, 44, 43}),
                    new StageGroup(6, 2, "2단계 · 정서 힐링·마음 위로", "396+528 화음·스트레스 완화",
                            new int[]{41, 42, 220}),
                    new StageGroup(6, 3, "3단계 · 무기력 탈출·기분 전환", "산뜻한 활력·에너지 회복",
                            new int[]{40, 221})
            },
            // Tab 7: 모기·벌레 (5)
            {
                    new StageGroup(7, 1, "1단계 · 실내·취침 무음 방어", "16~21.5kHz 무음 가드",
                            new int[]{45}),
                    new StageGroup(7, 2, "2단계 · 창가·실내 날벌레 보조", "날파리·모기 기피 펄스",
                            new int[]{47, 224}),
                    new StageGroup(7, 3, "3단계 · 캠핑·야외 강력 방어", "잠자리 천적 펄스·복합 가드",
                            new int[]{46, 48})
            }
    };

    private static final List<RoutineCourse> ROUTINES;
    private static final List<Integer> GLOBAL_ORDERED_IDS;

    static {
        List<RoutineCourse> rList = new ArrayList<>(4);
        rList.add(new RoutineCourse(
                "workout_heavy",
                "웨이트 풀코스",
                "준비→고중량→쿨다운→회복 · 65분",
                5,
                new RoutineStep[]{
                        new RoutineStep(201, 5, "운동 전 워밍업"),
                        new RoutineStep(202, 5, "신경 점화"),
                        new RoutineStep(35, 35, "고중량 몰입"),
                        new RoutineStep(205, 10, "심박 하강 쿨다운"),
                        new RoutineStep(206, 10, "근육 깊은 회복")
                }));
        rList.add(new RoutineCourse(
                "workout_volume",
                "근비대·유산소 코스",
                "준비→반복세트→쿨다운 · 55분",
                5,
                new RoutineStep[]{
                        new RoutineStep(201, 5, "운동 전 워밍업"),
                        new RoutineStep(203, 35, "근비대 몰입"),
                        new RoutineStep(37, 15, "근육 쿨다운")
                }));
        rList.add(new RoutineCourse(
                "focus_deep",
                "깊은 집중 풀코스",
                "예열→깊은몰입→뇌휴식 · 60분",
                2,
                new RoutineStep[]{
                        new RoutineStep(210, 5, "몰입 예열"),
                        new RoutineStep(102, 45, "40Hz 깊은 몰입"),
                        new RoutineStep(212, 10, "뇌 피로 쿨다운")
                }));
        rList.add(new RoutineCourse(
                "sleep_deep",
                "꿀잠 숙면 코스",
                "4-6호흡→입면→소음차폐 · 60분",
                1,
                new RoutineStep[]{
                        new RoutineStep(207, 10, "4-6 호흡 이완"),
                        new RoutineStep(208, 20, "10→2Hz 입면 하강"),
                        new RoutineStep(107, 30, "심야 브라운 실드")
                }));
        ROUTINES = Collections.unmodifiableList(rList);

        List<Integer> global = new ArrayList<>(88);
        for (int t = 0; t < TAB_STAGES.length; t++) {
            StageGroup[] groups = TAB_STAGES[t];
            for (int g = 0; g < groups.length; g++) {
                int[] ids = groups[g].presetIds;
                for (int k = 0; k < ids.length; k++) {
                    global.add(ids[k]);
                }
            }
        }
        GLOBAL_ORDERED_IDS = Collections.unmodifiableList(global);
    }

    private JourneyOrderPolicy() {
    }

    public static StageGroup[] getStagesForTab(int tabIndex) {
        int clamped = TabSyncPolicy.clampTabIndex(tabIndex);
        return TAB_STAGES[clamped];
    }

    public static List<PresetModel> getOrderedPresetsForTab(int tabIndex) {
        StageGroup[] groups = getStagesForTab(tabIndex);
        List<PresetModel> out = new ArrayList<>();
        for (int g = 0; g < groups.length; g++) {
            int[] ids = groups[g].presetIds;
            for (int k = 0; k < ids.length; k++) {
                out.add(JourneyPackCatalog.findAnyById(ids[k]));
            }
        }
        return out;
    }

    public static String getStageLabelForPreset(int presetId) {
        for (int t = 0; t < TAB_STAGES.length; t++) {
            StageGroup[] groups = TAB_STAGES[t];
            for (int g = 0; g < groups.length; g++) {
                int[] ids = groups[g].presetIds;
                for (int k = 0; k < ids.length; k++) {
                    if (ids[k] == presetId) {
                        return groups[g].title;
                    }
                }
            }
        }
        return "";
    }

    public static int getNextOrderedPresetId(int currentId, int delta) {
        int size = GLOBAL_ORDERED_IDS.size();
        if (size == 0) {
            return 1;
        }
        int idx = 0;
        for (int i = 0; i < size; i++) {
            if (GLOBAL_ORDERED_IDS.get(i) == currentId) {
                idx = i;
                break;
            }
        }
        int nextIdx = ((idx + delta) % size + size) % size;
        return GLOBAL_ORDERED_IDS.get(nextIdx);
    }

    public static List<RoutineCourse> getAllRoutines() {
        return ROUTINES;
    }

    public static RoutineCourse getRoutineById(String id) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < ROUTINES.size(); i++) {
            RoutineCourse c = ROUTINES.get(i);
            if (id.equals(c.id)) {
                return c;
            }
        }
        return null;
    }
}
