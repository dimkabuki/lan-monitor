package com.dimkabuki.lanmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAudioMuteStateTest {
    @Test
    fun startsUnmuted() {
        assertFalse(RemoteAudioMuteState().isMuted)
    }

    @Test
    fun toggleAlternatesMuteState() {
        val state = RemoteAudioMuteState()

        assertTrue(state.toggle())
        assertFalse(state.toggle())
        assertTrue(state.toggle())
    }

    @Test
    fun resetRestoresUnmutedState() {
        val state = RemoteAudioMuteState()
        state.toggle()

        state.reset()

        assertFalse(state.isMuted)
    }
}
