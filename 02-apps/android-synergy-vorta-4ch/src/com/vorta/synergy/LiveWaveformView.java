package com.vorta.synergy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Real-time zero-allocation Canvas oscilloscope displaying the exact 32-bit float PCM samples
 * written to the Android AudioTrack HAL buffer, including thermal cooling & clipping states.
 */
public class LiveWaveformView extends View {
    private static final int DRAW_POINTS = 256;
    private static final int DISPLAY_WINDOW_SAMPLES = 3072; // 64 ms @ 48kHz

    private final float[] mSnapshot = new float[SynergyAudioService.SCOPE_RING_SIZE];
    private final Path mWavePath = new Path();

    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRailPaint = new Paint();
    private final Paint mCeilPaint = new Paint();
    private final Paint mTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDensity;

    public LiveWaveformView(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;

        mBgPaint.setColor(Color.parseColor("#0B1220"));
        mBgPaint.setStyle(Paint.Style.FILL);

        mBorderPaint.setColor(Color.parseColor("#1E293B"));
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(Math.max(1f, mDensity));

        mRailPaint.setColor(Color.parseColor("#1E293B"));
        mRailPaint.setStrokeWidth(Math.max(1f, mDensity * 0.8f));

        mCeilPaint.setColor(Color.parseColor("#334155"));
        mCeilPaint.setStrokeWidth(Math.max(1f, mDensity * 0.7f));

        mTracePaint.setStyle(Paint.Style.STROKE);
        mTracePaint.setStrokeWidth(Math.max(1.5f, mDensity * 1.6f));
        mTracePaint.setStrokeJoin(Paint.Join.ROUND);
        mTracePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 4f || h <= 4f) return;

        float r = 6f * mDensity;
        canvas.drawRoundRect(0f, 0f, w, h, r, r, mBgPaint);
        canvas.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, mBorderPaint);

        float padY = 4f * mDensity;
        float midY = h * 0.5f;
        float ampY = (h * 0.5f) - padY;
        float ceilTopY = midY - ampY * 0.89125f;
        float ceilBotY = midY + ampY * 0.89125f;

        canvas.drawLine(0f, ceilTopY, w, ceilTopY, mCeilPaint);
        canvas.drawLine(0f, ceilBotY, w, ceilBotY, mCeilPaint);
        canvas.drawLine(0f, midY, w, midY, mRailPaint);

        boolean active = SynergyAudioService.isAnySourceActive() && !SynergyAudioService.sAuxSuspended;
        if (!active || SynergyAudioService.sDutyCoolingNow) {
            mTracePaint.setColor(SynergyAudioService.sDutyCoolingNow
                    ? Color.parseColor("#B45309")
                    : Color.parseColor("#475569"));
            canvas.drawLine(0f, midY, w, midY, mTracePaint);
            return;
        }

        SynergyAudioService.copyScopeSnapshot(mSnapshot);
        int len = mSnapshot.length;
        int span = Math.min(DISPLAY_WINDOW_SAMPLES, len / 2);
        int maxTrig = len - span - 1;
        int start = 0;
        for (int i = 1; i < maxTrig; i++) {
            if (mSnapshot[i - 1] <= 0f && mSnapshot[i] > 0.005f) {
                start = i;
                break;
            }
        }

        if (SynergyAudioService.sLiveClipCount > 0) {
            mTracePaint.setColor(Color.parseColor("#EF4444"));
        } else if (SynergyAudioService.sLivePureMode) {
            mTracePaint.setColor(Color.parseColor("#10B981"));
        } else {
            mTracePaint.setColor(Color.parseColor("#F59E0B"));
        }

        mWavePath.reset();
        float xStep = w / (float) (DRAW_POINTS - 1);
        float sampleStep = (float) span / (float) (DRAW_POINTS - 1);
        for (int p = 0; p < DRAW_POINTS; p++) {
            int idx = start + Math.round(p * sampleStep);
            if (idx >= len) idx = len - 1;
            float s = mSnapshot[idx];
            if (Float.isNaN(s) || Float.isInfinite(s)) s = 0f;
            if (s > 1f) s = 1f;
            else if (s < -1f) s = -1f;
            float x = p * xStep;
            float y = midY - (s * ampY);
            if (p == 0) mWavePath.moveTo(x, y);
            else mWavePath.lineTo(x, y);
        }
        canvas.drawPath(mWavePath, mTracePaint);
    }
}
