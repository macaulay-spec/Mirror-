package com.jarvis.app.tools

import android.graphics.Bitmap
import kotlin.math.sqrt

/** Local, no-key image analysis: dimensions and dominant colors. Real OCR/describe upgrades when keys are added. */
object ImageAnalyzer {

    data class Analysis(
        val width: Int,
        val height: Int,
        val dominantColors: List<Pair<String, Int>>
    )

    fun analyze(bitmap: Bitmap): Analysis? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val colors = HashMap<Int, Long>()
        val step = maxOf(1, (bitmap.width * bitmap.height / 8000))
        var count = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                // quantize to reduce noise
                val q = 0xFF000000.toInt() or ((c and 0xFF0000) shr 16 and 0xF8 shl 16) or
                    ((c and 0xFF00) shr 8 and 0xF8 shl 8) or ((c and 0xFF) and 0xF8)
                colors[q] = (colors[q] ?: 0L) + 1
                count++
            }
        }
        val top = colors.entries.sortedByDescending { it.value }.take(3)
        val list = top.map { (color, freq) ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val pct = (freq * 100 / maxOf(1, count))
            val hexc = "#" + String.format("%02X%02X%02X", r, g, b)
            hexc to pct.toInt()
        }
        return Analysis(bitmap.width, bitmap.height, list)
    }

    fun describe(analysis: Analysis?): String {
        if (analysis == null) return "I couldn't read that image."
        val colorsText = analysis.dominantColors.joinToString(", ") { "${it.first} (~${it.second}%)" }
        return "Image: ${analysis.width}×${analysis.height}. Main colors: $colorsText. " +
            "Full OCR/captioning needs a key (ML Kit or a vision model) — I'll add that when you have one."
    }
}
