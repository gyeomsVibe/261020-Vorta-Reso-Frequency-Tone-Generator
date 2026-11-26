import com.vorta.unified.SweepItem;
import com.vorta.unified.SweepStore;
import com.vorta.unified.VortaVolumeGuard;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Host-JVM verification suite for vorta_unified_app v5.2.1:
 * Verifies S6/S7 safe-volume hardening, A30 live-volume readout (zero jargon),
 * in-app banner & adjustResize migration, SweepStore round-trip, and zero font-size changes.
 */
public final class TestVortaUnifiedV521Audit {
    private static int sPassed = 0;
    private static int sFailed = 0;

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            sPassed++;
            System.out.println("[PASS] " + name + " | " + detail);
        } else {
            sFailed++;
            System.err.println("[FAIL] " + name + " | " + detail);
        }
    }

    private static List<String> extractSetTextSizeLines(String src) {
        List<String> out = new ArrayList<String>();
        for (String line : src.split("\\r?\\n")) {
            if (line.contains("setTextSize(")) {
                out.add(line.trim());
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        // Gate 1: VortaVolumeGuard red-team helpers
        boolean clampOk = VortaVolumeGuard.computeSafeClampStep(15) == 9
                && VortaVolumeGuard.computeSafeClampStep(25) == 15;
        boolean cooldownOk = !VortaVolumeGuard.shouldResetDialogRetryCounter(100_000L, 50_000L, 3)
                && VortaVolumeGuard.shouldResetDialogRetryCounter(400_000L, 50_000L, 3);
        boolean manualDownOk = !VortaVolumeGuard.isOsSafeVolumeClamp(9, 10, 14, 9, 2, false, true);
        boolean realClampOk = VortaVolumeGuard.isOsSafeVolumeClamp(9, 14, 14, 9, 3, false, false)
                && VortaVolumeGuard.isOsSafeVolumeClamp(9, 10, 14, 9, 3, true, false);
        boolean sanitizeOk = VortaVolumeGuard.sanitizeRestoredControllerComponent("com.vorta.unified/.SafeVolAuth") == null
                && "com.other/.Ctrl".equals(VortaVolumeGuard.sanitizeRestoredControllerComponent("com.other/.Ctrl"));
        check("Gate1_VolumeGuardRedteam",
                clampOk && cooldownOk && manualDownOk && realClampOk && sanitizeOk,
                "clamp=" + clampOk + " cooldown=" + cooldownOk + " manual=" + manualDownOk + " real=" + realClampOk);

        // Gate 2: SweepStore round-trip
        List<SweepItem> orig = new ArrayList<SweepItem>();
        orig.add(new SweepItem(1, "#55~ 63hz (45s)", 55.0f, 63.0f, 45.0f, true));
        orig.add(new SweepItem(2, "#2. 27~40.5hz", 27.0f, 40.5f, 320.0f, false));
        String json = SweepStore.serializeSweeps(orig);
        List<SweepItem> parsed = SweepStore.deserializeSweeps(json);
        check("Gate2_SweepStoreRoundTrip",
                parsed.size() == 2 && "#55~ 63hz (45s)".equals(parsed.get(0).title)
                        && Math.abs(parsed.get(1).endHz - 40.5f) < 1e-4,
                "parsed=" + parsed.size());

        // Gate 3: Legacy UI fixes + A30 jargon removal + SafeVol accessibility expansion
        File srcDir = new File("vorta_unified_app/src/com/vorta/unified");
        String mainCode = new String(Files.readAllBytes(new File(srcDir, "MainActivity.java").toPath()), StandardCharsets.UTF_8);
        String a11yCode = new String(Files.readAllBytes(new File(srcDir, "VortaAccessibilityService.java").toPath()), StandardCharsets.UTF_8);
        String guardCode = new String(Files.readAllBytes(new File(srcDir, "VortaVolumeGuard.java").toPath()), StandardCharsets.UTF_8);

        boolean noToast = !mainCode.contains("Toast.makeText");
        boolean noOldVolJargon = !mainCode.contains("순음 고정(볼륨 무개입)")
                && !mainCode.contains("자유볼륨")
                && !mainCode.contains("개인기기 보호");
        boolean hasLiveVol = mainCode.contains("formatLiveVolumeStepText") && guardCode.contains("볼륨 %d/%d단");
        boolean a11yOk = a11yCode.contains("음량 제한") && a11yCode.contains("4500L");

        boolean manifestsOk = true;
        for (String fl : new String[]{"s6_api24", "s7_api26", "a30_api30"}) {
            String mf = new String(Files.readAllBytes(new File("vorta_unified_app/flavors/" + fl + "/AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
            if (!mf.contains("versionCode=\"521\"") || !mf.contains("versionName=\"5.2.1\"")
                    || mf.contains("adjustPan") || !mf.contains("adjustResize")) {
                manifestsOk = false;
            }
        }
        check("Gate3_UiAndManifestAudit",
                noToast && noOldVolJargon && hasLiveVol && a11yOk && manifestsOk,
                "noToast=" + noToast + " noJargon=" + noOldVolJargon + " liveVol=" + hasLiveVol
                        + " a11y=" + a11yOk + " manifests=" + manifestsOk);

        // Gate 4: Strictly preserve ALL pre-existing setTextSize(...) calls
        File backupMain = new File(".work/backup_261127_unified/MainActivity.java");
        String oldMain = new String(Files.readAllBytes(backupMain.toPath()), StandardCharsets.UTF_8);
        List<String> oldSizes = extractSetTextSizeLines(oldMain);
        List<String> newSizes = extractSetTextSizeLines(mainCode);
        List<String> extra = new ArrayList<String>(newSizes);
        for (String s : oldSizes) {
            if (!extra.remove(s)) {
                extra.add("MISSING_OLD:" + s);
            }
        }
        boolean fontPreserved = newSizes.size() == oldSizes.size() + 1
                && extra.size() == 1 && extra.get(0).equals("mTvBanner.setTextSize(13);");
        check("Gate4_PreserveExistingFontSizes",
                fontPreserved,
                "oldCount=" + oldSizes.size() + " newCount=" + newSizes.size() + " delta=" + extra);

        System.out.println("\nSUMMARY: PASSED=" + sPassed + " FAILED=" + sFailed);
        if (sFailed > 0) System.exit(1);
    }
}
