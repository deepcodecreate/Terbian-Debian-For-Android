/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.terbian.terminal.PromptStyle;
import com.terbian.terminal.view.TerminalTheme;
import com.terbian.terminal.view.Watermark;

public final class TerminalPreviewView extends View {
    public static final int MODE_EMBLEM = 0;

    public static final int MODE_COMPACT = 1;

    public static final int MODE_FULL = 2;

    public static final int MODE_PROMPT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final Watermark.Painter emblem = new Watermark.Painter();
    private TerminalTheme theme = TerminalTheme.black();
    private PromptStyle prompt = PromptStyle.KALI;
    private Watermark.Config watermark;
    private int mode = MODE_FULL;
    private float cornerDp = Shapes.LARGE;
    private float minStrength;

    public TerminalPreviewView(Context context) {
        this(context, null);
    }

    public TerminalPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(Typeface.MONOSPACE);
    }

    public void setTheme(TerminalTheme theme) {
        this.theme = theme;
        invalidate();
    }

    public void setPromptStyle(PromptStyle style) {
        this.prompt = style == null ? PromptStyle.KALI : style;
        invalidate();
    }

    public void setWatermark(Watermark.Config config) {
        this.watermark = config;
        invalidate();
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    public void setCornerDp(float dp) {
        this.cornerDp = dp;
        invalidate();
    }

    public void setMinStrength(float percent) {
        this.minStrength = percent;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int blue() {
        return theme.bright[4];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        float r = Shapes.radius(getContext(), cornerDp);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        canvas.drawColor(theme.backgroundColor);

        if (watermark != null && watermark.emblem != Watermark.Emblem.NONE) {
            Watermark.Config c = watermark;
            if (minStrength > 0 && c.strength < minStrength) {
                c = c.copy();
                c.strength = Math.round(minStrength);
            }
            emblem.draw(canvas, getContext(), w, h, theme.foregroundColor, theme.cursorColor, c, dp(10));
        }

        if (mode == MODE_FULL) {
            drawFull(canvas, w, h);
        } else if (mode == MODE_COMPACT) {
            drawCompact(canvas, w, h);
        } else if (mode == MODE_PROMPT) {
            drawPromptOnly(canvas, w, h);
        }
        canvas.restore();
    }

    private float run(Canvas canvas, String text, float x, float y, int color) {
        paint.setColor(color);
        canvas.drawText(text, x, y, paint);
        return x + paint.measureText(text);
    }

    private float promptHeader(Canvas canvas, float pad, float y, float line) {
        float x;
        switch (prompt) {
            case KALI:
                x = run(canvas, "\u250C\u2500\u2500(", pad, y, theme.standard[4]);
                x = run(canvas, "root@terbian", x, y, blue());
                x = run(canvas, ")-[", x, y, theme.standard[4]);
                x = run(canvas, "~", x, y, theme.foregroundColor);
                run(canvas, "]", x, y, theme.standard[4]);
                return y + line;
            case ROUNDED:
                x = run(canvas, "\u256D\u2500", pad, y, theme.standard[5]);
                x = run(canvas, "root", x, y, theme.bright[6]);
                x = run(canvas, "@", x, y, theme.bright[0]);
                x = run(canvas, "terbian ", x, y, theme.bright[2]);
                run(canvas, "~", x, y, theme.bright[3]);
                return y + line;
            default:
                return y;
        }
    }

    private float promptInput(Canvas canvas, float pad, float y) {
        float x;
        switch (prompt) {
            case KALI:
                x = run(canvas, "\u2514\u2500", pad, y, theme.standard[4]);
                return run(canvas, "# ", x, y, blue());
            case ROUNDED:
                x = run(canvas, "\u2570\u2500", pad, y, theme.standard[5]);
                return run(canvas, "\u276F ", x, y, theme.bright[5]);
            default:
                x = run(canvas, "\u279C ", pad, y, theme.bright[2]);
                return run(canvas, "~ ", x, y, theme.bright[6]);
        }
    }

    private void drawCursor(Canvas canvas, float x, float y) {
        paint.setColor(theme.cursorColor);
        canvas.drawRect(x + dp(2), y - dp(11), x + dp(9), y + dp(3), paint);
    }

    private void drawFull(Canvas canvas, int w, int h) {
        float pad = dp(14);
        paint.setTextSize(dp(11.5f));
        float line = dp(17);
        float y = pad + line * 0.8f;
        y = promptHeader(canvas, pad, y, line);
        float x = promptInput(canvas, pad, y);
        run(canvas, "ls", x, y, theme.foregroundColor);

        y += line;
        x = run(canvas, "bin  ", pad, y, blue());
        x = run(canvas, "etc  ", x, y, blue());
        x = run(canvas, "run.sh  ", x, y, theme.bright[2]);
        x = run(canvas, "a.tar  ", x, y, theme.bright[1]);
        run(canvas, "notes.txt", x, y, theme.foregroundColor);

        y += line;
        y = promptHeader(canvas, pad, y, line);
        x = promptInput(canvas, pad, y);
        drawCursor(canvas, x, y);

        drawSwatches(canvas, pad, h - pad - dp(5), dp(5), w - pad * 2);
    }

    private void drawPromptOnly(Canvas canvas, int w, int h) {
        float pad = dp(14);
        paint.setTextSize(dp(11.5f));
        float line = dp(17);
        int lines = prompt == PromptStyle.ARROW ? 1 : 2;
        float y = (h - line * lines) / 2f + line * 0.8f;
        y = promptHeader(canvas, pad, y, line);
        float x = promptInput(canvas, pad, y);
        run(canvas, "ls -la", x, y, theme.foregroundColor);
        drawCursor(canvas, x + paint.measureText("ls -la "), y);
    }

    private void drawCompact(Canvas canvas, int w, int h) {
        float pad = dp(10);
        paint.setTextSize(dp(9.5f));
        float line = dp(14);
        float y = pad + line * 0.8f;
        float x = run(canvas, "root@terbian", pad, y, blue());
        run(canvas, ":~#", x, y, theme.foregroundColor);
        y += line;
        x = run(canvas, "bin ", pad, y, blue());
        x = run(canvas, "run.sh ", x, y, theme.bright[2]);
        run(canvas, "txt", x, y, theme.foregroundColor);
        drawSwatches(canvas, pad, h - pad - dp(3), dp(3), w - pad * 2);
    }

    private void drawSwatches(Canvas canvas, float left, float cy, float radius, float width) {
        float step = Math.min(radius * 2.9f, width / 16f);
        float x = left + radius;
        for (int i = 0; i < 16; i++) {
            paint.setColor(i < 8 ? theme.standard[i] : theme.bright[i - 8]);
            canvas.drawCircle(x + i * step, cy, radius, paint);
        }
    }
}
