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

package com.terbian.gui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.WindowCompat;

import com.terbian.R;
import com.terbian.debian.Distro;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.SessionType;
import com.terbian.ui.BaseActivity;
import com.terbian.ui.MainActivity;
import com.terbian.ui.ThemeApplier;
import com.terbian.ui.ThemePalette;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppsActivity extends BaseActivity {
    private final ExecutorService io = Executors.newFixedThreadPool(2);

    private ThemePalette p;
    private Distro distro;
    private List<LinuxApps.Entry> entries = new ArrayList<>();
    private String query = "";
    private int generation;

    private LinearLayout grid;
    private TextView emptyView;
    private TextView chipDebian;
    private TextView chipUbuntu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        p = ThemePalette.of(this);
        ThemeApplier.styleWindow(this, p);
        distro = AppSettings.defaultDistro(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ThemeApplier.backdrop(root, p);
        root.setFitsSystemWindows(true);
        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildBody(content);
        setContentView(root);
        refreshChips();
        reload();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(p.surface);
        header.setPadding(dp(4), 0, dp(4), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(android.R.color.transparent);
        back.setContentDescription(getString(R.string.close));
        ThemeApplier.tintIcon(back, p.foreground);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(R.string.apps_title);
        title.setTextColor(p.foreground);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText(R.string.apps_sub);
        sub.setTextColor(p.subtle);
        sub.setTextSize(11);
        titles.addView(title);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton refresh = new ImageButton(this);
        refresh.setImageResource(R.drawable.ic_refresh);
        refresh.setBackgroundResource(android.R.color.transparent);
        refresh.setContentDescription(getString(R.string.apps_refresh));
        ThemeApplier.tintIcon(refresh, p.foreground);
        refresh.setOnClickListener(v -> reload());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return header;
    }

    private void buildBody(LinearLayout content) {
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipDebian = chip(Distro.DEBIAN);
        chipUbuntu = chip(Distro.UBUNTU);
        chips.addView(chipDebian, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(44), 1f);
        up.setMarginStart(dp(8));
        chips.addView(chipUbuntu, up);
        content.addView(chips);

        EditText search = new EditText(this);
        search.setHint(R.string.apps_search);
        search.setSingleLine(true);
        search.setTextColor(p.foreground);
        search.setHintTextColor(p.subtle);
        search.setTextSize(14);
        search.setPadding(dp(16), 0, dp(16), 0);
        ThemeApplier.styleRow(search, p);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                renderGrid();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        sp.topMargin = dp(10);
        content.addView(search, sp);

        emptyView = new TextView(this);
        emptyView.setTextColor(p.muted);
        emptyView.setTextSize(13);
        emptyView.setPadding(dp(4), dp(16), dp(4), dp(8));
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(12);
        content.addView(grid, gp);
    }

    private TextView chip(Distro target) {
        TextView chip = new TextView(this);
        chip.setText(target.label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setClickable(true);
        chip.setFocusable(true);
        ThemeApplier.styleChip(chip, p);
        chip.setOnClickListener(v -> {
            if (distro == target) {
                return;
            }
            distro = target;
            refreshChips();
            reload();
        });
        return chip;
    }

    private void refreshChips() {
        chipDebian.setSelected(distro == Distro.DEBIAN);
        chipUbuntu.setSelected(distro == Distro.UBUNTU);
    }

    private void reload() {
        final int token = ++generation;
        final Distro target = distro;
        final File rootfs = target.rootfsDir(this);
        io.execute(() -> {
            boolean installed = new File(rootfs, "etc/os-release").isFile();
            List<LinuxApps.Entry> found = installed ? LinuxApps.scan(rootfs) : new ArrayList<>();
            runOnUiThread(() -> {
                if (isDestroyed() || token != generation) {
                    return;
                }
                entries = found;
                emptyView.setText(installed ? getString(R.string.apps_empty)
                        : getString(R.string.apps_not_installed, target.label));
                renderGrid();
            });
        });
    }

    private void renderGrid() {
        grid.removeAllViews();
        List<LinuxApps.Entry> shown = new ArrayList<>();
        for (LinuxApps.Entry entry : entries) {
            if (query.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(entry);
            }
        }
        if (!entries.isEmpty() && shown.isEmpty()) {
            emptyView.setText(R.string.apps_no_match);
        }
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int columns = Math.max(3, (widthDp - 32) / 100);
        File rootfs = distro.rootfsDir(this);
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(tile(shown.get(i), rootfs), lp);
        }
        if (row != null) {
            int remainder = shown.size() % columns;
            if (remainder != 0) {
                for (int k = remainder; k < columns; k++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                    lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                    row.addView(new View(this), lp);
                }
            }
        }
    }

    private View tile(LinuxApps.Entry entry, File rootfs) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(4), dp(12), dp(4), dp(10));
        ThemeApplier.styleRow(tile, p);
        tile.setClickable(true);
        tile.setFocusable(true);

        FrameLayout box = new FrameLayout(this);
        TextView letter = new TextView(this);
        String name = entry.name.trim();
        letter.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
        letter.setGravity(Gravity.CENTER);
        letter.setTextSize(22);
        letter.setTypeface(Typeface.DEFAULT_BOLD);
        letter.setTextColor(p.onPrimaryContainer);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(p.primaryContainer);
        letter.setBackground(circle);
        box.addView(letter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setVisibility(View.GONE);
        box.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.addView(box, new LinearLayout.LayoutParams(dp(52), dp(52)));

        io.execute(() -> {
            Bitmap bitmap = LinuxApps.loadIcon(rootfs, entry, dp(96));
            if (bitmap != null) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    icon.setImageBitmap(bitmap);
                    icon.setVisibility(View.VISIBLE);
                    letter.setVisibility(View.GONE);
                });
            }
        });

        TextView label = new TextView(this);
        label.setText(entry.name);
        label.setTextColor(p.foreground);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        tile.addView(label, lp);

        tile.setOnClickListener(v -> launch(entry));
        return tile;
    }

    private void launch(LinuxApps.Entry entry) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_RUN_COMMAND, entry.command);
        intent.putExtra(MainActivity.EXTRA_RUN_TITLE, entry.name);
        intent.putExtra(MainActivity.EXTRA_RUN_TYPE, SessionType.forDistro(distro).name());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
