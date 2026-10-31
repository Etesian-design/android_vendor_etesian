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

package com.bompotis.etesian.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;

/**
 * The user's background choice for Etesian apps. Content apps (light on the N9) can be N9
 * light (default), true black, dark grey (Google's dark theme) or a wallpaper; screens that are
 * black on the N9 can be black (default) or a wallpaper of their own. Wallpapers come with the
 * user's chosen effect already applied, and are always dark enough for light text.
 *
 * <pre>
 * EtesianBackground.Style s = EtesianBackground.content(activity);
 * EtesianBackground.apply(activity, s);   // window background
 * if (s.dark) { use light text and transparent or dark surfaces }
 * </pre>
 */
public final class EtesianBackground {

    /** One way a screen's background can look. */
    public enum Style {
        /** The N9's light grey content background: dark text. */
        N9_LIGHT(false, 0xFFE6E6E6, 0xFFF4F4F4),
        /** True black: light text. */
        BLACK(true, 0xFF000000, 0xFF161616),
        /** Google's dark theme greys: light text. */
        DARK_GREY(true, 0xFF121212, 0xFF1E1E1E),
        /** The user's wallpaper (with its effect): light text, translucent surfaces. */
        WALLPAPER(true, 0x00000000, 0x33000000);

        /** Whether text and icons should be light. */
        public final boolean dark;
        /** The page colour (transparent for a wallpaper). */
        public final int page;
        /** Cards, rows and panels on the page. */
        public final int surface;

        Style(boolean dark, int page, int surface) {
            this.dark = dark;
            this.page = page;
            this.surface = surface;
        }

        /** Main text colour for this background. */
        public int text() {
            return dark ? 0xFFFFFFFF : 0xFF191919;
        }

        /** Secondary text colour for this background. */
        public int secondaryText() {
            return dark ? 0xFFB0B0B0 : 0xFF6E6E6E;
        }
    }

    private static final String AUTHORITY = "com.bompotis.etesian.launcher.backgrounds";

    private EtesianBackground() {}

    /** The background for a content app (N9 light by default). */
    public static Style content(Context context) {
        switch (Settings.Secure.getInt(context.getContentResolver(), "etesian_light_bg", 0)) {
            case 1: return Style.BLACK;
            case 2: return Style.DARK_GREY;
            case 3: return Style.WALLPAPER;
            default: return Style.N9_LIGHT;
        }
    }

    /** The background for a screen that is black on the N9 (black by default). */
    public static Style blackScreen(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), "etesian_dark_bg", 0) == 1
                ? Style.WALLPAPER : Style.BLACK;
    }

    /** Applies [style] as the window background of a content app. */
    public static void apply(Activity activity, Style style) {
        activity.getWindow().setBackgroundDrawable(drawable(activity, style, "light"));
    }

    /** Applies [style] as the window background of a black screen. */
    public static void applyBlackScreen(Activity activity, Style style) {
        activity.getWindow().setBackgroundDrawable(drawable(activity, style, "dark"));
    }

    /**
     * The background as a drawable (e.g. for a view): the colour, or the wallpaper centre-cropped
     * (falling back to black when there is none).
     */
    public static Drawable drawable(Context context, Style style, String kind) {
        if (style != Style.WALLPAPER) return new ColorDrawable(style.page);
        final Bitmap b = wallpaper(context, kind);
        return b != null ? new CropDrawable(b) : new ColorDrawable(0xFF000000);
    }

    private static Bitmap sCache;
    private static String sCacheKey;

    /** The processed wallpaper ("light" for content apps, "dark" for black screens). */
    public static synchronized Bitmap wallpaper(Context context, String kind) {
        final String key = kind + ":" + Settings.Secure.getLong(context.getContentResolver(),
                "etesian_bg_version", 0);
        if (key.equals(sCacheKey)) return sCache;
        Bitmap b = null;
        try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(
                Uri.parse("content://" + AUTHORITY + "/" + kind), "r")) {
            if (fd != null) b = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
        } catch (Exception ignored) {
        }
        sCache = b;
        sCacheKey = key;
        return b;
    }

    /** A bitmap scaled to cover its bounds, centred. */
    private static final class CropDrawable extends Drawable {
        private final Bitmap mBitmap;
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect mSrc = new Rect();

        CropDrawable(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            final float scale = Math.max((float) b.width() / mBitmap.getWidth(),
                    (float) b.height() / mBitmap.getHeight());
            final int w = Math.round(b.width() / scale);
            final int h = Math.round(b.height() / scale);
            final int x = (mBitmap.getWidth() - w) / 2;
            final int y = (mBitmap.getHeight() - h) / 2;
            mSrc.set(x, y, x + w, y + h);
            canvas.drawBitmap(mBitmap, mSrc, b, mPaint);
        }

        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { mPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
