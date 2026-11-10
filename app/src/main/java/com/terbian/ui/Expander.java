/*
 * Copyright 2026 deepcodecreate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terbian.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

final class Expander {
    private Expander() {
    }

    static void expand(final View view) {
        Object running = view.getTag(com.terbian.R.id.expander_anim);
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }
        view.measure(
                View.MeasureSpec.makeMeasureSpec(
                        ((View) view.getParent()).getWidth() > 0
                                ? ((View) view.getParent()).getWidth() : ViewGroup.LayoutParams.MATCH_PARENT,
                        ((View) view.getParent()).getWidth() > 0
                                ? View.MeasureSpec.AT_MOST : View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = view.getMeasuredHeight();
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = 0;
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.requestLayout();

        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(Motion.spatialMs(view.getContext()));
        anim.setInterpolator(Motion.spatial(view.getContext()));
        anim.addUpdateListener(a -> {
            lp.height = Math.max(0, (int) a.getAnimatedValue());
            view.setAlpha(Math.min(1f, a.getAnimatedFraction() * 1.6f));
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTag(com.terbian.R.id.expander_anim, null);
                if (!cancelled) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setAlpha(1f);
                    view.requestLayout();
                }
            }
        });
        view.setTag(com.terbian.R.id.expander_anim, anim);
        anim.start();
    }

    static void collapse(final View view) {
        Object running = view.getTag(com.terbian.R.id.expander_anim);
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        final int start = view.getHeight();
        if (start <= 0) {
            view.setVisibility(View.GONE);
            return;
        }
        ValueAnimator anim = ValueAnimator.ofInt(start, 0);
        anim.setDuration(Motion.effectsMs(view.getContext()) + Motion.effectsMs(view.getContext()) / 2);
        anim.setInterpolator(Motion.exit(view.getContext()));
        anim.addUpdateListener(a -> {
            lp.height = Math.max(0, (int) a.getAnimatedValue());
            view.setAlpha(Math.max(0f, 1f - a.getAnimatedFraction()));
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTag(com.terbian.R.id.expander_anim, null);
                if (!cancelled) {
                    view.setVisibility(View.GONE);
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setAlpha(1f);
                }
            }
        });
        view.setTag(com.terbian.R.id.expander_anim, anim);
        anim.start();
    }

    static void toggle(View view, View arrow, boolean open) {
        if (open) {
            expand(view);
        } else {
            collapse(view);
        }
        if (arrow != null) {
            arrow.animate().rotation(open ? 180f : 0f)
                    .setDuration(Motion.spatialMs(arrow.getContext()))
                    .setInterpolator(Motion.spatial(arrow.getContext())).start();
            Motion.haptic(arrow);
        }
    }
}
