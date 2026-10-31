package com.bompotis.etesian.store

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.bompotis.etesian.store.data.Repository
import com.bompotis.etesian.store.install.Installer
import com.bompotis.etesian.store.ui.ImageLoader
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StoreApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repo: Repository
        private set
    lateinit var installer: Installer
        private set
    lateinit var images: ImageLoader
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, scope)
        installer = Installer(this, scope)
        images = ImageLoader(this)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                ChangeBus.post(ChangeBus.PACKAGES)
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, // Package broadcasts come from the system; must be exported to be received.
        Context.RECEIVER_EXPORTED)
        repo.start()
    }

    companion object {
        fun get(context: Context) = context.applicationContext as StoreApplication
    }
}

/** Main-thread notifications: index replaced, packages changed, install progress. */
object ChangeBus {
    const val INDEX = 1
    const val PACKAGES = 2
    const val PROGRESS = 3

    private val listeners = CopyOnWriteArraySet<(Int) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(l: (Int) -> Unit) { listeners.add(l) }
    fun remove(l: (Int) -> Unit) { listeners.remove(l) }
    fun post(kind: Int) { main.post { listeners.forEach { it(kind) } } }
}
