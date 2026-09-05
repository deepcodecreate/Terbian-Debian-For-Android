package com.terbian.ui;

import android.graphics.Color;

import com.terbian.terminal.view.TerminalTheme;

public final class ThemePalette {

    public final int background;
    public final int surface;
    public final int surfaceHigh;
    public final int border;
    public final int foreground;
    public final int muted;
    public final int subtle;
    public final int primary;
    public final int primaryContainer;
    public final int onPrimary;

    private ThemePalette(int background, int surface, int surfaceHigh, int border,
                          int foreground, int muted, int subtle,
                          int primary, int primaryContainer, int onPrimary) {
        this.background = background;
        this.surface = surface;
        this.surfaceHigh = surfaceHigh;
        this.border = border;
        this.foreground = foreground;
        this.muted = muted;
        this.subtle = subtle;
        this.primary = primary;
        this.primaryContainer = primaryContainer;
        this.onPrimary = onPrimary;
    }

    public static ThemePalette from(TerminalTheme theme) {
        int background = theme.backgroundColor;
        int foreground = theme.foregroundColor;
        int primary = theme.cursorColor;
        int onPrimary = theme.cursorTextColor;

        int surface = mix(background, foreground, 0.06f);
        int surfaceHigh = mix(background, foreground, 0.12f);
        int border = mix(background, foreground, 0.22f);
        int muted = mix(foreground, background, 0.25f);
        int subtle = mix(foreground, background, 0.48f);
        int primaryContainer = mix(background, primary, 0.35f);

        return new ThemePalette(background, surface, surfaceHigh, border,
                foreground, muted, subtle, primary, primaryContainer, onPrimary);
    }

    private static int mix(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
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
