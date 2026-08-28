# Jarvis Architecture

## Overview
Jarvis is an Android agent capable of natural language understanding, cross-app accessibility control, and voice interaction.

## Architecture
```
USER
  ↓
CHAT / VOICE / ORB
  ↓
CONVERSATION MANAGER (DialogueManager)
  ↓
AGENT EXECUTOR
  ↓
JARVIS API CLIENT (Backend Gateway)
  ↓
BACKEND (Node.js/Express)
  ↓
AI PROVIDERS (Gemini, etc.)
  ↓
RESULT
  ↓
ANDROID APP
  ↓
TOOL EXECUTION (Accessibility, Device Tools)
  ↓
OBSERVE & VERIFY
  ↓
RESPONSE (Chat / TTS)
```

## Backend
The backend serves as the authoritative AI gateway. The Android app stores NO persistent AI credentials.
