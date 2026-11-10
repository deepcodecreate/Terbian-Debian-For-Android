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

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class VncAuth {
    private VncAuth() {
    }

    public static byte[] encrypt(byte[] challenge, String password) throws RfbException {
        return des(challenge, password, Cipher.ENCRYPT_MODE);
    }

    public static byte[] decrypt(byte[] block, String password) throws RfbException {
        return des(block, password, Cipher.DECRYPT_MODE);
    }

    private static byte[] des(byte[] input, String password, int mode) throws RfbException {
        if (input == null || input.length == 0 || (input.length % 8) != 0) {
            throw new RfbException("VNC auth block must be a positive multiple of 8 bytes");
        }
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(mode, new SecretKeySpec(desKey(password), "DES"));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new RfbException("VNC authentication crypto failed", e);
        }
    }

    static byte[] desKey(String password) {
        byte[] raw = password == null ? new byte[0] : password.getBytes(StandardCharsets.ISO_8859_1);
        byte[] key = new byte[8];
        for (int i = 0; i < 8; i++) {
            int value = i < raw.length ? (raw[i] & 0xff) : 0;
            key[i] = reverseBits(value);
        }
        return key;
    }

    private static byte reverseBits(int value) {
        int reversed = 0;
        for (int i = 0; i < 8; i++) {
            reversed = (reversed << 1) | (value & 1);
            value >>= 1;
        }
        return (byte) reversed;
    }
}
