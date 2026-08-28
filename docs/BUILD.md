# Build Instructions

## Environment
- Android SDK 35
- Gradle (use the included `gradlew` wrapper)
- Backend: Node.js 18+

## Android Client
To build the Android APK:
```bash
./gradlew assembleDebug
```

Ensure you have your backend URL configured correctly in `ApiConfig.kt` before compiling.

## Backend
To run the Node.js backend:
```bash
cd backend
npm install
node src/index.js
```
The backend requires `.env` variables for `GEMINI_KEYS`, `OPENAI_KEYS`, and `ELEVENLABS_API_KEY`.
