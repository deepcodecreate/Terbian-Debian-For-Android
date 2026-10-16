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

package com.terbian.terminal;

import android.content.Context;
import android.util.Log;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ProotSessionFactory {
    private static final String LOG_TAG = "TerbianProotSession";
    private static final String PROOT_LIBRARY_NAME = "libproot.so";
    private static final String LOADER_LIBRARY_NAME = "libloader.so";
    private static final String LOADER32_LIBRARY_NAME = "libloader32.so";

    private ProotSessionFactory() {
    }

    public static TerminalSession createProotSession(
            Context context, File rootfsDir, String loginShell, TerminalSessionClient client) {
        try {
            return createProotSessionInternal(context, rootfsDir, loginShell, client);
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, e.getMessage());
            throw e;
        } catch (Throwable t) {
            Log.e(LOG_TAG, "unexpected failure creating proot session", t);
            throw new IllegalStateException("Unexpected error starting Debian: " + t, t);
        }
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
        writeIfMissing(new File(dir, "empty"), "");
        return dir;
    }

    private static void writeIfMissing(File f, String content) {
        if (f.isFile() && f.length() > 0) return;
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(LOG_TAG, "cannot write " + f, e);
        }
    }

    private static boolean readable(String path) {
        File f = new File(path);
        return f.isFile() && f.canRead();
    }

    private static void bindFake(List<String> args, File fakeDir, String name, String target, boolean onlyIfUnreadable) {
        if (onlyIfUnreadable && readable(target)) return;
        File src = new File(fakeDir, name);
        if (!src.isFile()) return;
        args.add("-b");
        args.add(src.getAbsolutePath() + ":" + target);
    }

    private static TerminalSession createProotSessionInternal(
            Context context, File rootfsDir, String loginShell, TerminalSessionClient client) {
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        Log.i(LOG_TAG, "nativeLibDir=" + nativeLibDir);

        File prootBinary = new File(nativeLibDir, PROOT_LIBRARY_NAME);
        if (!prootBinary.isFile()) {
            throw new IllegalStateException("PRoot not found: " + prootBinary.getAbsolutePath());
        }

        File loaderBinary = new File(nativeLibDir, LOADER_LIBRARY_NAME);
        if (!loaderBinary.isFile()) {
            throw new IllegalStateException("PRoot loader not found: " + loaderBinary.getAbsolutePath());
        }

        if (!rootfsDir.isDirectory()) {
            throw new IllegalStateException("Debian rootfs not found: " + rootfsDir.getAbsolutePath());
        }

        File tmpDir = new File(context.getCacheDir(), "proot-tmp/" + System.currentTimeMillis());
        tmpDir.mkdirs();

        File guestTmp = new File(context.getFilesDir(), "debian/tmp");
        guestTmp.mkdirs();
        File fake = prepareFakeProc(context);

        List<String> args = new ArrayList<>();
        args.add("proot");

        args.add("--ashmem-memfd");
        args.add("--link2symlink");
        args.add("-L");
        args.add("--kill-on-exit");
        args.add("--sysvipc");
        args.add("--kernel-release=6.2.1-terbian");
        args.add("-0");
        args.add("-r");
        args.add(rootfsDir.getAbsolutePath());

        args.add("-b");
        args.add("/dev");
        args.add("-b");
        args.add("/dev/urandom:/dev/random");
        args.add("-b");
        args.add("/proc");
        args.add("-b");
        args.add("/proc/self/fd:/dev/fd");
        args.add("-b");
        args.add("/proc/self/fd/0:/dev/stdin");
        args.add("-b");
        args.add("/proc/self/fd/1:/dev/stdout");
        args.add("-b");
        args.add("/proc/self/fd/2:/dev/stderr");
        args.add("-b");
        args.add("/sys");
        args.add("-b");
        args.add(guestTmp.getAbsolutePath() + ":/tmp");

        bindFake(args, fake, "loadavg", "/proc/loadavg", true);
        bindFake(args, fake, "stat", "/proc/stat", true);
        bindFake(args, fake, "uptime", "/proc/uptime", true);
        bindFake(args, fake, "version", "/proc/version", true);
        bindFake(args, fake, "vmstat", "/proc/vmstat", true);
        bindFake(args, fake, "cap_last_cap", "/proc/sys/kernel/cap_last_cap", true);
        bindFake(args, fake, "max_user_watches", "/proc/sys/fs/inotify/max_user_watches", true);

        args.add("-b");
        args.add("/storage/emulated/0:/sdcard");
        args.add("-b");
        args.add("/storage/emulated/0:/storage/emulated/0");
        args.add("-b");
        args.add(context.getFilesDir().getParentFile().getAbsolutePath()
                + ":/" + context.getPackageName().replace('.', '_'));
        args.add("-w");
        args.add("/root");
        args.add("/usr/bin/env");
        args.add("-i");
        args.add("HOME=/root");
        args.add("USER=root");
        args.add("LOGNAME=root");
        args.add("SHELL=/bin/bash");
        args.add("TERM=xterm-256color");
        args.add("COLORTERM=truecolor");
        args.add("LANG=C.UTF-8");
        args.add("LC_ALL=C.UTF-8");
        args.add("TMPDIR=/tmp");
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games");
        args.add(loginShell == null ? "/bin/bash" : loginShell);

        args.add("--login");

        List<String> env = new ArrayList<>();
        env.add("PROOT_TMP_DIR=" + tmpDir.getAbsolutePath());
        env.add("PROOT_LOADER=" + loaderBinary.getAbsolutePath());
        env.add("LD_LIBRARY_PATH=" + nativeLibDir);

        File loader32 = new File(nativeLibDir, LOADER32_LIBRARY_NAME);
        if (loader32.isFile()) {
            env.add("PROOT_LOADER_32=" + loader32.getAbsolutePath());
        }

        Log.i(LOG_TAG, "args=" + args);

        TerminalSession session = new TerminalSession(
                prootBinary.getAbsolutePath(),
                rootfsDir.getAbsolutePath(),
                args.toArray(new String[0]),
                env.toArray(new String[0]),
                null,
                client);

        Log.i(LOG_TAG, "TerminalSession created (real pty via libtermux.so)");
        return session;
    }
}
