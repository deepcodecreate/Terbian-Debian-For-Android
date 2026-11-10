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

package com.terbian.gui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.terbian.R;
import com.terbian.prefs.AppSettings;
import com.terbian.ui.ThemeApplier;
import com.terbian.ui.ThemePalette;

final class GuiTheme {
    private GuiTheme() {
    }

    static ThemePalette apply(Activity activity) {
        ThemePalette p = ThemePalette.of(activity);
        ThemeApplier.styleWindow(activity, p);
        activity.getWindow().getDecorView().setBackgroundColor(p.background);
        Colors c = new Colors(activity);
        View root = activity.findViewById(android.R.id.content);
        if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
            View content = ((ViewGroup) root).getChildAt(0);
            walk(content, p, c);
            if (content.getFitsSystemWindows()) {
                ThemeApplier.backdrop(content, p);
            }
        }
        return p;
    }

    private static final class Colors {
        final int bg;
        final int surface;
        final int fg;
        final int muted;
        final int subtle;

        Colors(Activity a) {
            bg = a.getColor(R.color.bg);
            surface = a.getColor(R.color.surface);
            fg = a.getColor(R.color.fg);
            muted = a.getColor(R.color.muted);
            subtle = a.getColor(R.color.subtle);
        }
    }

    private static void walk(View v, ThemePalette p, Colors c) {
        String tag = v.getTag() instanceof String ? (String) v.getTag() : "";

        Drawable bg = v.getBackground();
        if (bg instanceof ColorDrawable) {
            int color = ((ColorDrawable) bg).getColor();
            if (color == c.bg) {
                v.setBackgroundColor(p.background);
            } else if (color == c.surface) {
                v.setBackgroundColor(p.surface);
            }
        }
        if ("card".equals(tag) || "field".equals(tag)) {
            ThemeApplier.styleRow(v, p);
        } else if ("panel".equals(tag)) {
            v.setBackgroundColor(p.background);
        } else if ("overlay".equals(tag)) {
            v.setBackgroundColor(ThemePalette.withAlpha(p.surface, 0xE6));
        } else if ("chip".equals(tag)) {
            v.setBackground(chipBackground(v, p));
        } else if ("terminal".equals(tag)) {
            styleTerminal(v);
            return;
        }

        if (v instanceof MaterialButton) {
            styleButton((MaterialButton) v, tag, p);
        } else if (v instanceof SwitchCompat) {
            SwitchCompat toggle = (SwitchCompat) v;
            toggle.setTextColor(p.foreground);
            int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
            toggle.setThumbTintList(new ColorStateList(states, new int[]{p.onPrimary, p.subtle}));
            toggle.setTrackTintList(new ColorStateList(states, new int[]{p.primary, p.surfaceHighest}));
        } else if (v instanceof EditText) {
            EditText edit = (EditText) v;
            edit.setTextColor(p.foreground);
            edit.setHintTextColor(p.subtle);
        } else if (v instanceof TextView) {
            TextView text = (TextView) v;
            int cur = text.getCurrentTextColor();
            if (cur == c.fg) {
                text.setTextColor(p.foreground);
            } else if (cur == c.muted) {
                text.setTextColor(p.muted);
            } else if (cur == c.subtle) {
                text.setTextColor(p.subtle);
            }
        } else if (v instanceof com.terbian.ui.ExpressiveLoadingView) {
            ((com.terbian.ui.ExpressiveLoadingView) v).setColor(p.primary);
            ((com.terbian.ui.ExpressiveLoadingView) v).setContainerColor(p.primaryContainer);
        } else if (v instanceof ImageView && "icon".equals(tag)) {
            ImageViewCompat.setImageTintList((ImageView) v, ColorStateList.valueOf(p.foreground));
        }

        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), p, c);
            }
        }
    }

    private static void styleTerminal(View panel) {
        com.terbian.terminal.view.TerminalTheme theme = AppSettings.currentTheme(panel.getContext());
        int bg = theme.backgroundColor;
        int fg = theme.foregroundColor;
        int muted = ThemePalette.withAlpha(fg, 0x99);
        int border = ThemePalette.withAlpha(fg, 0x33);
        panel.setBackground(com.terbian.ui.Shapes.rect(panel.getContext(), bg,
                com.terbian.ui.Shapes.MEDIUM, border, 1));
        tintTerminal(panel, fg, muted, theme.cursorColor);
    }

    private static void tintTerminal(View v, int fg, int muted, int accent) {
        if (v instanceof TextView) {
            TextView text = (TextView) v;
            text.setTextColor(text.getId() == R.id.guiLog ? fg : muted);
            text.setHighlightColor(ThemePalette.withAlpha(accent, 0x55));
        } else if (v instanceof ImageView) {
            ImageViewCompat.setImageTintList((ImageView) v, ColorStateList.valueOf(muted));
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintTerminal(group.getChildAt(i), fg, muted, accent);
            }
        }
    }

    private static StateListDrawable chipBackground(View v, ThemePalette p) {
        float density = v.getResources().getDisplayMetrics().density;
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_selected},
                chipShape(v, p.secondaryContainer, p.secondaryContainer, density));
        list.addState(new int[]{}, chipShape(v, p.surface, p.border, density));
        if (v instanceof TextView) {
            ((TextView) v).setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {}},
                    new int[]{p.onSecondaryContainer, p.foreground}));
        }
        return list;
    }

    private static GradientDrawable chipShape(View v, int fill, int stroke, float density) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setCornerRadius(com.terbian.ui.Shapes.radius(v, com.terbian.ui.Shapes.LARGE));
        shape.setStroke(Math.max(1, Math.round(density)), stroke);
        return shape;
    }

    private static void styleButton(MaterialButton b, String tag, ThemePalette p) {
        b.setCornerRadius(Math.round(com.terbian.ui.Shapes.radius(b, com.terbian.ui.Shapes.EXTRA_LARGE_INCREASED)));
        if ("primary".equals(tag)) {
            int[][] states = {{android.R.attr.state_enabled}, {}};
            b.setBackgroundTintList(new ColorStateList(states,
                    new int[]{p.primary, ThemePalette.withAlpha(p.primary, 90)}));
            int[][] on = {{android.R.attr.state_enabled}, {}};
            int[] onColors = {p.onPrimary, ThemePalette.withAlpha(p.onPrimary, 110)};
            b.setTextColor(new ColorStateList(on, onColors));
            b.setIconTint(new ColorStateList(on, onColors));
            b.setStrokeWidth(0);
            b.setRippleColor(ColorStateList.valueOf(ThemePalette.withAlpha(p.onPrimary, 40)));
        } else if ("outline".equals(tag)) {
            b.setBackgroundTintList(ColorStateList.valueOf(p.secondaryContainer));
            b.setTextColor(p.onSecondaryContainer);
            b.setIconTint(ColorStateList.valueOf(p.onSecondaryContainer));
            b.setStrokeWidth(0);
            b.setRippleColor(ColorStateList.valueOf(ThemePalette.withAlpha(p.primary, 50)));
        }
    }
}
