# JARVIS — Sideload Android AI Operating Layer

A personal AI control layer you install on **your own phone**. No Play Store required —
this is a **sideloaded APK** built for your device, with the permissions you grant it and
an optional Accessibility service you turn on when you want JARVIS to read the screen and
tap/type inside other apps.

---

## What it does right now

- **Voice-first input:** foreground microphone service + notification, wake phrase
  ("Hey JARVIS"), spoken replies. Text input too.
- **Neural voice output:** Gemini text-to-speech with nine selectable voice presets, and
  automatic fallback to the device's own TTS engine when that is unavailable.
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

---

## Architecture

### There is no backend

JARVIS talks **directly** to the AI providers over HTTPS. There is no proxy server, no
deployment step, and nothing to host.

```
┌────────────────────────┐      HTTPS       ┌──────────────────────────────────┐
│  Android App           │ ───────────────→ │  NVIDIA NIM (reasoning)          │
│                        │                  │  integrate.api.nvidia.com/v1     │
│  · Foreground mic svc  │                  │  OpenAI-compatible chat + tools  │
│  · Accessibility svc   │                  └──────────────────────────────────┘
│  · Room memory         │      HTTPS       ┌──────────────────────────────────┐
│  · Tool/agent layer    │ ───────────────→ │  Google Gemini                   │
│                        │                  │  · chat + tools (generativelanguage)
│                        │                  │  · native speech generation (TTS)│
│                        │                  └──────────────────────────────────┘
└────────────────────────┘
```

> **History:** this repository used to ship a `convex/` directory and a `BackendConfig`
> proxy path. It was removed on 2026-09-07 because it had never been deployed — the app
> was still pointed at the `https://YOUR_DEPLOYMENT.convex.site` placeholder, so every
> call through it would have failed on DNS. The app has always run in direct mode.

### AI providers

| Slot | Provider | What it does |
|---|---|---|
| Reasoning | **NVIDIA NIM** — Nemotron-3-Super / Nano / Ultra | Tool-calling chat. The NVIDIA key is compiled into the app. |
| Reasoning (fallback) | **Google Gemini** — Flash / Pro / Nano Banana Lite | Same OpenAI-compatible protocol. Needs a key of your own. |
| Reasoning (last resort) | **Local engine** | On-device rule engine + canned replies when no network or no key works. |
| Speech output | **Gemini native TTS** | Real voices, streamed and played live. |
| Speech output (fallback) | **Android TextToSpeech** | Always available; quality depends on the device. |
| Speech input | **Android SpeechRecognizer** | On-device/Google recognition. See limitations below. |

Provider order is defined once in `ApiConfig.PROVIDER_FALLBACK_CHAIN`. Every hop is
reported through `VoiceDiagnostics` / the Diagnostics screen rather than failing silently.

### API keys

- **NVIDIA:** the owner's key is embedded in `ApiConfig.NVIDIA_API_KEY`. It is compiled
  into the APK. Treat any APK built from this repository as containing a live key, and
  rotate it if an APK is ever shared publicly.
- **Gemini:** optional. Supply it via the `GEMINI_API_KEY` environment variable,
  `local.properties`, or Settings → API Keys at runtime. Without it, JARVIS runs on
  NVIDIA plus the device's own TTS.

See [`docs/KEYS_SETUP.md`](docs/KEYS_SETUP.md) for the full key matrix and rotation steps.

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

In Android Studio: open the project root, wait for sync, then **Build > Build APK(s)**.

> Gradle needs internet once to download dependencies.

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

## Offline / no-key mode

The app **degrades to a working state with no network at all**:
- Android **SpeechRecognizer** for STT (on-device / offline)
- Android **TextToSpeech** for voice replies
- Built-in **local rule engine** for understanding + device actions
- **Room** local DB for memory

You can build, install, and use JARVIS without configuring anything.

---

## Voice selection

JARVIS speaks with **Gemini native text-to-speech** and lets you pick a voice in
Settings → Voice:

| Preset | Voice | Character |
|---|---|---|
| `jarvis_core` | Puck | Calm, measured — the default |
| `london` | Charon | Firm, low |
| `windsor` | Enceladus | Warm, authoritative |
| `oxford` | Zephyr | Bright, articulate |
| `british_f` | Kore | Clear, precise |
| `warm_f` | Leda | Warm, conversational |
| `crisp_m` | Fenrir | Crisp, energetic |
| `deep_m` | Iapetus | Deep, slow |
| `soft_m` | Umbriel | Soft, understated |

Each id is a real Gemini prebuilt voice, so what you select is what speaks. Pick one and
tap **Preview** to hear it; the choice persists across restarts.

If a preset id is not recognised, playback falls back to Gemini's default voice, and if
Gemini TTS is unavailable entirely it falls back to Android TTS — always with the reason
recorded in the diagnostics trail rather than silently.

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
  built-in `SpeechRecognizer`, which works well on Pixel/Nexus but may be unreliable or
  absent on Samsung/other OEMs. **There is no cloud STT fallback any more** — if a device
  ships without a recognition service, JARVIS reports that plainly and text input still
  works. For production use, upgrade to an offline engine (Vosk) or Picovoice Porcupine.
- **JARVIS cannot be selected as the system default assistant by an in-app prompt.**
  `ROLE_ASSISTANT` is declared `requestable="false"` in AOSP and is reserved for apps that
  implement `VoiceInteractionService` or handle `ACTION_ASSIST`. Settings → Apps → Default
  apps is the only real path, and the app deep-links you there.

Everything else is within "what a person can do with a phone, through the legitimate doors."
