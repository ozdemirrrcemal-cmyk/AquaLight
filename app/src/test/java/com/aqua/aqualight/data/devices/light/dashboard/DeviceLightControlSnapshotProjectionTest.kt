package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMutationParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightControlSnapshotProjectionTest {

    @Test
    fun `authoritative status projects live hero values without firmware models`() {
        val parsed = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val status = parsed.copy(
            mode = DeviceLightMode.AUTO,
            auto = parsed.auto.copy(activeProgramId = "program-1")
        )

        val graph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(mode = DeviceLightMode.AUTO),
            status.product
        )
        val snapshot = status.toControlSnapshot(DeviceUid("light-pro"), graph)

        assertEquals(DeviceLightControlMode.AUTOMATIC, snapshot.hero.mode)
        assertEquals("program-1", snapshot.activeAutomaticProgramId)
        assertEquals(true, snapshot.hero.outputActive)
        assertEquals(DeviceLightOutputCondition.ACTIVE, snapshot.hero.outputCondition)
        assertEquals(true, snapshot.hero.outputHealthy)
        assertEquals(76.0, snapshot.hero.estimatedPowerWatts ?: Double.NaN, 0.0)
        assertEquals(5000, snapshot.hero.estimatedColorTemperatureKelvin)
        assertTrue(snapshot.adaptation.supported)
        assertEquals(DeviceLightAdaptationState.DISABLED, snapshot.adaptation.state)
        assertEquals(1_000, snapshot.adaptation.currentPermille)
        assertEquals(0L, snapshot.adaptation.remainingSeconds)
        assertTrue(snapshot.channelKeys.containsAll(listOf("red", "green", "blue", "white")))
        assertEquals(listOf(20, 30, 40, 50), snapshot.channels.map { it.effectivePercent })
        assertEquals(listOf(0L, 43_200_000L, 86_400_000L), snapshot.plan?.points?.map { it.timeMs })
        assertEquals(listOf(750, 650, 550, 450), snapshot.plan?.points?.get(1)?.channelLevels)
        assertEquals(status.auto.programCount, snapshot.automaticProgramCount)
        assertEquals(status.custom.pointCount, snapshot.customCurvePointCount)
    }
}
