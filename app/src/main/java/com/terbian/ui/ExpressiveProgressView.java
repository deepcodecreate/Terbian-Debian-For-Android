/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class ExpressiveProgressView extends View {
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private ValueAnimator animator;
    private float phase;
    private float progress = -1f;
    private float shown;

    public ExpressiveProgressView(Context context) {
        this(context, null);
    }

    public ExpressiveProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        active.setStyle(Paint.Style.STROKE);
        active.setStrokeCap(Paint.Cap.ROUND);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColors(int activeColor, int trackColor) {
        active.setColor(activeColor);
        track.setColor(trackColor);
        invalidate();
    }

    public void setProgress(float value) {
        this.progress = value;
        if (value < 0f) {
            invalidate();
            return;
        }
        float target = Math.max(0f, Math.min(1f, value));
        if (Motion.reduced(getContext())) {
            shown = target;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, target);
        a.setDuration(Motion.spatialMs(getContext()));
        a.setInterpolator(Motion.effects(getContext()));
        a.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Motion.reduced(getContext())) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1100L);
            animator.setInterpolator(new LinearInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float cy = getHeight() / 2f;
        float stroke = 4f * density;
        float amp = 3f * density;
        float length = 22f * density;
        active.setStrokeWidth(stroke);
        track.setStrokeWidth(stroke);
        if (w <= 0) return;

        float from;
        float to;
        if (progress >= 0f) {
            from = stroke / 2f;
            to = stroke / 2f + (w - stroke) * shown;
        } else {
            float head = (phase * 1.5f) * w;
            float span = w * 0.4f;
            from = Math.max(stroke / 2f, head - span);
            to = Math.min(w - stroke / 2f, head);
            if (to <= from) to = from + 0.1f;
        }

        if (to < w - stroke / 2f) {
            canvas.drawLine(Math.max(to + stroke, stroke / 2f), cy, w - stroke / 2f, cy, track);
        }
        if (progress < 0f && from > stroke) {
            canvas.drawLine(stroke / 2f, cy, from - stroke, cy, track);
        }

        path.reset();
        boolean calm = Motion.reduced(getContext());
        float shift = phase * length;
        for (float x = from; x <= to; x += 2f * density) {
            float y = cy + (calm ? 0f
                    : amp * (float) Math.sin((x - shift) / length * 2.0 * Math.PI));
            if (x == from) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, active);
    }
}
