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

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The N9 fast scroll, shared by {@link EtesianFastScroller} (RecyclerView) and
 * {@link EtesianListFastScroller} (ListView) (n9-contacts-fastscroll*.png).
 *
 * <p>While the list scrolls, a wide grey translucent band shows down the right edge with each
 * visible section's label at its first row and a small rounded-square dot at the scroll position.
 * A finger on the band hides the dot; a dark band runs across the list centred on the finger with
 * the current section big and light at its left edge, and every small move steps one section.
 */
final class FastScrollCore {

    /** What the core needs from its list. */
    interface Host {
        View view();
        int count();
        int firstVisible();
        int childCount();
        /** Top of the [i]th visible child, in list coordinates. */
        int childTop(int i);
        void jumpTo(int position);
    }

    private static final int STRIP_COLOR = 0xB3B8B8BC;
    private static final int SECTION_COLOR = 0xFF737573;
    private static final int DOT_COLOR = 0xFF9C9CA0;
    private static final int DOT_RIM_COLOR = 0xFFE8E8EA;
    private static final int BAND_COLOR = 0xD91A1A1A;
    private static final int LETTER_COLOR = 0xFFFFFFFF;
    /** N9: 70 of 480 px wide; the finger band 150 of 854 px high. */
    static final float STRIP_WIDTH_FRACTION = 70f / 480f;
    static final float BAND_HEIGHT_FRACTION = 150f / 854f;
    private static final int LETTER_SIZE_SP = 72;
    private static final int LABEL_SIZE_SP = 40;
    private static final int SECTION_SIZE_SP = 12;
    private static final int DOT_DP = 8;
    /** A move this long steps one section. */
    private static final int STEP_DP = 14;
    private static final long FADE_DELAY_MS = 1000;
    private static final long FADE_DURATION_MS = 250;

    private final Host mHost;
    private final View mView;
    private final EtesianFastScroller.SectionProvider mSectionProvider;
    private EtesianFastScroller.BandLabelProvider mBandLabels;
    private final float mDensity;
    private final Paint mStripPaint = new Paint();
    private final Paint mBandPaint = new Paint();
    private final Paint mLetterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mLetterSize;
    private final float mLabelSize;
    private final List<String> mSections = new ArrayList<>();
    private final List<Integer> mPositions = new ArrayList<>();

    private boolean mEnabled = true;
    private float mStripAlpha;
    private float mStripTarget;
    private ValueAnimator mStripAnimator;
    private boolean mDragging;
    private float mDragY;
    private float mStepY;
    private int mIndex = -1;
    private String mCurrentSection;

    private final Runnable mFadeOut = () -> {
        if (!mDragging) animateStrip(0f);
    };

    FastScrollCore(Host host, EtesianFastScroller.SectionProvider sectionProvider) {
        mHost = host;
        mView = host.view();
        mSectionProvider = sectionProvider;
        mDensity = mView.getResources().getDisplayMetrics().density;
        mStripPaint.setColor(STRIP_COLOR);
        mBandPaint.setColor(BAND_COLOR);
        mLetterPaint.setColor(LETTER_COLOR);
        mLetterSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LETTER_SIZE_SP,
                mView.getResources().getDisplayMetrics());
        mLabelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP,
                mView.getResources().getDisplayMetrics());
        mLetterPaint.setTypeface(Typeface.create(
                Typeface.create("etesian-text", Typeface.NORMAL), 200, false));
        mSectionPaint.setColor(SECTION_COLOR);
        mSectionPaint.setTextAlign(Paint.Align.CENTER);
        mSectionPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                SECTION_SIZE_SP, mView.getResources().getDisplayMetrics()));
        mSectionPaint.setTypeface(Typeface.create("etesian-text", Typeface.BOLD));
        // The band's width is reserved at the list's end, so rows never run under it.
        mBasePaddingEnd = mView.getPaddingEnd();
        mView.addOnLayoutChangeListener(mLayoutListener);
        mView.post(this::reserve);
    }

    private final int mBasePaddingEnd;
    private boolean mReleased;
    private final View.OnLayoutChangeListener mLayoutListener =
            (v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol) v.post(this::reserve);
            };

    /** Gives the list back its own end padding. */
    void release() {
        mReleased = true;
        stop();
        mView.removeOnLayoutChangeListener(mLayoutListener);
        mView.setPaddingRelative(mView.getPaddingStart(), mView.getPaddingTop(), mBasePaddingEnd,
                mView.getPaddingBottom());
    }

    private void reserve() {
        if (mReleased || mView.getWidth() == 0) return;
        final int want = mBasePaddingEnd
                + (mEnabled ? Math.round(mView.getWidth() * STRIP_WIDTH_FRACTION) : 0);
        if (mView.getPaddingEnd() != want) {
            mView.setPaddingRelative(mView.getPaddingStart(), mView.getPaddingTop(), want,
                    mView.getPaddingBottom());
        }
    }

    void setBandLabels(EtesianFastScroller.BandLabelProvider labels) {
        mBandLabels = labels;
    }

    void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        reserve();
        if (!enabled) {
            stop();
            mStripAlpha = 0f;
            mStripTarget = 0f;
            mDragging = false;
            mCurrentSection = null;
            mView.invalidate();
        }
    }

    boolean isEnabled() {
        return mEnabled;
    }

    void stop() {
        mView.removeCallbacks(mFadeOut);
        if (mStripAnimator != null) mStripAnimator.cancel();
    }

    /** The list scrolled: show the band, fade it out a second later. */
    void onListScrolled() {
        if (!mEnabled || mDragging) return;
        if (mStripTarget < 1f) animateStrip(1f);
        mView.removeCallbacks(mFadeOut);
        mView.postDelayed(mFadeOut, FADE_DELAY_MS);
    }

    private void animateStrip(float target) {
        if (mStripAnimator != null) mStripAnimator.cancel();
        mStripTarget = target;
        mStripAnimator = ValueAnimator.ofFloat(mStripAlpha, target);
        mStripAnimator.setDuration(FADE_DURATION_MS);
        mStripAnimator.addUpdateListener(a -> {
            mStripAlpha = (float) a.getAnimatedValue();
            mView.invalidate();
        });
        mStripAnimator.start();
    }

    private boolean isRtl() {
        return mView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private float stripWidth() {
        return mView.getWidth() * STRIP_WIDTH_FRACTION;
    }

    private boolean inStrip(float x) {
        return isRtl() ? x <= stripWidth() : x >= mView.getWidth() - stripWidth();
    }

    /** 0 when the band is hidden, 1 when fully shown. */
    float stripAlpha() {
        return mEnabled ? mStripAlpha : 0f;
    }

    boolean isDragging() {
        return mDragging;
    }

    /** True when the core took the event (the list should not handle it). */
    boolean onTouchEvent(MotionEvent e) {
        if (!mEnabled) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mStripAlpha > 0f && inStrip(e.getX())) {
                    mDragging = true;
                    stop();
                    mStripAlpha = 1f;
                    mStripTarget = 1f;
                    if (mView.getParent() != null) {
                        mView.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    readSections();
                    mStepY = e.getY();
                    mDragY = e.getY();
                    jumpTo(indexAt(e.getY()));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) dragTo(e.getY());
                return mDragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                mDragging = false;
                mCurrentSection = null;
                mIndex = -1;
                mView.invalidate();
                mView.postDelayed(mFadeOut, FADE_DELAY_MS);
                return true;
            default:
                return mDragging;
        }
    }

    private String sectionFor(int position) {
        final String s = mSectionProvider.sectionFor(position);
        return s == null || s.isEmpty() ? null : s;
    }

    /** Distinct sections in list order, with the first position of each. */
    private void readSections() {
        mSections.clear();
        mPositions.clear();
        final int count = mHost.count();
        for (int i = 0; i < count; i++) {
            final String section = sectionFor(i);
            if (section == null || mSections.contains(section)) continue;
            mSections.add(section);
            mPositions.add(i);
        }
    }

    private int indexAt(float y) {
        final float height = Math.max(1, mView.getHeight());
        final float fraction = Math.min(Math.max(y / height, 0f), 0.9999f);
        return (int) (fraction * mSections.size());
    }

    /** N9: the band snaps section by section; any small move steps to the next or previous one. */
    private void dragTo(float y) {
        mDragY = y;
        if (Math.abs(y - mStepY) >= STEP_DP * mDensity) {
            final int dir = y > mStepY ? 1 : -1;
            mStepY = y;
            jumpTo(mIndex + dir);
        }
        mView.invalidate();
    }

    private void jumpTo(int index) {
        if (mSections.isEmpty()) return;
        index = Math.min(Math.max(index, 0), mSections.size() - 1);
        if (index != mIndex) {
            mIndex = index;
            mCurrentSection = mSections.get(index);
            mHost.jumpTo(mPositions.get(index));
        }
        mView.invalidate();
    }

    private String bandLabel(String section) {
        return mBandLabels != null ? mBandLabels.bandLabelFor(section) : section;
    }

    void draw(Canvas c) {
        if (!mEnabled) return;
        final int width = mView.getWidth();
        final int height = mView.getHeight();
        final float strip = stripWidth();
        final float left = isRtl() ? 0 : width - strip;

        if (mStripAlpha > 0f) {
            final int a = (int) (255 * mStripAlpha);
            mStripPaint.setAlpha((int) (Color.alpha(STRIP_COLOR) * mStripAlpha));
            c.drawRect(left, 0, left + strip, height, mStripPaint);

            // Each visible section's label at its first row.
            mSectionPaint.setAlpha(a);
            final int first = mHost.firstVisible();
            final float half = (mSectionPaint.ascent() + mSectionPaint.descent()) / 2;
            for (int i = 0; i < mHost.childCount(); i++) {
                final int pos = first + i;
                final String s = sectionFor(pos);
                if (s == null) continue;
                if (pos > 0 && s.equals(sectionFor(pos - 1))) continue;
                final float y = Math.max(mHost.childTop(i) + 12 * mDensity, 12 * mDensity);
                c.drawText(bandLabel(s), left + strip / 2, y - half, mSectionPaint);
            }

            // The scroll position dot, hidden while the finger is on the band.
            if (!mDragging) {
                final int count = mHost.count();
                final int visible = Math.max(1, mHost.childCount());
                final float f = count <= visible ? 0f
                        : Math.min(1f, mHost.firstVisible() / (float) (count - visible));
                final float d = DOT_DP * mDensity;
                final float top = d + f * (height - 3 * d);
                final float x = left + (strip - d) / 2;
                mDotPaint.setColor(DOT_RIM_COLOR);
                mDotPaint.setAlpha(a);
                c.drawRoundRect(x, top, x + d, top + d, 2 * mDensity, 2 * mDensity, mDotPaint);
                final float r = 1.5f * mDensity;
                mDotPaint.setColor(DOT_COLOR);
                mDotPaint.setAlpha(a);
                c.drawRoundRect(x + r, top + r, x + d - r, top + d - r, mDensity, mDensity, mDotPaint);
            }
        }

        if (mDragging && mCurrentSection != null) {
            final float screen = mView.getResources().getDisplayMetrics().heightPixels;
            final float bandHeight = BAND_HEIGHT_FRACTION * screen;
            final float top = Math.min(Math.max(mDragY - bandHeight / 2, 0f),
                    Math.max(height - bandHeight, 0f));
            if (isRtl()) {
                c.drawRect(strip, top, width, top + bandHeight, mBandPaint);
            } else {
                c.drawRect(0, top, width - strip, top + bandHeight, mBandPaint);
            }
            // A letter is big; a longer section (a date) a little smaller so it fits.
            mLetterPaint.setTextSize(mCurrentSection.length() <= 2 ? mLetterSize : mLabelSize);
            final float baseline = top + bandHeight / 2
                    - (mLetterPaint.ascent() + mLetterPaint.descent()) / 2;
            if (isRtl()) {
                mLetterPaint.setTextAlign(Paint.Align.RIGHT);
                c.drawText(mCurrentSection, width - 8 * mDensity, baseline, mLetterPaint);
            } else {
                mLetterPaint.setTextAlign(Paint.Align.LEFT);
                c.drawText(mCurrentSection, 8 * mDensity, baseline, mLetterPaint);
            }
        }
    }
}
