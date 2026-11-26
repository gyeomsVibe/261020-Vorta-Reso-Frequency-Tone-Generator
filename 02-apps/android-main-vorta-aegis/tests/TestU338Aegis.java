import com.vorta.aegis.AegisDspCore;
import com.vorta.aegis.JourneyOrderPolicy;
import com.vorta.aegis.JourneyPackCatalog;
import com.vorta.aegis.PresetAdvisor;
import com.vorta.aegis.PresetModel;
import com.vorta.aegis.ProPackCatalog;
import com.vorta.aegis.TabSyncPolicy;
import com.vorta.aegis.VolumeCurve;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestU338Aegis {
    private static int sPassed = 0;

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAIL: " + msg);
        }
        sPassed++;
    }

    private static void assertNear(double actual, double expected, double tol, String msg) {
        if (Double.isNaN(actual) || Math.abs(actual - expected) > tol) {
            throw new AssertionError(String.format(Locale.US, "FAIL: %s expected=%.8f actual=%.8f", msg, expected, actual));
        }
        sPassed++;
    }

    private static double catmullRomMax(double p0, double p1, double p2, double p3) {
        double maxTp = 0.0;
        for (int sub = 1; sub <= 3; sub++) {
            double t = sub * 0.25;
            double t2 = t * t;
            double t3 = t2 * t;
            double interp = 0.5 * ((2.0 * p1)
                    + (-p0 + p2) * t
                    + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                    + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
            double abs = Math.abs(interp);
            if (abs > maxTp) maxTp = abs;
        }
        return maxTp;
    }

    private static double verifyZeroOvershootAt120(PresetModel preset, int profileMode, int ambType, int ambMix, boolean personalAudio) {
        AegisDspCore dsp = new AegisDspCore(48000);
        dsp.setPersonalAudioConnected(personalAudio);
        dsp.setOutputProfileMode(profileMode);
        dsp.setAmbientLayer(ambType, ambMix);
        dsp.setVolume(120, false);
        dsp.playPreset(preset);

        final int blk = 480;
        float[] buf = new float[blk * 2];
        double sumSq = 0.0;
        double maxAbs = 0.0;
        double pL0 = 0.0, pL1 = 0.0, pL2 = 0.0, pL3 = 0.0;
        double pR0 = 0.0, pR1 = 0.0, pR2 = 0.0, pR3 = 0.0;
        double maxTp = 0.0;
        int totalFrames = 0;

        for (int b = 0; b < 110; b++) {
            dsp.renderStereoFloat(buf, blk);
            for (int i = 0; i < blk; i++) {
                float l = buf[i * 2];
                float r = buf[i * 2 + 1];
                assertTrue(!Float.isNaN(l) && !Float.isInfinite(l), "finite L id=" + preset.id + " prof=" + profileMode);
                assertTrue(!Float.isNaN(r) && !Float.isInfinite(r), "finite R id=" + preset.id + " prof=" + profileMode);
                double aL = Math.abs((double) l);
                double aR = Math.abs((double) r);
                if (aL > maxAbs) maxAbs = aL;
                if (aR > maxAbs) maxAbs = aR;
                sumSq += (l * l) + (r * r);

                pL0 = pL1; pL1 = pL2; pL2 = pL3; pL3 = l;
                pR0 = pR1; pR1 = pR2; pR2 = pR3; pR3 = r;
                totalFrames++;
                if (totalFrames >= 3) {
                    double tp = Math.max(catmullRomMax(pL0, pL1, pL2, pL3), catmullRomMax(pR0, pR1, pR2, pR3));
                    if (tp > maxTp) maxTp = tp;
                }
            }
        }

        assertTrue(dsp.getOvershootCount() == 0L, "Zero overshoot preset=" + preset.id + " prof=" + profileMode);
        assertTrue(maxAbs <= AegisDspCore.CLEAN_BOOST_CEILING + 1e-6, "Sample peak <= -0.50 dBTP preset=" + preset.id);
        assertTrue(maxTp <= AegisDspCore.CLEAN_BOOST_CEILING + 5e-4, "4x True-peak <= -0.50 dBTP preset=" + preset.id + " tp=" + maxTp);
        return Math.sqrt(sumSq / (110 * blk * 2));
    }

    private static void verifyCatalogAndJourneyOrder() {
        List<PresetModel> core = PresetModel.getAllPresets();
        List<PresetModel> pro = ProPackCatalog.getProPackPresets();
        List<PresetModel> base64 = ProPackCatalog.getAllPresetsWithPro();
        List<PresetModel> journey = JourneyPackCatalog.getJourneyPackPresets();
        List<PresetModel> all88 = JourneyPackCatalog.getAllPresets88();

        assertTrue(core.size() == 48, "Core strictly 48");
        assertTrue(pro.size() == 16, "Pro strictly 16");
        assertTrue(base64.size() == 64, "ProPackCatalog.getAllPresetsWithPro strictly 64");
        assertTrue(journey.size() == 24, "Journey strictly 24");
        assertTrue(all88.size() == 88, "Combined strictly 88");

        Set<Integer> allIds = new HashSet<>();
        for (PresetModel p : all88) {
            assertTrue(allIds.add(p.id), "Unique ID " + p.id);
            assertTrue(p.tabIndex >= 0 && p.tabIndex < 8, "Valid tabIndex " + p.id);
            assertTrue(!hasForbidden(p.title) && !hasForbidden(p.badge) && !hasForbidden(p.desc), "Clean strings " + p.id);
        }
        assertTrue("J01".equals(JourneyPackCatalog.getShortCode(JourneyPackCatalog.findAnyById(201))), "J01 code");
        assertTrue("J24".equals(JourneyPackCatalog.getShortCode(JourneyPackCatalog.findAnyById(224))), "J24 code");
        assertTrue("C01".equals(JourneyPackCatalog.getShortCode(JourneyPackCatalog.findAnyById(101))), "C01 code");
        assertTrue("P01".equals(JourneyPackCatalog.getShortCode(JourneyPackCatalog.findAnyById(1))), "P01 code");

        // Verify JourneyOrderPolicy covers all 88 presets exactly once across 8 tabs
        Set<Integer> stagedIds = new HashSet<>();
        for (int t = 0; t < 8; t++) {
            JourneyOrderPolicy.StageGroup[] groups = JourneyOrderPolicy.getStagesForTab(t);
            assertTrue(groups.length >= 3, "At least 3 stages in tab=" + t);
            int prevStage = 0;
            for (JourneyOrderPolicy.StageGroup g : groups) {
                assertTrue(g.tabIndex == t, "Stage tabIndex matches " + t);
                assertTrue(g.stageNum > prevStage, "Stage nums strictly ascending");
                prevStage = g.stageNum;
                assertTrue(g.presetIds.length > 0, "Non-empty stage " + g.title);
                for (int pid : g.presetIds) {
                    assertTrue(stagedIds.add(pid), "No duplicate staged ID=" + pid);
                    PresetModel pm = JourneyPackCatalog.findAnyById(pid);
                    assertTrue(pm.id == pid && pm.tabIndex == t, "Staged preset belongs to tab " + t + " id=" + pid);
                }
            }
        }
        assertTrue(stagedIds.size() == 88 && stagedIds.equals(allIds), "StageGroups cover 100% of 88 presets");

        // Verify Tab 5 exact workout chronological order: 201 -> 202 -> 35 -> 203 -> 36 -> 204 -> 205 -> 37 -> 206 -> 114 -> 38 -> 39
        int[] expectedTab5 = new int[]{201, 202, 35, 203, 36, 204, 205, 37, 206, 114, 38, 39};
        for (int i = 0; i < expectedTab5.length - 1; i++) {
            assertTrue(JourneyOrderPolicy.getNextOrderedPresetId(expectedTab5[i], 1) == expectedTab5[i + 1],
                    "Tab 5 next step " + expectedTab5[i] + " -> " + expectedTab5[i + 1]);
            assertTrue(JourneyOrderPolicy.getNextOrderedPresetId(expectedTab5[i + 1], -1) == expectedTab5[i],
                    "Tab 5 prev step " + expectedTab5[i + 1] + " -> " + expectedTab5[i]);
        }

        // Verify 4 Auto-Course Routines
        List<JourneyOrderPolicy.RoutineCourse> courses = JourneyOrderPolicy.getAllRoutines();
        assertTrue(courses.size() == 4, "4 Auto-Courses present");
        for (JourneyOrderPolicy.RoutineCourse c : courses) {
            assertTrue(c.steps.length >= 3, "Course has >=3 stages: " + c.id);
            assertTrue(c.getTotalMinutes() >= 45, "Course duration >= 45m: " + c.id);
            for (JourneyOrderPolicy.RoutineStep st : c.steps) {
                PresetModel pm = JourneyPackCatalog.findAnyById(st.presetId);
                assertTrue(pm.id == st.presetId && st.durationMinutes > 0, "Valid step id=" + st.presetId);
            }
        }
    }

    private static void verifyDsp88x3AndBinauralRamps() {
        List<PresetModel> all88 = JourneyPackCatalog.getAllPresets88();
        int[] profiles = new int[]{
                AegisDspCore.PROFILE_PURE,
                AegisDspCore.PROFILE_PHONE_SPEAKER,
                AegisDspCore.PROFILE_HEADPHONE
        };
        for (int prof : profiles) {
            for (PresetModel p : all88) {
                double rms = verifyZeroOvershootAt120(p, prof, AegisDspCore.AMBIENT_OFF, 0, p.noiseType == 2);
                assertTrue(!Double.isNaN(rms) && rms > 0.005, "Finite non-zero RMS id=" + p.id + " prof=" + prof);
            }
        }

        // Ascending Binaural Progression Ramp J01 (201: 200Hz, 10->18Hz, 300s) -> clamps & holds at 18.0Hz
        PresetModel j01 = JourneyPackCatalog.findAnyById(201);
        AegisDspCore asc = new AegisDspCore(48000);
        asc.setOutputProfileMode(AegisDspCore.PROFILE_PHONE_SPEAKER);
        asc.setVolume(100, false);
        asc.playPreset(j01);
        assertTrue(asc.isAcousticCompensationActive(), "Speaker compensation active on J01 binaural ramp");
        float[] secBuf = new float[48000 * 2];
        asc.renderStereoFloat(secBuf, 48000);
        float bStart = asc.getLiveBeatHz();
        assertNear(asc.getLiveFreqHz(), 200.0, 1e-3, "J01 carrier == 200Hz");
        for (int s = 1; s < 150; s++) asc.renderStereoFloat(secBuf, 48000);
        float bMid = asc.getLiveBeatHz();
        for (int s = 150; s < 310; s++) asc.renderStereoFloat(secBuf, 48000);
        float bEndHold = asc.getLiveBeatHz();
        assertTrue(bStart >= 9.9f && bStart < 10.5f, "J01 beat starts near 10Hz (" + bStart + ")");
        assertTrue(bMid > bStart + 2.0f && bMid < 17.5f, "J01 beat ramps up (" + bMid + ")");
        assertNear(bEndHold, 18.0, 0.05, "J01 beat clamps and holds at 18.0Hz past 300s");
        assertTrue(asc.getOvershootCount() == 0L, "J01 ramp zero overshoot");

        // Descending Binaural Progression Ramp J05 (205: 160Hz, 12->6Hz, 300s) -> clamps & holds at 6.0Hz
        PresetModel j05 = JourneyPackCatalog.findAnyById(205);
        AegisDspCore desc = new AegisDspCore(48000);
        desc.setOutputProfileMode(AegisDspCore.PROFILE_PURE);
        desc.setVolume(100, false);
        desc.playPreset(j05);
        desc.renderStereoFloat(secBuf, 48000);
        float dStart = desc.getLiveBeatHz();
        for (int s = 1; s < 150; s++) desc.renderStereoFloat(secBuf, 48000);
        float dMid = desc.getLiveBeatHz();
        for (int s = 150; s < 310; s++) desc.renderStereoFloat(secBuf, 48000);
        float dEndHold = desc.getLiveBeatHz();
        assertTrue(dStart <= 12.1f && dStart > 11.5f, "J05 beat starts near 12Hz (" + dStart + ")");
        assertTrue(dMid < dStart - 2.0f && dMid > 6.5f, "J05 beat ramps down (" + dMid + ")");
        assertNear(dEndHold, 6.0, 0.05, "J05 beat clamps and holds at 6.0Hz past 300s");

        // Ambient mix max 60% @ 120% on new Journey presets (201, 206, 208, 217, 224)
        for (int pid : new int[]{201, 206, 208, 217, 224}) {
            PresetModel jp = JourneyPackCatalog.findAnyById(pid);
            verifyZeroOvershootAt120(jp, AegisDspCore.PROFILE_PHONE_SPEAKER, AegisDspCore.AMBIENT_RAIN, 60, jp.noiseType == 2);
        }
    }

    private static void verifyAdvisor88() {
        String[] tabs = new String[]{"안티방어", "수면", "집중·공부", "명상·안정", "두통·통증", "운동·피로", "기분·힐링", "모기·벌레"};
        for (int h = 0; h < 24; h++) {
            int[] recs = PresetAdvisor.getTimeOfDayRecommendations(h);
            assertTrue(recs.length == 3, "3 recs hour=" + h);
            for (int id : recs) {
                assertTrue(JourneyPackCatalog.findAnyById(id).id == id, "Rec id valid=" + id);
            }
        }
        String[] queries = new String[]{"포모도로", "워밍업", "쿨다운", "근육통", "거북목", "날파리", "J01", "J08", "수면", "40Hz"};
        for (String q : queries) {
            int hits = 0;
            for (PresetModel p : JourneyPackCatalog.getAllPresets88()) {
                if (PresetAdvisor.matchesQuery(p, tabs[p.tabIndex], q)) hits++;
            }
            assertTrue(hits >= 1, "Search matches >= 1 for '" + q + "'");
        }
        String glossary = PresetAdvisor.getBeginnerGlossaryText();
        assertTrue(glossary.contains("포모도로") && glossary.contains("4-6 호흡") && glossary.contains("딥워크"),
                "Glossary covers key user terms");
        assertTrue(!hasForbidden(glossary), "Glossary has no forbidden chars");
        for (PresetModel p : JourneyPackCatalog.getAllPresets88()) {
            assertTrue(PresetAdvisor.getRecommendedVolumePercent(p) >= 15, "Rec vol >= 15 id=" + p.id);
            assertTrue(!hasForbidden(PresetAdvisor.getListeningGuideBadge(p)), "Clean guide id=" + p.id);
            String tb = (p.title + " " + p.badge);
            assertTrue(!tb.contains("CNS") && !tb.contains("DOMS") && !tb.contains("VDT") && !tb.contains("HIIT"),
                    "No unexplained acronyms in title/badge id=" + p.id + ": " + tb);
            String exp = PresetAdvisor.buildPlainExplanation(p);
            assertTrue(exp != null && exp.contains("[이게 뭔가요?]") && exp.contains("[참고 원리]") && !hasForbidden(exp),
                    "Plain explanation non-empty & clean id=" + p.id);
            if (p.id == 104) {
                assertTrue(exp.contains("25분") && exp.contains("포모도로"), "C04 explains pomodoro");
            }
        }
    }

    private static boolean hasForbidden(String s) {
        if (s == null) return false;
        if (s.contains("\ubc1c\uc9c4") || s.contains("\u2605")) return true;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp >= 0x1F300) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static void verifyAppSourceAudit(File appDir) throws Exception {
        File manifest = new File(appDir, "AndroidManifest.xml");
        String mfText = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
        assertTrue(mfText.contains("android:versionCode=\"12\""), "versionCode=12");
        assertTrue(mfText.contains("android:versionName=\"3.4.4\""), "versionName=3.4.4");
        assertTrue(mfText.contains("android:minSdkVersion=\"21\""), "minSdkVersion=21");
        assertTrue(mfText.contains("android:exported=\"false\""), "AegisAudioService exported=false");

        File mainAct = new File(appDir, "src/com/vorta/aegis/MainActivity.java");
        String mainText = new String(Files.readAllBytes(mainAct.toPath()), StandardCharsets.UTF_8);
        assertTrue(!mainText.contains("mSeekQuickVolume"), "Unified volume: no mSeekQuickVolume");
        assertTrue(!mainText.contains("mBtnQuickMute"), "Unified volume: no mBtnQuickMute");
        assertTrue(!mainText.contains("mTvQuickVolPct"), "Unified volume: no mTvQuickVolPct");
        assertTrue(mainText.contains("mScopeSection.setVisibility(View.GONE);"), "Mini mode hides waveform graph by default");
        assertTrue(mainText.contains("mScopeSection.setVisibility(expanded ? View.VISIBLE : View.GONE);"), "Studio toggle controls waveform visibility");
        assertTrue(mainText.contains("mWaveformView.setActive(expanded && mLastDisplayedRunning);"), "Waveform animates in Studio mode only");
        assertTrue(!mainText.contains("card.addView(mWaveformView);"), "Waveform lives inside mScopeSection");
        assertTrue(!mainText.contains("mConsoleDetailBody.addView(mWaveformView)"), "Waveform placed directly below info, not below volume");
        assertTrue(!mainText.contains("mConsoleDetailBody.addView(buildVolumeControlBlock())"), "No duplicate volume inside studio body");
        int volBlockAdds = mainText.split("addView\\(buildVolumeControlBlock\\(\\)\\)", -1).length - 1;
        assertTrue(volBlockAdds == 1, "Exactly 1 buildVolumeControlBlock() addView (" + volBlockAdds + ")");
        int idxMini = mainText.indexOf("card.addView(miniHeader);");
        int idxScopeSec = mainText.indexOf("card.addView(mScopeSection);");
        int idxVol = mainText.indexOf("card.addView(buildVolumeControlBlock());");
        int idxDetail = mainText.indexOf("card.addView(mConsoleDetailBody);");
        assertTrue(idxMini > 0 && idxMini < idxScopeSec && idxScopeSec < idxVol && idxVol < idxDetail,
                "Studio vertical order strictly Info -> Auto Waveform Section -> Unified Volume -> Detail ("
                + idxMini + " < " + idxScopeSec + " < " + idxVol + " < " + idxDetail + ")");

        List<File> files = new ArrayList<>();
        collectFiles(new File(appDir, "src"), files);
        collectFiles(new File(appDir, "res"), files);
        files.add(manifest);
        assertTrue(files.size() == 31, "Source+res+manifest file count == 31 (" + files.size() + ")");

        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertTrue(!text.contains("\ubc1c\uc9c4"), "Zero '발진' in " + f.getName());
            assertTrue(!text.contains("\u2605"), "Zero '★' in " + f.getName());
            assertTrue(!text.contains("requestAudioFocus"), "Zero requestAudioFocus in " + f.getName());
            assertTrue(!text.contains("AudioFocusRequest"), "Zero AudioFocusRequest in " + f.getName());
            assertTrue(!text.contains("setStreamVolume"), "Zero setStreamVolume in " + f.getName());
            assertTrue(!text.contains("adjustStreamVolume"), "Zero adjustStreamVolume in " + f.getName());
            assertTrue(!text.contains("FLAG_KEEP_SCREEN_ON"), "Zero FLAG_KEEP_SCREEN_ON in " + f.getName());
            assertTrue(!text.contains("turnScreenOn"), "Zero turnScreenOn in " + f.getName());
            assertTrue(!text.contains("AudioTrack.Builder"), "API 21 safe in " + f.getName());
            assertTrue(!text.contains(".setMin("), "API 21 safe in " + f.getName());
            if ("LiveWaveformView.java".equals(f.getName())) {
                assertTrue(!text.contains("String.format"), "Zero String.format in LiveWaveformView");
            }
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                assertTrue(cp < 0x1F300, "Zero emoji in " + f.getName());
                i += Character.charCount(cp);
            }
        }
    }

    private static void collectFiles(File dir, List<File> out) {
        if (!dir.exists()) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectFiles(f, out);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".xml")) out.add(f);
        }
    }

    public static void main(String[] args) throws Exception {
        verifyCatalogAndJourneyOrder();
        verifyDsp88x3AndBinauralRamps();
        verifyAdvisor88();
        verifyAppSourceAudit(new File("d:/D_Workspace_NB/-agentic-ai-workspace/261126_vorta_app/vorta_aegis_app"));
        System.out.println("PASS: " + sPassed + "/0 U338 Android v3.4.0 checks verified");
    }
}
