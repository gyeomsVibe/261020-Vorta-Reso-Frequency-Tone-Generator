package com.vorta.unified;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Zombie-proof persistent JSON sweep store & module preference store for Vorta Unified v5.0.
 */
public final class SweepStore {
    private static final String PREF_NAME = "vorta_unified_store_v50";
    private static final String KEY_ACTIVE_MODULE = "active_module_v1";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on_v1";
    private static final String KEY_INITIALIZED = "sweeps_initialized_v1";
    private static final String KEY_SWEEPS_JSON = "sweeps_json_v1";
    private static final String KEY_SETS_INDEX = "custom_sets_index_v1";
    private static final String KEY_SET_PREFIX = "custom_set_data_";

    private static final String KEY_MANUAL_HZ = "manual_freq_hz";
    private static final String KEY_MANUAL_DUAL = "manual_dual_on";
    private static final String KEY_MANUAL_HZ2 = "manual_freq2_hz";
    private static final String KEY_FM_ON = "mod_fm_on_v2";
    private static final String KEY_AM_ON = "mod_am_on_v2";
    private static final String KEY_RM_ON = "mod_rm_on_v2";
    private static final String KEY_FM_DEPTH = "fm_depth";
    private static final String KEY_FM_RATE = "fm_rate_hz";
    private static final String KEY_AM_DEPTH = "am_depth";
    private static final String KEY_AM_RATE = "am_rate_hz";
    private static final String KEY_RM_DEPTH = "rm_depth";
    private static final String KEY_RM_RATE = "rm_rate_hz";

    private SweepStore() {}

    /** Pure-Java restart/initial-seed decision testable directly on host JVM. */
    public static boolean loadFromState(boolean initialized, String rawJson, List<SweepItem> out) {
        if (out == null) return false;
        out.clear();
        if (initialized) {
            out.addAll(deserializeSweeps(rawJson));
            return false;
        }
        seedStarterDefaults(out, 1);
        return true;
    }

    public static void seedStarterDefaults(List<SweepItem> out, int startId) {
        int id = Math.max(1, startId);
        out.add(new SweepItem(id++, "사용자 스윕 1 (17.5~19.8 Hz)", 17.5f, 19.8f, 440.0f, true));
        out.add(new SweepItem(id++, "사용자 스윕 2 (4.0~6.2 Hz)", 4.0f, 6.2f, 290.0f, true));
        out.add(new SweepItem(id++, "사용자 스윕 3 (20.7~27.7 Hz)", 20.7f, 27.7f, 740.0f, true));
    }

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

    public static VortaModule loadActiveModule(Context context, VortaModule fallback) {
        if (context == null) return fallback;
        return VortaModule.fromName(getPrefs(context).getString(KEY_ACTIVE_MODULE, null), fallback);
    }

    public static void saveActiveModule(Context context, VortaModule module) {
        if (context == null || module == null) return;
        getPrefs(context).edit().putString(KEY_ACTIVE_MODULE, module.name()).apply();
    }

    public static boolean loadKeepScreenOn(Context context) {
        if (context == null) return false;
        return getPrefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public static void saveKeepScreenOn(Context context, boolean keepOn) {
        if (context == null) return;
        getPrefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }

    public static int loadInto(Context context, List<SweepItem> out) {
        if (context == null || out == null) return 1;
        SharedPreferences sp = getPrefs(context);
        boolean initialized = sp.getBoolean(KEY_INITIALIZED, false);
        String raw = sp.getString(KEY_SWEEPS_JSON, null);
        if (loadFromState(initialized, raw, out)) {
            saveNow(context, out);
        }
        int maxId = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).id > maxId) maxId = out.get(i).id;
        }
        return maxId + 1;
    }

    public static boolean saveNow(Context context, List<SweepItem> items) {
        if (context == null || items == null) return false;
        return getPrefs(context).edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putString(KEY_SWEEPS_JSON, serializeSweeps(items))
                .commit();
    }

    public static int resetToStarterDefaults(Context context, List<SweepItem> items) {
        if (items == null) return 1;
        items.clear();
        seedStarterDefaults(items, 1);
        if (context != null) saveNow(context, items);
        return items.size() + 1;
    }

    public static void saveManualAndModState(
            Context context, float hz, boolean dual, float hz2,
            float fmD, float fmR, float amD, float amR, float rmD, float rmR) {
        if (context == null) return;
        getPrefs(context).edit()
                .putFloat(KEY_MANUAL_HZ, hz)
                .putBoolean(KEY_MANUAL_DUAL, dual)
                .putFloat(KEY_MANUAL_HZ2, hz2)
                .putFloat(KEY_FM_DEPTH, fmD)
                .putFloat(KEY_FM_RATE, fmR)
                .putFloat(KEY_AM_DEPTH, amD)
                .putFloat(KEY_AM_RATE, amR)
                .putFloat(KEY_RM_DEPTH, rmD)
                .putFloat(KEY_RM_RATE, rmR)
                .apply();
    }

    public static void savePureModulationToggles(Context context, boolean fmOn, boolean amOn, boolean rmOn) {
        if (context == null) return;
        getPrefs(context).edit()
                .putBoolean(KEY_FM_ON, fmOn)
                .putBoolean(KEY_AM_ON, amOn)
                .putBoolean(KEY_RM_ON, rmOn)
                .putFloat(KEY_FM_DEPTH, fmOn ? ModulationProfile.LOCKED_FM_DEPTH : 0.0f)
                .putFloat(KEY_FM_RATE, fmOn ? ModulationProfile.LOCKED_FM_RATE_HZ : 0.0f)
                .putFloat(KEY_AM_DEPTH, amOn ? ModulationProfile.LOCKED_AM_DEPTH : 0.0f)
                .putFloat(KEY_AM_RATE, amOn ? ModulationProfile.LOCKED_AM_RATE_HZ : 0.0f)
                .putFloat(KEY_RM_DEPTH, rmOn ? ModulationProfile.LOCKED_RM_DEPTH : 0.0f)
                .putFloat(KEY_RM_RATE, rmOn ? ModulationProfile.LOCKED_RM_RATE_HZ : 0.0f)
                .apply();
    }

    public static void loadManualAndModIntoEngine(Context context) {
        if (context == null) return;
        SharedPreferences sp = getPrefs(context);
        VortaAudioService.sManualFreq = sp.getFloat(KEY_MANUAL_HZ, VortaAudioService.sManualFreq);
        VortaAudioService.sManualDualEnabled = sp.getBoolean(KEY_MANUAL_DUAL, VortaAudioService.sManualDualEnabled);
        VortaAudioService.sManualSecondaryFreq = sp.getFloat(KEY_MANUAL_HZ2, VortaAudioService.sManualSecondaryFreq);
        boolean fmOn = sp.getBoolean(KEY_FM_ON, false);
        boolean amOn = sp.getBoolean(KEY_AM_ON, false);
        boolean rmOn = sp.getBoolean(KEY_RM_ON, false);
        VortaAudioService.applyLockedPureModulation(fmOn, amOn, rmOn);
    }

    public static List<String> listNamedSets(Context context) {
        List<String> names = new ArrayList<String>();
        if (context == null) return names;
        String raw = getPrefs(context).getString(KEY_SETS_INDEX, "");
        if (raw == null || raw.isEmpty()) return names;
        for (String line : raw.split("\\n")) {
            String n = line.trim();
            if (!n.isEmpty() && !names.contains(n)) names.add(n);
        }
        return names;
    }

    public static boolean saveNamedSet(Context context, String name, List<SweepItem> items) {
        if (context == null || name == null || items == null) return false;
        String clean = name.replace('\n', ' ').trim();
        if (clean.isEmpty()) return false;
        List<String> names = listNamedSets(context);
        if (!names.contains(clean)) names.add(clean);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(names.get(i));
        }
        return getPrefs(context).edit()
                .putString(KEY_SETS_INDEX, sb.toString())
                .putString(KEY_SET_PREFIX + clean, serializeSweeps(items))
                .commit();
    }

    public static boolean loadNamedSet(Context context, String name, List<SweepItem> out) {
        if (context == null || name == null || out == null) return false;
        String raw = getPrefs(context).getString(KEY_SET_PREFIX + name, null);
        if (raw == null) return false;
        out.clear();
        out.addAll(deserializeSweeps(raw));
        saveNow(context, out);
        return true;
    }

    public static void deleteNamedSet(Context context, String name) {
        if (context == null || name == null) return;
        List<String> names = listNamedSets(context);
        names.remove(name);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(names.get(i));
        }
        getPrefs(context).edit()
                .putString(KEY_SETS_INDEX, sb.toString())
                .remove(KEY_SET_PREFIX + name)
                .commit();
    }

    public static String serializeSweeps(List<SweepItem> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            SweepItem it = items.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"id\":").append(it.id);
            sb.append(",\"title\":\"").append(escapeJson(it.title)).append('"');
            sb.append(",\"startHz\":").append(it.startHz);
            sb.append(",\"endHz\":").append(it.endHz);
            sb.append(",\"durationSec\":").append(it.durationSec);
            sb.append(",\"secondaryHz\":").append(it.secondaryHz);
            sb.append(",\"isLoop\":").append(it.isLoop);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    public static List<SweepItem> deserializeSweeps(String json) {
        List<SweepItem> result = new ArrayList<SweepItem>();
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
            result.add(parseItemObject(trimmed.substring(objStart + 1, objEnd), fallbackId++));
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

    private static SweepItem parseItemObject(String body, int fallbackId) {
        int id = fallbackId;
        String title = "사용자 스윕 " + fallbackId;
        float startHz = 10.0f, endHz = 25.0f, durationSec = 60.0f, secondaryHz = 0.0f;
        boolean isLoop = true;
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
            } else {
                int valEnd = valStart;
                while (valEnd < len && body.charAt(valEnd) != ',' && body.charAt(valEnd) != '}') valEnd++;
                String rawVal = body.substring(valStart, valEnd).trim();
                i = valEnd + 1;
                try {
                    if ("id".equals(key)) id = Integer.parseInt(rawVal);
                    else if ("startHz".equals(key)) startHz = Float.parseFloat(rawVal);
                    else if ("endHz".equals(key)) endHz = Float.parseFloat(rawVal);
                    else if ("durationSec".equals(key)) durationSec = Float.parseFloat(rawVal);
                    else if ("secondaryHz".equals(key)) secondaryHz = Float.parseFloat(rawVal);
                    else if ("isLoop".equals(key)) isLoop = Boolean.parseBoolean(rawVal);
                } catch (NumberFormatException ignored) {}
            }
        }
        return new SweepItem(id, title, startHz, endHz, durationSec, isLoop, secondaryHz);
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
