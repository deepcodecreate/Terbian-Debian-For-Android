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

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.terbian.R;
import com.terbian.debian.ArchitectureDetector;
import com.terbian.debian.NativeBinaries;
import com.terbian.debian.Distro;
import com.terbian.debian.RootfsInstaller;
import com.terbian.debian.RootfsManager;
import com.terbian.gui.MaxHeightScrollView;
import com.terbian.gui.TerbianGuiActivity;
import com.terbian.mvvm.SessionEntry;
import com.terbian.mvvm.SessionsViewModel;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.SessionType;
import com.terbian.terminal.session.SessionRegistry;
import com.terbian.terminal.view.TerbianTerminalView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainActivity extends BaseActivity implements ExtraKeysView.Host {
    public static final String EXTRA_RUN_COMMAND = "run_command";
    public static final String EXTRA_RUN_TITLE = "run_title";

    public static final String EXTRA_RUN_TYPE = "run_type";

    private LinearLayout headerBar;
    private TextView activeSessionTitle;
    private ImageButton newSessionButton;
    private ImageButton menuButton;
    private ImageButton quickButton;
    private ImageButton pasteButton;
    private TabLayout sessionTabs;
    private ViewPager2 sessionPager;
    private ScrollView installOverlayScroll;
    private View installOverlayRoot;
    private ExpressiveLoadingView installLoader;
    private ExpressiveProgressView installWave;
    private TextView installTitle;
    private TextView installSub;
    private TextView installOverlay;
    private ExtraKeysView extraKeys;

    private SessionsViewModel sessionsViewModel;
    private SessionPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;

    private boolean installing;
    private int lastKnownSessionCount = 0;
    private List<SessionEntry> previousSessions = new ArrayList<>();
    private ThemePalette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    Math.max(bars.bottom, ime.bottom)
            );
            return insets;
        });

        bindViews();
        setupPager();
        setupToolbarActions();
        extraKeys.setHost(this);
        extraKeys.setVisibility(AppSettings.extraKeys(this) ? View.VISIBLE : View.GONE);

        applyAppTheme();
        requestNotificationPermissionIfNeeded();
        requestStorageAccessOnFirstLaunch();
        prepareThenStartFirstSession();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (hasStorageAccess()) {
            Toast.makeText(this, R.string.storage_access_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
        }
    }

    private void requestStorageAccessOnFirstLaunch() {
        if (hasStorageAccess() || AppSettings.prefs(this).getBoolean(AppSettings.KEY_STORAGE_ASKED, false)) {
            return;
        }
        AppSettings.prefs(this).edit().putBoolean(AppSettings.KEY_STORAGE_ASKED, true).apply();
        requestStorageAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        extraKeys.setVisibility(AppSettings.extraKeys(this) ? View.VISIBLE : View.GONE);
        applyAppTheme();
    }

    private void bindViews() {
        headerBar = findViewById(R.id.headerBar);
        activeSessionTitle = findViewById(R.id.activeSessionTitle);
        newSessionButton = findViewById(R.id.newSessionButton);
        menuButton = findViewById(R.id.menuButton);
        sessionTabs = findViewById(R.id.sessionTabs);
        sessionPager = findViewById(R.id.sessionPager);
        installOverlayScroll = findViewById(R.id.installOverlayScroll);
        installOverlayRoot = findViewById(R.id.installOverlayRoot);
        installLoader = findViewById(R.id.installLoader);
        installWave = findViewById(R.id.installWave);
        installTitle = findViewById(R.id.installTitle);
        installSub = findViewById(R.id.installSub);
        installOverlay = findViewById(R.id.installOverlay);
        extraKeys = findViewById(R.id.extraKeys);
    }

    private void setupPager() {
        sessionsViewModel = new ViewModelProvider(this).get(SessionsViewModel.class);
        pagerAdapter = new SessionPagerAdapter(this);
        sessionPager.setAdapter(pagerAdapter);
        sessionPager.setOffscreenPageLimit(3);
        sessionPager.setPageTransformer((page, position) ->
                page.setAlpha(1f - Math.min(1f, Math.abs(position) * 0.35f)));

        sessionsViewModel.getSessions().observe(this, this::onSessionsChanged);
        sessionPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateActiveTitle(position);
                extraKeys.refreshModifiers();
            }
        });
    }

    private void onSessionsChanged(List<SessionEntry> sessions) {
        boolean structural = sessions.size() != previousSessions.size();
        if (structural) {
            pagerAdapter.submitList(sessions);
            if (tabLayoutMediator == null) {
                tabLayoutMediator = new TabLayoutMediator(sessionTabs, sessionPager,
                        (tab, position) -> bindTab(tab, pagerAdapter.sessionAt(position)));
                tabLayoutMediator.attach();
                sessionTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        applyTabState(tab, true, true);
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {
                        applyTabState(tab, false, true);
                    }

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {
                    }
                });
            }
            if (sessions.size() > lastKnownSessionCount) {
                sessionPager.post(() -> sessionPager.setCurrentItem(sessions.size() - 1, true));
            }
            lastKnownSessionCount = sessions.size();
        } else {
            for (int i = 0; i < sessions.size(); i++) {
                TabLayout.Tab tab = sessionTabs.getTabAt(i);
                if (tab != null) bindTab(tab, sessions.get(i));
            }
            updateActiveTitle(sessionPager.getCurrentItem());
            pagerAdapter.updateSessionsSilently(sessions);
        }
        previousSessions = new ArrayList<>(sessions);
    }

    private void bindTab(TabLayout.Tab tab, SessionEntry entry) {
        if (entry == null) return;
        if (tab.getCustomView() == null) {
            tab.setCustomView(R.layout.tab_session);
        }
        View view = tab.getCustomView();
        if (view == null) {
            tab.setText(entry.title);
            return;
        }
        TextView title = view.findViewById(R.id.tabTitle);
        title.setText(entry.title);
        view.findViewById(R.id.tabClose).setOnClickListener(v -> {
            Motion.haptic(v);
            closeSessionCompletely(entry.id);
        });
        if (palette != null) {
            ThemeApplier.styleSessionTab(view, palette);
        }
        applyTabState(tab, tab.isSelected(), false);
        tab.view.setOnLongClickListener(v -> {
            showTabMenu(v, entry.id);
            return true;
        });
    }

    private void applyTabState(TabLayout.Tab tab, boolean selected, boolean animate) {
        View view = tab.getCustomView();
        if (view == null) return;
        view.setSelected(selected);
        view.findViewById(R.id.tabClose).setVisibility(selected ? View.VISIBLE : View.GONE);
        ExpressiveShapeView dot = view.findViewById(R.id.tabDot);
        int shape = selected ? 2 : 0;
        if (animate) dot.morphTo(shape);
        else dot.setShape(shape);
        if (palette != null) dot.setFillColor(selected ? palette.primary : palette.border);
    }

    private void showTabMenu(View anchor, String sessionId) {
        ThemePalette p = palette != null ? palette : ThemePalette.of(this);
        boolean hasOthers = pagerAdapter.getItemCount() > 1;

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(8), 0, dp(8));
        menu.setMinimumWidth(dp(168));
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(p.surfaceHigh);
        menuBg.setCornerRadius(dp(12));
        menuBg.setStroke(dp(1), p.border);
        menu.setBackground(menuBg);

        PopupWindow[] holder = new PopupWindow[1];

        menu.addView(buildMenuItem(p, getString(R.string.close_this), true, () -> {
            holder[0].dismiss();
            closeSessionCompletely(sessionId);
        }));
        menu.addView(buildMenuItem(p, getString(R.string.close_other), hasOthers, () -> {
            holder[0].dismiss();
            closeOtherSessions(sessionId);
        }));
        menu.addView(buildMenuItem(p, getString(R.string.close_all), true, () -> {
            holder[0].dismiss();
            closeAllSessions();
        }));

        PopupWindow popup = new PopupWindow(menu,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        holder[0] = popup;
        popup.showAsDropDown(anchor, 0, dp(-4));
    }

    private View buildMenuItem(ThemePalette p, String label, boolean enabled, Runnable onClick) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextSize(14);
        item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(48));
        item.setPadding(dp(16), 0, dp(16), 0);
        item.setTextColor(enabled ? p.foreground : ThemePalette.withAlpha(p.foreground, 97));
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (enabled) {
            item.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(ThemePalette.withAlpha(p.primary, 40)), null,
                    new ColorDrawable(0xFFFFFFFF)));
            item.setOnClickListener(v -> onClick.run());
        } else {
            item.setEnabled(false);
        }
        return item;
    }

    void closeSessionCompletely(String sessionId) {
        SessionRegistry.getInstance(this).stopAndRemove(sessionId);
        sessionsViewModel.closeSession(sessionId);
    }

    void closeOtherSessions(String keepId) {
        List<SessionEntry> current = sessionsViewModel.getSessions().getValue();
        if (current == null) return;
        SessionRegistry registry = SessionRegistry.getInstance(this);
        for (SessionEntry entry : new ArrayList<>(current)) {
            if (!entry.id.equals(keepId)) registry.stopAndRemove(entry.id);
        }
        sessionsViewModel.closeOthers(keepId);
    }

    void closeAllSessions() {
        List<SessionEntry> current = sessionsViewModel.getSessions().getValue();
        if (current == null) return;
        SessionRegistry registry = SessionRegistry.getInstance(this);
        for (SessionEntry entry : new ArrayList<>(current)) {
            registry.stopAndRemove(entry.id);
        }
        sessionsViewModel.closeAll();
    }

    private void updateActiveTitle(int position) {
        SessionEntry entry = pagerAdapter.sessionAt(position);
        activeSessionTitle.setText(entry != null ? entry.title : getString(R.string.app_name));
    }

    private void setupToolbarActions() {
        newSessionButton.setOnClickListener(v -> promptNewSessionType(type -> startSession(type, null)));
        menuButton.setOnClickListener(this::showMenu);

        ViewGroup bar = (ViewGroup) newSessionButton.getParent();
        int at = bar.indexOfChild(newSessionButton);
        ViewGroup.LayoutParams size = newSessionButton.getLayoutParams();
        quickButton = buildToolbarButton(R.drawable.ic_quick_bolt, R.string.quick_commands);
        pasteButton = buildToolbarButton(R.drawable.ic_quick_paste, R.string.paste);
        bar.addView(quickButton, at, new ViewGroup.LayoutParams(size.width, size.height));
        bar.addView(pasteButton, at + 1, new ViewGroup.LayoutParams(size.width, size.height));
        quickButton.setOnClickListener(v -> showQuickCommands());
        pasteButton.setOnClickListener(v -> pasteFromClipboard());
    }

    private ImageButton buildToolbarButton(int icon, int description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(getString(description));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        button.setBackgroundResource(ripple.resourceId);
        return button;
    }

    private com.termux.terminal.TerminalSession currentSession() {
        SessionEntry entry = pagerAdapter.sessionAt(sessionPager.getCurrentItem());
        if (entry == null) return null;
        com.termux.terminal.TerminalSession session = SessionRegistry.getInstance(this).get(entry.id);
        return session != null && session.isRunning() ? session : null;
    }

    private void runInTerminal(String command) {
        com.termux.terminal.TerminalSession session = currentSession();
        if (session == null) {
            Toast.makeText(this, R.string.qc_no_terminal, Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] data = (command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        session.write(data, 0, data.length);
    }

    private void clearScreen() {
        com.termux.terminal.TerminalSession session = currentSession();
        if (session == null) {
            Toast.makeText(this, R.string.qc_no_terminal, Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] data = {12};
        session.write(data, 0, data.length);
    }

    private void pasteFromClipboard() {
        com.termux.terminal.TerminalSession session = currentSession();
        if (session == null || session.getEmulator() == null) {
            Toast.makeText(this, R.string.qc_no_terminal, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(this) : null;
        if (text == null || text.length() == 0) {
            Toast.makeText(this, R.string.qc_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        session.getEmulator().paste(text.toString());
    }

    private void showQuickCommands() {
        ThemePalette p = palette != null ? palette : ThemePalette.of(this);
        List<QuickCommands.Item> items = QuickCommands.load(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, pad, pad, pad);
        container.setBackground(Shapes.rect(this, p.surface, Shapes.LARGE, 0, 0));

        TextView title = new TextView(this);
        title.setText(R.string.quick_commands);
        title.setTextColor(p.foreground);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(4));
        container.addView(title);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        MaxHeightScrollView scroll = new MaxHeightScrollView(this);
        scroll.addView(list);
        container.addView(scroll);

        AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final QuickCommands.Item item = items.get(i);
            View row = buildSessionOptionRow(p, item.name, item.command, () -> {
                holder[0].dismiss();
                runInTerminal(item.command);
            });
            if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 1) {
                ((TextView) ((LinearLayout) row).getChildAt(1)).setTypeface(Typeface.MONOSPACE);
            }
            row.setOnLongClickListener(v -> {
                holder[0].dismiss();
                showEditCommand(items, index);
                return true;
            });
            list.addView(row);
        }
        container.addView(buildSessionOptionRow(p, getString(R.string.quick_commands_add), "", () -> {
            holder[0].dismiss();
            showEditCommand(items, -1);
        }));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        holder[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        dialog.show();
    }

    private void showEditCommand(List<QuickCommands.Item> items, int index) {
        ThemePalette p = palette != null ? palette : ThemePalette.of(this);
        QuickCommands.Item existing = index >= 0 ? items.get(index) : null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Shapes.rect(this, p.surface, Shapes.LARGE, 0, 0));

        EditText name = new EditText(this);
        name.setHint(R.string.qc_name_hint);
        name.setSingleLine(true);
        EditText command = new EditText(this);
        command.setHint(R.string.qc_command_hint);
        command.setTypeface(Typeface.MONOSPACE);
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        command.setMaxLines(4);
        for (EditText field : new EditText[]{name, command}) {
            field.setTextColor(p.foreground);
            field.setHintTextColor(p.subtle);
            field.setBackground(Shapes.rect(this, p.surfaceHigh, Shapes.SMALL, p.border, 1));
            field.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            box.addView(field, lp);
        }
        if (existing != null) {
            name.setText(existing.name);
            command.setText(existing.command);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, (d, w) -> showQuickCommands())
                .setOnCancelListener(d -> showQuickCommands())
                .setPositiveButton(R.string.qc_save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String c = command.getText().toString().trim();
                    if (!c.isEmpty()) {
                        List<QuickCommands.Item> next = new ArrayList<>(items);
                        QuickCommands.Item made = new QuickCommands.Item(n.isEmpty() ? c : n, c);
                        if (index >= 0) next.set(index, made); else next.add(made);
                        QuickCommands.save(this, next);
                    }
                    showQuickCommands();
                });
        if (existing != null) {
            builder.setNeutralButton(R.string.qc_delete, (d, w) -> {
                List<QuickCommands.Item> next = new ArrayList<>(items);
                next.remove(index);
                QuickCommands.save(this, next);
                showQuickCommands();
            });
        }
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        dialog.show();
    }

    private void styleTerminalCard() {
        final float radius = Shapes.radius(this, Shapes.LARGE);
        ViewGroup.LayoutParams lp = sessionPager.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            if (m.leftMargin != dp(10)) {
                m.setMargins(dp(10), dp(6), dp(10), dp(8));
                sessionPager.setLayoutParams(m);
            }
        }
        sessionPager.setBackgroundColor(palette.background);
        sessionPager.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        sessionPager.setClipToOutline(true);
        sessionPager.invalidateOutline();

        View pages = sessionPager.getChildAt(0);
        if (pages instanceof RecyclerView) {
            pages.setPadding(dp(12), dp(10), dp(12), dp(6));
        }
    }

    private void showMenu(View anchor) {
        final boolean showStorage = !hasStorageAccess();
        final int[] ids = showStorage ? new int[]{7, 8, 6, 5} : new int[]{7, 8, 5};
        final int[] labels = showStorage
                ? new int[]{R.string.terbian_gui, R.string.clear_screen, R.string.storage_access, R.string.settings}
                : new int[]{R.string.terbian_gui, R.string.clear_screen, R.string.settings};
        List<String> names = new ArrayList<>();
        for (int label : labels) {
            names.add(getString(label));
        }
        ThemePalette p = palette != null ? palette : ThemePalette.of(this);
        ThemedMenu.show(anchor, p, names, 1, index -> onMenuItem(ids[index]));
    }

    private void onMenuItem(int id) {
        if (id == 5) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == 7) {
            startActivity(new Intent(this, TerbianGuiActivity.class));
        } else if (id == 6) {
            requestStorageAccess();
        } else if (id == 8) {
            clearScreen();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String command = intent.getStringExtra(EXTRA_RUN_COMMAND);
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        String runTitle = intent.getStringExtra(EXTRA_RUN_TITLE);
        intent.removeExtra(EXTRA_RUN_COMMAND);
        intent.removeExtra(EXTRA_RUN_TITLE);
        SessionType runType = SessionType.fromName(intent.getStringExtra(EXTRA_RUN_TYPE));
        intent.removeExtra(EXTRA_RUN_TYPE);
        startSession(runType, entry -> {
            sessionsViewModel.renameSession(entry.id,
                    runTitle == null || runTitle.isEmpty() ? getString(R.string.terbian_gui) : runTitle);
            typeWhenReady(entry.id, command, 0);
        });
    }

    private void startSession(SessionType type, Consumer<SessionEntry> onCreated) {
        Distro distro = type.distro();
        if (distro == null || new RootfsManager(this, distro).isInstalled()) {
            SessionEntry entry = sessionsViewModel.addSession(type);
            if (onCreated != null) onCreated.accept(entry);
            return;
        }
        installRootfs(distro, () -> {
            SessionEntry entry = sessionsViewModel.addSession(type);
            if (onCreated != null) onCreated.accept(entry);
        });
    }

    private void typeWhenReady(String sessionId, String command, int attempt) {
        com.termux.terminal.TerminalSession session = SessionRegistry.getInstance(this).get(sessionId);
        if (session != null && session.isRunning()) {
            sessionPager.postDelayed(() -> {
                if (!session.isRunning()) return;
                byte[] data = (command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                session.write(data, 0, data.length);
            }, 700);
            return;
        }
        if (attempt < 60) {
            sessionPager.postDelayed(() -> typeWhenReady(sessionId, command, attempt + 1), 250);
        }
    }

    private void promptNewSessionType(Consumer<SessionType> onChosen) {
        ThemePalette p = palette != null ? palette : ThemePalette.of(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.surface);
        bg.setCornerRadius(dp(16));
        container.setBackground(bg);

        TextView title = new TextView(this);
        title.setText(R.string.choose_session_type);
        title.setTextColor(p.foreground);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        container.addView(title);

        AlertDialog[] holder = new AlertDialog[1];

        container.addView(buildSessionOptionRow(p, getString(R.string.session_type_debian),
                getString(R.string.session_type_debian_desc), () -> {
                    holder[0].dismiss();
                    onChosen.accept(SessionType.DEBIAN);
                }));
        boolean ubuntuReady = new RootfsManager(this, Distro.UBUNTU).isInstalled();
        container.addView(buildSessionOptionRow(p, getString(R.string.session_type_ubuntu),
                getString(ubuntuReady ? R.string.session_type_ubuntu_desc
                        : R.string.session_type_ubuntu_desc_download), () -> {
                    holder[0].dismiss();
                    onChosen.accept(SessionType.UBUNTU);
                }));
        container.addView(buildSessionOptionRow(p, getString(R.string.session_type_shell),
                getString(R.string.session_type_shell_desc), () -> {
                    holder[0].dismiss();
                    onChosen.accept(SessionType.SHELL);
                }));
        boolean rooted = com.terbian.terminal.AndroidShell.findSu() != null;
        container.addView(buildSessionOptionRow(p, getString(R.string.session_type_root),
                getString(rooted ? R.string.session_type_root_desc : R.string.session_type_root_desc_missing), () -> {
                    holder[0].dismiss();
                    onChosen.accept(SessionType.ROOT);
                }));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        holder[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }
        dialog.show();
    }

    private View buildSessionOptionRow(ThemePalette p, String label, String description, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(14);
        int padV = dp(12);
        row.setPadding(padH, padV, padH, padV);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(p.surfaceHigh);
        rowBg.setCornerRadius(dp(12));
        rowBg.setStroke((int) (1 * getResources().getDisplayMetrics().density), p.border);
        row.setBackground(rowBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(p.foreground);
        labelView.setTextSize(15);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(labelView);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(p.subtle);
        descView.setTextSize(12);
        descView.setPadding(0, dp(2), 0, 0);
        row.addView(descView);

        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public TerbianTerminalView currentTerminal() {
        int pos = sessionPager.getCurrentItem();
        Object item = pagerAdapter.getFragment(pos);
        if (item instanceof TerminalFragment) {
            return ((TerminalFragment) item).getTerminalView();
        }
        return null;
    }

    public void refreshExtraKeys() {
        if (extraKeys != null) {
            extraKeys.refreshModifiers();
        }
    }

    private void applyAppTheme() {
        palette = ThemePalette.of(this);

        ThemeApplier.styleWindow(this, palette);

        findViewById(R.id.rootLayout).setBackgroundColor(palette.surface);
        headerBar.setBackgroundColor(palette.surface);
        activeSessionTitle.setTextColor(palette.foreground);
        ThemeApplier.tintIcon(newSessionButton, palette.foreground);
        ThemeApplier.tintIcon(menuButton, palette.foreground);
        ThemeApplier.tintIcon(quickButton, palette.foreground);
        ThemeApplier.tintIcon(pasteButton, palette.foreground);
        styleTerminalCard();
        ThemeApplier.styleTabLayout(sessionTabs, palette);
        installOverlayRoot.setBackgroundColor(palette.background);
        installLoader.setColor(palette.primary);
        installLoader.setContainerColor(palette.primaryContainer);
        installWave.setColors(palette.primary, palette.surfaceHighest);
        installTitle.setTextColor(palette.foreground);
        installSub.setTextColor(palette.subtle);
        installOverlay.setTextColor(palette.foreground);
        extraKeys.applyTheme(palette);

        for (int i = 0; i < sessionTabs.getTabCount(); i++) {
            TabLayout.Tab tab = sessionTabs.getTabAt(i);
            if (tab != null) bindTab(tab, pagerAdapter.sessionAt(i));
        }

        applyThemeToVisible();
    }

    private void applyThemeToVisible() {
        for (int i = 0; i < pagerAdapter.getItemCount(); i++) {
            Object item = pagerAdapter.getFragment(i);
            if (item instanceof TerminalFragment) {
                ((TerminalFragment) item).applyTheme(AppSettings.currentTheme(this));
            }
        }
    }

    private void prepareThenStartFirstSession() {
        try {
            NativeBinaries.ensureExtracted(this);
        } catch (Exception ignored) {
        }

        Distro distro = AppSettings.defaultDistro(this);
        new RootfsManager(this, distro).createDirectory();
        if (new RootfsManager(this, distro).isInstalled()) {
            addFirstSessionIfNeeded();
            return;
        }
        installRootfs(distro, this::addFirstSessionIfNeeded);
    }

    private void installRootfs(Distro distro, Runnable onReady) {
        if (installing) {
            Toast.makeText(this, getString(R.string.install_in_progress), Toast.LENGTH_SHORT).show();
            return;
        }
        installing = true;
        new RootfsManager(this, distro).createDirectory();

        showInstallOverlay(distro);
        appendInstallLog("Terbian Environment\n");
        appendInstallLog("Architecture: " + ArchitectureDetector.getAbi() + "\n");
        appendInstallLog(distro.label + " rootfs: NOT INSTALLED\nInstalling " + distro.label + " rootfs...\n\n");

        RootfsInstaller rootfsInstaller = new RootfsInstaller(this, distro);
        rootfsInstaller.installAsync(new RootfsInstaller.Listener() {
            @Override
            public void onProgress(long processedBytes, long totalBytes) {
                if (totalBytes > 0) {
                    runOnUiThread(() -> installWave.setProgress(processedBytes / (float) totalBytes));
                }
            }

            @Override
            public void onMessage(String message) {
                runOnUiThread(() -> appendInstallLog(message == null ? "" : message));
            }

            @Override
            public void onComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    installing = false;
                    appendInstallLog("\n" + (message == null ? "" : message) + "\n");
                    if (isDestroyed()) return;
                    if (success) {
                        hideInstallOverlay();
                        onReady.run();
                    } else {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        installOverlayScroll.postDelayed(MainActivity.this::hideInstallOverlay, 6000);
                    }
                });
            }
        });
    }

    private void addFirstSessionIfNeeded() {
        if (sessionsViewModel.isEmpty()) {
            sessionsViewModel.addSession(SessionType.forDistro(AppSettings.defaultDistro(this)));
        }
    }

    private void showInstallOverlay(Distro distro) {
        installTitle.setText(getString(R.string.installing_title, distro.label));
        installOverlayRoot.setVisibility(View.VISIBLE);
        installOverlay.setText("");
    }

    private void hideInstallOverlay() {
        installOverlayRoot.setVisibility(View.GONE);
    }

    private void appendInstallLog(String text) {
        installOverlay.append(text);
        installOverlayScroll.post(() -> installOverlayScroll.fullScroll(View.FOCUS_DOWN));
    }
}
