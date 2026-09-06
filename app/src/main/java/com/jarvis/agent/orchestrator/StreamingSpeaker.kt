package com.jarvis.agent.orchestrator

/**
 * StreamingSpeaker — turns a token stream into speakable sentences.
 *
 * The AI reply arrives as small deltas while it streams. Feeding them here
 * buffers until a sentence boundary is detected, then hands the completed
 * sentence to [onSentence] so TTS can start speaking the first sentence
 * while the rest of the reply is still streaming in.
 *
 * Boundary rules:
 *  - Split after . ! ? … (and closing quotes/brackets) followed by whitespace or end of text.
 *  - A boundary only splits once the accumulated sentence is at least
 *    [MIN_SENTENCE_CHARS] long — this keeps "Dr." / "Mr." / "e.g." from
 *    producing choppy half-utterances.
 *  - [flush] emits whatever remains, used when the stream completes.
 */
class StreamingSpeaker(private val onSentence: (String) -> Unit) {

    private val buffer = StringBuilder()
    private val sentenceEnd = Regex("""[.!?…]+["')\]]*(?=\s|$)""")

    /** True once anything was spoken — callers use this to avoid double-speaking the final reply. */
    var hasSpoken: Boolean = false
        private set

    fun feed(delta: String) {
        if (delta.isEmpty()) return
        buffer.append(delta)
        drain(force = false)
    }

    fun flush() {
        drain(force = true)
    }

    private fun drain(force: Boolean) {
        if (!force) return
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) {
            buffer.setLength(0)
            emit(text)
        }
    }

    private fun emit(sentence: String) {
        val clean = sentence.trim()
        if (clean.isEmpty()) return
        hasSpoken = true
        onSentence(clean)
    }

    companion object {
        private const val MIN_SENTENCE_CHARS = 24
        private const val FORCE_FLUSH_CHARS = 160
    }
}
