package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameHealthTest {
    @Test
    fun noObservedFrameIsUnhealthyAndHasNoAge() {
        val result = VideoFrameHealth.evaluate(
            lastFrameAtMs = 0L,
            nowMs = 10_000L,
            stallTimeoutMs = 3_000L,
        )

        assertFalse(result.isHealthy)
        assertNull(result.frameAgeMs)
    }

    @Test
    fun frameAtStallBoundaryIsHealthy() {
        val result = VideoFrameHealth.evaluate(
            lastFrameAtMs = 7_000L,
            nowMs = 10_000L,
            stallTimeoutMs = 3_000L,
        )

        assertTrue(result.isHealthy)
        assertEquals(3_000L, result.frameAgeMs)
    }

    @Test
    fun frameBeyondStallBoundaryIsUnhealthy() {
        val result = VideoFrameHealth.evaluate(
            lastFrameAtMs = 6_999L,
            nowMs = 10_000L,
            stallTimeoutMs = 3_000L,
        )

        assertFalse(result.isHealthy)
        assertEquals(3_001L, result.frameAgeMs)
    }

    @Test
    fun negativeCalculatedAgeIsClampedToZero() {
        val result = VideoFrameHealth.evaluate(
            lastFrameAtMs = 10_001L,
            nowMs = 10_000L,
            stallTimeoutMs = 3_000L,
        )

        assertTrue(result.isHealthy)
        assertEquals(0L, result.frameAgeMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveStallTimeout() {
        VideoFrameHealth.evaluate(
            lastFrameAtMs = 1L,
            nowMs = 2L,
            stallTimeoutMs = 0L,
        )
    }
}
