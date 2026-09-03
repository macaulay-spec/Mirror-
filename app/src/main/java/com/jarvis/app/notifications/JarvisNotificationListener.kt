package com.jarvis.app.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.agent.tool.ToolDefinition
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.NotificationEntity
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        @Volatile var instance: JarvisNotificationListener? = null
            private set

        /**
         * Best effort reply through a notification's RemoteInput action.
         * [key] targets the EXACT conversation; when null/blank the most recent
         * notification of [packageName] with a reply action is used.
         */
        fun replyViaNotification(packageName: String, replyText: String, key: String? = null): Boolean {
            val inst = instance ?: return false
            return inst.sendReply(packageName, replyText, key)
        }

        fun dismissNotification(key: String): Boolean {
            val inst = instance ?: return false
            return try {
                inst.cancelNotification(key)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun registerTools() {
            // read_notifications tool
            ToolRegistry.register(
                ToolDefinition(
                    id = "read_notifications",
                    name = "Read Notifications",
                    description = "Reads incoming notifications from apps like WhatsApp, SMS, Telegram, Gmail, etc.",
                    category = "NOTIFICATIONS",
                    riskLevel = RiskLevel.LEVEL_0
                ) { context, args ->
                    val filterApp = args["app"]?.toString() ?: args["package"]?.toString() ?: ""
                    val limit = (args["limit"]?.toString()?.toIntOrNull()) ?: 10

                    val activeList = if (filterApp.isNotBlank()) {
                        NotificationRepository.byApp(filterApp)
                    } else {
                        NotificationRepository.all.value
                    }

                    if (activeList.isEmpty()) {
                        ToolExecutionResult(
                            toolId = "read_notifications",
                            success = true,
                            data = mapOf("count" to 0, "notifications" to emptyList<Map<String, Any>>()),
                            verificationDetails = if (filterApp.isNotBlank()) "No active notifications found for $filterApp." else "No active notifications present on device."
                        )
                    } else {
                        val items = activeList.take(limit).map {
                            mapOf(
                                "app" to it.appLabel,
                                "package" to it.packageName,
                                "sender" to it.sender,
                                "title" to it.title,
                                "content" to (if (it.fullContent.isNotBlank()) it.fullContent else it.text),
                                // CHANGED (production repair): expose the notification
                                // key so reply_notification can target the EXACT
                                // conversation instead of guessing by package.
                                "key" to it.key,
                                "hasReplyAction" to it.hasReplyAction,
                                "timestamp" to it.timestamp
                            )
                        }
                        ToolExecutionResult(
                            toolId = "read_notifications",
                            success = true,
                            data = mapOf("count" to items.size, "notifications" to items),
                            verificationDetails = "Retrieved ${items.size} notifications."
                        )
                    }
                }
            )

            // read_otp tool — "what's my code" is the question that matters most
            ToolRegistry.register(
                ToolDefinition(
                    id = "read_otp",
                    name = "Read One-Time Code",
                    description = "Finds the verification or one-time code in recent notifications and SMS.",
                    category = "NOTIFICATIONS",
                    riskLevel = RiskLevel.LEVEL_0
                ) { _, args ->
                    if (!AssistantPrefs.readOtpAloud) {
                        return@ToolDefinition ToolExecutionResult(
                            toolId = "read_otp",
                            success = false,
                            data = null,
                            error = "Reading codes aloud is switched off in Settings."
                        )
                    }

                    val only = args["app"]?.toString() ?: args["package"]?.toString() ?: ""
                    val pool = if (only.isBlank()) NotificationRepository.all.value
                    else NotificationRepository.byApp(only)

                    val hit = pool.asSequence()
                        .mapNotNull { n ->
                            val body = if (n.fullContent.isNotBlank()) n.fullContent else n.text
                            val code = OtpExtractor.find("$body ${n.title}") ?: return@mapNotNull null
                            Triple(n, code, body)
                        }
                        .firstOrNull()

                    if (hit == null) {
                        ToolExecutionResult(
                            toolId = "read_otp",
                            success = true,
                            data = mapOf("found" to false),
                            verificationDetails = "I cannot see a verification code in your notifications."
                        )
                    } else {
                        val (n, code, _) = hit
                        ToolExecutionResult(
                            toolId = "read_otp",
                            success = true,
                            data = mapOf(
                                "found" to true,
                                "code" to code,
                                "app" to n.appLabel,
                                "sender" to n.sender
                            ),
                            verificationDetails = OtpExtractor.spoken(code, n.appLabel)
                        )
                    }
                }
            )

            // reply_notification tool
            ToolRegistry.register(
                ToolDefinition(
                    id = "reply_notification",
                    name = "Reply to Notification",
                    description = "Sends an inline reply to a notification (e.g. WhatsApp, SMS, Telegram) using RemoteInput. " +
                        "Pass the notification's 'key' (from read_notifications) to reply in the exact conversation; " +
                        "otherwise the most recent conversation of that app is used.",
                    category = "MESSAGING",
                    // CHANGED (production repair): sending a message on someone's behalf
                    // is communication — it must go through the same confirm flow as
                    // calls/SMS instead of firing the moment the model picks it.
                    riskLevel = RiskLevel.LEVEL_2
                ) { _, args ->
                    val pkg = args["package"]?.toString() ?: args["app"]?.toString() ?: ""
                    val message = args["message"]?.toString() ?: args["text"]?.toString() ?: ""
                    val key = args["key"]?.toString()?.takeIf { it.isNotBlank() }

                    if (message.isBlank()) {
                        return@ToolDefinition ToolExecutionResult(
                            toolId = "reply_notification",
                            success = false,
                            data = null,
                            error = "Reply message text cannot be blank."
                        )
                    }

                    // Resolve package if user supplied app label (e.g. 'whatsapp')
                    val resolvedPkg = if (!pkg.contains(".")) {
                        val notif = NotificationRepository.byApp(pkg).firstOrNull()
                        notif?.packageName ?: when (pkg.lowercase()) {
                            "whatsapp" -> "com.whatsapp"
                            "telegram" -> "org.telegram.messenger"
                            "messages", "sms" -> "com.google.android.apps.messaging"
                            else -> pkg
                        }
                    } else {
                        pkg
                    }

                    val success = replyViaNotification(resolvedPkg, message, key)
                    ToolExecutionResult(
                        toolId = "reply_notification",
                        success = success,
                        data = mapOf("package" to resolvedPkg, "message" to message),
                        verificationDetails = if (success) "Sent reply '$message' to $resolvedPkg."
                        else "Could not send inline reply. The app's notification may not support RemoteInput, or no conversation notification is active."
                    )
                }
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
        if (sbn != null) {
            persistNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onListenerDisconnected() {
        // FIX (production repair): Android drops the listener binding on crash,
        // Doze, or battery-saver pressure — previously it silently died here and
        // every notification feature stopped working in the background. Ask for
        // an immediate rebind instead.
        if (instance === this) instance = null
        try {
            requestRebind(ComponentName(this, JarvisNotificationListener::class.java))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * This used to redact anything containing "otp", "verification code", or any 4-8 digit
     * number — which quietly broke the one notification feature people actually asked for:
     * "read my code". Reading codes aloud is the point, so redaction now only happens when
     * the user has switched on Hide Sensitive Content, and even then one-time codes are
     * spared because they are useless when hidden.
     */
    private fun hideContent(text: String): Boolean {
        if (!AssistantPrefs.hideSensitiveContent) return false
        if (OtpExtractor.find(text) != null) return false
        val lower = text.lowercase()
        return lower.contains("password") || lower.contains("passcode") ||
            lower.contains("cvv") || lower.contains("pin is")
    }

    private fun persistNotification(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: title
        
        if (text.isBlank() && title.isBlank()) return
        if (hideContent(text)) text = "[HIDDEN - sensitive content]"
        if (hideContent(title)) title = "[HIDDEN - sensitive content]"

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        // MessagingStyle multi-message extraction
        val fullContent = extractFullMessageContent(extras, title, text)
        val hasReply = findReplyActionForSbn(sbn) != null

        serviceScope.launch {
            try {
                val db = AppDatabase.get(applicationContext)
                db.notificationDao().insert(
                    NotificationEntity(
                        packageName = sbn.packageName,
                        appLabel = appLabel,
                        title = title,
                        text = text,
                        fullContent = fullContent,
                        sbnKey = sbn.key,
                        timestamp = sbn.postTime,
                        hasReplyAction = hasReply,
                        isRead = false
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun extractFullMessageContent(extras: Bundle, title: String, text: String): String {
        try {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                val sb = StringBuilder()
                for (item in messages) {
                    if (item is Bundle) {
                        val sender = item.getCharSequence("sender")?.toString() ?: ""
                        val msgText = item.getCharSequence("text")?.toString() ?: ""
                        if (msgText.isNotBlank()) {
                            if (sender.isNotBlank()) sb.append("$sender: ")
                            sb.append(msgText).append("\n")
                        }
                    }
                }
                if (sb.isNotBlank()) return sb.toString().trim()
            }
        } catch (_: Exception) {}

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText
        return if (title.isNotBlank()) "$title: $text" else text
    }

    private fun refresh() {
        try {
            val sbns = activeNotifications ?: return
            val apps = packageManager.getInstalledApplications(0)
            val items = sbns.mapNotNull { sbn ->
                val notif = sbn.notification ?: return@mapNotNull null
                val extras = notif.extras ?: return@mapNotNull null
                
                var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: ""
                var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val sender = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: title
                
                if (text.isBlank() && title.isBlank()) return@mapNotNull null
                
                if (hideContent(text)) text = "[HIDDEN - sensitive content]"
                if (hideContent(title)) title = "[HIDDEN - sensitive content]"
                
                val label = apps.firstOrNull { it.packageName == sbn.packageName }?.loadLabel(packageManager)?.toString()
                    ?: sbn.packageName
                    
                val fullContent = extractFullMessageContent(extras, title, text)
                val hasReply = findReplyActionForSbn(sbn) != null
                    
                JarvisNotification(
                    packageName = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    sender = sender,
                    fullContent = fullContent,
                    key = sbn.key,
                    timestamp = sbn.postTime,
                    hasReplyAction = hasReply
                )
            }
            NotificationRepository.updateActive(items)
        } catch (_: Exception) { }
    }

    private fun sendReply(packageName: String, replyText: String, key: String? = null): Boolean {
        return try {
            val action = findReplyAction(packageName, key) ?: return false
            val inputs = action.getRemoteInputs() ?: return false
            val results = Bundle()
            results.putCharSequence(inputs.first().resultKey, replyText)
            val intent = android.content.Intent()
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(this, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * FIX (production repair): replies used to hit the FIRST notification of the
     * package — with two WhatsApp chats active the reply could land in the wrong
     * conversation. Now: exact notification key first, then the most recent
     * notification of the package that actually supports inline reply.
     */
    private fun findReplyAction(packageName: String, key: String? = null): Notification.Action? {
        val sbns = activeNotifications ?: return null

        if (!key.isNullOrBlank()) {
            sbns.firstOrNull { it.key == key }
                ?.let { sbn -> findReplyActionForSbn(sbn)?.let { return it } }
        }

        return sbns.asSequence()
            .filter { it.packageName == packageName }
            .maxByOrNull { it.postTime }
            ?.let { findReplyActionForSbn(it) }
    }

    private fun findReplyActionForSbn(sbn: StatusBarNotification): Notification.Action? {
        sbn.notification.actions?.forEach { action ->
            val inputs = action.getRemoteInputs()
            if (inputs != null && inputs.isNotEmpty()) return action
        }
        return null
    }
}
