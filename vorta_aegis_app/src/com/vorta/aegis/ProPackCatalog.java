package com.vorta.aegis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Commercial PC Pro Pack (16 presets, IDs 101..116, codes C01..C16) matching
 * vorta_aegis_pc/packs/vorta_pc_pro_pack_v1.json 1:1 while keeping PresetModel.getAllPresets()
 * strictly 48 core presets for standalone Android/PC parity sync.
 */
public final class ProPackCatalog {
    public static final int PRO_ID_MIN = 101;
    public static final int PRO_ID_MAX = 116;

    private static List<PresetModel> sCachedProPresets;
    private static List<PresetModel> sCachedAll64;

    private ProPackCatalog() {
    }

    public static synchronized List<PresetModel> getProPackPresets() {
        if (sCachedProPresets != null) {
            return sCachedProPresets;
        }
        List<PresetModel> list = new ArrayList<>(16);

        // C01 (101)
        list.add(new PresetModel(101, 2,
                "순수 브라운 노이즈 · 심층 작업 차폐", "PC Pro · 1/f² 마스킹",
                "• 부드러운 저역 성형 스테레오 브라운 노이즈로 키보드·생활 소음을 자극 없이 차폐\n• 장시간 코딩·문서·AI 작업 시 25~45% 배경 볼륨 권장 (고주파 피로 0%)",
                0.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C02 (102)
        list.add(new PresetModel(102, 2,
                "40Hz 감마 딥워크 · 브라운 융합", "PC Pro · 감마 + 차폐",
                "• 인지 작업용 40 Hz 감마 바이노럴(200/240 Hz)에 포근한 브라운 노이즈를 융합\n• 순수 톤의 단조로움을 줄여 60분 이상 깊은 몰입 유지 (이어폰 30~45% 권장)",
                200.0f, 240.0f, false, 0.0f, 0.0f, 0.0f, false, 40.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C03 (103)
        list.add(new PresetModel(103, 2,
                "오픈 오피스 말소리 마스킹 (12Hz)", "PC Pro · 스피치 마스킹",
                "• 대역 성형 마스킹 노이즈 + 12 Hz 알파 비트로 주변 대화·카페 소음 방해 완화\n• 사무실·공유오피스·독서실 이어폰 작업 시 30~50% 재생 추천",
                160.0f, 172.0f, false, 0.0f, 0.0f, 0.0f, false, 12.0f, 5,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C04 (104)
        list.add(new PresetModel(104, 2,
                "포모도로 25분 · 16Hz 안정 집중", "PC Pro · 타이머 최적",
                "• 16 Hz 저베타 바이노럴 + 초저속 호흡 AM(0.25 Hz)으로 산만함 없이 25분 페이스 유지\n• 타이머 30분 설정과 함께 문서 작성·학습 루틴용으로 활용",
                240.0f, 256.0f, false, 0.0f, 0.0f, 0.0f, false, 16.0f, 0,
                false, 0.0f, 0.0f, true, 0.06f, 0.25f));

        // C05 (105)
        list.add(new PresetModel(105, 2,
                "영상·강의 시청 병행 저자극 배경", "PC Pro · 비간섭 베드",
                "• 120/130 Hz 낮은 피치와 핑크노이즈 15%를 은은하게 깔아 사람 목소리 대역과 비충돌\n• 유튜브 강의·회의 녹화본·음악 감상 아래 15~25% 보조 재생 최적",
                120.0f, 130.0f, false, 0.0f, 0.0f, 0.0f, false, 10.0f, 1,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C06 (106)
        list.add(new PresetModel(106, 1,
                "20분 파워 낮잠 · 알파→델타 하강", "PC Pro · 낮잠 스윕",
                "• 8.0→3.0 Hz 완만한 왕복 스윕 + 브라운 노이즈로 짧은 휴식 리듬 전환\n• 점심 후 15분·30분 자동 정지 타이머와 함께 가동 권장 (수면 관성 최소화)",
                0.0f, 0.0f, true, 8.0f, 3.0f, 600.0f, false, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C07 (107)
        list.add(new PresetModel(107, 1,
                "심야 브라운 수면 실드 (1.8Hz)", "PC Pro · 야간 차폐",
                "• 110/111.8 Hz(1.8 Hz 델타 차)와 브라운 노이즈가 야간 층간·외부 돌발 소음 완충\n• PC 스피커 야간 재생 또는 60분 타이머 수면용 20~35% 볼륨 권장",
                110.0f, 111.8f, false, 0.0f, 0.0f, 0.0f, false, 1.8f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C08 (108)
        list.add(new PresetModel(108, 1,
                "취침 전 뇌파 쿨다운 (6.5Hz)", "PC Pro · 세타 전환",
                "• 170/176.5 Hz 세타 바이노럴 + 초저속 호흡 AM(0.15 Hz)으로 늦은 밤 각성 진정\n• PC 마감 전 15분 조용히 들으며 수면 모드로 자연 전환",
                170.0f, 176.5f, false, 0.0f, 0.0f, 0.0f, false, 6.5f, 0,
                false, 0.0f, 0.0f, true, 0.10f, 0.15f));

        // C09 (109)
        list.add(new PresetModel(109, 3,
                "발표·미팅 전 4-6 호흡 페이서", "PC Pro · 6회/분 호흡",
                "• 분당 6회(0.10 Hz) 파동 볼륨 호흡 페이서 + 따뜻한 136.1 Hz 옴 톤으로 긴장 정돈\n• 파형 팽창 시 4초 들이쉬고 수축 시 6초 내쉬기 (중요 미팅 5분 전 활용)",
                136.1f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.22f, 0.10f));

        // C10 (110)
        list.add(new PresetModel(110, 3,
                "창의적 영감 · 알파-세타 브릿지", "PC Pro · 7.5Hz 경계",
                "• 7.5 Hz 알파-세타 경계 바이노럴 + 미세 0.2 Hz FM 유동으로 고정관념 완화\n• 기획·디자인·아이디어 스케치 중 20~35% 배경 재생 권장",
                192.0f, 199.5f, false, 0.0f, 0.0f, 0.0f, false, 7.5f, 0,
                true, 0.02f, 0.20f, false, 0.0f, 0.0f));

        // C11 (111)
        list.add(new PresetModel(111, 3,
                "따뜻한 완전5도 화음 공명 (216+324Hz)", "PC Pro · 3:2 배음 앰비언트",
                "• 자연 조화음 216 Hz와 순정 완전5도 324 Hz(2:3 비율)의 풍성한 사운드 앰비언트\n• 단조로운 단일 톤이 부담스러울 때 PC 스피커·이어폰 모두 편안하게 활용",
                216.0f, 324.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 0.12f));

        // C12 (112)
        list.add(new PresetModel(112, 4,
                "이명 완화 · 대역 성형 소프트 마스킹", "PC Pro · 2.8kHz 롤오프",
                "• 고음역 치찰감을 낮춘(2.8 kHz 롤오프) 100% 순수 스테레오 마스킹 사운드\n• 조용한 방에서 귀 울림·이명감이 거슬릴 때 들릴 듯 말 듯한 15~30% 권장 (비의료 보조)",
                0.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 5,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C13 (113)
        list.add(new PresetModel(113, 4,
                "모니터 장시간 주시 · 5분 눈·어깨 휴식", "PC Pro · VDT 리셋",
                "• 180/190 Hz(10 Hz 알파) + 느린 어깨 이완 호흡(0.16 Hz)으로 화면 집중 후 긴장 완화\n• 1시간 PC 작업 후 눈을 감고 5분 쉴 때 20~35% 볼륨 추천",
                180.0f, 190.0f, false, 0.0f, 0.0f, 0.0f, false, 10.0f, 0,
                false, 0.0f, 0.0f, true, 0.10f, 0.16f));

        // C14 (114)
        list.add(new PresetModel(114, 5,
                "식곤증 · 오후 졸음 리프레시 (18.5Hz)", "PC Pro · 리듬 각성",
                "• 18.5 Hz 베타 바이노럴 + 경쾌한 2.0 Hz 리듬 AM으로 오후 쳐진 컨디션 환기\n• 점심 후 10~20분 신나는 음악 아래 30~45% 레이어링 활용",
                260.0f, 278.5f, false, 0.0f, 0.0f, 0.0f, false, 18.5f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 2.0f));

        // C15 (115)
        list.add(new PresetModel(115, 0,
                "PC 팬·60Hz 전기 험 체감 완화", "PC Pro · 60Hz 대역 마스킹",
                "• 60 Hz 험 + 120 Hz 완충 역위상 조합에 브라운 노이즈를 더해 PC 팬·어댑터 공명 체감 완화\n• 본체·변압기 소음이 거슬리는 실내 PC 환경에서 30~50% 권장",
                60.0f, 120.0f, false, 0.0f, 0.0f, 0.0f, true, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C16 (116)
        list.add(new PresetModel(116, 0,
                "스피커·이어폰 좌우 위상 자가 점검", "PC Pro · L/R 캘리브레이션",
                "• 220 Hz(L)와 221 Hz(R) 1.0 Hz 느린 맥놀이로 PC 스피커·이어폰 좌우 밸런스 및 접촉 점검\n• 새 이어폰·DAC 연결 직후 순수 원음 또는 이어폰 모드에서 30% 테스트 권장",
                220.0f, 221.0f, false, 0.0f, 0.0f, 0.0f, false, 1.0f, 0,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        sCachedProPresets = Collections.unmodifiableList(list);
        return sCachedProPresets;
    }

    public static synchronized List<PresetModel> getAllPresetsWithPro() {
        if (sCachedAll64 != null) {
            return sCachedAll64;
        }
        List<PresetModel> core = PresetModel.getAllPresets();
        List<PresetModel> pro = getProPackPresets();
        List<PresetModel> combined = new ArrayList<>(core.size() + pro.size());
        combined.addAll(core);
        combined.addAll(pro);
        sCachedAll64 = Collections.unmodifiableList(combined);
        return sCachedAll64;
    }

    public static boolean isProPreset(int id) {
        return id >= PRO_ID_MIN && id <= PRO_ID_MAX;
    }

    public static String getShortCode(PresetModel p) {
        if (p == null) {
            return "";
        }
        if (isProPreset(p.id)) {
            return String.format(Locale.US, "C%02d", p.id - 100);
        }
        return p.getShortCode();
    }

    public static PresetModel findAnyById(int id) {
        List<PresetModel> all = getAllPresetsWithPro();
        for (int i = 0; i < all.size(); i++) {
            PresetModel p = all.get(i);
            if (p.id == id) {
                return p;
            }
        }
        return all.get(0);
    }

    public static int getNextPresetId(int currentId, int delta) {
        List<PresetModel> all = getAllPresetsWithPro();
        int count = all.size();
        if (count == 0) {
            return 1;
        }
        int idx = 0;
        for (int i = 0; i < count; i++) {
            if (all.get(i).id == currentId) {
                idx = i;
                break;
            }
        }
        int nextIdx = ((idx + delta) % count + count) % count;
        return all.get(nextIdx).id;
    }
}
