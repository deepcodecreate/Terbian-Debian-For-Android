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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

public final class ThemedMenu {
    private ThemedMenu() {
    }

    public static void show(View anchor, ThemePalette p, List<String> labels, int columns, IntConsumer onPick) {
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int cols = Math.max(1, columns);

        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(6 * density);
        menu.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.surfaceHigh);
        bg.setCornerRadius(Shapes.radius(context, Shapes.LARGE));
        menu.setBackground(bg);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(menu);

        PopupWindow[] holder = new PopupWindow[1];
        java.util.ArrayList<View> items = new java.util.ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < labels.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                menu.addView(row, new LinearLayout.LayoutParams(
                        cols == 1 ? Math.round(180 * density) : LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final int index = i;
            TextView item = new TextView(context);
            item.setText(labels.get(i));
            item.setTextSize(14);
            item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            item.setTextColor(p.foreground);
            item.setGravity(cols == 1 ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            item.setMinHeight(Math.round(44 * density));
            if (cols > 1) {
                item.setMinWidth(Math.round(62 * density));
            }
            int h = Math.round(14 * density);
            item.setPadding(h, 0, h, 0);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Shapes.radius(context, Shapes.MEDIUM));
            shape.setColor(0xFFFFFFFF);
            item.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(ThemePalette.withAlpha(p.primary, 60)), null, shape));
            item.setOnClickListener(v -> {
                Motion.haptic(v);
                holder[0].dismiss();
                onPick.accept(index);
            });
            items.add(item);
            row.addView(item, new LinearLayout.LayoutParams(
                    cols == 1 ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    cols == 1 ? 0f : 1f));
        }

        int maxHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.6f);
        PopupWindow popup = new PopupWindow(scroll,
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (scroll.getMeasuredHeight() > maxHeight) {
            popup.setHeight(maxHeight);
        }
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setElevation(8 * density);
        popup.setAnimationStyle(0);
        holder[0] = popup;
        popup.showAsDropDown(anchor, 0, Math.round(-4 * density));
        animateIn(scroll, items, anchor);
    }

    private static void animateIn(View menu, java.util.List<View> items, View anchor) {
        Context c = anchor.getContext();
        if (Motion.reduced(c)) return;
        boolean rtl = anchor.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        menu.setPivotX(rtl ? 0f : menu.getMeasuredWidth());
        menu.setPivotY(0f);
        menu.setScaleX(0.78f);
        menu.setScaleY(0.6f);
        menu.setAlpha(0f);
        menu.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(Motion.spatialMs(c)).setInterpolator(Motion.spatial(c)).start();
        if (!Motion.expressive(c)) return;
        float shift = 10f * c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < items.size(); i++) {
            View item = items.get(i);
            item.setAlpha(0f);
            item.setTranslationY(-shift);
            item.animate().alpha(1f).translationY(0f)
                    .setStartDelay(40L + i * 28L)
                    .setDuration(Motion.spatialMs(c) / 2)
                    .setInterpolator(Motion.spatial(c)).start();
        }
    }
}
