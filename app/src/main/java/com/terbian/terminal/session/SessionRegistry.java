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

package com.terbian.terminal.session;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.terbian.service.TerminalService;
import com.terbian.terminal.ProotSessionFactory;
import com.terbian.terminal.SessionType;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SessionRegistry {
    private static volatile SessionRegistry instance;

    public static SessionRegistry getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionRegistry.class) {
                if (instance == null) {
                    instance = new SessionRegistry(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context appContext;
    private final Map<String, TerminalSession> sessions = new LinkedHashMap<>();
    private final Map<String, SessionType> sessionTypes = new LinkedHashMap<>();

    private SessionRegistry(Context appContext) {
        this.appContext = appContext;
    }

    public synchronized TerminalSession getOrCreate(
            String id, String title, SessionType type, TerminalSessionClient client) {
        TerminalSession session = sessions.get(id);
        if (session != null) {
            session.updateTerminalSessionClient(client);
            return session;
        }

        SessionType resolvedType = type == null ? SessionType.DEBIAN : type;
        session = (resolvedType == SessionType.SHELL)
                ? createLocalShellSession(client)
                : createDebianSession(client);

        sessions.put(id, session);
        sessionTypes.put(id, resolvedType);
        notifySessionStarted(id, title);
        return session;
    }

    private TerminalSession createDebianSession(TerminalSessionClient client) {
        File rootfsDir = new File(appContext.getFilesDir(), "debian/rootfs");
        return ProotSessionFactory.createProotSession(appContext, rootfsDir, "/bin/bash", client);
    }

    private TerminalSession createLocalShellSession(TerminalSessionClient client) {
        File home = new File(appContext.getFilesDir(), "shell-home");
        if (!home.exists()) {
            home.mkdirs();
        }
        String shell = new File("/system/bin/bash").isFile() ? "/system/bin/bash" : "/system/bin/sh";
        String[] env = new String[]{
                "HOME=" + home.getAbsolutePath(),
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PATH=" + System.getenv("PATH"),
        };
        return new TerminalSession(shell, home.getAbsolutePath(), new String[]{shell, "-i"}, env, null, client);
    }

    public synchronized TerminalSession get(String id) {
        return sessions.get(id);
    }

    public synchronized SessionType getType(String id) {
        SessionType type = sessionTypes.get(id);
        return type == null ? SessionType.DEBIAN : type;
    }

    public synchronized boolean isRunning(String id) {
        TerminalSession session = sessions.get(id);
        return session != null && session.isRunning();
    }

    public synchronized void stopAndRemove(String id) {
        TerminalSession session = sessions.remove(id);
        sessionTypes.remove(id);
        if (session != null) {
            session.finishIfRunning();
        }
        notifySessionStopped(id);
        if (sessions.isEmpty()) {
            stopService();
        }
    }

    public synchronized int activeCount() {
        return sessions.size();
    }

    private void notifySessionStarted(String id, String title) {
        try {
            Intent intent = new Intent(appContext, TerminalService.class);
            intent.setAction(TerminalService.ACTION_SESSION_STARTED);
            intent.putExtra(TerminalService.EXTRA_SESSION_ID, id);
            intent.putExtra(TerminalService.EXTRA_SESSION_TITLE, title);
            ContextCompat.startForegroundService(appContext, intent);
        } catch (Exception ignored) {
        }
    }

    private void notifySessionStopped(String id) {
        try {
            Intent intent = new Intent(appContext, TerminalService.class);
            intent.setAction(TerminalService.ACTION_SESSION_STOPPED);
            intent.putExtra(TerminalService.EXTRA_SESSION_ID, id);
            appContext.startService(intent);
        } catch (Exception ignored) {
        }
    }

    private void stopService() {
        try {
            appContext.stopService(new Intent(appContext, TerminalService.class));
        } catch (Exception ignored) {
        }
    }
}
