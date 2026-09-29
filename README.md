# Saeed AI Call

Android prototype for automatic answering of cellular calls.

Current stage:
- Android Telecom / InCallService
- Default Phone/Dialer role
- Automatic answer
- Ready for AI voice layer

Important: Android restricts third-party access to the audio path of ordinary cellular calls. The AI conversation layer must be tested on the actual device before the final audio architecture is chosen.

Next stage:
Caller audio -> speech recognition -> AI -> speech synthesis -> call audio.

## Companion server

The `server/` folder contains a computer-hosted phone assistant using Twilio Voice, Gemini Live, and WhatsApp Business Cloud API. It streams live audio from a provider-managed phone call to Gemini and can send an approved-template WhatsApp alert when a caller asks to leave Saeed a personal message.

This is a separate PSTN/provider-based call route; it does not yet connect to the Android app's cellular audio path. See [server setup and costs](server/README.md).

## Costs and secrets

The source code can be run on your own computer without a software license fee. Gemini Live may have a limited free quota, but phone minutes/number rental and WhatsApp template messages can cost money. The server README links to current price pages.

Never commit `.env`, API tokens, or local `data/settings.json`. The server ignores them by default.