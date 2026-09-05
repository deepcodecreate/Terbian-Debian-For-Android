package com.terbian.debian;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ProotExec {

    private static final String LOG_TAG = "TerbianProotExec";
    private static final int TAIL_LINES = 80;

    private ProotExec() {
    }

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

    public static Result run(Context context, File guestRoot, List<String> extraBinds, String command)
            throws IOException, InterruptedException {
        return run(context, guestRoot, extraBinds, command, null);
    }

    public static Result run(
            Context context,
            File guestRoot,
            List<String> extraBinds,
            String command,
            LineListener lineListener)
            throws IOException, InterruptedException {

        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;

        File prootBinary = new File(nativeLibDir, "libproot.so");
        if (!prootBinary.isFile()) {
            throw new IOException("PRoot not found: " + prootBinary.getAbsolutePath());
        }
        File loaderBinary = new File(nativeLibDir, "libloader.so");
        if (!loaderBinary.isFile()) {
            throw new IOException("PRoot loader not found: " + loaderBinary.getAbsolutePath());
        }
        File loader32Binary = new File(nativeLibDir, "libloader32.so");

        File tmpDir = new File(context.getCacheDir(), "proot-exec-tmp/" + System.nanoTime());
        tmpDir.mkdirs();

        List<String> cmd = new ArrayList<>();
        cmd.add(prootBinary.getAbsolutePath());
        cmd.add("--kill-on-exit");
        cmd.add("-0");

        cmd.add("--link2symlink");
        cmd.add("-r");
        cmd.add(guestRoot.getAbsolutePath());
        cmd.add("-b");
        cmd.add("/dev");
        cmd.add("-b");
        cmd.add("/proc");
        cmd.add("-b");
        cmd.add("/sys");

        cmd.add("-b");
        cmd.add(context.getFilesDir().getParentFile().getAbsolutePath());
        if (extraBinds != null) {
            for (String bind : extraBinds) {
                cmd.add("-b");
                cmd.add(bind);
            }
        }
        cmd.add("-w");
        cmd.add("/");
        cmd.add("/system/bin/sh");
        cmd.add("-c");
        cmd.add(command);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
        pb.environment().put("PROOT_LOADER", loaderBinary.getAbsolutePath());
        pb.environment().put("LD_LIBRARY_PATH", nativeLibDir);
        if (loader32Binary.isFile()) {
            pb.environment().put("PROOT_LOADER_32", loader32Binary.getAbsolutePath());
        }
        pb.environment()
                .put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin");

        Log.i(LOG_TAG, "exec: " + cmd);

        Process process = pb.start();
        ArrayDeque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineListener != null) lineListener.onLine(line);
                tail.addLast(line);
                if (tail.size() > TAIL_LINES) tail.removeFirst();
            }
        }
        int exitCode = process.waitFor();
        deleteRecursive(tmpDir);

        String tailOutput = String.join("\n", tail);
        Log.i(LOG_TAG, "exit=" + exitCode);
        return new Result(exitCode, tailOutput);
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
}
