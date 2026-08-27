package com.jarvis.app.tools

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

class CalendarToolkit(private val context: Context) {

    private fun hasWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun hasRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Creates an event with the given title. Default: tomorrow 9:00 AM - 10:00 AM. */
    fun createEvent(title: String): String {
        if (!hasWrite()) return "Need calendar permission first."
        return try {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val start = cal.timeInMillis
            val end = cal.apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, mainCalendarId())
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) "Added '$title' to your calendar for tomorrow 9:00 AM."
            else "Couldn't add the event."
        } catch (e: Exception) {
            "Calendar failed: ${e.message}"
        }
    }

    private fun mainCalendarId(): Long {
        try {
            val uri = CalendarContract.Calendars.CONTENT_URI
            val fields = arrayOf(CalendarContract.Calendars._ID)
            context.contentResolver.query(uri, fields, null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getLong(0)
            }
        } catch (_: Exception) { }
        return 1L
    }
}
