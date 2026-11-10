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

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.terbian.R;
import com.terbian.ui.BaseActivity;
import com.terbian.ui.Shapes;
import com.terbian.ui.ThemeApplier;
import com.terbian.ui.ThemePalette;

public class TerbianGuiSettingsActivity extends BaseActivity {
    private ThemePalette p;
    private EditText hostField;
    private EditText portField;
    private EditText passwordField;
    private EditText timeoutField;
    private EditText geometryField;
    private MaterialSwitch savePassword;
    private MaterialSwitch autoConnect;
    private MaterialSwitch fitScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_gui_settings);
        p = GuiTheme.apply(this);

        GuiSettings settings = GuiSettings.load(this);
        hostField = field(settings.host, "127.0.0.1", android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        portField = field(String.valueOf(settings.port), "5901", android.text.InputType.TYPE_CLASS_NUMBER);
        passwordField = field("", getString(R.string.gui_password_hint), android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        timeoutField = field(String.valueOf(settings.timeoutMs), "8000", android.text.InputType.TYPE_CLASS_NUMBER);
        geometryField = field(settings.geometry, "1280x720", android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        savePassword = toggle(settings.savePassword);
        autoConnect = toggle(settings.autoConnect);
        fitScreen = toggle(!GuiSettings.SCALE_ORIGINAL.equals(settings.scaleMode));

        if (settings.savePassword) {
            try {
                String saved = PasswordStore.load(this);
                if (saved != null) {
                    passwordField.setText(saved);
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.gui_password_load_failed, Toast.LENGTH_LONG).show();
            }
        }

        LinearLayout column = findViewById(R.id.guiSettingsColumn);
        column.addView(card(0,
                row(R.drawable.ic_set_link, getString(R.string.gui_host), null, null, hostField),
                row(R.drawable.ic_set_tune, getString(R.string.gui_port), null, null, portField),
                row(R.drawable.ic_set_shield, getString(R.string.gui_password), null, null, passwordField)));
        column.addView(card(12,
                row(R.drawable.ic_refresh, getString(R.string.gui_timeout), null, null, timeoutField),
                row(R.drawable.ic_desktop, getString(R.string.gui_geometry), null, null, geometryField)));
        column.addView(card(12,
                row(R.drawable.ic_set_storage, getString(R.string.gui_save_password), null, savePassword, null),
                row(R.drawable.ic_play, getString(R.string.gui_auto_connect), null, autoConnect, null),
                row(R.drawable.ic_set_appearance, getString(R.string.gui_fit_default), null, fitScreen, null)));

        TextView help = text(getString(R.string.gui_settings_help), 12, p.subtle);
        help.setPadding(dp(8), dp(14), dp(8), 0);
        column.addView(help);

        findViewById(R.id.closeGuiSettings).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveGuiSettings).setOnClickListener(v -> save());
    }

    private EditText field(String value, String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        edit.setText(value);
        edit.setHint(hint);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        edit.setTextColor(p.foreground);
        edit.setHintTextColor(p.subtle);
        edit.setHighlightColor(ThemePalette.withAlpha(p.primary, 0x55));
        edit.setPadding(dp(16), 0, dp(16), 0);
        edit.setBackground(Shapes.rect(this, p.surfaceHigh, Shapes.MEDIUM, p.border, 1));
        return edit;
    }

    private MaterialSwitch toggle(boolean checked) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setChecked(checked);
        ThemeApplier.styleSwitch(s, p);
        return s;
    }

    private View card(int topMarginDp, View... rows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Shapes.rect(this, p.surface, Shapes.LARGE, 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        card.setLayoutParams(lp);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                card.addView(divider());
            }
            card.addView(rows[i]);
        }
        return card;
    }

    private View row(int icon, String title, String sub, MaterialSwitch trailing, EditText extra) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (trailing != null) {
            top.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(ThemePalette.withAlpha(p.primary, 40)), null,
                    new ColorDrawable(Color.WHITE)));
            top.setClickable(true);
            top.setOnClickListener(v -> trailing.toggle());
        }

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Shapes.rect(this, p.surfaceHighest, Shapes.MEDIUM, 0, 0));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        ThemeApplier.tintIcon(image, p.primary);
        tile.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        top.addView(tile, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, p.foreground));
        if (sub != null) {
            TextView s = text(sub, 13, p.subtle);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(16));
        top.addView(texts, tp);

        if (trailing != null) {
            trailing.setClickable(false);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.setMarginStart(dp(12));
            top.addView(trailing, sp);
        }
        box.addView(top);

        if (extra != null) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
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

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void save() {
        String host = GuiSettings.sanitizeHost(text(hostField));
        int port;
        int timeout;
        try {
            port = Integer.parseInt(text(portField));
            timeout = Integer.parseInt(text(timeoutField));
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.gui_settings_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (port < 1 || port > 65535 || timeout < 1000 || timeout > 120000) {
            Toast.makeText(this, R.string.gui_settings_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        String geometry = GuiSettings.sanitizeGeometry(text(geometryField));
        String password = text(passwordField);
        if (!password.isEmpty() && !GuiSettings.passwordCharsOk(password)) {
            Toast.makeText(this, R.string.gui_password_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        boolean saveSecret = savePassword.isChecked() && !password.isEmpty();
        try {
            if (saveSecret) {
                PasswordStore.save(this, password);
                GuiSettings.setSessionPassword(null);
            } else {
                PasswordStore.clear(this);
                GuiSettings.setSessionPassword(password.isEmpty() ? null : password);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.gui_password_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        new GuiSettings(
                host,
                port,
                timeout,
                fitScreen.isChecked() ? GuiSettings.SCALE_FIT : GuiSettings.SCALE_ORIGINAL,
                autoConnect.isChecked(),
                saveSecret,
                geometry
        ).save(this);
        Toast.makeText(this, R.string.gui_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
