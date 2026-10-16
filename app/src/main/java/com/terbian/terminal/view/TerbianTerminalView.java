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

package com.terbian.terminal.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;

public class TerbianTerminalView extends TerminalView {
    public static final float MIN_FONT_SP = 8f;
    public static final float MAX_FONT_SP = 32f;

    public interface ModifierStateListener {
        void onModifierStateChanged();
    }

    private boolean ctrlModifierActive;
    private boolean altModifierActive;
    private TerminalTheme currentTheme;
    private ModifierStateListener modifierStateListener;

    public TerbianTerminalView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public void setModifierStateListener(ModifierStateListener listener) {
        this.modifierStateListener = listener;
    }

    private void notifyModifierStateChanged() {
        if (modifierStateListener != null) {
            modifierStateListener.onModifierStateChanged();
        }
    }

    public void setCustomTypeFace(Typeface typeface) {
        if (mRenderer == null) {
            setTextSize(spToPx(14f));
        }
        setTypeface(typeface);
    }

    public boolean isCtrlModifierActive() {
        return ctrlModifierActive;
    }

    public void toggleCtrlModifier() {
        ctrlModifierActive = !ctrlModifierActive;
        notifyModifierStateChanged();
    }

    public boolean isAltModifierActive() {
        return altModifierActive;
    }

    public void toggleAltModifier() {
        altModifierActive = !altModifierActive;
        notifyModifierStateChanged();
    }

    public boolean consumeCtrlModifier() {
        boolean was = ctrlModifierActive;
        if (was) {
            ctrlModifierActive = false;
            notifyModifierStateChanged();
        }
        return was;
    }

    public boolean consumeAltModifier() {
        boolean was = altModifierActive;
        if (was) {
            altModifierActive = false;
            notifyModifierStateChanged();
        }
        return was;
    }

    public void sendRawInput(String data) {
        TerminalSession session = getCurrentSession();
        if (session != null && session.isRunning()) {
            session.write(data);
        }
    }

    public TerminalTheme getTheme() {
        return currentTheme;
    }

    public void setTheme(TerminalTheme theme) {
        if (theme == null) {
            return;
        }
        currentTheme = theme;
        setBackgroundColor(theme.backgroundColor);
        setTextSize(spToPx(theme.fontSizeSp));
        setTerminalCursorBlinkerRate(theme.cursorBlinkEnabled ? theme.cursorBlinkIntervalMs : 0);

        setTerminalCursorBlinkerState(theme.cursorBlinkEnabled, true);

        TerminalSession session = getCurrentSession();
        if (session == null) {
            return;
        }
        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) {
            return;
        }

        int[] colors = emulator.mColors.mCurrentColors;
        for (int i = 0; i < 8 && i < theme.standard.length; i++) {
            colors[i] = theme.standard[i];
        }
        for (int i = 0; i < 8 && i < theme.bright.length; i++) {
            colors[8 + i] = theme.bright[i];
        }
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foregroundColor;
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.backgroundColor;
        colors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursorColor;

        onScreenUpdated();
    }

    private int spToPx(float sp) {
        return Math.round(sp * getResources().getDisplayMetrics().scaledDensity);
    }

    public String copyScreen() {
        TerminalSession session = getCurrentSession();
        if (session == null || session.getEmulator() == null) {
            return "";
        }
        return session.getEmulator().getScreen().getTranscriptText();
    }

    public void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("terbian", text));
        }
    }

    public void pasteFromClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(getContext());
        if (text != null) {
            sendRawInput(text.toString());
        }
    }
}
