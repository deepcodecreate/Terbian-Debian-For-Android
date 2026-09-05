package com.terbian.terminal;

import android.content.Context;
import android.util.Log;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
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

        List<String> args = new ArrayList<>();
        args.add("proot");

        args.add("--link2symlink");
        args.add("-L");
        args.add("--kill-on-exit");

        args.add("--sysvipc");
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
        args.add("/sys");
        args.add("-b");
        args.add("/storage/emulated/0:/sdcard");
        args.add("-b");
        args.add(context.getFilesDir().getParentFile().getAbsolutePath()
                + ":/" + context.getPackageName().replace('.', '_'));
        args.add("-b");
        args.add("/storage/emulated/0:/storage/emulated/0");
        args.add("-w");
        args.add("/root");
        args.add("/usr/bin/env");
        args.add("-i");
        args.add("HOME=/root");
        args.add("USER=root");
        args.add("LOGNAME=root");
        args.add("TERM=xterm-256color");
        args.add("LANG=C.UTF-8");
        args.add("LC_ALL=C.UTF-8");
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
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
