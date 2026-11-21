/*
 * Copyright The Etesian Authors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.bompotis.etesian.fastscroll;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

/**
 * The N9 fast scroll for a {@link ListView}; looks and behaves like {@link EtesianFastScroller}
 * (see {@link FastScrollCore}).
 *
 * <p>A ListView has no item decorations or item touch listeners, so the list forwards to it:
 *
 * <pre>
 * mScroller = new EtesianListFastScroller(this, position -> sectionOf(position));
 *
 * public boolean dispatchTouchEvent(MotionEvent ev) {
 *     return mScroller.onTouchEvent(ev) || super.dispatchTouchEvent(ev);
 * }
 * // from onScroll / onScrollStateChanged: mScroller.onListScrolled();
 * // at the end of dispatchDraw:            mScroller.draw(canvas);
 * </pre>
 *
 * Positions given to the {@link EtesianFastScroller.SectionProvider} are list positions
 * (header views included).
 */
public final class EtesianListFastScroller {

    private final FastScrollCore mCore;

    public EtesianListFastScroller(ListView listView,
            EtesianFastScroller.SectionProvider sectionProvider) {
        mCore = new FastScrollCore(new FastScrollCore.Host() {
            @Override public View view() { return listView; }
            @Override public int count() { return listView.getCount(); }
            @Override public int firstVisible() { return listView.getFirstVisiblePosition(); }
            @Override public int childCount() { return listView.getChildCount(); }
            @Override public int childTop(int i) { return listView.getChildAt(i).getTop(); }
            @Override public void jumpTo(int position) { listView.setSelectionFromTop(position, 0); }
        }, sectionProvider);
    }

    /** Short labels for the sections inside the grey band. */
    public EtesianListFastScroller setBandLabels(EtesianFastScroller.BandLabelProvider labels) {
        mCore.setBandLabels(labels);
        return this;
    }

    /**
     * Whether the band is there now. While it is, it carries the one column of section letters, so a list that
     * draws its own letter on each section header (Contacts) leaves it out; without the band, the list's own show.
     */
    public boolean isBandShown() {
        return mCore.isBandShown();
    }

    /** Turns the scroller on or off (e.g. off while searching). */
    public void setEnabled(boolean enabled) {
        mCore.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return mCore.isEnabled();
    }

    /** Call when the list scrolls: shows the band and fades it out a second later. */
    public void onListScrolled() {
        mCore.onListScrolled();
    }

    /**
     * Call first from the list's dispatchTouchEvent. Returns true when the scroller took the
     * event (the list should then not handle it).
     */
    public boolean onTouchEvent(MotionEvent e) {
        return mCore.onTouchEvent(e);
    }

    /** Call at the end of the list's dispatchDraw. */
    public void draw(Canvas c) {
        mCore.draw(c);
    }
}
