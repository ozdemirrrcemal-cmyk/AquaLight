package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCardMapperTest {

    @Test
    fun `unassigned device has no third line`() {
        val card = DeviceCardMapper.map(device())

        assertEquals("", card.card.supportingText)
    }

    @Test
    fun `assigned device third line is only trimmed tank name`() {
        val card = DeviceCardMapper.map(
            device = device(assignedTankName = "  Tank 1  ")
        )

        assertEquals("Tank 1", card.card.supportingText)
    }

    @Test
    fun `application item preserves card identity and online presentation`() {
        val card = DeviceCardMapper.map(device())

        assertEquals("AQL-WPE-336172", card.deviceUid)
        assertEquals("WRGB Pro Elite 120", card.card.displayName)
        assertEquals("AQL-WPE-336172", card.card.serialText)
        assertEquals(DeviceConnectionVisualState.ONLINE, card.card.statusStyle)
    }

    private fun device(
        assignedTankName: String = ""
    ) = OwnerDeviceListItem(
        deviceUid = "AQL-WPE-336172",
        displayName = "WRGB Pro Elite 120",
        serialText = "AQL-WPE-336172",
        family = OwnerDeviceFamily.LIGHT,
        availability = OwnerDeviceAvailability.REACHABLE,
        assignedTankName = assignedTankName
    )
}
