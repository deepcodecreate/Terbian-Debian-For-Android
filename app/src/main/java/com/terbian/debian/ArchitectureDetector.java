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
