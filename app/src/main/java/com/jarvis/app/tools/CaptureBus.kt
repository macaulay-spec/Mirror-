package com.jarvis.app.tools

import android.net.Uri

/** Holds the most recent captured photo URI so the assistant can analyze it. */
object CaptureBus {
    @Volatile var lastImage: Uri? = null
    fun set(uri: Uri?) { lastImage = uri }
    fun get(): Uri? = lastImage
}
