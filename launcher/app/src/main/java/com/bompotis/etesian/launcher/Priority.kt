package com.bompotis.etesian.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings

/**
 * Android extras → Priority people (story Phone v2, state priority): priority is per contact, not
 * per conversation. Notifications are matched to a contact through their Person uris (tel:,
 * mailto:, contact links) or, failing that, a title equal to the contact's name; a priority
 * person's notifications from every app sit first under Notifications, grouped under them.
 * The people: Settings.Secure etesian_priority_people (comma list of contact ids).
 */
object Priority {
    const val KEY = "etesian_priority_people"
    class Person(val id: Long, val name: String, val photo: Uri?)

    private val cache = mutableMapOf<String, Long>()

    fun on(ctx: Context) = Extras.on(ctx, Extras.PRIORITY)
    fun ids(ctx: Context): Set<Long> = Settings.Secure.getString(ctx.contentResolver, KEY).orEmpty()
        .split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
    fun isPriority(ctx: Context, id: Long) = id in ids(ctx)
    fun set(ctx: Context, id: Long, on: Boolean) {
        val s = ids(ctx).toMutableSet().apply { if (on) add(id) else remove(id) }
        Settings.Secure.putString(ctx.contentResolver, KEY, s.joinToString(","))
    }

    private fun can(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** The contact this notification is about, or -1. */
    fun contactOf(ctx: Context, e: EventItem): Long {
        if (!can(ctx)) return -1
        val k = e.people.joinToString("|") + "#" + e.title
        cache[k]?.let { return it }
        var id = -1L
        for (u in e.people) { id = byUri(ctx, u); if (id >= 0) break }
        if (id < 0 && e.title.isNotBlank()) id = byName(ctx, e.title)
        cache[k] = id
        return id
    }

    private fun q(ctx: Context, uri: Uri, col: String, sel: String? = null, args: Array<String>? = null): Long = runCatching {
        ctx.contentResolver.query(uri, arrayOf(col), sel, args, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
    }.getOrDefault(-1L)

    private fun byUri(ctx: Context, s: String): Long = when {
        s.startsWith("tel:") -> q(ctx, Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(s.removePrefix("tel:"))),
            ContactsContract.PhoneLookup._ID)
        s.startsWith("mailto:") -> q(ctx, Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(s.removePrefix("mailto:"))),
            ContactsContract.CommonDataKinds.Email.CONTACT_ID)
        s.startsWith("content://com.android.contacts") -> q(ctx, Uri.parse(s), ContactsContract.Contacts._ID)
        else -> -1L
    }

    private fun byName(ctx: Context, name: String) = q(ctx, ContactsContract.Contacts.CONTENT_URI, ContactsContract.Contacts._ID,
        "${ContactsContract.Contacts.DISPLAY_NAME}=?", arrayOf(name))

    fun person(ctx: Context, id: Long): Person? = runCatching {
        ctx.contentResolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { c ->
            if (c.moveToFirst()) Person(id, c.getString(0).orEmpty(), c.getString(1)?.let(Uri::parse)) else null
        }
    }.getOrNull()

    fun clearCache() = cache.clear()
}
