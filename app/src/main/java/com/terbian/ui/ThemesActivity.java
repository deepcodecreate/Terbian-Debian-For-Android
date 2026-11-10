/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.slider.Slider;
import com.terbian.R;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.PromptStyle;
import com.terbian.terminal.view.TerminalTheme;
import com.terbian.terminal.view.Watermark;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ThemesActivity extends BaseActivity {
    private LinearLayout root;
    private ThemePalette p;
    private int savedScroll;
    private String justSelected;

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
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TerminalTheme current = AppSettings.currentTheme(this);
        Watermark.Config wm = Watermark.load(this);

        TerminalPreviewView big = new TerminalPreviewView(this);
        big.setMode(TerminalPreviewView.MODE_FULL);
        big.setCornerDp(Shapes.EXTRA_LARGE);
        big.setTheme(current);
        big.setWatermark(wm);
        big.setPromptStyle(AppSettings.promptStyle(this));
        column.addView(big, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176)));

        column.addView(section(R.string.th_section_themes));
        List<View> cards = new ArrayList<>();
        for (TerminalTheme theme : TerminalTheme.all()) {
            cards.add(themeCard(theme, theme.id.equals(current.id), wm));
        }
        addGrid(column, cards, 2, 10);

        column.addView(section(R.string.th_section_prompt));
        column.addView(hint(getString(R.string.th_prompt_help)));
        PromptStyle chosen = AppSettings.promptStyle(this);
        LinearLayout promptHost = new LinearLayout(this);
        promptHost.setOrientation(LinearLayout.VERTICAL);
        promptHost.setPadding(0, dp(10), 0, 0);
        for (PromptStyle ps : PromptStyle.values()) {
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (promptHost.getChildCount() > 0) pp.topMargin = dp(10);
            promptHost.addView(promptCard(ps, ps == chosen, current, wm), pp);
        }
        column.addView(promptHost);

        column.addView(section(R.string.th_section_bg));
        column.addView(hint(getString(R.string.th_bg_help)));
        List<View> tiles = new ArrayList<>();
        for (Watermark.Emblem e : Watermark.Emblem.values()) {
            tiles.add(emblemTile(e, e == wm.emblem, current, wm));
        }
        LinearLayout gridHost = new LinearLayout(this);
        gridHost.setOrientation(LinearLayout.VERTICAL);
        gridHost.setPadding(0, dp(8), 0, 0);
        addGrid(gridHost, tiles, 3, 8);
        column.addView(gridHost);

        if (wm.emblem != Watermark.Emblem.NONE) {
            column.addView(label(R.string.th_style));
            column.addView(chips(new int[]{R.string.th_style_faint, R.string.th_style_emboss, R.string.th_style_glow},
                    wm.style.ordinal(), i -> {
                        AppSettings.setWmStyle(this, Watermark.Style.values()[i]);
                        render();
                    }));
            column.addView(label(R.string.th_position));
            column.addView(chips(new int[]{R.string.th_pos_center, R.string.th_pos_corner},
                    wm.position.ordinal(), i -> {
                        AppSettings.setWmPosition(this, Watermark.Position.values()[i]);
                        render();
                    }));
            column.addView(sliderRow(R.string.th_strength, 5, 60, wm.strength, v -> {
                AppSettings.setWmStrength(this, v);
                render();
            }));
            column.addView(sliderRow(R.string.th_size, 30, 100, wm.size, v -> {
                AppSettings.setWmSize(this, v);
                render();
            }));
        }

        final int restore = savedScroll;
        scroll.post(() -> scroll.scrollTo(0, restore));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> savedScroll = y);
        Motion.attachPressFeedback(root);
        justSelected = null;
    }

    private View themeCard(TerminalTheme theme, boolean selected, Watermark.Config wm) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(10));
        card.setBackground(Shapes.rect(this, selected ? p.primaryContainer : p.surfaceHigh,
                Shapes.LARGE, p.primary, selected ? 2 : 0));

        TerminalPreviewView mini = new TerminalPreviewView(this);
        mini.setMode(TerminalPreviewView.MODE_COMPACT);
        mini.setCornerDp(Shapes.MEDIUM);
        mini.setTheme(theme);
        mini.setWatermark(wm);
        mini.setMinStrength(0);
        card.addView(mini, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

        TextView name = text(theme.name, 13, selected ? p.onPrimaryContainer : p.foreground, true);
        name.setSingleLine(true);
        name.setPadding(dp(8), dp(8), dp(8), 0);
        card.addView(name);
        frame.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (selected) {
            ExpressiveShapeView badge = new ExpressiveShapeView(this);
            badge.setFillColor(p.primary);
            boolean fresh = theme.id.equals(justSelected);
            badge.setShape(fresh ? 0 : 2);
            if (fresh) badge.post(() -> badge.morphTo(2));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(26), dp(26),
                    Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(12), dp(12), 0);
            frame.addView(badge, lp);
            TextView check = text("✓", 13, p.onPrimary, true);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(26), dp(26),
                    Gravity.TOP | Gravity.END);
            cp.setMargins(0, dp(12), dp(12), 0);
            frame.addView(check, cp);
        }

        frame.setClickable(true);
        frame.setOnClickListener(v -> {
            Motion.haptic(v);
            AppSettings.prefs(this).edit().putString(AppSettings.KEY_THEME, theme.id).apply();
            justSelected = theme.id;
            render();
        });
        frame.setContentDescription(theme.name);
        return frame;
    }

    private View promptCard(PromptStyle style, boolean selected, TerminalTheme theme, Watermark.Config wm) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(10));
        card.setBackground(Shapes.rect(this, selected ? p.primaryContainer : p.surfaceHigh,
                Shapes.LARGE, p.primary, selected ? 2 : 0));

        TerminalPreviewView pic = new TerminalPreviewView(this);
        pic.setMode(TerminalPreviewView.MODE_PROMPT);
        pic.setCornerDp(Shapes.MEDIUM);
        pic.setTheme(theme);
        pic.setWatermark(wm);
        pic.setMinStrength(0);
        pic.setPromptStyle(style);
        card.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

        TextView name = text(style.label, 13, selected ? p.onPrimaryContainer : p.foreground, true);
        name.setSingleLine(true);
        name.setPadding(dp(8), dp(8), dp(8), 0);
        card.addView(name);
        frame.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (selected) {
            ExpressiveShapeView badge = new ExpressiveShapeView(this);
            badge.setFillColor(p.primary);
            badge.setShape(2);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(26), dp(26),
                    Gravity.BOTTOM | Gravity.END);
            lp.setMargins(0, 0, dp(12), dp(8));
            frame.addView(badge, lp);
            TextView check = text("\u2713", 13, p.onPrimary, true);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(26), dp(26),
                    Gravity.BOTTOM | Gravity.END);
            cp.setMargins(0, 0, dp(12), dp(8));
            frame.addView(check, cp);
        }

        frame.setClickable(true);
        frame.setOnClickListener(v -> {
            Motion.haptic(v);
            AppSettings.setPromptStyle(this, style);
            render();
        });
        frame.setContentDescription(style.label);
        return frame;
    }

    private View emblemTile(Watermark.Emblem emblem, boolean selected, TerminalTheme theme,
                            Watermark.Config wm) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(5), dp(5), dp(5), dp(8));
        tile.setBackground(Shapes.rect(this, selected ? p.primaryContainer : p.surfaceHigh,
                Shapes.LARGE, p.primary, selected ? 2 : 0));

        TerminalPreviewView pic = new TerminalPreviewView(this);
        pic.setMode(TerminalPreviewView.MODE_EMBLEM);
        pic.setCornerDp(Shapes.MEDIUM);
        pic.setTheme(theme);
        Watermark.Config c = wm.copy();
        c.emblem = emblem;
        c.position = Watermark.Position.CENTER;
        c.size = 70;
        pic.setWatermark(c);
        pic.setMinStrength(30);
        tile.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        TextView name = text(getString(emblem.label), 12, selected ? p.onPrimaryContainer : p.foreground, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(0, dp(7), 0, 0);
        tile.addView(name);

        tile.setClickable(true);
        tile.setOnClickListener(v -> {
            Motion.haptic(v);
            AppSettings.setWmEmblem(this, emblem);
            render();
        });
        return tile;
    }

    private void addGrid(LinearLayout parent, List<View> items, int cols, int gapDp) {
        for (int i = 0; i < items.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                View v = i + c < items.size() ? items.get(i + c) : new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(c == 0 ? 0 : dp(gapDp / 2f), 0, c == cols - 1 ? 0 : dp(gapDp / 2f), 0);
                row.addView(v, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = i == 0 ? 0 : dp(gapDp);
            parent.addView(row, rp);
        }
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
        titles.addView(text(getString(R.string.themes_title), 20, p.foreground, true));
        titles.addView(text(getString(R.string.themes_sub), 12, p.subtle, false));
        bar.addView(titles);
        return bar;
    }

    private TextView section(int res) {
        TextView t = text(getString(res), 12, p.primary, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(4), dp(26), 0, dp(10));
        return t;
    }

    private TextView label(int res) {
        TextView t = text(getString(res), 13, p.muted, false);
        t.setPadding(dp(4), dp(16), 0, dp(6));
        return t;
    }

    private TextView hint(String s) {
        return text(s, 12, p.subtle, false);
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
        TextView number = text(value + "%", 13, p.primary, true);
        top.addView(text(getString(label), 15, p.foreground, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(number);
        box.addView(top);

        Slider slider = new Slider(this);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(1f);
        slider.setValue(Math.max(min, Math.min(max, value)));
        ThemeApplier.styleSlider(slider, p);
        slider.addOnChangeListener((s, v, fromUser) -> number.setText(Math.round(v) + "%"));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {
            }

            @Override
            public void onStopTrackingTouch(Slider s) {
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
