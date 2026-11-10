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

public class VncScrollStripView extends View {
    public interface Callback {
        void onStripScroll(float dy);

        void onStripNotch(boolean up);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float slop;

    private Callback callback;
    private float startY;
    private float lastY;
    private long downTime;
    private boolean moved;

    public VncScrollStripView(Context context) {
        this(context, null);
    }

    public VncScrollStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        slop = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), 8f * density);
        fill.setColor(0xFF161616);
        stroke.setColor(0xFF323232);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        mark.setColor(0xFF777777);
        mark.setTextSize(16f * density);
        mark.setTextAlign(Paint.Align.CENTER);
    }

    public void setPalette(com.terbian.ui.ThemePalette palette) {
        fill.setColor(palette.surface);
        stroke.setColor(palette.border);
        mark.setColor(palette.subtle);
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
        float cx = getWidth() / 2f;
        canvas.drawText("\u25B2", cx, getHeight() * 0.25f + mark.getTextSize() / 3f, mark);
        canvas.drawText("\u25BC", cx, getHeight() * 0.75f + mark.getTextSize() / 3f, mark);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downTime = event.getEventTime();
                startY = lastY = event.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float y = event.getY();
                if (!moved && Math.abs(y - startY) > slop) {
                    moved = true;
                }
                if (moved && callback != null) {
                    callback.onStripScroll(y - lastY);
                }
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!moved && callback != null && event.getEventTime() - downTime <= 250L) {
                    callback.onStripNotch(event.getY() < getHeight() / 2f);
                }
                return true;
            default:
                return true;
        }
    }
}
