package com.dimkabuki.lanmonitor

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoleTest {
    @Test
    fun rolesExposeTheEnglishDisplayLabels() {
        assertEquals(listOf("Camera", "Monitor"), AppRole.values().map(AppRole::label))
    }
}
