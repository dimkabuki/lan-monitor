package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraDiscoveryPolicyTest {
    private val cameraId = "a8d8aa40-9f8f-4e51-a92a-472d64bf9b15"

    @Test
    fun acceptsAddressOnlyWhenAdvertisedIdentityMatchesPairing() {
        assertEquals(
            "192.168.1.32",
            CameraDiscoveryPolicy.selectAddress(cameraId, cameraId, "192.168.1.32"),
        )
    }

    @Test
    fun rejectsUnpairedCameraIdentity() {
        assertNull(
            CameraDiscoveryPolicy.selectAddress(
                cameraId,
                "b8d8aa40-9f8f-4e51-a92a-472d64bf9b15",
                "192.168.1.32",
            ),
        )
    }


    @Test
    fun acceptsAnyValidCameraIdentityOnThePrivateLanWithoutPairing() {
        assertEquals(
            "192.168.1.45",
            CameraDiscoveryPolicy.selectDiscoveredCamera(
                "c8d8aa40-9f8f-4e51-a92a-472d64bf9b15",
                "192.168.1.45",
            ),
        )
    }

    @Test
    fun rejectsInvalidIdentityOrNonPrivateEndpointInOpenDiscovery() {
        assertNull(CameraDiscoveryPolicy.selectDiscoveredCamera(null, "192.168.1.45"))
        assertNull(CameraDiscoveryPolicy.selectDiscoveredCamera("not-a-uuid", "192.168.1.45"))
        assertNull(CameraDiscoveryPolicy.selectDiscoveredCamera(cameraId, "8.8.8.8"))
        assertNull(CameraDiscoveryPolicy.selectDiscoveredCamera(cameraId, "localhost"))
    }

    @Test
    fun rejectsMalformedIdentityAndNonPrivateAddresses() {
        assertNull(CameraDiscoveryPolicy.selectAddress("bad-id", cameraId, "192.168.1.32"))
        assertNull(CameraDiscoveryPolicy.selectAddress(cameraId, cameraId, "8.8.8.8"))
        assertNull(CameraDiscoveryPolicy.selectAddress(cameraId, cameraId, "localhost"))
        assertNull(CameraDiscoveryPolicy.selectAddress(cameraId, cameraId, null))
        assertNull(CameraDiscoveryPolicy.selectAddress(cameraId, null, "192.168.1.32"))
    }
}
