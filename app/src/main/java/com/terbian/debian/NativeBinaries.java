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

import android.content.Context;
import android.content.res.AssetManager;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class NativeBinaries {
    private NativeBinaries() {
    }

    public static File binDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "bin");
    }

    public static File libDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "lib");
    }

    public static synchronized void ensureExtracted(Context context) throws Exception {
        Context app = context.getApplicationContext();
        File bin = binDir(app);
        File lib = libDir(app);
        if (!bin.exists() && !bin.mkdirs()) {
            throw new java.io.IOException("Cannot create " + bin);
        }
        if (!lib.exists() && !lib.mkdirs()) {
            throw new java.io.IOException("Cannot create " + lib);
        }

        AssetManager assets = app.getAssets();
        copyAssetTree(assets, "bin/" + ArchitectureDetector.getAbi(), bin);
        copyAssetTree(assets, "bin", bin);
        copyAssetTree(assets, "lib/" + ArchitectureDetector.getAbi(), lib);
        copyAssetTree(assets, "proot", lib);

        File proot = new File(bin, "proot");
        if (!proot.isFile()) {
            File alt = new File(lib, "proot");
            if (alt.isFile()) {
                proot = alt;
            }
        }
        if (proot.isFile()) {
            chmod755(proot);
        }
        File loader = new File(lib, "libproot_loader.so");
        if (loader.isFile()) {
            chmod755(loader);
        }
    }

    public static File proot(Context context) {
        File a = new File(binDir(context), "proot");
        if (a.isFile()) return a;
        File b = new File(libDir(context), "proot");
        if (b.isFile()) return b;
        return new File(binDir(context), "proot");
    }

    public static File loader(Context context) {
        File a = new File(libDir(context), "libproot_loader.so");
        if (a.isFile()) return a;
        return new File(binDir(context), "libproot_loader.so");
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File destDir) {
        String[] children;
        try {
            children = assets.list(assetPath);
        } catch (Exception e) {
            return;
        }
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, new File(destDir, new File(assetPath).getName()));
            return;
        }
        for (String child : children) {
            String next = assetPath + "/" + child;
            String[] nested;
            try {
                nested = assets.list(next);
            } catch (Exception e) {
                nested = null;
            }
            if (nested != null && nested.length > 0) {
                File sub = new File(destDir, child);
                if (!sub.exists()) sub.mkdirs();
                copyAssetTree(assets, next, sub);
            } else {
                copyAssetFile(assets, next, new File(destDir, child));
            }
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File dest) {
        if (dest.exists() && dest.length() > 0) {
            chmod755(dest);
            return;
        }
        try (InputStream in = assets.open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            chmod755(dest);
        } catch (Exception ignored) {
            dest.delete();
        }
    }

    private static void chmod755(File file) {
        file.setReadable(true, false);
        file.setWritable(true, false);
        file.setExecutable(true, false);
        try {
            Os.chmod(file.getAbsolutePath(), 0755);
        } catch (Exception ignored) {
        }
    }
}
