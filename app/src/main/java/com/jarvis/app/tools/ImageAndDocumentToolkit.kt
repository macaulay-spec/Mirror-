package com.jarvis.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

object ImageUtils {
    fun fromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { null }
    }
}

/** Read text out of common document types via SAF URIs. */
object DocumentReader {

    fun displayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) ?: uri.lastPathSegment ?: "file" else uri.lastPathSegment ?: "file"
                } else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    fun readText(context: Context, uri: Uri): String {
        val name = displayName(context, uri).lowercase(Locale.ROOT)
        return if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") ||
            name.endsWith(".csv") || name.endsWith(".log")
        ) {
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
            } catch (_: Exception) { "" }
        } else {
            "This is a $name file. Text extraction for this type isn't wired in yet."
        }
    }
}
