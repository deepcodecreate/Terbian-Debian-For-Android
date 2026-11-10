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

package com.terbian.vnc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class RfbClient {
    public static final int ENC_RAW = 0;
    public static final int ENC_COPY_RECT = 1;
    public static final int ENC_RRE = 2;
    public static final int ENC_HEXTILE = 5;
    public static final int ENC_DESKTOP_SIZE = -223;
    public static final int ENC_EXT_DESKTOP_SIZE = -308;

    public static final int MSG_FRAMEBUFFER_UPDATE = 0;
    public static final int MSG_SET_COLOR_MAP = 1;
    public static final int MSG_BELL = 2;
    public static final int MSG_SERVER_CUT_TEXT = 3;

    private static final int CLIENT_SET_PIXEL_FORMAT = 0;
    private static final int CLIENT_SET_ENCODINGS = 2;
    private static final int CLIENT_UPDATE_REQUEST = 3;
    private static final int CLIENT_KEY_EVENT = 4;
    private static final int CLIENT_POINTER_EVENT = 5;
    private static final int CLIENT_CUT_TEXT = 6;
    private static final int CLIENT_SET_DESKTOP_SIZE = 251;

    public interface Listener {
        void onReady(int width, int height, String desktopName);

        void onUpdated(int x, int y, int width, int height);

        void onResize(int width, int height);

        void onBell();

        void onServerCutText(String text);

        void onResizeSupported(boolean supported);
    }

    private final Framebuffer framebuffer = new Framebuffer();
    private final Object writeLock = new Object();

    private DataInputStream in;
    private DataOutputStream out;
    private Listener listener = ListenerAdapter.INSTANCE;
    private int versionMinor = 8;
    private String desktopName = "";
    private boolean extendedResize;
    private volatile boolean open;
    private byte[] scratch = new byte[4096];
    private final byte[] pixelBuf = new byte[4];

    private byte[] scratch(int size) {
        if (scratch.length < size) {
            scratch = new byte[size];
        }
        return scratch;
    }

    public Framebuffer framebuffer() {
        return framebuffer;
    }

    public String desktopName() {
        return desktopName;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean extendedResizeSupported() {
        return extendedResize;
    }

    public void connect(Socket socket, String password, boolean shared, Listener listener) throws IOException {
        if (listener != null) {
            this.listener = listener;
        }

        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 256 * 1024));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
        negotiateVersion();
        authenticate(password);
        out.writeByte(shared ? 1 : 0);
        out.flush();
        readServerInit();
        sendPixelFormat();
        sendEncodings();
        open = true;
        listener.onReady(framebuffer.width(), framebuffer.height(), desktopName);
    }

    public void requestUpdate(boolean incremental) throws IOException {
        int width = framebuffer.width();
        int height = framebuffer.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        synchronized (writeLock) {
            out.writeByte(CLIENT_UPDATE_REQUEST);
            out.writeByte(incremental ? 1 : 0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(width);
            out.writeShort(height);
            out.flush();
        }
    }

    public void pointerEvent(int x, int y, int buttonMask) throws IOException {
        int width = framebuffer.width();
        int height = framebuffer.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x >= width) x = width - 1;
        if (y >= height) y = height - 1;
        synchronized (writeLock) {
            out.writeByte(CLIENT_POINTER_EVENT);
            out.writeByte(buttonMask & 0xff);
            out.writeShort(x);
            out.writeShort(y);
            out.flush();
        }
    }

    public void keyEvent(boolean down, int keysym) throws IOException {
        if (keysym == 0) {
            return;
        }
        synchronized (writeLock) {
            out.writeByte(CLIENT_KEY_EVENT);
            out.writeByte(down ? 1 : 0);
            out.writeShort(0);
            out.writeInt(keysym);
            out.flush();
        }
    }

    public void clientCutText(String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
        if (data.length > 1_000_000) {
            throw new RfbException("Clipboard text is too large");
        }
        synchronized (writeLock) {
            out.writeByte(CLIENT_CUT_TEXT);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        }
    }

    public void requestDesktopSize(int width, int height) throws IOException {
        if (!extendedResize) {
            throw new RfbException("This VNC server has not advertised desktop resizing");
        }
        if (width < 320 || height < 240 || width > Framebuffer.MAX_EDGE || height > Framebuffer.MAX_EDGE) {
            throw new RfbException("Desktop size is out of range");
        }
        synchronized (writeLock) {
            out.writeByte(CLIENT_SET_DESKTOP_SIZE);
            out.writeByte(0);
            out.writeShort(width);
            out.writeShort(height);
            out.writeByte(1);
            out.writeByte(0);
            out.writeInt(0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(width);
            out.writeShort(height);
            out.writeInt(0);
            out.flush();
        }
    }

    public int processOneMessage() throws IOException {
        int type = in.readUnsignedByte();
        switch (type) {
            case MSG_FRAMEBUFFER_UPDATE:
                readFramebufferUpdate();
                return type;
            case MSG_SET_COLOR_MAP:
                skipColorMap();
                return type;
            case MSG_BELL:
                listener.onBell();
                return type;
            case MSG_SERVER_CUT_TEXT:
                readServerCutText();
                return type;
            default:
                throw new RfbException("Unsupported server message " + type);
        }
    }

    public void close() {
        open = false;
    }

    private void negotiateVersion() throws IOException {
        byte[] banner = new byte[12];
        in.readFully(banner);
        String text = new String(banner, StandardCharsets.US_ASCII);
        if (!text.startsWith("RFB ") || text.length() < 12 || text.charAt(11) != '\n') {
            throw new RfbException("Server did not send an RFB banner");
        }
        int major;
        int minor;
        try {
            major = Integer.parseInt(text.substring(4, 7));
            minor = Integer.parseInt(text.substring(8, 11));
        } catch (NumberFormatException e) {
            throw new RfbException("Unsupported RFB version: " + text.trim());
        }
        if (major != 3) {
            throw new RfbException("Unsupported RFB version: " + text.trim());
        }
        if (minor >= 8) {
            versionMinor = 8;
        } else if (minor >= 7) {
            versionMinor = 7;
        } else if (minor >= 3) {
            versionMinor = 3;
        } else {
            throw new RfbException("Unsupported RFB version: " + text.trim());
        }
        String reply = String.format("RFB 003.%03d\n", versionMinor);
        out.write(reply.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void authenticate(String password) throws IOException {
        if (versionMinor == 3) {
            int type = in.readInt();
            if (type == 0) {
                throw new RfbException(readFailureReason("Server refused the connection"));
            }
            selectSecurity(type, password);
            return;
        }
        int count = in.readUnsignedByte();
        if (count == 0) {
            throw new RfbException(readFailureReason("Server refused the connection"));
        }
        byte[] types = new byte[count];
        in.readFully(types);
        int chosen = chooseSecurity(types, password);
        out.writeByte(chosen);
        out.flush();
        selectSecurity(chosen, password);
    }

    private int chooseSecurity(byte[] types, String password) throws RfbException {
        boolean none = false;
        boolean vnc = false;
        for (byte type : types) {
            if (type == 1) none = true;
            if (type == 2) vnc = true;
        }
        boolean havePassword = password != null && !password.isEmpty();
        if (havePassword && vnc) {
            return 2;
        }
        if (!havePassword && none) {
            return 1;
        }
        if (vnc) {
            throw new RfbException("This desktop requires a VNC password. Set one in Connection settings.");
        }
        if (none) {
            return 1;
        }
        throw new RfbException("Server offered no supported security type (None or VNC authentication)");
    }

    private void selectSecurity(int type, String password) throws IOException {
        if (type == 1) {
            if (versionMinor >= 8) {
                readSecurityResult();
            }
            return;
        }
        if (type == 2) {
            byte[] challenge = new byte[16];
            in.readFully(challenge);
            byte[] response = VncAuth.encrypt(challenge, password == null ? "" : password);
            synchronized (writeLock) {
                out.write(response);
                out.flush();
            }
            readSecurityResult();
            return;
        }
        throw new RfbException("Unsupported security type " + type);
    }

    private void readSecurityResult() throws IOException {
        int result = in.readInt();
        if (result == 0) {
            return;
        }
        String reason = versionMinor >= 8
                ? readFailureReason("VNC authentication failed")
                : "VNC authentication failed";
        throw new RfbException(reason);
    }

    private String readFailureReason(String fallback) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > 8192) {
            return fallback;
        }
        byte[] data = new byte[length];
        in.readFully(data);
        String text = new String(data, StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? fallback : text;
    }

    private void readServerInit() throws IOException {
        int width = in.readUnsignedShort();
        int height = in.readUnsignedShort();
        skipFully(16);
        int nameLength = in.readInt();
        if (nameLength < 0 || nameLength > 4096) {
            throw new RfbException("Implausible desktop name length");
        }
        byte[] name = new byte[nameLength];
        in.readFully(name);
        desktopName = new String(name, StandardCharsets.UTF_8);
        framebuffer.resize(width, height);
    }

    private void sendPixelFormat() throws IOException {
        synchronized (writeLock) {
            out.writeByte(CLIENT_SET_PIXEL_FORMAT);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(32);
            out.writeByte(24);
            out.writeByte(0);
            out.writeByte(1);
            out.writeShort(255);
            out.writeShort(255);
            out.writeShort(255);
            out.writeByte(16);
            out.writeByte(8);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.flush();
        }
    }

    private void sendEncodings() throws IOException {
        int[] encodings = new int[]{
                ENC_HEXTILE,
                ENC_RRE,
                ENC_COPY_RECT,
                ENC_RAW,
                ENC_DESKTOP_SIZE,
                ENC_EXT_DESKTOP_SIZE
        };
        synchronized (writeLock) {
            out.writeByte(CLIENT_SET_ENCODINGS);
            out.writeByte(0);
            out.writeShort(encodings.length);
            for (int encoding : encodings) {
                out.writeInt(encoding);
            }
            out.flush();
        }
    }

    private void readFramebufferUpdate() throws IOException {
        in.readUnsignedByte();
        int rectangles = in.readUnsignedShort();
        boolean resized = false;
        int unionLeft = Integer.MAX_VALUE;
        int unionTop = Integer.MAX_VALUE;
        int unionRight = 0;
        int unionBottom = 0;
        for (int i = 0; i < rectangles; i++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int encoding = in.readInt();
            if (encoding == ENC_DESKTOP_SIZE || encoding == ENC_EXT_DESKTOP_SIZE) {
                if (encoding == ENC_EXT_DESKTOP_SIZE) {
                    extendedResize = true;
                    skipExtendedDesktopSize();
                    listener.onResizeSupported(true);
                }
                if (width > 0 && height > 0) {
                    framebuffer.resize(width, height);
                    resized = true;
                    listener.onResize(width, height);
                }
                continue;
            }
            framebuffer.lock().lock();
            try {
                switch (encoding) {
                    case ENC_RAW:
                        readRaw(x, y, width, height);
                        break;
                    case ENC_COPY_RECT:
                        readCopyRect(x, y, width, height);
                        break;
                    case ENC_RRE:
                        readRre(x, y, width, height);
                        break;
                    case ENC_HEXTILE:
                        readHextile(x, y, width, height);
                        break;
                    default:
                        throw new RfbException(
                                "Unsupported encoding " + encoding
                                        + ". This client implements Raw, CopyRect, RRE, and Hextile.");
                }
            } finally {
                framebuffer.lock().unlock();
            }
            unionLeft = Math.min(unionLeft, x);
            unionTop = Math.min(unionTop, y);
            unionRight = Math.max(unionRight, x + width);
            unionBottom = Math.max(unionBottom, y + height);
        }
        if (resized) {
            listener.onUpdated(0, 0, framebuffer.width(), framebuffer.height());
        } else if (unionRight > unionLeft && unionBottom > unionTop) {
            listener.onUpdated(unionLeft, unionTop, unionRight - unionLeft, unionBottom - unionTop);
        }
    }

    private void skipFully(int n) throws IOException {
        if (n < 0) {
            throw new RfbException("Negative skip length");
        }
        byte[] scratch = new byte[Math.min(Math.max(n, 1), 8192)];
        int left = n;
        while (left > 0) {
            int chunk = Math.min(left, scratch.length);
            in.readFully(scratch, 0, chunk);
            left -= chunk;
        }
    }

    private void skipExtendedDesktopSize() throws IOException {
        int screens = in.readUnsignedByte();
        skipFully(3);
        if (screens < 0 || screens > 64) {
            throw new RfbException("Implausible screen count");
        }
        skipFully(screens * 16);
    }

    private void readRaw(int x, int y, int width, int height) throws IOException {
        long count = (long) width * (long) height;
        if (width < 0 || height < 0 || count > (long) Framebuffer.MAX_EDGE * Framebuffer.MAX_EDGE) {
            throw new RfbException("Raw rectangle is too large");
        }
        byte[] raw = scratch((int) count * 4);
        in.readFully(raw, 0, (int) count * 4);
        blitRaw(raw, x, y, width, height);
    }

    private void blitRaw(byte[] raw, int x, int y, int width, int height) {
        int fbWidth = framebuffer.width();
        int fbHeight = framebuffer.height();
        int[] pixels = framebuffer.pixels();
        int x0 = Math.max(0, -x);
        int y0 = Math.max(0, -y);
        int x1 = Math.min(width, fbWidth - x);
        int y1 = Math.min(height, fbHeight - y);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        for (int row = y0; row < y1; row++) {
            int src = (row * width + x0) * 4;
            int dst = (y + row) * fbWidth + x + x0;
            for (int col = x0; col < x1; col++) {
                pixels[dst++] = 0xff000000 | (raw[src] & 0xff) | ((raw[src + 1] & 0xff) << 8)
                        | ((raw[src + 2] & 0xff) << 16);
                src += 4;
            }
        }
    }

    private void readCopyRect(int x, int y, int width, int height) throws IOException {
        int srcX = in.readUnsignedShort();
        int srcY = in.readUnsignedShort();
        int fbWidth = framebuffer.width();
        int fbHeight = framebuffer.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] pixels = framebuffer.pixels();
        if (x >= 0 && y >= 0 && srcX >= 0 && srcY >= 0
                && x + width <= fbWidth && srcX + width <= fbWidth
                && y + height <= fbHeight && srcY + height <= fbHeight) {
            if (srcY >= y) {
                for (int row = 0; row < height; row++) {
                    System.arraycopy(pixels, (srcY + row) * fbWidth + srcX, pixels, (y + row) * fbWidth + x, width);
                }
            } else {
                for (int row = height - 1; row >= 0; row--) {
                    System.arraycopy(pixels, (srcY + row) * fbWidth + srcX, pixels, (y + row) * fbWidth + x, width);
                }
            }
            return;
        }
        int[] copy = new int[width * height];
        for (int row = 0; row < height; row++) {
            int sy = srcY + row;
            for (int col = 0; col < width; col++) {
                int sx = srcX + col;
                int color = 0xff000000;
                if (sx >= 0 && sy >= 0 && sx < fbWidth && sy < fbHeight) {
                    color = pixels[sy * fbWidth + sx];
                }
                copy[row * width + col] = color;
            }
        }
        for (int row = 0; row < height; row++) {
            int dy = y + row;
            if (dy < 0 || dy >= fbHeight) continue;
            for (int col = 0; col < width; col++) {
                int dx = x + col;
                if (dx >= 0 && dx < fbWidth) {
                    pixels[dy * fbWidth + dx] = copy[row * width + col];
                }
            }
        }
    }

    private void readRre(int x, int y, int width, int height) throws IOException {
        long subrects = in.readInt() & 0xffffffffL;
        if (subrects > 5_000_000) {
            throw new RfbException("RRE subrectangle count is too large");
        }
        int background = readPixel();
        fillRect(x, y, width, height, background);
        for (long i = 0; i < subrects; i++) {
            int color = readPixel();
            int sx = in.readUnsignedShort();
            int sy = in.readUnsignedShort();
            int sw = in.readUnsignedShort();
            int sh = in.readUnsignedShort();
            fillRect(x + sx, y + sy, sw, sh, color);
        }
    }

    private void readHextile(int x, int y, int width, int height) throws IOException {
        int background = 0xff000000;
        int foreground = 0xff000000;
        for (int tileY = 0; tileY < height; tileY += 16) {
            int tileHeight = Math.min(16, height - tileY);
            for (int tileX = 0; tileX < width; tileX += 16) {
                int tileWidth = Math.min(16, width - tileX);
                int mask = in.readUnsignedByte();
                if ((mask & 1) != 0) {
                    byte[] raw = scratch(tileWidth * tileHeight * 4);
                    in.readFully(raw, 0, tileWidth * tileHeight * 4);
                    blitRaw(raw, x + tileX, y + tileY, tileWidth, tileHeight);
                    continue;
                }
                if ((mask & 2) != 0) {
                    background = readPixel();
                }
                if ((mask & 4) != 0) {
                    foreground = readPixel();
                }
                fillRect(x + tileX, y + tileY, tileWidth, tileHeight, background);
                if ((mask & 8) != 0) {
                    int count = in.readUnsignedByte();
                    boolean colored = (mask & 16) != 0;
                    int color = foreground;
                    for (int i = 0; i < count; i++) {
                        if (colored) {
                            color = readPixel();
                        }
                        int xy = in.readUnsignedByte();
                        int wh = in.readUnsignedByte();
                        int sx = (xy >> 4) & 0x0f;
                        int sy = xy & 0x0f;
                        int sw = ((wh >> 4) & 0x0f) + 1;
                        int sh = (wh & 0x0f) + 1;
                        fillRect(x + tileX + sx, y + tileY + sy, sw, sh, color);
                    }
                }
            }
        }
    }

    private int readPixel() throws IOException {
        in.readFully(pixelBuf);
        return pixelFromBytes(pixelBuf, 0);
    }

    private static int pixelFromBytes(byte[] raw, int offset) {
        int b0 = raw[offset] & 0xff;
        int b1 = raw[offset + 1] & 0xff;
        int b2 = raw[offset + 2] & 0xff;
        int pixel = b0 | (b1 << 8) | (b2 << 16);
        return 0xff000000 | pixel;
    }

    private void fillRect(int x, int y, int width, int height, int color) {
        int fbWidth = framebuffer.width();
        int fbHeight = framebuffer.height();
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(fbWidth, x + width);
        int y1 = Math.min(fbHeight, y + height);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int[] pixels = framebuffer.pixels();
        for (int row = y0; row < y1; row++) {
            java.util.Arrays.fill(pixels, row * fbWidth + x0, row * fbWidth + x1, color);
        }
    }

    private void skipColorMap() throws IOException {
        in.readUnsignedByte();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        if (count < 0 || count > 65535) {
            throw new EOFException("Bad color map");
        }
        skipFully(count * 6);
    }

    private void readServerCutText() throws IOException {
        skipFully(3);
        int length = in.readInt();
        if (length < 0) {
            int extended = -length;
            if (extended > 8_000_000) {
                throw new RfbException("Extended clipboard message is too large");
            }
            skipFully(extended);
            return;
        }
        if (length > 1_000_000) {
            throw new RfbException("Server clipboard message is too large");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        listener.onServerCutText(new String(data, StandardCharsets.ISO_8859_1));
    }

    private static final class ListenerAdapter implements Listener {
        static final ListenerAdapter INSTANCE = new ListenerAdapter();

        @Override public void onReady(int width, int height, String desktopName) { }
        @Override public void onUpdated(int x, int y, int width, int height) { }
        @Override public void onResize(int width, int height) { }
        @Override public void onBell() { }
        @Override public void onServerCutText(String text) { }
        @Override public void onResizeSupported(boolean supported) { }
    }
}
