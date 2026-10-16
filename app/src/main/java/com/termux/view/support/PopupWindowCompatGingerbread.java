// SPDX-License-Identifier: Apache-2.0

package com.termux.view.support;

import android.util.Log;
import android.widget.PopupWindow;

import java.lang.reflect.Method;

public class PopupWindowCompatGingerbread {
  private static Method sSetWindowLayoutTypeMethod;
  private static boolean sSetWindowLayoutTypeMethodAttempted;
  private static Method sGetWindowLayoutTypeMethod;
  private static boolean sGetWindowLayoutTypeMethodAttempted;

  public static void setWindowLayoutType(PopupWindow popupWindow, int layoutType) {
    if (!sSetWindowLayoutTypeMethodAttempted) {
      try {
        sSetWindowLayoutTypeMethod =
            PopupWindow.class.getDeclaredMethod("setWindowLayoutType", int.class);
        sSetWindowLayoutTypeMethod.setAccessible(true);
      } catch (Exception e) {
      }
      sSetWindowLayoutTypeMethodAttempted = true;
    }
    if (sSetWindowLayoutTypeMethod != null) {
      try {
        sSetWindowLayoutTypeMethod.invoke(popupWindow, layoutType);
      } catch (Exception e) {
      }
    }
  }

  public static int getWindowLayoutType(PopupWindow popupWindow) {
    if (!sGetWindowLayoutTypeMethodAttempted) {
      try {
        sGetWindowLayoutTypeMethod = PopupWindow.class.getDeclaredMethod("getWindowLayoutType");
        sGetWindowLayoutTypeMethod.setAccessible(true);
      } catch (Exception e) {
      }
      sGetWindowLayoutTypeMethodAttempted = true;
    }
    if (sGetWindowLayoutTypeMethod != null) {
      try {
        return (Integer) sGetWindowLayoutTypeMethod.invoke(popupWindow);
      } catch (Exception e) {
      }
    }
    return 0;
  }
}
