package com.jarvis.app.assist

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Makes JARVIS the phone's default digital assistant (Siri/Bixby parity).
 *
 * Once held, the home gesture, the headset button and the "digital assistant app" slot all
 * route to JARVIS instead of Google. Android 8 (API 26) introduced the assistant role, and
 * our minSdk is 26, so this works on every device we target.
 */
object AssistantRoleManager {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isDefault(context: Context): Boolean {
        if (!isSupported()) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    /** Shows the system "Change default assistant" dialog. */
    fun request(context: Context): Boolean {
        if (!isSupported()) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        if (!rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return false
        return try {
            context.startActivity(
                rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Some skins (MIUI, older One UI) hide the role dialog but still ship a settings page. */
    fun openSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                continue
            }
        }
    }

    fun statusText(context: Context): String = when {
        !isSupported() -> "Needs Android 8 or newer."
        isDefault(context) -> "JARVIS is your default assistant."
        else -> "Not set — long-press home currently opens another assistant."
    }
}
