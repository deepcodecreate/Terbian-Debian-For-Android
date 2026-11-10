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

import android.os.Build;

import com.terbian.terminal.AndroidShell;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class PhantomGuard {
    private static final AtomicBoolean DONE = new AtomicBoolean();

    private PhantomGuard() {
    }

    static void disable(java.util.function.Consumer<String> log) {
        if (Build.VERSION.SDK_INT < 31 || DONE.get()) {
            return;
        }
        String su = AndroidShell.findSu();
        if (su == null) {
            return;
        }
        String script = "device_config set_sync_disabled_for_tests persistent; "
                + "device_config put activity_manager max_phantom_processes 2147483647; "
                + "settings put global settings_enable_monitor_phantom_procs false; "
                + "device_config get activity_manager max_phantom_processes";
        try {
            Process process = new ProcessBuilder(su, "-c", script).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            java.io.InputStream in = process.getInputStream();
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() == 0) {
                DONE.set(true);
                log.accept("Android background process limit lifted for the desktop.\n");
            } else {
                log.accept("Could not lift the Android process limit: " + text + "\n");
            }
        } catch (Exception e) {
            log.accept("Could not lift the Android process limit: " + e.getMessage() + "\n");
        }
    }

    static void tuneFirefox(File rootfs) {
        String prefs = "pref(\"dom.ipc.processCount\", 2);\n"
                + "pref(\"dom.ipc.processCount.webIsolated\", 1);\n"
                + "pref(\"fission.autostart\", false);\n"
                + "pref(\"layers.gpu-process.enabled\", false);\n"
                + "pref(\"media.rdd-process.enabled\", false);\n"
                + "pref(\"media.utility-process.enabled\", false);\n"
                + "pref(\"browser.tabs.unloadOnLowMemory\", true);\n"
                + "pref(\"browser.sessionstore.interval\", 60000);\n";
        for (String dir : new String[]{"usr/lib/firefox-esr", "usr/lib/firefox"}) {
            File base = new File(rootfs, dir);
            if (!base.isDirectory()) {
                continue;
            }
            File prefDir = new File(base, "defaults/pref");
            if (!prefDir.isDirectory() && !prefDir.mkdirs()) {
                continue;
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(prefDir, "terbian.js"))) {
                out.write(prefs.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
    }
}
