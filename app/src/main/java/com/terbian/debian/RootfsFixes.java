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

import com.terbian.terminal.PromptStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RootfsFixes {
    private RootfsFixes() {
    }

    public static void ensureGroupNames(File rootfs) {
        try {
            Set<Integer> wanted = ownGroupIds();
            if (wanted.isEmpty()) {
                return;
            }
            File groupFile = new File(rootfs, "etc/group");
            String text = groupFile.isFile() ? read(groupFile) : "";
            Set<Integer> known = new HashSet<>();
            for (String line : text.split("\n")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    try {
                        known.add(Integer.parseInt(parts[2].trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            StringBuilder add = new StringBuilder();
            for (int gid : wanted) {
                if (!known.contains(gid)) {
                    add.append("aid_").append(gid).append(":x:").append(gid).append(":\n");
                }
            }
            if (add.length() == 0) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(groupFile, true)) {
                if (!text.isEmpty() && !text.endsWith("\n")) {
                    out.write('\n');
                }
                out.write(add.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static final String[] TOOLS = {
            "terbian-setup", "terbian-gui", "terbian-appimage", "terbian-help", "terbian-doctor"};

    public static void refreshTools(Context context, File rootfs) {
        try {
            File bin = new File(rootfs, "usr/local/bin");
            if (!bin.isDirectory() && !bin.mkdirs()) {
                return;
            }
            for (String tool : TOOLS) {
                copyAsset(context, "debian-tools/" + tool, new File(bin, tool));
            }
            File profileD = new File(rootfs, "etc/profile.d");
            if (profileD.isDirectory() || profileD.mkdirs()) {
                copyAsset(context, "debian-tools/terbian-firstrun.sh", new File(profileD, "terbian-firstrun.sh"));
            }
        } catch (Exception ignored) {
        }
    }

    private static final String PROMPT_FILE = ".terbian_prompt";
    private static final String PROMPT_HOOK_MARK = "# terbian-prompt";

    public static void applyPrompt(Context context, File rootfs, PromptStyle style) {
        try {
            File home = new File(rootfs, "root");
            if (!home.isDirectory() && !home.mkdirs()) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(new File(home, PROMPT_FILE), false)) {
                out.write(style.bashScript().getBytes(StandardCharsets.UTF_8));
            }
            File bashrc = new File(home, ".bashrc");
            String text = bashrc.isFile() ? read(bashrc) : "";
            if (text.contains(PROMPT_HOOK_MARK)) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(bashrc, true)) {
                String hook = (text.isEmpty() || text.endsWith("\n") ? "" : "\n")
                        + "\n" + PROMPT_HOOK_MARK + "\n"
                        + "[ -f \"$HOME/" + PROMPT_FILE + "\" ] && . \"$HOME/" + PROMPT_FILE + "\"\n";
                out.write(hook.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static void copyAsset(Context context, String asset, File dest) {
        try (InputStream in = context.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(dest, false)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            try {
                Os.chmod(dest.getAbsolutePath(), 0755);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private static Set<Integer> ownGroupIds() throws Exception {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String line : read(new File("/proc/self/status")).split("\n")) {
            if (line.startsWith("Groups:") || line.startsWith("Gid:")) {
                for (String token : line.substring(line.indexOf(':') + 1).trim().split("\\s+")) {
                    if (!token.isEmpty()) {
                        try {
                            ids.add(Integer.parseInt(token));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return ids;
    }

    private static String read(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
