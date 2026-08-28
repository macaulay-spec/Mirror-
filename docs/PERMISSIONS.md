# Permissions & Setup

Jarvis requires specific permissions to function correctly. These are managed via `PermissionCenterScreen` and verified by `PermissionAndSetupHelper`.

## Core Permissions
- **Accessibility**: Required for reading screen contents and tapping elements.
- **Overlay (Draw Over Other Apps)**: Required to show the floating Orb.
- **Microphone**: Required for STT and Wake Word detection.

## Extended Capabilities
- **Notification Access**: Uses `JarvisNotificationListener` to read incoming notifications and optionally reply.
- **Usage Access**: Uses `AppOpsManager` to verify usage tracking, enabling Jarvis to know recently used applications and daily usage time.
- **System Settings**: Required to modify volume, brightness, etc.
- **Battery Optimization**: Should be disabled for reliable wake word functionality.
