package com.bompotis.etesian.store.data

import android.os.Build
import android.os.LocaleList
import android.util.JsonReader
import android.util.JsonToken
import java.io.File

/** Streaming parser for F-Droid index-v2.json; keeps one suggested version per app. */
class IndexParser {
    private val sdk = Build.VERSION.SDK_INT
    private val abis = Build.SUPPORTED_ABIS.toSet()
    private val langs: List<String> = run {
        val out = ArrayList<String>()
        val list = LocaleList.getDefault()
        for (i in 0 until list.size()) {
            val l = list[i]
            out += l.toLanguageTag()
            out += l.language
        }
        out += listOf("en-US", "en")
        out.distinct()
    }

    fun parse(file: File): RepoIndex {
        JsonReader(file.bufferedReader(Charsets.UTF_8)).use { r ->
            var timestamp = 0L
            var categories = emptyList<Category>()
            val apps = HashMap<String, FdroidApp>(8192)
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "repo" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "timestamp" -> timestamp = r.nextLong()
                                "categories" -> categories = readCategories(r)
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    }
                    "packages" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val pkg = r.nextName()
                            readPackage(r, pkg)?.let { apps[pkg] = it }
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            return RepoIndex(timestamp, apps, categories)
        }
    }

    private fun JsonReader.nullSkipped(): Boolean {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return true
        }
        return false
    }

    private fun <T> pick(map: Map<String, T>): T? {
        if (map.isEmpty()) return null
        for (l in langs) map[l]?.let { return it }
        for (l in langs) {
            map.entries.firstOrNull { it.key.startsWith("$l-") }?.let { return it.value }
        }
        return map.values.first()
    }

    private fun <T> readLocalized(r: JsonReader, value: (JsonReader) -> T?): T? {
        if (r.nullSkipped()) return null
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            return value(r)
        }
        val map = HashMap<String, T>()
        r.beginObject()
        while (r.hasNext()) {
            val k = r.nextName()
            value(r)?.let { map[k] = it }
        }
        r.endObject()
        return pick(map)
    }

    private fun readString(r: JsonReader): String? =
        if (r.nullSkipped()) null
        else if (r.peek() == JsonToken.STRING || r.peek() == JsonToken.NUMBER) r.nextString()
        else { r.skipValue(); null }

    private fun readLong(r: JsonReader): Long =
        if (r.nullSkipped()) 0L
        else if (r.peek() == JsonToken.NUMBER) r.nextLong()
        else { r.skipValue(); 0L }

    private fun readLocalizedString(r: JsonReader): String? = readLocalized(r) { readString(it) }

    /** A file object: {"name": "/path", "sha256": ..., "size": ...}; returns name. */
    private fun readFileName(r: JsonReader): String? {
        if (r.nullSkipped()) return null
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        var name: String? = null
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "name") name = readString(r) else r.skipValue()
        }
        r.endObject()
        return name
    }

    private fun readStringArray(r: JsonReader): List<String> {
        if (r.nullSkipped()) return emptyList()
        val out = ArrayList<String>()
        r.beginArray()
        while (r.hasNext()) readString(r)?.let { out += it }
        r.endArray()
        return out
    }

    private fun readCategories(r: JsonReader): List<Category> {
        val out = ArrayList<Category>()
        if (r.nullSkipped()) return out
        r.beginObject()
        while (r.hasNext()) {
            val id = r.nextName()
            var name: String? = null
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "name") name = readLocalizedString(r) else r.skipValue()
            }
            r.endObject()
            out += Category(id, name ?: id)
        }
        r.endObject()
        return out
    }

    private class Meta {
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var author: String? = null
        var license: String? = null
        var webSite: String? = null
        var icon: String? = null
        var categories: List<String> = emptyList()
        var screenshots: List<String> = emptyList()
        var added = 0L
        var lastUpdated = 0L
    }

    private fun readPackage(r: JsonReader, pkg: String): FdroidApp? {
        var meta: Meta? = null
        var best: Version? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "metadata" -> meta = readMeta(r)
                "versions" -> {
                    if (r.nullSkipped()) continue
                    r.beginObject()
                    while (r.hasNext()) {
                        r.nextName()
                        val v = readVersion(r) ?: continue
                        if (best == null || v.versionCode > best.versionCode) best = v
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        val m = meta ?: return null
        val v = best ?: return null
        return FdroidApp(
            packageName = pkg,
            name = m.name ?: pkg,
            summary = m.summary.orEmpty(),
            description = m.description.orEmpty(),
            author = m.author,
            license = m.license,
            webSite = m.webSite,
            icon = m.icon,
            categories = m.categories,
            screenshots = m.screenshots,
            added = m.added,
            lastUpdated = if (m.lastUpdated > 0) m.lastUpdated else v.added,
            suggested = v,
        )
    }

    private fun readMeta(r: JsonReader): Meta {
        val m = Meta()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "name" -> m.name = readLocalizedString(r)
                "summary" -> m.summary = readLocalizedString(r)
                "description" -> m.description = readLocalizedString(r)
                "authorName" -> m.author = readString(r)
                "license" -> m.license = readString(r)
                "webSite" -> m.webSite = readString(r)
                "icon" -> m.icon = readLocalized(r) { readFileName(it) }
                "categories" -> m.categories = readStringArray(r)
                "added" -> m.added = readLong(r)
                "lastUpdated" -> m.lastUpdated = readLong(r)
                "screenshots" -> m.screenshots = readScreenshots(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return m
    }

    /** {"phone": {"en-US": [file, ...]}, "sevenInch": ...}: phone screenshots only. */
    private fun readScreenshots(r: JsonReader): List<String> {
        if (r.nullSkipped()) return emptyList()
        var phone: List<String> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "phone") {
                phone = readLocalized(r) { rr ->
                    if (rr.nullSkipped()) null else {
                        val out = ArrayList<String>()
                        rr.beginArray()
                        while (rr.hasNext()) readFileName(rr)?.let { out += it }
                        rr.endArray()
                        out
                    }
                } ?: emptyList()
            } else {
                r.skipValue()
            }
        }
        r.endObject()
        return phone
    }

    /** Returns null for versions this device can't use, or beta-channel releases. */
    private fun readVersion(r: JsonReader): Version? {
        var added = 0L
        var apk: String? = null
        var sha: String? = null
        var size = 0L
        var versionName = ""
        var versionCode = 0L
        var minSdk = 1
        var maxSdk = Int.MAX_VALUE
        val perms = ArrayList<String>()
        var signers: List<String> = emptyList()
        var native: List<String> = emptyList()
        var beta = false
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "added" -> added = readLong(r)
                "releaseChannels" -> beta = readStringArray(r).isNotEmpty()
                "file" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> apk = readString(r)
                            "sha256" -> sha = readString(r)
                            "size" -> size = readLong(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "manifest" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "versionName" -> versionName = readString(r).orEmpty()
                            "versionCode" -> versionCode = readLong(r)
                            "maxSdkVersion" -> maxSdk = readLong(r).toInt().takeIf { it > 0 } ?: Int.MAX_VALUE
                            "usesSdk" -> {
                                r.beginObject()
                                while (r.hasNext()) {
                                    when (r.nextName()) {
                                        "minSdkVersion" -> minSdk = readLong(r).toInt()
                                        "maxSdkVersion" -> maxSdk = readLong(r).toInt().takeIf { it > 0 } ?: Int.MAX_VALUE
                                        else -> r.skipValue()
                                    }
                                }
                                r.endObject()
                            }
                            "signer" -> {
                                r.beginObject()
                                while (r.hasNext()) {
                                    if (r.nextName() == "sha256") signers = readStringArray(r).map { it.lowercase() }
                                    else r.skipValue()
                                }
                                r.endObject()
                            }
                            "usesPermission", "usesPermissionSdk23" -> readPermissions(r, perms)
                            "nativecode" -> native = readStringArray(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        val apkName = apk ?: return null
        val apkSha = sha ?: return null
        if (beta) return null
        if (sdk < minSdk || sdk > maxSdk) return null
        if (native.isNotEmpty() && native.none { it in abis }) return null
        if (!apkName.startsWith("/") || apkName.contains("..")) return null
        return Version(versionName, versionCode, apkName, apkSha.lowercase(), size, minSdk,
            perms.distinct(), signers, added)
    }

    private fun readPermissions(r: JsonReader, out: MutableList<String>) {
        if (r.nullSkipped()) return
        r.beginArray()
        while (r.hasNext()) {
            var name: String? = null
            var max = Int.MAX_VALUE
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "name" -> name = readString(r)
                    "maxSdkVersion" -> max = readLong(r).toInt().takeIf { it > 0 } ?: Int.MAX_VALUE
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (name != null && sdk <= max) out += name
        }
        r.endArray()
    }
}
