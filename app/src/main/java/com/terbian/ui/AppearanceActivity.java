/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.terbian.R;

import java.util.function.IntConsumer;

public class AppearanceActivity extends BaseActivity {
    private static final int[] SEEDS = {
            0xFFD70A53, 0xFF6750A4, 0xFF0B57D0, 0xFF00796B, 0xFF388E3C,
            0xFFF9A825, 0xFFE64A19, 0xFFC2185B, 0xFF5D4037, 0xFF607D8B};

    private LinearLayout root;
    private ThemePalette p;
    private int savedScroll;
    private int previewShape;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        setContentView(root);
        render();
    }

    private void render() {
        p = ThemePalette.of(this);
        ThemeApplier.styleWindow(this, p);
        ThemeApplier.backdrop(root, p);
        getWindow().getDecorView().setBackgroundColor(p.background);
        root.removeAllViews();

        root.addView(header());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(preview());

        column.addView(section(R.string.ap_theme));
        Appearance.ThemeMode mode = Appearance.themeMode(this);
        column.addView(chips(new int[]{R.string.ap_system, R.string.ap_light, R.string.ap_dark},
                mode.ordinal(), i -> {
                    Appearance.setThemeMode(this, Appearance.ThemeMode.values()[i]);
                    changed();
                }));

        column.addView(section(R.string.ap_colors));
        Appearance.ColorSource source = Appearance.colorSource(this);
        column.addView(chips(new int[]{R.string.ap_src_terminal, R.string.ap_src_wallpaper, R.string.ap_src_seed},
                source.ordinal(), i -> {
                    Appearance.setColorSource(this, Appearance.ColorSource.values()[i]);
                    changed();
                }));
        boolean wallpaperMissing = source == Appearance.ColorSource.WALLPAPER && Build.VERSION.SDK_INT < 31;
        if (wallpaperMissing) {
            column.addView(hint(getString(R.string.ap_wallpaper_unavailable)));
        }
        if (source == Appearance.ColorSource.SEED || wallpaperMissing) {
            column.addView(label(R.string.ap_seed));
            column.addView(swatches());
            column.addView(label(R.string.ap_style));
            column.addView(styleChips());
        }
        column.addView(label(R.string.ap_contrast));
        column.addView(chips(new int[]{R.string.ap_contrast_std, R.string.ap_contrast_med, R.string.ap_contrast_high},
                Appearance.contrast(this), i -> {
                    Appearance.setContrast(this, i);
                    changed();
                }));
        column.addView(switchRow(R.string.ap_pure_black, R.string.ap_pure_black_hint,
                Appearance.pureBlack(this), on -> {
                    Appearance.setPureBlack(this, on);
                    changed();
                }));

        column.addView(section(R.string.ap_shapes));
        column.addView(sliderRow(R.string.ap_corners, 50, 150, Math.round(Appearance.cornerScale(this) * 100),
                v -> {
                    Appearance.setCornerScale(this, v / 100f);
                    changed();
                }));
        column.addView(sliderRow(R.string.ap_text, 85, 130, Math.round(Appearance.textScale(this) * 100),
                v -> {
                    Appearance.setTextScale(this, v / 100f);
                    recreate();
                }));

        column.addView(section(R.string.ap_motion));
        column.addView(chips(new int[]{R.string.ap_motion_expressive, R.string.ap_motion_standard, R.string.ap_motion_reduced},
                Appearance.motion(this).ordinal(), i -> {
                    Appearance.setMotion(this, Appearance.MotionStyle.values()[i]);
                    changed();
                }));
        column.addView(switchRow(R.string.ap_haptics, 0, Appearance.haptics(this), on -> {
            Appearance.setHaptics(this, on);
            if (on) Motion.haptic(root);
        }));

        final int restore = savedScroll;
        scroll.post(() -> scroll.scrollTo(0, restore));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> savedScroll = y);
        Motion.attachPressFeedback(root);
    }

    private void changed() {
        render();
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(p.surface);
        bar.setPadding(dp(4), 0, dp(16), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackground(selectable());
        back.setContentDescription(getString(R.string.close));
        ThemeApplier.tintIcon(back, p.foreground);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(4), dp(8), 0, dp(8));
        TextView title = text(getString(R.string.appearance_title), 20, p.foreground, true);
        TextView sub = text(getString(R.string.appearance_subtitle), 12, p.subtle, false);
        titles.addView(title);
        titles.addView(sub);
        bar.addView(titles);
        return bar;
    }

    private View preview() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(Shapes.rect(this, p.surfaceHigh, Shapes.EXTRA_LARGE, p.border, 0));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(8);
        card.setLayoutParams(cardLp);

        ExpressiveShapeView shape = new ExpressiveShapeView(this);
        shape.setFillColor(p.primary);
        shape.setShape(previewShape);
        shape.setOnClickListener(v -> {
            previewShape = (previewShape + 1) % 6;
            ((ExpressiveShapeView) v).morphTo(previewShape);
            Motion.haptic(v);
        });
        card.addView(shape, new LinearLayout.LayoutParams(dp(84), dp(84)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(20), 0, 0, 0);
        info.addView(text("Terbian", 20, p.foreground, true));
        info.addView(text(getString(R.string.ap_preview), 13, p.muted, false));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);
        buttons.addView(pill("Filled", p.primary, p.onPrimary));
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
        buttons.addView(pill("Tonal", p.secondaryContainer, p.onSecondaryContainer));
        info.addView(buttons);
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private TextView pill(String label, int fill, int on) {
        TextView t = text(label, 13, on, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setMinHeight(dp(40));
        t.setBackground(Shapes.rect(this, fill, Shapes.EXTRA_LARGE_INCREASED, fill, 0));
        return t;
    }

    private TextView section(int res) {
        TextView t = text(getString(res), 12, p.primary, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(4), dp(24), 0, dp(8));
        return t;
    }

    private TextView label(int res) {
        TextView t = text(getString(res), 13, p.muted, false);
        t.setPadding(dp(4), dp(14), 0, dp(6));
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 12, p.subtle, false);
        t.setPadding(dp(4), dp(8), 0, 0);
        return t;
    }

    private View chips(int[] labels, int selected, IntConsumer onSelect) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(getString(labels[i]), 13, p.foreground, true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setClickable(true);
            chip.setFocusable(true);
            ThemeApplier.styleChip(chip, p);
            chip.setSelected(i == selected);
            chip.setOnClickListener(v -> {
                Motion.haptic(v);
                onSelect.accept(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(chip, lp);
        }
        return row;
    }

    private View styleChips() {
        int[] names = {R.string.ap_style_tonal, R.string.ap_style_vibrant, R.string.ap_style_expressive,
                R.string.ap_style_neutral, R.string.ap_style_mono};
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int selected = Appearance.paletteStyle(this).ordinal();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView chip = text(getString(names[i]), 13, p.foreground, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setClickable(true);
            ThemeApplier.styleChip(chip, p);
            chip.setSelected(i == selected);
            chip.setOnClickListener(v -> {
                Motion.haptic(v);
                Appearance.setPaletteStyle(this, Appearance.PaletteStyle.values()[index]);
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(chip, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    private View swatches() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(4), dp(2), dp(4));
        int current = Appearance.seed(this);
        for (int color : SEEDS) {
            View swatch = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            boolean on = color == current;
            d.setStroke(dp(on ? 3 : 1), on ? p.foreground : p.border);
            swatch.setBackground(d);
            swatch.setClickable(true);
            swatch.setOnClickListener(v -> {
                Motion.haptic(v);
                Appearance.setSeed(this, color);
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(swatch, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    private View switchRow(int label, int hintRes, boolean checked, java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(16), dp(10));
        row.setMinimumHeight(dp(64));
        ThemeApplier.styleRow(row, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(getString(label), 15, p.foreground, false));
        if (hintRes != 0) texts.addView(text(getString(hintRes), 12, p.subtle, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(checked);
        ThemeApplier.styleSwitch(toggle, p);
        toggle.setOnCheckedChangeListener((b, on) -> onChange.accept(on));
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        return row;
    }

    private View sliderRow(int label, int min, int max, int value, IntConsumer onCommit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        ThemeApplier.styleRow(box, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(getString(label), 15, p.foreground, false);
        TextView number = text(value + "%", 13, p.primary, true);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(number);
        box.addView(top);

        Slider slider = new Slider(this);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(5f);
        slider.setValue(Math.max(min, Math.min(max, Math.round(value / 5f) * 5)));
        ThemeApplier.styleSlider(slider, p);
        slider.addOnChangeListener((s, v, fromUser) -> number.setText(Math.round(v) + "%"));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {
            }

            @Override
            public void onStopTrackingTouch(Slider s) {
                Motion.haptic(s);
                onCommit.accept(Math.round(s.getValue()));
            }
        });
        box.addView(slider);
        return box;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private android.graphics.drawable.Drawable selectable() {
        TypedArray a = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
        android.graphics.drawable.Drawable d = a.getDrawable(0);
        a.recycle();
        return d;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
