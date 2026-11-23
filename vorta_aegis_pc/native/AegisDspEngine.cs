using System;

namespace VortaAegisPC
{
    /// <summary>
    /// 64-bit IEEE-754 continuous-phase synthesizer ported from AegisAudioService.java
    /// and extended with Windows PC speaker psychoacoustic compensation:
    /// - Missing-fundamental 2nd/3rd harmonics for 32-85 Hz presets on desktop/monitor speakers
    /// - 144 Hz warm envelope-locked carrier for sub-32 Hz infrasound presets on small speakers
    /// - Subtle isochronic pulse assist on binaural presets in PcSpeaker mode
    /// - -1.0 dBTP headroom ceiling, preset-switch de-clicker, and ultrasonic -12 dB safety guard
    /// </summary>
    public sealed class AegisDspEngine
    {
        private const double TWO_PI = 2.0 * Math.PI;
        public const double CLEAN_BOOST_CEILING = 0.9440608762859234; // -0.50 dBTP safely below Windows CAudioLimiter
        public const double MAX_AMP_NORM = CLEAN_BOOST_CEILING;
        public const double UNITY_HEADROOM_SCALE = CLEAN_BOOST_CEILING / VolumeCurve.MAX_BOOST_LINEAR;
        public const int SCOPE_RING_SIZE = 4096;

        private int mSampleRate = 48000;
        private double mFadeInStep;
        private double mFadeOutStep;
        private double mGainSmoothAlpha;
        private double mDeclickDecay;
        private double mLimReleaseAlpha;

        private readonly object mCmdLock = new object();
        private volatile bool mActive;
        private volatile bool mFadeOut;
        private volatile PresetItem mCurrentPreset;
        private volatile PcOutputProfile mOutputProfile = PcOutputProfile.PcSpeaker;
        private volatile bool mUltrasonicGuardEnabled = true;
        private volatile bool mUltrasonicGuarded;
        private volatile int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
        private volatile bool mIsMuted;
        private double mTargetLinearGain = VolumeCurve.PercentToLinearGain(VolumeCurve.DEFAULT_PERCENT);

        private long mPresetFrameCounter;
        private float mLiveFreqHz;
        private float mLiveFreqRHz;
        private float mPeakLevel;
        private float mLiveRmsDbfs = -96.0f;
        private float mSweepProgress = 0.0f;
        private volatile float mLimiterGrDb = 0.0f;

        private double mPhaseL;
        private double mPhaseR;
        private double mPhaseFm;
        private double mPhaseAm;
        private double mCarrierPhase;
        private double mBeatPhase;

        private double mB0;
        private double mB1;
        private double mB2;
        private uint mRngState = 0x51A7F319u;

        private double mSmoothedGain;
        private double mFadeInGain;
        private double mFadeOutGain = 1.0;
        private double mLimGain = 1.0;

        private int mLastPresetId = -1;
        private double mPrevRawL;
        private double mPrevRawR;
        private double mDeclickL;
        private double mDeclickR;

        private readonly float[] mScopeRingL = new float[SCOPE_RING_SIZE];
        private readonly float[] mScopeRingR = new float[SCOPE_RING_SIZE];
        private readonly object mScopeLock = new object();
        private int mScopeWritePos = 0;
        private volatile bool mScopeHasData = false;
        private float[] mBlockScopeL = new float[2048];
        private float[] mBlockScopeR = new float[2048];

        public AegisDspEngine(int sampleRate)
        {
            SetSampleRate(sampleRate);
            mSmoothedGain = mTargetLinearGain;
        }

        public void SetSampleRate(int sampleRate)
        {
            int sr = sampleRate > 8000 ? sampleRate : 48000;
            mSampleRate = sr;
            mFadeInStep = 1.0 / (sr * 1.0);
            mFadeOutStep = 1.0 / (sr * 0.5);
            mGainSmoothAlpha = 1.0 - Math.Exp(-1.0 / (0.015 * sr));
            mDeclickDecay = Math.Pow(0.993, 48000.0 / sr);
            mLimReleaseAlpha = 1.0 - Math.Exp(-1.0 / (0.180 * sr));
        }

        public int SampleRate { get { return mSampleRate; } }
        public bool IsActive { get { return mActive; } }
        public bool IsPlaying { get { return mActive && !mFadeOut; } }
        public PresetItem CurrentPreset { get { return mCurrentPreset; } }
        public float LiveFreqHz { get { return IsPlaying ? mLiveFreqHz : 0.0f; } }
        public float LiveFreqRHz { get { return IsPlaying ? mLiveFreqRHz : 0.0f; } }
        public float PeakLevel { get { return mActive ? mPeakLevel : 0.0f; } }
        public float LiveRmsDbfs { get { return mActive ? mLiveRmsDbfs : -96.0f; } }
        public float SweepProgress { get { return IsPlaying ? mSweepProgress : 0.0f; } }
        public float LimiterGainReductionDb { get { return mActive ? mLimiterGrDb : 0.0f; } }
        public bool IsUltrasonicGuarded { get { return IsPlaying && mUltrasonicGuarded; } }
        public PcOutputProfile OutputProfile { get { return mOutputProfile; } }
        public bool UltrasonicGuardEnabled { get { return mUltrasonicGuardEnabled; } }
        public int VolumePercent { get { return mVolumePercent; } }
        public bool IsMuted { get { return mIsMuted; } }

        public void CopyScopeSnapshot(float[] destL, float[] destR)
        {
            if (destL == null && destR == null) return;
            lock (mScopeLock)
            {
                int wp = mScopeWritePos;
                if (destL != null)
                {
                    int lenL = Math.Min(destL.Length, SCOPE_RING_SIZE);
                    for (int i = 0; i < lenL; i++)
                    {
                        destL[i] = mScopeRingL[(wp + i) % SCOPE_RING_SIZE];
                    }
                }
                if (destR != null)
                {
                    int lenR = Math.Min(destR.Length, SCOPE_RING_SIZE);
                    for (int i = 0; i < lenR; i++)
                    {
                        destR[i] = mScopeRingR[(wp + i) % SCOPE_RING_SIZE];
                    }
                }
            }
        }

        public void SetVolume(int percent, bool muted)
        {
            lock (mCmdLock)
            {
                int clamped = VolumeCurve.ClampPercent(percent);
                mVolumePercent = clamped;
                mIsMuted = muted;
                mTargetLinearGain = VolumeCurve.PercentToLinearGain(clamped);
            }
        }

        public void SetOutputProfile(PcOutputProfile profile)
        {
            mOutputProfile = profile;
        }

        public void SetUltrasonicGuardEnabled(bool enabled)
        {
            mUltrasonicGuardEnabled = enabled;
        }

        public void PlayPreset(PresetItem preset)
        {
            if (preset == null) return;
            lock (mCmdLock)
            {
                bool wasActive = mActive;
                mCurrentPreset = preset;
                mPresetFrameCounter = 0;
                mFadeOut = false;
                mFadeOutGain = 1.0;
                mLiveFreqHz = preset.IsSweep ? preset.SweepStart : preset.Freq1;
                mLiveFreqRHz = preset.IsSweep
                    ? preset.SweepStart
                    : (preset.BinauralBeat > 0f ? (preset.Freq1 + preset.BinauralBeat) : (preset.Freq2 > 0f ? preset.Freq2 : preset.Freq1));
                mSweepProgress = 0.0f;
                mUltrasonicGuarded = ComputeGuardActive(preset, mOutputProfile, mUltrasonicGuardEnabled);

                if (!wasActive)
                {
                    mActive = true;
                    mFadeInGain = 0.0;
                    mLimGain = 1.0;
                    mLimiterGrDb = 0.0f;
                    mLiveRmsDbfs = -96.0f;
                    double initGain = mUltrasonicGuarded ? (mTargetLinearGain * 0.25) : mTargetLinearGain;
                    mSmoothedGain = mIsMuted ? 0.0 : initGain;
                    mLastPresetId = -1;
                    mPrevRawL = 0.0;
                    mPrevRawR = 0.0;
                    mDeclickL = 0.0;
                    mDeclickR = 0.0;
                }
            }
        }

        public void RequestStop()
        {
            lock (mCmdLock)
            {
                if (mActive)
                {
                    mFadeOut = true;
                }
            }
        }

        private static bool ComputeGuardActive(PresetItem p, PcOutputProfile profile, bool guardToggle)
        {
            if (p == null || p.NoiseType != 2) return false;
            return (profile == PcOutputProfile.Headphone) || guardToggle;
        }

        private double NextWhiteNoise()
        {
            // Fast allocation-free xorshift32 PRNG scaled to [-1.0, 1.0]
            uint x = mRngState;
            x ^= x << 13;
            x ^= x >> 17;
            x ^= x << 5;
            mRngState = x;
            return (x / 2147483647.5) - 1.0;
        }

        public bool RenderStereoFloat(float[] outInterleaved, int frameCount)
        {
            if (outInterleaved == null || frameCount <= 0) return false;
            int totalSamples = frameCount * 2;
            if (outInterleaved.Length < totalSamples) frameCount = outInterleaved.Length / 2;

            PresetItem p = mCurrentPreset;
            if (!mActive || p == null)
            {
                Array.Clear(outInterleaved, 0, frameCount * 2);
                mPeakLevel = 0.0f;
                mLiveFreqRHz = 0.0f;
                mLiveRmsDbfs = -96.0f;
                mSweepProgress = 0.0f;
                mLimiterGrDb = 0.0f;
                if (mScopeHasData)
                {
                    lock (mScopeLock)
                    {
                        Array.Clear(mScopeRingL, 0, SCOPE_RING_SIZE);
                        Array.Clear(mScopeRingR, 0, SCOPE_RING_SIZE);
                        mScopeWritePos = 0;
                        mScopeHasData = false;
                    }
                }
                return false;
            }

            if (mBlockScopeL.Length < frameCount)
            {
                mBlockScopeL = new float[frameCount];
                mBlockScopeR = new float[frameCount];
            }

            PcOutputProfile profile = mOutputProfile;
            bool guard = ComputeGuardActive(p, profile, mUltrasonicGuardEnabled);
            mUltrasonicGuarded = guard;

            int sr = mSampleRate;
            double durationSamples = Math.Max(1.0, p.SweepDurationSec * sr);
            long cycleFrames = Math.Max(2L, (long)Math.Round(durationSamples * 2.0));
            double blockPeak = 0.0;
            double sumSq = 0.0;
            int writtenFrames = 0;

            for (int i = 0; i < frameCount; i++)
            {
                mPresetFrameCounter++;

                double fL;
                double fR;
                if (p.IsSweep)
                {
                    double cyclePos = (mPresetFrameCounter % cycleFrames) / durationSamples;
                    double tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                    double sweepFreq = p.SweepStart + (p.SweepEnd - p.SweepStart) * tri;
                    fL = sweepFreq;
                    fR = sweepFreq;
                    if (i == 0) mSweepProgress = (float)tri;
                }
                else
                {
                    fL = p.Freq1;
                    if (p.BinauralBeat > 0f)
                    {
                        fR = p.Freq1 + p.BinauralBeat;
                    }
                    else if (p.Freq2 > 0f)
                    {
                        fR = p.Freq2;
                    }
                    else
                    {
                        fR = p.Freq1;
                    }
                    if (i == 0) mSweepProgress = 0.0f;
                }

                // Capture nominal carrier before FM offset
                if (i == 0)
                {
                    mLiveFreqHz = (float)fL;
                    mLiveFreqRHz = (float)fR;
                }

                if (p.FmMod && p.FmRate > 0f)
                {
                    mPhaseFm += (TWO_PI * p.FmRate) / sr;
                    if (mPhaseFm > TWO_PI) mPhaseFm -= TWO_PI;
                    double fmOffset = Math.Sin(mPhaseFm) * (fL * p.FmDepth);
                    fL += fmOffset;
                    fR += fmOffset;
                }

                double amGain = 1.0;
                if (p.AmMod && p.AmRate > 0f)
                {
                    mPhaseAm += (TWO_PI * p.AmRate) / sr;
                    if (mPhaseAm > TWO_PI) mPhaseAm -= TWO_PI;
                    amGain = 1.0 - (p.AmDepth * 0.5 * (1.0 - Math.Sin(mPhaseAm)));
                }

                mPhaseL += (TWO_PI * fL) / sr;
                if (mPhaseL > TWO_PI) mPhaseL -= TWO_PI;

                mPhaseR += (TWO_PI * fR) / sr;
                if (mPhaseR > TWO_PI) mPhaseR -= TWO_PI;

                double sampleL;
                double sampleR;
                if (p.IsAntiPhase)
                {
                    if (p.BinauralBeat > 0f)
                    {
                        sampleL = -Math.Sin(mPhaseL);
                        sampleR = -Math.Sin(mPhaseR);
                    }
                    else if (p.Freq2 > 0f)
                    {
                        double mixed = (-0.65 * Math.Sin(mPhaseL)) + (0.35 * Math.Sin(mPhaseR));
                        sampleL = mixed;
                        sampleR = mixed;
                    }
                    else
                    {
                        double inv = -Math.Sin(mPhaseL);
                        sampleL = inv;
                        sampleR = inv;
                    }
                }
                else
                {
                    sampleL = Math.Sin(mPhaseL);
                    sampleR = Math.Sin(mPhaseR);
                }

                // Noise / Flutter synthesis modes
                if (p.NoiseType == 1)
                {
                    double white = NextWhiteNoise();
                    mB0 = 0.99765 * mB0 + white * 0.0990460;
                    mB1 = 0.96300 * mB1 + white * 0.2965164;
                    mB2 = 0.57000 * mB2 + white * 1.0526913;
                    double pink = (mB0 + mB1 + mB2 + white * 0.1848) * 0.12;
                    sampleL = sampleL * 0.85 + pink * 0.15;
                    sampleR = sampleR * 0.85 + pink * 0.15;
                }
                else if (p.NoiseType == 3)
                {
                    double flutterPhase = (TWO_PI * 38.5 * mPresetFrameCounter) / sr;
                    double flutter = 0.6 + 0.4 * Math.Sin(flutterPhase);
                    sampleL *= flutter;
                    sampleR *= flutter;
                }

                // PC Output Acoustic Profile Compensation (Missing Fundamental & Carrier Assist)
                if (p.NoiseType != 2)
                {
                    if (profile == PcOutputProfile.PcSpeaker)
                    {
                        if (fL < 32.0)
                        {
                            mCarrierPhase += (TWO_PI * 144.0) / sr;
                            if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                            double carrierWave = Math.Sin(mCarrierPhase) + 0.12 * Math.Sin(2.0 * mCarrierPhase);
                            double envL = 0.5 + 0.5 * Math.Sin(mPhaseL);
                            double envR = (p.BinauralBeat > 0f) ? (0.5 + 0.5 * Math.Sin(mPhaseR)) : envL;
                            sampleL = sampleL * 0.78 + carrierWave * envL * 0.26;
                            sampleR = sampleR * 0.78 + carrierWave * envR * 0.26;
                        }
                        else if (fL < 85.0)
                        {
                            double hL = 0.28 * Math.Sin(2.0 * mPhaseL) + 0.14 * Math.Sin(3.0 * mPhaseL);
                            double hR = 0.28 * Math.Sin(2.0 * mPhaseR) + 0.14 * Math.Sin(3.0 * mPhaseR);
                            sampleL = (sampleL + hL) * 0.78;
                            sampleR = (sampleR + hR) * 0.78;
                        }

                        if (p.BinauralBeat > 0f)
                        {
                            mBeatPhase += (TWO_PI * p.BinauralBeat) / sr;
                            if (mBeatPhase > TWO_PI) mBeatPhase -= TWO_PI;
                            double pulse = 0.86 + 0.14 * Math.Sin(mBeatPhase);
                            sampleL *= pulse;
                            sampleR *= pulse;
                        }
                    }
                    else if (profile == PcOutputProfile.Headphone && fL < 28.0)
                    {
                        mCarrierPhase += (TWO_PI * 128.0) / sr;
                        if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                        double gentleCarrier = Math.Sin(mCarrierPhase) * (0.5 + 0.5 * Math.Sin(mPhaseL));
                        sampleL = sampleL * 0.88 + gentleCarrier * 0.16;
                        sampleR = sampleR * 0.88 + gentleCarrier * 0.16;
                    }
                }

                // Sample-by-sample preset-switch de-clicker
                if (p.Id != mLastPresetId)
                {
                    if (mLastPresetId != -1)
                    {
                        mDeclickL = mPrevRawL - sampleL;
                        mDeclickR = mPrevRawR - sampleR;
                    }
                    mLastPresetId = p.Id;
                }
                if (Math.Abs(mDeclickL) > 1e-6 || Math.Abs(mDeclickR) > 1e-6)
                {
                    sampleL += mDeclickL;
                    sampleR += mDeclickR;
                    mDeclickL *= mDeclickDecay;
                    mDeclickR *= mDeclickDecay;
                }
                mPrevRawL = sampleL;
                mPrevRawR = sampleR;

                sampleL *= amGain;
                sampleR *= amGain;

                if (mFadeInGain < 1.0)
                {
                    mFadeInGain += mFadeInStep;
                    if (mFadeInGain > 1.0) mFadeInGain = 1.0;
                }

                double targetLinear = mTargetLinearGain;
                double guardedTarget = guard ? (targetLinear * 0.25) : targetLinear;
                double effectiveTarget = mIsMuted ? 0.0 : guardedTarget;
                mSmoothedGain += mGainSmoothAlpha * (effectiveTarget - mSmoothedGain);

                double envGain = mFadeInGain;
                if (mFadeOut)
                {
                    mFadeOutGain -= mFadeOutStep;
                    if (mFadeOutGain <= 0.0)
                    {
                        mFadeOutGain = 0.0;
                        mActive = false;
                    }
                    envGain *= mFadeOutGain;
                }
                else if (mFadeOutGain < 1.0)
                {
                    mFadeOutGain = Math.Min(1.0, mFadeOutGain + mFadeInStep);
                    envGain *= mFadeOutGain;
                }

                double requestedGain = mSmoothedGain * envGain * UNITY_HEADROOM_SCALE;
                double instPeak = Math.Max(Math.Abs(sampleL), Math.Abs(sampleR)) * requestedGain;
                if (instPeak > CLEAN_BOOST_CEILING)
                {
                    double needed = CLEAN_BOOST_CEILING / instPeak;
                    if (needed < mLimGain) mLimGain = needed;
                }
                else if (mLimGain < 1.0)
                {
                    mLimGain += mLimReleaseAlpha * (1.0 - mLimGain);
                    if (mLimGain > 1.0) mLimGain = 1.0;
                }

                double totalGain = requestedGain * mLimGain;
                double vL = sampleL * totalGain;
                double vR = sampleR * totalGain;
                if (vL > CLEAN_BOOST_CEILING) vL = CLEAN_BOOST_CEILING;
                else if (vL < -CLEAN_BOOST_CEILING) vL = -CLEAN_BOOST_CEILING;
                if (vR > CLEAN_BOOST_CEILING) vR = CLEAN_BOOST_CEILING;
                else if (vR < -CLEAN_BOOST_CEILING) vR = -CLEAN_BOOST_CEILING;

                outInterleaved[i * 2] = (float)vL;
                outInterleaved[i * 2 + 1] = (float)vR;
                sumSq += (vL * vL) + (vR * vR);

                double boostVis = (mVolumePercent > VolumeCurve.UNITY_PERCENT)
                    ? (0.84 + 0.12 * ((mVolumePercent - VolumeCurve.UNITY_PERCENT) / 20.0))
                    : 0.82;
                double scopeScale = mIsMuted ? 0.0 : (envGain * (guard ? 0.48 : boostVis));
                double scL = sampleL * scopeScale;
                double scR = sampleR * scopeScale;
                if (scL > 1.0) scL = 1.0; else if (scL < -1.0) scL = -1.0;
                if (scR > 1.0) scR = 1.0; else if (scR < -1.0) scR = -1.0;
                mBlockScopeL[i] = (float)scL;
                mBlockScopeR[i] = (float)scR;
                writtenFrames = i + 1;

                double absMax = Math.Max(Math.Abs(vL), Math.Abs(vR));
                if (absMax > blockPeak) blockPeak = absMax;

                if (!mActive)
                {
                    int rem = (frameCount - (i + 1)) * 2;
                    if (rem > 0)
                    {
                        Array.Clear(outInterleaved, (i + 1) * 2, rem);
                    }
                    mFadeOut = false;
                    mCurrentPreset = null;
                    mLastPresetId = -1;
                    mPrevRawL = 0.0;
                    mPrevRawR = 0.0;
                    mDeclickL = 0.0;
                    mDeclickR = 0.0;
                    mLiveFreqHz = 0.0f;
                    mLiveFreqRHz = 0.0f;
                    mSweepProgress = 0.0f;
                    mUltrasonicGuarded = false;
                    mLimGain = 1.0;
                    mLimiterGrDb = 0.0f;
                    mLiveRmsDbfs = -96.0f;
                    break;
                }
            }

            if (!mActive)
            {
                if (mScopeHasData)
                {
                    lock (mScopeLock)
                    {
                        Array.Clear(mScopeRingL, 0, SCOPE_RING_SIZE);
                        Array.Clear(mScopeRingR, 0, SCOPE_RING_SIZE);
                        mScopeWritePos = 0;
                        mScopeHasData = false;
                    }
                }
            }
            else if (writtenFrames > 0)
            {
                lock (mScopeLock)
                {
                    for (int k = 0; k < writtenFrames; k++)
                    {
                        mScopeRingL[mScopeWritePos] = mBlockScopeL[k];
                        mScopeRingR[mScopeWritePos] = mBlockScopeR[k];
                        mScopeWritePos++;
                        if (mScopeWritePos >= SCOPE_RING_SIZE) mScopeWritePos = 0;
                    }
                    mScopeHasData = true;
                }
                double rms = Math.Sqrt(sumSq / (writtenFrames * 2));
                mLiveRmsDbfs = (rms > 1e-6) ? (float)(20.0 * Math.Log10(rms)) : -96.0f;
                mLimiterGrDb = (mLimGain < 0.999) ? (float)(20.0 * Math.Log10(mLimGain)) : 0.0f;
            }

            mPeakLevel = (float)(mPeakLevel * 0.4 + blockPeak * 0.6);
            return mActive;
        }
    }
}
