# Documentation index

This directory mixes **current** documentation with **point-in-time records** written during
earlier development passes. The historical files were kept deliberately — they explain why
the code looks the way it does — but they describe systems that no longer exist. Read this
page first.

## Two integrations were removed

If a document tells you to configure either of these, it is historical:

| Removed | What it was | Replaced by |
| --- | --- | --- |
| **ElevenLabs** | Cloud TTS + `CloudSttEngine`, `ELEVENLABS_API_KEY` | Gemini TTS (`GeminiVoicePlayer`) with an Android `TextToSpeech` fallback |
| **Convex** | `convex/` backend, `BackendConfig`, `chatViaProxy` in `JarvisApiClient` | Nothing — requests go direct to the provider. `isBackendReady` was permanently false, so the direct path was already the only one that ran |

Also gone: the Rork Toolkit gateway (`TOOLKIT_URL` / `TOOLKIT_SECRET_KEY`), which was never
referenced by working code.

The AI provider model is now **Gemini first, NVIDIA as a working fallback**. See
`docs/AI_PROVIDERS.md` and `docs/SECURITY.md`.

## Current documentation

| File | Contents |
| --- | --- |
| [`BUILD.md`](BUILD.md) | Toolchain versions, keys, build commands, CI. **Start here.** |
| [`SECURITY.md`](SECURITY.md) | Where each credential lives, rotation, permissions, on-device data |
| [`KEYS_SETUP.md`](KEYS_SETUP.md) | Getting a Gemini key; what the removed integrations used to need |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Module layout and the request path |
| [`AI_PROVIDERS.md`](AI_PROVIDERS.md) | Provider chain, models, failover behaviour |
| [`VOICE.md`](VOICE.md) | Gemini TTS voices, wake word, mic arbitration |
| [`JARVIS_MASTER_IMPLEMENTATION_PLAN.md`](JARVIS_MASTER_IMPLEMENTATION_PLAN.md) | The live plan: completed work, prioritised backlog |
| [`JARVIS_DEFAULT_ASSISTANT.md`](JARVIS_DEFAULT_ASSISTANT.md) | Why `ROLE_ASSISTANT` is unreachable and what the app does instead |
| [`ACCESSIBILITY.md`](ACCESSIBILITY.md) | The accessibility service and the on-screen tool surface |
| [`PERMISSIONS.md`](PERMISSIONS.md) | Permission request flow |
| [`WEB_AGENT.md`](WEB_AGENT.md) | Web search / browse tools |
| [`CAPABILITIES.md`](CAPABILITIES.md) | What the assistant can do, by tool category |
| [`VOSK_SETUP.md`](VOSK_SETUP.md) | Offline wake-word models |
| [`APPLICATION_ID.md`](APPLICATION_ID.md) | Package naming |

## Historical records

Accurate when written; **not** a description of the current app. Useful for rationale and
for understanding older commits.

| File | Why it is historical |
| --- | --- |
| `ACCESSIBILITY_DIAGNOSIS.md` | A diagnosis snapshot referring to numbered "Item" fixes, the Rork key and the Convex guard |
| `BUILD_STATUS.md` | Status table naming `ElevenLabsVoicePlayer`, which no longer exists |
| `CAPABILITY_COMPARISON_JARVIS_VS_SIRI_VS_BIXBY.md` | Comparison written around ElevenLabs HD voices; also lists `find_text`, renamed to `click_text` |
| `JARVIS_BUILD_MANIFEST.md` | Build manifest from the ElevenLabs era; lists `find_text` |
| `JARVIS_CAPABILITY_SPEC.md` | Capability spec asking for an ElevenLabs key |
| `JARVIS_FINAL_STATE.md` | Target-state document describing an ElevenLabs voice pipeline |
| `DESIGN_AUDIT.md` | Audit of an earlier UI pass |
| `FOUNDATION_README.md` | Early foundation notes |
| `SIRI_BIXBY_HOW_THEY_WORK_AND_OUR_PLAN.md` | Research notes; still useful, predates the current tool set |

## Generated files

These are produced by scripts and verified in CI — edit the generator, not the output:

| File | Generator |
| --- | --- |
| `eval/tools.json` | `scripts/generate_eval_tools.py` (`--check` fails CI on drift) |
| `app/schemas/**.json` | Room, via KSP at build time |
