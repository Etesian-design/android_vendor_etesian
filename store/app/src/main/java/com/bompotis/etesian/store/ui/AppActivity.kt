package com.bompotis.etesian.store.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bompotis.etesian.store.ChangeBus
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.StoreApplication
import com.bompotis.etesian.store.data.FdroidApp
import java.text.DateFormat
import java.util.Date

/** Application page: icon, name, author, actions, screenshots, description, details. */
class AppActivity : Activity() {
    companion object {
        private const val EXTRA_PKG = "pkg"

        fun start(ctx: Context, pkg: String) {
            ctx.startActivity(Intent(ctx, AppActivity::class.java).putExtra(EXTRA_PKG, pkg))
        }
    }

    private val store by lazy { StoreApplication.get(this) }
    private var pkg: String? = null
    private var shownApp: FdroidApp? = null
    private lateinit var content: LinearLayout
    private lateinit var header: Ui.Header
    private var actionButton: TextView? = null
    private var uninstallButton: TextView? = null

    private val listener: (Int) -> Unit = { kind ->
        if (kind == ChangeBus.INDEX) render() else updateButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG)
            ?: intent.data?.getQueryParameter("id")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.WINDOW)
        }
        header = Ui.Header(this, getString(R.string.app_name))
        root.addView(header.view)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.LIST)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // The window's decor view exists only after setContentView.
        setContentView(root)
        Ui.setupWindow(this, root, header.view, scroll)
    }

    override fun onStart() {
        super.onStart()
        ChangeBus.add(listener)
        render()
    }

    override fun onStop() {
        ChangeBus.remove(listener)
        super.onStop()
    }

    private fun d(v: Int) = Ui.dp(this, v)

    private fun render() {
        val p = pkg
        val app = p?.let { store.repo.index?.apps?.get(it) }
        if (app != null && app === shownApp) {
            updateButtons()
            return
        }
        shownApp = app
        content.removeAllViews()
        actionButton = null
        uninstallButton = null
        if (app == null) {
            if (!store.repo.busy) {
                content.addView(Ui.text(this, 16f, Ui.SECONDARY).apply {
                    setText(R.string.app_not_found)
                    setPadding(d(16), d(24), d(16), d(24))
                })
            }
            return
        }
        header.title.text = app.name
        buildTop(app)
        buildScreenshots(app)
        section(app.summary.takeIf { it.isNotBlank() }?.let { s ->
            Ui.text(this, 16f).apply { text = s }
        })
        if (app.description.isNotBlank()) {
            section(Ui.text(this, 15f).apply {
                text = Html.fromHtml(app.description, Html.FROM_HTML_MODE_COMPACT).trim()
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(Ui.BLUE)
                setLineSpacing(0f, 1.15f)
            })
        }
        buildInfo(app)
        buildPermissions(app)
        updateButtons()
    }

    private fun section(view: View?) {
        view ?: return
        view.setPadding(d(16), d(14), d(16), d(14))
        content.addView(view)
        content.addView(Ui.separator(this))
    }

    private fun buildTop(app: FdroidApp) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconPx = d(96)
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        Ui.squircle(icon)
        row.addView(icon, LinearLayout.LayoutParams(iconPx, iconPx))
        store.images.load(icon, app.icon?.let { RepositoryUrls.file(it) }, iconPx)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), 0, 0, 0)
        }
        col.addView(Ui.text(this, 26f, Ui.TEXT, Ui.light).apply { text = app.name })
        app.author?.takeIf { it.isNotBlank() }?.let { a ->
            col.addView(Ui.text(this, 14f, Ui.SECONDARY).apply { text = a })
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, d(10), 0, 0)
        }
        val action = Ui.button(this).apply { setOnClickListener { AppActions.perform(this@AppActivity, app) } }
        val uninstall = Ui.button(this).apply {
            setText(R.string.action_uninstall)
            setBackgroundResource(R.drawable.btn_red)
            setOnClickListener { store.installer.uninstall(app.packageName, app.name) }
        }
        buttons.addView(action)
        buttons.addView(uninstall, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = d(8) })
        col.addView(buttons)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        section(row)
        actionButton = action
        uninstallButton = uninstall
    }

    private fun buildScreenshots(app: FdroidApp) {
        if (app.screenshots.isEmpty()) return
        val h = d(260)
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in app.screenshots) {
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            Ui.rounded(iv, 0.04f)
            strip.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h).apply {
                marginEnd = d(8)
            })
            store.images.load(iv, RepositoryUrls.file(s), h)
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
        section(scroller)
    }

    private fun buildInfo(app: FdroidApp) {
        val v = app.suggested
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun line(label: Int, value: String?) {
            if (value.isNullOrBlank()) return
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, d(3), 0, d(3))
            }
            r.addView(Ui.text(this, 14f, Ui.SECONDARY).apply { setText(label) },
                LinearLayout.LayoutParams(d(110), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(Ui.text(this, 14f).apply { text = value; setTextIsSelectable(true) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            table.addView(r)
        }
        line(R.string.info_version, "${v.versionName} (${v.versionCode})")
        line(R.string.info_size, Ui.formatSize(v.size))
        line(R.string.info_license, app.license)
        if (app.lastUpdated > 0) {
            line(R.string.info_updated, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(app.lastUpdated)))
        }
        line(R.string.info_package, app.packageName)
        val info = AppActions.installedInfo(this, app.packageName)
        if (info != null && !AppActions.signerMatches(info, app)) {
            table.addView(Ui.text(this, 14f, 0xFFD42D2A.toInt()).apply {
                setText(R.string.signer_mismatch)
                setPadding(0, d(8), 0, 0)
            })
        }
        section(table)
    }

    private fun buildPermissions(app: FdroidApp) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 14f, Ui.BLUE).apply {
            setText(R.string.permissions)
            setPadding(0, 0, 0, d(6))
        })
        val pm = packageManager
        val perms = app.suggested.permissions
        if (perms.isEmpty()) {
            col.addView(Ui.text(this, 14f, Ui.SECONDARY).apply { setText(R.string.no_permissions) })
        }
        for (p in perms) {
            val label = try {
                pm.getPermissionInfo(p, 0).loadLabel(pm).toString()
                    .replaceFirstChar { it.uppercase() }
            } catch (e: Exception) {
                null
            }
            col.addView(Ui.text(this, 14f).apply { text = label ?: p })
            if (label != null) col.addView(Ui.text(this, 12f, Ui.SECONDARY).apply { text = p })
        }
        section(col)
    }

    private fun updateButtons() {
        val app = shownApp ?: return
        actionButton?.let { AppActions.bind(it, app) }
        val installed = AppActions.installedInfo(this, app.packageName) != null
        uninstallButton?.visibility =
            if (installed && store.installer.state(app.packageName) == null) View.VISIBLE else View.GONE
    }
}
