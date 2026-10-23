package com.dimkabuki.lanmonitor

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object LocalLanAddress {
    /**
     * Restrict manual signaling targets to RFC 1918 home/private IPv4 ranges.
     * This is a simple input guard, not a security boundary; the pairing token
     * is still required by the Camera-hosted signaling endpoint.
     */
    fun isPrivateIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        if (parts.any { part ->
                part.isEmpty() || part.length > 3 ||
                    (part.length > 1 && part.startsWith('0')) ||
                    part.any { !it.isDigit() }
            }
        ) return false

        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    fun privateIpv4Addresses(): List<String> = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?: return@runCatching emptyList()

        Collections.list(interfaces)
            .filter { networkInterface ->
                runCatching { networkInterface.isUp && !networkInterface.isLoopback }
                    .getOrDefault(false)
            }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
            }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter(::isPrivateIpv4)
            .distinct()
            .sortedWith(compareBy<String>({ addressPriority(it) }, { it }))
    }.getOrDefault(emptyList())

    private fun addressPriority(address: String): Int = when {
        address.startsWith("192.168.") -> 0
        address.startsWith("10.") -> 1
        address.startsWith("172.") -> 2
        else -> 3
    }
}
