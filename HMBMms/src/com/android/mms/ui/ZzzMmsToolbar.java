/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mms.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import hb.widget.toolbar.Toolbar;

import android.R;

/**
 * hummingbird add by tangyisen for  hb style 2017.3.28
 * */
public class ZzzMmsToolbar extends Toolbar {
    private static final int NO_MAX_HEIGHT = -1;

    private boolean force = true;
    private int mToolbarMinHeight;

    public ZzzMmsToolbar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mToolbarMinHeight = getMinimumHeight();
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(force) {
            setMeasuredDimension(getMeasuredWidth(), mToolbarMinHeight);
        }
    }
}