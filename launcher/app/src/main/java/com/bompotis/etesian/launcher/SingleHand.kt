package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.Settings

/**
 * Single hand mode: the whole screen at half its size, within reach of the thumb (SystemUI's EtesianSingleHand
 * draws it; this only turns it on and off). Tap the empty space to move it, double tap there for full screen.
 * From a toolbar or Breeze button, a Breeze gesture, or the app shortcut.
 */
object SingleHand {
    const val SETTING = "etesian_single_hand"

    fun isOn(ctx: Context) = Settings.Secure.getInt(ctx.contentResolver, SETTING, 0) == 1

    fun toggle(ctx: Context) {
        Settings.Secure.putInt(ctx.contentResolver, SETTING, if (isOn(ctx)) 0 else 1)
    }
}

/** The app shortcut ("Single hand mode" on Etesian settings' icon): toggles it, shows nothing. */
class SingleHandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SingleHand.toggle(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
