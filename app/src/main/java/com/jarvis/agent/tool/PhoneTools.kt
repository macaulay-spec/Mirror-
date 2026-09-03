package com.jarvis.agent.tool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.jarvis.app.tools.ContactsResolver
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult

/**
 * Calls, SMS and call-log tools.
 *
 * Before this file the app could do none of these: `CALL_PHONE` was declared in the
 * manifest but never requested, and `ContactsToolkit.dial()` was never called by anything.
 *
 * Ambiguity is returned as a question rather than a silent guess — "Amaka has two
 * numbers, home or mobile?" — which the assistant simply speaks.
 */
object PhoneTools {

    fun registerAll() {
        registerCallContact()
        registerSendSms()
        registerContactLookup()
        registerCallLog()
    }

    private fun firstArg(args: Map<String, Any?>, vararg keys: String): String =
        keys.mapNotNull { args[it]?.toString()?.trim() }.firstOrNull { it.isNotBlank() } ?: ""

    private fun missingPermission(context: Context, permission: String, friendly: String): ToolExecutionResult? {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return null
        return ToolExecutionResult(
            toolId = "phone",
            success = false,
            data = mapOf("missingPermission" to permission),
            error = "I need the $friendly permission before I can do that. Open Settings and allow it, then ask me again."
        )
    }

    /** Resolve one or more numbers for a spoken name, returning a disambiguation question if needed. */
    private sealed class Resolution {
        data class Ready(val number: String, val name: String) : Resolution()
        data class Ambiguous(val question: String) : Resolution()
        data class NotFound(val message: String) : Resolution()
        data class Blocked(val message: String) : Resolution()
    }

    private fun resolveTarget(context: Context, args: Map<String, Any?>): Resolution {
        val requestedType = firstArg(args, "number_type", "type", "line").lowercase()
        val raw = firstArg(args, "contact", "name", "person", "recipient", "to", "number", "phone")

        if (raw.isBlank()) return Resolution.NotFound("Who should I contact?")

        // A literal number
        if (raw.any { it.isDigit() } && raw.replace(Regex("[^+ \\-()]"), "").length >= 7 &&
            !raw.any { it.isLetter() }
        ) {
            return Resolution.Ready(raw, raw)
        }

        if (!ContactsResolver.hasPermission(context)) {
            return Resolution.Blocked(
                "I need access to your contacts to find \"$raw\". Grant contacts permission and try again."
            )
        }

        var candidates = ContactsResolver.find(context, raw)
        if (candidates.isEmpty()) {
            return Resolution.NotFound("I couldn't find anyone called \"$raw\" in your contacts.")
        }
        if (requestedType.isNotBlank()) {
            val filtered = candidates.filter { it.typeLabel.equals(requestedType, ignoreCase = true) }
            if (filtered.isNotEmpty()) candidates = filtered
        }

        return when {
            candidates.size == 1 -> Resolution.Ready(candidates[0].number, candidates[0].name)
            else -> {
                val options = candidates.joinToString(", ") { "${it.typeLabel} ending ${it.lastFour}" }
                Resolution.Ambiguous("${candidates[0].name} has ${candidates.size} numbers: $options. Which one?")
            }
        }
    }

    private fun registerCallContact() {
        ToolRegistry.register(
            ToolDefinition(
                id = "call_contact",
                name = "Call a Contact",
                description = "Calls a person by name, nickname or number. Ask which number when a contact has several.",
                category = "PHONE",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                when (val target = resolveTarget(context, args)) {
                    is Resolution.Blocked -> fail("call_contact", target.message)
                    is Resolution.NotFound -> fail("call_contact", target.message)
                    is Resolution.Ambiguous -> fail("call_contact", target.question)
                    is Resolution.Ready -> {
                        val denied = missingPermission(context, Manifest.permission.CALL_PHONE, "phone")
                        if (denied != null) return@ToolDefinition denied

                        val uri = Uri.parse("tel:${Uri.encode(target.number)}")
                        val canCallDirectly = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED

                        val intent = Intent(
                            if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        return@ToolDefinition try {
                            context.startActivity(intent)
                            ToolExecutionResult(
                                toolId = "call_contact",
                                success = true,
                                data = mapOf("name" to target.name, "number" to target.number),
                                verificationDetails = "Calling ${target.name}."
                            )
                        } catch (e: Exception) {
                            fail("call_contact", "I couldn't start the call: ${e.localizedMessage}")
                        }
                    }
                }
            }
        )
    }

    private fun registerSendSms() {
        ToolRegistry.register(
            ToolDefinition(
                id = "send_sms",
                name = "Send SMS",
                description = "Sends a text message to a contact or number by opening the messaging app, typing the message, and asking for confirmation before sending. Requires the JARVIS Accessibility Service for full automation; falls back to a drafted composer otherwise.",
                category = "MESSAGING",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val body = firstArg(args, "message", "text", "body", "content")
                if (body.isBlank()) return@ToolDefinition fail("send_sms", "What should I say?")
                val contactQuery = firstArg(args, "contact", "recipient", "name", "number")
                if (contactQuery.isBlank()) return@ToolDefinition fail("send_sms", "Who should I send it to?")

                // Route through the unified, Accessibility-driven MessagingAutomation
                // instead of a headless SmsManager call. This is the vision's worked
                // example: open the real app, type, verify, confirm, then send.
                // The same stateful entry point the send_message tool uses, so the
                // typed-intent path and the LLM path behave identically.
                val result = com.jarvis.app.tools.MessagingAutomation.executeSend(
                    context,
                    com.jarvis.app.tools.MessagingAutomation.TargetApp.SMS,
                    contactQuery,
                    body
                )
                if (!result.success) {
                    return@ToolDefinition fail("send_sms", result.verificationDetails.ifBlank { result.error ?: "I couldn't prepare the message." })
                }
                if (result.preparedOnly) {
                    ToolExecutionResult(
                        toolId = "send_sms",
                        success = true,
                        data = mapOf("recipient" to contactQuery, "message" to body, "preparedOnly" to true),
                        verificationDetails = result.verificationDetails
                    )
                } else {
                    ToolExecutionResult(
                        toolId = "send_sms",
                        success = true,
                        data = mapOf(
                            "recipient" to contactQuery,
                            "message" to body,
                            "pendingSendApp" to (result.pendingSendApp ?: "your messaging app")
                        ),
                        verificationDetails = result.verificationDetails
                    )
                }
            }
        )
    }

    private fun registerContactLookup() {
        ToolRegistry.register(
            ToolDefinition(
                id = "contact_lookup",
                name = "Look Up a Contact",
                description = "Finds a person's phone numbers by name or nickname.",
                category = "PHONE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val query = firstArg(args, "contact", "name", "person", "query", "who")
                if (query.isBlank()) return@ToolDefinition fail("contact_lookup", "Who are you looking for?")
                if (!ContactsResolver.hasPermission(context)) {
                    return@ToolDefinition fail(
                        "contact_lookup",
                        "I need contacts permission to look people up."
                    )
                }
                val matches = ContactsResolver.find(context, query)
                if (matches.isEmpty()) {
                    return@ToolDefinition fail("contact_lookup", "Nobody called \"$query\" in your contacts.")
                }
                val spoken = matches.joinToString(", ") { "${it.typeLabel} ending ${it.lastFour}" }
                ToolExecutionResult(
                    toolId = "contact_lookup",
                    success = true,
                    data = mapOf(
                        "name" to matches.first().name,
                        "numbers" to matches.map { mapOf("type" to it.typeLabel, "number" to it.number) }
                    ),
                    verificationDetails = "${matches.first().name}: $spoken."
                )
            }
        )
    }

    private fun registerCallLog() {
        ToolRegistry.register(
            ToolDefinition(
                id = "read_call_log",
                name = "Read Call History",
                description = "Reads recent incoming, outgoing and missed calls.",
                category = "PHONE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val limit = args["limit"]?.toString()?.toIntOrNull() ?: 10
                val records = ContactsResolver.recentCalls(context, limit)
                if (records.isEmpty()) {
                    return@ToolDefinition fail(
                        "read_call_log",
                        "I can't see your call history yet — grant the call-log permission in Settings."
                    )
                }
                val spoken = records.joinToString("; ") {
                    "${it.name ?: it.number}, ${it.type}"
                }
                ToolExecutionResult(
                    toolId = "read_call_log",
                    success = true,
                    data = mapOf("calls" to records.map { mapOf("name" to it.name, "number" to it.number, "type" to it.type) }),
                    verificationDetails = spoken
                )
            }
        )
    }

    private fun defaultSmsManager(context: Context): SmsManager = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SmsManager.getSmsManagerForSubscriptionId(SmsManager.getDefaultSmsSubscriptionId())
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    } catch (_: Exception) {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }

    private fun fail(toolId: String, message: String) =
        ToolExecutionResult(toolId = toolId, success = false, data = null, error = message)
}
