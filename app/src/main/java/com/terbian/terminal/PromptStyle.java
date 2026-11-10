/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.terminal;

public enum PromptStyle {
    KALI("kali", "Kali",
            "PS1='\\[\\e[34m\\]\u250C\u2500\u2500(\\[\\e[1;34m\\]\\u@terbian\\[\\e[0;34m\\])-[\\[\\e[0m\\]\\w\\[\\e[34m\\]]\\n"
                    + "\u2514\u2500\\[\\e[1;34m\\]\\$ \\[\\e[0m\\]'\n"),

    ROUNDED("rounded", "Rounded",
            "PS1='\\[\\e[35m\\]\u256D\u2500\\[\\e[1;36m\\]\\u\\[\\e[0;90m\\]@\\[\\e[1;32m\\]terbian"
                    + "\\[\\e[0m\\] \\[\\e[33m\\]\\w\\n"
                    + "\\[\\e[35m\\]\u2570\u2500\\[\\e[1;35m\\]\u276F \\[\\e[0m\\]'\n"),

    ARROW("arrow", "Arrow",
            "PS1='\\[\\e[1;32m\\]\u279C \\[\\e[1;36m\\]\\w \\[\\e[0m\\]'\n");

    public final String id;
    public final String label;
    private final String bashLine;

    PromptStyle(String id, String label, String bashLine) {
        this.id = id;
        this.label = label;
        this.bashLine = bashLine;
    }

    public String bashScript() {
        return bashLine;
    }

    public static PromptStyle fromId(String id) {
        for (PromptStyle s : values()) {
            if (s.id.equals(id)) return s;
        }
        return KALI;
    }
}
