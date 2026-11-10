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

public final class GuiSettings {
    public static final String PREFS = "terbian_gui";
    public static final String SCALE_FIT = "fit";
    public static final String SCALE_ORIGINAL = "original";

    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TIMEOUT = "timeout_ms";
    private static final String KEY_SCALE = "scale_mode";
    private static final String KEY_AUTO = "auto_connect";
    private static final String KEY_SAVE_PASSWORD = "save_password";
    private static final String KEY_GEOMETRY = "geometry";

    private static volatile String sessionPassword;

    public final String host;
    public final int port;
    public final int timeoutMs;
    public final String scaleMode;
    public final boolean autoConnect;
    public final boolean savePassword;
    public final String geometry;

    public GuiSettings(
            String host,
            int port,
            int timeoutMs,
            String scaleMode,
            boolean autoConnect,
            boolean savePassword,
            String geometry) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.scaleMode = scaleMode;
        this.autoConnect = autoConnect;
        this.savePassword = savePassword;
        this.geometry = geometry;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static GuiSettings load(Context context) {
        SharedPreferences prefs = prefs(context);
        String host = prefs.getString(KEY_HOST, "127.0.0.1");
        int port = prefs.getInt(KEY_PORT, 5901);
        int timeout = prefs.getInt(KEY_TIMEOUT, 8000);
        String scale = prefs.getString(KEY_SCALE, SCALE_FIT);
        boolean auto = prefs.getBoolean(KEY_AUTO, false);
        boolean save = prefs.getBoolean(KEY_SAVE_PASSWORD, false);
        String geometry = prefs.getString(KEY_GEOMETRY, "1280x720");
        return new GuiSettings(
                sanitizeHost(host),
                clamp(port, 1, 65535),
                clamp(timeout, 1000, 120000),
                SCALE_ORIGINAL.equals(scale) ? SCALE_ORIGINAL : SCALE_FIT,
                auto,
                save,
                sanitizeGeometry(geometry));
    }

    public void save(Context context) {
        prefs(context).edit()
                .putString(KEY_HOST, sanitizeHost(host))
                .putInt(KEY_PORT, clamp(port, 1, 65535))
                .putInt(KEY_TIMEOUT, clamp(timeoutMs, 1000, 120000))
                .putString(KEY_SCALE, SCALE_ORIGINAL.equals(scaleMode) ? SCALE_ORIGINAL : SCALE_FIT)
                .putBoolean(KEY_AUTO, autoConnect)
                .putBoolean(KEY_SAVE_PASSWORD, savePassword)
                .putString(KEY_GEOMETRY, sanitizeGeometry(geometry))
                .apply();
    }

    public static void setSessionPassword(String password) {
        sessionPassword = password == null || password.isEmpty() ? null : password;
    }

    public static String sessionPassword() {
        return sessionPassword;
    }

    public static String sanitizeHost(String host) {
        if (host == null) {
            return "127.0.0.1";
        }
        String trimmed = host.trim();
        if (trimmed.isEmpty() || trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\n') >= 0
                || trimmed.length() > 253) {
            return "127.0.0.1";
        }
        return trimmed;
    }

    public static String sanitizeGeometry(String geometry) {
        if (geometry == null || !geometry.matches("[0-9]{3,4}x[0-9]{3,4}")) {
            return "1280x720";
        }
        String[] parts = geometry.split("x");
        int w = Integer.parseInt(parts[0]);
        int h = Integer.parseInt(parts[1]);
        if (w < 320 || h < 240 || w > 4096 || h > 4096) {
            return "1280x720";
        }
        return w + "x" + h;
    }

    public static String normalizePassword(String password) {
        if (password == null) {
            return "";
        }
        String trimmed = password.replace("\r", "").replace("\n", "");
        if (trimmed.length() > 8) {
            trimmed = trimmed.substring(0, 8);
        }
        return trimmed;
    }

    public static boolean passwordCharsOk(String password) {
        String value = password == null ? "" : password;
        if (value.isEmpty() || value.length() > 8) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
