# JARVIS Holographic Chat UI Architecture (`mirror-ui-premium`)

## Overview
The `JarvisChatUi` module provides a futuristic, high-performance holographic messaging system for the JARVIS AI Assistant. Designed with glassmorphism, responsive status telemetry, interactive device tool verification cards, and fluid enter/exit animations.

## Key Components

### 1. `JarvisChatHeader`
- Displays core status telemetry (`IDLE`, `LISTENING`, `THINKING`, `EXECUTING`, `SPEAKING`).
- Real-time holographic accent glow tied to `JarvisVisualState`.
- Includes instant mode-switch trigger to toggle between Voice Orb stage and Chat mode.

### 2. `JarvisChatMessageItem`
- High-density message bubbles supporting `USER`, `JARVIS AI`, and `SYSTEM` roles.
- Interactive **Tool Authorization Banner**: standardizes safety verification for LEVEL_1 and LEVEL_2 device execution tools before invocation.
- Precise timestamping formatted to `HH:mm:ss`.

### 3. `JarvisChatInputBar`
- Horizontal scrolling quick-action chips for rapid command execution ("Battery Status", "Device Info", "Volume Up", "Open Browser", "Flashlight On").
- Glowing multi-layered input field with dynamic focus state and cursor styling.
- Microphone shortcut button supporting voice-capture toggles.

### 4. `JarvisChatView`
- Full-screen composable integrating header, message transcript stream, and input bar.
