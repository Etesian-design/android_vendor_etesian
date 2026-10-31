package com.bompotis.etesian.store.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

object Http {
    private const val USER_AGENT = "EtesianStore/1.0"

    private fun open(url: String): HttpURLConnection {
        if (!url.startsWith("https://")) throw IOException("Refusing non-HTTPS URL $url")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", USER_AGENT)
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return c
    }

    /**
     * Downloads [url] to [dest] (via a .part file). Returns the lower-case hex SHA-256 of
     * the data; if [expectedSha256] is given and differs, deletes the data and throws.
     */
    fun download(
        url: String,
        dest: File,
        expectedSha256: String? = null,
        expectedSize: Long = -1,
        cancelled: () -> Boolean = { false },
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): String {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        val c = open(url)
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw CancellationException("Download cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (expectedSize > 0 && done > expectedSize) {
                            throw SecurityException("Download larger than expected: $url")
                        }
                        progress(done, total)
                    }
                }
            }
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }
        val sha = hex(md.digest())
        if (expectedSha256 != null && !sha.equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            throw SecurityException("SHA-256 mismatch for $url")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.delete()
            throw IOException("Can't write ${dest.path}")
        }
        return sha
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v ushr 4]).append("0123456789abcdef"[v and 0xf])
        }
        return sb.toString()
    }
}
