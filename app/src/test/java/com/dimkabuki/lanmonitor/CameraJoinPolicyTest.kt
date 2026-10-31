package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraJoinPolicyTest {
    private val cameraToken = "AbCdEf0123456789"

    @Test
    fun acceptsTokenlessJoinInOpenLanMode() {
        assertEquals(
            CameraJoinMode.OPEN_LAN,
            CameraJoinPolicy.resolve("join", cameraToken, ""),
        )
    }

    @Test
    fun acceptsMatchingTokenForOptionalPairedMode() {
        assertEquals(
            CameraJoinMode.PAIRED_TOKEN,
            CameraJoinPolicy.resolve("join", cameraToken, cameraToken),
        )
    }

    @Test
    fun rejectsWrongNonEmptyTokenAndUnexpectedMessageTypes() {
        assertNull(CameraJoinPolicy.resolve("join", cameraToken, "wrong-token-12345"))
        assertNull(CameraJoinPolicy.resolve("offer", cameraToken, ""))
    }
}
