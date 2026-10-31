package com.dimkabuki.lanmonitor

/**
 * Bounds the number of Camera-side signaling peers.
 *
 * Admission is reserved when a socket opens, not only after its join message, so simultaneous
 * handshakes cannot overbook the Camera. The reservation is released when that socket is cleaned up.
 */
internal class CameraPeerAdmissionPolicy(
    private val maxPeers: Int = MAX_MONITORS,
) {
    private val lock = Any()
    private val admittedPeerIds = mutableSetOf<String>()

    init {
        require(maxPeers > 0) { "Maximum peer count must be positive." }
    }

    fun tryAdmit(peerId: String): Boolean = synchronized(lock) {
        if (peerId.isBlank() || peerId in admittedPeerIds || admittedPeerIds.size >= maxPeers) {
            false
        } else {
            admittedPeerIds.add(peerId)
        }
    }

    fun release(peerId: String): Boolean = synchronized(lock) {
        admittedPeerIds.remove(peerId)
    }

    fun activeCount(): Int = synchronized(lock) { admittedPeerIds.size }

    companion object {
        const val MAX_MONITORS = 2
    }
}
