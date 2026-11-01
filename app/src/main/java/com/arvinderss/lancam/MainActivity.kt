package com.arvinderss.lancam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arvinderss.lancam.databinding.ActivityMainBinding
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.IpType
import com.pedro.rtspserver.server.ServerClient

class MainActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    private lateinit var b: ActivityMainBinding
    private lateinit var camera: RtspServerCamera2
    private lateinit var discovery: Discovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        camera = RtspServerCamera2(b.preview, this, StreamConfig.PORT)
        discovery = Discovery(this)
        camera.getStreamClient().forceIpType(IpType.IPv4)
        // LAN-only guard: drop any client whose address is not private/link-local.
        camera.getStreamClient().setClientListener(object : ClientListener {
            override fun onClientConnected(client: ServerClient) {
                if (!LanUtils.isPrivate(client.getAddress())) {
                    client.stopClient()
                    toast("Blocked non-LAN client ${client.getAddress()}")
                } else {
                    b.status.append("\nClient: ${client.getAddress()}")
                }
            }
            override fun onClientDisconnected(client: ServerClient) {}
            override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {}
        })
        b.preview.holder.addCallback(this)

        b.resolution.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            StreamConfig.PRESETS.map { it.label }
        )
        // Default to the best preset the back camera actually supports.
        val supported = camera.resolutionsBack.map { it.width to it.height }.toSet()
        val best = StreamConfig.PRESETS.indexOfFirst { (it.width to it.height) in supported }
        if (best >= 0) b.resolution.setSelection(best)

        b.toggle.setOnClickListener { if (camera.isStreaming) stopStream() else startStream() }
        requestPermissions()
    }

    private fun startStream() {
        val p = StreamConfig.PRESETS[b.resolution.selectedItemPosition]
        val supported = camera.resolutionsBack.any { it.width == p.width && it.height == p.height }
        if (!supported) {
            toast("${p.label} not supported by this camera"); return
        }
        camera.setVideoCodec(VideoCodec.H265)
        val videoOk = camera.prepareVideo(p.width, p.height, p.fps, p.bitrateMbps * 1_000_000, 0)
            || run { camera.setVideoCodec(VideoCodec.H264); camera.prepareVideo(p.width, p.height, p.fps, p.bitrateMbps * 1_000_000, 0) }
        val audioOk = camera.prepareAudio(128 * 1024, 48000, true)
        if (!videoOk || !audioOk) { toast("Encoder can't handle ${p.label}"); return }

        camera.startStream()
        discovery.start()
        b.toggle.text = "Stop"
        b.resolution.isEnabled = false
        val ip = LanUtils.localIpv4() ?: "<no LAN IP>"
        b.status.text = "rtsp://$ip:${StreamConfig.PORT}/${StreamConfig.PATH}\nmDNS: ${discovery.serviceName}"
    }

    private fun stopStream() {
        if (camera.isStreaming) camera.stopStream()
        discovery.stop()
        b.toggle.text = "Start"
        b.resolution.isEnabled = true
        b.status.text = "Idle"
    }

    // --- Preview lifecycle ---
    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
        if (hasPermissions() && !camera.isOnPreview) camera.startPreview()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopStream()
        if (camera.isOnPreview) camera.stopPreview()
    }

    // --- ConnectChecker callbacks (one per RTSP client) ---
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() = runOnUiThread { toast("Client connected") }
    override fun onConnectionFailed(reason: String) = runOnUiThread { toast("Client error: $reason") }
    override fun onDisconnect() = runOnUiThread { toast("Client disconnected") }
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
    override fun onNewBitrate(bitrate: Long) {}

    // --- Permissions ---
    private val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private fun hasPermissions() = perms.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestPermissions() {
        if (!hasPermissions()) ActivityCompat.requestPermissions(this, perms, 1)
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (hasPermissions() && !camera.isOnPreview) camera.startPreview()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        stopStream()
        super.onDestroy()
    }
}
