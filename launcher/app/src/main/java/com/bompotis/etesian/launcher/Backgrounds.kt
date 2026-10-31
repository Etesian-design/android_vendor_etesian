package com.bompotis.etesian.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import java.io.File

/**
 * Background choices beyond the N9 defaults:
 * - Apps with light backgrounds: N9 light (default), true black, dark grey (Google's dark
 *   theme), or a wallpaper.
 * - Screens with black backgrounds (home, status menu, settings): black (default) or a
 *   wallpaper of their own.
 * Each wallpaper can have an effect: none, blur, dim, or "ambience" (blurred, darkened and
 * slightly more colourful, like Sailfish's Silica). The processed images are kept here and served
 * to the other apps by [BackgroundsProvider]; the choices are in Settings.Secure, where the
 * etesian-theme library reads them.
 */
object Backgrounds {
    const val LIGHT = "light"
    const val DARK = "dark"

    /** 0 N9 light, 1 true black, 2 dark grey, 3 wallpaper. */
    const val SETTING_LIGHT = "etesian_light_bg"
    /** 0 black, 1 wallpaper. */
    const val SETTING_DARK = "etesian_dark_bg"
    /** Bumped whenever an image changes, so apps reload it. */
    const val SETTING_VERSION = "etesian_bg_version"

    /** Home's wallpaper: 0 as the black screens, 1 one picture panning across the views, 2 one per view. */
    const val SETTING_HOME_MODE = "etesian_home_wallpaper"
    const val HOME_SAME = 0
    const val HOME_SCROLL = 1
    const val HOME_PER_PAGE = 2
    const val HOME_SCROLL_KIND = "home_scroll"
    /** The per-view wallpapers, as (kind, label), keyed to the home view ids. */
    val PAGES = listOf("page_widgets" to "Widgets", "page_events" to "Events",
        "page_apps" to "Applications", "page_switcher" to "Open applications")

    fun homeMode(ctx: Context) = Settings.Secure.getInt(ctx.contentResolver, SETTING_HOME_MODE, HOME_SAME)
    /** 0 none, 1 the picture: whether [kind] (a home wallpaper) is on. */
    fun setting(kind: String) = "etesian_${kind}_bg"
    fun enabled(ctx: Context, kind: String) = Settings.Secure.getInt(ctx.contentResolver, setting(kind), 0) == 1 && hasImage(ctx, kind)

    const val EFFECT_NONE = 0
    const val EFFECT_BLUR = 1
    const val EFFECT_DIM = 2
    const val EFFECT_AMBIENCE = 3

    private fun dir(ctx: Context) = File(ctx.filesDir, "backgrounds").apply { mkdirs() }
    fun original(ctx: Context, kind: String) = File(dir(ctx), "$kind-original.jpg")
    fun processed(ctx: Context, kind: String) = File(dir(ctx), "$kind.png")

    fun effect(ctx: Context, kind: String) =
        Settings.Secure.getInt(ctx.contentResolver, "etesian_${kind}_bg_effect", EFFECT_AMBIENCE)

    fun setEffect(ctx: Context, kind: String, effect: Int) {
        Settings.Secure.putInt(ctx.contentResolver, "etesian_${kind}_bg_effect", effect)
        process(ctx, kind)
    }

    fun hasImage(ctx: Context, kind: String) = original(ctx, kind).exists()

    /** Takes the user's picture for [kind], scaled to the screen, and processes it. */
    fun setImage(ctx: Context, kind: String, uri: Uri): Boolean = runCatching {
        val dm = ctx.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        original(ctx, kind).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        process(ctx, kind)
        true
    }.getOrDefault(false)

    /** Applies the effect to the original and publishes the result. */
    fun process(ctx: Context, kind: String) {
        val src = BitmapFactory.decodeFile(original(ctx, kind).path) ?: return
        val out = when (effect(ctx, kind)) {
            EFFECT_BLUR -> blur(src, 24)
            EFFECT_DIM -> dim(src, 0.45f)
            EFFECT_AMBIENCE -> dim(saturate(blur(src, 32), 1.25f), 0.35f)
            else -> src
        }
        processed(ctx, kind).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Settings.Secure.putLong(ctx.contentResolver, SETTING_VERSION, System.currentTimeMillis())
    }

    private fun dim(src: Bitmap, amount: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawColor(android.graphics.Color.argb((amount * 255).toInt(), 0, 0, 0))
        return out
    }

    private fun saturate(src: Bitmap, s: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(s) })
        })
        return out
    }

    /**
     * A soft blur: shrink, box-blur a few passes, scale back up (smooth and fast enough for a
     * one-off on a phone-sized image).
     */
    private fun blur(src: Bitmap, radius: Int): Bitmap {
        val factor = 8
        val small = Bitmap.createScaledBitmap(src, maxOf(1, src.width / factor),
            maxOf(1, src.height / factor), true)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val r = maxOf(1, radius / factor)
        repeat(3) {
            boxPass(px, w, h, r, horizontal = true)
            boxPass(px, w, h, r, horizontal = false)
        }
        val blurred = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val line = IntArray(len)
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[if (horizontal) l * w + i else i * w + l]
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = line[i.coerceIn(0, len - 1)]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            val n = 2 * r + 1
            for (i in 0 until len) {
                px[if (horizontal) l * w + i else i * w + l] =
                    (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val out = line[(i - r).coerceIn(0, len - 1)]
                val inn = line[(i + r + 1).coerceIn(0, len - 1)]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}

/**
 * Serves the processed backgrounds to other apps (read-only):
 * content://com.bompotis.etesian.launcher.backgrounds/light and …/dark.
 */
class BackgroundsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val kind = uri.lastPathSegment
        if (kind != Backgrounds.LIGHT && kind != Backgrounds.DARK) return null
        val file = Backgrounds.processed(context!!, kind)
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}

/** A bitmap covering its bounds, centred (a wallpaper behind a screen). */
class CroppedBitmap(private val bitmap: Bitmap) : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = android.graphics.Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val scale = maxOf(b.width().toFloat() / bitmap.width, b.height().toFloat() / bitmap.height)
        val w = (b.width() / scale).toInt()
        val h = (b.height() / scale).toInt()
        val x = (bitmap.width - w) / 2
        val y = (bitmap.height - h) / 2
        src.set(x, y, x + w, y + h)
        canvas.drawBitmap(bitmap, src, b, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/** A black screen's background: black, or the user's black-screen wallpaper. */
fun Backgrounds.blackScreen(ctx: Context): android.graphics.drawable.Drawable {
    val wall = Settings.Secure.getInt(ctx.contentResolver, SETTING_DARK, 0) == 1
    val bmp = if (wall) BitmapFactory.decodeFile(processed(ctx, DARK).path) else null
    return if (bmp != null) CroppedBitmap(bmp) else android.graphics.drawable.ColorDrawable(N9.BACKGROUND)
}

/**
 * A light content page's background and whether it is dark: N9 light (null: the page's own
 * colour), true black, Google's dark grey, or the user's app wallpaper.
 */
fun Backgrounds.contentPage(ctx: Context): Pair<android.graphics.drawable.Drawable?, Boolean> =
    when (Settings.Secure.getInt(ctx.contentResolver, SETTING_LIGHT, 0)) {
        1 -> android.graphics.drawable.ColorDrawable(0xFF000000.toInt()) to true
        2 -> android.graphics.drawable.ColorDrawable(0xFF121212.toInt()) to true
        3 -> (BitmapFactory.decodeFile(processed(ctx, LIGHT).path)?.let { CroppedBitmap(it) }
            ?: android.graphics.drawable.ColorDrawable(0xFF000000.toInt())) to true
        else -> null to false
    }
