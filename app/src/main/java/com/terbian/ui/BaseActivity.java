/*
 * Copyright 2026 deepcodecreate
 * SPDX-License-Identifier: Apache-2.0
 */
package com.terbian.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {
    private boolean pressAttached;
    private Context appearanceBase;

    @Override
    protected void attachBaseContext(Context newBase) {
        appearanceBase = newBase;
        super.attachBaseContext(Appearance.wrap(newBase));
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null && appearanceBase != null) {
            overrideConfiguration.fontScale = Appearance.fontScale(appearanceBase);
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Appearance.applyNightMode(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (!pressAttached) {
            pressAttached = true;
            android.view.View content = findViewById(android.R.id.content);

            if (content != null) content.post(() -> Motion.attachPressFeedback(content));
        }
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        super.startActivity(intent, options);
        Motion.opening(this);
    }

    @Override
    public void finish() {
        super.finish();
        Motion.closing(this);
    }
}
