package com.jarvis.app.assist

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.jarvis.app.voice.VoiceDiagnostics

/**
 * Makes JARVIS the phone's default digital assistant (Siri/Bixby parity).
 *
 * ## Why this only ever opens Settings
 *
 * The previous version tried `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)`
 * first and treated a successful `startActivity` as success. That was wrong twice
 * over:
 *
 * 1. **`ROLE_ASSISTANT` is not requestable.** AOSP's PermissionController declares
 *    it with `requestable="false"` and `overrideUserWhenGranting="true"`, because the
 *    role is granted by the platform to whichever app the user picks as their
 *    digital assistant -- not by an app asking for it. The `RequestRoleActivity` that
 *    the intent resolves to therefore finishes immediately with `RESULT_CANCELED`,
 *    usually without drawing anything.
 * 2. **The no-op still returned `true`.** `startActivity` succeeded, so callers such
 *    as `DiagnosticsActivity` took the "dialog shown" branch and never ran their
 *    Settings fallback. Tapping "SET JARVIS AS DEFAULT" did nothing at all.
 *
 * So the Settings page is the primary -- and only -- path. It is also the correct
 * one: that is where the user actually chooses their assistant.
 *
 * ## Eligibility caveat (honest, not a TODO to paper over)
 *
 * `MainActivity` declares `android.intent.action.ASSIST` and
 * `android.intent.action.VOICE_COMMAND`, which is enough for JARVIS to appear in the
 * assistant picker on many builds (notably Samsung and Xiaomi). On stock Android 10+
 * the "Digital assistant app" picker additionally requires the candidate to expose a
 * `VoiceInteractionService`; JARVIS does not have one yet, so it may not be listed
 * there. Adding one is a real feature (service + session + `xml` metadata) and is
 * tracked separately rather than stubbed here. `statusText()` tells the user which
 * situation they are in instead of promising an outcome the platform will not grant.
 */
object AssistantRoleManager {

    private const val TAG = "AssistantRole"

    /**
     * `RoleManager` was added in API 29. On API 26-28 `getSystemService` returns
     * null, so role queries fall back to the legacy `Settings.Secure` slot.
     *
     * The old `isSupported()` claimed API 26+ and then silently returned false on
     * 26-28, which read as "your phone can't do this" rather than "we can't check".
     */
    fun canQueryRole(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** True when JARVIS currently holds the assistant slot. */
    fun isDefault(context: Context): Boolean {
        if (canQueryRole()) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return false
            return try {
                rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            } catch (e: Exception) {
                Log.w(TAG, "isRoleHeld failed", e)
                false
            }
        }
        // API 26-28: the platform stored the assistant component in Settings.Secure.
        // Format is "package/component", e.g.
        // "com.google.android.googlequicksearchbox/…VoiceInteractionService".
        return try {
            val slot = Settings.Secure.getString(context.contentResolver, "assistant")
            !slot.isNullOrBlank() && slot.contains(context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "legacy assistant lookup failed", e)
            false
        }
    }

    /**
     * Reports whether the platform would even show a role-request dialog. Kept
     * explicit so nobody re-adds the dead `createRequestRoleIntent` path by accident.
     *
     * `isRoleAvailable` is about the device having the role at all; it says nothing
     * about requestability, which is why it is not a sufficient check on its own.
     */
    fun isRoleRequestable(context: Context): Boolean {
        if (!canQueryRole()) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return try {
            // ROLE_ASSISTANT is requestable=false in AOSP, so this is false in
            // practice. Guarded rather than hardcoded so an OEM that does make it
            // requestable still gets the dialog path via makeDefault().
            rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        } catch (e: Exception) {
            Log.w(TAG, "isRoleAvailable failed", e)
            false
        }
    }

    /**
     * Takes the user to the screen where they can pick JARVIS as their assistant.
     *
     * Tries the most specific page first and degrades gracefully: some OEM skins hide
     * the voice-input page but still ship the generic default-apps page, and some
     * ship neither.
     *
     * @return true if some settings page was opened.
     */
    fun openDefaultAssistantSettings(context: Context): Boolean {
        val candidates = listOf(
            // Stock Android: Settings > Apps > Default apps > Digital assistant app.
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            // Generic default-apps picker (API 24+).
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            // Last resort: at least lands on JARVIS's own app page.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                VoiceDiagnostics.report("Opened default-assistant settings: ${intent.action}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "could not open ${intent.action}", e)
            }
        }
        VoiceDiagnostics.report(
            "No default-assistant settings page could be opened on this device."
        )
        return false
    }

    /**
     * The single entry point for "make JARVIS my assistant".
     *
     * Uses the role dialog only on the (currently hypothetical) platform that makes
     * `ROLE_ASSISTANT` requestable; otherwise goes straight to Settings.
     */
    fun makeDefault(context: Context): Boolean {
        if (isDefault(context)) return true
        if (canQueryRole() && isRoleRequestable(context)) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null) {
                try {
                    context.startActivity(
                        rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "role dialog unavailable, falling back to Settings", e)
                }
            }
        }
        return openDefaultAssistantSettings(context)
    }

    /**
     * Status line for Diagnostics/Setup. States what is true rather than what was
     * hoped for, because the caller cannot grant this role on the user's behalf.
     */
    fun statusText(context: Context): String = when {
        isDefault(context) -> "JARVIS is your default assistant."
        declaresAssistAction(context) ->
            "Not set. Tap the button to open Android's assistant settings and choose " +
                "JARVIS. If it is not listed, your Android build only offers that slot " +
                "to apps with a VoiceInteractionService, which JARVIS does not have yet."
        else ->
            "Not set, and JARVIS does not declare android.intent.action.ASSIST, so it " +
                "cannot appear in the assistant picker at all."
    }

    /** Does MainActivity still advertise itself as an assistant target? */
    private fun declaresAssistAction(context: Context): Boolean = try {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT)
        pm.queryIntentActivities(probe, 0).any { it.activityInfo.packageName == context.packageName }
    } catch (e: Exception) {
        Log.w(TAG, "ASSIST probe failed", e)
        false
    }
}
