/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.terminal.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.terbian.R;
import com.terbian.prefs.AppSettings;

public final class Watermark {
    private Watermark() {
    }

    public enum Emblem {
        NONE("none", 0, R.string.wm_none),
        KALI("kali", R.drawable.wm_kali, R.string.wm_kali),
        UBUNTU("ubuntu", R.drawable.wm_ubuntu, R.string.wm_ubuntu),
        DEBIAN("debian", R.drawable.wm_debian, R.string.wm_debian),
        ARCH("arch", R.drawable.wm_arch, R.string.wm_arch),
        BLACKARCH("blackarch", R.drawable.wm_blackarch, R.string.wm_blackarch),
        FREEBSD("freebsd", R.drawable.wm_freebsd, R.string.wm_freebsd),
        PACMAN("pacman", R.drawable.wm_pacman, R.string.wm_pacman),
        REDHAT("redhat", R.drawable.wm_redhat, R.string.wm_redhat);

        public final String id;
        public final int drawable;
        public final int label;

        Emblem(String id, int drawable, int label) {
            this.id = id;
            this.drawable = drawable;
            this.label = label;
        }

        public static Emblem fromId(String id) {
            for (Emblem e : values()) {
                if (e.id.equals(id)) return e;
            }
            return NONE;
        }
    }

    public enum Style { FAINT, EMBOSS, GLOW }

    public enum Position { CENTER, CORNER }

    public static final class Config {
        public Emblem emblem = Emblem.NONE;
        public Style style = Style.EMBOSS;
        public Position position = Position.CENTER;

        public int strength = 22;

        public int size = 62;

        public Config copy() {
            Config c = new Config();
            c.emblem = emblem;
            c.style = style;
            c.position = position;
            c.strength = strength;
            c.size = size;
            return c;
        }
    }

    public static Config load(Context context) {
        Config c = new Config();
        c.emblem = AppSettings.wmEmblem(context);
        c.style = AppSettings.wmStyle(context);
        c.position = AppSettings.wmPosition(context);
        c.strength = AppSettings.wmStrength(context);
        c.size = AppSettings.wmSize(context);
        return c;
    }

    public static final class Painter {
        private Emblem loaded = Emblem.NONE;
        private Drawable drawable;

        private Drawable drawableFor(Context context, Emblem emblem) {
            if (emblem == Emblem.NONE) return null;
            if (drawable == null || loaded != emblem) {
                Drawable d = ContextCompat.getDrawable(context, emblem.drawable);
                drawable = d == null ? null : d.mutate();
                loaded = emblem;
            }
            return drawable;
        }

        public void draw(Canvas canvas, Context context, int width, int height,
                         int fg, int accent, Config cfg, float inset) {
            if (cfg == null || cfg.emblem == Emblem.NONE || width <= 0 || height <= 0) return;
            Drawable d = drawableFor(context, cfg.emblem);
            if (d == null) return;

            float density = context.getResources().getDisplayMetrics().density;
            float side = Math.min(width, height) * cfg.size / 100f;
            float cx;
            float cy;
            if (cfg.position == Position.CENTER) {
                cx = width / 2f;
                cy = height / 2f;
            } else {
                cx = width - inset - side / 2f;
                cy = height - inset - side / 2f;
            }
            int left = Math.round(cx - side / 2f);
            int top = Math.round(cy - side / 2f);
            int right = Math.round(cx + side / 2f);
            int bottom = Math.round(cy + side / 2f);
            float strength = Math.max(5, Math.min(60, cfg.strength)) / 100f;

            switch (cfg.style) {
                case EMBOSS: {
                    float off = Math.max(1f, side / 70f) * Math.max(1f, density * 0.6f);
                    paint(canvas, d, left - off, top - off, right - off, bottom - off,
                            0xFFFFFFFF, strength * 0.55f);
                    paint(canvas, d, left + off, top + off, right + off, bottom + off,
                            0xFF000000, Math.min(1f, strength * 1.6f));
                    paint(canvas, d, left, top, right, bottom, fg, strength * 0.45f);
                    break;
                }
                case GLOW: {
                    for (int i = 3; i >= 1; i--) {
                        float grow = side * 0.035f * i;
                        paint(canvas, d, left - grow, top - grow, right + grow, bottom + grow,
                                accent, strength * 0.22f);
                    }
                    paint(canvas, d, left, top, right, bottom, accent, strength * 0.85f);
                    break;
                }
                default:
                    paint(canvas, d, left, top, right, bottom, fg, strength);
                    break;
            }
        }

        private static void paint(Canvas canvas, Drawable d, float l, float t, float r, float b,
                                  int color, float alpha) {
            d.setBounds(Math.round(l), Math.round(t), Math.round(r), Math.round(b));
            d.setTint(color);
            d.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
            d.draw(canvas);
        }
    }
}
