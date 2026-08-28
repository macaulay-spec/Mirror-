# Security

## Credential Management
The Android application contains ZERO persistent provider credentials.
All AI communication goes through the Jarvis Backend via `JarvisApiClient`.

Credentials for Gemini, ElevenLabs, etc., must be configured in the backend's environment variables (`.env`).
Do not hardcode or commit keys to the repository.

## Permissions
Jarvis requests permissions only when explicitly enabled by the user via the `PermissionCenterScreen`.
- Accessibility: Used for screen reading and interaction.
- Overlay: Used for the floating Orb.
- Microphone: Used for STT and Wake Word.
