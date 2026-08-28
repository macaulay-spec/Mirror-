package com.jarvis.agent.dialogue

import android.content.Context
import com.jarvis.agent.nlu.IntentRouter
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.app.people.PeopleGraph
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The piece the app never had.
 *
 * Before this, every utterance was one-shot: parse → fire → reply. There was no state, so
 * JARVIS could not ask "which Mumsi?", could not confirm before calling, could not
 * understand "call her back", and gave up instead of repairing a failure.
 *
 * This holds the conversation state:
 *  - an open slot   ("Who should I call?") that the next utterance fills
 *  - a pending confirmation for anything risky (calls, messages, payments)
 *  - entity memory  (last person, last message, last app) so pronouns resolve
 */
class DialogueManager(private val context: Context) {

    // ------------------------------------------------------------------ model

    data class TurnResult(
        val handled: Boolean,
        val spoken: String? = null,
        val toolResult: ToolExecutionResult? = null,
        val confirmRequest: ToolExecutionRequest? = null
    )

    private class OpenSlot(
        val intentId: String,
        val slot: String,
        val question: String,
        val options: List<String>,
        val args: MutableMap<String, Any?>
    )

    private enum class Slot { CONTACT, CONTACT_PICK, NUMBER_CHOICE, MESSAGE, APP, NONE }

    // ------------------------------------------------------------------ state

    private var openSlot: OpenSlot? = null
    private var pendingConfirm: ToolExecutionRequest? = null

    private val _awaitingQuestion = MutableStateFlow<String?>(null)
    val awaitingQuestion: StateFlow<String?> = _awaitingQuestion.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ToolExecutionRequest?>(null)
    val pendingConfirmation: StateFlow<ToolExecutionRequest?> = _pendingConfirmation.asStateFlow()

    val entities = EntityMemory()

    /** True while JARVIS is mid-question, so the UI can show it is listening for an answer. */
    val isMidTask: Boolean get() = openSlot != null || pendingConfirm != null

    // ------------------------------------------------------------------- turn

    suspend fun handle(input: String): TurnResult {
        val text = input.trim()
        if (text.isBlank()) return TurnResult(false)

        // 1. Answering a confirmation?
        pendingConfirm?.let { request ->
            return when {
                IntentRouter.isCancel(text) || IntentRouter.isNo(text) -> {
                    val name = request.name
                    pendingConfirm = null
                    _pendingConfirmation.value = null
                    TurnResult(true, "Okay, cancelled. I won't $name.")
                }
                IntentRouter.isYes(text) -> {
                    pendingConfirm = null
                    _pendingConfirmation.value = null
                    val result = ToolRegistry.execute(context, request)
                    TurnResult(true, result.verificationDetails ?: result.error, result)
                }
                else -> TurnResult(true, "Yes or no? ${describeRequest(request)}")
            }
        }

        // 2. Filling an open slot?
        openSlot?.let { slot ->
            return continueSlot(slot, text)
        }

        // 3. A fresh utterance
        return startNewIntent(text)
    }

    /** Called by the orchestrator when the user taps CONFIRM in the UI. */
    fun noteConfirmed() {
        pendingConfirm = null
        _pendingConfirmation.value = null
    }

    fun cancel() {
        openSlot = null
        pendingConfirm = null
        _awaitingQuestion.value = null
        _pendingConfirmation.value = null
    }

    // ------------------------------------------------------------ slot filling

    private suspend fun continueSlot(slot: OpenSlot, text: String): TurnResult {
        if (IntentRouter.isCancel(text)) {
            openSlot = null
            _awaitingQuestion.value = null
            return TurnResult(true, "Okay, cancelled.")
        }

        val value = when (Slot.valueOf(slot.slot)) {
            Slot.CONTACT -> text
            Slot.CONTACT_PICK -> text          // resolved below
            Slot.NUMBER_CHOICE -> chooseOption(slot.options, text)
            Slot.MESSAGE -> text
            Slot.APP -> text
            Slot.NONE -> text
        }

        val args = slot.args

        when (Slot.valueOf(slot.slot)) {
            Slot.CONTACT, Slot.CONTACT_PICK -> {
                val matches = PeopleGraph.resolve(context, value)
                when {
                    matches.isEmpty() -> {
                        // Still unknown: ask once more with a picker, then give up politely.
                        val suggestions = PeopleGraph.allPeople(context).take(8).map { it.displayName }
                        if (suggestions.isEmpty()) {
                            openSlot = null
                            _awaitingQuestion.value = null
                            return TurnResult(
                                true,
                                "I still can't find \"$value\" and there's nobody in your contacts to pick from. " +
                                    "Add them to your contacts and I'll find them next time."
                            )
                        }
                        openSlot = OpenSlot(
                            slot.intentId, Slot.CONTACT_PICK.name,
                            "I can't find \"$value\". Who is that?",
                            suggestions + "Nobody there", args
                        )
                        _awaitingQuestion.value = openSlot?.question
                        return TurnResult(true, openSlot!!.question)
                    }
                    matches.size > 1 -> {
                        val options = matches.map { "${it.person.displayName} (${it.numbers.firstOrNull()?.value ?: "no number"})" }
                        openSlot = OpenSlot(
                            slot.intentId, Slot.NUMBER_CHOICE.name,
                            "Which ${matches.first().person.displayName}?",
                            options, args
                        )
                        _awaitingQuestion.value = openSlot?.question
                        return TurnResult(true, openSlot!!.question)
                    }
                    else -> {
                        val match = matches.first()
                        // If this was a "who is X?" answer, remember the nickname forever.
                        if (Slot.valueOf(slot.slot) == Slot.CONTACT_PICK) {
                            PeopleGraph.learnNickname(context, value, match.person)
                        }
                        args["contact"] = match.person.displayName
                        entities.notePerson(match.person.displayName, match.numbers.firstOrNull()?.value)
                        if (match.numbers.size > 1) {
                            val options = match.numbers.map { "${it.type} ending ${it.value.takeLast(4)}" }
                            openSlot = OpenSlot(
                                slot.intentId, Slot.NUMBER_CHOICE.name,
                                "${match.person.displayName} has ${match.numbers.size} numbers: ${options.joinToString(", ")}. Which one?",
                                options, args
                            )
                            _awaitingQuestion.value = openSlot?.question
                            return TurnResult(true, openSlot!!.question)
                        }
                        match.numbers.firstOrNull()?.let {
                            args["number"] = it.value
                            args["number_type"] = it.type
                        }
                    }
                }
            }
            Slot.NUMBER_CHOICE -> {
                val chosen = chooseOption(slot.options, text)
                Regex("ending (\\d{4})").find(chosen)?.groupValues?.get(1)?.let { tail ->
                    val match = PeopleGraph.resolve(context, args["contact"]?.toString() ?: "").firstOrNull()
                    match?.numbers?.firstOrNull { it.value.endsWith(tail) }?.let {
                        args["number"] = it.value
                        args["number_type"] = it.type
                    }
                }
            }
            Slot.MESSAGE -> args["message"] = value
            Slot.APP -> args["app"] = value
            else -> Unit
        }

        openSlot = null
        _awaitingQuestion.value = null
        return runIntent(slot.intentId, args)
    }

    // -------------------------------------------------------------- new intent

    private suspend fun startNewIntent(text: String): TurnResult {
        val parsed = IntentRouter.parse(context, text)
        if (parsed.isUnknown) return TurnResult(false)

        val args = parsed.args
        applyContext(parsed.id, args, text)

        return runIntent(parsed.id, args)
    }

    /** "call her back" / "send it again" — resolve pronouns from entity memory. */
    private fun applyContext(intentId: String, args: MutableMap<String, Any?>, text: String) {
        val lower = text.lowercase()
        val contact = args["contact"]?.toString()
        val needsPerson = intentId == IntentRouter.INTENT_CALL || intentId == IntentRouter.INTENT_SMS

        if (needsPerson && (contact.isNullOrBlank() || lower.contains("back") || lower.contains("again"))) {
            entities.lastPersonName?.let { last ->
                if (contact.isNullOrBlank() || lower.contains("back") || lower.contains("again")) {
                    args["contact"] = last
                }
            }
        }
        if (intentId == IntentRouter.INTENT_SMS && args["message"]?.toString().isNullOrBlank()) {
            entities.lastMessage?.let { args["message"] = it }
        }
    }

    // ----------------------------------------------------------------- running

    private suspend fun runIntent(intentId: String, args: MutableMap<String, Any?>): TurnResult {
        // Fill whatever is still missing before touching the device.
        when (intentId) {
            IntentRouter.INTENT_CALL, IntentRouter.INTENT_SMS, IntentRouter.INTENT_REPLY -> {
                val contact = args["contact"]?.toString()?.trim()
                if (contact.isNullOrBlank()) {
                    return ask(
                        intentId, Slot.CONTACT,
                        if (intentId == IntentRouter.INTENT_CALL) "Who should I call?" else "Who should I message?",
                        recentPeopleNames(), args
                    )
                }
                // Known person? Resolve to a real number, or ask who they are.
                val matches = PeopleGraph.resolve(context, contact)
                if (matches.isEmpty()) {
                    val suggestions = recentPeopleNames()
                    return if (suggestions.isEmpty()) {
                        TurnResult(true, "I couldn't find \"$contact\" in your contacts.")
                    } else {
                        ask(intentId, Slot.CONTACT_PICK, "I can't find \"$contact\". Who is that?", suggestions + "Nobody there", args)
                    }
                }
                val match = matches.first()
                args["contact"] = match.person.displayName
                entities.notePerson(match.person.displayName, match.numbers.firstOrNull()?.value)
                when {
                    match.numbers.size > 1 -> {
                        val options = match.numbers.map { "${it.type} ending ${it.value.takeLast(4)}" }
                        return ask(
                            intentId, Slot.NUMBER_CHOICE,
                            "${match.person.displayName} has ${match.numbers.size} numbers: ${options.joinToString(", ")}. Which one?",
                            options, args
                        )
                    }
                    match.numbers.size == 1 -> {
                        args["number"] = match.numbers[0].value
                        args["number_type"] = match.numbers[0].type
                    }
                }
                if (intentId == IntentRouter.INTENT_SMS && args["message"]?.toString().isNullOrBlank()) {
                    return ask(intentId, Slot.MESSAGE, "What should I say to ${match.person.displayName}?", emptyList(), args)
                }
            }

            IntentRouter.INTENT_OPEN_APP -> {
                val app = args["app"]?.toString()?.trim()
                if (app.isNullOrBlank()) return ask(intentId, Slot.APP, "Which app should I open?", emptyList(), args)
            }
        }

        return execute(intentId, args)
    }

    private suspend fun execute(intentId: String, args: MutableMap<String, Any?>): TurnResult {
        val tool = ToolRegistry.getTool(intentId)
            ?: return TurnResult(true, "I don't know how to do that yet.")

        val request = ToolExecutionRequest(
            toolId = intentId,
            name = tool.name,
            arguments = args.toMap(),
            riskLevel = tool.riskLevel,
            requiresConfirmation = tool.riskLevel >= RiskLevel.LEVEL_2
        )

        // Anything that phones or messages someone, spends money or deletes data is
        // confirmed first — spoken aloud and shown as a card in the chat.
        if (request.requiresConfirmation) {
            pendingConfirm = request
            _pendingConfirmation.value = request
            val prompt = describeRequest(request)
            return TurnResult(true, prompt, confirmRequest = request)
        }

        val result = ToolRegistry.execute(context, request)
        rememberOutcome(intentId, args, result)
        return TurnResult(true, result.verificationDetails ?: result.error, result)
    }

    private fun rememberOutcome(intentId: String, args: Map<String, Any?>, result: ToolExecutionResult) {
        if (!result.success) return
        when (intentId) {
            IntentRouter.INTENT_CALL, IntentRouter.INTENT_SMS, IntentRouter.INTENT_REPLY -> {
                entities.notePerson(
                    args["contact"]?.toString() ?: return,
                    args["number"]?.toString()
                )
                entities.noteMessage(args["message"]?.toString())
            }
            IntentRouter.INTENT_OPEN_APP -> entities.noteApp(args["app"]?.toString() ?: return)
        }
    }

    // ------------------------------------------------------------------ asking

    private fun ask(
        intentId: String,
        slot: Slot,
        question: String,
        options: List<String>,
        args: MutableMap<String, Any?>
    ): TurnResult {
        openSlot = OpenSlot(intentId, slot.name, question, options, args)
        _awaitingQuestion.value = question
        return TurnResult(true, question)
    }

    private suspend fun recentPeopleNames(): List<String> =
        try {
            PeopleGraph.allPeople(context).take(8).map { it.displayName }
        } catch (_: Exception) {
            emptyList()
        }

    private fun chooseOption(options: List<String>, answer: String): String {
        if (options.isEmpty()) return answer
        val lower = answer.lowercase().trim()
        val index = when {
            lower == "1" || lower.contains("first") -> 0
            lower == "2" || lower.contains("second") -> 1
            lower == "3" || lower.contains("third") -> 2
            else -> null
        }
        if (index != null && index < options.size) return options[index]
        return options.firstOrNull { it.lowercase().contains(lower) || lower.contains(it.lowercase()) }
            ?: answer
    }

    private fun describeRequest(request: ToolExecutionRequest): String {
        val who = request.arguments["contact"]?.toString()
            ?: request.arguments["app"]?.toString()
            ?: request.arguments["package"]?.toString()
        return when (request.toolId) {
            IntentRouter.INTENT_CALL -> "Call $who${numberSuffix(request)}. Shall I?"
            IntentRouter.INTENT_SMS, IntentRouter.INTENT_REPLY ->
                "Send to $who: \"${request.arguments["message"] ?: request.arguments["reply_text"]}\". Shall I?"
            else -> "${request.name} — shall I go ahead?"
        }
    }

    private fun numberSuffix(request: ToolExecutionRequest): String {
        val type = request.arguments["number_type"]?.toString()
        return if (!type.isNullOrBlank()) " on $type" else ""
    }
}
