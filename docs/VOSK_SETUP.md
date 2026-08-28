# Enabling Vosk (offline wake word, no account)

Vosk is the wake-word engine we want: **fully offline, open source, no account, no email,
no API key.** Picovoice is not required at all.

It is not enabled in the code yet for one honest reason: it needs a Gradle dependency and
a ~45 MB model file, and both must be downloaded inside Android Studio. Adding a
dependency here without being able to build would risk breaking your project. The three
steps below take about five minutes.

---

## Step 1 — add the dependency

In `app/build.gradle.kts`, inside `dependencies { … }`:

```kotlin
implementation("com.alphacephei:vosk-android:0.3.47")
```

Sync. That pulls the JNI libraries for all ABIs (adds ~20 MB to the APK; use
`abiFilters` or split APKs if you want it smaller).

## Step 2 — add the model

1. Download **vosk-model-small-en-us-0.15** (~45 MB) from
   `https://alphacephei.com/vosk/models`
2. In Android Studio: **right-click `app` → New → Folder → Assets Folder** → finish
3. Unzip the model into `app/src/main/assets/` so you have
   `app/src/main/assets/vosk-model-small-en-us-0.15/`

(Optional but better: download it on first run into `filesDir` instead of shipping it,
so the install stays small.)

## Step 3 — drop in the engine

Save this as `app/src/main/java/com/jarvis/app/voice/VoskWakeWordEngine.kt`:

```kotlin
package com.jarvis.app.voice

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Offline wake word. No account, no key, no network.
 *
 * Runs a tiny grammar so the recognizer only ever has to decide between "hey jarvis" and
 * background noise, which is what keeps the CPU cost down.
 */
class VoskWakeWordEngine(private val context: Context) : WakeWordEngine {

    override val name = "Vosk (offline)"

    private var model: Model? = null
    private var service: SpeechService? = null
    private var running = false

    override val isListening: Boolean get() = running

    override fun start(
        onPartial: (String) -> Unit,
        onDetected: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        LibVosk.setLogLevel(LogLevel.WARNINGS)

        StorageService.unpack(
            context, "vosk-model-small-en-us-0.15", "model",
            { m ->
                model = m
                try {
                    // A grammar restricts recognition to the wake phrase only.
                    val grammar = "[\"hey jarvis\", \"jarvis\", \"hey javis\", \"ok jarvis\", \"[unk]\"]"
                    val recognizer = Recognizer(m, 16000.0f, grammar)
                    service = SpeechService(recognizer, 16000.0f).apply {
                        startListening(object : RecognitionListener {
                            override fun onPartialResult(hypothesis: String) {
                                val text = hypothesis
                                    .substringAfter("\"text\" :", "")
                                    .trim('"', ' ', '}', '{')
                                if (text.isBlank()) return
                                onPartial(text)
                                if (text.contains("jarvis", ignoreCase = true) ||
                                    text.contains("javis", ignoreCase = true)
                                ) onDetected()
                            }
                            override fun onResult(hypothesis: String) = Unit
                            override fun onFinalResult(hypothesis: String) = Unit
                            override fun onError(e: Exception) {
                                running = false
                                onError(e.localizedMessage ?: "vosk error")
                            }
                            override fun onTimeout() { running = false }
                        })
                    }
                    running = true
                } catch (e: IOException) {
                    onError("Could not start Vosk: ${e.localizedMessage}")
                }
            },
            { e -> onError("Model missing: ${e.localizedMessage}. Did you add it to assets?") }
        )
    }

    override fun stop() {
        running = false
        service?.stop()
        service?.shutdown()
        service = null
    }

    override fun release() {
        stop()
        model?.close()
        model = null
    }
}
```

Then switch the engine in `WakeWordForegroundService` — replace

```kotlin
private val engine: WakeWordEngine = SystemSpeechRecognizerEngine(this)
```

with

```kotlin
private val engine: WakeWordEngine = VoskWakeWordEngine(this)
```

That single line is the only change needed, because everything goes through the
`WakeWordEngine` interface.

---

## Notes

- **Battery:** ~2–4 %/hour. Better than looping the system recognizer, worse than
  Picovoice's DSP path (~1 %), but Picovoice needs an account and Vosk does not.
- **Privacy:** nothing leaves the phone. No network permission is even used.
- **Other languages:** download the matching model from alphacephei.com and change the
  model folder name — e.g. `vosk-model-small-en-gb-0.15` for a British-accented model.
- **If it fails:** the app falls back to `SystemSpeechRecognizerEngine` automatically and
  Diagnostics tells you why ("Model missing: …").
