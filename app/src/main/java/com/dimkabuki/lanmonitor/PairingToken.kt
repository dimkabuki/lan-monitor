package com.dimkabuki.lanmonitor

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates and compares temporary local-pairing secrets without relying on Android-only APIs,
 * so the security-sensitive behavior can be exercised by ordinary JVM unit tests.
 */
internal object PairingToken {
    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(12) // 96 bits of entropy
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun matches(expected: String, actual: String): Boolean {
        if (expected.isBlank() || actual.isBlank()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }
}
