package com.bompotis.etesian.store.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.widget.TextView
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.StoreApplication
import com.bompotis.etesian.store.data.FdroidApp
import com.bompotis.etesian.store.data.Http
import com.bompotis.etesian.store.install.Installer

enum class Action { INSTALL, UPDATE, OPEN, INSTALLED }

object AppActions {
    fun installedInfo(ctx: Context, pkg: String): PackageInfo? = try {
        ctx.packageManager.getPackageInfo(
            pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** True when F-Droid's build is signed like the installed copy (so it can update it). */
    fun signerMatches(info: PackageInfo, app: FdroidApp): Boolean {
        val wanted = app.suggested.signers
        if (wanted.isEmpty()) return true
        val signers = info.signingInfo?.apkContentsSigners ?: return false
        return signers.any { Http.sha256(it.toByteArray()) in wanted }
    }

    fun action(ctx: Context, app: FdroidApp, info: PackageInfo? = installedInfo(ctx, app.packageName)): Action {
        if (info == null) return Action.INSTALL
        if (app.suggested.versionCode > info.longVersionCode && signerMatches(info, app)) return Action.UPDATE
        return if (ctx.packageManager.getLaunchIntentForPackage(app.packageName) != null) Action.OPEN
        else Action.INSTALLED
    }

    /** Styles [button] for [app]'s current action or install progress. */
    fun bind(button: TextView, app: FdroidApp) {
        val ctx = button.context
        val state = StoreApplication.get(ctx).installer.state(app.packageName)
        if (state != null) {
            button.setBackgroundResource(R.drawable.btn_grey)
            button.text = when (state) {
                is Installer.State.Queued -> ctx.getString(R.string.action_waiting)
                is Installer.State.Downloading ->
                    if (state.percent >= 0) "${state.percent}%" else ctx.getString(R.string.action_downloading)
                is Installer.State.Installing -> ctx.getString(R.string.action_installing)
                is Installer.State.Uninstalling -> ctx.getString(R.string.action_uninstall)
            }
            button.isEnabled = state is Installer.State.Queued || state is Installer.State.Downloading
            return
        }
        button.isEnabled = true
        when (action(ctx, app)) {
            Action.INSTALL -> {
                button.setBackgroundResource(R.drawable.btn_green)
                button.setText(R.string.action_install)
            }
            Action.UPDATE -> {
                button.setBackgroundResource(R.drawable.btn_blue)
                button.setText(R.string.action_update)
            }
            Action.OPEN -> {
                button.setBackgroundResource(R.drawable.btn_grey)
                button.setText(R.string.action_open)
            }
            Action.INSTALLED -> {
                button.setBackgroundResource(R.drawable.btn_grey)
                button.setText(R.string.action_installed)
                button.isEnabled = false
            }
        }
    }

    /** Click on the action button: install/update/open, or cancel a pending download. */
    fun perform(activity: Activity, app: FdroidApp) {
        val installer = StoreApplication.get(activity).installer
        if (installer.state(app.packageName) != null) {
            installer.cancel(app.packageName)
            return
        }
        when (action(activity, app)) {
            Action.INSTALL, Action.UPDATE -> installer.install(app)
            Action.OPEN -> activity.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.let { activity.startActivity(it) }
            Action.INSTALLED -> Unit
        }
    }
}
