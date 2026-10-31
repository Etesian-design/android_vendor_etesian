/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.bompotis.etesian.fastscroll;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.SectionIndexer;

/**
 * A ListView with the N9 fast scroll built in, for layouts. Sections come from the adapter's
 * {@link SectionIndexer} unless {@link #setSectionProvider} gives others.
 */
public class EtesianFastScrollListView extends ListView {

    private final EtesianListFastScroller mScroller;
    private EtesianFastScroller.SectionProvider mProvider;
    private OnScrollListener mListener;

    public EtesianFastScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new EtesianListFastScroller(this, this::sectionFor);
        super.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int state) {
                if (state != SCROLL_STATE_IDLE) mScroller.onListScrolled();
                if (mListener != null) mListener.onScrollStateChanged(v, state);
            }

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (mListener != null) mListener.onScroll(v, first, visible, total);
            }
        });
    }

    public void setSectionProvider(EtesianFastScroller.SectionProvider provider) {
        mProvider = provider;
    }

    public EtesianListFastScroller scroller() {
        return mScroller;
    }

    private String sectionFor(int position) {
        if (mProvider != null) return mProvider.sectionFor(position);
        if (!(getAdapter() instanceof SectionIndexer)) return null;
        final SectionIndexer s = (SectionIndexer) getAdapter();
        final Object[] sections = s.getSections();
        if (sections == null || sections.length == 0) return null;
        final int i = s.getSectionForPosition(position);
        return i < 0 || i >= sections.length ? null : String.valueOf(sections[i]);
    }

    /** The platform scroller is replaced: this turns the N9 one on or off. */
    @Override
    public void setFastScrollEnabled(boolean enabled) {
        super.setFastScrollEnabled(false);
        if (mScroller != null) mScroller.setEnabled(enabled);
    }

    @Override
    public void setFastScrollAlwaysVisible(boolean alwaysShow) {
        super.setFastScrollAlwaysVisible(false);
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        mListener = l;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return mScroller.onTouchEvent(ev) || super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        mScroller.draw(canvas);
    }
}
