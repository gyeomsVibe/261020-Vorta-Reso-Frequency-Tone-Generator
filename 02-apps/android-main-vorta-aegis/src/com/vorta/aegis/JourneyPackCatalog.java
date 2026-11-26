package com.vorta.aegis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Curated Journey Progression Pack (24 presets, IDs 201..224, codes J01..J24)
 * filling pre-activity prep, peak/routine focus, post-activity cooldown, and deep recovery
 * across all 8 tabs while keeping PresetModel (48) and ProPackCatalog (16) intact.
 */
public final class JourneyPackCatalog {
    public static final int JOURNEY_ID_MIN = 201;
    public static final int JOURNEY_ID_MAX = 224;

    private static List<PresetModel> sCachedJourneyPresets;
    private static List<PresetModel> sCachedAll88;

    private JourneyPackCatalog() {
    }

    public static synchronized List<PresetModel> getJourneyPackPresets() {
        if (sCachedJourneyPresets != null) {
            return sCachedJourneyPresets;
        }
        List<PresetModel> list = new ArrayList<>(24);

        // Tab 5: 운동·피로 (6 new: J01..J06)
        list.add(new PresetModel(201, 5,
                "운동 전 몸·마음 워밍업 (10→18Hz)", "1단계 · 5분 예열",
                "• 5분 동안 차분한 상태(10 Hz)에서 운동 각성(18 Hz)으로 서서히 올려 몸과 마음을 예열\n• 스트레칭·가벼운 웜업 세트 동안 좋아하는 음악 아래 35%로 함께 재생",
                200.0f, 0.0f, true, 10.0f, 18.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(202, 5,
                "고중량 직전 힘·신경 깨우기 (22Hz)", "1단계 · 본세트 직전",
                "• 무거운 무게를 들기 직전 중추신경과 집중력을 바짝 깨워주는 강한 리듬(22 Hz)\n• 스쿼트·데드리프트·벤치프레스 들어가기 3~5분 전 음악과 40% 병행 추천",
                220.0f, 242.0f, false, 0.0f, 0.0f, 0.0f, false, 22.0f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 1.5f));

        list.add(new PresetModel(203, 5,
                "근비대 · 반복 세트 리듬 몰입 (16Hz)", "2단계 · 반복 세트",
                "• 16 Hz 안정 집중 리듬 + 포근한 저음 배경으로 헬스장 소음을 가리고 세트에 몰입\n• 8~15회 근비대 반복 운동 중 집중이 흐트러질 때 음악과 35% 병행 추천",
                180.0f, 196.0f, false, 0.0f, 0.0f, 0.0f, false, 16.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(204, 5,
                "고강도 인터벌 · 서킷 한계 돌파", "2단계 · 인터벌·서킷",
                "• 빠른 24 Hz 각성 리듬 + 강한 박자감으로 힘든 서킷·인터벌·스프린트 페이스 유지\n• 고강도 마무리 세트나 인터벌 러닝 중 신나는 음악과 45% 병행 추천",
                240.0f, 264.0f, false, 0.0f, 0.0f, 0.0f, false, 24.0f, 0,
                false, 0.0f, 0.0f, true, 0.14f, 2.0f));

        list.add(new PresetModel(205, 5,
                "운동 직후 심박 진정 · 숨 고르기", "3단계 · 4-6 호흡 쿨다운",
                "• 5분간 리듬을 낮추며(12→6 Hz) 4초 들숨·6초 날숨 박자로 빨라진 심박수를 차분히 진정\n• 본 운동 끝난 직후 정리 스트레칭 5~10분간 재생 추천",
                160.0f, 0.0f, true, 12.0f, 6.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.18f, 0.10f));

        list.add(new PresetModel(206, 5,
                "운동 후 근육통 · 지친 몸 깊은 회복", "4단계 · 근육 회복",
                "• 낮은 174 Hz 이완 톤 + 깊은 휴식 리듬(4.5 Hz) + 저음 배경으로 운동 후 뻐근한 몸 회복\n• 하체·등 고강도 운동 후 저녁 휴식이나 잠들기 전 25~40% 추천",
                174.0f, 178.5f, false, 0.0f, 0.0f, 0.0f, false, 4.5f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // Tab 1: 수면 (3 new: J07..J09)
        list.add(new PresetModel(207, 1,
                "잠들기 전 4-6 호흡 긴장 풀기", "1단계 · 수면 준비",
                "• 따뜻한 128 Hz + 4초 들숨·6초 날숨 리듬 + 은은한 바람 배경으로 취침 전 긴장 풀기\n• 잠들기 10분 전 누워서 소리 박자에 맞춰 천천히 숨 쉬기 (20~35%)",
                128.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 1,
                false, 0.0f, 0.0f, true, 0.22f, 0.10f));

        list.add(new PresetModel(208, 1,
                "20분 자연 입면 · 서서히 잠들기", "2단계 · 입면 유도",
                "• 20분 동안 깨어있는 리듬(10 Hz)에서 깊은 숙면 리듬(2 Hz)으로 천천히 내려가며 유지\n• 잠들기 직전 30~60분 타이머를 걸어두고 조용히 듣기 (20~35%)",
                160.0f, 0.0f, true, 10.0f, 2.0f, 1200.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(209, 1,
                "아침 기상 · 잠 덜 깬 멍함 깨우기", "4단계 · 기상 예열",
                "• 5분 동안 졸음 리듬(3 Hz)에서 맑은 집중 리듬(14 Hz)으로 부드럽게 올려 아침 멍함 해소\n• 알람 후 일어나기 힘들 때 침대에서 5~10분 틀어두기 (35~50%)",
                210.0f, 0.0f, true, 3.0f, 14.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // Tab 2: 집중·공부 (3 new: J10..J12)
        list.add(new PresetModel(210, 2,
                "공부·업무 시작 5분 예열 (10→16Hz)", "1단계 · 집중 시동",
                "• 5분 동안 편안함(10 Hz)에서 집중 리듬(16 Hz)으로 올려 산만한 마음을 작업 모드로 전환\n• 책상에 앉아 공부·업무 시작 직전 5분 워밍업으로 추천 (30~45%)",
                220.0f, 0.0f, true, 10.0f, 16.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(211, 2,
                "암기 · 시험 전 기억 정착 (12Hz)", "2단계 · 암기·학습",
                "• 긴장 없이 기억이 잘 남는 12 Hz 안정 리듬 + 은은한 바람 배경으로 잡념 차단\n• 영단어·자격증 암기나 시험 직전 복습할 때 30~45% 추천",
                216.0f, 228.0f, false, 0.0f, 0.0f, 0.0f, false, 12.0f, 1,
                true, 0.02f, 0.4f, false, 0.0f, 0.0f));

        list.add(new PresetModel(212, 2,
                "집중 작업 후 10분 두뇌 식히기", "4단계 · 두뇌 휴식",
                "• 5분 동안 집중 리듬(14 Hz)에서 편안한 휴식 리듬(8 Hz)으로 낮춰 과열된 머리 식히기\n• 25분 집중(포모도로) 쉬는 시간이나 장시간 업무 후 눈 감고 10분 휴식 (25~35%)",
                180.0f, 0.0f, true, 14.0f, 8.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // Tab 3: 명상·안정 (3 new: J13..J15)
        list.add(new PresetModel(213, 3,
                "두근거림 진정 · 4-6 호흡 안정", "1단계 · 심박 안정",
                "• 차분한 8 Hz 안정 리듬 + 4초 들숨·6초 날숨(1분 6회) 호흡 박자로 긴장과 두근거림 완화\n• 스트레스·초조함이 올라올 때 5분간 숨을 맞춰 쉬기 (25~40%)",
                144.0f, 152.0f, false, 0.0f, 0.0f, 0.0f, false, 8.0f, 0,
                false, 0.0f, 0.0f, true, 0.24f, 0.10f));

        list.add(new PresetModel(214, 3,
                "명상 입문 · 잡념 비우기 (10→5.5Hz)", "3단계 · 명상 진입",
                "• 7분 동안 편안함(10 Hz)에서 깊은 명상 리듬(5.5 Hz)으로 서서히 낮춰 입문자도 쉽게 몰입\n• 명상이 어렵거나 생각이 많은 저녁 10~20분 조용히 듣기",
                174.0f, 0.0f, true, 10.0f, 5.5f, 420.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(215, 3,
                "퇴근 후 하루 감정 정리 · 저녁 명상", "4단계 · 저녁 마무리",
                "• 따뜻한 136.1 Hz 톤 + 6 Hz 이완 리듬 + 포근한 저음 배경으로 하루 피로와 잔여 스트레스 해소\n• 퇴근 후 조용한 저녁 15~30분 편하게 쉬며 듣기 (20~35%)",
                136.1f, 142.1f, false, 0.0f, 0.0f, 0.0f, false, 6.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // Tab 4: 두통·통증 (3 new: J16..J18)
        list.add(new PresetModel(216, 4,
                "거북목 · 뭉친 어깨·목 이완 (128Hz)", "1단계 · 목·어깨",
                "• 묵직하고 부드러운 128 Hz + 느린 호흡 박자 + 바람 배경으로 굳은 목·어깨 긴장 완화\n• 스마트폰·모니터 오래 본 뒤 가벼운 목 스트레칭과 함께 재생 (25~40%)",
                128.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 1,
                false, 0.0f, 0.0f, true, 0.14f, 0.16f));

        list.add(new PresetModel(217, 4,
                "긴장성 두통 · 자극 없는 머리 휴식", "2단계 · 두통 진정",
                "• 8분간 서서히 낮아지는 리듬(8→3 Hz) + 고음을 깎아낸 부드러운 배경으로 두통 자극 최소화\n• 빛·소리에 예민하고 머리가 지끈거릴 때 20~30% 작은 음량 권장",
                150.0f, 0.0f, true, 8.0f, 3.0f, 480.0f, false, 0.0f, 5,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(218, 4,
                "허리·전신 뻐근함 저음 이완 휴식", "3단계 · 전신 이완",
                "• 낮은 두 공명음(110+174 Hz)과 느린 이완 박자로 허리·하체 뻐근함과 피로감 완화\n• 오래 앉아 있거나 서 있은 날 저녁 누워서 쉴 때 25~45% 추천",
                110.0f, 174.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.12f, 0.25f));

        // Tab 6: 기분·힐링 (3 new: J19..J21)
        list.add(new PresetModel(219, 6,
                "화·짜증 급속 진정 · 감정 식히기", "1단계 · 감정 진정",
                "• 차분한 396 Hz 톤 + 4초 들숨·6초 날숨 호흡 박자 + 저음 배경으로 치미는 화를 가라앉힘\n• 짜증·갈등 직후 5~10분 숨을 고르며 감정 정리 (25~40%)",
                396.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 4,
                false, 0.0f, 0.0f, true, 0.18f, 0.10f));

        list.add(new PresetModel(220, 6,
                "지친 저녁 따뜻한 위로 화음", "2단계 · 마음 위로",
                "• 귀에 포근하게 감기는 396+528 Hz 화음과 느린 호흡 파동으로 따뜻한 정서 안정감 제공\n• 지치고 외로운 저녁, 잔잔한 음악 아래 20~35% 함께 재생 추천",
                396.0f, 528.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 0.15f));

        list.add(new PresetModel(221, 6,
                "무기력 탈출 · 산뜻한 기분 전환", "3단계 · 활력 전환",
                "• 5분간 차분함(8 Hz)에서 경쾌한 활력 리듬(14 Hz)으로 서서히 올려 축 처진 기분 환기\n• 무기력한 오전·오후 산책이나 가벼운 음악과 30~45% 병행 추천",
                256.0f, 0.0f, true, 8.0f, 14.0f, 300.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // Tab 0: 안티방어 (2 new: J22..J23)
        list.add(new PresetModel(222, 0,
                "밤중 층간 저주파 · 소음 복합 방패", "2단계 · 야간 방어",
                "• 35 Hz 반대 파동 상쇄 + 3 Hz 숙면 보조 + 포근한 저음 배경으로 밤중 바닥 웅웅거림 완충\n• 밤 시간 거슬리는 저주파 진동이 느껴질 때 25~45% 권장",
                35.0f, 3.0f, false, 0.0f, 0.0f, 0.0f, true, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        list.add(new PresetModel(223, 0,
                "원인 모를 실내 저주파 전체 방어", "3단계 · 광역 순환",
                "• 실내에서 흔한 12~55 Hz 저주파 구간 전체를 10분 주기로 훑으며 반대 파동으로 분산\n• 어디서 오는지 알기 어려운 복합 실내 웅웅거림에 장시간 가동",
                0.0f, 0.0f, true, 12.0f, 55.0f, 600.0f, true, 0.0f, 0,
                true, 0.04f, 0.8f, false, 0.0f, 0.0f));

        // Tab 7: 모기·벌레 (1 new: J24)
        list.add(new PresetModel(224, 7,
                "실내 날파리 · 초파리 고주파 가드", "2단계 · 실내 해충",
                "• 15.5~19.5 kHz 고주파 스윕 송출로 여름철 실내 날벌레·초파리 접근 완화 보조\n• 이어폰 금지 — 반드시 스피커 직접 출력(75~100%) 사용",
                0.0f, 0.0f, true, 15500.0f, 19500.0f, 25.0f, false, 0.0f, 2,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        sCachedJourneyPresets = Collections.unmodifiableList(list);
        return sCachedJourneyPresets;
    }

    public static synchronized List<PresetModel> getAllPresets88() {
        if (sCachedAll88 != null) {
            return sCachedAll88;
        }
        List<PresetModel> base64 = ProPackCatalog.getAllPresetsWithPro();
        List<PresetModel> journey24 = getJourneyPackPresets();
        List<PresetModel> combined = new ArrayList<>(base64.size() + journey24.size());
        combined.addAll(base64);
        combined.addAll(journey24);
        sCachedAll88 = Collections.unmodifiableList(combined);
        return sCachedAll88;
    }

    public static boolean isJourneyPreset(int id) {
        return id >= JOURNEY_ID_MIN && id <= JOURNEY_ID_MAX;
    }

    public static String getShortCode(PresetModel p) {
        if (p == null) {
            return "";
        }
        if (isJourneyPreset(p.id)) {
            return String.format(Locale.US, "J%02d", p.id - 200);
        }
        return ProPackCatalog.getShortCode(p);
    }

    public static PresetModel findAnyById(int id) {
        List<PresetModel> all = getAllPresets88();
        for (int i = 0; i < all.size(); i++) {
            PresetModel p = all.get(i);
            if (p.id == id) {
                return p;
            }
        }
        return all.get(0);
    }

    public static int getNextPresetId(int currentId, int delta) {
        return JourneyOrderPolicy.getNextOrderedPresetId(currentId, delta);
    }
}
