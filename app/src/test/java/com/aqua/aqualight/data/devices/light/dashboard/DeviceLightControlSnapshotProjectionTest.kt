package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemTemperaturePolicy
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomPoint
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMutationParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
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
        val snapshot = status.toControlSnapshot(
            deviceUid = DeviceUid("light-pro"),
            graph = graph,
            customDocument = null,
            systemSupported = true,
            systemSnapshot = systemSnapshot()
        )

        assertEquals(DeviceLightControlMode.AUTOMATIC, snapshot.hero.mode)
        assertEquals("program-1", snapshot.activeAutomaticProgramId)
        assertEquals(true, snapshot.hero.outputActive)
        assertEquals(DeviceLightOutputCondition.ACTIVE, snapshot.hero.outputCondition)
        assertEquals(true, snapshot.hero.outputHealthy)
        assertEquals(62.0, snapshot.hero.estimatedPowerWatts ?: Double.NaN, 0.0)
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
        assertTrue(snapshot.systemSupported)
        assertEquals(42.8, snapshot.system?.temperatureCelsius ?: Double.NaN, 0.0)
        assertEquals(listOf(35, 40), snapshot.system?.fanPercents)
        assertEquals(DeviceLightSystemCondition.NORMAL, snapshot.system?.condition)
    }

    @Test
    fun `custom graph points project to the card schedule window`() {
        val parsed = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val status = parsed.copy(mode = DeviceLightMode.CUSTOM)
        val graph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(mode = DeviceLightMode.CUSTOM),
            status.product
        )

        val custom = DeviceLightCustomDocument(
            revision = 3L,
            installed = true,
            weekdaysMask = 127,
            pointCount = 2,
            points = listOf(
                DeviceLightCustomPoint(28_800_000L, DeviceLightScene.wrgb(20, 50, 60, 10)),
                DeviceLightCustomPoint(72_000_000L, DeviceLightScene.wrgb(40, 70, 80, 20))
            ),
            event = null
        )
        val snapshot = status.toControlSnapshot(
            deviceUid = DeviceUid("light-custom"),
            graph = graph,
            customDocument = custom,
            systemSupported = false,
            systemSnapshot = null
        )

        assertEquals(28_800_000L, snapshot.plan?.activeWindow?.startTimeMs)
        assertEquals(72_000_000L, snapshot.plan?.activeWindow?.endTimeMs)
    }

    @Test
    fun `rgb product does not expose unsupported system surface`() {
        val status = DeviceLightStatusParser.parse(
            DeviceLightRuntimeFixtures.status(DeviceLightProduct.RGB_PRO_SLIM)
        )
        val graph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(
                mode = status.mode,
                product = DeviceLightProduct.RGB_PRO_SLIM
            ),
            status.product
        )

        val snapshot = status.toControlSnapshot(
            deviceUid = DeviceUid("light-rgb"),
            graph = graph,
            systemSupported = false,
            systemSnapshot = null
        )

        assertEquals(false, snapshot.systemSupported)
        assertEquals(null, snapshot.system)
    }

    private fun systemSnapshot() = DeviceLightSystemSnapshot(
        deviceUid = "light-pro",
        temperatureCelsius = 42.8,
        condition = DeviceLightSystemCondition.NORMAL,
        sensorHealthy = true,
        fans = listOf(
            DeviceLightSystemFanSnapshot(key = "fan1", percent = 35, healthy = true),
            DeviceLightSystemFanSnapshot(key = "fan2", percent = 40, healthy = true)
        ),
        mode = DeviceLightFanMode.AUTOMATIC,
        startTemperatureCelsius = 35,
        fullSpeedTemperatureCelsius = 50,
        startTemperaturePolicy = DeviceLightSystemTemperaturePolicy(20, 45),
        fullSpeedTemperaturePolicy = DeviceLightSystemTemperaturePolicy(30, 60),
        protectionThresholdCelsius = 60,
        protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(50, 70),
        protectionActive = false,
        firmwareWriteAuthoritative = true
    )
}
