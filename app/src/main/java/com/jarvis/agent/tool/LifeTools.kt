package com.jarvis.agent.tool

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.app.tools.LocationToolkit
import com.jarvis.app.tools.TimeParser
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import java.io.File
import java.io.FileOutputStream

/**
 * Everyday actions: events and reminders with real time understanding, navigation,
 * alarms and timers, the clipboard, and reading what is on the screen as an image.
 *
 * These are the things people actually ask a phone assistant to do, and none of them
 * existed before this file.
 */
object LifeTools {

    fun registerAll() {
        registerCalendar()
        registerReminder()
        registerAlarm()
        registerTimer()
        registerNavigate()
        registerNearby()
        registerShareLocation()
        registerClipboard()
        registerScreenCapture()
    }

    private fun arg(args: Map<String, Any?>, vararg keys: String): String =
        keys.mapNotNull { args[it]?.toString()?.trim() }.firstOrNull { it.isNotBlank() } ?: ""

    // ------------------------------------------------------------- calendar

    /** Replaces the version that hardcoded every event to tomorrow at 9am. */
    private fun registerCalendar() {
        ToolRegistry.register(
            ToolDefinition(
                id = "calendar_create",
                name = "Create Calendar Event",
                description = "Creates a calendar event. Understands phrases like 'meeting Thursday 3pm', 'tomorrow morning', 'in 2 hours'.",
                category = "CALENDAR",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val title = arg(args, "title", "event", "name").ifBlank { "JARVIS event" }
                val whenText = arg(args, "when", "time", "date", "at")
                val notes = arg(args, "notes", "description")

                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return@ToolDefinition error("calendar_create", "I need calendar permission to add events.")
                }

                val start = TimeParser.parse("$title $whenText")
                    ?: return@ToolDefinition error("calendar_create", "I couldn't understand the time. Try 'Thursday 3pm'.")
                val durationMinutes = args["duration_minutes"]?.toString()?.toIntOrNull() ?: 60
                val end = start + durationMinutes * 60_000L

                val calendarId = primaryCalendarId(context)
                    ?: return@ToolDefinition error("calendar_create", "There's no calendar on this phone to write to.")

                val values = android.content.ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, end)
                    put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    if (notes.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, notes)
                }

                return@ToolDefinition try {
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri == null) error("calendar_create", "The calendar refused the event.")
                    else ToolExecutionResult(
                        toolId = "calendar_create",
                        success = true,
                        data = mapOf("title" to title, "start" to start),
                        verificationDetails = "Added \"$title\" for ${TimeParser.describe(start)}."
                    )
                } catch (e: Exception) {
                    error("calendar_create", "I couldn't create the event: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerReminder() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_reminder",
                name = "Set a Reminder",
                description = "Sets a reminder for a time the user speaks naturally, e.g. 'remind me to call the bank tomorrow 10am'.",
                category = "CALENDAR",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                // A reminder is a short calendar event with a reminder alarm attached.
                val what = arg(args, "what", "text", "title", "reminder").ifBlank { "Reminder" }
                val whenText = arg(args, "when", "time", "at")
                val start = TimeParser.parse("$what $whenText")
                    ?: return@ToolDefinition error("set_reminder", "When should I remind you?")

                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return@ToolDefinition error("set_reminder", "I need calendar permission to set reminders.")
                }

                val calendarId = primaryCalendarId(context)
                    ?: return@ToolDefinition error("set_reminder", "There's no calendar to write to.")
                val values = android.content.ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, what)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + 15 * 60_000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }
                return@ToolDefinition try {
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    uri?.lastPathSegment?.toLongOrNull()?.let { eventId ->
                        context.contentResolver.insert(
                            CalendarContract.Reminders.CONTENT_URI,
                            android.content.ContentValues().apply {
                                put(CalendarContract.Reminders.EVENT_ID, eventId)
                                put(CalendarContract.Reminders.MINUTES, 0)
                                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            }
                        )
                    }
                    ToolExecutionResult(
                        toolId = "set_reminder",
                        success = true,
                        data = mapOf("what" to what, "start" to start),
                        verificationDetails = "Reminder set: \"$what\" ${TimeParser.describe(start)}."
                    )
                } catch (e: Exception) {
                    error("set_reminder", "I couldn't set that reminder: ${e.localizedMessage}")
                }
            }
        )
    }

    // --------------------------------------------------------------- alarms

    private fun registerAlarm() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_alarm",
                name = "Set an Alarm",
                description = "Sets a phone alarm. Understands '6am', 'tomorrow 7:30', 'in 20 minutes'.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val whenText = arg(args, "when", "time", "at", "for")
                val label = arg(args, "label", "message")
                val millis = TimeParser.parse(whenText)
                    ?: return@ToolDefinition error("set_alarm", "When should the alarm go off?")

                val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                return@ToolDefinition try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
                        putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "set_alarm",
                        success = true,
                        data = mapOf("when" to millis),
                        verificationDetails = "Alarm set for ${TimeParser.describe(millis)}."
                    )
                } catch (e: Exception) {
                    error("set_alarm", "I couldn't set the alarm: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerTimer() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_timer",
                name = "Set a Timer",
                description = "Starts a countdown timer, e.g. 'timer for 10 minutes'.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val seconds = parseDurationSeconds(arg(args, "duration", "length", "time", "seconds", "for"))
                    ?: return@ToolDefinition error("set_timer", "How long should the timer run?")

                return@ToolDefinition try {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val minutes = seconds / 60
                    ToolExecutionResult(
                        toolId = "set_timer",
                        success = true,
                        data = mapOf("seconds" to seconds),
                        verificationDetails = "Timer started for $minutes minute${if (minutes == 1) "" else "s"}."
                    )
                } catch (e: Exception) {
                    error("set_timer", "I couldn't start the timer: ${e.localizedMessage}")
                }
            }
        )
    }

    // ------------------------------------------------------------ navigation

    private fun registerNavigate() {
        ToolRegistry.register(
            ToolDefinition(
                id = "navigate_to",
                name = "Navigate Somewhere",
                description = "Opens turn-by-turn navigation to a place the user names, or to home/work.",
                category = "LOCATION",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val place = arg(args, "destination", "place", "to", "address")
                if (place.isBlank()) return@ToolDefinition error("navigate_to", "Where should I navigate to?")

                val resolved = resolvePlace(context, place)
                return@ToolDefinition try {
                    val uri = Uri.parse("google.navigation:q=${Uri.encode(resolved)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(resolved)}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    ToolExecutionResult(
                        toolId = "navigate_to",
                        success = true,
                        data = mapOf("destination" to resolved),
                        verificationDetails = "Navigating to $resolved."
                    )
                } catch (e: Exception) {
                    error("navigate_to", "I couldn't open navigation: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerNearby() {
        ToolRegistry.register(
            ToolDefinition(
                id = "nearby_search",
                name = "Find Places Nearby",
                description = "Finds nearby places: 'fuel station near me', 'pharmacy nearby'.",
                category = "LOCATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val query = arg(args, "query", "place", "what")
                if (query.isBlank()) return@ToolDefinition error("nearby_search", "What are you looking for?")
                return@ToolDefinition try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    ToolExecutionResult(
                        toolId = "nearby_search",
                        success = true,
                        data = mapOf("query" to query),
                        verificationDetails = "Showing $query near you."
                    )
                } catch (e: Exception) {
                    error("nearby_search", "I couldn't search nearby: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerShareLocation() {
        ToolRegistry.register(
            ToolDefinition(
                id = "share_location",
                name = "Share My Location",
                description = "Opens the share sheet with the current location attached.",
                category = "LOCATION",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, _ ->
                val where = LocationToolkit(context).lastKnown()
                return@ToolDefinition try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, where)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share location").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    ToolExecutionResult(
                        toolId = "share_location",
                        success = true,
                        data = mapOf("location" to where),
                        verificationDetails = "Ready to share: $where"
                    )
                } catch (e: Exception) {
                    error("share_location", "I couldn't share the location: ${e.localizedMessage}")
                }
            }
        )
    }

    // ------------------------------------------------------------- clipboard

    private fun registerClipboard() {
        ToolRegistry.register(
            ToolDefinition(
                id = "clipboard_read",
                name = "Read Clipboard",
                description = "Reads the text currently copied to the clipboard.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    ?: return@ToolDefinition error("clipboard_read", "Clipboard isn't available.")
                val text = manager.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()
                return@ToolDefinition if (text.isNullOrBlank()) {
                    ToolExecutionResult(
                        toolId = "clipboard_read", success = true, data = emptyMap<String, Any>(),
                        verificationDetails = "The clipboard is empty."
                    )
                } else {
                    ToolExecutionResult(
                        toolId = "clipboard_read", success = true,
                        data = mapOf("text" to text),
                        verificationDetails = text.take(300)
                    )
                }
            }
        )

        ToolRegistry.register(
            ToolDefinition(
                id = "clipboard_write",
                name = "Copy to Clipboard",
                description = "Copies text to the clipboard.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val text = arg(args, "text", "content", "value")
                if (text.isBlank()) return@ToolDefinition error("clipboard_write", "What should I copy?")
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    ?: return@ToolDefinition error("clipboard_write", "Clipboard isn't available.")
                manager.setPrimaryClip(ClipData.newPlainText("JARVIS", text))
                ToolExecutionResult(
                    toolId = "clipboard_write", success = true,
                    data = mapOf("text" to text),
                    verificationDetails = "Copied to clipboard."
                )
            }
        )
    }

    // -------------------------------------------------------- screen capture

    private fun registerScreenCapture() {
        ToolRegistry.register(
            ToolDefinition(
                id = "screen_capture",
                name = "Capture the Screen",
                description = "Takes a screenshot and reports what is on it.",
                category = "SCREEN",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    return@ToolDefinition error(
                        "screen_capture",
                        "Screen capture needs Android 11 or newer, with JARVIS enabled in Accessibility."
                    )
                }
                val service = JarvisAccessibilityService.instance
                    ?: return@ToolDefinition error(
                        "screen_capture",
                        "Turn JARVIS on in Accessibility settings and I can capture the screen."
                    )

                return@ToolDefinition try {
                    kotlinx.coroutines.suspendCancellableCoroutine<ToolExecutionResult> { cont ->
                        val callback =
                            object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                                override fun onSuccess(
                                    result: android.accessibilityservice.AccessibilityService.ScreenshotResult
                                ) {
                                    if (cont.isCompleted) return
                                    val bitmap = bitmapFrom(result)
                                    cont.resumeWith(Result.success(describeBitmap(context, bitmap)))
                                }

                                override fun onFailure(errorCode: Int) {
                                    if (cont.isCompleted) return
                                    cont.resumeWith(
                                        Result.success(
                                            error("screen_capture", "The system refused the screenshot ($errorCode).")
                                        )
                                    )
                                }
                            }

                        service.takeScreenshot(
                            android.view.Display.DEFAULT_DISPLAY,
                            context.mainExecutor,
                            callback
                        )
                    }
                } catch (e: Exception) {
                    error("screen_capture", "Screenshot failed: ${e.localizedMessage}")
                }
            }
        )
    }

    /**
     * AccessibilityService.ScreenshotResult never had a getBitmap() — the bitmap has to be
     * wrapped out of the hardware buffer, and the buffer must be closed afterwards or the
     * system leaks it on every capture.
     */
    @Suppress("NewApi")
    private fun bitmapFrom(
        result: android.accessibilityservice.AccessibilityService.ScreenshotResult
    ): Bitmap? = try {
        val buffer = result.hardwareBuffer
        val wrapped = if (buffer != null) Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) else null
        buffer?.close()
        wrapped
    } catch (_: Exception) {
        null
    }

    private fun describeBitmap(context: Context, bitmap: Bitmap?): ToolExecutionResult {
        if (bitmap == null) return error("screen_capture", "The screen returned nothing.")
        return try {
            val file = File(context.cacheDir, "jarvis_screen_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            val analysis = com.jarvis.app.tools.ImageAnalyzer.analyze(bitmap)
            val described = com.jarvis.app.tools.ImageAnalyzer.describe(analysis)
            ToolExecutionResult(
                toolId = "screen_capture",
                success = true,
                data = mapOf("path" to file.absolutePath, "width" to bitmap.width, "height" to bitmap.height),
                verificationDetails = described
            )
        } catch (e: Exception) {
            error("screen_capture", "I captured the screen but could not read it: ${e.localizedMessage}")
        }
    }

    // ---------------------------------------------------------------- utils

    private fun primaryCalendarId(context: Context): Long? {
        return try {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(1) == 1) return c.getLong(0)
                }
            }
            // No primary flagged — fall back to the first calendar
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolvePlace(context: Context, place: String): String {
        val lower = place.lowercase()
        return when {
            lower.contains("home") -> savedPlace(context, "home") ?: place
            lower.contains("work") || lower.contains("office") -> savedPlace(context, "work") ?: place
            else -> place
        }
    }

    private fun savedPlace(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences("jarvis_places", Context.MODE_PRIVATE)
        return prefs.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    private fun parseDurationSeconds(text: String): Int? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        Regex("(\\d+)\\s*(second|sec|minute|min|hour|hr)s?").find(lower)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: return@let
            return when (m.groupValues[2]) {
                "second", "sec" -> amount
                "minute", "min" -> amount * 60
                else -> amount * 3600
            }
        }
        return text.trim().toIntOrNull()
    }

    private fun error(toolId: String, message: String) =
        ToolExecutionResult(toolId = toolId, success = false, data = null, error = message)
}
