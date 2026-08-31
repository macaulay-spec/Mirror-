# JARVIS Convex Backend Setup

## Quick Start (5 minutes)

### 1. Install Convex CLI globally (one-time)

```bash
npm install -g convex
```

### 2. Login to Convex

```bash
cd convex
npx convex login
```

This opens a browser. Create a free account or login.

### 3. Deploy the backend

```bash
npx convex deploy
```

This uploads your Convex functions and gives you a deployment URL like:
`https://happy-animal-123.convex.site`

**Copy this URL!**

### 4. Set your API keys as secrets

```bash
npx convex env set XAI_API_KEY "AQ.Ab8RN6LVmURwb8YsZu0kcyO1cI5BHpsBen2Re1h4Sv31VnJhGA"
npx convex env set ELEVENLABS_API_KEY "your-elevenlabs-key-here"
```

Optional (if you have them):
```bash
npx convex env set GEMINI_API_KEY "AIzaSy..."
npx convex env set OPENAI_API_KEY "sk-..."
npx convex env set ANTHROPIC_API_KEY "sk-ant-..."
npx convex env set GROQ_API_KEY "gsk_..."
```

### 5. Update the Android app

Open `app/src/main/java/com/jarvis/app/config/BackendConfig.kt` and replace:

```kotlin
const val WORKER_URL = "https://YOUR_DEPLOYMENT.convex.site"
```

With your actual deployment URL:

```kotlin
const val WORKER_URL = "https://happy-animal-123.convex.site"
```

### 6. Build and test

```bash
./gradlew assembleDebug
```

Install the APK on your phone and test!

---

## What's Running

Your Convex backend provides these endpoints:

| Endpoint | Method | What it does |
|---|---|---|
| `/api/llm/chat` | POST | Proxies AI requests (Gemini, xAI, OpenAI, etc.) |
| `/api/tts/speak` | POST | ElevenLabs text-to-speech |
| `/api/tts/voices` | GET | List available ElevenLabs voices |
| `/api/stt/transcribe` | POST | Speech-to-text |
| `/api/preferences` | GET/POST | Save/load voice preferences |
| `/api/health` | GET | Health check |

---

## Troubleshooting

### "AI service not configured on the backend"

Your Convex environment variables aren't set. Run:
```bash
npx convex env list          # Check what's set
npx convex env set XAI_API_KEY "your-key"   # Set a key
```

### "Backend connection failed"

Check that `BackendConfig.WORKER_URL` matches your actual Convex URL.

### Testing locally (without Convex)

Set `BackendConfig.USE_BACKEND = false` in BackendConfig.kt to use direct API calls.
Note: This puts API keys in the APK — only for testing!

---

## Architecture

```
Android App
    │
    │ (no API keys)
    │
    ▼
┌─────────────────────┐
│   Convex Backend    │
│   (holds all keys)  │
└─────────┬───────────┘
          │
          │ (API keys here)
          │
          ▼
┌─────────────────────┐
│   AI Providers      │
│   • xAI Grok        │
│   • Google Gemini   │
│   • OpenAI          │
│   • ElevenLabs TTS  │
│   • Anthropic       │
└─────────────────────┘
```

API keys are stored as Convex environment variables (encrypted at rest).
The Android app never sees or embeds any API keys.
