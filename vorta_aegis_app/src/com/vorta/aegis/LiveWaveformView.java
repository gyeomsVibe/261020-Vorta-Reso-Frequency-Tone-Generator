package com.vorta.aegis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;

import java.util.Locale;

/**
 * Zero-allocation 30fps live oscilloscope view for Vorta Aegis Android v3.2.0.
 * Features:
 * - Pre-allocated Paints, Paths, and 4096-sample L/R snapshot buffers (0 allocations inside onDraw)
 * - Auto dual-rate view: 1500 Hz decimated ring (<55 Hz, ~3.0 cycles) or 48 kHz ring (>=55 Hz)
 * - Dynamic hysteresis rising zero-crossing trigger (-15% arm threshold)
 * - Pre-limiter fundamental wave vs live output toggle, 0% CPU when idle or hidden
 */
public class LiveWaveformView extends View {
    private static final int DRAW_POINTS = 240;

    private final float[] mSnapL = new float[AegisDspCore.SCOPE_RING_SIZE];
    private final float[] mSnapR = new float[AegisDspCore.SCOPE_RING_SIZE];

    private final Paint mBgPaint;
    private final Paint mBorderPaint;
    private final Paint mGridPaint;
    private final Paint mZeroPaint;
    private final Paint mIdleLinePaint;
    private final Paint mWavePaintL;
    private final Paint mWavePaintR;
    private final Paint mBadgePaint;

    private final Path mPathL = new Path();
    private final Path mPathR = new Path();
    private final float mDensity;

    private AegisDspCore mDspCore;
    private boolean mIsActive = false;
    private boolean mFundamentalMode = true;
    private boolean mAttached = false;
    private boolean mLoopScheduled = false;

    private String mCachedBadge = "1500Hz · 기본 파동";
    private int mLastBadgeKey = Integer.MIN_VALUE;

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

    private boolean shouldAnimate() {
        return mIsActive && mAttached && getVisibility() == View.VISIBLE && mDspCore != null && mDspCore.isActive();
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
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE) {
            stopLoop();
        } else if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }

    private void updateBadgeIfNeeded(boolean lowRate, float liveHz) {
        int hzKey = Math.round(liveHz * 10.0f);
        int key = (hzKey << 2) | (lowRate ? 2 : 0) | (mFundamentalMode ? 1 : 0);
        if (key != mLastBadgeKey) {
            mLastBadgeKey = key;
            String rateLabel = lowRate ? "1500Hz 스코프" : "48kHz 스코프";
            String tapLabel = mFundamentalMode ? "기본 파동" : "실출력";
            if (liveHz > 0.0f) {
                mCachedBadge = String.format(Locale.US, "%s · %s · %.1fHz", rateLabel, tapLabel, liveHz);
            } else {
                mCachedBadge = rateLabel + " · " + tapLabel + " · 노이즈";
            }
        }
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

        // Background grid (4 horiz, 8 vert)
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
            canvas.drawLine(8.0f * mDensity, midY, w - 8.0f * mDensity, midY, mIdleLinePaint);
            canvas.drawText("대기 중 · 실시간 파형 스코프", 10.0f * mDensity, 16.0f * mDensity, mBadgePaint);
            canvas.drawRoundRect(pad, pad, w - pad, h - pad, rad, rad, mBorderPaint);
            return;
        }

        float liveHz = mDspCore.getLiveFreqHz();
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
        if (winSamples < 48) winSamples = 48;
        if (winSamples > totalLen - 64) winSamples = totalLen - 64;

        // Scan peak for dynamic hysteresis threshold
        int scanStart = Math.max(0, totalLen - winSamples * 2 - 32);
        float peak = 0.0f;
        for (int i = scanStart; i < totalLen; i++) {
            float a = Math.abs(mSnapL[i]);
            if (a > peak) peak = a;
        }
        float armThresh = Math.max(0.015f, peak * 0.15f);

        int maxStart = Math.max(0, totalLen - winSamples);
        int searchFrom = Math.max(1, maxStart - Math.min(winSamples, totalLen / 3));
        int trigStart = maxStart;
        boolean armed = false;
        for (int i = searchFrom; i <= maxStart; i++) {
            float s = mSnapL[i];
            if (!armed) {
                if (s <= -armThresh) {
                    armed = true;
                }
            } else if (mSnapL[i - 1] <= 0.0f && s > 0.0f) {
                trigStart = i;
                break;
            }
        }

        float maxDiff = 0.0f;
        for (int k = 0; k < DRAW_POINTS; k++) {
            int idx = trigStart + (k * (winSamples - 1)) / (DRAW_POINTS - 1);
            float d = Math.abs(mSnapL[idx] - mSnapR[idx]);
            if (d > maxDiff) maxDiff = d;
        }
        boolean showStereo = maxDiff > 0.025f;

        float leftX = 6.0f * mDensity;
        float drawW = w - 12.0f * mDensity;
        float ampY = (h * 0.5f) - (14.0f * mDensity);

        mPathL.reset();
        if (showStereo) mPathR.reset();

        for (int k = 0; k < DRAW_POINTS; k++) {
            int idx = trigStart + (k * (winSamples - 1)) / (DRAW_POINTS - 1);
            float x = leftX + (drawW * k) / (DRAW_POINTS - 1);
            float yL = midY - (mSnapL[idx] * ampY);
            if (k == 0) {
                mPathL.moveTo(x, yL);
            } else {
                mPathL.lineTo(x, yL);
            }
            if (showStereo) {
                float yR = midY - (mSnapR[idx] * ampY);
                if (k == 0) {
                    mPathR.moveTo(x, yR);
                } else {
                    mPathR.lineTo(x, yR);
                }
            }
        }

        if (showStereo) {
            canvas.drawPath(mPathR, mWavePaintR);
        }
        canvas.drawPath(mPathL, mWavePaintL);

        updateBadgeIfNeeded(lowRate, liveHz);
        canvas.drawText(mCachedBadge, 10.0f * mDensity, 15.0f * mDensity, mBadgePaint);
        canvas.drawRoundRect(pad, pad, w - pad, h - pad, rad, rad, mBorderPaint);

        if (shouldAnimate()) {
            scheduleNextFrame();
        }
    }
}
