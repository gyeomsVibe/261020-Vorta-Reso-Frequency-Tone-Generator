import com.vorta.freqgen.PureDspEngine;
import com.vorta.freqgen.SweepPresetStore;
import com.vorta.unified.PureDspCore;
import com.vorta.unified.ScenarioCatalog;
import com.vorta.unified.SweepItem;
import com.vorta.unified.SweepStore;
import com.vorta.unified.VortaModule;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Epoch-2 Exhaustive All-Scenario Spectral & Harsh-Environment Verification Suite:
 * - Audits all 48 unified scenarios + 15 Pro presets (63 spectral cases total)
 * - Runs 10 harsh stress & engineering contract gates (24h wrap, C^1 tail, 50ms slam, 4-thread race, AUX storm, UI contracts)
 * - Outputs full 63-row report to d:\D_Workspace_NB\주파수\.work\epoch2_all_scenarios_spectral_report.md
 */
public final class TestVortaHarshAudit {
    private static final int SR = 48000;
    private static final int SETTLE_FRAMES = 48000;   // 1.0s fade-in settle
    private static final int MEASURE_FRAMES = 240000; // 5.0s steady-state spectral capture

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

    private static final class SpectralMetrics {
        boolean hasNanInf;
        double peakAbs;
        double rmsDbfs;
        double dcOffset;
        double maxSampleDelta;
        double thdPlusNPercent; // computed via exact continuous least-squares sinusoid subtraction
    }

    private static double[] solveLinearSystem(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[piv][col])) piv = r;
            }
            double[] tmp = m[col];
            m[col] = m[piv];
            m[piv] = tmp;
            double div = m[col][col];
            if (Math.abs(div) < 1e-18) return new double[n];
            for (int j = col; j <= n; j++) m[col][j] /= div;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double factor = m[r][col];
                for (int j = col; j <= n; j++) m[r][j] -= factor * m[col][j];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = m[i][n];
        return x;
    }

    private static SpectralMetrics analyzeBuffer(float[] stereo, int frames, int sr, boolean fitExactSines, double... freqs) {
        SpectralMetrics sm = new SpectralMetrics();
        double sum = 0.0;
        double prev = stereo[0];
        for (int f = 0; f < frames; f++) {
            float l = stereo[f * 2];
            float r = stereo[f * 2 + 1];
            if (Float.isNaN(l) || Float.isInfinite(l) || Float.isNaN(r) || Float.isInfinite(r)) {
                sm.hasNanInf = true;
                return sm;
            }
            double v = l;
            double av = Math.abs(v);
            if (av > sm.peakAbs) sm.peakAbs = av;
            sum += v;
            if (f > 0) {
                double d = Math.abs(v - prev);
                if (d > sm.maxSampleDelta) sm.maxSampleDelta = d;
            }
            prev = v;
        }
        sm.dcOffset = sum / frames;
        double sumSqAc = 0.0;
        for (int f = 0; f < frames; f++) {
            double ac = stereo[f * 2] - sm.dcOffset;
            sumSqAc += ac * ac;
        }
        double rms = Math.sqrt(sumSqAc / frames);
        sm.rmsDbfs = 20.0 * Math.log10(Math.max(1e-9, rms));

        if (!fitExactSines || freqs == null || freqs.length == 0) {
            sm.thdPlusNPercent = -1.0;
            return sm;
        }

        int cols = freqs.length * 2 + 1; // index 0 = true DC bias (orthogonalized jointly against arbitrary fractional cycles)
        double[][] gram = new double[cols][cols];
        double[] rhs = new double[cols];
        double[] w = new double[freqs.length];
        for (int i = 0; i < freqs.length; i++) w[i] = PureDspCore.TWO_PI * freqs[i] / sr;

        double[] basis = new double[cols];
        basis[0] = 1.0;
        for (int f = 0; f < frames; f++) {
            double y = stereo[f * 2];
            for (int m = 0; m < freqs.length; m++) {
                double ang = w[m] * f;
                basis[1 + m * 2] = Math.cos(ang);
                basis[1 + m * 2 + 1] = Math.sin(ang);
            }
            for (int r = 0; r < cols; r++) {
                rhs[r] += basis[r] * y;
                for (int c = r; c < cols; c++) {
                    gram[r][c] += basis[r] * basis[c];
                }
            }
        }
        for (int r = 0; r < cols; r++) {
            for (int c = 0; c < r; c++) gram[r][c] = gram[c][r];
        }
        double[] coeff = solveLinearSystem(gram, rhs);
        sm.dcOffset = coeff[0]; // true harmonic/electrical DC residual orthogonal to all sinusoid components

        double errSq = 0.0;
        double fundSq = 0.0;
        for (int f = 0; f < frames; f++) {
            double ac = stereo[f * 2] - coeff[0];
            double fit = 0.0;
            for (int m = 0; m < freqs.length; m++) {
                double ang = w[m] * f;
                fit += coeff[1 + m * 2] * Math.cos(ang) + coeff[1 + m * 2 + 1] * Math.sin(ang);
            }
            double res = ac - fit;
            errSq += res * res;
            fundSq += fit * fit;
        }
        sm.thdPlusNPercent = (fundSq > 1e-12) ? (100.0 * Math.sqrt(errSq / fundSq)) : 100.0;
        return sm;
    }

    private static void auditAll63Scenarios(String reportOutPath) throws Exception {
        List<String> rows = new ArrayList<String>();
        int scOk = 0;
        int scFail = 0;
        double maxObsPeak = 0.0;
        double worstPureThd = 0.0;

        float[] settleBuf = new float[SETTLE_FRAMES * 2];
        float[] measBuf = new float[MEASURE_FRAMES * 2];

        // 1) All 48 merged catalog scenarios
        List<ScenarioCatalog.ScenarioSpec> all = ScenarioCatalog.all();
        for (int i = 0; i < all.size(); i++) {
            ScenarioCatalog.ScenarioSpec sp = all.get(i);
            PureDspCore.DspState st = new PureDspCore.DspState();
            PureDspCore.renderUnifiedChunk(
                    settleBuf, SETTLE_FRAMES, SR, false,
                    sp, false, 0f, false, 0f, null, st,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, st);
            PureDspCore.renderUnifiedChunk(
                    measBuf, MEASURE_FRAMES, SR, false,
                    sp, false, 0f, false, 0f, null, st,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, st);

            boolean pureSine = !sp.isSweep && !sp.fmOn && !sp.amOn && !sp.rmOn;
            double[] fqs = (sp.secondaryHz >= 0.5f)
                    ? new double[]{sp.primaryHz, sp.secondaryHz}
                    : new double[]{sp.primaryHz};
            SpectralMetrics sm = analyzeBuffer(measBuf, MEASURE_FRAMES, SR, pureSine, fqs);
            if (sm.peakAbs > maxObsPeak) maxObsPeak = sm.peakAbs;
            if (pureSine && sm.thdPlusNPercent > worstPureThd) worstPureThd = sm.thdPlusNPercent;

            float minF = sp.isSweep ? Math.min(sp.sweepMinHz, sp.sweepMaxHz) : (sp.secondaryHz > 0f ? Math.min(sp.primaryHz, sp.secondaryHz) : sp.primaryHz);
            double lowSpec = minF;
            if (sp.fmOn && sp.fmRateHz > 0f) lowSpec = Math.min(lowSpec, Math.abs(minF - sp.fmRateHz));
            double dcLimit = (lowSpec < 4.0) ? 0.065 : (pureSine ? 1e-4 : 0.015);
            boolean ok = !sm.hasNanInf
                    && sm.peakAbs <= PureDspCore.TRUE_PEAK_HEADROOM + 1e-5
                    && sm.peakAbs >= 0.40
                    && sm.rmsDbfs >= -11.5 && sm.rmsDbfs <= -3.8
                    && Math.abs(sm.dcOffset) <= dcLimit
                    && sm.maxSampleDelta <= 0.015
                    && (!pureSine || sm.thdPlusNPercent < 0.005);

            if (ok) scOk++;
            else {
                scFail++;
                System.err.println(String.format(Locale.US,
                        "SCENARIO FAIL [%s] peak=%.5f rms=%.2f dc=%.6f dMax=%.5f thd=%.6f",
                        sp.id, sm.peakAbs, sm.rmsDbfs, sm.dcOffset, sm.maxSampleDelta, sm.thdPlusNPercent));
            }
            String thdStr = pureSine ? String.format(Locale.US, "%.6f%%", sm.thdPlusNPercent) : "N/A(Mod/Swp)";
            rows.add(String.format(Locale.US,
                    "| %s | %s | %s | %.5f | %.2f | %.6f | %.5f | %s | %s |",
                    sp.id, sp.module.name(), sp.formatSpecLine().replace("|", "/"),
                    sm.peakAbs, sm.rmsDbfs, sm.dcOffset, sm.maxSampleDelta, thdStr, ok ? "PASS" : "FAIL"));
        }

        // 2) 15 Pro Presets (3 ISO packs + 5 schedule bands + 3 HW suites + 4 representative matrix/manual cases)
        class ProCase {
            final String id;
            final String desc;
            final boolean pure;
            final double[] freqs;
            final boolean manual;
            final boolean dual;
            final float mHz;
            final float mHz2;
            final SweepItem[] sweeps;

            ProCase(String id, String desc, boolean pure, double[] freqs, boolean manual, boolean dual, float mHz, float mHz2, SweepItem... sweeps) {
                this.id = id;
                this.desc = desc;
                this.pure = pure;
                this.freqs = freqs;
                this.manual = manual;
                this.dual = dual;
                this.mHz = mHz;
                this.mHz2 = mHz2;
                this.sweeps = sweeps;
            }
        }

        ProCase[] proCases = new ProCase[]{
                new ProCase("pro_iso16283_pack", "ISO 16283 40~50 + 50~63Hz", false, null, false, false, 0f, 0f,
                        sw(1, 40f, 50f, 360f, 0f), sw(2, 50f, 63f, 480f, 0f)),
                new ProCase("pro_iso2631_pack", "ISO 2631 4~8 + 8~12 + 30~50Hz", false, null, false, false, 0f, 0f,
                        sw(1, 4f, 8f, 600f, 0f), sw(2, 8f, 12f, 480f, 0f), sw(3, 30f, 50f, 420f, 0f)),
                new ProCase("pro_iso226_beat_pack", "ISO 226 Dual Beats 24+28 & 48+52Hz", true, new double[]{24.0, 28.0, 48.0, 52.0}, false, false, 0f, 0f,
                        sw(1, 24f, 24f, 600f, 28f), sw(2, 48f, 48f, 600f, 52f)),
                new ProCase("pro_band_1_4", "Schedule Band 1~4Hz", false, null, false, false, 0f, 0f, sw(1, 1f, 4f, 20f, 0f)),
                new ProCase("pro_band_5_11", "Schedule Band 5~11Hz", false, null, false, false, 0f, 0f, sw(1, 5f, 11f, 35f, 0f)),
                new ProCase("pro_band_12_22", "Schedule Band 12~22Hz", false, null, false, false, 0f, 0f, sw(1, 12f, 22f, 55f, 0f)),
                new ProCase("pro_band_27_36", "Schedule Band 27~36Hz", false, null, false, false, 0f, 0f, sw(1, 27f, 36f, 50f, 0f)),
                new ProCase("pro_band_38_55", "Schedule Band 38~55Hz", false, null, false, false, 0f, 0f, sw(1, 38f, 55f, 90f, 0f)),
                new ProCase("pro_hw_s6_suite", "HW Suite S6 (17.5~19.8 + 4~6.2 + 20.7~27.7)", false, null, false, false, 0f, 0f,
                        sw(1, 17.5f, 19.8f, 440f, 0f), sw(2, 4f, 6.2f, 290f, 0f), sw(3, 20.7f, 27.7f, 740f, 0f)),
                new ProCase("pro_hw_s7_suite", "HW Suite S7 (21~24.5 + 2.4~4.6 + 28Hz)", false, null, false, false, 0f, 0f,
                        sw(1, 21f, 24.5f, 1250f, 0f), sw(2, 2.4f, 4.6f, 1480f, 0f), sw(3, 28f, 28f, 3600f, 0f)),
                new ProCase("pro_hw_a30_suite", "HW Suite A30 (24~36 + 5~24.5 + 55Hz)", false, null, false, false, 0f, 0f,
                        sw(1, 24f, 36f, 6240f, 0f), sw(2, 5f, 24.5f, 12000f, 0f), sw(3, 55f, 55f, 3600f, 0f)),
                new ProCase("pro_matrix_7_83", "Manual Pure 7.83Hz", true, new double[]{7.83}, true, false, 7.83f, 0f),
                new ProCase("pro_matrix_18_98", "Manual Pure 18.98Hz", true, new double[]{18.98}, true, false, 18.98f, 0f),
                new ProCase("pro_matrix_43_2", "Manual Pure 43.20Hz", true, new double[]{43.20}, true, false, 43.20f, 0f),
                new ProCase("pro_dual_28_32", "Manual Dual Beat 28+32Hz", true, new double[]{28.0, 32.0}, true, true, 28.0f, 32.0f)
        };

        for (ProCase pc : proCases) {
            PureDspCore.DspState st = new PureDspCore.DspState();
            List<SweepItem> list = new ArrayList<SweepItem>();
            for (SweepItem s : pc.sweeps) list.add(s);
            PureDspCore.renderUnifiedChunk(settleBuf, SETTLE_FRAMES, SR, false,
                    null, pc.manual, pc.mHz, pc.dual, pc.mHz2, list, st,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, st);
            PureDspCore.renderUnifiedChunk(measBuf, MEASURE_FRAMES, SR, false,
                    null, pc.manual, pc.mHz, pc.dual, pc.mHz2, list, st,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, st);

            SpectralMetrics sm = analyzeBuffer(measBuf, MEASURE_FRAMES, SR, pc.pure, pc.freqs);
            if (sm.peakAbs > maxObsPeak) maxObsPeak = sm.peakAbs;
            if (pc.pure && sm.thdPlusNPercent > worstPureThd) worstPureThd = sm.thdPlusNPercent;

            boolean multi = (!pc.manual && pc.sweeps.length > 1) || (pc.freqs != null && pc.freqs.length >= 2);
            double minRms = multi ? -14.0 : -11.5;
            double minPeak = multi ? 0.35 : 0.40;
            boolean ok = !sm.hasNanInf
                    && sm.peakAbs <= PureDspCore.TRUE_PEAK_HEADROOM + 1e-5
                    && sm.peakAbs >= minPeak
                    && sm.rmsDbfs >= minRms && sm.rmsDbfs <= -3.8
                    && Math.abs(sm.dcOffset) <= (pc.pure ? 5e-4 : 0.065)
                    && sm.maxSampleDelta <= 0.015
                    && (!pc.pure || sm.thdPlusNPercent < 0.005);

            if (ok) scOk++;
            else {
                scFail++;
                System.err.println(String.format(Locale.US,
                        "PRO FAIL [%s] peak=%.5f rms=%.2f dc=%.6f dMax=%.5f thd=%.6f",
                        pc.id, sm.peakAbs, sm.rmsDbfs, sm.dcOffset, sm.maxSampleDelta, sm.thdPlusNPercent));
            }
            String thdStr = pc.pure ? String.format(Locale.US, "%.6f%%", sm.thdPlusNPercent) : "N/A(Swp/Sum)";
            rows.add(String.format(Locale.US,
                    "| %s | PRO_MANUAL | %s | %.5f | %.2f | %.6f | %.5f | %s | %s |",
                    pc.id, pc.desc, sm.peakAbs, sm.rmsDbfs, sm.dcOffset, sm.maxSampleDelta, thdStr, ok ? "PASS" : "FAIL"));
        }

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(reportOutPath), StandardCharsets.UTF_8))) {
            pw.println("# Epoch-2 All-Scenario Spectral Purity & Headroom Audit (" + rows.size() + " Cases @ 48000 Hz)");
            pw.println();
            pw.println(String.format(Locale.US, "- **Result**: PASSED=%d, FAILED=%d, MaxPeak=%.5f (-1.0 dBTP limit=%.5f), WorstPureSineTHD+N=%.6f%%",
                    scOk, scFail, maxObsPeak, PureDspCore.TRUE_PEAK_HEADROOM, worstPureThd));
            pw.println();
            pw.println("| ID | Module | Waveform / Band | Peak | RMS (dBFS) | DC Offset | MaxDelta | THD+N (Pure) | Status |");
            pw.println("|---|---|---|---|---|---|---|---|---|");
            for (String row : rows) pw.println(row);
        }

        check("Epoch2_AllScenarios_SpectralAudit", scFail == 0 && rows.size() == 183,
                String.format(Locale.US, "total=%d passed=%d failed=%d maxPeak=%.5f worstPureTHD=%.6f%%",
                        rows.size(), scOk, scFail, maxObsPeak, worstPureThd));
    }

    private static SweepItem sw(int id, float s, float e, float dur, float sec2) {
        SweepItem it = new SweepItem(id, "sw" + id, s, e, dur, true, sec2);
        it.isPlaying = true;
        return it;
    }

    private static void testHarshStressGates() throws Exception {
        // Gate H1: 24-hour continuous NCO phase-wrap endurance (4,147,200,000 samples @ 48kHz)
        PureDspCore.DspState stH1 = new PureDspCore.DspState();
        double bigPhase = (PureDspCore.TWO_PI * 55.0 * 4147200000L) / (double) SR;
        stH1.scenPhase1 = PureDspCore.wrapPhase(bigPhase);
        stH1.scenSmoothedHz = 55.0;
        stH1.envGain = 1.0;
        stH1.lastScenId = "s6_sched_1";
        ScenarioCatalog.ScenarioSpec clean55 = new ScenarioCatalog.ScenarioSpec(
                "h1_55", VortaModule.S6_BTL, ScenarioCatalog.Category.MAIN,
                "55Hz", "55Hz", "g", false, 55.0f, 0f, 0f, 0f, 0f,
                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, 2);
        float[] h1Buf = new float[MEASURE_FRAMES * 2];
        PureDspCore.renderUnifiedChunk(h1Buf, MEASURE_FRAMES, SR, false,
                clean55, false, 0f, false, 0f, null, stH1,
                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, stH1);
        SpectralMetrics smH1 = analyzeBuffer(h1Buf, MEASURE_FRAMES, SR, true, 55.0);
        check("Harsh_G1_24hPhaseWrapEndurance",
                !smH1.hasNanInf && smH1.thdPlusNPercent < 0.005 && Math.abs(smH1.dcOffset) < 1e-5
                        && smH1.peakAbs <= PureDspCore.TRUE_PEAK_HEADROOM + 1e-5,
                String.format(Locale.US, "THD+N=%.6f%% Peak=%.5f DC=%.7f", smH1.thdPlusNPercent, smH1.peakAbs, smH1.dcOffset));

        // Gate H2: Zero-click C^1 scenario-stop & AUX-mute fade-out tails + Pro matchTailPhase check
        PureDspCore.DspState stH2 = new PureDspCore.DspState();
        // Drive 55 Hz until near peak positive slope so frozen phase would otherwise leave massive DC!
        int runFrames = 48000 + 218; // ends mid-cycle away from zero
        float[] drive = new float[runFrames * 2];
        PureDspCore.renderUnifiedChunk(drive, runFrames, SR, false,
                clean55, false, 0f, false, 0f, null, stH2,
                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, stH2);
        float lastOut = drive[(runFrames - 1) * 2];
        float[] tailStop = new float[24000 * 2]; // 0.5s > 0.4s fade-out
        PureDspCore.renderUnifiedChunk(tailStop, 24000, SR, false,
                null, false, 0f, false, 0f, null, stH2,
                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, stH2);
        double stopJump = Math.abs(tailStop[0] - lastOut);
        SpectralMetrics smStop = analyzeBuffer(tailStop, 24000, SR, false);
        float endTail = tailStop[(24000 - 1) * 2];
        double phiUp = PureDspEngine.matchTailPhase(Math.sin(0.6), Math.sin(0.7));
        double phiDown = PureDspEngine.matchTailPhase(Math.sin(Math.PI - 0.7), Math.sin(Math.PI - 0.6));
        boolean phaseMatchOk = Math.abs(Math.sin(phiUp) - Math.sin(0.7)) < 1e-9 && Math.cos(phiUp) > 0
                && Math.abs(Math.sin(phiDown) - Math.sin(Math.PI - 0.6)) < 1e-9 && Math.cos(phiDown) < 0;
        check("Harsh_G2_ZeroClick_C1_FadeOutTail",
                stopJump <= 0.012 && smStop.maxSampleDelta <= 0.012 && Math.abs(smStop.dcOffset) < 0.002
                        && endTail == 0.0f && phaseMatchOk,
                String.format(Locale.US, "lastOut=%.4f stopJump=%.5f maxDelta=%.5f tailDC=%.6f end=%.1f phaseMatch=%s",
                        lastOut, stopJump, smStop.maxSampleDelta, smStop.dcOffset, endTail, phaseMatchOk));

        // Gate H3: Rapid 48-scenario 50ms slam switching (zero gaps, mixes single/dual/sweep/FM/AM/RM)
        PureDspCore.DspState stH3 = new PureDspCore.DspState();
        List<ScenarioCatalog.ScenarioSpec> allScen = ScenarioCatalog.all();
        float[] slamBlock = new float[2400 * 2]; // 50ms @ 48kHz
        double slamWorstDelta = 0.0;
        double slamPeak = 0.0;
        boolean slamNan = false;
        float prevEdge = 0.0f;
        for (int i = 0; i < allScen.size(); i++) {
            PureDspCore.renderUnifiedChunk(slamBlock, 2400, SR, false,
                    allScen.get(i), false, 0f, false, 0f, null, stH3,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, stH3);
            if (i > 0) {
                double edgeJump = Math.abs(slamBlock[0] - prevEdge);
                if (edgeJump > slamWorstDelta) slamWorstDelta = edgeJump;
            }
            SpectralMetrics sm = analyzeBuffer(slamBlock, 2400, SR, false);
            if (sm.hasNanInf) slamNan = true;
            if (sm.maxSampleDelta > slamWorstDelta) slamWorstDelta = sm.maxSampleDelta;
            if (sm.peakAbs > slamPeak) slamPeak = sm.peakAbs;
            prevEdge = slamBlock[(2400 - 1) * 2];
        }
        check("Harsh_G3_48Scenario_50ms_SlamSwitch",
                !slamNan && slamPeak <= PureDspCore.TRUE_PEAK_HEADROOM + 1e-5 && slamWorstDelta <= 0.015,
                String.format(Locale.US, "scenarios=%d maxDelta=%.5f maxPeak=%.5f", allScen.size(), slamWorstDelta, slamPeak));

        // Gate H4: 8-way stacked concurrent sweeps + manual dual-sine + max FM/AM/RM overload
        PureDspCore.DspState stH4 = new PureDspCore.DspState();
        List<SweepItem> stack8 = new ArrayList<SweepItem>();
        for (int i = 1; i <= 8; i++) stack8.add(sw(i, 5f * i, 63f, 20f * i, (i % 2 == 0) ? 55f : 0f));
        float[] ovBuf = new float[48000 * 2];
        PureDspCore.renderUnifiedChunk(ovBuf, 48000, SR, false,
                null, true, 63.0f, true, 59.5f, stack8, stH4,
                true, 0.25f, 15.0f, true, 0.50f, 15.0f, true, 0.50f, 60.0f, stH4);
        PureDspCore.renderUnifiedChunk(ovBuf, 48000, SR, false,
                null, true, 63.0f, true, 59.5f, stack8, stH4,
                true, 0.25f, 15.0f, true, 0.50f, 15.0f, true, 0.50f, 60.0f, stH4);
        SpectralMetrics smH4 = analyzeBuffer(ovBuf, 48000, SR, false);
        check("Harsh_G4_8WayStackPlusMaxModOverload",
                !smH4.hasNanInf && smH4.peakAbs <= PureDspCore.TRUE_PEAK_HEADROOM + 1e-5 && smH4.maxSampleDelta <= 0.025,
                String.format(Locale.US, "sources=9 Peak=%.5f maxDelta=%.5f RMS=%.2fdBFS", smH4.peakAbs, smH4.maxSampleDelta, smH4.rmsDbfs));

        // Gate H5: 3.5mm AUX chattering storm (100 glitches < 250ms never mute; >=250ms mutes; -6/-3 rebuild)
        PureDspCore.AuxDebouncePolicy deb = new PureDspCore.AuxDebouncePolicy();
        boolean falseMute = false;
        long t = 1000L;
        for (int b = 0; b < 100; b++) {
            if (deb.onHardwareDisconnected(t, false)) falseMute = true;
            if (deb.pollDebounceExpired(t + 10 + (b % 230), false, false)) falseMute = true;
            deb.onHardwareConnected();
            if (deb.pollDebounceExpired(t + 250, true, false)) falseMute = true;
            t += 300L;
        }
        boolean realMute = !deb.onHardwareDisconnected(t, false) && deb.pollDebounceExpired(t + 255L, false, false);
        boolean rebuild = PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(-6)
                && PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(-3)
                && !PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(2880 * 2);
        check("Harsh_G5_AuxChatter100xStormAndDeadRebuild", !falseMute && realMute && rebuild,
                "falseMute=" + falseMute + " realMute=" + realMute + " rebuild=" + rebuild);

        // Gate H6: 4-thread concurrent SweepItem mutation + DSP render torture (500ms live race)
        final List<SweepItem> sharedList = new ArrayList<SweepItem>();
        final Object sharedLock = new Object();
        final AtomicInteger raceErrors = new AtomicInteger(0);
        final AtomicInteger renderedChunks = new AtomicInteger(0);
        final long endAt = System.currentTimeMillis() + 600L;
        Thread mut1 = new Thread(new Runnable() {
            @Override public void run() {
                int k = 1;
                while (System.currentTimeMillis() < endAt) {
                    try {
                        synchronized (sharedLock) {
                            if (sharedList.size() > 6) sharedList.clear();
                            sharedList.add(sw(k++, 10f, 45f, 30f, 0f));
                        }
                        Thread.sleep(2);
                    } catch (Throwable ex) { raceErrors.incrementAndGet(); }
                }
            }
        });
        Thread mut2 = new Thread(new Runnable() {
            @Override public void run() {
                while (System.currentTimeMillis() < endAt) {
                    try {
                        synchronized (sharedLock) {
                            for (SweepItem it : sharedList) it.isPlaying = !it.isPlaying;
                        }
                        Thread.sleep(3);
                    } catch (Throwable ex) { raceErrors.incrementAndGet(); }
                }
            }
        });
        Thread dspThr = new Thread(new Runnable() {
            @Override public void run() {
                PureDspCore.DspState dst = new PureDspCore.DspState();
                float[] b = new float[2880 * 2];
                while (System.currentTimeMillis() < endAt) {
                    try {
                        PureDspCore.renderUnifiedChunk(b, 2880, SR, false,
                                null, false, 0f, false, 0f, sharedList, sharedLock,
                                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, dst);
                        renderedChunks.incrementAndGet();
                    } catch (Throwable ex) { raceErrors.incrementAndGet(); }
                }
            }
        });
        mut1.start(); mut2.start(); dspThr.start();
        mut1.join(3000L); mut2.join(3000L); dspThr.join(3000L);
        check("Harsh_G6_MultiThreadConcurrentRenderTorture",
                raceErrors.get() == 0 && renderedChunks.get() > 10,
                "errors=" + raceErrors.get() + " chunks=" + renderedChunks.get());

        // Gate H7: Accordion Menu Module Order Contract (1. Pro -> 2. A30 -> 3. S7 -> 4. S6)
        VortaModule[] mods = VortaModule.values();
        boolean orderOk = mods.length == 4
                && mods[0] == VortaModule.PRO_MANUAL && mods[0].shortLabel.equals("1. 보르타 Pro")
                && mods[1] == VortaModule.A30_SWEEP && mods[1].shortLabel.equals("2. 갤럭시 A30")
                && mods[2] == VortaModule.S7_ANCHOR && mods[2].shortLabel.equals("3. 갤럭시 S7")
                && mods[3] == VortaModule.S6_BTL && mods[3].shortLabel.equals("4. 갤럭시 S6");
        check("Harsh_G7_AccordionMenuOrder_Pro_A30_S7_S6", orderOk,
                mods[0].shortLabel + " | " + mods[1].shortLabel + " | " + mods[2].shortLabel + " | " + mods[3].shortLabel);

        // Gate H8: Zombie Preset 50-Cycle Survival
        boolean zombieOk = true;
        for (int cyc = 0; cyc < 50; cyc++) {
            List<SweepItem> u = new ArrayList<SweepItem>();
            SweepStore.loadFromState(false, null, u);
            u.clear();
            String eJson = SweepStore.serializeSweeps(u);
            List<SweepItem> ur = new ArrayList<SweepItem>();
            boolean reSeed = SweepStore.loadFromState(true, eJson, ur);
            if (reSeed || !ur.isEmpty()) zombieOk = false;

            List<com.vorta.freqgen.SweepItem> p = new ArrayList<com.vorta.freqgen.SweepItem>();
            SweepPresetStore.loadFromState(false, null, p);
            p.clear();
            String pJson = SweepPresetStore.serializeSweeps(p);
            List<com.vorta.freqgen.SweepItem> pr = new ArrayList<com.vorta.freqgen.SweepItem>();
            boolean pReSeed = SweepPresetStore.loadFromState(true, pJson, pr);
            if (pReSeed || !pr.isEmpty()) zombieOk = false;
        }
        check("Harsh_G8_ZombiePreset50CycleSurvival", zombieOk, "cycles=50");

        // Gate H9: 16-bit TPDF Quantization Spectral Purity @ 43.2Hz
        PureDspCore.DspState qst = new PureDspCore.DspState();
        qst.envGain = 1.0;
        float[] qf = new float[MEASURE_FRAMES * 2];
        PureDspCore.renderPureSineFloat(qf, MEASURE_FRAMES, SR, 43.2, qst);
        short[] qs = new short[MEASURE_FRAMES * 2];
        PureDspCore.quantizeToPcm16Tpdf(qf, qs, MEASURE_FRAMES * 2, qst);
        float[] back = new float[MEASURE_FRAMES * 2];
        int maxAbs16 = 0;
        for (int i = 0; i < qs.length; i++) {
            int a = Math.abs((int) qs[i]);
            if (a > maxAbs16) maxAbs16 = a;
            back[i] = qs[i] / 32767.0f;
        }
        SpectralMetrics sm16 = analyzeBuffer(back, MEASURE_FRAMES, SR, true, 43.2);
        check("Harsh_G9_16BitTpdfSpectralPurity",
                maxAbs16 > 28000 && maxAbs16 <= 32767 && Math.abs(sm16.dcOffset) < 5e-4 && sm16.thdPlusNPercent < 0.005,
                String.format(Locale.US, "maxAbs16=%d DC=%.6f THD+N=%.6f%%", maxAbs16, sm16.dcOffset, sm16.thdPlusNPercent));

        // Gate H10: Source Static UI & Parity Audit (5-tab headings, LiveWaveformView, zero ISO/(NEW), pure locked modulation)
        String uniMain = new String(Files.readAllBytes(Paths.get("d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/MainActivity.java")), StandardCharsets.UTF_8);
        String proMain = new String(Files.readAllBytes(Paths.get("d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/MainActivity.java")), StandardCharsets.UTF_8);
        String catSrc = new String(Files.readAllBytes(Paths.get("d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/ScenarioCatalog.java")), StandardCharsets.UTF_8);
        String modSrc = new String(Files.readAllBytes(Paths.get("d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/ModulationProfile.java")), StandardCharsets.UTF_8);
        boolean noEmoji = !hasExtendedEmoji(uniMain) && !hasExtendedEmoji(proMain) && !hasExtendedEmoji(catSrc) && !hasExtendedEmoji(modSrc);
        boolean noDefExp = !Pattern.compile("defaultExpanded\\s*=\\s*true").matcher(uniMain).find()
                && !Pattern.compile("defaultExpanded\\s*=\\s*true").matcher(proMain).find();
        boolean hasKeepOn = uniMain.contains("FLAG_KEEP_SCREEN_ON") && uniMain.contains("화면유지");
        boolean hasAllProAccordions = uniMain.contains("인체 공진 매트릭스 단일 프리셋")
                && uniMain.contains("기기별 특화 프리셋")
                && uniMain.contains("시간·상황·전술 시나리오")
                && uniMain.contains("f2 적용");
        boolean noIsoOrNew = !uniMain.contains("(NEW)") && !proMain.contains("(NEW)") && !catSrc.contains("(NEW)")
                && !uniMain.contains("ISO ") && !uniMain.contains("ISO1") && !uniMain.contains("ISO2")
                && !proMain.contains("ISO ") && !proMain.contains("ISO1") && !proMain.contains("ISO2")
                && !catSrc.contains("[ISO") && !catSrc.contains("ISO ");
        boolean hasFiveTabsAndScope = uniMain.contains("LiveWaveformView")
                && catSrc.contains("MAIN(\"메인\")") && catSrc.contains("SUB1(\"서브1\")")
                && catSrc.contains("SUB2(\"서브2\")") && catSrc.contains("COMMON(\"공통\")")
                && catSrc.contains("BASIC(\"기초\")");
        boolean modLockedNoSeekBar = !uniMain.contains("SeekBar") && uniMain.contains("ModulationProfile")
                && uniMain.contains("mTvTeleMod");
        boolean pureModNoScenarios = !modSrc.contains("아침") && !modSrc.contains("오후") && !modSrc.contains("저녁")
                && !modSrc.contains("취침") && !modSrc.contains("외출") && !modSrc.contains("출근")
                && !modSrc.contains("요격") && !modSrc.contains("응징") && !modSrc.contains("전술")
                && !modSrc.contains("시간대별") && !modSrc.contains("상황별");
        check("Harsh_G10_SourceStaticUiAndProParity",
                noEmoji && noDefExp && hasKeepOn && hasAllProAccordions && noIsoOrNew && hasFiveTabsAndScope
                        && modLockedNoSeekBar && pureModNoScenarios,
                "noEmoji=" + noEmoji + " noDefExp=" + noDefExp + " keepScreenOn=" + hasKeepOn
                        + " fullPro=" + hasAllProAccordions + " noIsoOrNew=" + noIsoOrNew
                        + " fiveTabsScope=" + hasFiveTabsAndScope + " modLockedNoSeekBar=" + modLockedNoSeekBar
                        + " pureModNoScenarios=" + pureModNoScenarios);

        // Gate H11 (Vaccine): Objective live buffer analyzer catches clipping & harmonic distortion (zero hallucination)
        int snapLen = 4096;
        PureDspCore.DspState vst = new PureDspCore.DspState();
        vst.envGain = 1.0;
        float[] vStereo = new float[snapLen * 2];
        PureDspCore.renderPureSineFloat(vStereo, snapLen, SR, 43.2, vst);
        float[] vMonoClean = new float[snapLen];
        for (int i = 0; i < snapLen; i++) vMonoClean[i] = vStereo[i * 2];
        PureDspCore.LiveQualityReport cleanRep = PureDspCore.measureLiveBufferQuality(
                vMonoClean, snapLen, SR, true, 43.2, 0.0);

        // Inject synthetic clipping + 3rd harmonic distortion -> verifier MUST detect & fail pure quality check
        float[] vMonoCorrupt = new float[snapLen];
        for (int i = 0; i < snapLen; i++) {
            double h3 = 0.15 * Math.sin(PureDspCore.TWO_PI * (43.2 * 3.0) * i / (double) SR);
            vMonoCorrupt[i] = vMonoClean[i] + (float) h3;
        }
        vMonoCorrupt[120] = 1.05f;
        PureDspCore.LiveQualityReport badRep = PureDspCore.measureLiveBufferQuality(
                vMonoCorrupt, snapLen, SR, true, 43.2, 0.0);
        boolean h11Ok = cleanRep.clipOrNanCount == 0 && cleanRep.thdPlusNPercent >= 0.0 && cleanRep.thdPlusNPercent < 0.01
                && Math.abs(cleanRep.peak - PureDspCore.TRUE_PEAK_HEADROOM) < 0.01
                && badRep.clipOrNanCount >= 1 && badRep.thdPlusNPercent > 10.0;
        check("Harsh_G11_LiveSignalVerifierNoHallucination", h11Ok,
                String.format(Locale.US, "cleanTHD=%.5f%% clip=%d | corruptTHD=%.2f%% clip=%d",
                        cleanRep.thdPlusNPercent, cleanRep.clipOrNanCount, badRep.thdPlusNPercent, badRep.clipOrNanCount));

        // Gate H12: Locked pure modulation (3 channels, 8 states) + moved 12 Pro operational scenarios in ScenarioCatalog
        List<com.vorta.unified.ModulationProfile.ChannelSpec> chs = com.vorta.unified.ModulationProfile.channels();
        boolean mpRenderOk = (chs.size() == 3);
        double mpMaxPeak = 0.0;
        float[] mpBuf = new float[MEASURE_FRAMES * 2];
        double pureZeroThd = 0.0;
        for (int mask = 0; mask < 8; mask++) {
            boolean fmOn = (mask & 1) != 0;
            boolean amOn = (mask & 2) != 0;
            boolean rmOn = (mask & 4) != 0;
            PureDspCore.DspState mst = new PureDspCore.DspState();
            mst.envGain = 1.0;
            PureDspCore.renderUnifiedChunk(
                    mpBuf, MEASURE_FRAMES, SR, false,
                    null, true, 28.0f, false, 0.0f, null, mst,
                    fmOn, com.vorta.unified.ModulationProfile.LOCKED_FM_DEPTH, com.vorta.unified.ModulationProfile.LOCKED_FM_RATE_HZ,
                    amOn, com.vorta.unified.ModulationProfile.LOCKED_AM_DEPTH, com.vorta.unified.ModulationProfile.LOCKED_AM_RATE_HZ,
                    rmOn, com.vorta.unified.ModulationProfile.LOCKED_RM_DEPTH, com.vorta.unified.ModulationProfile.LOCKED_RM_RATE_HZ, mst);
            SpectralMetrics sm = analyzeBuffer(mpBuf, MEASURE_FRAMES, SR, mask == 0, 28.0);
            if (sm.hasNanInf || sm.peakAbs > PureDspCore.TRUE_PEAK_HEADROOM + 1e-5) mpRenderOk = false;
            if (mask == 0) {
                pureZeroThd = sm.thdPlusNPercent;
                if (sm.thdPlusNPercent >= 0.001) mpRenderOk = false;
            }
            if (sm.peakAbs > mpMaxPeak) mpMaxPeak = sm.peakAbs;
        }
        List<ScenarioCatalog.ScenarioSpec> proScens = ScenarioCatalog.proScenarios();
        if (proScens.size() != 12) mpRenderOk = false;
        float[] psBuf = new float[24000 * 2];
        for (ScenarioCatalog.ScenarioSpec ps : proScens) {
            if (ScenarioCatalog.findById(ps.id) == null
                    || hasExtendedEmoji(ps.title) || hasExtendedEmoji(ps.effectDesc)
                    || ps.title.contains("(NEW)") || ps.title.contains("ISO") || ps.title.contains("↔")) {
                mpRenderOk = false;
            }
            PureDspCore.DspState pst = new PureDspCore.DspState();
            pst.envGain = 1.0;
            PureDspCore.renderUnifiedChunk(
                    psBuf, 24000, SR, false,
                    ps, false, 0f, false, 0f, null, pst,
                    false, 0f, 0f, false, 0f, 0f, false, 0f, 0f, pst);
            SpectralMetrics sm = analyzeBuffer(psBuf, 24000, SR, false);
            if (sm.hasNanInf || sm.peakAbs > PureDspCore.TRUE_PEAK_HEADROOM + 1e-5) mpRenderOk = false;
            if (sm.peakAbs > mpMaxPeak) mpMaxPeak = sm.peakAbs;
        }
        if (!"변조: OFF (순수 정현파)".equals(com.vorta.unified.VortaAudioService.formatModulationSpec(
                false, 0f, 0f, false, 0f, 0f, false, 0f, 0f))) {
            mpRenderOk = false;
        }
        String fmTele = com.vorta.unified.VortaAudioService.formatModulationSpec(
                true, com.vorta.unified.ModulationProfile.LOCKED_FM_DEPTH, com.vorta.unified.ModulationProfile.LOCKED_FM_RATE_HZ,
                false, 0f, 0f, false, 0f, 0f);
        if (!fmTele.contains("FM 3.5%(1.50Hz)")) {
            mpRenderOk = false;
        }
        check("Harsh_G12_PureLockedModulationAndProScenarios12", mpRenderOk,
                String.format(Locale.US, "channels=%d combos=8 proScenarios=%d pureTHD=%.6f%% maxPeak=%.5f tele=%s",
                        chs.size(), proScens.size(), pureZeroThd, mpMaxPeak, fmTele));
    }

    private static boolean hasExtendedEmoji(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp >= 0x1F300) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        String reportPath = "d:\\D_Workspace_NB\\주파수\\.work\\epoch2_all_scenarios_spectral_report.md";
        auditAll63Scenarios(reportPath);
        testHarshStressGates();
        System.out.println(String.format(Locale.US, "\nEPOCH-2 HARSH AUDIT SUMMARY: PASSED=%d FAILED=%d", sPassed, sFailed));
        if (sFailed > 0) System.exit(1);
    }
}
