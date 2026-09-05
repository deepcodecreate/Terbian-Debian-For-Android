package com.terbian.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.terbian.R;
import com.terbian.debian.ArchitectureDetector;
import com.terbian.debian.NativeBinaries;
import com.terbian.debian.RootfsInstaller;
import com.terbian.debian.RootfsManager;
import com.terbian.mvvm.SessionEntry;
import com.terbian.mvvm.SessionsViewModel;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.SessionType;
import com.terbian.terminal.session.SessionRegistry;
import com.terbian.terminal.view.TerbianTerminalView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements ExtraKeysView.Host {

    private LinearLayout headerBar;
    private TextView activeSessionTitle;
    private ImageButton newSessionButton;
    private ImageButton menuButton;
    private TabLayout sessionTabs;
    private ViewPager2 sessionPager;
    private ScrollView installOverlayScroll;
    private TextView installOverlay;
    private ExtraKeysView extraKeys;

    private SessionsViewModel sessionsViewModel;
    private SessionPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;

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
        prepareDebianThenStartFirstSession();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
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
        View close = view.findViewById(R.id.tabClose);
        title.setText(entry.title);
        if (palette != null) {
            title.setTextColor(palette.foreground);
            ThemeApplier.tintIcon((android.widget.ImageView) close, palette.subtle);
        }
        close.setOnClickListener(v -> closeSessionCompletely(entry.id));
    }

    void closeSessionCompletely(String sessionId) {
        SessionRegistry.getInstance(this).stopAndRemove(sessionId);
        sessionsViewModel.closeSession(sessionId);
    }

    private void updateActiveTitle(int position) {
        SessionEntry entry = pagerAdapter.sessionAt(position);
        activeSessionTitle.setText(entry != null ? entry.title : getString(R.string.app_name));
    }

    private void setupToolbarActions() {
        newSessionButton.setOnClickListener(v -> promptNewSessionType(sessionsViewModel::addSession));
        menuButton.setOnClickListener(this::showMenu);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.new_session);
        menu.getMenu().add(0, 2, 1, R.string.copy_screen);
        menu.getMenu().add(0, 3, 2, R.string.paste);
        menu.getMenu().add(0, 4, 3, R.string.close_session);
        menu.getMenu().add(0, 5, 4, R.string.settings);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                promptNewSessionType(sessionsViewModel::addSession);
                return true;
            }
            TerbianTerminalView tv = currentTerminal();
            if (id == 2 && tv != null) {
                tv.copyToClipboard(tv.copyScreen());
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == 3 && tv != null) {
                tv.pasteFromClipboard();
                return true;
            }
            if (id == 4) {
                SessionEntry entry = pagerAdapter.sessionAt(sessionPager.getCurrentItem());
                if (entry != null) closeSessionCompletely(entry.id);
                return true;
            }
            if (id == 5) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void promptNewSessionType(Consumer<SessionType> onChosen) {
        ThemePalette p = palette != null ? palette : ThemePalette.from(AppSettings.currentTheme(this));

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
        container.addView(buildSessionOptionRow(p, getString(R.string.session_type_shell),
                getString(R.string.session_type_shell_desc), () -> {
                    holder[0].dismiss();
                    onChosen.accept(SessionType.SHELL);
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
        palette = ThemePalette.from(AppSettings.currentTheme(this));

        ThemeApplier.styleWindow(this, palette);
        findViewById(R.id.rootLayout).setBackgroundColor(palette.background);
        headerBar.setBackgroundColor(palette.surface);
        activeSessionTitle.setTextColor(palette.foreground);
        ThemeApplier.tintIcon(newSessionButton, palette.foreground);
        ThemeApplier.tintIcon(menuButton, palette.foreground);
        ThemeApplier.styleTabLayout(sessionTabs, palette);
        installOverlayScroll.setBackgroundColor(palette.background);
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

    private void prepareDebianThenStartFirstSession() {
        RootfsManager rootfsManager = new RootfsManager(this);
        rootfsManager.createDirectory();
        try {
            NativeBinaries.ensureExtracted(this);
        } catch (Exception ignored) {
        }

        if (rootfsManager.isInstalled()) {
            addFirstSessionIfNeeded();
            return;
        }

        showInstallOverlay();
        appendInstallLog("Terbian Environment\n");
        appendInstallLog("Architecture: " + ArchitectureDetector.getAbi() + "\n");
        appendInstallLog("Debian rootfs: NOT INSTALLED\nInstalling Debian rootfs...\n\n");

        RootfsInstaller rootfsInstaller = new RootfsInstaller(this);
        rootfsInstaller.installAsync(new RootfsInstaller.Listener() {
            @Override
            public void onProgress(long processedBytes, long totalBytes) {
            }

            @Override
            public void onMessage(String message) {
                runOnUiThread(() -> appendInstallLog(message == null ? "" : message));
            }

            @Override
            public void onComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    appendInstallLog("\n" + (message == null ? "" : message) + "\n");
                    hideInstallOverlay();
                    addFirstSessionIfNeeded();
                });
            }
        });
    }

    private void addFirstSessionIfNeeded() {
        if (sessionsViewModel.isEmpty()) sessionsViewModel.addSession(SessionType.DEBIAN);
    }

    private void showInstallOverlay() {
        installOverlayScroll.setVisibility(View.VISIBLE);
        installOverlay.setText("");
    }

    private void hideInstallOverlay() {
        installOverlayScroll.setVisibility(View.GONE);
    }

    private void appendInstallLog(String text) {
        installOverlay.append(text);
        installOverlayScroll.post(() -> installOverlayScroll.fullScroll(View.FOCUS_DOWN));
    }
}
