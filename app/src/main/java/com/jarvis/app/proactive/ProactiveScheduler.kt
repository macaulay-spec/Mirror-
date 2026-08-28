package com.jarvis.app.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the times JARVIS speaks without being asked.
 *
 * Uses inexact alarms on purpose — `setWindow` needs no special permission, while
 * `setExact` requires SCHEDULE_EXACT_ALARM, which Android 12+ turns off by default and
 * forces the user into a settings screen. A morning briefing that lands within a few
 * minutes of the requested time is worth more than a permission prompt.
 */
object ProactiveScheduler {

    private const val PREFS = "jarvis_proactive"
    private const val KEY_ENABLED = "briefing_enabled"
    private const val KEY_HOUR = "briefing_hour"
    private const val KEY_MINUTE = "briefing_minute"
    private const val REQ_BRIEFING = 4101

    const val ACTION_BRIEFING = "com.jarvis.app.action.MORNING_BRIEFING"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
        if (isEnabled(context)) schedule(context)
    }

    fun briefingTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HOUR, 7) to prefs.getInt(KEY_MINUTE, 0)
    }

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (hour, minute) = briefingTime(context)

        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val pending = pendingIntent(context)
        // A 15 minute window keeps it close to the requested time without exact alarms.
        alarm.setWindow(
            AlarmManager.RTC_WAKEUP,
            trigger,
            15 * 60_000L,
            pending
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarm.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ProactiveReceiver::class.java).apply {
            action = ACTION_BRIEFING
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQ_BRIEFING, intent, flags)
    }
}
