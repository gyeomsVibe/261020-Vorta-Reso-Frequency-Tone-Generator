package com.vorta.synergy;

import java.util.Locale;

/**
 * Acoustic scenario step supporting SINGLE, SWEEP, STEPPED_DWELL, and REST_COOL modes.
 * Zero Android dependencies.
 */
public class ScenarioStep {
    public enum StepType {
        SINGLE("단일음"),
        SWEEP("스윕"),
        STEPPED_DWELL("스텝 체류"),
        REST_COOL("냉각 휴지");

        public final String label;

        StepType(String label) {
            this.label = label;
        }

        public static StepType fromName(String name, StepType fallback) {
            if (name != null) {
                StepType[] vals = values();
                for (int i = 0; i < vals.length; i++) {
                    if (vals[i].name().equals(name)) return vals[i];
                }
            }
            return fallback;
        }
    }

    public int id;
    public String title;
    public StepType type;
    public float minHz;
    public float maxHz;
    public float secondaryHz;
    public float durationSec;
    public int stepCount;
    public boolean fmOn;
    public float fmDepth;
    public float fmRateHz;
    public boolean amOn;
    public float amDepth;
    public float amRateHz;
    public boolean rmOn;
    public float rmDepth;
    public float rmRateHz;
    public boolean isLoop;
    public boolean jitterEnabled;
    public float trimDb;

    public volatile boolean isPlaying;
    public volatile float currentHz;
    public double phase1;
    public double phase2;
    public double progressSec;

    public ScenarioStep(
            int id,
            String title,
            StepType type,
            float minHz,
            float maxHz,
            float secondaryHz,
            float durationSec,
            int stepCount,
            boolean fmOn,
            float fmDepth,
            float fmRateHz,
            boolean amOn,
            float amDepth,
            float amRateHz,
            boolean rmOn,
            float rmDepth,
            float rmRateHz,
            boolean isLoop,
            boolean jitterEnabled,
            float trimDb) {
        this.id = id;
        this.title = title != null ? title : ("스텝 " + id);
        this.type = type != null ? type : StepType.SINGLE;
        this.minHz = minHz;
        this.maxHz = maxHz;
        this.secondaryHz = secondaryHz;
        this.durationSec = Math.max(1.0f, durationSec);
        this.stepCount = Math.max(2, stepCount);
        this.fmOn = fmOn;
        this.fmDepth = fmDepth;
        this.fmRateHz = fmRateHz;
        this.amOn = amOn;
        this.amDepth = amDepth;
        this.amRateHz = amRateHz;
        this.rmOn = rmOn;
        this.rmDepth = rmDepth;
        this.rmRateHz = rmRateHz;
        this.isLoop = isLoop;
        this.jitterEnabled = jitterEnabled;
        this.trimDb = Math.max(-12.0f, Math.min(0.0f, trimDb));
        this.isPlaying = false;
        this.currentHz = minHz;
    }

    public ScenarioStep copy() {
        ScenarioStep c = new ScenarioStep(
                id, title, type, minHz, maxHz, secondaryHz, durationSec, stepCount,
                fmOn, fmDepth, fmRateHz, amOn, amDepth, amRateHz, rmOn, rmDepth, rmRateHz,
                isLoop, jitterEnabled, trimDb);
        c.isPlaying = this.isPlaying;
        c.currentHz = this.currentHz;
        return c;
    }

    public String formatCompactSpec() {
        if (type == StepType.REST_COOL) {
            return String.format(Locale.US, "냉각 무음 %.0fs", durationSec);
        }
        String base;
        if (type == StepType.SWEEP) {
            base = String.format(Locale.US, "%.1f~%.1f Hz 스윕(%.0fs)", minHz, maxHz, durationSec);
        } else if (type == StepType.STEPPED_DWELL) {
            base = String.format(Locale.US, "%.1f~%.1f Hz %d단(%.0fs)", minHz, maxHz, stepCount, durationSec);
        } else if (secondaryHz > 0.0f) {
            base = String.format(Locale.US, "%.1f+%.1f Hz 이중", minHz, secondaryHz);
        } else {
            base = String.format(Locale.US, "%.1f Hz 단일", minHz);
        }
        String mod = ModulationProfile.formatCompact(fmOn, fmDepth, fmRateHz, amOn, amDepth, amRateHz, rmOn, rmDepth, rmRateHz);
        if (trimDb < -0.05f) {
            return base + " · " + mod + String.format(Locale.US, " · %.0fdB", trimDb);
        }
        return base + " · " + mod;
    }
}
