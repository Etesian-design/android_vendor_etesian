package com.bompotis.etesian.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.content.ComponentName
import android.content.res.Resources
import androidx.core.graphics.PathParser
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Renders any launcher icon into the chosen tile shape (N9 squircle by default). */
object IconFactory {

    /** The N9 tile matches Android's built-in squircle. */
    const val SHAPE_N9 = "n9"
    const val SHAPE_SUPERELLIPSE = "superellipse"
    const val SHAPE_SYSTEM = "system"
    const val SHAPE_ROUNDED = "rounded"

    /** Current shape, set from [Prefs] before icons are loaded. */
    @Volatile var style = SHAPE_N9

    /** Chosen icon pack (package name, "" for none) and its parsed contents, set from [Prefs]. */
    @Volatile var packName = ""
    @Volatile var pack: IconPacks.Pack? = null

    /** AOSP "squircle" icon shape overlay (also offered by Pixel Launcher / Nova); used for the N9 tile. */
    private const val AOSP_SQUIRCLE = "M100.00 50.00L99.92 57.13L99.69 61.68L99.31 65.56L98.78 69.04L98.09 72.23L97.25 75.18L96.26 77.92L95.12 80.48L93.82 82.86L92.38 85.08L90.78 87.13L89.04 89.04L87.13 90.78L85.08 92.38L82.86 93.82L80.48 95.12L77.92 96.26L75.18 97.25L72.23 98.09L69.04 98.78L65.56 99.31L61.68 99.69L57.13 99.92L50.00 100.00L42.87 99.92L38.32 99.69L34.44 99.31L30.96 98.78L27.77 98.09L24.82 97.25L22.08 96.26L19.52 95.12L17.14 93.82L14.92 92.38L12.87 90.78L10.96 89.04L9.22 87.13L7.62 85.08L6.18 82.86L4.88 80.48L3.74 77.92L2.75 75.18L1.91 72.23L1.22 69.04L0.69 65.56L0.31 61.68L0.08 57.13L0.00 50.00L0.08 42.87L0.31 38.32L0.69 34.44L1.22 30.96L1.91 27.77L2.75 24.82L3.74 22.08L4.88 19.52L6.18 17.14L7.62 14.92L9.22 12.87L10.96 10.96L12.87 9.22L14.92 7.62L17.14 6.18L19.52 4.88L22.08 3.74L24.82 2.75L27.77 1.91L30.96 1.22L34.44 0.69L38.32 0.31L42.87 0.08L50.00 0.00L57.13 0.08L61.68 0.31L65.56 0.69L69.04 1.22L72.23 1.91L75.18 2.75L77.92 3.74L80.48 4.88L82.86 6.18L85.08 7.62L87.13 9.22L89.04 10.96L90.78 12.87L92.38 14.92L93.82 17.14L95.12 19.52L96.26 22.08L97.25 24.82L98.09 27.77L98.78 30.96L99.31 34.44L99.69 38.32L99.92 42.87Z"

    fun shape(size: Float): Path = when (style) {
        SHAPE_SYSTEM -> systemMask()?.let { scaled(it, size) } ?: scaled(PathParser.createPathFromPathData(AOSP_SQUIRCLE), size)
        SHAPE_SUPERELLIPSE -> superellipse(size)
        SHAPE_ROUNDED -> Path().apply { addRoundRect(RectF(0f, 0f, size, size), size * 0.2f, size * 0.2f, Path.Direction.CW) }
        else -> scaled(PathParser.createPathFromPathData(AOSP_SQUIRCLE), size)
    }

    /** Clips a view (thumbnail, icon tile, avatar) to the N9 tile shape. */
    fun clipView(v: android.view.View) {
        v.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val sz = minOf(view.width, view.height).toFloat()
                if (sz <= 0f) return
                outline.setPath(shape(sz).apply { offset((view.width - sz) / 2f, (view.height - sz) / 2f) })
            }
        }
        v.clipToOutline = true
    }

    /** A solid N9 tile drawable (icon-tile backgrounds); fills the largest centred square. */
    fun tile(color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(c: Canvas) {
            val b = bounds; val sz = minOf(b.width(), b.height()).toFloat()
            c.drawPath(shape(sz).apply { offset(b.left + (b.width() - sz) / 2f, b.top + (b.height() - sz) / 2f) }, paint)
        }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Centre-crops [src] into a px-sized N9 tile bitmap (album art, thumbs in RemoteViews). */
    fun squircled(src: Bitmap, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawPath(shape(px.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(src.width, src.height)
        val sx = (src.width - side) / 2; val sy = (src.height - side) / 2
        c.drawBitmap(src, android.graphics.Rect(sx, sy, sx + side, sy + side), android.graphics.Rect(0, 0, px, px), paint)
        return out
    }

    /** The device-wide icon shape (LineageOS Settings → Styles), defined on a 100×100 canvas. */
    private fun systemMask(): Path? = runCatching {
        val res = Resources.getSystem()
        val id = res.getIdentifier("config_icon_mask", "string", "android")
        PathParser.createPathFromPathData(res.getString(id))
    }.getOrNull()

    private fun scaled(p: Path, size: Float) = Path(p).apply {
        transform(Matrix().apply { setScale(size / 100f, size / 100f) })
    }

    /** Superellipse |x|^n + |y|^n = 1, the N9 icon silhouette. */
    fun superellipse(size: Float, n: Double = 5.0): Path {
        val path = Path()
        val r = size / 2f
        val steps = 160
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = r + r * sign(c) * abs(c).pow(2.0 / n)
            val y = r + r * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    /**
     * An app's icon: the icon pack's own drawing when it has one (used as it is, packs are
     * already shaped), else the pack's back/mask/upon treatment, else the launcher's tile.
     */
    fun render(d: Drawable, px: Int, cn: ComponentName?): Bitmap {
        val p = pack ?: return render(d, px)
        if (cn != null) p.iconFor(cn)?.let { icon ->
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, px, px)
            icon.draw(Canvas(bmp))
            return bmp
        }
        return runCatching { p.compose(d, px, cn?.hashCode() ?: 0) }.getOrNull() ?: render(d, px)
    }

    fun render(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawPath(shape(px.toFloat()), maskPaint)
            val layer = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
            canvas.saveLayer(null, layer)
            // White base shows through when an adaptive icon has a transparent background.
            canvas.drawColor(0xFFF4F4F4.toInt())
            // Adaptive layers are 108dp with the central 72dp visible.
            val ext = px / 4
            d.background?.apply { setBounds(-ext, -ext, px + ext, px + ext); draw(canvas) }
            d.foreground?.apply { setBounds(-ext, -ext, px + ext, px + ext); draw(canvas) }
            canvas.restore()
        } else {
            val raw = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(raw))
            val shape = shape(px.toFloat())
            if (fillsSquare(raw)) {
                // Full-bleed legacy icon: cut it to the squircle.
                canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.drawBitmap(raw, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                })
            } else {
                // Shaped legacy icon: place it on a white squircle tile, as the N9 did.
                canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F4F4.toInt() })
                val inset = (px * 0.16f).toInt()
                d.setBounds(inset, inset, px - inset, px - inset)
                d.draw(canvas)
            }
            raw.recycle()
        }
        return bmp
    }

    /** True when the icon is opaque at its corners and edge midpoints, i.e. already a full square. */
    private fun fillsSquare(b: Bitmap): Boolean {
        val lo = (b.width * 0.06f).toInt()
        val hi = b.width - 1 - lo
        val mid = b.width / 2
        val points = listOf(lo to lo, hi to lo, lo to hi, hi to hi, mid to lo, mid to hi, lo to mid, hi to mid)
        return points.all { (x, y) -> (b.getPixel(x, y) ushr 24) > 200 }
    }
}
