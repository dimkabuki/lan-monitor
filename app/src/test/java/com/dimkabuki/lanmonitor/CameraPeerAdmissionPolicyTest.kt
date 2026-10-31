package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPeerAdmissionPolicyTest {
    @Test
    fun admitsTwoIndependentMonitorsAndRejectsThird() {
        val policy = CameraPeerAdmissionPolicy()

        assertTrue(policy.tryAdmit("monitor-a"))
        assertTrue(policy.tryAdmit("monitor-b"))
        assertFalse(policy.tryAdmit("monitor-c"))
        assertEquals(2, policy.activeCount())
    }

    @Test
    fun releasingOnePeerAllowsAnotherMonitorToJoin() {
        val policy = CameraPeerAdmissionPolicy()

        assertTrue(policy.tryAdmit("monitor-a"))
        assertTrue(policy.tryAdmit("monitor-b"))
        assertTrue(policy.release("monitor-a"))
        assertFalse(policy.release("monitor-a"))
        assertTrue(policy.tryAdmit("monitor-c"))
        assertEquals(2, policy.activeCount())
    }

    @Test
    fun rejectsBlankAndDuplicatePeerIds() {
        val policy = CameraPeerAdmissionPolicy()

        assertFalse(policy.tryAdmit(""))
        assertTrue(policy.tryAdmit("monitor-a"))
        assertFalse(policy.tryAdmit("monitor-a"))
        assertEquals(1, policy.activeCount())
    }
}
