package com.jarvis.app.proactive

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.app.JarvisApp
import com.jarvis.app.notifications.NotificationRepository
import com.jarvis.app.voice.SpeechOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The part that makes JARVIS feel like an assistant rather than a search box: it speaks
 * first. Re-arms the briefing after reboot or app update, and answers the alarm by
 * assembling a morning summary and saying it out loud.
 */
class ProactiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (ProactiveScheduler.isEnabled(context)) ProactiveScheduler.schedule(context)
                // FIX (production repair): the wake-word listener used to stay
                // dead after reboot or app update until the user manually opened
                // the app. Restart it whenever the mic permission is already
                // granted, so "Hey JARVIS" survives a reboot.
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, com.jarvis.app.voice.WakeWordForegroundService::class.java)
                        )
                    }
                }
                return
            }
            Intent.ACTION_BATTERY_LOW -> {
                speak(context, "Battery is getting low, sir. You may want to plug in.")
                return
            }
        }

        if (intent.action != ProactiveScheduler.ACTION_BRIEFING) return

        // Re-arm first so a crash while building the briefing cannot lose tomorrow.
        ProactiveScheduler.schedule(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val briefing = buildBriefing(context)
                speak(context, briefing)
                notify(context, briefing)
            } finally {
                pending.finish()
            }
        }
    }

    private fun speak(context: Context, text: String) {
        val speech = SpeechOutput(context.applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            // TextToSpeech needs a beat to bind before speak() will make a sound.
            delay(900)
            speech.speak(text)
            delay((text.length * 55L) + 4_000L)
            speech.shutdown()
        }
    }

    private suspend fun buildBriefing(context: Context): String {
        val parts = mutableListOf<String>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        parts += "$greeting, sir. It is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."

        val events = todayEvents(context)
        if (events.isEmpty()) {
            parts += "Your calendar is clear today."
        } else {
            parts += if (events.size == 1) {
                "You have one thing today: ${events.first()}."
            } else {
                "You have ${events.size} things today. ${events.take(3).joinToString(" ... ")}."
            }
        }

        val unread = NotificationRepository.all.value.size
        if (unread > 0) parts += "There are $unread unread notifications waiting for you."

        val battery = batteryPercent(context)
        if (battery >= 0) parts += "The battery is at $battery percent."

        return parts.joinToString(" ")
    }

    private suspend fun todayEvents(context: Context): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        return runCatching {
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val end = start + 24 * 60 * 60 * 1000L

            val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DELETED} = 0"
            val args = arrayOf(start.toString(), end.toString())

            val out = mutableListOf<String>()
            val time = SimpleDateFormat("h:mm a", Locale.getDefault())
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                while (cursor.moveToNext() && out.size < 5) {
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "an appointment"
                    val starts = cursor.getLong(startIndex)
                    out += "$title at ${time.format(Date(starts))}"
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun batteryPercent(context: Context): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun notify(context: Context, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val notification = NotificationCompat.Builder(context, JarvisApp.CHANNEL_BRIEFING)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Your morning briefing")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(9021, notification)
        }
    }
}
