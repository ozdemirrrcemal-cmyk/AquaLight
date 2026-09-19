package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ManualInactiveModeNoticeTest {

    @Test
    fun `automatic mode uses automatic output-context copy`() {
        assertEquals(
            R.string.device_light_manual_inactive_automatic_message,
            DeviceLightManualMode.AUTOMATIC.inactiveOutputMessageRes()
        )
    }

    @Test
    fun `custom mode uses custom output-context copy`() {
        assertEquals(
            R.string.device_light_manual_inactive_custom_message,
            DeviceLightManualMode.CUSTOM.inactiveOutputMessageRes()
        )
    }

    @Test
    fun `manual mode cannot create inactive notice copy`() {
        assertThrows(IllegalStateException::class.java) {
            DeviceLightManualMode.MANUAL.inactiveOutputMessageRes()
        }
    }
}
