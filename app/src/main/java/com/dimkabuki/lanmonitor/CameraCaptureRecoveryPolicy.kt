package com.dimkabuki.lanmonitor

internal object CameraCaptureRecoveryPolicy {
    const val STALL_TIMEOUT_MS = 2_500L

    fun shouldRecover(lastFrameAtMs: Long, nowMs: Long): Boolean =
        lastFrameAtMs <= 0L || nowMs - lastFrameAtMs >= STALL_TIMEOUT_MS
}
