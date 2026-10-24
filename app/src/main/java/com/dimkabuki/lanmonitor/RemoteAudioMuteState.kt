package com.dimkabuki.lanmonitor

/**
 * Holds the Monitor playback mute choice across WebRTC peer reconnections.
 *
 * UI toggles and remote-track callbacks can run on different threads.
 */
internal class RemoteAudioMuteState {
    @Volatile
    var isMuted: Boolean = false
        private set

    @Synchronized
    fun toggle(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    @Synchronized
    fun reset() {
        isMuted = false
    }
}
