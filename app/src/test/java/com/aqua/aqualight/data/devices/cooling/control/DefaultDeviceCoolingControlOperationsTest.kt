package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultDeviceCoolingControlOperationsTest {

    @Test
    fun `catalog one runtime one maps the same authoritative fan everywhere`() {
        val base = DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())
        val fan = base.fans.single().copy(
            percentNow = 42.0,
            percentManual = 37.0,
            percentMin = 10.0,
            percentMax = 90.0
        )

        val result = base.copy(
            fanOutputCount = 1,
            fans = listOf(fan)
        ).toControlResult(expectedFanOutputCount = 1)

        val available = result as DeviceCoolingControlResult.Available
        assertEquals(42, available.snapshot.actualFanPercent)
        assertEquals(37, available.snapshot.manualFanPercent)
        val manualCapabilities = available.snapshot.capabilities.manualFan
        assertNotNull(manualCapabilities)
        assertEquals(10, manualCapabilities?.minimumPercent)
        assertEquals(90, manualCapabilities?.maximumPercent)
    }

    @Test
    fun `catalog one runtime zero is invalid data`() {
        val status = DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())
            .copy(
                fanOutputCount = 0,
                fans = emptyList()
            )

        assertEquals(
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData),
            status.toControlResult(expectedFanOutputCount = 1)
        )
    }

    @Test
    fun `catalog one runtime two is invalid data`() {
        val base = DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())
        val firstFan = base.fans.single()
        val secondFan = firstFan.copy(
            index = 1,
            key = "fan2",
            name = "Fan 2",
            displayName = "Fan 2"
        )
        val status = base.copy(
            fanOutputCount = 2,
            fans = listOf(firstFan, secondFan)
        )

        assertEquals(
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData),
            status.toControlResult(expectedFanOutputCount = 1)
        )
    }
}
