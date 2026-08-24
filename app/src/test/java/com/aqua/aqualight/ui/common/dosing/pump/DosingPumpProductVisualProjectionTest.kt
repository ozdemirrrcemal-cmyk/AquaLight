package com.aqua.aqualight.ui.common.dosing.pump

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.ui.tabs.devices.DeviceCardMapper
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusSnapshotMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingPumpProductVisualProjectionTest {

    @Test
    fun `main devices card preserves catalog dose pro 2 identity`() {
        val ui = DeviceCardMapper.map(
            OwnerDeviceListItem(
                deviceUid = "dose-2",
                displayName = "Dose Pro 2",
                serialText = "dose-2",
                family = OwnerDeviceFamily.DOSING,
                availability = OwnerDeviceAvailability.REACHABLE,
                dosingChannelCount = 2
            )
        )

        assertTrue(ui.card.isDosingProduct)
        assertEquals(2, ui.card.dosingChannelCount)
    }

    @Test
    fun `settings devices card preserves catalog dose pro 4 identity`() {
        val item = DeviceStatusSnapshotMapper.items(
            statuses = listOf(
                OwnerDeviceStatusSnapshot(
                    deviceUid = "dose-4",
                    displayName = "Dose Pro 4",
                    serialText = "dose-4",
                    family = OwnerDeviceFamily.DOSING,
                    availability = OwnerDeviceAvailability.REACHABLE,
                    dosingChannelCount = 4
                )
            ),
            nowMillis = 0L
        ).single()

        assertTrue(item.isDosingProduct)
        assertEquals(4, item.dosingChannelCount)
    }

    @Test
    fun `shared visual accepts only commercial dose pro pump counts`() {
        assertTrue(isSupportedDosingPumpCount(2))
        assertTrue(isSupportedDosingPumpCount(4))
        assertFalse(isSupportedDosingPumpCount(0))
        assertFalse(isSupportedDosingPumpCount(3))
    }
}
