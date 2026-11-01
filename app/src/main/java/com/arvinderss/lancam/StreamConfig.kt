package com.arvinderss.lancam

object StreamConfig {
    const val PORT = 8554
    const val PATH = "live"
    const val SERVICE_TYPE = "_rtsp._tcp."

    data class Preset(val label: String, val width: Int, val height: Int, val fps: Int, val bitrateMbps: Int)

    /** Capped at 4K: reliable on far more phones, and plenty for YouTube/Facebook live. */
    val PRESETS = listOf(
        Preset("4K 30fps", 3840, 2160, 30, 35),
        Preset("4K 60fps", 3840, 2160, 60, 50),
        Preset("1440p 30fps", 2560, 1440, 30, 20),
        Preset("1080p 60fps", 1920, 1080, 60, 15),
        Preset("1080p 30fps", 1920, 1080, 30, 8),
    )
}
