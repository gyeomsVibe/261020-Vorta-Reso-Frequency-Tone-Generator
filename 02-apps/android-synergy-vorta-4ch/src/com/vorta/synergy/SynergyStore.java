package com.vorta.synergy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Zombie-proof persistent JSON store with synchronous commit() boolean feedback
 * and 4-channel fleet config export/import.
 */
public final class SynergyStore {
    private static final String PREF_NAME = "vorta_synergy_store_v10";
    private static final String KEY_ACTIVE_CHANNEL = "active_channel_v1";
    private static final String KEY_ACTIVE_PKG_CODE = "active_pkg_code_v1";
    private static final String KEY_AUTO_SCHED_24H = "auto_schedule_24h_v1";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on_v1";
    private static final String KEY_JITTER_ENABLED = "jitter_enabled_v1";
    private static final String KEY_DUTY_MODE = "duty_mode_v1"; // 0=ROLE_DEFAULT, 1=CONTINUOUS, 2=20/10, 3=15/15
    private static final String KEY_TRIM_DB = "trim_db_v1";
    private static final String KEY_STABILIZER_ON = "stabilizer_enabled_v1";
    private static final String KEY_NOTCH_CENTER_PREFIX = "notch_center_";
    private static final String KEY_NOTCH_WIDTH_PREFIX = "notch_width_";
    private static final String KEY_FM_ON = "mod_fm_on_v1";
    private static final String KEY_AM_ON = "mod_am_on_v1";
    private static final String KEY_RM_ON = "mod_rm_on_v1";
    private static final String KEY_INITIALIZED = "steps_initialized_v1";
    private static final String KEY_STEPS_JSON = "steps_json_v1";
    private static final String KEY_SETS_INDEX = "named_sets_index_v1";
    private static final String KEY_SET_PREFIX = "named_set_data_";

    private SynergyStore() {}

    private static SharedPreferences getPrefs(Context context) {
        Context app = context.getApplicationContext();
        Context base = (app != null) ? app : context;
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Context de = base.createDeviceProtectedStorageContext();
                if (de != null) base = de;
            } catch (Throwable ignored) {}
        }
        return base.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void seedStarterSteps(SynergyChannel ch, List<ScenarioStep> out) {
        out.clear();
        SynergyChannel c = (ch != null) ? ch : SynergyChannel.CH1_SUB_LOW;
        float lo = c.sweetMinHz;
        float hi = c.sweetMaxHz;
        float mid = Math.round(((lo + hi) * 0.5f) * 10.0f) / 10.0f;
        float beat2 = Math.round((mid + 4.0f) * 10.0f) / 10.0f;
        out.add(new ScenarioStep(1, "핵심 대역 스윕", ScenarioStep.StepType.SWEEP,
                lo, hi, 0.0f, 240.0f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, true, 0.0f));
        out.add(new ScenarioStep(2, "4Hz 이중 맥놀이", ScenarioStep.StepType.SINGLE,
                mid, mid, beat2, 300.0f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, 0.0f));
        out.add(new ScenarioStep(3, "단일 피크 강타", ScenarioStep.StepType.SINGLE,
                hi, hi, 0.0f, 300.0f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, 0.0f));
    }

    public static boolean loadFromState(boolean initialized, String rawJson, SynergyChannel ch, List<ScenarioStep> out) {
        if (out == null) return false;
        out.clear();
        if (initialized) {
            out.addAll(deserializeSteps(rawJson));
            return false;
        }
        seedStarterSteps(ch, out);
        return true;
    }

    public static SynergyChannel loadActiveChannel(Context ctx, SynergyChannel fallback) {
        if (ctx == null) return fallback;
        return SynergyChannel.fromName(getPrefs(ctx).getString(KEY_ACTIVE_CHANNEL, null), fallback);
    }

    public static boolean saveActiveChannel(Context ctx, SynergyChannel ch) {
        if (ctx == null || ch == null) return false;
        return getPrefs(ctx).edit().putString(KEY_ACTIVE_CHANNEL, ch.name()).commit();
    }

    public static String loadActivePackageCode(Context ctx) {
        if (ctx == null) return "S-01";
        return getPrefs(ctx).getString(KEY_ACTIVE_PKG_CODE, "S-01");
    }

    public static void saveActivePackageCode(Context ctx, String code) {
        if (ctx == null || code == null) return;
        getPrefs(ctx).edit().putString(KEY_ACTIVE_PKG_CODE, code).apply();
    }

    public static boolean loadAutoSchedule24h(Context ctx) {
        return ctx != null && getPrefs(ctx).getBoolean(KEY_AUTO_SCHED_24H, false);
    }

    public static void saveAutoSchedule24h(Context ctx, boolean on) {
        if (ctx != null) getPrefs(ctx).edit().putBoolean(KEY_AUTO_SCHED_24H, on).apply();
    }

    public static boolean loadKeepScreenOn(Context ctx) {
        return ctx != null && getPrefs(ctx).getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public static void saveKeepScreenOn(Context ctx, boolean on) {
        if (ctx != null) getPrefs(ctx).edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply();
    }

    public static boolean loadJitterEnabled(Context ctx) {
        return ctx != null && getPrefs(ctx).getBoolean(KEY_JITTER_ENABLED, true);
    }

    public static void saveJitterEnabled(Context ctx, boolean on) {
        if (ctx != null) getPrefs(ctx).edit().putBoolean(KEY_JITTER_ENABLED, on).apply();
    }

    public static int loadDutyMode(Context ctx) {
        return ctx == null ? 1 : getPrefs(ctx).getInt(KEY_DUTY_MODE, 1);
    }

    public static void saveDutyMode(Context ctx, int mode) {
        if (ctx != null) getPrefs(ctx).edit().putInt(KEY_DUTY_MODE, mode).apply();
    }

    public static float loadTrimDb(Context ctx) {
        return ctx == null ? 0.0f : getPrefs(ctx).getFloat(KEY_TRIM_DB, 0.0f);
    }

    public static void saveTrimDb(Context ctx, float trimDb) {
        if (ctx != null) getPrefs(ctx).edit().putFloat(KEY_TRIM_DB, trimDb).apply();
    }

    public static boolean loadStabilizerEnabled(Context ctx) {
        return ctx != null && getPrefs(ctx).getBoolean(KEY_STABILIZER_ON, false);
    }

    public static boolean saveStabilizerEnabled(Context ctx, boolean on) {
        return ctx != null && getPrefs(ctx).edit().putBoolean(KEY_STABILIZER_ON, on).commit();
    }

    public static float loadNotchCenterHz(Context ctx, SynergyChannel ch) {
        if (ctx == null || ch == null) return 0.0f;
        return getPrefs(ctx).getFloat(KEY_NOTCH_CENTER_PREFIX + ch.name(), 0.0f);
    }

    public static float loadNotchWidthHz(Context ctx, SynergyChannel ch) {
        if (ctx == null || ch == null) return 1.5f;
        return getPrefs(ctx).getFloat(KEY_NOTCH_WIDTH_PREFIX + ch.name(), 1.5f);
    }

    public static boolean saveNotchFilter(Context ctx, SynergyChannel ch, float centerHz, float halfWidthHz) {
        if (ctx == null || ch == null) return false;
        return getPrefs(ctx).edit()
                .putFloat(KEY_NOTCH_CENTER_PREFIX + ch.name(), Math.max(0.0f, centerHz))
                .putFloat(KEY_NOTCH_WIDTH_PREFIX + ch.name(), Math.max(0.5f, halfWidthHz))
                .commit();
    }

    public static void saveModulationToggles(Context ctx, boolean fmOn, boolean amOn, boolean rmOn) {
        if (ctx == null) return;
        getPrefs(ctx).edit()
                .putBoolean(KEY_FM_ON, fmOn)
                .putBoolean(KEY_AM_ON, amOn)
                .putBoolean(KEY_RM_ON, rmOn)
                .apply();
    }

    public static boolean[] loadModulationToggles(Context ctx) {
        if (ctx == null) return new boolean[]{false, false, false};
        SharedPreferences sp = getPrefs(ctx);
        return new boolean[]{
                sp.getBoolean(KEY_FM_ON, false),
                sp.getBoolean(KEY_AM_ON, false),
                sp.getBoolean(KEY_RM_ON, false)
        };
    }

    public static int loadStepsInto(Context ctx, SynergyChannel ch, List<ScenarioStep> out) {
        if (ctx == null || out == null) return 1;
        SharedPreferences sp = getPrefs(ctx);
        boolean initialized = sp.getBoolean(KEY_INITIALIZED, false);
        String raw = sp.getString(KEY_STEPS_JSON, null);
        if (loadFromState(initialized, raw, ch, out)) {
            saveStepsNow(ctx, out);
        }
        int maxId = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).id > maxId) maxId = out.get(i).id;
        }
        return maxId + 1;
    }

    public static boolean saveStepsNow(Context ctx, List<ScenarioStep> steps) {
        if (ctx == null || steps == null) return false;
        return getPrefs(ctx).edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putString(KEY_STEPS_JSON, serializeSteps(steps))
                .commit();
    }

    public static int resetToStarterDefaults(Context ctx, SynergyChannel ch, List<ScenarioStep> steps) {
        if (steps == null) return 1;
        seedStarterSteps(ch, steps);
        if (ctx != null) saveStepsNow(ctx, steps);
        return steps.size() + 1;
    }

    public static List<String> listNamedSets(Context ctx) {
        List<String> names = new ArrayList<String>();
        if (ctx == null) return names;
        String raw = getPrefs(ctx).getString(KEY_SETS_INDEX, "");
        if (raw == null || raw.isEmpty()) return names;
        for (String line : raw.split("\\n")) {
            String n = line.trim();
            if (!n.isEmpty() && !names.contains(n)) names.add(n);
        }
        return names;
    }

    public static boolean saveNamedSet(Context ctx, String name, List<ScenarioStep> steps) {
        if (ctx == null || name == null || steps == null) return false;
        String clean = name.replace('\n', ' ').trim();
        if (clean.isEmpty()) return false;
        List<String> names = listNamedSets(ctx);
        if (!names.contains(clean)) names.add(clean);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(names.get(i));
        }
        return getPrefs(ctx).edit()
                .putString(KEY_SETS_INDEX, sb.toString())
                .putString(KEY_SET_PREFIX + clean, serializeSteps(steps))
                .commit();
    }

    public static boolean loadNamedSet(Context ctx, String name, List<ScenarioStep> out) {
        if (ctx == null || name == null || out == null) return false;
        String raw = getPrefs(ctx).getString(KEY_SET_PREFIX + name, null);
        if (raw == null) return false;
        out.clear();
        out.addAll(deserializeSteps(raw));
        return saveStepsNow(ctx, out);
    }

    public static boolean deleteNamedSet(Context ctx, String name) {
        if (ctx == null || name == null) return false;
        List<String> names = listNamedSets(ctx);
        names.remove(name);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(names.get(i));
        }
        return getPrefs(ctx).edit()
                .putString(KEY_SETS_INDEX, sb.toString())
                .remove(KEY_SET_PREFIX + name)
                .commit();
    }

    public static String exportFleetJson(
            SynergyChannel ch, String pkgCode, boolean auto24h,
            boolean jitter, int dutyMode, float trimDb, List<ScenarioStep> steps) {
        return exportFleetJson(ch, pkgCode, auto24h, jitter, dutyMode, trimDb, true, 0.0f, 1.5f, steps);
    }

    public static String exportFleetJson(
            SynergyChannel ch, String pkgCode, boolean auto24h,
            boolean jitter, int dutyMode, float trimDb,
            boolean stabilizerOn, float notchCenterHz, float notchHalfWidthHz,
            List<ScenarioStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"schema\":\"vorta_synergy_fleet_v1\"");
        sb.append(",\"channel\":\"").append(ch != null ? ch.name() : SynergyChannel.CH1_SUB_LOW.name()).append('"');
        sb.append(",\"packageCode\":\"").append(escapeJson(pkgCode != null ? pkgCode : "S-01")).append('"');
        sb.append(",\"auto24h\":").append(auto24h);
        sb.append(",\"jitter\":").append(jitter);
        sb.append(",\"dutyMode\":").append(dutyMode);
        sb.append(",\"trimDb\":").append(trimDb);
        sb.append(",\"stabilizerOn\":").append(stabilizerOn);
        sb.append(",\"notchCenterHz\":").append(notchCenterHz);
        sb.append(",\"notchHalfWidthHz\":").append(notchHalfWidthHz);
        sb.append(",\"steps\":").append(serializeSteps(steps));
        sb.append('}');
        return sb.toString();
    }

    public static final class FleetImportResult {
        public SynergyChannel channel = SynergyChannel.CH1_SUB_LOW;
        public String packageCode = "S-01";
        public boolean auto24h = false;
        public boolean jitter = true;
        public int dutyMode = 0;
        public float trimDb = 0.0f;
        public boolean stabilizerOn = true;
        public float notchCenterHz = 0.0f;
        public float notchHalfWidthHz = 1.5f;
        public List<ScenarioStep> steps = new ArrayList<ScenarioStep>();
        public boolean valid = false;
    }

    public static FleetImportResult importFleetJson(String json) {
        FleetImportResult r = new FleetImportResult();
        if (json == null) return r;
        String t = json.trim();
        if (!t.startsWith("{") || !t.contains("\"vorta_synergy_fleet_v1\"")) return r;
        String chStr = extractJsonString(t, "channel");
        if (chStr != null) r.channel = SynergyChannel.fromName(chStr, SynergyChannel.CH1_SUB_LOW);
        String pkgStr = extractJsonString(t, "packageCode");
        if (pkgStr != null && SynergyCatalog.findPackage(pkgStr) != null) r.packageCode = pkgStr;
        r.auto24h = extractJsonBoolean(t, "auto24h", false);
        r.jitter = extractJsonBoolean(t, "jitter", true);
        r.dutyMode = (int) extractJsonFloat(t, "dutyMode", 0f);
        r.trimDb = extractJsonFloat(t, "trimDb", 0f);
        r.stabilizerOn = extractJsonBoolean(t, "stabilizerOn", true);
        r.notchCenterHz = extractJsonFloat(t, "notchCenterHz", 0f);
        r.notchHalfWidthHz = Math.max(0.5f, extractJsonFloat(t, "notchHalfWidthHz", 1.5f));
        int stepsIdx = t.indexOf("\"steps\"");
        if (stepsIdx >= 0) {
            int arrStart = t.indexOf('[', stepsIdx);
            if (arrStart >= 0) {
                r.steps = deserializeSteps(t.substring(arrStart));
            }
        }
        r.valid = true;
        return r;
    }

    private static String extractJsonString(String json, String key) {
        String pat = "\"" + key + "\":\"";
        int idx = json.indexOf(pat);
        if (idx < 0) return null;
        int start = idx + pat.length();
        int end = findClosingQuote(json, start);
        return (end > start) ? unescapeJson(json.substring(start, end)) : null;
    }

    private static boolean extractJsonBoolean(String json, String key, boolean fallback) {
        String pat = "\"" + key + "\":";
        int idx = json.indexOf(pat);
        if (idx < 0) return fallback;
        int start = idx + pat.length();
        return json.startsWith("true", start);
    }

    private static float extractJsonFloat(String json, String key, float fallback) {
        String pat = "\"" + key + "\":";
        int idx = json.indexOf(pat);
        if (idx < 0) return fallback;
        int start = idx + pat.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        try {
            return Float.parseFloat(json.substring(start, end).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String serializeSteps(List<ScenarioStep> steps) {
        if (steps == null || steps.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < steps.size(); i++) {
            ScenarioStep it = steps.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"id\":").append(it.id);
            sb.append(",\"title\":\"").append(escapeJson(it.title)).append('"');
            sb.append(",\"type\":\"").append(it.type.name()).append('"');
            sb.append(",\"minHz\":").append(it.minHz);
            sb.append(",\"maxHz\":").append(it.maxHz);
            sb.append(",\"secondaryHz\":").append(it.secondaryHz);
            sb.append(",\"durationSec\":").append(it.durationSec);
            sb.append(",\"stepCount\":").append(it.stepCount);
            sb.append(",\"fmOn\":").append(it.fmOn);
            sb.append(",\"amOn\":").append(it.amOn);
            sb.append(",\"rmOn\":").append(it.rmOn);
            sb.append(",\"isLoop\":").append(it.isLoop);
            sb.append(",\"jitterEnabled\":").append(it.jitterEnabled);
            sb.append(",\"trimDb\":").append(it.trimDb);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    public static List<ScenarioStep> deserializeSteps(String json) {
        List<ScenarioStep> result = new ArrayList<ScenarioStep>();
        if (json == null) return result;
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.equals("[]")) return result;
        int idx = 1;
        int fallbackId = 1;
        while (idx < trimmed.length()) {
            int objStart = trimmed.indexOf('{', idx);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(trimmed, objStart);
            if (objEnd < 0) break;
            result.add(parseStepObject(trimmed.substring(objStart + 1, objEnd), fallbackId++));
            idx = objEnd + 1;
        }
        return result;
    }

    private static int findMatchingBrace(String s, int openIdx) {
        boolean inStr = false;
        boolean esc = false;
        for (int i = openIdx + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (!inStr && c == '}') return i;
        }
        return -1;
    }

    private static ScenarioStep parseStepObject(String body, int fallbackId) {
        int id = fallbackId;
        String title = "스텝 " + fallbackId;
        ScenarioStep.StepType type = ScenarioStep.StepType.SWEEP;
        float minHz = 10.0f, maxHz = 20.0f, secondaryHz = 0.0f, durationSec = 120.0f, trimDb = 0.0f;
        int stepCount = 4;
        boolean fmOn = false, amOn = false, rmOn = false, isLoop = true, jitter = false;

        int i = 0;
        int len = body.length();
        while (i < len) {
            while (i < len && (body.charAt(i) == ' ' || body.charAt(i) == ',' || body.charAt(i) == '\n' || body.charAt(i) == '\r')) i++;
            if (i >= len || body.charAt(i) != '"') break;
            int keyEnd = findClosingQuote(body, i + 1);
            if (keyEnd < 0) break;
            String key = unescapeJson(body.substring(i + 1, keyEnd));
            int colon = body.indexOf(':', keyEnd + 1);
            if (colon < 0) break;
            int valStart = colon + 1;
            while (valStart < len && body.charAt(valStart) == ' ') valStart++;
            if (valStart >= len) break;
            if (body.charAt(valStart) == '"') {
                int valEnd = findClosingQuote(body, valStart + 1);
                if (valEnd < 0) break;
                String rawVal = unescapeJson(body.substring(valStart + 1, valEnd));
                i = valEnd + 1;
                if ("title".equals(key)) title = rawVal;
                else if ("type".equals(key)) type = ScenarioStep.StepType.fromName(rawVal, type);
            } else {
                int valEnd = valStart;
                while (valEnd < len && body.charAt(valEnd) != ',' && body.charAt(valEnd) != '}') valEnd++;
                String rawVal = body.substring(valStart, valEnd).trim();
                i = valEnd + 1;
                try {
                    if ("id".equals(key)) id = Integer.parseInt(rawVal);
                    else if ("minHz".equals(key)) minHz = Float.parseFloat(rawVal);
                    else if ("maxHz".equals(key)) maxHz = Float.parseFloat(rawVal);
                    else if ("secondaryHz".equals(key)) secondaryHz = Float.parseFloat(rawVal);
                    else if ("durationSec".equals(key)) durationSec = Float.parseFloat(rawVal);
                    else if ("stepCount".equals(key)) stepCount = Integer.parseInt(rawVal);
                    else if ("trimDb".equals(key)) trimDb = Float.parseFloat(rawVal);
                    else if ("fmOn".equals(key)) fmOn = Boolean.parseBoolean(rawVal);
                    else if ("amOn".equals(key)) amOn = Boolean.parseBoolean(rawVal);
                    else if ("rmOn".equals(key)) rmOn = Boolean.parseBoolean(rawVal);
                    else if ("isLoop".equals(key)) isLoop = Boolean.parseBoolean(rawVal);
                    else if ("jitterEnabled".equals(key)) jitter = Boolean.parseBoolean(rawVal);
                } catch (NumberFormatException ignored) {}
            }
        }
        return new ScenarioStep(
                id, title, type, minHz, maxHz, secondaryHz, durationSec, stepCount,
                fmOn, fmOn ? ModulationProfile.LOCKED_FM_DEPTH : 0f, fmOn ? ModulationProfile.LOCKED_FM_RATE_HZ : 0f,
                amOn, amOn ? ModulationProfile.LOCKED_AM_DEPTH : 0f, amOn ? ModulationProfile.LOCKED_AM_RATE_HZ : 0f,
                rmOn, rmOn ? ModulationProfile.LOCKED_RM_DEPTH : 0f, rmOn ? ModulationProfile.LOCKED_RM_RATE_HZ : 0f,
                isLoop, jitter, trimDb);
    }

    private static int findClosingQuote(String s, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String escapeJson(String in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(in.length() + 8);
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String unescapeJson(String in) {
        if (in == null || in.indexOf('\\') < 0) return in != null ? in : "";
        StringBuilder sb = new StringBuilder(in.length());
        boolean esc = false;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (esc) {
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
