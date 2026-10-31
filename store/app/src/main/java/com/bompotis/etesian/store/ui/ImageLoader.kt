package com.bompotis.etesian.store.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.data.Http
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Icons and screenshots: memory LRU + disk cache in cacheDir, fetched on 4 threads. */
class ImageLoader(context: Context) {
    private val disk = File(context.cacheDir, "images").apply { mkdirs() }
    private val io = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        scope.launch(io) { trimDisk(60L * 1024 * 1024) }
    }

    fun load(view: ImageView, url: String?, sizePx: Int, placeholder: Int = R.drawable.icon_placeholder) {
        (view.getTag(R.id.image_job) as? Job)?.cancel()
        view.setTag(R.id.image_url, url)
        if (url == null) {
            view.setImageResource(placeholder)
            return
        }
        val key = "$url@$sizePx"
        memory.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageResource(placeholder)
        val job = scope.launch {
            val bmp = withContext(io) { fetch(url, sizePx, key) }
            if (bmp != null && view.getTag(R.id.image_url) == url) view.setImageBitmap(bmp)
        }
        view.setTag(R.id.image_job, job)
    }

    private suspend fun fetch(url: String, sizePx: Int, key: String): Bitmap? = try {
        val f = File(disk, Http.sha256(url.toByteArray()))
        if (!f.exists()) {
            val ctx = kotlin.coroutines.coroutineContext
            Http.download(url, f, expectedSize = 8L * 1024 * 1024, cancelled = { !ctx.isActive })
        } else {
            f.setLastModified(System.currentTimeMillis())
        }
        decode(f, sizePx)?.also { memory.put(key, it) }
    } catch (e: Exception) {
        null
    }

    private fun decode(f: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            f.delete()
            return null
        }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun trimDisk(maxBytes: Long) {
        val files = disk.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > maxBytes) f.delete()
        }
    }
}
