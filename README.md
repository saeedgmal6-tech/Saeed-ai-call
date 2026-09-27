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
