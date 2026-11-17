package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Android extras → Share network (story Phone v2, state wifi-qr): a dim sheet over the screen with
 * "Share network", the QR code of the connected Wi-Fi network, "SSID · password" and Close.
 * Scanning is Android's own Wi-Fi QR scanner (Settings' Wi-Fi Easy Connect enrollee).
 */
class WifiShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val net = WifiQr.current(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xE6000000.toInt())
            setOnClickListener { finish() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            contentDescription = "Share network"
        }
        col.addView(TextView(this).apply {
            text = "Share network"; setTextColor(N9.TEXT); textSize = 32f; typeface = N9.FONT_LIGHT
            gravity = Gravity.CENTER
        })
        if (net == null) {
            col.addView(TextView(this).apply {
                text = if (wifiOn()) "Android hides the network's name while location is off.\nTurn on location to share it."
                    else "Not connected to a Wi-Fi network"
                gravity = Gravity.CENTER; setTextColor(0xFFC8C8C8.toInt()); textSize = 18f; typeface = N9.FONT_LIGHT
                setPadding(0, dp(24f), 0, dp(24f))
            })
        } else {
            col.addView(ImageView(this).apply {
                setImageBitmap(WifiQr.bitmap(net.payload, dp(204f)))
                contentDescription = "QR code"
            }, LinearLayout.LayoutParams(dp(204f), dp(204f)).apply { topMargin = dp(24f); bottomMargin = dp(20f) })
            col.addView(TextView(this).apply {
                text = listOf(net.ssid, net.password).filter { it.isNotEmpty() }.joinToString(" \u00b7 ")
                setTextColor(0xFFC8C8C8.toInt()); textSize = 18f; typeface = N9.FONT_LIGHT; gravity = Gravity.CENTER
                contentDescription = "Network and password"
            })
        }
        if (net == null && wifiOn()) col.addView(N9.editButton(this, "Location settings") {
            runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }; finish()
        }.apply { contentDescription = "Location settings" }, LinearLayout.LayoutParams(dp(232f), dp(44f)))
        col.addView(N9.editButton(this, "Close") { finish() }.apply { contentDescription = "Close" },
            LinearLayout.LayoutParams(dp(232f), dp(44f)).apply { topMargin = dp(28f) })
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)
    }
}

/** Connected to Wi-Fi (the transport is known without location). */
private fun Activity.wifiOn(): Boolean = runCatching {
    val cm = getSystemService(android.net.ConnectivityManager::class.java)
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
}.getOrDefault(false)

object WifiQr {
    class Net(val ssid: String, val password: String, val payload: String)

    /** The connected network, its saved password and the standard WIFI: QR payload; null when not connected. */
    fun current(ctx: Context): Net? {
        val wm = ctx.getSystemService(WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION") val info = wm.connectionInfo ?: return null
        val configs = runCatching {
            wm.javaClass.getMethod("getPrivilegedConfiguredNetworks").invoke(wm) as List<*>
        }.onFailure { android.util.Log.w("EtesianWifiQr", "configs", it) }.getOrNull()?.filterIsInstance<android.net.wifi.WifiConfiguration>()
            .orEmpty()
        // With location off Android hides the name ("<unknown ssid>") even from us: the saved network marked current
        // still says which one it is.
        @Suppress("DEPRECATION") val shown = info.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        // The connected network with its password, whatever location says (READ_WIFI_CREDENTIAL, NEARBY_WIFI_DEVICES).
        val connected = runCatching { wm.javaClass.getMethod("getPrivilegedConnectedNetwork").invoke(wm) }
            .onFailure { android.util.Log.w("EtesianWifiQr", "connected", it) }.getOrNull() as? android.net.wifi.WifiConfiguration
        @Suppress("DEPRECATION") val conf = connected
            ?: configs.firstOrNull { it.networkId == info.networkId && info.networkId != -1 }
            ?: shown?.let { s -> configs.firstOrNull { it.SSID?.trim('"') == s } }
            ?: configs.firstOrNull { it.status == android.net.wifi.WifiConfiguration.Status.CURRENT }
        val ssid = shown ?: conf?.SSID?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        @Suppress("DEPRECATION") val psk = conf?.preSharedKey?.takeIf { it.startsWith("\"") }?.trim('"').orEmpty()
        @Suppress("DEPRECATION") val type = when {
            conf == null -> if (psk.isEmpty()) "nopass" else "WPA"
            conf.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.SAE) -> "SAE"
            psk.isNotEmpty() -> "WPA"
            conf.wepKeys?.firstOrNull() != null -> "WEP"
            else -> "nopass"
        }
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        val payload = "WIFI:T:$type;S:${esc(ssid)};" + (if (psk.isNotEmpty()) "P:${esc(psk)};" else "") + ";"
        return Net(ssid, psk, payload)
    }

    fun bitmap(payload: String, px: Int): Bitmap {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, px, px,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        for (y in 0 until px) for (x in 0 until px) b.setPixel(x, y, if (m[x, y]) 0xFF111111.toInt() else Color.WHITE)
        return b
    }

    fun share(ctx: Context) = ctx.startActivity(Intent(ctx, WifiShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    /** Android's Wi-Fi QR scanner (Settings, Wi-Fi Easy Connect). */
    fun scan(ctx: Context) = runCatching {
        ctx.startActivity(Intent("android.settings.WIFI_DPP_ENROLLEE_QR_CODE_SCANNER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
