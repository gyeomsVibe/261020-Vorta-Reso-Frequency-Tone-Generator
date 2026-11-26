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
                "포근한 브라운 노이즈 · 주변 소음 가리기", "Pro · 저음 소음 차폐",
                "• 폭포·빗소리처럼 낮은 톤의 부드러운 배경음으로 키보드·생활 소음을 자극 없이 가려줌\n• 장시간 업무·독서·공부할 때 25~45%로 틀어두면 귀 피로가 적음",
                0.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C02 (102)
        list.add(new PresetModel(102, 2,
                "40Hz 깊은 몰입(딥워크) · 소음 차폐", "Pro · 초집중 + 차폐",
                "• 방해 없이 깊게 몰입하는 40 Hz 집중 리듬에 포근한 폭포 계열 배경음을 섞어 단조로움을 줄임\n• 1시간 이상 코딩·기획·시험 공부할 때 이어폰 30~45% 추천",
                200.0f, 240.0f, false, 0.0f, 0.0f, 0.0f, false, 40.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C03 (103)
        list.add(new PresetModel(103, 2,
                "사무실·카페 말소리 가리기 (12Hz)", "Pro · 말소리 차폐",
                "• 주변 대화 소리를 부드럽게 덮는 배경음 + 차분한 12 Hz 집중 리듬 조합\n• 카페·사무실·독서실에서 말소리가 거슬릴 때 이어폰 30~50% 추천",
                160.0f, 172.0f, false, 0.0f, 0.0f, 0.0f, false, 12.0f, 5,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C04 (104)
        list.add(new PresetModel(104, 2,
                "25분 집중·5분 휴식 (포모도로)", "Pro · 25분 집중 루틴",
                "• 25분 일하고 5분 쉬는 집중법(포모도로)에 맞춰 딴생각 없이 페이스를 잡아주는 안정 리듬\n• 타이머 30분 설정 후 문서 작성·공부·업무 시작 시 25~40%로 활용",
                240.0f, 256.0f, false, 0.0f, 0.0f, 0.0f, false, 16.0f, 0,
                false, 0.0f, 0.0f, true, 0.06f, 0.25f));

        // C05 (105)
        list.add(new PresetModel(105, 2,
                "영상·강의 들을 때 조용한 배경음", "Pro · 음성 방해 없음",
                "• 말소리와 겹치지 않는 낮은 톤 + 은은한 바람 계열 배경음으로 영상·인강 시청을 방해하지 않음\n• 유튜브·강의·회의를 들으면서 15~25% 작은 음량으로 깔아두기",
                120.0f, 130.0f, false, 0.0f, 0.0f, 0.0f, false, 10.0f, 1,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C06 (106)
        list.add(new PresetModel(106, 1,
                "20분 짧은 낮잠 · 개운한 휴식", "Pro · 파워 낮잠",
                "• 8→3 Hz로 천천히 낮아지는 리듬과 포근한 배경음으로 너무 깊게 잠들지 않게 짧은 휴식 유도\n• 점심 후 15~30분 타이머를 걸어두고 눈 붙일 때 추천 (일어날 때 멍함 최소화)",
                0.0f, 0.0f, true, 8.0f, 3.0f, 600.0f, false, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C07 (107)
        list.add(new PresetModel(107, 1,
                "심야 소음 가리는 깊은 수면 방패", "Pro · 야간 소음 차폐",
                "• 깊은 숙면 리듬(1.8 Hz)에 포근한 저음 배경음을 더해 밤중 층간·차량 돌발 소음을 완충\n• 잘 때 60분 타이머를 걸고 20~35% 볼륨으로 틀어두기",
                110.0f, 111.8f, false, 0.0f, 0.0f, 0.0f, false, 1.8f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C08 (108)
        list.add(new PresetModel(108, 1,
                "잠들기 15분 전 · 머리 식히기", "Pro · 취침 전 진정",
                "• 늦은 밤까지 깨어 있는 두뇌를 차분한 졸음 리듬(6.5 Hz)과 느린 호흡 파동으로 진정\n• 휴대폰·PC 마감 후 침대에 눕기 전 15분간 20~35%로 조용히 청취",
                170.0f, 176.5f, false, 0.0f, 0.0f, 0.0f, false, 6.5f, 0,
                false, 0.0f, 0.0f, true, 0.10f, 0.15f));

        // C09 (109)
        list.add(new PresetModel(109, 3,
                "발표·면접 전 4-6 호흡 긴장 풀기", "Pro · 4초 들숨·6초 날숨",
                "• 소리가 커질 때 4초 들이쉬고 작아질 때 6초 내쉬는 리듬(1분 6회)으로 두근거림 진정\n• 중요한 발표·미팅·시험 5분 전에 소리에 맞춰 숨 쉬기",
                136.1f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.22f, 0.10f));

        // C10 (110)
        list.add(new PresetModel(110, 3,
                "아이디어 · 창의적 기획 발상", "Pro · 유연한 사고",
                "• 편안함과 몽롱함의 경계 리듬(7.5 Hz)으로 굳은 생각을 부드럽게 풀어줌\n• 기획·글쓰기·디자인 아이디어가 막힐 때 20~35% 배경 재생 추천",
                192.0f, 199.5f, false, 0.0f, 0.0f, 0.0f, false, 7.5f, 0,
                true, 0.02f, 0.20f, false, 0.0f, 0.0f));

        // C11 (111)
        list.add(new PresetModel(111, 3,
                "따뜻한 화음 공명 · 편안한 공간음", "Pro · 부드러운 화음",
                "• 귀에 편안한 두 음(216+324 Hz)이 화음으로 어우러져 단조로운 기계음 느낌이 없음\n• 단순한 삐 소리가 부담스러울 때 스피커·이어폰 어디서나 편안하게 휴식",
                216.0f, 324.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 0.12f));

        // C12 (112)
        list.add(new PresetModel(112, 4,
                "귀울림(이명) 완화 · 부드러운 배경음", "Pro · 고음 자극 제거",
                "• 귀에 거슬리는 날카로운 고음을 깎아낸(2.8 kHz 이하) 부드러운 배경 차폐음\n• 조용한 방에서 귀울림이 신경 쓰일 때 들릴 듯 말 듯 15~30% 추천 (의료기기 아님)",
                0.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false, 0.0f, 5,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C13 (113)
        list.add(new PresetModel(113, 4,
                "화면 오래 본 뒤 · 5분 눈·어깨 휴식", "Pro · 모니터·폰 휴식",
                "• 차분한 10 Hz 휴식 리듬과 느린 어깨 이완 파동으로 화면 집중 후 쌓인 긴장 완화\n• 1시간 PC·스마트폰 사용 후 눈을 감고 5분 쉴 때 20~35% 추천",
                180.0f, 190.0f, false, 0.0f, 0.0f, 0.0f, false, 10.0f, 0,
                false, 0.0f, 0.0f, true, 0.10f, 0.16f));

        // C14 (114)
        list.add(new PresetModel(114, 5,
                "점심 후 식곤증 · 오후 졸음 깨기", "Pro · 오후 활력",
                "• 경쾌한 18.5 Hz 각성 리듬과 톡톡 튀는 맥박감으로 나른한 오후 컨디션을 환기\n• 점심 후 졸릴 때 10~20분, 신나는 음악 아래 30~45%로 함께 재생",
                260.0f, 278.5f, false, 0.0f, 0.0f, 0.0f, false, 18.5f, 0,
                false, 0.0f, 0.0f, true, 0.08f, 2.0f));

        // C15 (115)
        list.add(new PresetModel(115, 0,
                "가전·PC 팬 웅웅거림 체감 완화", "Pro · 60Hz 전기음 완충",
                "• 냉장고·PC 팬·충전기에서 나는 60 Hz 전기 웅웅거림을 반대 파동과 저음 배경으로 완충\n• 방 안 전자기기 소음이 거슬릴 때 30~50%로 재생",
                60.0f, 120.0f, false, 0.0f, 0.0f, 0.0f, true, 0.0f, 4,
                false, 0.0f, 0.0f, false, 0.0f, 0.0f));

        // C16 (116)
        list.add(new PresetModel(116, 0,
                "이어폰·스피커 좌우 소리 균형 점검", "Pro · 좌우 테스트",
                "• 왼쪽(220 Hz)과 오른쪽(221 Hz)의 느린 울림으로 이어폰·스피커 좌우 균형과 접촉 확인\n• 새 이어폰이나 스피커 연결 직후 30%로 간단히 테스트",
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
