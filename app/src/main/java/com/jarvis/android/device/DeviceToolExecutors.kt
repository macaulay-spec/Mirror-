package com.jarvis.android.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.camera2.CameraManager
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
        // Flashlight Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "device_flashlight",
                name = "Toggle Flashlight",
                description = "Turns the device flashlight/torch on or off.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val enable = args["enable"]?.toString()?.toBooleanStrictOrNull() ?: true
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                try {
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, enable)
                        ToolExecutionResult(
                            toolId = "device_flashlight",
                            success = true,
                            data = mapOf("enabled" to enable),
                            verificationDetails = "Flashlight set to ${if (enable) "ON" else "OFF"}"
                        )
                    } else {
                        ToolExecutionResult(
                            toolId = "device_flashlight",
                            success = false,
                            data = null,
                            error = "No camera with flashlight found."
                        )
                    }
                } catch (e: Exception) {
                    ToolExecutionResult(
                        toolId = "device_flashlight",
                        success = false,
                        data = null,
                        error = "Flashlight error: ${e.localizedMessage}"
                    )
                }
            }
        )

        // Volume Adjuster Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "device_volume",
                name = "Adjust Media Volume",
                description = "Changes media playback volume (up, down, mute, unmute).",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val action = args["action"]?.toString()?.lowercase() ?: "up"
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    when (action) {
                        "up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        "down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        "mute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                        "unmute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    }
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    ToolExecutionResult(
                        toolId = "device_volume",
                        success = true,
                        data = mapOf("volume" to current, "max" to max),
                        verificationDetails = "Media volume adjusted: $current/$max"
                    )
                } else {
                    ToolExecutionResult(toolId = "device_volume", success = false, data = null, error = "Audio service unavailable")
                }
            }
        )

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
                val raw = (args["app"] ?: args["name"] ?: args["app_name"] ?: args["package"] ?: args["query"])
                    ?.toString()?.trim() ?: ""
                val result = com.jarvis.app.tools.AppLauncher.launch(context, raw)
                ToolExecutionResult(
                    toolId = "open_app",
                    success = result.success,
                    data = mapOf("app" to raw, "package" to (result.packageName ?: "")),
                    verificationDetails = if (result.success) result.message else null,
                    error = if (result.success) null else result.message
                )
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

        // Battery Info Tool
        ToolRegistry.register(
            ToolDefinition(
                id = "battery_info",
                name = "Get Battery Status",
                description = "Returns current battery percentage, charging state, and temperature.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val batteryFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, batteryFilter)
                val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
                val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                val temp = (batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

                ToolExecutionResult(
                    toolId = "battery_info",
                    success = true,
                    data = mapOf(
                        "percentage" to batteryPct,
                        "charging" to isCharging,
                        "temperature_c" to temp
                    ),
                    verificationDetails = "Battery is at $batteryPct% (${if (isCharging) "Charging" else "Discharging"}), Temp: ${temp}°C"
                )
            }
        )

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
