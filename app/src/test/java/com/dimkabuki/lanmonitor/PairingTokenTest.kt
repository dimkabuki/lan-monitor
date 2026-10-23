package com.dimkabuki.lanmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTokenTest {
    @Test
    fun generatedTokenIsUrlSafeAndContainsExpectedEntropyEncoding() {
        val token = PairingToken.generate()

        // 12 random bytes encode to 16 unpadded base64url characters.
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]{16}")))
        assertFalse(token.contains("="))
    }

    @Test
    fun tokenMatchesOnlyTheExactNonBlankValue() {
        val token = PairingToken.generate()

        val alteredToken = (if (token.first() == 'A') "B" else "A") + token.drop(1)

        assertTrue(PairingToken.matches(token, token))
        assertFalse(PairingToken.matches(token, ""))
        assertFalse(PairingToken.matches("", token))
        assertFalse(PairingToken.matches(" ", token))
        assertFalse(PairingToken.matches(token, token.dropLast(1)))
        assertFalse(PairingToken.matches(token, alteredToken))
    }
}
