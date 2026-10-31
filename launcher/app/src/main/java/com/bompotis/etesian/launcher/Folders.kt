package com.bompotis.etesian.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject

/** One cell of the Applications grid: an application, or an N9 folder of them. */
sealed class GridEntry {
    abstract val key: String
    abstract val label: String

    class App(val app: AppInfo) : GridEntry() {
        override val key get() = app.component.flattenToString()
        override val label get() = app.label
    }

    /** [private]: Android extras → Private space, the locked folder at the end (not stored, not editable). */
    class Folder(val id: String, var name: String, val apps: MutableList<AppInfo>, val private: Boolean = false) : GridEntry() {
        override val key get() = KEY_PREFIX + id
        override val label get() = name
    }

    companion object {
        const val KEY_PREFIX = "folder:"
    }
}

/**
 * N9 folders: created empty from edit mode ("New folder", appended at the end of the grid),
 * filled by dropping applications on them. Stored next to the grid order: the order lists a
 * folder as "folder:<id>", and [KEY] holds each folder's name and applications.
 */
object Folders {
    private const val PREFS = "apps"
    private const val KEY = "folders"

    /** id -> (name, components in order). */
    fun load(ctx: Context): Map<String, Pair<String, List<String>>> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { id ->
                val f = o.getJSONObject(id)
                val apps = f.optJSONArray("apps") ?: JSONArray()
                f.optString("name", "Folder") to List(apps.length()) { apps.getString(it) }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(ctx: Context, folders: List<GridEntry.Folder>) {
        val o = JSONObject()
        folders.forEach { f ->
            o.put(f.id, JSONObject().put("name", f.name)
                .put("apps", JSONArray(f.apps.map { it.component.flattenToString() })))
                .also { if (f.private) o.remove(f.id) }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36)

    /**
     * The folder's icon, as on the N9: a dark lit squircle; empty, it shows a light folder
     * glyph, otherwise up to four of its applications in a 2×2 grid, in reading order.
     */
    fun icon(ctx: Context, folder: GridEntry.Folder, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = px.toFloat()
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, s, 0xFF5A5A5E.toInt(), 0xFF26262A.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawPath(IconFactory.superellipse(s, 2.8), tile)
        // Top lighting, like the N9's icons.
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, s * 0.5f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        c.drawPath(IconFactory.superellipse(s, 2.8), gloss)
        if (folder.private) {
            // The private space: a light lock (story state private-space).
            Glyphs.drawable("lock", 0xFFE8E8E8.toInt()).apply {
                val g = (s * 0.5f).toInt(); val o = ((s - g) / 2).toInt(); setBounds(o, o, o + g, o + g); draw(c)
            }
        } else if (folder.apps.isEmpty()) {
            drawGlyph(c, s)
        } else {
            val cell = s * 0.34f
            val gap = s * 0.06f
            val start = (s - cell * 2 - gap) / 2
            folder.apps.take(4).forEachIndexed { i, app ->
                val x = start + (i % 2) * (cell + gap)
                val y = start + (i / 2) * (cell + gap)
                c.drawBitmap(app.icon, null, RectF(x, y, x + cell, y + cell), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        return bmp
    }

    /** A light folder: tab on the top left, body below. */
    private fun drawGlyph(c: Canvas, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, s * 0.3f, 0f, s * 0.72f, 0xFFF4F1E6.toInt(), 0xFFD9D3C2.toInt(), Shader.TileMode.CLAMP)
        }
        val r = s * 0.04f
        c.drawRoundRect(RectF(s * 0.26f, s * 0.30f, s * 0.48f, s * 0.40f), r, r, p)
        c.drawRoundRect(RectF(s * 0.26f, s * 0.35f, s * 0.74f, s * 0.70f), r, r, p)
    }
}
