# Security

## Credential Management

The Android application sources API keys **only** from `BuildConfig` values
injected at compile time (from `local.properties`, which is gitignored, or
from CI secrets). `ApiConfig` getters return `BuildConfig` values and
default to an empty string — there are **no hardcoded fallback keys** in
source. With no key supplied, the corresponding provider is simply
unavailable rather than silently using a baked-in credential.

When `BackendConfig.USE_BACKEND = true` and a real Convex deployment URL is
set (`BackendConfig.isBackendReady`), all AI communication goes through the
Convex backend via `JarvisApiClient`, and keys live server-side as Convex
environment variables. When `USE_BACKEND = false`, the app calls providers
directly with keys from `local.properties` / BuildConfig.

Do not hardcode or commit keys to the repository.

## ⚠️ SECURITY ADVISORY — leaked keys in git history (action required)

A previous version of this repository committed **live** API keys directly
in `ApiConfig.kt` as plaintext `HARDCODED_*` / `*_FALLBACK` constants:

- a Gemini key (`AQ.Ab8RN6LVmUR…`)
- an ElevenLabs key (`sk_d61e4d09ae…`)
- a Rork Toolkit key (`rork_sk_ied1mfj2…`)

Commit `34a6832` re-added them after an earlier removal (`6017591`). They
have now been removed from `HEAD` (fail-closed getters), **but they remain
in the git history** on at least commits `34a6832`, `93794f7`, `d50ff0e`,
`d758e8f`.

**The repository owner MUST:**

1. **Rotate / revoke all three keys immediately** at their respective
   provider consoles (Google AI Studio, ElevenLabs, Rork). Treat them as
   compromised — they were in a public repo.
2. **Rewrite git history** to scrub the keys from every commit, using BFG
   Repo-Cleaner or `git filter-repo`, then force-push:
   ```bash
   # using git filter-repo
   pip install git-filter-repo
   echo 'AQ.Ab8RN6LVmUR...' > keys-to-remove.txt   # one secret per line
   echo 'sk_d61e4d09ae...' >> keys-to-remove.txt
   echo 'rork_sk_ied1mfj2...' >> keys-to-remove.txt
   git filter-repo --replace-text keys-to-remove.txt
   git push --force origin --all
   ```
3. Have all collaborators re-clone after the rewrite (history rewrite
   changes every commit SHA).

Removing the keys from `HEAD` does **not** remove them from history.

## Permissions

Jarvis requests permissions only when explicitly enabled by the user via the `PermissionCenterScreen`.
- Accessibility: Used for screen reading and interaction.
- Overlay: Used for the floating Orb.
- Microphone: Used for STT and Wake Word.
