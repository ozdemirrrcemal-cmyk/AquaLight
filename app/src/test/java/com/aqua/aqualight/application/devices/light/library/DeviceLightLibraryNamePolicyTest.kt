package com.aqua.aqualight.application.devices.light.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightLibraryNamePolicyTest {

    @Test
    fun `name is unicode normalized trimmed and whitespace collapsed`() {
        val result = DeviceLightLibraryNamePolicy.validate("  Evening\t  View  ")

        assertTrue(result is DeviceLightLibraryNamePolicy.Validation.Valid)
        val name = (result as DeviceLightLibraryNamePolicy.Validation.Valid).name
        assertEquals("Evening View", name.display)
        assertEquals("evening view", name.normalized)
    }

    @Test
    fun `blank and over forty characters are rejected`() {
        assertEquals(
            DeviceLightLibraryNamePolicy.InvalidReason.BLANK,
            (DeviceLightLibraryNamePolicy.validate("   ") as
                DeviceLightLibraryNamePolicy.Validation.Invalid).reason
        )
        assertEquals(
            DeviceLightLibraryNamePolicy.InvalidReason.TOO_LONG,
            (DeviceLightLibraryNamePolicy.validate("x".repeat(41)) as
                DeviceLightLibraryNamePolicy.Validation.Invalid).reason
        )
    }
}
