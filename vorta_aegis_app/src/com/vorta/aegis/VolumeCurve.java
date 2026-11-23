package com.vorta.aegis;

import java.util.Locale;

/**
 * Perceptual logarithmic volume curve and dB formatter for independent in-app gain control.
 * Uses -48 dB floor so background frequencies can sit cleanly beneath music apps without masking them.
 */
public final class VolumeCurve {
    public static final double MIN_DB = -48.0;
    public static final int DEFAULT_PERCENT = 45;

    private VolumeCurve() {
    }

    /** Clamps an integer volume percentage into [0, 100]. */
    public static int clampPercent(int percent) {
        if (percent < 0) return 0;
        if (percent > 100) return 100;
        return percent;
    }

    /** Maps [0..100] percent to [-48.0..0.0] dB (0 -> -Infinity, 100 -> +0.0). */
    public static double percentToDb(int percent) {
        int clamped = clampPercent(percent);
        if (clamped <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (clamped >= 100) {
            return 0.0;
        }
        return MIN_DB * (1.0 - (clamped / 100.0));
    }

    /** Converts [0..100] percent to a perceptual linear amplitude multiplier in [0.0, 1.0]. */
    public static double percentToLinearGain(int percent) {
        int clamped = clampPercent(percent);
        if (clamped <= 0) {
            return 0.0;
        }
        if (clamped >= 100) {
            return 1.0;
        }
        return Math.pow(10.0, percentToDb(clamped) / 20.0);
    }

    /** Formats current gain readout in dB for tabular monospace UI display without -0.0 artifacts. */
    public static String formatDb(int percent, boolean muted) {
        int clamped = clampPercent(percent);
        if (muted || clamped <= 0) {
            return "-∞ dB";
        }
        double db = percentToDb(clamped);
        if (Math.abs(db) < 0.05) {
            db = 0.0;
        }
        return String.format(Locale.US, "%.1f dB", db);
    }
}
