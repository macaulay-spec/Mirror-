package com.jarvis.app.dialogue

import android.content.Context
import com.jarvis.app.contextgraph.ContextGraphDao
import com.jarvis.app.intent.Intent
import com.jarvis.app.intent.IntentRouter
import com.jarvis.app.memory.PersonEntity
import com.jarvis.app.people.PeopleGraph

class DialogueManager(
    private val context: Context,
    private val contextGraphDao: ContextGraphDao? = null
) {
    var openSlot: String? = null // e.g., "contact", "message_body"
    var pendingIntent: Intent? = null
    var pendingConfirm: Intent? = null
    val entities = EntityMemory()

    suspend fun handle(utterance: String): DialogueResult {
        // Handle corrections/cancellations
        val lower = utterance.lowercase().trim()
        if (lower == "stop" || lower == "cancel" || lower == "never mind" || lower == "nevermind") {
            clearState()
            return DialogueResult.Reply("Cancelled.")
        }

        // Check if we are confirming an action
        if (pendingConfirm != null) {
            val intent = pendingConfirm!!
            if (lower == "yes" || lower == "yeah" || lower == "do it") {
                pendingConfirm = null
                return executeIntent(intent)
            } else if (lower == "no" || lower == "don't") {
                pendingConfirm = null
                return DialogueResult.Reply("Okay, cancelled.")
            }
        }

        // Check if we are filling an open slot
        if (openSlot != null && pendingIntent != null) {
            return fillSlot(utterance)
        }

        // Parse new intent
        val intent = IntentRouter.route(utterance)
        return processIntent(intent, utterance)
    }

    private suspend fun processIntent(intent: Intent, utterance: String): DialogueResult {
        when (intent) {
            is Intent.CallPerson -> {
                val contactName = intent.contact ?: return DialogueResult.Ask("contact_name", "Who do you want to call?")
                val person = resolvePerson(contactName)
                
                if (person == null) {
                    // Start lazy onboarding: Ask who this is
                    pendingIntent = intent
                    openSlot = "new_contact_relation"
                    return DialogueResult.Ask("new_contact_relation", "I don't know who $contactName is. Who are they to you?")
                }

                entities.lastContact = person
                val numbers = PeopleGraph.numbers(person)
                
                // Disambiguate if multiple numbers and type not specified
                if (intent.numberType == null && numbers.size > 1) {
                    pendingIntent = intent
                    openSlot = "number_type"
                    return DialogueResult.Ask("number_type", "${person.displayName} has multiple numbers. Home or mobile?", listOf("Home", "Mobile"))
                }

                if (!intent.confirm) {
                    pendingConfirm = intent.copy(confirm = true)
                    return DialogueResult.Confirm(
                        tool = "call_contact",
                        arguments = mapOf("contact" to person.displayName, "type" to (intent.numberType ?: "default")),
                        prompt = "Calling ${person.displayName}. Yes?",
                        risk = 2
                    )
                }

                return executeIntent(intent)
            }
            
            is Intent.SendMessage -> {
                val contactName = intent.contact ?: return DialogueResult.Ask("contact_name", "Who do you want to text?")
                val person = resolvePerson(contactName)
                
                if (person == null) {
                    pendingIntent = intent
                    openSlot = "new_contact_relation"
                    return DialogueResult.Ask("new_contact_relation", "I don't have $contactName saved. Who is that?")
                }
                
                entities.lastContact = person
                
                val body = intent.body
                if (body == null) {
                    pendingIntent = intent
                    openSlot = "message_body"
                    return DialogueResult.Ask("message_body", "What should I say to ${person.displayName}?")
                }
                
                if (!intent.confirm) {
                    pendingConfirm = intent.copy(confirm = true)
                    return DialogueResult.Confirm(
                        tool = "send_message",
                        arguments = mapOf("contact" to person.displayName, "body" to body),
                        prompt = "Send to ${person.displayName}: '$body'. Send it?",
                        risk = 2
                    )
                }
                
                return executeIntent(intent)
            }

            is Intent.OpenApp -> {
                val appName = intent.appName ?: return DialogueResult.Ask("app_name", "Which app?")
                // If it's a compound command with search/and/to, send to LLM
                if (appName.contains(" and ") || appName.contains(" search") || appName.contains(" for ") || appName.contains(" to ")) {
                    return DialogueResult.ToolCall("llm_fallback", mapOf("utterance" to utterance))
                }
                return DialogueResult.ToolCall("open_app", mapOf("app_name" to appName))
            }
            
            is Intent.ToggleSetting -> {
                return DialogueResult.ToolCall("toggle_setting", mapOf("setting" to intent.setting, "state" to intent.state))
            }
            
            is Intent.SetVolume -> {
                return DialogueResult.ToolCall("set_volume", mapOf("direction" to (intent.direction ?: "up")))
            }

            // CHANGED (forensic audit, D.2): Navigate and ReadMessages used to
            // have no branch here, so they fell to the `else` below and always
            // got "I'm not sure how to do that yet." -- even though
            // IntentRouter correctly recognized both, and (for ReadMessages)
            // JarvisAIEngine already had a working handler that this dead end
            // prevented from ever being reached. Route both to the model,
            // same as Unknown does.
            is Intent.Navigate -> {
                return DialogueResult.ToolCall("llm_fallback", mapOf("utterance" to utterance))
            }

            is Intent.ReadMessages -> {
                return DialogueResult.ToolCall("llm_fallback", mapOf("utterance" to utterance))
            }

            is Intent.Unknown -> {
                // LLM Fallback
                return DialogueResult.ToolCall("llm_fallback", mapOf("utterance" to intent.raw))
            }
            else -> return DialogueResult.Reply("I'm not sure how to do that yet.")
        }
    }

    private suspend fun fillSlot(utterance: String): DialogueResult {
        val slot = openSlot
        val intent = pendingIntent ?: return DialogueResult.Reply("I forgot what we were doing.")
        
        openSlot = null // Close the slot
        
        when (intent) {
            is Intent.CallPerson -> {
                if (slot == "new_contact_relation") {
                    // The user is answering "Who is mumsi?" e.g., "my mother"
                    val relation = utterance.replace("my ", "").trim()
                    val contactName = intent.contact ?: ""
                    val matches = PeopleGraph.resolve(context, contactName)
                    if (matches.isNotEmpty()) {
                        PeopleGraph.setRelationship(context, matches.first().person, relation)
                    }
                    return processIntent(intent, utterance)
                }
                if (slot == "number_type") {
                    val updatedIntent = intent.copy(numberType = utterance.trim().lowercase())
                    pendingIntent = null
                    return processIntent(updatedIntent, utterance)
                }
            }
            is Intent.SendMessage -> {
                if (slot == "message_body") {
                    val updatedIntent = intent.copy(body = utterance)
                    pendingIntent = null
                    return processIntent(updatedIntent, utterance)
                }
            }
            else -> {}
        }
        
        clearState()
        return DialogueResult.Reply("I got confused. Let's start over.")
    }

    private fun executeIntent(intent: Intent): DialogueResult {
        return when (intent) {
            is Intent.CallPerson -> {
                DialogueResult.ToolCall("call_contact", mapOf(
                    "contact" to (intent.contact ?: ""),
                    "type" to (intent.numberType ?: "default")
                ))
            }
            is Intent.SendMessage -> {
                DialogueResult.ToolCall("send_message", mapOf(
                    "contact" to (intent.contact ?: ""),
                    "body" to (intent.body ?: "")
                ))
            }
            else -> DialogueResult.Reply("Action not implemented yet.")
        }
    }

    private suspend fun resolvePerson(name: String): PersonEntity? {
        // If pronoun, check entity memory. CHANGED (forensic audit, D.1): strip
        // a trailing " back" first (the redial/reply case -- "call her back",
        // "text him back") so it's compared as "her"/"him" instead of the
        // literal "her back", which never matched and sent this down the
        // "I don't know who that is" new-contact flow instead of redialing.
        val lower = name.lowercase().removeSuffix(" back").trim()
        if (lower == "him" || lower == "her" || lower == "them") {
            return entities.lastContact
        }
        
        // Otherwise look up in graph
        val matches = PeopleGraph.resolve(context, name)
        return matches.firstOrNull()?.person
    }

    private fun clearState() {
        openSlot = null
        pendingConfirm = null
        pendingIntent = null
    }
}
