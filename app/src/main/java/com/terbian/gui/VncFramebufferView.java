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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.terbian.vnc.Framebuffer;

public class VncFramebufferView extends View {
    private final Matrix matrix = new Matrix();
    private final Matrix inverse = new Matrix();
    private final float[] mapped = new float[2];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap bitmap;
    private VncInputController input;
    private float scale = 1f;
    private float transX;
    private float transY;
    private boolean fit = true;
    private int backdrop = 0xFF000000;

    public VncFramebufferView(Context context) {
        this(context, null);
    }

    public VncFramebufferView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setColor(0xFFACACAC);
        textPaint.setTextSize(42f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setPalette(com.terbian.ui.ThemePalette palette) {
        backdrop = palette.background;
        textPaint.setColor(palette.subtle);
        invalidate();
    }

    public void setInputController(VncInputController controller) {
        this.input = controller;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, false) {
            private String composing = "";

            private void replaceComposing(String next) {
                int common = 0;
                int max = Math.min(composing.length(), next.length());
                while (common < max && composing.charAt(common) == next.charAt(common)) {
                    common++;
                }
                if (common > 0 && Character.isHighSurrogate(composing.charAt(common - 1))) {
                    common--;
                }
                if (input != null) {
                    int erase = composing.codePointCount(common, composing.length());
                    for (int i = 0; i < erase; i++) {
                        input.tap(com.terbian.vnc.KeySyms.BACKSPACE);
                    }
                    input.sendText(next.substring(common));
                }
                composing = next;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                replaceComposing(text == null ? "" : text.toString());
                return true;
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                return true;
            }

            @Override
            public boolean finishComposingText() {
                composing = "";
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                replaceComposing(text == null ? "" : text.toString());
                composing = "";
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (input != null) {
                    for (int i = 0; i < beforeLength; i++) {
                        input.tap(com.terbian.vnc.KeySyms.BACKSPACE);
                    }
                    for (int i = 0; i < afterLength; i++) {
                        input.tap(com.terbian.vnc.KeySyms.DELETE);
                    }
                }
                composing = composing.substring(0, Math.max(0, composing.length() - beforeLength));
                return true;
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                return deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                if (input != null) {
                    input.tap(com.terbian.vnc.KeySyms.RETURN);
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(android.view.KeyEvent event) {
                if (input != null && (input.handleKeyEvent(event) || input.handleImeKeyEvent(event))) {
                    return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }

    public boolean isFit() {
        return fit;
    }

    public void setFit(boolean fitToScreen) {
        fit = fitToScreen;
        if (!fit && bitmap != null) {
            scale = 1f;
        }
        rebuildMatrix();
        invalidate();
    }

    public void zoomBy(float factor, float focusX, float focusY) {
        if (bitmap == null || scale <= 0f) {
            return;
        }
        fit = false;
        float next = Math.max(0.25f, Math.min(8f, scale * factor));
        float remoteX = (focusX - transX) / scale;
        float remoteY = (focusY - transY) / scale;
        scale = next;
        transX = focusX - remoteX * scale;
        transY = focusY - remoteY * scale;
        rebuildMatrix();
        invalidate();
    }

    public void panBy(float dx, float dy) {
        if (bitmap == null) {
            return;
        }
        fit = false;
        transX += dx;
        transY += dy;
        rebuildMatrix();
        invalidate();
    }

    public boolean mapToRemote(float viewX, float viewY, int[] out) {
        if (bitmap == null) {
            return false;
        }
        rebuildMatrix();
        mapped[0] = viewX;
        mapped[1] = viewY;
        inverse.mapPoints(mapped);
        int x = (int) Math.floor(mapped[0]);
        int y = (int) Math.floor(mapped[1]);
        if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) {
            return false;
        }
        out[0] = x;
        out[1] = y;
        return true;
    }

    public float displayScale() {
        rebuildMatrix();
        return scale;
    }

    public int remoteWidth() {
        return bitmap == null ? 0 : bitmap.getWidth();
    }

    public int remoteHeight() {
        return bitmap == null ? 0 : bitmap.getHeight();
    }

    public void submit(Framebuffer framebuffer) {
        submit(framebuffer, null);
    }

    public void submit(Framebuffer framebuffer, int[] dirty) {
        framebuffer.lock().lock();
        try {
            int width = framebuffer.width();
            int height = framebuffer.height();
            if (width <= 0 || height <= 0) {
                return;
            }
            if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                Bitmap next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                if (bitmap != null) {
                    bitmap.recycle();
                }
                bitmap = next;
                dirty = null;
            }
            int[] pixels = framebuffer.pixels();
            if (dirty == null) {
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            } else {
                int x = Math.max(0, dirty[0]);
                int y = Math.max(0, dirty[1]);
                int w = Math.min(width - x, dirty[2]);
                int h = Math.min(height - y, dirty[3]);
                if (w > 0 && h > 0) {
                    bitmap.setPixels(pixels, y * width + x, width, x, y, w, h);
                }
            }
        } finally {
            framebuffer.lock().unlock();
        }
        rebuildMatrix();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildMatrix();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backdrop);
        if (bitmap == null) {
            canvas.drawText("Waiting for the desktop", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }
        rebuildMatrix();
        canvas.save();
        canvas.concat(matrix);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }

    private void rebuildMatrix() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (fit) {
            scale = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            if (scale <= 0f) {
                scale = 1f;
            }
            transX = (getWidth() - bitmap.getWidth() * scale) / 2f;
            transY = (getHeight() - bitmap.getHeight() * scale) / 2f;
        } else {
            clampTranslation();
        }
        matrix.setScale(scale, scale);
        matrix.postTranslate(transX, transY);
        if (!matrix.invert(inverse)) {
            inverse.reset();
        }
    }

    private void clampTranslation() {
        float drawnWidth = bitmap.getWidth() * scale;
        float drawnHeight = bitmap.getHeight() * scale;
        if (drawnWidth <= getWidth()) {
            transX = (getWidth() - drawnWidth) / 2f;
        } else {
            transX = Math.min(0f, Math.max(getWidth() - drawnWidth, transX));
        }
        if (drawnHeight <= getHeight()) {
            transY = (getHeight() - drawnHeight) / 2f;
        } else {
            transY = Math.min(0f, Math.max(getHeight() - drawnHeight, transY));
        }
    }
}
