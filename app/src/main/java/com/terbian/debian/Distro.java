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

public enum Distro {
    DEBIAN("debian", "Debian", "debian/rootfs.tb"),
    UBUNTU("ubuntu", "Ubuntu", "ubuntu/rootfs.tbo");

    public final String id;
    public final String label;

    public final String assetPath;

    Distro(String id, String label, String assetPath) {
        this.id = id;
        this.label = label;
        this.assetPath = assetPath;
    }

    public File baseDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), id);
    }

    public File rootfsDir(Context context) {
        return new File(baseDir(context), "rootfs");
    }

    public static Distro fromId(String id) {
        return UBUNTU.id.equals(id) ? UBUNTU : DEBIAN;
    }
}
