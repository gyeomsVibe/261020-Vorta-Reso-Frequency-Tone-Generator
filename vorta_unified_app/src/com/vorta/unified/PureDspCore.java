package com.vorta.unified;

import java.util.List;

/**
 * Pure 64-bit double NCO -> 32-bit IEEE float acoustic synthesis core.
 * Zero Android dependencies so host JVM FFT/THD & harsh stress verification execute exact runtime math.
 */
public final class PureDspCore {
    public static final double TWO_PI = 2.0 * Math.PI;
    public static final double TRUE_PEAK_HEADROOM = 0.8912509381337456; // 10^(-1/20) = -1.0 dBTP
    public static final double MAX_AMPLITUDE_16 = 29200.0;
    public static final float MIN_FREQ_HZ = 0.5f;
    public static final float MAX_FREQ_HZ = 63.0f;
    public static final long AUX_DISCONNECT_DEBOUNCE_MS = 250L;

    public static final class DspState {
        public double phase1 = 0.0;
        public double phase2 = 0.0;
        public double fmPhase = 0.0;
        public double amPhase = 0.0;
        public double rmPhase = 0.0;
        public double envGain = 0.0;
        public double smoothedF1 = -1.0;
        public double smoothedF2 = -1.0;

        // Unified runtime fields
        public double scenPhase1 = 0.0;
        public double scenPhase2 = 0.0;
        public double scenSweepPos = 0.0;
        public double scenFmPhase = 0.0;
        public double scenAmPhase = 0.0;
        public double scenRmPhase = 0.0;
        public double scenSmoothedHz = -1.0;
        public double scenSmoothedHz2 = -1.0;
        public double scenDualMix = 0.0;
        public double scenModOffset = 0.0;
        public double lastModGain = 1.0;
        public String lastScenId = null;

        // C^1 fade-out / AUX-mute tail continuity
        public double tailPhase = 0.0;
        public double tailHz = 28.0;
        public double lastSampleNorm = 0.0;
        public double prevSampleNorm = 0.0;
        public boolean tailLocked = false;

        public long rngState = 0x5DEECE66DL;

        public double nextTpdf() {
            return nextUniform() - nextUniform();
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
    }

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

    public static final class AuxDebouncePolicy {
        private long pendingDisconnectAtMs = -1L;

        public void onHardwareConnected() {
            pendingDisconnectAtMs = -1L;
        }

        public boolean onHardwareDisconnected(long nowMs, boolean currentlySuspended) {
            if (currentlySuspended) return false;
            if (pendingDisconnectAtMs < 0L) pendingDisconnectAtMs = nowMs;
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
            return writeReturn < 0;
        }
    }

    private PureDspCore() {}

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static double clampHz(double hz) {
        return clamp(hz, (double) MIN_FREQ_HZ, (double) MAX_FREQ_HZ);
    }

    /** Matches sine phase & derivative sign to last rendered normalized sample for C^1 clickless fade-out tails. */
    public static double matchTailPhase(double prevNorm, double lastNorm) {
        double y = clamp(lastNorm, -1.0, 1.0);
        double phi = Math.asin(y);
        if ((lastNorm - prevNorm) < 0.0) {
            phi = Math.PI - phi;
        }
        return wrapPhase(phi);
    }

    public static double computeFreqSlewAlpha(int sampleRate) {
        int sr = Math.max(8000, sampleRate);
        return 1.0 - Math.exp(-TWO_PI * 18.0 / (double) sr);
    }

    public static double slewFrequency(double currentSmoothed, double targetHz, double alpha) {
        if (currentSmoothed <= 0.0) return targetHz;
        if (Math.abs(targetHz - currentSmoothed) < 1e-6) return targetHz;
        return currentSmoothed + alpha * (targetHz - currentSmoothed);
    }

    public static double wrapPhase(double phase) {
        if (phase >= TWO_PI || phase < 0.0) {
            return phase - TWO_PI * Math.floor(phase / TWO_PI);
        }
        return phase;
    }

    public static void renderPureSineFloat(
            float[] stereoOut, int frames, int sampleRate, double freqHz, DspState st) {
        double invSr = 1.0 / (double) Math.max(8000, sampleRate);
        double alpha = computeFreqSlewAlpha(sampleRate);
        for (int f = 0; f < frames; f++) {
            st.smoothedF1 = slewFrequency(st.smoothedF1, freqHz, alpha);
            double s = Math.sin(st.phase1) * TRUE_PEAK_HEADROOM;
            st.phase1 = wrapPhase(st.phase1 + TWO_PI * st.smoothedF1 * invSr);
            float sf = (float) s;
            int idx = f * 2;
            stereoOut[idx] = sf;
            stereoOut[idx + 1] = sf;
        }
    }

    public static void renderDualSineFloat(
            float[] stereoOut, int frames, int sampleRate, double f1Hz, double f2Hz, DspState st) {
        double invSr = 1.0 / (double) Math.max(8000, sampleRate);
        double alpha = computeFreqSlewAlpha(sampleRate);
        for (int f = 0; f < frames; f++) {
            st.smoothedF1 = slewFrequency(st.smoothedF1, f1Hz, alpha);
            st.smoothedF2 = slewFrequency(st.smoothedF2, f2Hz, alpha);
            double s = 0.5 * (Math.sin(st.phase1) + Math.sin(st.phase2)) * TRUE_PEAK_HEADROOM;
            st.phase1 = wrapPhase(st.phase1 + TWO_PI * st.smoothedF1 * invSr);
            st.phase2 = wrapPhase(st.phase2 + TWO_PI * st.smoothedF2 * invSr);
            float sf = (float) s;
            int idx = f * 2;
            stereoOut[idx] = sf;
            stereoOut[idx + 1] = sf;
        }
    }

    public static void quantizeToPcm16Tpdf(float[] stereoFloat, short[] outPcm, int totalSamples, DspState st) {
        for (int i = 0; i < totalSamples; i++) {
            double scaled = stereoFloat[i] * 32767.0 + st.nextTpdf();
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
        return new BufferStats(peak, rms, rmsDb, sum / frames, maxDelta);
    }

    /**
     * Exact production unified DSP chunk renderer shared between VortaAudioService and Host-JVM audits.
     * Enforces 64-bit double NCO, clickless scenario hot-switching, C^1 continuous fade-out tails,
     * [0.5, 63.0] Hz range bounds, and strict -1.0 dBTP true-peak headroom.
     */
    public static float renderUnifiedChunk(
            float[] floatBuf, int chunkFrames, int sampleRate, boolean auxSuspended,
            ScenarioCatalog.ScenarioSpec activeScen,
            boolean manualOn, float manualFreq, boolean manualDual, float manualSecFreq,
            List<SweepItem> sweepItems, Object sweepLock,
            boolean fmOn, float fmDepth, float fmRateHz,
            boolean amOn, float amDepth, float amRateHz,
            boolean rmOn, float rmDepth, float rmRateHz,
            DspState st) {
        final int sr = Math.max(8000, sampleRate);
        final double invSr = 1.0 / (double) sr;
        final double hzAlpha = computeFreqSlewAlpha(sr);
        final double fadeInStep = 1.0 / (0.8 * sr);
        final double fadeOutStep = 1.0 / (0.4 * sr);
        final double auxMuteStep = 1.0 / (0.02 * sr);
        final double dualRampStep = 1.0 / (0.04 * sr);
        final double modDecay = Math.exp(-TWO_PI * 30.0 * invSr);
        float instantDisplayHz = 0.0f;

        SweepItem[] activeSweeps = null;
        if (activeScen == null && sweepItems != null) {
            final Object lock = (sweepLock != null) ? sweepLock : st;
            synchronized (lock) {
                int sz = sweepItems.size();
                if (sz > 0) {
                    int cnt = 0;
                    for (int i = 0; i < sz; i++) {
                        if (sweepItems.get(i).isPlaying) cnt++;
                    }
                    if (cnt > 0) {
                        activeSweeps = new SweepItem[cnt];
                        int k = 0;
                        for (int i = 0; i < sz; i++) {
                            SweepItem it = sweepItems.get(i);
                            if (it.isPlaying) activeSweeps[k++] = it;
                        }
                    }
                }
            }
        }

        if (activeScen != null) {
            boolean scenChanged = (activeScen.id != null && !activeScen.id.equals(st.lastScenId));
            if (scenChanged) {
                st.lastScenId = activeScen.id;
                st.scenSweepPos = 0.0;
                st.scenFmPhase = 0.0;
                st.scenAmPhase = 0.0;
                st.scenRmPhase = 0.0;
                double firstTargetHz = clampHz(activeScen.isSweep ? activeScen.sweepMinHz : activeScen.primaryHz);
                boolean hasSec = activeScen.secondaryHz >= MIN_FREQ_HZ;
                double firstSecHz = hasSec ? clampHz(activeScen.secondaryHz) : firstTargetHz;
                if (st.envGain <= 1e-6 || st.scenSmoothedHz <= 0.0) {
                    st.scenSmoothedHz = firstTargetHz;
                    st.scenSmoothedHz2 = firstSecHz;
                    st.scenDualMix = hasSec ? 1.0 : 0.0;
                    st.scenModOffset = 0.0;
                    st.lastModGain = 1.0;
                } else {
                    st.scenModOffset = clamp(st.scenModOffset + (st.lastModGain - 1.0), -1.0, 1.0);
                }
            }
        } else {
            st.lastScenId = null;
        }

        for (int f = 0; f < chunkFrames; f++) {
            double sampleSum = 0.0;
            int activeSources = 0;
            double lastCarrierHz = (st.tailHz > 0.0) ? st.tailHz : 28.0;

            if (activeScen != null) {
                activeSources = 1;
                double curHz;
                if (activeScen.isSweep) {
                    double tri = (st.scenSweepPos <= 1.0) ? st.scenSweepPos : (2.0 - st.scenSweepPos);
                    curHz = activeScen.sweepMinHz + (activeScen.sweepMaxHz - activeScen.sweepMinHz) * tri;
                    double step = 2.0 / (Math.max(1.0, (double) activeScen.sweepPeriodSec) * sr);
                    st.scenSweepPos += step;
                    if (st.scenSweepPos >= 2.0) st.scenSweepPos -= 2.0;
                } else {
                    curHz = activeScen.primaryHz;
                }
                curHz = clampHz(curHz);
                st.scenSmoothedHz = slewFrequency(st.scenSmoothedHz, curHz, hzAlpha);
                instantDisplayHz = (float) st.scenSmoothedHz;

                double instHz = st.scenSmoothedHz;
                if (activeScen.fmOn && activeScen.fmDepth > 0.0f) {
                    instHz = clampHz(instHz * (1.0 + activeScen.fmDepth * Math.sin(st.scenFmPhase)));
                    st.scenFmPhase = wrapPhase(st.scenFmPhase + TWO_PI * activeScen.fmRateHz * invSr);
                }
                double v1 = Math.sin(st.scenPhase1);
                st.scenPhase1 = wrapPhase(st.scenPhase1 + TWO_PI * instHz * invSr);

                boolean hasSec = activeScen.secondaryHz >= MIN_FREQ_HZ;
                if (hasSec) {
                    double secTarget = clampHz(activeScen.secondaryHz);
                    if (st.scenDualMix <= 1e-6 || st.scenSmoothedHz2 <= 0.0) {
                        st.scenSmoothedHz2 = secTarget;
                    } else {
                        st.scenSmoothedHz2 = slewFrequency(st.scenSmoothedHz2, secTarget, hzAlpha);
                    }
                    st.scenDualMix = Math.min(1.0, st.scenDualMix + dualRampStep);
                } else {
                    st.scenDualMix = Math.max(0.0, st.scenDualMix - dualRampStep);
                }

                double val;
                if (st.scenDualMix > 1e-7) {
                    double f2Hz = (st.scenSmoothedHz2 > 0.0) ? st.scenSmoothedHz2 : instHz;
                    double v2 = Math.sin(st.scenPhase2);
                    st.scenPhase2 = wrapPhase(st.scenPhase2 + TWO_PI * f2Hz * invSr);
                    double w2 = 0.5 * st.scenDualMix;
                    val = (1.0 - w2) * v1 + w2 * v2;
                    lastCarrierHz = (1.0 - w2) * instHz + w2 * f2Hz;
                } else {
                    val = v1;
                    lastCarrierHz = instHz;
                }

                double rawMod = 1.0;
                if (activeScen.amOn && activeScen.amDepth > 0.0f) {
                    rawMod *= (1.0 - activeScen.amDepth * 0.5 * (1.0 - Math.cos(st.scenAmPhase)));
                    st.scenAmPhase = wrapPhase(st.scenAmPhase + TWO_PI * activeScen.amRateHz * invSr);
                }
                if (activeScen.rmOn && activeScen.rmDepth > 0.0f) {
                    rawMod *= ((1.0 - activeScen.rmDepth) + activeScen.rmDepth * Math.cos(st.scenRmPhase));
                    st.scenRmPhase = wrapPhase(st.scenRmPhase + TWO_PI * activeScen.rmRateHz * invSr);
                }
                double effMod = clamp(rawMod + st.scenModOffset, -1.0, 1.0);
                st.scenModOffset *= modDecay;
                st.lastModGain = effMod;
                sampleSum = val * effMod;
            } else {
                if (manualOn && manualFreq >= MIN_FREQ_HZ) {
                    activeSources++;
                    st.smoothedF1 = slewFrequency(st.smoothedF1, clampHz(manualFreq), hzAlpha);
                    double instHz = st.smoothedF1;
                    if (fmOn && fmDepth > 0.0f) {
                        instHz = clampHz(instHz * (1.0 + fmDepth * Math.sin(st.fmPhase)));
                    }
                    double val = Math.sin(st.phase1);
                    st.phase1 = wrapPhase(st.phase1 + TWO_PI * instHz * invSr);
                    lastCarrierHz = instHz;
                    if (manualDual && manualSecFreq >= MIN_FREQ_HZ) {
                        st.smoothedF2 = slewFrequency(st.smoothedF2, clampHz(manualSecFreq), hzAlpha);
                        double v2 = Math.sin(st.phase2);
                        st.phase2 = wrapPhase(st.phase2 + TWO_PI * st.smoothedF2 * invSr);
                        val = 0.5 * (val + v2);
                        lastCarrierHz = 0.5 * (instHz + st.smoothedF2);
                    }
                    if (amOn && amDepth > 0.0f) val *= (1.0 - amDepth * 0.5 * (1.0 - Math.cos(st.amPhase)));
                    if (rmOn && rmDepth > 0.0f) val *= ((1.0 - rmDepth) + rmDepth * Math.cos(st.rmPhase));
                    sampleSum += val;
                    instantDisplayHz = (float) st.smoothedF1;
                }

                if (activeSweeps != null) {
                    for (int s = 0; s < activeSweeps.length; s++) {
                        SweepItem item = activeSweeps[s];
                        if (!item.isPlaying) continue;
                        activeSources++;
                        double tri = (item.sweepPos <= 1.0) ? item.sweepPos : (2.0 - item.sweepPos);
                        double sHz = clampHz(item.startHz);
                        double eHz = clampHz(item.endHz);
                        double curHz = clampHz(sHz + (eHz - sHz) * tri);
                        item.currentHz = (float) curHz;
                        if (!manualOn && activeSources == 1) instantDisplayHz = (float) curHz;

                        if (Math.abs(eHz - sHz) > 0.001) {
                            item.sweepPos += 2.0 / (Math.max(1.0, (double) item.durationSec) * sr);
                            if (item.sweepPos >= 2.0) {
                                if (item.isLoop) item.sweepPos -= 2.0;
                                else {
                                    item.isPlaying = false;
                                    item.sweepPos = 0.0;
                                }
                            }
                        }
                        double instHz = curHz;
                        if (fmOn && fmDepth > 0.0f) {
                            instHz = clampHz(curHz * (1.0 + fmDepth * Math.sin(st.fmPhase)));
                        }
                        double val = Math.sin(item.phase);
                        item.phase = wrapPhase(item.phase + TWO_PI * instHz * invSr);
                        lastCarrierHz = instHz;
                        if (item.secondaryHz >= MIN_FREQ_HZ) {
                            double f2 = clampHz(item.secondaryHz);
                            double v2 = Math.sin(item.phase2);
                            item.phase2 = wrapPhase(item.phase2 + TWO_PI * f2 * invSr);
                            val = 0.5 * (val + v2);
                            lastCarrierHz = 0.5 * (instHz + f2);
                        }
                        if (amOn && amDepth > 0.0f) val *= (1.0 - amDepth * 0.5 * (1.0 - Math.cos(st.amPhase)));
                        if (rmOn && rmDepth > 0.0f) val *= ((1.0 - rmDepth) + rmDepth * Math.cos(st.rmPhase));
                        sampleSum += val;
                    }
                }
                if (fmOn) st.fmPhase = wrapPhase(st.fmPhase + TWO_PI * fmRateHz * invSr);
                if (amOn) st.amPhase = wrapPhase(st.amPhase + TWO_PI * amRateHz * invSr);
                if (rmOn) st.rmPhase = wrapPhase(st.rmPhase + TWO_PI * rmRateHz * invSr);
            }

            double outNorm = 0.0;
            if (!auxSuspended && activeSources > 0) {
                double normRaw = clamp(sampleSum / (double) activeSources, -1.0, 1.0);
                st.prevSampleNorm = st.lastSampleNorm;
                st.lastSampleNorm = normRaw;
                st.tailHz = clampHz(lastCarrierHz);
                st.tailLocked = false;
                if (st.envGain < 1.0) st.envGain = Math.min(1.0, st.envGain + fadeInStep);
                outNorm = normRaw * st.envGain * TRUE_PEAK_HEADROOM;
            } else if (st.envGain > 0.0) {
                if (!st.tailLocked) {
                    st.tailPhase = matchTailPhase(st.prevSampleNorm, st.lastSampleNorm);
                    st.tailLocked = true;
                }
                st.tailPhase = wrapPhase(st.tailPhase + TWO_PI * st.tailHz * invSr);
                double tailVal = Math.sin(st.tailPhase);
                st.prevSampleNorm = st.lastSampleNorm;
                st.lastSampleNorm = tailVal;
                st.envGain = Math.max(0.0, st.envGain - (auxSuspended ? auxMuteStep : fadeOutStep));
                outNorm = tailVal * st.envGain * TRUE_PEAK_HEADROOM;
            } else {
                st.tailLocked = false;
                st.prevSampleNorm = 0.0;
                st.lastSampleNorm = 0.0;
            }

            if (outNorm > TRUE_PEAK_HEADROOM) outNorm = TRUE_PEAK_HEADROOM;
            else if (outNorm < -TRUE_PEAK_HEADROOM) outNorm = -TRUE_PEAK_HEADROOM;

            float fOut = (float) outNorm;
            int idx = f * 2;
            floatBuf[idx] = fOut;
            floatBuf[idx + 1] = fOut;
        }
        return instantDisplayHz;
    }

    public static final class LiveQualityReport {
        public final double peak;
        public final double rmsDbfs;
        public final double dcOffset;
        public final double thdPlusNPercent;
        public final int clipOrNanCount;

        public LiveQualityReport(double peak, double rmsDbfs, double dcOffset, double thdPlusNPercent, int clipOrNanCount) {
            this.peak = peak;
            this.rmsDbfs = rmsDbfs;
            this.dcOffset = dcOffset;
            this.thdPlusNPercent = thdPlusNPercent;
            this.clipOrNanCount = clipOrNanCount;
        }
    }

    /**
     * Objective runtime verification over generated 32-bit float PCM samples:
     * measures true peak, RMS dBFS, DC offset, clipping/non-finite anomalies, and
     * joint least-squares THD+N% for pure single or dual sine waves (-1.0 when non-pure).
     */
    public static LiveQualityReport measureLiveBufferQuality(
            float[] monoSamples, int count, int sampleRate,
            boolean isPureSineMode, double f1Hz, double f2Hz) {
        if (monoSamples == null || count <= 0) {
            return new LiveQualityReport(0.0, -120.0, 0.0, -1.0, 0);
        }
        int n = Math.min(count, monoSamples.length);
        double peak = 0.0;
        double sumSq = 0.0;
        double sum = 0.0;
        int clipOrNan = 0;
        int valid = 0;
        for (int i = 0; i < n; i++) {
            float s = monoSamples[i];
            if (Float.isNaN(s) || Float.isInfinite(s)) {
                clipOrNan++;
                continue;
            }
            double v = s;
            double av = Math.abs(v);
            if (av >= 0.999) clipOrNan++;
            if (av > peak) peak = av;
            sumSq += v * v;
            sum += v;
            valid++;
        }
        if (valid <= 0) {
            return new LiveQualityReport(0.0, -120.0, 0.0, -1.0, clipOrNan);
        }
        double rms = Math.sqrt(sumSq / (double) valid);
        double rmsDbfs = 20.0 * Math.log10(Math.max(1e-7, rms));
        double dc = sum / (double) valid;

        double thdPct = -1.0;
        int sr = Math.max(8000, sampleRate);
        if (isPureSineMode && f1Hz >= 0.5 && valid >= 64 && peak > 1e-4) {
            boolean dual = (f2Hz >= 0.5) && (Math.abs(f2Hz - f1Hz) >= 0.1);
            int cols = dual ? 5 : 3;
            double[][] a = new double[cols][cols];
            double[] b = new double[cols];
            double[] phi = new double[cols];
            double w1 = TWO_PI * f1Hz / (double) sr;
            double w2 = dual ? (TWO_PI * f2Hz / (double) sr) : 0.0;

            for (int i = 0; i < n; i++) {
                float s = monoSamples[i];
                if (Float.isNaN(s) || Float.isInfinite(s)) continue;
                double v = s;
                phi[0] = 1.0;
                phi[1] = Math.cos(w1 * i);
                phi[2] = Math.sin(w1 * i);
                if (dual) {
                    phi[3] = Math.cos(w2 * i);
                    phi[4] = Math.sin(w2 * i);
                }
                for (int r = 0; r < cols; r++) {
                    b[r] += phi[r] * v;
                    for (int c = r; c < cols; c++) {
                        a[r][c] += phi[r] * phi[c];
                    }
                }
            }
            for (int r = 1; r < cols; r++) {
                for (int c = 0; c < r; c++) a[r][c] = a[c][r];
            }
            double[] coef = solveLinearSystem(a, b, cols);
            if (coef != null) {
                double sigSq = 0.0;
                double resSq = 0.0;
                for (int i = 0; i < n; i++) {
                    float s = monoSamples[i];
                    if (Float.isNaN(s) || Float.isInfinite(s)) continue;
                    double v = s;
                    double pure = coef[1] * Math.cos(w1 * i) + coef[2] * Math.sin(w1 * i);
                    if (dual) pure += coef[3] * Math.cos(w2 * i) + coef[4] * Math.sin(w2 * i);
                    double fit = coef[0] + pure;
                    double err = v - fit;
                    sigSq += pure * pure;
                    resSq += err * err;
                }
                if (sigSq > 1e-8) {
                    thdPct = 100.0 * Math.sqrt(Math.max(0.0, resSq) / sigSq);
                }
            }
        }
        return new LiveQualityReport(peak, rmsDbfs, dc, thdPct, clipOrNan);
    }

    private static double[] solveLinearSystem(double[][] a, double[] b, int n) {
        for (int p = 0; p < n; p++) {
            int maxRow = p;
            double maxVal = Math.abs(a[p][p]);
            for (int r = p + 1; r < n; r++) {
                double av = Math.abs(a[r][p]);
                if (av > maxVal) {
                    maxVal = av;
                    maxRow = r;
                }
            }
            if (maxVal < 1e-12) return null;
            if (maxRow != p) {
                double[] tmp = a[p];
                a[p] = a[maxRow];
                a[maxRow] = tmp;
                double bt = b[p];
                b[p] = b[maxRow];
                b[maxRow] = bt;
            }
            double pivot = a[p][p];
            for (int r = p + 1; r < n; r++) {
                double factor = a[r][p] / pivot;
                for (int c = p; c < n; c++) a[r][c] -= factor * a[p][c];
                b[r] -= factor * b[p];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double sum = b[r];
            for (int c = r + 1; c < n; c++) sum -= a[r][c] * x[c];
            x[r] = sum / a[r][r];
        }
        return x;
    }
}
