package com.bompotis.etesian.store.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.bompotis.etesian.store.StoreApplication

/** Receives PackageInstaller session / uninstall results. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Installer.EXTRA_PKG) ?: return
        val label = intent.getStringExtra(Installer.EXTRA_LABEL) ?: pkg
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        StoreApplication.get(context).installer.onResult(pkg, label, status, message, confirm)
    }
}
