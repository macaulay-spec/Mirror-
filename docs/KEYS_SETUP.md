# JARVIS — Key Setup Guide

> **Rewritten 2026-09-07 (audit P0-A / P0-B).** The previous version of this document
> described an xAI Grok integration and a seven-provider auto-detection table that do
> not exist in the code, and claimed a key was "already configured in the build
> system". None of that was true. This version documents what the app actually does.

---

## 1. Action required if you ever built from this repository before 2026-09-07

Two live credentials were committed to this repository in four places
(`app/.../config/ApiConfig.kt`, `.github/workflows/android.yml`,
`eval/jarvis_eval.py`, `test_ai.sh`) and are present in the public git history.

**Treat both as compromised and rotate them now:**

| Key | Where to rotate |
|---|---|
| NVIDIA NIM (`nvapi-…`) | <https://build.nvidia.com> → API Keys → delete the old key, issue a new one |
| ElevenLabs (`sk_…`) | <https://elevenlabs.io> → Profile → API Keys → revoke and reissue |

They have been removed from the working tree and a CI job (`secret-scan` in
`.github/workflows/android.yml`) now fails the build if a credential-shaped literal
is ever committed again. Removing them from *history* additionally requires
`git filter-repo` or a BFG rewrite plus a force-push — an owner decision, since it
rewrites every commit SHA.

---

## 2. What the app actually integrates

| Provider | Used for | Where |
|---|---|---|
| **NVIDIA NIM** | LLM reasoning + tool calling (primary) | `JarvisApiClient` → `https://integrate.api.nvidia.com/v1/chat/completions` |
| **Google Gemini** | LLM reasoning (OpenAI-compatible endpoint) and neural TTS | `JarvisApiClient`, `GeminiVoicePlayer` |
| **ElevenLabs** | Cloud speech-to-text fallback | `CloudSttEngine` → `/v1/speech-to-text` |
| **Open-Meteo** | Weather | keyless — no setup |
| **DuckDuckGo** | Instant-answer web lookup | keyless — no setup |

NVIDIA models in use: `nvidia/nemotron-3-super-120b-a12b` (fast tier),
`nvidia/nemotron-3-nano-30b-a3b` (fallback tier),
`nvidia/nemotron-3-ultra-550b-a55b` (deep-reasoning tier).

---

## 3. Supplying keys

There are two independent paths. Either one is sufficient; the runtime path works
without rebuilding.

### A. Build-time (CI or your own machine)

```bash
cp local.properties.example local.properties
```

```properties
NVIDIA_API_KEY=nvapi-...
GEMINI_API_KEY=AIza...
ELEVENLABS_API_KEY=sk_...
```

`local.properties` is gitignored. `app/build.gradle.kts` resolves each secret in this
order:

1. **Environment variable** — what CI uses.
2. **`local.properties`** — what a developer machine uses.
3. Empty string — the provider reports itself unavailable instead of failing oddly.

> This ordering is the fix for audit **P0-B**. Previously the build script read only
> `System.getenv(...)` while CI wrote `local.properties`, so every key compiled in as
> `""`. That silently disabled the Gemini provider chain and made `CloudSttEngine`
> inert in every distributed APK — which is why devices without a Google speech
> recognizer had no voice input at all.

For CI, set repository secrets (**Settings → Secrets and variables → Actions**):
`NVIDIA_API_KEY`, `GEMINI_API_KEY`, `ELEVENLABS_API_KEY`.
`.github/workflows/android.yml` already passes them to Gradle as environment variables.

### B. Runtime, no rebuild (the path most users want)

**Settings → Access Control** → paste a key. It is stored in SharedPreferences under
`custom_neural_api_key` and survives restarts.

The provider is inferred from the key's shape (`ApiConfig.autoDetectProvider`):

| Key prefix | Detected as | Can drive the LLM? |
|---|---|---|
| `nvapi-` | `nvidia_super` | Yes |
| `AIza` | `gemini_flash` | Yes |
| contains `,` `;` or newline | `gemini_flash` (multi-key pool, rotates on HTTP 429) | Yes |
| `sk_` | `elevenlabs` | **No** — speech-to-text only |
| anything else | `gemini_flash` (best guess) | Yes |

> The `sk_` row is the fix for audit **§4.6**. An ElevenLabs key used to be promoted
> to `activeProvider` and sent to the chat-completions endpoint, guaranteeing a 401
> before the fallback chain started. Voice-only keys are now excluded from LLM
> provider selection.

**Known limitation (tracked as P1-8):** a key from a provider that is not in the table
above (OpenRouter, Groq, Anthropic, …) is guessed as `gemini_flash` and will be sent
to Google's endpoint, where it fails. Only NVIDIA and Gemini keys are supported today.

---

## 4. Provider resolution order

`ApiConfig.activeProvider` picks the first that has a usable key:

1. The user's runtime custom key, **if** it belongs to an LLM-capable provider.
2. `gemini_flash`, if any Gemini key is present (BuildConfig or the custom pool).
3. `nvidia_super`, if an NVIDIA key is present.
4. Empty string → no AI. `JarvisAIEngine.localFallback()` answers with an explicit
   "add a key in Settings → Access Control" message rather than failing silently.

On a request failure the chain walks `ApiConfig.getNextProvider()`:
`gemini_flash → gemini_pro → gemini_lite → nvidia_super → nvidia_nano → nvidia_ultra`.

---

## 5. Without any key at all

JARVIS still works as a local device controller. These paths need no network:

- battery, time, flashlight, volume, brightness, DND, ringer, Wi-Fi/Bluetooth toggles
- opening apps, navigation intents, alarms, timers
- accessibility screen reading, clicking and typing (once the service is enabled)
- notification reading and RemoteInput replies
- Android `TextToSpeech` output and the device `SpeechRecognizer` for input

What is unavailable without a key: free-form conversation, multi-step reasoning, and
cloud STT/TTS.

---

## 6. Verify

Install the build, then open **Settings → Diagnostics**. It reports the resolved
provider (`ApiConfig.getProviderLabel()`), whether accessibility and notification
access are live, and the voice engine state. `VoiceDiagnostics` records every TTS and
STT failure with its real reason — if a voice path is broken it says why rather than
staying silent.
