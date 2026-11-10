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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.terbian.R;
import com.terbian.debian.ArchitectureDetector;
import com.terbian.debian.AptSources;
import com.terbian.debian.Distro;
import com.terbian.debian.MirrorList;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.SessionType;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RepositoriesActivity extends BaseActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ThemePalette p;
    private AptSources sources;
    private String browseDistro = AptSources.DEBIAN;

    private LinearLayout content;
    private TextView infoText;
    private TextView chipDebian;
    private TextView chipUbuntu;
    private EditText mirrorField;
    private LinearLayout listHost;
    private MaterialButton loadButton;
    private ExpressiveLoadingView loading;
    private TextView armNote;
    private TextView distroNote;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        p = ThemePalette.of(this);
        ThemeApplier.styleWindow(this, p);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ThemeApplier.backdrop(root, p);
        root.setFitsSystemWindows(true);
        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        browseDistro = AppSettings.defaultDistro(this).id;
        loadSources();
        buildBody();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void loadSources() {
        File rootfs = Distro.fromId(browseDistro).rootfsDir(this);
        try {
            sources = new File(rootfs, "etc/os-release").isFile() ? AptSources.open(rootfs) : null;
        } catch (Exception e) {
            sources = null;
        }
    }

    private String distroLabel() {
        return Distro.fromId(browseDistro).label;
    }

    private String notInstalledText() {
        return getString(R.string.repos_not_installed, distroLabel());
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(p.surface);
        header.setPadding(dp(4), 0, dp(16), 0);

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
        title.setText(R.string.repos_title);
        title.setTextColor(p.foreground);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText(R.string.repos_sub);
        sub.setTextColor(p.subtle);
        sub.setTextSize(11);
        titles.addView(title);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return header;
    }

    private void buildBody() {
        LinearLayout card = card();
        infoText = new TextView(this);
        infoText.setTextColor(p.muted);
        infoText.setTextSize(13);
        infoText.setTextIsSelectable(true);
        card.addView(infoText);
        content.addView(card);
        refreshInfo();

        content.addView(label(getString(R.string.repos_manual), 20));
        mirrorField = new EditText(this);
        mirrorField.setHint(R.string.repos_manual_hint);
        mirrorField.setSingleLine(true);
        mirrorField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        mirrorField.setTextColor(p.foreground);
        mirrorField.setHintTextColor(p.subtle);
        mirrorField.setTextSize(14);
        mirrorField.setPadding(dp(16), 0, dp(16), 0);
        ThemeApplier.styleRow(mirrorField, p);
        content.addView(mirrorField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        MaterialButton apply = button(getString(R.string.repos_apply), true);
        apply.setOnClickListener(v -> applyMirror(mirrorField.getText().toString()));
        MaterialButton nano = button(getString(R.string.repos_nano), false);
        nano.setOnClickListener(v -> editWithNano());
        buttons.addView(apply, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(48), 1f);
        np.setMarginStart(dp(8));
        buttons.addView(nano, np);
        content.addView(buttons, bp);

        MaterialButton reset = button(getString(R.string.repos_reset), false);
        reset.setOnClickListener(v -> confirmReset());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rp.topMargin = dp(8);
        content.addView(reset, rp);

        content.addView(label(getString(R.string.repos_online), 24));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipDebian = chip("Debian", AptSources.DEBIAN);
        chipUbuntu = chip("Ubuntu", AptSources.UBUNTU);
        chips.addView(chipDebian, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(44), 1f);
        up.setMarginStart(dp(8));
        chips.addView(chipUbuntu, up);
        content.addView(chips);
        distroNote = new TextView(this);
        distroNote.setTextColor(p.primary);
        distroNote.setTextSize(12);
        distroNote.setPadding(0, dp(8), 0, 0);
        content.addView(distroNote);
        refreshChips();

        loadButton = button(getString(R.string.repos_load), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(8);
        loadButton.setOnClickListener(v -> loadMirrors());
        content.addView(loadButton, lp);

        loading = new ExpressiveLoadingView(this);
        loading.setColor(p.primary);
        loading.setContainerColor(p.primaryContainer);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(48), dp(48));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = dp(12);
        content.addView(loading, pp);

        armNote = new TextView(this);
        armNote.setText(R.string.repos_arm_note);
        armNote.setTextColor(p.subtle);
        armNote.setTextSize(12);
        armNote.setPadding(0, dp(8), 0, 0);
        content.addView(armNote);
        refreshArmNote();

        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(listHost);
    }

    private void refreshInfo() {
        if (sources == null) {
            infoText.setText(notInstalledText());
            return;
        }
        String current = sources.currentMirror();
        infoText.setText(sources.distro() + " " + sources.codename() + "\n"
                + getString(R.string.repos_current) + ": " + sources.guestFile()
                + (current.isEmpty() ? "" : "\n" + current));
    }

    private void refreshChips() {
        chipDebian.setSelected(AptSources.DEBIAN.equals(browseDistro));
        chipUbuntu.setSelected(AptSources.UBUNTU.equals(browseDistro));
        ThemeApplier.styleChip(chipDebian, p);
        ThemeApplier.styleChip(chipUbuntu, p);
        if (mirrorField != null) {
            mirrorField.setHint(sources != null ? sources.defaultMirror(
                    AptSources.UBUNTU.equals(browseDistro) && isArm())
                    : (AptSources.UBUNTU.equals(browseDistro)
                    ? "https://archive.ubuntu.com/ubuntu" : "https://deb.debian.org/debian"));
        }
        if (distroNote != null) {
            distroNote.setVisibility(sources == null ? View.VISIBLE : View.GONE);
            distroNote.setText(getString(R.string.repos_install_hint, distroLabel()));
        }
    }

    private static boolean isArm() {
        return ArchitectureDetector.isArm64() || ArchitectureDetector.isArm32();
    }

    private void refreshArmNote() {
        boolean arm = AptSources.UBUNTU.equals(browseDistro) && isArm();
        armNote.setVisibility(arm ? View.VISIBLE : View.GONE);
    }

    private void loadMirrors() {
        final String distro = browseDistro;
        listHost.removeAllViews();
        loading.setVisibility(View.VISIBLE);
        loadButton.setEnabled(false);
        final TextView status = new TextView(this);
        statusView = status;
        status.setText(R.string.repos_loading);
        status.setTextColor(p.subtle);
        status.setTextSize(13);
        status.setPadding(0, dp(8), 0, 0);
        listHost.addView(status);
        final ExpressiveProgressView wave = new ExpressiveProgressView(this);
        wave.setVisibility(View.GONE);
        listHost.addView(wave, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        io.execute(() -> {
            Map<String, List<String>> result = null;
            String error = null;
            try {
                result = MirrorList.fetch(distro);
                if (AptSources.UBUNTU.equals(distro) && isArm()) {
                    runOnUiThread(() -> {
                        status.setText(getString(R.string.repos_checking, 0, 0));
                        wave.setColors(p.primary, p.surfaceHighest);
                        wave.setProgress(0f);
                        wave.setVisibility(View.VISIBLE);
                    });
                    String codename = sources != null ? sources.codename() : "";
                    result = MirrorList.onlyArmCompatible(result, codename, (done, total) ->
                            runOnUiThread(() -> {
                                status.setText(getString(R.string.repos_checking, done, total));
                                wave.setProgress(total == 0 ? 0f : done / (float) total);
                            }));
                    java.util.LinkedHashMap<String, List<String>> withOfficial = new java.util.LinkedHashMap<>();
                    withOfficial.put(getString(R.string.repos_official),
                            java.util.Collections.singletonList("http://ports.ubuntu.com/ubuntu-ports"));
                    withOfficial.putAll(result);
                    result = withOfficial;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final Map<String, List<String>> data = result;
            final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading.setVisibility(View.GONE);
                loadButton.setEnabled(true);
                listHost.removeAllViews();
                if (failure != null) {
                    listHost.addView(note(getString(R.string.repos_load_failed, failure)));
                } else if (data == null || data.isEmpty()) {
                    listHost.addView(note(getString(R.string.repos_empty)));
                } else {
                    showCountries(distro, data);
                }
            });
        });
    }

    private void showCountries(String distro, Map<String, List<String>> data) {
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            final String country = entry.getKey();
            final List<String> urls = entry.getValue();

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(16), 0, dp(12), 0);
            header.setClickable(true);
            header.setFocusable(true);
            ThemeApplier.styleRow(header, p);

            TextView name = new TextView(this);
            name.setText(country);
            name.setTextColor(p.foreground);
            name.setTextSize(14);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView count = new TextView(this);
            count.setText(getString(R.string.repos_mirror_count, urls.size()));
            count.setTextColor(p.subtle);
            count.setTextSize(12);
            count.setPadding(0, 0, dp(8), 0);
            header.addView(count);

            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron);
            ThemeApplier.tintIcon(arrow, p.subtle);
            header.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            hp.topMargin = dp(8);
            listHost.addView(header, hp);

            LinearLayout items = new LinearLayout(this);
            items.setOrientation(LinearLayout.VERTICAL);
            items.setVisibility(View.GONE);
            listHost.addView(items);

            final boolean[] filled = {false};
            header.setOnClickListener(v -> {
                boolean open = items.getVisibility() != View.VISIBLE;
                if (open && !filled[0]) {
                    filled[0] = true;
                    for (String url : urls) {
                        items.addView(mirrorRow(distro, url));
                    }
                }
                Expander.toggle(items, arrow, open);
            });
        }
    }

    private View mirrorRow(String distro, String url) {
        TextView row = new TextView(this);
        row.setText(url);
        row.setTextColor(p.muted);
        row.setTextSize(13);
        row.setTypeface(Typeface.MONOSPACE);
        row.setMinHeight(dp(44));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(6), dp(12), dp(6));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            mirrorField.setText(url);
            if (sources == null) {
                Toast.makeText(this, notInstalledText(), Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.repos_apply)
                    .setMessage(url)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.repos_apply, (d, w) -> applyMirror(url))
                    .show();
        });
        return row;
    }

    private void applyMirror(String raw) {
        final String url = raw == null ? "" : raw.trim();
        if (sources == null) {
            Toast.makeText(this, notInstalledText(), Toast.LENGTH_LONG).show();
            return;
        }
        if (!AptSources.validUrl(url)) {
            Toast.makeText(this, R.string.repos_bad_url, Toast.LENGTH_LONG).show();
            return;
        }
        String installed = sources.distro();
        String other = AptSources.UBUNTU.equals(installed) ? AptSources.DEBIAN : AptSources.UBUNTU;
        String lower = url.toLowerCase();
        if (lower.contains(other) && !lower.contains(installed)) {
            Toast.makeText(this, getString(R.string.repos_wrong_distro, Distro.fromId(installed).label,
                    Distro.fromId(other).label), Toast.LENGTH_LONG).show();
            return;
        }
        final boolean arm = AptSources.UBUNTU.equals(installed)
                && (ArchitectureDetector.isArm64() || ArchitectureDetector.isArm32());
        io.execute(() -> {
            String backup = null;
            String error = null;
            try {
                backup = sources.apply(url, arm);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String b = backup;
            final String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (err != null) {
                    Toast.makeText(this, getString(R.string.repos_failed, err), Toast.LENGTH_LONG).show();
                    return;
                }
                refreshInfo();
                Toast.makeText(this, getString(R.string.repos_applied, b), Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(this)
                        .setTitle(R.string.repos_update_title)
                        .setMessage(R.string.repos_update_message)
                        .setNegativeButton(R.string.repos_later, null)
                        .setPositiveButton(R.string.repos_update,
                                (d, w) -> runInTerminal("apt-get update", "apt update"))
                        .show();
            });
        });
    }

    private void confirmReset() {
        if (sources == null) {
            Toast.makeText(this, notInstalledText(), Toast.LENGTH_LONG).show();
            return;
        }
        final boolean arm = AptSources.UBUNTU.equals(sources.distro()) && isArm();
        final String def = sources.defaultMirror(arm);
        new AlertDialog.Builder(this)
                .setTitle(R.string.repos_reset_title)
                .setMessage(getString(R.string.repos_reset_message, def))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.repos_reset, (d, w) -> {
                    mirrorField.setText(def);
                    applyMirror(def);
                })
                .show();
    }

    private void editWithNano() {
        if (sources == null) {
            Toast.makeText(this, notInstalledText(), Toast.LENGTH_LONG).show();
            return;
        }
        String file = sources.guestFile();

        String lock = sources.guestDir() + "/." + sources.guestFileName() + ".swp";
        runInTerminal("rm -f '" + lock + "'; (command -v nano >/dev/null 2>&1 || apt-get install -y nano) && nano '"
                + file + "'", "nano");
    }

    private void runInTerminal(String command, String title) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_RUN_COMMAND, command);
        intent.putExtra(MainActivity.EXTRA_RUN_TITLE, title);

        intent.putExtra(MainActivity.EXTRA_RUN_TYPE,
                SessionType.forDistro(Distro.fromId(browseDistro)).name());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        ThemeApplier.styleRow(card, p);
        return card;
    }

    private TextView label(String text, int topDp) {
        TextView label = new TextView(this);
        label.setText(text.toUpperCase());
        label.setTextColor(p.subtle);
        label.setTextSize(11);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(8);
        label.setLayoutParams(lp);
        return label;
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(p.muted);
        view.setTextSize(13);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private TextView chip(String text, String distro) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(p.foreground);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            if (distro.equals(browseDistro)) return;
            browseDistro = distro;
            loadSources();
            listHost.removeAllViews();
            mirrorField.setText("");
            refreshInfo();
            refreshChips();
            refreshArmNote();
        });
        return chip;
    }

    private MaterialButton button(String text, boolean primary) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMaxLines(1);
        b.setCornerRadius(dp(12));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        if (primary) {
            b.setBackgroundTintList(ColorStateList.valueOf(p.primary));
            b.setTextColor(p.onPrimary);
            b.setStrokeWidth(0);
        } else {
            b.setBackgroundTintList(ColorStateList.valueOf(p.surface));
            b.setTextColor(p.foreground);
            b.setStrokeColor(ColorStateList.valueOf(p.border));
            b.setStrokeWidth(Math.max(1, dp(1)));
        }
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
