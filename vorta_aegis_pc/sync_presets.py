import json
import os
import subprocess
import sys

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PRESET_JAVA = os.path.join(ROOT_DIR, "vorta_aegis_app", "src", "com", "vorta", "aegis", "PresetModel.java")
OUT_JSON = os.path.join(os.path.dirname(__file__), "web", "presets.json")
WORK_DIR = os.path.join(ROOT_DIR, ".work", "scratch", "preset_export")

JBR_JAVAC = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
JBR_JAVA = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"

EXPORTER_JAVA = r'''import com.vorta.aegis.PresetModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExportPresetsJson {
    private static String esc(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        List<PresetModel> all = PresetModel.getAllPresets();
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < all.size(); i++) {
            PresetModel p = all.get(i);
            sb.append("  {");
            sb.append("\"id\":").append(p.id).append(",");
            sb.append("\"shortCode\":").append(esc(p.getShortCode())).append(",");
            sb.append("\"tabIndex\":").append(p.tabIndex).append(",");
            sb.append("\"title\":").append(esc(p.title)).append(",");
            sb.append("\"badge\":").append(esc(p.badge)).append(",");
            sb.append("\"desc\":").append(esc(p.desc)).append(",");
            sb.append("\"freq1\":").append(Float.toString(p.freq1)).append(",");
            sb.append("\"freq2\":").append(Float.toString(p.freq2)).append(",");
            sb.append("\"isSweep\":").append(p.isSweep).append(",");
            sb.append("\"sweepStart\":").append(Float.toString(p.sweepStart)).append(",");
            sb.append("\"sweepEnd\":").append(Float.toString(p.sweepEnd)).append(",");
            sb.append("\"sweepDurationSec\":").append(Float.toString(p.sweepDurationSec)).append(",");
            sb.append("\"isAntiPhase\":").append(p.isAntiPhase).append(",");
            sb.append("\"binauralBeat\":").append(Float.toString(p.binauralBeat)).append(",");
            sb.append("\"noiseType\":").append(p.noiseType).append(",");
            sb.append("\"fmMod\":").append(p.fmMod).append(",");
            sb.append("\"fmDepth\":").append(Float.toString(p.fmDepth)).append(",");
            sb.append("\"fmRate\":").append(Float.toString(p.fmRate)).append(",");
            sb.append("\"amMod\":").append(p.amMod).append(",");
            sb.append("\"amDepth\":").append(Float.toString(p.amDepth)).append(",");
            sb.append("\"amRate\":").append(Float.toString(p.amRate));
            sb.append("}");
            if (i < all.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        Files.write(Paths.get(args[0]), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
'''


def has_forbidden_symbol(text: str) -> bool:
    if not text:
        return False
    if "\u2605" in text:
        return True
    return any(ord(ch) >= 0x1F300 for ch in text)


def sync() -> list:
    if not os.path.exists(PRESET_JAVA):
        raise FileNotFoundError(f"Android PresetModel.java not found: {PRESET_JAVA}")
    if not os.path.exists(JBR_JAVAC) or not os.path.exists(JBR_JAVA):
        raise FileNotFoundError("Android Studio JBR javac/java not found")

    os.makedirs(WORK_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)

    exporter_src = os.path.join(WORK_DIR, "ExportPresetsJson.java")
    with open(exporter_src, "w", encoding="utf-8") as f:
        f.write(EXPORTER_JAVA)

    subprocess.check_call(
        [JBR_JAVAC, "-encoding", "UTF-8", "-d", WORK_DIR, PRESET_JAVA, exporter_src]
    )
    subprocess.check_call(
        [JBR_JAVA, "-Dfile.encoding=UTF-8", "-cp", WORK_DIR, "ExportPresetsJson", OUT_JSON]
    )

    with open(OUT_JSON, "r", encoding="utf-8") as f:
        presets = json.load(f)

    assert len(presets) == 48, f"Expected 48 presets, got {len(presets)}"
    counts = [0] * 8
    for p in presets:
        idx = p["tabIndex"]
        assert 0 <= idx < 8, f"Invalid tabIndex {idx}"
        counts[idx] += 1
        for field in ("title", "badge", "desc"):
            val = p[field]
            assert "\ubc1c\uc9c4" not in val, f"Forbidden term in preset {p['id']}"
            assert not has_forbidden_symbol(val), f"Emoji/star in preset {p['id']}"

    expected_counts = [12, 6, 6, 5, 5, 5, 5, 4]
    assert counts == expected_counts, f"Tab counts mismatch: {counts} != {expected_counts}"
    print(f"Synced {len(presets)} presets -> {OUT_JSON} (tabs={counts})")
    return presets


if __name__ == "__main__":
    sync()
