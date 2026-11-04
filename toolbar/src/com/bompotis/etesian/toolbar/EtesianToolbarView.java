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

package com.bompotis.etesian.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The N9 toolbar drawn inside the activity's own window, so it is part of the page: it slides,
 * fades and is swiped away with it, and the page's snapshot holds it. Looks and lays out exactly
 * as SystemUI's bar (EtesianBottomBar), which only draws for apps that don't use this library.
 */
final class EtesianToolbarView extends LinearLayout {
    static final int BAR_HEIGHT_DP = 64;
    private static final int BUTTON_DP = 48;
    private static final String TAG = "EtesianToolbar";

    /** Colours for one theme (as SystemUI's). */
    static final class Theme {
        final int top, bottom, edge, highlight, selected, icon, buttonTop, buttonBottom, stroke,
                badge, badgeText;

        Theme(int top, int bottom, int edge, int highlight, int selected, int icon,
                int buttonTop, int buttonBottom, int stroke, int badge, int badgeText) {
            this.top = top; this.bottom = bottom; this.edge = edge; this.highlight = highlight;
            this.selected = selected; this.icon = icon; this.buttonTop = buttonTop;
            this.buttonBottom = buttonBottom; this.stroke = stroke; this.badge = badge;
            this.badgeText = badgeText;
        }
    }

    /** N9: the Store/Calendar toolbar. */
    static final Theme N9_LIGHT = new Theme(0xFFD6D6D8, 0xFFADADB0, 0xFF8E8E90,
            0xFFF4F4F4, 0x66FFFFFF, 0xFF262626, 0xFFF2F2F2, 0xFFCDCDCF, 0xFF9C9C9E,
            0xFFF26B1D, Color.WHITE);
    /** Symbian Belle: charcoal gradient, white glyphs, a soft highlight under the selection. */
    static final Theme BELLE_DARK = new Theme(0xFF3C3D40, 0xFF141416, 0xFF000000,
            0xFF5C5E62, 0x30FFFFFF, Color.WHITE, 0xFF4A4B4F, 0xFF26272A, 0xFF66686C,
            0xFF2A86D8, Color.WHITE);

    private final Paint mFillPaint = new Paint();
    private final int mButtonSize;
    private Theme mTheme = N9_LIGHT;

    EtesianToolbarView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        // Taps on the bar never fall through to the page under it.
        setClickable(true);
        mButtonSize = dp(BUTTON_DP);
    }

    static int barHeightPx(Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BAR_HEIGHT_DP,
                context.getResources().getDisplayMetrics());
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** Recreates the buttons; [onBack] runs for Back, [listener] gets the items' ids. */
    void bind(Theme theme, boolean back, boolean backRight, List<EtesianToolbar.Item> items,
            EtesianToolbar.Listener listener, Runnable onBack) {
        mTheme = theme;
        removeAllViews();
        invalidate();
        final View backButton = back ? backButton(onBack) : null;
        final List<View> all = new ArrayList<>();
        if (backButton != null && !backRight) all.add(backButton);
        boolean tabs = false;
        for (EtesianToolbar.Item item : items) {
            tabs |= item.mTab;
            final View b = cell(item, listener);
            if (item.mTab) { all.add(b); continue; }
            all.add(b);
        }
        if (backButton != null && backRight) all.add(backButton);
        if (tabs) {
            for (View v : all) addView(v, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            return;
        }
        // N9 (measured, 480 px wide): the first and last button centres sit 44 px from the
        // edges whatever the count, the others evenly between them; a lone one sits at its side
        // (Back on its side, an app item by its alignment, default end).
        final int cell = Math.round(getResources().getDisplayMetrics().widthPixels * 88f / 480f);
        if (all.size() == 1) {
            final View only = all.get(0);
            final boolean start = only == backButton ? !backRight
                    : items.size() == 1 && items.get(0).mStart;
            if (start) addView(only, new LayoutParams(cell, LayoutParams.MATCH_PARENT));
            addView(new View(getContext()), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            if (!start) addView(only, new LayoutParams(cell, LayoutParams.MATCH_PARENT));
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) addView(new View(getContext()), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            addView(all.get(i), new LayoutParams(cell, LayoutParams.MATCH_PARENT));
        }
    }

    /** Back: the N9's rounded button. */
    private View backButton(Runnable onBack) {
        final ImageView back = new ImageView(getContext());
        back.setBackground(new SquircleDrawable(new int[] {mTheme.buttonTop, mTheme.buttonBottom},
                Math.max(1, dp(1)), mTheme.stroke, 0));
        back.setImageDrawable(new ArrowDrawable(mTheme.icon));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onBack.run();
        });
        final FrameLayout cell = new FrameLayout(getContext());
        cell.addView(back, new FrameLayout.LayoutParams(mButtonSize, mButtonSize, Gravity.CENTER));
        return cell;
    }

    /** One item: an icon-only cell; a selected tab is the N9's lighter block. */
    private View cell(EtesianToolbar.Item item, EtesianToolbar.Listener listener) {
        final FrameLayout cell = new FrameLayout(getContext());
        final ImageView icon = new ImageView(getContext());
        Drawable d = null;
        try {
            d = item.mIcon != null ? item.mIcon.loadDrawable(getContext())
                    : getContext().getDrawable(item.mIconRes);
        } catch (RuntimeException e) {
            Log.w(TAG, "Bad toolbar icon for " + item.mId, e);
        }
        icon.setImageDrawable(d);
        if (item.mTint) icon.setImageTintList(ColorStateList.valueOf(mTheme.icon));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setAlpha(item.mEnabled ? 1f : 0.35f);
        // An action is only its squircle (hit and pressed area); a tab is its whole slot.
        final FrameLayout target = item.mTab ? cell : new FrameLayout(getContext());
        if (!item.mTab) {
            cell.addView(target, new FrameLayout.LayoutParams(mButtonSize, mButtonSize, Gravity.CENTER));
            final StateListDrawable press = new StateListDrawable();
            press.addState(new int[] {android.R.attr.state_pressed},
                    new SquircleDrawable(new int[] {mTheme.selected, mTheme.selected}, 0, 0, mButtonSize));
            target.setBackground(press);
        }
        target.addView(icon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        if (!TextUtils.isEmpty(item.mBadge)) {
            final TextView b = new TextView(getContext());
            b.setText(item.mBadge);
            b.setTextColor(mTheme.badgeText);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(dp(18));
            b.setPadding(dp(5), 0, dp(5), 0);
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(9));
            bg.setColor(mTheme.badge);
            b.setBackground(bg);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, dp(18), Gravity.CENTER);
            lp.leftMargin = dp(18);
            lp.bottomMargin = dp(18);
            target.addView(b, lp);
        }
        if (item.mTab && item.mSelected) {
            // The selected tab: a lighter full-height cell with square edges (N9 Store).
            cell.setBackground(new ColorDrawable(mTheme.selected));
        }
        target.setContentDescription(item.mLabel);
        if (item.mEnabled) {
            target.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) listener.onItemSelected(item.mId);
            });
        }
        return cell;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // A vertical gradient, a dark edge over a light line at the top.
        final int line = Math.max(1, dp(1));
        mFillPaint.setShader(new LinearGradient(0, 0, 0, getHeight(), mTheme.top, mTheme.bottom,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), mFillPaint);
        mFillPaint.setShader(null);
        mFillPaint.setColor(mTheme.edge);
        canvas.drawRect(0, 0, getWidth(), line, mFillPaint);
        mFillPaint.setColor(mTheme.highlight);
        canvas.drawRect(0, line, getWidth(), 2 * line, mFillPaint);
        super.onDraw(canvas);
    }

    /** Left arrow, drawn centred in the button. */
    private static final class ArrowDrawable extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();

        ArrowDrawable(int color) {
            mPaint.setColor(color);
            mPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float l = getBounds().left, t = getBounds().top;
            float s = Math.min(w, h);
            float cx = l + w / 2f, cy = t + h / 2f;
            float half = s * 0.22f;
            mPath.reset();
            mPath.moveTo(cx - half, cy);
            mPath.lineTo(cx - half * 0.1f, cy - half * 0.8f);
            mPath.lineTo(cx - half * 0.1f, cy - half * 0.3f);
            mPath.lineTo(cx + half, cy - half * 0.3f);
            mPath.lineTo(cx + half, cy + half * 0.3f);
            mPath.lineTo(cx - half * 0.1f, cy + half * 0.3f);
            mPath.lineTo(cx - half * 0.1f, cy + half * 0.8f);
            mPath.close();
            c.drawPath(mPath, mPaint);
        }

        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { mPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * A button or highlight in the N9 squircle (superellipse, n = 2.8). [size] > 0 centres a
     * square of that size; 0 fills the bounds.
     */
    private static final class SquircleDrawable extends Drawable {
        private static final double N = 2.8;
        private final int[] mColors;
        private final int mStrokeWidth;
        private final int mSize;
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();

        SquircleDrawable(int[] colors, int strokeWidth, int strokeColor, int size) {
            mColors = colors;
            mStrokeWidth = strokeWidth;
            mSize = size;
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeWidth(strokeWidth);
            mStroke.setColor(strokeColor);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float w = mSize > 0 ? Math.min(mSize, Math.min(b.width(), b.height())) : b.width();
            float h = mSize > 0 ? w : b.height();
            float inset = mStrokeWidth / 2f;
            float rx = w / 2f - inset, ry = h / 2f - inset;
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            mPath.reset();
            final int steps = 64;
            for (int i = 0; i <= steps; i++) {
                double t = 2 * Math.PI * i / steps, c = Math.cos(t), s = Math.sin(t);
                float x = cx + rx * (float) (Math.signum(c) * Math.pow(Math.abs(c), 2 / N));
                float y = cy + ry * (float) (Math.signum(s) * Math.pow(Math.abs(s), 2 / N));
                if (i == 0) mPath.moveTo(x, y); else mPath.lineTo(x, y);
            }
            mPath.close();
            mFill.setShader(new LinearGradient(0, cy - ry, 0, cy + ry, mColors[0], mColors[1],
                    Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(mPath, mFill);
            if (mStrokeWidth > 0) canvas.drawPath(mPath, mStroke);
        }

        @Override public void setAlpha(int alpha) { mFill.setAlpha(alpha); mStroke.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { mFill.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
