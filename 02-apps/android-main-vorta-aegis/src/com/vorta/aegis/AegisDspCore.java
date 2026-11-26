package com.vorta.aegis;

/**
 * Pure-Java 64-bit IEEE-754 continuous-phase DSP engine with zero Android framework imports
 * for deterministic host JVM verification and Android AudioTrack playback parity with PC v3.2.0+.
 * Features:
 * - Zero hard clipping: linear peak-following limiter targeting -0.50 dBTP (0.944061)
 * - 120% clean boost (+1.6 dB) with -2.08 dB unity headroom (CLEAN_BOOST_CEILING / 1.20)
 * - Output acoustic profile compensation (Auto, Phone Speaker Missing-Fundamental/144Hz/Isochronic, Headphone, Pure)
 * - True stereo decorrelated Pink noise + procedural Brown/Pink/Rain ambient bed mixer
 * - Post-AM preset/profile transition de-clicker & squared-envelope gentle sleep fade-out
 * - Dual-rate scope rings (48 kHz + 32x decimated 1500 Hz 2.73s low-frequency ring)
 */
public final class AegisDspCore {
    private static final double TWO_PI = 2.0 * Math.PI;
    public static final int SAMPLE_RATE = 48000;
    public static final double CLEAN_BOOST_CEILING = 0.9440608762859234; // -0.50 dBTP
    public static final double UNITY_HEADROOM_SCALE = CLEAN_BOOST_CEILING / VolumeCurve.MAX_BOOST_LINEAR;
    public static final int SCOPE_RING_SIZE = 4096;
    public static final int SCOPE_LOW_DECIM = 32; // 48000 / 32 = 1500 Hz (2.73s window)
    public static final int SCOPE_LOW_RING_SIZE = 4096;

    // Output acoustic compensation profiles
    public static final int PROFILE_AUTO = 0;
    public static final int PROFILE_PHONE_SPEAKER = 1;
    public static final int PROFILE_HEADPHONE = 2;
    public static final int PROFILE_PURE = 3;

    // Optional procedural ambient bed layers
    public static final int AMBIENT_OFF = 0;
    public static final int AMBIENT_BROWN = 1;
    public static final int AMBIENT_PINK = 2;
    public static final int AMBIENT_RAIN = 3;
    public static final int AMBIENT_MAX_MIX_PCT = 60;

    private final int mSampleRate;
    private final double mFadeInStep;
    private final double mFadeOutStep;
    private final double mGainSmoothAlpha;
    private final double mDeclickDecay;
    private final double mLimReleaseAlpha;
    private final double mTinnitusLpAlpha;
    private final double mRainLpAlpha;

    private final Object mCmdLock = new Object();
    private volatile boolean mActive = false;
    private volatile boolean mFadeOut = false;
    private volatile boolean mGentleFadeActive = false;
    private double mActiveFadeOutStep;
    private volatile PresetModel mCurrentPreset = null;
    private volatile boolean mPersonalAudioConnected = false;
    private volatile boolean mUltrasonicGuarded = false;
    private volatile int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
    private volatile boolean mIsMuted = false;
    private double mTargetLinearGain = VolumeCurve.percentToLinearGain(VolumeCurve.DEFAULT_PERCENT);

    private volatile int mOutputProfileMode = PROFILE_AUTO;
    private volatile int mAmbientType = AMBIENT_OFF;
    private volatile int mAmbientMixPercent = 0;
    private double mSmoothedAmbientMix = 0.0;

    private long mPresetFrameCounter = 0L;
    private volatile float mLiveFreqHz = 0.0f;
    private volatile float mLiveFreqRHz = 0.0f;
    private volatile float mLiveBeatHz = 0.0f;
    private volatile float mLiveRmsDbfs = -96.0f;
    private volatile float mLimiterGrDb = 0.0f;
    private long mOvershootCount = 0L;

    private double mPhaseL = 0.0;
    private double mPhaseR = 0.0;
    private double mPhaseFm = 0.0;
    private double mPhaseAm = 0.0;
    private double mPhaseFlutter = 0.0;
    private double mCarrierPhase = 0.0;
    private double mBeatPhase = 0.0;

    // Stereo decorrelated pink noise (preset noiseType == 1)
    private double mB0L = 0.0, mB1L = 0.0, mB2L = 0.0;
    private double mB0R = 0.0, mB1R = 0.0, mB2R = 0.0;
    private double mBrownL = 0.0;
    private double mBrownR = 0.0;
    private double mTinB0L = 0.0;
    private double mTinB1L = 0.0;
    private double mTinB0R = 0.0;
    private double mTinB1R = 0.0;

    // Dedicated independent stereo ambient bed filter states
    private double mAmbBrownL = 0.0, mAmbBrownR = 0.0;
    private double mAmbPink0L = 0.0, mAmbPink1L = 0.0, mAmbPink2L = 0.0;
    private double mAmbPink0R = 0.0, mAmbPink1R = 0.0, mAmbPink2R = 0.0;
    private double mAmbRainLpL = 0.0, mAmbRainLpR = 0.0;
    private double mAmbRainDropL = 0.0, mAmbRainDropR = 0.0;
    private double mAmbRainLfoPhase = 0.0;

    private int mRngState = 0x51A7F319;

    private double mSmoothedGain;
    private double mFadeInGain = 0.0;
    private double mFadeOutGain = 1.0;
    private double mLimGain = 1.0;

    private int mLastPresetId = -1;
    private int mLastEffectiveProfile = -1;
    private double mPrevRawL = 0.0;
    private double mPrevRawR = 0.0;
    private double mDeclickL = 0.0;
    private double mDeclickR = 0.0;

    private final float[] mScopeRingL = new float[SCOPE_RING_SIZE];
    private final float[] mScopeRingR = new float[SCOPE_RING_SIZE];
    private final float[] mScopeFundRingL = new float[SCOPE_RING_SIZE];
    private final float[] mScopeFundRingR = new float[SCOPE_RING_SIZE];
    private final float[] mLowOutRingL = new float[SCOPE_LOW_RING_SIZE];
    private final float[] mLowOutRingR = new float[SCOPE_LOW_RING_SIZE];
    private final float[] mLowFundRingL = new float[SCOPE_LOW_RING_SIZE];
    private final float[] mLowFundRingR = new float[SCOPE_LOW_RING_SIZE];
    private final Object mScopeLock = new Object();
    private int mScopeWritePos = 0;
    private int mLowWritePos = 0;
    private volatile boolean mScopeHasData = false;

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

    public AegisDspCore() {
        this(SAMPLE_RATE);
    }

    public AegisDspCore(int sampleRate) {
        int sr = sampleRate > 8000 ? sampleRate : SAMPLE_RATE;
        mSampleRate = sr;
        mFadeInStep = 1.0 / (sr * 1.0);
        mFadeOutStep = 1.0 / (sr * 0.5);
        mActiveFadeOutStep = mFadeOutStep;
        mGainSmoothAlpha = 1.0 - Math.exp(-1.0 / (0.015 * sr));
        mDeclickDecay = Math.pow(0.993, 48000.0 / sr);
        mLimReleaseAlpha = 1.0 - Math.exp(-1.0 / (0.180 * sr));
        mTinnitusLpAlpha = 1.0 - Math.exp(-TWO_PI * 2800.0 / sr);
        mRainLpAlpha = 1.0 - Math.exp(-TWO_PI * 2400.0 / sr);
        mSmoothedGain = mTargetLinearGain;
    }

    public int getSampleRate() { return mSampleRate; }
    public boolean isActive() { return mActive; }
    public boolean isPlaying() { return mActive && !mFadeOut; }
    public PresetModel getCurrentPreset() { return mCurrentPreset; }
    public float getLiveFreqHz() { return isPlaying() ? mLiveFreqHz : 0.0f; }
    public float getLiveFreqRHz() { return isPlaying() ? mLiveFreqRHz : 0.0f; }
    public float getLiveBeatHz() { return isPlaying() ? mLiveBeatHz : 0.0f; }
    public float getLiveRmsDbfs() { return mActive ? mLiveRmsDbfs : -96.0f; }
    public float getLimiterGrDb() { return mActive ? mLimiterGrDb : 0.0f; }
    public long getOvershootCount() { return mOvershootCount; }
    public boolean isUltrasonicGuarded() { return isPlaying() && mUltrasonicGuarded; }
    public int getVolumePercent() { return mVolumePercent; }
    public boolean isMuted() { return mIsMuted; }
    public int getOutputProfileMode() { return mOutputProfileMode; }
    public int getAmbientType() { return mAmbientType; }
    public int getAmbientMixPercent() { return mAmbientMixPercent; }

    public int getEffectiveProfile() {
        int mode = mOutputProfileMode;
        if (mode == PROFILE_AUTO) {
            return mPersonalAudioConnected ? PROFILE_HEADPHONE : PROFILE_PHONE_SPEAKER;
        }
        return mode;
    }

    public boolean isAcousticCompensationActive() {
        PresetModel p = mCurrentPreset;
        if (!isPlaying() || p == null || p.noiseType == 2) {
            return false;
        }
        int eff = getEffectiveProfile();
        if (eff == PROFILE_PURE) {
            return false;
        }
        double f = mLiveFreqHz > 0f ? mLiveFreqHz : (p.isSweep && p.freq1 <= 0f ? p.sweepStart : p.freq1);
        boolean hasBeat = (p.binauralBeat > 0f) || (p.isSweep && p.freq1 > 0f);
        if (eff == PROFILE_PHONE_SPEAKER) {
            return (f > 0.0 && f < 85.0) || hasBeat;
        }
        if (eff == PROFILE_HEADPHONE) {
            return (f > 0.0 && f < 28.0);
        }
        return false;
    }

    public void setPersonalAudioConnected(boolean connected) {
        mPersonalAudioConnected = connected;
    }

    public void setOutputProfileMode(int mode) {
        if (mode < PROFILE_AUTO || mode > PROFILE_PURE) {
            mode = PROFILE_AUTO;
        }
        mOutputProfileMode = mode;
    }

    public void setAmbientLayer(int type, int mixPercent) {
        if (type < AMBIENT_OFF || type > AMBIENT_RAIN) {
            type = AMBIENT_OFF;
        }
        if (mixPercent < 0) mixPercent = 0;
        if (mixPercent > AMBIENT_MAX_MIX_PCT) mixPercent = AMBIENT_MAX_MIX_PCT;
        synchronized (mCmdLock) {
            mAmbientType = type;
            mAmbientMixPercent = mixPercent;
        }
    }

    public void setVolume(int percent, boolean muted) {
        synchronized (mCmdLock) {
            int clamped = VolumeCurve.clampPercent(percent);
            mVolumePercent = clamped;
            mIsMuted = muted;
            mTargetLinearGain = VolumeCurve.percentToLinearGain(clamped);
        }
    }

    public void playPreset(PresetModel preset) {
        if (preset == null) return;
        synchronized (mCmdLock) {
            boolean wasActive = mActive;
            mCurrentPreset = preset;
            mPresetFrameCounter = 0L;
            mFadeOut = false;
            mGentleFadeActive = false;
            mActiveFadeOutStep = mFadeOutStep;
            mFadeOutGain = 1.0;
            if (preset.isSweep && preset.freq1 > 0f) {
                mLiveFreqHz = preset.freq1;
                mLiveFreqRHz = preset.freq1 + preset.sweepStart;
                mLiveBeatHz = preset.sweepStart;
            } else {
                mLiveFreqHz = preset.isSweep ? preset.sweepStart : preset.freq1;
                mLiveFreqRHz = preset.isSweep
                        ? preset.sweepStart
                        : (preset.binauralBeat > 0f
                            ? (preset.freq1 + preset.binauralBeat)
                            : (preset.freq2 > 0f ? preset.freq2 : preset.freq1));
                mLiveBeatHz = preset.binauralBeat;
            }
            mUltrasonicGuarded = (preset.noiseType == 2) && mPersonalAudioConnected;

            if (!wasActive) {
                mActive = true;
                mFadeInGain = 0.0;
                mLimGain = 1.0;
                mLimiterGrDb = 0.0f;
                mLiveRmsDbfs = -96.0f;
                mOvershootCount = 0L;
                double initGain = mUltrasonicGuarded ? (mTargetLinearGain * 0.25) : mTargetLinearGain;
                mSmoothedGain = mIsMuted ? 0.0 : initGain;
                mSmoothedAmbientMix = (preset.noiseType != 2 && mAmbientType != AMBIENT_OFF)
                        ? (mAmbientMixPercent / 100.0) : 0.0;
                mLastPresetId = -1;
                mLastEffectiveProfile = -1;
                mPrevRawL = 0.0;
                mPrevRawR = 0.0;
                mDeclickL = 0.0;
                mDeclickR = 0.0;
                mPhaseFlutter = 0.0;
                mCarrierPhase = 0.0;
                mBeatPhase = 0.0;
                mB0L = 0.0; mB1L = 0.0; mB2L = 0.0;
                mB0R = 0.0; mB1R = 0.0; mB2R = 0.0;
                mBrownL = 0.0; mBrownR = 0.0;
                mTinB0L = 0.0; mTinB1L = 0.0;
                mTinB0R = 0.0; mTinB1R = 0.0;
                mAmbBrownL = 0.0; mAmbBrownR = 0.0;
                mAmbPink0L = 0.0; mAmbPink1L = 0.0; mAmbPink2L = 0.0;
                mAmbPink0R = 0.0; mAmbPink1R = 0.0; mAmbPink2R = 0.0;
                mAmbRainLpL = 0.0; mAmbRainLpR = 0.0;
                mAmbRainDropL = 0.0; mAmbRainDropR = 0.0;
                mAmbRainLfoPhase = 0.0;
                mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
                mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
                mDecimCount = 0;
            }
        }
    }

    public void requestStop() {
        synchronized (mCmdLock) {
            if (mActive) {
                mGentleFadeActive = false;
                mActiveFadeOutStep = mFadeOutStep;
                mFadeOut = true;
            }
        }
    }

    public void requestGentleStop(double fadeSeconds) {
        synchronized (mCmdLock) {
            if (mActive) {
                double sec = fadeSeconds > 0.5 ? fadeSeconds : 0.5;
                mActiveFadeOutStep = 1.0 / (mSampleRate * sec);
                mGentleFadeActive = true;
                mFadeOut = true;
            }
        }
    }

    public double copyScopeSnapshot(boolean fundamentalTap, boolean lowRate, float[] destL, float[] destR) {
        double effectiveSampleRate = lowRate ? (mSampleRate / (double) SCOPE_LOW_DECIM) : (double) mSampleRate;
        if (destL == null && destR == null) {
            return effectiveSampleRate;
        }
        synchronized (mScopeLock) {
            if (lowRate) {
                float[] srcL = fundamentalTap ? mLowFundRingL : mLowOutRingL;
                float[] srcR = fundamentalTap ? mLowFundRingR : mLowOutRingR;
                int wp = mLowWritePos;
                if (destL != null) {
                    int lenL = Math.min(destL.length, SCOPE_LOW_RING_SIZE);
                    for (int i = 0; i < lenL; i++) {
                        destL[i] = srcL[(wp + i) % SCOPE_LOW_RING_SIZE];
                    }
                }
                if (destR != null) {
                    int lenR = Math.min(destR.length, SCOPE_LOW_RING_SIZE);
                    for (int i = 0; i < lenR; i++) {
                        destR[i] = srcR[(wp + i) % SCOPE_LOW_RING_SIZE];
                    }
                }
            } else {
                float[] srcL = fundamentalTap ? mScopeFundRingL : mScopeRingL;
                float[] srcR = fundamentalTap ? mScopeFundRingR : mScopeRingR;
                int wp = mScopeWritePos;
                if (destL != null) {
                    int lenL = Math.min(destL.length, SCOPE_RING_SIZE);
                    for (int i = 0; i < lenL; i++) {
                        destL[i] = srcL[(wp + i) % SCOPE_RING_SIZE];
                    }
                }
                if (destR != null) {
                    int lenR = Math.min(destR.length, SCOPE_RING_SIZE);
                    for (int i = 0; i < lenR; i++) {
                        destR[i] = srcR[(wp + i) % SCOPE_RING_SIZE];
                    }
                }
            }
        }
        return effectiveSampleRate;
    }

    private double nextWhiteNoise() {
        // Allocation-free unsigned xorshift32 scaled symmetrically to [-1.0, 1.0]
        int x = mRngState;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        mRngState = x;
        long u = x & 0xFFFFFFFFL;
        return (u / 2147483647.5) - 1.0;
    }

    private void clearScopeRings() {
        synchronized (mScopeLock) {
            java.util.Arrays.fill(mScopeRingL, 0.0f);
            java.util.Arrays.fill(mScopeRingR, 0.0f);
            java.util.Arrays.fill(mScopeFundRingL, 0.0f);
            java.util.Arrays.fill(mScopeFundRingR, 0.0f);
            java.util.Arrays.fill(mLowOutRingL, 0.0f);
            java.util.Arrays.fill(mLowOutRingR, 0.0f);
            java.util.Arrays.fill(mLowFundRingL, 0.0f);
            java.util.Arrays.fill(mLowFundRingR, 0.0f);
            mScopeWritePos = 0;
            mLowWritePos = 0;
            mScopeHasData = false;
        }
        mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
        mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
        mDecimCount = 0;
    }

    public boolean renderStereoFloat(float[] outInterleaved, int frameCount) {
        if (outInterleaved == null || frameCount <= 0) return false;
        int maxFrames = outInterleaved.length / 2;
        if (frameCount > maxFrames) frameCount = maxFrames;

        PresetModel p = mCurrentPreset;
        if (!mActive || p == null) {
            java.util.Arrays.fill(outInterleaved, 0, frameCount * 2, 0.0f);
            mLiveFreqHz = 0.0f;
            mLiveFreqRHz = 0.0f;
            mLiveBeatHz = 0.0f;
            mLiveRmsDbfs = -96.0f;
            mLimiterGrDb = 0.0f;
            if (mScopeHasData) {
                clearScopeRings();
            }
            return false;
        }

        if (mBlockScopeL.length < frameCount) {
            mBlockScopeL = new float[frameCount];
            mBlockScopeR = new float[frameCount];
            mBlockFundL = new float[frameCount];
            mBlockFundR = new float[frameCount];
        }
        int neededLow = (frameCount / SCOPE_LOW_DECIM) + 2;
        if (mBlockLowOutL.length < neededLow) {
            mBlockLowOutL = new float[neededLow];
            mBlockLowOutR = new float[neededLow];
            mBlockLowFundL = new float[neededLow];
            mBlockLowFundR = new float[neededLow];
        }

        boolean guard = (p.noiseType == 2) && mPersonalAudioConnected;
        mUltrasonicGuarded = guard;
        int effProfile = getEffectiveProfile();
        int ambType = mAmbientType;
        double targetAmbMix = (p.noiseType != 2 && ambType != AMBIENT_OFF)
                ? (mAmbientMixPercent / 100.0) : 0.0;

        int sr = mSampleRate;
        double durationSamples = Math.max(1.0, p.sweepDurationSec * sr);
        long cycleFrames = Math.max(2L, Math.round(durationSamples * 2.0));
        double sumSq = 0.0;
        int writtenFrames = 0;
        int writtenLow = 0;

        for (int i = 0; i < frameCount; i++) {
            mPresetFrameCounter++;

            double fL;
            double fR;
            double activeBeatHz = p.binauralBeat;
            if (p.isSweep && p.freq1 > 0f) {
                double progress = Math.min(1.0, mPresetFrameCounter / durationSamples);
                double beatHz = p.sweepStart + (p.sweepEnd - p.sweepStart) * progress;
                fL = p.freq1;
                fR = p.freq1 + beatHz;
                activeBeatHz = beatHz;
            } else if (p.isSweep) {
                double cyclePos = (mPresetFrameCounter % cycleFrames) / durationSamples;
                double tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                double sweepFreq = p.sweepStart + (p.sweepEnd - p.sweepStart) * tri;
                fL = sweepFreq;
                fR = sweepFreq;
                activeBeatHz = 0.0;
            } else {
                fL = p.freq1;
                if (p.binauralBeat > 0f) {
                    fR = p.freq1 + p.binauralBeat;
                } else if (p.freq2 > 0f) {
                    fR = p.freq2;
                } else {
                    fR = p.freq1;
                }
            }

            if (i == 0) {
                mLiveFreqHz = (float) fL;
                mLiveFreqRHz = (float) fR;
                mLiveBeatHz = (float) activeBeatHz;
            }

            if (p.fmMod && p.fmRate > 0f) {
                mPhaseFm += (TWO_PI * p.fmRate) / sr;
                if (mPhaseFm > TWO_PI) mPhaseFm -= TWO_PI;
                double fmOffset = Math.sin(mPhaseFm) * (fL * p.fmDepth);
                fL += fmOffset;
                fR += fmOffset;
            }

            double amGain = 1.0;
            if (p.amMod && p.amRate > 0f) {
                mPhaseAm += (TWO_PI * p.amRate) / sr;
                if (mPhaseAm > TWO_PI) mPhaseAm -= TWO_PI;
                amGain = 1.0 - (p.amDepth * 0.5 * (1.0 - Math.sin(mPhaseAm)));
            }

            mPhaseL += (TWO_PI * fL) / sr;
            if (mPhaseL > TWO_PI) mPhaseL -= TWO_PI;

            mPhaseR += (TWO_PI * fR) / sr;
            if (mPhaseR > TWO_PI) mPhaseR -= TWO_PI;

            double sampleL;
            double sampleR;
            if (p.isAntiPhase) {
                if (p.binauralBeat > 0f) {
                    sampleL = -Math.sin(mPhaseL);
                    sampleR = -Math.sin(mPhaseR);
                } else if (p.freq2 > 0f) {
                    double mixed = (-0.65 * Math.sin(mPhaseL)) + (0.35 * Math.sin(mPhaseR));
                    sampleL = mixed;
                    sampleR = mixed;
                } else {
                    double inv = -Math.sin(mPhaseL);
                    sampleL = inv;
                    sampleR = inv;
                }
            } else {
                sampleL = Math.sin(mPhaseL);
                sampleR = Math.sin(mPhaseR);
            }

            if (p.noiseType == 1) {
                double whiteL = nextWhiteNoise();
                mB0L = 0.99765 * mB0L + whiteL * 0.0990460;
                mB1L = 0.96300 * mB1L + whiteL * 0.2965164;
                mB2L = 0.57000 * mB2L + whiteL * 1.0526913;
                double pinkL = Math.tanh((mB0L + mB1L + mB2L + whiteL * 0.1848) * 0.102);

                double whiteR = nextWhiteNoise();
                mB0R = 0.99765 * mB0R + whiteR * 0.0990460;
                mB1R = 0.96300 * mB1R + whiteR * 0.2965164;
                mB2R = 0.57000 * mB2R + whiteR * 1.0526913;
                double pinkR = Math.tanh((mB0R + mB1R + mB2R + whiteR * 0.1848) * 0.102);

                sampleL = sampleL * 0.82 + pinkL * 0.15;
                sampleR = sampleR * 0.82 + pinkR * 0.15;
            } else if (p.noiseType == 3) {
                mPhaseFlutter += (TWO_PI * 38.5) / sr;
                if (mPhaseFlutter > TWO_PI) mPhaseFlutter -= TWO_PI;
                double flutter = 0.6 + 0.4 * Math.sin(mPhaseFlutter);
                sampleL *= flutter;
                sampleR *= flutter;
            } else if (p.noiseType == 4) {
                double wL = nextWhiteNoise();
                double wR = nextWhiteNoise();
                mBrownL = 0.996 * mBrownL + wL * 0.045;
                mBrownR = 0.996 * mBrownR + wR * 0.045;
                double bL = Math.tanh(mBrownL * 1.15);
                double bR = Math.tanh(mBrownR * 1.15);
                if (!p.isSweep && p.freq1 <= 0f) {
                    sampleL = bL * 0.90;
                    sampleR = bR * 0.90;
                } else {
                    sampleL = sampleL * 0.42 + bL * 0.56;
                    sampleR = sampleR * 0.42 + bR * 0.56;
                }
            } else if (p.noiseType == 5) {
                double wL = nextWhiteNoise();
                double wR = nextWhiteNoise();
                mTinB0L = 0.988 * mTinB0L + wL * 0.085;
                mTinB1L = mTinB1L + mTinnitusLpAlpha * ((mTinB0L + wL * 0.06) - mTinB1L);
                mTinB0R = 0.988 * mTinB0R + wR * 0.085;
                mTinB1R = mTinB1R + mTinnitusLpAlpha * ((mTinB0R + wR * 0.06) - mTinB1R);
                double tL = Math.tanh(mTinB1L * 0.95);
                double tR = Math.tanh(mTinB1R * 0.95);
                if (!p.isSweep && p.freq1 <= 0f) {
                    sampleL = tL * 0.88;
                    sampleR = tR * 0.88;
                } else {
                    sampleL = sampleL * 0.35 + tL * 0.62;
                    sampleR = sampleR * 0.35 + tR * 0.62;
                }
            }

            // Capture clean pre-compensation fundamental wave tap (with AM envelope)
            double fundL = sampleL * amGain;
            double fundR = sampleR * amGain;

            // Output Acoustic Profile Compensation (Psychoacoustic Missing-Fundamental & Speaker Assist)
            if (p.noiseType != 2) {
                if (effProfile == PROFILE_PHONE_SPEAKER) {
                    if (fL > 0.0 && fL < 32.0) {
                        mCarrierPhase += (TWO_PI * 144.0) / sr;
                        if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                        double carrierWave = (Math.sin(mCarrierPhase) + 0.12 * Math.sin(2.0 * mCarrierPhase)) / 1.12;
                        double envL = 0.5 + 0.5 * Math.sin(mPhaseL);
                        double envR = (activeBeatHz > 0.0) ? (0.5 + 0.5 * Math.sin(mPhaseR)) : envL;
                        sampleL = sampleL * 0.72 + carrierWave * envL * 0.24;
                        sampleR = sampleR * 0.72 + carrierWave * envR * 0.24;
                    } else if (fL >= 32.0 && fL < 85.0) {
                        double hL = 0.28 * Math.sin(2.0 * mPhaseL) + 0.14 * Math.sin(3.0 * mPhaseL);
                        double hR = 0.28 * Math.sin(2.0 * mPhaseR) + 0.14 * Math.sin(3.0 * mPhaseR);
                        sampleL = (sampleL + hL) * 0.70;
                        sampleR = (sampleR + hR) * 0.70;
                    }

                    if (activeBeatHz > 0.0) {
                        mBeatPhase += (TWO_PI * activeBeatHz) / sr;
                        if (mBeatPhase > TWO_PI) mBeatPhase -= TWO_PI;
                        double pulse = 0.86 + 0.14 * Math.sin(mBeatPhase);
                        sampleL *= pulse;
                        sampleR *= pulse;
                    }
                } else if (effProfile == PROFILE_HEADPHONE && fL > 0.0 && fL < 28.0) {
                    mCarrierPhase += (TWO_PI * 128.0) / sr;
                    if (mCarrierPhase > TWO_PI) mCarrierPhase -= TWO_PI;
                    double gentleCarrier = Math.sin(mCarrierPhase) * (0.5 + 0.5 * Math.sin(mPhaseL));
                    sampleL = sampleL * 0.84 + gentleCarrier * 0.15;
                    sampleR = sampleR * 0.84 + gentleCarrier * 0.15;
                }
            }

            // Optional procedural stereo ambient soundscape bed mixer
            mSmoothedAmbientMix += mGainSmoothAlpha * (targetAmbMix - mSmoothedAmbientMix);
            if (p.noiseType != 2 && mSmoothedAmbientMix > 1e-5) {
                double ambL = 0.0;
                double ambR = 0.0;
                if (ambType == AMBIENT_BROWN) {
                    double awL = nextWhiteNoise();
                    double awR = nextWhiteNoise();
                    mAmbBrownL = 0.996 * mAmbBrownL + awL * 0.045;
                    mAmbBrownR = 0.996 * mAmbBrownR + awR * 0.045;
                    ambL = Math.tanh(mAmbBrownL * 1.15);
                    ambR = Math.tanh(mAmbBrownR * 1.15);
                } else if (ambType == AMBIENT_PINK) {
                    double awL = nextWhiteNoise();
                    mAmbPink0L = 0.99765 * mAmbPink0L + awL * 0.0990460;
                    mAmbPink1L = 0.96300 * mAmbPink1L + awL * 0.2965164;
                    mAmbPink2L = 0.57000 * mAmbPink2L + awL * 1.0526913;
                    ambL = Math.tanh((mAmbPink0L + mAmbPink1L + mAmbPink2L + awL * 0.1848) * 0.102);

                    double awR = nextWhiteNoise();
                    mAmbPink0R = 0.99765 * mAmbPink0R + awR * 0.0990460;
                    mAmbPink1R = 0.96300 * mAmbPink1R + awR * 0.2965164;
                    mAmbPink2R = 0.57000 * mAmbPink2R + awR * 1.0526913;
                    ambR = Math.tanh((mAmbPink0R + mAmbPink1R + mAmbPink2R + awR * 0.1848) * 0.102);
                } else if (ambType == AMBIENT_RAIN) {
                    double awL = nextWhiteNoise();
                    mAmbPink0L = 0.99765 * mAmbPink0L + awL * 0.0990460;
                    mAmbPink1L = 0.96300 * mAmbPink1L + awL * 0.2965164;
                    mAmbPink2L = 0.57000 * mAmbPink2L + awL * 1.0526913;
                    double rBaseL = (mAmbPink0L + mAmbPink1L + mAmbPink2L + awL * 0.1848) * 0.105;
                    mAmbRainLpL = mAmbRainLpL + mRainLpAlpha * (rBaseL - mAmbRainLpL);
                    double dropL = (awL > 0.975) ? (awL - 0.975) * 4.5 : 0.0;
                    mAmbRainDropL = 0.94 * mAmbRainDropL + dropL;

                    double awR = nextWhiteNoise();
                    mAmbPink0R = 0.99765 * mAmbPink0R + awR * 0.0990460;
                    mAmbPink1R = 0.96300 * mAmbPink1R + awR * 0.2965164;
                    mAmbPink2R = 0.57000 * mAmbPink2R + awR * 1.0526913;
                    double rBaseR = (mAmbPink0R + mAmbPink1R + mAmbPink2R + awR * 0.1848) * 0.105;
                    mAmbRainLpR = mAmbRainLpR + mRainLpAlpha * (rBaseR - mAmbRainLpR);
                    double dropR = (awR > 0.975) ? (awR - 0.975) * 4.5 : 0.0;
                    mAmbRainDropR = 0.94 * mAmbRainDropR + dropR;

                    mAmbRainLfoPhase += (TWO_PI * 0.12) / sr;
                    if (mAmbRainLfoPhase > TWO_PI) mAmbRainLfoPhase -= TWO_PI;
                    double swell = 0.94 + 0.06 * Math.sin(mAmbRainLfoPhase);
                    ambL = Math.tanh((mAmbRainLpL * swell + mAmbRainDropL * 0.35) * 1.05);
                    ambR = Math.tanh((mAmbRainLpR * swell + mAmbRainDropR * 0.35) * 1.05);
                }
                double mix = mSmoothedAmbientMix;
                sampleL = sampleL * (1.0 - 0.45 * mix) + ambL * (0.65 * mix);
                sampleR = sampleR * (1.0 - 0.45 * mix) + ambR * (0.65 * mix);
            }

            // Apply AM envelope BEFORE post-AM de-clicker
            sampleL *= amGain;
            sampleR *= amGain;

            if (p.id != mLastPresetId || effProfile != mLastEffectiveProfile) {
                if (mLastPresetId != -1) {
                    mDeclickL = mPrevRawL - sampleL;
                    mDeclickR = mPrevRawR - sampleR;
                }
                mLastPresetId = p.id;
                mLastEffectiveProfile = effProfile;
            }
            if (Math.abs(mDeclickL) > 1e-6 || Math.abs(mDeclickR) > 1e-6) {
                sampleL += mDeclickL;
                sampleR += mDeclickR;
                mDeclickL *= mDeclickDecay;
                mDeclickR *= mDeclickDecay;
            }
            mPrevRawL = sampleL;
            mPrevRawR = sampleR;

            if (mFadeInGain < 1.0) {
                mFadeInGain += mFadeInStep;
                if (mFadeInGain > 1.0) mFadeInGain = 1.0;
            }

            double targetLinear = mTargetLinearGain;
            double guardedTarget = guard ? (targetLinear * 0.25) : targetLinear;
            double effectiveTarget = mIsMuted ? 0.0 : guardedTarget;
            mSmoothedGain += mGainSmoothAlpha * (effectiveTarget - mSmoothedGain);

            double envGain = mFadeInGain;
            if (mFadeOut) {
                mFadeOutGain -= mActiveFadeOutStep;
                if (mFadeOutGain <= 0.0) {
                    mFadeOutGain = 0.0;
                    mActive = false;
                }
                double fadeCurve = mGentleFadeActive ? (mFadeOutGain * mFadeOutGain) : mFadeOutGain;
                envGain *= fadeCurve;
            } else if (mFadeOutGain < 1.0) {
                mFadeOutGain = Math.min(1.0, mFadeOutGain + mFadeInStep);
                envGain *= mFadeOutGain;
            }

            double requestedGain = mSmoothedGain * envGain * UNITY_HEADROOM_SCALE;
            double instPeak = Math.max(Math.abs(sampleL), Math.abs(sampleR)) * requestedGain;
            final double limCeil = CLEAN_BOOST_CEILING * 0.99999; // zero hard clamp
            if (instPeak > limCeil) {
                double needed = limCeil / instPeak;
                if (needed < mLimGain) mLimGain = needed;
            } else if (mLimGain < 1.0) {
                double candidate = mLimGain + mLimReleaseAlpha * (1.0 - mLimGain);
                if (candidate > 1.0) candidate = 1.0;
                if (instPeak * candidate <= limCeil) mLimGain = candidate;
            }

            double totalGain = requestedGain * mLimGain;
            double vL = sampleL * totalGain;
            double vR = sampleR * totalGain;
            if (Double.isNaN(vL) || Double.isInfinite(vL)) vL = 0.0;
            if (Double.isNaN(vR) || Double.isInfinite(vR)) vR = 0.0;
            if (Math.abs(vL) > CLEAN_BOOST_CEILING || Math.abs(vR) > CLEAN_BOOST_CEILING) {
                mOvershootCount++;
            }

            outInterleaved[i * 2] = (float) vL;
            outInterleaved[i * 2 + 1] = (float) vR;
            sumSq += (vL * vL) + (vR * vR);

            double boostVis = (mVolumePercent > VolumeCurve.UNITY_PERCENT)
                    ? (0.84 + 0.12 * ((mVolumePercent - VolumeCurve.UNITY_PERCENT) / 20.0))
                    : 0.82;
            double fundScale = mIsMuted ? 0.0 : (envGain * (guard ? 0.48 : boostVis));
            double scopeScale = fundScale * mLimGain;
            double scL = Math.max(-1.0, Math.min(1.0, sampleL * scopeScale));
            double scR = Math.max(-1.0, Math.min(1.0, sampleR * scopeScale));
            double scFundL = Math.max(-1.0, Math.min(1.0, fundL * fundScale));
            double scFundR = Math.max(-1.0, Math.min(1.0, fundR * fundScale));
            mBlockScopeL[i] = (float) scL;
            mBlockScopeR[i] = (float) scR;
            mBlockFundL[i] = (float) scFundL;
            mBlockFundR[i] = (float) scFundR;
            writtenFrames = i + 1;

            mDecimAccOutL += scL;
            mDecimAccOutR += scR;
            mDecimAccFundL += scFundL;
            mDecimAccFundR += scFundR;
            mDecimCount++;
            if (mDecimCount >= SCOPE_LOW_DECIM) {
                double invDec = 1.0 / SCOPE_LOW_DECIM;
                mBlockLowOutL[writtenLow] = (float) (mDecimAccOutL * invDec);
                mBlockLowOutR[writtenLow] = (float) (mDecimAccOutR * invDec);
                mBlockLowFundL[writtenLow] = (float) (mDecimAccFundL * invDec);
                mBlockLowFundR[writtenLow] = (float) (mDecimAccFundR * invDec);
                writtenLow++;
                mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
                mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
                mDecimCount = 0;
            }

            if (!mActive) {
                int rem = (frameCount - (i + 1)) * 2;
                if (rem > 0) {
                    java.util.Arrays.fill(outInterleaved, (i + 1) * 2, (i + 1) * 2 + rem, 0.0f);
                }
                mFadeOut = false;
                mGentleFadeActive = false;
                mActiveFadeOutStep = mFadeOutStep;
                mCurrentPreset = null;
                mLastPresetId = -1;
                mLastEffectiveProfile = -1;
                mPrevRawL = 0.0;
                mPrevRawR = 0.0;
                mDeclickL = 0.0;
                mDeclickR = 0.0;
                mLiveFreqHz = 0.0f;
                mLiveFreqRHz = 0.0f;
                mLiveBeatHz = 0.0f;
                mUltrasonicGuarded = false;
                mLimGain = 1.0;
                mLimiterGrDb = 0.0f;
                mLiveRmsDbfs = -96.0f;
                break;
            }
        }

        if (!mActive) {
            if (mScopeHasData) {
                clearScopeRings();
            }
        } else if (writtenFrames > 0) {
            synchronized (mScopeLock) {
                for (int k = 0; k < writtenFrames; k++) {
                    mScopeRingL[mScopeWritePos] = mBlockScopeL[k];
                    mScopeRingR[mScopeWritePos] = mBlockScopeR[k];
                    mScopeFundRingL[mScopeWritePos] = mBlockFundL[k];
                    mScopeFundRingR[mScopeWritePos] = mBlockFundR[k];
                    mScopeWritePos++;
                    if (mScopeWritePos >= SCOPE_RING_SIZE) mScopeWritePos = 0;
                }
                for (int k = 0; k < writtenLow; k++) {
                    mLowOutRingL[mLowWritePos] = mBlockLowOutL[k];
                    mLowOutRingR[mLowWritePos] = mBlockLowOutR[k];
                    mLowFundRingL[mLowWritePos] = mBlockLowFundL[k];
                    mLowFundRingR[mLowWritePos] = mBlockLowFundR[k];
                    mLowWritePos++;
                    if (mLowWritePos >= SCOPE_LOW_RING_SIZE) mLowWritePos = 0;
                }
                mScopeHasData = true;
            }
            double rms = Math.sqrt(sumSq / (writtenFrames * 2));
            mLiveRmsDbfs = (rms > 1e-6) ? (float) (20.0 * Math.log10(rms)) : -96.0f;
            mLimiterGrDb = (mLimGain < 0.999) ? (float) (20.0 * Math.log10(mLimGain)) : 0.0f;
        }

        return mActive;
    }
}
