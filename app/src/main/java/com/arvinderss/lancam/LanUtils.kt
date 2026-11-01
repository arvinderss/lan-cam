package com.arvinderss.lancam

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object LanUtils {
    /** First private IPv4 address on a Wi-Fi/Ethernet interface, or null. */
    fun localIpv4(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { isPrivate(it) }
        ?.hostAddress

    /** True for RFC1918 / link-local / loopback addresses only. */
    fun isPrivate(addr: InetAddress): Boolean =
        addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isLoopbackAddress
}
