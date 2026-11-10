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
import android.system.Os;

import com.terbian.debian.Distro;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AndroidShell {
    private static final String[] SU_CANDIDATES = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su",
            "/vendor/bin/su", "/product/bin/su", "/data/adb/magisk/su", "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su", "/debug_ramdisk/su", "/apex/com.android.runtime/bin/su",
    };

    private AndroidShell() {
    }

    public static String findSu() {
        for (String path : SU_CANDIDATES) {
            if (new File(path).exists()) {
                return path;
            }
        }
        String pathVar = System.getenv("PATH");
        if (pathVar != null) {
            for (String dir : pathVar.split(":")) {
                if (!dir.isEmpty() && new File(dir, "su").exists()) {
                    return new File(dir, "su").getAbsolutePath();
                }
            }
        }
        return null;
    }

    public static TerminalSession create(Context context, boolean root, TerminalSessionClient client) {
        Context app = context.getApplicationContext();
        File home = new File(app.getFilesDir(), "shell-home");
        home.mkdirs();
        File tmp = new File(app.getCacheDir(), "shell-tmp");
        tmp.mkdirs();
        File rc = new File(home, ".terbian_rc");
        write(rc, RC);
        File storage = new File(home, "storage");
        if (!storage.exists()) {
            try {
                Os.symlink("/sdcard", storage.getAbsolutePath());
            } catch (Exception ignored) {
            }
        }

        String su = findSu();
        String nativeLibDir = app.getApplicationInfo().nativeLibraryDir;

        Map<String, String> env = new TreeMap<>(System.getenv());
        env.put("HOME", home.getAbsolutePath());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("LANG", "C.UTF-8");
        env.put("TMPDIR", tmp.getAbsolutePath());
        env.put("EXTERNAL_STORAGE", "/sdcard");
        env.put("ENV", rc.getAbsolutePath());
        env.put("TERBIAN_PROMPT", com.terbian.prefs.AppSettings.promptStyle(app).id);
        env.put("PATH", androidPath(env.get("PATH")));
        env.put("TERBIAN_PROOT", new File(nativeLibDir, "libproot.so").getAbsolutePath());
        env.put("TERBIAN_LOADER", new File(nativeLibDir, "libloader.so").getAbsolutePath());
        env.put("TERBIAN_LIBDIR", nativeLibDir);
        env.put("TERBIAN_DEBIAN", Distro.DEBIAN.rootfsDir(app).getAbsolutePath());
        env.put("TERBIAN_UBUNTU", Distro.UBUNTU.rootfsDir(app).getAbsolutePath());
        env.put("TERBIAN_GUEST_TMP", new File(app.getFilesDir(), "debian/tmp").getAbsolutePath());
        if (su != null) {
            env.put("TERBIAN_SU", su);
        }
        if (root && su == null) {
            env.put("TERBIAN_ROOT_MISSING", "1");
        }

        if (root && su != null) {
            return rootSession(su, env, client);
        }
        String shell = pickShell();
        String[] args = shell.endsWith("bash")
                ? new String[]{shell, "--rcfile", rc.getAbsolutePath(), "-i"}
                : new String[]{shell, "-i"};
        env.put("SHELL", shell);
        return new TerminalSession(shell, home.getAbsolutePath(), args, toArray(env), null, client);
    }

    private static TerminalSession rootSession(String su, Map<String, String> env, TerminalSessionClient client) {
        String rootHome = "/data/local/tmp";
        env.put("HOME", rootHome);
        env.put("SHELL", "/system/bin/sh");
        StringBuilder script = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            String key = e.getKey();
            if (key.equals("HOME") || key.equals("TERM") || key.equals("PATH") || key.equals("ENV")
                    || key.equals("TMPDIR") || key.equals("LANG") || key.equals("SHELL")
                    || key.equals("COLORTERM") || key.startsWith("TERBIAN_") || key.equals("ANDROID_ROOT")
                    || key.equals("ANDROID_DATA") || key.equals("EXTERNAL_STORAGE")) {
                script.append("export ").append(key).append('=').append(quote(e.getValue())).append("; ");
            }
        }
        script.append("cd ").append(rootHome).append("; ").append(ROOT_TTY_BOOTSTRAP);
        return new TerminalSession(su, rootHome, new String[]{su, "-c", script.toString()},
                toArray(env), null, client);
    }

    private static final String ROOT_TTY_BOOTSTRAP =
            "if ( : </dev/tty ) 2>/dev/null; then exec /system/bin/sh -i; fi; "
            + "for _b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox"
            + " /system/bin/busybox /system/xbin/busybox; do "
            + "if [ -x \"$_b\" ] && \"$_b\" setsid -c true </dev/null >/dev/null 2>&1; then "
            + "( exec \"$_b\" setsid -c /system/bin/sh -i ); exit $?; fi; done; "
            + "_h=$(setsid </dev/null 2>&1); "
            + "case \"$_h\" in *'[-t]'*) ( exec setsid -t /system/bin/sh -i ); exit $?;; esac; "
            + "exec /system/bin/sh -i";

    private static String pickShell() {
        for (String candidate : new String[]{"/system/bin/bash", "/system/xbin/bash", "/system/bin/sh"}) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return "/system/bin/sh";
    }

    private static String androidPath(String current) {
        List<String> parts = new ArrayList<>();
        if (current != null) {
            for (String p : current.split(":")) {
                if (!p.isEmpty() && !parts.contains(p)) {
                    parts.add(p);
                }
            }
        }
        for (String p : new String[]{"/system/bin", "/system/xbin", "/vendor/bin", "/product/bin",
                "/apex/com.android.runtime/bin", "/apex/com.android.art/bin"}) {
            if (!parts.contains(p) && new File(p).isDirectory()) {
                parts.add(p);
            }
        }
        return String.join(":", parts);
    }

    private static String[] toArray(Map<String, String> env) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            list.add(e.getKey() + "=" + e.getValue());
        }
        return list.toArray(new String[0]);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void write(File file, String text) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static final String RC =
            "\n" +
            "export HISTFILE=\"$HOME/.sh_history\" HISTSIZE=5000\n" +
            "export EDITOR=\"${EDITOR:-vi}\"\n" +
            "\n" +
            "_tb_ps1() {\n" +
            "    _u=$(id -un 2>/dev/null)\n" +
            "    if [ \"$(id -u 2>/dev/null)\" = 0 ]; then _c='#'; else _c='$'; fi\n" +
            "    case \"$PWD\" in\n" +
            "        \"$HOME\") _p='~' ;;\n" +
            "        \"$HOME\"/*) _p=\"~${PWD#\"$HOME\"}\" ;;\n" +
            "        *) _p=\"$PWD\" ;;\n" +
            "    esac\n" +
            "    case \"$TERBIAN_PROMPT\" in\n" +
            "        rounded) printf '\\342\\225\\255\\342\\224\\200%s@android %s\\n\\342\\225\\260\\342\\224\\200\\342\\235\\257 ' \"${_u:-shell}\" \"$_p\" ;;\n" +
            "        arrow) printf '\\342\\236\\234 %s ' \"$_p\" ;;\n" +
            "        *) printf '\\342\\224\\214\\342\\224\\200\\342\\224\\200(%s@android)-[%s]\\n\\342\\224\\224\\342\\224\\200%s ' \"${_u:-shell}\" \"$_p\" \"$_c\" ;;\n" +
            "    esac\n" +
            "}\n" +
            "PS1='$(_tb_ps1)'\n" +
            "\n" +
            "alias ll='ls -lah'\n" +
            "alias la='ls -A'\n" +
            "alias l='ls -CF'\n" +
            "alias ..='cd ..'\n" +
            "alias cls='clear'\n" +
            "\n" +
            "_tb_setup_storage() {\n" +
            "    ln -sfn /sdcard \"$HOME/storage\" && echo \"~/storage -> /sdcard\"\n" +
            "}\n" +
            "\n" +
            "sudo() {\n" +
            "    if [ -z \"$TERBIAN_SU\" ]; then\n" +
            "        echo \"No su binary found: this device is not rooted (or su is hidden from Terbian).\" >&2\n" +
            "        return 1\n" +
            "    fi\n" +
            "    if [ $# -eq 0 ]; then\n" +
            "        echo \"usage: sudo COMMAND [ARGS...]\" >&2\n" +
            "        return 2\n" +
            "    fi\n" +
            "    \"$TERBIAN_SU\" -c \"$*\"\n" +
            "}\n" +
            "\n" +
            "root() {\n" +
            "    if [ -z \"$TERBIAN_SU\" ]; then\n" +
            "        echo \"No su binary found: this device is not rooted (or su is hidden from Terbian).\" >&2\n" +
            "        return 1\n" +
            "    fi\n" +
            "    \"$TERBIAN_SU\"\n" +
            "}\n" +
            "\n" +
            "_tb_proot() {\n" +
            "    _r=$1\n" +
            "    shift\n" +
            "    if [ ! -d \"$_r/usr\" ]; then\n" +
            "        echo \"That system is not installed yet. Open a terminal of it from the + button once.\" >&2\n" +
            "        return 1\n" +
            "    fi\n" +
            "    [ $# -eq 0 ] && set -- /bin/bash --login\n" +
            "    mkdir -p \"$TERBIAN_GUEST_TMP\" 2>/dev/null\n" +
            "    PROOT_TMP_DIR=\"$TMPDIR\" PROOT_LOADER=\"$TERBIAN_LOADER\" LD_LIBRARY_PATH=\"$TERBIAN_LIBDIR\" \\\n" +
            "        \"$TERBIAN_PROOT\" --ashmem-memfd --link2symlink -L --kill-on-exit --sysvipc \\\n" +
            "        --kernel-release=6.2.1-terbian -0 -r \"$_r\" \\\n" +
            "        -b /dev -b /dev/urandom:/dev/random -b /proc -b /proc/self/fd:/dev/fd -b /sys \\\n" +
            "        -b \"$TERBIAN_GUEST_TMP\":/tmp -b /storage/emulated/0:/sdcard \\\n" +
            "        -w /root /usr/bin/env -i HOME=/root USER=root LOGNAME=root SHELL=/bin/bash \\\n" +
            "        TERM=\"${TERM:-xterm-256color}\" LANG=C.UTF-8 LC_ALL=C.UTF-8 TMPDIR=/tmp \\\n" +
            "        PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \"$@\"\n" +
            "}\n" +
            "debian() { _tb_proot \"$TERBIAN_DEBIAN\" \"$@\"; }\n" +
            "ubuntu() { _tb_proot \"$TERBIAN_UBUNTU\" \"$@\"; }\n" +
            "\n" +
            "ADB_TARGET=\"${ADB_TARGET:-localhost:5555}\"\n" +
            "export ADB_TARGET\n" +
            "\n" +
            "_tb_adb_root() {\n" +
            "    for _d in \"$TERBIAN_DEBIAN\" \"$TERBIAN_UBUNTU\"; do\n" +
            "        if [ -e \"$_d/usr/bin/adb\" ]; then\n" +
            "            echo \"$_d\"\n" +
            "            return 0\n" +
            "        fi\n" +
            "    done\n" +
            "    return 1\n" +
            "}\n" +
            "\n" +
            "adb() {\n" +
            "    _a=$(_tb_adb_root) || {\n" +
            "        echo \"adb is not installed yet. Run: adb-setup\" >&2\n" +
            "        return 127\n" +
            "    }\n" +
            "    _tb_proot \"$_a\" /usr/bin/env ADB_TARGET=\"$ADB_TARGET\" /bin/sh -c \\\n" +
            "        'adb connect \"$ADB_TARGET\" >/dev/null 2>&1; exec adb \"$@\"' sh \"$@\"\n" +
            "}\n" +
            "\n" +
            "_tb_adb_setup() {\n" +
            "    _tb_proot \"$TERBIAN_DEBIAN\" /usr/bin/env DEBIAN_FRONTEND=noninteractive /bin/sh -c \\\n" +
            "        'apt-get update && apt-get install -y adb' && echo \"adb installed. Try: adb-help\"\n" +
            "}\n" +
            "\n" +
            "_tb_adb_connect() {\n" +
            "    if [ $# -ne 1 ]; then\n" +
            "        echo \"usage: adb-connect HOST:PORT   (current: $ADB_TARGET)\" >&2\n" +
            "        return 2\n" +
            "    fi\n" +
            "    ADB_TARGET=$1\n" +
            "    export ADB_TARGET\n" +
            "    adb devices\n" +
            "}\n" +
            "\n" +
            "_tb_adb_pair() { adb pair \"$@\"; }\n" +
            "_tb_adb_shell() { adb shell \"$@\"; }\n" +
            "\n" +
            "_tb_adb_help() {\n" +
            "    cat <<'EOF'\n" +
            "adb from this shell (real adb client, runs inside Debian/Ubuntu):\n" +
            "  1. adb-setup                          install adb once (needs the Debian system and a network)\n" +
            "  2. Settings > Developer options > Wireless debugging: turn it on\n" +
            "  3. adb-pair  localhost:PAIR_PORT CODE  pair once (use \"Pair device with pairing code\")\n" +
            "  4. adb-connect localhost:PORT          the port shown on the Wireless debugging screen\n" +
            "  5. adb devices | adb shell | adb install app.apk | adb logcat ...\n" +
            "adb-shell gives a shell-user shell (pm, am, settings, dumpsys with more rights than this app has).\n" +
            "If you ran \"adb tcpip 5555\" from a computer, the default target localhost:5555 already works.\n" +
            "EOF\n" +
            "}\n" +
            "\n" +
            "_tb_help() {\n" +
            "    cat <<'EOF'\n" +
            "Android shell:  ll la setup-storage     files live in ~ , phone storage in /sdcard\n" +
            "Superuser:      sudo CMD   root         only on a rooted device (Magisk, KernelSU, ...)\n" +
            "Linux systems:  debian [CMD]  ubuntu [CMD]   full Debian/Ubuntu tools from this shell\n" +
            "adb:            adb-help\n" +
            "Own settings:   ~/.shrc.local\n" +
            "EOF\n" +
            "}\n" +
            "\n" +
            "alias setup-storage=_tb_setup_storage\n" +
            "alias adb-setup=_tb_adb_setup\n" +
            "alias adb-connect=_tb_adb_connect\n" +
            "alias adb-pair=_tb_adb_pair\n" +
            "alias adb-shell=_tb_adb_shell\n" +
            "alias adb-help=_tb_adb_help\n" +
            "alias help-terbian=_tb_help\n" +
            "\n" +
            "if [ -z \"$TERBIAN_RC_DONE\" ]; then\n" +
            "    TERBIAN_RC_DONE=1\n" +
            "    export TERBIAN_RC_DONE\n" +
            "    echo \"Terbian Android shell - $(getprop ro.product.model 2>/dev/null), Android $(getprop ro.build.version.release 2>/dev/null)\"\n" +
            "    if [ \"$(id -u 2>/dev/null)\" = 0 ]; then\n" +
            "        echo \"You are root. Type help-terbian for commands.\"\n" +
            "    elif [ -n \"$TERBIAN_SU\" ]; then\n" +
            "        echo \"su found: use sudo CMD or root. Type help-terbian for commands.\"\n" +
            "    else\n" +
            "        echo \"No su found (device not rooted). Type help-terbian, or adb-help for shell-level access.\"\n" +
            "    fi\n" +
            "    [ -n \"$TERBIAN_ROOT_MISSING\" ] && echo \"Root shell requested, but there is no su binary on this device.\" >&2\n" +
            "fi\n" +
            "\n" +
            "[ -f \"$HOME/.shrc.local\" ] && . \"$HOME/.shrc.local\"\n";
}
