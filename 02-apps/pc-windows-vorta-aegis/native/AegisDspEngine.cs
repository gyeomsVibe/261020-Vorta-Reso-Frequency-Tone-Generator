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
        public const int SCOPE_LOW_DECIM = 32; // 48000 / 32 = 1500 Hz -> 2.73s window for low frequencies
        public const int SCOPE_LOW_RING_SIZE = 4096;

        public const int AMBIENT_OFF = 0;
        public const int AMBIENT_BROWN = 1;
        public const int AMBIENT_PINK = 2;
        public const int AMBIENT_RAIN = 3;
        public const int AMBIENT_MAX_MIX = 60;

        private int mSampleRate = 48000;
        private double mFadeInStep;
        private double mFadeOutStep;
        private double mActiveFadeOutStep;
        private double mGainSmoothAlpha;
        private double mAmbSmoothAlpha;
        private double mDeclickDecay;
        private double mLimReleaseAlpha;
        private double mTinnitusLpAlpha;
        private double mRainLpAlpha;
        private double mRainHpAlpha;

        private readonly object mCmdLock = new object();
        private volatile bool mActive;
        private volatile bool mFadeOut;
        private volatile bool mGentleFadeOut;
        private volatile PresetItem mCurrentPreset;
        private volatile PcOutputProfile mOutputProfile = PcOutputProfile.PcSpeaker;
        private volatile bool mUltrasonicGuardEnabled = true;
        private volatile bool mUltrasonicGuarded;
        private volatile int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
        private volatile bool mIsMuted;
        private double mTargetLinearGain = VolumeCurve.PercentToLinearGain(VolumeCurve.DEFAULT_PERCENT);

        private volatile int mAmbientType = AMBIENT_OFF;
        private volatile int mAmbientMixPercent = 25;
        private double mAmbMixTarget = 0.0;
        private double mAmbMixSmooth = 0.0;
        private uint mAmbRngState = 0x7A3D91C5u;
        private double mAmbBrownL;
        private double mAmbBrownR;
        private double mAmbPk0L;
        private double mAmbPk1L;
        private double mAmbPk2L;
        private double mAmbPk0R;
        private double mAmbPk1R;
        private double mAmbPk2R;
        private double mAmbRainLpL;
        private double mAmbRainLpR;
        private double mAmbRainHpL;
        private double mAmbRainHpR;
        private double mAmbRainDropL;
        private double mAmbRainDropR;

        private long mPresetFrameCounter;
        private float mLiveFreqHz;
        private float mLiveFreqRHz;
        private float mLiveBeatHz;
        private float mPeakLevel;
        private float mLiveRmsDbfs = -96.0f;
        private float mSweepProgress = 0.0f;
        private volatile float mLimiterGrDb = 0.0f;
        private long mOvershootCount = 0;

        private double mPhaseL;
        private double mPhaseR;
        private double mPhaseFm;
        private double mPhaseAm;
        private double mCarrierPhase;
        private double mBeatPhase;
        private double mFlutterPhase;

        private double mB0L;
        private double mB1L;
        private double mB2L;
        private double mB0R;
        private double mB1R;
        private double mB2R;
        private double mBrownL;
        private double mBrownR;
        private double mTinB0L;
        private double mTinB1L;
        private double mTinB0R;
        private double mTinB1R;
        private uint mRngState = 0x51A7F319u;

        private double mSmoothedGain;
        private double mFadeInGain;
        private double mFadeOutGain = 1.0;
        private double mLimGain = 1.0;

        private int mLastPresetId = -1;
        private PcOutputProfile mLastProfile = PcOutputProfile.PcSpeaker;
        private double mPrevRawL;
        private double mPrevRawR;
        private double mDeclickL;
        private double mDeclickR;

        private readonly float[] mScopeRingL = new float[SCOPE_RING_SIZE];
        private readonly float[] mScopeRingR = new float[SCOPE_RING_SIZE];
        private readonly float[] mScopeFundRingL = new float[SCOPE_RING_SIZE];
        private readonly float[] mScopeFundRingR = new float[SCOPE_RING_SIZE];
        private readonly float[] mLowOutRingL = new float[SCOPE_LOW_RING_SIZE];
        private readonly float[] mLowOutRingR = new float[SCOPE_LOW_RING_SIZE];
        private readonly float[] mLowFundRingL = new float[SCOPE_LOW_RING_SIZE];
        private readonly float[] mLowFundRingR = new float[SCOPE_LOW_RING_SIZE];
        private readonly object mScopeLock = new object();
        private int mScopeWritePos = 0;
        private int mLowWritePos = 0;
        private volatile bool mScopeHasData = false;
        private double mDecimAccOutL = 0.0;
        private double mDecimAccOutR = 0.0;
        private double mDecimAccFundL = 0.0;
        private double mDecimAccFundR = 0.0;
        private int mDecimCount = 0;
        private float[] mBlockScopeL = new float[2048];
        private float[] mBlockScopeR = new float[2048];
        private float[] mBlockFundL = new float[2048];
        private float[] mBlockFundR = new float[2048];
        private float[] mBlockLowOutL = new float[256];
        private float[] mBlockLowOutR = new float[256];
        private float[] mBlockLowFundL = new float[256];
        private float[] mBlockLowFundR = new float[256];

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
            mActiveFadeOutStep = mFadeOutStep;
            mGainSmoothAlpha = 1.0 - Math.Exp(-1.0 / (0.015 * sr));
            mAmbSmoothAlpha = 1.0 - Math.Exp(-1.0 / (0.025 * sr));
            mDeclickDecay = Math.Pow(0.993, 48000.0 / sr);
            mLimReleaseAlpha = 1.0 - Math.Exp(-1.0 / (0.180 * sr));
            mTinnitusLpAlpha = 1.0 - Math.Exp(-TWO_PI * 2800.0 / sr);
            mRainLpAlpha = 1.0 - Math.Exp(-TWO_PI * 2600.0 / sr);
            mRainHpAlpha = 1.0 - Math.Exp(-TWO_PI * 340.0 / sr);
        }

        public int SampleRate { get { return mSampleRate; } }
        public bool IsActive { get { return mActive; } }
        public bool IsPlaying { get { return mActive && !mFadeOut; } }
        public bool IsGentleFadingOut { get { return mActive && mFadeOut && mGentleFadeOut; } }
        public PresetItem CurrentPreset { get { return mCurrentPreset; } }
        public float LiveFreqHz { get { return IsPlaying ? mLiveFreqHz : 0.0f; } }
        public float LiveFreqRHz { get { return IsPlaying ? mLiveFreqRHz : 0.0f; } }
        public float LiveBeatHz { get { return IsPlaying ? mLiveBeatHz : 0.0f; } }
        public float PeakLevel { get { return mActive ? mPeakLevel : 0.0f; } }
        public float LiveRmsDbfs { get { return mActive ? mLiveRmsDbfs : -96.0f; } }
        public float SweepProgress { get { return IsPlaying ? mSweepProgress : 0.0f; } }
        public float LimiterGainReductionDb { get { return mActive ? mLimiterGrDb : 0.0f; } }
        public long OvershootCount { get { return mOvershootCount; } }
        public bool IsUltrasonicGuarded { get { return IsPlaying && mUltrasonicGuarded; } }
        public PcOutputProfile OutputProfile { get { return mOutputProfile; } }
        public bool UltrasonicGuardEnabled { get { return mUltrasonicGuardEnabled; } }
        public int VolumePercent { get { return mVolumePercent; } }
        public bool IsMuted { get { return mIsMuted; } }
        public int AmbientType { get { return mAmbientType; } }
        public int AmbientMixPercent { get { return mAmbientMixPercent; } }

        public bool IsPcCompensationActive
        {
            get
            {
                PresetItem p = mCurrentPreset;
                if (!IsPlaying || p == null || p.NoiseType == 2) return false;
                double f = mLiveFreqHz > 0f ? mLiveFreqHz : (p.IsSweep ? p.SweepStart : p.Freq1);
                if (mOutputProfile == PcOutputProfile.PcSpeaker)
                {
                    return (f > 0.0 && f < 85.0) || (p.BinauralBeat > 0f) || p.IsBinauralRamp;
                }
                if (mOutputProfile == PcOutputProfile.Headphone)
                {
                    return (f > 0.0 && f < 28.0);
                }
                return false;
            }
        }

        public void CopyScopeSnapshot(float[] destL, float[] destR)
        {
            double dummySr;
            CopyScopeSnapshotEx(false, false, destL, destR, out dummySr);
        }

        public void CopyScopeSnapshotEx(bool fundamentalTap, bool lowRate, float[] destL, float[] destR, out double effectiveSampleRate)
        {
            effectiveSampleRate = lowRate ? (mSampleRate / (double)SCOPE_LOW_DECIM) : (double)mSampleRate;
            if (destL == null && destR == null) return;
            lock (mScopeLock)
            {
                if (lowRate)
                {
                    float[] srcL = fundamentalTap ? mLowFundRingL : mLowOutRingL;
                    float[] srcR = fundamentalTap ? mLowFundRingR : mLowOutRingR;
                    int wp = mLowWritePos;
                    if (destL != null)
                    {
                        int lenL = Math.Min(destL.Length, SCOPE_LOW_RING_SIZE);
                        for (int i = 0; i < lenL; i++)
                        {
                            destL[i] = srcL[(wp + i) % SCOPE_LOW_RING_SIZE];
                        }
                    }
                    if (destR != null)
                    {
                        int lenR = Math.Min(destR.Length, SCOPE_LOW_RING_SIZE);
                        for (int i = 0; i < lenR; i++)
                        {
                            destR[i] = srcR[(wp + i) % SCOPE_LOW_RING_SIZE];
                        }
                    }
                }
                else
                {
                    float[] srcL = fundamentalTap ? mScopeFundRingL : mScopeRingL;
                    float[] srcR = fundamentalTap ? mScopeFundRingR : mScopeRingR;
                    int wp = mScopeWritePos;
                    if (destL != null)
                    {
                        int lenL = Math.Min(destL.Length, SCOPE_RING_SIZE);
                        for (int i = 0; i < lenL; i++)
                        {
                            destL[i] = srcL[(wp + i) % SCOPE_RING_SIZE];
                        }
                    }
                    if (destR != null)
                    {
                        int lenR = Math.Min(destR.Length, SCOPE_RING_SIZE);
                        for (int i = 0; i < lenR; i++)
                        {
                            destR[i] = srcR[(wp + i) % SCOPE_RING_SIZE];
                        }
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

        public void SetAmbientBed(int ambientType, int mixPercent)
        {
            lock (mCmdLock)
            {
                int type = ambientType < AMBIENT_OFF ? AMBIENT_OFF : (ambientType > AMBIENT_RAIN ? AMBIENT_RAIN : ambientType);
                int mix = mixPercent < 0 ? 0 : (mixPercent > AMBIENT_MAX_MIX ? AMBIENT_MAX_MIX : mixPercent);
                mAmbientType = type;
                mAmbientMixPercent = mix;
                mAmbMixTarget = (type == AMBIENT_OFF) ? 0.0 : (mix / (double)AMBIENT_MAX_MIX);
            }
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
                mGentleFadeOut = false;
                mActiveFadeOutStep = mFadeOutStep;
                if (preset.IsBinauralRamp)
                {
                    mLiveFreqHz = preset.Freq1;
                    mLiveFreqRHz = preset.Freq1 + preset.SweepStart;
                    mLiveBeatHz = preset.SweepStart;
                }
                else
                {
                    mLiveFreqHz = preset.IsSweep ? preset.SweepStart : preset.Freq1;
                    mLiveFreqRHz = preset.IsSweep
                        ? preset.SweepStart
                        : (preset.BinauralBeat > 0f ? (preset.Freq1 + preset.BinauralBeat) : (preset.Freq2 > 0f ? preset.Freq2 : preset.Freq1));
                    mLiveBeatHz = preset.BinauralBeat > 0f ? preset.BinauralBeat : 0.0f;
                }
                mSweepProgress = 0.0f;
                mUltrasonicGuarded = ComputeGuardActive(preset, mOutputProfile, mUltrasonicGuardEnabled);

                if (!wasActive)
                {
                    mActive = true;
                    mFadeInGain = 0.0;
                    mFadeOutGain = 1.0;
                    mLimGain = 1.0;
                    mLimiterGrDb = 0.0f;
                    mLiveRmsDbfs = -96.0f;
                    mOvershootCount = 0;
                    double initGain = mUltrasonicGuarded ? (mTargetLinearGain * 0.25) : mTargetLinearGain;
                    mSmoothedGain = mIsMuted ? 0.0 : initGain;
                    mAmbMixSmooth = (preset.NoiseType == 2) ? 0.0 : mAmbMixTarget;
                    mLastPresetId = -1;
                    mLastProfile = mOutputProfile;
                    mPrevRawL = 0.0;
                    mPrevRawR = 0.0;
                    mDeclickL = 0.0;
                    mDeclickR = 0.0;
                    mFlutterPhase = 0.0;
                    mB0L = 0.0; mB1L = 0.0; mB2L = 0.0;
                    mB0R = 0.0; mB1R = 0.0; mB2R = 0.0;
                    mBrownL = 0.0; mBrownR = 0.0;
                    mTinB0L = 0.0; mTinB1L = 0.0;
                    mTinB0R = 0.0; mTinB1R = 0.0;
                    mAmbBrownL = 0.0; mAmbBrownR = 0.0;
                    mAmbPk0L = 0.0; mAmbPk1L = 0.0; mAmbPk2L = 0.0;
                    mAmbPk0R = 0.0; mAmbPk1R = 0.0; mAmbPk2R = 0.0;
                    mAmbRainLpL = 0.0; mAmbRainLpR = 0.0;
                    mAmbRainHpL = 0.0; mAmbRainHpR = 0.0;
                    mAmbRainDropL = 0.0; mAmbRainDropR = 0.0;
                    mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
                    mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
                    mDecimCount = 0;
                }
            }
        }

        public void RequestStop()
        {
            lock (mCmdLock)
            {
                if (mActive)
                {
                    mActiveFadeOutStep = mFadeOutStep;
                    mGentleFadeOut = false;
                    mFadeOut = true;
                }
            }
        }

        public void RequestGentleStop(double durationSec)
        {
            lock (mCmdLock)
            {
                if (mActive)
                {
                    double clampedSec = durationSec < 0.5 ? 0.5 : (durationSec > 60.0 ? 60.0 : durationSec);
                    mActiveFadeOutStep = 1.0 / (mSampleRate * clampedSec);
                    mGentleFadeOut = true;
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

        private double NextAmbientWhiteNoise()
        {
            uint x = mAmbRngState;
            x ^= x << 13;
            x ^= x >> 17;
            x ^= x << 5;
            mAmbRngState = x;
            return (x / 2147483647.5) - 1.0;
        }

        private void ClearScopeRings()
        {
            lock (mScopeLock)
            {
                Array.Clear(mScopeRingL, 0, SCOPE_RING_SIZE);
                Array.Clear(mScopeRingR, 0, SCOPE_RING_SIZE);
                Array.Clear(mScopeFundRingL, 0, SCOPE_RING_SIZE);
                Array.Clear(mScopeFundRingR, 0, SCOPE_RING_SIZE);
                Array.Clear(mLowOutRingL, 0, SCOPE_LOW_RING_SIZE);
                Array.Clear(mLowOutRingR, 0, SCOPE_LOW_RING_SIZE);
                Array.Clear(mLowFundRingL, 0, SCOPE_LOW_RING_SIZE);
                Array.Clear(mLowFundRingR, 0, SCOPE_LOW_RING_SIZE);
                mScopeWritePos = 0;
                mLowWritePos = 0;
                mScopeHasData = false;
            }
            mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
            mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
            mDecimCount = 0;
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
                    ClearScopeRings();
                }
                return false;
            }

            if (mBlockScopeL.Length < frameCount)
            {
                mBlockScopeL = new float[frameCount];
                mBlockScopeR = new float[frameCount];
                mBlockFundL = new float[frameCount];
                mBlockFundR = new float[frameCount];
            }
            int neededLow = (frameCount / SCOPE_LOW_DECIM) + 2;
            if (mBlockLowOutL.Length < neededLow)
            {
                mBlockLowOutL = new float[neededLow];
                mBlockLowOutR = new float[neededLow];
                mBlockLowFundL = new float[neededLow];
                mBlockLowFundR = new float[neededLow];
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
            int writtenLow = 0;

            for (int i = 0; i < frameCount; i++)
            {
                mPresetFrameCounter++;

                double fL;
                double fR;
                double liveBeat = 0.0;
                if (p.IsBinauralRamp)
                {
                    double progress = Math.Min(1.0, (double)mPresetFrameCounter / durationSamples);
                    liveBeat = p.SweepStart + (p.SweepEnd - p.SweepStart) * progress;
                    fL = p.Freq1;
                    fR = p.Freq1 + liveBeat;
                    if (i == 0)
                    {
                        mSweepProgress = (float)progress;
                        mLiveBeatHz = (float)liveBeat;
                    }
                }
                else if (p.IsSweep)
                {
                    double cyclePos = (mPresetFrameCounter % cycleFrames) / durationSamples;
                    double tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                    double sweepFreq = p.SweepStart + (p.SweepEnd - p.SweepStart) * tri;
                    fL = sweepFreq;
                    fR = sweepFreq;
                    if (i == 0)
                    {
                        mSweepProgress = (float)tri;
                        mLiveBeatHz = 0.0f;
                    }
                }
                else
                {
                    fL = p.Freq1;
                    if (p.BinauralBeat > 0f)
                    {
                        liveBeat = p.BinauralBeat;
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
                    if (i == 0)
                    {
                        mSweepProgress = 0.0f;
                        mLiveBeatHz = p.BinauralBeat > 0f ? p.BinauralBeat : 0.0f;
                    }
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

                // Noise / Flutter synthesis modes (all strictly bounded in [-1.0, 1.0])
                if (p.NoiseType == 1)
                {
                    // True stereo decorrelated Pink noise
                    double wL = NextWhiteNoise();
                    double wR = NextWhiteNoise();
                    mB0L = 0.99765 * mB0L + wL * 0.0990460;
                    mB1L = 0.96300 * mB1L + wL * 0.2965164;
                    mB2L = 0.57000 * mB2L + wL * 1.0526913;
                    mB0R = 0.99765 * mB0R + wR * 0.0990460;
                    mB1R = 0.96300 * mB1R + wR * 0.2965164;
                    mB2R = 0.57000 * mB2R + wR * 1.0526913;
                    double pL = Math.Tanh(((mB0L + mB1L + mB2L + wL * 0.1848) * 0.12) * 0.85);
                    double pR = Math.Tanh(((mB0R + mB1R + mB2R + wR * 0.1848) * 0.12) * 0.85);
                    sampleL = sampleL * 0.82 + pL * 0.15;
                    sampleR = sampleR * 0.82 + pR * 0.15;
                }
                else if (p.NoiseType == 3)
                {
                    mFlutterPhase += (TWO_PI * 38.5) / sr;
                    if (mFlutterPhase > TWO_PI) mFlutterPhase -= TWO_PI;
                    double flutter = 0.6 + 0.4 * Math.Sin(mFlutterPhase);
                    sampleL *= flutter;
                    sampleR *= flutter;
                }
                else if (p.NoiseType == 4)
                {
                    // Warm stereo 1/f^2 Brown noise masking
                    double wL = NextWhiteNoise();
                    double wR = NextWhiteNoise();
                    mBrownL = 0.996 * mBrownL + wL * 0.045;
                    mBrownR = 0.996 * mBrownR + wR * 0.045;
                    double bL = Math.Tanh(mBrownL * 1.15);
                    double bR = Math.Tanh(mBrownR * 1.15);
                    if (!p.IsSweep && p.Freq1 <= 0f)
                    {
                        sampleL = bL * 0.90;
                        sampleR = bR * 0.90;
                    }
                    else
                    {
                        sampleL = sampleL * 0.42 + bL * 0.56;
                        sampleR = sampleR * 0.42 + bR * 0.56;
                    }
                }
                else if (p.NoiseType == 5)
                {
                    // Warm band-shaped Tinnitus / Speech relief masking (~2.8kHz rolloff)
                    double wL = NextWhiteNoise();
                    double wR = NextWhiteNoise();
                    mTinB0L = 0.988 * mTinB0L + wL * 0.085;
                    mTinB1L = mTinB1L + mTinnitusLpAlpha * ((mTinB0L + wL * 0.06) - mTinB1L);
                    mTinB0R = 0.988 * mTinB0R + wR * 0.085;
                    mTinB1R = mTinB1R + mTinnitusLpAlpha * ((mTinB0R + wR * 0.06) - mTinB1R);
                    double tL = Math.Tanh(mTinB1L * 0.95);
                    double tR = Math.Tanh(mTinB1R * 0.95);
                    if (!p.IsSweep && p.Freq1 <= 0f)
                    {
                        sampleL = tL * 0.88;
                        sampleR = tR * 0.88;
                    }
                    else
                    {
                        sampleL = sampleL * 0.35 + tL * 0.62;
                        sampleR = sampleR * 0.35 + tR * 0.62;
                    }
                }

                // Capture clean pre-compensation fundamental wave tap (with AM envelope)
                double fundL = sampleL * amGain;
                double fundR = sampleR * amGain;

                // PC Output Acoustic Profile Compensation (Missing Fundamental & Carrier Assist)
                if (p.NoiseType != 2)
                {
                    if (profile == PcOutputProfile.PcSpeaker)
                    {
                        if (fL > 0.0 && fL < 32.0)
                        {
                            mCarrierPhase += (TWO_PI * 144.0) / sr;
                            if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                            double carrierWave = (Math.Sin(mCarrierPhase) + 0.12 * Math.Sin(2.0 * mCarrierPhase)) / 1.12;
                            double envL = 0.5 + 0.5 * Math.Sin(mPhaseL);
                            double envR = (p.BinauralBeat > 0f) ? (0.5 + 0.5 * Math.Sin(mPhaseR)) : envL;
                            sampleL = sampleL * 0.72 + carrierWave * envL * 0.24;
                            sampleR = sampleR * 0.72 + carrierWave * envR * 0.24;
                        }
                        else if (fL >= 32.0 && fL < 85.0)
                        {
                            double hL = 0.28 * Math.Sin(2.0 * mPhaseL) + 0.14 * Math.Sin(3.0 * mPhaseL);
                            double hR = 0.28 * Math.Sin(2.0 * mPhaseR) + 0.14 * Math.Sin(3.0 * mPhaseR);
                            sampleL = (sampleL + hL) * 0.70;
                            sampleR = (sampleR + hR) * 0.70;
                        }

                        if (liveBeat > 0.0)
                        {
                            mBeatPhase += (TWO_PI * liveBeat) / sr;
                            if (mBeatPhase > TWO_PI) mBeatPhase -= TWO_PI;
                            double pulse = 0.86 + 0.14 * Math.Sin(mBeatPhase);
                            sampleL *= pulse;
                            sampleR *= pulse;
                        }
                    }
                    else if (profile == PcOutputProfile.Headphone && fL > 0.0 && fL < 28.0)
                    {
                        mCarrierPhase += (TWO_PI * 128.0) / sr;
                        if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                        double gentleCarrier = Math.Sin(mCarrierPhase) * (0.5 + 0.5 * Math.Sin(mPhaseL));
                        sampleL = sampleL * 0.84 + gentleCarrier * 0.15;
                        sampleR = sampleR * 0.84 + gentleCarrier * 0.15;
                    }
                }

                // Apply AM modulation BEFORE ambient mix & de-clicker
                sampleL *= amGain;
                sampleR *= amGain;

                // Optional Ambient Soundscape Bed Mixer (Brown / Pink / Rain, skipped on ultrasonic presets)
                int ambType = (p.NoiseType == 2) ? AMBIENT_OFF : mAmbientType;
                double effAmbTarget = (ambType == AMBIENT_OFF) ? 0.0 : mAmbMixTarget;
                mAmbMixSmooth += mAmbSmoothAlpha * (effAmbTarget - mAmbMixSmooth);
                if (mAmbMixSmooth > 1e-5 && ambType != AMBIENT_OFF)
                {
                    double awL = NextAmbientWhiteNoise();
                    double awR = NextAmbientWhiteNoise();
                    double ambL = 0.0;
                    double ambR = 0.0;
                    if (ambType == AMBIENT_BROWN)
                    {
                        mAmbBrownL = 0.996 * mAmbBrownL + awL * 0.045;
                        mAmbBrownR = 0.996 * mAmbBrownR + awR * 0.045;
                        ambL = Math.Tanh(mAmbBrownL * 1.15);
                        ambR = Math.Tanh(mAmbBrownR * 1.15);
                    }
                    else if (ambType == AMBIENT_PINK)
                    {
                        mAmbPk0L = 0.99765 * mAmbPk0L + awL * 0.0990460;
                        mAmbPk1L = 0.96300 * mAmbPk1L + awL * 0.2965164;
                        mAmbPk2L = 0.57000 * mAmbPk2L + awL * 1.0526913;
                        mAmbPk0R = 0.99765 * mAmbPk0R + awR * 0.0990460;
                        mAmbPk1R = 0.96300 * mAmbPk1R + awR * 0.2965164;
                        mAmbPk2R = 0.57000 * mAmbPk2R + awR * 1.0526913;
                        ambL = Math.Tanh(((mAmbPk0L + mAmbPk1L + mAmbPk2L + awL * 0.1848) * 0.12) * 0.90);
                        ambR = Math.Tanh(((mAmbPk0R + mAmbPk1R + mAmbPk2R + awR * 0.1848) * 0.12) * 0.90);
                    }
                    else if (ambType == AMBIENT_RAIN)
                    {
                        // Soft filtered stereo rainfall + gentle stochastic droplet shimmer
                        mAmbPk0L = 0.992 * mAmbPk0L + awL * 0.068;
                        mAmbPk0R = 0.992 * mAmbPk0R + awR * 0.068;
                        mAmbRainLpL += mRainLpAlpha * ((mAmbPk0L + awL * 0.085) - mAmbRainLpL);
                        mAmbRainLpR += mRainLpAlpha * ((mAmbPk0R + awR * 0.085) - mAmbRainLpR);
                        mAmbRainHpL += mRainHpAlpha * (mAmbRainLpL - mAmbRainHpL);
                        mAmbRainHpR += mRainHpAlpha * (mAmbRainLpR - mAmbRainHpR);
                        double triggerL = NextAmbientWhiteNoise();
                        double triggerR = NextAmbientWhiteNoise();
                        mAmbRainDropL = 0.985 * mAmbRainDropL + (triggerL > 0.994 ? 0.12 : 0.0);
                        mAmbRainDropR = 0.985 * mAmbRainDropR + (triggerR > 0.994 ? 0.12 : 0.0);
                        ambL = Math.Tanh((mAmbRainLpL - mAmbRainHpL * 0.35 + awL * mAmbRainDropL) * 1.10);
                        ambR = Math.Tanh((mAmbRainLpR - mAmbRainHpR * 0.35 + awR * mAmbRainDropR) * 1.10);
                    }
                    sampleL = sampleL * (1.0 - 0.35 * mAmbMixSmooth) + ambL * (0.45 * mAmbMixSmooth);
                    sampleR = sampleR * (1.0 - 0.35 * mAmbMixSmooth) + ambR * (0.45 * mAmbMixSmooth);
                }

                // Sample-by-sample preset-switch AND profile-switch de-clicker
                if (p.Id != mLastPresetId || profile != mLastProfile)
                {
                    if (mLastPresetId != -1)
                    {
                        mDeclickL = mPrevRawL - sampleL;
                        mDeclickR = mPrevRawR - sampleR;
                    }
                    mLastPresetId = p.Id;
                    mLastProfile = profile;
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
                    mFadeOutGain -= mActiveFadeOutStep;
                    if (mFadeOutGain <= 0.0)
                    {
                        mFadeOutGain = 0.0;
                        mActive = false;
                    }
                    envGain *= (mGentleFadeOut ? (mFadeOutGain * mFadeOutGain) : mFadeOutGain);
                }
                else if (mFadeOutGain < 1.0)
                {
                    mFadeOutGain = Math.Min(1.0, mFadeOutGain + mFadeInStep);
                    envGain *= mFadeOutGain;
                }

                double requestedGain = mSmoothedGain * envGain * UNITY_HEADROOM_SCALE;
                double instPeak = Math.Max(Math.Abs(sampleL), Math.Abs(sampleR)) * requestedGain;
                const double LIM_CEIL = CLEAN_BOOST_CEILING * 0.99999; // margin against float32 rounding, zero hard clamp
                if (instPeak > LIM_CEIL)
                {
                    double needed = LIM_CEIL / instPeak;
                    if (needed < mLimGain) mLimGain = needed;
                }
                else if (mLimGain < 1.0)
                {
                    double candidate = mLimGain + mLimReleaseAlpha * (1.0 - mLimGain);
                    if (candidate > 1.0) candidate = 1.0;
                    if (instPeak * candidate <= LIM_CEIL) mLimGain = candidate;
                }

                double totalGain = requestedGain * mLimGain;
                double vL = sampleL * totalGain;
                double vR = sampleR * totalGain;
                if (double.IsNaN(vL) || double.IsInfinity(vL)) vL = 0.0;
                if (double.IsNaN(vR) || double.IsInfinity(vR)) vR = 0.0;
                if (Math.Abs(vL) > CLEAN_BOOST_CEILING || Math.Abs(vR) > CLEAN_BOOST_CEILING)
                {
                    mOvershootCount++;
                }

                outInterleaved[i * 2] = (float)vL;
                outInterleaved[i * 2 + 1] = (float)vR;
                sumSq += (vL * vL) + (vR * vR);

                double boostVis = (mVolumePercent > VolumeCurve.UNITY_PERCENT)
                    ? (0.84 + 0.12 * ((mVolumePercent - VolumeCurve.UNITY_PERCENT) / 20.0))
                    : 0.82;
                double scopeScale = mIsMuted ? 0.0 : (envGain * (guard ? 0.48 : boostVis) * mLimGain);
                double scL = Math.Max(-1.0, Math.Min(1.0, sampleL * scopeScale));
                double scR = Math.Max(-1.0, Math.Min(1.0, sampleR * scopeScale));
                double scFundL = Math.Max(-1.0, Math.Min(1.0, fundL * scopeScale));
                double scFundR = Math.Max(-1.0, Math.Min(1.0, fundR * scopeScale));
                mBlockScopeL[i] = (float)scL;
                mBlockScopeR[i] = (float)scR;
                mBlockFundL[i] = (float)scFundL;
                mBlockFundR[i] = (float)scFundR;
                writtenFrames = i + 1;

                mDecimAccOutL += scL;
                mDecimAccOutR += scR;
                mDecimAccFundL += scFundL;
                mDecimAccFundR += scFundR;
                mDecimCount++;
                if (mDecimCount >= SCOPE_LOW_DECIM)
                {
                    double invDec = 1.0 / SCOPE_LOW_DECIM;
                    mBlockLowOutL[writtenLow] = (float)(mDecimAccOutL * invDec);
                    mBlockLowOutR[writtenLow] = (float)(mDecimAccOutR * invDec);
                    mBlockLowFundL[writtenLow] = (float)(mDecimAccFundL * invDec);
                    mBlockLowFundR[writtenLow] = (float)(mDecimAccFundR * invDec);
                    writtenLow++;
                    mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
                    mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
                    mDecimCount = 0;
                }

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
                    mGentleFadeOut = false;
                    mCurrentPreset = null;
                    mLastPresetId = -1;
                    mPrevRawL = 0.0;
                    mPrevRawR = 0.0;
                    mDeclickL = 0.0;
                    mDeclickR = 0.0;
                    mLiveFreqHz = 0.0f;
                    mLiveFreqRHz = 0.0f;
                    mLiveBeatHz = 0.0f;
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
                    ClearScopeRings();
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
                        mScopeFundRingL[mScopeWritePos] = mBlockFundL[k];
                        mScopeFundRingR[mScopeWritePos] = mBlockFundR[k];
                        mScopeWritePos++;
                        if (mScopeWritePos >= SCOPE_RING_SIZE) mScopeWritePos = 0;
                    }
                    for (int k = 0; k < writtenLow; k++)
                    {
                        mLowOutRingL[mLowWritePos] = mBlockLowOutL[k];
                        mLowOutRingR[mLowWritePos] = mBlockLowOutR[k];
                        mLowFundRingL[mLowWritePos] = mBlockLowFundL[k];
                        mLowFundRingR[mLowWritePos] = mBlockLowFundR[k];
                        mLowWritePos++;
                        if (mLowWritePos >= SCOPE_LOW_RING_SIZE) mLowWritePos = 0;
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
