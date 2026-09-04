# JARVIS — Android Studio AI Agent Prompt (COPY & PASTE)
> Paste this whole block into your Android Studio AI assistant (Copilot / JetBrains AI /
> Gemini Code Assist) and let it work on the project. Project root: `jarvis-android/`.
---
```markdown
You are now the JARVIS Android build agent. I want you to make this project compile,
run, and ship a working Debug APK on a local emulator. Do NOT redesign the app. Keep the
existing package structure. Fix whatever is broken, finish what is stubbed, and report
exactly what you changed and what still needs a real device or an API key.
## PROJECT
Root: jarvis-android/
- Kotlin + Jetpack Compose Android app, package: com.jarvis.app
- minSdk 26, targetSdk 35, compileSdk 35, JDK 17, AGP 8.5.2, Kotlin 2.0.20
- Already wired: compose, room (ktx + ksp), datastore, okhttp, camera, icons-extended
- No API keys required to build. All optional keys live in
  app/src/main/java/com/jarvis/app/config/ApiConfig.kt and are empty by default.
## STEP 1 — MAKE IT COMPILE (MOST IMPORTANT)
1. Run a Gradle sync/build and FIX every compile error.
2. Fix version/API mismatches (e.g. deprecated kotlinOptions, missing material icons,
   AGP/Kotlin versions, missing imports). Prefer minimal, safe changes.
3. Add needed `force`/`compose` compiler flags only if required.
4. Tell me the exact build command and the resulting APK path when it builds.
## STEP 2 — RUN ON EMULATOR
1. Give me exact steps to run on an API 34/35 emulator (Pixel profile).
2. Ensure the app launches to the Home screen (status + chat + input + mic FAB).
## STEP 3 — TEST & FIX THE VOICE PIPELINE
- WakeWordForegroundService.java uses Android SpeechRecognizer looking for "jarvis".
  - Confirm it keeps the foreground notification and restarts recognizer on errors.
  - On emulator, speech may fail (no real mic). Add a graceful fallback message:
    "Voice is unreliable here — type your command instead."
- SpeechOutput (TextToSpeech) should speak replies. Handle init not ready.
## STEP 4 — TEST & FIX MESSAGING SEND
- MessagingSender.sendReply() tries: 1) Notification RemoteInput reply, 2) Accessibility
  typing, 3) SMS/deep-link fallback.
- Make sure it does NOT crash when accessibility/notification access is not enabled.
- If the target app isn't installed, return a clean message, never crash.
- Keep the confirm-before-send flow (draft -> "send" -> send).
## STEP 5 — TEST ALL DEVICE TOOLS
Make sure these do not crash when permission is missing; return a helpful message instead:
- open app / "open chrome"
- "search for book" -> opens Chrome with google.com/search
- battery, storage, connectivity/wifi, time
- volume, brightness, DND, flashlight, media controls
- location ("where am i")
- contacts lookup + call
- calendar: add event
- files: SAF picker -> read .txt/.md/json/csv/log
- camera: take picture -> local analysis
- permissions dashboard buttons (open correct Settings pages)
## STEP 6 — FIX ANY UI/UX ISSUES
- Home = voice-assistant surface: status, chat bubbles, text input + send, mic FAB,
  quick command chips. NO big orb on the main screen. Keep it minimal and clean.
- Settings = permissions dashboard (all runtime + special Settings pages).
- Text input must always work even if mic fails.
## WHAT TO ADD WITH NO KEY (do these if time)
1. ML Kit OCR (`com.google.mlkit:text-recognition`) so camera images return real text.
2. Sensible wake-word improvement ONLY if easy: prefer a small on-device model
   (openWakeWord / microWakeWord) if it builds cleanly; otherwise keep SpeechRecognizer.
## WHAT TO LEAVE STUBBED (needs API key, do NOT invent keys)
- Smart chat is already wired via AiGateway (Gemini / OpenAI / Anthropic) — leave it.
- Real image captioning / web research citations — leave stubbed.
- Home Assistant / Google / Microsoft OAuth — leave stubbed.
- Cloud STT/TTS — leave configured but inactive until key added.
## REPORT (AT THE END)
Return a markdown report:
- Build status (pass/fail) + APK path.
- What I had to fix.
- Emulator test results you can confirm.
- Anything that only works on a real device.
- Anything waiting on an API key.
- Any warnings (permissions, scoped storage, background limits).
```
---
## Short version (if you want a one-liner)
```text
Open jarvis-android/, make it compile (fix all errors), run on an API 34/35 emulator,
confirm Home screen loads, ensure "open chrome" + "search for book" work, ensure
messaging send doesn't crash without permissions, keep text input working even when
mic fails, and report build status + APK path + what needs a real device or API key.
```
