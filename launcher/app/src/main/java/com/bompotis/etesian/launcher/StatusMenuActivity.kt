package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.roundToInt

/**
 * The N9 status menu, opened by tapping the status bar: a translucent black panel that drops
 * down from the top with the volume/profile bar and connection toggles, and a Settings shortcut.
 * It replaces Android's quick settings; notifications live in the Events view instead.
 */
class StatusMenuActivity : Activity() {

    private lateinit var panel: LinearLayout
    private val rows = mutableListOf<() -> Unit>()
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var torchOn = false
    private var torchId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; refresh() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0
        // N9: the menu leaves the way it came, up. Back must run close() (targetSdk 37 never calls
        // onBackPressed), and no system close animation may slide the window down after it.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (editing) endEdit() else close()
        }
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        // N9: the status menu drops over the dimmed screen with no toolbar under it (mock: n9-status-menu.png).
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = false, items = emptyList(), style = "dark", hidden = true) {}
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Black, or the user's black-screen wallpaper.
            background = Backgrounds.blackScreen(this@StatusMenuActivity)
            // Status bar height plus a little, as on the N9.
            val top = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))
            setPadding(mp(5f), top + mp(8f), mp(5f), mp(6f))
            isClickable = true
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(DIM)
            addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            // Tapping the dimmed area closes the menu.
            setOnClickListener { close() }
        }
        setContentView(root)
        build()
        torchId = runCatching {
            val cm = getSystemService(CameraManager::class.java)
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
        panel.translationY = -dp(400f).toFloat()
        panel.animate().translationY(0f).setDuration(220).start()
    }

    override fun onResume() {
        super.onResume()
        runCatching { getSystemService(CameraManager::class.java).registerTorchCallback(torchCallback, null) }
        refresh()
    }

    override fun onPause() {
        runCatching { getSystemService(CameraManager::class.java).unregisterTorchCallback(torchCallback) }
        super.onPause()
    }

    private fun close() {
        // N9: the shade lifts with the menu: the dim leaves upward too (its lower edge sweeps up), never down.
        val root = panel.parent as FrameLayout
        if (!editing) {
            root.background = null
            val dim = View(this).apply { setBackgroundColor(DIM) }
            root.addView(dim, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            dim.animate().translationY(-root.height.toFloat()).setDuration(180).start()
        }
        panel.animate().translationY(-panel.height.toFloat()).setDuration(180).withEndAction {
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }.start()
    }



    private fun refresh() = rows.forEach { it() }

    /** Mock px → dp: the mocks are drawn on a 360 px wide phone, this one is 491 dp wide (×1.36). */
    private fun mp(v: Float) = dp(v * MOCK)
    private fun ms(v: Float) = v * MOCK

    /** N9 graphics from the user's own N9 files when present (vendor/etesian/n9-files). */
    private fun n9Icon(name: String) = File("/product/media/etesian/icons/$name.png")
        .takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun build() {
        panel.removeAllViews(); rows.clear()
        // Edit mode: a solid page down to the edit bar, so nothing behind shows between the blocks and Done / Add item.
        (panel.parent as? FrameLayout)?.setBackgroundColor(if (editing) N9.BACKGROUND else DIM)
        panel.layoutParams?.height = if (editing) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (editing) panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, dp(64f) + bottomInset() + dp(16f))
        else panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, dp(6f))
        editRoot?.let { (it.parent as? ViewGroup)?.removeView(it) }; editRoot = null
        if (Extras.on(this, Extras.STATUS_MENU)) { buildCustom(); return }
        if (Extras.on(this, Extras.MUSIC)) panel.addView(SmallMusic(this))
        if (Extras.on(this, Extras.MODES)) {
            panel.addView(VolumeBar(this, showProfile = true, showVolume = false))
            panel.addView(modeStrip())
            panel.addView(VolumeBar(this, showProfile = false, showVolume = true))
        } else panel.addView(VolumeBar(this))
        // Mock 12: the privacy row is the first row, right under Volume.
        panel.addView(View(this), LinearLayout.LayoutParams(1, mp(16f)))
        if (Extras.on(this, Extras.PRIVACY)) privacyRow()?.let { panel.addView(it) }
        recordingRow()?.let { panel.addView(it) }
        ongoingRows().forEach { panel.addView(it) }
        listOf("wifi", "data", "bt", "flight", "torch", "rotate").forEach { addRow(it) }
        panel.addView(BrightnessBar(this))
        // No Settings button: the N9 status menu never had one (Settings is an app).
    }

    /** A status-menu connection toggle: its N9 row icon, strip glyph, title, state and action. */
    private class Toggle(val icon: String, val glyph: String, val title: String, val status: () -> String,
                         val isOn: () -> Boolean, val flip: () -> Unit, val longPress: (() -> Unit)? = null)

    private fun def(id: String): Toggle? = when (id) {
        "wifi" -> Toggle("icon-m-common-wlan", "wifi", "Internet connection", {
            val wifi = getSystemService(WifiManager::class.java)
            if (!wifi.isWifiEnabled) "Wi-Fi off"
            else wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" } ?: "Not connected"
        }, { getSystemService(WifiManager::class.java).isWifiEnabled }, {
            @Suppress("DEPRECATION") getSystemService(WifiManager::class.java).let { it.isWifiEnabled = !it.isWifiEnabled }
        }, longPress = { startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) })
        "data" -> Toggle("icon-m-common-cellular", "data", "Mobile data", { if (dataOn()) "On" else "Off" }, ::dataOn, {
            runCatching {
                getSystemService(TelephonyManager::class.java).javaClass
                    .getMethod("setDataEnabled", Boolean::class.java).invoke(getSystemService(TelephonyManager::class.java), !dataOn())
            }
        })
        "bt" -> Toggle("icon-m-bluetooth", "bt", "Bluetooth", { if (btOn()) "On" else "Off" }, ::btOn, {
            @Suppress("DEPRECATION", "MissingPermission")
            getSystemService(BluetoothManager::class.java).adapter?.let { if (it.isEnabled) it.disable() else it.enable() }
        }, longPress = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        "flight" -> Toggle("icon-m-status-menu-flight-mode", "flight", "Flight mode", { if (flight()) "On" else "Off" }, ::flight, {
            runCatching {
                val cm = getSystemService(android.net.ConnectivityManager::class.java)
                cm.javaClass.getMethod("setAirplaneMode", Boolean::class.java).invoke(cm, !flight())
            }
        })
        "torch" -> Toggle("icon-m-camera-torch-on", "torch", "Flashlight", { if (torchOn) "On" else "Off" }, { torchOn }, {
            torchId?.let { id -> runCatching { getSystemService(CameraManager::class.java).setTorchMode(id, !torchOn) } }
        })
        "rotate" -> Toggle("icon-m-image-edit-rotate-right", "rotate", "Auto-rotate", { if (rotate()) "On" else "Off" }, ::rotate, {
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotate()) 0 else 1)
        })
        // Screen recorder (mock 1a): SystemUI records the whole screen, no audio; tap again stops.
        "record" -> Toggle("icon-m-etesian-record", "record", "Screen recorder", {
            if (Recorder.on(this)) "Recording \u00b7 tap to stop" else "Off"
        }, { Recorder.on(this) }, {
            // Starting closes the menu, so the recording begins on the screen underneath.
            val starting = !Recorder.on(this); Recorder.toggle(this); if (starting) finish()
        })
        "location" -> Toggle("icon-m-common-location", "location", "Location", { if (locationOn()) "On" else "Off" }, ::locationOn, {
            runCatching {
                val lm = getSystemService(android.location.LocationManager::class.java)
                lm.javaClass.getMethod("setLocationEnabledForUser", Boolean::class.java, android.os.UserHandle::class.java)
                    .invoke(lm, !locationOn(), android.os.Process.myUserHandle())
            }
        }, longPress = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
        else -> null
    }

    private fun locationOn() = runCatching { getSystemService(android.location.LocationManager::class.java).isLocationEnabled }.getOrDefault(false)

    /** The N9 row for toggle [id]. */
    private fun addRow(id: String, into: ViewGroup? = null, longPress: (() -> Unit)? = null): View? {
        val t = def(id) ?: return null
        return toggle(t.icon, t.title, t.status, t.isOn, t.flip, longPress ?: t.longPress, into)
    }

    private fun dataOn() = runCatching { getSystemService(TelephonyManager::class.java).isDataEnabled }.getOrDefault(false)
    @SuppressLint("MissingPermission")
    private fun btOn() = getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
    private fun flight() = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    private fun rotate() = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun divider() = View(this).apply {
        setBackgroundColor(N9.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
            topMargin = dp(4f); bottomMargin = dp(4f)
        }
    }

    /** One N9-style row: icon, title and status line (state is the subtitle; icon dims when off). Tap toggles. */
    private fun toggle(icon: String, title: String, status: () -> String, isOn: () -> Boolean, flip: () -> Unit,
                       longPress: (() -> Unit)? = null, into: ViewGroup? = null): View {
        val bmp = n9Icon(icon)
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // The N9 icon PNGs carry their own rounded tile; only fallback icons get ours.
            if (bmp != null) setImageBitmap(bmp)
            else {
                background = IconFactory.tile(0xFF3A3A3A.toInt())
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            }
        }
        val name = TextView(this).apply { text = title; setTextColor(N9.TEXT); textSize = ms(18f); typeface = N9.FONT_BOLD; includeFontPadding = false }
        val sub = TextView(this).apply { setTextColor(0xFFA0A0A0.toInt()); textSize = ms(15f); typeface = N9.FONT; includeFontPadding = false; setPadding(0, mp(3f), 0, 0) }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            // Mock rows: 60 high, 46 px lit squircle, 14 px gap, 8 px in from the edge.
            minimumHeight = mp(60f)
            setPadding(mp(8f), 0, 0, 0)
            addView(iv, LinearLayout.LayoutParams(mp(46f), mp(46f)).apply { marginEnd = mp(14f) })
            addView(LinearLayout(this@StatusMenuActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(name); addView(sub)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { flip(); postDelayed({ refresh() }, 600) }
            longPress?.let { lp -> setOnLongClickListener { lp(); true } }
        }
        (into ?: panel).addView(row)
        rows += {
            sub.text = status()
            val on = isOn()
            iv.imageAlpha = if (on) 255 else 115
        }
        return row
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(N9.TEXT); textSize = ms(15f); typeface = N9.FONT
        includeFontPadding = false
        setPadding(0, mp(10f), 0, mp(4f))
    }

    /** N9 profile selector and volume: "Profile" (Silent | Beep | Ringing, where Ringing is the
     *  ringer volume slider) and "Volume" (media volume). */
    private inner class VolumeBar(ctx: Context, showProfile: Boolean = true, showVolume: Boolean = true) : LinearLayout(ctx) {
        private val profiles = ProfileStrip(ctx)
        private val bar = FillBar(ctx, 0xFFCC00CC.toInt(), {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }) { f ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
        }

        init {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(4f))
            if (showProfile) {
                addView(sectionLabel("Profile"))
                addView(profiles, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mp(17f)))
            }
            if (showVolume) {
                addView(sectionLabel("Volume"))
                addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mp(17f)))
            }
            rows += { refreshBar() }
        }

        fun refreshBar() {
            bar.invalidate()
            profiles.invalidate()
        }

        /** One continuous strip: Silent | Beep | Ringing. Ringing runs to the end of the strip and is the
         *  ringer volume slider: its fill reaches as far as the ringer level, the rest is grey. */
        @SuppressLint("ClickableViewAccessibility")
        private inner class ProfileStrip(ctx: Context) : View(ctx) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; textSize = mp(11f).toFloat(); typeface = N9.FONT_BOLD }
            private val names = arrayOf("Silent", "Beep", "Ringing")
            private val modes = intArrayOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_NORMAL)
            private val gap get() = mp(4f).toFloat()
            private var dragging = false

            private fun labelW(i: Int) = text.measureText(names[i]) + mp(6f)

            /** Silent and Beep are fixed segments (mock: 3px text inset, 30px tail, 74px when selected). */
            private fun fixedW(i: Int) = labelW(i) + mp(if (i == selected()) 71f else 27f)

            private val ringStart get() = fixedW(0) + fixedW(1) + gap * 2

            private fun selected() = modes.indexOf(audio.ringerMode).coerceAtLeast(0)

            private fun ringLevel() =
                audio.getStreamVolume(AudioManager.STREAM_RING).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_RING)

            /** Fill end of the Ringing slider: never narrower than its label. */
            private fun ringEnd(): Float {
                val x0 = ringStart
                return (x0 + (width - x0) * ringLevel()).coerceIn(x0 + labelW(2), width.toFloat())
            }

            private fun setRing(x: Float) {
                val x0 = ringStart
                val f = ((x - x0) / (width - x0).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                runCatching {
                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    audio.setStreamVolume(AudioManager.STREAM_RING, (f * max).roundToInt().coerceAtLeast(1), 0)
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragging = e.x >= ringStart
                        if (dragging) parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> if (dragging) { setRing(e.x); refreshBar() }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) setRing(e.x)
                        else runCatching { audio.ringerMode = modes[if (e.x < fixedW(0) + gap) 0 else 1] }
                        dragging = false
                        refreshBar()
                    }
                    MotionEvent.ACTION_CANCEL -> dragging = false
                }
                return true
            }

            override fun onDraw(canvas: Canvas) {
                val sel = selected()
                val h = height.toFloat()
                val ty = h / 2f - (text.descent() + text.ascent()) / 2
                var x = 0f
                for (i in 0 until 2) {
                    val w = fixedW(i)
                    paint.color = if (i == sel) SEL else UNSEL
                    canvas.drawRect(x, 0f, x + w, h, paint)
                    text.color = if (i == sel) 0xFFFFFFFF.toInt() else UNSEL_TEXT
                    canvas.drawText(names[i], x + mp(3f), ty, text)
                    x += w + gap
                }
                // Ringing is the ringer volume slider in both states (mock: dark blue fill when not selected).
                val end = ringEnd()
                paint.color = 0xFF3A3A3A.toInt()
                canvas.drawRect(end, 0f, width.toFloat(), h, paint)
                paint.color = if (sel == 2) SEL else UNSEL
                canvas.drawRect(x, 0f, end, h, paint)
                text.color = if (sel == 2) 0xFFFFFFFF.toInt() else UNSEL_TEXT
                canvas.drawText(names[2], x + mp(3f), ty, text)
            }
        }
    }

    private companion object {
        const val MOCK = 1.36f; const val DIM = 0xA6000000.toInt(); const val ADD_GREEN = 0xFF5CA834.toInt(); const val SEL = 0xFF4A9BFF.toInt(); const val UNSEL = 0xFF23436E.toInt(); const val UNSEL_TEXT = 0xFF8A9BB4.toInt()
    }

    /** Screen brightness: the same flat bar as Volume, N9 Settings blue (mock 1a). */
    private inner class BrightnessBar(ctx: Context) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(2f))
            addView(sectionLabel("Brightness"))
            addView(FillBar(ctx, N9.ACCENT, {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }) { f ->
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (f * 255).roundToInt().coerceIn(1, 255))
            }.apply { contentDescription = "Brightness" }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mp(17f)))
        }
    }

    /**
     * A continuous square-cornered N9 fill bar; drag or tap anywhere to set. With [slider] it is drawn
     * as the N9 Settings slider instead: a thin blue track with a big white squircle thumb.
     */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class FillBar(ctx: Context, private val fill: Int, private val level: () -> Float,
                                private val set: (Float) -> Unit) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var slider = false

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!isEnabled) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            val inset = if (slider) height / 2f else 0f
            set(((e.x - inset) / (width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
            invalidate()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            if (slider) { drawSlider(canvas); return }
            paint.color = 0xFF3A3A3A.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = if (isEnabled) fill else 0x55FFFFFF
            canvas.drawRect(0f, 0f, width * level().coerceIn(0f, 1f), height.toFloat(), paint)
        }

        private fun drawSlider(canvas: Canvas) {
            val r = height / 2f
            val track = dp(4f).toFloat()
            val cy = height / 2f
            val x = r + (width - 2 * r) * level().coerceIn(0f, 1f)
            paint.color = 0xFF3A3A3A.toInt()
            canvas.drawRect(r, cy - track / 2, width - r, cy + track / 2, paint)
            paint.color = if (isEnabled) fill else 0x55FFFFFF
            canvas.drawRect(r, cy - track / 2, x, cy + track / 2, paint)
            paint.color = N9.TEXT
            val t = r * 0.85f
            canvas.drawRoundRect(x - t, cy - t, x + t, cy + t, t * 0.45f, t * 0.45f, paint)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Android extras → "Status menu: toggle strip, sliders and hidden rows" (mocks 01–01g, 1d, 1e).

    private var editing = false
    private var expanded = false
    private var editRoot: View? = null
    private val rowBlocks = mutableListOf<View>()
    private var handle: View? = null

    private fun buildCustom() {
        val blocks = StatusLayout.blocks(this)
        val maxRows = StatusLayout.maxRows(this)
        val reveal = StatusLayout.reveal(this)
        rowBlocks.clear(); handle = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(list)
        var rowsSeen = 0
        blocks.forEach { id ->
            val v = blockView(id) ?: return@forEach
            if (id.startsWith("row:")) {
                rowsSeen++
                if (!editing && maxRows > 0 && rowsSeen > maxRows) { rowBlocks += v; v.visibility = if (expanded) View.VISIBLE else View.GONE }
            }
            list.addView(if (editing) editWrap(id, v, list) else v.also { w ->
                w.setOnLongClickListener { startEdit(); true }
            })
            if (id == StatusLayout.PROFILE && !editing && Extras.on(this, Extras.MODES)) list.addView(modeStrip())
            if (id == StatusLayout.VOLUME && !editing && Extras.on(this, Extras.PRIVACY)) privacyRow()?.let { list.addView(it) }
            if (id == StatusLayout.VOLUME && !editing) recordingRow()?.let { list.addView(it) }
            if (id == StatusLayout.VOLUME && !editing) ongoingRows().forEach { list.addView(it) }
        }
        if (!editing && rowBlocks.isNotEmpty() && reveal != "swipe") {
            handle = moreHandle().also { panel.addView(it) }
        }
        // Mocks 1 / 1a: the menu ends with the rows (or the handle); no divider, no Settings button.
        if (editing) {
            addSection()
            editBar()
        }
        refresh()
    }

    /** One block as shown in the menu (null: unknown or not available). */
    private fun blockView(id: String): View? = when {
        id == StatusLayout.PROFILE -> VolumeBar(this, showProfile = true, showVolume = false)
        id == StatusLayout.VOLUME -> VolumeBar(this, showProfile = false, showVolume = true)
        id == StatusLayout.STRIP -> strip()
        id == StatusLayout.MUSIC -> if (Extras.on(this, Extras.MUSIC)) SmallMusic(this) else null
        id == "brightness" -> SliderBlock("Brightness", N9.ACCENT, {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        }) { f ->
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (f * 255).roundToInt().coerceIn(1, 255))
        }
        id == "alarm" -> SliderBlock("Alarm volume", 0xFFF77219.toInt(), {
            audio.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        }) { f ->
            audio.setStreamVolume(AudioManager.STREAM_ALARM,
                (f * audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)).roundToInt().coerceAtLeast(1), 0)
        }
        id.startsWith("row:") -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (editing) {
                val t = def(id.removePrefix("row:")) ?: return null
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = mp(38f)
                    addView(LitSquircle(context, 0xFF3A3A3A.toInt(), t.glyph), LinearLayout.LayoutParams(mp(34f), mp(34f)).apply { marginEnd = mp(14f) })
                    addView(TextView(context).apply { text = t.title; setTextColor(N9.TEXT); textSize = ms(15f); typeface = N9.FONT_BOLD })
                })
            } else addRow(id.removePrefix("row:"), this, longPress = { startEdit() }) ?: return null
        }
        else -> null
    }

    /** A flat N9 slider block: label above, a filled bar, no knob; drag or tap anywhere. */
    private inner class SliderBlock(label: String, color: Int, level: () -> Float, set: (Float) -> Unit) : LinearLayout(this@StatusMenuActivity) {
        init {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(4f))
            addView(sectionLabel(label))
            val bar = FillBar(context, color, level, set).apply { contentDescription = label }
            addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mp(17f)))
            rows += { bar.invalidate() }
        }
    }

    /**
     * The toggle strip: lit squircle tiles, blue when on, evenly spaced edge to edge, wrapping
     * after six. Empty: nothing in normal mode, a dashed placeholder in edit mode.
     */
    private fun strip(): View {
        val ids = StatusLayout.toggles(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Toggles"
            // Mock: 12 px above the tiles; the rows below start 16 px further down.
            setPadding(0, mp(12f), 0, mp(18f))
        }
        if (ids.isEmpty()) {
            if (!editing) return box.apply { visibility = View.GONE }
            box.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                repeat(4) { addView(DashedSquircle(context), LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(8f) }) }
                addView(TextView(context).apply {
                    text = "No toggles — tap ✎ to add"; setTextColor(0xFFA0A0A0.toInt()); textSize = 14f; typeface = N9.FONT
                })
            })
            return box
        }
        val size = if (editing) ms(34f) else ms(44f)
        ids.chunked(6).forEach { chunk ->
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            chunk.forEachIndexed { i, id ->
                val t = def(id) ?: return@forEachIndexed
                if (i > 0) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                val tile = LitSquircle(this, 0xFF3A3A3A.toInt(), t.glyph).apply {
                    contentDescription = t.title
                    if (!editing) {
                        setOnClickListener { t.flip(); postDelayed({ refresh() }, 600) }
                        setOnLongClickListener { startEdit(); true }
                    }
                }
                line.addView(tile, LinearLayout.LayoutParams(dp(size), dp(size)))
                rows += {
                    val on = t.isOn()
                    tile.set(if (on) 0xFF1C6BC8.toInt() else 0xFF3A3A3A.toInt())
                    tile.stateDescription = if (on) "On" else "Off"
                }
            }
            // A short last line keeps the same spacing as a full one.
            if (chunk.size < 6 && ids.size > 6) repeat(6 - chunk.size) {
                line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                line.addView(View(this), LinearLayout.LayoutParams(dp(size), dp(size)))
            }
            box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (box.childCount > 0) topMargin = mp(10f)
            })
        }
        return box
    }

    private class DashedSquircle(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFF5A5A5E.toInt(); strokeWidth = ctx.dp(1.5f).toFloat()
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(ctx.dp(4f).toFloat(), ctx.dp(3.5f).toFloat()), 0f)
        }
        override fun onDraw(c: Canvas) {
            val s = minOf(width, height).toFloat() - p.strokeWidth
            c.translate(p.strokeWidth / 2, p.strokeWidth / 2)
            c.drawPath(IconFactory.superellipse(s, 2.8), p)
        }
    }

    /** The "more" handle under the list: a chevron between two lines, clearly not a row. */
    private fun moreHandle(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(mp(40f), 0, mp(40f), 0)
        minimumHeight = mp(22f)
        contentDescription = if (expanded) "Fewer rows" else "More rows"
        val chevron = ImageView(context).apply {
            setImageDrawable(Glyphs.drawable("chevron", 0xFFC8C8C8.toInt()))
            rotation = if (expanded) 180f else 0f
        }
        addView(View(context).apply { setBackgroundColor(0xFF4A4A4A.toInt()) }, LinearLayout.LayoutParams(0, dp(1f), 1f))
        addView(chevron, LinearLayout.LayoutParams(mp(16f), mp(16f)).apply { marginStart = mp(8f); marginEnd = mp(8f) })
        addView(View(context).apply { setBackgroundColor(0xFF4A4A4A.toInt()) }, LinearLayout.LayoutParams(0, dp(1f), 1f))
        setOnClickListener { setExpanded(!expanded) }
    }

    private fun setExpanded(on: Boolean) {
        if (expanded == on || rowBlocks.isEmpty()) return
        expanded = on
        rowBlocks.forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
        handle?.let { h ->
            h.contentDescription = if (on) "Fewer rows" else "More rows"
            ((h as ViewGroup).getChildAt(1) as ImageView).animate().rotation(if (on) 180f else 0f).setDuration(160).start()
        }
    }

    /** Swipe down on the menu reveals the hidden rows, up hides them (Reveal: Swipe down / Both). */
    private var swipeY = 0f
    private var swallow = false
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { swipeY = e.rawY; swallow = false }
        if (swallow) return true
        if (e.actionMasked == MotionEvent.ACTION_MOVE && !editing && rowBlocks.isNotEmpty() &&
            Extras.on(this, Extras.STATUS_MENU) && StatusLayout.reveal(this) != "button") {
            val dy = e.rawY - swipeY
            if ((dy > dp(80f) && !expanded) || (dy < -dp(80f) && expanded)) {
                setExpanded(!expanded); swallow = true
                super.dispatchTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
                return true
            }
        }
        return super.dispatchTouchEvent(e)
    }

    /**
     * Android extras → privacy indicators (mock 12): "Camera in use · <app> · tap to stop" at the
     * top of the menu while an app uses the camera or microphone; tapping stops that app.
     */
    private fun privacyRow(): View? {
        val ops = runCatching {
            val am = getSystemService(android.app.AppOpsManager::class.java)
            @Suppress("UNCHECKED_CAST")
            val list = am.javaClass.getMethod("getPackagesForOps", Array<String>::class.java)
                .invoke(am, arrayOf(android.app.AppOpsManager.OPSTR_CAMERA, android.app.AppOpsManager.OPSTR_RECORD_AUDIO)) as List<Any>?
            list.orEmpty().flatMap { po ->
                val pkg = po.javaClass.getMethod("getPackageName").invoke(po) as String
                (po.javaClass.getMethod("getOps").invoke(po) as List<Any>).filter { e ->
                    e.javaClass.getMethod("isRunning").invoke(e) as Boolean
                }.map { e -> pkg to (e.javaClass.getMethod("getOpStr").invoke(e) as String) }
            }
        }.getOrNull().orEmpty().filter { it.first != packageName }
        val (pkg, op) = ops.firstOrNull { it.second == android.app.AppOpsManager.OPSTR_CAMERA } ?: ops.firstOrNull() ?: return null
        val camera = op == android.app.AppOpsManager.OPSTR_CAMERA
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        return LinearLayout(this).apply {
            // The same size as the other rows (mock 12).
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = mp(60f)
            setPadding(mp(8f), 0, 0, 0)
            contentDescription = "Privacy"
            addView(LitSquircle(context, 0xFF2EAD3E.toInt(), if (camera) "camera" else "mic"),
                LinearLayout.LayoutParams(mp(46f), mp(46f)).apply { marginEnd = mp(14f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = if (camera) "Camera in use" else "Microphone in use"; setTextColor(N9.TEXT); textSize = ms(18f); typeface = N9.FONT_BOLD; includeFontPadding = false })
                addView(TextView(context).apply { text = "$app \u00b7 tap to stop"; setTextColor(0xFFA0A0A0.toInt()); textSize = ms(15f); typeface = N9.FONT; includeFontPadding = false; setPadding(0, mp(3f), 0, 0) })
            })
            setOnClickListener {
                runCatching {
                    val am = getSystemService(android.app.ActivityManager::class.java)
                    am.javaClass.getMethod("forceStopPackage", String::class.java).invoke(am, pkg)
                }
                visibility = View.GONE
            }
        }
    }

    /** Mock "Recording": while the screen is recorded, a red row "Recording the screen · mm:ss · tap to stop". */
    private fun recordingRow(): View? {
        val since = Recorder.since(this)
        if (since <= 0L) return null
        val s = ((System.currentTimeMillis() - since) / 1000).coerceAtLeast(0)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = mp(60f)
            setPadding(mp(8f), 0, 0, 0)
            contentDescription = "Recording the screen"
            addView(LitSquircle(context, 0xFFC7302A.toInt(), "record"),
                LinearLayout.LayoutParams(mp(46f), mp(46f)).apply { marginEnd = mp(14f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = "Recording the screen"; setTextColor(N9.TEXT); textSize = ms(18f); typeface = N9.FONT_BOLD; includeFontPadding = false })
                addView(TextView(context).apply { text = String.format("%02d:%02d \u00b7 tap to stop", s / 60, s % 60); setTextColor(0xFFA0A0A0.toInt()); textSize = ms(15f); typeface = N9.FONT; includeFontPadding = false; setPadding(0, mp(3f), 0, 0) })
            })
            setOnClickListener { Recorder.stop(context); visibility = View.GONE }
        }
    }

    /**
     * Unified ongoing statuses (approved "Recording" state): casting and screen sharing get the same
     * row as a recording — a lit squircle, what is happening, "<who> · tap to stop" — and the same
     * red status-bar dot (SystemUI). Tapping stops it.
     */
    private fun ongoingRows(): List<View> = Ongoing.current(this).map { o ->
        statusRow(o.color, o.glyph, o.title, "${o.detail} \u00b7 tap to stop") { Ongoing.stop(this, o) }
    }

    /** The one row form of the ongoing statuses. */
    private fun statusRow(color: Int, glyph: String, title: String, line: String, onTap: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = mp(60f)
        setPadding(mp(8f), 0, 0, 0)
        contentDescription = title
        addView(LitSquircle(context, color, glyph), LinearLayout.LayoutParams(mp(46f), mp(46f)).apply { marginEnd = mp(14f) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title; setTextColor(N9.TEXT); textSize = ms(18f); typeface = N9.FONT_BOLD; includeFontPadding = false })
            addView(TextView(context).apply { text = line; setTextColor(0xFFA0A0A0.toInt()); textSize = ms(15f); typeface = N9.FONT; includeFontPadding = false; setPadding(0, mp(3f), 0, 0) })
        })
        setOnClickListener { onTap(); visibility = View.GONE }
    }

    /** Android extras → Modes: the Mode strip; long-press a mode opens its page. */
    private fun modeStrip(): View = ModeStrip(this) { m ->
        startActivity(Intent(this, ModeSettingsActivity::class.java).putExtra(ModeSettingsActivity.EXTRA_ID, m.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }.also { s -> rows += { s.refresh() } }


    // ----- edit mode: drag handle left, ✕ (or ✎ on the strip) right; Done / Add item -----

    private fun startEdit() {
        if (editing) return
        panel.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        editing = true; expanded = false
        build()
    }

    private fun endEdit() { editing = false; build() }

    private fun editWrap(id: String, content: View, list: LinearLayout): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        tag = id
        setPadding(0, dp(2f), 0, dp(2f))
        val grip = ImageView(context).apply {
            setImageDrawable(Glyphs.drawable("drag", 0xFFC8C8C8.toInt()))
            contentDescription = "Drag ${StatusLayout.title(id)}"
        }
        addView(grip, LinearLayout.LayoutParams(mp(20f), mp(20f)).apply { marginEnd = mp(10f) })
        addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mark = if (id == StatusLayout.STRIP) LitSquircle.pencil(context).apply {
            contentDescription = "Edit toggles"; setOnClickListener { togglesEditor() }
        } else if (id == StatusLayout.MUSIC) View(context) // the Music extra owns it: moved, never removed here
        else LitSquircle.remove(context).apply {
            contentDescription = "Remove ${StatusLayout.title(id)}"
            setOnClickListener { StatusLayout.setBlocks(context, StatusLayout.blocks(context) - id); build() }
        }
        addView(mark, LinearLayout.LayoutParams(mp(26f), mp(26f)).apply { marginStart = mp(8f) })
        dragBy(grip, this, list) { from, to ->
            // Move by id: blocks not shown (e.g. Music with its extra off) keep their place in the saved list.
            val shown = (0 until list.childCount).mapNotNull { list.getChildAt(it).tag as? String }
            val target = shown.getOrNull(to) ?: return@dragBy
            val b = StatusLayout.blocks(context).toMutableList()
            b.remove(id); b.add(b.indexOf(target).let { if (to > from) it + 1 else it }, id)
            StatusLayout.setBlocks(context, b); build()
        }
    }

    /** Dragging [grip] moves [row] inside [list]; [moved] (from, to) on release. */
    @SuppressLint("ClickableViewAccessibility")
    private fun dragBy(grip: View, row: View, list: LinearLayout, moved: (Int, Int) -> Unit) {
        var y0 = 0f
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.rawY; row.elevation = dp(6f).toFloat(); row.alpha = 0.85f; list.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> row.translationY = e.rawY - y0
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val from = list.indexOfChild(row)
                    val mid = row.top + row.translationY + row.height / 2f
                    var to = 0
                    for (i in 0 until list.childCount) {
                        val c = list.getChildAt(i)
                        if (c !== row && mid > c.top + c.height / 2f) to++
                    }
                    row.translationY = 0f; row.elevation = 0f; row.alpha = 1f
                    if (e.actionMasked == MotionEvent.ACTION_UP && to != from) moved(from, to.coerceIn(0, list.childCount - 1))
                }
            }
            true
        }
    }

    /** The black edit bar at the bottom: Done and Add item, identical N9 buttons. */
    private fun editBar(left: String = "Done", right: String = "Add item", onLeft: () -> Unit = { endEdit() },
                        onRight: () -> Unit = { addItem() }) {
        val root = panel.parent as FrameLayout
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(0, dp(10f), 0, dp(10f))
            isClickable = true
            val lp = { LinearLayout.LayoutParams(dp(N9.EDIT_BUTTON_W), dp(N9.EDIT_BUTTON_H)).apply { marginStart = dp(4f); marginEnd = dp(4f) } }
            addView(N9.editButton(context, left, onLeft), lp())
            addView(N9.editButton(context, right, onRight), lp())
        }
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = bottomInset()
        })
        editRoot = bar
    }

    /** Height of a navigation bar covering the bottom (the N9 toolbar is hidden on this page). */
    private fun bottomInset(): Int {
        val ins = window.decorView.rootWindowInsets?.getInsets(
            android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0  // not tappableElement: the hidden toolbar still reports one
        return ins  // the N9 toolbar is hidden here, so only a real navigation bar covers the bottom
    }

    /** The sliders and rows not in the menu yet, with their kind. */
    /** Sliders go right under Volume (mock 1a); rows at the end. */
    private fun withAdded(id: String): List<String> {
        val b = StatusLayout.blocks(this).toMutableList()
        if (id in StatusLayout.SLIDERS || id == StatusLayout.PROFILE || id == StatusLayout.VOLUME) {
            var at = b.indexOfLast { it == StatusLayout.VOLUME || it == StatusLayout.PROFILE || it in StatusLayout.SLIDERS } + 1
            if (id == StatusLayout.PROFILE) at = 0
            b.add(at, id)
        } else b += id
        return b
    }

    private fun available(): List<Pair<String, String>> {
        val have = StatusLayout.blocks(this)
        val avail = mutableListOf<Pair<String, String>>()
        listOf(StatusLayout.PROFILE, StatusLayout.VOLUME).filter { it !in have }.forEach { avail += it to "Slider" }
        StatusLayout.SLIDERS.filter { it !in have }.forEach { avail += it to "Slider" }
        StatusLayout.TOGGLES.map { "row:$it" }.filter { it !in have }.forEach { avail += it to "Row" }
        return avail
    }

    /** Edit mode (mock 1c): "Add" under the blocks, each available item with a green + squircle. */
    private fun addSection() {
        val avail = available()
        if (avail.isEmpty()) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; contentDescription = "Add list" }
        box.addView(sectionLabel("Add"))
        avail.forEach { (id, kind) ->
            box.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = mp(25f)
                setPadding(mp(14f), mp(5f), 0, mp(5f))
                contentDescription = "Add ${StatusLayout.title(id)}"
                addView(LitSquircle(context, ADD_GREEN, "add", glyphScale = 0.7f), LinearLayout.LayoutParams(mp(20f), mp(20f)).apply { marginEnd = mp(10f) })
                addView(TextView(context).apply { text = StatusLayout.title(id); setTextColor(N9.TEXT); textSize = ms(15f); typeface = N9.FONT_BOLD })
                addView(TextView(context).apply { text = kind.lowercase(); setTextColor(0xFFA0A0A0.toInt()); textSize = ms(13f); typeface = N9.FONT; setPadding(mp(10f), 0, 0, 0) })
                setOnClickListener { StatusLayout.setBlocks(this@StatusMenuActivity, withAdded(id)); build() }
            })
        }
        panel.addView(android.widget.ScrollView(this).apply { addView(box) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Add item: the sliders, rows (and the Mode strip) not in the menu yet. */
    private fun addItem() {
        val have = StatusLayout.blocks(this)
        val avail = available()
        if (avail.isEmpty()) { android.widget.Toast.makeText(this, "Everything is in the menu", android.widget.Toast.LENGTH_SHORT).show(); return }
        N9Sheet.show(this, avail.map { (id, kind) ->
            N9Sheet.Item(StatusLayout.title(id), kind) { StatusLayout.setBlocks(this@StatusMenuActivity, withAdded(id)); build() }
        }, title = "Add item", label = "Add item")
    }

    /** The toggles editor (mock 1e): reorder, remove, add; Done / Reset. */
    private fun togglesEditor() {
        val root = panel.parent as FrameLayout
        val page = FrameLayout(this).apply { setBackgroundColor(N9.BACKGROUND); isClickable = true; contentDescription = "Toggles editor" }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply { addView(col) }
        page.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            bottomMargin = dp(64f) + bottomInset()
        })
        val top = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))
        col.setPadding(dp(16f), top + dp(16f), dp(16f), dp(16f))
        fun fill() {
            col.removeAllViews()
            col.addView(TextView(this).apply { text = "Toggles"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT; setPadding(0, 0, 0, dp(12f)) })
            val on = StatusLayout.toggles(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(list)
            on.forEach { id ->
                val t = def(id) ?: return@forEach
                list.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60f)
                    val grip = ImageView(context).apply { setImageDrawable(Glyphs.drawable("drag", 0xFFC8C8C8.toInt())); contentDescription = "Drag ${t.title}" }
                    addView(grip, LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { marginEnd = dp(10f) })
                    addView(LitSquircle(context, 0xFF3A3A3A.toInt(), t.glyph), LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginEnd = dp(14f) })
                    addView(TextView(context).apply { text = t.title; setTextColor(N9.TEXT); textSize = 18f; typeface = N9.FONT_BOLD },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(LitSquircle.remove(context).apply {
                        contentDescription = "Remove ${t.title}"
                        setOnClickListener { StatusLayout.setToggles(context, StatusLayout.toggles(context) - id); fill() }
                    }, LinearLayout.LayoutParams(dp(26f), dp(26f)))
                    dragBy(grip, this, list) { from, to ->
                        val l = StatusLayout.toggles(context).toMutableList(); l.add(to, l.removeAt(from))
                        StatusLayout.setToggles(context, l); fill()
                    }
                })
            }
            val rest = StatusLayout.TOGGLES.filter { it !in on }
            if (rest.isNotEmpty()) col.addView(TextView(this).apply {
                text = "Available"; setTextColor(0xFFA0A0A0.toInt()); textSize = 15f; typeface = N9.FONT_BOLD; setPadding(0, dp(16f), 0, dp(4f))
            })
            rest.forEach { id ->
                val t = def(id) ?: return@forEach
                col.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60f)
                    contentDescription = "Add ${t.title}"
                    addView(LitSquircle(context, ADD_GREEN, "add", glyphScale = 0.7f), LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginStart = dp(3f); marginEnd = dp(13f) })
                    addView(LitSquircle(context, 0xFF3A3A3A.toInt(), t.glyph), LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginEnd = dp(14f) })
                    addView(TextView(context).apply { text = t.title; setTextColor(N9.TEXT); textSize = 18f; typeface = N9.FONT_BOLD })
                    setOnClickListener { StatusLayout.setToggles(context, StatusLayout.toggles(context) + id); fill() }
                })
            }
        }
        fill()
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setBackgroundColor(0xFF000000.toInt()); setPadding(0, dp(10f), 0, dp(10f))
            val lp = { LinearLayout.LayoutParams(dp(N9.EDIT_BUTTON_W), dp(N9.EDIT_BUTTON_H)).apply { marginStart = dp(4f); marginEnd = dp(4f) } }
            addView(N9.editButton(context, "Done") { root.removeView(page); build() }, lp())
            addView(N9.editButton(context, "Reset") { StatusLayout.setToggles(context, StatusLayout.DEFAULT_TOGGLES); fill() }, lp())
        }
        page.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = bottomInset()
        })
    }

    // ----- Android extras → Music in the status menu: the small Belle music widget (mock 07) -----

    private inner class SmallMusic(ctx: Context) : FrameLayout(ctx) {
        private val sessions get() = runCatching {
            getSystemService(android.media.session.MediaSessionManager::class.java)
                .getActiveSessions(android.content.ComponentName(context, EventsService::class.java))
        }.getOrNull().orEmpty()
        private val controller get() = sessions.let { l ->
            l.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
        }
        private val art = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "Album art" }
        private val title = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        private val output = TextView(ctx).apply {
            setTextColor(0xFF4A9FF0.toInt()); textSize = 14f; typeface = N9.FONT; contentDescription = "Play on"
            setPadding(0, dp(2f), 0, dp(2f))
            setOnClickListener { playOn() }
        }
        private val prev = LitSquircle(ctx, 0xFF3A3A3A.toInt(), "prev").apply { contentDescription = "Previous" }
        private val play = LitSquircle(ctx, 0xFF3A3A3A.toInt(), "play").apply { contentDescription = "Play" }
        private val next = LitSquircle(ctx, 0xFF3A3A3A.toInt(), "next").apply { contentDescription = "Next" }
        private val callback = object : android.media.session.MediaController.Callback() {
            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = update()
            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = update()
        }
        private var watched: android.media.session.MediaController? = null

        init {
            contentDescription = "Now playing"
            setPadding(0, dp(4f), 0, dp(10f))
            // The glossy panel, lower than the art which rises above it.
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFF4A4A4E.toInt(), 0xFF2A2A2D.toInt(), 0xFF1A1A1C.toInt())).apply {
                    cornerRadius = dp(6f).toFloat(); setStroke(dp(1f), 0xFF5A5A5E.toInt())
                }
                setPadding(dp(104f), dp(8f), dp(10f), dp(8f))
                addView(title); addView(output)
                addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER
                    // the recessed groove holding the three buttons
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFF0E0E10.toInt(), 0xFF1E1E21.toInt())).apply {
                        cornerRadius = dp(6f).toFloat(); setStroke(dp(1f), 0xFF0A0A0A.toInt())
                    }
                    setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
                    listOf(prev, play, next).forEachIndexed { i, b ->
                        if (i > 0) addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                        addView(b, LinearLayout.LayoutParams(dp(40f), dp(40f)))
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
            }
            addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
            addView(art, LayoutParams(dp(92f), dp(92f)).apply { leftMargin = dp(4f) })
            prev.setOnClickListener { controller?.transportControls?.skipToPrevious() }
            next.setOnClickListener { controller?.transportControls?.skipToNext() }
            play.setOnClickListener {
                val c = controller
                if (c == null) openLibrary()
                else if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            }
            title.setOnClickListener { controller?.sessionActivity?.let { runCatching { it.send() }; finish() } ?: openLibrary() }
            rows += { update() }
        }

        private fun openLibrary() {
            runCatching { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            finish()
        }

        fun update() {
            val c = controller
            if (c !== watched) { watched?.unregisterCallback(callback); watched = c; c?.registerCallback(callback) }
            val meta = c?.metadata
            val t = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            val a = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            val bmp = meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            val st = c?.playbackState?.state ?: android.media.session.PlaybackState.STATE_NONE
            val idle = st == android.media.session.PlaybackState.STATE_NONE || st == android.media.session.PlaybackState.STATE_STOPPED ||
                st == android.media.session.PlaybackState.STATE_ERROR
            if (c == null || t == null || idle) {
                // Nothing playing: the widget only shows in edit mode, so it can still be moved.
                visibility = if (editing) View.VISIBLE else View.GONE
                title.text = "Go to Music library"
                output.visibility = View.GONE
                art.setImageDrawable(null)
                art.background = null
                art.setImageDrawable(LitSquircleDrawable(0xFFCC09BA.toInt(), "music"))
                play.set(0xFF3A3A3A.toInt(), "play"); play.alpha = 0.4f; prev.alpha = 0.4f; next.alpha = 0.4f
                return
            }
            visibility = View.VISIBLE
            title.text = listOfNotNull(t, a?.takeIf { it.isNotEmpty() }).joinToString(" · ")
            // album art is content: shown whole and square, never shaped
            if (bmp != null) art.setImageBitmap(bmp) else art.setImageDrawable(LitSquircleDrawable(0xFFCC09BA.toInt(), "music"))
            output.visibility = View.VISIBLE
            output.text = "${outputName()} ›"
            val playing = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            play.set(0xFF3A3A3A.toInt(), if (playing) "pause" else "play"); play.contentDescription = if (playing) "Pause" else "Play"
            play.alpha = 1f; prev.alpha = 1f; next.alpha = 1f
        }
    }

    /** A lit squircle as a drawable, for the music widget's missing art. */
    private inner class LitSquircleDrawable(private val color: Int, private val glyph: String) : android.graphics.drawable.Drawable() {
        override fun draw(c: Canvas) {
            val v = LitSquircle(this@StatusMenuActivity, color, glyph)
            v.layout(0, 0, bounds.width(), bounds.height())
            c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); v.draw(c); c.restore()
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Where media plays now: Bluetooth / wired headset, else this phone. */
    private fun outputs(): List<android.media.AudioDeviceInfo> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type in setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

    private fun outputName(): String = outputs().firstOrNull()?.let { d ->
        if (d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
            d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth" else "Headset"
    } ?: "This phone"

    /**
     * The N9 "Play on" selection dialog (mock 07b): light title, plain rows, the current output
     * highlighted, over a ~96% backdrop. Choosing another output hands over to Android's output
     * switcher (moving another app's media needs MEDIA_CONTENT_CONTROL).
     */
    private fun playOn() {
        val current = outputName()
        val names = (listOf("This phone") + outputs().map { d ->
            if (d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth" else "Headset"
        }).distinct()
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xF5000000.toInt()); setPadding(dp(24f), 0, dp(24f), 0)
            contentDescription = "Play on"
            setOnClickListener { d.dismiss() }
            addView(TextView(context).apply {
                text = "Play on"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT; gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16f))
            })
            names.forEach { n ->
                addView(TextView(context).apply {
                    text = n; textSize = 20f; typeface = N9.FONT_BOLD; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16f), 0, dp(16f), 0); minHeight = dp(64f)
                    val on = n == current
                    setTextColor(N9.TEXT)
                    if (on) background = GradientDrawable().apply { setColor(0xFF1C6BC8.toInt()); cornerRadius = dp(4f).toFloat() }
                    isSelected = on
                    setOnClickListener {
                        d.dismiss()
                        if (!on) runCatching {
                            sendBroadcast(Intent("com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG").setPackage("com.android.systemui"))
                        }
                    }
                })
            }
        }
        d.setContentView(col)
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        d.show()
    }
}
