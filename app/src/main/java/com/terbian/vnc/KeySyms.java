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

public final class KeySyms {
    public static final int BACKSPACE = 0xff08;
    public static final int TAB = 0xff09;
    public static final int RETURN = 0xff0d;
    public static final int ESCAPE = 0xff1b;
    public static final int DELETE = 0xffff;
    public static final int HOME = 0xff50;
    public static final int LEFT = 0xff51;
    public static final int UP = 0xff52;
    public static final int RIGHT = 0xff53;
    public static final int DOWN = 0xff54;
    public static final int PAGE_UP = 0xff55;
    public static final int PAGE_DOWN = 0xff56;
    public static final int END = 0xff57;
    public static final int INSERT = 0xff63;
    public static final int F1 = 0xffbe;
    public static final int SHIFT_L = 0xffe1;
    public static final int CONTROL_L = 0xffe3;
    public static final int ALT_L = 0xffe9;
    public static final int SUPER_L = 0xffeb;

    private KeySyms() {
    }

    public static int functionKey(int n) {
        if (n < 1 || n > 12) {
            throw new IllegalArgumentException("function key out of range");
        }
        return F1 + (n - 1);
    }

    public static int fromUnicode(int codePoint) {
        if (codePoint <= 0) {
            return 0;
        }
        if (codePoint == '\n' || codePoint == '\r') {
            return RETURN;
        }
        if (codePoint == '\t') {
            return TAB;
        }
        if (codePoint == 0x7f) {
            return DELETE;
        }
        if (codePoint == 0x08) {
            return BACKSPACE;
        }
        if (codePoint <= 0xff) {
            return codePoint;
        }
        return 0x01000000 | codePoint;
    }
}
