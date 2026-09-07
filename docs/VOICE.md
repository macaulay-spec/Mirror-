# Voice Architecture

JARVIS runs a fully on-device voice pipeline. **There is no backend** — every hop is a
direct HTTPS call to a provider, and every hop has a local fallback.

## Flow

`Wake Word` → `Listening` → `STT` → `Agent Processing` → `TTS` → `Speaking` → `Idle`

## Wake word

Implemented natively on-device. When activated it brings up the Orb and enters the
`LISTENING` state.

## Speech input (STT)

Uses Android's `SpeechRecognizer`. No audio leaves the device unless the recognizer
itself is a cloud service (Google's, on most devices).

`JarvisVoiceEngine.startListening()` checks `SpeechRecognizer.isRecognitionAvailable()`
first. On devices that ship **no** recognition service it does **not** silently do
nothing: it writes a diagnostic explaining the situation, moves the Orb to `ERROR`, and
releases audio focus. Text input continues to work.

> **History:** there used to be a cloud STT fallback (`CloudSttEngine`, recording the
> microphone and posting it to ElevenLabs Scribe). It was removed on 2026-09-07 together
> with the whole ElevenLabs integration. It is deliberately not replaced yet — a
> second microphone path is a P0 concern (see the implementation plan, "single mic
> ownership"), and adding one back has to be done as part of that work, not around it.

## Speech output (TTS)

Two tiers, tried in order for **every** utterance:

1. **Gemini native speech generation** (`GeminiVoicePlayer`) — `POST
   /v1beta/models/gemini-…-tts:generateContent` with a `responseModalities: ["AUDIO"]`
   speech config. Returns base64 PCM s16le, 24 kHz mono, streamed and played live. The
   voice name comes from `ApiConfig.PRESET_VOICES` (nine presets, all real Gemini
   prebuilt voices).
2. **Android `TextToSpeech`** — always available, quality depends on the installed
   engine and voice data.

If tier 1 fails, the reason is recorded (missing key, HTTP status, malformed audio,
playback error) and tier 2 runs. JARVIS is never silent without an explanation.

## Diagnostics

`VoiceDiagnostics` is a rolling trail of every voice decision and failure. It is surfaced
in Settings and in the Diagnostics screen (`Section("VOICE OUTPUT")`), which is how you
tell a dead key from quota exhaustion from a network blip.

## Orb integration

`JarvisFloatingOrbService` mirrors the engine's visual state (`IDLE`, `LISTENING`,
`THINKING`, `SPEAKING`, `ERROR`) so the Orb never animates while nothing is actually
happening.
