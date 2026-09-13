package com.aqua.aqualight.data.devices.light.control

import com.aqua.aqualight.application.devices.light.control.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.control.DeviceLightOutputCondition
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightControlSnapshotProjectionTest {

    @Test
    fun `authoritative status projects live hero values without firmware models`() {
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
            .copy(mode = DeviceLightMode.AUTO)

        val snapshot = status
            .toControlSnapshot(DeviceUid("light-pro"))

        assertEquals(DeviceLightControlMode.AUTOMATIC, snapshot.hero.mode)
        assertEquals(true, snapshot.hero.outputActive)
        assertEquals(DeviceLightOutputCondition.ACTIVE, snapshot.hero.outputCondition)
        assertEquals(true, snapshot.hero.outputHealthy)
        assertEquals(76.0, snapshot.hero.estimatedPowerWatts ?: Double.NaN, 0.0)
        assertEquals(5000, snapshot.hero.estimatedColorTemperatureKelvin)
        assertTrue(snapshot.channelKeys.containsAll(listOf("red", "green", "blue", "white")))
    }
}
