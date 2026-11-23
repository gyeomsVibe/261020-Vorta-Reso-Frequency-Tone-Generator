/**
 * Real-time phase-continuous stereo synthesizer engine for Vorta Aegis Pro v2.3 PC.
 * Faithfully mirrors Android AegisAudioService.java DSP math:
 * - -1.0 dBTP headroom ceiling (29200 / 32768)
 * - 1.0s fade-in, 0.5s fade-out, ~15ms volume smoothing, 48kHz-normalized preset-switch de-clicker
 * - Continuous 64-bit IEEE-754 phase accumulators across FM/AM/Sweep/Binaural/Anti-phase modes
 * - 3-pole IIR pink bio-noise (noiseType=1), ultrasonic -12dB safety guard (noiseType=2), 38.5Hz flutter (noiseType=3)
 */
import { VolumeCurve } from "./policy.js";

const TWO_PI = 2.0 * Math.PI;
export const MAX_AMP_NORM = 29200.0 / 32768.0; // -1.0 dBTP true-peak ceiling

export class AegisDspEngine {
    constructor(sampleRate = 48000) {
        const sr = (Number.isFinite(sampleRate) && sampleRate > 0) ? sampleRate : 48000;
        this.sampleRate = sr;
        this.fadeInStep = 1.0 / (sr * 1.0);
        this.fadeOutStep = 1.0 / (sr * 0.5);
        this.gainSmoothAlpha = 1.0 - Math.exp(-1.0 / (0.015 * sr));
        this.declickDecay = Math.pow(0.993, 48000.0 / sr);

        this.active = false;
        this.fadeOut = false;
        this.currentPreset = null;
        this.presetFrameCounter = 0;
        this.liveFreqHz = 0.0;

        this.phaseL = 0.0;
        this.phaseR = 0.0;
        this.phaseFm = 0.0;
        this.phaseAm = 0.0;

        this.b0 = 0.0;
        this.b1 = 0.0;
        this.b2 = 0.0;

        this.volumePercent = VolumeCurve.DEFAULT_PERCENT;
        this.isMuted = false;
        this.targetLinearGain = VolumeCurve.percentToLinearGain(VolumeCurve.DEFAULT_PERCENT);
        this.smoothedGain = this.targetLinearGain;
        this.ultrasonicGuardEnabled = true;
        this.ultrasonicGuarded = false;

        this.fadeInGain = 0.0;
        this.fadeOutGain = 1.0;

        this.lastPresetId = -1;
        this.prevRawL = 0.0;
        this.prevRawR = 0.0;
        this.declickL = 0.0;
        this.declickR = 0.0;
    }

    setVolume(percent, muted) {
        this.volumePercent = VolumeCurve.clampPercent(percent);
        this.isMuted = Boolean(muted);
        this.targetLinearGain = VolumeCurve.percentToLinearGain(this.volumePercent);
    }

    setUltrasonicGuard(enabled) {
        this.ultrasonicGuardEnabled = Boolean(enabled);
    }

    setPreset(preset) {
        if (!preset) return;
        const wasActive = this.active;
        this.currentPreset = preset;
        this.presetFrameCounter = 0;
        this.fadeOut = false;
        this.fadeOutGain = 1.0;
        this.liveFreqHz = preset.isSweep ? preset.sweepStart : preset.freq1;
        this.ultrasonicGuarded = (preset.noiseType === 2) && this.ultrasonicGuardEnabled;
        if (!wasActive) {
            this.active = true;
            this.fadeInGain = 0.0;
            this.smoothedGain = this.isMuted ? 0.0 : (this.ultrasonicGuarded ? this.targetLinearGain * 0.25 : this.targetLinearGain);
            this.lastPresetId = -1;
            this.prevRawL = 0.0;
            this.prevRawR = 0.0;
            this.declickL = 0.0;
            this.declickR = 0.0;
        }
    }

    requestStop() {
        if (this.active) {
            this.fadeOut = true;
        }
    }

    renderBlock(outL, outR) {
        const len = outL.length;
        if (!this.active || !this.currentPreset) {
            outL.fill(0.0);
            if (outR) outR.fill(0.0);
            return {
                active: false,
                liveHz: 0.0,
                ultrasonicGuarded: false
            };
        }

        const p = this.currentPreset;
        const sr = this.sampleRate;
        const guard = (p.noiseType === 2) && this.ultrasonicGuardEnabled;
        this.ultrasonicGuarded = guard;

        const durationSamples = Math.max(1.0, p.sweepDurationSec * sr);
        const cycleFrames = Math.max(2.0, Math.round(durationSamples * 2.0));

        for (let i = 0; i < len; i++) {
            this.presetFrameCounter++;

            let fL;
            let fR;
            if (p.isSweep) {
                const cyclePos = (this.presetFrameCounter % cycleFrames) / durationSamples;
                const tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                const sweepFreq = p.sweepStart + (p.sweepEnd - p.sweepStart) * tri;
                fL = sweepFreq;
                fR = sweepFreq;
            } else {
                fL = p.freq1;
                if (p.binauralBeat > 0) {
                    fR = p.freq1 + p.binauralBeat;
                } else if (p.freq2 > 0) {
                    fR = p.freq2;
                } else {
                    fR = p.freq1;
                }
            }

            // Record steady nominal carrier Hz before FM modulation offset
            if (i === 0) {
                this.liveFreqHz = fL;
            }

            if (p.fmMod && p.fmRate > 0) {
                this.phaseFm += (TWO_PI * p.fmRate) / sr;
                if (this.phaseFm > TWO_PI) this.phaseFm -= TWO_PI;
                const fmOffset = Math.sin(this.phaseFm) * (fL * p.fmDepth);
                fL += fmOffset;
                fR += fmOffset;
            }

            let amGain = 1.0;
            if (p.amMod && p.amRate > 0) {
                this.phaseAm += (TWO_PI * p.amRate) / sr;
                if (this.phaseAm > TWO_PI) this.phaseAm -= TWO_PI;
                amGain = 1.0 - (p.amDepth * 0.5 * (1.0 - Math.sin(this.phaseAm)));
            }

            this.phaseL += (TWO_PI * fL) / sr;
            if (this.phaseL > TWO_PI) this.phaseL -= TWO_PI;

            this.phaseR += (TWO_PI * fR) / sr;
            if (this.phaseR > TWO_PI) this.phaseR -= TWO_PI;

            let sampleL;
            let sampleR;
            if (p.isAntiPhase) {
                if (p.binauralBeat > 0) {
                    sampleL = -Math.sin(this.phaseL);
                    sampleR = -Math.sin(this.phaseR);
                } else if (p.freq2 > 0) {
                    const mixed = (-0.65 * Math.sin(this.phaseL)) + (0.35 * Math.sin(this.phaseR));
                    sampleL = mixed;
                    sampleR = mixed;
                } else {
                    const inv = -Math.sin(this.phaseL);
                    sampleL = inv;
                    sampleR = inv;
                }
            } else {
                sampleL = Math.sin(this.phaseL);
                sampleR = Math.sin(this.phaseR);
            }

            if (p.noiseType === 1) {
                const white = Math.random() * 2.0 - 1.0;
                this.b0 = 0.99765 * this.b0 + white * 0.0990460;
                this.b1 = 0.96300 * this.b1 + white * 0.2965164;
                this.b2 = 0.57000 * this.b2 + white * 1.0526913;
                const pink = (this.b0 + this.b1 + this.b2 + white * 0.1848) * 0.12;
                sampleL = sampleL * 0.85 + pink * 0.15;
                sampleR = sampleR * 0.85 + pink * 0.15;
            } else if (p.noiseType === 3) {
                const flutterPhase = (TWO_PI * 38.5 * this.presetFrameCounter) / sr;
                const flutter = 0.6 + 0.4 * Math.sin(flutterPhase);
                sampleL *= flutter;
                sampleR *= flutter;
            }

            // Sample-by-sample preset-switch de-clicker
            if (p.id !== this.lastPresetId) {
                if (this.lastPresetId !== -1) {
                    this.declickL = this.prevRawL - sampleL;
                    this.declickR = this.prevRawR - sampleR;
                }
                this.lastPresetId = p.id;
            }
            if (Math.abs(this.declickL) > 1e-6 || Math.abs(this.declickR) > 1e-6) {
                sampleL += this.declickL;
                sampleR += this.declickR;
                this.declickL *= this.declickDecay;
                this.declickR *= this.declickDecay;
            }
            this.prevRawL = sampleL;
            this.prevRawR = sampleR;

            sampleL *= amGain;
            sampleR *= amGain;

            if (this.fadeInGain < 1.0) {
                this.fadeInGain = Math.min(1.0, this.fadeInGain + this.fadeInStep);
            }

            const targetGain = guard ? (this.targetLinearGain * 0.25) : this.targetLinearGain;
            const effectiveTarget = this.isMuted ? 0.0 : targetGain;
            this.smoothedGain += this.gainSmoothAlpha * (effectiveTarget - this.smoothedGain);

            let envGain = this.fadeInGain;
            if (this.fadeOut) {
                this.fadeOutGain -= this.fadeOutStep;
                if (this.fadeOutGain <= 0.0) {
                    this.fadeOutGain = 0.0;
                    this.active = false;
                }
                envGain *= this.fadeOutGain;
            } else if (this.fadeOutGain < 1.0) {
                this.fadeOutGain = Math.min(1.0, this.fadeOutGain + this.fadeInStep);
                envGain *= this.fadeOutGain;
            }

            const totalGain = this.smoothedGain * envGain * MAX_AMP_NORM;
            const vL = Math.max(-MAX_AMP_NORM, Math.min(MAX_AMP_NORM, sampleL * totalGain));
            const vR = Math.max(-MAX_AMP_NORM, Math.min(MAX_AMP_NORM, sampleR * totalGain));
            outL[i] = vL;
            if (outR) outR[i] = vR;

            if (!this.active) {
                for (let j = i + 1; j < len; j++) {
                    outL[j] = 0.0;
                    if (outR) outR[j] = 0.0;
                }
                this.fadeOut = false;
                this.currentPreset = null;
                this.lastPresetId = -1;
                this.prevRawL = 0.0;
                this.prevRawR = 0.0;
                this.declickL = 0.0;
                this.declickR = 0.0;
                this.liveFreqHz = 0.0;
                this.ultrasonicGuarded = false;
                break;
            }
        }

        return {
            active: this.active,
            liveHz: this.active ? this.liveFreqHz : 0.0,
            ultrasonicGuarded: this.active ? this.ultrasonicGuarded : false
        };
    }
}
