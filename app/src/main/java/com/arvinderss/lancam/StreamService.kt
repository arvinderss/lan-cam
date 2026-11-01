package com.arvinderss.lancam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.IpType
import com.pedro.rtspserver.server.ServerClient

/**
 * Owns the camera + RTSP server so streaming survives screen lock and app switching.
 * Bound by MainActivity for preview; promoted to a foreground service while live.
 */
class StreamService : Service(), ConnectChecker {

    inner class LocalBinder : Binder() { val service get() = this@StreamService }
    private val binder = LocalBinder()

    private lateinit var camera: RtspServerCamera2
    private lateinit var discovery: Discovery
    private lateinit var dnd: Dnd
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var previewView: OpenGlView? = null
    private val clients = mutableSetOf<String>()

    var status: String = "Idle"; private set
    var onStatus: (() -> Unit)? = null
    val isLive get() = StreamState.live

    override fun onCreate() {
        super.onCreate()
        camera = RtspServerCamera2(this, this, StreamConfig.PORT)
        discovery = Discovery(this)
        dnd = Dnd(this)
        camera.getStreamClient().forceIpType(IpType.IPv4)
        camera.getStreamClient().setClientListener(object : ClientListener {
            override fun onClientConnected(client: ServerClient) {
                val ip = client.getAddress()
                if (!LanUtils.isPrivate(ip)) { client.stopClient(); return }
                clients += ip; updateStatus()
            }
            override fun onClientDisconnected(client: ServerClient) {
                clients -= client.getAddress(); updateStatus()
            }
            override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {}
        })
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stop()
        return START_NOT_STICKY
    }

    fun isSupported(p: StreamConfig.Preset) =
        camera.resolutionsBack.any { it.width == p.width && it.height == p.height }

    // ---- Preview (only while the activity is visible) ----
    fun attachPreview(view: OpenGlView) {
        previewView = view
        camera.replaceView(view)
        if (!camera.isOnPreview && !camera.isStreaming) camera.startPreview(com.pedro.encoder.input.video.CameraHelper.Facing.BACK, 1920, 1080, 30, 0)
    }

    fun detachPreview() {
        previewView = null
        if (camera.isStreaming) camera.replaceView(this as Context)
        else if (camera.isOnPreview) camera.stopPreview()
    }

    // ---- Live ----
    /** Returns null on success, or an error message. */
    fun start(p: StreamConfig.Preset, useMic: Boolean, useDnd: Boolean, blockCalls: Boolean): String? {
        if (StreamState.live) return null
        if (!isSupported(p)) return "${p.label} isn't supported by this camera"

        if (camera.isOnPreview) camera.stopPreview()
        camera.setVideoCodec(VideoCodec.H265)
        val bitrate = p.bitrateMbps * 1_000_000
        val videoOk = camera.prepareVideo(p.width, p.height, p.fps, bitrate, 0) ||
            run { camera.setVideoCodec(VideoCodec.H264); camera.prepareVideo(p.width, p.height, p.fps, bitrate, 0) }
        if (!videoOk) return "Encoder can't handle ${p.label}"

        if (useMic) {
            camera.getStreamClient().setOnlyVideo(false)
            if (!camera.prepareAudio(128 * 1024, 48000, true)) return "Microphone unavailable"
        } else {
            camera.getStreamClient().setOnlyVideo(true)
        }

        ContextCompat.startForegroundService(this, Intent(this, StreamService::class.java))
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
            (if (useMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(p.label), type)

        previewView?.let { camera.replaceView(it) }
        camera.startStream()
        discovery.start()
        acquireLocks()
        if (useDnd) dnd.enable()
        StreamState.blockCalls = blockCalls
        StreamState.live = true
        updateStatus(p.label + if (useMic) " + phone mic" else " (video only)")
        return null
    }

    fun stop() {
        if (camera.isStreaming) camera.stopStream()
        discovery.stop()
        releaseLocks()
        dnd.restore()
        StreamState.live = false
        clients.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        previewView?.let { attachPreview(it) }
        updateStatus()
    }

    private var label = ""
    private fun updateStatus(newLabel: String? = null) {
        newLabel?.let { label = it }
        status = if (!StreamState.live) "Idle" else buildString {
            append("LIVE ").append(label)
            append("\nrtsp://").append(LanUtils.localIpv4() ?: "<no LAN IP>")
            append(":").append(StreamConfig.PORT).append("/").append(StreamConfig.PATH)
            append("\nmDNS: ").append(discovery.serviceName)
            append("\nViewers: ").append(if (clients.isEmpty()) "none" else clients.joinToString())
        }
        onStatus?.invoke()
    }

    private fun acquireLocks() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LanCam:stream").apply { acquire() }
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "LanCam:wifi").apply {
            setReferenceCounted(false); acquire()
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release(); wifiLock = null
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live stream", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("LanCam is live")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }

    // ---- ConnectChecker ----
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) { updateStatus() }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}

    override fun onDestroy() {
        if (StreamState.live) stop()
        if (camera.isOnPreview) camera.stopPreview()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "live"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.arvinderss.lancam.STOP"
    }
}
