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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LinuxApps {
    private static final String[] DESKTOP_DIRS = {
            "usr/share/applications", "usr/local/share/applications", "root/.local/share/applications",
    };
    private static final String[] APPIMAGE_DIRS = {
            "root", "root/Applications", "root/Downloads", "root/Desktop", "opt", "usr/local/bin", "root/.local/bin",
    };
    private static final String[] ICON_DIRS = {
            "256x256", "192x192", "128x128", "96x96", "64x64", "48x48", "512x512", "72x72", "36x36", "32x32",
    };
    private static final String[] ICON_THEMES = {"hicolor", "Adwaita", "gnome", "AdwaitaLegacy", "elementary-xfce"};

    private LinuxApps() {
    }

    public static final class Entry {
        public final String name;
        public final String command;
        public final String iconName;
        public final boolean appImage;
        public final String source;

        Entry(String name, String command, String iconName, boolean appImage, String source) {
            this.name = name;
            this.command = command;
            this.iconName = iconName;
            this.appImage = appImage;
            this.source = source;
        }
    }

    public static List<Entry> scan(File rootfs) {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String dir : DESKTOP_DIRS) {
            File[] files = new File(rootfs, dir).listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.getName().endsWith(".desktop") || !file.isFile()) {
                    continue;
                }
                Entry entry = parseDesktop(file);
                if (entry != null && seen.add(entry.name.toLowerCase(Locale.ROOT) + "|" + entry.command)) {
                    result.add(entry);
                }
            }
        }
        for (String dir : APPIMAGE_DIRS) {
            File[] files = new File(rootfs, dir).listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (!file.isFile() || !lower.endsWith(".appimage")) {
                    continue;
                }
                String guestPath = "/" + dir + "/" + file.getName();
                String name = file.getName().substring(0, file.getName().length() - ".AppImage".length());
                String command = "chmod +x " + shellQuote(guestPath) + " 2>/dev/null; APPIMAGE_EXTRACT_AND_RUN=1 "
                        + shellQuote(guestPath);
                if (seen.add(name.toLowerCase(Locale.ROOT) + "|" + command)) {
                    result.add(new Entry(name, command, null, true, guestPath));
                }
            }
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    private static Entry parseDesktop(File file) {
        String name = null;
        String exec = null;
        String icon = null;
        String type = "Application";
        boolean hidden = false;
        boolean terminal = false;
        boolean inEntry = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("[")) {
                    inEntry = line.equals("[Desktop Entry]");
                    continue;
                }
                if (!inEntry || line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "Name":
                        name = value;
                        break;
                    case "Exec":
                        exec = value;
                        break;
                    case "Icon":
                        icon = value;
                        break;
                    case "Type":
                        type = value;
                        break;
                    case "NoDisplay":
                    case "Hidden":
                        if (value.equalsIgnoreCase("true")) {
                            hidden = true;
                        }
                        break;
                    case "Terminal":
                        terminal = value.equalsIgnoreCase("true");
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (name == null || exec == null || hidden || terminal || !type.equals("Application")) {
            return null;
        }
        String command = cleanExec(exec);
        if (command.isEmpty()) {
            return null;
        }
        return new Entry(name, command, icon, false, file.getName());
    }

    static String cleanExec(String exec) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < exec.length(); i++) {
            char c = exec.charAt(i);
            if (c == '%' && i + 1 < exec.length()) {
                char next = exec.charAt(i + 1);
                i++;
                if (next == '%') {
                    out.append('%');
                }
                continue;
            }
            out.append(c);
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    public static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static Bitmap loadIcon(File rootfs, Entry entry, int targetPx) {
        String icon = entry.iconName;
        if (icon == null || icon.isEmpty()) {
            return null;
        }
        List<File> candidates = new ArrayList<>();
        if (icon.startsWith("/")) {
            candidates.add(new File(rootfs, icon.substring(1)));
        } else {
            for (String theme : ICON_THEMES) {
                for (String size : ICON_DIRS) {
                    candidates.add(new File(rootfs, "usr/share/icons/" + theme + "/" + size + "/apps/" + icon + ".png"));
                }
            }
            candidates.add(new File(rootfs, "usr/share/pixmaps/" + icon + ".png"));
            candidates.add(new File(rootfs, "usr/share/icons/" + icon + ".png"));
        }
        for (File candidate : candidates) {
            if (!candidate.isFile() || !candidate.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                continue;
            }
            Bitmap bitmap = decode(candidate, targetPx);
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    private static Bitmap decode(File file, int targetPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }
}
