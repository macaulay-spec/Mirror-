package com.jarvis.app.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Quick Settings tile — the guaranteed way to reach JARVIS.
 *
 * Samsung, Xiaomi and HiOS do not always honour the assistant role for the home gesture,
 * so the tile is the fallback that always works: one tap and JARVIS is listening.
 * Add it from the Quick Settings edit pane.
 */
class JarvisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.label = "JARVIS"
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, com.jarvis.app.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("WAKE_WORD_ACTIVATED", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
