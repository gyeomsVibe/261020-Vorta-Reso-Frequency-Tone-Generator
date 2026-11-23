using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace VortaAegisPC
{
    public static class AppProductInfo
    {
        public const string APP_NAME_KO = "보르타 이지스";
        public const string APP_NAME_EN = "Vorta Aegis Pro PC";
        public const string APP_VERSION = "3.2.0";
        public const string VERSION_BADGE = "v3.2 PC";
        public const string BUILD_DATE = "2026-11-23";
        public const string COPYRIGHT_LINE = "Copyright (c) 2026 gyeomsVibe (김윤겸). All Rights Reserved.";
        public const string LICENSE_TIER = "Proprietary Commercial / Evaluation EULA (v1.0)";
        public const string CODE_SIGN_STATUS = "로컬 빌드 (미서명) · 정식 배포 시 MS Store MSIX / Authenticode 서명 적용 권장";
        public const string CONTACT_EMAIL = "yoongyeomkim@naver.com";
        public const string GITHUB_REPO_URL = "https://github.com/gyeomsVibe/261020-Vorta-Reso--Frequency-Tone-Generator-";
        public const string VERSION_MANIFEST_URL = "https://raw.githubusercontent.com/gyeomsVibe/261020-Vorta-Reso--Frequency-Tone-Generator-/main/vorta_aegis_pc/version.json";
        public const string RELEASES_API_URL = "https://api.github.com/repos/gyeomsVibe/261020-Vorta-Reso--Frequency-Tone-Generator-/releases/latest";

        public const string BRAND_MEANING_VORTA =
            "Vorta (보르타): Vortex(파동 에너지) + Volta(정밀 주파수 전위)의 합성어로, 흩어진 환경음과 작업 리듬을 정밀한 음향 파동으로 정렬하는 독자 브랜드입니다.";
        public const string BRAND_MEANING_AEGIS =
            "Aegis (이지스): 수호 방패를 뜻하며, 동영상·인터넷·개발·AI 툴 같은 메인 작업과 충돌 없이 곁에서 집중과 휴식을 지키는 초경량 보조 음향 방패입니다.";
    }

    public sealed class UpdateCheckResult
    {
        public bool Success;
        public bool HasUpdate;
        public string CurrentVersion = AppProductInfo.APP_VERSION;
        public string LatestVersion = AppProductInfo.APP_VERSION;
        public string ReleaseDate = AppProductInfo.BUILD_DATE;
        public string Summary = "";
        public string DownloadUrl = AppProductInfo.GITHUB_REPO_URL;
        public string ErrorMessage = "";
    }

    public static class UpdateChecker
    {
        public static int CompareSemVer(string a, string b)
        {
            int[] pa;
            string preA;
            ParseSemVer(a, out pa, out preA);
            int[] pb;
            string preB;
            ParseSemVer(b, out pb, out preB);
            for (int i = 0; i < 3; i++)
            {
                if (pa[i] < pb[i]) return -1;
                if (pa[i] > pb[i]) return 1;
            }
            bool hasPreA = !string.IsNullOrEmpty(preA);
            bool hasPreB = !string.IsNullOrEmpty(preB);
            if (!hasPreA && !hasPreB) return 0;
            if (!hasPreA && hasPreB) return 1;  // 1.0.0 > 1.0.0-rc1
            if (hasPreA && !hasPreB) return -1; // 1.0.0-rc1 < 1.0.0
            return ComparePreRelease(preA, preB);
        }

        private static bool IsAsciiDigitsOnly(string s)
        {
            if (string.IsNullOrEmpty(s)) return false;
            for (int i = 0; i < s.Length; i++)
            {
                char c = s[i];
                if (c < '0' || c > '9') return false;
            }
            return true;
        }

        private static int CompareAsciiUnsignedInt(string a, string b)
        {
            string sa = (a ?? "").TrimStart('0');
            string sb = (b ?? "").TrimStart('0');
            if (sa.Length < sb.Length) return -1;
            if (sa.Length > sb.Length) return 1;
            return string.CompareOrdinal(sa, sb);
        }

        private static int ComparePreRelease(string preA, string preB)
        {
            string[] idsA = preA.Split('.');
            string[] idsB = preB.Split('.');
            int max = Math.Max(idsA.Length, idsB.Length);
            for (int i = 0; i < max; i++)
            {
                if (i >= idsA.Length) return -1;
                if (i >= idsB.Length) return 1;
                bool isNumA = IsAsciiDigitsOnly(idsA[i]);
                bool isNumB = IsAsciiDigitsOnly(idsB[i]);
                if (isNumA && isNumB)
                {
                    int c = CompareAsciiUnsignedInt(idsA[i], idsB[i]);
                    if (c != 0) return c;
                }
                else if (isNumA != isNumB)
                {
                    return isNumA ? -1 : 1; // SemVer 2.0 Sec 11: Numeric identifiers always have lower precedence than non-numeric
                }
                else
                {
                    int cmp = string.CompareOrdinal(idsA[i], idsB[i]);
                    if (cmp < 0) return -1;
                    if (cmp > 0) return 1;
                }
            }
            return 0;
        }

        private static void ParseSemVer(string v, out int[] parts, out string preRelease)
        {
            parts = new int[] { 0, 0, 0 };
            preRelease = "";
            if (string.IsNullOrEmpty(v)) return;
            string clean = v.Trim();
            if (clean.StartsWith("v", StringComparison.OrdinalIgnoreCase))
            {
                clean = clean.Substring(1).Trim();
            }
            int plus = clean.IndexOf('+');
            if (plus >= 0) clean = clean.Substring(0, plus);
            int dash = clean.IndexOf('-');
            if (dash >= 0)
            {
                preRelease = clean.Substring(dash + 1).Trim();
                clean = clean.Substring(0, dash);
            }
            string[] tokens = clean.Split('.');
            for (int i = 0; i < 3 && i < tokens.Length; i++)
            {
                int val;
                if (int.TryParse(tokens[i].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out val))
                {
                    parts[i] = val;
                }
            }
        }

        public static void CheckNowAsync(Action<UpdateCheckResult> callback)
        {
            Thread t = new Thread(delegate()
            {
                UpdateCheckResult res = CheckBlocking();
                if (callback != null)
                {
                    try { callback(res); } catch { }
                }
            });
            t.IsBackground = true;
            t.Start();
        }

        private static string HttpGetText(string url)
        {
            bool tlsConfigured = false;
            try
            {
                // Enforce TLS 1.2 + TLS 1.3 only (no TLS 1.0 / 1.1)
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | (SecurityProtocolType)12288;
                tlsConfigured = true;
            }
            catch
            {
                try
                {
                    ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                    tlsConfigured = true;
                }
                catch
                {
                    tlsConfigured = false;
                }
            }
            if (!tlsConfigured)
            {
                throw new NotSupportedException("TLS 1.2+ protocol is required for update checks.");
            }
            HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = "GET";
            req.Timeout = 5000;
            req.ReadWriteTimeout = 5000;
            req.UserAgent = "VortaAegisPC/" + AppProductInfo.APP_VERSION;
            req.Accept = "application/json";
            using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
            using (Stream st = resp.GetResponseStream())
            using (StreamReader sr = new StreamReader(st, Encoding.UTF8))
            {
                return sr.ReadToEnd();
            }
        }

        public static UpdateCheckResult CheckBlocking()
        {
            UpdateCheckResult result = new UpdateCheckResult();
            JavaScriptSerializer ser = new JavaScriptSerializer();

            try
            {
                string raw = HttpGetText(AppProductInfo.VERSION_MANIFEST_URL);
                Dictionary<string, object> dict = ser.Deserialize<Dictionary<string, object>>(raw);
                if (dict != null && dict.ContainsKey("version"))
                {
                    string latest = Convert.ToString(dict["version"], CultureInfo.InvariantCulture) ?? AppProductInfo.APP_VERSION;
                    result.Success = true;
                    result.LatestVersion = latest;
                    result.HasUpdate = CompareSemVer(AppProductInfo.APP_VERSION, latest) < 0;
                    result.ReleaseDate = dict.ContainsKey("releaseDate")
                        ? Convert.ToString(dict["releaseDate"], CultureInfo.InvariantCulture)
                        : AppProductInfo.BUILD_DATE;
                    result.Summary = dict.ContainsKey("notes")
                        ? Convert.ToString(dict["notes"], CultureInfo.InvariantCulture)
                        : "";
                    result.DownloadUrl = dict.ContainsKey("downloadUrl")
                        ? Convert.ToString(dict["downloadUrl"], CultureInfo.InvariantCulture)
                        : AppProductInfo.GITHUB_REPO_URL;
                    return result;
                }
            }
            catch
            {
                // Fall back to GitHub Releases API
            }

            try
            {
                string rawRel = HttpGetText(AppProductInfo.RELEASES_API_URL);
                Dictionary<string, object> rel = ser.Deserialize<Dictionary<string, object>>(rawRel);
                if (rel != null && rel.ContainsKey("tag_name"))
                {
                    string tag = Convert.ToString(rel["tag_name"], CultureInfo.InvariantCulture) ?? AppProductInfo.APP_VERSION;
                    result.Success = true;
                    result.LatestVersion = tag.TrimStart('v', 'V');
                    result.HasUpdate = CompareSemVer(AppProductInfo.APP_VERSION, result.LatestVersion) < 0;
                    result.Summary = rel.ContainsKey("name")
                        ? Convert.ToString(rel["name"], CultureInfo.InvariantCulture)
                        : "";
                    result.DownloadUrl = rel.ContainsKey("html_url")
                        ? Convert.ToString(rel["html_url"], CultureInfo.InvariantCulture)
                        : AppProductInfo.GITHUB_REPO_URL;
                    return result;
                }
            }
            catch (Exception ex)
            {
                result.Success = false;
                result.ErrorMessage = "업데이트 서버 연결 확인 실패 (" + ex.Message + ")";
                return result;
            }

            result.Success = false;
            result.ErrorMessage = "원격 버전 매니페스트를 읽지 못했습니다. GitHub 저장소에서 직접 확인할 수 있습니다.";
            return result;
        }
    }
}
