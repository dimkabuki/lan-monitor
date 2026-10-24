package com.dimkabuki.lanmonitor

import kotlin.math.abs
import org.webrtc.VideoFrame

/**
 * A deliberately small, in-memory view of decoded video.
 * Analysis is capped at four samples per second by the caller.
 */
internal object MovementFrameSampler {
    const val SAMPLE_WIDTH = 96
    const val SAMPLE_HEIGHT = 72
    const val ANALYSIS_INTERVAL_MS = 250L

    fun sampleLuma(buffer: VideoFrame.I420Buffer): ByteArray {
        val width = buffer.width
        val height = buffer.height
        require(width > 0 && height > 0) { "Video frame dimensions must be positive." }
        val stride = buffer.strideY
        require(stride >= width) { "Video frame luma stride is invalid." }

        val data = buffer.dataY.duplicate()
        val start = data.position()
        require(start + (height - 1) * stride + width <= data.limit()) {
            "Video frame luma plane is smaller than its dimensions."
        }

        val sampled = ByteArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        var targetIndex = 0
        for (sampleY in 0 until SAMPLE_HEIGHT) {
            val sourceY = (((2 * sampleY + 1) * height) / (2 * SAMPLE_HEIGHT))
                .coerceIn(0, height - 1)
            val sourceRow = start + sourceY * stride
            for (sampleX in 0 until SAMPLE_WIDTH) {
                val sourceX = (((2 * sampleX + 1) * width) / (2 * SAMPLE_WIDTH))
                    .coerceIn(0, width - 1)
                sampled[targetIndex++] = data.get(sourceRow + sourceX)
            }
        }
        return sampled
    }
}

internal data class MovementDetectionResult(
    val movementStarted: Boolean,
    val notificationAllowed: Boolean,
    val changedFraction: Double,
)

/**
 * Pixel-difference policy over reduced luma frames. Mean signed luma change is subtracted before
 * changed samples are counted, suppressing uniform brightness shifts. Motion is confirmed over
 * several samples and is re-armed only after several quiet samples.
 */
internal class MovementDetector(
    private val changedFractionThreshold: Double = 0.10,
    private val lumaDifferenceThreshold: Int = 20,
    private val consecutiveMotionFramesRequired: Int = 3,
    private val stationaryFramesToReset: Int = 4,
    private val alertCooldown: MovementAlertCooldown = MovementAlertCooldown(),
) {
    private var previousFrame: ByteArray? = null
    private var consecutiveMotionFrames = 0
    private var consecutiveStationaryFrames = 0
    private var moving = false

    init {
        require(changedFractionThreshold in 0.0..1.0) { "Changed-fraction threshold must be between 0 and 1." }
        require(lumaDifferenceThreshold in 1..255) { "Luma difference threshold must be between 1 and 255." }
        require(consecutiveMotionFramesRequired > 0) { "Motion confirmation frame count must be positive." }
        require(stationaryFramesToReset > 0) { "Stationary reset frame count must be positive." }
    }

    @Synchronized
    fun process(samples: ByteArray, nowMs: Long): MovementDetectionResult {
        require(samples.isNotEmpty()) { "Movement samples must not be empty." }
        require(nowMs >= 0L) { "Timestamp must not be negative." }

        val previous = previousFrame
        if (previous == null || previous.size != samples.size) {
            previousFrame = samples.copyOf()
            consecutiveMotionFrames = 0
            consecutiveStationaryFrames = 0
            moving = false
            return MovementDetectionResult(false, false, 0.0)
        }

        var signedDeltaSum = 0L
        for (index in samples.indices) {
            signedDeltaSum += (samples[index].toInt() and 0xFF) - (previous[index].toInt() and 0xFF)
        }
        val averageSignedDelta = signedDeltaSum.toDouble() / samples.size

        var changedSamples = 0
        for (index in samples.indices) {
            val current = samples[index].toInt() and 0xFF
            val old = previous[index].toInt() and 0xFF
            if (abs((current - old) - averageSignedDelta) >= lumaDifferenceThreshold) changedSamples++
        }

        val changedFraction = changedSamples.toDouble() / samples.size
        previousFrame = samples.copyOf()

        if (changedFraction >= changedFractionThreshold) {
            consecutiveMotionFrames++
            consecutiveStationaryFrames = 0
            if (!moving && consecutiveMotionFrames >= consecutiveMotionFramesRequired) {
                moving = true
                return MovementDetectionResult(
                    movementStarted = true,
                    notificationAllowed = alertCooldown.tryAcquire(nowMs),
                    changedFraction = changedFraction,
                )
            }
        } else {
            consecutiveMotionFrames = 0
            if (moving) {
                consecutiveStationaryFrames++
                if (consecutiveStationaryFrames >= stationaryFramesToReset) {
                    moving = false
                    consecutiveStationaryFrames = 0
                }
            }
        }

        return MovementDetectionResult(false, false, changedFraction)
    }

    /** Reset frame history but preserve the current session's notification cooldown. */
    @Synchronized
    fun resetBaseline() {
        previousFrame = null
        consecutiveMotionFrames = 0
        consecutiveStationaryFrames = 0
        moving = false
    }

    @Synchronized
    fun reset() {
        resetBaseline()
        alertCooldown.reset()
    }
}

internal class MovementAlertCooldown(
    private val cooldownMs: Long = 8_000L,
) {
    private var lastNotificationAtMs: Long? = null

    init {
        require(cooldownMs > 0L) { "Cooldown must be positive." }
    }

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        require(nowMs >= 0L) { "Timestamp must not be negative." }
        val last = lastNotificationAtMs
        if (last != null && (nowMs < last || nowMs - last < cooldownMs)) return false
        lastNotificationAtMs = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        lastNotificationAtMs = null
    }
}
