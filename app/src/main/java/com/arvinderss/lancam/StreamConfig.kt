package com.arvinderss.lancam

object StreamConfig {
    const val PORT = 8554
    const val PATH = "live"
    const val SERVICE_TYPE = "_rtsp._tcp."

    data class Preset(val label: String, val width: Int, val height: Int, val fps: Int, val bitrateMbps: Int)

    val PRESETS = listOf(
        Preset("8K 30fps", 7680, 4320, 30, 100),
        Preset("4K 60fps", 3840, 2160, 60, 60),
        Preset("4K 30fps", 3840, 2160, 30, 40),
        Preset("1080p 60fps", 1920, 1080, 60, 15),
        Preset("1080p 30fps", 1920, 1080, 30, 8),
    )
}
