# Security

## Credential Management

The Android application sources API keys **only** from `BuildConfig` values
injected at compile time (from environment variables in CI, or from
`local.properties` for local development, which is gitignored). `ApiConfig`
getters return `BuildConfig` values and **default to an empty string** when
no key is supplied — there are **no hardcoded fallback keys** in source.

When `BackendConfig.USE_BACKEND = true` and a real Convex deployment URL is
set (`BackendConfig.isBackendReady`), all AI communication goes through the
Convex backend via `JarvisApiClient`, and keys live server-side as Convex
environment variables. When `USE_BACKEND = false`, the app calls providers
directly with keys from `local.properties` / BuildConfig.

**Do not hardcode or commit keys to the repository.**

## ⚠️ SECURITY ADVISORY — leaked keys in git history (action required)

A previous version of this repository committed **live** API keys directly
in `ApiConfig.kt` as plaintext constants:

- an NVIDIA API key (`nvapi-qodXWqy4Hcl...`)
- an ElevenLabs API key (`sk_5dec6e6f0ffc...`)

These keys were exposed in source code and in the GitHub Actions CI workflow
(`.github/workflows/android.yml`). They have been removed from `HEAD` and
replaced with environment-variable-only injection, but **they remain in the
git history**.

**The repository owner MUST:**

1. **Rotate / revoke both keys immediately** at their respective provider
   consoles (NVIDIA, ElevenLabs). Treat them as compromised — they were in
   the repository history.
2. **Rewrite git history** to scrub the keys from every commit, using BFG
   Repo-Cleaner or `git filter-repo`, then force-push:
   ```bash
   # using git filter-repo
   pip install git-filter-repo
   echo 'nvapi-qodXWqy4Hcl...' > keys-to-remove.txt   # one secret per line
   echo 'sk_5dec6e6f0ffc...' >> keys-to-remove.txt
   git filter-repo --replace-text keys-to-remove.txt
   git push --force origin --all
   ```
3. Have all collaborators re-clone after the rewrite (history rewrite
   changes every commit SHA).

4. Update the GitHub Actions workflow to use **GitHub Secrets** (`${{ secrets.NVIDIA_API_KEY }}`
   and `${{ secrets.ELEVENLABS_API_KEY }}`) instead of hardcoded values.

Removing the keys from `HEAD` does **not** remove them from history.

## CI/CD Security

The `.github/workflows/android.yml` workflow has been updated to use GitHub
Secrets for all API keys. Ensure the following secrets are configured in the
repository settings:

- `NVIDIA_API_KEY`
- `ELEVENLABS_API_KEY`

## Permissions

Jarvis requests permissions only when explicitly enabled by the user via the permissions flow.
- Accessibility: Used for screen reading and interaction.
- Overlay: Used for the floating Orb.
- Microphone: Used for STT and Wake Word.
