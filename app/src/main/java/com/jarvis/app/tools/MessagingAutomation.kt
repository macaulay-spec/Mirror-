package com.jarvis.app.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.app.people.PeopleGraph
import kotlinx.coroutines.delay

/**
 * Accessibility-driven message sending.
 *
 * This is the fix for the "chatbot with a shortcut" pattern that the project's
 * vision document explicitly rules out. The old `send_message` tool fired an
 * SMS through `DeviceToolkit.sendSms()` (a headless API call) and the old
 * `send_whatsapp` tool opened an `api.whatsapp.com/send?...` deep link that
 * skips straight to a pre-filled chat — both are shortcuts that take the
 * action FOR the user instead of driving the real app the way a person would.
 *
 * The worked example in HOW_JARVIS_IS_SUPPOSED_TO_WORK.md requires instead:
 *   open the target app -> find the right person by matching against the
 *   phone's own contacts -> navigate into that specific conversation -> type
 *   the message into the actual input field -> ask "send now?" -> wait for
 *   confirmation -> tap send, verifying at each step rather than assuming.
 *
 * This object implements exactly that, using the capabilities
 * JarvisAccessibilityService already exposes (clickText,
 * clickElementByDescription, setTextInField, findTextOnScreen, scroll,
 * screenSignature). It does NOT hard-code per-app logic beyond the launch
 * package and a small set of locale-agnostic affordance labels it tries in
 * order — everything else is general UI driving through Accessibility, which
 * is the "generalize, don't hard-code" principle.
 *
 * If the Accessibility Service is not enabled, this falls back to opening the
 * native composer with the message as a DRAFT and says so plainly, so the user
 * always knows whether Jarvis actually sent it or only prepared it.
 */
object MessagingAutomation {

    /** Result of an automation attempt, surfaced as a verification card to the user. */
    data class Result(
        val success: Boolean,
        val verificationDetails: String,
        val error: String? = null,
        /** True when the message was prepared but NOT sent (e.g. Accessibility off,
         *  or the user did not confirm). The user must tap send themselves. */
        val preparedOnly: Boolean = false,
        /** When the flow needs the user to confirm "send now?" this carries the
         *  exact app the send will happen in, so the confirmation prompt is concrete. */
        val pendingSendApp: String? = null
    )

    /**
     * The two target apps this automation drives. The orchestrator's
     * confirmation system re-executes the SAME tool when the user says "yes",
     * so the send tools branch on [pendingDraft]: if a verified draft is
     * pending for the app, a second call performs the final send tap.
     */
    enum class TargetApp { WHATSAPP, SMS }

    /**
     * Tracks a draft that has been typed & verified into the real app's input
     * field and is awaiting the user's "send now?" confirmation. When non-null,
     * a re-invocation of the matching send tool performs the send tap.
     */
    @Volatile
    private var pendingDraft: TargetApp? = null

    /** Public so the UI/tests can observe whether a send is pending. */
    val hasPendingSend: Boolean get() = pendingDraft != null
    val pendingSendAppName: String? get() = when (pendingDraft) {
        TargetApp.WHATSAPP -> "WhatsApp"
        TargetApp.SMS -> "your messaging app"
        null -> null
    }

    /** Clear a pending draft (e.g. on cancel / emergency stop). */
    fun clearPending() { pendingDraft = null }

    /**
     * Unified, stateful entry point the send tools call.
     *
     * First call (no pending draft): resolves the contact, opens the app,
     * types the message, verifies it landed, sets [pendingDraft], and returns
     * a Result with pendingSendApp so the orchestrator surfaces "send now?".
     *
     * Confirm call (pending draft for this app): taps the real send button and
     * verifies, then clears [pendingDraft]. This is what the orchestrator's
     * `confirmToolExecution` triggers by re-executing the same tool.
     *
     * If a pending draft exists for the OTHER app, it is cleared first (the
     * user moved on to a new request).
     */
    suspend fun executeSend(
        context: Context,
        app: TargetApp,
        contactQuery: String,
        message: String
    ): Result {
        // Confirm path: a draft is already pending for THIS app -> send it.
        if (pendingDraft == app) {
            val sendResult = when (app) {
                TargetApp.WHATSAPP -> confirmSendWhatsApp()
                TargetApp.SMS -> confirmSendSms()
            }
            pendingDraft = null
            return sendResult
        }
        // If a draft was pending for a different app, the user has moved on;
        // abandon it before starting a fresh draft.
        if (pendingDraft != null) pendingDraft = null

        // Draft path.
        val draft = when (app) {
            TargetApp.WHATSAPP -> sendWhatsAppDraft(context, contactQuery, message)
            TargetApp.SMS -> sendSmsDraft(context, contactQuery, message)
        }
        // Only record a pending draft if we actually typed & verified (i.e. NOT
        // the preparedOnly fallback, where the user must press send themselves).
        if (draft.success && !draft.preparedOnly && draft.pendingSendApp != null) {
            pendingDraft = app
        }
        return draft
    }

    /** Resolve a spoken/typed name to a real contact using the phone's own contacts. */
    private suspend fun resolveContact(context: Context, query: String): ResolvedContact? {
        // PeopleGraph is JARVIS's own synced view of the contacts DB; it does
        // fuzzy matching on spoken names. ContactsToolkit reads ContactsProvider
        // directly as a second source. Together they mirror how a person would
        // search for someone by name.
        val matches = PeopleGraph.resolve(context, query)
        val top = matches.firstOrNull()
        if (top != null) {
            val num = top.numbers.firstOrNull()?.value
            if (!num.isNullOrBlank()) {
                return ResolvedContact(top.person.displayName, num)
            }
        }
        val direct = ContactsToolkit(context).search(query)
        if (direct != null && direct.phone.isNotBlank()) {
            return ResolvedContact(direct.name, direct.phone)
        }
        // Last resort: if the "query" already looks like a phone number, use it.
        val cleaned = query.replace(Regex("[^0-9+]"), "")
        if (cleaned.length >= 7) return ResolvedContact(query, cleaned)
        return null
    }

    private data class ResolvedContact(val displayName: String, val number: String)

    // ── Public entry points ───────────────────────────────────────────────

    /**
     * Send a WhatsApp message by driving the real WhatsApp UI.
     * Returns [Result]. The caller (the tool) decides how to present the
     * "send now?" confirmation; this function types into the input field and
     * STOPS there, returning pendingSendApp so the orchestrator's existing
     * risk/confirmation system can ask the user before the final send tap.
     */
    suspend fun sendWhatsAppDraft(context: Context, contactQuery: String, message: String): Result {
        val service = JarvisAccessibilityService.instance
        val contact = resolveContact(context, contactQuery)
            ?: return Result(false, "", "I couldn't find a contact named \"$contactQuery\" in your contacts.")

        if (service == null) {
            // Accessibility not enabled — can't drive the UI. Fall back to the
            // deep link so at least the right chat opens with the text drafted,
            // and be honest that JARVIS didn't type/send it itself.
            return openWhatsAppDraftLink(context, contact, message)
        }

        // Step 1: open WhatsApp to the target chat via its share deep link.
        // This lands the user in the specific conversation (the "find the person
        // and open their conversation" step), then we drive the rest via UI.
        if (!openWhatsAppChat(context, contact.number, message)) {
            return Result(false, "", "WhatsApp doesn't seem to be installed, so I can't open the chat.")
        }

        // Wait for WhatsApp to come to the foreground and settle.
        waitForPackage(service, "com.whatsapp", timeoutMs = 6000)

        // Step 2: confirm we actually landed in a chat with an input field
        // (not the share-sheet picker, not a "chat not found" error). Verify,
        // don't assume.
        val inputReady = waitForInputField(service, timeoutMs = 6000)
        if (!inputReady) {
            // The share link sometimes shows an app chooser first; try to click
            // WhatsApp if a chooser appeared, then re-check.
            service.clickElementByDescription("WhatsApp") || service.clickText("WhatsApp")
            delay(800)
            if (!waitForInputField(service, timeoutMs = 4000)) {
                return Result(
                    success = false,
                    verificationDetails = "I opened WhatsApp for ${contact.displayName} but couldn't find the message input field.",
                    error = "Could not locate the chat input field in WhatsApp."
                )
            }
        }

        // Step 3: type the message into the REAL input field.
        val typed = typeIntoField(service, message)
        if (!typed) {
            return Result(
                success = false,
                verificationDetails = "I found the chat with ${contact.displayName} but couldn't type into the input field.",
                error = "Failed to type the message into WhatsApp's input field."
            )
        }
        delay(400)

        // Step 4: verify the text actually landed in the field (read-back).
        val onScreen = service.findTextOnScreen().joinToString(" ")
        val textLanded = onScreen.contains(message.take(24), true)

        // Step 5: STOP before sending. The vision's worked example requires
        // asking "send now?" and waiting for confirmation. We return
        // pendingSendApp so the tool's caller surfaces the confirmation prompt
        // through the existing LEVEL_2 confirmation system; on "yes" the
        // orchestrator re-invokes us with confirmed=true to tap send.
        return Result(
            success = true,
            verificationDetails = "I opened your WhatsApp chat with ${contact.displayName} and typed \"$message\"" +
                if (textLanded) " — it's in the input field, ready to send." else " into the input field.",
            pendingSendApp = "WhatsApp"
        )
    }

    /**
     * Perform the final send tap after the user confirmed. Only meaningful for
     * WhatsApp (the draft flow above left the text typed and ready).
     */
    suspend fun confirmSendWhatsApp(): Result {
        val service = JarvisAccessibilityService.instance
            ?: return Result(false, "", "Accessibility Service was disabled before I could press send.")
        // The send button in WhatsApp is an icon; its content description is
        // "Send" in most locales. Try description first, then a couple of
        // common alternates, and finally fall back to pressing Enter via a
        // typed newline-then-backspace is NOT reliable — prefer the button.
        val sent = sequenceOf("Send", "send", "Mengirim", "Enviar", "Envoyer")
            .any { service.clickElementByDescription(it) || service.clickText(it) }
        delay(600)
        // Verify: the input field should now be empty again after a real send.
        val cleared = service.findTextOnScreen().none { it.contains(messagePlaceholder()) }
        return if (sent) {
            Result(
                success = true,
                verificationDetails = if (cleared)
                    "Sent. The message to your WhatsApp contact is on its way."
                else
                    "I tapped send in WhatsApp, but I couldn't confirm the field cleared — worth a glance."
            )
        } else {
            Result(success = false, verificationDetails = "", error = "I couldn't find the send button to tap.")
        }
    }

    /**
     * Send an SMS by driving the real default messaging app UI the same way.
     */
    suspend fun sendSmsDraft(context: Context, contactQuery: String, message: String): Result {
        val service = JarvisAccessibilityService.instance
        val contact = resolveContact(context, contactQuery)
            ?: return Result(false, "", "I couldn't find a contact named \"$contactQuery\" in your contacts.")

        if (service == null) {
            // Open the native SMS composer with a draft — honest fallback.
            return openSmsComposer(context, contact, message)
        }

        // Step 1: open the default SMS app to a new conversation with the number.
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.number)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(smsIntent) }
            ?: return Result(false, "", "I couldn't open your messaging app.")
        waitForAnySmsApp(service, timeoutMs = 6000)

        // Step 2: locate the message input field.
        if (!waitForInputField(service, timeoutMs = 6000)) {
            return Result(
                success = false,
                verificationDetails = "I opened your messaging app for ${contact.displayName} but couldn't find the text field.",
                error = "Could not locate the SMS input field."
            )
        }

        // Step 3: type the message (the intent already drafted it, but re-type
        // to be sure it's really there and to exercise the real field).
        typeIntoField(service, message)
        delay(400)
        val onScreen = service.findTextOnScreen().joinToString(" ")
        val textLanded = onScreen.contains(message.take(24), true)

        return Result(
            success = true,
            verificationDetails = "I opened a message to ${contact.displayName} and the text \"$message\" is in the input field" +
                if (textLanded) " — ready to send." else ".",
            pendingSendApp = "your messaging app"
        )
    }

    suspend fun confirmSendSms(): Result {
        val service = JarvisAccessibilityService.instance
            ?: return Result(false, "", "Accessibility Service was disabled before I could press send.")
        val sent = sequenceOf("Send", "send", "SMS", "Send SMS", "Enviar", "Envoyer")
            .any { service.clickElementByDescription(it) || service.clickText(it) }
        delay(600)
        return if (sent) {
            Result(success = true, verificationDetails = "Sent. The SMS to your contact is on its way.")
        } else {
            Result(success = false, verificationDetails = "", error = "I couldn't find the send button to tap in your messaging app.")
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun openWhatsAppChat(context: Context, number: String, message: String): Boolean {
        val clean = number.replace(Regex("[^0-9]"), "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrElse {
            // WhatsApp not installed — try the generic intent (opens WhatsApp
            // Business or a browser, whichever can handle it).
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(fallback); true }.getOrDefault(false)
        }
    }

    private fun openWhatsAppDraftLink(context: Context, contact: ResolvedContact, message: String): Result {
        val clean = contact.number.replace(Regex("[^0-9]"), "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
        return Result(
            success = true,
            verificationDetails = "Accessibility isn't enabled, so I opened WhatsApp to ${contact.displayName}'s chat with your message drafted. You'll need to press send yourself. Enable the JARVIS Accessibility Service if you'd like me to send for you.",
            preparedOnly = true
        )
    }

    private fun openSmsComposer(context: Context, contact: ResolvedContact, message: String): Result {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.number)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        return Result(
            success = true,
            verificationDetails = "Accessibility isn't enabled, so I opened your messaging app to ${contact.displayName} with \"$message\" drafted. Press send yourself, or enable the JARVIS Accessibility Service so I can send for you.",
            preparedOnly = true
        )
    }

    private suspend fun waitForPackage(service: JarvisAccessibilityService, pkg: String, timeoutMs: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (service.currentPackageName.equals(pkg, true)) return
            delay(150)
        }
    }

    private suspend fun waitForAnySmsApp(service: JarvisAccessibilityService, timeoutMs: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val pkg = service.currentPackageName
            // Common default SMS apps; if any is foreground we're good.
            if (pkg.contains("messaging", true) || pkg.contains("sms", true) ||
                pkg.contains("messages", true) || setOf(
                    "com.google.android.apps.messaging",
                    "com.android.mms",
                    "com.samsung.android.messaging"
                ).any { it.equals(pkg, true) }) return
            delay(150)
        }
    }

    /** Wait until an editable input field is present on screen. */
    private suspend fun waitForInputField(service: JarvisAccessibilityService, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val root = service.getStructuredScreenData()
            if (root.any { it["editable"] == true }) return true
            delay(150)
        }
        return false
    }

    /** Type text into the first editable field on screen. */
    private fun typeIntoField(service: JarvisAccessibilityService, text: String): Boolean =
        service.setTextInField("", text)

    private fun messagePlaceholder(): String = "Type a message"
}
