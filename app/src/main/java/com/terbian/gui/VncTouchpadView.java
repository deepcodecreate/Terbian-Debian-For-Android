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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class VncTouchpadView extends View {
    public interface Callback {
        void onPadMove(float dx, float dy);

        void onPadTap();

        void onPadTwoFingerTap();

        void onPadScroll(float dy);

        void onPadDragStart();

        void onPadDragEnd();
    }

    private static final long TAP_TIMEOUT_MS = 250L;
    private static final long DRAG_WINDOW_MS = 300L;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float slop;

    private Callback callback;
    private float startX;
    private float startY;
    private float lastX;
    private float lastY;
    private float lastAvgY;
    private long downTime;
    private int maxPointers;
    private boolean moved;
    private boolean dragging;
    private long lastTapUp = -10_000L;
    private float lastTapX;
    private float lastTapY;

    public VncTouchpadView(Context context) {
        this(context, null);
    }

    public VncTouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        slop = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), 8f * density);
        fill.setColor(0xFF161616);
        stroke.setColor(0xFF323232);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        text.setColor(0xFF666666);
        text.setTextSize(13f * density);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setPalette(com.terbian.ui.ThemePalette palette) {
        fill.setColor(palette.surface);
        stroke.setColor(palette.border);
        text.setColor(palette.subtle);
        invalidate();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = 12f * getResources().getDisplayMetrics().density;
        rect.set(1f, 1f, getWidth() - 1f, getHeight() - 1f);
        canvas.drawRoundRect(rect, r, r, fill);
        canvas.drawRoundRect(rect, r, r, stroke);
        canvas.drawText("Touchpad", getWidth() / 2f, getHeight() / 2f + text.getTextSize() / 3f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downTime = event.getEventTime();
                startX = lastX = event.getX();
                startY = lastY = event.getY();
                maxPointers = 1;
                moved = false;
                if (callback != null && event.getEventTime() - lastTapUp <= DRAG_WINDOW_MS
                        && Math.hypot(startX - lastTapX, startY - lastTapY) < slop * 4f) {
                    dragging = true;
                    lastTapUp = -10_000L;
                    callback.onPadDragStart();
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, event.getPointerCount());
                lastAvgY = averageY(event, -1);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && event.getPointerCount() >= 2) {
                    float avg = averageY(event, -1);
                    if (Math.abs(avg - lastAvgY) > 0f && callback != null) {
                        if (Math.abs(event.getX(0) - startX) + Math.abs(avg - startY) > slop) {
                            moved = true;
                        }
                        callback.onPadScroll(avg - lastAvgY);
                    }
                    lastAvgY = avg;
                } else {
                    float x = event.getX(0);
                    float y = event.getY(0);
                    if (!moved && Math.hypot(x - startX, y - startY) > slop) {
                        moved = true;
                    }
                    if ((moved || dragging) && callback != null) {
                        callback.onPadMove(x - lastX, y - lastY);
                    }
                    lastX = x;
                    lastY = y;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                if (remaining < event.getPointerCount()) {
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                }
                lastAvgY = averageY(event, event.getActionIndex());
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    if (callback != null) callback.onPadDragEnd();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    if (callback != null) callback.onPadDragEnd();
                    return true;
                }
                if (!moved && callback != null
                        && event.getEventTime() - downTime <= TAP_TIMEOUT_MS) {
                    if (maxPointers == 1) {
                        lastTapUp = event.getEventTime();
                        lastTapX = event.getX();
                        lastTapY = event.getY();
                        callback.onPadTap();
                    } else if (maxPointers == 2) {
                        callback.onPadTwoFingerTap();
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private static float averageY(MotionEvent event, int skipIndex) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skipIndex) continue;
            sum += event.getY(i);
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }
}
