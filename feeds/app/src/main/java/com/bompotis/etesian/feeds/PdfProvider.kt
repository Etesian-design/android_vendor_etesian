package com.bompotis.etesian.feeds

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Hands a PDF from Linkwarden to the user's PDF viewer: read only, and only the files in cache/linkwarden, to an app
 * given access to that one file for the time it is open (FLAG_GRANT_READ_URI_PERMISSION).
 */
class PdfProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val dir = File(context!!.cacheDir, "linkwarden")
        val f = File(dir, uri.lastPathSegment.orEmpty())
        require(f.canonicalFile.parentFile == dir.canonicalFile && f.exists()) { "No such file" }
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri) = "application/pdf"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        val f = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf(f.name, f.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        fun uri(f: File): Uri = Uri.parse("content://com.bompotis.etesian.feeds.pdf/" + f.name)
    }
}
