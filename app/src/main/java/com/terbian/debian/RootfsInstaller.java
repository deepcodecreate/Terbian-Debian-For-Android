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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class RootfsInstaller {
    public interface Listener {
        void onProgress(long processedBytes, long totalBytes);
        void onMessage(String message);
        void onComplete(boolean success, String message);
    }

    private static final int TAR_BLOCK_SIZE = 512;

    private final StringBuilder installLog = new StringBuilder();
    private int fileCount, dirCount, symlinkCount, hardLinkCount, hardLinkCopied;
    private long byteCount, extractMillis;

    private final Context context;
    private final File rootfsDirectory;

    public RootfsInstaller(Context context) {
        this.context = context.getApplicationContext();
        this.rootfsDirectory = new File(this.context.getFilesDir(), "debian/rootfs");
    }

    public void installAsync(Listener listener) {
        new Thread(() -> {
            try {
                install(listener);
                if (listener != null) {
                    listener.onComplete(true, "Debian rootfs installed successfully.");
                }
            } catch (Exception e) {
                if (listener != null) {
                    String message = e.getMessage();
                    if (message == null || message.isEmpty()) {
                        message = e.getClass().getSimpleName();
                    }
                    listener.onComplete(false, "Rootfs installation failed: " + message);
                }
            }
        }, "Terbian-RootfsInstaller").start();
    }

    private void install(Listener listener) throws Exception {
        File debianDirectory = rootfsDirectory.getParentFile();
        if (debianDirectory == null) {
            throw new IOException("Invalid Debian directory");
        }
        if (!debianDirectory.exists() && !debianDirectory.mkdirs() && !debianDirectory.exists()) {
            throw new IOException("Cannot create Debian directory");
        }

        File temporaryRootfs = new File(debianDirectory, "rootfs_installing");

        if (temporaryRootfs.exists()) {
            notify(listener, "Removing incomplete installation...\n");
            deleteRecursive(temporaryRootfs);
        }

        if (!temporaryRootfs.mkdirs() && !temporaryRootfs.exists()) {
            throw new IOException("Cannot create temporary rootfs directory");
        }

        makeWritable(temporaryRootfs);

        notify(listener, "Extracting Debian rootfs...\n\n");

        long startMs = System.currentTimeMillis();
        try (InputStream assetInput = context.getAssets().open("debian/rootfs.tb");
             BufferedInputStream bufferedInput = new BufferedInputStream(assetInput, 64 * 1024);
             GZIPInputStream gzipInput = new GZIPInputStream(bufferedInput, 64 * 1024)) {
            extractTar(gzipInput, temporaryRootfs, listener);
        }
        extractMillis = System.currentTimeMillis() - startMs;

        notify(listener, "\nExtraction finished.\n");
        notify(listener, "Verifying Debian filesystem...\n");

        verifyRootfs(temporaryRootfs);
        writeEnvironmentDefaults(temporaryRootfs);
        installTerbianTools(temporaryRootfs);
        String summary = "Files: " + fileCount + "  Directories: " + dirCount
                + "  Symlinks: " + symlinkCount + "  Hard links: " + hardLinkCount
                + "  (copied instead: " + hardLinkCopied + ")\n"
                + "Data written: " + (byteCount / (1024 * 1024)) + " MiB in " + (extractMillis / 1000.0) + " s\n";
        notify(listener, "\n" + summary);
        writeInstallLog(temporaryRootfs, summary);
        File marker = new File(temporaryRootfs, "etc/terbian-rootfs-v2");
        writeTextFile(marker, "terbian rootfs v2\n");

        if (rootfsDirectory.exists()) {
            notify(listener, "Removing old rootfs...\n");
            deleteRecursive(rootfsDirectory);
        }

        if (!temporaryRootfs.renameTo(rootfsDirectory)) {
            copyDirectory(temporaryRootfs, rootfsDirectory);
            deleteRecursive(temporaryRootfs);
        }

        notify(listener, "Debian rootfs verification complete.\n");
    }

    private void writeInstallLog(File rootfsDir, String summary) throws IOException {
        File dir = new File(rootfsDir, "root/.terbian");
        createDirectory(dir);
        String header = "== Terbian: Debian rootfs installation report ==\n"
                + "ABI: " + ArchitectureDetector.getAbi() + "\n";
        writeTextFile(new File(dir, "install.log"),
                header + installLog + "\n" + summary + "== end of report (cat /root/.terbian/install.log) ==\n");
    }

    private void installTerbianTools(File rootfsDir) throws IOException {
        File bin = new File(rootfsDir, "usr/local/bin");
        createDirectory(bin);
        String[] tools = {"terbian-setup", "terbian-gui", "terbian-appimage", "terbian-help", "terbian-doctor"};
        for (String t : tools) {
            copyAssetToFile("debian-tools/" + t, new File(bin, t));
            makeWritable(new File(bin, t));
        }
        File profileD = new File(rootfsDir, "etc/profile.d");
        createDirectory(profileD);
        File fr = new File(profileD, "terbian-firstrun.sh");
        copyAssetToFile("debian-tools/terbian-firstrun.sh", fr);
        makeWritable(fr);
    }

    private void copyAssetToFile(String assetPath, File dest) throws IOException {
        try (InputStream in = context.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private void extractTarWithProot(File tarGzFile, File destination, Listener listener) throws Exception {
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Cannot create extraction target: " + destination);
        }
        String quotedTar = shellQuote(tarGzFile.getAbsolutePath());
        String quotedDest = shellQuote(destination.getAbsolutePath());
        String command = "tar -xzf " + quotedTar + " -C " + quotedDest;

        List<String> extraBinds = new ArrayList<>();
        extraBinds.add(tarGzFile.getParentFile().getAbsolutePath());
        extraBinds.add(destination.getAbsolutePath());

        ProotExec.Result result = ProotExec.run(
                context,
                new File("/"),
                extraBinds,
                command,
                line -> notify(listener, line + "\n"));

        if (!result.isSuccess()) {
            throw new IOException("tar exited with code " + result.exitCode + ":\n" + result.tailOutput);
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void notify(Listener listener, String message) {
        if (message != null && !message.startsWith("Installing: ")) installLog.append(message);
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    private static final class PendingLink {
        final String name;
        final String linkName;

        PendingLink(String name, String linkName) {
            this.name = name;
            this.linkName = linkName;
        }
    }

    private void extractTar(InputStream input, File destination, Listener listener) throws Exception {
        byte[] header = new byte[TAR_BLOCK_SIZE];
        String pendingLongName = null;
        List<PendingLink> pendingHardLinks = new ArrayList<>();

        while (true) {
            readFully(input, header);

            if (isEmptyBlock(header)) {
                break;
            }

            String name = readString(header, 0, 100);
            String mode = readString(header, 100, 8);
            String sizeText = readString(header, 124, 12);
            String type = readString(header, 156, 1);
            String linkName = readString(header, 157, 100);
            String prefix = readString(header, 345, 155);

            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }

            long size = parseOctal(sizeText);

            if ("L".equals(type)) {
                byte[] longName = readBytes(input, size);
                pendingLongName = new String(longName, StandardCharsets.UTF_8).replace("\0", "");
                skipPadding(input, size);
                continue;
            }

            if ("K".equals(type)) {
                skipFully(input, size);
                skipPadding(input, size);
                continue;
            }

            if (pendingLongName != null) {
                name = pendingLongName;
                pendingLongName = null;
            }

            if ("x".equals(type) || "g".equals(type)) {
                skipFully(input, size);
                skipPadding(input, size);
                continue;
            }

            File output = secureResolve(destination, name);
            notify(listener, "Installing: " + name + "\n");

            if ("5".equals(type) || name.endsWith("/")) {
                createDirectory(output);
                dirCount++;
                makeWritable(output);
                skipFully(input, size);
                skipPadding(input, size);
                continue;
            }

            if ("2".equals(type)) {
                createParent(output);
                deleteIfExists(output);
                try {
                    Os.symlink(linkName, output.getAbsolutePath());
                    symlinkCount++;
                } catch (Exception e) {
                    throw new IOException("Cannot create symlink: " + name + " -> " + linkName, e);
                }
                skipFully(input, size);
                skipPadding(input, size);
                continue;
            }

            if ("1".equals(type)) {
                pendingHardLinks.add(new PendingLink(name, linkName));
                skipFully(input, size);
                skipPadding(input, size);
                continue;
            }

            if ("0".equals(type) || type.length() == 0) {
                createParent(output);
                writeFile(input, output, size);
                fileCount++;
                byteCount += size;
                applyMode(output, mode);
                skipPadding(input, size);
                continue;
            }

            skipFully(input, size);
            skipPadding(input, size);
        }

        resolvePendingHardLinks(destination, pendingHardLinks, listener);
    }

    private void resolvePendingHardLinks(File destination, List<PendingLink> pendingHardLinks, Listener listener)
            throws IOException {
        for (PendingLink link : pendingHardLinks) {
            File output = secureResolve(destination, link.name);
            File target = secureResolve(destination, link.linkName);

            createParent(output);
            deleteIfExists(output);

            hardLinkCount++;
            if (target.isFile()) {
                copyFile(target, output);
                hardLinkCopied++;
                try { Os.chmod(output.getAbsolutePath(), Os.stat(target.getAbsolutePath()).st_mode & 0777); } catch (Exception ignored) { }
            } else {
                notify(listener, "Warning: missing hard link target for " + link.name + " -> " + link.linkName + "\n");
            }
        }
    }

    private File secureResolve(File destination, String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty()) {
            throw new IOException("Invalid TAR entry");
        }

        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        File result = new File(destination, normalized);
        String basePath = destination.getCanonicalPath();
        String resultPath = result.getCanonicalPath();

        if (!resultPath.equals(basePath) && !resultPath.startsWith(basePath + File.separator)) {
            throw new IOException("Unsafe TAR path: " + entryName);
        }

        return result;
    }

    private void createDirectory(File directory) throws IOException {
        if (directory.exists()) {
            return;
        }
        if (!directory.mkdirs() && !directory.exists()) {
            throw new IOException("Cannot create directory: " + directory);
        }
    }

    private void createParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            createDirectory(parent);
            makeWritable(parent);
        }
    }

    private void writeFile(InputStream input, File output, long size) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output), 64 * 1024)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                int requested = (int) Math.min(buffer.length, remaining);
                int count = input.read(buffer, 0, requested);
                if (count == -1) {
                    throw new IOException("Unexpected end of TAR");
                }
                out.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

    private void applyMode(File file, String modeText) {
        try {
            long mode = parseOctal(modeText);
            int permissions = (int) (mode & 0777);
            Os.chmod(file.getAbsolutePath(), permissions);
        } catch (Exception ignored) {
        }
    }

    private void deleteRecursive(File fileOrDirectory) throws IOException {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return;
        }

        makeWritable(fileOrDirectory);

        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        if (!fileOrDirectory.delete() && fileOrDirectory.exists()) {
            throw new IOException("Cannot delete: " + fileOrDirectory.getAbsolutePath());
        }
    }

    private void deleteIfExists(File file) throws IOException {
        if (file.exists()) {
            deleteRecursive(file);
        }
    }

    private void makeWritable(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        file.setWritable(true, false);
        file.setReadable(true, false);
        file.setExecutable(true, false);
        try {
            Os.chmod(file.getAbsolutePath(), 0755);
        } catch (Exception ignored) {
        }
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            createDirectory(target);
            File[] files = source.listFiles();
            if (files != null) {
                for (File file : files) {
                    copyDirectory(file, new File(target, file.getName()));
                }
            }
        } else {
            copyFile(source, target);
        }
    }

    private void copyFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void writeEnvironmentDefaults(File rootfsDir) throws IOException {
        File aptConfDir = new File(rootfsDir, "etc/apt/apt.conf.d");
        createDirectory(aptConfDir);

        writeTextFile(new File(aptConfDir, "99terbian"),
                "APT::Get::Assume-Yes \"true\";\n"
                        + "APT::Install-Recommends \"false\";\n"
                        + "Acquire::Retries \"3\";\n"
                        + "Acquire::ForceIPv4 \"true\";\n"
                        + "Acquire::Check-Valid-Until \"false\";\n"
                        + "APT::Sandbox::User \"root\";\n"
                        + "Dpkg::Options {\n"
                        + "   \"--force-confdef\";\n"
                        + "   \"--force-confold\";\n"
                        + "}\n"
                        + "#clear DPkg::Pre-Install-Pkgs;\n");

        File dpkgConfDir = new File(rootfsDir, "etc/dpkg/dpkg.cfg.d");
        createDirectory(dpkgConfDir);
        writeTextFile(new File(dpkgConfDir, "99terbian"), "force-unsafe-io\n");

        File resolvConf = new File(rootfsDir, "etc/resolv.conf");
        writeTextFile(resolvConf, "nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n");

        File rootDir = new File(rootfsDir, "root");
        createDirectory(rootDir);
        File bashrc = new File(rootDir, ".bashrc");
        String bashrcAddition =
                "\nexport DEBIAN_FRONTEND=noninteractive\n"
                        + "export TERM=xterm-256color\n"
                        + "alias ls='ls --color=auto'\n"
                        + "alias grep='grep --color=auto'\n"
                        + "alias ll='ls -alF --color=auto'\n"
                        + "export TERBIAN_WINSIZE_FILE=/root/.terbian_winsize\n"
                        + "PROMPT_COMMAND='[ -f \"$TERBIAN_WINSIZE_FILE\" ] && . \"$TERBIAN_WINSIZE_FILE\" 2>/dev/null'\n"
                        + "PS1='\\[\\033[01;32m\\]\\u@terbian\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ '\n"
                        + "if [ -t 1 ] && command -v fastfetch >/dev/null 2>&1; then\n"
                        + "    fastfetch\n"
                        + "fi\n";

        if (bashrc.exists()) {
            appendTextFile(bashrc, bashrcAddition);
        } else {
            writeTextFile(bashrc, bashrcAddition);
        }

        makeWritable(bashrc);
    }

    private void writeTextFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        makeWritable(file);
    }

    private void appendTextFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        makeWritable(file);
    }

    private void verifyRootfs(File rootfsDir) throws IOException {
        String[] requiredDirs = {"bin", "etc", "lib", "usr", "var"};
        for (String dir : requiredDirs) {
            File check = new File(rootfsDir, dir);
            if (!check.exists()) {
                throw new IOException("Missing core Debian directory: " + dir);
            }
        }
    }

    private boolean isEmptyBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = in.read(buffer, offset, buffer.length - offset);
            if (count == -1) throw new IOException("Unexpected EOF in TAR stream");
            offset += count;
        }
    }

    private byte[] readBytes(InputStream in, long count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long remaining = count;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) throw new IOException("Unexpected EOF reading TAR bytes");
            out.write(buffer, 0, read);
            remaining -= read;
        }
        return out.toByteArray();
    }

    private String readString(byte[] buffer, int offset, int length) {
        int count = 0;
        while (count < length && buffer[offset + count] != 0) {
            count++;
        }
        return new String(buffer, offset, count, StandardCharsets.UTF_8).trim();
    }

    private long parseOctal(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return 0;
        try {
            return Long.parseLong(trimmed, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) throw new IOException("Unexpected EOF skipping TAR bytes");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private void skipPadding(InputStream in, long size) throws IOException {
        long remainder = size % TAR_BLOCK_SIZE;
        if (remainder > 0) {
            skipFully(in, TAR_BLOCK_SIZE - remainder);
        }
    }
}
