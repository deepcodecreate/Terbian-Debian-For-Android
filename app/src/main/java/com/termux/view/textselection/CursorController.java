// SPDX-License-Identifier: Apache-2.0

package com.termux.view.textselection;

import android.view.MotionEvent;
import android.view.ViewTreeObserver;

import com.termux.view.TerminalView;

public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
    void show(MotionEvent event);

    boolean hide();

    void render();

    void updatePosition(TextSelectionHandleView handle, int x, int y);

    boolean onTouchEvent(MotionEvent event);

    void onDetached();

    boolean isActive();
}
