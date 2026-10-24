package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogSanitizerTest {
    @Test
    fun redactsPairingSecretsAndSensitiveAssignments() {
        val safe = DiagnosticLogSanitizer.sanitize(
            "token=super-secret pairing_token:another-secret password=hunter2 host=192.168.1.4",
        )

        assertFalse(safe.contains("super-secret"))
        assertFalse(safe.contains("another-secret"))
        assertFalse(safe.contains("hunter2"))
        assertTrue(safe.contains("host=192.168.1.4"))
        assertTrue(safe.contains("token=[REDACTED]"))
    }

    @Test
    fun redactsSdpAndIceCandidatePayloads() {
        assertEquals(
            "[REDACTED SDP/ICE payload]",
            DiagnosticLogSanitizer.sanitize("offer sdp=v=0\r\nm=audio 9 RTP/AVP 0"),
        )
        assertEquals(
            "[REDACTED SDP/ICE payload]",
            DiagnosticLogSanitizer.sanitize("candidate:a=candidate:1 1 UDP 2122260223"),
        )
    }

    @Test
    fun flattensMultilineDetailsAndLimitsSize() {
        val sanitized = DiagnosticLogSanitizer.sanitize("first line\nsecond\tline")
        assertEquals("first line second line", sanitized)
        assertTrue(DiagnosticLogSanitizer.sanitize("x".repeat(1000)).length <= 700)
    }
}
