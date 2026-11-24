package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

/**
 * The Feeds app's connections, managed from Etesian settings (Events and notifications → Feeds), not from a menu in the
 * app (2026-11-24, owner). Full dark pages in the settings' look (DarkPage), as the N9 Accounts app's Add account:
 * - Sync, not signed in: the services as rows (squircle, name, what it syncs, ›); a row opens its form page.
 * - A service's form: its fields, a hint, and Sign in; a failure shows on the page; success goes back to Settings.
 * - Signed in: the account (service, server, user) and Sign out.
 * - Linkwarden: the form, or the connection and Disconnect.
 * [EXTRA_WHAT] is [ACCOUNT] or [LINKWARDEN]; [EXTRA_SERVICE] picks a service's form.
 */
class ConnectionsActivity : Activity() {
    companion object {
        const val EXTRA_WHAT = "what"
        const val EXTRA_SERVICE = "service"
        const val ACCOUNT = "account"
        const val LINKWARDEN = "linkwarden"
        private const val FORM = 1

        /** Each service: its tile colour, its mark, and what syncing it keeps. */
        private val ABOUT = mapOf(
            Sync.FRESHRSS to Triple(0xFF0062BE.toInt(), "F", "Feeds, posts, read and starred, both ways"),
            Sync.NEXTCLOUD to Triple(0xFF0082C9.toInt(), "N", "Feeds, posts, read and starred, both ways"),
            Sync.TTRSS to Triple(0xFFE36F1E.toInt(), "T", "Feeds, posts, read and starred, both ways"),
            Sync.CLOUD to Triple(0xFF3B8C5A.toInt(), "C", "Follow the feeds your Cloud Companion shares; read and starred stay on this phone"),
        )
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.n9_page_in, R.anim.n9_page_out)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.n9_page_back_in, R.anim.n9_page_back_out)
        }
        val service = intent.getStringExtra(EXTRA_SERVICE)
        when (intent.getStringExtra(EXTRA_WHAT)) {
            ACCOUNT -> when {
                service != null -> signInPage(service)
                Sync.account(this) != null -> accountPage()
                else -> servicesPage()
            }
            LINKWARDEN -> if (Linkwarden.active(this)) linkwardenPage() else linkwardenForm()
            else -> finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FORM && resultCode == RESULT_OK) finish() // signed in: straight back to Settings
    }

    /** Not signed in: choose the service, as the N9's Add account. */
    private fun servicesPage() {
        val body = DarkPage.body(this)
        body.addView(DarkPage.note(this, "Choose where your feeds are kept. Your own feeds on this phone are set aside while you are signed in, and come back when you sign out."))
        for ((id, name) in Sync.SERVICES) {
            val (color, mark, what) = ABOUT.getValue(id)
            body.addView(DarkPage.serviceRow(this, name, what, color, mark) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(this, ConnectionsActivity::class.java)
                    .putExtra(EXTRA_WHAT, ACCOUNT).putExtra(EXTRA_SERVICE, id), FORM)
            })
            body.addView(DarkPage.rule(this))
        }
        DarkPage.page(this, "Sync", body)
    }

    /** Signed in: who and where, and Sign out. */
    private fun accountPage() {
        val a = Sync.account(this) ?: return finish()
        val body = DarkPage.body(this)
        body.addView(DarkPage.detail(this, "Service", a.name))
        body.addView(DarkPage.detail(this, if (a.service == Sync.CLOUD) "Feeds address" else "Server", a.server))
        if (a.service != Sync.CLOUD) body.addView(DarkPage.detail(this, "User name", a.user))
        body.addView(DarkPage.note(this,
            if (a.service == Sync.CLOUD) "The feeds and posts come from Cloud Companion, where its owner changes the list. What you read or star stays on this phone."
            else "Your feeds, posts, and what you have read or starred are kept on this server."))
        body.addView(DarkPage.button(this, "Sign out", blue = false) {
            Sync.signOut(this); Feeds.switchPlace(applicationContext, toAccount = false); Feeds.refresh(applicationContext, force = true)
            finish()
        })
        DarkPage.page(this, "Sync", body)
    }

    /** A service's form: the server (and user name and password), its hint, Sign in. */
    private fun signInPage(service: String) {
        val name = Sync.SERVICES.firstOrNull { it.first == service }?.second ?: return finish()
        val body = DarkPage.body(this)
        val (serverBox, server) = DarkPage.field(this, if (service == Sync.CLOUD) "Feeds address" else "Server address",
            if (service == Sync.CLOUD) "Feeds address from Cloud Companion" else "https://", uri = true)
        server.contentDescription = "Server address"
        body.addView(serverBox)
        val (userBox, user) = DarkPage.field(this, "User name", "")
        val (passBox, password) = DarkPage.field(this, "Password", "", secret = true)
        if (service != Sync.CLOUD) { body.addView(userBox); body.addView(passBox) }
        body.addView(DarkPage.note(this, when (service) {
            Sync.FRESHRSS -> "Use the API password set in FreshRSS under Profile."
            Sync.NEXTCLOUD -> "An app password from Nextcloud's Security settings is best."
            Sync.TTRSS -> "Turn on \"Enable API\" in Tiny Tiny RSS's preferences first."
            else -> "In Cloud Companion, open Feeds, then Share, and copy the Etesian Feeds address. Read and starred stay on this phone."
        }))
        val error = error()
        body.addView(error)
        lateinit var go: TextView
        go = DarkPage.button(this, "Sign in", blue = true) {
            val acc = Sync.Account(service, address(server), user.text.toString().trim(), password.text.toString())
            busy(go, "Signing in…", error)
            val app = applicationContext
            Thread {
                val e = Sync.signIn(app, acc)
                main.post {
                    if (e != null) { failed(go, "Sign in", error, e); return@post }
                    Feeds.switchPlace(app, toAccount = true)
                    FeedsJob.schedule(app)
                    Feeds.refresh(app, force = true)
                    setResult(RESULT_OK); finish()
                }
            }.start()
        }
        body.addView(go)
        DarkPage.page(this, name, body)
        server.requestFocus()
    }

    /** Linkwarden connected: where, and Disconnect. */
    private fun linkwardenPage() {
        val body = DarkPage.body(this)
        body.addView(DarkPage.detail(this, "Server", Linkwarden.server(this)))
        body.addView(DarkPage.note(this, "Articles can be saved to it from the reader, and their readable copy and PDF are read from it."))
        body.addView(DarkPage.button(this, "Disconnect", blue = false) { Linkwarden.signOut(this); finish() })
        DarkPage.page(this, "Linkwarden", body)
    }

    private fun linkwardenForm() {
        val body = DarkPage.body(this)
        body.addView(DarkPage.note(this, "Save articles to Linkwarden from the reader, and read their preserved readable copy and PDF."))
        val (serverBox, server) = DarkPage.field(this, "Server address", "https://", uri = true)
        val (tokenBox, token) = DarkPage.field(this, "Access token", "", secret = true)
        body.addView(serverBox); body.addView(tokenBox)
        body.addView(DarkPage.note(this, "Make an access token in Linkwarden under Settings, Access Tokens."))
        val error = error()
        body.addView(error)
        lateinit var go: TextView
        go = DarkPage.button(this, "Connect", blue = true) {
            busy(go, "Connecting…", error)
            val s = address(server); val t = token.text.toString(); val app = applicationContext
            Thread {
                val e = Linkwarden.signIn(app, s, t)
                main.post { if (e != null) failed(go, "Connect", error, e) else finish() }
            }.start()
        }
        body.addView(go)
        DarkPage.page(this, "Linkwarden", body)
        server.requestFocus()
    }

    private fun address(f: EditText) = f.text.toString().trim().let { if ("://" in it || it.isEmpty()) it else "https://$it" }

    private fun error() = Ui.text(this, 16f, DarkPage.ERROR).apply {
        maxLines = 6; visibility = View.GONE; setPadding(dp(16f), dp(8f), dp(16f), 0); contentDescription = "Error"
    }

    private fun busy(go: TextView, label: String, error: TextView) {
        go.text = label; go.isEnabled = false; go.alpha = 0.6f; error.visibility = View.GONE
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(go.windowToken, 0)
    }

    private fun failed(go: TextView, label: String, error: TextView, e: String) {
        if (isFinishing || isDestroyed) return
        go.text = label; go.isEnabled = true; go.alpha = 1f
        error.text = e; error.visibility = View.VISIBLE
    }
}
