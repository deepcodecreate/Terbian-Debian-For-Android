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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process RFB server used to verify the client without Android or TigerVNC.
 * Run: java com.terbian.vnc.RfbSelfTest
 */
public final class RfbSelfTest {
    private static final String PASSWORD = "secret12";

    public static void main(String[] args) throws Exception {
        roundTripAuth();
        session();
        authenticationFailure();
        System.out.println("RfbSelfTest OK");
    }

    private static void roundTripAuth() throws Exception {
        byte[] challenge = new byte[16];
        new SecureRandom().nextBytes(challenge);
        byte[] encrypted = VncAuth.encrypt(challenge, PASSWORD);
        byte[] decrypted = VncAuth.decrypt(encrypted, PASSWORD);
        if (!java.util.Arrays.equals(challenge, decrypted)) {
            throw new AssertionError("DES round-trip failed");
        }
        byte[] wrong = VncAuth.decrypt(encrypted, "nope1234");
        if (java.util.Arrays.equals(challenge, wrong)) {
            throw new AssertionError("wrong password matched the challenge");
        }
    }

    private static void session() throws Exception {
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        AtomicInteger pointerX = new AtomicInteger(-1);
        AtomicInteger pointerMask = new AtomicInteger(-1);
        AtomicInteger keysym = new AtomicInteger(-1);
        AtomicInteger keyDown = new AtomicInteger(-1);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    serve(socket, pointerX, pointerMask, keysym, keyDown);
                } catch (Throwable t) {
                    serverError.set(t);
                }
            }, "rfb-test-server");
            thread.start();
            try (Socket socket = new Socket("127.0.0.1", server.getLocalPort())) {
                socket.setSoTimeout(5000);
                RfbClient client = new RfbClient();
                client.connect(socket, PASSWORD, true, null);
                if (client.framebuffer().width() != 16 || client.framebuffer().height() != 16) {
                    throw new AssertionError("unexpected size");
                }
                client.requestUpdate(false);
                client.processOneMessage();
                expect(client, 0, 0, 0xffff0000, "raw red");
                expect(client, 1, 0, 0xff00ff00, "raw green");

                client.requestUpdate(true);
                client.processOneMessage();
                expect(client, 2, 0, 0xffff0000, "copyrect red");
                expect(client, 3, 0, 0xff00ff00, "copyrect green");

                client.requestUpdate(true);
                client.processOneMessage();
                expect(client, 0, 2, 0xff00ff00, "rre background");
                expect(client, 1, 3, 0xffffffff, "rre subrect");

                client.requestUpdate(true);
                client.processOneMessage();
                expect(client, 0, 4, 0xffff0000, "hextile subrect");
                expect(client, 1, 4, 0xff0000ff, "hextile background");

                client.pointerEvent(3, 4, 1);
                client.keyEvent(true, 'a');
                client.keyEvent(false, 'a');
                client.requestUpdate(true);
                try {
                    client.processOneMessage();
                    throw new AssertionError("unsupported encoding was accepted");
                } catch (RfbException expected) {
                    if (expected.getMessage() == null || !expected.getMessage().contains("16")) {
                        throw expected;
                    }
                }
            }
            thread.join(5000);
        }
        if (serverError.get() != null) {
            throw new AssertionError("server failed", serverError.get());
        }
        if (pointerX.get() != 3 || pointerMask.get() != 1) {
            throw new AssertionError("pointer " + pointerX.get() + " mask " + pointerMask.get());
        }
        if (keysym.get() != 'a' || keyDown.get() != 0) {
            throw new AssertionError("keysym " + keysym.get() + " down " + keyDown.get());
        }
    }

    private static void authenticationFailure() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    byte[] version = new byte[12];
                    in.readFully(version);
                    out.writeByte(1);
                    out.writeByte(2);
                    out.flush();
                    in.readUnsignedByte();
                    byte[] challenge = new byte[16];
                    out.write(challenge);
                    out.flush();
                    in.readFully(challenge);
                    out.writeInt(1);
                    byte[] reason = "nope".getBytes(StandardCharsets.UTF_8);
                    out.writeInt(reason.length);
                    out.write(reason);
                    out.flush();
                } catch (Exception ignored) {
                }
            });
            thread.start();
            try (Socket socket = new Socket("127.0.0.1", server.getLocalPort())) {
                socket.setSoTimeout(5000);
                RfbClient client = new RfbClient();
                try {
                    client.connect(socket, PASSWORD, true, null);
                    throw new AssertionError("auth failure was not reported");
                } catch (RfbException expected) {
                    if (expected.getMessage() == null || !expected.getMessage().contains("nope")) {
                        throw expected;
                    }
                }
            }
            thread.join(5000);
        }
    }

    private static void serve(
            Socket socket,
            AtomicInteger pointerX,
            AtomicInteger pointerMask,
            AtomicInteger keysym,
            AtomicInteger keyDown) throws Exception {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        byte[] version = new byte[12];
        in.readFully(version);
        out.writeByte(1);
        out.writeByte(2);
        out.flush();
        int chosen = in.readUnsignedByte();
        if (chosen != 2) {
            throw new AssertionError("security " + chosen);
        }
        byte[] challenge = new byte[16];
        new SecureRandom().nextBytes(challenge);
        out.write(challenge);
        out.flush();
        byte[] response = new byte[16];
        in.readFully(response);
        byte[] plain = VncAuth.decrypt(response, PASSWORD);
        if (!java.util.Arrays.equals(challenge, plain)) {
            throw new AssertionError("client failed VNC auth");
        }
        out.writeInt(0);
        out.flush();
        in.readUnsignedByte();
        out.writeShort(16);
        out.writeShort(16);
        out.write(new byte[16]);
        byte[] name = "test".getBytes(StandardCharsets.US_ASCII);
        out.writeInt(name.length);
        out.write(name);
        out.flush();

        readUntilUpdate(in, null, null, null, null);
        writeRaw(out);
        readUntilUpdate(in, null, null, null, null);
        writeCopyRect(out);
        readUntilUpdate(in, null, null, null, null);
        writeRre(out);
        readUntilUpdate(in, null, null, null, null);
        writeHextile(out);
        readUntilUpdate(in, pointerX, pointerMask, keysym, keyDown);
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(1);
        out.writeShort(1);
        out.writeInt(16);
        out.flush();
    }

    private static void readUntilUpdate(
            DataInputStream in,
            AtomicInteger pointerX,
            AtomicInteger pointerMask,
            AtomicInteger keysym,
            AtomicInteger keyDown) throws Exception {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                in.skipBytes(19);
            } else if (type == 2) {
                in.readUnsignedByte();
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    in.readInt();
                }
            } else if (type == 3) {
                in.readUnsignedByte();
                in.readUnsignedShort();
                in.readUnsignedShort();
                in.readUnsignedShort();
                in.readUnsignedShort();
                return;
            } else if (type == 4) {
                int down = in.readUnsignedByte();
                in.readUnsignedShort();
                int sym = in.readInt();
                if (keyDown != null) {
                    keyDown.set(down);
                    keysym.set(sym);
                }
            } else if (type == 5) {
                int mask = in.readUnsignedByte();
                int x = in.readUnsignedShort();
                in.readUnsignedShort();
                if (pointerX != null) {
                    pointerMask.set(mask);
                    pointerX.set(x);
                }
            } else {
                throw new AssertionError("client message " + type);
            }
        }
    }

    private static void writeRaw(DataOutputStream out) throws Exception {
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(16);
        out.writeShort(16);
        out.writeInt(RfbClient.ENC_RAW);
        byte[] raw = new byte[16 * 16 * 4];
        putPixel(raw, 0, 0xff, 0x00, 0x00);
        putPixel(raw, 1, 0x00, 0xff, 0x00);
        out.write(raw);
        out.flush();
    }

    private static void writeCopyRect(DataOutputStream out) throws Exception {
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(1);
        out.writeShort(2);
        out.writeShort(0);
        out.writeShort(2);
        out.writeShort(1);
        out.writeInt(RfbClient.ENC_COPY_RECT);
        out.writeShort(0);
        out.writeShort(0);
        out.flush();
    }

    private static void writeRre(DataOutputStream out) throws Exception {
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(2);
        out.writeShort(2);
        out.writeShort(2);
        out.writeInt(RfbClient.ENC_RRE);
        out.writeInt(1);
        writePixel(out, 0x00, 0xff, 0x00);
        writePixel(out, 0xff, 0xff, 0xff);
        out.writeShort(1);
        out.writeShort(1);
        out.writeShort(1);
        out.writeShort(1);
        out.flush();
    }

    private static void writeHextile(DataOutputStream out) throws Exception {
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(4);
        out.writeShort(16);
        out.writeShort(12);
        out.writeInt(RfbClient.ENC_HEXTILE);
        out.writeByte(0x1a);
        writePixel(out, 0x00, 0x00, 0xff);
        out.writeByte(1);
        writePixel(out, 0xff, 0x00, 0x00);
        out.writeByte(0x00);
        out.writeByte(0x00);
        out.flush();
    }

    private static void putPixel(byte[] raw, int index, int red, int green, int blue) {
        int offset = index * 4;
        raw[offset] = (byte) blue;
        raw[offset + 1] = (byte) green;
        raw[offset + 2] = (byte) red;
    }

    private static void writePixel(DataOutputStream out, int red, int green, int blue) throws Exception {
        out.writeByte(blue);
        out.writeByte(green);
        out.writeByte(red);
        out.writeByte(0);
    }

    private static void expect(RfbClient client, int x, int y, int color, String label) {
        int pixel = client.framebuffer().pixel(x, y);
        if (pixel != color) {
            throw new AssertionError(label + " got " + Integer.toHexString(pixel));
        }
    }
}
