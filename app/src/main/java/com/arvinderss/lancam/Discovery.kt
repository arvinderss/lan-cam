package com.arvinderss.lancam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/** Advertises the RTSP stream on the LAN via mDNS / DNS-SD. Never touches the internet. */
class Discovery(private val context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.RegistrationListener? = null

    val serviceName = "LanCam-${Build.MODEL}".replace(" ", "-")

    fun start() {
        lock = wifi.createMulticastLock("lancam-mdns").apply { setReferenceCounted(false); acquire() }
        val info = NsdServiceInfo().apply {
            serviceName = this@Discovery.serviceName
            serviceType = StreamConfig.SERVICE_TYPE
            port = StreamConfig.PORT
            setAttribute("path", "/${StreamConfig.PATH}")
            setAttribute("codec", "h265")
        }
        listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) = Log.i(TAG, "mDNS registered: ${i.serviceName}")
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) = Log.w(TAG, "mDNS failed: $e")
            override fun onServiceUnregistered(i: NsdServiceInfo) = Log.i(TAG, "mDNS unregistered")
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) = Log.w(TAG, "mDNS unregister failed: $e")
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
        lock?.release(); lock = null
    }

    companion object { private const val TAG = "LanCam" }
}
