package com.vorta.aegis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;

/**
 * Zero-allocation 30fps live oscilloscope view for Vorta Aegis Android v3.2.0.
 * Features:
 * - Pre-allocated Paints, Paths, Rect, char[] badge buffer, and 4096-sample L/R snapshot buffers
 *   (strictly 0 heap allocations inside onDraw)
 * - Auto dual-rate view: 1500 Hz decimated ring (<55 Hz, ~3.0 cycles) or 48 kHz ring (>=55 Hz)
 * - Dynamic hysteresis rising zero-crossing trigger (-15% arm threshold)
 * - Stops animation when idle, detached, window-hidden, or scrolled off-screen (getGlobalVisibleRect)
 */
public class LiveWaveformView extends View {
    private static final int DRAW_POINTS = 240;

    private static final char[] IDLE_BADGE = "대기 중 · 무신호 기준선".toCharArray();
    private static final char[] PREFIX_LOW_FUND = "1500Hz · 기본 · ".toCharArray();
    private static final char[] PREFIX_LOW_OUT = "1500Hz · 실출력 · ".toCharArray();
    private static final char[] PREFIX_FAST_FUND = "48kHz · 기본 · ".toCharArray();
    private static final char[] PREFIX_FAST_OUT = "48kHz · 실출력 · ".toCharArray();
    private static final char[] SUFFIX_NOISE = "노이즈".toCharArray();
    private static final char[] SUFFIX_HZ_SEP = "Hz · ".toCharArray();
    private static final char[] SUFFIX_CYCLE = "주기".toCharArray();

    private final float[] mSnapL = new float[AegisDspCore.SCOPE_RING_SIZE];
    private final float[] mSnapR = new float[AegisDspCore.SCOPE_RING_SIZE];
    private final Rect mVisibleRect = new Rect();
    private final char[] mBadgeChars = new char[80];
    private final char[] mDigitTmp = new char[12];
    private int mBadgeLen = 0;

    private final Paint mBgPaint;
    private final Paint mBorderPaint;
    private final Paint mGridPaint;
    private final Paint mZeroPaint;
    private final Paint mIdleLinePaint;
    private final Paint mWaveFillPaint;
    private final Paint mWavePaintL;
    private final Paint mWavePaintR;
    private final Paint mBadgePaint;

    private final Path mPathL = new Path();
    private final Path mFillPathL = new Path();
    private final Path mPathR = new Path();
    private final float mDensity;

    private AegisDspCore mDspCore;
    private boolean mIsActive = false;
    private boolean mFundamentalMode = true;
    private boolean mAttached = false;
    private boolean mLoopScheduled = false;
    private int mLastBadgeKey = Integer.MIN_VALUE;
    private long mLastDrawNs = 0L;
    private float mTravelPhaseL = 0.0f;
    private float mTravelPhaseR = 0.0f;

    private final Runnable mFrameLoop = new Runnable() {
        @Override
        public void run() {
            mLoopScheduled = false;
            if (shouldAnimate()) {
                if (Build.VERSION.SDK_INT >= 16) {
                    postInvalidateOnAnimation();
                } else {
                    invalidate();
                }
            }
        }
    };

    public LiveWaveformView(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;

        mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(Color.parseColor("#101216"));

        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(1.0f * mDensity);
        mBorderPaint.setColor(Color.parseColor("#2A2D35"));

        mGridPaint = new Paint();
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1.0f);
        mGridPaint.setColor(Color.parseColor("#1B1E26"));

        mZeroPaint = new Paint();
        mZeroPaint.setStyle(Paint.Style.STROKE);
        mZeroPaint.setStrokeWidth(1.0f);
        mZeroPaint.setColor(Color.parseColor("#2E333E"));

        mIdleLinePaint = new Paint();
        mIdleLinePaint.setStyle(Paint.Style.STROKE);
        mIdleLinePaint.setStrokeWidth(1.5f * mDensity);
        mIdleLinePaint.setColor(Color.parseColor("#4D3E20"));

        mWaveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWaveFillPaint.setStyle(Paint.Style.FILL);
        mWaveFillPaint.setColor(Color.parseColor("#24E5A93C"));

        mWavePaintR = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaintR.setStyle(Paint.Style.STROKE);
        mWavePaintR.setStrokeWidth(1.4f * mDensity);
        mWavePaintR.setStrokeJoin(Paint.Join.ROUND);
        mWavePaintR.setStrokeCap(Paint.Cap.ROUND);
        mWavePaintR.setColor(Color.parseColor("#38BDF8"));

        mWavePaintL = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaintL.setStyle(Paint.Style.STROKE);
        mWavePaintL.setStrokeWidth(1.85f * mDensity);
        mWavePaintL.setStrokeJoin(Paint.Join.ROUND);
        mWavePaintL.setStrokeCap(Paint.Cap.ROUND);
        mWavePaintL.setColor(Color.parseColor("#E5A93C"));

        mBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBadgePaint.setTextSize(10.0f * mDensity);
        mBadgePaint.setTypeface(Typeface.MONOSPACE);
        mBadgePaint.setColor(Color.parseColor("#9A9FA8"));

        updateBadgeIfNeeded(true, 0.0f, 0.0f);
    }

    public void bindDspCore(AegisDspCore core) {
        mDspCore = core;
        if (shouldAnimate()) {
            scheduleNextFrame();
        } else {
            invalidate();
        }
    }

    public void setActive(boolean active) {
        if (mIsActive != active) {
            mIsActive = active;
            if (!active) {
                stopLoop();
                invalidate();
            } else {
                scheduleNextFrame();
                invalidate();
            }
        }
    }

    public void pokeIfActive() {
        if (shouldAnimate() && !mLoopScheduled) {
            scheduleNextFrame();
            invalidate();
        }
    }

    public void setFundamentalMode(boolean fundamental) {
        if (mFundamentalMode != fundamental) {
            mFundamentalMode = fundamental;
            mLastBadgeKey = Integer.MIN_VALUE;
            invalidate();
        }
    }

    public boolean isFundamentalMode() {
        return mFundamentalMode;
    }

    private boolean isVisibleOnScreen() {
        return mAttached
                && getVisibility() == View.VISIBLE
                && getWindowVisibility() == View.VISIBLE
                && getWidth() > 0
                && getHeight() > 0
                && getGlobalVisibleRect(mVisibleRect);
    }

    private boolean shouldAnimate() {
        return mIsActive && mDspCore != null && mDspCore.isActive() && isVisibleOnScreen();
    }

    private void scheduleNextFrame() {
        if (!mLoopScheduled && shouldAnimate()) {
            mLoopScheduled = true;
            postDelayed(mFrameLoop, 33L);
        }
    }

    private void stopLoop() {
        removeCallbacks(mFrameLoop);
        mLoopScheduled = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        stopLoop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            stopLoop();
        } else if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE) {
            stopLoop();
        } else if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }

    private int appendTenths(int tenths, int pos) {
        int whole = tenths / 10;
        int frac = tenths % 10;
        int dCount = 0;
        if (whole == 0) {
            mDigitTmp[dCount++] = '0';
        } else {
            while (whole > 0 && dCount < mDigitTmp.length) {
                mDigitTmp[dCount++] = (char) ('0' + (whole % 10));
                whole /= 10;
            }
        }
        for (int i = dCount - 1; i >= 0 && pos < mBadgeChars.length - 10; i--) {
            mBadgeChars[pos++] = mDigitTmp[i];
        }
        if (pos < mBadgeChars.length - 8) {
            mBadgeChars[pos++] = '.';
            mBadgeChars[pos++] = (char) ('0' + frac);
        }
        return pos;
    }

    private void updateBadgeIfNeeded(boolean lowRate, float liveHz, float cycles) {
        int tenths = (liveHz > 0.0f) ? Math.round(liveHz * 10.0f) : 0;
        int cycTenths = (cycles > 0.0f) ? Math.round(cycles * 10.0f) : 0;
        int key = (tenths << 10) | ((cycTenths & 0xFF) << 2) | (lowRate ? 2 : 0) | (mFundamentalMode ? 1 : 0);
        if (key == mLastBadgeKey) {
            return;
        }
        mLastBadgeKey = key;

        char[] prefix = lowRate
                ? (mFundamentalMode ? PREFIX_LOW_FUND : PREFIX_LOW_OUT)
                : (mFundamentalMode ? PREFIX_FAST_FUND : PREFIX_FAST_OUT);
        int pos = prefix.length;
        System.arraycopy(prefix, 0, mBadgeChars, 0, pos);

        if (tenths <= 0) {
            System.arraycopy(SUFFIX_NOISE, 0, mBadgeChars, pos, SUFFIX_NOISE.length);
            mBadgeLen = pos + SUFFIX_NOISE.length;
            return;
        }

        pos = appendTenths(tenths, pos);
        if (pos + SUFFIX_HZ_SEP.length < mBadgeChars.length - 8) {
            System.arraycopy(SUFFIX_HZ_SEP, 0, mBadgeChars, pos, SUFFIX_HZ_SEP.length);
            pos += SUFFIX_HZ_SEP.length;
            pos = appendTenths(cycTenths, pos);
            if (pos + SUFFIX_CYCLE.length <= mBadgeChars.length) {
                System.arraycopy(SUFFIX_CYCLE, 0, mBadgeChars, pos, SUFFIX_CYCLE.length);
                pos += SUFFIX_CYCLE.length;
            }
        } else {
            mBadgeChars[pos++] = 'H';
            mBadgeChars[pos++] = 'z';
        }
        mBadgeLen = pos;
    }

    private float findRisingTrigger(float[] buf, int totalLen, int winSamples) {
        int maxTrig = totalLen - winSamples - 2;
        if (maxTrig <= 2) return 0.0f;

        float peak = 0.0f;
        for (int i = 0; i < totalLen; i += 4) {
            float a = Math.abs(buf[i]);
            if (a > peak) peak = a;
        }
        float armThresh = -Math.max(0.001f, peak * 0.15f);
        int searchFrom = Math.max(1, maxTrig - Math.max(winSamples, 256));
        boolean armed = false;
        for (int i = searchFrom; i <= maxTrig; i++) {
            float prev = buf[i - 1];
            float cur = buf[i];
            if (!armed) {
                if (cur <= armThresh) {
                    armed = true;
                }
            } else if (prev <= 0.0f && cur > 0.0f) {
                float denom = cur - prev;
                return (denom > 1e-6f) ? ((i - 1) + (-prev / denom)) : (float) i;
            }
        }
        // Fallback: full forward scan from index 1 if latest window had no complete negative arm
        armed = false;
        for (int i = 1; i <= maxTrig; i++) {
            float prev = buf[i - 1];
            float cur = buf[i];
            if (!armed) {
                if (cur <= armThresh) {
                    armed = true;
                }
            } else if (prev <= 0.0f && cur > 0.0f) {
                float denom = cur - prev;
                return (denom > 1e-6f) ? ((i - 1) + (-prev / denom)) : (float) i;
            }
        }
        return 0.0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 4.0f || h <= 4.0f) return;

        float rad = 10.0f * mDensity;
        float pad = 1.0f * mDensity;
        canvas.drawRoundRect(pad, pad, w - pad, h - pad, rad, rad, mBgPaint);

        for (int i = 1; i < 4; i++) {
            float gy = (h * i) / 4.0f;
            canvas.drawLine(pad, gy, w - pad, gy, i == 2 ? mZeroPaint : mGridPaint);
        }
        for (int i = 1; i < 8; i++) {
            float gx = (w * i) / 8.0f;
            canvas.drawLine(gx, pad, gx, h - pad, mGridPaint);
        }

        float midY = h * 0.5f;
        if (!mIsActive || mDspCore == null || !mDspCore.isActive()) {
            mLastDrawNs = 0L;
            canvas.drawLine(8.0f * mDensity, midY, w - 8.0f * mDensity, midY, mIdleLinePaint);
            canvas.drawText(IDLE_BADGE, 0, IDLE_BADGE.length, 10.0f * mDensity, 16.0f * mDensity, mBadgePaint);
            canvas.drawRoundRect(pad, pad, w - pad, h - pad, rad, rad, mBorderPaint);
            return;
        }

        long nowNs = System.nanoTime();
        float dtSec = (mLastDrawNs == 0L) ? 0.0f : ((nowNs - mLastDrawNs) * 1e-9f);
        mLastDrawNs = nowNs;
        if (dtSec < 0.0f) dtSec = 0.0f;
        if (dtSec > 0.1f) dtSec = 0.1f;

        float liveHz = mDspCore.getLiveFreqHz();
        float liveRHz = mDspCore.getLiveFreqRHz();
        boolean lowRate = (liveHz > 0.0f && liveHz < 55.0f);
        double sr = mDspCore.copyScopeSnapshot(mFundamentalMode, lowRate, mSnapL, mSnapR);
        int totalLen = lowRate ? AegisDspCore.SCOPE_LOW_RING_SIZE : AegisDspCore.SCOPE_RING_SIZE;

        int winSamples;
        if (liveHz > 0.4f && liveHz < 55.0f) {
            winSamples = (int) Math.round((3.0 * sr) / liveHz);
        } else if (liveHz >= 55.0f) {
            winSamples = (int) Math.round((3.5 * sr) / liveHz);
        } else {
            winSamples = 1200;
        }
        if (winSamples < 24) winSamples = 24;
        if (winSamples > totalLen - 64) winSamples = totalLen - 64;

        float beatDiff = Math.abs(liveRHz - liveHz);
        float trigStartL = 0.0f;
        float trigStartR = 0.0f;
        if (liveHz > 0.0f) {
            float visCycPerSec = (float) Math.min(1.35, Math.max(0.30, 0.25 + 0.35 * Math.log10(1.0 + liveHz)));
            mTravelPhaseL = (mTravelPhaseL + dtSec * visCycPerSec) % 1.0f;

            float spcL = (float) (sr / liveHz);
            int cyclePadL = (int) Math.ceil(spcL) + 2;
            if (winSamples + cyclePadL < totalLen - 8) {
                trigStartL = findRisingTrigger(mSnapL, totalLen, winSamples + cyclePadL) + (mTravelPhaseL * spcL);
            } else {
                trigStartL = findRisingTrigger(mSnapL, totalLen, winSamples);
            }

            if (beatDiff > 4.0f && liveRHz > 0.0f) {
                float ratio = Math.min(2.5f, Math.max(0.4f, liveRHz / liveHz));
                mTravelPhaseR = (mTravelPhaseR + dtSec * visCycPerSec * ratio) % 1.0f;
                float spcR = (float) (sr / liveRHz);
                int cyclePadR = (int) Math.ceil(spcR) + 2;
                if (winSamples + cyclePadR < totalLen - 8) {
                    trigStartR = findRisingTrigger(mSnapR, totalLen, winSamples + cyclePadR) + (mTravelPhaseR * spcR);
                } else {
                    trigStartR = findRisingTrigger(mSnapR, totalLen, winSamples);
                }
            } else {
                trigStartR = trigStartL;
            }
        }

        float spanF = (float) (winSamples - 1);
        float maxDiff = 0.0f;
        for (int k = 0; k < DRAW_POINTS; k += 2) {
            int idxL = (int) (trigStartL + (k * spanF) / (DRAW_POINTS - 1));
            int idxR = (int) (trigStartR + (k * spanF) / (DRAW_POINTS - 1));
            if (idxL >= totalLen) idxL = totalLen - 1;
            if (idxR >= totalLen) idxR = totalLen - 1;
            float d = Math.abs(mSnapL[idxL] - mSnapR[idxR]);
            if (d > maxDiff) maxDiff = d;
        }
        boolean showStereo = (beatDiff > 0.05f) || (maxDiff > 0.025f);

        float leftX = 6.0f * mDensity;
        float drawW = w - 12.0f * mDensity;
        float ampY = (h * 0.5f) - (16.0f * mDensity);

        mPathL.reset();
        mFillPathL.reset();
        mFillPathL.moveTo(leftX, midY);
        if (showStereo) mPathR.reset();

        int maxIdx = totalLen - 2;
        for (int k = 0; k < DRAW_POINTS; k++) {
            float posL = trigStartL + (k * spanF) / (DRAW_POINTS - 1);
            int iL0 = (int) posL;
            if (iL0 < 0) iL0 = 0;
            if (iL0 > maxIdx) iL0 = maxIdx;
            float fL = posL - iL0;
            float vL = mSnapL[iL0] + (mSnapL[iL0 + 1] - mSnapL[iL0]) * fL;
            if (vL > 1.0f) vL = 1.0f; else if (vL < -1.0f) vL = -1.0f;

            float x = leftX + (drawW * k) / (DRAW_POINTS - 1);
            float yL = midY - (vL * ampY);
            if (k == 0) {
                mPathL.moveTo(x, yL);
            } else {
                mPathL.lineTo(x, yL);
            }
            mFillPathL.lineTo(x, yL);

            if (showStereo) {
                float posR = trigStartR + (k * spanF) / (DRAW_POINTS - 1);
                int iR0 = (int) posR;
                if (iR0 < 0) iR0 = 0;
                if (iR0 > maxIdx) iR0 = maxIdx;
                float fR = posR - iR0;
                float vR = mSnapR[iR0] + (mSnapR[iR0 + 1] - mSnapR[iR0]) * fR;
                if (vR > 1.0f) vR = 1.0f; else if (vR < -1.0f) vR = -1.0f;
                float yR = midY - (vR * ampY);
                if (k == 0) {
                    mPathR.moveTo(x, yR);
                } else {
                    mPathR.lineTo(x, yR);
                }
            }
        }
        mFillPathL.lineTo(leftX + drawW, midY);
        mFillPathL.close();

        canvas.drawPath(mFillPathL, mWaveFillPaint);
        if (showStereo) {
            canvas.drawPath(mPathR, mWavePaintR);
        }
        canvas.drawPath(mPathL, mWavePaintL);

        float dispCycles = (liveHz > 0.0f && sr > 0.0) ? (float) ((winSamples * liveHz) / sr) : 0.0f;
        updateBadgeIfNeeded(lowRate, liveHz, dispCycles);
        canvas.drawText(mBadgeChars, 0, mBadgeLen, 10.0f * mDensity, 15.0f * mDensity, mBadgePaint);
        canvas.drawRoundRect(pad, pad, w - pad, h - pad, rad, rad, mBorderPaint);

        if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }
}
