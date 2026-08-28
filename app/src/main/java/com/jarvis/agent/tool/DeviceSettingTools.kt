package com.jarvis.agent.tool

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult

/**
 * Device settings that were written in `DeviceToolkit` but never exposed as tools:
 * brightness, Do Not Disturb, ringer mode, Wi-Fi, Bluetooth.
 *
 * Where modern Android blocks direct control (Wi-Fi since API 29, Bluetooth since 33)
 * we open the system panel instead of pretending it worked.
 */
object DeviceSettingTools {

    private const val TAG = "DeviceSettingTools"

    fun registerAll() {
        registerBrightness()
        registerDnd()
        registerRingerMode()
        registerWifi()
        registerBluetooth()
    }

    private fun registerBrightness() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_brightness",
                name = "Set Screen Brightness",
                description = "Sets screen brightness to a percentage (0-100), or enables auto brightness.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val auto = args["auto"]?.toString()?.toBoolean() ?: false
                val percent = args["percent"]?.toString()?.toIntOrNull()
                    ?: args["level"]?.toString()?.toIntOrNull()
                    ?: args["value"]?.toString()?.toIntOrNull()

                if (!Settings.System.canWrite(context)) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "set_brightness",
                        success = false,
                        data = mapOf("missingPermission" to Manifest.permission.WRITE_SETTINGS),
                        error = "I need permission to change system settings before I can dim the screen. " +
                            "Open Settings, allow \"Modify system settings\" for JARVIS, then ask again."
                    )
                }

                return@ToolDefinition try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    if (!auto) {
                        val value = (percent ?: 50).coerceIn(0, 100)
                        val scaled = (value / 100f * 255f).toInt().coerceIn(0, 255)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, scaled)
                        ToolExecutionResult(
                            toolId = "set_brightness",
                            success = true,
                            data = mapOf("percent" to value),
                            verificationDetails = "Brightness set to $value%."
                        )
                    } else {
                        ToolExecutionResult(
                            toolId = "set_brightness",
                            success = true,
                            data = mapOf("auto" to true),
                            verificationDetails = "Automatic brightness turned on."
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "brightness failed", e)
                    ToolExecutionResult(
                        toolId = "set_brightness",
                        success = false,
                        data = null,
                        error = "I couldn't change the brightness on this phone: ${e.localizedMessage}"
                    )
                }
            }
        )
    }

    private fun registerDnd() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_dnd",
                name = "Do Not Disturb",
                description = "Turns Do Not Disturb (total silence) on or off.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val on = args["on"]?.toString()?.toBoolean()
                    ?: args["enabled"]?.toString()?.toBoolean()
                    ?: true

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    ?: return@ToolDefinition simpleError("set_dnd", "Notifications aren't available on this device.")

                if (!nm.isNotificationPolicyAccessGranted) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "set_dnd",
                        success = false,
                        data = mapOf("missingAccess" to "notification_policy"),
                        error = "I need Do Not Disturb access. Open Settings, give JARVIS notification access, then try again."
                    )
                }

                return@ToolDefinition try {
                    nm.setInterruptionFilter(
                        if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                        else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                    ToolExecutionResult(
                        toolId = "set_dnd",
                        success = true,
                        data = mapOf("on" to on),
                        verificationDetails = if (on) "Do Not Disturb is on." else "Do Not Disturb is off."
                    )
                } catch (e: SecurityException) {
                    ToolExecutionResult(
                        toolId = "set_dnd",
                        success = false,
                        data = null,
                        error = "This phone won't let me control Do Not Disturb until you grant notification access."
                    )
                } catch (e: Exception) {
                    simpleError("set_dnd", "I couldn't change Do Not Disturb: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerRingerMode() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_ringer_mode",
                name = "Set Ringer Mode",
                description = "Puts the phone in silent, vibrate or normal ring mode.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val mode = (args["mode"] ?: args["state"])?.toString()?.lowercase() ?: "normal"
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    ?: return@ToolDefinition simpleError("set_ringer_mode", "Audio isn't available.")

                val ringerMode = when {
                    mode.contains("silent") || mode.contains("mute") -> AudioManager.RINGER_MODE_SILENT
                    mode.contains("vibrat") -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_NORMAL
                }

                return@ToolDefinition try {
                    am.ringerMode = ringerMode
                    val spoken = when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Phone is silent."
                        AudioManager.RINGER_MODE_VIBRATE -> "Phone is on vibrate."
                        else -> "Phone is ringing normally."
                    }
                    ToolExecutionResult(
                        toolId = "set_ringer_mode",
                        success = true,
                        data = mapOf("mode" to mode),
                        verificationDetails = spoken
                    )
                } catch (e: SecurityException) {
                    ToolExecutionResult(
                        toolId = "set_ringer_mode",
                        success = false,
                        data = null,
                        error = "I need Do Not Disturb access to change the ringer on this Android version."
                    )
                } catch (e: Exception) {
                    simpleError("set_ringer_mode", "I couldn't change the ringer: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerWifi() {
        ToolRegistry.register(
            ToolDefinition(
                id = "toggle_wifi",
                name = "Turn Wi-Fi On or Off",
                description = "Turns Wi-Fi on or off. On Android 10+ this opens the system Wi-Fi panel, because apps are no longer allowed to toggle Wi-Fi directly.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val on = args["on"]?.toString()?.toBoolean()
                    ?: args["enabled"]?.toString()?.toBoolean()
                    ?: true

                // Android 10 (API 29) removed third-party Wi-Fi toggling.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return@ToolDefinition openPanel(
                        context, Settings.Panel.ACTION_WIFI, "toggle_wifi",
                        "Android won't let apps switch Wi-Fi directly — I've opened the Wi-Fi panel for you."
                    )
                }

                return@ToolDefinition try {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        ?: return@ToolDefinition simpleError("toggle_wifi", "Wi-Fi isn't available on this device.")
                    @Suppress("DEPRECATION")
                    val applied = wm.setWifiEnabled(on)
                    if (applied) {
                        ToolExecutionResult(
                            toolId = "toggle_wifi",
                            success = true,
                            data = mapOf("on" to on),
                            verificationDetails = if (on) "Wi-Fi is on." else "Wi-Fi is off."
                        )
                    } else {
                        openPanel(
                            context, Settings.Panel.ACTION_WIFI, "toggle_wifi",
                            "I couldn't switch Wi-Fi directly — I've opened the Wi-Fi panel for you."
                        )
                    }
                } catch (e: Exception) {
                    simpleError("toggle_wifi", "I couldn't change Wi-Fi: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun registerBluetooth() {
        ToolRegistry.register(
            ToolDefinition(
                id = "toggle_bluetooth",
                name = "Turn Bluetooth On or Off",
                description = "Turns Bluetooth on or off. On Android 13+ this opens settings, because apps can no longer toggle Bluetooth directly.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val on = args["on"]?.toString()?.toBoolean()
                    ?: args["enabled"]?.toString()?.toBoolean()
                    ?: true

                // Android 13 (API 33) blocks BluetoothAdapter.enable() for third-party apps.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return@ToolDefinition try {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        ToolExecutionResult(
                            toolId = "toggle_bluetooth",
                            success = true,
                            data = mapOf("openedSettings" to true),
                            verificationDetails = "Android won't let apps switch Bluetooth directly — I've opened Bluetooth settings for you."
                        )
                    } catch (e: Exception) {
                        simpleError("toggle_bluetooth", "I couldn't open Bluetooth settings: ${e.localizedMessage}")
                    }
                }

                val missing = android.Manifest.permission.BLUETOOTH_ADMIN
                if (ContextCompat.checkSelfPermission(context, missing) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return@ToolDefinition simpleError(
                        "toggle_bluetooth",
                        "I need the Bluetooth permission to do that."
                    )
                }

                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: return@ToolDefinition simpleError("toggle_bluetooth", "This device has no Bluetooth.")

                return@ToolDefinition try {
                    val applied = if (on) adapter.enable() else adapter.disable()
                    ToolExecutionResult(
                        toolId = "toggle_bluetooth",
                        success = applied,
                        data = mapOf("on" to on),
                        verificationDetails = if (applied) {
                            if (on) "Bluetooth is on." else "Bluetooth is off."
                        } else null,
                        error = if (applied) null else "I couldn't switch Bluetooth on this phone."
                    )
                } catch (e: SecurityException) {
                    simpleError("toggle_bluetooth", "This Android version won't let me switch Bluetooth.")
                } catch (e: Exception) {
                    simpleError("toggle_bluetooth", "Bluetooth failed: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun openPanel(
        context: Context,
        action: String,
        toolId: String,
        message: String
    ): ToolExecutionResult = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult(
                toolId = toolId,
                success = true,
                data = mapOf("openedPanel" to true),
                verificationDetails = message
            )
        } else {
            context.startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolExecutionResult(
                toolId = toolId,
                success = true,
                data = mapOf("openedPanel" to true),
                verificationDetails = message
            )
        }
    } catch (e: Exception) {
        simpleError(toolId, "I couldn't open the system panel: ${e.localizedMessage}")
    }

    private fun simpleError(toolId: String, message: String) =
        ToolExecutionResult(toolId = toolId, success = false, data = null, error = message)
}
