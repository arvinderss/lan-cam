package com.arvinderss.lancam

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Rejects incoming calls while live (if enabled). Requires the user to grant
 * LanCam the "Caller ID & spam" (call screening) role. Note: on some Android
 * versions screening apps only see calls from numbers NOT in contacts; DND
 * still silences contacts so they don't interrupt the stream.
 */
class CallBlocker : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val block = incoming && StreamState.live && StreamState.blockCalls
        val response = CallResponse.Builder().apply {
            if (block) {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipCallLog(false)
                setSkipNotification(false)
            }
        }.build()
        respondToCall(details, response)
    }
}
