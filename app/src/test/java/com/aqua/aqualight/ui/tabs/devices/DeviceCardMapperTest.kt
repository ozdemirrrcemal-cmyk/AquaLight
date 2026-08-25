package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactVisualKind
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
        assertEquals(DeviceCompactStatusStyle.ONLINE, card.card.statusStyle)
        assertEquals(DeviceCompactVisualKind.ICON, card.card.visualKind)
    }

    @Test
    fun `dosing family uses shared product identity visual`() {
        val card = DeviceCardMapper.map(
            device = device(family = OwnerDeviceFamily.DOSING)
        )

        assertEquals(DeviceCompactVisualKind.DOSING_IDENTITY, card.card.visualKind)
    }

    private fun device(
        assignedTankName: String = "",
        family: OwnerDeviceFamily = OwnerDeviceFamily.LIGHT
    ) = OwnerDeviceListItem(
        deviceUid = "AQL-WPE-336172",
        displayName = "WRGB Pro Elite 120",
        serialText = "AQL-WPE-336172",
        family = family,
        availability = OwnerDeviceAvailability.REACHABLE,
        assignedTankName = assignedTankName
    )
}
