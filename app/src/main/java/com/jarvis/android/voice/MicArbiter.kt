package com.jarvis.android.voice

import android.util.Log
import com.jarvis.app.voice.VoiceDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single authority on who is holding the microphone.
 *
 * ## The problem this solves (audit P1-D)
 *
 * JARVIS has two independent `SpeechRecognizer` stacks:
 *
 *  - [JarvisVoiceEngine] — the foreground conversation recognizer.
 *  - `SystemSpeechRecognizerEngine`, owned by `WakeWordForegroundService` — the
 *    background "Hey JARVIS" listener.
 *
 * They used to coordinate only by *observing each other*: the wake-word service polled
 * `VoiceBus.engineState` for up to 120 s waiting for `IDLE`, then started its own
 * recognizer regardless. Meanwhile `continuousMode` was set to `true` on every wake word
 * and every mic toggle and was never cleared on a normal turn ending, so the conversation
 * recognizer kept re-arming and the state never settled. When it briefly did, both
 * recognizers came up at once → `ERROR_RECOGNIZER_BUSY` → both backed off → hands-free
 * listening died until the service was restarted.
 *
 * Android permits exactly one active `SpeechRecognizer` per app. Two components cannot
 * negotiate that by polling a shared enum; something has to *own* the decision. This is
 * that something.
 *
 * ## Invariant
 *
 * At most one holder at a time. Conversation preempts wake word (the user is actively
 * talking to JARVIS); wake word may only start when nothing else holds the mic, and
 * resumes by observing [holder] rather than by polling engine state.
 *
 * ## Threading
 *
 * Both owners already confine their recognizer work to the main thread
 * (`JarvisVoiceEngine.mainHandler`, `WakeWordForegroundService`'s `Dispatchers.Main`
 * scope), and every entry point here is `@Synchronized`, so the invariant holds
 * regardless of which thread calls in. Yield hooks are invoked while holding the monitor;
 * they must not block, and re-entering this object from a hook is safe because the
 * holder has already been cleared before the hook runs.
 */
object MicArbiter {

    private const val TAG = "MicArbiter"

    /** Who currently owns the microphone. */
    enum class Holder { NONE, WAKE_WORD, CONVERSATION }

    private val _holder = MutableStateFlow(Holder.NONE)

    /**
     * Observe this instead of polling engine state. `WakeWordForegroundService` collects
     * it and re-requests the mic the moment it returns to [Holder.NONE].
     */
    val holder: StateFlow<Holder> = _holder.asStateFlow()

    private var wakeWordYield: (() -> Unit)? = null
    private var conversationYield: (() -> Unit)? = null

    /**
     * Registers the callback used to forcibly stop the wake-word recognizer when a
     * conversation needs the mic. Called by `WakeWordForegroundService` on create.
     */
    fun setWakeWordOwner(onYield: () -> Unit) {
        wakeWordYield = onYield
    }

    /**
     * Registers the callback used to forcibly stop the conversation recognizer when
     * something with higher priority takes over. Called by [JarvisVoiceEngine] on init.
     */
    fun setConversationOwner(onYield: () -> Unit) {
        conversationYield = onYield
    }

    /** Drops both hooks. Used on teardown so a dead service is never called back. */
    fun clearOwners() {
        wakeWordYield = null
        conversationYield = null
    }

    /**
     * Takes the microphone for a conversation, preempting the wake word if necessary.
     *
     * @return true once [holder] is [Holder.CONVERSATION] and the caller may start its
     *   recognizer. Always true today; the boolean exists so a future policy (e.g. a
     *   phone call holding the mic) can refuse without changing call sites.
     */
    @Synchronized
    fun acquireConversation(reason: String): Boolean {
        when (_holder.value) {
            Holder.CONVERSATION -> return true
            Holder.WAKE_WORD -> {
                Log.i(TAG, "conversation preempts wake word ($reason)")
                _holder.value = Holder.NONE
                // Stop the background recognizer BEFORE granting, so there is never an
                // instant where two recognizers are live.
                runCatching { wakeWordYield?.invoke() }
                    .onFailure { Log.w(TAG, "wake-word yield hook failed", it) }
            }
            Holder.NONE -> Unit
        }
        _holder.value = Holder.CONVERSATION
        VoiceDiagnostics.report("Mic → conversation ($reason)")
        return true
    }

    /** Gives the microphone back. Wake word may resume; it is notified via [holder]. */
    @Synchronized
    fun releaseConversation(reason: String) {
        if (_holder.value != Holder.CONVERSATION) return
        _holder.value = Holder.NONE
        VoiceDiagnostics.report("Mic released by conversation ($reason)")
    }

    /**
     * Requests the microphone for background wake-word listening.
     *
     * @return false if a conversation holds it. The caller must NOT start a recognizer in
     *   that case — it should collect [holder] and retry when it becomes [Holder.NONE].
     */
    @Synchronized
    fun acquireWakeWord(): Boolean {
        when (_holder.value) {
            Holder.WAKE_WORD -> return true
            Holder.CONVERSATION -> {
                Log.i(TAG, "wake word denied: conversation holds the mic")
                return false
            }
            Holder.NONE -> Unit
        }
        _holder.value = Holder.WAKE_WORD
        return true
    }

    @Synchronized
    fun releaseWakeWord() {
        if (_holder.value != Holder.WAKE_WORD) return
        _holder.value = Holder.NONE
    }

    /**
     * Forcibly ends a conversation turn — used by `emergencyStop()` and audio-focus loss.
     * Stops the conversation recognizer through its hook and frees the mic.
     */
    @Synchronized
    fun forceReleaseConversation(reason: String) {
        if (_holder.value != Holder.CONVERSATION) return
        _holder.value = Holder.NONE
        runCatching { conversationYield?.invoke() }
            .onFailure { Log.w(TAG, "conversation yield hook failed", it) }
        VoiceDiagnostics.report("Mic force-released from conversation ($reason)")
    }

    /** One-line snapshot for the Diagnostics screen. */
    fun describe(): String = "Microphone owner: ${_holder.value.name}"
}
