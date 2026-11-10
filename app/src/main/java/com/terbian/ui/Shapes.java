/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public final class Shapes {
    public static final int EXTRA_SMALL = 8;
    public static final int SMALL = 12;
    public static final int MEDIUM = 16;
    public static final int LARGE = 24;
    public static final int EXTRA_LARGE = 32;
    public static final int EXTRA_LARGE_INCREASED = 36;
    public static final int EXTRA_EXTRA_LARGE = 48;

    private Shapes() {
    }

    public static float radius(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.max(2f, dp * Appearance.cornerScale(context)) * density;
    }

    public static float radius(View view, float dp) {
        return radius(view.getContext(), dp);
    }

    public static GradientDrawable rect(Context context, int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radius(context, radiusDp));
        if (strokeDp > 0) {
            d.setStroke(Math.max(1, Math.round(strokeDp * context.getResources().getDisplayMetrics().density)), stroke);
        }
        return d;
    }
}
