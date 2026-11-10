/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */

package com.terbian.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.appcompat.app.AppCompatDelegate;

import com.terbian.prefs.AppSettings;

public final class Appearance {
    public enum ThemeMode { SYSTEM, LIGHT, DARK }

    public enum ColorSource { TERMINAL, WALLPAPER, SEED }

    public enum PaletteStyle { TONAL_SPOT, VIBRANT, EXPRESSIVE, NEUTRAL, MONOCHROME }

    public enum MotionStyle { EXPRESSIVE, STANDARD, REDUCED }

    public static final int DEFAULT_SEED = 0xFFD70A53;

    private static final String K_MODE = "ap_theme_mode";
    private static final String K_SOURCE = "ap_color_source";
    private static final String K_SEED = "ap_seed";
    private static final String K_STYLE = "ap_palette_style";
    private static final String K_CONTRAST = "ap_contrast";
    private static final String K_BLACK = "ap_pure_black";
    private static final String K_CORNERS = "ap_corner_scale";
    private static final String K_TEXT = "ap_text_scale";
    private static final String K_MOTION = "ap_motion";
    private static final String K_HAPTICS = "ap_haptics";

    private Appearance() {
    }

    private static SharedPreferences p(Context c) {
        return AppSettings.prefs(c);
    }

    private static <E extends Enum<E>> E readEnum(Context c, String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, p(c).getString(key, fallback.name()));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static ThemeMode themeMode(Context c) {
        return readEnum(c, K_MODE, ThemeMode.class, ThemeMode.DARK);
    }

    public static void setThemeMode(Context c, ThemeMode v) {
        p(c).edit().putString(K_MODE, v.name()).apply();
        applyNightMode(c);
    }

    public static ColorSource colorSource(Context c) {
        return readEnum(c, K_SOURCE, ColorSource.class, ColorSource.WALLPAPER);
    }

    public static void setColorSource(Context c, ColorSource v) {
        p(c).edit().putString(K_SOURCE, v.name()).apply();
    }

    public static int seed(Context c) {
        return p(c).getInt(K_SEED, DEFAULT_SEED);
    }

    public static void setSeed(Context c, int color) {
        p(c).edit().putInt(K_SEED, color | 0xFF000000).apply();
    }

    public static PaletteStyle paletteStyle(Context c) {
        return readEnum(c, K_STYLE, PaletteStyle.class, PaletteStyle.TONAL_SPOT);
    }

    public static void setPaletteStyle(Context c, PaletteStyle v) {
        p(c).edit().putString(K_STYLE, v.name()).apply();
    }

    public static int contrast(Context c) {
        return Math.max(0, Math.min(2, p(c).getInt(K_CONTRAST, 0)));
    }

    public static void setContrast(Context c, int level) {
        p(c).edit().putInt(K_CONTRAST, level).apply();
    }

    public static boolean pureBlack(Context c) {
        return p(c).getBoolean(K_BLACK, true);
    }

    public static void setPureBlack(Context c, boolean on) {
        p(c).edit().putBoolean(K_BLACK, on).apply();
    }

    public static float cornerScale(Context c) {
        return Math.max(0.5f, Math.min(1.5f, p(c).getFloat(K_CORNERS, 1f)));
    }

    public static void setCornerScale(Context c, float v) {
        p(c).edit().putFloat(K_CORNERS, v).apply();
    }

    public static float textScale(Context c) {
        return Math.max(0.85f, Math.min(1.3f, p(c).getFloat(K_TEXT, 1f)));
    }

    public static void setTextScale(Context c, float v) {
        p(c).edit().putFloat(K_TEXT, v).apply();
    }

    public static MotionStyle motion(Context c) {
        return readEnum(c, K_MOTION, MotionStyle.class, MotionStyle.EXPRESSIVE);
    }

    public static void setMotion(Context c, MotionStyle v) {
        p(c).edit().putString(K_MOTION, v.name()).apply();
    }

    public static boolean haptics(Context c) {
        return p(c).getBoolean(K_HAPTICS, true);
    }

    public static void setHaptics(Context c, boolean on) {
        p(c).edit().putBoolean(K_HAPTICS, on).apply();
    }

    public static boolean isDark(Context c) {
        switch (themeMode(c)) {
            case LIGHT:
                return false;
            case DARK:
                return true;
            default:
                int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return night == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    public static void applyNightMode(Context c) {
        int mode;
        switch (themeMode(c)) {
            case LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static float fontScale(Context c) {
        return Resources.getSystem().getConfiguration().fontScale * textScale(c);
    }

    public static Context wrap(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = fontScale(base);
        return base.createConfigurationContext(config);
    }
}
