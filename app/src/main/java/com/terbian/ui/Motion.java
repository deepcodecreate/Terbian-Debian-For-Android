/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.app.Activity;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.animation.TimeInterpolator;

import com.terbian.R;

public final class Motion {
    private Motion() {
    }

    public static boolean reduced(Context c) {
        return Appearance.motion(c) == Appearance.MotionStyle.REDUCED;
    }

    public static boolean expressive(Context c) {
        return Appearance.motion(c) == Appearance.MotionStyle.EXPRESSIVE;
    }

    public static long spatialMs(Context c) {
        switch (Appearance.motion(c)) {
            case REDUCED:
                return 0L;
            case STANDARD:
                return 300L;
            default:
                return 480L;
        }
    }

    public static long effectsMs(Context c) {
        switch (Appearance.motion(c)) {
            case REDUCED:
                return 0L;
            case STANDARD:
                return 150L;
            default:
                return 200L;
        }
    }

    public static TimeInterpolator spatial(Context c) {
        switch (Appearance.motion(c)) {
            case REDUCED:
                return new LinearInterpolator();
            case STANDARD:
                return new PathInterpolator(0.2f, 0f, 0f, 1f);
            default:
                return new SpringInterpolator(0.6f, 12f);
        }
    }

    public static TimeInterpolator effects(Context c) {
        return reduced(c) ? new LinearInterpolator() : new PathInterpolator(0.31f, 0.94f, 0.34f, 1f);
    }

    public static TimeInterpolator exit(Context c) {
        return reduced(c) ? new LinearInterpolator() : new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    }

    public static final class SpringInterpolator implements TimeInterpolator {
        private final float zeta;
        private final float omega;
        private final float omegaD;

        public SpringInterpolator(float dampingRatio, float omega) {
            this.zeta = dampingRatio;
            this.omega = omega;
            this.omegaD = (float) (omega * Math.sqrt(1 - dampingRatio * dampingRatio));
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            double decay = Math.exp(-zeta * omega * t);
            double wave = Math.cos(omegaD * t) + (zeta * omega / omegaD) * Math.sin(omegaD * t);
            return (float) (1.0 - decay * wave);
        }
    }

    public static void haptic(View view) {
        if (view == null || !Appearance.haptics(view.getContext())) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void pressable(View view) {
        view.setOnTouchListener((v, event) -> {
            Context c = v.getContext();
            if (reduced(c)) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float density = c.getResources().getDisplayMetrics().density;
                    float size = Math.max(1f, Math.max(v.getWidth(), v.getHeight()));
                    float shrink = Math.max(0.01f, Math.min(0.07f, 20f * density / size));
                    v.animate().cancel();
                    v.animate().scaleX(1f - shrink).scaleY(1f - shrink)
                            .setDuration(effectsMs(c) / 2).setInterpolator(effects(c)).start();
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(spatialMs(c)).setInterpolator(spatial(c)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    public static void attachPressFeedback(View root) {
        if (root == null) return;
        String cls = root.getClass().getName();
        if (cls.startsWith("com.termux") || cls.contains("TerminalView") || cls.contains("Vnc")
                || cls.contains("ExtraKeys")) return;
        if (root.isClickable() && root.hasOnClickListeners() && !(root instanceof android.widget.AbsListView)) {
            pressable(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                attachPressFeedback(g.getChildAt(i));
            }
        }
    }

    private static boolean rtl(Activity a) {
        return a.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @SuppressWarnings("deprecation")
    public static void opening(Activity a) {
        if (reduced(a)) {
            a.overridePendingTransition(0, 0);
        } else if (rtl(a)) {
            a.overridePendingTransition(R.anim.open_enter_rtl, R.anim.open_exit_rtl);
        } else {
            a.overridePendingTransition(R.anim.open_enter, R.anim.open_exit);
        }
    }

    @SuppressWarnings("deprecation")
    public static void closing(Activity a) {
        if (reduced(a)) {
            a.overridePendingTransition(0, 0);
        } else if (rtl(a)) {
            a.overridePendingTransition(R.anim.close_enter_rtl, R.anim.close_exit_rtl);
        } else {
            a.overridePendingTransition(R.anim.close_enter, R.anim.close_exit);
        }
    }
}
