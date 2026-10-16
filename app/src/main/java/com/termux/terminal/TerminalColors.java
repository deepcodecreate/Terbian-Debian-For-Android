// SPDX-License-Identifier: Apache-2.0

package com.termux.terminal;

import android.graphics.Color;

public final class TerminalColors {
    public static final TerminalColorScheme COLOR_SCHEME = new TerminalColorScheme();

    public final int[] mCurrentColors = new int[TextStyle.NUM_INDEXED_COLORS];

    public TerminalColors() {
        reset();
    }

    public void reset(int index) {
        mCurrentColors[index] = COLOR_SCHEME.mDefaultColors[index];
    }

    public void reset() {
        System.arraycopy(COLOR_SCHEME.mDefaultColors, 0, mCurrentColors, 0, TextStyle.NUM_INDEXED_COLORS);
    }

    static int parse(String c) {
        try {
            int skipInitial, skipBetween;
            if (c.charAt(0) == '#') {
                skipInitial = 1;
                skipBetween = 0;
            } else if (c.startsWith("rgb:")) {
                skipInitial = 4;
                skipBetween = 1;
            } else {
                return 0;
            }
            int charsForColors = c.length() - skipInitial - 2 * skipBetween;
            if (charsForColors % 3 != 0) return 0;
            int componentLength = charsForColors / 3;
            double mult = 255 / (Math.pow(2, componentLength * 4) - 1);

            int currentPosition = skipInitial;
            String rString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String gString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String bString = c.substring(currentPosition, currentPosition + componentLength);

            int r = (int) (Integer.parseInt(rString, 16) * mult);
            int g = (int) (Integer.parseInt(gString, 16) * mult);
            int b = (int) (Integer.parseInt(bString, 16) * mult);
            return 0xFF << 24 | r << 16 | g << 8 | b;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    public void tryParseColor(int intoIndex, String textParameter) {
        int c = parse(textParameter);
        if (c != 0) mCurrentColors[intoIndex] = c;
    }

    public static int getPerceivedBrightnessOfColor(int color) {
        return (int)
            Math.floor(Math.sqrt(
                Math.pow(Color.red(color), 2) * 0.241 +
                    Math.pow(Color.green(color), 2) * 0.691 +
                    Math.pow(Color.blue(color), 2) * 0.068
            ));
    }
}
