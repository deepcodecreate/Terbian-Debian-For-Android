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

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

public final class ThemeApplier {
    private ThemeApplier() {
    }

    public static void styleWindow(Activity activity, ThemePalette palette) {
        try {
            activity.getWindow().setStatusBarColor(palette.background);
            activity.getWindow().setNavigationBarColor(palette.surface);
        } catch (Exception ignored) {
        }
    }

    public static void styleTabLayout(TabLayout tabLayout, ThemePalette palette) {
        tabLayout.setBackgroundColor(palette.surface);
        tabLayout.setSelectedTabIndicatorColor(palette.primary);
        tabLayout.setTabTextColors(palette.subtle, palette.foreground);
    }

    public static void styleSlider(Slider slider, ThemePalette palette) {
        slider.setTrackActiveTintList(ColorStateList.valueOf(palette.primary));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(palette.border));
        slider.setThumbTintList(ColorStateList.valueOf(palette.primary));
        slider.setHaloTintList(ColorStateList.valueOf(ThemePalette.withAlpha(palette.primary, 60)));
    }

    public static void styleSwitch(MaterialSwitch toggle, ThemePalette palette) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{palette.primary, palette.subtle};
        int[] trackColors = new int[]{
                ThemePalette.withAlpha(palette.primary, 130),
                ThemePalette.withAlpha(palette.foreground, 60)
        };
        toggle.setThumbTintList(new ColorStateList(states, thumbColors));
        toggle.setTrackTintList(new ColorStateList(states, trackColors));
    }

    public static void tintIcon(ImageView icon, int color) {
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
    }

    public static void styleRow(View row, ThemePalette palette) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(palette.surface);
        drawable.setCornerRadius(dp(row, 14));
        drawable.setStroke((int) dp(row, 1), palette.border);
        row.setBackground(drawable);
    }

    public static void styleChip(View chip, ThemePalette palette) {
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(palette.primaryContainer);
        selected.setCornerRadius(dp(chip, 10));
        selected.setStroke((int) dp(chip, 2), palette.primary);

        GradientDrawable unselected = new GradientDrawable();
        unselected.setColor(palette.surfaceHigh);
        unselected.setCornerRadius(dp(chip, 10));
        unselected.setStroke((int) dp(chip, 1), palette.border);

        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_selected}, selected);
        drawable.addState(new int[]{}, unselected);
        chip.setBackground(drawable);
    }

    public static void styleThemeCard(View card, ThemePalette palette, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(card, 14));
        drawable.setStroke((int) dp(card, selected ? 2 : 1), selected ? palette.primary : palette.border);
        card.setBackground(drawable);
    }

    public static void setText(TextView view, String text, int color) {
        if (view == null) return;
        if (text != null) view.setText(text);
        view.setTextColor(color);
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
