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

package com.terbian.mvvm;

import com.terbian.terminal.SessionType;

public class SessionEntry {
    public final String id;
    public String title;
    public final SessionType type;

    public SessionEntry(String id, String title) {
        this(id, title, SessionType.DEBIAN);
    }

    public SessionEntry(String id, String title, SessionType type) {
        this.id = id;
        this.title = title;
        this.type = type == null ? SessionType.DEBIAN : type;
    }
}
