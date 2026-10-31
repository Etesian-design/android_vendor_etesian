package com.bompotis.etesian.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Current weather for the Events header, from Open-Meteo (free, no account or key).
 * Uses the last known coarse location; refreshes at most every 30 minutes.
 */
object Weather {
    class Now(val temperature: Int, val unit: String, val description: String, val place: String?,
              val code: Int = -1, val day: Boolean = true, val days: List<Day> = emptyList())

    /** One day's forecast: today first. */
    class Day(val time: Long, val high: Int, val low: Int, val code: Int)

    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val LAST_FIX = "weather_last_fix"
    @Volatile var current: Now? = null
        private set
    private var fetchedAt = 0L
    private var fetching = false
    private var lastKey = ""

    fun refresh(ctx: Context, done: () -> Unit) {
        if (!Prefs.weatherEnabled(ctx)) { current = null; done(); return }
        val fahrenheit = Prefs.weatherFahrenheit(ctx)
        val city = Prefs.weatherCity(ctx)
        // A changed city or unit makes the cached reading stale.
        val key = "$city/$fahrenheit"
        if (key != lastKey) { lastKey = key; fetchedAt = 0 }
        current?.let { publish(ctx, it) }
        if (fetching || System.currentTimeMillis() - fetchedAt < MAX_AGE_MS && current != null) return
        val app = ctx.applicationContext
        fun load(lat: Double, lon: Double, place: String?) {
            fetching = true
            // Remembered, so a restart without a fix yet (GPS only, indoors) still shows the weather.
            if (place == null) Prefs.of(app).edit().putString(LAST_FIX, "$lat,$lon").apply()
            io.execute {
                val now = runCatching { fetch(app, lat, lon, place, fahrenheit) }.getOrNull()
                main.post {
                    fetching = false
                    if (now != null) { current = now; fetchedAt = System.currentTimeMillis(); publish(app, now); done() }
                }
            }
        }
        if (city.isNotBlank()) {
            // A city typed in settings: look it up once with Open-Meteo's geocoding.
            fetching = true
            io.execute {
                val hit = runCatching { geocode(city) }.getOrNull()
                main.post {
                    fetching = false
                    if (hit != null) load(hit.first, hit.second, hit.third)
                }
            }
            return
        }
        lastLocation(ctx)?.let { load(it.latitude, it.longitude, null); return }
        Prefs.of(ctx).getString(LAST_FIX, null)?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }
            ?.let { load(it[0], it[1], null) }
        // No fix yet (LineageOS without microG has only GPS): ask for one.
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { lm.allProviders.contains(it) } ?: return
        runCatching {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc -> loc?.let { load(it.latitude, it.longitude, null) } }
        }
    }

    private fun geocode(city: String): Triple<Double, Double, String> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + java.net.URLEncoder.encode(city, "UTF-8")
        val body = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
            .inputStream.bufferedReader().use { it.readText() }
        val r = JSONObject(body).getJSONArray("results").getJSONObject(0)
        return Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
    }

    private fun lastLocation(ctx: Context): Location? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fetch(ctx: Context, lat: Double, lon: Double, knownPlace: String?, fahrenheit: Boolean): Now {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto%s"
            .format(java.util.Locale.ROOT, lat, lon, if (fahrenheit) "&temperature_unit=fahrenheit" else "")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val cur = json.getJSONObject("current")
        val days = runCatching {
            val d = json.getJSONObject("daily")
            val t = d.getJSONArray("time"); val hi = d.getJSONArray("temperature_2m_max")
            val lo = d.getJSONArray("temperature_2m_min"); val code = d.getJSONArray("weather_code")
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            List(t.length()) { i -> Day(fmt.parse(t.getString(i))!!.time, hi.getDouble(i).roundToInt(), lo.getDouble(i).roundToInt(), code.getInt(i)) }
        }.getOrDefault(emptyList())
        val place = knownPlace ?: runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(ctx).getFromLocation(lat, lon, 1)?.firstOrNull()?.locality
        }.getOrNull()
        return Now(cur.getDouble("temperature_2m").roundToInt(), if (fahrenheit) "°F" else "°C",
            describe(cur.getInt("weather_code")), place, cur.getInt("weather_code"),
            cur.optInt("is_day", 1) == 1, days)
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    /** For the optional lock screen weather line (SystemUI KeyguardSliceViewSection): "code|day|temp|unit|place". */
    const val SETTING_NOW = "etesian_weather_now"
    const val SETTING_LOCK = "etesian_lock_weather"

    private fun publish(ctx: Context, now: Now) {
        val v = "${now.code}|${if (now.day) 1 else 0}|${now.temperature}|${now.unit}|${now.place.orEmpty()}"
        runCatching {
            val cr = ctx.contentResolver
            if (android.provider.Settings.Secure.getString(cr, SETTING_NOW) != v) android.provider.Settings.Secure.putString(cr, SETTING_NOW, v)
        }
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> ""
    }
}
