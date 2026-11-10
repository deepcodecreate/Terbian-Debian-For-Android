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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.terbian.R;
import com.terbian.ui.BaseActivity;
import com.terbian.vnc.KeySyms;

public class VncViewerActivity extends BaseActivity implements VncSession.Listener, VncInputController.Host {
    public static final String EXTRA_CONNECT = "connect";

    public static final String EXTRA_PORT = "port";

    private VncFramebufferView desktop;
    private VncInputController input;
    private VncSession session;
    private View toolbar;
    private View keyBar;
    private View showChrome;
    private View touchpadPanel;
    private TextView padToggle;
    private boolean padUserSet;
    private TextView viewerStatus;
    private TextView ctrlKey;
    private TextView altKey;
    private TextView shiftKey;
    private TextView rightKey;
    private TextView middleKey;
    private TextView dragKey;
    private TextView scrollKey;
    private com.terbian.ui.ThemePalette palette;
    private boolean fullscreen;
    private boolean frameQueued;
    private boolean keyboardVisible;

    private boolean switching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vnc_viewer);

        int wantedPort = getIntent().getIntExtra(EXTRA_PORT, -1);
        int sessionPort = wantedPort > 0 ? wantedPort : GuiSettings.load(this).port;
        session = VncSession.forPort(sessionPort);
        VncSession.setActive(sessionPort);
        desktop = findViewById(R.id.desktopView);
        toolbar = findViewById(R.id.viewerToolbar);
        keyBar = findViewById(R.id.keyBar);
        showChrome = findViewById(R.id.showChrome);
        touchpadPanel = findViewById(R.id.touchpadPanel);
        padToggle = findViewById(R.id.btnPad);
        viewerStatus = findViewById(R.id.viewerStatus);
        ctrlKey = findViewById(R.id.keyCtrl);
        altKey = findViewById(R.id.keyAlt);
        shiftKey = findViewById(R.id.keyShift);
        rightKey = findViewById(R.id.btnRight);
        middleKey = findViewById(R.id.btnMiddle);
        dragKey = findViewById(R.id.btnDrag);
        scrollKey = findViewById(R.id.btnScroll);
        input = new VncInputController(desktop, session);
        input.setHost(this);
        desktop.setInputController(input);

        palette = GuiTheme.apply(this);
        desktop.setPalette(palette);
        ((VncTouchpadView) findViewById(R.id.touchpad)).setPalette(palette);
        ((VncScrollStripView) findViewById(R.id.scrollStrip)).setPalette(palette);
        setupTouchpad();
        GuiSettings settings = GuiSettings.load(this);
        desktop.setFit(GuiSettings.SCALE_FIT.equals(settings.scaleMode));

        findViewById(R.id.btnHideChrome).setOnClickListener(v -> setChrome(false));
        showChrome.setOnClickListener(v -> setChrome(true));
        findViewById(R.id.btnKeyboard).setOnClickListener(v -> toggleKeyboard());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> desktop.zoomBy(1.25f, desktop.getWidth() / 2f, desktop.getHeight() / 2f));
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> desktop.zoomBy(0.8f, desktop.getWidth() / 2f, desktop.getHeight() / 2f));
        findViewById(R.id.btnFit).setOnClickListener(v -> desktop.setFit(true));
        findViewById(R.id.btnOneToOne).setOnClickListener(v -> desktop.setFit(false));
        findViewById(R.id.btnFullscreen).setOnClickListener(v -> setFullscreen(!fullscreen));
        findViewById(R.id.btnResize).setOnClickListener(v -> requestResize());
        findViewById(R.id.btnPaste).setOnClickListener(v -> pasteClipboard());
        findViewById(R.id.btnSwitch).setOnClickListener(v -> chooseDesktop());
        findViewById(R.id.btnViewerDisconnect).setOnClickListener(v -> {
            session.disconnect();
            finish();
        });
        rightKey.setOnClickListener(v -> input.setRightArmed(!input.rightArmed()));
        middleKey.setOnClickListener(v -> input.setMiddleArmed(!input.middleArmed()));
        dragKey.setOnClickListener(v -> input.setDragLock(!input.dragLock()));
        scrollKey.setOnClickListener(v -> input.setScrollMode(!input.scrollMode()));
        ctrlKey.setOnClickListener(v -> input.toggleCtrl());
        altKey.setOnClickListener(v -> input.toggleAlt());
        shiftKey.setOnClickListener(v -> input.toggleShift());
        findViewById(R.id.keyEsc).setOnClickListener(v -> input.tap(KeySyms.ESCAPE));
        findViewById(R.id.keyTab).setOnClickListener(v -> input.tap(KeySyms.TAB));
        findViewById(R.id.keyLeft).setOnClickListener(v -> input.tap(KeySyms.LEFT));
        findViewById(R.id.keyUp).setOnClickListener(v -> input.tap(KeySyms.UP));
        findViewById(R.id.keyDown).setOnClickListener(v -> input.tap(KeySyms.DOWN));
        findViewById(R.id.keyRight).setOnClickListener(v -> input.tap(KeySyms.RIGHT));
        findViewById(R.id.keyFn).setOnClickListener(this::showFunctionKeys);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreen) {
                    setFullscreen(false);
                    return;
                }
                input.releaseAll();
                session.disconnect();
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        boolean connect = getIntent().getBooleanExtra(EXTRA_CONNECT, false) || settings.autoConnect;
        if (connect && !session.isConnected() && session.phase() != VncSession.Phase.CONNECTING) {
            session.connect(this, wantedPort);
        }
        viewerStatus.setText(session.detail());
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        session.addListener(this);
        session.markAllDirty();
        if (session.framebuffer().width() > 0) {
            submitFrame();
        }
        viewerStatus.setText(session.detail());
    }

    @Override
    protected void onPause() {
        input.releaseAll();
        super.onPause();
    }

    @Override
    protected void onStop() {
        session.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        input.releaseAll();
        if (isFinishing() && !switching) {
            session.disconnect();
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (input.handleKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onPhase(VncSession.Phase phase, String detail) {
        if (isDestroyed()) return;
        viewerStatus.setText(detail);
        if (phase == VncSession.Phase.FAILED) {
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onFrame() {
        if (frameQueued || isDestroyed()) {
            return;
        }
        frameQueued = true;
        desktop.postOnAnimation(() -> {
            frameQueued = false;
            submitFrame();
        });
    }

    @Override
    public void onBell() {
    }

    @Override
    public void onClipboard(String text) {
        if (text == null || text.isEmpty() || isDestroyed()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terbian GUI", text));
        }
    }

    @Override
    public void onModifiersChanged() {
        ctrlKey.setSelected(input.ctrlSticky());
        altKey.setSelected(input.altSticky());
        shiftKey.setSelected(input.shiftSticky());
        rightKey.setSelected(input.rightArmed());
        middleKey.setSelected(input.middleArmed());
        dragKey.setSelected(input.dragLock());
        scrollKey.setSelected(input.scrollMode());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!padUserSet) {
            setPadVisible(newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE);
        }
    }

    private void setPadVisible(boolean visible) {
        touchpadPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        padToggle.setSelected(visible);
    }

    private void setupTouchpad() {
        VncTouchpadView pad = findViewById(R.id.touchpad);
        pad.setCallback(new VncTouchpadView.Callback() {
            @Override
            public void onPadMove(float dx, float dy) {
                input.padMove(dx, dy);
            }

            @Override
            public void onPadTap() {
                input.padClick(1);
            }

            @Override
            public void onPadTwoFingerTap() {
                input.padClick(4);
            }

            @Override
            public void onPadScroll(float dy) {
                input.padScroll(dy);
            }

            @Override
            public void onPadDragStart() {
                input.padDragStart();
            }

            @Override
            public void onPadDragEnd() {
                input.padDragEnd();
            }
        });
        VncScrollStripView strip = findViewById(R.id.scrollStrip);
        strip.setCallback(new VncScrollStripView.Callback() {
            @Override
            public void onStripScroll(float dy) {
                input.padScroll(dy);
            }

            @Override
            public void onStripNotch(boolean up) {
                input.padNotch(up);
            }
        });
        bindPadButton(findViewById(R.id.padLeft), 1);
        bindPadButton(findViewById(R.id.padRight), 4);
        padToggle.setOnClickListener(v -> {
            padUserSet = true;
            setPadVisible(touchpadPanel.getVisibility() != View.VISIBLE);
        });

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        setPadVisible(!landscape);
    }

    private void bindPadButton(View button, int mask) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setSelected(true);
                    input.padButton(mask, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setSelected(false);
                    input.padButton(mask, false);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void chooseDesktop() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            final java.util.List<DesktopRegistry.Instance> running = DesktopRegistry.running(app);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (running.size() < 2) {
                    Toast.makeText(this, R.string.gui_no_other_desktop, Toast.LENGTH_SHORT).show();
                    return;
                }
                java.util.Collections.sort(running, (x, y) -> x.display - y.display);
                String[] labels = new String[running.size()];
                for (int k = 0; k < labels.length; k++) {
                    DesktopRegistry.Instance i = running.get(k);
                    String mark = i.port() == session.port() ? "  \u25B6 " + getString(R.string.gui_current_mark)
                            : (VncSession.forPort(i.port()).isConnected()
                            ? "  \u2022 " + getString(R.string.gui_connected_mark) : "");
                    labels[k] = i.label() + mark;
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.gui_choose_desktop)
                        .setItems(labels, (d, which) -> {
                            DesktopRegistry.Instance chosen = running.get(which);
                            if (chosen.port() == session.port()) return;
                            input.releaseAll();
                            switching = true;
                            android.content.Intent intent = new android.content.Intent(this, VncViewerActivity.class);
                            intent.putExtra(EXTRA_CONNECT, true);
                            intent.putExtra(EXTRA_PORT, chosen.port());
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }, "Terbian-GUI-Switch").start();
    }

    private void submitFrame() {
        try {
            int[] dirty = new int[4];
            boolean partial = session.takeDirty(dirty);
            desktop.submit(session.framebuffer(), partial ? dirty : null);
        } catch (OutOfMemoryError e) {
            session.disconnect();
            viewerStatus.setText(getString(R.string.gui_oom));
        }
    }

    private void setChrome(boolean show) {
        toolbar.setVisibility(show ? View.VISIBLE : View.GONE);
        keyBar.setVisibility(show ? View.VISIBLE : View.GONE);
        showChrome.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void setFullscreen(boolean enabled) {
        fullscreen = enabled;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), desktop);
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            setChrome(false);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            setChrome(true);
        }
    }

    private void toggleKeyboard() {
        desktop.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(desktop);
        boolean visible = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        if (visible) {
            imm.hideSoftInputFromWindow(desktop.getWindowToken(), 0);
        } else {
            imm.restartInput(desktop);
            imm.showSoftInput(desktop, 0);
        }
        keyboardVisible = !visible;
    }

    private void requestResize() {
        if (!session.resizeSupported()) {
            Toast.makeText(this, R.string.gui_resize_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        int width = Math.max(320, desktop.getWidth());
        int height = Math.max(240, desktop.getHeight());
        if (!session.requestResize(width, height)) {
            Toast.makeText(this, R.string.gui_resize_unsupported, Toast.LENGTH_LONG).show();
        }
    }

    private void pasteClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, R.string.gui_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            Toast.makeText(this, R.string.gui_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        session.clientCutText(text.toString());
    }

    private void showFunctionKeys(View anchor) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            labels.add("F" + i);
        }
        com.terbian.ui.ThemedMenu.show(anchor, palette, labels, 4,
                index -> input.tap(KeySyms.functionKey(index + 1)));
    }
}
