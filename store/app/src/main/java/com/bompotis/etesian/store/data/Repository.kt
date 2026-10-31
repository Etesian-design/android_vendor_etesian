package com.bompotis.etesian.store.data

import android.content.Context
import android.util.Log
import com.bompotis.etesian.store.ChangeBus
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The f-droid.org main repository. The index is only accepted when entry.jar is signed by
 * F-Droid's key and index-v2.json matches the SHA-256 that entry.json pins. The verified
 * index is cached in filesDir and checked again against the cached entry on load.
 */
class Repository(context: Context, private val scope: CoroutineScope) {
    companion object {
        const val BASE = "https://f-droid.org/repo"
        private const val TAG = "EtesianStore"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val KEY_LAST_CHECK = "last_check"
    }

    private val dir = File(context.filesDir, "fdroid").apply { mkdirs() }
    private val indexFile = File(dir, "index-v2.json")
    private val entryFile = File(dir, "entry.json")
    private val prefs = context.getSharedPreferences("repo", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    @Volatile var index: RepoIndex? = null
        private set
    @Volatile var busy = false
        private set
    @Volatile var error: String? = null
        private set

    fun start(): Job = scope.launch {
        mutex.withLock {
            busy = true
            ChangeBus.post(ChangeBus.INDEX)
            try {
                loadCached()
            } finally {
                busy = false
                ChangeBus.post(ChangeBus.INDEX)
            }
        }
        refresh(force = false)
    }

    /** Manual refresh with [force]; otherwise checks the repo at most once a day. */
    fun refresh(force: Boolean): Job = scope.launch {
        mutex.withLock {
            val last = prefs.getLong(KEY_LAST_CHECK, 0)
            val now = System.currentTimeMillis()
            if (!force && index != null && now - last in 0 until DAY_MS) return@withLock
            busy = true
            error = null
            ChangeBus.post(ChangeBus.INDEX)
            try {
                update()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Repository update failed", e)
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
                ChangeBus.post(ChangeBus.INDEX)
            }
        }
    }

    private fun loadCached() {
        if (!indexFile.exists() || !entryFile.exists()) return
        try {
            val entry = EntryVerifier.parse(entryFile.readText())
            if (Http.sha256(indexFile) != entry.indexSha256) {
                Log.w(TAG, "Cached index doesn't match its entry; discarding")
                indexFile.delete()
                entryFile.delete()
                return
            }
            index = IndexParser().parse(indexFile)
        } catch (e: Exception) {
            Log.w(TAG, "Can't load cached index", e)
        }
    }

    private suspend fun update() {
        val ctx = coroutineContext
        val jar = File(dir, "entry.jar")
        try {
            Http.download("$BASE/entry.jar", jar, cancelled = { !ctx.isActive })
            val entryText = EntryVerifier.readVerifiedEntry(jar)
            val entry = EntryVerifier.parse(entryText)
            val cached = if (entryFile.exists()) {
                runCatching { EntryVerifier.parse(entryFile.readText()) }.getOrNull()
            } else null
            if (cached != null && index != null && indexFile.exists()) {
                if (cached.indexSha256 == entry.indexSha256) return // unchanged
                if (entry.timestamp < cached.timestamp) {
                    throw SecurityException("Repository offered an older index than the cached one")
                }
            }
            val tmp = File(dir, "index-v2.json.new")
            Http.download(
                BASE + entry.indexName, tmp,
                expectedSha256 = entry.indexSha256,
                expectedSize = entry.indexSize,
                cancelled = { !ctx.isActive },
            )
            val parsed = IndexParser().parse(tmp)
            if (!tmp.renameTo(indexFile)) throw java.io.IOException("Can't store index")
            entryFile.writeText(entryText)
            index = parsed
        } finally {
            jar.delete()
        }
    }
}
