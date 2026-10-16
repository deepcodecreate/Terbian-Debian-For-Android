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

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.terbian.R;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.view.CursorStyle;
import com.terbian.terminal.view.TerminalTheme;

public class SettingsActivity extends AppCompatActivity {
    private View settingsRoot;
    private LinearLayout settingsHeader;
    private TextView settingsTitle;
    private TextView settingsSubtitle;
    private ImageButton closeSettings;

    private TextView labelTerminalColor;
    private TextView helpTerminalColor;
    private LinearLayout themeGrid;

    private TextView labelFontSize;
    private Slider fontSeek;
    private TextView fontValue;

    private TextView labelCursor;
    private View cursorBlock;
    private View cursorBar;
    private View cursorUnderline;

    private LinearLayout rowBlink;
    private TextView labelBlink;
    private MaterialSwitch switchBlink;
    private TextView helpBlink;

    private TextView labelKeyboard;
    private LinearLayout rowKeys;
    private TextView labelKeys;
    private MaterialSwitch switchKeys;
    private LinearLayout rowBell;
    private TextView labelBell;
    private MaterialSwitch switchBell;

    private ThemePalette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_settings);

        bindViews();
        closeSettings.setOnClickListener(v -> finish());

        buildThemeGrid();
        setupFontSlider();
        setupCursorChips();
        setupSwitches();

        applyPalette(ThemePalette.from(AppSettings.currentTheme(this)));
    }

    private void bindViews() {
        settingsRoot = findViewById(R.id.settingsRoot);
        settingsHeader = findViewById(R.id.settingsHeader);
        settingsTitle = findViewById(R.id.settingsTitle);
        settingsSubtitle = findViewById(R.id.settingsSubtitle);
        closeSettings = findViewById(R.id.closeSettings);

        labelTerminalColor = findViewById(R.id.labelTerminalColor);
        helpTerminalColor = findViewById(R.id.helpTerminalColor);
        themeGrid = findViewById(R.id.themeGrid);

        labelFontSize = findViewById(R.id.labelFontSize);
        fontSeek = findViewById(R.id.fontSeek);
        fontValue = findViewById(R.id.fontValue);

        labelCursor = findViewById(R.id.labelCursor);
        cursorBlock = findViewById(R.id.cursorBlock);
        cursorBar = findViewById(R.id.cursorBar);
        cursorUnderline = findViewById(R.id.cursorUnderline);

        rowBlink = findViewById(R.id.rowBlink);
        labelBlink = findViewById(R.id.labelBlink);
        switchBlink = findViewById(R.id.switchBlink);
        helpBlink = findViewById(R.id.helpBlink);

        labelKeyboard = findViewById(R.id.labelKeyboard);
        rowKeys = findViewById(R.id.rowKeys);
        labelKeys = findViewById(R.id.labelKeys);
        switchKeys = findViewById(R.id.switchKeys);
        rowBell = findViewById(R.id.rowBell);
        labelBell = findViewById(R.id.labelBell);
        switchBell = findViewById(R.id.switchBell);
    }

    private void buildThemeGrid() {
        String current = AppSettings.themeId(this);
        for (TerminalTheme theme : TerminalTheme.all()) {
            View card = getLayoutInflater().inflate(R.layout.item_theme, themeGrid, false);
            View cardView = card.findViewById(R.id.themeCard);
            TextView prompt = card.findViewById(R.id.themePrompt);
            TextView name = card.findViewById(R.id.themeName);

            prompt.setText("root@terbian:~#");
            prompt.setTextColor(theme.foregroundColor);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(16f);
            bg.setColor(theme.backgroundColor);
            cardView.setBackground(bg);
            cardView.setSelected(theme.id.equals(current));
            restyleThemeCardBorder(cardView, theme.id.equals(current));

            name.setText(theme.name);
            name.setTextColor(theme.cursorColor);

            cardView.setOnClickListener(v -> {
                AppSettings.prefs(this).edit().putString(AppSettings.KEY_THEME, theme.id).apply();
                for (int i = 0; i < themeGrid.getChildCount(); i++) {
                    View child = themeGrid.getChildAt(i);
                    View cv = child.findViewById(R.id.themeCard);
                    if (cv != null) {
                        cv.setSelected(cv == cardView);
                        restyleThemeCardBorder(cv, cv == cardView);
                    }
                }
                applyPalette(ThemePalette.from(theme));
            });

            themeGrid.addView(card);
        }
    }

    private void restyleThemeCardBorder(View cardView, boolean selected) {
        GradientDrawable drawable = (GradientDrawable) cardView.getBackground();
        if (drawable == null) return;
        int borderColor = palette != null ? palette.border : getColor(R.color.border);
        int primaryColor = palette != null ? palette.primary : getColor(R.color.primary);
        drawable.setStroke(selected ? 2 : 1, selected ? primaryColor : borderColor);
    }

    private void setupFontSlider() {
        int sp = Math.round(AppSettings.fontSizeSp(this));
        sp = (int) Math.max(fontSeek.getValueFrom(), Math.min(fontSeek.getValueTo(), sp));
        fontSeek.setValue(sp);
        fontValue.setText(String.valueOf(sp));

        fontSeek.addOnChangeListener((slider, value, fromUser) -> {
            int size = Math.round(value);
            fontValue.setText(String.valueOf(size));
            if (fromUser) {
                AppSettings.prefs(this).edit()
                        .putFloat(AppSettings.KEY_FONT_SP, size)
                        .apply();
            }
        });
    }

    private void setupCursorChips() {
        bindCursorChip(cursorBlock, CursorStyle.BLOCK);
        bindCursorChip(cursorBar, CursorStyle.BAR);
        bindCursorChip(cursorUnderline, CursorStyle.UNDERLINE);
    }

    private void setupSwitches() {
        switchBlink.setChecked(AppSettings.cursorBlink(this));
        switchBlink.setOnCheckedChangeListener((b, on) ->
                AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_CURSOR_BLINK, on).apply());

        switchKeys.setChecked(AppSettings.extraKeys(this));
        switchKeys.setOnCheckedChangeListener((b, on) ->
                AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_EXTRA_KEYS, on).apply());

        switchBell.setChecked(AppSettings.visualBell(this));
        switchBell.setOnCheckedChangeListener((b, on) ->
                AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_BELL, on).apply());
    }

    private void bindCursorChip(View chip, CursorStyle style) {
        chip.setSelected(AppSettings.cursorStyle(this) == style);
        chip.setOnClickListener(v -> {
            AppSettings.prefs(this).edit().putString(AppSettings.KEY_CURSOR_STYLE, style.name()).apply();
            cursorBlock.setSelected(style == CursorStyle.BLOCK);
            cursorBar.setSelected(style == CursorStyle.BAR);
            cursorUnderline.setSelected(style == CursorStyle.UNDERLINE);
            if (palette != null) {
                ThemeApplier.styleChip(cursorBlock, palette);
                ThemeApplier.styleChip(cursorBar, palette);
                ThemeApplier.styleChip(cursorUnderline, palette);
            }
        });
    }

    private void applyPalette(ThemePalette p) {
        this.palette = p;

        ThemeApplier.styleWindow(this, p);
        settingsRoot.setBackgroundColor(p.background);
        settingsHeader.setBackgroundColor(p.surface);
        settingsTitle.setTextColor(p.foreground);
        settingsSubtitle.setTextColor(p.subtle);
        ThemeApplier.tintIcon(closeSettings, p.foreground);

        labelTerminalColor.setTextColor(p.subtle);
        helpTerminalColor.setTextColor(p.muted);

        labelFontSize.setTextColor(p.subtle);
        ThemeApplier.styleSlider(fontSeek, p);
        fontValue.setTextColor(p.muted);

        labelCursor.setTextColor(p.subtle);
        ThemeApplier.styleChip(cursorBlock, p);
        ThemeApplier.styleChip(cursorBar, p);
        ThemeApplier.styleChip(cursorUnderline, p);

        ThemeApplier.styleRow(rowBlink, p);
        labelBlink.setTextColor(p.foreground);
        ThemeApplier.styleSwitch(switchBlink, p);
        helpBlink.setTextColor(p.subtle);

        labelKeyboard.setTextColor(p.subtle);
        ThemeApplier.styleRow(rowKeys, p);
        labelKeys.setTextColor(p.foreground);
        ThemeApplier.styleSwitch(switchKeys, p);
        ThemeApplier.styleRow(rowBell, p);
        labelBell.setTextColor(p.foreground);
        ThemeApplier.styleSwitch(switchBell, p);

        for (int i = 0; i < themeGrid.getChildCount(); i++) {
            View child = themeGrid.getChildAt(i);
            View cv = child.findViewById(R.id.themeCard);
            if (cv != null) restyleThemeCardBorder(cv, cv.isSelected());
        }
    }
}
