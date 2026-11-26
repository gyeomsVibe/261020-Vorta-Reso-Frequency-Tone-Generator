import com.vorta.synergy.HardwareStabilizer;
import com.vorta.synergy.ModulationProfile;
import com.vorta.synergy.ScenarioStep;
import com.vorta.synergy.SynergyCatalog;
import com.vorta.synergy.SynergyChannel;
import com.vorta.synergy.SynergyDspCore;
import com.vorta.synergy.SynergyStore;
import com.vorta.synergy.SynergyVolumeGuard;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 7-Gate Host JVM Acceptance & Red-Team Audit for Vorta Synergy v1.0.0.
 */
public class TestVortaSynergyAudit {
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

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static double[] freqRange(SynergyCatalog.ChannelProgram prog) {
        if (prog.step.type == ScenarioStep.StepType.SINGLE) {
            return new double[]{prog.step.minHz, prog.step.minHz};
        }
        return new double[]{
                Math.min(prog.step.minHz, prog.step.maxHz),
                Math.max(prog.step.minHz, prog.step.maxHz)
        };
    }

    private static boolean rangesOverlap(double[] a, double[] b) {
        return Math.max(a[0], b[0]) <= Math.min(a[1], b[1]);
    }

    public static void main(String[] args) throws Exception {
        // ---------------------------------------------------------------------
        // GATE 1: Channel Band Guardrails (All 80 programs + clamping bounds)
        // ---------------------------------------------------------------------
        List<SynergyCatalog.SynergyPackage> pkgs = SynergyCatalog.packages();
        check("Gate1_PackageCount20", pkgs.size() == 20, "packages=" + pkgs.size());

        int bandViolations = 0;
        for (SynergyCatalog.SynergyPackage p : pkgs) {
            SynergyChannel[] chs = SynergyChannel.values();
            for (SynergyChannel ch : chs) {
                SynergyCatalog.ChannelProgram cp = p.programFor(ch);
                if (!ch.isInBand(cp.step.minHz) || !ch.isInBand(cp.step.maxHz)) bandViolations++;
                if (cp.step.secondaryHz > 0f && !ch.isInBand(cp.step.secondaryHz)) bandViolations++;
            }
        }
        boolean clampOk = Math.abs(SynergyChannel.CH1_SUB_LOW.clampHz(55.0f) - 21.0f) < 1e-4f
                && Math.abs(SynergyChannel.CH2_LOW_MID.clampHz(5.0f) - 10.0f) < 1e-4f
                && Math.abs(SynergyChannel.CH2_LOW_MID.clampHz(50.0f) - 40.0f) < 1e-4f
                && Math.abs(SynergyChannel.CH3_TACTILE_MAIN.clampHz(20.0f) - 40.0f) < 1e-4f
                && Math.abs(SynergyChannel.CH4_TACTILE_SUB.clampHz(80.0f) - 63.0f) < 1e-4f;
        check("Gate1_ChannelBandGuardrails", bandViolations == 0 && clampOk,
                "violations=" + bandViolations + " clampOk=" + clampOk);

        // ---------------------------------------------------------------------
        // GATE 2: Zero Cross-Channel Collision & Co-prime Sweep Periods
        // ---------------------------------------------------------------------
        int collisions = 0;
        int nonCoprimeSweeps = 0;
        for (SynergyCatalog.SynergyPackage p : pkgs) {
            double[] r1 = freqRange(p.ch1);
            double[] r2 = freqRange(p.ch2);
            double[] r3 = freqRange(p.ch3);
            double[] r4 = freqRange(p.ch4);
            if (rangesOverlap(r1, r2) || rangesOverlap(r1, r3) || rangesOverlap(r1, r4)
                    || rangesOverlap(r2, r3) || rangesOverlap(r2, r4)) {
                collisions++;
            }
            if (rangesOverlap(r3, r4)) {
                collisions++;
            } else if (p.ch3.step.type == ScenarioStep.StepType.SINGLE
                    && p.ch4.step.type == ScenarioStep.StepType.SINGLE
                    && Math.abs(p.ch3.step.minHz - p.ch4.step.minHz) < 3.5) {
                collisions++;
            }
            List<Long> periods = new ArrayList<Long>();
            for (SynergyCatalog.ChannelProgram cp : Arrays.asList(p.ch1, p.ch2, p.ch3, p.ch4)) {
                if (cp.step.type != ScenarioStep.StepType.SINGLE) {
                    periods.add((long) Math.round(cp.step.durationSec));
                }
            }
            for (int i = 0; i < periods.size(); i++) {
                for (int j = i + 1; j < periods.size(); j++) {
                    if (gcd(periods.get(i), periods.get(j)) != 1L) {
                        nonCoprimeSweeps++;
                    }
                }
            }
        }
        check("Gate2_ZeroCollisionAndCoprime", collisions == 0 && nonCoprimeSweeps == 0,
                "collisions=" + collisions + " nonCoprimeSweeps=" + nonCoprimeSweeps);

        // ---------------------------------------------------------------------
        // GATE 3: Full 102-Item DSP Parity, Peak <= -1.0 dBTP, THD+N & Vaccine
        // ---------------------------------------------------------------------
        double maxPeakObserved = 0.0;
        double worstPureThd = 0.0;
        int dspFailures = 0;
        float[] buf = new float[2880 * 2];
        float[] mono = new float[2880];

        for (SynergyCatalog.SynergyPackage p : pkgs) {
            for (SynergyChannel ch : SynergyChannel.values()) {
                SynergyCatalog.ChannelProgram cp = p.programFor(ch);
                SynergyDspCore.DspState ds = new SynergyDspCore.DspState();
                SynergyDspCore.RenderRequest req = new SynergyDspCore.RenderRequest();
                req.channel = ch;
                req.singleStep = cp.step.copy();
                req.dutyOnMin = 0;
                req.dutyCoolMin = 0;
                // Warm past 2.0s hardware-stabilizer soft-start ramp
                for (int k = 0; k < 38; k++) {
                    SynergyDspCore.renderChunk(buf, 2880, 48000, ds, req);
                }
                for (int i = 0; i < 2880; i++) mono[i] = buf[i * 2];
                boolean isPure = (cp.step.type == ScenarioStep.StepType.SINGLE
                        && !cp.step.fmOn && !cp.step.amOn && !cp.step.rmOn);
                double f1 = ch.clampHz(cp.step.minHz);
                double f2 = cp.step.secondaryHz > 0f ? ch.clampHz(cp.step.secondaryHz) : 0.0;
                SynergyDspCore.LiveQualityReport qr = SynergyDspCore.measureLiveBufferQuality(
                        mono, 2880, 48000, isPure, f1, f2);
                if (qr.peak > maxPeakObserved) maxPeakObserved = qr.peak;
                if (qr.clipOrNanCount > 0 || qr.peak > SynergyDspCore.TRUE_PEAK_HEADROOM + 1e-5) dspFailures++;
                if (isPure) {
                    if (qr.thdPlusNPercent < 0.0 || qr.thdPlusNPercent > 0.01) dspFailures++;
                    if (qr.thdPlusNPercent > worstPureThd) worstPureThd = qr.thdPlusNPercent;
                }
            }
        }

        List<SynergyCatalog.BasicEntry> basics = SynergyCatalog.basicCatalog();
        check("Gate3a_BasicCount22", basics.size() == 22, "basic=" + basics.size());
        for (SynergyCatalog.BasicEntry be : basics) {
            SynergyDspCore.DspState ds = new SynergyDspCore.DspState();
            SynergyDspCore.RenderRequest req = new SynergyDspCore.RenderRequest();
            req.channel = be.recommendedChannel;
            req.singleStep = be.step.copy();
            for (int k = 0; k < 18; k++) {
                SynergyDspCore.renderChunk(buf, 2880, 48000, ds, req);
            }
            for (int i = 0; i < 2880; i++) mono[i] = buf[i * 2];
            SynergyDspCore.LiveQualityReport qr = SynergyDspCore.measureLiveBufferQuality(mono, 2880, 48000, false, 0, 0);
            if (qr.peak > maxPeakObserved) maxPeakObserved = qr.peak;
            if (qr.clipOrNanCount > 0 || qr.peak > SynergyDspCore.TRUE_PEAK_HEADROOM + 1e-5) dspFailures++;
        }

        // Digital trim -6 dB check + Vaccine corrupt injection check
        SynergyDspCore.DspState trimDs = new SynergyDspCore.DspState();
        SynergyDspCore.RenderRequest trimReq = new SynergyDspCore.RenderRequest();
        trimReq.channel = SynergyChannel.CH3_TACTILE_MAIN;
        trimReq.singleStep = new ScenarioStep(1, "TrimCheck", ScenarioStep.StepType.SINGLE,
                48.0f, 48.0f, 0f, 300f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, -6.0f);
        for (int k = 0; k < 38; k++) SynergyDspCore.renderChunk(buf, 2880, 48000, trimDs, trimReq);
        for (int i = 0; i < 2880; i++) mono[i] = buf[i * 2];
        SynergyDspCore.LiveQualityReport trimQr = SynergyDspCore.measureLiveBufferQuality(mono, 2880, 48000, true, 48.0, 0.0);
        double expectedPeak = SynergyDspCore.TRUE_PEAK_HEADROOM * Math.pow(10.0, -6.0 / 20.0);
        boolean trimOk = Math.abs(trimQr.peak - expectedPeak) < 0.01 && trimQr.thdPlusNPercent < 0.01;

        float[] corrupt = new float[2880];
        for (int i = 0; i < 2880; i++) {
            double t = i / 48000.0;
            corrupt[i] = (float) (0.8 * Math.sin(2.0 * Math.PI * 48.0 * t) + 0.18 * Math.sin(2.0 * Math.PI * 144.0 * t));
        }
        corrupt[100] = 1.04f;
        SynergyDspCore.LiveQualityReport corruptQr = SynergyDspCore.measureLiveBufferQuality(corrupt, 2880, 48000, true, 48.0, 0.0);

        check("Gate3b_Dsp102ParityAndVaccine",
                dspFailures == 0 && trimOk && corruptQr.thdPlusNPercent > 15.0 && corruptQr.clipOrNanCount >= 1,
                String.format(Locale.US, "failures=%d maxPeak=%.5f worstPureTHD=%.6f%% trimPeak=%.4f corruptTHD=%.2f%%",
                        dspFailures, maxPeakObserved, worstPureThd, trimQr.peak, corruptQr.thdPlusNPercent));

        // ---------------------------------------------------------------------
        // GATE 4: CH4 Thermal Duty Governor (All 20 packages + C^1 fade to 0.0f)
        // ---------------------------------------------------------------------
        int ch4WithoutCool = 0;
        for (SynergyCatalog.SynergyPackage p : pkgs) {
            if (p.ch4.dutyOnMin <= 0 || p.ch4.dutyCoolMin <= 0) ch4WithoutCool++;
        }
        SynergyDspCore.DspState dutyDs = new SynergyDspCore.DspState();
        SynergyDspCore.RenderRequest dutyReq = new SynergyDspCore.RenderRequest();
        dutyReq.channel = SynergyChannel.CH4_TACTILE_SUB;
        dutyReq.singleStep = pkgs.get(0).ch4.step.copy();
        dutyReq.dutyOnMin = 1; // 60 sec ON
        dutyReq.dutyCoolMin = 1; // 60 sec COOL
        // Run 65 seconds (65 * 48000 / 2880 ~= 1084 chunks) to enter cooling phase cleanly
        double maxJumpDuringFade = 0.0;
        double prevS = 0.0;
        for (int k = 0; k < 1090; k++) {
            SynergyDspCore.renderChunk(buf, 2880, 48000, dutyDs, dutyReq);
            for (int i = 0; i < 2880; i++) {
                double d = Math.abs(buf[i * 2] - prevS);
                if (d > maxJumpDuringFade) maxJumpDuringFade = d;
                prevS = buf[i * 2];
            }
        }
        double coolMaxAbs = 0.0;
        for (int i = 0; i < 2880; i++) coolMaxAbs = Math.max(coolMaxAbs, Math.abs(buf[i * 2]));
        check("Gate4_ThermalDutyGovernor",
                ch4WithoutCool == 0 && dutyDs.dutyCooling && coolMaxAbs == 0.0 && maxJumpDuringFade < 0.02,
                String.format(Locale.US, "ch4Missing=%d cooling=%s coolAbs=%.6f maxDelta=%.5f",
                        ch4WithoutCool, dutyDs.dutyCooling, coolMaxAbs, maxJumpDuringFade));

        // ---------------------------------------------------------------------
        // GATE 5: SynergyStore JSON Round-Trip & Anti-Zombie Persistence
        // ---------------------------------------------------------------------
        List<ScenarioStep> seed = new ArrayList<ScenarioStep>();
        SynergyStore.seedStarterSteps(SynergyChannel.CH2_LOW_MID, seed);
        seed.add(new ScenarioStep(9, "휴지 테스트", ScenarioStep.StepType.REST_COOL,
                10f, 10f, 0f, 600f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, -2f));
        String json = SynergyStore.serializeSteps(seed);
        List<ScenarioStep> loaded = new ArrayList<ScenarioStep>();
        boolean seededFresh = SynergyStore.loadFromState(true, json, SynergyChannel.CH2_LOW_MID, loaded);
        List<ScenarioStep> zombieCheck = new ArrayList<ScenarioStep>();
        SynergyStore.loadFromState(true, "[]", SynergyChannel.CH2_LOW_MID, zombieCheck);

        String fleet = SynergyStore.exportFleetJson(SynergyChannel.CH4_TACTILE_SUB, "S-03", true, true, 2, -2.0f, seed);
        SynergyStore.FleetImportResult imported = SynergyStore.importFleetJson(fleet);
        check("Gate5_StoreAndFleetJson",
                !seededFresh && loaded.size() == 4 && loaded.get(3).type == ScenarioStep.StepType.REST_COOL
                        && zombieCheck.isEmpty()
                        && imported.valid && imported.channel == SynergyChannel.CH4_TACTILE_SUB
                        && "S-03".equals(imported.packageCode) && imported.steps.size() == 4,
                "loaded=" + loaded.size() + " zombieSize=" + zombieCheck.size() + " fleetSteps=" + imported.steps.size());

        // ---------------------------------------------------------------------
        // GATE 6: Commercial Terminology & UI Bug Regression Static Audit
        // ---------------------------------------------------------------------
        String[] bannedWords = {
                "자유볼륨", "해당없음", "순음 고정", "개인기기", "스마토", "트랜스2",
                "본카운터", "311호", "211호", "팬더아파트", "갤럭시 S6", "갤럭시 S7", "갤럭시 A30", "발진"
        };
        File srcDir = new File("vorta_synergy_app/src/com/vorta/synergy");
        StringBuilder allUiCode = new StringBuilder();
        for (String fn : new String[]{"MainActivity.java", "SynergyChannel.java", "SynergyCatalog.java", "ModulationProfile.java", "ScenarioStep.java", "HardwareStabilizer.java"}) {
            allUiCode.append(new String(Files.readAllBytes(new File(srcDir, fn).toPath()), StandardCharsets.UTF_8));
        }
        String uiAll = allUiCode.toString();
        List<String> bannedHits = new ArrayList<String>();
        for (String bw : bannedWords) {
            if (uiAll.contains(bw)) bannedHits.add(bw);
        }
        String mainCode = new String(Files.readAllBytes(new File(srcDir, "MainActivity.java").toPath()), StandardCharsets.UTF_8);
        boolean noToast = !mainCode.contains("Toast.makeText");
        boolean noInlineEdit = !mainCode.contains("makeLabeledInput");
        boolean adjustResizeAll = true;
        for (String fl : new String[]{"s6_api24", "s7_api26", "a30_api30"}) {
            String mf = new String(Files.readAllBytes(new File("vorta_synergy_app/flavors/" + fl + "/AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
            if (mf.contains("adjustPan") || !mf.contains("adjustResize")) adjustResizeAll = false;
        }
        check("Gate6_CommercialAndBugRegression",
                bannedHits.isEmpty() && noToast && noInlineEdit && adjustResizeAll,
                "banned=" + bannedHits + " noToast=" + noToast + " adjustResizeAll=" + adjustResizeAll);

        // ---------------------------------------------------------------------
        // GATE 7: SynergyVolumeGuard Red-Team Logic Verification
        // ---------------------------------------------------------------------
        boolean dynamicClampOk = SynergyVolumeGuard.computeSafeClampStep(15) == 9
                && SynergyVolumeGuard.computeSafeClampStep(25) == 15;
        boolean cooldownResetOk = !SynergyVolumeGuard.shouldResetDialogRetryCounter(100_000L, 50_000L, 3)
                && SynergyVolumeGuard.shouldResetDialogRetryCounter(400_000L, 50_000L, 3);
        boolean manualStepDownHonored = !SynergyVolumeGuard.isOsSafeVolumeClamp(9, 10, 14, 9, 2, false, true);
        boolean realOsClampCaught = SynergyVolumeGuard.isOsSafeVolumeClamp(9, 14, 14, 9, 3, false, false)
                && SynergyVolumeGuard.isOsSafeVolumeClamp(9, 10, 14, 9, 3, true, false);
        boolean authCleaned = SynergyVolumeGuard.sanitizeRestoredControllerComponent("com.vorta.unified/.SafeVolAuth") == null
                && "com.other/.Ctrl".equals(SynergyVolumeGuard.sanitizeRestoredControllerComponent("com.other/.Ctrl"));
        check("Gate7_SafeVolumeRedteamAudit",
                dynamicClampOk && cooldownResetOk && manualStepDownHonored && realOsClampCaught && authCleaned,
                "dynamicClamp=" + dynamicClampOk + " cooldown=" + cooldownResetOk
                        + " manualStep=" + manualStepDownHonored + " realClamp=" + realOsClampCaught);

        // ---------------------------------------------------------------------
        // GATE 8: Unstable Hardware Stabilizer (Excursion, Notch, Thermal RC, Battery)
        // ---------------------------------------------------------------------
        double ch1Center = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH1_SUB_LOW, 10.5, true);
        double ch1LowCut = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH1_SUB_LOW, 1.5, true);
        double ch1HighCut = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH1_SUB_LOW, 20.5, true);
        boolean ch1RollOk = Math.abs(ch1Center) < 1e-6 && ch1LowCut < -1.0 && ch1HighCut < -1.5;

        double ch2Mid = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH2_LOW_MID, 25.0, true);
        double ch2High = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH2_LOW_MID, 39.0, true);
        boolean ch2ProtOk = Math.abs(ch2Mid + 1.5) < 1e-6 && ch2High < -3.5;

        HardwareStabilizer.Profile prof3 = HardwareStabilizer.profileFor(SynergyChannel.CH3_TACTILE_MAIN);
        double ch3MainGain = HardwareStabilizer.computeContourGainDb(SynergyChannel.CH3_TACTILE_MAIN, 55.0, true);
        boolean ch3MainOk = Math.abs(prof3.baseTrimDb) < 1e-6 && Math.abs(ch3MainGain) < 1e-6;

        double notchAt36 = HardwareStabilizer.computeNotchCutDb(36.0, 36.0f, 1.5f);
        double notchAt28 = HardwareStabilizer.computeNotchCutDb(28.0, 36.0f, 1.5f);
        boolean notchOk = Math.abs(notchAt36 + 12.0) < 1e-6 && Math.abs(notchAt28) < 1e-6;

        HardwareStabilizer.Profile prof4 = HardwareStabilizer.profileFor(SynergyChannel.CH4_TACTILE_SUB);
        double load = 0.0;
        for (int s = 0; s < 1200; s++) {
            load = HardwareStabilizer.updateThermalLoad(load, 0.7071, 1.0, prof4);
        }
        double derateHot = HardwareStabilizer.computeThermalDerateDb(load, prof4, true);
        double cooledLoad = load;
        for (int s = 0; s < 600; s++) {
            cooledLoad = HardwareStabilizer.updateThermalLoad(cooledLoad, 0.0, 1.0, prof4);
        }
        boolean thermalRcOk = load > 0.80 && derateHot <= -1.0 && derateHot >= -3.0 && cooledLoad < load * 0.25;

        // Verify smooth DSP THD+N under active stabilizer on CH1 10.5 Hz & CH4 thermal derating
        SynergyDspCore.DspState stabDs = new SynergyDspCore.DspState();
        stabDs.thermalLoad = 0.90; // already hot CH4
        SynergyDspCore.RenderRequest stabReq = new SynergyDspCore.RenderRequest();
        stabReq.channel = SynergyChannel.CH4_TACTILE_SUB;
        stabReq.stabilizerEnabled = true;
        stabReq.notchCenterHz = 0.0f;
        stabReq.singleStep = new ScenarioStep(77, "CH4 HotPure", ScenarioStep.StepType.SINGLE,
                55.0f, 55.0f, 0f, 300f, 2, false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, true, false, 0f);
        for (int k = 0; k < 35; k++) SynergyDspCore.renderChunk(buf, 2880, 48000, stabDs, stabReq);
        for (int i = 0; i < 2880; i++) mono[i] = buf[i * 2];
        SynergyDspCore.LiveQualityReport stabQr = SynergyDspCore.measureLiveBufferQuality(mono, 2880, 48000, true, 55.0, 0.0);

        boolean battHystOk = HardwareStabilizer.evaluateBatteryThermalThrottle(43.2f, false)
                && HardwareStabilizer.evaluateBatteryThermalThrottle(41.0f, true)
                && !HardwareStabilizer.evaluateBatteryThermalThrottle(38.9f, true);

        check("Gate8_UnstableHardwareStabilizer",
                ch1RollOk && ch2ProtOk && ch3MainOk && notchOk && thermalRcOk && battHystOk
                        && stabQr.thdPlusNPercent >= 0.0 && stabQr.thdPlusNPercent < 0.01 && stabQr.peak < 0.80,
                String.format(Locale.US, "ch1=%s ch2=%s ch3Main=%s notch=%s thermalLoad=%.2f derate=%.2fdB cool=%.2f batt=%s hotPureTHD=%.6f%%",
                        ch1RollOk, ch2ProtOk, ch3MainOk, notchOk, load, derateHot, cooledLoad, battHystOk, stabQr.thdPlusNPercent));

        System.out.println("\nSUMMARY: PASSED=" + sPassed + " FAILED=" + sFailed);
        if (sFailed > 0) System.exit(1);
    }
}
