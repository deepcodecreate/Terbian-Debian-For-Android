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

package com.terbian.debian;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuestRunner {
    private static final String LOG_TAG = "TerbianGuest";
    private static final int TAIL_LINES = 120;
    public static final int EXIT_TIMEOUT = -2;

    public interface LineListener {
        void onLine(String line);
    }

    public static final class Result {
        public final int exitCode;
        public final String tailOutput;

        Result(int exitCode, String tailOutput) {
            this.exitCode = exitCode;
            this.tailOutput = tailOutput;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static final class RunningGuest {
        private final Process process;
        private final File tmpDir;
        private final ArrayDeque<String> tail = new ArrayDeque<>();
        private final AtomicBoolean readerDone = new AtomicBoolean();

        RunningGuest(Process process, File tmpDir) {
            this.process = process;
            this.tmpDir = tmpDir;
        }

        public boolean isAlive() {
            return process.isAlive();
        }

        public synchronized String tail() {
            return String.join("\n", tail);
        }

        synchronized void addLine(String line) {
            tail.addLast(line);
            while (tail.size() > TAIL_LINES) {
                tail.removeFirst();
            }
        }

        public void destroy() {
            process.destroy();
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            deleteRecursive(tmpDir);
        }

        void markReaderDone() {
            readerDone.set(true);
        }
    }

    private GuestRunner() {
    }

    public static Result run(Context context, String command, LineListener listener)
            throws IOException, InterruptedException {
        return run(context, Distro.DEBIAN, command, listener);
    }

    public static Result run(Context context, Distro distro, String command, LineListener listener)
            throws IOException, InterruptedException {
        RunningGuest guest = start(context, distro, command, true, listener);
        try {
            int exit = guest.process.waitFor();
            return new Result(exit, guest.tail());
        } finally {
            guest.destroy();
        }
    }

    public static Result run(Context context, String command, LineListener listener, long timeoutSeconds)
            throws IOException, InterruptedException {
        return run(context, Distro.DEBIAN, command, listener, timeoutSeconds);
    }

    public static Result run(Context context, Distro distro, String command, LineListener listener,
                             long timeoutSeconds) throws IOException, InterruptedException {
        RunningGuest guest = start(context, distro, command, true, listener);
        try {
            if (!guest.process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                return new Result(EXIT_TIMEOUT, guest.tail());
            }
            return new Result(guest.process.exitValue(), guest.tail());
        } finally {
            guest.destroy();
        }
    }

    public static RunningGuest startDaemon(Context context, String command, LineListener listener)
            throws IOException {
        return start(context, Distro.DEBIAN, command, false, listener);
    }

    public static RunningGuest startDaemon(Context context, Distro distro, String command, LineListener listener)
            throws IOException {
        return start(context, distro, command, false, listener);
    }

    public static void installGuiScript(Context context, File rootfs) throws IOException {
        File bin = new File(rootfs, "usr/local/bin");
        if (!bin.isDirectory() && !bin.mkdirs() && !bin.isDirectory()) {
            throw new IOException("Cannot create " + bin.getAbsolutePath());
        }
        File dest = new File(bin, "terbian-gui");
        try (InputStream in = context.getAssets().open("debian-tools/terbian-gui");
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        dest.setReadable(true, false);
        dest.setExecutable(true, false);
        try {
            Os.chmod(dest.getAbsolutePath(), 0755);
        } catch (Exception ignored) {
        }
    }

    private static RunningGuest start(
            Context context, Distro distro, String command, boolean killOnExit, LineListener listener)
            throws IOException {
        Context app = context.getApplicationContext();
        File rootfs = distro.rootfsDir(app);
        if (!new RootfsManager(app, distro).isInstalled()) {
            throw new IOException(distro.label + " is not installed yet");
        }
        RootfsFixes.ensureGroupNames(rootfs);
        String nativeLibDir = app.getApplicationInfo().nativeLibraryDir;
        File prootBinary = new File(nativeLibDir, "libproot.so");
        File loaderBinary = new File(nativeLibDir, "libloader.so");
        File loader32Binary = new File(nativeLibDir, "libloader32.so");
        if (!prootBinary.isFile()) {
            throw new IOException("PRoot not found: " + prootBinary.getAbsolutePath());
        }
        if (!loaderBinary.isFile()) {
            throw new IOException("PRoot loader not found: " + loaderBinary.getAbsolutePath());
        }

        File tmpDir = new File(app.getCacheDir(), "proot-gui-tmp/" + System.nanoTime());
        if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) {
            throw new IOException("Cannot create " + tmpDir.getAbsolutePath());
        }
        File guestTmp = new File(app.getFilesDir(), "debian/tmp");
        if (!guestTmp.isDirectory() && !guestTmp.mkdirs() && !guestTmp.isDirectory()) {
            throw new IOException("Cannot create " + guestTmp.getAbsolutePath());
        }
        File fake = prepareFakeProc(app);

        List<String> args = new ArrayList<>();
        args.add(prootBinary.getAbsolutePath());
        args.add("--ashmem-memfd");
        args.add("--link2symlink");
        args.add("-L");
        if (killOnExit) {
            args.add("--kill-on-exit");
        }
        args.add("--sysvipc");
        args.add("--kernel-release=6.2.1-terbian");
        args.add("-0");
        args.add("-r");
        args.add(rootfs.getAbsolutePath());
        args.add("-b");
        args.add("/dev");
        args.add("-b");
        args.add("/dev/urandom:/dev/random");
        args.add("-b");
        args.add("/proc");
        args.add("-b");
        args.add("/proc/self/fd:/dev/fd");
        args.add("-b");
        args.add("/sys");
        args.add("-b");
        args.add(guestTmp.getAbsolutePath() + ":/tmp");

        File guestShm = new File(app.getFilesDir(), "debian/shm");
        if (!guestShm.isDirectory() && !guestShm.mkdirs() && !guestShm.isDirectory()) {
            throw new IOException("Cannot create " + guestShm.getAbsolutePath());
        }
        guestShm.setReadable(true, false);
        guestShm.setWritable(true, false);
        guestShm.setExecutable(true, false);
        args.add("-b");
        args.add(guestShm.getAbsolutePath() + ":/dev/shm");
        bindFake(args, fake, "loadavg", "/proc/loadavg", true);
        bindFake(args, fake, "stat", "/proc/stat", true);
        bindFake(args, fake, "uptime", "/proc/uptime", true);
        bindFake(args, fake, "version", "/proc/version", true);
        bindFake(args, fake, "vmstat", "/proc/vmstat", true);
        bindFake(args, fake, "cap_last_cap", "/proc/sys/kernel/cap_last_cap", true);
        bindFake(args, fake, "max_user_watches", "/proc/sys/fs/inotify/max_user_watches", true);
        bindIfExists(args, "/storage/emulated/0", "/sdcard");
        bindIfExists(args, "/storage/emulated/0", "/storage/emulated/0");
        File appData = app.getFilesDir().getParentFile();
        if (appData != null) {
            args.add("-b");
            args.add(appData.getAbsolutePath() + ":/" + app.getPackageName().replace('.', '_'));
        }
        args.add("-w");
        args.add("/root");
        args.add("/usr/bin/env");
        args.add("-i");
        args.add("HOME=/root");
        args.add("USER=root");
        args.add("LOGNAME=root");
        args.add("SHELL=/bin/bash");
        args.add("TERM=xterm-256color");
        args.add("LANG=C.UTF-8");
        args.add("LC_ALL=C.UTF-8");
        args.add("TMPDIR=/tmp");
        args.add("DEBIAN_FRONTEND=noninteractive");

        args.add("MOZ_FAKE_NO_SANDBOX=1");
        args.add("MOZ_DISABLE_CONTENT_SANDBOX=1");
        args.add("MOZ_DISABLE_GMP_SANDBOX=1");
        args.add("MOZ_DISABLE_RDD_SANDBOX=1");
        args.add("MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1");
        args.add("LIBGL_ALWAYS_SOFTWARE=1");
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        args.add("/bin/bash");
        args.add("-lc");
        args.add(command);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
        pb.environment().put("PROOT_LOADER", loaderBinary.getAbsolutePath());
        pb.environment().put("LD_LIBRARY_PATH", nativeLibDir);
        if (loader32Binary.isFile()) {
            pb.environment().put("PROOT_LOADER_32", loader32Binary.getAbsolutePath());
        }

        Log.i(LOG_TAG, "start killOnExit=" + killOnExit + " command=" + command);
        Process process = pb.start();
        RunningGuest guest = new RunningGuest(process, tmpDir);
        Thread reader = new Thread(() -> {
            try (BufferedReader buffered = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = buffered.readLine()) != null) {
                    guest.addLine(line);
                    if (listener != null) {
                        listener.onLine(line);
                    }
                }
            } catch (IOException e) {
                Log.i(LOG_TAG, "reader closed: " + e.getMessage());
            } finally {
                guest.markReaderDone();
            }
        }, "Terbian-GuestReader");
        reader.setDaemon(true);
        reader.start();
        return guest;
    }

    private static void bindIfExists(List<String> args, String source, String target) {
        if (new File(source).exists()) {
            args.add("-b");
            args.add(source + ":" + target);
        }
    }

    private static boolean readable(String path) {
        File file = new File(path);
        return file.isFile() && file.canRead();
    }

    private static void bindFake(
            List<String> args, File fakeDir, String name, String target, boolean onlyIfUnreadable) {
        if (onlyIfUnreadable && readable(target)) {
            return;
        }
        File src = new File(fakeDir, name);
        if (!src.isFile()) {
            return;
        }
        args.add("-b");
        args.add(src.getAbsolutePath() + ":" + target);
    }

    private static File prepareFakeProc(Context context) {
        File dir = new File(context.getFilesDir(), "proc-fake");
        dir.mkdirs();
        writeIfMissing(new File(dir, "loadavg"), "0.12 0.07 0.02 2/165 765\n");
        writeIfMissing(new File(dir, "stat"),
                "cpu  1957 0 2877 93280 262 342 254 87 0 0\n"
                        + "cpu0 31 0 226 12027 82 10 8 0 0 0\n"
                        + "intr 63361 0 0 0 0 0 0 0 0 0\n"
                        + "ctxt 38014093\nbtime 1694292441\nprocesses 26442\n"
                        + "procs_running 1\nprocs_blocked 0\nsoftirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677\n");
        writeIfMissing(new File(dir, "uptime"), "124.08 932.80\n");
        writeIfMissing(new File(dir, "version"),
                "Linux version 6.2.1 (terbian@android) (gcc 12.2.0) #1 SMP PREEMPT\n");
        writeIfMissing(new File(dir, "vmstat"),
                "nr_free_pages 1743136\nnr_zone_inactive_anon 179281\nnr_zone_active_anon 7183\n"
                        + "nr_zone_inactive_file 22858\nnr_zone_active_file 51328\npgpgin 0\npgpgout 0\n");
        writeIfMissing(new File(dir, "cap_last_cap"), "40\n");
        writeIfMissing(new File(dir, "max_user_watches"), "4096\n");
        return dir;
    }

    private static void writeIfMissing(File file, String content) {
        if (file.isFile() && file.length() > 0) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(LOG_TAG, "cannot write " + file, e);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
