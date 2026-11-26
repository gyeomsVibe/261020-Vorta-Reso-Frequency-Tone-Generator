package com.vorta.synergy;

import java.util.List;

/**
 * Pure 64-bit double NCO -> 32-bit IEEE float acoustic synthesis core for Vorta Synergy.
 * Supports SINGLE, SWEEP, STEPPED_DWELL, REST_COOL, anti-habituation jitter,
 * C^1 clickless thermal duty gating, and -12..0 dB digital soft trim under -1.0 dBTP ceiling.
 * Zero Android dependencies so host JVM audit tests execute exact runtime math.
 */
public final class SynergyDspCore {
    public static final double TWO_PI = 2.0 * Math.PI;
    public static final double TRUE_PEAK_HEADROOM = 0.8912509381337456; // -1.0 dBTP
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
        public double dutyGain = 1.0;
        public double smoothedF1 = -1.0;
        public double smoothedF2 = -1.0;
        public double dualMix = 0.0;

        public double stepProgressSec = 0.0;
        public int activeStepId = -1;
        public boolean sweepReversed = false;
        public double jitterTimeScale = 1.0;
        public double jitterHzOffset = 0.0;

        public double dutyElapsedSec = 0.0;
        public boolean dutyCooling = false;
        public double dutyRemainSec = 0.0;

        public double thermalLoad = 0.0;
        public double smoothedStabGain = 1.0;
        public double lastContourDb = 0.0;
        public double lastThermalDerateDb = 0.0;
        public double lastNotchDb = 0.0;
        public double lastTotalStabDb = 0.0;

        public double tailPhase = 0.0;
        public double tailHz = 28.0;
        public double lastSampleNorm = 0.0;
        public double prevSampleNorm = 0.0;
        public boolean tailLocked = false;

        public long rngState = 0x5DEECE66DL;

        public double nextUniform() {
            long x = rngState;
            x ^= (x << 13);
            x ^= (x >>> 7);
            x ^= (x << 17);
            if (x == 0L) x = 0x9E3779B97F4A7C15L;
            rngState = x;
            return ((x >>> 11) * 0x1.0p-53);
        }

        public double nextTpdf() {
            return nextUniform() - nextUniform();
        }
    }

    public static final class RenderRequest {
        public SynergyChannel channel = SynergyChannel.CH1_SUB_LOW;
        public ScenarioStep singleStep = null;
        public List<ScenarioStep> multiSteps = null;
        public int dutyOnMin = 0;
        public int dutyCoolMin = 0;
        public boolean globalJitter = false;
        public float globalTrimDb = 0.0f;
        public boolean globalFmOn = false;
        public float globalFmDepth = 0.0f;
        public float globalFmRateHz = 0.0f;
        public boolean globalAmOn = false;
        public float globalAmDepth = 0.0f;
        public float globalAmRateHz = 0.0f;
        public boolean globalRmOn = false;
        public float globalRmDepth = 0.0f;
        public float globalRmRateHz = 0.0f;
        public boolean useGlobalModulation = false;
        public boolean auxSuspended = false;
        public boolean stabilizerEnabled = true;
        public float notchCenterHz = 0.0f;
        public float notchHalfWidthHz = 1.5f;
        public boolean batteryThermalThrottle = false;
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

    private SynergyDspCore() {}

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static double wrapPhase(double p) {
        if (p >= TWO_PI || p < 0.0) {
            p %= TWO_PI;
            if (p < 0.0) p += TWO_PI;
        }
        return p;
    }

    public static double matchTailPhase(double prevSample, double lastSample) {
        double y = clamp(lastSample, -1.0, 1.0);
        double theta = Math.asin(y);
        if (lastSample < prevSample) {
            theta = Math.PI - theta;
        }
        return wrapPhase(theta);
    }

    private static double dbToLinear(double db) {
        double c = clamp(db, -12.0, 0.0);
        return Math.pow(10.0, c / 20.0);
    }

    private static double computeStepTargetHz(ScenarioStep st, DspState ds, SynergyChannel ch, double invSr) {
        if (st.type == ScenarioStep.StepType.REST_COOL) {
            return ch.minHz;
        }
        if (st.type == ScenarioStep.StepType.SINGLE) {
            double base = st.minHz + (st.jitterEnabled ? ds.jitterHzOffset : 0.0);
            return ch.clampHz((float) base);
        }

        double lo = ch.clampHz(Math.min(st.minHz, st.maxHz));
        double hi = ch.clampHz(Math.max(st.minHz, st.maxHz));
        double span = Math.max(0.0, hi - lo);
        double cycleSec = Math.max(1.0, st.durationSec * ds.jitterTimeScale);
        ds.stepProgressSec += invSr;
        if (ds.stepProgressSec >= cycleSec) {
            ds.stepProgressSec -= cycleSec;
            if (st.jitterEnabled) {
                ds.sweepReversed = !ds.sweepReversed;
                ds.jitterTimeScale = 0.85 + ds.nextUniform() * 0.30; // +/-15%
                ds.jitterHzOffset = (ds.nextUniform() - 0.5) * 0.8;   // +/-0.4 Hz
            }
        }
        double u = clamp(ds.stepProgressSec / cycleSec, 0.0, 1.0);
        if (ds.sweepReversed) u = 1.0 - u;

        if (st.type == ScenarioStep.StepType.STEPPED_DWELL) {
            int n = Math.max(2, st.stepCount);
            int idx = (int) Math.floor(u * n);
            if (idx >= n) idx = n - 1;
            double frac = (double) idx / (double) (n - 1);
            return ch.clampHz((float) (lo + span * frac + (st.jitterEnabled ? ds.jitterHzOffset : 0.0)));
        }

        // Triangle ping-pong SWEEP
        double tri = (u < 0.5) ? (u * 2.0) : (2.0 - u * 2.0);
        return ch.clampHz((float) (lo + span * tri + (st.jitterEnabled ? ds.jitterHzOffset : 0.0)));
    }

    public static float renderChunk(
            float[] floatBuf, int frames, int sampleRate, DspState st, RenderRequest req) {
        if (floatBuf == null || frames <= 0 || st == null || req == null) return 0.0f;
        int sr = Math.max(8000, sampleRate);
        double invSr = 1.0 / (double) sr;
        SynergyChannel ch = (req.channel != null) ? req.channel : SynergyChannel.CH1_SUB_LOW;
        HardwareStabilizer.Profile stabProf = HardwareStabilizer.profileFor(ch);

        double softSec = req.stabilizerEnabled ? stabProf.softStartSec : 1.0;
        double slewMs = req.stabilizerEnabled ? stabProf.slewMs : 35.0;
        double fadeInStep = 1.0 / (Math.max(0.5, softSec) * sr);
        double fadeOutStep = 1.0 / (0.5 * sr);
        double auxMuteStep = 1.0 / (0.020 * sr);
        double freqSlewAlpha = 1.0 - Math.exp(-1.0 / ((slewMs * 0.001) * sr));
        double dualMixAlpha = 1.0 - Math.exp(-1.0 / (0.040 * sr));
        double stabAlpha = 1.0 - Math.exp(-1.0 / (0.030 * sr));

        boolean singleMode = (req.singleStep != null);
        double dutyOnSec = Math.max(0.0, req.dutyOnMin * 60.0);
        double dutyCoolSec = Math.max(0.0, req.dutyCoolMin * 60.0);
        boolean dutyActive = (dutyOnSec > 0.0 && dutyCoolSec > 0.0);

        float instantHz = 0.0f;
        double sumOutSq = 0.0;

        for (int f = 0; f < frames; f++) {
            // Thermal duty state machine
            boolean stepIsRest = singleMode && req.singleStep.type == ScenarioStep.StepType.REST_COOL;
            if (dutyActive) {
                st.dutyElapsedSec += invSr;
                if (!st.dutyCooling) {
                    if (st.dutyElapsedSec >= dutyOnSec) {
                        st.dutyCooling = true;
                        st.dutyElapsedSec = 0.0;
                    }
                    st.dutyRemainSec = Math.max(0.0, dutyOnSec - st.dutyElapsedSec);
                } else {
                    if (st.dutyElapsedSec >= dutyCoolSec) {
                        st.dutyCooling = false;
                        st.dutyElapsedSec = 0.0;
                    }
                    st.dutyRemainSec = Math.max(0.0, dutyCoolSec - st.dutyElapsedSec);
                }
            } else {
                st.dutyCooling = false;
                st.dutyElapsedSec = 0.0;
                st.dutyRemainSec = 0.0;
            }

            boolean shouldSilenceForDuty = stepIsRest || st.dutyCooling;
            if (shouldSilenceForDuty) {
                if (st.dutyGain > 0.0) st.dutyGain = Math.max(0.0, st.dutyGain - fadeOutStep);
            } else {
                if (st.dutyGain < 1.0) st.dutyGain = Math.min(1.0, st.dutyGain + fadeInStep);
            }

            double sampleSum = 0.0;
            int activeSources = 0;
            double lastCarrierHz = ch.minHz;
            double totalTrimDb = req.globalTrimDb;

            if (singleMode) {
                ScenarioStep step = req.singleStep;
                if (step.id != st.activeStepId) {
                    st.activeStepId = step.id;
                    st.stepProgressSec = 0.0;
                    st.sweepReversed = false;
                    st.jitterTimeScale = 1.0;
                    st.jitterHzOffset = 0.0;
                }
                boolean useJitter = step.jitterEnabled || req.globalJitter;
                boolean prevJ = step.jitterEnabled;
                step.jitterEnabled = useJitter;
                double targetF1 = computeStepTargetHz(step, st, ch, invSr);
                step.jitterEnabled = prevJ;

                if (st.smoothedF1 <= 0.0) st.smoothedF1 = targetF1;
                else st.smoothedF1 += (targetF1 - st.smoothedF1) * freqSlewAlpha;
                double f1 = ch.clampHz((float) st.smoothedF1);
                lastCarrierHz = f1;
                instantHz = (float) f1;
                step.currentHz = instantHz;

                double targetF2 = (step.secondaryHz > 0.0f) ? ch.clampHz(step.secondaryHz) : 0.0;
                double targetDual = (targetF2 > 0.0) ? 1.0 : 0.0;
                st.dualMix += (targetDual - st.dualMix) * dualMixAlpha;
                if (targetF2 > 0.0) {
                    if (st.smoothedF2 <= 0.0) st.smoothedF2 = targetF2;
                    else st.smoothedF2 += (targetF2 - st.smoothedF2) * freqSlewAlpha;
                }

                boolean fmOn = req.useGlobalModulation ? req.globalFmOn : step.fmOn;
                double fmD = req.useGlobalModulation ? req.globalFmDepth : step.fmDepth;
                double fmR = req.useGlobalModulation ? req.globalFmRateHz : step.fmRateHz;
                boolean amOn = req.useGlobalModulation ? req.globalAmOn : step.amOn;
                double amD = req.useGlobalModulation ? req.globalAmDepth : step.amDepth;
                double amR = req.useGlobalModulation ? req.globalAmRateHz : step.amRateHz;
                boolean rmOn = req.useGlobalModulation ? req.globalRmOn : step.rmOn;
                double rmD = req.useGlobalModulation ? req.globalRmDepth : step.rmDepth;
                double rmR = req.useGlobalModulation ? req.globalRmRateHz : step.rmRateHz;

                double fmRatio = (fmOn && fmD > 0.0 && fmR > 0.0) ? (1.0 + fmD * Math.sin(st.fmPhase)) : 1.0;
                double instF1 = ch.clampHz((float) (f1 * fmRatio));
                if (fmOn && fmR > 0.0) instantHz = (float) instF1;
                st.phase1 = wrapPhase(st.phase1 + TWO_PI * instF1 * invSr);
                double s1 = Math.sin(st.phase1);
                double mixed = s1;
                if (st.dualMix > 1e-4 && st.smoothedF2 > 0.0) {
                    double instF2 = ch.clampHz((float) (st.smoothedF2 * fmRatio));
                    st.phase2 = wrapPhase(st.phase2 + TWO_PI * instF2 * invSr);
                    double s2 = Math.sin(st.phase2);
                    mixed = (1.0 - 0.5 * st.dualMix) * s1 + (0.5 * st.dualMix) * s2;
                }
                double modGain = 1.0;
                if (amOn && amD > 0.0 && amR > 0.0) {
                    modGain *= (1.0 - amD + amD * Math.sin(st.amPhase));
                }
                if (rmOn && rmD > 0.0 && rmR > 0.0) {
                    modGain *= ((1.0 - rmD) + rmD * Math.cos(st.rmPhase));
                }
                if (fmOn && fmR > 0.0) st.fmPhase = wrapPhase(st.fmPhase + TWO_PI * fmR * invSr);
                if (amOn && amR > 0.0) st.amPhase = wrapPhase(st.amPhase + TWO_PI * amR * invSr);
                if (rmOn && rmR > 0.0) st.rmPhase = wrapPhase(st.rmPhase + TWO_PI * rmR * invSr);

                totalTrimDb = clamp(step.trimDb + req.globalTrimDb, -12.0, 0.0);
                sampleSum = mixed * modGain;
                activeSources = (step.type == ScenarioStep.StepType.REST_COOL) ? 0 : 1;
            } else if (req.multiSteps != null && !req.multiSteps.isEmpty()) {
                double modGain = 1.0;
                if (req.globalAmOn && req.globalAmDepth > 0.0 && req.globalAmRateHz > 0.0) {
                    modGain *= (1.0 - req.globalAmDepth + req.globalAmDepth * Math.sin(st.amPhase));
                }
                if (req.globalRmOn && req.globalRmDepth > 0.0 && req.globalRmRateHz > 0.0) {
                    modGain *= ((1.0 - req.globalRmDepth) + req.globalRmDepth * Math.cos(st.rmPhase));
                }
                double fmRatio = (req.globalFmOn && req.globalFmDepth > 0.0 && req.globalFmRateHz > 0.0)
                        ? (1.0 + req.globalFmDepth * Math.sin(st.fmPhase)) : 1.0;

                for (int i = 0; i < req.multiSteps.size(); i++) {
                    ScenarioStep item = req.multiSteps.get(i);
                    if (!item.isPlaying || item.type == ScenarioStep.StepType.REST_COOL) continue;
                    double fHz;
                    if (item.type == ScenarioStep.StepType.SINGLE) {
                        fHz = ch.clampHz(item.minHz);
                    } else {
                        double lo = ch.clampHz(Math.min(item.minHz, item.maxHz));
                        double hi = ch.clampHz(Math.max(item.minHz, item.maxHz));
                        double dur = Math.max(1.0, item.durationSec);
                        item.progressSec += invSr;
                        if (item.progressSec >= dur) {
                            if (item.isLoop) item.progressSec -= dur;
                            else {
                                item.progressSec = dur;
                                item.isPlaying = false;
                            }
                        }
                        double u = clamp(item.progressSec / dur, 0.0, 1.0);
                        if (item.type == ScenarioStep.StepType.STEPPED_DWELL) {
                            int n = Math.max(2, item.stepCount);
                            int idx = Math.min(n - 1, (int) Math.floor(u * n));
                            fHz = ch.clampHz((float) (lo + (hi - lo) * ((double) idx / (double) (n - 1))));
                        } else {
                            double tri = (u < 0.5) ? (u * 2.0) : (2.0 - u * 2.0);
                            fHz = ch.clampHz((float) (lo + (hi - lo) * tri));
                        }
                    }
                    item.currentHz = (float) fHz;
                    lastCarrierHz = fHz;
                    if (activeSources == 0) instantHz = (float) fHz;

                    double instF1 = ch.clampHz((float) (fHz * fmRatio));
                    item.phase1 = wrapPhase(item.phase1 + TWO_PI * instF1 * invSr);
                    double s = Math.sin(item.phase1);
                    if (item.secondaryHz > 0.0f) {
                        double instF2 = ch.clampHz((float) (ch.clampHz(item.secondaryHz) * fmRatio));
                        item.phase2 = wrapPhase(item.phase2 + TWO_PI * instF2 * invSr);
                        s = 0.5 * (s + Math.sin(item.phase2));
                    }
                    sampleSum += s * modGain;
                    activeSources++;
                }
                if (req.globalFmOn && req.globalFmRateHz > 0.0) st.fmPhase = wrapPhase(st.fmPhase + TWO_PI * req.globalFmRateHz * invSr);
                if (req.globalAmOn && req.globalAmRateHz > 0.0) st.amPhase = wrapPhase(st.amPhase + TWO_PI * req.globalAmRateHz * invSr);
                if (req.globalRmOn && req.globalRmRateHz > 0.0) st.rmPhase = wrapPhase(st.rmPhase + TWO_PI * req.globalRmRateHz * invSr);
            }

            double contourDb = HardwareStabilizer.computeContourGainDb(ch, lastCarrierHz, req.stabilizerEnabled);
            double notchDb = HardwareStabilizer.computeNotchCutDb(lastCarrierHz, req.notchCenterHz, req.notchHalfWidthHz);
            double derateDb = HardwareStabilizer.computeThermalDerateDb(st.thermalLoad, stabProf, req.stabilizerEnabled);
            double battDb = req.batteryThermalThrottle ? -3.0 : 0.0;
            double stabDb = clamp(contourDb + notchDb + derateDb + battDb, -18.0, 0.0);
            st.lastContourDb = contourDb;
            st.lastNotchDb = notchDb;
            st.lastThermalDerateDb = derateDb;
            st.lastTotalStabDb = stabDb;

            double targetStabLin = Math.pow(10.0, stabDb / 20.0);
            st.smoothedStabGain += (targetStabLin - st.smoothedStabGain) * stabAlpha;

            double trimLin = dbToLinear(totalTrimDb);
            double outNorm = 0.0;
            if (!req.auxSuspended && activeSources > 0 && st.dutyGain > 0.0) {
                double normRaw = clamp(sampleSum / (double) activeSources, -1.0, 1.0)
                        * trimLin * st.smoothedStabGain * st.dutyGain;
                st.prevSampleNorm = st.lastSampleNorm;
                st.lastSampleNorm = normRaw;
                st.tailHz = ch.clampHz((float) lastCarrierHz);
                st.tailLocked = false;
                if (st.envGain < 1.0) st.envGain = Math.min(1.0, st.envGain + fadeInStep);
                outNorm = normRaw * st.envGain * TRUE_PEAK_HEADROOM;
            } else if (st.envGain > 0.0 && st.dutyGain > 0.0) {
                if (!st.tailLocked) {
                    st.tailPhase = matchTailPhase(st.prevSampleNorm, st.lastSampleNorm);
                    st.tailLocked = true;
                }
                st.tailPhase = wrapPhase(st.tailPhase + TWO_PI * st.tailHz * invSr);
                double tailVal = Math.sin(st.tailPhase) * trimLin * st.smoothedStabGain * st.dutyGain;
                st.prevSampleNorm = st.lastSampleNorm;
                st.lastSampleNorm = tailVal;
                st.envGain = Math.max(0.0, st.envGain - (req.auxSuspended ? auxMuteStep : fadeOutStep));
                outNorm = tailVal * st.envGain * TRUE_PEAK_HEADROOM;
            } else {
                st.tailLocked = false;
                st.prevSampleNorm = 0.0;
                st.lastSampleNorm = 0.0;
                if (st.dutyGain <= 0.0) st.envGain = 0.0;
            }

            if (outNorm > TRUE_PEAK_HEADROOM) outNorm = TRUE_PEAK_HEADROOM;
            else if (outNorm < -TRUE_PEAK_HEADROOM) outNorm = -TRUE_PEAK_HEADROOM;

            sumOutSq += outNorm * outNorm;
            float fOut = (float) outNorm;
            int idx = f * 2;
            floatBuf[idx] = fOut;
            floatBuf[idx + 1] = fOut;
        }
        double chunkRmsNorm = Math.sqrt(sumOutSq / (double) frames) / TRUE_PEAK_HEADROOM;
        st.thermalLoad = HardwareStabilizer.updateThermalLoad(st.thermalLoad, chunkRmsNorm, frames * invSr, stabProf);
        if (st.dutyCooling) return 0.0f;
        return instantHz;
    }

    public static void quantizeToPcm16Tpdf(float[] floatBuf, int samples, short[] pcm16Out, DspState st) {
        if (floatBuf == null || pcm16Out == null || st == null) return;
        int n = Math.min(samples, Math.min(floatBuf.length, pcm16Out.length));
        double lsb = 1.0 / 32767.0;
        for (int i = 0; i < n; i++) {
            double v = clamp(floatBuf[i], -TRUE_PEAK_HEADROOM, TRUE_PEAK_HEADROOM);
            double dithered = v + st.nextTpdf() * lsb;
            long q = Math.round(dithered * 32767.0);
            if (q > MAX_AMPLITUDE_16) q = (long) MAX_AMPLITUDE_16;
            else if (q < -MAX_AMPLITUDE_16) q = -(long) MAX_AMPLITUDE_16;
            pcm16Out[i] = (short) q;
        }
    }

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
