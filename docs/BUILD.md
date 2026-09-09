# Build Instructions

JARVIS is a **single Android app**. There is no backend service to run: the removed
`convex/` directory was the last of it, and AI requests go straight from the device to
the provider APIs.

> This file previously documented a `backend/` Node.js service and told you to configure a
> backend URL in `ApiConfig.kt`. Neither existed at the time it was written — there is no
> `backend/` directory in the repository, and `ApiConfig.kt` has no backend URL. Rewritten
> 2026-09-09 to describe what actually builds.

## Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17+ | Required by AGP 8.13. Kotlin/Java bytecode still targets 11. |
| Gradle | 8.14.1 | Supplied by the wrapper — do not install separately. |
| Android Gradle Plugin | 8.13.2 | From `gradle/libs.versions.toml`. |
| Kotlin | 2.3.10 | With KSP 2.3.9 (Room 2.8.4 codegen). |
| Android SDK | compileSdk / targetSdk **36**, minSdk **26** | API 26+ devices (Android 8.0+). |

The SDK location is read from `local.properties` (`sdk.dir=`) or `ANDROID_HOME`.

## API keys

Both keys are **optional** — the app compiles and runs without either.

```bash
cp local.properties.example local.properties   # then paste your key
```

| Key | Source | If absent |
| --- | --- | --- |
| `GEMINI_API_KEY` | Environment variable, else `local.properties`, injected into `BuildConfig` at compile time | Gemini provider reports unavailable; JARVIS falls back to NVIDIA |
| NVIDIA | Hardcoded in `ApiConfig.kt` (`HARDCODED_NVIDIA_KEY`) — owner decision, see `docs/SECURITY.md` | n/a |

Environment variables take precedence over `local.properties`. Values are escaped before
being emitted into `BuildConfig`, so a key containing a quote or backslash cannot break the
generated Java.

ElevenLabs and the Rork Toolkit gateway were removed and take no key. If you see
`ELEVENLABS_API_KEY` or `EXPO_PUBLIC_RORK_TOOLKIT_SECRET_KEY` mentioned anywhere, that
document is historical — see `docs/README.md`.

## Build

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # release (needs a signing config to be installable)
./gradlew lintDebug              # Android lint
./gradlew test                   # unit tests
```

Clean build after changing `BuildConfig` fields or Room entities:

```bash
./gradlew clean assembleDebug
```

## Room schema export

Room writes schemas to `app/schemas/`. **Never hand-edit them.** Bumping the database
version requires a migration in `Migrations.kt` plus the matching exported schema file,
otherwise the app crashes at first launch with a missing-migration `IllegalStateException`.

```bash
python3 scripts/check_room_migrations.py    # verifies versions, migration coverage, schemas
```

## Tool manifest

The eval harness must test the same tool surface the app sends the model. That list is
generated, not hand-written:

```bash
python3 scripts/generate_eval_tools.py           # regenerate eval/tools.json
python3 scripts/generate_eval_tools.py --check   # exit 1 if it would change (what CI runs)
```

Adding, renaming or removing a tool without regenerating the manifest fails CI. When a new
tool reads arguments that `ToolSchema.defaultArgs()` cannot guess, add an explicit entry to
`ToolSchema.ARG_HINTS` — otherwise the model is told the tool takes parameters its handler
never reads.

## Continuous integration

| Workflow | Jobs | Trigger |
| --- | --- | --- |
| `.github/workflows/android.yml` | `tool-manifest` (blocking), `build` | push / PR |
| `.github/workflows/eval.yml` | `eval` — live routing eval, posts a transcript as a commit comment | `arena/**` branches |

CI is the build oracle for this repository: it compiles the app and runs the eval against
the real provider. The eval distinguishes a **routing regression** (model picked the wrong
tool) from **infrastructure noise** (provider 429/5xx), and tolerates a small amount of the
latter so a provider outage does not read as broken code.
