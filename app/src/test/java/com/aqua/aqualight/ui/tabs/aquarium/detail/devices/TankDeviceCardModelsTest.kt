package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardChannelSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TankDeviceCardModelsTest {

    @Test
    fun `dose pro 2 keeps two page spotlight when catalog and runtime agree`() {
        val ui = compactCard(dosingChannelCount = 2).toDosingSpotlightCardUi(
            summary = summary(channelCount = 2),
            selectedIndex = 1
        )

        assertEquals(2, ui.header.dosingChannelCount)
        assertEquals(2, ui.pageCount)
        assertEquals(1, ui.selectedIndex)
        assertEquals(2, ui.selectedChannel?.channelNumber)
        assertNotNull(ui.summary)
    }

    @Test
    fun `catalog and runtime pump count mismatch fails closed`() {
        val ui = compactCard(dosingChannelCount = 2).toDosingSpotlightCardUi(
            summary = summary(channelCount = 4),
            selectedIndex = 0
        )

        assertEquals(2, ui.header.dosingChannelCount)
        assertNull(ui.summary)
        assertNull(ui.selectedChannel)
        assertEquals(0, ui.pageCount)
        assertEquals(0, ui.selectedIndex)
    }

    private fun compactCard(dosingChannelCount: Int) = DeviceCompactCardUi(
        deviceUid = DEVICE_UID,
        displayName = "Dose Pro $dosingChannelCount",
        serialText = DEVICE_UID,
        iconRes = 0,
        isDosingProduct = true,
        dosingChannelCount = dosingChannelCount,
        statusStyle = DeviceCompactStatusStyle.ONLINE
    )

    private fun summary(channelCount: Int): DeviceDosingCardSummary = DeviceDosingCardSummary(
        deviceUid = DEVICE_UID,
        channelCount = channelCount,
        activeChannelCount = channelCount,
        channels = (1..channelCount).map { channelNumber ->
            DeviceDosingCardChannelSummary(
                channelNumber = channelNumber,
                title = "Channel $channelNumber",
                runtimeEnabled = true,
                dailyDoseMicroliters = null,
                nextDose = null,
                reservoir = null
            )
        }
    )

    private companion object {
        const val DEVICE_UID = "dose-device"
    }
}
