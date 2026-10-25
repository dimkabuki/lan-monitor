package com.dimkabuki.lanmonitor

internal object CameraCaptureRecoveryPolicy {
    const val STALL_TIMEOUT_MS = 2_500L
    const val FOREGROUND_RETURN_GRACE_MS = 3_000L

    fun shouldRecover(lastFrameAtMs: Long, nowMs: Long): Boolean =
        lastFrameAtMs <= 0L || nowMs - lastFrameAtMs >= STALL_TIMEOUT_MS
}
