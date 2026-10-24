package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CameraPairingCodecTest {
    private val cameraId = "a8d8aa40-9f8f-4e51-a92a-472d64bf9b15"
    private val token = "AbCdEfGhIjKlMnOp"

    @Test
    fun roundTripsVersionedPayloadWithMultipleLanAddresses() {
        val expected = CameraPairingPayload(
            cameraId = cameraId,
            addresses = listOf("192.168.1.20", "10.0.0.8"),
            port = 8765,
            token = token,
        )

        val decoded = CameraPairingCodec.decode(CameraPairingCodec.encode(expected))

        assertEquals(expected, decoded)
    }

    @Test
    fun acceptsOnlyPrivateLanAddressHints() {
        val encoded = CameraPairingCodec.encode(
            CameraPairingPayload(cameraId, listOf("192.168.1.20"), 8765, token),
        )

        assertEquals(listOf("192.168.1.20"), CameraPairingCodec.decode(encoded)?.addresses)
        assertNull(CameraPairingCodec.decode(encoded.replace("192.168.1.20", "8.8.8.8")))
        assertNull(CameraPairingCodec.decode(encoded.replace("192.168.1.20", "localhost")))
    }

    @Test
    fun rejectsUnknownVersionsMalformedPayloadsAndUntrustedSchemes() {
        val valid = CameraPairingCodec.encode(
            CameraPairingPayload(cameraId, listOf("192.168.1.20"), 8765, token),
        )

        assertNull(CameraPairingCodec.decode(""))
        assertNull(CameraPairingCodec.decode("not a URI"))
        assertNull(CameraPairingCodec.decode(valid.replace("version=1", "version=2")))
        assertNull(CameraPairingCodec.decode(valid.replace("lanmonitor://pair", "https://pair")))
        assertNull(CameraPairingCodec.decode(valid.replace("cameraId=$cameraId", "cameraId=invalid")))
        assertNull(CameraPairingCodec.decode(valid.replace("token=$token", "token=short")))
        assertNull(CameraPairingCodec.decode(valid.replace("lanmonitor://pair?", "lanmonitor://untrusted@pair?")))
        assertNull(CameraPairingCodec.decode(valid.replace("lanmonitor://pair?", "lanmonitor://pair:8765?")))
        assertNull(CameraPairingCodec.decode(valid.replace("lanmonitor://pair?", "lanmonitor://pair/extra?")))
    }

    @Test
    fun rejectsDuplicateSecuritySensitiveFieldsAndUnknownFields() {
        val valid = CameraPairingCodec.encode(
            CameraPairingPayload(cameraId, listOf("192.168.1.20"), 8765, token),
        )

        assertNull(CameraPairingCodec.decode("$valid&token=$token"))
        assertNull(CameraPairingCodec.decode("$valid&unexpected=value"))
        assertNull(CameraPairingCodec.decode("$valid&cameraId=${UUID.randomUUID()}"))
    }

    @Test
    fun encodeRejectsPublicAddressesInvalidTokensAndWrongPorts() {
        assertFalse(runCatching {
            CameraPairingCodec.encode(
                CameraPairingPayload(cameraId, listOf("8.8.8.8"), 8765, token),
            )
        }.isSuccess)
        assertFalse(runCatching {
            CameraPairingCodec.encode(
                CameraPairingPayload(cameraId, listOf("192.168.1.20"), 8765, "short"),
            )
        }.isSuccess)
        assertFalse(runCatching {
            CameraPairingCodec.encode(
                CameraPairingPayload(cameraId, listOf("192.168.1.20"), 9999, token),
            )
        }.isSuccess)
    }

    @Test
    fun rejectsTooLongPayload() {
        assertNull(CameraPairingCodec.decode("lanmonitor://pair?" + "a".repeat(1_025)))
    }
}
