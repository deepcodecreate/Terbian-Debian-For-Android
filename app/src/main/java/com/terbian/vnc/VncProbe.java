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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class VncProbe {
    private VncProbe() {
    }

    public static String banner(String host, int port, int timeoutMs) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            byte[] buffer = new byte[12];
            InputStream in = socket.getInputStream();
            int offset = 0;
            while (offset < buffer.length) {
                int n = in.read(buffer, offset, buffer.length - offset);
                if (n < 0) {
                    break;
                }
                offset += n;
            }
            if (offset <= 0) {
                return null;
            }
            return new String(buffer, 0, offset, StandardCharsets.US_ASCII);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isRfb(String host, int port, int timeoutMs) {
        String banner = banner(host, port, timeoutMs);
        return banner != null && banner.startsWith("RFB ");
    }
}
