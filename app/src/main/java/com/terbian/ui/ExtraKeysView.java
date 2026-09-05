package com.terbian.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.terbian.terminal.view.TerbianTerminalView;

import java.util.ArrayList;
import java.util.List;

public class ExtraKeysView extends HorizontalScrollView {

    public interface Host {
        TerbianTerminalView currentTerminal();
    }

    private Host host;
    private TextView ctrl;
    private TextView alt;
    private final List<TextView> keys = new ArrayList<>();

    private int normalBg = 0xFF3A2A1C;
    private int normalFg = 0xFFF3E6D8;
    private int activeBg = 0xFFD9A441;
    private int activeFg = 0xFF2A1608;

    public ExtraKeysView(Context context) {
        super(context);
        init();
    }

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void applyTheme(ThemePalette palette) {
        setBackgroundColor(palette.surface);
        normalBg = palette.surfaceHigh;
        normalFg = palette.foreground;
        activeBg = palette.primary;
        activeFg = palette.onPrimary;
        refreshModifiers();
    }

    public void refreshModifiers() {
        TerbianTerminalView tv = host == null ? null : host.currentTerminal();
        boolean ctrlOn = tv != null && tv.isCtrlModifierActive();
        boolean altOn = tv != null && tv.isAltModifierActive();
        for (TextView key : keys) {
            boolean active = (key == ctrl && ctrlOn) || (key == alt && altOn);
            tint(key, active);
        }
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        int pad = dp(6);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, dp(6), pad, dp(6));
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        ctrl = addKey(row, "CTRL", v -> {
            TerbianTerminalView tv = terminal();
            if (tv == null) return;
            tv.requestFocus();
            tv.toggleCtrlModifier();
            refreshModifiers();
        });
        alt = addKey(row, "ALT", v -> {
            TerbianTerminalView tv = terminal();
            if (tv == null) return;
            tv.requestFocus();
            tv.toggleAltModifier();
            refreshModifiers();
        });
        addKey(row, "ESC", v -> send("\u001b"));
        addKey(row, "TAB", v -> send("\t"));
        addKey(row, "-", v -> send("-"));
        addKey(row, "/", v -> send("/"));
        addKey(row, "|", v -> send("|"));
        addKey(row, "HOME", v -> send("\u001b[H"));
        addKey(row, "END", v -> send("\u001b[F"));
        addKey(row, "PGUP", v -> send("\u001b[5~"));
        addKey(row, "PGDN", v -> send("\u001b[6~"));
        addKey(row, "↑", v -> send("\u001b[A"));
        addKey(row, "↓", v -> send("\u001b[B"));
        addKey(row, "←", v -> send("\u001b[D"));
        addKey(row, "→", v -> send("\u001b[C"));
    }

    private TerbianTerminalView terminal() {
        return host == null ? null : host.currentTerminal();
    }

    private void send(String seq) {
        TerbianTerminalView tv = terminal();
        if (tv == null) return;
        tv.requestFocus();
        if (tv.isCtrlModifierActive() && seq.length() == 1) {
            char c = Character.toUpperCase(seq.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                tv.sendRawInput(String.valueOf((char) (c - 'A' + 1)));
                tv.toggleCtrlModifier();
                refreshModifiers();
                return;
            }
        }
        if (tv.isAltModifierActive()) {
            tv.sendRawInput("\u001b" + seq);
            tv.toggleAltModifier();
            refreshModifiers();
            return;
        }
        tv.sendRawInput(seq);
    }

    private TextView addKey(LinearLayout row, String label, OnClickListener click) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setMinWidth(dp(48));
        tv.setMinHeight(dp(40));
        tv.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMarginStart(dp(3));
        lp.setMarginEnd(dp(3));
        tv.setLayoutParams(lp);
        tint(tv, false);
        tv.setOnClickListener(click);
        row.addView(tv);
        keys.add(tv);
        return tv;
    }

    private void tint(TextView tv, boolean on) {
        if (tv == null) return;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(8));
        if (on) {
            drawable.setColor(activeBg);
            tv.setTextColor(activeFg);
        } else {
            drawable.setColor(normalBg);
            tv.setTextColor(normalFg);
        }
        tv.setBackground(drawable);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
