package com.saeed.aicall

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class SaeedCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences("saeed_ai_call", MODE_PRIVATE)
        val target = prefs.getString("target_caller", "")
            ?.filter { it.isDigit() }
            .orEmpty()

        val caller = callDetails.handle?.schemeSpecificPart
            ?.filter { it.isDigit() }
            .orEmpty()

        val isIncoming =
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        val matches = isIncoming &&
                target.isNotBlank() &&
                caller.isNotBlank() &&
                normalize(caller) == normalize(target)

        Log.d(
            "SaeedAICall",
            "Screening incoming=$isIncoming caller=$caller target=$target matches=$matches"
        )

        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .build()
        )

        if (matches) {
            Log.d(
                "SaeedAICall",
                "TARGET CALLER DETECTED. Ready for the call-control/audio stage."
            )
        }
    }

    private fun normalize(number: String): String {
        return number.removePrefix("20").removePrefix("0").takeLast(10)
    }
}
