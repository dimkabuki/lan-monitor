package com.dimkabuki.lanmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureRecoveryPolicyTest {
    @Test
    fun missingLocalFramesRequestsRecovery() {
        assertTrue(CameraCaptureRecoveryPolicy.shouldRecover(lastFrameAtMs = 0L, nowMs = 10_000L))
    }

    @Test
    fun recentLocalFrameDoesNotRequestRecovery() {
        assertFalse(CameraCaptureRecoveryPolicy.shouldRecover(lastFrameAtMs = 9_000L, nowMs = 10_000L))
    }

    @Test
    fun stalledLocalFramesRequestRecoveryAtTimeout() {
        assertTrue(
            CameraCaptureRecoveryPolicy.shouldRecover(
                lastFrameAtMs = 7_500L,
                nowMs = 10_000L,
            ),
        )
    }
}
