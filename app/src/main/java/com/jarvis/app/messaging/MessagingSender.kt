package com.jarvis.app.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.app.notifications.JarvisNotificationListener
import com.jarvis.app.tools.DeviceToolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends replies to messaging apps.
 *
 * Order of attack:
 *   1. Notification RemoteInput reply (fast, official) — works for apps that expose it.
 *   2. Open the app and drive it with Accessibility (read screen, type, click Send).
 *   3. SMS fallback / deep-link draft.
 */
class MessagingSender(private val context: Context) {

    private val tools = DeviceToolkit(context)
    private val pkgMap = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "messages" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "x" to "com.twitter.android",
        "twitter" to "com.twitter.android"
    )

    suspend fun sendReply(target: String, body: String): String = withContext(Dispatchers.IO) {
        val key = target.lowercase()
        val pkg = pkgMap.entries.firstOrNull { key.contains(it.key) }?.value

        // 1) Notification reply
        if (pkg != null && tryNotificationReply(pkg, body)) {
            return@withContext "Sent via notification reply."
        }

        // 2) Accessibility drive
        if (pkg != null && JarvisAccessibilityService.instance != null && tryAccessibilitySend(pkg, body)) {
            return@withContext "Sent by typing in $target."
        }

        // 3) Deep-link / SMS draft
        if (pkg == "com.google.android.apps.messaging" || key.contains("sms")) {
            tools.openSmsApp(null, body)
            return@withContext "Opened SMS composer with your message. Tap send there."
        }

        // 4) Open the app with pre-filled text via share/draft intent
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
                `package` = pkg
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened $target with your message ready. You still need to tap send there."
        } catch (_: Exception) {
            "Couldn't auto-send in $target. Enable JARVIS in Accessibility settings, then try again."
        }
    }

    private fun tryNotificationReply(pkg: String, body: String): Boolean =
        JarvisNotificationListener.replyViaNotification(pkg, body)

    private suspend fun tryAccessibilitySend(pkg: String, body: String): Boolean {
        val svc = JarvisAccessibilityService.instance ?: return true
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch == null) return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            delay(1400)

            // Type into the message input field, then tap Send.
            val typed = svc.setTextInField("", body) || svc.setTextInField("Message", body)
            delay(500)
            val sent = svc.clickText("Send") || svc.clickText("SEND") || svc.clickText("➤")
            return typed && sent
        } catch (_: Exception) {
            return false
        }
    }
}
