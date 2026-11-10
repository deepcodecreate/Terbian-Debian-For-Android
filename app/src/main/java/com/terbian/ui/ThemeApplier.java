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
            activity.getWindow().setStatusBarColor(palette.surface);
            activity.getWindow().setNavigationBarColor(palette.surfaceLow);
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(
                            activity.getWindow(), activity.getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(!palette.dark);
            controller.setAppearanceLightNavigationBars(!palette.dark);
        } catch (Exception ignored) {
        }
    }

    public static void backdrop(View root, ThemePalette palette) {
        root.setBackground(new ToolbarBackdrop(root, palette.background, palette.surface));
    }

    public static void styleTabLayout(TabLayout tabLayout, ThemePalette palette) {
        tabLayout.setBackgroundColor(palette.surface);
        tabLayout.setSelectedTabIndicator(null);
        tabLayout.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        tabLayout.setTabTextColors(palette.subtle, palette.foreground);
    }

    public static void styleSessionTab(View tabRoot, ThemePalette palette) {
        View pill = tabRoot.findViewById(com.terbian.R.id.tabPill);
        TextView title = tabRoot.findViewById(com.terbian.R.id.tabTitle);
        ImageView close = tabRoot.findViewById(com.terbian.R.id.tabClose);
        com.terbian.ui.ExpressiveShapeView dot = tabRoot.findViewById(com.terbian.R.id.tabDot);
        float r = Shapes.radius(tabRoot, Shapes.EXTRA_LARGE_INCREASED);

        GradientDrawable on = new GradientDrawable();
        on.setColor(palette.primaryContainer);
        on.setCornerRadius(r);
        GradientDrawable off = new GradientDrawable();
        off.setColor(palette.surfaceHigh);
        off.setCornerRadius(r);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, on);
        states.addState(new int[]{}, off);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(r);
        pill.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(ThemePalette.withAlpha(palette.primary, 50)), states, mask));

        title.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{palette.onPrimaryContainer, palette.subtle}));
        tintIcon(close, palette.onPrimaryContainer);
        dot.setFillColor(tabRoot.isSelected() ? palette.primary : palette.border);
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
        int[] thumbColors = new int[]{palette.onPrimary, palette.subtle};
        int[] trackColors = new int[]{palette.primary, palette.surfaceHighest};
        toggle.setThumbTintList(new ColorStateList(states, thumbColors));
        toggle.setTrackTintList(new ColorStateList(states, trackColors));
    }

    public static void tintIcon(ImageView icon, int color) {
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
    }

    public static void styleRow(View row, ThemePalette palette) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(palette.surfaceHigh);
        fill.setCornerRadius(Shapes.radius(row, Shapes.MEDIUM));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(Shapes.radius(row, Shapes.MEDIUM));
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(ThemePalette.withAlpha(palette.primary, 50)), fill, mask));
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
        if (chip instanceof TextView) {
            ((TextView) chip).setTextColor(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                    new int[]{palette.onPrimaryContainer, palette.foreground}));
        }
    }

    public static void styleThemeCard(View card, ThemePalette palette, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? ThemePalette.withAlpha(palette.primary, 28) : Color.TRANSPARENT);
        drawable.setCornerRadius(Shapes.radius(card, Shapes.LARGE));
        drawable.setStroke((int) dp(card, selected ? 3 : 1), selected ? palette.primary : palette.border);
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
