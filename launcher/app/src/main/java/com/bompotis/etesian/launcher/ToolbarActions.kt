package com.bompotis.etesian.launcher

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Runs the user's toolbar buttons. An action is JSON:
 * - `{"t":"app","c":"pkg/cls"}`, `{"t":"shortcut","p":pkg,"id":id}`, `{"t":"settings","a":action}`
 * - `{"t":"key","k":keycode}`, `{"t":"torch"}`, `{"t":"wifi"}`, `{"t":"bluetooth"}`, `{"t":"rotation"}`
 * - `{"t":"wait","ms":n}`, and `{"t":"macro","steps":[action…]}` running steps in order.
 */
object ToolbarActions {
    private const val TAG = "ToolbarActions"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Media and volume keys go to the audio system; others are injected as key presses. */
    val KEYS = listOf(
        "Back" to KeyEvent.KEYCODE_BACK, "Home" to KeyEvent.KEYCODE_HOME,
        "Recent apps" to KeyEvent.KEYCODE_APP_SWITCH,
        "Play / pause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        "Next track" to KeyEvent.KEYCODE_MEDIA_NEXT, "Previous track" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        "Volume up" to KeyEvent.KEYCODE_VOLUME_UP, "Volume down" to KeyEvent.KEYCODE_VOLUME_DOWN,
        "Mute" to KeyEvent.KEYCODE_VOLUME_MUTE, "Screenshot" to KeyEvent.KEYCODE_SYSRQ,
    )

    fun run(ctx: Context, action: JSONObject) {
        val app = ctx.applicationContext
        // Steps run in order on one worker; waits only hold up that macro.
        worker.execute { runCatching { step(app, action) }.onFailure { Log.w(TAG, "Action failed: $action", it) } }
    }

    private fun onMain(block: () -> Unit) {
        val done = java.util.concurrent.CountDownLatch(1)
        main.post { runCatching(block).onFailure { Log.w(TAG, "Step failed", it) }; done.countDown() }
        done.await()
    }

    private fun step(ctx: Context, a: JSONObject) {
        when (a.optString("t")) {
            "macro" -> {
                val steps = a.optJSONArray("steps") ?: JSONArray()
                for (i in 0 until steps.length()) step(ctx, steps.getJSONObject(i))
            }
            "wait" -> SystemClock.sleep(a.optLong("ms", 500).coerceIn(0, 10_000))
            "app" -> onMain {
                val cn = ComponentName.unflattenFromString(a.getString("c")) ?: return@onMain
                ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            "shortcut" -> onMain {
                ctx.getSystemService(LauncherApps::class.java)
                    .startShortcut(a.getString("p"), a.getString("id"), null, null, Process.myUserHandle())
            }
            "settings" -> onMain {
                ctx.startActivity(Intent(a.getString("a")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "key" -> key(ctx, a.getInt("k"))
            "torch" -> torch(ctx)
            "wifi" -> {
                val wm = ctx.getSystemService(WifiManager::class.java)
                @Suppress("DEPRECATION") wm.setWifiEnabled(!wm.isWifiEnabled)
            }
            "bluetooth" -> {
                val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
                @Suppress("DEPRECATION", "MissingPermission")
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
            }
            "rotation" -> {
                val cr = ctx.contentResolver
                val auto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (auto == 1) 0 else 1)
            }
        }
    }

    private fun key(ctx: Context, code: Int) {
        val media = code in setOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val audio = ctx.getSystemService(AudioManager::class.java)
        when {
            media -> {
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
            code == KeyEvent.KEYCODE_VOLUME_UP -> audio.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            code == KeyEvent.KEYCODE_VOLUME_DOWN -> audio.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            code == KeyEvent.KEYCODE_VOLUME_MUTE -> audio.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            // Back, Home, Recents, Screenshot: an injected key press (the launcher may inject).
            else -> android.app.Instrumentation().sendKeyDownUpSync(code)
        }
    }

    private var torchOn = false
    private var torchWatched = false

    private fun torch(ctx: Context) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        if (!torchWatched) {
            torchWatched = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) torchOn = enabled
                }
            }, main)
            SystemClock.sleep(150) // the callback reports the current state first
        }
        cm.setTorchMode(id, !torchOn)
    }

    /** One line describing an action, for the settings screens. */
    fun describe(ctx: Context, a: JSONObject): String = when (a.optString("t")) {
        "app" -> "Opens " + (runCatching {
            val cn = ComponentName.unflattenFromString(a.getString("c"))!!
            ctx.packageManager.getActivityInfo(cn, 0).loadLabel(ctx.packageManager)
        }.getOrNull() ?: "an app")
        "shortcut" -> "Shortcut in " + (runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(a.getString("p"), 0))
        }.getOrNull() ?: a.optString("p"))
        "settings" -> "Opens " + (ToolbarConfig.SETTINGS_PAGES.firstOrNull { it.second == a.optString("a") }?.first ?: "settings")
        "key" -> "Presses " + (KEYS.firstOrNull { it.second == a.optInt("k") }?.first ?: "a key")
        "torch" -> "Torch on/off"
        "wifi" -> "Wi-Fi on/off"
        "bluetooth" -> "Bluetooth on/off"
        "rotation" -> "Auto-rotate on/off"
        "wait" -> "Waits ${a.optLong("ms", 500)} ms"
        "macro" -> (a.optJSONArray("steps")?.length() ?: 0).let { "Macro, $it step" + if (it == 1) "" else "s" }
        else -> ""
    }
}

/** SystemUI asks the launcher to run a toolbar button (only SystemUI may: see the manifest). */
class ToolbarRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val json = intent.getStringExtra("action") ?: return
        runCatching { ToolbarActions.run(context, JSONObject(json)) }
    }
}
