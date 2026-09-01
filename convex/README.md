# JARVIS Backend - Convex Proxy

## 🎯 Purpose

This is the **Convex backend proxy** for JARVIS Android AI Assistant. It provides a secure way to route AI API calls through a server-side proxy, keeping all API keys **completely hidden** from the Android app.

## ⚡ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    JARVIS Android App                          │
│  (No API keys embedded - completely safe to distribute)        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Convex Backend (this directory)                    │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  HTTP Actions:                                           │ │
│  │    /api/llm/chat      - AI chat (Gemini, xAI, etc.)      │ │
│  │    /api/tts/speak     - Text-to-speech (ElevenLabs)     │ │
│  │    /api/tts/voices    - List available voices             │ │
│  │    /api/stt/transcribe - Speech-to-text                    │ │
│  │    /api/preferences   - User settings                     │ │
│  │    /api/health        - Health check                      │ │
│  └─────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Environment Variables (ENCRYPTED at rest):               │ │
│  │    XAI_API_KEY, GEMINI_API_KEY, ELEVENLABS_API_KEY, etc.  │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AI Providers                                │
│  • xAI Grok (xai-)                                           │
│  • Google Gemini (AIzaSy, AQ.)                               │
│  • ElevenLabs TTS (sk_)                                     │
│  • OpenAI (sk-)                                             │
│  • Anthropic Claude (sk-ant-)                               │
│  • Groq (gsk_)                                              │
│  • Mistral (mx-)                                            │
│  • Cerebras (csk-)                                          │
└─────────────────────────────────────────────────────────────┘
```

**Key Benefit:** API keys never leave your Convex backend. The Android app only knows the Convex deployment URL.

---

## 🚀 Quick Setup (5 minutes)

### Prerequisites
- Node.js 18+ installed
- Convex CLI installed: `npm install -g convex`

### 1. Initialize Convex Project

```bash
cd convex
npm install
npx convex login
```

### 2. Deploy the Backend

```bash
npx convex deploy
```

This will output your deployment URL, e.g.:
```
https://happy-animal-123.convex.site
```

**Save this URL!**

### 3. Set Your API Keys (SECURELY)

**IMPORTANT:** Get your own keys from each provider. NEVER use hardcoded keys.

```bash
# Required for basic functionality
npx convex env set XAI_API_KEY "your-xai-key-from-console.x.ai"
npx convex env set GEMINI_API_KEY "your-gemini-key-from-aistudio.google.com"
npx convex env set ELEVENLABS_API_KEY "your-elevenlabs-key"

# Optional providers (if you have access)
npx convex env set OPENAI_API_KEY "your-openai-key"
npx convex env set ANTHROPIC_API_KEY "your-anthropic-key"
npx convex env set GROQ_API_KEY "your-groq-key"
npx convex env set MISTRAL_API_KEY "your-mistral-key"
npx convex env set CEREBRAS_API_KEY "your-cerebras-key"
```

### 4. Configure the Android App

Open `app/src/main/java/com/jarvis/app/config/BackendConfig.kt` and update:

```kotlin
const val WORKER_URL = "https://YOUR_DEPLOYMENT.convex.site"
const val USE_BACKEND = true  // Enable backend proxy
```

Replace `YOUR_DEPLOYMENT` with your actual Convex deployment URL.

### 5. Test the Connection

```bash
# Test health endpoint
curl https://YOUR_DEPLOYMENT.convex.site/api/health
# Should return: {"status":"ok","backend":"convex","timestamp":...}
```

---

## 📁 Project Structure

```
convex/
├── http.ts           # HTTP action endpoints
├── schema.ts        # Convex database schema
├── userSettings.ts  # User settings storage
├── voicePreferences.ts # Voice preferences storage
├── package.json
├── tsconfig.json
└── SETUP.md
```

---

## 🔧 HTTP Endpoints

### GET /api/health
Health check for the backend.

**Response:**
```json
{
  "status": "ok",
  "backend": "convex",
  "timestamp": 1234567890
}
```

### POST /api/llm/chat
Proxy AI chat requests to various providers.

**Request:**
```json
{
  "provider": "xai",
  "model": "grok-3-mini",
  "systemPrompt": "You are JARVIS...",
  "messages": [
    {"role": "user", "content": "Hello"}
  ],
  "tools": [...],
  "toolChoice": "auto"
}
```

**Supported providers:** `xai`, `gemini`, `openai`, `anthropic`, `groq`, `cerebras`, `mistral`, `openrouter`

### POST /api/tts/speak
Generate speech audio using ElevenLabs.

**Request:**
```json
{
  "text": "Hello, I am JARVIS",
  "voiceId": "JBFqnCBsd6RMkjVDRZzb",
  "modelId": "eleven_multilingual_v2",
  "stability": 0.5,
  "similarityBoost": 0.5,
  "style": 0.0
}
```

**Response:** Audio bytes (MP3 format)

### GET /api/tts/voices
List available ElevenLabs voices.

### POST /api/stt/transcribe
Convert speech audio to text.

**Request:** Multipart form with audio file

**Response:**
```json
{
  "text": "Hello JARVIS"
}
```

### POST /api/preferences
Save user preferences.

### GET /api/preferences/:id
Load user preferences.

---

## 🔐 Security Model

### ✅ Secure
- API keys stored as Convex environment variables
- Encrypted at rest in Convex infrastructure
- Never exposed to client (Android app)
- Keys only accessible to your Convex functions

### ❌ Insecure (DON'T DO THIS)
- Hardcoding keys in Android app
- Committing keys to Git
- Using BuildConfig with committed keys
- Storing keys in SharedPreferences without encryption

---

## 📊 Provider Support

| Provider | Key Prefix | Endpoint | Status |
|----------|------------|----------|--------|
| xAI Grok | `xai-` | `/api/llm/chat` | ✅ Supported |
| Google Gemini | `AIzaSy`, `AQ.` | `/api/llm/chat` | ✅ Supported |
| ElevenLabs | `sk_` | `/api/tts/speak`, `/api/stt/transcribe` | ✅ Supported |
| OpenAI | `sk-` | `/api/llm/chat` | ✅ Supported |
| Anthropic | `sk-ant-` | `/api/llm/chat` | ✅ Supported |
| Groq | `gsk_` | `/api/llm/chat` | ✅ Supported |
| Mistral | `mx-` | `/api/llm/chat` | ✅ Supported |
| Cerebras | `csk-` | `/api/llm/chat` | ✅ Supported |
| OpenRouter | `sk-or-` | `/api/llm/chat` | ✅ Supported |

---

## 🛠️ Development

### Local Development

```bash
# Start Convex dev server
npx convex dev

# In another terminal, test endpoints
curl -X POST http://localhost:3210/api/health
```

### Code Generation

After adding new functions, regenerate types:

```bash
npx convex codegen
```

### Deployment

```bash
# Deploy to production
npx convex deploy

# Deploy and open dashboard
npx convex deploy --open
```

---

## 🐛 Troubleshooting

### "AI service not configured on the backend"

Your Convex environment variables aren't set. Check with:

```bash
npx convex env list
```

Set missing keys:

```bash
npx convex env set XAI_API_KEY "your-key"
```

### "Backend connection failed"

Check that `BackendConfig.WORKER_URL` in the Android app matches your deployment URL.

Test connectivity:

```bash
curl https://YOUR_DEPLOYMENT.convex.site/api/health
```

### "403 Forbidden" from AI provider

Your API key may be:
- Invalid
- Expired
- Rate limited
- Missing required permissions

Test your key directly with the provider's API before configuring in Convex.

---

## 📄 License

This backend is part of the JARVIS Android AI Assistant project.

---

## 🙏 Credits

- [Convex](https://convex.dev) - Backend-as-a-Service platform
- All AI providers listed above

---

## 🎯 Next Steps

1. ✅ Deploy Convex backend
2. ✅ Set environment variables (API keys)
3. ✅ Configure Android app with deployment URL
4. ✅ Test end-to-end functionality
5. 🔜 Monitor usage and costs
6. 🔜 Add custom logic for your use case

---

**Need help?** Check [Convex Documentation](https://docs.convex.dev) or [JARVIS Documentation](https://github.com/macaulay-spec/Mirror-)
