package com.terbian.debian;

import android.content.Context;

import java.io.File;

public class RootfsManager {

    private final Context context;
    private final File rootfsDirectory;

    public RootfsManager(Context context) {
        this.context = context.getApplicationContext();
        rootfsDirectory = new File(context.getFilesDir(), "debian/rootfs");
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
        File debianVersion = new File(etc, "debian_version");
        return etc.isDirectory() && usr.isDirectory() && debianVersion.isFile();
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
