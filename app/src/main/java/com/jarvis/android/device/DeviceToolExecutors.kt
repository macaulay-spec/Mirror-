package com.jarvis.android.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jarvis.agent.tool.ToolDefinition
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult

object DeviceToolExecutors {

    fun registerAll() {
        // CHANGED (forensic audit): removed the duplicate "device_flashlight"
        // and "device_volume" registrations that used to live here. ToolRegistry.kt
        // defines the canonical versions of both (correct argument keys, volume
        // level/direction/max support); because ToolRegistration.registerAll()
        // calls DeviceToolExecutors.registerAll() AFTER ToolRegistry's init, the
        // copies here silently overwrote the good ones -- which is why "turn the
        // torch off" did nothing (this copy read args["enable"] while the model
        // sends "enabled") and "set volume to 50%" ignored the number entirely.

        // Haptic Vibrate Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "device_vibrate",
                name = "Trigger Haptic Vibration",
                description = "Triggers a haptic pulse or vibration pattern.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val durationMs = (args["duration_ms"]?.toString()?.toLongOrNull()) ?: 200L
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                ToolExecutionResult(
                    toolId = "device_vibrate",
                    success = true,
                    data = mapOf("duration_ms" to durationMs),
                    verificationDetails = "Vibrated for ${durationMs}ms"
                )
            }
        )

        // Open App Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "open_app",
                name = "Open Installed App",
                description = "Launches an installed app by name (e.g. YouTube, Spotify, WhatsApp, Settings, Camera, Maps, Chrome).",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val appQuery = args["app"]?.toString() ?: args["name"]?.toString() ?: args["app_name"]?.toString() ?: ""
                if (appQuery.isBlank()) {
                    return@ToolDefinition ToolExecutionResult(toolId = "open_app", success = false, data = null, error = "App name must be provided.")
                }

                val pm = context.packageManager
                val db = com.jarvis.app.memory.AppDatabase.get(context)
                // Check Context Graph app aliases first (e.g., 'the bank app' -> specific package)
                var resolvedPackage: String? = null
                try {
                    val aliases = db.contextGraphDao().getAllAppAliasesSync()
                    val matchedAlias = aliases.firstOrNull { alias ->
                        alias.defaultLabel.equals(appQuery, ignoreCase = true) ||
                        alias.nicknames.any { nick -> nick.equals(appQuery, ignoreCase = true) || appQuery.contains(nick, ignoreCase = true) }
                    }
                    resolvedPackage = matchedAlias?.packageName
                } catch (_: Exception) {}

                val intent = (resolvedPackage?.let { pm.getLaunchIntentForPackage(it) })
                    ?: pm.getLaunchIntentForPackage(appQuery)
                    ?: run {
                        val apps = pm.getInstalledApplications(0)
                        val match = apps.firstOrNull { app ->
                            val label = pm.getApplicationLabel(app).toString()
                            label.equals(appQuery, ignoreCase = true) || label.contains(appQuery, ignoreCase = true) || app.packageName.contains(appQuery, ignoreCase = true)
                        }
                        match?.let { pm.getLaunchIntentForPackage(it.packageName) }
                    }

                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "open_app",
                        success = true,
                        data = mapOf("app" to appQuery),
                        verificationDetails = "Successfully opened app '$appQuery'."
                    )
                } else {
                    ToolExecutionResult(
                        toolId = "open_app",
                        success = false,
                        data = null,
                        error = "App '$appQuery' not found on device."
                    )
                }
            }
        )

        // Cross-App Search Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "app_search",
                name = "Cross-App Search",
                description = "Searches for a query inside an app (e.g. YouTube, Spotify, Google Maps, Web browser, Google Play).",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val app = args["app"]?.toString()?.lowercase() ?: "youtube"
                val query = args["query"]?.toString() ?: ""

                if (query.isBlank()) {
                    return@ToolDefinition ToolExecutionResult(toolId = "app_search", success = false, data = null, error = "Search query is required.")
                }

                val intent: android.content.Intent? = when {
                    app.contains("youtube") -> {
                        android.content.Intent(android.content.Intent.ACTION_SEARCH).apply {
                            `package` = "com.google.android.youtube"
                            putExtra("query", query)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                    app.contains("spotify") -> {
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("spotify:search:$query")).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                    app.contains("map") -> {
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(query)}")).apply {
                            `package` = "com.google.android.apps.maps"
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                    app.contains("play") || app.contains("store") -> {
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=${android.net.Uri.encode(query)}")).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                    else -> {
                        // Web Search fallback
                        android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(android.app.SearchManager.QUERY, query)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }

                try {
                    if (intent != null) {
                        context.startActivity(intent)
                        ToolExecutionResult(
                            toolId = "app_search",
                            success = true,
                            data = mapOf("app" to app, "query" to query),
                            verificationDetails = "Triggered search for '$query' in $app."
                        )
                    } else {
                        ToolExecutionResult(toolId = "app_search", success = false, data = null, error = "Cannot search in $app.")
                    }
                } catch (e: Exception) {
                    // Fallback to browser search
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(query)}")).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    ToolExecutionResult(
                        toolId = "app_search",
                        success = true,
                        data = mapOf("app" to "browser", "query" to query),
                        verificationDetails = "Searched '$query' via web browser."
                    )
                }
            }
        )

        // Lock Screen Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "device_lock",
                name = "Lock Screen / Turn Off Display",
                description = "Locks the device screen using Accessibility service.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { _, _ ->
                val service = com.jarvis.android.accessibility.JarvisAccessibilityService.instance
                if (service != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val success = service.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    ToolExecutionResult(
                        toolId = "device_lock",
                        success = success,
                        data = null,
                        verificationDetails = if (success) "Device screen locked." else "Failed to lock screen."
                    )
                } else {
                    ToolExecutionResult(
                        toolId = "device_lock",
                        success = false,
                        data = null,
                        error = "Accessibility service with lock screen capability is required."
                    )
                }
            }
        )

        // Media Playback Control Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "device_media_control",
                name = "Media Playback Control",
                description = "Controls media playback (play, pause, next, previous, stop).",
                category = "MEDIA",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val action = args["action"]?.toString()?.lowercase() ?: "play_pause"
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val keyCode = when (action) {
                    "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                    "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                    "play_pause", "toggle" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "next", "skip" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
                    else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
                
                ToolExecutionResult(
                    toolId = "device_media_control",
                    success = true,
                    data = mapOf("action" to action),
                    verificationDetails = "Media action '$action' executed."
                )
            }
        )

        // CHANGED (forensic audit): removed the duplicate "battery_info" tool
        // that used to be registered here -- it did the same job as
        // "device_battery" in ToolRegistry.kt under a different id, and both
        // were visible to the AI simultaneously. Temperature reporting was
        // merged into device_battery instead of being lost.

        // Smart TV Cast / Remote Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "smart_tv_control",
                name = "Smart TV / Android Box Control",
                description = "Sends playback, launch or casting commands to Smart TV / Android TV on the local network.",
                category = "INTEGRATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val action = args["action"]?.toString() ?: "open_cast"
                val mediaUrl = args["url"]?.toString() ?: ""

                // Launch Cast dialog or Google Home / Android TV Remote
                val intent = if (mediaUrl.isNotBlank()) {
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(mediaUrl)).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    val pm = context.packageManager
                    pm.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
                        ?: pm.getLaunchIntentForPackage("com.google.android.tv.remote.service")
                        ?: android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                }

                try {
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "smart_tv_control",
                        success = true,
                        data = mapOf("action" to action),
                        verificationDetails = "Smart TV control connection initialized."
                    )
                } catch (e: Exception) {
                    ToolExecutionResult(
                        toolId = "smart_tv_control",
                        success = false,
                        data = null,
                        error = "Could not launch TV control: ${e.localizedMessage}"
                    )
                }
            }
        )
    }
}
