package com.bompotis.etesian.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Adds a widget to Breeze's menu: the widget picker page, then Android's permission and the widget's own setup
 * when it has one. Its own widget host, apart from the Widgets view's, so the two never share ids.
 */
class BreezeWidgetActivity : Activity() {
    companion object {
        private const val HOST_ID = 0x4E3B
        private const val REQ_BIND = 1
        private const val REQ_CONFIGURE = 2

        fun host(ctx: Context) = AppWidgetHost(ctx.applicationContext, HOST_ID)
    }

    private val awm by lazy { AppWidgetManager.getInstance(this) }
    private val host by lazy { host(this) }
    private var pending = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = savedInstanceState?.getInt("pending", -1) ?: -1
        // A page of Etesian settings: the picker in this window, so the bottom bar (dark, Back) is this page's.
        window.setDecorFitsSystemWindows(false)
        setContentView(WidgetPickerView(this) { bind(it) })
    }

    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = true, items = emptyList(), style = "dark") {}
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("pending", pending)
    }

    private fun bind(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrAdd(id)
        else @Suppress("DEPRECATION") startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND)
    }

    private fun configureOrAdd(id: Int) {
        if (awm.getAppWidgetInfo(id)?.configure != null) {
            runCatching { host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null) }
                .onFailure { commit(id) }
        } else commit(id)
    }

    private fun commit(id: Int) {
        pending = -1
        Prefs.setBreezeWidgets(this, Prefs.breezeWidgets(this) + id)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val id = pending
        if (id == -1) { finish(); return }
        if (resultCode != RESULT_OK) { host.deleteAppWidgetId(id); pending = -1; finish(); return }
        if (requestCode == REQ_BIND) configureOrAdd(id) else commit(id)
    }
}
