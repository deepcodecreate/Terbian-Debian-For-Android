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

package com.terbian.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.WindowCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.terbian.R;
import com.terbian.debian.Distro;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.view.CursorStyle;
import com.terbian.terminal.view.Watermark;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends BaseActivity {
    private static final String GITHUB_URL = "https://github.com/deepcodecreate";

    private LinearLayout root;
    private ThemePalette p;
    private final boolean[] open = {true, true, true};
    private int savedScroll;
    private boolean built;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        setContentView(root);
        render();
        built = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (built) {
            render();
        }
    }

    private void render() {
        p = ThemePalette.of(this);
        ThemeApplier.styleWindow(this, p);
        ThemeApplier.backdrop(root, p);
        root.removeAllViews();
        root.addView(header());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(8), dp(14), dp(28));
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(group(0, R.drawable.ic_set_appearance, getString(R.string.cat_appearance),
                getString(R.string.grp_appearance_sub), appearanceRows()));
        column.addView(group(1, R.drawable.ic_set_tune, getString(R.string.grp_behavior),
                getString(R.string.grp_behavior_sub), behaviorRows()));
        column.addView(group(2, R.drawable.ic_set_shield, getString(R.string.grp_data),
                getString(R.string.grp_data_sub), dataRows()));

        final int restore = savedScroll;
        scroll.post(() -> scroll.scrollTo(0, restore));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> savedScroll = y);
        Motion.attachPressFeedback(root);
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setBackgroundColor(p.surface);
        bar.setPadding(dp(4), 0, dp(16), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackground(null);
        back.setContentDescription(getString(R.string.close));
        ThemeApplier.tintIcon(back, p.foreground);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(4), dp(8), 0, dp(8));
        titles.addView(text(getString(R.string.settings), 20, p.foreground, true));
        titles.addView(text(getString(R.string.settings_sub), 12, p.subtle, false));
        bar.addView(titles);
        return bar;
    }

    private List<View> appearanceRows() {
        List<View> rows = new ArrayList<>();
        Watermark.Emblem wm = AppSettings.wmEmblem(this);
        String themeValue = AppSettings.currentTheme(this).name
                + (wm == Watermark.Emblem.NONE ? "" : " \u00B7 " + getString(wm.label));
        rows.add(row(R.drawable.ic_set_palette, getString(R.string.terminal_color), themeValue,
                chevron(), null, v -> startActivity(new Intent(this, ThemesActivity.class))));
        rows.add(row(R.drawable.ic_set_appearance, getString(R.string.appearance_title),
                getString(R.string.ap_row_value), chevron(), null,
                v -> startActivity(new Intent(this, AppearanceActivity.class))));

        final CursorStyle[] styles = {CursorStyle.BLOCK, CursorStyle.BAR, CursorStyle.UNDERLINE};
        CursorStyle current = AppSettings.cursorStyle(this);
        int selectedStyle = 0;
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == current) selectedStyle = i;
        }
        rows.add(row(R.drawable.ic_set_cursor, getString(R.string.cursor), null, null,
                chips(new int[]{R.string.cursor_block, R.string.cursor_bar, R.string.cursor_underline},
                        selectedStyle, i -> {
                            AppSettings.prefs(this).edit()
                                    .putString(AppSettings.KEY_CURSOR_STYLE, styles[i].name())
                                    .apply();
                            render();
                        }), null));
        return rows;
    }

    private List<View> behaviorRows() {
        List<View> rows = new ArrayList<>();
        rows.add(row(R.drawable.ic_set_cursor, getString(R.string.blink_cursor), getString(R.string.blink_help),
                toggle(AppSettings.cursorBlink(this), AppSettings.KEY_CURSOR_BLINK), null, null));
        rows.add(row(R.drawable.ic_set_keyboard, getString(R.string.extra_keys), null,
                toggle(AppSettings.extraKeys(this), AppSettings.KEY_EXTRA_KEYS), null, null));
        rows.add(row(R.drawable.ic_set_bell, getString(R.string.visual_bell), null,
                toggle(AppSettings.visualBell(this), AppSettings.KEY_BELL), null, null));

        Distro current = AppSettings.defaultDistro(this);
        rows.add(row(R.drawable.ic_set_terminal, getString(R.string.default_terminal),
                getString(R.string.default_terminal_help), null,
                chips(new int[]{R.string.session_type_debian, R.string.session_type_ubuntu},
                        current == Distro.UBUNTU ? 1 : 0, i -> {
                            AppSettings.setDefaultDistro(this, i == 1 ? Distro.UBUNTU : Distro.DEBIAN);
                            render();
                        }), null));
        return rows;
    }

    private List<View> dataRows() {
        List<View> rows = new ArrayList<>();
        rows.add(row(R.drawable.ic_set_storage, getString(R.string.repos_title),
                getString(R.string.repos_row_help), chevron(), null,
                v -> startActivity(new Intent(this, RepositoriesActivity.class))));

        TextView versionText = text(getString(R.string.app_version), 13, p.subtle, false);
        versionText.setTypeface(Typeface.MONOSPACE);
        rows.add(row(R.drawable.ic_set_info, getString(R.string.info_version), null, versionText, null, null));

        rows.add(row(R.drawable.ic_set_link, getString(R.string.info_github),
                getString(R.string.info_github_handle), null, null, v -> openLink(GITHUB_URL)));
        return rows;
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private View group(int index, int icon, String title, String subtitle, List<View> rows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Shapes.rect(this, p.surface, Shapes.LARGE, 0, 0));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(index == 0 ? 8 : 12);
        card.setLayoutParams(cp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        for (View r : rows) {
            content.addView(divider());
            content.addView(r);
        }
        content.setVisibility(open[index] ? View.VISIBLE : View.GONE);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        ThemeApplier.tintIcon(arrow, p.subtle);
        arrow.setRotation(open[index] ? 180f : 0f);

        LinearLayout head = rowShell(icon, title, subtitle, arrow, true);
        head.setOnClickListener(v -> {
            open[index] = content.getVisibility() != View.VISIBLE;
            Expander.toggle(content, arrow, open[index]);
        });
        card.addView(head);
        card.addView(content);
        return card;
    }

    private LinearLayout rowShell(int icon, String title, String sub, View trailing, boolean big) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(big ? 16 : 12), dp(16), dp(big ? 16 : 12));
        top.setBackground(ripple());
        top.setClickable(true);

        FrameLayoutTile tile = new FrameLayoutTile(this, icon);
        top.addView(tile, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, big ? 18 : 16, p.foreground, false));
        if (sub != null) {
            TextView s = text(sub, 13, p.subtle, false);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(16));
        top.addView(texts, tp);

        if (trailing != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(12));
            top.addView(trailing, lp);
        }
        return top;
    }

    private View row(int icon, String title, String sub, View trailing, View extra, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = rowShell(icon, title, sub, trailing, false);
        if (click != null) {
            top.setOnClickListener(click);
        } else {
            top.setBackground(null);
            top.setClickable(false);
        }
        box.addView(top);
        if (extra != null) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(dp(80), 0, dp(16), dp(14));
            box.addView(extra, ep);
        }
        return box;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(p.border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart(dp(80));
        lp.setMarginEnd(dp(16));
        d.setLayoutParams(lp);
        return d;
    }

    private View chevron() {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.ic_chevron);
        v.setRotation(-90f);
        ThemeApplier.tintIcon(v, p.subtle);
        return v;
    }

    private View toggle(boolean checked, String key) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setChecked(checked);
        ThemeApplier.styleSwitch(s, p);
        s.setOnCheckedChangeListener((b, on) -> AppSettings.prefs(this).edit().putBoolean(key, on).apply());
        return s;
    }

    private View chips(int[] labels, int selected, java.util.function.IntConsumer onSelect) {
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(getString(labels[i]), 13, p.foreground, true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setClickable(true);
            chip.setSelected(i == selected);
            ThemeApplier.styleChip(chip, p);
            chip.setOnClickListener(v -> {
                Motion.haptic(v);
                onSelect.accept(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(i == 0 ? 0 : dp(4), dp(4), i == labels.length - 1 ? 0 : dp(4), 0);
            rowView.addView(chip, lp);
        }
        return rowView;
    }

    private final class FrameLayoutTile extends android.widget.FrameLayout {
        FrameLayoutTile(android.content.Context context, int icon) {
            super(context);
            setBackground(Shapes.rect(context, p.surfaceHighest, Shapes.MEDIUM, 0, 0));
            ImageView image = new ImageView(context);
            image.setImageResource(icon);
            ThemeApplier.tintIcon(image, p.primary);
            addView(image, new LayoutParams(dp(24), dp(24), Gravity.CENTER));
        }
    }

    private android.graphics.drawable.Drawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(ThemePalette.withAlpha(p.primary, 40)), null,
                new ColorDrawable(Color.WHITE));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
