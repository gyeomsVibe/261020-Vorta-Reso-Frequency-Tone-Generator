package com.vorta.freqgen;

/**
 * Pure 64-bit double NCO -> 32-bit IEEE float acoustic synthesis core.
 * Zero Android dependencies so host JVM FFT/THD verification tests execute the exact runtime math.
 */
public final class PureDspEngine {
    public static final double TWO_PI = 2.0 * Math.PI;
    public static final double TRUE_PEAK_HEADROOM = 0.8912509381337456; // 10^(-1/20) = -1.0 dBTP
    public static final double MAX_AMPLITUDE_16 = 29200.0;
    public static final long AUX_DISCONNECT_DEBOUNCE_MS = 250L;

    /** Mutable phase/smoother state owned strictly by the audio worker thread. */
    public static final class DspState {
        public double manualPhase = 0.0;
        public double manualPhase2 = 0.0;
        public double fmPhase = 0.0;
        public double amPhase = 0.0;
        public double rmPhase = 0.0;
        public double envGain = 0.0;
        public double smoothedManualHz = -1.0;
        public double smoothedManualHz2 = -1.0;
        public double tailPhase = 0.0;
        public double tailHz = 28.0;
        public double lastSampleNorm = 0.0;
        public double prevSampleNorm = 0.0;
        public boolean tailLocked = false;
        public long rngState = 0x5DEECE66DL;

        /** Generates zero-mean triangular probability density function (TPDF) noise in [-1, 1]. */
        public double nextTpdf() {
            double u1 = nextUniform();
            double u2 = nextUniform();
            return u1 - u2;
        }

        private double nextUniform() {
            long x = rngState;
            x ^= (x << 13);
            x ^= (x >>> 7);
            x ^= (x << 17);
            if (x == 0L) x = 0x9E3779B97F4A7C15L;
            rngState = x;
            return ((x >>> 11) * 0x1.0p-53);
        }

        public void resetPhases() {
            manualPhase = 0.0;
            manualPhase2 = 0.0;
            fmPhase = 0.0;
            amPhase = 0.0;
            rmPhase = 0.0;
            envGain = 0.0;
            smoothedManualHz = -1.0;
            smoothedManualHz2 = -1.0;
            tailPhase = 0.0;
            tailHz = 28.0;
            lastSampleNorm = 0.0;
            prevSampleNorm = 0.0;
            tailLocked = false;
        }
    }

    /** Immutable buffer telemetry statistics for THD/DC/headroom verification. */
    public static final class BufferStats {
        public final double peakAbs;
        public final double rms;
        public final double rmsDbfs;
        public final double dcOffset;
        public final double maxSampleDelta;

        public BufferStats(double peakAbs, double rms, double rmsDbfs, double dcOffset, double maxSampleDelta) {
            this.peakAbs = peakAbs;
            this.rms = rms;
            this.rmsDbfs = rmsDbfs;
            this.dcOffset = dcOffset;
            this.maxSampleDelta = maxSampleDelta;
        }
    }

    /** Pure-Java wired AUX debounce & AudioTrack error recovery decision policy. */
    public static final class AuxDebouncePolicy {
        private long pendingDisconnectAtMs = -1L;

        public void onHardwareConnected() {
            pendingDisconnectAtMs = -1L;
        }

        public boolean onHardwareDisconnected(long nowMs, boolean currentlySuspended) {
            if (currentlySuspended) return false;
            if (pendingDisconnectAtMs < 0L) {
                pendingDisconnectAtMs = nowMs;
            }
            return (nowMs - pendingDisconnectAtMs) >= AUX_DISCONNECT_DEBOUNCE_MS;
        }

        public boolean pollDebounceExpired(long nowMs, boolean wiredNow, boolean currentlySuspended) {
            if (wiredNow) {
                pendingDisconnectAtMs = -1L;
                return false;
            }
            if (currentlySuspended || pendingDisconnectAtMs < 0L) return false;
            return (nowMs - pendingDisconnectAtMs) >= AUX_DISCONNECT_DEBOUNCE_MS;
        }

        public static boolean shouldRebuildAudioTrack(int writeReturn) {
            return writeReturn < 0; // Covers AudioTrack.ERROR_DEAD_OBJECT (-6), ERROR_INVALID_OPERATION (-3), etc.
        }
    }

    private PureDspEngine() {}

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** Matches sine phase & derivative sign to the last rendered sample for C^1 clickless fade-out tails. */
    public static double matchTailPhase(double prevNorm, double lastNorm) {
        double y = clamp(lastNorm, -1.0, 1.0);
        double phi = Math.asin(y);
        if ((lastNorm - prevNorm) < 0.0) {
            phi = Math.PI - phi;
        }
        return wrapPhase(phi);
    }

    /** Computes one-pole exponential frequency slewing coefficient (~9ms cutoff) for clickless transitions. */
    public static double computeFreqSlewAlpha(int sampleRate) {
        int sr = Math.max(8000, sampleRate);
        return 1.0 - Math.exp(-TWO_PI * 18.0 / (double) sr);
    }

    public static double slewFrequency(double currentSmoothed, double targetHz, double alpha) {
        if (currentSmoothed <= 0.0) return targetHz;
        double diff = Math.abs(targetHz - currentSmoothed);
        if (diff < 1e-6) return targetHz;
        return currentSmoothed + alpha * (targetHz - currentSmoothed);
    }

    public static double wrapPhase(double phase) {
        if (phase >= TWO_PI) {
            return phase - TWO_PI * Math.floor(phase / TWO_PI);
        }
        if (phase < 0.0) {
            return phase - TWO_PI * Math.floor(phase / TWO_PI);
        }
        return phase;
    }

    /** Renders stereo interleaved 32-bit float pure sine scaled strictly to -1.0 dBTP headroom. */
    public static void renderPureSineFloat(
            float[] stereoOut, int frames, int sampleRate, double freqHz, DspState state) {
        double invSr = 1.0 / (double) Math.max(8000, sampleRate);
        double alpha = computeFreqSlewAlpha(sampleRate);
        for (int f = 0; f < frames; f++) {
            state.smoothedManualHz = slewFrequency(state.smoothedManualHz, freqHz, alpha);
            double s = Math.sin(state.manualPhase) * TRUE_PEAK_HEADROOM;
            state.manualPhase = wrapPhase(state.manualPhase + TWO_PI * state.smoothedManualHz * invSr);
            float sf = (float) s;
            int idx = f * 2;
            stereoOut[idx] = sf;
            stereoOut[idx + 1] = sf;
        }
    }

    /** Renders stereo linear superposition dual-sine f1 + f2 (clean acoustic beating without IM distortion). */
    public static void renderDualSineFloat(
            float[] stereoOut, int frames, int sampleRate, double f1Hz, double f2Hz, DspState state) {
        double invSr = 1.0 / (double) Math.max(8000, sampleRate);
        double alpha = computeFreqSlewAlpha(sampleRate);
        for (int f = 0; f < frames; f++) {
            state.smoothedManualHz = slewFrequency(state.smoothedManualHz, f1Hz, alpha);
            state.smoothedManualHz2 = slewFrequency(state.smoothedManualHz2, f2Hz, alpha);
            double s = 0.5 * (Math.sin(state.manualPhase) + Math.sin(state.manualPhase2)) * TRUE_PEAK_HEADROOM;
            state.manualPhase = wrapPhase(state.manualPhase + TWO_PI * state.smoothedManualHz * invSr);
            state.manualPhase2 = wrapPhase(state.manualPhase2 + TWO_PI * state.smoothedManualHz2 * invSr);
            float sf = (float) s;
            int idx = f * 2;
            stereoOut[idx] = sf;
            stereoOut[idx + 1] = sf;
        }
    }

    /** Quantizes normalized float stereo buffer to 16-bit PCM using unbiased 1-LSB TPDF dither. */
    public static void quantizeToPcm16Tpdf(float[] stereoFloat, short[] outPcm, int totalSamples, DspState state) {
        for (int i = 0; i < totalSamples; i++) {
            double scaled = stereoFloat[i] * 32767.0 + state.nextTpdf();
            if (scaled > 32767.0) scaled = 32767.0;
            else if (scaled < -32767.0) scaled = -32767.0;
            outPcm[i] = (short) Math.round(scaled);
        }
    }

    public static BufferStats measureBufferStats(float[] stereoFloat, int frames) {
        if (stereoFloat == null || frames <= 0) {
            return new BufferStats(0.0, 0.0, -120.0, 0.0, 0.0);
        }
        double peak = 0.0;
        double sumSq = 0.0;
        double sum = 0.0;
        double maxDelta = 0.0;
        double prev = stereoFloat[0];
        for (int f = 0; f < frames; f++) {
            double v = stereoFloat[f * 2];
            double av = Math.abs(v);
            if (av > peak) peak = av;
            sumSq += v * v;
            sum += v;
            if (f > 0) {
                double d = Math.abs(v - prev);
                if (d > maxDelta) maxDelta = d;
            }
            prev = v;
        }
        double rms = Math.sqrt(sumSq / frames);
        double rmsDb = 20.0 * Math.log10(Math.max(1e-7, rms));
        double dc = sum / frames;
        return new BufferStats(peak, rms, rmsDb, dc, maxDelta);
    }
}
