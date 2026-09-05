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

    private AppSettings() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String themeId(Context context) {
        return prefs(context).getString(KEY_THEME, TerminalTheme.ID_BURNT);
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
