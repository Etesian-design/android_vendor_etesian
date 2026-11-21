package com.bompotis.etesian.launcher.standby

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The standby screen's settings (Etesian settings → Standby screen): what an AMOLED screen shows while the device is
 * locked. Lines built from [StandbyItems] and the user's own words, the colour, size and alignment, and whether the
 * clock, the battery glyph and the notification icons show. Kept as JSON in Settings.Secure "etesian_standby"; the
 * on/off switch is "etesian_standby_on", which the framework reads to hand the doze screen to [StandbyDream].
 */
data class StandbyConfig(
    val lines: List<Line> = emptyList(),
    val color: Int = WHITE,
    /** Line text size, sp. */
    val size: Int = 15,
    val align: Align = Align.CENTRE,
    val clock: Boolean = true,
    val battery: Boolean = false,
    val icons: Boolean = true,
) {
    enum class Align { LEFT, CENTRE, RIGHT }

    /** One piece of a line: the user's words, or an item (its id in [StandbyItems]). */
    sealed class Part {
        data class Text(val text: String) : Part()
        data class Item(val id: String) : Part()
    }

    /** A line; when every item in it is empty, [empty] shows instead (the line hides when that is blank too). */
    data class Line(val parts: List<Part>, val empty: String = "")

    fun toJson(): String = JSONObject()
        .put("color", color).put("size", size).put("align", align.name)
        .put("clock", clock).put("battery", battery).put("icons", icons)
        .put("lines", JSONArray(lines.map { l ->
            JSONObject().put("empty", l.empty).put("parts", JSONArray(l.parts.map { p ->
                when (p) { is Part.Text -> JSONObject().put("t", p.text); is Part.Item -> JSONObject().put("i", p.id) }
            }))
        })).toString()

    companion object {
        const val KEY = "etesian_standby"
        const val KEY_ON = "etesian_standby_on"
        const val WHITE = 0xFFFFFFFF.toInt()
        /** The colours offered: white, N9 blue, green, amber, red (red is the kindest at night). */
        val COLOURS = intArrayOf(WHITE, 0xFF3D9BF0.toInt(), 0xFF33C46B.toInt(), 0xFFF2A33A.toInt(), 0xFFE5484D.toInt())

        fun fromJson(s: String?): StandbyConfig {
            if (s.isNullOrBlank()) return StandbyConfig()
            return runCatching {
                val o = JSONObject(s)
                val ls = o.optJSONArray("lines") ?: JSONArray()
                StandbyConfig(
                    lines = (0 until ls.length()).map { i ->
                        val l = ls.getJSONObject(i); val ps = l.optJSONArray("parts") ?: JSONArray()
                        Line((0 until ps.length()).map { j ->
                            val p = ps.getJSONObject(j)
                            if (p.has("i")) Part.Item(p.getString("i")) else Part.Text(p.optString("t"))
                        }, l.optString("empty"))
                    },
                    color = o.optInt("color", WHITE), size = o.optInt("size", 15).coerceIn(10, 28),
                    align = runCatching { Align.valueOf(o.optString("align")) }.getOrDefault(Align.CENTRE),
                    clock = o.optBoolean("clock", true), battery = o.optBoolean("battery", false),
                    icons = o.optBoolean("icons", true),
                )
            }.getOrDefault(StandbyConfig())
        }

        fun load(ctx: Context) = fromJson(Settings.Secure.getString(ctx.contentResolver, KEY))
        fun save(ctx: Context, c: StandbyConfig) { Settings.Secure.putString(ctx.contentResolver, KEY, c.toJson()) }

        fun isOn(ctx: Context) = Settings.Secure.getInt(ctx.contentResolver, KEY_ON, 1) == 1 &&
            Settings.Secure.getInt(ctx.contentResolver, "doze_always_on", 0) == 1
        /** On: the standby screen while locked (and Android's always-on display with it, which runs it). */
        fun setOn(ctx: Context, on: Boolean) {
            Settings.Secure.putInt(ctx.contentResolver, KEY_ON, if (on) 1 else 0)
            Settings.Secure.putInt(ctx.contentResolver, "doze_always_on", if (on) 1 else 0)
        }

        private fun t(s: String) = Part.Text(s)
        private fun i(s: String) = Part.Item(s)

        /** One tap fills the lines. */
        val PRESETS: List<Pair<String, StandbyConfig>> = listOf(
            "The N9" to StandbyConfig(lines = listOf(Line(listOf(i("date_compact")))), align = Align.CENTRE),
            "Billboard" to StandbyConfig(
                lines = listOf(
                    Line(listOf(i("date_full"))), Line(listOf(i("ip"))),
                    Line(listOf(t("Battery: "), i("battery"), t("%"))), Line(listOf(i("battery_bar"))),
                    Line(listOf(i("song")), empty = "No media"),
                ),
                align = Align.RIGHT, battery = true, icons = false,
            ),
            "Everything" to StandbyConfig(
                lines = listOf(
                    Line(listOf(i("date_full"))), Line(listOf(i("song")), empty = "No media"),
                    Line(listOf(i("network"), t(" · "), i("connection"))), Line(listOf(i("battery"), t("% "), i("battery_state"))),
                    Line(listOf(t("Profile: "), i("profile"))), Line(listOf(t("Next alarm: "), i("alarm"))),
                    Line(listOf(t("Up "), i("uptime"), t(" · load "), i("load"))),
                    Line(listOf(t("Memory "), i("ram"), t(" · storage "), i("storage"))),
                ),
                align = Align.CENTRE, battery = true,
            ),
        )
    }
}
