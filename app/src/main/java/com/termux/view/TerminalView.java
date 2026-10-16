// SPDX-License-Identifier: Apache-2.0

package com.termux.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Scroller;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.textselection.TextSelectionCursorController;

public class TerminalView extends View {
  private static boolean TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;

  public TerminalSession mTermSession;

  public TerminalEmulator mEmulator;

  public TerminalRenderer mRenderer;

  public TerminalViewClient mClient;

  private TextSelectionCursorController mTextSelectionCursorController;

  private Handler mTerminalCursorBlinkerHandler;
  private TerminalCursorBlinkerRunnable mTerminalCursorBlinkerRunnable;
  private int mTerminalCursorBlinkerRate;
  private boolean mCursorInvisibleIgnoreOnce;

  private float mCursorAlpha = 1f;

  private ValueAnimator mCursorBlinkAnimator;

  private int mLastRenderedCursorCol;

  private int mLastRenderedCursorRow;
  private ValueAnimator mCursorMoveAnimator;

  public static final int TERMINAL_CURSOR_BLINK_RATE_MIN = 100;
  public static final int TERMINAL_CURSOR_BLINK_RATE_MAX = 2000;

  int mTopRow;

  int[] mDefaultSelectors = new int[] {-1, -1, -1, -1};

  float mScaleFactor = 1.f;
  final GestureAndScaleRecognizer mGestureRecognizer;

  private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;

  private long mMouseStartDownTime = -1;

  final Scroller mScroller;

  float mScrollRemainder;

  int mCombiningAccent;

  @RequiresApi(api = Build.VERSION_CODES.O)
  private int mAutoFillType = AUTOFILL_TYPE_NONE;

  @RequiresApi(api = Build.VERSION_CODES.O)
  private int mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;

  private String[] mAutoFillHints = new String[0];

  private final boolean mAccessibilityEnabled;

  public static final int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD =
      KeyCharacterMap.VIRTUAL_KEYBOARD;

  public static final int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;

  private static final String LOG_TAG = "TerminalView";

  public TerminalView(Context context, AttributeSet attributes) {
    super(context, attributes);
    mGestureRecognizer =
        new GestureAndScaleRecognizer(
            context,
            new GestureAndScaleRecognizer.Listener() {
              boolean scrolledWithFinger;

              @Override
              public boolean onUp(MotionEvent event) {
                mScrollRemainder = 0.0f;
                if (mEmulator != null
                    && mEmulator.isMouseTrackingActive()
                    && !event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && !isSelectingText()
                    && !scrolledWithFinger) {
                  sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                  sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                  return true;
                }
                scrolledWithFinger = false;
                return false;
              }

              @Override
              public boolean onSingleTapUp(MotionEvent event) {
                if (mEmulator == null) return true;

                if (isSelectingText()) {
                  stopTextSelectionMode();
                  return true;
                }
                requestFocus();
                mClient.onSingleTapUp(event);
                return true;
              }

              @Override
              public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
                if (mEmulator == null) return true;
                if (mEmulator.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                  sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                } else {
                  scrolledWithFinger = true;
                  distanceY += mScrollRemainder;
                  int deltaRows = (int) (distanceY / mRenderer.mFontLineSpacing);
                  mScrollRemainder = distanceY - deltaRows * mRenderer.mFontLineSpacing;
                  doScroll(e, deltaRows);
                }
                return true;
              }

              @Override
              public boolean onScale(float focusX, float focusY, float scale) {
                if (mEmulator == null || isSelectingText()) return true;
                mScaleFactor *= scale;
                mScaleFactor = mClient.onScale(mScaleFactor);
                return true;
              }

              @Override
              public boolean onFling(final MotionEvent e2, float velocityX, float velocityY) {
                if (mEmulator == null) return true;

                if (!mScroller.isFinished()) return true;

                final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
                float SCALE = 0.25f;
                if (mouseTrackingAtStartOfFling) {
                  mScroller.fling(
                      0,
                      0,
                      0,
                      -(int) (velocityY * SCALE),
                      0,
                      0,
                      -mEmulator.mRows / 2,
                      mEmulator.mRows / 2);
                } else {
                  mScroller.fling(
                      0,
                      mTopRow,
                      0,
                      -(int) (velocityY * SCALE),
                      0,
                      0,
                      -mEmulator.getScreen().getActiveTranscriptRows(),
                      0);
                }

                post(
                    new Runnable() {
                      private int mLastY = 0;

                      @Override
                      public void run() {
                        if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
                          mScroller.abortAnimation();
                          return;
                        }
                        if (mScroller.isFinished()) return;
                        boolean more = mScroller.computeScrollOffset();
                        int newY = mScroller.getCurrY();
                        int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
                        doScroll(e2, diff);
                        mLastY = newY;
                        if (more) post(this);
                      }
                    });

                return true;
              }

              @Override
              public boolean onDown(float x, float y) {
                return false;
              }

              @Override
              public boolean onDoubleTap(MotionEvent event) {
                return false;
              }

              @Override
              public void onLongPress(MotionEvent event) {
                if (mGestureRecognizer.isInProgress()) return;
                if (mClient.onLongPress(event)) return;
                if (!isSelectingText()) {
                  performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                  startTextSelectionMode(event);
                }
              }
            });
    mScroller = new Scroller(context);
    AccessibilityManager am =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    mAccessibilityEnabled = am.isEnabled();
  }

  public void setTerminalViewClient(TerminalViewClient client) {
    this.mClient = client;
  }

  public void setIsTerminalViewKeyLoggingEnabled(boolean value) {
    TERMINAL_VIEW_KEY_LOGGING_ENABLED = value;
  }

  public boolean attachSession(TerminalSession session) {
    if (session == mTermSession) return false;
    mTopRow = 0;

    mTermSession = session;
    mEmulator = null;
    mCombiningAccent = 0;

    updateSize();

    setVerticalScrollBarEnabled(true);

    return true;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (mClient.isTerminalViewSelected()) {
      if (mClient.shouldEnforceCharBasedInput()) {
        outAttrs.inputType =
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
      } else {
        outAttrs.inputType = InputType.TYPE_NULL;
      }
    } else {
      outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
    }

    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;

    return new BaseInputConnection(this, true) {
      @Override
      public boolean finishComposingText() {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
          mClient.logInfo(LOG_TAG, "IME: finishComposingText()");
        super.finishComposingText();

        sendTextToTerminal(getEditable());
        getEditable().clear();
        return true;
      }

      @Override
      public boolean commitText(CharSequence text, int newCursorPosition) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
          mClient.logInfo(LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
        }
        super.commitText(text, newCursorPosition);

        if (mEmulator == null) return true;

        Editable content = getEditable();
        sendTextToTerminal(content);
        content.clear();
        return true;
      }

      @Override
      public boolean deleteSurroundingText(int leftLength, int rightLength) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
          mClient.logInfo(
              LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
        }

        KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
        return super.deleteSurroundingText(leftLength, rightLength);
      }

      void sendTextToTerminal(CharSequence text) {
        stopTextSelectionMode();
        final int textLengthInChars = text.length();
        for (int i = 0; i < textLengthInChars; i++) {
          char firstChar = text.charAt(i);
          int codePoint;
          if (Character.isHighSurrogate(firstChar)) {
            if (++i < textLengthInChars) {
              codePoint = Character.toCodePoint(firstChar, text.charAt(i));
            } else {
              codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
            }
          } else {
            codePoint = firstChar;
          }

          if (mClient.readShiftKey()) codePoint = Character.toUpperCase(codePoint);

          boolean ctrlHeld = false;
          if (codePoint <= 31 && codePoint != 27) {
            if (codePoint == '\n') {
              codePoint = '\r';
            }

            ctrlHeld = true;
            switch (codePoint) {
              case 31:
                codePoint = '_';
                break;
              case 30:
                codePoint = '^';
                break;
              case 29:
                codePoint = ']';
                break;
              case 28:
                codePoint = '\\';
                break;
              default:
                codePoint += 96;
                break;
            }
          }

          inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
        }
      }
    };
  }

  @Override
  protected int computeVerticalScrollRange() {
    return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
  }

  @Override
  protected int computeVerticalScrollExtent() {
    return mEmulator == null ? 1 : mEmulator.mRows;
  }

  @Override
  protected int computeVerticalScrollOffset() {
    return mEmulator == null
        ? 1
        : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
  }

  public void onScreenUpdated() {
    onScreenUpdated(false);
  }

  public void onScreenUpdated(boolean skipScrolling) {
    if (mEmulator == null) return;

    int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
    if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory;

    if (isSelectingText() || mEmulator.isAutoScrollDisabled()) {
      int rowShift = mEmulator.getScrollCounter();
      if (-mTopRow + rowShift > rowsInHistory) {
        if (isSelectingText()) stopTextSelectionMode();

        if (mEmulator.isAutoScrollDisabled()) {
          mTopRow = -rowsInHistory;
          skipScrolling = true;
        }
      } else {
        skipScrolling = true;
        mTopRow -= rowShift;
        decrementYTextSelectionCursors(rowShift);
      }
    }

    if (!skipScrolling && mTopRow != 0) {
      if (mTopRow < -3) {
        awakenScrollBars();
      }
      mTopRow = 0;
    }

    mEmulator.clearScrollCounter();

    invalidate();
    if (mAccessibilityEnabled) setContentDescription(getText());
  }

  public void onContextMenuClosed(Menu menu) {
    unsetStoredSelectedText();
  }

  public void setTextSize(int textSize) {
    mRenderer =
        new TerminalRenderer(
            textSize, mRenderer == null ? Typeface.MONOSPACE : mRenderer.mTypeface);
    updateSize();
  }

  public void setTypeface(Typeface newTypeface) {
    mRenderer = new TerminalRenderer(mRenderer.mTextSize, newTypeface);
    updateSize();
    invalidate();
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return true;
  }

  @Override
  public boolean isOpaque() {
    return true;
  }

  public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
    int column = (int) (event.getX() / mRenderer.mFontWidth);
    int row =
        (int) ((event.getY() - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
    int logicalRow = row;
    if (relativeToScroll) {
      logicalRow += mTopRow;
    }
    column = mRenderer.translateVisualToLogicalColumn(mEmulator, column, logicalRow);
    return new int[] {column, logicalRow};
  }

  void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
    int[] columnAndRow = getColumnAndRow(e, false);
    int x = columnAndRow[0] + 1;
    int y = columnAndRow[1] + 1;
    if (pressed
        && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
            || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
      if (mMouseStartDownTime == e.getDownTime()) {
        x = mMouseScrollStartX;
        y = mMouseScrollStartY;
      } else {
        mMouseStartDownTime = e.getDownTime();
        mMouseScrollStartX = x;
        mMouseScrollStartY = y;
      }
    }
    mEmulator.sendMouseEvent(button, x, y, pressed);
  }

  void doScroll(MotionEvent event, int rowsDown) {
    boolean up = rowsDown < 0;
    int amount = Math.abs(rowsDown);
    for (int i = 0; i < amount; i++) {
      if (mEmulator.isMouseTrackingActive()) {
        sendMouseEventCode(
            event,
            up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON,
            true);
      } else if (mEmulator.isAlternateBufferActive()) {
        handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
      } else {
        mTopRow =
            Math.min(
                0,
                Math.max(
                    -(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
        if (!awakenScrollBars()) invalidate();
      }
    }
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (mEmulator != null
        && event.isFromSource(InputDevice.SOURCE_MOUSE)
        && event.getAction() == MotionEvent.ACTION_SCROLL) {
      boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
      doScroll(event, up ? -3 : 3);
      return true;
    }
    return false;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  @TargetApi(23)
  public boolean onTouchEvent(MotionEvent event) {
    if (mEmulator == null) return true;
    final int action = event.getAction();

    if (isSelectingText()) {
      updateFloatingToolbarVisibility(event);
      mGestureRecognizer.onTouchEvent(event);
      return true;
    } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
      if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
        if (action == MotionEvent.ACTION_DOWN) showContextMenu();
        return true;
      } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
        ClipboardManager clipboardManager =
            (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
          ClipData.Item clipItem = clipData.getItemAt(0);
          if (clipItem != null) {
            CharSequence text = clipItem.coerceToText(getContext());
            if (!TextUtils.isEmpty(text)) mEmulator.paste(text.toString());
          }
        }
      } else if (mEmulator.isMouseTrackingActive()) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
          case MotionEvent.ACTION_UP:
            sendMouseEventCode(
                event,
                TerminalEmulator.MOUSE_LEFT_BUTTON,
                event.getAction() == MotionEvent.ACTION_DOWN);
            break;
          case MotionEvent.ACTION_MOVE:
            sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
            break;
        }
      }
    }

    mGestureRecognizer.onTouchEvent(event);
    return true;
  }

  @Override
  public boolean onKeyPreIme(int keyCode, KeyEvent event) {
    if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
      mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      cancelRequestAutoFill();
      if (isSelectingText()) {
        stopTextSelectionMode();
        return true;
      } else if (mClient.shouldBackButtonBeMappedToEscape()) {
        switch (event.getAction()) {
          case KeyEvent.ACTION_DOWN:
            return onKeyDown(keyCode, event);
          case KeyEvent.ACTION_UP:
            return onKeyUp(keyCode, event);
        }
      }
    } else if (mClient.shouldUseCtrlSpaceWorkaround()
        && keyCode == KeyEvent.KEYCODE_SPACE
        && event.isCtrlPressed()) {
      return onKeyDown(keyCode, event);
    }
    return super.onKeyPreIme(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
      mClient.logInfo(
          LOG_TAG,
          "onKeyDown(keyCode="
              + keyCode
              + ", isSystem()="
              + event.isSystem()
              + ", event="
              + event
              + ")");
    if (mEmulator == null) return true;
    if (isSelectingText()) {
      stopTextSelectionMode();
    }

    if (mClient.onKeyDown(keyCode, event, mTermSession)) {
      invalidate();
      return true;
    } else if (event.isSystem()
        && (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
      return super.onKeyDown(keyCode, event);
    } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE
        && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
      mTermSession.write(event.getCharacters());
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
      return super.onKeyDown(keyCode, event);
    }

    final int metaState = event.getMetaState();
    final boolean controlDown = event.isCtrlPressed() || mClient.readControlKey();
    final boolean leftAltDown =
        (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || mClient.readAltKey();
    final boolean shiftDown = event.isShiftPressed() || mClient.readShiftKey();
    final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

    int keyMod = 0;
    if (controlDown) keyMod |= KeyHandler.KEYMOD_CTRL;
    if (event.isAltPressed() || leftAltDown) keyMod |= KeyHandler.KEYMOD_ALT;
    if (shiftDown) keyMod |= KeyHandler.KEYMOD_SHIFT;
    if (event.isNumLockOn()) keyMod |= KeyHandler.KEYMOD_NUM_LOCK;

    if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
      if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
        mClient.logInfo(LOG_TAG, "handleKeyCode() took key event");
      return true;
    }

    int bitsToClear = KeyEvent.META_CTRL_MASK;
    if (rightAltDownFromEvent) {
    } else {
      bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
    }
    int effectiveMetaState = event.getMetaState() & ~bitsToClear;

    if (shiftDown) effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
    if (mClient.readFnKey()) effectiveMetaState |= KeyEvent.META_FUNCTION_ON;

    int result = event.getUnicodeChar(effectiveMetaState);
    if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
      mClient.logInfo(
          LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
    if (result == 0) {
      return false;
    }

    int oldCombiningAccent = mCombiningAccent;
    if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
      if (mCombiningAccent != 0)
        inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
      mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
    } else {
      if (mCombiningAccent != 0) {
        int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
        if (combinedChar > 0) result = combinedChar;
        mCombiningAccent = 0;
      }
      inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
    }

    if (mCombiningAccent != oldCombiningAccent) invalidate();

    return true;
  }

  public void inputCodePoint(
      int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
    if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
      mClient.logInfo(
          LOG_TAG,
          "inputCodePoint(eventSource="
              + eventSource
              + ", codePoint="
              + codePoint
              + ", controlDownFromEvent="
              + controlDownFromEvent
              + ", leftAltDownFromEvent="
              + leftAltDownFromEvent
              + ")");
    }

    if (mTermSession == null) return;

    if (mEmulator != null) mEmulator.setCursorBlinkState(true);

    final boolean controlDown = controlDownFromEvent || mClient.readControlKey();
    final boolean altDown = leftAltDownFromEvent || mClient.readAltKey();

    if (mClient.onCodePoint(codePoint, controlDown, mTermSession)) return;

    if (controlDown) {
      if (codePoint >= 'a' && codePoint <= 'z') {
        codePoint = codePoint - 'a' + 1;
      } else if (codePoint >= 'A' && codePoint <= 'Z') {
        codePoint = codePoint - 'A' + 1;
      } else if (codePoint == ' ' || codePoint == '2') {
        codePoint = 0;
      } else if (codePoint == '[' || codePoint == '3') {
        codePoint = 27;
      } else if (codePoint == '\\' || codePoint == '4') {
        codePoint = 28;
      } else if (codePoint == ']' || codePoint == '5') {
        codePoint = 29;
      } else if (codePoint == '^' || codePoint == '6') {
        codePoint = 30;
      } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
        codePoint = 31;
      } else if (codePoint == '8') {
        codePoint = 127;
      }
    }

    if (codePoint > -1) {
      if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
        switch (codePoint) {
          case 0x02DC:
            codePoint = 0x007E;
            break;
          case 0x02CB:
            codePoint = 0x0060;
            break;
          case 0x02C6:
            codePoint = 0x005E;
            break;
        }
      }

      mTermSession.writeCodePoint(altDown, codePoint);
    }
  }

  public boolean handleKeyCode(int keyCode, int keyMod) {
    if (mEmulator != null) mEmulator.setCursorBlinkState(true);

    if (handleKeyCodeAction(keyCode, keyMod)) return true;

    TerminalEmulator term = mTermSession.getEmulator();
    String code =
        KeyHandler.getCode(
            keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
    if (code == null) return false;
    mTermSession.write(code);
    return true;
  }

  public boolean handleKeyCodeAction(int keyCode, int keyMod) {
    boolean shiftDown = (keyMod & KeyHandler.KEYMOD_SHIFT) != 0;

    switch (keyCode) {
      case KeyEvent.KEYCODE_PAGE_UP:
      case KeyEvent.KEYCODE_PAGE_DOWN:

        if (shiftDown) {
          long time = SystemClock.uptimeMillis();
          MotionEvent motionEvent =
              MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
          doScroll(
              motionEvent,
              keyCode == KeyEvent.KEYCODE_PAGE_UP ? -mEmulator.mRows : mEmulator.mRows);
          motionEvent.recycle();
          return true;
        }
    }

    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
      mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");

    if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true;

    if (mClient.onKeyUp(keyCode, event)) {
      invalidate();
      return true;
    } else if (event.isSystem()) {
      return super.onKeyUp(keyCode, event);
    }

    return true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    updateSize();
  }

  public void updateSize() {
    int viewWidth = getWidth();
    int viewHeight = getHeight();
    if (viewWidth == 0 || viewHeight == 0 || mTermSession == null) return;

    int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
    int newRows =
        Math.max(
            4, (viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);

    if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
      mTermSession.updateSize(
          newColumns, newRows, (int) mRenderer.getFontWidth(), mRenderer.getFontLineSpacing());
      mEmulator = mTermSession.getEmulator();
      mClient.onEmulatorSet();

      if (mTerminalCursorBlinkerRunnable != null)
        mTerminalCursorBlinkerRunnable.setEmulator(mEmulator);

      mTopRow = 0;
      scrollTo(0, 0);
      invalidate();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (mEmulator == null) {
      canvas.drawColor(0XFF000000);
    } else {
      detectCursorMove();
      mRenderer.mCursorAlpha = mCursorAlpha;

      int[] sel = mDefaultSelectors;
      if (mTextSelectionCursorController != null) {
        mTextSelectionCursorController.getSelectors(sel);
      }

      mRenderer.render(mEmulator, canvas, mTopRow, sel[0], sel[1], sel[2], sel[3]);

      renderTextSelection();
    }
  }

  private void detectCursorMove() {
    if (mRenderer == null) return;
    int col = mEmulator.getCursorCol();
    int row = mEmulator.getCursorRow();
    int dx = col - mLastRenderedCursorCol;
    int dy = row - mLastRenderedCursorRow;
    mLastRenderedCursorCol = col;
    mLastRenderedCursorRow = row;
    if (dx == 0 && dy == 0) return;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 1) return;

    final float startX = -dx * mRenderer.mFontWidth;
    final float startY = -dy * mRenderer.mFontLineSpacing;
    if (mCursorMoveAnimator != null) mCursorMoveAnimator.cancel();
    mCursorMoveAnimator = ValueAnimator.ofFloat(1f, 0f);
    mCursorMoveAnimator.setDuration(150);
    mCursorMoveAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
    mCursorMoveAnimator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            float progress = (float) animation.getAnimatedValue();
            mRenderer.mCursorOffsetX = startX * progress;
            mRenderer.mCursorOffsetY = startY * progress;
            invalidate();
          }
        });
    mCursorMoveAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            mRenderer.mCursorOffsetX = 0f;
            mRenderer.mCursorOffsetY = 0f;
          }
        });
    mCursorMoveAnimator.start();
  }

  public TerminalSession getCurrentSession() {
    return mTermSession;
  }

  private CharSequence getText() {
    return mEmulator
        .getScreen()
        .getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
  }

  public int getCursorX(float x) {
    return getCursorX(x, mEmulator.getCursorRow());
  }

  public int getCursorX(float x, int row) {
    int visualCol = (int) (x / mRenderer.mFontWidth);
    return mRenderer.translateVisualToLogicalColumn(mEmulator, visualCol, row);
  }

  public int getCursorY(float y) {
    return (int) (((y - 40) / mRenderer.mFontLineSpacing) + mTopRow);
  }

  public int getPointX(int cx) {
    return getPointX(cx, mEmulator.getCursorRow());
  }

  public int getPointX(int cx, int cy) {
    if (cx > mEmulator.mColumns) {
      cx = mEmulator.mColumns;
    }
    int visualCol = mRenderer.translateLogicalToVisualColumn(mEmulator, cx, cy);
    return Math.round(visualCol * mRenderer.mFontWidth);
  }

  public int getPointY(int cy) {
    return Math.round((cy - mTopRow) * mRenderer.mFontLineSpacing);
  }

  public int getTopRow() {
    return mTopRow;
  }

  public void setTopRow(int mTopRow) {
    this.mTopRow = mTopRow;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void autofill(AutofillValue value) {
    if (value.isText()) {
      mTermSession.write(value.getTextValue().toString());
    }

    resetAutoFill();
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public int getAutofillType() {
    return mAutoFillType;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public String[] getAutofillHints() {
    return mAutoFillHints;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public AutofillValue getAutofillValue() {
    return AutofillValue.forText("");
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public int getImportantForAutofill() {
    return mAutoFillImportance;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private synchronized void resetAutoFill() {
    mAutoFillType = AUTOFILL_TYPE_NONE;
    mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
    mAutoFillHints = new String[0];
  }

  public AutofillManager getAutoFillManagerService() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;

    try {
      Context context = getContext();
      if (context == null) return null;
      return context.getSystemService(AutofillManager.class);
    } catch (Exception e) {
      mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e);
      return null;
    }
  }

  public boolean isAutoFillEnabled() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

    try {
      AutofillManager autofillManager = getAutoFillManagerService();
      return autofillManager != null && autofillManager.isEnabled();
    } catch (Exception e) {
      mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e);
      return false;
    }
  }

  public synchronized void requestAutoFillUsername() {
    requestAutoFill(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new String[] {View.AUTOFILL_HINT_USERNAME}
            : null);
  }

  public synchronized void requestAutoFillPassword() {
    requestAutoFill(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new String[] {View.AUTOFILL_HINT_PASSWORD}
            : null);
  }

  public synchronized void requestAutoFill(String[] autoFillHints) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
    if (autoFillHints == null || autoFillHints.length < 1) return;

    try {
      AutofillManager autofillManager = getAutoFillManagerService();
      if (autofillManager != null && autofillManager.isEnabled()) {
        mAutoFillType = AUTOFILL_TYPE_TEXT;

        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES;

        mAutoFillHints = autoFillHints;
        autofillManager.requestAutofill(this);
      }
    } catch (Exception e) {
      mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e);
    }
  }

  public synchronized void cancelRequestAutoFill() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
    if (mAutoFillType == AUTOFILL_TYPE_NONE) return;

    try {
      AutofillManager autofillManager = getAutoFillManagerService();
      if (autofillManager != null && autofillManager.isEnabled()) {
        resetAutoFill();
        autofillManager.cancel();
      }
    } catch (Exception e) {
      mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e);
    }
  }

  public synchronized boolean setTerminalCursorBlinkerRate(int blinkRate) {
    boolean result;

    if (blinkRate != 0
        && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN
            || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
      mClient.logError(
          LOG_TAG,
          "The cursor blink rate must be in between "
              + TERMINAL_CURSOR_BLINK_RATE_MIN
              + "-"
              + TERMINAL_CURSOR_BLINK_RATE_MAX
              + ": "
              + blinkRate);
      mTerminalCursorBlinkerRate = 0;
      result = false;
    } else {
      mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to " + blinkRate);
      mTerminalCursorBlinkerRate = blinkRate;
      result = true;
    }

    if (mTerminalCursorBlinkerRate == 0) {
      mClient.logVerbose(LOG_TAG, "Cursor blinker disabled");
      stopTerminalCursorBlinker();
    }

    return result;
  }

  public synchronized void setTerminalCursorBlinkerState(
      boolean start, boolean startOnlyIfCursorEnabled) {
    stopTerminalCursorBlinker();

    if (mEmulator == null) return;

    mEmulator.setCursorBlinkingEnabled(false);

    if (start) {
      if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN
          || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX) return;

      else if (startOnlyIfCursorEnabled && !mEmulator.isCursorEnabled()) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
          mClient.logVerbose(
              LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled");
        return;
      }

      if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
        mClient.logVerbose(
            LOG_TAG, "Starting cursor blinker with the blink rate " + mTerminalCursorBlinkerRate);
      if (mTerminalCursorBlinkerHandler == null)
        mTerminalCursorBlinkerHandler = new Handler(Looper.getMainLooper());
      mTerminalCursorBlinkerRunnable =
          new TerminalCursorBlinkerRunnable(mEmulator, mTerminalCursorBlinkerRate);
      mEmulator.setCursorBlinkingEnabled(true);
      mTerminalCursorBlinkerRunnable.run();
    }
  }

  private void stopTerminalCursorBlinker() {
    if (mCursorBlinkAnimator != null) mCursorBlinkAnimator.cancel();
    mCursorAlpha = 1f;
    invalidate();
    if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
      if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logVerbose(LOG_TAG, "Stopping cursor blinker");
      mTerminalCursorBlinkerHandler.removeCallbacks(mTerminalCursorBlinkerRunnable);
    }
  }

  private class TerminalCursorBlinkerRunnable implements Runnable {
    private TerminalEmulator mEmulator;
    private final int mBlinkRate;

    public TerminalCursorBlinkerRunnable(TerminalEmulator emulator, int blinkRate) {
      mEmulator = emulator;
      mBlinkRate = blinkRate;
    }

    public void setEmulator(TerminalEmulator emulator) {
      mEmulator = emulator;
    }

    public void run() {
      try {
        if (mEmulator != null) {
          mEmulator.setCursorBlinkState(true);

          if (mCursorBlinkAnimator == null) {
            mCursorBlinkAnimator = ValueAnimator.ofFloat(1f, 0f);
            mCursorBlinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
            mCursorBlinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mCursorBlinkAnimator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                  @Override
                  public void onAnimationUpdate(ValueAnimator animation) {
                    mCursorAlpha = (float) animation.getAnimatedValue();
                    invalidate();
                  }
                });
            mCursorBlinkAnimator.addListener(
                new AnimatorListenerAdapter() {
                  @Override
                  public void onAnimationEnd(Animator animation) {
                    mCursorAlpha = 1f;
                    invalidate();
                  }
                });
          }

          mCursorBlinkAnimator.setDuration(mBlinkRate);
          if (!mCursorBlinkAnimator.isRunning()) mCursorBlinkAnimator.start();
        }
      } finally {
        mTerminalCursorBlinkerHandler.postDelayed(this, mBlinkRate);
      }
    }
  }

  TextSelectionCursorController getTextSelectionCursorController() {
    if (mTextSelectionCursorController == null) {
      mTextSelectionCursorController = new TextSelectionCursorController(this);

      final ViewTreeObserver observer = getViewTreeObserver();
      if (observer != null) {
        observer.addOnTouchModeChangeListener(mTextSelectionCursorController);
      }
    }

    return mTextSelectionCursorController;
  }

  private void showTextSelectionCursors(MotionEvent event) {
    getTextSelectionCursorController().show(event);
  }

  private boolean hideTextSelectionCursors() {
    return getTextSelectionCursorController().hide();
  }

  private void renderTextSelection() {
    if (mTextSelectionCursorController != null) mTextSelectionCursorController.render();
  }

  public boolean isSelectingText() {
    if (mTextSelectionCursorController != null) {
      return mTextSelectionCursorController.isActive();
    } else {
      return false;
    }
  }

  public String getSelectedText() {
    if (isSelectingText() && mTextSelectionCursorController != null)
      return mTextSelectionCursorController.getSelectedText();
    else return null;
  }

  @Nullable
  public String getStoredSelectedText() {
    return mTextSelectionCursorController != null
        ? mTextSelectionCursorController.getStoredSelectedText()
        : null;
  }

  public void unsetStoredSelectedText() {
    if (mTextSelectionCursorController != null)
      mTextSelectionCursorController.unsetStoredSelectedText();
  }

  private ActionMode getTextSelectionActionMode() {
    if (mTextSelectionCursorController != null) {
      return mTextSelectionCursorController.getActionMode();
    } else {
      return null;
    }
  }

  public void startTextSelectionMode(MotionEvent event) {
    if (!requestFocus()) {
      return;
    }

    showTextSelectionCursors(event);
    mClient.copyModeChanged(isSelectingText());

    invalidate();
  }

  public void stopTextSelectionMode() {
    if (hideTextSelectionCursors()) {
      mClient.copyModeChanged(isSelectingText());
      invalidate();
    }
  }

  private void decrementYTextSelectionCursors(int decrement) {
    if (mTextSelectionCursorController != null) {
      mTextSelectionCursorController.decrementYTextSelectionCursors(decrement);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (mTextSelectionCursorController != null) {
      getViewTreeObserver().addOnTouchModeChangeListener(mTextSelectionCursorController);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();

    if (mTextSelectionCursorController != null) {
      stopTextSelectionMode();

      getViewTreeObserver().removeOnTouchModeChangeListener(mTextSelectionCursorController);
      mTextSelectionCursorController.onDetached();
    }
  }

  private final Runnable mShowFloatingToolbar =
      new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void run() {
          if (getTextSelectionActionMode() != null) {
            getTextSelectionActionMode().hide(0);
          }
        }
      };

  @RequiresApi(api = Build.VERSION_CODES.M)
  private void showFloatingToolbar() {
    if (getTextSelectionActionMode() != null) {
      int delay = ViewConfiguration.getDoubleTapTimeout();
      postDelayed(mShowFloatingToolbar, delay);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  void hideFloatingToolbar() {
    if (getTextSelectionActionMode() != null) {
      removeCallbacks(mShowFloatingToolbar);
      getTextSelectionActionMode().hide(-1);
    }
  }

  public void updateFloatingToolbarVisibility(MotionEvent event) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
          hideFloatingToolbar();
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          showFloatingToolbar();
      }
    }
  }
}
