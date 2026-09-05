package com.jarvis.app.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
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
    private const val KEY_EVENING_ENABLED = "evening_briefing_enabled"
    private const val KEY_EVENING_HOUR = "evening_briefing_hour"
    private const val KEY_EVENING_MINUTE = "evening_briefing_minute"
    private const val REQ_BRIEFING = 4101
    private const val REQ_EVENING = 4102

    const val ACTION_BRIEFING = "com.jarvis.app.action.MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.jarvis.app.action.EVENING_BRIEFING"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) schedule(context) else cancel(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute) }
        if (isEnabled(context)) schedule(context)
    }

    fun briefingTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HOUR, 7) to prefs.getInt(KEY_MINUTE, 0)
    }

    fun isEveningEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EVENING_ENABLED, false)

    fun setEveningEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_EVENING_ENABLED, enabled) }
        schedule(context)
    }

    fun setEveningTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putInt(KEY_EVENING_HOUR, hour).putInt(KEY_EVENING_MINUTE, minute) }
        if (isEveningEnabled(context)) schedule(context)
    }

    fun eveningTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_EVENING_HOUR, 20) to prefs.getInt(KEY_EVENING_MINUTE, 30)
    }

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (isEnabled(context)) {
            val (hour, minute) = briefingTime(context)
            // A 15 minute window keeps it close to the requested time without exact alarms.
            alarm.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt(hour, minute),
                15 * 60_000L,
                pendingIntent(context, REQ_BRIEFING, ACTION_BRIEFING)
            )
        }

        if (isEveningEnabled(context)) {
            val (hour, minute) = eveningTime(context)
            alarm.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt(hour, minute),
                15 * 60_000L,
                pendingIntent(context, REQ_EVENING, ACTION_EVENING_BRIEFING)
            )
        }
    }

    private fun triggerAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarm.cancel(pendingIntent(context, REQ_BRIEFING, ACTION_BRIEFING)) }
        runCatching { alarm.cancel(pendingIntent(context, REQ_EVENING, ACTION_EVENING_BRIEFING)) }
    }

    private fun pendingIntent(context: Context, reqCode: Int, action: String): PendingIntent {
        val intent = Intent(context, ProactiveReceiver::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }
}
