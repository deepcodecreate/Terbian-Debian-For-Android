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

public final class ExpressiveShapeView extends View {
    private static final float[][] SHAPES = {
            {4, 0.10f},
            {6, 0.12f},
            {8, 0.10f},
            {4, 0.22f},
            {5, 0.16f},
            {12, 0.05f},
    };
    private static final int STEPS = 180;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int from;
    private int to;
    private float progress = 1f;
    private ValueAnimator animator;

    public ExpressiveShapeView(Context context) {
        this(context, null);
    }

    public ExpressiveShapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setFillColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public int shapeIndex() {
        return to;
    }

    public void setShape(int index) {
        cancel();
        from = to = wrap(index);
        progress = 1f;
        invalidate();
    }

    public void morphToNext() {
        morphTo(to + 1);
    }

    public void morphTo(int index) {
        int target = wrap(index);
        Context c = getContext();
        if (Motion.reduced(c)) {
            setShape(target);
            return;
        }
        cancel();

        from = to;
        to = target;
        final float startRotation = getRotation();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Motion.spatialMs(c) + 120L);
        animator.setInterpolator(Motion.spatial(c));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            setRotation(startRotation + 45f * progress);
            invalidate();
        });
        animator.start();
    }

    private static int wrap(int index) {
        int n = SHAPES.length;
        return ((index % n) + n) % n;
    }

    private void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }

    private static float radiusAt(float[] shape, double angle) {
        return 1f + shape[1] * (float) Math.cos(shape[0] * angle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float[] a = SHAPES[from];
        float[] b = SHAPES[to];

        float room = 1f + Math.max(a[1], b[1]) + 0.08f;
        float scale = size / 2f / room;
        path.reset();
        for (int i = 0; i <= STEPS; i++) {
            double angle = 2.0 * Math.PI * i / STEPS;
            float r = radiusAt(a, angle) + (radiusAt(b, angle) - radiusAt(a, angle)) * progress;
            float x = cx + (float) Math.cos(angle) * r * scale;
            float y = cy + (float) Math.sin(angle) * r * scale;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
    }
}
