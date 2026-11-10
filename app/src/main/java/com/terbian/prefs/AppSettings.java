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

package com.terbian.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.terbian.debian.Distro;
import com.terbian.terminal.PromptStyle;
import com.terbian.terminal.view.CursorStyle;
import com.terbian.terminal.view.TerminalTheme;
import com.terbian.terminal.view.Watermark;

public final class AppSettings {
    public static final String PREFS = "terbian_settings";

    public static final String KEY_THEME = "terminal_theme";
    public static final String KEY_FONT_SP = "font_size_sp";
    public static final String KEY_CURSOR_BLINK = "cursor_blink";
    public static final String KEY_CURSOR_STYLE = "cursor_style";
    public static final String KEY_EXTRA_KEYS = "extra_keys";
    public static final String KEY_BELL = "visual_bell";
    public static final String KEY_STORAGE_ASKED = "storage_asked";
    public static final String KEY_DEFAULT_TERMINAL = "default_terminal";
    public static final String KEY_WM_EMBLEM = "wm_emblem";
    public static final String KEY_WM_STYLE = "wm_style";
    public static final String KEY_WM_POSITION = "wm_position";
    public static final String KEY_WM_STRENGTH = "wm_strength";
    public static final String KEY_WM_SIZE = "wm_size";
    public static final String KEY_PROMPT = "prompt_style";

    private AppSettings() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String themeId(Context context) {
        return prefs(context).getString(KEY_THEME, TerminalTheme.ID_BLACK);
    }

    public static float fontSizeSp(Context context) {
        return prefs(context).getFloat(KEY_FONT_SP, 14f);
    }

    public static boolean cursorBlink(Context context) {
        return prefs(context).getBoolean(KEY_CURSOR_BLINK, false);
    }

    public static CursorStyle cursorStyle(Context context) {
        String raw = prefs(context).getString(KEY_CURSOR_STYLE, CursorStyle.BLOCK.name());
        try {
            return CursorStyle.valueOf(raw);
        } catch (Exception e) {
            return CursorStyle.BLOCK;
        }
    }

    public static boolean extraKeys(Context context) {
        return prefs(context).getBoolean(KEY_EXTRA_KEYS, true);
    }

    public static boolean visualBell(Context context) {
        return prefs(context).getBoolean(KEY_BELL, true);
    }

    public static Distro defaultDistro(Context context) {
        return Distro.fromId(prefs(context).getString(KEY_DEFAULT_TERMINAL, Distro.DEBIAN.id));
    }

    public static void setDefaultDistro(Context context, Distro distro) {
        prefs(context).edit().putString(KEY_DEFAULT_TERMINAL, distro.id).apply();
    }

    public static Watermark.Emblem wmEmblem(Context context) {
        return Watermark.Emblem.fromId(prefs(context).getString(KEY_WM_EMBLEM, Watermark.Emblem.NONE.id));
    }

    public static void setWmEmblem(Context context, Watermark.Emblem emblem) {
        prefs(context).edit().putString(KEY_WM_EMBLEM, emblem.id).apply();
    }

    public static Watermark.Style wmStyle(Context context) {
        try {
            return Watermark.Style.valueOf(
                    prefs(context).getString(KEY_WM_STYLE, Watermark.Style.EMBOSS.name()));
        } catch (Exception e) {
            return Watermark.Style.EMBOSS;
        }
    }

    public static void setWmStyle(Context context, Watermark.Style style) {
        prefs(context).edit().putString(KEY_WM_STYLE, style.name()).apply();
    }

    public static Watermark.Position wmPosition(Context context) {
        try {
            return Watermark.Position.valueOf(
                    prefs(context).getString(KEY_WM_POSITION, Watermark.Position.CENTER.name()));
        } catch (Exception e) {
            return Watermark.Position.CENTER;
        }
    }

    public static void setWmPosition(Context context, Watermark.Position position) {
        prefs(context).edit().putString(KEY_WM_POSITION, position.name()).apply();
    }

    public static int wmStrength(Context context) {
        return Math.max(5, Math.min(60, prefs(context).getInt(KEY_WM_STRENGTH, 22)));
    }

    public static void setWmStrength(Context context, int value) {
        prefs(context).edit().putInt(KEY_WM_STRENGTH, value).apply();
    }

    public static int wmSize(Context context) {
        return Math.max(30, Math.min(100, prefs(context).getInt(KEY_WM_SIZE, 62)));
    }

    public static void setWmSize(Context context, int value) {
        prefs(context).edit().putInt(KEY_WM_SIZE, value).apply();
    }

    public static PromptStyle promptStyle(Context context) {
        return PromptStyle.fromId(prefs(context).getString(KEY_PROMPT, PromptStyle.KALI.id));
    }

    public static void setPromptStyle(Context context, PromptStyle style) {
        prefs(context).edit().putString(KEY_PROMPT, style.id).apply();
    }

    public static TerminalTheme currentTheme(Context context) {
        return TerminalTheme.byId(themeId(context))
                .toBuilder()
                .fontSizeSp(fontSizeSp(context))
                .cursorBlinkEnabled(cursorBlink(context))
                .cursorBlinkIntervalMs(530)
                .cursorStyle(cursorStyle(context))
                .build();
    }
}
