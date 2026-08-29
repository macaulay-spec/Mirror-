# JARVIS — Key Setup Guide

## Your xAI Grok key is already configured in the build system.

GitHub's secret scanner correctly blocks raw API keys from being committed.
Your key is safe — add it only to `local.properties` on your own machine.

---

## Step 1: Create your local key file

```bash
cd Mirror-
cp local.properties.example local.properties
```

Open `local.properties` and fill in the keys you have.
The file is gitignored — it will never be committed.

```
# xAI Grok (starts with AQ.)
XAI_API_KEY=paste_your_xai_key_here

# Google Gemini (starts with AIzaSy) — optional fallback
GEMINI_API_KEY=

# ElevenLabs HD voice (starts with sk_) — optional
ELEVENLABS_API_KEY=
```

---

## Step 2: Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Provider Priority

JARVIS uses the first working key it finds:

1. Key entered in-app (Settings → Access Control)
2. `XAI_API_KEY` in local.properties → xAI Grok
3. `GEMINI_API_KEY` in local.properties → Google Gemini

---

## Key Auto-Detection

Paste any key in Settings → Access Control and JARVIS identifies the provider:

| Prefix   | Provider         |
|----------|-----------------|
| `AQ.`    | xAI Grok         |
| `AIzaSy` | Google Gemini    |
| `sk-ant-`| Anthropic Claude |
| `sk-`    | OpenAI           |
| `gsk_`   | Groq             |
| `csk-`   | Cerebras         |
| `sk-or-` | OpenRouter       |

---

## ElevenLabs Voice (Optional)

Free key at https://elevenlabs.io/api
Without it, JARVIS uses Android TTS with a British male voice selection.

---

## Verify

After installing: **Settings → DIAGNOSTICS → TEST ALL PROVIDERS**
You'll see live OK / FAIL with the real error for each provider.
