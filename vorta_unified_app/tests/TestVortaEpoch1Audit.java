import com.vorta.freqgen.PureDspEngine;
import com.vorta.freqgen.SweepItem;
import com.vorta.freqgen.SweepPresetStore;
import com.vorta.unified.PureDspCore;
import com.vorta.unified.ScenarioCatalog;
import com.vorta.unified.SweepStore;
import com.vorta.unified.VortaModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Epoch-1 Host-JVM Verification Suite (Gates 1-6):
 * Executes exact production DSP math, zombie-preset persistence, catalog invariants,
 * and AUX debounce / ERROR_DEAD_OBJECT recovery policy with zero mocks.
 */
public final class TestVortaEpoch1Audit {
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

    private static double computeThdPlusNPercent(float[] stereo, int frames, int sr, double... targetFreqs) {
        double sum = 0.0;
        for (int f = 0; f < frames; f++) {
            float x = stereo[f * 2];
            if (Float.isNaN(x) || Float.isInfinite(x)) return 100.0;
            sum += x;
        }
        double dc = sum / frames;
        double eTotal = 0.0;
        for (int f = 0; f < frames; f++) {
            double ac = stereo[f * 2] - dc;
            eTotal += ac * ac;
        }
        eTotal /= frames;

        double eFund = 0.0;
        double twoPiOverSr = 2.0 * Math.PI / (double) sr;
        for (double hz : targetFreqs) {
            double c = 0.0;
            double s = 0.0;
            double w = twoPiOverSr * hz;
            for (int f = 0; f < frames; f++) {
                double x = stereo[f * 2];
                double ang = w * f;
                c += x * Math.cos(ang);
                s += x * Math.sin(ang);
            }
            c = (2.0 * c) / frames;
            s = (2.0 * s) / frames;
            eFund += 0.5 * (c * c + s * s);
        }
        double eResid = Math.max(0.0, eTotal - eFund);
        return Math.sqrt(eResid / Math.max(1e-18, eFund)) * 100.0;
    }

    private static void testGate1PureSineFftAndHeadroom() {
        final int sr = 48000;
        final int frames = 240000; // 5.0 seconds -> exact integer cycle count for 55.0, 43.2, 28.0 Hz
        double[] testFreqs = {55.0, 43.2, 28.0};

        for (double hz : testFreqs) {
            // Pro engine
            float[] bufPro = new float[frames * 2];
            PureDspEngine.DspState stPro = new PureDspEngine.DspState();
            PureDspEngine.renderPureSineFloat(bufPro, frames, sr, hz, stPro);
            PureDspEngine.BufferStats statsPro = PureDspEngine.measureBufferStats(bufPro, frames);
            double thdPro = computeThdPlusNPercent(bufPro, frames, sr, hz);

            check(String.format(Locale.US, "Gate1_Pro_PureSine_%.1fHz", hz),
                    thdPro < 0.005 && Math.abs(statsPro.dcOffset) < 1e-4 && statsPro.peakAbs <= 0.892,
                    String.format(Locale.US, "THD+N=%.6f%% Peak=%.5f DC=%.7f", thdPro, statsPro.peakAbs, statsPro.dcOffset));

            // Unified engine
            float[] bufUni = new float[frames * 2];
            PureDspCore.DspState stUni = new PureDspCore.DspState();
            PureDspCore.renderPureSineFloat(bufUni, frames, sr, hz, stUni);
            PureDspCore.BufferStats statsUni = PureDspCore.measureBufferStats(bufUni, frames);
            double thdUni = computeThdPlusNPercent(bufUni, frames, sr, hz);

            check(String.format(Locale.US, "Gate1_Unified_PureSine_%.1fHz", hz),
                    thdUni < 0.005 && Math.abs(statsUni.dcOffset) < 1e-4 && statsUni.peakAbs <= 0.892,
                    String.format(Locale.US, "THD+N=%.6f%% Peak=%.5f DC=%.7f", thdUni, statsUni.peakAbs, statsUni.dcOffset));
        }

        // Verify TPDF dither 16-bit fallback preserves near-zero DC & headroom
        float[] fBuf = new float[48000 * 2];
        short[] p16 = new short[48000 * 2];
        PureDspCore.DspState dSt = new PureDspCore.DspState();
        PureDspCore.renderPureSineFloat(fBuf, 48000, 48000, 55.0, dSt);
        PureDspCore.quantizeToPcm16Tpdf(fBuf, p16, p16.length, dSt);
        int maxAbs16 = 0;
        long sum16 = 0;
        for (int i = 0; i < 48000; i++) {
            int v = p16[i * 2];
            if (Math.abs(v) > maxAbs16) maxAbs16 = Math.abs(v);
            sum16 += v;
        }
        double dc16 = (double) sum16 / (48000.0 * 32767.0);
        check("Gate1_TPDF_16Bit_Fallback", maxAbs16 <= 29210 && Math.abs(dc16) < 1e-3,
                "maxAbs16=" + maxAbs16 + " dc16=" + String.format(Locale.US, "%.6f", dc16));
    }

    private static void testGate2DualSineBeatingPurity() {
        final int sr = 48000;
        final int frames = 96000; // 2.0s exact cycles for 55.0 and 59.5 Hz
        float[] buf = new float[frames * 2];
        PureDspCore.DspState st = new PureDspCore.DspState();
        PureDspCore.renderDualSineFloat(buf, frames, sr, 55.0, 59.5, st);
        PureDspCore.BufferStats stats = PureDspCore.measureBufferStats(buf, frames);
        double thd = computeThdPlusNPercent(buf, frames, sr, 55.0, 59.5);
        check("Gate2_DualSine_Linear_Beat_55_59.5Hz",
                thd < 0.005 && stats.peakAbs <= 0.892 && Math.abs(stats.dcOffset) < 1e-4,
                String.format(Locale.US, "THD+N=%.6f%% Peak=%.5f", thd, stats.peakAbs));
    }

    private static void testGate3PhaseContinuousZeroClickTransition() {
        final int sr = 48000;
        final int half = 4800; // 100ms chunks
        float[] chunk1 = new float[half * 2];
        float[] chunk2 = new float[half * 2];
        PureDspCore.DspState st = new PureDspCore.DspState();
        PureDspCore.renderPureSineFloat(chunk1, half, sr, 55.0, st);
        PureDspCore.renderPureSineFloat(chunk2, half, sr, 28.0, st);

        double boundaryJump = Math.abs(chunk2[0] - chunk1[(half - 1) * 2]);
        PureDspCore.BufferStats s2 = PureDspCore.measureBufferStats(chunk2, half);
        check("Gate3_PhaseContinuous_ZeroClick_55_to_28Hz",
                boundaryJump < 0.015 && s2.maxSampleDelta < 0.015,
                String.format(Locale.US, "boundaryJump=%.5f maxDelta=%.5f", boundaryJump, s2.maxSampleDelta));
    }

    private static void testGate4ZombiePresetRegression() {
        // Pro store test
        List<SweepItem> pro = new ArrayList<SweepItem>();
        boolean seededFirst = SweepPresetStore.loadFromState(false, null, pro);
        check("Gate4_Pro_FirstLaunchSeeds3", seededFirst && pro.size() == 3, "size=" + pro.size());

        // Delete all items -> save "[]" -> restart with initialized=true
        pro.clear();
        String emptyJson = SweepPresetStore.serializeSweeps(pro);
        List<SweepItem> proAfterDeleteRestart = new ArrayList<SweepItem>();
        boolean seededRestart = SweepPresetStore.loadFromState(true, emptyJson, proAfterDeleteRestart);
        check("Gate4_Pro_Zombie1To3NeverResurrectAfterDeleteAll",
                !seededRestart && proAfterDeleteRestart.isEmpty(),
                "emptyJson=" + emptyJson + " reloaded=" + proAfterDeleteRestart.size());

        // Custom edited sweeps roundtrip
        List<SweepItem> custom = new ArrayList<SweepItem>();
        custom.add(new SweepItem(7, "테스트 \"인용\" 스윕", 40.5f, 62.5f, 450.0f, false, 44.5f));
        custom.add(new SweepItem(9, "야간 스윕 2", 4.2f, 8.1f, 900.0f, true, 0.0f));
        String customJson = SweepPresetStore.serializeSweeps(custom);
        List<SweepItem> customReloaded = new ArrayList<SweepItem>();
        SweepPresetStore.loadFromState(true, customJson, customReloaded);
        boolean roundtripOk = customReloaded.size() == 2
                && customReloaded.get(0).id == 7
                && customReloaded.get(0).title.equals("테스트 \"인용\" 스윕")
                && Math.abs(customReloaded.get(0).startHz - 40.5f) < 1e-4
                && Math.abs(customReloaded.get(0).secondaryHz - 44.5f) < 1e-4
                && !customReloaded.get(0).isLoop
                && customReloaded.get(1).id == 9;
        check("Gate4_Pro_CustomJsonRoundtrip", roundtripOk, "count=" + customReloaded.size());

        // Unified SweepStore identical regression
        List<com.vorta.unified.SweepItem> uni = new ArrayList<com.vorta.unified.SweepItem>();
        boolean uniSeed = SweepStore.loadFromState(false, null, uni);
        uni.clear();
        String uniEmpty = SweepStore.serializeSweeps(uni);
        List<com.vorta.unified.SweepItem> uniReloaded = new ArrayList<com.vorta.unified.SweepItem>();
        boolean uniReSeed = SweepStore.loadFromState(true, uniEmpty, uniReloaded);
        check("Gate4_Unified_Zombie1To3NeverResurrect", uniSeed && !uniReSeed && uniReloaded.isEmpty(),
                "uniEmpty=" + uniEmpty);
    }

    private static boolean hasEmoji(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp >= 0x1F300) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static void testGate5ScenarioCatalogIntegrity() {
        VortaModule[] mods = VortaModule.values();
        check("Gate5_4MergedModulesPresent", mods.length == 4, "modules=" + mods.length);

        for (VortaModule m : new VortaModule[]{VortaModule.S6_BTL, VortaModule.S7_ANCHOR, VortaModule.A30_SWEEP}) {
            int mainC = ScenarioCatalog.forModule(m, ScenarioCatalog.Category.MAIN).size();
            int sub1C = ScenarioCatalog.forModule(m, ScenarioCatalog.Category.SUB1).size();
            int sub2C = ScenarioCatalog.forModule(m, ScenarioCatalog.Category.SUB2).size();
            int comC = ScenarioCatalog.forModule(m, ScenarioCatalog.Category.COMMON).size();
            int basC = ScenarioCatalog.forModule(m, ScenarioCatalog.Category.BASIC).size();
            int expSub1 = (m == VortaModule.S7_ANCHOR) ? 16 : 11;
            int expSub2 = (m == VortaModule.S7_ANCHOR) ? 16 : 10;
            check("Gate5_" + m.name() + "_Counts",
                    mainC == 20 && sub1C == expSub1 && sub2C == expSub2 && comC == 12 && basC == 22,
                    String.format(Locale.US, "main=%d sub1=%d sub2=%d com=%d bas=%d", mainC, sub1C, sub2C, comC, basC));
        }
        check("Gate5_TotalCatalog168", ScenarioCatalog.all().size() == 168, "total=" + ScenarioCatalog.all().size());

        boolean boundsOk = true;
        boolean noEmoji = true;
        boolean noClutterTags = true;
        String err = "";
        for (ScenarioCatalog.ScenarioSpec sp : ScenarioCatalog.all()) {
            if (hasEmoji(sp.title) || hasEmoji(sp.effectDesc) || hasEmoji(sp.knobGuide)) {
                noEmoji = false;
                err = "emoji in " + sp.id;
            }
            if (sp.title.contains("(NEW)") || sp.title.contains("ISO") || sp.effectDesc.contains("ISO")
                    || sp.title.contains("구버전") || sp.title.contains("시나리오")) {
                noClutterTags = false;
                err = "clutter tag in " + sp.id;
            }
            float maxF = sp.isSweep ? Math.max(sp.sweepMinHz, sp.sweepMaxHz) : Math.max(sp.primaryHz, sp.secondaryHz);
            float minF = sp.isSweep ? Math.min(sp.sweepMinHz, sp.sweepMaxHz) : (sp.secondaryHz > 0.0f ? Math.min(sp.primaryHz, sp.secondaryHz) : sp.primaryHz);
            if (maxF > 63.0f || minF < 0.5f) {
                boundsOk = false;
                err = "freq out of bounds in " + sp.id;
            }
        }
        check("Gate5_CatalogBoundsAndNoEmoji", boundsOk && noEmoji && noClutterTags,
                "total=" + ScenarioCatalog.all().size() + " " + err);
    }

    private static void testGate6AuxDebounceAndRecoveryPolicy() {
        PureDspCore.AuxDebouncePolicy deb = new PureDspCore.AuxDebouncePolicy();
        boolean m0 = deb.onHardwareDisconnected(1000L, false);
        boolean m1 = deb.pollDebounceExpired(1200L, false, false);
        boolean m2 = deb.pollDebounceExpired(1260L, false, false);
        deb.onHardwareConnected();
        boolean mReset = deb.pollDebounceExpired(1500L, true, false);
        check("Gate6_AuxDisconnectDebounce250ms", !m0 && !m1 && m2 && !mReset,
                String.format(Locale.US, "t0=%s t200=%s t260=%s reset=%s", m0, m1, m2, mReset));

        boolean rebuildDead = PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(-6);
        boolean rebuildInv = PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(-3);
        boolean okWrite = PureDspCore.AuxDebouncePolicy.shouldRebuildAudioTrack(3840);
        check("Gate6_AudioTrackDeadObjectRecovery", rebuildDead && rebuildInv && !okWrite,
                "dead=-6:" + rebuildDead + " inv=-3:" + rebuildInv + " ok:" + okWrite);
    }

    public static void main(String[] args) {
        testGate1PureSineFftAndHeadroom();
        testGate2DualSineBeatingPurity();
        testGate3PhaseContinuousZeroClickTransition();
        testGate4ZombiePresetRegression();
        testGate5ScenarioCatalogIntegrity();
        testGate6AuxDebounceAndRecoveryPolicy();

        System.out.println(String.format(Locale.US, "\nEPOCH-1 HOST AUDIT SUMMARY: PASSED=%d FAILED=%d", sPassed, sFailed));
        if (sFailed > 0) System.exit(1);
    }
}
