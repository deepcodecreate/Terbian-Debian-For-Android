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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.terbian.debian.Distro;
import com.terbian.prefs.AppSettings;
import com.terbian.ui.BaseActivity;
import com.terbian.ui.ThemeApplier;
import com.terbian.R;
import com.terbian.ui.ThemePalette;

public class TerbianGuiActivity extends BaseActivity implements TerbianDesktopManager.Ui, VncSession.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView statusRootfs;
    private TextView statusDesktop;
    private TextView statusServer;
    private TextView statusConnection;
    private TextView statusDetail;
    private TextView guiLog;
    private android.widget.ScrollView guiLogScroll;
    private com.terbian.ui.ExpressiveLoadingView progress;
    private MaterialButton installButton;
    private MaterialButton startButton;
    private MaterialButton connectButton;
    private MaterialButton disconnectButton;
    private ImageButton powerButton;
    private View chipDebian;
    private View chipUbuntu;
    private Distro distro = Distro.DEBIAN;
    private ThemePalette palette;
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            manager.refreshAsync(TerbianGuiActivity.this, distro);
            main.postDelayed(this, 3000);
        }
    };
    private DesktopStatus lastStatus;
    private TerbianDesktopManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terbian_gui);
        manager = TerbianDesktopManager.get();
        statusRootfs = findViewById(R.id.statusRootfs);
        statusDesktop = findViewById(R.id.statusDesktop);
        statusServer = findViewById(R.id.statusServer);
        statusConnection = findViewById(R.id.statusConnection);
        statusDetail = findViewById(R.id.statusDetail);
        guiLog = findViewById(R.id.guiLog);
        guiLogScroll = findViewById(R.id.guiLogScroll);
        progress = findViewById(R.id.guiProgress);
        installButton = findViewById(R.id.btnInstall);
        startButton = findViewById(R.id.btnStart);
        connectButton = findViewById(R.id.btnConnect);
        disconnectButton = findViewById(R.id.btnDisconnect);
        powerButton = findViewById(R.id.guiPower);
        chipDebian = findViewById(R.id.guiDistroDebian);
        chipUbuntu = findViewById(R.id.guiDistroUbuntu);
        distro = Distro.fromId(GuiSettings.prefs(this).getString("gui_distro", AppSettings.defaultDistro(this).id));
        chipDebian.setOnClickListener(v -> selectDistro(Distro.DEBIAN));
        chipUbuntu.setOnClickListener(v -> selectDistro(Distro.UBUNTU));

        findViewById(R.id.guiBack).setOnClickListener(v -> finish());
        powerButton.setOnClickListener(v -> confirmStop());
        installButton.setOnClickListener(v -> confirmInstall());
        startButton.setOnClickListener(v -> {
            manager.startInPageAsync(this, distro);
            renderBusy();
        });
        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> VncSession.disconnectAll());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            manager.clearLog();
            guiLog.setText("");
        });
        findViewById(R.id.btnGuiSettings).setOnClickListener(v ->
                startActivity(new Intent(this, TerbianGuiSettingsActivity.class)));

        palette = GuiTheme.apply(this);
        refreshChips();
        guiLog.setText(manager.logText());
        scrollLogToEnd();
        renderConnection();
        renderBusy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        manager.setUi(this);
        VncSession.addGlobalListener(this);
        manager.refreshAsync(this, distro);
        main.postDelayed(poll, 3000);
        renderConnection();
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(poll);
        manager.clearUi(this);
        VncSession.removeGlobalListener(this);
        super.onStop();
    }

    @Override
    public void onLog(String line) {
        main.post(() -> {
            if (isDestroyed()) return;

            View content = guiLogScroll.getChildAt(0);
            boolean atBottom = content == null
                    || content.getBottom() - (guiLogScroll.getHeight() + guiLogScroll.getScrollY()) < dp(24);
            guiLog.append(line);
            if (guiLog.length() > 80_000) {
                guiLog.setText(guiLog.getText().subSequence(guiLog.length() - 60_000, guiLog.length()));
            }
            if (atBottom) scrollLogToEnd();
        });
    }

    private void scrollLogToEnd() {
        guiLogScroll.post(() -> guiLogScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onStatus(DesktopStatus status) {
        main.post(() -> {
            if (isDestroyed()) return;
            applyStatus(status);
            renderConnection();
            renderBusy();
        });
    }

    @Override
    public void onComplete() {
        main.post(() -> {
            if (isDestroyed()) return;
            renderBusy();
            manager.refreshAsync(this, distro);
        });
    }

    @Override
    public void onError(String message) {
        main.post(() -> {
            if (isDestroyed()) return;
            renderBusy();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            manager.refreshAsync(this, distro);
        });
    }

    @Override
    public void onWarning(String message) {
        main.post(() -> {
            if (isDestroyed()) return;
            renderBusy();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gui_low_ram_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            manager.refreshAsync(this, distro);
        });
    }

    @Override
    public void onPhase(VncSession.Phase phase, String detail) {
        if (isDestroyed()) return;
        renderConnection();
    }

    @Override
    public void onFrame() {
    }

    @Override
    public void onBell() {
    }

    @Override
    public void onClipboard(String text) {
    }

    private void confirmInstall() {
        if (lastStatus != null && !lastStatus.rootfsInstalled) {
            Toast.makeText(this, R.string.gui_need_debian, Toast.LENGTH_LONG).show();
            return;
        }
        EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint(R.string.gui_password_hint);
        password.setTextColor(palette != null ? palette.foreground : getColor(R.color.fg));
        password.setHintTextColor(palette != null ? palette.subtle : getColor(R.color.subtle));
        CheckBox save = new CheckBox(this);
        save.setText(R.string.gui_save_password);
        save.setTextColor(palette != null ? palette.foreground : getColor(R.color.fg));
        try {
            String existing = PasswordStore.load(this);
            if (existing != null) {
                password.setText(existing);
                save.setChecked(true);
            }
        } catch (Exception ignored) {
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        box.addView(password);
        box.addView(save);
        new AlertDialog.Builder(this)
                .setTitle(R.string.gui_install_title)
                .setMessage(R.string.gui_install_message)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.gui_install, (d, w) -> {
                    String value = password.getText() == null ? "" : password.getText().toString();
                    if (!GuiSettings.passwordCharsOk(value)) {
                        Toast.makeText(this, R.string.gui_password_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    manager.installAsync(this, distro, value, save.isChecked());
                    renderBusy();
                })
                .show();
    }

    private void confirmStop() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.gui_stop)
                .setMessage(R.string.gui_stop_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.gui_stop, (d, w) -> {
                    manager.stopAsync(this, distro);
                    renderBusy();
                })
                .show();
    }

    private void openViewer(boolean connect, int port) {
        Intent intent = new Intent(this, VncViewerActivity.class);
        intent.putExtra(VncViewerActivity.EXTRA_CONNECT, connect);
        if (port > 0) {
            intent.putExtra(VncViewerActivity.EXTRA_PORT, port);
        }
        startActivity(intent);
    }

    private void connect() {
        if (TerbianDesktopManager.customTarget(GuiSettings.load(this))) {
            openViewer(true, -1);
            return;
        }
        connectButton.setEnabled(false);
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            List<DesktopRegistry.Instance> running = DesktopRegistry.running(app);
            main.post(() -> {
                if (isDestroyed()) return;
                renderBusy();
                chooseDesktop(running);
            });
        }, "Terbian-GUI-Connect").start();
    }

    private void chooseDesktop(List<DesktopRegistry.Instance> running) {
        if (running.isEmpty()) {
            Toast.makeText(this, R.string.gui_no_desktop, Toast.LENGTH_LONG).show();
            return;
        }
        if (running.size() == 1) {
            openViewer(true, running.get(0).port());
            return;
        }
        final List<DesktopRegistry.Instance> options = new ArrayList<>(running);
        java.util.Collections.sort(options, (x, y) -> x.display - y.display);
        String[] labels = new String[options.size()];
        for (int k = 0; k < labels.length; k++) {
            DesktopRegistry.Instance i = options.get(k);
            boolean connected = VncSession.forPort(i.port()).isConnected();
            labels[k] = i.label() + (connected ? "  \u2022 " + getString(R.string.gui_connected_mark) : "");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.gui_choose_desktop)
                .setItems(labels, (d, which) -> openViewer(true, options.get(which).port()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void selectDistro(Distro next) {
        if (next == distro) return;
        distro = next;
        GuiSettings.prefs(this).edit().putString("gui_distro", next.id).apply();
        lastStatus = null;
        refreshChips();
        statusRootfs.setText(getString(R.string.gui_rootfs, distro.label, getString(R.string.gui_missing)));
        manager.refreshAsync(this, distro);
        renderBusy();
    }

    private void refreshChips() {
        chipDebian.setSelected(distro == Distro.DEBIAN);
        chipUbuntu.setSelected(distro == Distro.UBUNTU);
        if (palette != null) {
            ThemeApplier.styleChip(chipDebian, palette);
            ThemeApplier.styleChip(chipUbuntu, palette);
        }
    }

    private void renderBusy() {
        boolean busy = manager.isBusy();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        installButton.setEnabled(!busy);
        boolean running = lastStatus != null && (lastStatus.serverRunning || lastStatus.supervisorAlive);
        boolean canStart = !busy;
        startButton.setEnabled(canStart);
        startButton.setAlpha(canStart ? 1f : 0.45f);
        powerButton.setEnabled(!busy);
        powerButton.setAlpha(busy ? 0.4f : 1f);
        connectButton.setEnabled(!busy);
    }

    private void renderConnection() {
        VncSession session = VncSession.get();
        int live = 0;
        boolean connecting = false;
        StringBuilder ports = new StringBuilder();
        for (VncSession each : VncSession.all()) {
            if (each.isConnected()) {
                live++;
                if (ports.length() > 0) ports.append(", ");
                ports.append(':').append(each.port() - 5900);
            } else if (each.phase() == VncSession.Phase.CONNECTING) {
                connecting = true;
            }
        }
        statusConnection.setText(getString(R.string.gui_connection,
                live > 1 ? getString(R.string.gui_viewers_connected, live, ports.toString()) : session.detail()));
        disconnectButton.setVisibility(live > 0 || connecting ? View.VISIBLE : View.GONE);
        applyStatus(lastStatus);
    }

    private void applyStatus(DesktopStatus status) {
        if (status == null) {
            return;
        }
        lastStatus = status;
        statusRootfs.setText(getString(R.string.gui_rootfs, distro.label,
                status.rootfsInstalled ? getString(R.string.gui_ready) : getString(R.string.gui_missing)));
        statusDesktop.setText(getString(R.string.gui_desktop,
                status.desktopInstalled ? getString(R.string.gui_installed) : getString(R.string.gui_not_installed)));
        statusServer.setText(getString(R.string.gui_server,
                status.serverRunning ? getString(R.string.gui_running) : getString(R.string.gui_stopped)));
        statusDetail.setText(status.detail);
        color(statusRootfs, status.rootfsInstalled);
        color(statusDesktop, status.desktopInstalled);
        color(statusServer, status.serverRunning);

        installButton.setVisibility(status.desktopInstalled ? View.GONE : View.VISIBLE);
        startButton.setVisibility(status.desktopInstalled ? View.VISIBLE : View.GONE);
        renderBusy();
        boolean running = status.serverRunning || status.supervisorAlive;

        powerButton.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private void color(TextView view, boolean ok) {
        if (palette != null && !palette.dark) {
            view.setTextColor(ok ? 0xFF2E7D32 : 0xFF8F5F00);
            return;
        }
        view.setTextColor(getColor(ok ? R.color.status_ok : R.color.status_warn));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
