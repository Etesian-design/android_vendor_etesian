package com.bompotis.etesian.store.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import com.bompotis.etesian.store.ChangeBus
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.data.FdroidApp
import com.bompotis.etesian.store.data.Http
import com.bompotis.etesian.store.data.Repository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Downloads APKs, checks them against the index SHA-256 and installs them with a
 * PackageInstaller session. Silent as a privileged app holding INSTALL_PACKAGES; otherwise
 * the system asks the user (STATUS_PENDING_USER_ACTION, handled in [InstallReceiver]).
 */
class Installer(private val context: Context, private val scope: CoroutineScope) {
    sealed class State {
        object Queued : State()
        class Downloading(val percent: Int) : State()
        object Installing : State()
        object Uninstalling : State()
    }

    companion object {
        private const val TAG = "EtesianStore"
        const val ACTION_INSTALL = "com.bompotis.etesian.store.INSTALL_RESULT"
        const val ACTION_UNINSTALL = "com.bompotis.etesian.store.UNINSTALL_RESULT"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
    }

    private val states = ConcurrentHashMap<String, State>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val installLock = Mutex()
    private val apkDir = File(context.cacheDir, "apks").apply { mkdirs() }
    private val packageInstaller: PackageInstaller get() = context.packageManager.packageInstaller

    init {
        // Leftovers from an interrupted run.
        apkDir.listFiles()?.forEach { it.delete() }
    }

    fun state(pkg: String): State? = states[pkg]

    private fun setState(pkg: String, s: State?) {
        if (s == null) states.remove(pkg) else states[pkg] = s
        ChangeBus.post(ChangeBus.PROGRESS)
    }

    fun install(app: FdroidApp) {
        val pkg = app.packageName
        if (jobs[pkg]?.isActive == true || states[pkg] != null) return
        val v = app.suggested
        setState(pkg, State.Queued)
        jobs[pkg] = scope.launch {
            val ctx = coroutineContext
            try {
                installLock.withLock {
                    val apk = File(apkDir, "${pkg}_${v.versionCode}.apk")
                    var last = -2
                    Http.download(
                        Repository.BASE + v.apkName, apk,
                        expectedSha256 = v.sha256,
                        expectedSize = if (v.size > 0) v.size else -1,
                        cancelled = { !ctx.isActive },
                    ) { done, total ->
                        val p = if (total > 0) (done * 100 / total).toInt() else -1
                        if (p != last) {
                            last = p
                            setState(pkg, State.Downloading(p))
                        }
                    }
                    setState(pkg, State.Installing)
                    try {
                        commit(pkg, app.name, apk)
                    } finally {
                        apk.delete()
                    }
                }
            } catch (e: CancellationException) {
                setState(pkg, null)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Install of $pkg failed", e)
                fail(pkg, app.name, e.message ?: e.javaClass.simpleName)
            } finally {
                jobs.remove(pkg)
            }
        }
    }

    /** Cancels a queued or downloading install. */
    fun cancel(pkg: String) {
        val s = states[pkg]
        if (s is State.Queued || s is State.Downloading) jobs[pkg]?.cancel()
    }

    private fun commit(pkg: String, label: String, apk: File) {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(pkg)
        params.setSize(apk.length())
        params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(id)
        try {
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            session.commit(resultIntent(ACTION_INSTALL, pkg, label, id).intentSender)
            session.close()
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    fun uninstall(pkg: String, label: String) {
        if (states[pkg] != null) return
        setState(pkg, State.Uninstalling)
        try {
            packageInstaller.uninstall(pkg, resultIntent(ACTION_UNINSTALL, pkg, label, pkg.hashCode()).intentSender)
        } catch (e: Exception) {
            fail(pkg, label, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun resultIntent(action: String, pkg: String, label: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, InstallReceiver::class.java)
            .setAction(action)
            .setPackage(context.packageName)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_LABEL, label)
        // Mutable: the system adds the status extras.
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** Called from [InstallReceiver] with the session result. */
    fun onResult(pkg: String, label: String, status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        fail(pkg, label, e.message ?: "Can't ask for confirmation")
                    }
                } else {
                    fail(pkg, label, "No confirmation dialog")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                setState(pkg, null)
                ChangeBus.post(ChangeBus.PACKAGES)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> setState(pkg, null)
            else -> fail(pkg, label, message ?: "Error $status")
        }
    }

    private fun fail(pkg: String, label: String, message: String) {
        setState(pkg, null)
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.install_failed, label, message),
                Toast.LENGTH_LONG).show()
        }
    }
}
