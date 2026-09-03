# Application ID — Audit Note (Item 5)

## Current state

- `app/build.gradle.kts` sets:
  - `namespace = "com.rork.jarvisaiassistant"`
  - `applicationId = "com.rork.jarvisaiassistant"`
- All Kotlin source uses `com.jarvis.*` packages
  (e.g. `com.jarvis.app`, `com.jarvis.android.accessibility`).
- `BuildConfig` is generated under the namespace
  `com.rork.jarvisaiassistant`, and `ApiConfig` imports it as
  `com.rork.jarvisaiassistant.BuildConfig`.

## What this means

In Android, `applicationId` is the **installed app's identity** — the
string other apps, intents, and the system use to refer to the app. The
Kotlin package names are independent of it and may differ. So the app
installs and is known to the system as `com.rork.jarvisaiassistant`, even
though the code lives under `com.jarvis.*`. This compiles and runs fine.

The `com.rork.*` identifier comes from the Rork Toolkit scaffolding that
generated the project.

## Decision for the owner

This is a **one-time, owner decision**, not a defect. Two options:

1. **Keep `com.rork.jarvisaiassistant`** (no change). Fine if the app is
   not yet widely distributed. The Rork toolkit tooling may expect this id.

2. **Migrate to `com.jarvis.app`** (or another owner-chosen id) so the
   installed identity matches the code. This requires:
   - Changing `applicationId` (and optionally `namespace`) in
     `app/build.gradle.kts`.
   - Re-running the build; `BuildConfig` moves to the new package, so the
     `import com.rork.jarvisaiassistant.BuildConfig` line in `ApiConfig.kt`
     must be updated to match the new namespace.
   - **Installed-app continuity breaks**: a user who upgrades from the old
     id to the new id is treated as a fresh install (Android does not
     migrate app data across applicationId changes without explicit
     `PackageManager` data migration). Do this before public distribution.

## Recommendation

If the app has any installed users or is referenced by external intents /
deep links, keep the current id unless there is a strong reason to
rebrand. If it is still pre-release with no installed base, migrating to
`com.jarvis.app` now is cheaper than later. Either way, this should be an
explicit owner choice — the audit does **not** change it automatically.
