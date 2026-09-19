package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanPointSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanWindowSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightDeviceCardModelsTest {

    @Test
    fun `card projects only central Light snapshot data`() {
        val card = compactCard().toLightSpotlightCardUi(
            DeviceLightCardState.Ready(snapshot())
        )

        assertEquals(LightDeviceSpotlightContentState.READY, card.contentState)
        assertEquals(8 * HOUR_MS, card.sunriseTimeMs)
        assertEquals(19 * HOUR_MS + 30 * MINUTE_MS, card.sunsetTimeMs)
        assertEquals(4, card.snapshot?.channels?.size)
        assertEquals(72, card.snapshot?.channels?.first()?.effectivePercent)
        assertTrue(card.snapshot?.hero?.outputActive == true)
    }

    private fun compactCard() = DeviceCompactCardUi(
        deviceUid = DEVICE_UID,
        displayName = "WRGB Pro Elite 120",
        serialText = DEVICE_UID,
        iconRes = R.drawable.ic_device_light,
        statusStyle = DeviceConnectionVisualState.ONLINE
    )

    private fun snapshot() = DeviceLightControlSnapshot(
        deviceUid = DEVICE_UID,
        productKey = "LIGHT_WRGB_PRO_ELITE",
        physicalChannelCount = 4,
        channelKeys = listOf("red", "green", "blue", "white"),
        channels = listOf(
            DeviceLightChannelOutputSnapshot("red", "Red", 0xFF3B30, 72),
            DeviceLightChannelOutputSnapshot("green", "Green", 0x34C759, 64),
            DeviceLightChannelOutputSnapshot("blue", "Blue", 0x2196F3, 81),
            DeviceLightChannelOutputSnapshot("white", "White", 0xFFFFFF, 45)
        ),
        plan = DeviceLightPlanSnapshot(
            available = true,
            reason = DeviceLightPlanReason.OK,
            nowTimeMs = 12 * HOUR_MS,
            channelScale = 1000,
            hasScheduleToday = true,
            activeWindow = DeviceLightPlanWindowSnapshot(
                startTimeMs = 8 * HOUR_MS,
                endTimeMs = 19 * HOUR_MS + 30 * MINUTE_MS
            ),
            points = listOf(
                point(0, 0),
                point(8 * HOUR_MS, 0),
                point(9 * HOUR_MS, 1000),
                point(18 * HOUR_MS, 1000),
                point(19 * HOUR_MS + 30 * MINUTE_MS, 0),
                point(24 * HOUR_MS, 0)
            )
        ),
        automaticProgramCount = 1,
        customCurvePointCount = 0,
        hero = DeviceLightHeroSnapshot(
            mode = DeviceLightControlMode.AUTOMATIC,
            outputActive = true
        )
    )

    private fun point(timeMs: Long, value: Int) = DeviceLightPlanPointSnapshot(
        timeMs = timeMs,
        channelLevels = listOf(value, value, value, value)
    )

    private companion object {
        const val DEVICE_UID = "light-card-test"
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
    }
}
