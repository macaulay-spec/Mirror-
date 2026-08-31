# JARVIS — Sideload Android AI Operating Layer

A legitimate-but-free-thinking personal AI control layer you install on **your own phone**.
No Play Store required. This is a **sideload APK** built for your device, with full permissions
available and an optional Accessibility service you turn on when you want JARVIS to
read the screen and tap/type inside other apps.

---

## What it does right now

- **Voice-first input:** foreground microphone service + notification, wake phrase
  ("Hey JARVIS"), TTS replies. Text input too.
- **Read + reply to messages anywhere**: SMS (full), WhatsApp / Telegram / Instagram via
  Notification reply first, then Accessibility screen typing, then deep-link/open draft.
- **Full device control:** open apps (fuzzy), battery, storage, connectivity/Wi‑Fi,
  volume, brightness, DND, flashlight, media, back/home, notification shade.
- **Location:** "where am I" via GPS/network/Geocoder.
- **Contacts:** look up, call via dialer, open chat.
- **Calendar:** create events.
- **Files & camera:** open the system file picker (SAF), open camera.
- **Memory:** remember, forget, recall, full wipe (Room + local DB).
- **Full mode:** Accessibility service (OFF by default) to read screen, tap,
  type into fields, press back/home.
- **Permissions dashboard** in Settings: request all runtime permissions and open every
  special Settings page (notification access, accessibility, overlay, write-settings,
  usage stats, battery exemptions, location, files, install unknown apps).
- **ElevenLabs TTS** with voice selection — choose from dozens of premium voices.

---

## Architecture

### Backend (Convex)

**API keys live on the server, not in the app.** JARVIS uses Convex as its backend —
a serverless platform that holds all API keys as environment variables (secrets) and
proxies requests through HTTP actions. The Android app never sees or embeds any API keys.

```
┌──────────────────┐     HTTPS      ┌──────────────────────┐     HTTPS     ┌─────────────┐
│  Android App     │ ──────────────→ │  Convex Backend      │ ────────────→ │  AI APIs    │
│  (no API keys)   │                 │  (holds all keys)    │               │  Gemini     │
│                  │ ←────────────── │                      │ ←──────────── │  xAI Grok   │
│  Calls proxy URL │                 │  /api/llm/chat       │               │  OpenAI     │
│                  │                 │  /api/tts/speak      │               │  ElevenLabs │
│                  │                 │  /api/tts/voices     │               │  Anthropic  │
│                  │                 │  /api/stt/transcribe │               │  Groq       │
│                  │                 │  /api/preferences    │               │             │
└──────────────────┘                 └──────────────────────┘               └─────────────┘
```

**Backend endpoints (Convex HTTP actions):**

| Endpoint | Method | Description |
|---|---|---|
| `/api/llm/chat` | POST | Proxies LLM requests to Gemini, xAI, OpenAI, Anthropic, etc. |
| `/api/tts/speak` | POST | ElevenLabs TTS — returns audio MP3 |
| `/api/tts/voices` | GET | Lists available ElevenLabs voices |
| `/api/stt/transcribe` | POST | Speech-to-text via ElevenLabs |
| `/api/preferences` | GET/POST | Load/save user voice preferences |
| `/api/health` | GET | Health check |

### Deploy the backend

```bash
cd convex
npm install

# Initialize Convex project
npx convex init

# Set your API keys as Convex environment variables (secrets)
npx convex env set GEMINI_API_KEY <your-key>
npx convex env set XAI_API_KEY <your-key>
npx convex env set ELEVENLABS_API_KEY <your-key>
npx convex env set OPENAI_API_KEY <your-key>        # optional
npx convex env set ANTHROPIC_API_KEY <your-key>      # optional
npx convex env set GROQ_API_KEY <your-key>           # optional

# Deploy
npx convex deploy
```

Then update `BackendConfig.WORKER_URL` in `app/src/main/java/com/jarvis/app/config/BackendConfig.kt`
with your Convex deployment URL (e.g., `https://your-deployment.convex.site`).

### AI Provider Model

The app supports multiple AI providers through the Convex backend proxy:

| Provider | Use case |
|---|---|
| **Gemini** (default) | Best free tier, great function calling |
| **xAI Grok** | Fast, good reasoning |
| **OpenAI GPT-4o** | Most capable, paid |
| **Anthropic Claude** | Best at following instructions |
| **Groq** | Fastest inference, free |
| **ElevenLabs** | Premium TTS with voice selection |
| **Zero-key mode** | On-device STT + TTS + local rule engine |

---

## Build it on your machine

You need:
- Android Studio (Hedgehog or newer), or Android SDK + JDK 17 + Gradle 8.5+
- `local.properties` with `sdk.dir=...` pointing to your SDK (Android Studio makes it for you)

From the project root:

```bash
# Command line
./gradlew assembleDebug
# APK appears at:
# app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio: open the project root, wait for sync, then
**Build > Build APK(s)**.

> Note: Gradle needs internet once to download dependencies. Build on your own machine —
> the generation sandbox for this repo had no Android SDK, JDK, or network, so the APK
> could not be compiled inside this workspace.

---

## Install on your phone (sideload)

1. Copy `app-debug.apk` to your phone.
2. Tap it. Allow "install from unknown sources" when prompted.
3. Open JARVIS.
4. Grant: microphone, notifications, SMS, contacts, calendar, camera, photos (as prompted).
5. Tap the **mic FAB** to start the foreground listening service.
6. Say **"Hey JARVIS"** then your command.

---

## Enable "Full mode" (screen control in other apps)

1. Settings → **Open notification access** → enable **JARVIS** (reads + replies to messages).
2. Settings → **Open accessibility settings** → enable **JARVIS** (reads screen, taps, types).
   This is OFF by default and only does work inside apps while enabled.
3. Settings → **Open battery settings** → set JARVIS to Unrestricted if you want it to survive
   longer in the background.

Accessibility is the only way to type/send inside apps that don't expose a reply action,
and it's the closest thing to full phone control. Keep it on only when you want that.

---

## Zero-key mode (default)

The app **works with no API keys at all**. It uses:
- Android **SpeechRecognizer** for STT (on-device / offline)
- Android **TextToSpeech** for voice replies
- Built-in **local rule engine** for understanding + device actions
- **Room** local DB for memory

You can build, install, and use JARVIS exactly as-is.

---

## Voice Selection (ElevenLabs)

JARVIS supports ElevenLabs premium TTS with dozens of voices:

1. Deploy the Convex backend with your ElevenLabs API key
2. In Settings → Voice, tap "Load voices" to fetch available voices
3. Tap a voice name to hear a preview
4. Select your preferred voice — it persists across sessions via Convex
5. All TTS responses will use your selected voice

Voice categories include: premade, cloned, and generated voices with
labels for gender, accent, and use case.

---

## Design System

JARVIS uses a calm, precise visual identity:

- **Base**: deep graphite-blue (#0B0F17), not true black
- **Accents**: ice-blue (#6FD3FF) for presence + soft amber (#F5B87A) for warmth
- **Orb**: one unified visual everywhere — luminous core + single thin ring, state-driven
- **Typography**: clean sans-serif (Inter/system), monospace reserved for technical readouts only
- **Materiality**: translucent glass panels with soft shadows, not flat cyan-bordered cards

---

## Honest limitations (technical, not policy)

- **Cannot read the private database of another app** (e.g. WhatsApp message history).
  Android's kernel sandbox makes that impossible for *any* app. It reads what appears as a
  notification and controls what's on **screen** when Accessibility is on.
- **Cannot hold a microphone silently forever.** A foreground service with a visible
  notification is the legitimate always-listen path; some OEMs may kill it.
- Blindly automating arbitrary in-game controls is fragile: it needs Accessibility, apps
  update, and it can break or be detected. That part is optional and off by default.
- **Wake word reliability varies by device.** The current implementation uses Android's
  built-in SpeechRecognizer, which works well on Pixel/Nexus but may be unreliable on
  Samsung/other OEMs. For production use, upgrade to Vosk (offline) or Picovoice Porcupine.

Everything else is within "what a person can do with a phone, through the legitimate doors."
