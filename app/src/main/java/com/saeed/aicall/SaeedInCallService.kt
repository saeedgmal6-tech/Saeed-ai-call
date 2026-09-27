package com.saeed.aicall

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class SaeedInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val target = getSharedPreferences("saeed_ai_call", MODE_PRIVATE)
            .getString("target_caller", "")
            ?.filter { it.isDigit() }

        val caller = call.details.handle?.schemeSpecificPart
            ?.filter { it.isDigit() }

        Log.d("SaeedAICall", "Call detected caller=$caller target=$target")

        if (call.state == Call.STATE_RINGING &&
            !target.isNullOrBlank() &&
            !caller.isNullOrBlank() &&
            normalize(caller) == normalize(target)
        ) {
            try {
                call.answer(0)
                Log.d("SaeedAICall", "Auto-answer requested for configured caller")
            } catch (e: Exception) {
                Log.e("SaeedAICall", "Auto-answer failed", e)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("SaeedAICall", "Call ended")
    }

    private fun normalize(number: String): String {
        return number.removePrefix("20").removePrefix("0").takeLast(10)
    }
}
