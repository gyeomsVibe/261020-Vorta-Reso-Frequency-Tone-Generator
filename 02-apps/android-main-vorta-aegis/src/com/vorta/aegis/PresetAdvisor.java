package com.vorta.aegis;

import java.util.Locale;

/**
 * Pure-Java recommendation, plain-Korean search, and listening guidance advisor.
 * Zero Android framework imports for deterministic host JVM verification.
 */
public final class PresetAdvisor {
    private static final int[] REC_MORNING = new int[]{209, 210, 1};
    private static final int[] REC_FOCUS = new int[]{102, 211, 103};
    private static final int[] REC_EVENING = new int[]{201, 35, 205};
    private static final int[] REC_NIGHT = new int[]{208, 107, 4};

    private static final String[][] SYNONYMS = new String[][]{
            {"잠", "수면", "숙면", "취침", "델타", "밤", "입면"},
            {"불면", "수면", "숙면", "취침", "델타", "밤", "입면"},
            {"잘때", "수면", "숙면", "취침", "델타", "밤"},
            {"헬스", "운동", "웨이트", "근력", "근비대", "워밍업", "쿨다운"},
            {"근력", "운동", "웨이트", "근비대"},
            {"워밍업", "준비", "예열", "운동"},
            {"준비", "워밍업", "점화", "예열"},
            {"쿨다운", "하강", "이완", "회복"},
            {"근육통", "회복", "복구", "근신경"},
            {"회복", "복구", "휴식", "쿨다운", "피로"},
            {"근비대", "볼륨", "반복", "웨이트"},
            {"인터벌", "서킷", "고강도", "hiit"},
            {"기상", "아침", "깨우기", "각성"},
            {"런닝", "운동", "유산소", "러닝"},
            {"러닝", "운동", "유산소"},
            {"공부", "집중", "딥워크", "포모도로", "감마", "알파", "암기"},
            {"포모도로", "25분", "집중", "루틴", "휴식"},
            {"딥워크", "몰입", "집중", "감마"},
            {"일", "집중", "업무", "딥워크", "포모도로", "베타"},
            {"업무", "집중", "딥워크", "포모도로", "베타"},
            {"코딩", "집중", "딥워크", "베타", "감마"},
            {"이명", "귀울림", "마스킹"},
            {"머리", "두통", "통증", "편두통"},
            {"편두통", "두통", "통증", "진정", "감압"},
            {"거북목", "승모근", "목", "어깨"},
            {"화", "짜증", "감정", "진정", "스트레스"},
            {"짜증", "화", "감정", "진정", "스트레스"},
            {"층간", "안티", "차폐", "마스킹", "슬래브", "상쇄", "바닥"},
            {"소음", "안티", "차폐", "마스킹", "슬래브", "상쇄"},
            {"차폐", "안티", "마스킹", "브라운", "핑크"},
            {"방어", "안티", "상쇄", "스윕"},
            {"비", "브라운", "핑크", "마스킹"},
            {"빗소리", "브라운", "핑크", "마스킹"},
            {"노이즈", "브라운", "핑크", "마스킹"},
            {"귀울림", "이명", "마스킹"},
            {"날파리", "초파리", "모기", "해충"},
            {"벌레", "모기", "해충", "날파리"},
            {"해충", "모기", "날파리"}
    };

    private PresetAdvisor() {
    }

    /** Returns 3 curated preset IDs appropriate for the given hour of day (0..23). */
    public static int[] getTimeOfDayRecommendations(int hourOfDay) {
        int h = ((hourOfDay % 24) + 24) % 24;
        if (h >= 6 && h <= 10) {
            return REC_MORNING.clone();
        }
        if (h >= 11 && h <= 16) {
            return REC_FOCUS.clone();
        }
        if (h >= 17 && h <= 21) {
            return REC_EVENING.clone();
        }
        return REC_NIGHT.clone();
    }

    /** Returns a friendly Korean time-window recommendation header label. */
    public static String getTimeWindowLabel(int hourOfDay) {
        int h = ((hourOfDay % 24) + 24) % 24;
        if (h >= 6 && h <= 10) {
            return "아침 추천 · 원탭 시작";
        }
        if (h >= 11 && h <= 16) {
            return "낮·집중 추천 · 원탭 시작";
        }
        if (h >= 17 && h <= 21) {
            return "저녁·운동 추천 · 원탭 시작";
        }
        return "심야·수면 추천 · 원탭 시작";
    }

    /**
     * Evaluates plain-Korean multi-token search with everyday synonym expansion, stage labels, and Hz matching.
     */
    public static boolean matchesQuery(PresetModel p, String tabName, String rawQuery) {
        if (p == null) {
            return false;
        }
        if (rawQuery == null) {
            return true;
        }
        String trimmed = rawQuery.trim();
        if (trimmed.isEmpty()) {
            return true;
        }

        StringBuilder hay = new StringBuilder(320);
        String code = JourneyPackCatalog.getShortCode(p);
        hay.append(code).append(' ');
        if (tabName != null) hay.append(tabName).append(' ');
        String stageTitle = JourneyOrderPolicy.getStageLabelForPreset(p.id);
        if (!stageTitle.isEmpty()) hay.append(stageTitle).append(' ');
        if (p.title != null) hay.append(p.title).append(' ');
        if (p.badge != null) hay.append(p.badge).append(' ');
        if (p.desc != null) hay.append(p.desc).append(' ');

        if (p.isSweep) {
            if (p.freq1 > 0f) hay.append(formatHzToken(p.freq1)).append(' ');
            hay.append(formatHzToken(p.sweepStart)).append(' ');
            hay.append(formatHzToken(p.sweepEnd)).append(' ');
        } else {
            if (p.freq1 > 0f) hay.append(formatHzToken(p.freq1)).append(' ');
            if (p.freq2 > 0f) hay.append(formatHzToken(p.freq2)).append(' ');
            if (p.binauralBeat > 0f) hay.append(formatHzToken(p.binauralBeat)).append(' ');
        }
        if (p.isAntiPhase) hay.append("역위상 상쇄 ");
        if (p.noiseType == 4) hay.append("브라운 노이즈 ");
        if (p.noiseType == 5) hay.append("이명 마스킹 ");
        if (p.noiseType == 1) hay.append("핑크 노이즈 ");

        String hayLower = hay.toString().toLowerCase(Locale.ROOT);
        String hayCompact = hayLower.replace(" ", "");

        String[] tokens = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (tok.isEmpty()) continue;
            if (!matchesToken(hayLower, hayCompact, tok)) {
                return false;
            }
        }
        return true;
    }

    private static String formatHzToken(float hz) {
        if (hz >= 1000.0f) {
            return String.format(Locale.US, "%.1f hz %.0f hz %.1f khz", hz, hz, hz / 1000.0f);
        }
        if (Math.abs(hz - Math.round(hz)) < 0.05f) {
            return String.format(Locale.US, "%.0f hz %.2f hz", hz, hz);
        }
        return String.format(Locale.US, "%.2f hz %.1f hz", hz, hz);
    }

    private static boolean matchesToken(String hayLower, String hayCompact, String tok) {
        if (hayLower.contains(tok) || hayCompact.contains(tok)) {
            return true;
        }
        for (int i = 0; i < SYNONYMS.length; i++) {
            String[] row = SYNONYMS[i];
            if (row[0].equals(tok)) {
                for (int j = 1; j < row.length; j++) {
                    if (hayLower.contains(row[j])) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Returns a plain-Korean listening device and recommended volume guide badge for a preset. */
    public static String getListeningGuideBadge(PresetModel p) {
        if (p == null) {
            return "스피커/이어폰 OK · 추천 30~50%";
        }
        if (p.noiseType == 2) {
            return "스피커 전용 · 이어폰 주의 · 추천 75~100%";
        }
        if (p.binauralBeat > 0.0f || (p.isSweep && p.freq1 > 0.0f)) {
            return "이어폰 최적 · 음악 병행 OK · 추천 25~45%";
        }
        boolean lowAnti = p.isAntiPhase && ((p.isSweep && p.sweepStart < 60.0f)
                || (!p.isSweep && p.freq1 > 0.0f && p.freq1 < 60.0f));
        if (lowAnti) {
            return "저역 방어 · 우퍼/이어폰 최적 · 추천 45~75%";
        }
        if (p.noiseType == 4 || p.noiseType == 5 || p.noiseType == 1) {
            return "스피커/이어폰 모두 OK · 추천 20~45%";
        }
        if (p.tabIndex == 1 || p.tabIndex == 4) {
            return "조용한 휴식 · 음악 병행 OK · 추천 20~45%";
        }
        return "스피커/이어폰 OK · 음악 병행 가능 · 추천 30~50%";
    }

    /** Returns an approachable default listening volume percentage (15..100) for a preset. */
    public static int getRecommendedVolumePercent(PresetModel p) {
        if (p == null) {
            return VolumeCurve.DEFAULT_PERCENT;
        }
        if (p.noiseType == 2) {
            return 85;
        }
        if (p.id == 105 || p.id == 112 || p.id == 28 || p.id == 32 || p.id == 217) {
            return 25;
        }
        if (p.tabIndex == 1 || p.tabIndex == 3 || p.tabIndex == 4) {
            return 35;
        }
        if (p.tabIndex == 0) {
            return 55;
        }
        return 45;
    }

    /**
     * Returns true when switching from a high volume (>75%) into a calm sleep/relax/pain preset
     * without saved per-preset volume memory, so the service can auto-soften cleanly.
     */
    public static boolean shouldWarnHighVolumeSwitch(PresetModel from, PresetModel to, int currentVolPct) {
        if (currentVolPct <= 75 || to == null || to.noiseType == 2) {
            return false;
        }
        return to.tabIndex == 1 || to.tabIndex == 3 || to.tabIndex == 4
                || to.id == 105 || to.id == 112;
    }

    /** Builds a beginner-friendly plain-Korean explanation for any preset without technical barriers. */
    public static String buildPlainExplanation(PresetModel p) {
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(420);
        sb.append("[이게 뭔가요?]\n");
        String customIntro = getSpecialTopicIntro(p.id);
        if (!customIntro.isEmpty()) {
            sb.append(customIntro).append('\n');
        }
        sb.append(getPlainSoundMechanism(p)).append("\n\n");

        String stageLabel = JourneyOrderPolicy.getStageLabelForPreset(p.id);
        if (!stageLabel.isEmpty()) {
            sb.append("[여정 단계]\n").append(stageLabel).append("\n\n");
        }

        sb.append("[언제 · 어떻게 쓰나요?]\n");
        if (p.desc != null && !p.desc.trim().isEmpty()) {
            sb.append(p.desc.trim()).append('\n');
        }
        sb.append("• 청취 가이드: ").append(getListeningGuideBadge(p))
          .append(" (추천 시작 볼륨 ").append(getRecommendedVolumePercent(p)).append("%)\n\n");

        sb.append("[참고 원리]\n").append(getPlainScienceNote(p));
        return sb.toString();
    }

    private static String getSpecialTopicIntro(int id) {
        switch (id) {
            case 104:
                return "• '포모도로'는 25분 동안 한 가지 일에만 집중하고 5분 쉬는 가장 유명한 시간 관리 집중법입니다. 30분 타이머와 함께 켜두면 공부·업무 리듬을 쉽게 유지할 수 있습니다.";
            case 102:
                return "• '딥워크(깊은 몰입)'는 스마트폰 알림이나 잡념 없이 한 가지 어려운 일에 1시간 이상 깊게 빠져드는 작업 방식을 뜻합니다.";
            case 109:
            case 205:
            case 207:
            case 213:
            case 219:
                return "• '4-6 호흡법'은 4초 동안 천천히 숨을 들이쉬고 6초 동안 길게 내쉬는 호흡법입니다. 날숨을 길게 쉬면 심박이 차분해지고 긴장이 빨리 풀립니다.";
            case 202:
                return "• 고중량 본 세트에 들어가기 전, 힘과 집중력을 순간적으로 최대로 끌어올려 자세가 흔들리지 않게 준비하는 프리셋입니다.";
            case 203:
                return "• '근비대'는 8~15회씩 여러 세트를 반복해 근육 크기를 키우는 운동 방식입니다. 지루하거나 집중이 흐트러질 때 리듬을 잡아줍니다.";
            case 204:
                return "• '인터벌·서킷'은 고강도로 짧게 몰아치고 잠깐 쉬기를 반복하는 체력·심폐 트레이닝입니다.";
            case 112:
                return "• '귀울림(이명) 완화'는 날카로운 고음을 깎아낸 부드러운 배경음으로, 조용한 방에서 귀울림이 신경 쓰이지 않도록 시선을 분산해 줍니다.";
            case 106:
                return "• '20분 파워 낮잠'은 너무 깊은 잠으로 빠지기 전에 적당한 리듬에서 깨워 오후에 멍해지지 않게 돕는 짧은 낮잠용 프리셋입니다.";
            case 212:
                return "• '두뇌 식히기'는 25~50분 집중 작업이 끝난 뒤 과열된 뇌의 긴장을 부드럽게 풀어주는 휴식용 프리셋입니다.";
            default:
                return "";
        }
    }

    private static String getPlainSoundMechanism(PresetModel p) {
        if (p.noiseType == 2) {
            return "• 사람 귀에는 거의 들리지 않는 높은 고주파를 스피커로 내보내 모기·초파리 접근을 불편하게 만드는 해충 방어용 소리입니다. (이어폰 금지)";
        }
        if (p.isAntiPhase) {
            return "• '역위상(안티방어)'은 웅웅거리는 불쾌한 저주파 진동과 정반대 방향의 파동을 함께 내보내 답답함과 체감 울림을 부드럽게 분산합니다.";
        }
        if (p.isSweep && p.freq1 > 0f) {
            int mins = Math.max(1, Math.round(p.sweepDurationSec / 60.0f));
            return String.format(Locale.US,
                    "• 처음 %.1f Hz 리듬에서 시작해 %d분 동안 %.1f Hz로 서서히 변한 뒤 그 상태를 유지하는 자동 전환 코스형 사운드입니다.",
                    p.sweepStart, mins, p.sweepEnd);
        }
        if (p.binauralBeat > 0f) {
            return String.format(Locale.US,
                    "• 왼쪽 귀와 오른쪽 귀에 미세하게 다른 두 음을 들려주면 뇌가 그 차이(%.1f Hz)만큼의 자연스러운 리듬을 느끼게 됩니다.",
                    p.binauralBeat);
        }
        if (p.noiseType == 4 || p.noiseType == 5 || (p.freq1 <= 0f && !p.isSweep)) {
            return "• 자극 없는 폭포·바람·빗소리 계열의 연속 배경음을 양쪽에서 따로 생성해 주변 소음과 말소리를 자연스럽게 덮어줍니다.";
        }
        if (p.freq2 > 0f) {
            return String.format(Locale.US,
                    "• %.0f Hz와 %.0f Hz 두 음이 화음처럼 부드럽게 어우러져 단조로운 기계음 느낌 없이 편하게 들을 수 있습니다.",
                    p.freq1, p.freq2);
        }
        return "• 일정하고 순수한 단일 울림을 왜곡 없이 재생해 몸과 마음의 긴장을 차분히 가라앉힙니다.";
    }

    private static String getPlainScienceNote(PresetModel p) {
        if (p.noiseType == 2) {
            return "• 이어폰이나 헤드폰으로 재생하면 고주파가 귀에 가까워져 자동으로 음량이 작아집니다(-12 dB 보호). 반드시 스피커로만 사용하세요.";
        }
        if (p.isSweep && p.freq1 > 0f) {
            return String.format(Locale.US,
                    "• 기본 톤 %.0f Hz 기준으로 좌우 주파수 차이를 %.1f → %.1f Hz까지 이동시킵니다. (음악·유튜브와 동시 재생 가능)",
                    p.freq1, p.sweepStart, p.sweepEnd);
        }
        if (p.binauralBeat > 0f) {
            return String.format(Locale.US,
                    "• 좌 %.1f Hz / 우 %.1f Hz 조합으로 %.1f Hz 리듬을 만듭니다. 이어폰 사용 시 효과가 가장 선명합니다.",
                    p.freq1, p.freq2, p.binauralBeat);
        }
        if (p.amMod && p.amRate > 0f && p.amRate <= 0.25f) {
            return String.format(Locale.US,
                    "• 소리 크기가 약 %.1f초마다 천천히 커졌다 작아지며 자연스러운 호흡 박자를 안내합니다.",
                    1.0f / p.amRate);
        }
        return "• 앱 자체 볼륨을 20~45%로 맞추면 좋아하는 음악·유튜브·강의를 켠 채 겹쳐 들을 수 있습니다.";
    }

    /** Returns an ELI10 Korean glossary & chooser guide for all non-expert users. */
    public static String getBeginnerGlossaryText() {
        return "어려운 용어를 몰라도 상황만 보고 바로 고를 수 있도록 쉽게 정리했습니다.\n\n"
                + "1. 포모도로 (25분 집중 + 5분 휴식)\n"
                + "• 타이머를 맞춰 25분 동안 딱 한 가지 공부·업무만 하고, 5분 쉬는 가장 유명한 집중 방법입니다.\n\n"
                + "2. 딥워크 (깊은 몰입)\n"
                + "• 카톡·유튜브 방해 없이 45~60분 동안 어려운 일에 푹 빠져드는 상태입니다. '40Hz 깊은 몰입'이나 '깊은 집중 풀코스'를 쓰면 좋습니다.\n\n"
                + "3. 바이노럴 & 진행 램프\n"
                + "• 바이노럴: 왼쪽·오른쪽 귀에 약간 다른 소리를 들려줘 집중·휴식 리듬을 만드는 방식입니다. 이어폰을 끼면 가장 잘 느껴집니다.\n"
                + "• 진행 램프 (예: 10→18Hz): 처음부터 강한 소리가 아니라 5~20분 동안 서서히 원하는 리듬으로 바뀌는 기능입니다.\n\n"
                + "4. 숫자로 보는 뇌파 리듬 (Hz)\n"
                + "• 0.5~4 Hz (델타): 아주 깊은 숙면 · 머리 휴식\n"
                + "• 4~8 Hz (세타): 졸음 유도 · 명상 · 근육 회복\n"
                + "• 8~12 Hz (알파): 편안한 독서 · 긴장 풀기 · 호흡 안정\n"
                + "• 12~24 Hz (베타): 공부 · 웨이트 운동 · 멍함 깨우기\n"
                + "• 40 Hz (감마): 고난도 코딩 · 시험 대비 초집중\n\n"
                + "5. 브라운 / 핑크 / 빗소리 배경음\n"
                + "• 폭포처럼 낮은 저음 배경음(브라운), 부드러운 바람 소리(핑크), 빗소리처럼 주변 말소리·층간 소음을 자극 없이 덮어줍니다.\n\n"
                + "6. 안티방어 (역위상 상쇄)\n"
                + "• 바닥·벽에서 웅웅거리는 저주파 진동과 반대 방향 파동을 내보내 불편함과 울림을 부드럽게 분산합니다.\n\n"
                + "7. 4-6 호흡법\n"
                + "• 소리가 커질 때 4초 들이쉬고, 작아질 때 6초 길게 내쉬면 빨라진 심박과 긴장이 빠르게 진정됩니다.\n\n"
                + "8. 원탭 자동 코스 4종 (알아서 다음 단계 전환)\n"
                + "• 웨이트 풀코스 (65분): 워밍업 5분 → 힘 깨우기 5분 → 고중량 본운동 35분 → 심박 진정 10분 → 근육 회복 10분\n"
                + "• 근비대·유산소 (55분): 워밍업 5분 → 반복세트 몰입 35분 → 근육 쿨다운 15분\n"
                + "• 깊은 집중 (60분): 집중 예열 5분 → 40Hz 깊은 몰입 45분 → 두뇌 식히기 10분\n"
                + "• 꿀잠 숙면 (60분): 4-6 호흡 10분 → 20분 입면 하강 → 심야 소음 방패 30분 후 부드럽게 꺼짐\n\n"
                + "9. 음악·유튜브와 같이 듣는 법\n"
                + "• 이 앱은 다른 앱 소리를 끄지 않습니다. 보르타 볼륨을 20~45%로 두면 좋아하는 노래·강의 밑에 은은하게 깔립니다.";
    }
}
