# API Keys — what JARVIS uses and where it gets it

Last reviewed: 2026-09-07.

## Summary

| Provider | Used for | Key present? | Where it comes from | Required? |
|---|---|---|---|---|
| **NVIDIA NIM** | Reasoning + tool calling (primary brain) | **Yes — hardcoded** | `ApiConfig.NVIDIA_API_KEY` literal in source | No, it is already there |
| **Google Gemini** | Reasoning fallback **and** neural TTS | No | Build-time env var / `local.properties`, or Settings at runtime | Optional |
| Open-Meteo | Weather | — | No key needed | — |
| DuckDuckGo | Web search | — | No key needed | — |

Removed on 2026-09-07 and no longer take keys: **ElevenLabs** (TTS/STT) and the
**Convex** backend proxy. See the commit that removed them for the rationale.

## NVIDIA — hardcoded by owner decision

The NVIDIA NIM key is a string literal in
`app/src/main/java/com/jarvis/app/config/ApiConfig.kt`:

```kotlin
val NVIDIA_API_KEY: String
    get() = "nvapi-…"
```

This is deliberate. It was briefly moved to build-time injection and the owner asked
for it back inline. Accepted consequences:

- **The key is in every APK.** Anyone with the build can extract it with `strings`.
  Rotate it at <https://build.nvidia.com> before sharing an APK publicly.
- **It stays in git history**, including if it is removed again later.
- **It is the single source of truth.** `eval/jarvis_eval.py` reads the key out of
  `ApiConfig.kt` rather than keeping its own copy, so rotation is a one-file change.
- **A runtime key still wins.** A key saved in Settings → API Keys overrides this one
  (`ApiConfig.activeApiKey` / `currentApiKey`).

### Rotating the NVIDIA key

1. Create a new key at <https://build.nvidia.com>, revoke the old one.
2. Replace the literal in `ApiConfig.kt`. Nothing else needs editing.
3. CI's `secret-scan` job reads the new value from `ApiConfig.kt` automatically and
   keeps passing.

## Gemini — optional, never committed

Gemini serves two independent things: the chat fallback chain and native speech
generation (the neural voices). Without a key, JARVIS still works: reasoning runs on
NVIDIA, and speech falls back to the device's own TTS engine.

Get a key at <https://aistudio.google.com/apikey> (starts with `AIza`).

Supply it in any of these ways, in order of precedence:

1. **Environment variable** (what CI uses):
   ```bash
   GEMINI_API_KEY=AIza… ./gradlew assembleDebug
   ```
2. **`local.properties`** (gitignored, for your machine):
   ```properties
   GEMINI_API_KEY=AIza…
   ```
   ```bash
   cp local.properties.example local.properties
   ```
3. **At runtime** — Settings → API Keys. Persisted in SharedPreferences on the device,
   overrides everything above, and survives reinstalls only if you re-enter it.

A comma/newline/semicolon-separated list of Gemini keys is supported; they are rotated
as a pool.

## CI

`.github/workflows/android.yml` has three jobs:

- **secret-scan** — fails the build on any credential-shaped literal *other than* the
  sanctioned NVIDIA key, which it reads out of `ApiConfig.kt` at scan time. The
  workflow file itself contains no key.
- **build** — `assembleDebug` + `testDebugUnitTest` + APK artifact, with
  `GEMINI_API_KEY` injected from repository secrets.
- **eval** — runs `eval/jarvis_eval.py` against the live NVIDIA endpoint to catch
  reasoning/tool-selection regressions. Independent of `build`, so a brain regression
  is visible without blocking the APK.

To set the Gemini secret: repository → Settings → Secrets and variables → Actions →
New repository secret → `GEMINI_API_KEY`.

## Known limitations

- **No cloud STT.** Speech input is Android's `SpeechRecognizer` only. Devices without
  a recognition service get a clear diagnostic and text input still works.
- **Neural voices need a Gemini key.** The nine presets in Settings are real Gemini TTS
  voices; without a key JARVIS speaks with Android TTS instead.
- **The hardcoded NVIDIA key is a shared secret.** There is no per-user key isolation.
