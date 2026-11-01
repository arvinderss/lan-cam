package com.arvinderss.lancam

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object LanUtils {
    /** First private IPv4 address on an active interface, or null. */
    fun localIpv4(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { isPrivate(it) }
        ?.hostAddress

    /**
     * True only for LAN addresses: RFC1918 (10/8, 172.16/12, 192.168/16),
     * link-local (169.254/16, fe80::/10), loopback, and IPv6 unique-local (fc00::/7).
     */
    fun isPrivate(addr: InetAddress): Boolean {
        if (addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isLoopbackAddress) return true
        if (addr is Inet6Address) return (addr.address[0].toInt() and 0xFE) == 0xFC
        return false
    }

    /** Parses an IP literal (never does a DNS lookup) and checks it. Unparseable = blocked. */
    fun isPrivate(host: String): Boolean {
        val literal = host.trim().removePrefix("/").substringBefore('%').removeSurrounding("[", "]")
        if (literal.isEmpty() || !literal.all { it.isDigit() || it in "abcdefABCDEF.:" }) return false
        return runCatching { isPrivate(InetAddress.getByName(literal)) }.getOrDefault(false)
    }
}
