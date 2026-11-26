package com.vorta.aegis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable catalog of 48 situation-named anti-frequency neutralization, wellness, and pest-deterrent presets.
 * Every preset pairs everyday Korean context naming with concise 2-line bullet effects.
 */
public class PresetModel {
    public int id;
    public int tabIndex; // 0: 안티방어, 1: 수면, 2: 집중·공부, 3: 명상·안정, 4: 두통·통증, 5: 운동·피로, 6: 기분·힐링, 7: 모기·벌레
    public String title;
    public String badge;
    public String desc;
    public float freq1;
    public float freq2;
    public boolean isSweep;
    public float sweepStart;
    public float sweepEnd;
    public float sweepDurationSec;
    public boolean isAntiPhase; // 180도 역위상 능동 상쇄
    public float binauralBeat;  // 바이노럴 비트 차주파수 (Hz)
    public int noiseType;       // 0: None, 1: Pink Bio-Noise, 2: Ultrasonic Mosquito, 3: Dragonfly Flutter
    public boolean fmMod;
    public float fmDepth;
    public float fmRate;
    public boolean amMod;
    public float amDepth;
    public float amRate;

    private static List<PresetModel> sCachedPresets;

    public PresetModel(int id, int tabIndex, String title, String badge, String desc,
                       float freq1, float freq2, boolean isSweep, float sweepStart, float sweepEnd,
                       float sweepDurationSec, boolean isAntiPhase, float binauralBeat, int noiseType,
                       boolean fmMod, float fmDepth, float fmRate, boolean amMod, float amDepth, float amRate) {
        this.id = id;
        this.tabIndex = tabIndex;
        this.title = title;
        this.badge = badge;
        this.desc = desc;
        this.freq1 = freq1;
        this.freq2 = freq2;
        this.isSweep = isSweep;
        this.sweepStart = sweepStart;
        this.sweepEnd = sweepEnd;
        this.sweepDurationSec = sweepDurationSec;
        this.isAntiPhase = isAntiPhase;
        this.binauralBeat = binauralBeat;
        this.noiseType = noiseType;
        this.fmMod = fmMod;
        this.fmDepth = fmDepth;
        this.fmRate = fmRate;
        this.amMod = amMod;
        this.amDepth = amDepth;
        this.amRate = amRate;
    }

    /** Returns a compact two-digit preset code such as "P01". */
    public String getShortCode() {
        return (id < 10 ? "P0" : "P") + id;
    }

    /** Finds a preset by ID, or returns the first preset if not found. */
    public static PresetModel findById(int id) {
        List<PresetModel> all = getAllPresets();
        for (PresetModel p : all) {
            if (p.id == id) {
                return p;
            }
        }
        return all.get(0);
    }

    /** Returns an unmodifiable, lazily-cached list of all 48 presets. */
    public static synchronized List<PresetModel> getAllPresets() {
        if (sCachedPresets != null) {
            return sCachedPresets;
        }

        List<PresetModel> list = new ArrayList<>(48);
        int id = 1;

        // ==========================================
        // TAB 0: 안티방어 (보르타 4종 전수 대응 12종)
        // ==========================================
        list.add(new PresetModel(id++, 0,
                "눈 압박 · 시야 흐림 방어", "A30 · FreqGen 대응",
                "• 18.98 Hz 역위상 상쇄 + 10 Hz 알파 완충으로 눈 압박·긴장 완화\n• 저주파 노출 시 눈이 뻐근할 때 (60Hz 미만은 우퍼 권장)",
                18.98f, 10.0f, false, 0, 0, 0, true, 0, 0,
                true, 0.05f, 0.5f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "어지러움 · 균형 감각 방어", "S7 · FreqGen 대응",
                "• 7.0 Hz 역위상 상쇄 + 7.83 Hz 슈만공명으로 전정기관 균형 회복\n• 멍하거나 어지러운 진동이 느껴질 때 즉시 가동",
                7.0f, 7.83f, false, 0, 0, 0, true, 0, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "속 울렁임 · 심박 불안 방어", "S7 대응",
                "• 10.5 Hz 대역에 0.8 Hz 미세 파동 차이를 더해 속 울렁임과 맥박 불안감 분산\n• 이유 없이 속이 메스껍거나 맥박이 거슬릴 때 추천",
                10.5f, 11.3f, false, 0, 0, 0, false, 0.8f, 0,
                true, 0.06f, 0.8f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "새벽 잠 깸 · 수면 방해 차단", "FreqGen 대응",
                "• 16.0 Hz 역위상 + 3.0 Hz 델타 보호 + 핑크노이즈로 뇌파 간섭 차단\n• 야간 수면 중 저주파 소음에 깰 때 20~45% 배경 볼륨 권장",
                16.0f, 3.0f, false, 0, 0, 0, true, 0, 1,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "하복부 · 골반 묵직함 방어", "S7 대응",
                "• 28.0 Hz 역위상 전단파 상쇄로 하복부·골반저 압박감 감쇄\n• 앉아 있을 때 아래쪽 진동이 거슬릴 때 사용",
                28.0f, 0, false, 0, 0, 0, true, 0, 0,
                true, 0.04f, 1.0f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "등 · 척추 울림 하중 방어", "통합 시나리오",
                "• 33.0 Hz 역위상 파동 + 2 Hz 진폭 변조로 척추 공진 하중 분산\n• 등이나 의자로 전달되는 중저역 울림 대응",
                33.0f, 0, false, 0, 0, 0, true, 0, 0,
                false, 0, 0, true, 0.12f, 2.0f));

        list.add(new PresetModel(id++, 0,
                "갑작스런 불안 · 과각성 차단", "S6 대응",
                "• 40.0 Hz 역위상 상쇄와 528 Hz 조화음 완충을 동시에 출력\n• 알 수 없는 압박감·불안이 올라올 때 진정용 (음악 병행 45%)",
                40.0f, 528.0f, false, 0, 0, 0, true, 0, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "가슴 답답함 · 흉곽 울림 방어", "S6 대응",
                "• 43.2 Hz 흉곽 공진 대역을 180도 역위상으로 맞받아쳐 압박 완화\n• 가슴 쪽 공기가 묵직하고 호흡이 답답할 때 가동",
                43.2f, 0, false, 0, 0, 0, true, 0, 0,
                true, 0.03f, 1.2f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "바닥 · 벽면 웅웅거림 상쇄", "S6 · A30 대응",
                "• 55.0 Hz 바닥·벽 웅웅거림 대역에 반대 파동과 1.2 Hz 분산을 걸어 실내 울림 완화\n• 실내 벽·바닥의 55 Hz 공진음이 울릴 때 외장 스피커/우퍼 추천",
                55.0f, 56.2f, false, 0, 0, 0, true, 1.2f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "극저역 1~4.5Hz 자동 추적 방어", "전수 스윕",
                "• 1.0~4.5 Hz 극저역을 360초 왕복 스윕으로 역추적해 수면·이석 보호\n• 대역이 고정되지 않는 변동형 초저주파 방어용",
                0, 0, true, 1.0f, 4.5f, 360.0f, true, 0, 0,
                true, 0.08f, 0.3f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "눈·두통 16.5~21.5Hz 자동 방어", "전수 스윕",
                "• 16.5~21.5 Hz 안구·전두엽 핵심 대역을 450초 왕복 역위상 방어\n• 주간 작업 중 머리·눈 피로 유발 스윕 종합 차단",
                0, 0, true, 16.5f, 21.5f, 450.0f, true, 0, 0,
                true, 0.05f, 1.5f, false, 0, 0));

        list.add(new PresetModel(id++, 0,
                "실내 공진 46~55Hz 자동 방어", "슬래브 스윕",
                "• 46.0~55.0 Hz 벽체·슬래브 공진 대역을 600초 왕복 스윕으로 분산\n• 실내 구조 진동이 넓게 퍼질 때 장시간 방어",
                0, 0, true, 46.0f, 55.0f, 600.0f, true, 0, 0,
                false, 0, 0, true, 0.15f, 2.0f));

        // ==========================================
        // TAB 1: 수면 (6종)
        // ==========================================
        list.add(new PresetModel(id++, 1,
                "깊은 숙면 · 뒤척임 없는 밤", "델타 스윕",
                "• 0.5~2.5 Hz 느린 델타파 스윕으로 깊은 서파 수면 리듬 유도\n• 잠들기 직전 20% 음악배경 볼륨으로 조용히 틀어두기",
                0, 0, true, 0.5f, 2.5f, 480.0f, false, 0, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 1,
                "생각 많아 잠 못 드는 밤", "야간 회복",
                "• 3.0 Hz 바이노럴(150/153 Hz)이 과열된 뇌 긴장과 잡념 진정\n• 이어폰 또는 침대 머리맡 작은 음량(20~45%) 권장",
                150.0f, 153.0f, false, 0, 0, 0, false, 3.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 1,
                "잠들기 전 432Hz 편안한 밤", "432 Hz 피치",
                "• 432 Hz 자연 조화음과 1.5 Hz 델타 비트로 포근한 취침 분위기 조성\n• 잔잔한 수면 음악과 함께 20~45% 볼륨으로 동시 재생 추천",
                432.0f, 433.5f, false, 0, 0, 0, false, 1.5f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 1,
                "누웠을 때 몸 긴장 풀기", "신경 이완",
                "• 솔페지오 174 Hz 저역이 뭉친 어깨·허리와 신경계 긴장 이완\n• 폰 스피커로도 잘 들리며 취침 전 15분 이완에 적합",
                174.0f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.03f, 0.2f, false, 0, 0));

        list.add(new PresetModel(id++, 1,
                "새벽에 안 깨는 렘(REM) 숙면", "수면 주기 안정",
                "• 4.5 Hz 세타-델타 브릿지가 수면 주기 전환을 매끄럽게 유지\n• 중간에 자주 깨는 날 밤 이어폰으로 은은하게 재생",
                200.0f, 204.5f, false, 0, 0, 0, false, 4.5f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 1,
                "소음 가리는 수면 핑크노이즈", "소음 차폐",
                "• 1/f 핑크노이즈와 2.0 Hz 델타파가 바깥 차량·층간 소음 차폐\n• 이어폰 없이 폰 스피커(45%)로 틀어두기 좋은 밤 모드",
                100.0f, 102.0f, false, 0, 0, 0, false, 2.0f, 1,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 2: 집중·공부 (6종)
        // ==========================================
        list.add(new PresetModel(id++, 2,
                "마감 직전 초집중 (40Hz 감마)", "인지 집중",
                "• 40 Hz 감마 바이노럴(240/280 Hz)로 작업기억·정보 처리 속도 상승\n• 공부·마감 작업 시 연주곡/로파이와 45% 볼륨 병행 추천",
                240.0f, 280.0f, false, 0, 0, 0, false, 40.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 2,
                "독서 · 공부 차분한 몰입", "알파 몰입",
                "• 10.0 Hz 알파파 동조로 긴장 없이 오래 가는 독서·학습 흐름 유지\n• 조용한 음악 아래 20~45%로 깔아두면 부담 없이 지속",
                300.0f, 310.0f, false, 0, 0, 0, false, 10.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 2,
                "멍함 지우기 · 업무 시동 걸기", "차분한 각성",
                "• 14.0 Hz 차분한 각성 리듬(SMR)이 산만한 잡념을 정리하고 작업 시작을 도와줌\n• 식곤증 오는 오후에 음악과 함께 켜기 좋은 집중 모드",
                250.0f, 264.0f, false, 0, 0, 0, false, 14.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 2,
                "코딩 · 분석 논리 집중 모드", "베타 연산",
                "• 18.0 Hz 중베타파 자극으로 코드 분석과 복잡한 문제 풀이 지속\n• 이어폰 착용 + 45% 볼륨으로 선명한 각성 유지",
                280.0f, 298.0f, false, 0, 0, 0, false, 18.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 2,
                "기획 · 아이디어 창의적 작업", "듀얼 싱크",
                "• 432 Hz 기반 40 Hz 감마 + 10 Hz FM 리듬으로 유연한 사고 촉진\n• 브레인스토밍·디자인·글쓰기 작업 시 활용",
                432.0f, 472.0f, false, 0, 0, 0, false, 40.0f, 0,
                true, 0.05f, 10.0f, false, 0, 0));

        list.add(new PresetModel(id++, 2,
                "카페 · 사무실 잡음 차단 집중", "핑크노이즈",
                "• 핑크노이즈와 12 Hz 알파 비트로 주변 말소리·생활 소음 차폐\n• 독서실·카페에서 이어폰과 함께 45% 볼륨 권장",
                180.0f, 192.0f, false, 0, 0, 0, false, 12.0f, 1,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 3: 명상·안정 (5종)
        // ==========================================
        list.add(new PresetModel(id++, 3,
                "마음 가라앉히는 지구 공명 (7.83Hz)", "슈만 공명",
                "• 7.83 Hz 슈만 공명 바이노럴로 들뜬 호흡과 심박을 차분히 정돈\n• 이어폰 착용 후 20~45% 볼륨으로 조용히 명상하기 좋음",
                120.0f, 127.83f, false, 0, 0, 0, false, 7.83f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 3,
                "호흡에 집중하는 깊은 명상", "세타 이완",
                "• 5.5 Hz 세타파 유도로 잡념을 비우고 깊은 호흡 명상 몰입\n• 눈을 감고 10~20분 호흡 정리할 때 추천",
                180.0f, 185.5f, false, 0, 0, 0, false, 5.5f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 3,
                "가슴 편안해지는 옴(OM) 사운드", "OM 피치",
                "• 136.1 Hz 옴 톤과 미세한 호흡 변조로 가슴 답답함 완화\n• 폰 스피커로 틀어놓고 요가·스트레칭 시 활용 가능",
                136.1f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.02f, 0.1f, false, 0, 0));

        list.add(new PresetModel(id++, 3,
                "불안 잠재우는 528Hz 힐링 톤", "솔페지오 528",
                "• 528 Hz 순수 정현파와 0.5 Hz 진폭 호흡으로 정서 안정 유도\n• 잔잔한 피아노·뉴에이지 음악 밑에 20% 배경 재생 추천",
                528.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, true, 0.08f, 0.5f));

        list.add(new PresetModel(id++, 3,
                "퇴근 후 번아웃 식히기", "알파-세타",
                "• 8.0 Hz 경계 리듬으로 과열된 업무 긴장을 식히고 기분 전환\n• 퇴근길 이어폰으로 15분간 들으며 하루 마무리",
                210.0f, 218.0f, false, 0, 0, 0, false, 8.0f, 0,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 4: 두통·통증 (5종)
        // ==========================================
        list.add(new PresetModel(id++, 4,
                "지끈거리는 두통 · 긴장 진정", "솔페지오 174",
                "• 묵직한 174 Hz 저중역 톤으로 머리·어깨 뭉친 긴장감 완화 보조\n• 자극 없는 작은 음량(20~45%)으로 조용히 휴식",
                174.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 4,
                "뻐근한 몸 · 뭉친 부위 이완", "솔페지오 285",
                "• 285 Hz 부드러운 공명음으로 피로가 쌓인 근육·신경 이완감 제공\n• 장시간 앉아 몸이 뻐근할 때 음악과 함께 가동",
                285.0f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.02f, 0.5f, false, 0, 0));

        list.add(new PresetModel(id++, 4,
                "편두통 올 때 저역 호흡 진정", "110 Hz 진정",
                "• 110 Hz 베이스와 1 Hz 호흡 변조로 빛·소음에 예민할 때 휴식 유도\n• 어두운 방에서 20% 약한 음량으로 청취 권장",
                110.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, true, 0.10f, 1.0f));

        list.add(new PresetModel(id++, 4,
                "눈 피로 · 뒷목 뻐근함 풀기", "알파 10 Hz",
                "• 10 Hz 알파 바이노럴로 모니터 주시 후 눈·후두부 긴장 이완\n• 작업 중간 10분 눈 감고 쉴 때 이어폰 사용",
                160.0f, 170.0f, false, 0, 0, 0, false, 10.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 4,
                "머리 무겁고 멍할 때 감압 휴식", "델타 3.5 Hz",
                "• 3.5 Hz 저자극 델타 비트로 묵직한 두부 압박감 완화 보조\n• 낮잠·짧은 휴식 시 조용한 환경에서 20~45% 재생",
                130.0f, 133.5f, false, 0, 0, 0, false, 3.5f, 0,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 5: 운동·피로 (5종 — 헬스 웨이트 1순위)
        // ==========================================
        list.add(new PresetModel(id++, 5,
                "웨이트 트레이닝 · 고중량 몰입", "헬스 · 근력운동",
                "• 20 Hz 베타 바이노럴(200/220 Hz) · 근신경 집중·텐션 유지 (배경 20~45%)",
                200.0f, 220.0f, false, 0, 0, 0, false, 20.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 5,
                "유산소 · 아침 컨디션 ON", "러닝 · 기상 각성",
                "• 15 Hz 각성 비트로 워밍업, 러닝 페이스 및 아침 집중력 점화\n• 신나는 운동 플레이리스트 밑에 45%로 레이어링하기 좋음",
                250.0f, 265.0f, false, 0, 0, 0, false, 15.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 5,
                "운동 후 근육 쿨다운 · 피로 풀기", "스트레칭 · 회복",
                "• 136.1 Hz 저역 + 0.8 Hz 호흡 변조로 고강도 운동 후 긴장 이완\n• 헬스 종료 후 스트레칭·샤워 전 휴식 타임에 추천",
                136.1f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, true, 0.15f, 0.8f));

        list.add(new PresetModel(id++, 5,
                "오후 만성 피로 · 처짐 리셋", "오후 피로 회복",
                "• 528 Hz 맑은 공명음과 1 Hz 변조로 나른하고 축 처진 컨디션 환기\n• 업무 중 커피 대신 잔잔한 음악과 함께 20~45% 재생",
                528.0f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.04f, 1.0f, false, 0, 0));

        list.add(new PresetModel(id++, 5,
                "하루 끝 전신 피로 릴렉스", "전신 밸런스",
                "• 순수 432 Hz 조화음이 긴 일과로 지친 청각과 신경계 피로 완화\n• 저녁 휴식 시간 배경 사운드로 부담 없이 활용",
                432.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 6: 기분·힐링 (5종)
        // ==========================================
        list.add(new PresetModel(id++, 6,
                "가라앉은 기분 끌어올리기", "기분 균형",
                "• 10 Hz 알파 바이노럴(200/210 Hz)로 무기력하고 가라앉은 기분 환기\n• 좋아하는 음악과 20~45%로 함께 들으면 이질감 없이 산뜻함",
                200.0f, 210.0f, false, 0, 0, 0, false, 10.0f, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 6,
                "마음 편안해지는 정서 힐링", "솔페지오 639",
                "• 따뜻한 639 Hz 톤이 대인관계 스트레스와 예민한 마음 진정\n• 혼자 쉬고 싶은 저녁, 잔잔한 BGM과 병행하기 적합",
                639.0f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.02f, 0.5f, false, 0, 0));

        list.add(new PresetModel(id++, 6,
                "복잡한 생각 비우기 · 기분 전환", "솔페지오 852",
                "• 투명한 852 Hz 고음역 톤으로 답답하고 엉킨 생각 환기\n• 산책하거나 창문 열고 환기할 때 작은 음량(20%) 추천",
                852.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 6,
                "답답한 스트레스 털어내기", "솔페지오 741",
                "• 741 Hz 톤 + 1.2 Hz 리듬 변조로 답답한 감정을 가볍게 정리\n• 기분이 묵직한 날 가벼운 연주곡과 함께 재생",
                741.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, true, 0.08f, 1.2f));

        list.add(new PresetModel(id++, 6,
                "불안 · 긴장 내려놓기", "솔페지오 396",
                "• 안정적인 396 Hz 중음역이 초조하고 긴장된 마음을 차분히 안정\n• 중요한 일 전후 마인드 컨트롤용으로 적합",
                396.0f, 0, false, 0, 0, 0, false, 0, 0,
                false, 0, 0, false, 0, 0));

        // ==========================================
        // TAB 7: 모기·벌레 (4종)
        // ==========================================
        list.add(new PresetModel(id++, 7,
                "잠잘 때 무음 모기 가드", "초음파 스윕",
                "• 16~21.5 kHz 성인 무음 초음파 스윕 송출로 실내 해충 접근 보조 방어\n• 이어폰 금지 — 반드시 폰 스피커 직접 출력(75~100%) 사용",
                0, 0, true, 16000.0f, 21500.0f, 30.0f, false, 0, 2,
                false, 0, 0, false, 0, 0));

        list.add(new PresetModel(id++, 7,
                "캠핑 · 야외 잠자리 펄스", "천적 음향 모사",
                "• 모기 천적 잠자리 날갯짓(38.5 Hz) 연속 펄스를 모사한 야외 방어음\n• 캠핑·야외 활동 시 기기를 주변에 두고 스피커 출력",
                38.5f, 0, false, 0, 0, 0, false, 0, 3,
                false, 0, 0, true, 0.40f, 10.0f));

        list.add(new PresetModel(id++, 7,
                "흡혈 암모기 기피 펄스", "기피 모사",
                "• 550 Hz 수모기 날갯짓 변조 파형을 모사한 해충 기피 보조 출력\n• 소리가 들리므로 취침 외 야외·베란다 보조용으로 활용",
                550.0f, 0, false, 0, 0, 0, false, 0, 0,
                true, 0.05f, 8.0f, true, 0.25f, 4.0f));

        list.add(new PresetModel(id++, 7,
                "야외 강력 복합 모기 차단", "복합 가드",
                "• 18 kHz 초음파 반송파에 38.5 Hz 천적 펄스를 결합한 복합 모드\n• 야간 야외 활동 시 폰 스피커 직접 출력 권장 (이어폰 금지)",
                18000.0f, 0, false, 0, 0, 0, false, 0, 2,
                false, 0, 0, true, 0.50f, 38.5f));

        sCachedPresets = Collections.unmodifiableList(list);
        return sCachedPresets;
    }
}
