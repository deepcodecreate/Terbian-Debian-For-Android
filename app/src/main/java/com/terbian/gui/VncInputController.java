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

import android.view.GestureDetector;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.terbian.vnc.KeySyms;

import java.util.HashSet;
import java.util.Set;

public final class VncInputController {
    public interface Host {
        void onModifiersChanged();
    }

    private final VncSession session;
    private final VncFramebufferView view;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final int[] remote = new int[2];
    private final Set<Integer> keysDown = new HashSet<>();

    private Host host;
    private boolean leftHeld;
    private boolean rightArmed;
    private boolean middleArmed;
    private boolean dragLock;
    private boolean scrollMode;
    private boolean twoFinger;
    private boolean suppressLeft;
    private boolean ctrlSticky;
    private boolean altSticky;
    private boolean shiftSticky;
    private float lastPanX;
    private float lastPanY;
    private float scrollAccum;
    private int lastRemoteX;
    private int lastRemoteY;
    private int cursorX = -1;
    private int cursorY = -1;
    private int padButtons;
    private float padRemainderX;
    private float padRemainderY;
    private float padScrollAccum;

    public VncInputController(VncFramebufferView view, VncSession session) {
        this.view = view;
        this.session = session;
        scaleDetector = new ScaleGestureDetector(view.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                view.zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(view.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent event) {
                if (twoFinger || rightArmed || middleArmed) {
                    return;
                }
                if (!view.mapToRemote(event.getX(), event.getY(), remote)) {
                    return;
                }
                session.pointer(remote[0], remote[1], 0);
                session.pointer(remote[0], remote[1], 4);
                session.pointer(remote[0], remote[1], 0);
                leftHeld = false;
                suppressLeft = true;
                lastRemoteX = remote[0];
                lastRemoteY = remote[1];
                cursorX = lastRemoteX;
                cursorY = lastRemoteY;
            }
        });
        view.setOnTouchListener((v, event) -> onTouch(event));
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public boolean ctrlSticky() {
        return ctrlSticky;
    }

    public boolean altSticky() {
        return altSticky;
    }

    public boolean shiftSticky() {
        return shiftSticky;
    }

    public boolean dragLock() {
        return dragLock;
    }

    public boolean scrollMode() {
        return scrollMode;
    }

    public boolean rightArmed() {
        return rightArmed;
    }

    public boolean middleArmed() {
        return middleArmed;
    }

    public void setRightArmed(boolean armed) {
        rightArmed = armed;
        if (armed) middleArmed = false;
        notifyHost();
    }

    public void setMiddleArmed(boolean armed) {
        middleArmed = armed;
        if (armed) rightArmed = false;
        notifyHost();
    }

    public void setDragLock(boolean enabled) {
        dragLock = enabled;
        if (!enabled && leftHeld) {
            session.releasePointer();
            leftHeld = false;
        }
        notifyHost();
    }

    public void setScrollMode(boolean enabled) {
        scrollMode = enabled;
        notifyHost();
    }

    public void toggleCtrl() {
        ctrlSticky = !ctrlSticky;
        session.key(ctrlSticky, KeySyms.CONTROL_L);
        track(KeySyms.CONTROL_L, ctrlSticky);
        notifyHost();
    }

    public void toggleAlt() {
        altSticky = !altSticky;
        session.key(altSticky, KeySyms.ALT_L);
        track(KeySyms.ALT_L, altSticky);
        notifyHost();
    }

    public void toggleShift() {
        shiftSticky = !shiftSticky;
        session.key(shiftSticky, KeySyms.SHIFT_L);
        track(KeySyms.SHIFT_L, shiftSticky);
        notifyHost();
    }

    public void tap(int keysym) {
        if (keysym == 0) {
            return;
        }
        session.key(true, keysym);
        session.key(false, keysym);
    }

    public void sendText(CharSequence text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            tap(KeySyms.fromUnicode(cp));
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        int keysym = keysymFor(event);
        if (keysym == 0) {
            return false;
        }
        boolean fromIme = event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD;
        boolean special = isSpecial(event.getKeyCode());
        if (fromIme && !special && !event.isCtrlPressed() && !ctrlSticky && !altSticky) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                tap(keysym);
                return true;
            }
            sendEphemeralModifiers(event, true);
            session.key(true, keysym);
            track(keysym, true);
        } else {
            session.key(false, keysym);
            track(keysym, false);
            sendEphemeralModifiers(event, false);
        }
        return true;
    }

    private boolean ensureCursor() {
        int w = view.remoteWidth();
        int h = view.remoteHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        if (cursorX < 0 || cursorY < 0) {
            cursorX = w / 2;
            cursorY = h / 2;
        }
        cursorX = Math.max(0, Math.min(w - 1, cursorX));
        cursorY = Math.max(0, Math.min(h - 1, cursorY));
        return true;
    }

    public void padMove(float dx, float dy) {
        if (!ensureCursor()) {
            return;
        }
        float scale = Math.max(0.1f, view.displayScale());
        float gain = 1f + Math.min(1.5f, (float) Math.hypot(dx, dy) / 10f);
        padRemainderX += dx / scale * gain;
        padRemainderY += dy / scale * gain;
        int stepX = (int) padRemainderX;
        int stepY = (int) padRemainderY;
        padRemainderX -= stepX;
        padRemainderY -= stepY;
        if (stepX == 0 && stepY == 0) {
            return;
        }
        cursorX = Math.max(0, Math.min(view.remoteWidth() - 1, cursorX + stepX));
        cursorY = Math.max(0, Math.min(view.remoteHeight() - 1, cursorY + stepY));
        lastRemoteX = cursorX;
        lastRemoteY = cursorY;
        session.pointer(cursorX, cursorY, padButtons);
    }

    public void padButton(int mask, boolean down) {
        if (!ensureCursor()) {
            return;
        }
        if (down) {
            padButtons |= mask;
        } else {
            padButtons &= ~mask;
        }
        session.pointer(cursorX, cursorY, padButtons);
    }

    public void padClick(int mask) {
        if (!ensureCursor()) {
            return;
        }
        session.pointer(cursorX, cursorY, padButtons | mask);
        session.pointer(cursorX, cursorY, padButtons);
    }

    public void padDragStart() {
        padButton(1, true);
    }

    public void padDragEnd() {
        padButton(1, false);
    }

    public void padNotch(boolean up) {
        if (!ensureCursor()) {
            return;
        }
        session.wheel(cursorX, cursorY, up);
    }

    public void padScroll(float dy) {
        if (!ensureCursor()) {
            return;
        }
        padScrollAccum += dy;
        while (padScrollAccum >= 24f) {
            session.wheel(cursorX, cursorY, false);
            padScrollAccum -= 24f;
        }
        while (padScrollAccum <= -24f) {
            session.wheel(cursorX, cursorY, true);
            padScrollAccum += 24f;
        }
    }

    public boolean handleImeKeyEvent(KeyEvent event) {
        if (event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return keysymFor(event) != 0;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int keysym = keysymFor(event);
        if (keysym == 0) {
            return false;
        }
        tap(keysym);
        return true;
    }

    public void releaseAll() {
        padButtons = 0;
        session.releasePointer();
        leftHeld = false;
        suppressLeft = false;
        rightArmed = false;
        middleArmed = false;
        for (int keysym : new HashSet<>(keysDown)) {
            session.key(false, keysym);
        }
        keysDown.clear();
        ctrlSticky = false;
        altSticky = false;
        shiftSticky = false;
        notifyHost();
    }

    private boolean onTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            if (leftHeld && !dragLock) {
                session.releasePointer();
                leftHeld = false;
            }
            twoFinger = true;
            suppressLeft = true;
            lastPanX = event.getX(0);
            lastPanY = event.getY(0);
            scrollAccum = 0f;
        }
        if (twoFinger && action == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()
                && event.getPointerCount() >= 2) {
            float x = event.getX(0);
            float y = event.getY(0);
            float dx = x - lastPanX;
            float dy = y - lastPanY;
            lastPanX = x;
            lastPanY = y;
            if (scrollMode) {
                scrollAt(event, dy);
            } else {
                view.panBy(dx, dy);
            }
        }
        if (!twoFinger && event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
            if (action == MotionEvent.ACTION_DOWN) {
                suppressLeft = false;
                if (view.mapToRemote(event.getX(), event.getY(), remote)) {
                    lastRemoteX = remote[0];
                    lastRemoteY = remote[1];
                cursorX = lastRemoteX;
                cursorY = lastRemoteY;
                    if (rightArmed) {
                        click(4);
                        rightArmed = false;
                        suppressLeft = true;
                        notifyHost();
                    } else if (middleArmed) {
                        click(2);
                        middleArmed = false;
                        suppressLeft = true;
                        notifyHost();
                    } else if (dragLock && leftHeld) {
                        session.pointer(remote[0], remote[1], 1);
                    } else {
                        session.pointer(remote[0], remote[1], 1);
                        leftHeld = true;
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE && leftHeld && !suppressLeft) {
                if (view.mapToRemote(event.getX(), event.getY(), remote)) {
                    lastRemoteX = remote[0];
                    lastRemoteY = remote[1];
                cursorX = lastRemoteX;
                cursorY = lastRemoteY;
                    session.pointer(remote[0], remote[1], 1);
                }
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (leftHeld && !dragLock) {
                session.releasePointer();
                leftHeld = false;
            }
            twoFinger = false;
            suppressLeft = false;
        }
        return true;
    }

    private void scrollAt(MotionEvent event, float dy) {
        scrollAccum += dy;
        if (!view.mapToRemote(event.getX(), event.getY(), remote)) {
            remote[0] = lastRemoteX;
            remote[1] = lastRemoteY;
        }
        while (scrollAccum >= 36f) {
            session.wheel(remote[0], remote[1], false);
            scrollAccum -= 36f;
        }
        while (scrollAccum <= -36f) {
            session.wheel(remote[0], remote[1], true);
            scrollAccum += 36f;
        }
    }

    private void click(int mask) {
        session.pointer(remote[0], remote[1], mask);
        session.pointer(remote[0], remote[1], dragLock && leftHeld ? 1 : 0);
    }

    private void sendEphemeralModifiers(KeyEvent event, boolean down) {
        if (event.isCtrlPressed() && !ctrlSticky) {
            session.key(down, KeySyms.CONTROL_L);
        }
        if (event.isAltPressed() && !altSticky) {
            session.key(down, KeySyms.ALT_L);
        }
        if (event.isShiftPressed() && !shiftSticky && isSpecial(event.getKeyCode())) {
            session.key(down, KeySyms.SHIFT_L);
        }
    }

    private static boolean isSpecial(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_F1:
            case KeyEvent.KEYCODE_F2:
            case KeyEvent.KEYCODE_F3:
            case KeyEvent.KEYCODE_F4:
            case KeyEvent.KEYCODE_F5:
            case KeyEvent.KEYCODE_F6:
            case KeyEvent.KEYCODE_F7:
            case KeyEvent.KEYCODE_F8:
            case KeyEvent.KEYCODE_F9:
            case KeyEvent.KEYCODE_F10:
            case KeyEvent.KEYCODE_F11:
            case KeyEvent.KEYCODE_F12:
                return true;
            default:
                return false;
        }
    }

    private static int keysymFor(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_ESCAPE:
                return KeySyms.ESCAPE;
            case KeyEvent.KEYCODE_TAB:
                return KeySyms.TAB;
            case KeyEvent.KEYCODE_ENTER:
                return KeySyms.RETURN;
            case KeyEvent.KEYCODE_DEL:
                return KeySyms.BACKSPACE;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return KeySyms.DELETE;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return KeySyms.LEFT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return KeySyms.UP;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return KeySyms.RIGHT;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return KeySyms.DOWN;
            case KeyEvent.KEYCODE_MOVE_HOME:
                return KeySyms.HOME;
            case KeyEvent.KEYCODE_MOVE_END:
                return KeySyms.END;
            case KeyEvent.KEYCODE_PAGE_UP:
                return KeySyms.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return KeySyms.PAGE_DOWN;
            case KeyEvent.KEYCODE_F1:
            case KeyEvent.KEYCODE_F2:
            case KeyEvent.KEYCODE_F3:
            case KeyEvent.KEYCODE_F4:
            case KeyEvent.KEYCODE_F5:
            case KeyEvent.KEYCODE_F6:
            case KeyEvent.KEYCODE_F7:
            case KeyEvent.KEYCODE_F8:
            case KeyEvent.KEYCODE_F9:
            case KeyEvent.KEYCODE_F10:
            case KeyEvent.KEYCODE_F11:
            case KeyEvent.KEYCODE_F12:
                return KeySyms.functionKey(event.getKeyCode() - KeyEvent.KEYCODE_F1 + 1);
            default:
                break;
        }
        int meta = event.getMetaState();
        if (event.isCtrlPressed() || event.isAltPressed()) {
            meta = meta & KeyEvent.META_SHIFT_ON;
        }
        int unicode = event.getUnicodeChar(meta);
        if (unicode <= 0) {
            unicode = event.getUnicodeChar(0);
        }
        if ((event.isCtrlPressed() || event.isAltPressed()) && unicode >= 'A' && unicode <= 'Z') {
            unicode = unicode - 'A' + 'a';
        }
        return KeySyms.fromUnicode(unicode);
    }

    private void track(int keysym, boolean down) {
        if (down) {
            keysDown.add(keysym);
        } else {
            keysDown.remove(keysym);
        }
    }

    private void notifyHost() {
        if (host != null) {
            host.onModifiersChanged();
        }
    }
}
