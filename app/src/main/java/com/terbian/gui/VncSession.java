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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.terbian.vnc.Framebuffer;
import com.terbian.vnc.RfbClient;
import com.terbian.vnc.RfbException;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VncSession {
    private static final String LOG_TAG = "TerbianVNC";

    public enum Phase {
        IDLE, CONNECTING, CONNECTED, FAILED
    }

    public interface Listener {
        void onPhase(Phase phase, String detail);

        void onFrame();

        void onBell();

        void onClipboard(String text);
    }

    private static final java.util.Map<Integer, VncSession> SESSIONS = new java.util.HashMap<>();
    private static final CopyOnWriteArrayList<Listener> GLOBAL_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile int activePort = 5901;

    private final Framebuffer framebuffer = new Framebuffer();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object connectLock = new Object();

    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Terbian-VNC-Send");
        t.setDaemon(true);
        return t;
    });

    private Socket socket;
    private RfbClient client;
    private Thread thread;
    private volatile Phase phase = Phase.IDLE;
    private volatile String detail = "Not connected";
    private volatile boolean resizeSupported;
    private final Object dirtyLock = new Object();
    private boolean dirtyAll = true;
    private int dirtyL = Integer.MAX_VALUE;
    private int dirtyT = Integer.MAX_VALUE;
    private int dirtyR;
    private int dirtyB;
    private volatile Context appContext;
    private volatile String lastHost = "127.0.0.1";
    private volatile long connectedAt;
    private int reconnects;
    private int buttonMask;
    private int lastX;
    private int lastY;

    public static VncSession forPort(int port) {
        synchronized (SESSIONS) {
            VncSession session = SESSIONS.get(port);
            if (session == null) {
                session = new VncSession(port);
                SESSIONS.put(port, session);
            }
            return session;
        }
    }

    public static VncSession get() {
        return forPort(activePort);
    }

    public static void setActive(int port) {
        activePort = port;
    }

    public static java.util.List<VncSession> all() {
        synchronized (SESSIONS) {
            return new java.util.ArrayList<>(SESSIONS.values());
        }
    }

    public static void disconnectAll() {
        for (VncSession session : all()) {
            if (session.phase != Phase.IDLE) {
                session.disconnect();
            }
        }
    }

    public static void disconnectPort(int port) {
        VncSession session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(port);
        }
        if (session != null && session.phase != Phase.IDLE) {
            session.disconnect();
        }
    }

    public static void addGlobalListener(Listener listener) {
        if (!GLOBAL_LISTENERS.contains(listener)) {
            GLOBAL_LISTENERS.add(listener);
        }
        for (VncSession session : all()) {
            if (!session.listeners.contains(listener)) {
                session.listeners.add(listener);
            }
        }
    }

    public static void removeGlobalListener(Listener listener) {
        GLOBAL_LISTENERS.remove(listener);
        for (VncSession session : all()) {
            session.listeners.remove(listener);
        }
    }

    private VncSession(int port) {
        currentPort = port;
        listeners.addAll(GLOBAL_LISTENERS);
    }

    private void send(IoAction action) {
        try {
            sender.execute(() -> {
                RfbClient current = client;
                if (current == null || !current.isOpen()) {
                    return;
                }
                try {
                    action.run(current);
                } catch (Exception e) {
                    fail(e);
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private interface IoAction {
        void run(RfbClient client) throws Exception;
    }

    public Framebuffer framebuffer() {
        return framebuffer;
    }

    public Phase phase() {
        return phase;
    }

    public String detail() {
        return detail;
    }

    public boolean isConnected() {
        return phase == Phase.CONNECTED && client != null && client.isOpen();
    }

    public boolean resizeSupported() {
        return resizeSupported;
    }

    private volatile int currentPort = -1;

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int port() {
        return currentPort;
    }

    public void connect(Context context) {
        connect(context, -1);
    }

    public void connect(Context context, int portOverride) {
        reconnects = 0;
        open(context, portOverride);
    }

    private void open(Context context, int portOverride) {
        Context app = context.getApplicationContext();
        appContext = app;
        connectedAt = 0;
        synchronized (connectLock) {
            disconnectLocked();
            GuiSettings loaded = GuiSettings.load(app);
            GuiSettings settings = portOverride > 0
                    ? new GuiSettings("127.0.0.1", portOverride, loaded.timeoutMs, loaded.scaleMode,
                    loaded.autoConnect, loaded.savePassword, loaded.geometry)
                    : loaded;
            currentPort = settings.port;
            lastHost = settings.host;
            String password = resolvePassword(app);
            setPhase(Phase.CONNECTING, "Connecting to " + settings.host + ":" + settings.port + "…");
            running.set(true);
            thread = new Thread(() -> runConnection(settings, password), "Terbian-VNC");
            thread.start();
        }
    }

    public void disconnect() {
        synchronized (connectLock) {
            disconnectLocked();
            setPhase(Phase.IDLE, "Disconnected. The desktop was left running.");
        }
    }

    public void pointer(int x, int y, int mask) {
        RfbClient current = client;
        if (current == null || !current.isOpen()) {
            return;
        }
        buttonMask = mask;
        lastX = x;
        lastY = y;
        send(c -> c.pointerEvent(x, y, mask));
    }

    public void wheel(int x, int y, boolean up) {
        RfbClient current = client;
        if (current == null || !current.isOpen()) {
            return;
        }
        final int base = buttonMask;
        final int bit = up ? 8 : 16;
        send(c -> {
            c.pointerEvent(x, y, base | bit);
            c.pointerEvent(x, y, base);
        });
    }

    public void releasePointer() {
        pointer(lastX, lastY, 0);
    }

    public void key(boolean down, int keysym) {
        if (keysym == 0) {
            return;
        }
        send(c -> c.keyEvent(down, keysym));
    }

    public void clientCutText(String text) {
        send(c -> c.clientCutText(text));
    }

    public boolean requestResize(int width, int height) {
        RfbClient current = client;
        if (current == null || !current.isOpen() || !resizeSupported) {
            return false;
        }
        send(c -> c.requestDesktopSize(width, height));
        return true;
    }

    private void runConnection(GuiSettings settings, String password) {
        Socket opened = new Socket();
        RfbClient rfb = new RfbClient();
        try {
            opened.connect(new InetSocketAddress(settings.host, settings.port), settings.timeoutMs);
            opened.setTcpNoDelay(true);
            opened.setKeepAlive(true);
            opened.setReceiveBufferSize(512 * 1024);
            opened.setSoTimeout(settings.timeoutMs);
            synchronized (connectLock) {
                if (!running.get()) {
                    opened.close();
                    return;
                }
                socket = opened;
                client = rfb;
            }
            rfb.connect(opened, password, true, new RfbClient.Listener() {
                @Override
                public void onReady(int width, int height, String desktopName) {
                    try {
                        opened.setSoTimeout(0);
                    } catch (Exception ignored) {
                    }
                    resizeSupported = false;
                    connectedAt = System.currentTimeMillis();
                    setPhase(Phase.CONNECTED, width + "×" + height
                            + (desktopName == null || desktopName.isEmpty() ? "" : " · " + desktopName));
                    Log.i(LOG_TAG, "connected " + width + "x" + height);
                }

                @Override
                public void onUpdated(int x, int y, int width, int height) {
                    copyFrame(rfb, x, y, width, height);
                }

                @Override
                public void onResize(int width, int height) {
                    copyFrame(rfb, 0, 0, width, height);
                }

                @Override
                public void onBell() {
                    main.post(() -> {
                        for (Listener listener : listeners) listener.onBell();
                    });
                }

                @Override
                public void onServerCutText(String text) {
                    main.post(() -> {
                        for (Listener listener : listeners) listener.onClipboard(text);
                    });
                }

                @Override
                public void onResizeSupported(boolean supported) {
                    resizeSupported = supported;
                }
            });
            boolean full = true;
            rfb.requestUpdate(false);
            while (running.get()) {
                int type = rfb.processOneMessage();
                if (!running.get()) {
                    break;
                }
                if (type == RfbClient.MSG_FRAMEBUFFER_UPDATE) {
                    rfb.requestUpdate(!full);
                    full = false;
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                fail(e);
            }
        } finally {
            rfb.close();
            try {
                opened.close();
            } catch (Exception ignored) {
            }
            running.set(false);
            synchronized (connectLock) {
                if (socket == opened) {
                    socket = null;
                    client = null;
                }
            }
        }
    }

    private void copyFrame(RfbClient rfb, int x, int y, int w, int h) {
        Framebuffer remote = rfb.framebuffer();
        framebuffer.lock().lock();
        remote.lock().lock();
        try {
            int width = remote.width();
            int height = remote.height();
            if (width <= 0 || height <= 0) {
                return;
            }
            int[] src = remote.pixels();
            if (framebuffer.width() != width || framebuffer.height() != height) {
                framebuffer.resize(width, height);
                x = 0;
                y = 0;
                w = width;
                h = height;
                markDirty(0, 0, width, height);
            } else {
                int x0 = Math.max(0, x);
                int y0 = Math.max(0, y);
                int x1 = Math.min(width, x + w);
                int y1 = Math.min(height, y + h);
                if (x1 <= x0 || y1 <= y0) {
                    return;
                }
                x = x0;
                y = y0;
                w = x1 - x0;
                h = y1 - y0;
                markDirty(x, y, w, h);
            }
            int[] dst = framebuffer.pixels();
            if (w == width) {
                System.arraycopy(src, y * width, dst, y * width, w * h);
            } else {
                for (int row = y; row < y + h; row++) {
                    System.arraycopy(src, row * width + x, dst, row * width + x, w);
                }
            }
        } finally {
            remote.lock().unlock();
            framebuffer.lock().unlock();
        }
        main.post(() -> {
            for (Listener listener : listeners) listener.onFrame();
        });
    }

    private void markDirty(int x, int y, int w, int h) {
        synchronized (dirtyLock) {
            dirtyL = Math.min(dirtyL, x);
            dirtyT = Math.min(dirtyT, y);
            dirtyR = Math.max(dirtyR, x + w);
            dirtyB = Math.max(dirtyB, y + h);
        }
    }

    public void markAllDirty() {
        synchronized (dirtyLock) {
            dirtyAll = true;
        }
    }

    public boolean takeDirty(int[] out) {
        synchronized (dirtyLock) {
            boolean all = dirtyAll || dirtyR <= dirtyL || dirtyB <= dirtyT;
            out[0] = dirtyL;
            out[1] = dirtyT;
            out[2] = dirtyR - dirtyL;
            out[3] = dirtyB - dirtyT;
            dirtyAll = false;
            dirtyL = Integer.MAX_VALUE;
            dirtyT = Integer.MAX_VALUE;
            dirtyR = 0;
            dirtyB = 0;
            return !all;
        }
    }

    private void disconnectLocked() {
        running.set(false);
        buttonMask = 0;
        RfbClient current = client;
        Socket open = socket;
        if (current != null) {
            current.close();
        }
        if (open != null) {
            try {
                open.close();
            } catch (Exception ignored) {
            }
        }
        Thread worker = thread;
        if (worker != null && worker != Thread.currentThread()) {
            try {
                worker.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        client = null;
        socket = null;
        thread = null;
        resizeSupported = false;
    }

    private void fail(Exception error) {
        running.set(false);
        String message = friendly(error);
        Log.i(LOG_TAG, "connection ended: " + message);
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        if (shouldReconnect(error)) {
            reconnects++;
            setPhase(Phase.CONNECTING, "Connection lost, reconnecting…");
            Thread retry = new Thread(() -> reconnect(message), "Terbian-VNC-Retry");
            retry.setDaemon(true);
            retry.start();
            return;
        }
        setPhase(Phase.FAILED, message);
    }

    private boolean shouldReconnect(Exception error) {
        if (appContext == null || error instanceof RfbException) {
            return false;
        }
        if (connectedAt > 0 && System.currentTimeMillis() - connectedAt > 30_000) {
            reconnects = 0;
        }
        return connectedAt > 0 && reconnects < 4
                && (error instanceof java.io.EOFException || error instanceof java.net.SocketException);
    }

    private void reconnect(String message) {
        int port = currentPort;
        String host = lastHost;
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (phase != Phase.CONNECTING || running.get()) {
            return;
        }
        if (com.terbian.vnc.VncProbe.isRfb(host, port, 1500)) {
            open(appContext, port);
        } else {
            setPhase(Phase.FAILED, message + " The desktop server is no longer running.");
        }
    }

    private static String friendly(Exception error) {
        if (error instanceof RfbException && error.getMessage() != null) {
            return error.getMessage();
        }
        String name = error.getClass().getSimpleName();
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("ECONNREFUSED") || name.contains("ConnectException")) {
            return "Connection refused. Start the desktop and wait until the VNC server is ready.";
        }
        if (name.contains("SocketTimeout") || name.contains("Timeout")) {
            return "Connection timed out. The VNC server did not answer. Check that it is running on 127.0.0.1:5901.";
        }
        if (name.contains("UnknownHost")) {
            return "Unknown host. Check the connection settings.";
        }
        if (error instanceof java.io.EOFException) {
            return "The VNC server closed the connection. Android may have stopped the desktop; start it again.";
        }
        if (message.isEmpty()) {
            return "Disconnected (" + name + ")";
        }
        return message;
    }

    private static String resolvePassword(Context context) {
        try {
            String saved = PasswordStore.load(context);
            if (saved != null && !saved.isEmpty()) {
                return saved;
            }
        } catch (Exception ignored) {
        }
        return GuiSettings.sessionPassword();
    }

    private void setPhase(Phase next, String text) {
        phase = next;
        detail = text;
        main.post(() -> {
            for (Listener listener : listeners) listener.onPhase(next, text);
        });
    }
}
