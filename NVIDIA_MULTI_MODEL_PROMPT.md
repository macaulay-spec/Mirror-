JARVIS — NVIDIA MULTI-MODEL INTEGRATION (REPLACES RORK AS PRIMARY)

Context: Rork's gateway integration has turned out broken in practice, on top of
the hardcoded-key problem from earlier. The project owner now has a free NVIDIA
API key (build.nvidia.com / NIM, OpenAI-compatible endpoint at
https://integrate.api.nvidia.com/v1) and wants to use it as the new primary
brain — not one model, several, wired in properly. This document tells you
exactly which models, in what order, and how to wire them into the existing
architecture without repeating past mistakes.

================================================================
FIRST: VERIFY BEFORE BUILDING
================================================================

Read app/src/main/java/com/jarvis/app/config/ApiConfig.kt and
app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt as they actually
are right now before changing anything — this document describes the
architecture as of a specific earlier snapshot, and describes intent, not
guaranteed current file contents. Also specifically check whether the
hardcoded RORK_KEY_FALLBACK constant is still present — if it is, that's a
separate, already-flagged issue; don't let this NVIDIA work distract from it,
but don't block this work on it either unless asked.

================================================================
THE MODEL LINEUP, IN ORDER
================================================================

1. **Primary — GLM-5.2** (`zhipuai/glm-5.2` or whatever the exact NVIDIA
   catalog path is at build time — check the model's page on build.nvidia.com
   for the precise string, catalog paths do shift). Chosen specifically for
   long-horizon agentic reasoning and a 1M token context window — this
   directly targets Jarvis's known weak point (multi-step tool use that needs
   to hold history + tool schemas + intermediate results in context at once).

2. **Fallback 1 — Nemotron (NVIDIA's own flagship, e.g. Nemotron-3-Super or
   Nemotron-3-Ultra)**. Strong structured tool use, NVIDIA's own model so
   likely to have the best support/uptime on their own platform.

3. **Fallback 2 — Mistral Nemotron**. Purpose-built for agentic workflows and
   function calling specifically — a good second opinion if GLM-5.2 and
   Nemotron both fail or rate-limit.

4. **Fallback 3 — Llama 4 Maverick (or Llama 3.1 405B if Maverick isn't
   available)**. Broadest general reasoning as a last resort before falling
   through to the existing xAI/Gemini chain already in ApiConfig.

5. **Kept, not removed — the existing xAI/Gemini/OpenAI-compatible chain**
   already in ApiConfig stays as the final fallback tier below all of the
   above. Don't delete working fallback logic; extend it.

Rork drops out of the priority order entirely (leave the code path in place
if it's cheap to, since the project owner may revisit it, but it should not
be reached before any of the above).

================================================================
HOW TO WIRE THIS IN
================================================================

- Add an `NVIDIA_API_KEY` BuildConfig field, sourced from local.properties /
  CI secrets, exactly the same pattern the existing GEMINI_API_KEY /
  ELEVENLABS_API_KEY fields already use. **Do not hardcode a literal fallback
  key in source, the way RORK_KEY_FALLBACK was — that mistake should not
  repeat with this key.** If no key is configured, the app should behave as
  if that provider simply isn't available and fall through, not silently use
  an embedded default.
- Add an `NVIDIA_BASE_URL` constant (`https://integrate.api.nvidia.com/v1`)
  and the four model identifiers above as named constants, not inline
  strings, so they're easy to update when NVIDIA's catalog paths change.
- In JarvisApiClient, this should reuse the EXISTING OpenAI-compatible code
  path (the same one Groq/Cerebras/OpenRouter already go through) — NVIDIA's
  endpoint is OpenAI-compatible by design specifically so this works. Do not
  write a new parallel HTTP-calling implementation for this; that pattern
  (multiple implementations doing the same job) has caused real bugs earlier
  in this project (the duplicate ElevenLabs classes, the duplicate tool
  registrations) and should not happen again here.
- Provider selection logic: try the primary; on failure or rate-limit
  (HTTP 429), fall through to the next in the list above, in order, before
  falling through to the pre-existing chain. Surface which provider actually
  answered in whatever diagnostics/logging already exists, so failures are
  debuggable rather than silent.
- Update DiagnosticsActivity's provider-test screen to test all four new
  models along with the existing providers, using the SAME code path real
  chat uses (this was already flagged as a gap elsewhere — don't create a
  second, separate test path for these new models either).

================================================================
SPEECH — SEPARATE, LARGER PIECE OF WORK
================================================================

NVIDIA's Riva stack (Parakeet for ASR, Magpie for TTS, including voice
cloning) is a genuine, complete answer for "speech-to-text and text-to-speech
are broken" — but it's architecturally different from the text models above:
Riva uses gRPC, not a simple REST/OpenAI-compatible endpoint. This means:

- This is NOT a drop-in replacement for ElevenLabsVoicePlayer/ElevenLabsTts
  the way the text models are a near drop-in for the existing chat path.
- It needs a real gRPC client dependency added to the Android project, and a
  new voice-engine implementation built around it — treat this as its own
  task, not a quick addition alongside the text-model work above.
- NVIDIA publishes a complete reference pipeline
  (huggingface.co/spaces/nvidia/voice-agent-examples, the speech-to-speech
  example specifically) showing ASR -> LLM -> TTS wired together — use it as
  the architectural reference, not something to copy wholesale (it's a
  Python/Docker reference stack, not Android).
- Sequence this AFTER the text-model integration above is confirmed working,
  not simultaneously — get one clean win before starting the harder piece.

================================================================
IMAGE GENERATION — LOWEST PRIORITY, NOTE FOR LATER
================================================================

FLUX.1-Kontext-Dev and Qwen-Image (20B) are available in the same catalog for
if/when Jarvis needs an actual "generate an image" capability. Don't build
this now — just note that the same NVIDIA key already covers it, so no new
account/key work is needed when it's prioritized.

================================================================
HOW TO WORK
================================================================

- Get the text-model fallback chain (section "THE MODEL LINEUP") working and
  confirmed first — that's the fast, high-value win and directly extends
  code that already exists and works.
- Confirm each fallback tier actually triggers correctly (test by
  temporarily using an invalid key for the primary and confirming it falls
  through, not just that each model works in isolation).
- Don't repeat the pattern from earlier in this project where a fix is
  described as done but never actually committed and pushed — confirm each
  piece is actually in the GitHub repo, not just built locally, before
  reporting it as finished.
