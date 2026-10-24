package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionRegistryTest {
    @Test
    fun stopAndFailureActionsReachTheRegisteredSession() {
        val owner = Any()
        var stopRequested = false
        var reportedFailure: String? = null

        ActiveSessionRegistry.register(
            owner = owner,
            onStop = { stopRequested = true },
            onFailure = { reportedFailure = it },
        )
        try {
            ActiveSessionRegistry.requestStop()
            ActiveSessionRegistry.reportFailure("foreground service rejected")

            assertTrue(stopRequested)
            assertEquals("foreground service rejected", reportedFailure)
        } finally {
            ActiveSessionRegistry.unregister(owner)
        }
    }

    @Test
    fun unregisteringAnOldOwnerDoesNotClearTheCurrentSession() {
        val oldOwner = Any()
        val currentOwner = Any()
        var oldStopRequested = false
        var currentStopRequested = false

        ActiveSessionRegistry.register(oldOwner, { oldStopRequested = true }, {})
        ActiveSessionRegistry.register(currentOwner, { currentStopRequested = true }, {})
        try {
            ActiveSessionRegistry.unregister(oldOwner)
            ActiveSessionRegistry.requestStop()

            assertFalse(oldStopRequested)
            assertTrue(currentStopRequested)
        } finally {
            ActiveSessionRegistry.unregister(currentOwner)
            ActiveSessionRegistry.unregister(oldOwner)
        }
    }

    @Test
    fun unregisteredSessionDoesNotReceiveStopOrFailureActions() {
        val owner = Any()
        var stopRequested = false
        var reportedFailure: String? = null
        ActiveSessionRegistry.register(
            owner,
            { stopRequested = true },
            { reportedFailure = it },
        )
        ActiveSessionRegistry.unregister(owner)

        ActiveSessionRegistry.requestStop()
        ActiveSessionRegistry.reportFailure("ignored")

        assertFalse(stopRequested)
        assertEquals(null, reportedFailure)
    }
}
