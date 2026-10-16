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

import com.terbian.terminal.view.CursorStyle;
import com.terbian.terminal.view.TerminalTheme;

public final class AppSettings {
    public static final String PREFS = "terbian_settings";

    public static final String KEY_THEME = "terminal_theme";
    public static final String KEY_FONT_SP = "font_size_sp";
    public static final String KEY_CURSOR_BLINK = "cursor_blink";
    public static final String KEY_CURSOR_STYLE = "cursor_style";
    public static final String KEY_EXTRA_KEYS = "extra_keys";
    public static final String KEY_BELL = "visual_bell";
    public static final String KEY_STORAGE_ASKED = "storage_asked";

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
