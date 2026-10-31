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

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The N9 fast scroll for a {@link RecyclerView}; looks and behaves like
 * {@link EtesianListFastScroller} (see {@link FastScrollCore}).
 *
 * <pre>
 * EtesianFastScroller.attach(recyclerView, position -> sectionOf(position));
 * </pre>
 *
 * The sections are read from the adapter on each touch, so adapter changes need no extra call.
 * Works with a {@link LinearLayoutManager} (including GridLayoutManager).
 */
public final class EtesianFastScroller extends RecyclerView.ItemDecoration
        implements RecyclerView.OnItemTouchListener {

    /** Gives the section (e.g. the first letter) of an adapter position. */
    public interface SectionProvider {
        /** The section of this adapter position, or null for none (e.g. a search row). */
        String sectionFor(int adapterPosition);
    }

    /** Optional short text for a section inside the grey band (e.g. "27" for "Mon 27 Oct"). */
    public interface BandLabelProvider {
        String bandLabelFor(String section);
    }

    private final RecyclerView mRecyclerView;
    private final FastScrollCore mCore;
    private RecyclerView.OnScrollListener mScrollListener;
    private Runnable mOnJump;

    /** Adds a fast scroller to [recyclerView]. */
    public static EtesianFastScroller attach(RecyclerView recyclerView,
            SectionProvider sectionProvider) {
        final EtesianFastScroller scroller =
                new EtesianFastScroller(recyclerView, sectionProvider);
        recyclerView.addItemDecoration(scroller);
        recyclerView.addOnItemTouchListener(scroller);
        scroller.mScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy != 0) scroller.mCore.onListScrolled();
            }

            @Override
            public void onScrollStateChanged(RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) scroller.mCore.onListScrolled();
            }
        };
        recyclerView.addOnScrollListener(scroller.mScrollListener);
        return scroller;
    }

    private EtesianFastScroller(RecyclerView recyclerView, SectionProvider sectionProvider) {
        mRecyclerView = recyclerView;
        mCore = new FastScrollCore(new FastScrollCore.Host() {
            @Override public View view() { return mRecyclerView; }

            @Override public int count() {
                final RecyclerView.Adapter<?> a = mRecyclerView.getAdapter();
                return a == null ? 0 : a.getItemCount();
            }

            @Override public int firstVisible() {
                if (mRecyclerView.getChildCount() == 0) return 0;
                return Math.max(0, mRecyclerView.getChildAdapterPosition(mRecyclerView.getChildAt(0)));
            }

            @Override public int childCount() { return mRecyclerView.getChildCount(); }

            @Override public int childTop(int i) { return mRecyclerView.getChildAt(i).getTop(); }

            @Override public void jumpTo(int position) {
                mRecyclerView.stopScroll();
                final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, 0);
                } else {
                    mRecyclerView.scrollToPosition(position);
                }
                // After the re-layout, for lists that track their first row (pinned headers).
                if (mOnJump != null) mRecyclerView.post(() -> mRecyclerView.post(mOnJump));
            }
        }, sectionProvider);
    }

    /** Runs after each jump, once the list has laid out its new rows. */
    public EtesianFastScroller setOnJump(Runnable onJump) {
        mOnJump = onJump;
        return this;
    }

    /** Short labels for the sections inside the grey band. */
    public EtesianFastScroller setBandLabels(BandLabelProvider labels) {
        mCore.setBandLabels(labels);
        return this;
    }

    /**
     * How much the grey band shows (0..1), e.g. to fade out a list's own section letters that
     * would sit under it.
     */
    public float bandAlpha() {
        return mCore.stripAlpha();
    }

    /** Removes this scroller from its list. */
    public void detach() {
        mCore.release();
        mRecyclerView.removeItemDecoration(this);
        mRecyclerView.removeOnItemTouchListener(this);
        if (mScrollListener != null) mRecyclerView.removeOnScrollListener(mScrollListener);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return mCore.onTouchEvent(e);
        return mCore.isDragging();
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        mCore.onTouchEvent(e);
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        mCore.draw(c);
    }
}
