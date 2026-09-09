# JARVIS — Key Setup Guide

## API keys are injected at build time.

The app sources keys from environment variables (CI) or `local.properties`
(local development, gitignored). There are **no hardcoded fallback keys**
in source code.

## Step 1: Create your local key file

```bash
cp local.properties.example local.properties
```

Open `local.properties` and fill in the keys you have.
The file is gitignored — it will never be committed.

```
# NVIDIA NIM (primary AI provider — starts with nvapi-)
NVIDIA_API_KEY=paste_your_nvidia_key_here

# Google Gemini (starts with AIzaSy) — optional fallback
GEMINI_API_KEY=

# ElevenLabs HD voice (starts with sk_) — optional
ELEVENLABS_API_KEY=

# Rork Toolkit gateway (managed cloud TTS — optional)
TOOLKIT_URL=
TOOLKIT_SECRET_KEY=
```

## Step 2: Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Provider Priority

JARVIS uses the first working key it finds:

1. Key entered in-app (Settings → Access Control)
2. `NVIDIA_API_KEY` in local.properties → NVIDIA NIM (primary)
3. `GEMINI_API_KEY` in local.properties → Google Gemini (fallback)

## Key Auto-Detection

Paste any key in Settings → Access Control and JARVIS identifies the provider:

| Prefix   | Provider         |
|----------|-----------------|
| `nvapi-` | NVIDIA NIM      |
| `AIzaSy` | Google Gemini    |
| `sk_`    | ElevenLabs       |
| `sk-`    | OpenAI           |
| `sk-ant-`| Anthropic Claude |
| `gsk_`   | Groq             |
| `csk-`   | Cerebras         |
| `sk-or-` | OpenRouter       |
| `mx-`    | Mistral          |

## ElevenLabs Voice (Optional)

Free key at https://elevenlabs.io/api
Without it, JARVIS uses Android TTS with a British male voice selection.

## Verify

After installing: **Settings → DIAGNOSTICS → TEST ALL PROVIDERS**
You'll see live OK / FAIL with the real error for each provider.
