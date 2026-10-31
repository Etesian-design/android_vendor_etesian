package com.bompotis.etesian.store.data

import java.io.File
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import org.json.JSONObject

/**
 * Verifies F-Droid's signed entry.jar (JAR v1 signature) and returns its entry.json, which
 * pins the SHA-256 of index-v2.json. Anything not signed by F-Droid's repo key is refused.
 */
object EntryVerifier {
    /** SHA-256 of the f-droid.org/repo signing certificate (published by F-Droid). */
    const val FDROID_FINGERPRINT =
        "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"

    class Entry(val timestamp: Long, val indexName: String, val indexSha256: String, val indexSize: Long)

    fun readVerifiedEntry(jar: File): String {
        JarFile(jar, true).use { jf ->
            val entry = jf.getJarEntry("entry.json")
                ?: throw SecurityException("entry.jar has no entry.json")
            // Reading the whole stream makes JarFile check the digest (throws on mismatch).
            val bytes = jf.getInputStream(entry).use { it.readBytes() }
            val certs = entry.certificates
            if (certs == null || certs.isEmpty()) {
                throw SecurityException("entry.json is not signed")
            }
            for (cert in certs) {
                if (cert !is X509Certificate) throw SecurityException("Unexpected certificate type")
                val fp = Http.sha256(cert.encoded)
                if (!fp.equals(FDROID_FINGERPRINT, ignoreCase = true)) {
                    throw SecurityException("entry.jar signed by unknown key $fp")
                }
            }
            return String(bytes, Charsets.UTF_8)
        }
    }

    fun parse(json: String): Entry {
        val o = JSONObject(json)
        val index = o.getJSONObject("index")
        val name = index.getString("name")
        if (!name.startsWith("/") || name.contains("..")) throw SecurityException("Bad index path $name")
        return Entry(
            timestamp = o.getLong("timestamp"),
            indexName = name,
            indexSha256 = index.getString("sha256").lowercase(),
            indexSize = index.optLong("size", -1),
        )
    }
}
