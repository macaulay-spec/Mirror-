# Voice Architecture

Jarvis supports an end-to-step voice pipeline driven by the backend.

## Flow
`Wake Word` -> `Listening` -> `STT` -> `Agent Processing` -> `TTS` -> `Speaking` -> `Idle`

## Wake Word
Implemented natively on-device. When activated, it brings up the Orb and initiates the `Listening` state.

## STT & TTS
- **STT**: Uses Google's standard speech recognizer API.
- **TTS**: Routes through the Jarvis Backend to call ElevenLabs' API. The ElevenLabs API key is securely held on the backend (`ELEVENLABS_API_KEY`). A fallback to native Android TTS is implemented if the backend is unreachable.

## Orb Integration
The `JarvisFloatingOrbService` manages visual states (`LISTENING`, `THINKING`, `SPEAKING`) based on the active state of the Voice Engine.
