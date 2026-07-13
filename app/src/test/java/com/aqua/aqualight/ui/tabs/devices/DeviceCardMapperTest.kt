package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCardMapperTest {

    @Test
    fun `unassigned device has no third line`() {
        val card = DeviceCardMapper.map(snapshot())

        assertEquals("", card.card.supportingText)
    }

    @Test
    fun `assigned device third line is only trimmed tank name`() {
        val card = DeviceCardMapper.map(
            snapshot = snapshot(),
            assignedTankName = "  Tank 1  "
        )

        assertEquals("Tank 1", card.card.supportingText)
    }

    private fun snapshot() = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("AQL-WPE-336172"),
            serialNumber = "AQL-WPE-336172"
        ),
        product = DeviceProduct(
            family = DeviceFamily.LIGHT,
            displayName = "WRGB Pro Elite 120"
        )
    )
}
