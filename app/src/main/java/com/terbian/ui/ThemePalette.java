/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import com.terbian.prefs.AppSettings;
import com.terbian.terminal.view.TerminalTheme;

public final class ThemePalette {
    public final boolean dark;
    public final int background;
    public final int surfaceLowest;
    public final int surfaceLow;
    public final int surface;
    public final int surfaceHigh;
    public final int surfaceHighest;
    public final int border;
    public final int foreground;
    public final int muted;
    public final int subtle;
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int onSecondaryContainer;
    public final int error;

    private ThemePalette(Mut m) {
        dark = m.dark;
        background = m.background;
        surfaceLowest = m.surfaceLowest;
        surfaceLow = m.surfaceLow;
        surface = m.surface;
        surfaceHigh = m.surfaceHigh;
        surfaceHighest = m.surfaceHighest;
        border = m.border;
        foreground = m.foreground;
        muted = m.muted;
        subtle = m.subtle;
        primary = m.primary;
        onPrimary = m.onPrimary;
        primaryContainer = m.primaryContainer;
        onPrimaryContainer = m.onPrimaryContainer;
        secondaryContainer = m.secondaryContainer;
        onSecondaryContainer = m.onSecondaryContainer;
        error = m.error;
    }

    private static final class Mut {
        boolean dark = true;
        int background, surfaceLowest, surfaceLow, surface, surfaceHigh, surfaceHighest;
        int border, foreground, muted, subtle;
        int primary, onPrimary, primaryContainer, onPrimaryContainer;
        int secondaryContainer, onSecondaryContainer, error;
    }

    public static ThemePalette of(Context context) {
        return of(context, AppSettings.currentTheme(context));
    }

    public static ThemePalette of(Context context, TerminalTheme theme) {
        Mut m;
        Appearance.ColorSource source = Appearance.colorSource(context);
        boolean dark = Appearance.isDark(context);
        boolean black = Appearance.pureBlack(context);
        if (source == Appearance.ColorSource.TERMINAL) {
            m = fromTerminal(theme);
        } else if (source == Appearance.ColorSource.WALLPAPER && Build.VERSION.SDK_INT >= 31) {
            m = fromWallpaper(context, dark, black);
        } else {
            m = fromSeed(Appearance.seed(context), Appearance.paletteStyle(context), dark, black);
        }
        applyContrast(m, Appearance.contrast(context));
        return new ThemePalette(m);
    }

    public static ThemePalette from(TerminalTheme theme) {
        return new ThemePalette(fromTerminal(theme));
    }

    private static Mut fromTerminal(TerminalTheme theme) {
        Mut m = new Mut();
        int background = theme.backgroundColor;
        int foreground = theme.foregroundColor;
        int primary = theme.cursorColor;
        m.dark = ColorUtils.calculateLuminance(background) < 0.5;
        m.background = background;
        m.surfaceLowest = mix(background, Color.BLACK, m.dark ? 0.35f : 0f);
        m.surfaceLow = mix(background, foreground, 0.03f);
        m.surface = mix(background, foreground, 0.06f);
        m.surfaceHigh = mix(background, foreground, 0.12f);
        m.surfaceHighest = mix(background, foreground, 0.17f);
        m.border = mix(background, foreground, 0.22f);
        m.foreground = foreground;
        m.muted = mix(foreground, background, 0.25f);
        m.subtle = mix(foreground, background, 0.48f);
        m.primary = primary;
        m.onPrimary = theme.cursorTextColor;
        m.primaryContainer = mix(background, primary, 0.35f);
        m.onPrimaryContainer = foreground;
        m.secondaryContainer = m.surfaceHigh;
        m.onSecondaryContainer = foreground;
        m.error = m.dark ? 0xFFF2B8B5 : 0xFFB3261E;
        return m;
    }

    @SuppressLint("NewApi")
    private static Mut fromWallpaper(Context c, boolean dark, boolean pureBlack) {
        Mut m = new Mut();
        m.dark = dark;
        if (dark) {
            int base = c.getColor(android.R.color.system_neutral1_900);
            int tint = c.getColor(android.R.color.system_neutral1_100);
            m.background = base;
            m.surfaceLowest = mix(base, Color.BLACK, 0.4f);
            m.surfaceLow = mix(base, tint, 0.03f);
            m.surface = mix(base, tint, 0.06f);
            m.surfaceHigh = mix(base, tint, 0.10f);
            m.surfaceHighest = mix(base, tint, 0.14f);
            m.border = c.getColor(android.R.color.system_neutral2_600);
            m.foreground = c.getColor(android.R.color.system_neutral1_100);
            m.muted = c.getColor(android.R.color.system_neutral2_200);
            m.subtle = c.getColor(android.R.color.system_neutral2_400);
            m.primary = c.getColor(android.R.color.system_accent1_200);
            m.onPrimary = c.getColor(android.R.color.system_accent1_800);
            m.primaryContainer = c.getColor(android.R.color.system_accent1_700);
            m.onPrimaryContainer = c.getColor(android.R.color.system_accent1_100);
            m.secondaryContainer = c.getColor(android.R.color.system_accent2_700);
            m.onSecondaryContainer = c.getColor(android.R.color.system_accent2_100);
            m.error = 0xFFF2B8B5;
            if (pureBlack) toBlack(m);
        } else {
            int base = c.getColor(android.R.color.system_neutral1_10);
            int tint = c.getColor(android.R.color.system_neutral1_900);
            m.background = base;
            m.surfaceLowest = c.getColor(android.R.color.system_neutral1_0);
            m.surfaceLow = mix(base, tint, 0.03f);
            m.surface = mix(base, tint, 0.06f);
            m.surfaceHigh = mix(base, tint, 0.09f);
            m.surfaceHighest = mix(base, tint, 0.12f);
            m.border = c.getColor(android.R.color.system_neutral2_300);
            m.foreground = c.getColor(android.R.color.system_neutral1_900);
            m.muted = c.getColor(android.R.color.system_neutral2_700);
            m.subtle = c.getColor(android.R.color.system_neutral2_500);
            m.primary = c.getColor(android.R.color.system_accent1_600);
            m.onPrimary = c.getColor(android.R.color.system_accent1_0);
            m.primaryContainer = c.getColor(android.R.color.system_accent1_100);
            m.onPrimaryContainer = c.getColor(android.R.color.system_accent1_900);
            m.secondaryContainer = c.getColor(android.R.color.system_accent2_100);
            m.onSecondaryContainer = c.getColor(android.R.color.system_accent2_900);
            m.error = 0xFFB3261E;
        }
        return m;
    }

    private static Mut fromSeed(int seed, Appearance.PaletteStyle style, boolean dark, boolean pureBlack) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(seed, hsl);
        float hue = hsl[0];
        float seedSat = hsl[1];

        float pHue = hue;
        float pSat;
        float sSat;
        float nSat;
        switch (style) {
            case VIBRANT:
                pSat = clamp(Math.max(seedSat, 0.75f), 0f, 0.95f);
                sSat = 0.40f;
                nSat = 0.10f;
                break;
            case EXPRESSIVE:
                pHue = (hue + 60f) % 360f;
                pSat = 0.65f;
                sSat = 0.38f;
                nSat = 0.10f;
                break;
            case NEUTRAL:
                pSat = 0.12f;
                sSat = 0.08f;
                nSat = 0.03f;
                break;
            case MONOCHROME:
                pSat = 0f;
                sSat = 0f;
                nSat = 0f;
                break;
            default:
                pSat = clamp(seedSat * 0.7f, 0.38f, 0.60f);
                sSat = 0.22f;
                nSat = 0.06f;
                break;
        }
        float sHue = (pHue + 20f) % 360f;

        Mut m = new Mut();
        m.dark = dark;
        if (dark) {
            m.background = hsl(hue, nSat, 0.07f);
            m.surfaceLowest = hsl(hue, nSat, 0.05f);
            m.surfaceLow = hsl(hue, nSat, 0.10f);
            m.surface = hsl(hue, nSat, 0.12f);
            m.surfaceHigh = hsl(hue, nSat, 0.16f);
            m.surfaceHighest = hsl(hue, nSat, 0.20f);
            m.border = hsl(hue, nSat * 1.2f, 0.33f);
            m.foreground = hsl(hue, nSat, 0.90f);
            m.muted = hsl(hue, nSat * 1.5f, 0.75f);
            m.subtle = hsl(hue, nSat * 1.5f, 0.58f);
            m.primary = hsl(pHue, pSat, 0.78f);
            m.onPrimary = hsl(pHue, pSat, 0.14f);
            m.primaryContainer = hsl(pHue, pSat, 0.27f);
            m.onPrimaryContainer = hsl(pHue, pSat, 0.90f);
            m.secondaryContainer = hsl(sHue, sSat, 0.26f);
            m.onSecondaryContainer = hsl(sHue, sSat, 0.88f);
            m.error = 0xFFF2B8B5;
            if (pureBlack) toBlack(m);
        } else {
            m.background = hsl(hue, nSat, 0.98f);
            m.surfaceLowest = hsl(hue, nSat, 1.0f);
            m.surfaceLow = hsl(hue, nSat, 0.96f);
            m.surface = hsl(hue, nSat, 0.94f);
            m.surfaceHigh = hsl(hue, nSat, 0.92f);
            m.surfaceHighest = hsl(hue, nSat, 0.90f);
            m.border = hsl(hue, nSat * 1.2f, 0.75f);
            m.foreground = hsl(hue, nSat, 0.10f);
            m.muted = hsl(hue, nSat * 1.5f, 0.30f);
            m.subtle = hsl(hue, nSat * 1.5f, 0.45f);
            m.primary = hsl(pHue, pSat, 0.38f);
            m.onPrimary = hsl(pHue, pSat, 1.0f);
            m.primaryContainer = hsl(pHue, pSat, 0.90f);
            m.onPrimaryContainer = hsl(pHue, pSat, 0.14f);
            m.secondaryContainer = hsl(sHue, sSat, 0.88f);
            m.onSecondaryContainer = hsl(sHue, sSat, 0.14f);
            m.error = 0xFFB3261E;
        }
        return m;
    }

    private static void toBlack(Mut m) {
        m.background = Color.BLACK;
        m.surfaceLowest = Color.BLACK;
        m.surfaceLow = mix(m.surfaceLow, Color.BLACK, 0.7f);
        m.surface = mix(m.surface, Color.BLACK, 0.6f);
        m.surfaceHigh = mix(m.surfaceHigh, Color.BLACK, 0.5f);
        m.surfaceHighest = mix(m.surfaceHighest, Color.BLACK, 0.4f);
    }

    private static void applyContrast(Mut m, int level) {
        if (level <= 0) return;
        int extreme = m.dark ? Color.WHITE : Color.BLACK;
        float k = level == 1 ? 0.35f : 0.7f;
        m.foreground = mix(m.foreground, extreme, k);
        m.muted = mix(m.muted, extreme, k);
        m.subtle = mix(m.subtle, extreme, k);
        m.border = mix(m.border, m.foreground, k * 0.8f);
        m.primary = mix(m.primary, extreme, k * 0.3f);
        m.onPrimaryContainer = mix(m.onPrimaryContainer, extreme, k);
        m.onSecondaryContainer = mix(m.onSecondaryContainer, extreme, k);
    }

    private static int hsl(float h, float s, float l) {
        return ColorUtils.HSLToColor(new float[]{h, clamp(s, 0f, 1f), clamp(l, 0f, 1f)});
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int mix(int from, int to, float ratio) {
        float r = clamp(ratio, 0f, 1f);
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * r);
        int red = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * r);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * r);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * r);
        return Color.argb(a, red, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
