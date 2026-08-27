package com.jarvis.app.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class FileAndCameraToolkit(private val context: Context) {

    /** Opens the system SAF file picker. User picks a file; JARVIS gets its URI. */
    private var pendingFileUri: Uri? = null

    fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: Exception) { }
    }

    fun onFilePicked(uri: Uri?) { pendingFileUri = uri }
    fun pendingFile(): Uri? = pendingFileUri
    fun clearPendingFile() { pendingFileUri = null }

    fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: Exception) { }
    }

    /** Returns a human summary of the file (name + type + text if readable). */
    fun summarizeFile(uri: Uri): String {
        val name = DocumentReader.displayName(context, uri)
        val text = DocumentReader.readText(context, uri)
        return if (text.isNotBlank()) {
            "File: $name\n---\n${text.take(1200)}"
        } else {
            "Selected: $name. I can open this type, but text extraction isn't wired in yet."
        }
    }

    fun openManageStorage() {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
