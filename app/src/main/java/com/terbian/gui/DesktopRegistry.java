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
import android.content.SharedPreferences;

import com.terbian.debian.Distro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DesktopRegistry {
    public static final int MAX_DESKTOPS = 9;
    private static final String PREFS = "terbian_desktops";

    private static final long STARTING_GRACE_MS = 120_000;

    public static final class Instance {
        public final Distro distro;
        public final int display;
        public final long startedAt;

        Instance(Distro distro, int display, long startedAt) {
            this.distro = distro;
            this.display = display;
            this.startedAt = startedAt;
        }

        public int port() {
            return 5900 + display;
        }

        public String label() {
            return (distro == null ? "VNC" : distro.label) + " :" + display;
        }
    }

    private DesktopRegistry() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isUp(Context context, int display) {
        File lock = new File(sharedTmp(context), ".X" + display + "-lock");
        if (!lock.isFile()) {
            return false;
        }
        long pid = parsePid(firstLine(lock));
        if (!alive(pid)) {
            return false;
        }
        String cmd = "";
        try (java.io.FileInputStream in = new java.io.FileInputStream("/proc/" + pid + "/cmdline")) {
            byte[] data = new byte[512];
            int n = in.read(data);
            if (n > 0) {
                cmd = new String(data, 0, n, java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
            }
        } catch (Exception ignored) {
        }

        return cmd.isEmpty() || cmd.contains("vnc") || cmd.contains("proot") || cmd.contains("loader");
    }

    private static File sharedTmp(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "debian/tmp");
    }

    private static File marker(Context context, int display) {
        return new File(sharedTmp(context), ".terbian-display-" + display);
    }

    private static boolean alive(long pid) {
        return pid > 0 && new File("/proc/" + pid).exists();
    }

    private static String firstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static long parsePid(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Distro markerOwner(Context context, int display) {
        File file = marker(context, display);
        if (!file.isFile()) {
            return null;
        }
        String[] parts = firstLine(file).split("\\s+");
        if (parts.length < 2 || !alive(parsePid(parts[1]))) {
            return null;
        }
        for (Distro d : Distro.values()) {
            if (d.id.equals(parts[0])) {
                return d;
            }
        }
        return null;
    }

    private static Distro pidFileOwner(Context context, int display) {
        String suffix = ":" + display + ".pid";
        for (Distro d : Distro.values()) {
            File rootfs = d.rootfsDir(context);
            for (String dir : new String[]{"root/.vnc", "root/.config/tigervnc", "root/.local/state/tigervnc"}) {
                File[] files = new File(rootfs, dir).listFiles();
                if (files == null) {
                    continue;
                }
                for (File f : files) {
                    if (f.getName().endsWith(suffix) && alive(parsePid(firstLine(f)))) {
                        return d;
                    }
                }
            }
        }
        return null;
    }

    public static Distro ownerOf(Context context, int display) {
        Distro fromMarker = markerOwner(context, display);
        if (fromMarker != null) {
            return fromMarker;
        }
        String raw = prefs(context).getString("d" + display, null);
        Instance known = raw == null ? null : parse(display, raw);
        if (known != null && known.distro != null) {
            return known.distro;
        }
        return pidFileOwner(context, display);
    }

    private static boolean ownedByOther(Context context, int display, Distro distro) {
        Distro owner = markerOwner(context, display);
        return owner != null && owner != distro;
    }

    public static synchronized int reserve(Context context, Distro distro) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        for (int d = 1; d <= MAX_DESKTOPS; d++) {
            if (ownedByOther(context, d, distro)) {
                continue;
            }
            String raw = prefs.getString("d" + d, null);
            if (raw != null) {
                Instance known = parse(d, raw);
                boolean fresh = known != null && now - known.startedAt < STARTING_GRACE_MS;
                if (fresh || isUp(context, d)) {
                    continue;
                }
                prefs.edit().remove("d" + d).apply();
            } else if (isUp(context, d)) {
                continue;
            }
            prefs.edit().putString("d" + d, distro.id + "|" + now).apply();
            return d;
        }
        return -1;
    }

    public static synchronized void forget(Context context, int display) {
        prefs(context).edit().remove("d" + display).apply();
        File marker = marker(context, display);
        if (marker.exists() && !marker.delete()) {
            marker.deleteOnExit();
        }
    }

    public static synchronized List<Instance> running(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        List<Instance> result = new ArrayList<>();
        for (int d = 1; d <= MAX_DESKTOPS; d++) {
            String raw = prefs.getString("d" + d, null);
            Instance known = raw == null ? null : parse(d, raw);
            boolean up = isUp(context, d);
            if (up) {
                Distro owner = ownerOf(context, d);
                long started = known != null ? known.startedAt : 0L;
                result.add(new Instance(owner, d, started));
            } else {
                if (raw != null && (known == null || now - known.startedAt >= STARTING_GRACE_MS)) {
                    prefs.edit().remove("d" + d).apply();
                }
                File marker = marker(context, d);
                if (marker.isFile() && markerOwner(context, d) == null && !marker.delete()) {
                    marker.deleteOnExit();
                }
            }
        }
        Collections.sort(result, (a, b) -> a.startedAt != b.startedAt
                ? Long.compare(a.startedAt, b.startedAt) : Integer.compare(a.display, b.display));
        return result;
    }

    public static List<Instance> runningOf(Context context, Distro distro) {
        List<Instance> mine = new ArrayList<>();
        for (Instance i : running(context)) {
            if (i.distro == distro) {
                mine.add(i);
            }
        }
        return mine;
    }

    private static Instance parse(int display, String raw) {
        int bar = raw.indexOf('|');
        if (bar <= 0) {
            return null;
        }
        String id = raw.substring(0, bar);
        Distro distro = null;
        for (Distro d : Distro.values()) {
            if (d.id.equals(id)) {
                distro = d;
            }
        }
        if (distro == null) {
            return null;
        }
        try {
            return new Instance(distro, display, Long.parseLong(raw.substring(bar + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
