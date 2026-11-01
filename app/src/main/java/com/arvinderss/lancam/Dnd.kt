package com.arvinderss.lancam

import android.app.NotificationManager
import android.content.Context

/** Turns on total-silence Do Not Disturb for the duration of a live stream, then restores. */
class Dnd(context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private var saved: Int? = null

    fun hasAccess() = nm.isNotificationPolicyAccessGranted

    fun enable() {
        if (!hasAccess() || saved != null) return
        saved = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    fun restore() {
        val prev = saved ?: return
        if (hasAccess()) nm.setInterruptionFilter(prev)
        saved = null
    }
}
