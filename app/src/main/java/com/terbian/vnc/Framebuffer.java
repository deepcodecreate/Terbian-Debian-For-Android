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

import java.util.concurrent.locks.ReentrantLock;

public final class Framebuffer {
    public static final int MAX_EDGE = 8192;

    private final ReentrantLock lock = new ReentrantLock();
    private int[] pixels = new int[0];
    private volatile int width;
    private volatile int height;

    public ReentrantLock lock() {
        return lock;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0 || newWidth > MAX_EDGE || newHeight > MAX_EDGE) {
            throw new IllegalArgumentException("Unsupported desktop size " + newWidth + "x" + newHeight);
        }
        lock.lock();
        try {
            if (newWidth == width && newHeight == height) {
                return;
            }
            pixels = new int[newWidth * newHeight];
            width = newWidth;
            height = newHeight;
        } finally {
            lock.unlock();
        }
    }

    public int pixel(int x, int y) {
        lock.lock();
        try {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return 0;
            }
            return pixels[y * width + x];
        } finally {
            lock.unlock();
        }
    }
}
