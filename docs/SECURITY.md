# Security

## Credential management

Current state, as of the 2026-09-09 sync:

| Credential | Where it lives | Injected how |
| --- | --- | --- |
| Gemini API key | `GEMINI_API_KEY` in CI secrets, or `local.properties` (gitignored) | `buildSecret()` → `BuildConfig` at compile time; empty string if absent |
| NVIDIA API key | `HARDCODED_NVIDIA_KEY` constant in `app/src/main/java/com/jarvis/app/config/ApiConfig.kt` | Compiled into the APK |

Hardcoding the NVIDIA key is an **explicit owner decision** (the repository is private and
the APK is intended to be self-contained). It is recorded here as fact, not as an open
issue: there is no CI gate that fails on it, and no plan to add one.

The practical consequence worth knowing: rotating that key is a **code change plus a
rebuild**, not a config change. Anyone who installs a built APK can extract the key from
the binary, so it should be treated as a shared, revocable credential with spending limits
at the provider console rather than as a personal secret.

`ApiConfig.activeProvider` resolves Gemini first and falls back to NVIDIA, so a missing or
exhausted Gemini key degrades to a working brain instead of silencing the assistant.

### Removed credential paths

The following were deleted and take no key. References to them in older documents are
historical (see `docs/README.md`):

- **ElevenLabs** — the integration was removed entirely at the owner's instruction, along
  with `ELEVENLABS_API_KEY`, its `BuildConfig` field and `CloudSttEngine`. Voice is Gemini
  TTS with an Android `TextToSpeech` fallback.
- **Convex backend** — `convex/`, `BackendConfig`, and the `chatViaProxy` path in
  `JarvisApiClient`. `isBackendReady` was permanently false because no `WORKER_URL` was ever
  configured, so every call already took the direct path.
- **Rork Toolkit gateway** — `TOOLKIT_URL` / `TOOLKIT_SECRET_KEY`, unreferenced.

## Key rotation

```bash
# Gemini: update the CI secret, or local.properties for a local build
gh secret set GEMINI_API_KEY --app <app>        # CI
#   ...or edit local.properties                  # local

# NVIDIA: edit HARDCODED_NVIDIA_KEY in ApiConfig.kt, then rebuild
```

`local.properties` is gitignored. Environment variables take precedence over it, so CI
never depends on a committed file.

## Git history

Keys committed in earlier revisions remain reachable through git history even though the
current `HEAD` no longer contains the ElevenLabs or Rork values. `main` has since been
rewritten to a single squashed commit, so the specific SHAs named in earlier revisions of
this document no longer resolve. Treat any credential that was ever committed as revocable
at the provider console; rotation is the only complete remedy, and it is the owner's call
when to do it.

## Permissions

JARVIS requests permissions only when the user explicitly enables them via
`PermissionCenterScreen`.

- **Accessibility** — screen reading and on-screen interaction (`JarvisAccessibilityService`).
- **Overlay** — the floating Orb.
- **Microphone** — speech-to-text and the wake word.
- **Location** — weather and navigation; the weather tool prefers a named city and only
  reads device location when the user does not name one.

The default-assistant role is *not* requestable by third-party apps
(`ROLE_ASSISTANT` has `requestable=false`), so the app registers an `ACTION_ASSIST`
intent filter instead — see `docs/JARVIS_DEFAULT_ASSISTANT.md`.

## Data on device

Conversation history and memories are stored in a local Room database. Schema versions are
exported to `app/schemas/` and migration coverage is checked by
`scripts/check_room_migrations.py`. Destructive migration fallbacks are scoped to specific
old versions rather than applied globally, so a missing migration cannot silently wipe
user data on a current install.
