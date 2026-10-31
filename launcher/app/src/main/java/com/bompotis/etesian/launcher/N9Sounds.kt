package com.bompotis.etesian.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import java.io.File

/**
 * The N9's own interface sounds that Android has no slot for (closing applications, entering edit mode), from the
 * user's imported N9 files. Silent when they weren't imported, when touch sounds are off, or
 * when the phone is on vibrate or silent.
 */
object N9Sounds {
    private const val DIR = "/product/media/audio/ui/n9/"
    const val CLOSE = "close.wav"
    const val CLOSE_MULTIPLE = "close_multiple.wav"
    /** Entering edit mode (the N9 home's edit-mode-enter-feedback, priority2_popup). */
    const val EDIT = "popup.wav"

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()

    fun play(ctx: Context, name: String) {
        if (Settings.System.getInt(ctx.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return
        val audio = ctx.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val file = File(DIR + name)
        if (!file.exists()) return
        val p = pool ?: SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build().also { pool = it }
        ids[name]?.let { p.play(it, 1f, 1f, 1, 0, 1f); return }
        p.setOnLoadCompleteListener { sp, id, status -> if (status == 0) sp.play(id, 1f, 1f, 1, 0, 1f) }
        ids[name] = p.load(file.path, 1)
    }
}
