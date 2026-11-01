package com.arvinderss.lancam

/** Process-wide flags read by CallBlocker while a stream is live. */
object StreamState {
    @Volatile var live = false
    @Volatile var blockCalls = false
}
