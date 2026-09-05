package com.terbian.terminal;

public enum SessionType {
    DEBIAN,
    SHELL;

    public static SessionType fromName(String name) {
        if (SHELL.name().equals(name)) return SHELL;
        return DEBIAN;
    }
}
