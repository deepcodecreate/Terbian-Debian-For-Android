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

import java.io.File;

public class RootfsManager {
    private final Context context;
    private final Distro distro;
    private final File rootfsDirectory;

    public RootfsManager(Context context) {
        this(context, Distro.DEBIAN);
    }

    public RootfsManager(Context context, Distro distro) {
        this.context = context.getApplicationContext();
        this.distro = distro;
        rootfsDirectory = distro.rootfsDir(this.context);
    }

    public Distro getDistro() {
        return distro;
    }

    public File getRootfsDirectory() {
        return rootfsDirectory;
    }

    public boolean exists() {
        return rootfsDirectory.exists() && rootfsDirectory.isDirectory();
    }

    public boolean isInstalled() {
        if (!exists()) {
            return false;
        }
        File etc = new File(rootfsDirectory, "etc");
        File usr = new File(rootfsDirectory, "usr");
        File marker = new File(etc, "terbian-rootfs-v2");
        return etc.isDirectory() && usr.isDirectory() && marker.isFile();
    }

    public boolean createDirectory() {
        if (exists()) {
            return true;
        }
        return rootfsDirectory.mkdirs();
    }

    public long getSize() {
        if (!exists()) {
            return 0;
        }
        return calculateSize(rootfsDirectory);
    }

    private long calculateSize(File file) {
        if (file.isFile()) {
            return file.length();
        }
        long size = 0;
        File[] files = file.listFiles();
        if (files == null) {
            return 0;
        }
        for (File child : files) {
            size += calculateSize(child);
        }
        return size;
    }
}
