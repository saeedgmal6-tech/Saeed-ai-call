# SAEED AI

SAEED AI is an Arabic-first Android client for a privately operated, open-weight language model. The Android app does not call Gemini, OpenAI, or another hosted AI provider and does not require an AI-provider API key.

## Current Android foundation

- Arabic-first chat UI.
- Conversation history and editable memory stored locally in app-private storage.
- Arabic speech input through the Android speech-recognition service.
- Arabic text-to-speech through Android TTS.
- Explicit phone actions: open YouTube, open WhatsApp, open Android settings, and clear local chat history.
- Configurable private server address and model name.
- Chat Completions-compatible connection to a self-hosted endpoint such as Ollama or llama.cpp server at `/v1/chat/completions`.
- No Authorization header or AI API key is sent.

## Important: the model server is not included yet

This Android project is a client, not a magically provisioned server. It does not contain the language model weights and it does not create compute capacity. To get AI responses, a compatible self-hosted model server must already be running on hardware you control and reachable from the phone. Example model name shown in the app: `qwen2.5:3b`; the exact model must be installed on your server.

No server address is preconfigured. Until a private server is actually provisioned and tested, the app cannot provide generative AI responses. A zero-cost, always-on, powerful private server cannot be assumed to exist. A small free local model is a different, weaker architecture.

## Connection and privacy

Set the server Base URL in app settings, for example `http://192.168.1.10:11434` for an Ollama server on the same trusted Wi-Fi network. The app appends `/v1/chat/completions` unless that path is already present. Use only a trusted private network or a properly secured private VPN/tunnel. Plain HTTP on a shared or untrusted network can expose prompts and responses in transit.

Conversation history and memory remain in app-private local storage. Message content is sent only to the server address configured by the user. Android speech recognition may be provided by a device-installed recognition service; this app does not promise offline speech recognition. Android TTS is used for spoken replies.

## Current limitations

This is an initial Android client foundation, not a ChatGPT-equivalent autonomous assistant. It does not yet include bundled on-device inference, web research, image/file understanding, multi-step agent planning, accessibility-based UI automation, call audio processing, translation overlays, wake-word listening, or a provisioned private model server. Android permission and background-execution restrictions still apply; there is no unrestricted or silent control of the phone.

## Build

GitHub Actions builds `app/build/outputs/apk/debug/app-debug.apk` on pushes to `saeed-ai-rebuild`.

Debug application ID: `com.saeed.ai.debug`.

## Next engineering stages

1. Build and validate the Android client APK.
2. Provision a private model server on available user-controlled hardware and install a suitable open-weight model.
3. Connect the phone over a private network/VPN and verify inference end to end.
4. Add grounded web search and image/file workflows without external AI-provider APIs.
5. Add permission-gated Android actions, confirmations, audit log, and tests.
6. Benchmark model quality, latency, memory, thermal load, and server uptime before claiming capability.
