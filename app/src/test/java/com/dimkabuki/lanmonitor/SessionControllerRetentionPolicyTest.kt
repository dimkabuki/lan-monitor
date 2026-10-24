package com.dimkabuki.lanmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerRetentionPolicyTest {
    @Test
    fun controllerIsRetainedWhileUiIsAttached() {
        assertFalse(
            SessionControllerRetentionPolicy.shouldDispose(
                uiAttachmentCount = 1,
                serviceAttachmentCount = 0,
                hasSessionWork = false,
            ),
        )
    }

    @Test
    fun controllerIsRetainedWhileServiceOwnsIt() {
        assertFalse(
            SessionControllerRetentionPolicy.shouldDispose(
                uiAttachmentCount = 0,
                serviceAttachmentCount = 1,
                hasSessionWork = false,
            ),
        )
    }

    @Test
    fun controllerRemainsRetainedWhileSessionWorkIsRunning() {
        assertFalse(
            SessionControllerRetentionPolicy.shouldDispose(
                uiAttachmentCount = 0,
                serviceAttachmentCount = 0,
                hasSessionWork = true,
            ),
        )
    }

    @Test
    fun idleControllerIsDisposedOnlyWhenUiAndServiceAreDetached() {
        assertTrue(
            SessionControllerRetentionPolicy.shouldDispose(
                uiAttachmentCount = 0,
                serviceAttachmentCount = 0,
                hasSessionWork = false,
            ),
        )
    }
}
