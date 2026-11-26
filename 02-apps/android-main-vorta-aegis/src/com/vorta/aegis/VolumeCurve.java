package com.vorta.aegis;

import java.util.Locale;

/**
 * Perceptual logarithmic volume curve and dB formatter for independent in-app gain control.
 * Supports 0..120% with unity 0.0 dB at 100% and clean distortion-free boost up to +1.6 dB (120%).
 */
public final class VolumeCurve {
    public static final double MIN_DB = -48.0;
    public static final int DEFAULT_PERCENT = 45;
    public static final int UNITY_PERCENT = 100;
    public static final int MAX_PERCENT = 120;
    public static final double MAX_BOOST_LINEAR = 1.20;

    private VolumeCurve() {
    }

    /** Clamps an integer volume percentage into [0, 120]. */
    public static int clampPercent(int percent) {
        if (percent < 0) return 0;
        if (percent > MAX_PERCENT) return MAX_PERCENT;
        return percent;
    }

    /** Maps [0..120] percent to [-48.0..+1.584] dB (0 -> -Infinity, 100 -> +0.0, 120 -> +1.584). */
    public static double percentToDb(int percent) {
        int clamped = clampPercent(percent);
        if (clamped <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (clamped == UNITY_PERCENT) {
            return 0.0;
        }
        if (clamped > UNITY_PERCENT) {
            return 20.0 * Math.log10(clamped / 100.0);
        }
        return MIN_DB * (1.0 - (clamped / 100.0));
    }

    /** Converts [0..120] percent to a perceptual linear amplitude multiplier in [0.0, 1.20]. */
    public static double percentToLinearGain(int percent) {
        int clamped = clampPercent(percent);
        if (clamped <= 0) {
            return 0.0;
        }
        if (clamped == UNITY_PERCENT) {
            return 1.0;
        }
        if (clamped > UNITY_PERCENT) {
            return clamped / 100.0;
        }
        return Math.pow(10.0, percentToDb(clamped) / 20.0);
    }

    /** Formats current gain readout in dB for tabular monospace UI display without -0.0 artifacts. */
    public static String formatDb(int percent, boolean muted) {
        int clamped = clampPercent(percent);
        if (muted || clamped <= 0) {
            return "-\u221e dB";
        }
        double db = percentToDb(clamped);
        if (Math.abs(db) < 0.05) {
            return "0.0 dB";
        }
        if (db > 0.0) {
            return String.format(Locale.US, "+%.1f dB", db);
        }
        return String.format(Locale.US, "%.1f dB", db);
    }
}
