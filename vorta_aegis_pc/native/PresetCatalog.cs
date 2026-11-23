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
        public int NoiseType; // 0: None, 1: Pink, 2: Ultrasonic Mosquito, 3: Dragonfly Flutter, 4: Brown, 5: Tinnitus Soft Mask
        public bool FmMod;
        public float FmDepth;
        public float FmRate;
        public bool AmMod;
        public float AmDepth;
        public float AmRate;

        // Pack provenance metadata
        public string PackId = "core";
        public string PackName = "보르타 코어";
        public string PackVersion = "2.3.0";
        public bool IsCore = true;

        public string BuildSpecLine()
        {
            StringBuilder sb = new StringBuilder();
            if (!IsSweep && Freq1 <= 0f)
            {
                if (NoiseType == 4)
                {
                    sb.Append("브라운 노이즈 1/f² · 순수 스테레오 차폐");
                }
                else if (NoiseType == 5)
                {
                    sb.Append("소프트 대역 마스킹 · 2.8kHz 롤오프");
                }
                else
                {
                    sb.Append("스테레오 노이즈 마스킹");
                }
            }
            else if (IsSweep)
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
            if ((IsSweep || Freq1 > 0f) && NoiseType == 1)
            {
                sb.Append(" · 핑크 노이즈");
            }
            else if ((IsSweep || Freq1 > 0f) && NoiseType == 4)
            {
                sb.Append(" · 브라운 노이즈");
            }
            else if ((IsSweep || Freq1 > 0f) && NoiseType == 5)
            {
                sb.Append(" · 대역 마스킹");
            }
            return sb.ToString();
        }
    }

    public sealed class PresetPackInfo
    {
        public string PackId;
        public string PackName;
        public string PackVersion;
        public string Author;
        public string License;
        public string SourcePath;
        public bool IsCore;
        public int PresetCount;
    }

    public sealed class CatalogLoadResult
    {
        public IList<PresetItem> Presets;
        public IList<PresetPackInfo> Packs;
        public IList<string> SkippedFiles;
        public string UserPacksDir;
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

        public static string FormatTabLabel(int tabIndex, int count)
        {
            int idx = ClampTabIndex(tabIndex);
            return TAB_SHORT_NAMES[idx] + " " + Math.Max(0, count).ToString(CultureInfo.InvariantCulture);
        }

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
            List<PresetItem> core = LoadCoreInternal(baseDir);
            sCached = core.AsReadOnly();
            return sCached;
        }

        public static CatalogLoadResult LoadAllWithPacks(string baseDir, string userPacksDir)
        {
            List<KeyValuePair<string, string>> entries = new List<KeyValuePair<string, string>>();
            List<string> candidateDirs = new List<string>();
            if (!string.IsNullOrEmpty(baseDir))
            {
                candidateDirs.Add(Path.Combine(baseDir, "packs"));
            }
            if (!string.IsNullOrEmpty(userPacksDir))
            {
                candidateDirs.Add(userPacksDir);
            }
            for (int d = 0; d < candidateDirs.Count; d++)
            {
                string dir = candidateDirs[d];
                if (!Directory.Exists(dir)) continue;
                string[] files = Directory.GetFiles(dir, "*.json");
                Array.Sort(files, StringComparer.OrdinalIgnoreCase);
                for (int f = 0; f < files.Length; f++)
                {
                    try
                    {
                        string raw = File.ReadAllText(files[f], Encoding.UTF8);
                        entries.Add(new KeyValuePair<string, string>(files[f], raw));
                    }
                    catch
                    {
                        entries.Add(new KeyValuePair<string, string>(files[f], null));
                    }
                }
            }
            CatalogLoadResult res = LoadAllWithPackPayloads(baseDir, entries);
            res.UserPacksDir = userPacksDir ?? "";
            return res;
        }

        public static CatalogLoadResult LoadAllWithPackPayloads(string baseDir, IList<KeyValuePair<string, string>> namedJsonPayloads)
        {
            List<PresetItem> all = LoadCoreInternal(baseDir);
            List<PresetPackInfo> packs = new List<PresetPackInfo>();
            List<string> skipped = new List<string>();
            HashSet<int> usedIds = new HashSet<int>();
            HashSet<string> usedPackIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            PresetPackInfo coreInfo = new PresetPackInfo();
            coreInfo.PackId = "core";
            coreInfo.PackName = "보르타 코어 48";
            coreInfo.PackVersion = "2.3.0";
            coreInfo.Author = "gyeomsVibe";
            coreInfo.License = "Proprietary Commercial";
            coreInfo.SourcePath = Path.Combine(baseDir, "web", "presets.json");
            coreInfo.IsCore = true;
            coreInfo.PresetCount = all.Count;
            packs.Add(coreInfo);
            usedPackIds.Add("core");
            for (int i = 0; i < all.Count; i++)
            {
                usedIds.Add(all[i].Id);
            }

            if (namedJsonPayloads != null)
            {
                for (int i = 0; i < namedJsonPayloads.Count; i++)
                {
                    KeyValuePair<string, string> kv = namedJsonPayloads[i];
                    TryLoadPackJson(kv.Key, kv.Value, all, packs, skipped, usedIds, usedPackIds);
                }
            }

            CatalogLoadResult res = new CatalogLoadResult();
            res.Presets = all.AsReadOnly();
            res.Packs = packs.AsReadOnly();
            res.SkippedFiles = skipped.AsReadOnly();
            res.UserPacksDir = "";
            return res;
        }

        private static List<PresetItem> LoadCoreInternal(string baseDir)
        {
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
                PresetItem item = ParsePresetDict(dict, "core", "보르타 코어 48", "2.3.0", true);
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
            return list;
        }

        private static void TryLoadPackJson(
            string path,
            string raw,
            List<PresetItem> all,
            List<PresetPackInfo> packs,
            List<string> skipped,
            HashSet<int> usedIds,
            HashSet<string> usedPackIds)
        {
            string fn = Path.GetFileName(path ?? "pack.json");
            try
            {
                if (raw == null)
                {
                    skipped.Add(fn + ": unreadable file");
                    return;
                }
                JavaScriptSerializer serializer = new JavaScriptSerializer();
                Dictionary<string, object> root = serializer.Deserialize<Dictionary<string, object>>(raw);
                if (root == null)
                {
                    skipped.Add(fn + ": empty root object");
                    return;
                }
                if (!root.ContainsKey("schemaVersion"))
                {
                    skipped.Add(fn + ": missing schemaVersion");
                    return;
                }
                int schemaVer = Convert.ToInt32(root["schemaVersion"], CultureInfo.InvariantCulture);
                if (schemaVer != 1)
                {
                    skipped.Add(fn + ": unsupported schemaVersion=" + schemaVer);
                    return;
                }
                string packId = root.ContainsKey("packId") ? Convert.ToString(root["packId"], CultureInfo.InvariantCulture) : "";
                string packName = root.ContainsKey("packName") ? Convert.ToString(root["packName"], CultureInfo.InvariantCulture) : "";
                string packVer = root.ContainsKey("packVersion") ? Convert.ToString(root["packVersion"], CultureInfo.InvariantCulture) : "1.0.0";
                string author = root.ContainsKey("author") ? Convert.ToString(root["author"], CultureInfo.InvariantCulture) : "External";
                string license = root.ContainsKey("license") ? Convert.ToString(root["license"], CultureInfo.InvariantCulture) : "Custom";
                if (string.IsNullOrEmpty(packId) || string.IsNullOrEmpty(packName) || usedPackIds.Contains(packId))
                {
                    skipped.Add(fn + ": invalid or duplicate packId");
                    return;
                }
                if (!root.ContainsKey("presets"))
                {
                    skipped.Add(fn + ": missing presets array");
                    return;
                }
                System.Collections.IList rawPresets = root["presets"] as System.Collections.IList;
                if (rawPresets == null || rawPresets.Count == 0)
                {
                    skipped.Add(fn + ": empty presets array");
                    return;
                }

                List<PresetItem> validated = new List<PresetItem>();
                HashSet<int> batchIds = new HashSet<int>();
                for (int i = 0; i < rawPresets.Count; i++)
                {
                    Dictionary<string, object> d = rawPresets[i] as Dictionary<string, object>;
                    if (d == null)
                    {
                        skipped.Add(fn + ": malformed preset entry");
                        return;
                    }
                    PresetItem item = ParsePresetDict(d, packId, packName, packVer, false);
                    if (!IsValidExtensionPreset(item) || usedIds.Contains(item.Id) || batchIds.Contains(item.Id))
                    {
                        skipped.Add(fn + ": invalid or conflicting preset ID " + item.Id);
                        return;
                    }
                    batchIds.Add(item.Id);
                    validated.Add(item);
                }

                usedPackIds.Add(packId);
                for (int i = 0; i < validated.Count; i++)
                {
                    usedIds.Add(validated[i].Id);
                    all.Add(validated[i]);
                }

                PresetPackInfo info = new PresetPackInfo();
                info.PackId = packId;
                info.PackName = packName;
                info.PackVersion = packVer;
                info.Author = author;
                info.License = license;
                info.SourcePath = path;
                info.IsCore = false;
                info.PresetCount = validated.Count;
                packs.Add(info);
            }
            catch (Exception ex)
            {
                skipped.Add(fn + ": " + ex.GetType().Name);
            }
        }

        private static bool IsValidExtensionPreset(PresetItem item)
        {
            if (item == null) return false;
            if (item.Id < 100 || item.Id > 99999) return false;
            if (string.IsNullOrEmpty(item.ShortCode) || string.IsNullOrEmpty(item.Title)) return false;
            if (item.TabIndex < 0 || item.TabIndex >= TabSyncPolicy.TAB_COUNT) return false;
            if (item.NoiseType < 0 || item.NoiseType > 5) return false;
            if (float.IsNaN(item.Freq1) || float.IsInfinity(item.Freq1) || item.Freq1 < 0f || item.Freq1 > 22000f) return false;
            if (float.IsNaN(item.Freq2) || float.IsInfinity(item.Freq2) || item.Freq2 < 0f || item.Freq2 > 22000f) return false;
            if (float.IsNaN(item.SweepStart) || float.IsInfinity(item.SweepStart) || item.SweepStart < 0f || item.SweepStart > 22000f) return false;
            if (float.IsNaN(item.SweepEnd) || float.IsInfinity(item.SweepEnd) || item.SweepEnd < 0f || item.SweepEnd > 22000f) return false;
            if (float.IsNaN(item.SweepDurationSec) || float.IsInfinity(item.SweepDurationSec) || item.SweepDurationSec < 0f) return false;
            if (float.IsNaN(item.BinauralBeat) || float.IsInfinity(item.BinauralBeat) || item.BinauralBeat < 0f || item.BinauralBeat > 500f) return false;
            if (float.IsNaN(item.FmDepth) || item.FmDepth < 0f || item.FmDepth > 0.5f) return false;
            if (float.IsNaN(item.FmRate) || item.FmRate < 0f || item.FmRate > 100f) return false;
            if (float.IsNaN(item.AmDepth) || item.AmDepth < 0f || item.AmDepth > 0.95f) return false;
            if (float.IsNaN(item.AmRate) || item.AmRate < 0f || item.AmRate > 100f) return false;
            return true;
        }

        private static PresetItem ParsePresetDict(Dictionary<string, object> dict, string packId, string packName, string packVer, bool isCore)
        {
            PresetItem item = new PresetItem();
            item.Id = Convert.ToInt32(dict["id"], CultureInfo.InvariantCulture);
            item.ShortCode = Convert.ToString(dict["shortCode"], CultureInfo.InvariantCulture);
            item.TabIndex = Convert.ToInt32(dict["tabIndex"], CultureInfo.InvariantCulture);
            item.Title = Convert.ToString(dict["title"], CultureInfo.InvariantCulture);
            item.Badge = dict.ContainsKey("badge") ? Convert.ToString(dict["badge"], CultureInfo.InvariantCulture) : packName;
            item.Desc = dict.ContainsKey("desc") ? Convert.ToString(dict["desc"], CultureInfo.InvariantCulture) : "";
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
            item.PackId = packId;
            item.PackName = packName;
            item.PackVersion = packVer;
            item.IsCore = isCore;
            return item;
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
