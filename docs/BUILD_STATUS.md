# Build status — read this first when you get back

All work is on branch `arena/01a045b1-mirror`, pushed to GitHub.

---

## What is DONE (committed)

| Commit | What it delivered |
|---|---|
| `df73e9f` | **Fixed "open X".** The alias table was discarding the app name, so every "open …" launched a random app. New `AppLauncher` scores every launchable app and has a nickname map (chrome, whatsapp, "the bank app"). |
| `df73e9f` | **Calls, SMS, contacts, call log** — tools that did not exist at all. `CALL_PHONE` was in the manifest but never requested. |
| `df73e9f` | **Brightness, DND, ringer, Wi-Fi, Bluetooth** — written years ago in `DeviceToolkit`, never exposed. |
| `df73e9f` | **Permissions actually requested** (phone, contacts, SMS, calendar, location). |
| `68ffca5` | Contacts are **auto-imported**; JARVIS asks "who is mumsi?" once with a picker. |
| `fa06649` | **Keys moved to `local.properties`** (gitignored) → `BuildConfig`. No key in source. |
| `fa06649` | **Android 14 fixes**: foreground-service type, partial photo access. |
| `d2964aa` | **Dialogue manager** — the layer that never existed. Asks, disambiguates, confirms, remembers, repairs. |
| `d2964aa` | **Intent router** — ~35 typed intents, resolved locally and offline. |
| `d2964aa` | **People graph** — imported contacts + learned nicknames and relationships. |
| `d2964aa` | **Confirmations now actually fire** — the CONFIRM card existed in the UI but nothing ever fed it. |
| `d2964aa` | **ElevenLabs failures reported** instead of silently falling back to the robot voice. |
| `d2964aa` | **Always-on "Hey JARVIS" service is started** — it existed but nothing ever launched it. |
| `7386129` | **Diagnostics screen** (Settings → DIAGNOSTICS) — tests every provider, shows the real voice error, contacts imported, assistant status, tool count. |
| `3ed1576` | **Default system assistant** (`ACTION_ASSIST` + `RoleManager`) + **Quick Settings tile** + **Vosk seam**. |
| `397aa88` | **Real function calling.** The model now gets Gemini `functionDeclarations` / OpenAI `tools` generated from the registry, instead of a text list it had to guess JSON against. Any tool added from here on is reachable with no prompt edit. |
| `e630244` | **Everyday actions**: `TimeParser` ("tomorrow 3pm", "next Thursday", "in 20 minutes") replacing `calendar_create`'s hardcoded tomorrow-9am; plus reminders, alarms, timers, navigation, nearby search, share location, clipboard, screenshots. |
| `c8a8fb7` | **JARVIS speaks first**: daily morning briefing (calendar + unread + battery), re-armed after reboot, spoken aloud and left in a notification. Battery-low warning. Notification channels, which were missing entirely. |
| `314a0e8` | **OTPs are actually read.** The listener used to redact anything containing "otp" or a 4-8 digit number, silently killing the feature most wanted. New `read_otp` tool reads codes digit by digit. |
| `ab0f1f9` | **British voice.** Default was American Rachel. Now Daniel, with a fallback chain through the other British voices, and a UK engine voice for the offline fallback. |
| `0595c28` | **Self-configuration by voice**: "brief me at seven", "stop reading my codes", "turn off always listening". Fixed `LOCATION` and `ASSISTANT` being filtered out of the tool schema, which had made navigation invisible to the model. |

---

## What is NOT done yet

| Item | Why it waits |
|---|---|
| Vosk actually running | Needs a Gradle dependency + 45 MB model downloaded in Android Studio. `docs/VOSK_SETUP.md` has the exact 3 steps; the code swap is one line. Doing it here would have added an unverifiable dependency and risked breaking your build. |
| Vosk model wired end to end | The seam and the setup doc exist; step 2 of `docs/VOSK_SETUP.md` is downloading the 45 MB model in Android Studio. |
| VoiceInteractionService (system-drawn overlay, lock-screen) | Phase P1 |
| Streaming TTS + barge-in | Phase P4 |
| OCR, PDF/DOCX, web answers | Phase P2 |
| Routines ("I'm leaving home") | Phase P3 — the scheduler and briefing are the groundwork |
| Onboarding rewrite (10 steps incl. "make JARVIS default") | Next pass |

### Known limits that are Android's rules, not bugs

- Cannot read WhatsApp message *history* — only notifications, which is the sandbox every
  assistant lives in.
- Cannot toggle mobile data, airplane mode or hotspot on a non-rooted phone.
- `FLAG_SECURE` screens (banking apps) are invisible to accessibility and to screenshots.
- Wi-Fi toggle opens the system panel on Android 10+, Bluetooth on Android 13+ — the
  direct setters were removed by Google.

### Two things to confirm on the device

1. **The ElevenLabs voice IDs.** They are per-account. If Daniel is not on the account the
   app walks down the British list on its own and reports which voice it landed on in
   Diagnostics — but the right ID is worth pasting into Settings → Voice once you have it.
2. **The briefing has to be switched on once.** Say "brief me every morning at seven" or
   it stays off. It uses an inexact alarm on purpose, so there is no
   `SCHEDULE_EXACT_ALARM` permission prompt. |

---

## ⚠️ The one thing you must do before it gets smart

**You still do not have a Gemini key.** The key you sent (`AQ.Ab8RN6…`) is an **xAI Grok**
key — Grok's format, not Google's. Gemini keys always start with `AIzaSy`.

1. Go to `https://aistudio.google.com/apikey` (a normal Gmail is fine, no card, no company email)
2. Create a key — it looks like `AIzaSy…`
3. Put it in `local.properties`:
   ```
   GEMINI_API_KEY=AIzaSy...your key...
   ELEVENLABS_API_KEY=sk_1182...the one you sent
   ```
4. Rebuild

**Verify it in 5 seconds:** Settings → **DIAGNOSTICS** → **TEST ALL PROVIDERS**.
You'll see `OK`/`FAIL` per provider with the real error message.

---

## How to verify everything (Android Studio, ~10 minutes)

1. **Build** — `./gradlew assembleDebug`. I could not compile here (no JDK, no network in
   this workspace), so treat the first build as the syntax check. All new files pass a
   brace/paren balance check and a manual review, but the compiler is the real test.
2. **Install** the APK on your Android 14 phone
3. **Grant everything** when prompted (mic, phone, contacts, SMS, calendar, location)
4. **Settings → JARVIS ACCESS CONTROL:**
   - Notification access → **enable JARVIS** (unlocks reading/replying to WhatsApp etc.)
   - Accessibility → **enable JARVIS** (unlocks screen reading, tapping, typing)
   - Battery → **Unrestricted**
5. **Settings → DIAGNOSTICS** — run every check
6. **Default assistant:** Settings → DIAGNOSTICS → **SET JARVIS AS DEFAULT**
7. **Quick Settings:** edit pane → drag the **JARVIS** tile in

### Commands to try

| Say | Expected |
|---|---|
| "Open Chrome" | Chrome opens (this used to open a random app) |
| "Open WhatsApp" | WhatsApp opens |
| "Call mumsi" | Asks *"I can't find mumsi. Who is that?"* → pick → *"What is Amaka to you?"* → then confirms before dialling |
| "Call her back" | Redials, using context |
| "Text Tunde I'm on my way" | Resolves Tunde, drafts, reads it back, asks before sending |
| "Brightness 30" | Sets brightness (after you grant modify-system-settings) |
| "Hey JARVIS" | Wakes from the background |
| Long-press home | Opens JARVIS listening (once it's the default assistant) |

---

## Known honest limits

- **Cannot** read WhatsApp's message history (Android sandbox — no app can)
- **Cannot** toggle mobile data / airplane mode / hotspot (blocked since Android 5)
- **Cannot** see inside banking apps (`FLAG_SECURE`)
- **Wi-Fi** toggle opens the system panel on Android 10+; **Bluetooth** opens settings on
  Android 13+ — both are Android restrictions, not bugs
- Some OEMs (Samsung, Xiaomi) may refuse the assistant gesture → use the Quick Settings tile
