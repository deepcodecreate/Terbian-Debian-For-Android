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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AptSources {
    public static final String DEBIAN = "debian";
    public static final String UBUNTU = "ubuntu";

    private final File rootfs;
    private final String distro;
    private final String codename;

    private AptSources(File rootfs, String distro, String codename) {
        this.rootfs = rootfs;
        this.distro = distro;
        this.codename = codename;
    }

    public static AptSources open(File rootfs) throws IOException {
        String osRelease = readText(new File(rootfs, "etc/os-release"));
        String id = field(osRelease, "ID");
        String codename = field(osRelease, "VERSION_CODENAME");
        if (codename.isEmpty()) {
            codename = field(osRelease, "UBUNTU_CODENAME");
        }
        if (codename.isEmpty()) {
            codename = DEBIAN.equals(id) ? "stable" : "";
        }
        String distro = UBUNTU.equals(id) ? UBUNTU : DEBIAN;
        return new AptSources(rootfs, distro, codename);
    }

    public String distro() {
        return distro;
    }

    public String codename() {
        return codename;
    }

    public String guestFile() {
        return "/" + target().getAbsolutePath().substring(rootfs.getAbsolutePath().length() + 1);
    }

    public String currentMirror() {
        try {
            String text = readText(target());
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.startsWith("deb ") || t.startsWith("URIs:")) {
                    for (String part : t.split("\\s+")) {
                        if (part.startsWith("http://") || part.startsWith("https://")) {
                            return part;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    public static boolean validUrl(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (!(v.startsWith("http://") || v.startsWith("https://"))) return false;
        if (v.length() < 10) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c <= ' ' || c == '"' || c == '\'' || c == '`' || c == '\\' || c == '<' || c == '>') {
                return false;
            }
        }
        return v.indexOf('.', v.indexOf("//") + 2) > 0 || v.contains("localhost");
    }

    public String apply(String mirror, boolean armPorts) throws IOException {
        String base = mirror.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        File file = target();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        String backupName = null;
        if (file.isFile()) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File backup = new File(file.getParentFile(), file.getName() + ".terbian-" + stamp + ".bak");
            copy(file, backup);
            backupName = "/" + backup.getAbsolutePath().substring(rootfs.getAbsolutePath().length() + 1);
        }
        boolean deb822 = file.getName().endsWith(".sources");
        String content = deb822 ? deb822(base, armPorts) : classic(base, armPorts);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return backupName == null ? "-" : backupName;
    }

    public String defaultMirror(boolean armPorts) {
        if (UBUNTU.equals(distro)) {
            return armPorts ? "http://ports.ubuntu.com/ubuntu-ports" : "http://archive.ubuntu.com/ubuntu";
        }
        return "https://deb.debian.org/debian";
    }

    public String resetToDefault(boolean armPorts) throws IOException {
        return apply(defaultMirror(armPorts), armPorts);
    }

    public String guestDir() {
        String f = guestFile();
        int i = f.lastIndexOf('/');
        return i <= 0 ? "/" : f.substring(0, i);
    }

    public String guestFileName() {
        String f = guestFile();
        return f.substring(f.lastIndexOf('/') + 1);
    }

    private File target() {
        File list = new File(rootfs, "etc/apt/sources.list");
        File d = new File(rootfs, "etc/apt/sources.list.d/" + distro + ".sources");
        if (d.isFile()) {
            return d;
        }
        return list;
    }

    private String components() {
        if (UBUNTU.equals(distro)) {
            return "main restricted universe multiverse";
        }
        boolean old = "buster".equals(codename) || "bullseye".equals(codename) || "stretch".equals(codename);
        return old ? "main contrib non-free" : "main contrib non-free non-free-firmware";
    }

    private String security(boolean armPorts) {
        if (UBUNTU.equals(distro)) {
            return armPorts ? "http://ports.ubuntu.com/ubuntu-ports" : "http://security.ubuntu.com/ubuntu";
        }
        return "https://deb.debian.org/debian-security";
    }

    private String classic(String base, boolean armPorts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Written by Terbian. Edit with nano or change the mirror in Settings > Repositories.\n");
        String c = codename;
        if (UBUNTU.equals(distro)) {
            sb.append("deb ").append(base).append(' ').append(c).append(' ').append(components()).append('\n');
            sb.append("deb ").append(base).append(' ').append(c).append("-updates ").append(components()).append('\n');
            sb.append("deb ").append(base).append(' ').append(c).append("-backports ").append(components()).append('\n');
            sb.append("deb ").append(security(armPorts)).append(' ').append(c).append("-security ")
                    .append(components()).append('\n');
        } else {
            sb.append("deb ").append(base).append(' ').append(c).append(' ').append(components()).append('\n');
            sb.append("deb ").append(base).append(' ').append(c).append("-updates ").append(components()).append('\n');
            sb.append("deb ").append(security(false)).append(' ').append(c).append("-security ")
                    .append(components()).append('\n');
        }
        return sb.toString();
    }

    private String deb822(String base, boolean armPorts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Written by Terbian. Edit with nano or change the mirror in Settings > Repositories.\n");
        String keyring = UBUNTU.equals(distro)
                ? "/usr/share/keyrings/ubuntu-archive-keyring.gpg"
                : "/usr/share/keyrings/debian-archive-keyring.gpg";
        String suites = UBUNTU.equals(distro)
                ? codename + " " + codename + "-updates " + codename + "-backports"
                : codename + " " + codename + "-updates";
        sb.append("Types: deb\nURIs: ").append(base).append("\nSuites: ").append(suites)
                .append("\nComponents: ").append(components())
                .append("\nSigned-By: ").append(keyring).append("\n\n");
        sb.append("Types: deb\nURIs: ").append(security(armPorts)).append("\nSuites: ").append(codename)
                .append("-security\nComponents: ").append(components())
                .append("\nSigned-By: ").append(keyring).append('\n');
        return sb.toString();
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static String readText(File file) throws IOException {
        if (!file.isFile()) {
            return "";
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String field(String osRelease, String key) {
        for (String line : osRelease.split("\n")) {
            if (line.startsWith(key + "=")) {
                String v = line.substring(key.length() + 1).trim();
                if (v.length() >= 2 && (v.startsWith("\"") || v.startsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return "";
    }
}
