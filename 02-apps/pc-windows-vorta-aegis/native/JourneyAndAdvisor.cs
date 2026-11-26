using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace VortaAegisPC
{
    public sealed class StageGroup
    {
        public readonly int TabIndex;
        public readonly int StageNum;
        public readonly string Title;
        public readonly string Subtitle;
        public readonly int[] PresetIds;

        public StageGroup(int tabIndex, int stageNum, string title, string subtitle, int[] presetIds)
        {
            TabIndex = tabIndex;
            StageNum = stageNum;
            Title = title;
            Subtitle = subtitle;
            PresetIds = presetIds;
        }
    }

    public sealed class RoutineStep
    {
        public readonly int PresetId;
        public readonly int DurationMinutes;
        public readonly string StageLabel;

        public RoutineStep(int presetId, int durationMinutes, string stageLabel)
        {
            PresetId = presetId;
            DurationMinutes = durationMinutes;
            StageLabel = stageLabel;
        }
    }

    public sealed class RoutineCourse
    {
        public readonly string Id;
        public readonly string Title;
        public readonly string Badge;
        public readonly int TabIndex;
        public readonly RoutineStep[] Steps;

        public RoutineCourse(string id, string title, string badge, int tabIndex, RoutineStep[] steps)
        {
            Id = id;
            Title = title;
            Badge = badge;
            TabIndex = tabIndex;
            Steps = steps;
        }

        public int GetTotalMinutes()
        {
            int sum = 0;
            if (Steps != null)
            {
                for (int i = 0; i < Steps.Length; i++)
                {
                    sum += Steps[i].DurationMinutes;
                }
            }
            return sum;
        }
    }

    public static class JourneyOrderPolicy
    {
        private static readonly StageGroup[][] TAB_STAGES = new StageGroup[][]
        {
            // Tab 0: 안티방어 (16)
            new StageGroup[]
            {
                new StageGroup(0, 1, "1단계 · 신체 증상별 즉시 방어", "눈·어지러움·가슴·척추 대응",
                    new int[] { 1, 2, 3, 7, 8, 5, 6 }),
                new StageGroup(0, 2, "2단계 · 야간 수면·실내 험 상쇄", "야간 저주파·55/60Hz 완충",
                    new int[] { 4, 222, 9, 115 }),
                new StageGroup(0, 3, "3단계 · 자동 추적 스윕 방어", "변동형·전 대역 순환 분산",
                    new int[] { 10, 11, 12, 223 }),
                new StageGroup(0, 4, "4단계 · 스피커·이어폰 점검", "L/R 위상·밸런스 확인",
                    new int[] { 116 })
            },
            // Tab 1: 수면 (12)
            new StageGroup[]
            {
                new StageGroup(1, 1, "1단계 · 취침 전 준비·긴장 풀기", "이완 호흡·몸 긴장 해소",
                    new int[] { 108, 16, 207 }),
                new StageGroup(1, 2, "2단계 · 입면 유도", "알파→델타 하강·잡념 진정",
                    new int[] { 208, 14, 15 }),
                new StageGroup(1, 3, "3단계 · 깊은 숙면·소음 차폐", "서파 숙면·야간 소음 실드",
                    new int[] { 13, 17, 18, 107 }),
                new StageGroup(1, 4, "4단계 · 낮잠·아침 기상", "파워 낮잠·기상 관성 깨우기",
                    new int[] { 106, 209 })
            },
            // Tab 2: 집중·공부 (14)
            new StageGroup[]
            {
                new StageGroup(2, 1, "1단계 · 집중 워밍업·시동", "산만함 정리·작업 착수",
                    new int[] { 210, 21, 104 }),
                new StageGroup(2, 2, "2단계 · 본 작업·학습 몰입", "독서·암기·코딩·깊은 몰입",
                    new int[] { 20, 22, 19, 102, 211, 23 }),
                new StageGroup(2, 3, "3단계 · 소음 차폐·영상 병행", "브라운·스피치 마스킹",
                    new int[] { 101, 103, 24, 105 }),
                new StageGroup(2, 4, "4단계 · 집중 후 두뇌 쿨다운", "과열된 뇌 피로 리셋",
                    new int[] { 212 })
            },
            // Tab 3: 명상·안정 (11)
            new StageGroup[]
            {
                new StageGroup(3, 1, "1단계 · 호흡 정돈·긴장 해소", "4-6 호흡·심박 안정",
                    new int[] { 109, 213, 27 }),
                new StageGroup(3, 2, "2단계 · 마음 가라앉히기·화음", "슈만 공명·정서 안정",
                    new int[] { 25, 28, 111 }),
                new StageGroup(3, 3, "3단계 · 깊은 명상·내면 몰입", "세타 진입·창의 영감",
                    new int[] { 214, 26, 110 }),
                new StageGroup(3, 4, "4단계 · 퇴근·하루 마무리", "번아웃·저녁 감정 정리",
                    new int[] { 29, 215 })
            },
            // Tab 4: 두통·통증 (10)
            new StageGroup[]
            {
                new StageGroup(4, 1, "1단계 · 눈·목·어깨 피로 풀기", "화면 피로·거북목 이완",
                    new int[] { 113, 33, 216 }),
                new StageGroup(4, 2, "2단계 · 두통·편두통 감압", "저자극 진정·감압 휴식",
                    new int[] { 30, 32, 34, 217 }),
                new StageGroup(4, 3, "3단계 · 근육·허리·이명 보조", "온열 저역·대역 마스킹",
                    new int[] { 31, 218, 112 })
            },
            // Tab 5: 운동·피로 (12)
            new StageGroup[]
            {
                new StageGroup(5, 1, "1단계 · 운동 전 준비", "몸·마음 워밍업 & 신경 점화",
                    new int[] { 201, 202 }),
                new StageGroup(5, 2, "2단계 · 본 운동 (상황별)", "고중량·근비대·유산소·인터벌",
                    new int[] { 35, 203, 36, 204 }),
                new StageGroup(5, 3, "3단계 · 운동 직후 쿨다운", "심박 하강·부교감 전환",
                    new int[] { 205, 37 }),
                new StageGroup(5, 4, "4단계 · 근육 회복·피로 리셋", "근육통 복구·일상 활력",
                    new int[] { 206, 114, 38, 39 })
            },
            // Tab 6: 기분·힐링 (8)
            new StageGroup[]
            {
                new StageGroup(6, 1, "1단계 · 불안·화·스트레스 진정", "치미는 감정 온도 낮추기",
                    new int[] { 219, 44, 43 }),
                new StageGroup(6, 2, "2단계 · 정서 힐링·마음 위로", "396+528 화음·스트레스 완화",
                    new int[] { 41, 42, 220 }),
                new StageGroup(6, 3, "3단계 · 무기력 탈출·기분 전환", "산뜻한 활력·에너지 회복",
                    new int[] { 40, 221 })
            },
            // Tab 7: 모기·벌레 (5)
            new StageGroup[]
            {
                new StageGroup(7, 1, "1단계 · 실내·취침 무음 방어", "16~21.5kHz 무음 가드",
                    new int[] { 45 }),
                new StageGroup(7, 2, "2단계 · 창가·실내 날벌레 보조", "날파리·모기 기피 펄스",
                    new int[] { 47, 224 }),
                new StageGroup(7, 3, "3단계 · 캠핑·야외 강력 방어", "잠자리 천적 펄스·복합 가드",
                    new int[] { 46, 48 })
            }
        };

        private static readonly IList<RoutineCourse> ROUTINES;
        private static readonly int[] GLOBAL_ORDERED_IDS;
        private static readonly Dictionary<int, int> GLOBAL_RANK_MAP;
        private static readonly Dictionary<int, string> STAGE_LABEL_MAP;

        static JourneyOrderPolicy()
        {
            List<RoutineCourse> rList = new List<RoutineCourse>(4);
            rList.Add(new RoutineCourse(
                "workout_heavy",
                "웨이트 풀코스",
                "준비→고중량→쿨다운→회복 · 65분",
                5,
                new RoutineStep[]
                {
                    new RoutineStep(201, 5, "운동 전 워밍업"),
                    new RoutineStep(202, 5, "신경 점화"),
                    new RoutineStep(35, 35, "고중량 몰입"),
                    new RoutineStep(205, 10, "심박 하강 쿨다운"),
                    new RoutineStep(206, 10, "근육 깊은 회복")
                }));
            rList.Add(new RoutineCourse(
                "workout_volume",
                "근비대·유산소 코스",
                "준비→반복세트→쿨다운 · 55분",
                5,
                new RoutineStep[]
                {
                    new RoutineStep(201, 5, "운동 전 워밍업"),
                    new RoutineStep(203, 35, "근비대 몰입"),
                    new RoutineStep(37, 15, "근육 쿨다운")
                }));
            rList.Add(new RoutineCourse(
                "focus_deep",
                "깊은 집중 풀코스",
                "예열→깊은몰입→뇌휴식 · 60분",
                2,
                new RoutineStep[]
                {
                    new RoutineStep(210, 5, "몰입 예열"),
                    new RoutineStep(102, 45, "40Hz 깊은 몰입"),
                    new RoutineStep(212, 10, "뇌 피로 쿨다운")
                }));
            rList.Add(new RoutineCourse(
                "sleep_deep",
                "꿀잠 숙면 코스",
                "4-6호흡→입면→소음차폐 · 60분",
                1,
                new RoutineStep[]
                {
                    new RoutineStep(207, 10, "4-6 호흡 이완"),
                    new RoutineStep(208, 20, "10→2Hz 입면 하강"),
                    new RoutineStep(107, 30, "심야 브라운 실드")
                }));
            ROUTINES = rList.AsReadOnly();

            List<int> global = new List<int>(88);
            GLOBAL_RANK_MAP = new Dictionary<int, int>(88);
            STAGE_LABEL_MAP = new Dictionary<int, string>(88);
            int rank = 0;
            for (int t = 0; t < TAB_STAGES.Length; t++)
            {
                StageGroup[] groups = TAB_STAGES[t];
                for (int g = 0; g < groups.Length; g++)
                {
                    int[] ids = groups[g].PresetIds;
                    for (int k = 0; k < ids.Length; k++)
                    {
                        int pid = ids[k];
                        global.Add(pid);
                        GLOBAL_RANK_MAP[pid] = rank++;
                        STAGE_LABEL_MAP[pid] = groups[g].Title;
                    }
                }
            }
            GLOBAL_ORDERED_IDS = global.ToArray();
        }

        public static StageGroup[] GetStagesForTab(int tabIndex)
        {
            int clamped = TabSyncPolicy.ClampTabIndex(tabIndex);
            return TAB_STAGES[clamped];
        }

        public static string GetStageLabelForPreset(int presetId)
        {
            string title;
            if (STAGE_LABEL_MAP.TryGetValue(presetId, out title))
            {
                return title;
            }
            return "";
        }

        public static int GetGlobalOrderRank(int presetId)
        {
            int rank;
            if (GLOBAL_RANK_MAP.TryGetValue(presetId, out rank))
            {
                return rank;
            }
            return 100000 + presetId;
        }

        public static int GetNextOrderedPresetId(IList<PresetItem> all, int currentId, int delta)
        {
            if (all == null || all.Count == 0) return 1;
            HashSet<int> present = new HashSet<int>();
            for (int i = 0; i < all.Count; i++)
            {
                present.Add(all[i].Id);
            }
            List<int> ordered = new List<int>(all.Count);
            for (int i = 0; i < GLOBAL_ORDERED_IDS.Length; i++)
            {
                int id = GLOBAL_ORDERED_IDS[i];
                if (present.Remove(id))
                {
                    ordered.Add(id);
                }
            }
            if (present.Count > 0)
            {
                for (int i = 0; i < all.Count; i++)
                {
                    if (present.Contains(all[i].Id))
                    {
                        ordered.Add(all[i].Id);
                    }
                }
            }
            int size = ordered.Count;
            if (size == 0) return all[0].Id;
            int idx = 0;
            for (int i = 0; i < size; i++)
            {
                if (ordered[i] == currentId)
                {
                    idx = i;
                    break;
                }
            }
            int nextIdx = ((idx + delta) % size + size) % size;
            return ordered[nextIdx];
        }

        public static IList<RoutineCourse> GetAllRoutines()
        {
            return ROUTINES;
        }

        public static RoutineCourse GetRoutineById(string id)
        {
            if (string.IsNullOrEmpty(id)) return null;
            for (int i = 0; i < ROUTINES.Count; i++)
            {
                if (string.Equals(ROUTINES[i].Id, id, StringComparison.Ordinal))
                {
                    return ROUTINES[i];
                }
            }
            return null;
        }
    }

    public static class PresetAdvisor
    {
        private static readonly int[] REC_MORNING = new int[] { 209, 210, 1 };
        private static readonly int[] REC_FOCUS = new int[] { 102, 211, 103 };
        private static readonly int[] REC_EVENING = new int[] { 201, 35, 205 };
        private static readonly int[] REC_NIGHT = new int[] { 208, 107, 4 };

        private static readonly string[][] SYNONYMS = new string[][]
        {
            new string[] { "잠", "수면", "숙면", "취침", "델타", "밤", "입면" },
            new string[] { "불면", "수면", "숙면", "취침", "델타", "밤", "입면" },
            new string[] { "잘때", "수면", "숙면", "취침", "델타", "밤" },
            new string[] { "헬스", "운동", "웨이트", "근력", "근비대", "워밍업", "쿨다운" },
            new string[] { "근력", "운동", "웨이트", "근비대" },
            new string[] { "워밍업", "준비", "예열", "운동" },
            new string[] { "준비", "워밍업", "깨우기", "예열" },
            new string[] { "쿨다운", "하강", "이완", "회복", "숨" },
            new string[] { "근육통", "회복", "복구", "뻐근" },
            new string[] { "회복", "복구", "휴식", "쿨다운", "피로" },
            new string[] { "근비대", "반복", "웨이트" },
            new string[] { "인터벌", "서킷", "고강도" },
            new string[] { "기상", "아침", "깨우기", "각성" },
            new string[] { "런닝", "운동", "유산소", "러닝" },
            new string[] { "러닝", "운동", "유산소" },
            new string[] { "공부", "집중", "딥워크", "포모도로", "감마", "알파", "암기" },
            new string[] { "포모도로", "25분", "집중", "루틴", "휴식" },
            new string[] { "딥워크", "몰입", "집중", "감마" },
            new string[] { "일", "집중", "업무", "딥워크", "포모도로", "베타" },
            new string[] { "업무", "집중", "딥워크", "포모도로", "베타" },
            new string[] { "코딩", "집중", "딥워크", "베타", "감마" },
            new string[] { "머리", "두통", "통증", "편두통" },
            new string[] { "편두통", "두통", "통증", "진정" },
            new string[] { "거북목", "목", "어깨" },
            new string[] { "화", "짜증", "감정", "진정", "스트레스" },
            new string[] { "짜증", "화", "감정", "진정", "스트레스" },
            new string[] { "층간", "안티", "차폐", "마스킹", "바닥", "상쇄" },
            new string[] { "소음", "안티", "차폐", "마스킹", "상쇄" },
            new string[] { "차폐", "안티", "마스킹", "브라운", "핑크" },
            new string[] { "방어", "안티", "상쇄", "스윕" },
            new string[] { "비", "브라운", "핑크", "마스킹" },
            new string[] { "빗소리", "브라운", "핑크", "마스킹" },
            new string[] { "노이즈", "브라운", "핑크", "마스킹" },
            new string[] { "귀울림", "이명", "배경음" },
            new string[] { "이명", "귀울림", "배경음" },
            new string[] { "날파리", "초파리", "모기", "해충" },
            new string[] { "벌레", "모기", "해충", "날파리" },
            new string[] { "해충", "모기", "날파리" }
        };

        public static int[] GetTimeOfDayRecommendations(int hourOfDay)
        {
            int h = ((hourOfDay % 24) + 24) % 24;
            if (h >= 6 && h <= 10) return (int[])REC_MORNING.Clone();
            if (h >= 11 && h <= 16) return (int[])REC_FOCUS.Clone();
            if (h >= 17 && h <= 21) return (int[])REC_EVENING.Clone();
            return (int[])REC_NIGHT.Clone();
        }

        public static string GetTimeWindowLabel(int hourOfDay)
        {
            int h = ((hourOfDay % 24) + 24) % 24;
            if (h >= 6 && h <= 10) return "아침 추천 · 즐겨찾기 · 최근";
            if (h >= 11 && h <= 16) return "낮·집중 추천 · 즐겨찾기 · 최근";
            if (h >= 17 && h <= 21) return "저녁·운동 추천 · 즐겨찾기 · 최근";
            return "심야·수면 추천 · 즐겨찾기 · 최근";
        }

        public static bool MatchesQuery(PresetItem p, string tabName, string rawQuery)
        {
            if (p == null) return false;
            if (string.IsNullOrEmpty(rawQuery)) return true;
            string trimmed = rawQuery.Trim();
            if (trimmed.Length == 0) return true;

            StringBuilder hay = new StringBuilder(340);
            if (!string.IsNullOrEmpty(p.ShortCode)) hay.Append(p.ShortCode).Append(' ');
            if (!string.IsNullOrEmpty(tabName)) hay.Append(tabName).Append(' ');
            string stageTitle = JourneyOrderPolicy.GetStageLabelForPreset(p.Id);
            if (!string.IsNullOrEmpty(stageTitle)) hay.Append(stageTitle).Append(' ');
            if (!string.IsNullOrEmpty(p.PackName)) hay.Append(p.PackName).Append(' ');
            if (!string.IsNullOrEmpty(p.Title)) hay.Append(p.Title).Append(' ');
            if (!string.IsNullOrEmpty(p.Badge)) hay.Append(p.Badge).Append(' ');
            if (!string.IsNullOrEmpty(p.Desc)) hay.Append(p.Desc).Append(' ');

            if (p.IsSweep)
            {
                if (p.Freq1 > 0f) hay.Append(FormatHzToken(p.Freq1)).Append(' ');
                hay.Append(FormatHzToken(p.SweepStart)).Append(' ');
                hay.Append(FormatHzToken(p.SweepEnd)).Append(' ');
            }
            else
            {
                if (p.Freq1 > 0f) hay.Append(FormatHzToken(p.Freq1)).Append(' ');
                if (p.Freq2 > 0f) hay.Append(FormatHzToken(p.Freq2)).Append(' ');
                if (p.BinauralBeat > 0f) hay.Append(FormatHzToken(p.BinauralBeat)).Append(' ');
            }
            if (p.IsAntiPhase) hay.Append("역위상 상쇄 ");
            if (p.NoiseType == 4) hay.Append("브라운 노이즈 ");
            if (p.NoiseType == 5) hay.Append("이명 귀울림 마스킹 ");
            if (p.NoiseType == 1) hay.Append("핑크 노이즈 ");

            string hayLower = hay.ToString().ToLowerInvariant();
            string hayCompact = hayLower.Replace(" ", "");

            string[] tokens = trimmed.ToLowerInvariant().Split((char[])null, StringSplitOptions.RemoveEmptyEntries);
            for (int i = 0; i < tokens.Length; i++)
            {
                if (!MatchesToken(hayLower, hayCompact, tokens[i]))
                {
                    return false;
                }
            }
            return true;
        }

        private static string FormatHzToken(float hz)
        {
            if (hz >= 1000.0f)
            {
                return string.Format(CultureInfo.InvariantCulture, "{0:F1} hz {1:F0} hz {2:F1} khz {3:F1}hz {4:F0}hz", hz, hz, hz / 1000.0f, hz, hz);
            }
            if (Math.Abs(hz - Math.Round(hz)) < 0.05f)
            {
                return string.Format(CultureInfo.InvariantCulture, "{0:F0} hz {1:F2} hz {2:F0}hz", hz, hz, hz);
            }
            return string.Format(CultureInfo.InvariantCulture, "{0:F2} hz {1:F1} hz", hz, hz);
        }

        private static bool MatchesToken(string hayLower, string hayCompact, string tok)
        {
            if (hayLower.Contains(tok) || hayCompact.Contains(tok)) return true;
            for (int i = 0; i < SYNONYMS.Length; i++)
            {
                string[] row = SYNONYMS[i];
                if (string.Equals(row[0], tok, StringComparison.Ordinal))
                {
                    for (int j = 1; j < row.Length; j++)
                    {
                        if (hayLower.Contains(row[j])) return true;
                    }
                }
            }
            return false;
        }

        public static string GetListeningGuideBadge(PresetItem p)
        {
            if (p == null) return "스피커/이어폰 OK · 추천 25~45%";
            if (p.NoiseType == 2) return "스피커 전용 · 이어폰 주의 · 추천 75~100%";
            if (p.BinauralBeat > 0.0f || p.IsBinauralRamp) return "이어폰 최적 · 음악/영상 병행 OK · 추천 25~45%";
            bool lowAnti = p.IsAntiPhase && ((p.IsSweep && p.SweepStart < 60.0f) || (!p.IsSweep && p.Freq1 > 0.0f && p.Freq1 < 60.0f));
            if (lowAnti) return "저역 방어 · 스피커 보정/이어폰 · 추천 40~65%";
            if (p.NoiseType == 4 || p.NoiseType == 5 || p.NoiseType == 1) return "스피커/이어폰 모두 OK · 추천 20~40%";
            if (p.TabIndex == 1 || p.TabIndex == 4) return "조용한 휴식 · 배경 병행 OK · 추천 20~40%";
            return "스피커/이어폰 OK · 배경 병행 가능 · 추천 25~45%";
        }

        public static int GetRecommendedVolumePercent(PresetItem p)
        {
            if (p == null) return VolumeCurve.DEFAULT_PERCENT;
            if (p.NoiseType == 2) return 80;
            if (p.Id == 105 || p.Id == 112 || p.Id == 28 || p.Id == 32 || p.Id == 217) return 20;
            if (p.TabIndex == 1 || p.TabIndex == 3 || p.TabIndex == 4) return 25;
            if (p.TabIndex == 0) return 45;
            return 35;
        }

        public static bool ShouldWarnHighVolumeSwitch(PresetItem from, PresetItem to, int currentVolPct)
        {
            if (currentVolPct <= 75 || to == null || to.NoiseType == 2) return false;
            return to.TabIndex == 1 || to.TabIndex == 3 || to.TabIndex == 4
                || to.Id == 105 || to.Id == 112;
        }

        public static string BuildPlainExplanation(PresetItem p)
        {
            if (p == null) return "";
            StringBuilder sb = new StringBuilder(420);
            sb.Append("[이게 뭔가요?]\n");
            string custom = GetSpecialTopicIntro(p.Id);
            if (!string.IsNullOrEmpty(custom))
            {
                sb.Append(custom).Append('\n');
            }
            sb.Append(GetPlainSoundMechanism(p)).Append("\n\n");

            string stageLabel = JourneyOrderPolicy.GetStageLabelForPreset(p.Id);
            if (!string.IsNullOrEmpty(stageLabel))
            {
                sb.Append("[여정 단계]\n").Append(stageLabel).Append("\n\n");
            }

            sb.Append("[언제 · 어떻게 쓰나요?]\n");
            if (!string.IsNullOrEmpty(p.Desc))
            {
                sb.Append(p.Desc.Trim()).Append('\n');
            }
            sb.Append("• 청취 가이드: ").Append(GetListeningGuideBadge(p))
              .Append(" (추천 시작 볼륨 ").Append(GetRecommendedVolumePercent(p)).Append("%)\n\n");

            sb.Append("[참고 원리]\n").Append(GetPlainScienceNote(p));
            return sb.ToString();
        }

        private static string GetSpecialTopicIntro(int id)
        {
            switch (id)
            {
                case 104:
                    return "• '포모도로'는 25분 동안 한 가지 일에만 집중하고 5분 쉬는 가장 유명한 시간 관리 집중법입니다. 30분 타이머와 함께 켜두면 공부·업무 리듬을 쉽게 유지할 수 있습니다.";
                case 102:
                    return "• '딥워크(깊은 몰입)'는 스마트폰·메신저 방해 없이 한 가지 어려운 일에 1시간 이상 깊게 빠져드는 작업 방식을 뜻합니다.";
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

        private static string GetPlainSoundMechanism(PresetItem p)
        {
            if (p.NoiseType == 2)
            {
                return "• 사람 귀에는 거의 들리지 않는 높은 고주파를 스피커로 내보내 모기·초파리 접근을 불편하게 만드는 해충 방어용 소리입니다. (이어폰 금지)";
            }
            if (p.IsAntiPhase)
            {
                return "• '역위상(안티방어)'은 웅웅거리는 불쾌한 저주파 진동과 정반대 방향의 파동을 함께 내보내 답답함과 체감 울림을 부드럽게 분산합니다.";
            }
            if (p.IsBinauralRamp)
            {
                int mins = Math.Max(1, (int)Math.Round(p.SweepDurationSec / 60.0f));
                return string.Format(CultureInfo.InvariantCulture,
                    "• 처음 {0:F1} Hz 리듬에서 시작해 {1}분 동안 {2:F1} Hz로 서서히 변한 뒤 그 상태를 유지하는 자동 전환 코스형 사운드입니다.",
                    p.SweepStart, mins, p.SweepEnd);
            }
            if (p.BinauralBeat > 0f)
            {
                return string.Format(CultureInfo.InvariantCulture,
                    "• 왼쪽 귀와 오른쪽 귀에 미세하게 다른 두 음을 들려주면 뇌가 그 차이({0:F1} Hz)만큼의 자연스러운 리듬을 느끼게 됩니다.",
                    p.BinauralBeat);
            }
            if (p.NoiseType == 4 || p.NoiseType == 5 || (p.Freq1 <= 0f && !p.IsSweep))
            {
                return "• 자극 없는 폭포·바람·빗소리 계열의 연속 배경음을 양쪽에서 따로 생성해 주변 소음과 말소리를 자연스럽게 덮어줍니다.";
            }
            if (p.Freq2 > 0f)
            {
                return string.Format(CultureInfo.InvariantCulture,
                    "• {0:F0} Hz와 {1:F0} Hz 두 음이 화음처럼 부드럽게 어우러져 단조로운 기계음 느낌 없이 편하게 들을 수 있습니다.",
                    p.Freq1, p.Freq2);
            }
            return "• 일정하고 순수한 단일 울림을 왜곡 없이 재생해 몸과 마음의 긴장을 차분히 가라앉힙니다.";
        }

        private static string GetPlainScienceNote(PresetItem p)
        {
            if (p.NoiseType == 2)
            {
                return "• 이어폰이나 헤드폰으로 재생할 때 보호 모드가 켜지면 자동으로 음량이 작아집니다(-12 dB 보호). 반드시 스피커로만 사용하세요.";
            }
            if (p.IsBinauralRamp)
            {
                return string.Format(CultureInfo.InvariantCulture,
                    "• 기본 톤 {0:F0} Hz 기준으로 좌우 주파수 차이를 {1:F1} → {2:F1} Hz까지 이동시킵니다. (음악·유튜브와 동시 재생 가능)",
                    p.Freq1, p.SweepStart, p.SweepEnd);
            }
            if (p.BinauralBeat > 0f)
            {
                return string.Format(CultureInfo.InvariantCulture,
                    "• 좌 {0:F1} Hz / 우 {1:F1} Hz 조합으로 {2:F1} Hz 리듬을 만듭니다. 이어폰 사용 시 효과가 가장 선명하며, PC 스피커 보정 모드에서도 리듬 맥놀이를 보조합니다.",
                    p.Freq1, p.Freq2, p.BinauralBeat);
            }
            if (p.AmMod && p.AmRate > 0f && p.AmRate <= 0.25f)
            {
                return string.Format(CultureInfo.InvariantCulture,
                    "• 소리 크기가 약 {0:F1}초마다 천천히 커졌다 작아지며 자연스러운 호흡 박자를 안내합니다.",
                    1.0f / p.AmRate);
            }
            return "• 보르타 자체 볼륨을 20~40%로 맞추면 좋아하는 음악·유튜브·강의를 켠 채 겹쳐 들을 수 있습니다.";
        }

        public static string GetBeginnerGlossaryText()
        {
            return "어려운 용어를 몰라도 상황만 보고 바로 고를 수 있도록 쉽게 정리했습니다.\n\n"
                + "1. 포모도로 (25분 집중 + 5분 휴식)\n"
                + "• 타이머를 맞춰 25분 동안 딱 한 가지 공부·업무만 하고, 5분 쉬는 가장 유명한 집중 방법입니다.\n\n"
                + "2. 딥워크 (깊은 몰입)\n"
                + "• 메신저·영상 방해 없이 45~60분 동안 어려운 일에 푹 빠져드는 상태입니다. '40Hz 깊은 몰입'이나 '깊은 집중 풀코스'를 쓰면 좋습니다.\n\n"
                + "3. 바이노럴 & 진행 램프\n"
                + "• 바이노럴: 왼쪽·오른쪽 귀에 약간 다른 소리를 들려줘 집중·휴식 리듬을 만드는 방식입니다. 이어폰을 끼면 가장 잘 느껴집니다.\n"
                + "• 진행 램프 (예: 10→18Hz): 처음부터 강한 소리가 아니라 5~20분 동안 서서히 원하는 리듬으로 바뀌는 기능입니다.\n\n"
                + "4. 숫자로 보는 뇌파 리듬 (Hz)\n"
                + "• 0.5~4 Hz (델타): 아주 깊은 숙면 · 머리 휴식\n"
                + "• 4~8 Hz (세타): 졸음 유도 · 명상 · 근육 회복\n"
                + "• 8~12 Hz (알파): 편안한 독서 · 긴장 풀기 · 호흡 안정\n"
                + "• 12~24 Hz (베타): 공부 · 웨이트 운동 · 멍함 깨우기\n"
                + "• 40 Hz (감마): 고난도 코딩 · 시험 대비 초집중\n\n"
                + "5. 배경음 (브라운 / 핑크 / 빗소리)\n"
                + "• 하단 '배경음' 블렌더를 켜면 어떤 주파수에도 폭포·바람·빗소리 배경음을 0~60% 비율로 자연스럽게 깔 수 있습니다.\n\n"
                + "6. 안티방어 (역위상 상쇄)\n"
                + "• 바닥·벽에서 웅웅거리는 저주파 진동과 반대 방향 파동을 내보내 불편함과 울림을 부드럽게 분산합니다.\n\n"
                + "7. 4-6 호흡법\n"
                + "• 소리가 커질 때 4초 들이쉬고, 작아질 때 6초 길게 내쉬면 빨라진 심박과 긴장이 빠르게 진정됩니다.\n\n"
                + "8. 원탭 자동 코스 4종 (알아서 다음 단계 전환)\n"
                + "• 웨이트 풀코스 (65분): 워밍업 5분 → 힘 깨우기 5분 → 고중량 본운동 35분 → 심박 진정 10분 → 근육 회복 10분\n"
                + "• 근비대·유산소 (55분): 워밍업 5분 → 반복세트 몰입 35분 → 근육 쿨다운 15분\n"
                + "• 깊은 집중 (60분): 집중 예열 5분 → 40Hz 깊은 몰입 45분 → 두뇌 식히기 10분\n"
                + "• 꿀잠 숙면 (60분): 4-6 호흡 10분 → 20분 입면 하강 → 심야 소음 방패 30분 후 부드럽게 꺼짐\n\n"
                + "9. 다른 프로그램·음악과 같이 듣는 법\n"
                + "• 보르타는 Windows 오디오를 독점하지 않습니다. 유튜브·음악·개발 툴 소리는 그대로 두고 보르타 볼륨만 20~40%로 맞춰 사용하세요.";
        }
    }
}
