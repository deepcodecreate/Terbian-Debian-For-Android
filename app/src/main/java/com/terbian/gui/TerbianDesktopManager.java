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

import android.content.Context;
import android.content.Intent;
import android.system.Os;

import androidx.core.content.ContextCompat;

import com.terbian.debian.Distro;
import com.terbian.debian.GuestRunner;
import com.terbian.debian.RootfsManager;
import com.terbian.service.TerminalService;
import com.terbian.terminal.session.SessionRegistry;
import com.terbian.vnc.VncProbe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import android.system.OsConstants;
import android.app.ActivityManager;

public final class TerbianDesktopManager {
    public static final String DESKTOP_SESSION_ID = "terbian-gui-desktop";
    private static final int READY_TIMEOUT_MS = 45_000;
    private static final long STOP_TIMEOUT_S = 20;
    private static final long EXTRAS_TIMEOUT_S = 600;

    private static final long START_MIN_AVAIL_MB = 1000;

    private static final long CRITICAL_AVAIL_MB = 350;
    private static final java.util.Set<Distro> extrasTried =
            java.util.Collections.synchronizedSet(java.util.EnumSet.noneOf(Distro.class));

    public interface Ui {
        void onLog(String line);

        void onStatus(DesktopStatus status);

        void onComplete();

        void onError(String message);

        default void onWarning(String message) {
        }
    }

    private static volatile TerbianDesktopManager instance;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Terbian-GUI");
        thread.setDaemon(false);
        return thread;
    });

    private final ExecutorService probe = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Terbian-GUI-Probe");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean trimming = new AtomicBoolean();

    private final StringBuilder log = new StringBuilder();
    private final Object stateLock = new Object();
    private GuestRunner.RunningGuest supervisor;

    private final java.util.Map<Integer, Hosted> hosted = new java.util.HashMap<>();
    private volatile boolean busy;
    private volatile Ui ui;

    private static final class Hosted {
        final GuestRunner.RunningGuest guest;
        final Distro distro;

        Hosted(GuestRunner.RunningGuest guest, Distro distro) {
            this.guest = guest;
            this.distro = distro;
        }
    }

    public static TerbianDesktopManager get() {
        if (instance == null) {
            synchronized (TerbianDesktopManager.class) {
                if (instance == null) {
                    instance = new TerbianDesktopManager();
                }
            }
        }
        return instance;
    }

    private TerbianDesktopManager() {
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    public void clearUi(Ui ui) {
        if (this.ui == ui) {
            this.ui = null;
        }
    }

    public boolean isBusy() {
        return busy;
    }

    public String logText() {
        synchronized (log) {
            return log.toString();
        }
    }

    public void clearLog() {
        synchronized (log) {
            log.setLength(0);
        }
    }

    public static final class StartRequest {
        public final String command;
        public final Distro distro;
        public final int display;

        StartRequest(String command, Distro distro, int display) {
            this.command = command;
            this.distro = distro;
            this.display = display;
        }
    }

    public static boolean customTarget(GuiSettings settings) {
        return !"127.0.0.1".equals(settings.host) || settings.port != 5901;
    }

    public void refreshAsync(Context context, Distro distro) {
        Context app = context.getApplicationContext();
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        probe.execute(() -> {
            try {
                DesktopStatus status = status(app, distro);
                Ui current = ui;
                if (current != null) {
                    current.onStatus(status);
                }
                enforceRamLimitAsync(app);
            } catch (Exception e) {
                append(messageOf(e));
            } finally {
                refreshing.set(false);
            }
        });
    }

    public void installAsync(Context context, Distro distro, String password, boolean savePassword) {
        Context app = context.getApplicationContext();
        busy = true;
        io.execute(() -> {
            try {
                install(app, distro, password, savePassword);
                busy = false;
                postComplete();
            } catch (Exception e) {
                busy = false;
                postError(messageOf(e));
            } finally {
                busy = false;
            }
        });
    }

    public void startAsync(Context context) {
        Context app = context.getApplicationContext();
        busy = true;
        io.execute(() -> {
            try {
                start(app, Distro.DEBIAN);
                busy = false;
                postComplete();
            } catch (Exception e) {
                busy = false;
                postError(messageOf(e));
            } finally {
                busy = false;
            }
        });
    }

    public void startInPageAsync(Context context, Distro distro) {
        Context app = context.getApplicationContext();
        busy = true;
        io.execute(() -> {
            try {
                StartRequest request = prepareStart(app, distro);
                if (request == null) {
                    return;
                }
                runDesktopInPage(app, request);
                busy = false;
                postComplete();
            } catch (Exception e) {
                busy = false;
                postError(messageOf(e));
            } finally {
                busy = false;
            }
        });
    }

    private StartRequest prepareStart(Context app, Distro distro) throws Exception {
        File rootfs = distro.rootfsDir(app);
        if (!isDesktopInstalled(rootfs)) {
            throw new IllegalStateException("Install the desktop before starting it");
        }
        if (!new File(rootfs, "root/.vnc/passwd").isFile()) {
            throw new IllegalStateException("No VNC password file yet. Install the desktop again and set a password.");
        }
        GuiSettings settings = GuiSettings.load(app);
        if (customTarget(settings) && VncProbe.isRfb(settings.host, settings.port, 700)) {
            append("VNC server is already accepting RFB on " + settings.host + ":" + settings.port + ".\n");
            busy = false;
            postComplete();
            return null;
        }
        if (!DesktopRegistry.runningOf(app, distro).isEmpty() && lowOnRam(app, START_MIN_AVAIL_MB)) {
            int closed = trimExtras(app);
            busy = false;
            postWarning(ramMessage(app, closed));
            return null;
        }
        int display = DesktopRegistry.reserve(app, distro);
        if (display < 0) {
            throw new IllegalStateException("Already " + DesktopRegistry.MAX_DESKTOPS
                    + " desktops are running. Stop one first.");
        }
        try {
            ensureFileManagerSupport(app, distro, rootfs);
            GuestRunner.installGuiScript(app, rootfs);
            PhantomGuard.tuneFirefox(rootfs);
        } catch (Exception e) {
            DesktopRegistry.forget(app, display);
            throw e;
        }

        String command = "TERBIAN_DISPLAY=" + display + " terbian-gui start " + settings.geometry + " " + display;
        return new StartRequest(command, distro, display);
    }

    private void runDesktopInPage(Context app, StartRequest request) throws Exception {
        final Distro distro = request.distro;
        final int display = request.display;

        String script = request.command + "\n"
                + "echo TERBIAN_GUI_STARTED\n"
                + "while true; do sleep 3600; done\n";
        append("Starting a " + distro.label + " desktop on display :" + display + " (127.0.0.1:"
                + (5900 + display) + ").\n");
        PhantomGuard.disable(this::append);
        acquireForeground(app);
        GuestRunner.RunningGuest guest;
        try {
            guest = GuestRunner.startDaemon(app, distro, script, this::append);
        } catch (Exception e) {
            DesktopRegistry.forget(app, display);
            releaseForegroundIfIdle(app);
            throw e;
        }
        synchronized (stateLock) {
            hosted.put(display, new Hosted(guest, distro));
        }
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        boolean ready = false;
        try {
            while (System.currentTimeMillis() < deadline) {
                if (!guest.isAlive()) {
                    throw new IllegalStateException("Desktop process exited early.\n" + guest.tail());
                }

                if (DesktopRegistry.isUp(app, display)) {
                    ready = true;
                    break;
                }
                Thread.sleep(400);
            }
            if (!ready) {
                throw new IllegalStateException("The desktop on display :" + display
                        + " did not come up in " + (READY_TIMEOUT_MS / 1000) + "s.\n" + guest.tail());
            }

            Thread.sleep(1200);
        } catch (Exception e) {
            synchronized (stateLock) {
                hosted.remove(display);
            }
            guest.destroy();
            killDesktopProcesses(display);
            DesktopRegistry.forget(app, display);
            releaseForegroundIfIdle(app);
            throw e;
        }
        append("Desktop :" + display + " is ready. Tap Connect to open it.\n");
    }

    public void stopAsync(Context context, Distro distro) {
        Context app = context.getApplicationContext();
        busy = true;
        io.execute(() -> {
            try {
                stop(app, distro);
                busy = false;
                postComplete();
            } catch (Exception e) {
                busy = false;
                postError(messageOf(e));
            } finally {
                busy = false;
            }
        });
    }

    public DesktopStatus status(Context context, Distro distro) throws Exception {
        Context app = context.getApplicationContext();
        File rootfs = distro.rootfsDir(app);
        boolean rootfsInstalled = new RootfsManager(app, distro).isInstalled();
        boolean desktopInstalled = rootfsInstalled && isDesktopInstalled(rootfs);
        boolean passwordConfigured = new File(rootfs, "root/.vnc/passwd").isFile() || PasswordStore.hasPassword(app);
        GuiSettings settings = GuiSettings.load(app);
        boolean serverRunning = false;
        String banner = "";
        int mine = 0;
        int others = 0;
        if (rootfsInstalled) {
            for (DesktopRegistry.Instance i : DesktopRegistry.running(app)) {
                if (i.distro == distro) {
                    mine++;
                } else {
                    others++;
                }
            }
            serverRunning = mine > 0;
            if (customTarget(settings)) {
                String remote = VncProbe.banner(settings.host, settings.port, 700);
                if (remote != null && remote.startsWith("RFB ")) {
                    serverRunning = true;
                    banner = remote.trim();
                }
            }
        }
        boolean supervisorAlive;
        synchronized (stateLock) {
            supervisorAlive = supervisor != null && supervisor.isAlive();
            for (Hosted h : hosted.values()) {
                if (h.distro == distro && h.guest.isAlive()) {
                    supervisorAlive = true;
                }
            }
        }
        String detail;
        if (!rootfsInstalled) {
            detail = distro.label + " is not installed yet. Open a " + distro.label
                    + " terminal once so Terbian can extract it, then refresh.";
        } else if (!desktopInstalled) {
            detail = "The graphical desktop is not installed. Install XFCE and TigerVNC once, then start it.";
        } else if (serverRunning) {
            detail = mine + " " + distro.label + " desktop" + (mine == 1 ? "" : "s") + " running ("
                    + displaysOf(app, distro) + ")"
                    + (others > 0 ? "; " + others + " on the other system" : "")
                    + ". Connect to use "
                    + (mine > 1 ? "the first one" : "it")
                    + ". Disconnecting does not stop it.";
        } else if (others > 0) {
            detail = "No " + distro.label + " desktop is running, but " + others + " desktop"
                    + (others == 1 ? " is" : "s are") + " running on the other system. Start one here, or switch system to use it.";
        } else if (supervisorAlive) {
            detail = "The desktop process is up but is not accepting RFB yet.";
        } else {
            detail = "Desktop packages are installed. Start the desktop, then connect.";
        }
        return new DesktopStatus(
                rootfsInstalled,
                desktopInstalled,
                passwordConfigured,
                serverRunning,
                supervisorAlive,
                banner,
                detail);
    }

    private static String displaysOf(Context app, Distro distro) {
        StringBuilder sb = new StringBuilder();
        for (DesktopRegistry.Instance i : DesktopRegistry.runningOf(app, distro)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(':').append(i.display);
        }
        return sb.toString();
    }

    public void install(Context context, Distro distro, String password, boolean savePassword) throws Exception {
        Context app = context.getApplicationContext();
        File rootfs = distro.rootfsDir(app);
        if (!new RootfsManager(app, distro).isInstalled()) {
            throw new IllegalStateException(distro.label + " is not installed yet");
        }
        String normalized = password == null ? "" : password;
        if (!GuiSettings.passwordCharsOk(normalized)) {
            throw new IllegalArgumentException("VNC passwords are 1 to 8 printable characters. TigerVNC uses an 8-byte DES key.");
        }
        append("Installing XFCE and TigerVNC in " + distro.label + ". This can take several minutes.\n");
        GuestRunner.installGuiScript(app, rootfs);
        File incoming = new File(rootfs, "root/.vnc/passwd.incoming");
        try {
            writePasswordFile(incoming, normalized);
            if (savePassword) {
                PasswordStore.save(app, normalized);
                GuiSettings.setSessionPassword(null);
                GuiSettings current = GuiSettings.load(app);
                new GuiSettings(
                        current.host, current.port, current.timeoutMs, current.scaleMode,
                        current.autoConnect, true, current.geometry).save(app);
            } else {
                PasswordStore.clear(app);
                GuiSettings.setSessionPassword(normalized);
            }
            GuestRunner.Result result = GuestRunner.run(app, distro, "terbian-gui install", this::append);
            if (!result.isSuccess()) {
                throw new IllegalStateException("terbian-gui install failed (exit " + result.exitCode + ")");
            }
            if (!isDesktopInstalled(rootfs)) {
                throw new IllegalStateException("Install finished but XFCE or TigerVNC is still missing");
            }
            ensureFileManagerSupport(app, distro, rootfs);
            append("Desktop installed.\n");
        } finally {
            if (incoming.exists() && !incoming.delete()) {
                incoming.deleteOnExit();
            }
        }
    }

    public void start(Context context, Distro distro) throws Exception {
        Context app = context.getApplicationContext();
        File rootfs = distro.rootfsDir(app);
        if (!isDesktopInstalled(rootfs)) {
            throw new IllegalStateException("Install the desktop before starting it");
        }
        if (!new File(rootfs, "root/.vnc/passwd").isFile()) {
            throw new IllegalStateException("No VNC password file yet. Install the desktop again and set a password.");
        }
        GuiSettings settings = GuiSettings.load(app);
        if (VncProbe.isRfb(settings.host, settings.port, 700)) {
            append("VNC server is already accepting RFB on " + settings.host + ":" + settings.port + ".\n");
            return;
        }
        synchronized (stateLock) {
            if (supervisor != null) {
                supervisor.destroy();
                supervisor = null;
            }
        }
        ensureFileManagerSupport(app, distro, rootfs);
        GuestRunner.installGuiScript(app, rootfs);
        PhantomGuard.tuneFirefox(rootfs);
        PhantomGuard.disable(this::append);
        acquireForeground(app);
        String command = "TERBIAN_DISPLAY=1 terbian-gui start " + settings.geometry + " 1\n"
                + "echo TERBIAN_GUI_STARTED\n"
                + "while true; do sleep 3600; done\n";
        append("Starting desktop at " + settings.geometry + " on 127.0.0.1:5901.\n");
        GuestRunner.RunningGuest guest = GuestRunner.startDaemon(app, distro, command, this::append);
        synchronized (stateLock) {
            supervisor = guest;
        }
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            if (!guest.isAlive()) {
                releaseForeground(app);
                throw new IllegalStateException("Desktop process exited early.\n" + guest.tail());
            }
            if (VncProbe.isRfb("127.0.0.1", 5901, 500)) {
                ready = true;
                break;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                guest.destroy();
                throw e;
            }
        }
        if (!ready) {
            String tail = guest.tail();
            guest.destroy();
            synchronized (stateLock) {
                if (supervisor == guest) {
                    supervisor = null;
                }
            }
            releaseForeground(app);
            throw new IllegalStateException(
                    "TigerVNC did not open 127.0.0.1:5901. PRoot shares Android's loopback, "
                            + "so the server must be reachable there without binding every interface.\n"
                            + tail);
        }
        append("VNC server is ready on 127.0.0.1:5901.\n");
    }

    public void stop(Context context, Distro distro) throws Exception {
        Context app = context.getApplicationContext();
        append("Stopping the " + distro.label + " VNC desktop(s).\n");
        stopInstances(app, distro, DesktopRegistry.runningOf(app, distro), true);
        append("Desktop stopped. The " + distro.label + " filesystem was left in place.\n");
    }

    private void stopInstances(Context app, Distro distro, List<DesktopRegistry.Instance> targets,
                               boolean wholeDistro) throws Exception {
        File rootfs = distro.rootfsDir(app);
        for (DesktopRegistry.Instance i : targets) {
            VncSession.disconnectPort(i.port());
        }
        if (new File(rootfs, "usr/local/bin/terbian-gui").isFile() || isDesktopInstalled(rootfs)) {
            try {
                GuestRunner.installGuiScript(app, rootfs);
                if (targets.isEmpty() && wholeDistro) {
                    GuestRunner.Result result = GuestRunner.run(app, distro, "terbian-gui stop", this::append, STOP_TIMEOUT_S);
                    if (!result.isSuccess() && result.exitCode != GuestRunner.EXIT_TIMEOUT) {
                        append("terbian-gui stop exit " + result.exitCode + "\n");
                    }
                }
                for (DesktopRegistry.Instance i : targets) {
                    String command = "TERBIAN_DISPLAY=" + i.display + " terbian-gui stop " + i.display;
                    GuestRunner.Result result = GuestRunner.run(app, distro, command, this::append, STOP_TIMEOUT_S);
                    if (result.exitCode == GuestRunner.EXIT_TIMEOUT) {
                        append("terbian-gui stop did not finish in " + STOP_TIMEOUT_S + "s; forcing the desktop to close.\n");
                    } else if (!result.isSuccess()) {
                        append("terbian-gui stop exit " + result.exitCode + "\n");
                    }
                }
            } catch (Exception e) {
                append(messageOf(e) + "\n");
            }
        }
        List<GuestRunner.RunningGuest> hostedToClose = new ArrayList<>();
        synchronized (stateLock) {
            java.util.Iterator<java.util.Map.Entry<Integer, Hosted>> it = hosted.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Integer, Hosted> entry = it.next();
                boolean listed = false;
                for (DesktopRegistry.Instance i : targets) {
                    if (i.display == entry.getKey()) {
                        listed = true;
                    }
                }
                if (entry.getValue().distro == distro && (wholeDistro || listed)) {
                    hostedToClose.add(entry.getValue().guest);
                    it.remove();
                }
            }
        }
        for (GuestRunner.RunningGuest guest : hostedToClose) {
            guest.destroy();
        }
        if (distro == Distro.DEBIAN && wholeDistro) {
            synchronized (stateLock) {
                if (supervisor != null) {
                    supervisor.destroy();
                    supervisor = null;
                }
            }
        }

        for (DesktopRegistry.Instance i : targets) {
            if (DesktopRegistry.isUp(app, i.display)) {
                int killed = killDesktopProcesses(i.display);
                if (killed > 0) {
                    append("Closed " + killed + " leftover desktop process(es).\n");
                }
                long deadline = System.currentTimeMillis() + 5_000;
                while (System.currentTimeMillis() < deadline && DesktopRegistry.isUp(app, i.display)) {
                    Thread.sleep(250);
                }
            }
            DesktopRegistry.forget(app, i.display);
        }
        releaseForegroundIfIdle(app);
    }

    private static ActivityManager.MemoryInfo memoryInfo(Context app) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.getMemoryInfo(info);
        }
        return info;
    }

    public static long availableMb(Context app) {
        return memoryInfo(app).availMem / (1024 * 1024);
    }

    private static boolean lowOnRam(Context app, long minAvailMb) {
        ActivityManager.MemoryInfo info = memoryInfo(app);
        return info.lowMemory || info.availMem / (1024 * 1024) < minAvailMb;
    }

    private static String ramMessage(Context app, int closed) {
        return "Not enough free RAM (" + availableMb(app) + " MB free). Terbian GUI is limited to 1 desktop per "
                + "system (one Debian and one Ubuntu)."
                + (closed > 0 ? " " + closed + " extra desktop" + (closed == 1 ? " was" : "s were") + " closed." : "")
                + " Close other apps to free memory.";
    }

    private int trimExtras(Context app) throws Exception {
        int closed = 0;
        for (Distro d : Distro.values()) {
            List<DesktopRegistry.Instance> running = DesktopRegistry.runningOf(app, d);
            if (running.size() < 2) {
                continue;
            }
            int keep = 0;
            for (int k = 0; k < running.size(); k++) {
                if (VncSession.forPort(running.get(k).port()).isConnected()) {
                    keep = k;
                    break;
                }
            }
            List<DesktopRegistry.Instance> extra = new ArrayList<>(running);
            DesktopRegistry.Instance kept = extra.remove(keep);
            append("Low memory: keeping " + kept.label() + ", closing " + extra.size() + " other "
                    + d.label + " desktop(s).\n");
            stopInstances(app, d, extra, false);
            closed += extra.size();
        }
        return closed;
    }

    private void enforceRamLimitAsync(Context app) {
        if (busy || !lowOnRam(app, CRITICAL_AVAIL_MB) || !trimming.compareAndSet(false, true)) {
            return;
        }
        busy = true;
        io.execute(() -> {
            try {
                int closed = trimExtras(app);
                if (closed > 0) {
                    postWarning(ramMessage(app, closed));
                }
            } catch (Exception e) {
                append(messageOf(e) + "\n");
            } finally {
                busy = false;
                trimming.set(false);
            }
        });
    }

    private void postWarning(String message) {
        append(message + "\n");
        Ui current = ui;
        if (current != null) {
            current.onWarning(message);
        }
    }

    private static int killDesktopProcesses(int display) {
        String[] needles = display > 0
                ? new String[]{"Xtigervnc", "Xvnc"}
                : new String[]{"Xtigervnc", "Xvnc", "vncserver", "startxfce4", "xfce4-session", "xfwm4",
                        "xfce4-panel", "xfdesktop", "xfconfd"};
        java.util.regex.Pattern displayPattern = display > 0
                ? java.util.regex.Pattern.compile("(^|\\s):" + display + "(\\s|$)") : null;
        File[] entries = new File("/proc").listFiles();
        if (entries == null) {
            return 0;
        }
        int self = android.os.Process.myPid();
        int killed = 0;
        for (File entry : entries) {
            int pid;
            try {
                pid = Integer.parseInt(entry.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            if (pid == self) {
                continue;
            }
            String cmd;
            try (java.io.FileInputStream in = new java.io.FileInputStream(new File(entry, "cmdline"))) {
                byte[] data = new byte[4096];
                int n = in.read(data);
                if (n <= 0) {
                    continue;
                }
                cmd = new String(data, 0, n, StandardCharsets.UTF_8).replace('\0', ' ');
            } catch (Exception e) {
                continue;
            }
            if (cmd.contains("libproot")) {
                continue;
            }
            if (displayPattern != null && !displayPattern.matcher(cmd).find()) {
                continue;
            }
            for (String needle : needles) {
                if (cmd.contains(needle)) {
                    try {
                        Os.kill(pid, OsConstants.SIGKILL);
                        killed++;
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
        return killed;
    }

    private static boolean hasTrashSupport(File rootfs) {
        boolean gvfsTrash = new File(rootfs, "usr/libexec/gvfsd-trash").isFile()
                || new File(rootfs, "usr/lib/gvfs/gvfsd-trash").isFile();
        boolean dbus = new File(rootfs, "usr/bin/dbus-launch").isFile();
        return gvfsTrash && dbus;
    }

    private void ensureFileManagerSupport(Context app, Distro distro, File rootfs) {
        File trash = new File(rootfs, "root/.local/share/Trash");
        new File(trash, "files").mkdirs();
        new File(trash, "info").mkdirs();
        if (hasTrashSupport(rootfs) || !extrasTried.add(distro)) {
            return;
        }
        append("Installing file manager support (gvfs, D-Bus) so Trash works. This needs a network connection.\n");
        String apt = "apt-get -o Acquire::Retries=0 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 ";
        String command = apt + "update; " + apt + "install -y --no-install-recommends "
                + "gvfs gvfs-backends gvfs-daemons dbus-x11 libglib2.0-bin shared-mime-info";
        try {
            GuestRunner.Result result = GuestRunner.run(app, distro, command, this::append, EXTRAS_TIMEOUT_S);
            if (!hasTrashSupport(rootfs)) {
                append("Could not install gvfs (exit " + result.exitCode
                        + "). Trash will stay hidden until `apt install gvfs gvfs-backends dbus-x11` succeeds in the terminal.\n");
            } else {
                append("File manager support installed.\n");
            }
        } catch (Exception e) {
            append("File manager support was not installed: " + messageOf(e) + "\n");
        }
    }

    private static boolean isDesktopInstalled(File rootfs) {
        boolean vnc = new File(rootfs, "usr/bin/vncserver").isFile()
                || new File(rootfs, "usr/bin/Xtigervnc").isFile()
                || new File(rootfs, "bin/vncserver").isFile();
        boolean xfce = new File(rootfs, "usr/bin/startxfce4").isFile()
                || new File(rootfs, "usr/bin/xfce4-session").isFile();
        return vnc && xfce;
    }

    private static void writePasswordFile(File dest, String password) throws Exception {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("Cannot create " + parent.getAbsolutePath());
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(password.getBytes(StandardCharsets.UTF_8));
        }
        try {
            Os.chmod(dest.getAbsolutePath(), 0600);
        } catch (Exception ignored) {
        }
    }

    private void acquireForeground(Context app) {
        try {
            Intent intent = new Intent(app, TerminalService.class);
            intent.setAction(TerminalService.ACTION_SESSION_STARTED);
            intent.putExtra(TerminalService.EXTRA_SESSION_ID, DESKTOP_SESSION_ID);
            intent.putExtra(TerminalService.EXTRA_SESSION_TITLE, "Terbian GUI");
            ContextCompat.startForegroundService(app, intent);
        } catch (Exception ignored) {
        }
    }

    private void releaseForegroundIfIdle(Context app) {
        synchronized (stateLock) {
            for (Hosted h : hosted.values()) {
                if (h.guest.isAlive()) {
                    return;
                }
            }
        }
        releaseForeground(app);
    }

    private void releaseForeground(Context app) {
        try {
            Intent intent = new Intent(app, TerminalService.class);
            intent.setAction(TerminalService.ACTION_SESSION_STOPPED);
            intent.putExtra(TerminalService.EXTRA_SESSION_ID, DESKTOP_SESSION_ID);
            app.startService(intent);
        } catch (Exception ignored) {
        }
        try {
            if (SessionRegistry.getInstance(app).activeCount() == 0) {
                app.stopService(new Intent(app, TerminalService.class));
            }
        } catch (Exception ignored) {
        }
    }

    private void append(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        String text = line.endsWith("\n") ? line : line + "\n";
        synchronized (log) {
            log.append(text);
            if (log.length() > 80_000) {
                log.delete(0, log.length() - 60_000);
            }
        }
        Ui current = ui;
        if (current != null) {
            current.onLog(text);
        }
    }

    private void postComplete() {
        Ui current = ui;
        if (current != null) {
            current.onComplete();
        }
    }

    private void postError(String message) {
        append(message + "\n");
        Ui current = ui;
        if (current != null) {
            current.onError(message);
        }
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.trim();
    }
}
