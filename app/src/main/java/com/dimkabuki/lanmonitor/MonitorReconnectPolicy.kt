package com.dimkabuki.lanmonitor

/**
 * Deterministic retry timing for temporary LAN/signaling/media interruptions.
 */
internal object MonitorReconnectPolicy {
    const val INITIAL_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 5_000L
    const val MEDIA_STALL_RECONNECT_MS = 10_000L

    fun nextDelayMs(previousDelayMs: Long): Long {
        if (previousDelayMs < INITIAL_DELAY_MS) return INITIAL_DELAY_MS
        if (previousDelayMs >= MAX_DELAY_MS) return MAX_DELAY_MS
        return (previousDelayMs * 2).coerceAtMost(MAX_DELAY_MS)
    }
}
