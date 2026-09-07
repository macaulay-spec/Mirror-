# JARVIS — Complete Engineering Audit & Master Implementation Plan

**Date:** 2026-09-07
**Branch:** `arena/01a07967-mirror` (from `main` @ `5b775d1`)
**Scope:** Full system understanding → deep audit → integration/cloud audit → research → prioritized implementation plan.
**Method:** Every claim below is backed by a file:line reference in this repository, or by a citation to current vendor documentation. Nothing is assumed from the presence of a file.

---

## 0. Verification environment (read this first — it constrains everything)

This workspace has **no JDK, no Android SDK, and no outbound network** to Maven Central, Gradle's distribution server, Debian apt, or any AI/API host (`repo1.maven.org`, `services.gradle.org`, `integrate.api.nvidia.com`, `generativelanguage.googleapis.com`, `api.elevenlabs.io`, `api.open-meteo.com` all fail to connect). Only `github.com` and `pypi.org` are reachable.

Consequences, stated honestly:

| Capability | Available? | How |
|---|---|---|
| Read/edit/commit/push the real repo | **Yes** | `git` + `gh`, authenticated |
| Compile the app | **Not locally** — **Yes remotely** | `.github/workflows/android.yml` triggers on `arena/**`; GitHub runners have JDK 17 + Android SDK + Maven |
| Run the brain eval | **Not locally** — **Yes remotely** | `.github/workflows/eval.yml` |
| Run unit/instrumented tests | **Not locally** | No JVM. Must be added to CI to be meaningful |
| Exercise runtime behaviour on a device | **No** | **DEVICE VERIFICATION REQUIRED** — flagged per item below |
| Deploy a cloud backend | **No** | No credentials/network for Convex, Firebase, etc. Documented, not fabricated |

**Therefore CI is the build oracle for this engagement.** Every change is pushed to `arena/01a07967-mirror` and verified against a real Gradle build. No change is claimed as "working" unless CI proves it compiles, or unless it is explicitly marked *device verification required*.

**Baseline:** `main` CI is **green** (`Build Jarvis Android App`, run `34057636281`, success, 1m48s). `JARVIS Brain Eval` is **red** (runs `34055578791`, `33987635997`, both failure, exit code 1 in step "Run brain eval"). So: *the project compiles; the brain quality gate does not pass.*

---

## 1. Current architecture

### 1.1 Shape

A single-module (`:app`) Kotlin/Jetpack-Compose Android app, `namespace = com.rork.jarvisaiassistant`, `minSdk 26`, `compileSdk/targetSdk 36`, AGP 8.13.2, Kotlin 2.3.10, KSP, Gradle 8.14.1, configuration-cache on. **88 Kotlin files, ~22,000 lines.** Zero test source sets.

Package layout (four parallel roots, which is itself a finding — see §8.1):

```
com.jarvis.agent      AI/agent core: ai/, dialogue/, memory/, nlu/, orchestrator/, tool/
com.jarvis.android    Platform bindings: accessibility/, device/, overlay/, permissions/, voice/
com.jarvis.app        App shell + services: config/, memory/(Room), notifications/, people/,
                      proactive/, tools/, usage/, voice/, assist/, assistant/, diagnostics/
com.jarvis.core       model/ theme/ ui/
com.jarvis.feature    14 Compose screens
```

### 1.2 Real execution flow (traced, not assumed)

```
JarvisApp.onCreate()                                    [app/src/main/java/com/jarvis/app/JarvisApp.kt:55]
  ├─ ApiConfig.load()          restore prefs (SharedPreferences "jarvis_neural_prefs")
  ├─ AssistantPrefs.load()     alwaysListening=true, readOtpAloud=true by default
  ├─ ToolRegistration.registerAll()   ~70 tools into ToolRegistry (singleton object)
  ├─ PeopleGraph.syncFromContacts()   IO dispatcher, Room import of address book
  ├─ createNotificationChannels()     jarvis_listening, jarvis_briefing
  ├─ ProactiveScheduler.schedule()    AlarmManager.setWindow briefings (if enabled)
  ├─ startOrbIfAllowed()              JarvisFloatingOrbService (specialUse FGS) if canDrawOverlays
  └─ VoiceOrchestratorBridge.create() wires voiceEngine.onSpeechResult → orchestrator.submitUserInput

MainActivity.onCreate()                                 [app/.../app/MainActivity.kt:51]
  ├─ startWakeWordServiceIfAllowed()  microphone FGS if alwaysListening && RECORD_AUDIO
  └─ setContent { manual string-based destination switch }   // NO Navigation component
       "home" → DualModeHost(orchestrator)  |  "history" "device" "memory" "voice"
       isOnboarding → OnboardingScreen | showSettings → SettingsHubScreen

INPUT (typed or spoken)
  → AssistantOrchestrator.submitUserInput(text)         [orchestrator/AssistantOrchestrator.kt:212]
      ├─ IntentRouter.isCancel && busy  → cancelActive() ("Stopped.")
      ├─ barge-in: cancelActive() if turn active or speaking
      └─ scope.launch(Dispatchers.Main) { processUserCommand(text) }
            ├─ DialogueManager.handle(text)             [dialogue/DialogueManager.kt:73]
            │    1. pendingConfirm? yes/no/cancel → ToolRegistry.execute
            │    2. openSlot?  → slot filling (CONTACT / CONTACT_PICK / NUMBER_CHOICE / MESSAGE / APP)
            │    3. IntentRouter.parse() → call_contact | send_sms | device_flashlight |
            │       device_battery | device_time | UNKNOWN
            │    handled=true  → speak + optional confirmation card  → RETURN
            │    handled=false → fall through
            └─ JarvisAIEngine.processCommand()          [ai/JarvisAIEngine.kt:130] withContext(Default)
                 ├─ build systemPrompt (time, battery, active app, LIVE accessibility screen dump)
                 ├─ memoryManager.recallRelevant() + last 10 conversation rows → history
                 ├─ fastPath() exact-match battery/time/torch → RETURN without LLM
                 └─ AgentExecutor.executeTask()         [ai/AgentExecutor.kt:63] withContext(Default)
                      loop MAX_STEPS=4:
                        JarvisApiClient.chatStream(SSE) → onDelta → UI bubble + sentence-level TTS queue
                        no tool_calls → final reply, RETURN
                        risky call (LEVEL_2+) → RETURN pendingConfirmation
                        else ToolRegistry.execute() each call → results appended to history as role="user"
                        final iteration disables tools to force a summary
  → ReplySanitizer.sanitize() → addMessage() → voiceEngine.speak()/speakQueued()

OUTPUT (speech)
  JarvisVoiceEngine.speakQueued() → Channel<String> (UNLIMITED) → worker coroutine
    → speakOne(): if Gemini key present → GeminiVoicePlayer.speak() (Gemini TTS → WAV → MediaPlayer)
                  else/fallback        → Android TextToSpeech (QUEUE_FLUSH, utteranceId + Deferred)
```

### 1.3 State & storage

- **No ViewModel layer at all.** `AssistantOrchestrator` is a plain class held `by lazy` on `JarvisApp` for the whole process lifetime, exposing ~10 `StateFlow`s that Compose reads directly. UI state therefore survives configuration change trivially but is unbounded, untestable, and has no `SavedStateHandle`.
- **Room** `jarvis.db`, version 5, 8 entities (`memories`, `conversation`, `chat_sessions`, notifications, `people`, `places`, `app_aliases`, `habits`), 5 DAOs, `exportSchema = false`, **`fallbackToDestructiveMigration()`**.
- **SharedPreferences** in 4 separate stores (`jarvis_neural_prefs`, `jarvis_assistant_prefs`, `jarvis_proactive`, `jarvis_places`, `jarvis_voice`). DataStore is a declared dependency but unused.
- **In-memory singletons:** `NotificationRepository` (StateFlow of active notifications), `MessagingAutomation.pendingDraft`, `JarvisAccessibilityService.instance`, `EntityMemory`.

### 1.4 Services

| Component | Type | Manifest | Notes |
|---|---|---|---|
| `WakeWordForegroundService` | FGS `microphone`, START_STICKY | ✔ | Continuous `SpeechRecognizer` loop scanning for "jarvis" |
| `JarvisFloatingOrbService` | FGS `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | ✔ correct for API 34+ | `TYPE_APPLICATION_OVERLAY` `ComposeView` |
| `JarvisNotificationListener` | `NotificationListenerService` | ✔ | RemoteInput reply, OTP read-aloud, sensitive-content hiding |
| `JarvisAccessibilityService` | `AccessibilityService` | ✔ | 11 tools: screen read/find/click/type/scroll/tap/swipe/back/home/recents |
| `ProactiveReceiver` | BroadcastReceiver | ✔ | Briefings, boot re-arm, battery-low |

### 1.5 AI integration

All inference goes to **one OpenAI-compatible transport** (`JarvisApiClient`, raw OkHttp + `org.json`; Ktor is a declared-but-unused dependency). Two endpoints:
- NVIDIA NIM `https://integrate.api.nvidia.com/v1/chat/completions` (SSE streaming + tool calls)
- Gemini `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` (OpenAI-compat shim)

Provider chain: `gemini_flash → gemini_pro → gemini_lite → nvidia_super → nvidia_llama → nvidia_mistral → nvidia_ultra`, with a multi-key Gemini pool that rotates on HTTP 429. Two-tier routing by utterance length/keywords (`ApiConfig.providerForUtterance`).

---

## 2. Current capabilities — what actually works

Verified by code trace. "Works" here means *the code path is complete and coherent*, not that it was observed on hardware.

**Genuinely solid:**
1. **Compiles cleanly on modern toolchain** — CI green, targetSdk 36, Kotlin 2.3.10, Compose BOM 2026.02.01, Room+KSP. This is a real asset; many repos at this size do not build.
2. **Tool registry with ~70 registered actions** and a hard-fail on duplicate IDs (`ToolRegistry.register` uses `require(!tools.containsKey(...))`) plus an alias table for model-hallucinated names. This is good defensive design.
3. **Real OpenAI-style function calling** — `ToolSchema.forOpenAI()` emits actual JSON schemas from the registry, and `streamNVIDIA` correctly reassembles streamed `tool_calls` fragments by `index`. Multi-step loop feeds tool results back to the model.
4. **`DialogueManager` slot filling** — genuinely good: contact disambiguation, multi-number choice, "which Mumsi?", pronoun resolution via `EntityMemory` ("call her back"), confirmation state, and a proper "user changed their mind" escape hatch.
5. **Risk-tiered confirmation** — `LEVEL_2+` tools pause and surface a confirm card from *both* the typed-intent path and the model path (`AgentExecutor` reads the tool's own registered risk rather than hardcoding LEVEL_0). One confirmation system, correctly unified.
6. **Turn generation / cancellation** — `turnGeneration` + `isCurrent()` guards on every async callback, barge-in, "Stop." handling. This is the correct pattern and it is applied consistently.
7. **Streaming-to-speech** — sentence-boundary splitting of the SSE delta stream into a TTS queue, so audio starts before generation finishes.
8. **Accessibility screen grounding** — the live screen dump is injected into the system prompt with explicit anti-hallucination and anti-nagging instructions; `clickNode` climbs to the nearest clickable ancestor then falls back to a real gesture tap. Both are correct, non-obvious fixes.
9. **`screenSignature()`** verification — checks the screen actually changed after an action instead of trusting `performAction()`'s boolean.
10. **RemoteInput notification reply**, OTP extraction/read-aloud, sensitive-content hiding.
11. **Real time understanding** — `TimeParser` + authoritative current-time injection into the system prompt, so "tomorrow 8am" resolves correctly.
12. **Weather via Open-Meteo** (keyless), **web answers via DuckDuckGo Instant Answer** (keyless) — but see §3.11, the good one is shadowed.
13. **`BackendConfig.isBackendReady`** guard — correctly turns a silent misconfiguration into an explicit error at the LLM proxy entry point.
14. **Notification channels, audio-focus handling, `USAGE_ASSISTANT` audio attributes, bounded recognizer restart backoff.**

**Present but degraded:** wake word, cloud TTS, cloud STT, memory recall, proactive briefings (see §3, §4).

---

## 3. Broken functionality — what does not work, and exactly why

### P0-A — Live API credentials are committed to the repository (3 places)
- `app/src/main/java/com/jarvis/app/config/ApiConfig.kt:96-100` — `NVIDIA_API_KEY` and `ELEVENLABS_API_KEY` getters **return hardcoded literals**, directly contradicting the file's own docstring ("NO hardcoded keys are embedded in the source code", line 14).
- `.github/workflows/android.yml:33-37` — both keys written into `local.properties` in plaintext.
- `eval/jarvis_eval.py:113` — `DEFAULT_NVIDIA_API_KEY` literal, a third copy.

These keys are in the git history of a repository that has been public. **They must be treated as compromised and rotated by the owner.** This is not a style issue: anyone can drain the NVIDIA quota and the ElevenLabs subscription, and ElevenLabs keys can be used to clone voices.

### P0-B — Build-time key injection does not work, so CI-built APKs have no ElevenLabs key
`app/build.gradle.kts:21-27` populates `buildConfigField` from **`System.getenv(...)`**, but the CI step writes **`local.properties`** (`.github/workflows/android.yml:33`). Gradle does not export `local.properties` entries as environment variables, and the build script never parses that file. Result in every CI-built APK:
- `BuildConfig.GEMINI_API_KEY` = `""` → `ApiConfig.geminiKeys` empty → the entire Gemini branch of the provider chain is dead.
- `BuildConfig.ELEVENLABS_API_KEY` = `""` → `CloudSttEngine.listenAndTranscribe` returns null immediately with *"Cloud STT unavailable: no ElevenLabs key configured"* (`CloudSttEngine.kt:56-60`).
- `BuildConfig.TOOLKIT_SECRET_KEY` = `""`.

**Net effect: the cloud-STT safety net for devices without a Google recognizer is inert in every distributed build.** Those users have no voice input at all. The app only appears to work because `ApiConfig` hardcodes the NVIDIA key (P0-A) — the two bugs mask each other.

### P0-C — Cloud TTS can essentially never succeed, and when it times out it double-speaks
`app/src/main/java/com/jarvis/app/voice/GeminiVoicePlayer.kt:50` wraps the **entire** operation in `withTimeoutOrNull(2500L)`: HTTP request + base64 decode + file write + `MediaPlayer.prepare()` + **the full playback wait loop** (`while (!isDone) delay(100)`, line 148). Any utterance longer than ~2 seconds of audio cannot finish inside 2.5 s, so:
1. `withTimeoutOrNull` returns `null` → `speak()` returns `false`.
2. `JarvisVoiceEngine.speakOne()` (`JarvisVoiceEngine.kt:141-149`) sees `cloudStarted == false` and **also** calls `speakWithAndroidTts(text)`.
3. The `MediaPlayer` is *not* stopped when the coroutine is cancelled (it plays on its own thread), so the user hears **the neural voice and the robot voice overlapping**.

Compounding it:
- `connectTimeout(2s)` / `readTimeout(3s)` (lines 26-28) are far too short for TTS synthesis.
- `modelsToTry = listOf("gemini-2.0-flash", "gemini-2.5-flash-preview-tts")` (line 68) tries **`gemini-2.0-flash` first**, which does not support the `AUDIO` response modality — a guaranteed wasted round trip that consumes most of the 2.5 s budget before the real TTS model is even tried. Per current Google documentation the TTS-capable models are `gemini-3.1-flash-tts-preview` (current, has a free tier), `gemini-2.5-flash-preview-tts` and `gemini-2.5-pro-preview-tts`.
- `tempFile.deleteOnExit()` (line 132) is meaningless on Android — there is no JVM exit. **Every spoken sentence leaks a WAV file into `cacheDir` forever.**
- `isDone` (line 143) is a plain `var` mutated from `MediaPlayer` callbacks on another thread with no `@Volatile` — a visibility race.

### P0-D — Room wipes all user memory on every schema change
`app/src/main/java/com/jarvis/app/memory/AppDatabase.kt:37` — `.fallbackToDestructiveMigration()` with `version = 5` and `exportSchema = false`. For an assistant whose entire value proposition is long-term memory, **every future schema bump silently destroys all memories, conversation history, learned nicknames, the people graph, saved places and habits.** There are no migrations and no migration tests. (`fallbackToDestructiveMigration()` is also deprecated in Room 2.8 in favour of the explicit `dropAllTables` overload.)

### P1-A — `READ_CALL_LOG` is used but never declared
`PermissionAndSetupHelper.REQUIRED_PERMISSIONS` includes `Manifest.permission.READ_CALL_LOG` (line 42), and `ContactsResolver.recentCalls` reads `CallLog.Calls`. **The permission is absent from `AndroidManifest.xml`** (verified: 0 occurrences in the manifest, 3 files reference it in code). Consequences:
- Requesting a permission that is not declared → the system silently denies it and can invalidate the whole batch request.
- The `read_call_log` tool can **never** succeed; it always returns "grant the call-log permission in Settings", and Settings has nothing to grant.
- `requiredFeature`-style Play review flags for requesting undeclared permissions.

### P1-B — The permission-result callback is empty, so nothing happens after the user grants
`MainActivity.kt:32-34`:
```kotlin
private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { }
```
On **first install** the sequence is: `onCreate` → mic not yet granted → wake-word service not started → permission dialog → user grants mic → **the result is discarded**. `startWakeWordServiceIfAllowed()` and `PeopleGraph.syncFromContacts()` only run in the `else` branch of `requestCorePermissions()` (line 186), i.e. only when *everything was already granted before the request*. So hands-free wake word does not work until the user kills and relaunches the app, and the contacts import never runs on the first session. This precisely matches the reported symptom "the Orb is there but nothing happens".

### P1-C — All-or-nothing permission gating
`requestCorePermissions()` only proceeds when **all ~16** permissions are granted. Deny CAMERA (or SMS, or CALENDAR, or LOCATION) and the always-on wake-word service and contacts sync are never (re)started — even though RECORD_AUDIO and READ_CONTACTS were granted. Voice is the core feature; it must not be hostage to the camera permission.

### P1-D — Two `SpeechRecognizer`s fight over the microphone
`VoiceOrchestratorBridge` sets `voiceEngine.continuousMode = true` on every wake word and every mic toggle (`VoiceOrchestratorBridge.kt:78, 93`) and **nothing ever sets it back to false** except `AUDIOFOCUS_LOSS` or a permission error. Meanwhile `WakeWordForegroundService.resumeWhenIdle()` polls `VoiceBus.engineState` for 120 s waiting for `IDLE`, then calls `startListening()` on *its own* `SystemSpeechRecognizerEngine`. Because `continuousMode` keeps re-arming the voice engine's recognizer, the state never settles to IDLE, and when it briefly does, **two recognizer instances are live** → `ERROR_RECOGNIZER_BUSY` → both back off → hands-free dies. There is no single owner of the microphone.

### P1-E — `web_search` cannot answer anything; the good implementation is shadowed
Two implementations exist:
- `ToolRegistry.kt` registers `web_search` that only fires `Intent(ACTION_VIEW, google.com/search?q=…)` and returns *"Web search opened for 'X'"* — the model receives **no content**.
- `WebTools.search()` does a real DuckDuckGo Instant Answer lookup and returns the answer text — but it is only reachable through the tool registered as **`web_extract`**, whose name and description ("Web Open / Search") give the model no reason to prefer it for a question.

So "who is the president of France?" opens a browser instead of answering. The capable code exists and is unreachable under the obvious name.

### P1-F — `weather` ignores the place the user named
`LifeTools.registerWeather()` parses `val place = arg(args, "place", "location", "city", "where")` and then **never uses it**. It resolves latitude/longitude solely from `LocationToolkit.lastKnown()` and fails with *"I need your location for weather"* if GPS is unavailable. `resolvePlace()` is defined in the same file and never called. "What's the weather in Abuja?" cannot work, and weather fails entirely indoors/without location permission — despite Open-Meteo offering a **keyless geocoding API** that would fix this with no credentials.

### P1-G — Task-execution UI state is set and immediately erased
`AssistantOrchestrator.processUserCommand()` lines 326-328:
```kotlin
_currentTaskDescription.value = userInput
_isTaskExecuting.value = true
resetTaskExecution()      // ← nulls the description and sets isTaskExecuting = false
```
`TaskExecutionScreen` observes exactly these flows, so the multi-step task view can never show a description or an executing state from this path. Three consecutive statements where the third undoes the first two.

### P1-H — Brain eval gate is red
`.github/workflows/eval.yml` fails on both recent runs (exit code 1 = regressions found). Beyond the model-quality signal, the harness itself is **architecturally disconnected from the app**: `eval/jarvis_eval.py` declares its own 12-tool list with its own names (`open_app`, `read_notifications`) and its own `required` arrays, while the app registers `app_launch` and `get_recent_notifications` and emits schemas with **no `required` array at all** (`ToolSchema.parametersFor`, lines 133-149). The gate therefore cannot detect a regression in the thing it claims to protect — the real schema.

### P2-A — Dead wake-word intent path
`WAKE_WORD_ACTIVATED` is read in `MainActivity` twice (lines 79, 134) and **never written by any code in the repository** (verified by grep). The wake word actually travels `VoiceBus.wakeWordDetected → VoiceOrchestratorBridge`. So a wake word never brings the app to the foreground, and ~20 lines of intent handling are unreachable.

### P2-B — Duplicate wake-lock renewal loops
`WakeWordForegroundService.onCreate()` launches a renewal loop (`WAKE_LOCK_RENEWAL_MS`, 9 min) **and** `acquireWakeLock()` launches a second, near-identical loop. Two coroutines re-acquiring the same `PARTIAL_WAKE_LOCK` for the lifetime of the service. Redundant, and an indefinite partial wake lock is a battery and Play-policy concern on its own.

### P2-C — Convex backend is 100% dead code, and three client methods ignore the readiness guard
`BackendConfig.USE_BACKEND = false` and `WORKER_URL = "https://YOUR_DEPLOYMENT.convex.site"`. `JarvisApiClient.chat()` correctly guards via `isBackendReady`, but **`saveVoicePreferences()`, `loadVoicePreferences()` and `fetchElevenLabsVoices()` do not** — they unconditionally build URLs against the placeholder host and would fail with a DNS error. ~700 lines of `convex/*.ts` plus these client paths ship in a repo where none of it can run.

### P2-D — `ProactiveReceiver.speak()` is unreliable and leaks
It constructs a fresh `TextToSpeech` inside a `CoroutineScope(Dispatchers.Main).launch` that is **not** tied to the `goAsync()` pending result (lines 79-84). The receiver can return and the process be reclaimed before TTS binds — briefings go silent. Each invocation also creates a `TextToSpeech` that is never `shutdown()`.

### P2-E — Android 16 "Safer Intents" exposure for the evening briefing
`ProactiveScheduler.ACTION_EVENING_BRIEFING` (`com.jarvis.app.action.EVENING_BRIEFING`) is dispatched to `ProactiveReceiver`, but the receiver's `<intent-filter>` in the manifest declares **only** `MORNING_BRIEFING`. Today this works because the `PendingIntent` is explicit; under Android 16's Safer Intents enforcement, intents that do not match the target component's declared filter are candidates for blocking (logged as `Intent does not match component's intent filter`). Cheap to fix now, expensive to debug later.

---

## 4. Incorrect implementations — exists, but built badly

1. **Tool schemas are under-specified.** `ToolSchema.parametersFor()` emits only `{"type": …}` per property: **no `required` array, no per-property `description`, no `enum`s**. For a 70-tool surface this is the single biggest driver of wrong-tool and missing-argument selection — and it is exactly what the (currently red) eval measures. The eval's own hand-written schema *does* include `required`, which is strong evidence the author knew this mattered.
2. **Ambiguous, overlapping tools exposed simultaneously.** `open_app` (DeviceToolExecutors) *and* `app_launch` (ToolRegistry); `read_notifications` (NotificationListener) *and* `get_recent_notifications` (ToolRegistry); `reply_notification` *and* `reply_to_notification`; `send_sms` *and* `send_message` (both SMS!); `toggle_setting` *and* `toggle_wifi`/`toggle_bluetooth`/`device_flashlight`. The model is asked to choose between near-identical descriptions — the same class of bug the codebase already fixed once for `battery_info` vs `device_battery`.
3. **Tool results are returned to the model as `role = "user"`** (`AgentExecutor.kt:194`) with the text `"Tool execution results: …"`, and the assistant turn is polluted with `"\n[Action: Calling tools: …]"` (line 141). The OpenAI protocol has a dedicated `role = "tool"` + `tool_call_id` for this. The current approach works loosely but degrades multi-step reasoning and leaks scaffolding into the conversation.
4. **No sampling or reasoning controls on the request.** `streamNVIDIA`/`executeNVIDIA` send only `model`, `messages`, `stream`, `tools`, `tool_choice`. No `max_tokens`, no `temperature`, and — critically for Nemotron 3 — **no `chat_template_kwargs: {enable_thinking: false}`**. Nemotron 3 Super/Ultra default to reasoning mode; independent measurements put Nemotron 3 Super at ~16 s average latency with thinking on versus ~4 s for Nano. `ApiConfig`'s comment claiming "~480ms live stream" is only achievable with reasoning disabled. **This is very likely the dominant cause of "JARVIS is slow to answer."**
5. **Stale/incorrect model catalogue.** `NVIDIA_MISTRAL_MODEL = "mistralai/mistral-nemotron"` and `NVIDIA_LLAMA_MODEL = "meta/llama-3.2-11b-vision-instruct"` are old entries in a chain that is walked on every failure. The current NIM catalogue offers better free tiers: `nvidia/nemotron-3-nano-30b-a3b` (fast agentic tier), `nvidia/nemotron-3.5-lightning-30b-a3b`, `nvidia/z-ai/glm-5.2`, `nvidia/moonshotai/kimi-k2.6`. The two Nemotron 3 IDs already in the file (`nvidia/nemotron-3-super-120b-a12b`, `nvidia/nemotron-3-ultra-550b-a55b`) **are correct and current**.
6. **`ApiConfig` is a mutable global singleton pretending to be configuration.** Public `var` fields (`userName`, `voiceEngineType`, `selectedVoiceId`, `customApiKey`) mutated from UI, read from network threads, persisted by hand in 6 separate `save*` methods. `activeApiKey` / `currentApiKey` / `originalHasAI` / `hasAI` are four overlapping notions of "do we have a key" with subtly different logic. `autoDetectProvider` maps any `sk_`-prefixed key to provider `"elevenlabs"`, which then flows into `activeProvider` → `chatDirect` sends an **ElevenLabs key to the Gemini endpoint** and eats a 401 before falling through to NVIDIA.
7. **Custom keys stored in plaintext SharedPreferences** (`ApiConfig.saveCustomApiKey`). `androidx.security:security-crypto` / Keystore-backed storage is the correct home for a user-supplied credential. Related: `android:usesCleartextTraffic="true"` is set application-wide with no `network_security_config` justification.
8. **Memory recall is keyword overlap, not retrieval.** `MemoryRepository.recall()` loads **every** memory row into memory (`memoryDao.snapshot()`, no LIMIT), splits the query on `\W+`, and counts substring hits. No FTS, no embeddings, no recency decay beyond a tiebreaker, O(n) per turn and unbounded growth. `memoryDao.search()` uses `LIKE %q%`. For an assistant marketed on memory this is the weakest subsystem relative to its importance.
9. **Unbounded in-memory message list.** `_messages` grows for the whole session and is re-copied (`toMutableList()` + full reassign) on **every streamed token** (`AssistantOrchestrator.kt:354-358`) — an O(n²) recomposition driver on long replies. No paging, no snapshotFlow, no diffing.
10. **`ReplySanitizer.sanitize()` maps blank input to `"Done."`** — so an empty model reply is spoken aloud as "Done.", and legitimate code blocks lose their fences because the sanitizer strips ``` unconditionally.
11. **`device_flashlight` risk inconsistency.** Registered as `LEVEL_1`; `JarvisAIEngine.fastPath()` constructs the same request with `RiskLevel.LEVEL_0`. Harmless today, but it shows risk levels are not sourced from one place.
12. **Phantom `NAVIGATION` category.** `ToolSchema.EXPOSED_CATEGORIES` includes `"NAVIGATION"` with a comment claiming it fixes `navigate_to` being invisible. `navigate_to` is actually registered under `category = "LOCATION"`, and **no tool anywhere registers `NAVIGATION`**. All 16 real categories are already in the set, so the filter is a no-op and the comment documents a fix that never happened.
13. **~11 declared dependencies are entirely unused** (verified by grep for imports across all 88 files): **Ktor** (4 artifacts — all networking is OkHttp), **Koin**, **Coil** (2 artifacts), **CameraX** (4 artifacts), **DataStore Preferences**, **Navigation Compose**, **kotlinx-serialization-json** (plus the serialization compiler plugin, applied for nothing). `material-icons-extended` is used but pulls the full ~2,000-icon set.
14. **~1,800 lines of dead Compose/UI code**, zero references anywhere (verified per-symbol): `OnboardingFlow.kt` (520), `SettingsScreen.kt` (285), `PermissionsScreen.kt` (252), `WebSearchScreen.kt` (205), `DeviceActionsScreen.kt` (179), `SplashScreen.kt` (120), `ScreenAwarenessScreen.kt`, plus `StreamingSpeaker.kt` and `app/voice/JarvisVoice.kt` (a whole second ElevenLabs voice-selection implementation superseded by `ApiConfig.PRESET_VOICES`). Also dead: `ToolSchema.forGemini()`, `AgentExecutor.buildStepDescription()`, `LifeTools.resolvePlace()`, `SpeechOutput` (a third TTS wrapper), `JarvisVoiceEngine.splitIntoSentences()` (its only caller was replaced by an inline comment "No splitting to avoid 10-second pauses").
15. **Three parallel TTS abstractions** (`JarvisVoiceEngine`'s internal TTS, `GeminiVoicePlayer`, `SpeechOutput`) and **two voice-preference models** (`ApiConfig.PRESET_VOICES` / `OLD_PRESET_VOICES` / `JarvisVoice.BRITISH_CANDIDATES`). `OLD_PRESET_VOICES` is retained alongside the new list with an entry whose `id` is `"Aoede"` but whose `name` is `"Rex"`.
16. **Voice preset names do not match the provider.** `PRESET_VOICES` offers `"Eva"` and `"Rex"` (British). Gemini TTS ships 30 named voices — Zephyr, Puck, Charon, Kore, Fenrir, Leda, Orus, Aoede, Callirrhoe, Autonoe, Enceladus, Iapetus, Umbriel, Algieba, Despina, Erinome, Algenib, Rasalgethi, Laomedeia, Achernar, Alnilam, Schedar, Gacrux, Pulcherrima, Achird, Zubenelgenubi, Vindemiatrix, Sadachbia, Sadaltager, Sulafat. **Neither "Eva" nor "Rex" exists.** `GeminiVoicePlayer` silently maps `rex → Charon` and `eva → Aoede`, so the "Deep British Classic JARVIS" preset delivers an American voice. Accent on Gemini TTS is controlled by `language_code` (e.g. `en-GB`), which the code never sets.
17. **`CloudSttEngine`'s docstring is wrong.** It describes "the Vercel AI Gateway (`xai/grok-stt`) through the Rork proxy"; the implementation posts to `https://api.elevenlabs.io/v1/speech-to-text` with `xi-api-key`. The code is coherent, the documentation is not — and `ApiConfig.TOOLKIT_URL`/`TOOLKIT_SECRET_KEY` are hardcoded/unused leftovers from the abandoned Rork path.
18. **Release build is not releasable.** `isMinifyEnabled = false` and `signingConfig = signingConfigs.getByName("debug")` — a debug-signed, unshrunk release. `proguard-rules.pro` exists but is never exercised.
19. **`JarvisApp.onCreate()` does unguarded work on the main thread.** `ToolRegistration.registerAll()` touches `PackageManager` and `JarvisAccessibilityService.registerTools()`; `orchestrator` is `by lazy` and first touched from `MainActivity.onCreate()` on the main thread, constructing `JarvisAIEngine` → `JarvisMemoryManager` → Room. `appScope` and the orchestrator's scope are never cancelled and there is no `ProcessLifecycleOwner` awareness.

---

## 5. Missing capabilities

**Assistant fundamentals**
1. **No semantic memory.** No embeddings, no FTS4/FTS5, no summarisation of old conversations into durable facts, no forgetting/decay policy, no per-memory provenance. Memories are stored but not *used* intelligently.
2. **No proactive intelligence beyond two timed briefings.** `HabitEntity` exists in the schema and is **never written or read** — the habit-learning feature is schema-only. No location-based triggers, no "you usually leave for work at 8:10, traffic is bad", no notification triage.
3. **No real web answering.** No search API that returns ranked results to the model; no page-content extraction (`web_extract` opens a browser, it does not extract). JARVIS cannot research anything.
4. **No image understanding path that is reachable.** `ImageAnalyzer.kt` and `screen_capture` exist, and `camera`/`media` permissions are declared, but CameraX is an unused dependency and there is no vision model call in `JarvisApiClient` — the multimodal capability is declared, not delivered.
5. **No user-visible error recovery.** Failures surface as free-text chat bubbles ("Something went wrong: …"). There is no retry affordance, no offline queue, no degraded-mode indicator, no "tap to configure your key" deep link from the failure itself.
6. **No on-device wake word.** The wake word is a full `SpeechRecognizer` restarted in a loop — high battery cost, requires the Google app, and transcribes *everything* the user says. No Porcupine/openWakeWord/Vosk integration despite `docs/VOSK_SETUP.md` describing it as the plan.
7. **No `VoiceInteractionService`.** To be a real Android assistant (long-press home, "OK Google"-style handoff, `AssistStructure` access) the app needs one. `ROLE_ASSISTANT` is *not requestable* by apps (`requestable="false"` in AOSP `roles.xml`); it is granted only through Settings → Default apps → Digital assistant. So `AssistantRoleManager.request()` and the `createRequestRoleIntent` branch of `openDefaultAssistantSettings()` are no-ops on most devices — the existing fallback to `ACTION_VOICE_INPUT_SETTINGS` is the path that actually works and should be the primary one.

**Engineering fundamentals**
8. **Zero tests.** No `src/test`, no `src/androidTest`. Nothing protects `TimeParser`, `ReplySanitizer`, `OtpExtractor`, `MemoryRepository.recall`, `IntentRouter.parse`, `ToolSchema` output, provider fallback, or Room migrations — all of which are pure, deterministic, and trivially unit-testable off-device.
9. **No CI static analysis.** No lint gate in CI (`lint { abortOnError = false; checkReleaseBuilds = false }`), no detekt/ktlint, no dependency audit, no secret scanning.
10. **No crash reporting or structured logging.** Diagnostics is a manual in-app screen; `VoiceDiagnostics` is an in-memory ring. Nothing survives a crash.
11. **No dependency injection.** Koin is declared and unused; everything is `object` singletons, which is why nothing is testable.
12. **No schema export / migration story** (`exportSchema = false`).

---

## 6. Integration audit & opportunities

### 6.1 Existing integrations — status

| Integration | Status | Evidence |
|---|---|---|
| NVIDIA NIM (OpenAI-compat, SSE + tools) | **Working** — model IDs `nemotron-3-super-120b-a12b` / `nemotron-3-ultra-550b-a55b` verified current | but no `enable_thinking:false` → severe latency (§4.4) |
| NVIDIA `mistralai/mistral-nemotron` | **Suspect / likely stale** | not in current featured catalogue |
| NVIDIA `meta/llama-3.2-11b-vision-instruct` | **Dated**; no vision call path exists to use it | §4.5 |
| Gemini LLM (OpenAI-compat shim) | **Dead in practice** — key never injected | §P0-B |
| Gemini TTS | **Broken** — wrong model order, 2.5 s timeout around playback | §P0-C |
| ElevenLabs STT (Scribe) | **Code correct, key never injected** → inert | §P0-B |
| ElevenLabs TTS | **Not implemented** — `JarvisVoice` (the ElevenLabs TTS path) is dead code, refs=0 | §4.14 |
| Rork Toolkit gateway (`xai/grok-tts`, `grok-stt`) | **Abandoned but still wired** — `TOOLKIT_URL` hardcoded, `TOOLKIT_SECRET_KEY` empty, only referenced in stale comments | §4.17 |
| Convex backend (`convex/*.ts`) | **Dead** — `USE_BACKEND=false`, placeholder URL, never deployed; 3 client methods ignore the readiness guard | §P2-C |
| Open-Meteo weather | **Working but keyless-geocoding unused**; `place` argument ignored | §P1-F |
| DuckDuckGo Instant Answer | **Working but shadowed** behind `web_extract` | §P1-E |
| Android `SpeechRecognizer` STT | Working where the Google app exists | no offline/cloud parity |
| Android `TextToSpeech` | Working (this is what users actually hear) | §P0-C |
| AccessibilityService | **Strong** — best-implemented subsystem | §2.8 |
| NotificationListenerService | **Strong** — RemoteInput reply, OTP, sensitivity | §2.10 |
| Contacts / Calendar / AlarmClock / SMS / Usage stats | Working, correct permission checks | — |
| Home Assistant, LiveKit, Google STT/TTS | **Stub constants, all empty strings** (`ApiConfig.kt:355-361`) | aspirational only |

### 6.2 What I can legitimately add myself (no credentials required)

These are real, keyless, and verifiable — not decoration:

1. **Open-Meteo Geocoding API** (`geocoding-api.open-meteo.com/v1/search`) — makes `weather` honour a named place ("weather in Lagos") with no key and no GPS. Fixes §P1-F properly.
2. **Open-Meteo daily forecast + air quality** — extends the morning briefing from "it is 24°C" to a genuinely useful outlook, keyless.
3. **Wikipedia REST Summary API** (`en.wikipedia.org/api/rest_v1/page/summary/…`) — keyless entity answers, far better than DDG's sparse `AbstractText`.
4. **DuckDuckGo promoted as the real `web_search`** — rewire the registry entry to `WebTools.search()` so answers come back to the model instead of opening a browser. Fixes §P1-E with existing code.
5. **Open exchange rates** (`open.er-api.com/v1/latest/…`) — keyless, useful for a Nigerian user (NGN conversions).
6. **NVIDIA NIM catalogue refresh + `enable_thinking` control + `max_tokens`/`temperature`** — same key the user already has; add `nvidia/nemotron-3-nano-30b-a3b` as a genuine low-latency tier. Biggest single latency win available.
7. **Gemini TTS modernisation** — `gemini-3.1-flash-tts-preview` (has a free tier), correct model order, `language_code: "en-GB"` for a real British JARVIS, and **streaming TTS via `:streamGenerateContent`** so first-audio latency drops to roughly first-chunk latency. Requires a Gemini key the user supplies; the code must degrade gracefully without one.
8. **CI as a test/verification platform** — GitHub runners give me a real JDK+SDK build, unit-test execution, lint, APK-size tracking and secret scanning. This is genuine cloud infrastructure I can configure today.
9. **Generated audio assets for voice previews** — I can synthesise reference samples in this environment and commit them so the Voice Room can preview voices offline. (Bundled assets, not a runtime API.)
10. **Generated launcher/onboarding artwork** — image generation is available here; the current launcher is a single `drawable-xxxhdpi` PNG plus a vector.

### 6.3 What I cannot provide — documented, not fabricated

| Needed | Why I cannot do it | Exactly what the owner must do |
|---|---|---|
| Rotate the leaked NVIDIA + ElevenLabs keys | Revocation is an account-owner action | NVIDIA: build.nvidia.com → API Keys → revoke + reissue. ElevenLabs: Profile → API Keys → revoke + reissue. Then set them as **GitHub Actions secrets** `NVIDIA_API_KEY`, `ELEVENLABS_API_KEY`, `GEMINI_API_KEY`. |
| Deploy the Convex backend | No Convex account, no credentials, no network egress from this sandbox | `npx convex dev`, `npx convex env set GEMINI_API_KEY …` (+ NVIDIA/ELEVENLABS), `npx convex deploy`, then set `BackendConfig.WORKER_URL` and `USE_BACKEND = true`. `convex/SETUP.md` already documents this. |
| Porcupine / Picovoice wake-word licence | Commercial licence key required | Either supply an AccessKey, or use the keyless open-source route (openWakeWord / Vosk) which needs only a model file. |
| Firebase / Crashlytics, Play Console, signing keystore | Account + credentials I do not have and must not invent | Owner creates the project and uploads `google-services.json`; keystore goes into CI secrets, never the repo. |
| On-device verification | No emulator, no hardware, no SDK | Every runtime item below is tagged **DEVICE VERIFICATION REQUIRED**. |

---

## 7. Cloud opportunities (where cloud genuinely improves JARVIS)

The goal is capability, not decoration. Ranked by value:

1. **Server-side key custody (highest value).** The current design ships API keys inside the APK. Anyone can `apktool` it and steal the quota. The Convex backend already implements the right shape (`convex/http.ts`: `/api/llm/chat`, `/api/tts/speak`, `/api/stt/transcribe`, `/api/preferences`, `/api/health`) and `BackendConfig.isBackendReady` already guards the LLM path. Completing this removes P0-A permanently, enables key rotation without an app update, and lets the owner meter/limit usage per device. **Blocker: deployment credentials (§6.3).**
2. **Memory sync & backup.** `android:allowBackup="false"` means a phone loss erases every memory JARVIS has ever learned. A cloud-synced memory store (Convex `userSettings`/`voicePreferences` tables already exist as a pattern) turns memory from device-local to durable. Requires auth — currently there is **no authentication layer at all**, which is the prerequisite.
3. **Retrieval quality.** Embeddings for memory recall need a model call. `nvidia/nemotron-3-embed-1b` exists in the NIM catalogue on the same key the app already uses — semantic recall becomes available with **no new credential**, just a new endpoint call and a vector column. This is the highest-leverage quality upgrade for "JARVIS remembers me".
4. **Streaming neural TTS.** Gemini `streamGenerateContent` for TTS cuts time-to-first-audio from seconds to sub-second and is on the free tier for `gemini-3.1-flash-tts-preview`.
5. **Heavy reasoning off-device.** Already correct in spirit (Nemotron Ultra 550B for deep requests). Needs the latency controls in §4.4 to be usable in a voice loop.
6. **Analytics/quality loop.** The eval harness is the right idea but must run against the **real generated schema** and be wired to CI so a prompt change that makes JARVIS dumber cannot merge. No third-party analytics needed — GitHub Actions is the cloud here.

What I will **not** do: add a cloud service to look modern, add a dependency I cannot verify, or write code against a credential I do not have.

---

## 8. Architecture improvements

### 8.1 Module & package structure
Four parallel roots (`agent`, `android`, `app`, `core`, `feature`) with duplicated concepts across them — `android/voice/JarvisVoiceEngine` vs `app/voice/{GeminiVoicePlayer,SpeechOutput,JarvisVoice,VoiceBus,VoiceSettingsManager}`; `agent/memory/JarvisMemoryManager` vs `app/memory/*`; `agent/tool/*` vs `app/tools/*`; `android/device/DeviceToolExecutors` vs `app/tools/DeviceToolkit`. **Consolidate to one home per concept.** Do not create a multi-module split yet — one module is correct at 22k lines; the win is package coherence, not Gradle modules.

### 8.2 Introduce a ViewModel layer
`AssistantOrchestrator` should stay as the app-scoped *engine*, but UI state must move behind a `ViewModel` with `SavedStateHandle`, and the message list must become a paged/streamed Room `Flow` rather than an ever-growing `StateFlow<List<…>>` reassigned per token. This fixes §4.9 and makes the UI testable.

### 8.3 One microphone owner
Introduce a single `AudioSessionController` that owns mic state across `WakeWordForegroundService`, `JarvisVoiceEngine` and `CloudSttEngine`. Invariant: **exactly one recognizer alive at any time**, with an explicit handoff (wake → conversation → back to wake). Fixes §P1-D.

### 8.4 Configuration becomes immutable + injected
Replace the mutable `ApiConfig` object with an immutable `JarvisConfig` data class produced once at startup, plus a `SecretStore` (Keystore/EncryptedSharedPreferences) for user keys and a `ProviderResolver` with **one** definition of "do we have a usable key". Remove the four overlapping `hasAI`/`activeApiKey`/`currentApiKey`/`originalHasAI` accessors. Fix `autoDetectProvider` so an ElevenLabs key is never offered to an LLM endpoint.

### 8.5 Tool layer: one registry, one schema source of truth
- Derive the function-calling schema **from the tool definition itself** (each `ToolDefinition` carries typed parameters with names, types, descriptions, `required`, and enums) instead of the parallel `ARG_HINTS` string map that can silently drift.
- Delete the duplicate/ambiguous tools; keep one canonical `open_app`, `read_notifications`, `reply_to_notification`, and one SMS path.
- Return tool results as `role = "tool"` with `tool_call_id`.
- Make the eval harness **generate its tool list from the same source** so the gate tests reality.

### 8.6 Data layer
Room with `exportSchema = true`, checked-in schemas, real `Migration`s, and a `MigrationTest`. Replace `fallbackToDestructiveMigration()`. Add FTS5 (and later a vector column) for memory recall. Consolidate the 5 SharedPreferences stores into DataStore (already a declared dependency) or delete the dependency.

### 8.7 Remove dead weight
Delete the ~1,800 lines of unreferenced UI, the unused TTS/voice duplicates, `forGemini()`, `buildStepDescription()`, the `WAKE_WORD_ACTIVATED` path, the phantom `NAVIGATION` category, and the abandoned Rork/Convex client paths (or finish them — but not both). Remove the 11 unused dependencies. This is not cosmetic: every dead branch is a place a future fix can land by mistake.

### 8.8 Repository hygiene
Root contains 6 large loose PNGs (1.6 MB + 2.0 MB), 8 `grok_*.jpg` (~2 MB), `designs/` (~16 MB), two byte-identical 51 KB prompt files (`…MASTER_PROMPT-1.md` / `-2.md`), scratch scripts (`fix.sh`, `patch_aiengine.sh`, `test_ai.sh`, `replacement.txt`), and Rork/Expo leftovers (`metadata.json`, `.gitignore` entries for `node_modules`, `metro.config.js`, `app.json`). `.gitignore` also contains `app/src/main/java/**/Config.kt` — a rule that would **silently exclude a real source file** from version control. Move design assets to Git LFS or out of the repo; delete the scratch files; fix the ignore rule.

---

## 9. Performance improvements

1. **Disable Nemotron reasoning for conversational turns** (`chat_template_kwargs.enable_thinking=false`) and reserve reasoning-on for the deep tier. Expected: seconds → sub-second. *The single biggest win.*
2. **Add a genuine fast tier** (`nvidia/nemotron-3-nano-30b-a3b`) for greetings, small talk and single-tool commands; route only `DEEP_THINK_HINTS` to Super/Ultra.
3. **Set `max_tokens`** (a voice reply never needs 8,192) and a sane `temperature`; cap tool-loop steps by cost, not just count.
4. **Stream TTS** (`streamGenerateContent`) and play chunk-by-chunk instead of synthesise-all-then-play.
5. **Fix the per-token O(n) list rebuild** in the streaming bubble (§4.9) — use `SnapshotStateList` / mutate-in-place or a `derivedStateOf` over an index.
6. **Replace the continuous `SpeechRecognizer` wake loop** with a real on-device wake-word model. Current design restarts Google's recognizer forever, holds a `PARTIAL_WAKE_LOCK` indefinitely, and transcribes all ambient speech — battery and privacy cost with no accuracy benefit.
7. **Bound memory recall** — `snapshot()` has no LIMIT and loads the whole table every turn. Add FTS + a hard cap.
8. **Right-size HTTP timeouts** — 2 s connect / 3 s read on TTS is guaranteed failure; 3 s connect on the streaming LLM client is too aggressive for cellular. Use per-operation budgets and a single shared `OkHttpClient` (currently 5 separate clients with separate connection pools).
9. **Enable R8 + resource shrinking for release**, and a Baseline Profile for cold-start.
10. **Move `ToolRegistration.registerAll()` and first Room touch off the main thread** in `JarvisApp.onCreate()`.

---

## 10. UI/UX improvements

1. **Android 16 edge-to-edge is mandatory and largely unhandled.** Only `DualModeHost` applies `statusBarsPadding()`/`navigationBarsPadding()` (lines 193-194). `MainActivity` never calls `enableEdgeToEdge()`, the theme still sets `statusBarColor`/`navigationBarColor` (ignored under edge-to-edge), and `ChatHistoryScreen`, `DeviceControlScreen`, `MemoryPeopleScreen`, `VoiceRoomScreen`, `VoiceSelectionScreen`, `SettingsHubScreen`, `OnboardingScreen` have no inset handling → content under system bars on API 35/36.
2. **`android:screenOrientation="portrait"` is ignored on ≥600dp displays for targetSdk 36.** Both activities lock portrait; on a tablet/foldable the app will be resized and rotated anyway, with no adaptive layout. Remove the lock and make the layout adaptive, or accept the breakage knowingly.
3. **Predictive back is mandatory at targetSdk 36.** `android:enableOnBackInvokedCallback` is not declared. Compose `BackHandler` is used in `MainActivity` — verify it maps to `OnBackInvokedCallback` on activity-compose 1.12 and that no `onBackPressed()` override survives.
4. **Navigation is hand-rolled string switching** (`currentDest == "history"`) while `navigation-compose` sits unused. No deep links, no back-stack semantics, no animation contract, no type safety.
5. **Permission UX is a wall.** ~16 permissions requested at once, no rationale, no contextual asking, and (per §P1-B) no post-grant action. Replace with staged, explained, feature-triggered requests, and make the setup screen show real live status (it partly does via `SetupScreen`).
6. **No feedback when the assistant is degraded.** If no key is configured the user gets a chat bubble telling them to go to Settings. There should be a persistent, tappable status affordance (the Diagnostics screen exists but is not reachable from the main UI — it is a separate unexported Activity with no launcher entry found in the Compose tree).
7. **Voice presets lie about accent** (§4.16). A user who picks "Rex — Deep British Classic JARVIS" gets an American voice. Either set `language_code: en-GB` on Gemini TTS, or rename the presets to voices that actually exist.
8. **No voice previews.** `VoiceRoomScreen`/`VoiceSelectionScreen` list 7-9 voices with no way to hear them. Bundled preview assets (§6.2.9) fix this offline.
9. **Task execution view is unreachable/empty** because of §P1-G — a whole screen built for multi-step visibility that can never populate.

---

## 11. Android compatibility (targetSdk 36) — what must change

| Requirement | Current state | Action |
|---|---|---|
| Edge-to-edge, no opt-out | Unhandled except one screen | `enableEdgeToEdge()` + insets on every screen |
| Predictive back mandatory | `enableOnBackInvokedCallback` absent | Declare + verify `BackHandler` path |
| Orientation ignored ≥600dp | `portrait` locked on both activities | Remove lock, adaptive layout |
| FGS type + justification | **Correct** — `microphone`, `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Keep; verify runtime start restrictions |
| Background FGS start restrictions | Orb + wake services started from `JarvisApp.onCreate`/`MainActivity` (foreground) — OK; **`ProactiveReceiver` on `BOOT_COMPLETED` may be blocked** from starting a `microphone` FGS | Audit boot path; use `WorkManager`/exact-alarm exemptions correctly |
| Safer Intents | `EVENING_BRIEFING` action not in receiver filter | Declare both actions |
| `BODY_SENSORS` → `android.permissions.health` | Declared, unused | **Remove** |
| `USE_EXACT_ALARM` | Declared, unused (`setWindow` is used) — Play-policy-restricted | **Remove** |
| `SCHEDULE_EXACT_ALARM` | Declared, unused | **Remove** |
| `QUERY_ALL_PACKAGES` | Declared; Play requires justification | Prefer `<queries>` with explicit intents; remove if possible |
| `usesCleartextTraffic="true"` | On, with no need | Off + `network_security_config` |
| 16 KB page size (native libs) | No NDK/native libs of our own; **transitive native deps must be checked** | Verify all `.so`s are 16 KB-aligned before Play submission |
| `POST_NOTIFICATIONS` (API 33+) | Declared and in `REQUIRED_PERMISSIONS` | Keep; ensure FGS notification visibility is explained to the user |
| Debug-signed release | `signingConfig = debug` | Real keystore via CI secrets |

---

## 12. Testing strategy — how each subsystem gets verified

Given no local JVM, tests are **written to be run by CI**, and CI is extended to execute them.

| Subsystem | Test type | Runs where | Verifies |
|---|---|---|---|
| `TimeParser` | JVM unit | CI | "tomorrow 8am", "in 20 minutes", "Thursday 3pm", DST, midnight rollover |
| `ReplySanitizer` | JVM unit | CI | tool-JSON stripping, stack traces, fences, blank→fallback |
| `OtpExtractor` | JVM unit | CI | OTP formats, false positives, sensitive-content policy |
| `IntentRouter.parse` | JVM unit (Robolectric/mock Context) | CI | call/SMS/torch/battery/time routing, "tell me a joke" ≠ SMS |
| `DialogueManager` slot filling | JVM unit | CI | disambiguation, number choice, pronoun resolution, confirm/cancel/change-of-mind |
| `ToolSchema` | JVM unit + **golden-file** | CI | every registered tool produces a valid schema with `required`; snapshot catches drift |
| `ToolRegistry` | JVM unit | CI | no duplicate IDs, all aliases resolve, no unregistered category |
| Provider fallback / key resolution | JVM unit with a fake HTTP transport | CI | 429 rotation, chain order, ElevenLabs key never sent to an LLM endpoint |
| Room migrations | `MigrationTest` (androidTest, emulator) | CI (needs emulator) **or** local | v5→v6 non-destructive; schema JSON exported |
| `MemoryRepository.recall` | JVM unit | CI | ranking, cap, no full-table scan |
| Brain eval (model tool selection) | Python, live NVIDIA | CI | **regenerated from the real schema**; gates prompt/model changes |
| Build + lint + APK size | Gradle | CI | compile, lint clean, size regression budget |
| Secret scanning | gitleaks/trufflehog | CI | no credential can ever be committed again |
| Compose UI | `createComposeRule` screenshots | CI | insets, confirmation card, streaming bubble |
| Accessibility / notifications / overlay / wake word / TTS audio / calls / SMS | **DEVICE VERIFICATION REQUIRED** | Manual, checklist | documented per-item; cannot be automated here |

A `docs/DEVICE_VERIFICATION_CHECKLIST.md` will list every runtime behaviour that needs a physical device, with exact reproduction steps, so nothing is silently assumed to work.

---

## 13. Implementation order

### P0 — Critical (correctness, security, data loss)
| # | Item | Files | Verified by |
|---|---|---|---|
| P0-1 | Purge hardcoded credentials (3 copies); wire real injection: parse `local.properties` **and** env in `build.gradle.kts`, CI uses `${{ secrets.* }}`, add secret scanning; document rotation | `ApiConfig.kt`, `app/build.gradle.kts`, `.github/workflows/*.yml`, `eval/jarvis_eval.py` | CI build + scan |
| P0-2 | Fix cloud TTS: correct model order (`gemini-3.1-flash-tts-preview` first), separate synthesis timeout from playback, stop MediaPlayer on cancel, delete temp WAV, `@Volatile`, sane HTTP timeouts | `GeminiVoicePlayer.kt`, `JarvisVoiceEngine.kt` | CI + **device** |
| P0-3 | Stop Room from destroying memory: `exportSchema = true`, real migrations, migration test, remove destructive fallback | `AppDatabase.kt` + new `migrations/`, `schemas/` | CI |
| P0-4 | Declare `READ_CALL_LOG`; make the permission result callback actually do something; decouple wake-word/contacts start from all-or-nothing granting | `AndroidManifest.xml`, `MainActivity.kt`, `PermissionAndSetupHelper.kt` | CI + **device** |
| P0-5 | Single microphone owner; stop the two-recognizer fight; bound `continuousMode` lifetime | new `AudioSessionController`, `VoiceOrchestratorBridge.kt`, `WakeWordForegroundService.kt`, `JarvisVoiceEngine.kt` | CI + **device** |
| P0-6 | LLM request controls: `enable_thinking=false` for the fast tier, `max_tokens`, `temperature`; add Nemotron Nano fast tier; drop stale Mistral/Llama entries | `JarvisApiClient.kt`, `ApiConfig.kt` | CI + eval |
| P0-7 | Fix §P1-G (`resetTaskExecution()` ordering) | `AssistantOrchestrator.kt` | CI + unit test |

### P1 — Major (capability + reliability)
| # | Item |
|---|---|
| P1-1 | Typed tool parameters in `ToolDefinition`; schema with `required`/descriptions/enums generated from the definition; delete `ARG_HINTS` |
| P1-2 | De-duplicate tools: one `open_app`, one `read_notifications`, one `reply_to_notification`, one SMS path; return tool results as `role="tool"` + `tool_call_id` |
| P1-3 | Rewire `web_search` → `WebTools.search()` (real answers); add Wikipedia REST + Open-Meteo geocoding so `weather` honours a named place |
| P1-4 | Eval harness regenerated from the real schema; make the gate green and meaningful |
| P1-5 | Android 16 compatibility pass: `enableEdgeToEdge()` + insets everywhere, predictive back, remove portrait lock, Safer Intents, remove unused/Play-risky permissions, cleartext off |
| P1-6 | JVM unit test suite + CI test job (the pure-logic table in §12) |
| P1-7 | Fix the empty/duplicated voice presets; set `language_code: en-GB` for a genuine British JARVIS; delete `OLD_PRESET_VOICES` |
| P1-8 | Immutable config + `SecretStore` + one `ProviderResolver`; fix `autoDetectProvider` |

### P2 — Important (structure + product quality)
| # | Item |
|---|---|
| P2-1 | Delete ~1,800 lines of dead UI + unused TTS/voice duplicates + dead methods |
| P2-2 | Remove 11 unused dependencies; single shared `OkHttpClient`; consider dropping `material-icons-extended` for used-icons-only |
| P2-3 | Introduce ViewModel layer; paged/streamed messages; fix per-token list rebuild |
| P2-4 | Adopt `navigation-compose` (already declared) or remove it; type-safe routes |
| P2-5 | Memory recall: FTS5 now, embeddings (`nvidia/nemotron-3-embed-1b`) next; bound `snapshot()` |
| P2-6 | Fix `ProactiveReceiver` TTS lifecycle (`goAsync`-scoped, reuse one engine) |
| P2-7 | Real signing config for release + R8 + resource shrinking + Baseline Profile |
| P2-8 | Repository hygiene: move/delete large binaries, duplicate prompt docs, scratch scripts, fix the `**/Config.kt` ignore rule |
| P2-9 | Decide Convex: finish it (guarded by `isBackendReady` everywhere) or delete it — no half-wired backend |
| P2-10 | On-device wake word (openWakeWord/Vosk keyless path) replacing the recognizer loop |

### P3 — Enhancements
| # | Item |
|---|---|
| P3-1 | `VoiceInteractionService` → genuine default-assistant eligibility, `AssistStructure` access |
| P3-2 | Semantic memory + habit learning (make `HabitEntity` real) + proactive triggers |
| P3-3 | Vision path (screen capture → multimodal model) to deliver the declared camera/vision capability |
| P3-4 | Streaming TTS via `streamGenerateContent` |
| P3-5 | Voice preview assets in the Voice Room; generated launcher/onboarding art |
| P3-6 | Crash reporting + structured logging (owner must supply the project) |
| P3-7 | Cloud memory sync + authentication layer |
| P3-8 | Adaptive layouts for tablets/foldables |

---

## 14. Preservation commitments (Phase 9 compliance)

These subsystems are **good and will not be rewritten**, only extended:
- `DialogueManager` slot filling and `EntityMemory` pronoun resolution
- `JarvisAccessibilityService` (click-ancestor climb, gesture fallback, `screenSignature` verification)
- `JarvisNotificationListener` (RemoteInput reply, OTP, sensitivity)
- Turn-generation cancellation / barge-in in `AssistantOrchestrator`
- Risk-tiered confirmation, unified across typed-intent and model paths
- `TimeParser`, `ReplySanitizer`, `PeopleGraph` scoring, `MessagingAutomation`'s verify-then-confirm design
- `BackendConfig.isBackendReady` guard pattern

Replacement protocol for everything else: understand → confirm it is actually defective (with the evidence cited above) → map dependents → design → implement → verify dependents → only then delete the old path.

---

## 15. Standing honesty rules for this engagement

1. Nothing is reported as working unless CI proves it compiles, or it is explicitly marked **DEVICE VERIFICATION REQUIRED**.
2. No credential, subscription, API or service will be fabricated. Where one is genuinely unobtainable here, §6.3 states exactly what the owner must supply.
3. The leaked keys in §P0-A are treated as compromised. They will be removed from the repository, **not** reused, and rotation is flagged as an owner action.
4. All work lands on `arena/01a07967-mirror` in this repository — no parallel project, no report-only deliverable.
