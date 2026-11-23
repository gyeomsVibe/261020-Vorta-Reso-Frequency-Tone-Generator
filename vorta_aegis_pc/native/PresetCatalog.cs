using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace VortaAegisPC
{
    public enum PcOutputProfile
    {
        PcSpeaker = 0,
        Headphone = 1,
        PureRaw = 2
    }

    public sealed class PresetItem
    {
        public int Id;
        public string ShortCode;
        public int TabIndex;
        public string Title;
        public string Badge;
        public string Desc;
        public float Freq1;
        public float Freq2;
        public bool IsSweep;
        public float SweepStart;
        public float SweepEnd;
        public float SweepDurationSec;
        public bool IsAntiPhase;
        public float BinauralBeat;
        public int NoiseType; // 0: None, 1: Pink, 2: Ultrasonic Mosquito, 3: Dragonfly Flutter
        public bool FmMod;
        public float FmDepth;
        public float FmRate;
        public bool AmMod;
        public float AmDepth;
        public float AmRate;

        public string BuildSpecLine()
        {
            StringBuilder sb = new StringBuilder();
            if (IsSweep)
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture,
                    "스윕 {0:F1}~{1:F1} Hz · {2:F0}s 왕복", SweepStart, SweepEnd, SweepDurationSec));
            }
            else if (BinauralBeat > 0f)
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture,
                    "바이노럴 {0:F1} + {1:F1} Hz (차 {2:F2} Hz)", Freq1, Freq2, BinauralBeat));
            }
            else if (Freq2 > 0f)
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture,
                    "듀얼 {0:F2} + {1:F2} Hz", Freq1, Freq2));
            }
            else
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture,
                    "단일 {0:F2} Hz", Freq1));
            }

            if (IsAntiPhase)
            {
                sb.Append(" · 180° 상쇄");
            }
            if (FmMod)
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture, " · FM ±{0:F0}%", FmDepth * 100.0f));
            }
            if (AmMod)
            {
                sb.Append(string.Format(CultureInfo.InvariantCulture, " · AM {0:F0}%", AmDepth * 100.0f));
            }
            return sb.ToString();
        }
    }

    public static class VolumeCurve
    {
        public const double MIN_DB = -48.0;
        public const int DEFAULT_PERCENT = 30;
        public const int UNITY_PERCENT = 100;
        public const int MAX_PERCENT = 120;
        public const double MAX_BOOST_LINEAR = 1.20;

        public static int ClampPercent(int percent)
        {
            if (percent < 0) return 0;
            if (percent > MAX_PERCENT) return MAX_PERCENT;
            return percent;
        }

        public static double PercentToDb(int percent)
        {
            int clamped = ClampPercent(percent);
            if (clamped <= 0) return double.NegativeInfinity;
            if (clamped == UNITY_PERCENT) return 0.0d;
            if (clamped > UNITY_PERCENT)
            {
                return 20.0d * Math.Log10(clamped / 100.0d);
            }
            double db = MIN_DB * (1.0d - (clamped / 100.0d));
            return Math.Abs(db) < 0.0005d ? 0.0d : db;
        }

        public static double PercentToLinearGain(int percent)
        {
            int clamped = ClampPercent(percent);
            if (clamped <= 0) return 0.0d;
            if (clamped == UNITY_PERCENT) return 1.0d;
            if (clamped > UNITY_PERCENT) return clamped / 100.0d;
            return Math.Pow(10.0d, PercentToDb(clamped) / 20.0d);
        }

        public static string FormatDb(int percent, bool isMuted)
        {
            int clamped = ClampPercent(percent);
            if (isMuted || clamped <= 0)
            {
                return "-\u221e dB";
            }
            double db = PercentToDb(clamped);
            if (clamped > UNITY_PERCENT)
            {
                return string.Format(CultureInfo.InvariantCulture, "+{0:F1} dB", db);
            }
            if (clamped == UNITY_PERCENT || Math.Abs(db) < 0.05d)
            {
                return "0.0 dB";
            }
            return string.Format(CultureInfo.InvariantCulture, "{0:F1} dB", db);
        }
    }

    public static class TabSyncPolicy
    {
        public const int TAB_COUNT = 8;

        public static readonly string[] TAB_NAMES = new string[]
        {
            "안티방어 12",
            "수면 6",
            "집중·공부 6",
            "명상·안정 5",
            "두통·통증 5",
            "운동·피로 5",
            "기분·힐링 5",
            "모기·벌레 4"
        };

        public static readonly string[] TAB_SHORT_NAMES = new string[]
        {
            "안티방어",
            "수면",
            "집중·공부",
            "명상·안정",
            "두통·통증",
            "운동·피로",
            "기분·힐링",
            "모기·벌레"
        };

        public static int ClampTabIndex(int tabIndex)
        {
            return (tabIndex >= 0 && tabIndex < TAB_COUNT) ? tabIndex : 0;
        }

        public static int ResolveTabOnEntry(
            bool isPlaying,
            int playingTabIndex,
            int persistedTabIndex,
            int currentTabIndex,
            bool shouldAutoSync)
        {
            if (shouldAutoSync)
            {
                if (isPlaying && playingTabIndex >= 0 && playingTabIndex < TAB_COUNT)
                {
                    return playingTabIndex;
                }
                return ClampTabIndex(persistedTabIndex);
            }
            return ClampTabIndex(currentTabIndex);
        }

        public static bool IsViewingDifferentTab(bool isPlaying, int playingTabIndex, int currentTabIndex)
        {
            return isPlaying
                && playingTabIndex >= 0
                && playingTabIndex < TAB_COUNT
                && playingTabIndex != currentTabIndex;
        }
    }

    public static class PresetCatalog
    {
        private static readonly int[] EXPECTED_COUNTS = new int[] { 12, 6, 6, 5, 5, 5, 5, 4 };
        private static IList<PresetItem> sCached;

        public static IList<PresetItem> LoadAll(string baseDir)
        {
            if (sCached != null) return sCached;

            string jsonPath = Path.Combine(baseDir, "web", "presets.json");
            if (!File.Exists(jsonPath))
            {
                throw new FileNotFoundException("presets.json not found", jsonPath);
            }

            string rawJson = File.ReadAllText(jsonPath, Encoding.UTF8);
            JavaScriptSerializer serializer = new JavaScriptSerializer();
            object[] rawItems = serializer.Deserialize<object[]>(rawJson);
            if (rawItems == null || rawItems.Length != 48)
            {
                throw new InvalidDataException("Expected 48 presets in presets.json");
            }

            List<PresetItem> list = new List<PresetItem>(48);
            int[] counts = new int[TabSyncPolicy.TAB_COUNT];
            foreach (object obj in rawItems)
            {
                Dictionary<string, object> dict = obj as Dictionary<string, object>;
                if (dict == null) continue;
                PresetItem item = new PresetItem();
                item.Id = Convert.ToInt32(dict["id"], CultureInfo.InvariantCulture);
                item.ShortCode = Convert.ToString(dict["shortCode"], CultureInfo.InvariantCulture);
                item.TabIndex = Convert.ToInt32(dict["tabIndex"], CultureInfo.InvariantCulture);
                item.Title = Convert.ToString(dict["title"], CultureInfo.InvariantCulture);
                item.Badge = Convert.ToString(dict["badge"], CultureInfo.InvariantCulture);
                item.Desc = Convert.ToString(dict["desc"], CultureInfo.InvariantCulture);
                item.Freq1 = Convert.ToSingle(dict["freq1"], CultureInfo.InvariantCulture);
                item.Freq2 = Convert.ToSingle(dict["freq2"], CultureInfo.InvariantCulture);
                item.IsSweep = Convert.ToBoolean(dict["isSweep"], CultureInfo.InvariantCulture);
                item.SweepStart = Convert.ToSingle(dict["sweepStart"], CultureInfo.InvariantCulture);
                item.SweepEnd = Convert.ToSingle(dict["sweepEnd"], CultureInfo.InvariantCulture);
                item.SweepDurationSec = Convert.ToSingle(dict["sweepDurationSec"], CultureInfo.InvariantCulture);
                item.IsAntiPhase = Convert.ToBoolean(dict["isAntiPhase"], CultureInfo.InvariantCulture);
                item.BinauralBeat = Convert.ToSingle(dict["binauralBeat"], CultureInfo.InvariantCulture);
                item.NoiseType = Convert.ToInt32(dict["noiseType"], CultureInfo.InvariantCulture);
                item.FmMod = Convert.ToBoolean(dict["fmMod"], CultureInfo.InvariantCulture);
                item.FmDepth = Convert.ToSingle(dict["fmDepth"], CultureInfo.InvariantCulture);
                item.FmRate = Convert.ToSingle(dict["fmRate"], CultureInfo.InvariantCulture);
                item.AmMod = Convert.ToBoolean(dict["amMod"], CultureInfo.InvariantCulture);
                item.AmDepth = Convert.ToSingle(dict["amDepth"], CultureInfo.InvariantCulture);
                item.AmRate = Convert.ToSingle(dict["amRate"], CultureInfo.InvariantCulture);

                if (item.TabIndex < 0 || item.TabIndex >= TabSyncPolicy.TAB_COUNT)
                {
                    throw new InvalidDataException("Invalid tabIndex for preset " + item.Id);
                }
                counts[item.TabIndex]++;
                list.Add(item);
            }

            for (int t = 0; t < TabSyncPolicy.TAB_COUNT; t++)
            {
                if (counts[t] != EXPECTED_COUNTS[t])
                {
                    throw new InvalidDataException("Tab count mismatch on tab " + t);
                }
            }

            sCached = list.AsReadOnly();
            return sCached;
        }

        public static PresetItem FindById(IList<PresetItem> all, int id)
        {
            if (all == null || all.Count == 0) return null;
            for (int i = 0; i < all.Count; i++)
            {
                if (all[i].Id == id) return all[i];
            }
            return all[0];
        }
    }
}
