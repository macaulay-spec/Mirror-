package com.jarvis.agent.tool

import android.content.Context
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.android.device.DeviceToolExecutors
import com.jarvis.core.model.RiskLevel

/**
 * Registers all core Android subsystem tools with the unified ToolRegistry.
 */
object ToolRegistration {

    @Suppress("UNUSED_PARAMETER")
    fun registerAll(context: Context) {
        // Register device controls & hardware tools
        DeviceToolExecutors.registerAll()

        // Register accessibility tools
        JarvisAccessibilityService.registerTools()

        // Register notification tools
        com.jarvis.app.notifications.JarvisNotificationListener.registerTools()

        // Calls, SMS, contacts, call log
        PhoneTools.registerAll()

        // Brightness, Do Not Disturb, ringer, Wi-Fi, Bluetooth
        DeviceSettingTools.registerAll()

        // Events, reminders, alarms, navigation, clipboard, screenshots
        LifeTools.registerAll()

        // The switches JARVIS controls about itself, reachable by voice
        ProactiveTools.registerAll()
    }
}
