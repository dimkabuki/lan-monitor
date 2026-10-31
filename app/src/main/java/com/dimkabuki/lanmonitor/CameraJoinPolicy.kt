package com.dimkabuki.lanmonitor

/** The admission mode selected by a Monitor's first signaling message. */
internal enum class CameraJoinMode {
    OPEN_LAN,
    PAIRED_TOKEN,
}

/** Validates the signaling handshake while keeping tokenless LAN joins explicit. */
internal object CameraJoinPolicy {
    fun resolve(
        messageType: String,
        expectedToken: String,
        suppliedToken: String,
    ): CameraJoinMode? {
        if (messageType != "join") return null
        if (suppliedToken.isBlank()) return CameraJoinMode.OPEN_LAN
        return CameraJoinMode.PAIRED_TOKEN.takeIf {
            PairingToken.matches(expectedToken, suppliedToken)
        }
    }
}
