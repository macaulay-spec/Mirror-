# Keys — safe setup

## The rule

**Never put a key in a source file.** This repo is public. Everything goes in
`local.properties`, which is gitignored, and reaches the app through `BuildConfig`.
`ApiConfig` only ever reads `BuildConfig`, so there is no code path where a key is
committed.

## Setup (one time, ~2 minutes)

```bash
cp local.properties.example local.properties
```

Then edit `local.properties`:

```
GEMINI_API_KEY=AIza...your key...
ELEVENLABS_API_KEY=sk_...your key...
```

Rebuild. That's it — `app/build.gradle.kts` reads those lines (falling back to
environment variables of the same name, so CI works too) and injects them into
`BuildConfig`.

## Which key is which

| Service | Real key looks like | Where to get it | Needs |
|---|---|---|---|
| **Gemini** | `AIzaSy…` (39 chars) | `https://aistudio.google.com/apikey` | a personal Google account — free, no card |
| **ElevenLabs** | `sk_…` | `https://elevenlabs.io` → Profile → API key | any email — free tier 10k chars/month |
| **Grok / xAI** | `AQ.Ab8RN6…` | console.x.ai | *already bundled as a fallback* |
| Vosk | — | nothing | **no account at all** |
| Google Cloud STT/TTS | — | cloud console | a **card** — skipped, Vosk replaces it |

⚠️ **Common mix-up:** a key starting with `AQ.` is an **xAI Grok** key, *not* Gemini.
Gemini keys always start with `AIzaSy`. If `activeProvider` reports `gemini` but the key
starts with `AQ.`, requests will fail with a 401/403.

## What each key unlocks

| Key | Unlocks | Without it |
|---|---|---|
| Gemini | conversation, planning, function calling, summaries, web answers | offline intents + device control only |
| ElevenLabs | HD natural voice | Android's built-in TTS |

Everything in **P0** — opening apps, calls, SMS, contacts, device control, the wake word —
works with **zero keys**.

## If a key has leaked

Your keys have been pasted into a chat and (in the case of the bundled ones) committed to
a public repository. If a key was ever exposed, revoke and reissue it:

- **Gemini** → `https://aistudio.google.com/apikey` → delete the key, create a new one
- **ElevenLabs** → Profile → API keys → revoke, create a new one

Then put the new one in `local.properties`. Nothing in the code needs to change.

## Verifying it works

The app will ship a **Settings → Neural Core → Test providers** screen that pings each
configured provider and shows the live status (`OK` / `401` / `quota` / `unreachable`),
so you can see instantly which keys are alive and drop the dead ones.
