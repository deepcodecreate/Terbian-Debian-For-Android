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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

public class MaxHeightScrollView extends ScrollView {
    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = View.MeasureSpec.getMode(heightMeasureSpec);
        if (mode != View.MeasureSpec.UNSPECIFIED) {
            int cap = (int) (View.MeasureSpec.getSize(heightMeasureSpec) * 0.6f);
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
