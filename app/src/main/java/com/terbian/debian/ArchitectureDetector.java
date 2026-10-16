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

package com.terbian.debian;

import android.os.Build;

public final class ArchitectureDetector {
    private ArchitectureDetector() {
    }

    public static String getAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_ABIS;

            if (abis != null && abis.length > 0) {
                return abis[0];
            }
        }

        return Build.CPU_ABI;
    }

    public static boolean isArm64() {
        String abi = getAbi();

        return "arm64-v8a".equals(abi);
    }

    public static boolean isArm32() {
        String abi = getAbi();

        return "armeabi-v7a".equals(abi)
                || "armeabi".equals(abi);
    }

    public static boolean isX86_64() {
        return "x86_64".equals(getAbi());
    }

    public static boolean isX86() {
        return "x86".equals(getAbi());
    }
}
