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
