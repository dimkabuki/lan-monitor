package com.dimkabuki.lanmonitor

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Versioned data encoded in a Camera QR code. The stable cameraId is independent of an IP address;
 * addresses are only connection hints and can later be rediscovered using local service discovery.
 */
internal data class CameraPairingPayload(
    val cameraId: String,
    val addresses: List<String>,
    val port: Int,
    val token: String,
)

internal object CameraPairingCodec {
    private const val SCHEME = "lanmonitor"
    private const val HOST = "pair"
    private const val VERSION = "1"
    private const val MAX_PAYLOAD_LENGTH = 1_024
    private const val MAX_ADDRESSES = 8
    private const val SIGNAL_PORT = 8765
    private val tokenPattern = Regex("[A-Za-z0-9_-]{16}")

    fun encode(payload: CameraPairingPayload): String {
        require(isCanonicalUuid(payload.cameraId)) { "Camera identity must be a UUID." }
        require(payload.addresses.isNotEmpty() && payload.addresses.size <= MAX_ADDRESSES) {
            "Pairing QR must contain between 1 and $MAX_ADDRESSES local addresses."
        }
        require(payload.addresses.all(LocalLanAddress::isPrivateIpv4)) {
            "Pairing QR may contain private LAN IPv4 addresses only."
        }
        require(payload.port == SIGNAL_PORT) { "Unsupported signaling port." }
        require(tokenPattern.matches(payload.token)) { "Pairing token has an invalid format." }

        val parameters = mutableListOf(
            "version=$VERSION",
            "cameraId=${urlEncode(payload.cameraId)}",
            "port=${payload.port}",
            "token=${urlEncode(payload.token)}",
        )
        payload.addresses.distinct().forEach { parameters += "address=${urlEncode(it)}" }
        return "$SCHEME://$HOST?${parameters.joinToString("&")}"
    }

    /**
     * Returns null for unknown versions, malformed payloads, non-local addresses or invalid
     * credentials. A scanned QR never authorizes arbitrary non-private network targets.
     */
    fun decode(raw: String): CameraPairingPayload? {
        if (raw.isBlank() || raw.length > MAX_PAYLOAD_LENGTH) return null

        return runCatching {
            val uri = URI(raw)
            if (uri.scheme != SCHEME || uri.host != HOST || uri.rawFragment != null) return null
            val query = uri.rawQuery ?: return null
            val parameters = parseQuery(query)

            fun one(name: String): String? = parameters[name]?.singleOrNull()

            if (one("version") != VERSION) return null
            val cameraId = one("cameraId") ?: return null
            if (!isCanonicalUuid(cameraId)) return null
            val port = one("port")?.toIntOrNull() ?: return null
            if (port != SIGNAL_PORT) return null
            val token = one("token") ?: return null
            if (!tokenPattern.matches(token)) return null

            val addresses = parameters["address"].orEmpty().distinct()
            if (addresses.isEmpty() || addresses.size > MAX_ADDRESSES) return null
            if (addresses.any { !LocalLanAddress.isPrivateIpv4(it) }) return null

            CameraPairingPayload(
                cameraId = cameraId,
                addresses = addresses,
                port = port,
                token = token,
            )
        }.getOrNull()
    }

    private fun parseQuery(query: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        query.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val separator = part.indexOf('=')
            if (separator <= 0) throw IllegalArgumentException("Malformed query parameter.")
            val key = urlDecode(part.substring(0, separator))
            val value = urlDecode(part.substring(separator + 1))
            if (key !in setOf("version", "cameraId", "port", "token", "address")) {
                throw IllegalArgumentException("Unknown pairing parameter.")
            }
            result.getOrPut(key) { mutableListOf() }.add(value)
        }
        return result
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
