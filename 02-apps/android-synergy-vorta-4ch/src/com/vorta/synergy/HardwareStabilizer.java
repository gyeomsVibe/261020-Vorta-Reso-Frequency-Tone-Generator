package com.vorta.synergy;

/**
 * Pure-Java hardware stabilization & compensation model for unstable transducers and amplifiers.
 * - Frequency-dependent excursion roll-off (subsonic / rattle edge protection)
 * - Raised-cosine notch skip band (-12 dB) for mechanical rattle spots
 * - First-order RC electro-thermal accumulator & smooth linear thermal derating
 * - Battery thermal hysteresis guard (43.0 C engage / 39.0 C release)
 * Zero Android dependencies; 100% host JVM testable.
 */
public final class HardwareStabilizer {

    public static final class Profile {
        public final double softStartSec;
        public final double slewMs;
        public final double baseTrimDb;
        public final double lowEdgeHz;
        public final double lowEdgeMaxCutDb;
        public final double highEdgeHz;
        public final double highEdgeMaxCutDb;
        public final double thermalTauHeatSec;
        public final double thermalTauCoolSec;
        public final double thermalDerateStart;
        public final double thermalMaxCutDb;
        public final String commercialLabel;

        public Profile(
                double softStartSec,
                double slewMs,
                double baseTrimDb,
                double lowEdgeHz,
                double lowEdgeMaxCutDb,
                double highEdgeHz,
                double highEdgeMaxCutDb,
                double thermalTauHeatSec,
                double thermalTauCoolSec,
                double thermalDerateStart,
                double thermalMaxCutDb,
                String commercialLabel) {
            this.softStartSec = softStartSec;
            this.slewMs = slewMs;
            this.baseTrimDb = baseTrimDb;
            this.lowEdgeHz = lowEdgeHz;
            this.lowEdgeMaxCutDb = lowEdgeMaxCutDb;
            this.highEdgeHz = highEdgeHz;
            this.highEdgeMaxCutDb = highEdgeMaxCutDb;
            this.thermalTauHeatSec = thermalTauHeatSec;
            this.thermalTauCoolSec = thermalTauCoolSec;
            this.thermalDerateStart = thermalDerateStart;
            this.thermalMaxCutDb = thermalMaxCutDb;
            this.commercialLabel = commercialLabel;
        }
    }

    private static final Profile PROF_CH1 = new Profile(
            2.0, 60.0, 0.0, 3.5, 2.0, 17.5, 2.5,
            900.0, 360.0, 0.80, 2.0, "변위·이탈 보호 (2.0s 소프트스타트)");
    private static final Profile PROF_CH2 = new Profile(
            2.0, 60.0, -1.5, 14.0, 1.5, 34.0, 3.0,
            720.0, 300.0, 0.75, 3.0, "이중 드라이버 부하·열 보호");
    private static final Profile PROF_CH3 = new Profile(
            1.2, 40.0, 0.0, 40.0, 0.0, 63.0, 0.0,
            1200.0, 300.0, 0.85, 1.5, "최대 출력 메인 (풀 헤드룸 연속)");
    private static final Profile PROF_CH4 = new Profile(
            1.8, 55.0, -2.0, 43.0, 1.0, 58.0, 1.0,
            600.0, 300.0, 0.65, 3.0, "열보호 보조 (Class-AB 자동 감쇄)");

    private HardwareStabilizer() {}

    public static Profile profileFor(SynergyChannel ch) {
        if (ch == SynergyChannel.CH1_SUB_LOW) return PROF_CH1;
        if (ch == SynergyChannel.CH2_LOW_MID) return PROF_CH2;
        if (ch == SynergyChannel.CH3_TACTILE_MAIN) return PROF_CH3;
        return PROF_CH4;
    }

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** Frequency-dependent edge roll-off + channel base protective trim (dB <= 0.0). */
    public static double computeContourGainDb(SynergyChannel ch, double hz, boolean enabled) {
        if (!enabled || ch == null) return 0.0;
        Profile p = profileFor(ch);
        double cutDb = p.baseTrimDb;
        if (p.lowEdgeMaxCutDb > 0.0 && hz < p.lowEdgeHz && p.lowEdgeHz > ch.minHz) {
            double frac = clamp((p.lowEdgeHz - hz) / (p.lowEdgeHz - ch.minHz), 0.0, 1.0);
            double smooth = 0.5 * (1.0 - Math.cos(Math.PI * frac));
            cutDb -= p.lowEdgeMaxCutDb * smooth;
        }
        if (p.highEdgeMaxCutDb > 0.0 && hz > p.highEdgeHz && ch.maxHz > p.highEdgeHz) {
            double frac = clamp((hz - p.highEdgeHz) / (ch.maxHz - p.highEdgeHz), 0.0, 1.0);
            double smooth = 0.5 * (1.0 - Math.cos(Math.PI * frac));
            cutDb -= p.highEdgeMaxCutDb * smooth;
        }
        return clamp(cutDb, -12.0, 0.0);
    }

    /** Smooth raised-cosine -12 dB mechanical rattle notch filter. */
    public static double computeNotchCutDb(double hz, float notchCenterHz, float notchHalfWidthHz) {
        if (notchCenterHz <= 0.0f || notchHalfWidthHz <= 0.0f) return 0.0;
        double d = Math.abs(hz - notchCenterHz);
        if (d >= notchHalfWidthHz) return 0.0;
        double cosWeight = 0.5 * (1.0 + Math.cos(Math.PI * d / notchHalfWidthHz));
        return -12.0 * cosWeight;
    }

    /** First-order RC electro-thermal accumulator in [0.0, 1.0]. */
    public static double updateThermalLoad(double prevLoad, double rmsNorm, double dtSec, Profile p) {
        if (p == null || dtSec <= 0.0) return clamp(prevLoad, 0.0, 1.0);
        double drive = clamp(rmsNorm * rmsNorm * 2.0, 0.0, 1.0);
        double tau = (drive > prevLoad) ? Math.max(1.0, p.thermalTauHeatSec) : Math.max(1.0, p.thermalTauCoolSec);
        double next = prevLoad + ((drive - prevLoad) / tau) * dtSec;
        return clamp(next, 0.0, 1.0);
    }

    /** Smooth linear thermal derating (0 dB -> -thermalMaxCutDb) above thermalDerateStart. */
    public static double computeThermalDerateDb(double thermalLoad, Profile p, boolean enabled) {
        if (!enabled || p == null || thermalLoad <= p.thermalDerateStart || p.thermalDerateStart >= 1.0) {
            return 0.0;
        }
        double frac = clamp((thermalLoad - p.thermalDerateStart) / (1.0 - p.thermalDerateStart), 0.0, 1.0);
        return -p.thermalMaxCutDb * frac;
    }

    /** Battery thermal hysteresis: >= 43.0C engage (-3 dB), <= 39.0C release. */
    public static boolean evaluateBatteryThermalThrottle(float tempC, boolean currentlyThrottled) {
        if (Float.isNaN(tempC) || tempC <= 0.0f) return false;
        if (tempC >= 43.0f) return true;
        if (tempC <= 39.0f) return false;
        return currentlyThrottled;
    }
}
