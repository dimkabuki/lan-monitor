package com.dimkabuki.lanmonitor

/** Pure selection policy shared by the Android NSD adapter and JVM tests. */
internal object CameraDiscoveryPolicy {
    /**
     * Select any valid Lan Monitor Camera on the local network, without requiring a prior pairing.
     * The advertised identity must still be a canonical UUID and the endpoint must be private IPv4.
     */
    fun selectDiscoveredCamera(
        advertisedCameraId: String?,
        candidateAddress: String?,
    ): String? {
        if (advertisedCameraId == null || !isCanonicalUuid(advertisedCameraId)) return null
        val address = candidateAddress ?: return null
        return address.takeIf(LocalLanAddress::isPrivateIpv4)
    }

    /**
     * Select a connection target only when the advertisement identifies the QR-paired Camera.
     */
    fun selectAddress(
        expectedCameraId: String,
        advertisedCameraId: String?,
        candidateAddress: String?,
    ): String? {
        if (!isCanonicalUuid(expectedCameraId)) return null
        if (advertisedCameraId != expectedCameraId) return null
        return selectDiscoveredCamera(advertisedCameraId, candidateAddress)
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value.lowercase() }
            .getOrDefault(false)
}
