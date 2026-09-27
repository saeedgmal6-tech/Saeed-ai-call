package com.saeed.aicall

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class SaeedInCallService : InCallService() {
    override fun onCallAdded(c: Call) {
        super.onCallAdded(c)
        Log.d("SaeedAICall","Call detected")
        if (c.state == Call.STATE_RINGING) {
            try { c.answer(0); Log.d("SaeedAICall","Auto-answer requested") }
            catch(e:Exception){ Log.e("SaeedAICall","Auto-answer failed",e) }
        }
    }
    override fun onCallRemoved(c: Call) {
        super.onCallRemoved(c)
        Log.d("SaeedAICall","Call ended")
    }
}
