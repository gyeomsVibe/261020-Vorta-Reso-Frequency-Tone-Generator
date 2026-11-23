package com.vorta.aegis;

/**
 * Pure-Java 64-bit IEEE-754 continuous-phase DSP engine with zero Android framework imports
 * for deterministic host JVM verification and Android AudioTrack playback parity with PC v3.2.0.
 * Features:
 * - Zero hard clipping: linear peak-following limiter targeting -0.50 dBTP (0.944061)
 * - 120% clean boost (+1.6 dB) with -2.08 dB unity headroom (CLEAN_BOOST_CEILING / 1.20)
 * - Brown noise (type 4) and 2.8kHz-rolloff tinnitus/speech masking (type 5)
 * - Dual-rate scope rings (48 kHz + 32x decimated 1500 Hz 2.73s low-frequency ring)
 * - Pre-limiter fundamental wave tap vs live output tap for smooth oscilloscope inspection
 */
public final class AegisDspCore {
    private static final double TWO_PI = 2.0 * Math.PI;
    public static final int SAMPLE_RATE = 48000;
    public static final double CLEAN_BOOST_CEILING = 0.9440608762859234; // -0.50 dBTP
    public static final double UNITY_HEADROOM_SCALE = CLEAN_BOOST_CEILING / VolumeCurve.MAX_BOOST_LINEAR;
    public static final int SCOPE_RING_SIZE = 4096;
    public static final int SCOPE_LOW_DECIM = 32; // 48000 / 32 = 1500 Hz (2.73s window)
    public static final int SCOPE_LOW_RING_SIZE = 4096;

    private final int mSampleRate;
    private final double mFadeInStep;
    private final double mFadeOutStep;
    private final double mGainSmoothAlpha;
    private final double mDeclickDecay;
    private final double mLimReleaseAlpha;
    private final double mTinnitusLpAlpha;

    private final Object mCmdLock = new Object();
    private volatile boolean mActive = false;
    private volatile boolean mFadeOut = false;
    private volatile PresetModel mCurrentPreset = null;
    private volatile boolean mPersonalAudioConnected = false;
    private volatile boolean mUltrasonicGuarded = false;
    private volatile int mVolumePercent = VolumeCurve.DEFAULT_PERCENT;
    private volatile boolean mIsMuted = false;
    private double mTargetLinearGain = VolumeCurve.percentToLinearGain(VolumeCurve.DEFAULT_PERCENT);

    private long mPresetFrameCounter = 0L;
    private volatile float mLiveFreqHz = 0.0f;
    private volatile float mLiveFreqRHz = 0.0f;
    private volatile float mLiveRmsDbfs = -96.0f;
    private volatile float mLimiterGrDb = 0.0f;
    private long mOvershootCount = 0L;

    private double mPhaseL = 0.0;
    private double mPhaseR = 0.0;
    private double mPhaseFm = 0.0;
    private double mPhaseAm = 0.0;

    private double mB0 = 0.0;
    private double mB1 = 0.0;
    private double mB2 = 0.0;
    private double mBrownL = 0.0;
    private double mBrownR = 0.0;
    private double mTinB0L = 0.0;
    private double mTinB1L = 0.0;
    private double mTinB0R = 0.0;
    private double mTinB1R = 0.0;
    private int mRngState = 0x51A7F319;

    private double mSmoothedGain;
    private double mFadeInGain = 0.0;
    private double mFadeOutGain = 1.0;
    private double mLimGain = 1.0;

    private int mLastPresetId = -1;
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
        mGainSmoothAlpha = 1.0 - Math.exp(-1.0 / (0.015 * sr));
        mDeclickDecay = Math.pow(0.993, 48000.0 / sr);
        mLimReleaseAlpha = 1.0 - Math.exp(-1.0 / (0.180 * sr));
        mTinnitusLpAlpha = 1.0 - Math.exp(-TWO_PI * 2800.0 / sr);
        mSmoothedGain = mTargetLinearGain;
    }

    public int getSampleRate() { return mSampleRate; }
    public boolean isActive() { return mActive; }
    public boolean isPlaying() { return mActive && !mFadeOut; }
    public PresetModel getCurrentPreset() { return mCurrentPreset; }
    public float getLiveFreqHz() { return isPlaying() ? mLiveFreqHz : 0.0f; }
    public float getLiveFreqRHz() { return isPlaying() ? mLiveFreqRHz : 0.0f; }
    public float getLiveRmsDbfs() { return mActive ? mLiveRmsDbfs : -96.0f; }
    public float getLimiterGrDb() { return mActive ? mLimiterGrDb : 0.0f; }
    public long getOvershootCount() { return mOvershootCount; }
    public boolean isUltrasonicGuarded() { return isPlaying() && mUltrasonicGuarded; }
    public int getVolumePercent() { return mVolumePercent; }
    public boolean isMuted() { return mIsMuted; }

    public void setPersonalAudioConnected(boolean connected) {
        mPersonalAudioConnected = connected;
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
            mFadeOutGain = 1.0;
            mLiveFreqHz = preset.isSweep ? preset.sweepStart : preset.freq1;
            mLiveFreqRHz = preset.isSweep
                    ? preset.sweepStart
                    : (preset.binauralBeat > 0f
                        ? (preset.freq1 + preset.binauralBeat)
                        : (preset.freq2 > 0f ? preset.freq2 : preset.freq1));
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
                mLastPresetId = -1;
                mPrevRawL = 0.0;
                mPrevRawR = 0.0;
                mDeclickL = 0.0;
                mDeclickR = 0.0;
                mB0 = 0.0; mB1 = 0.0; mB2 = 0.0;
                mBrownL = 0.0; mBrownR = 0.0;
                mTinB0L = 0.0; mTinB1L = 0.0;
                mTinB0R = 0.0; mTinB1R = 0.0;
                mDecimAccOutL = 0.0; mDecimAccOutR = 0.0;
                mDecimAccFundL = 0.0; mDecimAccFundR = 0.0;
                mDecimCount = 0;
            }
        }
    }

    public void requestStop() {
        synchronized (mCmdLock) {
            if (mActive) {
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
            if (p.isSweep) {
                double cyclePos = (mPresetFrameCounter % cycleFrames) / durationSamples;
                double tri = (cyclePos <= 1.0) ? cyclePos : (2.0 - cyclePos);
                double sweepFreq = p.sweepStart + (p.sweepEnd - p.sweepStart) * tri;
                fL = sweepFreq;
                fR = sweepFreq;
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
                double white = nextWhiteNoise();
                mB0 = 0.99765 * mB0 + white * 0.0990460;
                mB1 = 0.96300 * mB1 + white * 0.2965164;
                mB2 = 0.57000 * mB2 + white * 1.0526913;
                double pinkRaw = (mB0 + mB1 + mB2 + white * 0.1848) * 0.12;
                double pink = Math.tanh(pinkRaw * 0.85);
                sampleL = sampleL * 0.82 + pink * 0.15;
                sampleR = sampleR * 0.82 + pink * 0.15;
            } else if (p.noiseType == 3) {
                double flutterPhase = (TWO_PI * 38.5 * mPresetFrameCounter) / sr;
                double flutter = 0.6 + 0.4 * Math.sin(flutterPhase);
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

            double fundL = sampleL * amGain;
            double fundR = sampleR * amGain;

            if (p.id != mLastPresetId) {
                if (mLastPresetId != -1) {
                    mDeclickL = mPrevRawL - sampleL;
                    mDeclickR = mPrevRawR - sampleR;
                }
                mLastPresetId = p.id;
            }
            if (Math.abs(mDeclickL) > 1e-6 || Math.abs(mDeclickR) > 1e-6) {
                sampleL += mDeclickL;
                sampleR += mDeclickR;
                mDeclickL *= mDeclickDecay;
                mDeclickR *= mDeclickDecay;
            }
            mPrevRawL = sampleL;
            mPrevRawR = sampleR;

            sampleL *= amGain;
            sampleR *= amGain;

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
                mFadeOutGain -= mFadeOutStep;
                if (mFadeOutGain <= 0.0) {
                    mFadeOutGain = 0.0;
                    mActive = false;
                }
                envGain *= mFadeOutGain;
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
            double scopeScale = mIsMuted ? 0.0 : (envGain * (guard ? 0.48 : boostVis) * mLimGain);
            double scL = Math.max(-1.0, Math.min(1.0, sampleL * scopeScale));
            double scR = Math.max(-1.0, Math.min(1.0, sampleR * scopeScale));
            double scFundL = Math.max(-1.0, Math.min(1.0, fundL * scopeScale));
            double scFundR = Math.max(-1.0, Math.min(1.0, fundR * scopeScale));
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
                mCurrentPreset = null;
                mLastPresetId = -1;
                mPrevRawL = 0.0;
                mPrevRawR = 0.0;
                mDeclickL = 0.0;
                mDeclickR = 0.0;
                mLiveFreqHz = 0.0f;
                mLiveFreqRHz = 0.0f;
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
