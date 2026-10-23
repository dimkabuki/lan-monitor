package com.dimkabuki.lanmonitor

/** Result of comparing the last decoded video-frame time with the current monotonic time. */
internal data class VideoFrameHealthSnapshot(
    val isHealthy: Boolean,
    val frameAgeMs: Long?,
)

/**
 * Pure time-based health policy for decoded video frames. A null age means no frame has ever
 * been observed in this session; a non-null age is milliseconds since the last decoded frame.
 */
internal object VideoFrameHealth {
    fun evaluate(
        lastFrameAtMs: Long,
        nowMs: Long,
        stallTimeoutMs: Long,
    ): VideoFrameHealthSnapshot {
        require(stallTimeoutMs > 0L) { "Stall timeout must be positive." }
        if (lastFrameAtMs <= 0L) {
            return VideoFrameHealthSnapshot(isHealthy = false, frameAgeMs = null)
        }

        // elapsedRealtime is monotonic; clamp defensively if a caller supplies inconsistent data.
        val ageMs = (nowMs - lastFrameAtMs).coerceAtLeast(0L)
        return VideoFrameHealthSnapshot(
            isHealthy = ageMs <= stallTimeoutMs,
            frameAgeMs = ageMs,
        )
    }
}
