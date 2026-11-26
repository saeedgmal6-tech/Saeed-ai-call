# SAEED AI

SAEED AI is a clean Android-first personal-assistant project. This repository was rebuilt from a new branch after removing the previous call-screening prototype and companion server files.

## Current foundation

- Arabic-first Android application UI.
- Text chat interface.
- Gemini API connector (user supplies their own API key).
- Local conversation history and editable personal memory.
- Arabic speech input through the Android speech-recognition service.
- Arabic text-to-speech through Android TTS.
- A small, explicit set of on-device actions: open YouTube, open WhatsApp, open Android settings, and clear local chat history.
- GitHub Actions debug APK build.

## Important scope and limitations

This is the first clean foundation, not a complete ChatGPT-equivalent assistant yet. It does not currently include an embedded on-device LLM, web search, image/file understanding, autonomous multi-step task planning, accessibility-based UI automation, live call audio processing, translation overlays, or an always-on wake-word service.

Gemini API access requires a valid API key and is subject to Google's current account eligibility, quotas, model availability, and pricing. The app does not ship with a shared API key. Chat history, memory, and the API key are stored in the app's private SharedPreferences; the key is not separately encrypted by this initial build. Prompts sent to Gemini leave the device and are processed by Google.

Android permission and background-execution rules remain in force. The app does not silently monitor the microphone or phone calls.

## Build

GitHub Actions builds app/build/outputs/apk/debug/app-debug.apk on pushes to saeed-ai-rebuild.

Debug application ID: com.saeed.ai.debug.

## Planned engineering stages

1. Validate the Android foundation and APK on the target OPPO device.
2. Add a secure provider layer and configurable model routing.
3. Add on-device model download/runtime after benchmarking memory and thermal limits.
4. Add grounded web search and file/image workflows.
5. Add permission-gated Android actions with confirmations, audit log, and rollback where possible.
6. Add optional translation and call-related features only where Android and carrier APIs permit them.
7. Add tests, release signing, privacy controls, and a documented update path.
