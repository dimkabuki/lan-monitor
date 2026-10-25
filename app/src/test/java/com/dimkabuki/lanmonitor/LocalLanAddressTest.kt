package com.dimkabuki.lanmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLanAddressTest {
    @Test
    fun acceptsHomeAndPrivateIpv4Ranges() {
        listOf(
            "192.168.1.20",
            "10.0.0.8",
            "172.16.0.5",
            "172.31.255.254",
            "169.254.1.2",
        ).forEach { assertTrue("Expected private LAN address: $it", LocalLanAddress.isPrivateIpv4(it)) }
    }

    @Test
    fun rejectsPublicAddressesAndInvalidInput() {
        listOf(
            "8.8.8.8",
            "172.32.0.1",
            "192.167.1.1",
            "localhost",
            "192.168.1",
            "192.168.001.1",
            "256.1.1.1",
            "",
        ).forEach { assertFalse("Expected rejected address: $it", LocalLanAddress.isPrivateIpv4(it)) }
    }
    @Test
    fun reverseResolvedLanHostnameIsNotTreatedAsNumericPeerAddress() {
        // Ktor's remoteHost may be a reverse-DNS name even when the socket peer is on the LAN.
        assertFalse(LocalLanAddress.isPrivateIpv4("Dmytro-s-S25.fritz.box"))
        assertTrue(LocalLanAddress.isPrivateIpv4("192.168.178.25"))
    }

}
