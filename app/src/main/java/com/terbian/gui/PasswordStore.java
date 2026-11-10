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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class PasswordStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "terbian_gui_vnc_key";
    private static final String PREFS = "terbian_gui_secure";
    private static final String KEY_BLOB = "blob";

    private PasswordStore() {
    }

    public static boolean hasPassword(Context context) {
        return prefs(context).contains(KEY_BLOB);
    }

    public static void save(Context context, String password) throws Exception {
        if (password == null || password.isEmpty()) {
            clear(context);
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        String blob = Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        prefs(context).edit().putString(KEY_BLOB, blob).apply();
    }

    public static String load(Context context) throws Exception {
        String blob = prefs(context).getString(KEY_BLOB, null);
        if (blob == null) {
            return null;
        }
        int split = blob.indexOf(':');
        if (split <= 0) {
            clear(context);
            return null;
        }
        byte[] iv = Base64.decode(blob.substring(0, split), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(blob.substring(split + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_BLOB).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build());
            generator.generateKey();
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
        }
        return (SecretKey) keyStore.getKey(ALIAS, null);
    }
}
