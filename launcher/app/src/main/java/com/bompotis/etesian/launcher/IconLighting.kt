package com.bompotis.etesian.launcher

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The N9's top light (Etesian-design/design docs/06b-icon-lighting.md), applied to a finished icon as it is drawn
 * (Settings → Appearance → N9 lighting): the launcher lights what an icon pack draws, the pack itself is never changed.
 *
 * - Tile: +6.5% lightness at 10% of the height to -5.5% at 72%, flat below.
 * - Top rim: the crescent between the tile and the tile moved down 2.5%, white 55% fading over its height.
 * - Glyph (light, plain glyphs on colour tiles): off-white gradient over its own height, 1 px white along its
 *   top edges.
 * - Cast shadow: the glyph moved down 1.5%, blurred 3%, 32% black (10% on white tiles), on the tile only.
 * Full-picture icons (many saturated colours in the "glyph") get only the tile light and the rim; photos and logos
 * are never re-lit.
 */
object IconLighting {

    fun apply(b: Bitmap): Bitmap {
        val s = b.width
        if (s < 24 || b.height != s) return b
        val out = if (b.isMutable && b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(s * s)
        out.getPixels(px, 0, s, 0, 0, s, s)
        val inside = BooleanArray(s * s) { (px[it] ushr 24) > 127 }

        // Tile colour per row: the median of the row's pixels just inside its left and right ends.
        val tile = Array(s) { IntArray(3) }
        val hasRow = BooleanArray(s)
        for (y in 0 until s) {
            var x0 = -1; var x1 = -1
            for (x in 0 until s) if (inside[y * s + x]) { if (x0 < 0) x0 = x; x1 = x }
            val n = x1 - x0 + 1
            if (x0 < 0 || n < 6) continue
            val k = max(2, n / 10)
            val pick = ArrayList<Int>()
            for (x in x0 + 1 until x0 + k) pick.add(px[y * s + x])
            for (x in x1 - k + 1 until x1) pick.add(px[y * s + x])
            for (c in 0..2) tile[y][c] = median(pick.map { (it shr (16 - 8 * c)) and 0xFF })
            hasRow[y] = true
        }
        val rows = (0 until s).filter { hasRow[it] }
        if (rows.size < 6) return out
        // Rows where the glyph reaches the tile's sides take the colour of the nearest row that is tile.
        val mid = rows.subList(rows.size / 4, rows.size * 3 / 4)
        val ref = IntArray(3) { c -> median(mid.map { tile[it][c] }) }
        val good = rows.filter { y -> (0..2).sumOf { abs(tile[y][it] - ref[it]) } < 110 }
        if (good.isNotEmpty()) for (y in rows) if (y !in good) {
            val g = good.minByOrNull { abs(it - y) }!!
            tile[y] = tile[g].copyOf()
        }

        val glyph = BooleanArray(s * s)
        var gCount = 0
        val sats = ArrayList<Int>()
        var gTop = s; var gBottom = -1
        for (y in rows) for (x in 0 until s) {
            val i = y * s + x
            if (!inside[i]) continue
            val p = px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
            if (abs(r - tile[y][0]) + abs(g - tile[y][1]) + abs(bl - tile[y][2]) > 60) {
                glyph[i] = true; gCount++
                sats.add(max(r, max(g, bl)) - min(r, min(g, bl)))
                gTop = min(gTop, y); gBottom = max(gBottom, y)
            }
        }
        val lightTile = median(rows.map { (tile[it][0] + tile[it][1] + tile[it][2]) / 3 }) > 200
        val plain = gCount > 0 && median(sats) < 40

        val rgb = Array(3) { c -> FloatArray(s * s) { ((px[it] shr (16 - 8 * c)) and 0xFF).toFloat() } }

        // 1. The tile's light.
        for (y in 0 until s) {
            val t = ((y.toFloat() / s - 0.10f) / 0.62f).coerceIn(0f, 1f)
            val k = 0.065f + (-0.055f - 0.065f) * t
            for (x in 0 until s) {
                val i = y * s + x
                if (!inside[i] || glyph[i]) continue
                for (c in 0..2) rgb[c][i] = if (k > 0) rgb[c][i] + (255 - rgb[c][i]) * k else rgb[c][i] * (1 + k)
            }
        }

        if (plain) {
            // 2. The glyph's cast shadow, on the tile.
            val sh = FloatArray(s * s)
            val dy = (0.015f * s).roundToInt()
            for (y in 0 until s - dy) for (x in 0 until s) if (glyph[y * s + x]) sh[(y + dy) * s + x] = 1f
            blur(sh, s, 0.03f * s)
            val strength = if (lightTile) 0.10f else 0.32f
            for (i in 0 until s * s) if (inside[i] && !glyph[i] && sh[i] > 0f) {
                val f = 1 - sh[i] * strength
                for (c in 0..2) rgb[c][i] *= f
            }
            // 3. The glyph: off-white over its own height, a white line along its top edges.
            if (!lightTile) {
                val stops = arrayOf(floatArrayOf(251f, 252f, 246f), floatArrayOf(242f, 241f, 236f), floatArrayOf(224f, 219f, 216f))
                for (y in gTop..gBottom) {
                    val u = ((y - gTop).toFloat() / max(1, gBottom - gTop)).coerceIn(0f, 1f)
                    val lo = if (u < 0.5f) stops[0] else stops[1]
                    val hi = if (u < 0.5f) stops[1] else stops[2]
                    val w = if (u < 0.5f) u * 2 else u * 2 - 1
                    for (x in 0 until s) {
                        val i = y * s + x
                        if (!glyph[i]) continue
                        for (c in 0..2) rgb[c][i] = rgb[c][i] / 255f * (lo[c] + (hi[c] - lo[c]) * w)
                        if (y == 0 || !glyph[i - s]) for (c in 0..2) rgb[c][i] += (255 - rgb[c][i]) * 0.8f
                    }
                }
            }
        }

        // 4. The top rim.
        val rim = max(1, (0.025f * s).roundToInt())
        for (x in 0 until s) {
            var top = -1
            for (y in 0 until s) if (inside[y * s + x]) { top = y; break }
            if (top < 0) continue
            for (y in top until min(s, top + rim)) {
                val i = y * s + x
                if (!inside[i]) continue
                if (y - rim >= 0 && inside[(y - rim) * s + x]) continue
                val fade = (1 - (y - top).toFloat() / rim).coerceIn(0f, 1f) * (1 - y.toFloat() / s / 0.5f).coerceIn(0f, 1f)
                for (c in 0..2) rgb[c][i] += (255 - rgb[c][i]) * 0.55f * fade
            }
        }

        for (i in 0 until s * s) {
            if (!inside[i]) continue
            val a = px[i] and 0xFF000000.toInt()
            px[i] = a or (clamp(rgb[0][i]) shl 16) or (clamp(rgb[1][i]) shl 8) or clamp(rgb[2][i])
        }
        out.setPixels(px, 0, s, 0, 0, s, s)
        return out
    }

    private fun clamp(v: Float) = v.roundToInt().coerceIn(0, 255)

    private fun median(v: List<Int>): Int {
        if (v.isEmpty()) return 0
        val a = v.sorted()
        return a[a.size / 2]
    }

    /** Separable Gaussian blur in place. */
    private fun blur(a: FloatArray, s: Int, sigma: Float) {
        if (sigma < 0.5f) return
        val r = (sigma * 3).toInt()
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val sum = k.sum(); for (i in k.indices) k[i] /= sum
        val tmp = FloatArray(s * s)
        for (y in 0 until s) for (x in 0 until s) {
            var v = 0f
            for (j in -r..r) { val xx = x + j; if (xx in 0 until s) v += a[y * s + xx] * k[j + r] }
            tmp[y * s + x] = v
        }
        for (y in 0 until s) for (x in 0 until s) {
            var v = 0f
            for (j in -r..r) { val yy = y + j; if (yy in 0 until s) v += tmp[yy * s + x] * k[j + r] }
            a[y * s + x] = v
        }
    }
}
