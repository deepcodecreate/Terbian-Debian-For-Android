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

public final class ExpressiveLoadingView extends View {
    private static final float[][] SHAPES = {
            {8, 0.09f}, {5, 0.15f}, {4, 0.22f}, {6, 0.12f}, {3, 0.17f}, {12, 0.06f},
    };
    private static final long STEP_MS = 620L;
    private static final int POINTS = 120;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint container = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Motion.SpringInterpolator spring = new Motion.SpringInterpolator(0.62f, 13f);
    private ValueAnimator animator;
    private float loop;
    private boolean showContainer;

    public ExpressiveLoadingView(Context context) {
        this(context, null);
    }

    public ExpressiveLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        container.setStyle(Paint.Style.FILL);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setContainerColor(int color) {
        container.setColor(color);
        showContainer = true;
        invalidate();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateRunning();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }

    private void updateRunning() {
        if (isAttachedToWindow() && getVisibility() == VISIBLE && getWindowVisibility() == VISIBLE) {
            start();
        } else {
            stop();
        }
    }

    private void start() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(STEP_MS * SHAPES.length);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            loop = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static float radiusAt(float[] s, double angle) {
        return 1f + s[1] * (float) Math.cos(s[0] * angle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        boolean reduced = Motion.reduced(getContext());

        if (showContainer) {
            canvas.drawCircle(cx, cy, size / 2f, container);
        }

        float t = reduced ? 0f : loop * SHAPES.length;
        int i = Math.min(SHAPES.length - 1, (int) t);
        float local = t - i;
        float[] a = SHAPES[i];
        float[] b = SHAPES[(i + 1) % SHAPES.length];
        float k = reduced ? 0f : spring.getInterpolation(local);
        float turn = reduced ? 0f : (i + local) * 60f;

        int baseAlpha = paint.getAlpha();
        if (reduced) {
            double pulse = 0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 450.0);
            paint.setAlpha((int) (255 * pulse));
            postInvalidateOnAnimation();
        }

        float inner = showContainer ? size * 0.62f : size;
        float room = 1f + 0.26f + 0.06f;
        float scale = inner / 2f / room;
        path.reset();
        for (int n = 0; n <= POINTS; n++) {
            double ang = 2.0 * Math.PI * n / POINTS;
            float ra = radiusAt(a, ang);
            float r = ra + (radiusAt(b, ang) - ra) * k;
            float x = (float) Math.cos(ang) * r * scale;
            float y = (float) Math.sin(ang) * r * scale;
            if (n == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(turn);
        canvas.drawPath(path, paint);
        canvas.restore();
        if (reduced) paint.setAlpha(baseAlpha);
    }
}
