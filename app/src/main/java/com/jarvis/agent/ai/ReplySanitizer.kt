package com.jarvis.agent.ai

/**
 * Strips internal artefacts from AI responses before they reach the user or TTS:
 *  - Old JSON {"action":"tool_call", ...} blobs left by the prose-JSON prompt style
 *  - Java/Kotlin stack-trace lines
 *  - System-prompt leakage (functionDeclarations, system_instruction keys)
 *  - Markdown triple-backtick fences around plain text that JARVIS would read aloud
 *  - Blank lines collapsed so the output is clean
 */
object ReplySanitizer {

    /** Matches old-style {"action":"tool_call", ... } blobs the AI sometimes emits */
    private val TOOL_CALL_JSON = Regex(
        """\{\s*"action"\s*:\s*"tool_call".*?\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /** Java/Kotlin stack-trace lines — never shown to the user */
    private val STACK_TRACE_LINE = Regex("""\tat [\w.${'$'}/<>]+\(.+\)""")

    /** Tokens that should never appear in a user-facing reply */
    private val INTERNAL_TOKENS = Regex(
        """(functionDeclarations|system_instruction|"action"\s*:\s*"tool_call"|JARVIS_SYSTEM_PROMPT|generationConfig|safetySettings)""",
        RegexOption.IGNORE_CASE
    )

    /** Triple-backtick code fences (keep the text, strip the fences) */
    private val CODE_FENCE = Regex("```[a-zA-Z]*\\n?|```")

    fun sanitize(input: String): String {
        if (input.isBlank()) return "Done."
        var s = input.trim()

        // 1. Remove JSON tool_call blobs
        s = TOOL_CALL_JSON.replace(s, "")

        // 2. Remove stack-trace lines
        s = STACK_TRACE_LINE.replace(s, "")

        // 3. Remove any line containing internal system tokens
        s = s.lines()
            .filterNot { line -> INTERNAL_TOKENS.containsMatchIn(line) }
            .joinToString("\n")

        // 4. Strip code fences (keep text inside)
        s = CODE_FENCE.replace(s, "")

        // 5. Collapse runs of blank lines into a single blank
        s = s.replace(Regex("\n{3,}"), "\n\n").trim()

        return s.ifBlank { "Done." }
    }
}
