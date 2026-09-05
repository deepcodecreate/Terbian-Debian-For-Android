package com.termux.terminal;

import java.nio.charset.StandardCharsets;

public abstract class TerminalOutput {

    public final void write(String data) {
        if (data == null) return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    public abstract void write(byte[] data, int offset, int count);

    public abstract void titleChanged(String oldTitle, String newTitle);

    public abstract void onCopyTextToClipboard(String text);

    public abstract void onPasteTextFromClipboard();

    public abstract void onBell();

    public abstract void onColorsChanged();

}
