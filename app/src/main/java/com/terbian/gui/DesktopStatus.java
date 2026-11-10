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

public final class DesktopStatus {
    public final boolean rootfsInstalled;
    public final boolean desktopInstalled;
    public final boolean passwordConfigured;
    public final boolean serverRunning;
    public final boolean supervisorAlive;
    public final String banner;
    public final String detail;

    public DesktopStatus(
            boolean rootfsInstalled,
            boolean desktopInstalled,
            boolean passwordConfigured,
            boolean serverRunning,
            boolean supervisorAlive,
            String banner,
            String detail) {
        this.rootfsInstalled = rootfsInstalled;
        this.desktopInstalled = desktopInstalled;
        this.passwordConfigured = passwordConfigured;
        this.serverRunning = serverRunning;
        this.supervisorAlive = supervisorAlive;
        this.banner = banner == null ? "" : banner;
        this.detail = detail == null ? "" : detail;
    }
}
