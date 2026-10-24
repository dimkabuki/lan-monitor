package com.dimkabuki.lanmonitor

/** Pure selection policy shared by the Android NSD adapter and JVM tests. */
internal object CameraDiscoveryPolicy {
    /**
     * Select a connection target only when the advertisement identifies the QR-paired Camera and
     * supplies a private/link-local IPv4 address. Service names and IP addresses are not identity.
     */
    fun selectAddress(
        expectedCameraId: String,
        advertisedCameraId: String?,
        candidateAddress: String?,
    ): String? {
        if (!isCanonicalUuid(expectedCameraId)) return null
        if (advertisedCameraId != expectedCameraId) return null
        val address = candidateAddress ?: return null
        return address.takeIf(LocalLanAddress::isPrivateIpv4)
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value.lowercase() }
            .getOrDefault(false)
}
