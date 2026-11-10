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

package com.terbian.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

public final class ToolbarBackdrop extends Drawable {
    private final View view;
    private final Paint body = new Paint();
    private final Paint bar = new Paint();

    public ToolbarBackdrop(View view, int bodyColor, int barColor) {
        this.view = view;
        body.setColor(bodyColor);
        bar.setColor(barColor);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        android.graphics.Rect b = getBounds();
        canvas.drawRect(b, body);
        int top = view.getPaddingTop();
        if (top > 0) {
            canvas.drawRect(b.left, b.top, b.right, b.top + top, bar);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
