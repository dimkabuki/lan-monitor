package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorReconnectPolicyTest {
    @Test
    fun retryDelayUsesBoundedExponentialBackoff() {
        assertEquals(2_000L, MonitorReconnectPolicy.nextDelayMs(1_000L))
        assertEquals(4_000L, MonitorReconnectPolicy.nextDelayMs(2_000L))
        assertEquals(5_000L, MonitorReconnectPolicy.nextDelayMs(4_000L))
        assertEquals(5_000L, MonitorReconnectPolicy.nextDelayMs(5_000L))
    }

    @Test
    fun retryDelayNeverFallsBelowInitialDelay() {
        assertEquals(1_000L, MonitorReconnectPolicy.nextDelayMs(0L))
        assertEquals(1_000L, MonitorReconnectPolicy.nextDelayMs(-1L))
    }
}
