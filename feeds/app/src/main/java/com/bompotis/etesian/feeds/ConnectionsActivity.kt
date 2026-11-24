package com.bompotis.etesian.feeds

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.Toast

/**
 * The Feeds app's connections, managed from Etesian settings (Events and notifications → Feeds), not from a menu in the
 * app (2026-11-24, user): the sync account and Linkwarden, each a dialog over the settings page. [EXTRA_WHAT] is
 * [ACCOUNT] or [LINKWARDEN]; the activity goes when its last dialog closes. Sign-in carries on after that.
 */
class ConnectionsActivity : Activity() {
    companion object {
        const val EXTRA_WHAT = "what"
        const val ACCOUNT = "account"
        const val LINKWARDEN = "linkwarden"
    }

    private val main = Handler(Looper.getMainLooper())
    private var open = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        when (intent.getStringExtra(EXTRA_WHAT)) {
            ACCOUNT -> accountDialog()
            LINKWARDEN -> linkwardenDialog()
            else -> finish()
        }
    }

    /** Shows a dialog; once none is left (the sign-in form follows the list of services), the activity goes. */
    private fun show(b: AlertDialog.Builder): AlertDialog {
        open++
        return b.setOnDismissListener { open--; main.post { if (open == 0 && !isFinishing) finish() } }.show()
    }

    /** Signed in: who and where, and Sign out. Not: the services, then the server, user name and password. */
    private fun accountDialog() {
        val a = Sync.account(this)
        if (a != null) {
            Ui.dialog(this).setTitle(a.name).setMessage(
                if (a.service == Sync.CLOUD) "Following ${a.server}\n\nThe feeds and posts come from Cloud Companion, where its owner changes the list. What you read or star stays on this phone."
                else "Signed in as ${a.user}\n${a.server}\n\nYour feeds, posts, and what you have read or starred are kept on this server.")
                .setPositiveButton("Sign out") { _, _ -> Sync.signOut(this); Feeds.switchPlace(applicationContext, toAccount = false); Feeds.refresh(applicationContext, force = true) }
                .setNegativeButton(android.R.string.cancel, null).let(::show)
            return
        }
        Ui.dialog(this).setTitle("Sync with").setItems(Sync.SERVICES.map { it.second }.toTypedArray()) { _, i ->
            signInDialog(Sync.SERVICES[i].first, Sync.SERVICES[i].second)
        }.setNegativeButton(android.R.string.cancel, null).let(::show)
    }

    /** Linkwarden: articles are saved to it, and their readable copy and PDF read from it. */
    private fun linkwardenDialog() {
        if (Linkwarden.active(this)) {
            Ui.dialog(this).setTitle("Linkwarden").setMessage("Connected to ${Linkwarden.server(this)}\n\nArticles can be saved to it from the reader, and their readable copy and PDF are read from it.")
                .setPositiveButton("Disconnect") { _, _ -> Linkwarden.signOut(this) }
                .setNegativeButton(android.R.string.cancel, null).let(::show)
            return
        }
        val server = Ui.field(this, "Server address").apply { contentDescription = "Server address" }
        val token = Ui.field(this, "Access token", uri = false).apply {
            contentDescription = "Access token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(server); addView(token)
            addView(Ui.text(this@ConnectionsActivity, 14f, Ui.SECONDARY).apply {
                text = "Make an access token in Linkwarden under Settings, Access Tokens."; setPadding(0, dp(6f), 0, 0) })
        }
        Ui.dialog(this).setTitle("Connect Linkwarden").setView(Ui.wrap(this, form))
            .setPositiveButton("Connect") { _, _ ->
                val s = server.text.toString().trim().let { if ("://" in it || it.isEmpty()) it else "https://$it" }
                val t = token.text.toString()
                val app = applicationContext
                Thread {
                    val error = Linkwarden.signIn(app, s, t)
                    main.post { Toast.makeText(app, error ?: "Linkwarden connected", Toast.LENGTH_LONG).show() }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null).let(::show)
    }

    private fun signInDialog(service: String, name: String) {
        val server = Ui.field(this, "Server address").apply { contentDescription = "Server address" }
        val user = Ui.field(this, "User name", uri = false).apply { contentDescription = "User name" }
        val password = Ui.field(this, "Password", uri = false).apply {
            contentDescription = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(server)
            if (service != Sync.CLOUD) { addView(user); addView(password) }
            else server.hint = "Feeds address from Cloud Companion"
            if (service == Sync.FRESHRSS) addView(Ui.text(this@ConnectionsActivity, 14f, Ui.SECONDARY).apply {
                text = "Use the API password set in FreshRSS under Profile."; setPadding(0, dp(6f), 0, 0) })
            if (service == Sync.CLOUD) addView(Ui.text(this@ConnectionsActivity, 14f, Ui.SECONDARY).apply {
                text = "In Cloud Companion, open Feeds, then Share, and copy the Etesian Feeds address. Read and starred stay on this phone."; setPadding(0, dp(6f), 0, 0) })
            if (service == Sync.TTRSS) addView(Ui.text(this@ConnectionsActivity, 14f, Ui.SECONDARY).apply {
                text = "Turn on \"Enable API\" in Tiny Tiny RSS's preferences first."; setPadding(0, dp(6f), 0, 0) })
            if (service == Sync.NEXTCLOUD) addView(Ui.text(this@ConnectionsActivity, 14f, Ui.SECONDARY).apply {
                text = "An app password from Nextcloud's Security settings is best."; setPadding(0, dp(6f), 0, 0) })
        }
        Ui.dialog(this).setTitle(name).setView(Ui.wrap(this, form))
            .setPositiveButton("Sign in") { _, _ ->
                val acc = Sync.Account(service, server.text.toString().trim().let { if ("://" in it || it.isEmpty()) it else "https://$it" },
                    user.text.toString().trim(), password.text.toString())
                val app = applicationContext
                Toast.makeText(app, "Signing in…", Toast.LENGTH_SHORT).show()
                Thread {
                    val error = Sync.signIn(app, acc)
                    main.post {
                        if (error != null) { Toast.makeText(app, error, Toast.LENGTH_LONG).show(); return@post }
                        Toast.makeText(app, "Signed in to $name", Toast.LENGTH_SHORT).show()
                        Feeds.switchPlace(app, toAccount = true)
                        FeedsJob.schedule(app)
                        Feeds.refresh(app, force = true)
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null).let(::show)
    }
}
