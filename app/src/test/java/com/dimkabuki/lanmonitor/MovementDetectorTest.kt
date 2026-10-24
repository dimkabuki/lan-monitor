package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementDetectorTest {
    @Test
    fun firstFrameOnlyEstablishesBaseline() {
        val detector = MovementDetector()
        val result = detector.process(frameWithBlock(x = 0), nowMs = 0L)

        assertFalse(result.movementStarted)
        assertFalse(result.notificationAllowed)
        assertEquals(0.0, result.changedFraction, 0.0)
    }

    @Test
    fun sustainedPixelChangeConfirmsOneMovementEpisode() {
        val detector = MovementDetector()
        detector.process(frameWithBlock(x = 0), nowMs = 0L)

        assertFalse(detector.process(frameWithBlock(x = 20), nowMs = 250L).movementStarted)
        assertFalse(detector.process(frameWithBlock(x = 40), nowMs = 500L).movementStarted)
        val confirmed = detector.process(frameWithBlock(x = 60), nowMs = 750L)

        assertTrue(confirmed.movementStarted)
        assertTrue(confirmed.notificationAllowed)
        assertTrue(confirmed.changedFraction >= 0.10)
    }

    @Test
    fun oneOrTwoChangedSamplesDoNotConfirmMotion() {
        val detector = MovementDetector()
        detector.process(frameWithBlock(x = 0), nowMs = 0L)

        assertFalse(detector.process(frameWithBlock(x = 20), nowMs = 250L).movementStarted)
        assertFalse(detector.process(frameWithBlock(x = 40), nowMs = 500L).movementStarted)
        assertFalse(detector.process(frameWithBlock(x = 40), nowMs = 750L).movementStarted)
        assertFalse(detector.process(frameWithBlock(x = 40), nowMs = 1_000L).movementStarted)
    }

    @Test
    fun uniformBrightnessShiftDoesNotConfirmMotion() {
        val detector = MovementDetector()
        detector.process(flatFrame(value = 40), nowMs = 0L)

        repeat(10) { index ->
            val result = detector.process(flatFrame(value = 90), nowMs = 250L * (index + 1))
            assertFalse(result.movementStarted)
            assertEquals(0.0, result.changedFraction, 0.0)
        }
    }

    @Test
    fun smallLumaNoiseDoesNotConfirmMotion() {
        val detector = MovementDetector()
        val baseline = flatFrame(value = 80)
        detector.process(baseline, nowMs = 0L)
        val noisy = baseline.copyOf()
        for (index in noisy.indices step 11) {
            noisy[index] = ((noisy[index].toInt() and 0xFF) + 3).toByte()
        }

        repeat(10) { index ->
            val result = detector.process(noisy, nowMs = 250L * (index + 1))
            assertFalse(result.movementStarted)
        }
    }

    @Test
    fun movementMustBecomeStationaryBeforeAnotherEpisodeCanStart() {
        val detector = MovementDetector()
        detector.process(frameWithBlock(x = 0), nowMs = 0L)
        detector.process(frameWithBlock(x = 20), nowMs = 250L)
        detector.process(frameWithBlock(x = 40), nowMs = 500L)
        assertTrue(detector.process(frameWithBlock(x = 60), nowMs = 750L).movementStarted)

        repeat(4) { index ->
            val result = detector.process(frameWithBlock(x = 60), nowMs = 1_000L + index * 250L)
            assertFalse(result.movementStarted)
        }

        detector.process(frameWithBlock(x = 30), nowMs = 2_000L)
        detector.process(frameWithBlock(x = 0), nowMs = 2_250L)
        val nextEpisode = detector.process(frameWithBlock(x = 30), nowMs = 2_500L)
        assertTrue(nextEpisode.movementStarted)
        assertFalse(nextEpisode.notificationAllowed)
    }

    private fun flatFrame(value: Int): ByteArray =
        ByteArray(MovementFrameSampler.SAMPLE_WIDTH * MovementFrameSampler.SAMPLE_HEIGHT) { value.toByte() }

    private fun frameWithBlock(x: Int, blockValue: Int = 200): ByteArray {
        val frame = flatFrame(value = 60)
        val width = MovementFrameSampler.SAMPLE_WIDTH
        val height = MovementFrameSampler.SAMPLE_HEIGHT
        val blockWidth = 30
        val blockHeight = 30
        for (y in 18 until 18 + blockHeight) {
            for (column in x until x + blockWidth) {
                if (column in 0 until width && y in 0 until height) {
                    frame[y * width + column] = blockValue.toByte()
                }
            }
        }
        return frame
    }
}

class MovementAlertCooldownTest {
    @Test
    fun cooldownAllowsFirstEventAndTheExactEightSecondBoundary() {
        val cooldown = MovementAlertCooldown(cooldownMs = 8_000L)

        assertTrue(cooldown.tryAcquire(1_000L))
        assertFalse(cooldown.tryAcquire(8_999L))
        assertTrue(cooldown.tryAcquire(9_000L))
    }

    @Test
    fun cooldownRejectsOutOfOrderTimestamps() {
        val cooldown = MovementAlertCooldown()

        assertTrue(cooldown.tryAcquire(5_000L))
        assertFalse(cooldown.tryAcquire(4_000L))
        assertFalse(cooldown.tryAcquire(12_999L))
        assertTrue(cooldown.tryAcquire(13_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCooldown() {
        MovementAlertCooldown(cooldownMs = 0L)
    }
}
