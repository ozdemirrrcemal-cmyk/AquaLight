package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightGenerationAuthorityTest {
    @Test
    fun `reconnect retains last Light presentation snapshot until new generation hydrates`() {
        val owner = DeviceLightRuntimeStateOwner()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(mode = DeviceLightMode.AUTO)

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, first))
        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)

        assertEquals(first, owner.statuses.value[DEVICE_UID])
        assertEquals(
            first,
            owner.currentStatus(DEVICE_UID, DeviceLightStatusReadAuthority.PRESENTATION)
        )
        assertNull(
            owner.currentStatus(DEVICE_UID, DeviceLightStatusReadAuthority.AUTHORITATIVE)
        )
        assertFalse(
            owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, DEVICE_UID, G2)
        )
        assertTrue(owner.recordStatus(DEVICE_UID, G2, second))
        assertEquals(second, owner.statuses.value[DEVICE_UID])
        assertEquals(
            second,
            owner.currentStatus(DEVICE_UID, DeviceLightStatusReadAuthority.AUTHORITATIVE)
        )
    }

    @Test
    fun `late old generation Light status cannot overwrite new state`() {
        val owner = DeviceLightRuntimeStateOwner()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(mode = DeviceLightMode.AUTO)
        val lateOld = first.copy(mode = DeviceLightMode.CUSTOM)

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, first)
        owner.beginGeneration(DEVICE_UID, G2)
        owner.recordStatus(DEVICE_UID, G2, second)

        assertFalse(owner.recordStatus(DEVICE_UID, G1, lateOld))
        assertEquals(second, owner.statuses.value[DEVICE_UID])
        assertTrue(
            owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, DEVICE_UID, G2)
        )
    }

    @Test
    fun `custom document must rehydrate after reconnect before it is readable`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val document = status.emptyCustomDocument()

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, status))
        assertTrue(owner.customProjection.record(DEVICE_UID, G1, document))
        assertEquals(document, owner.customProjection.currentAuthoritative(DEVICE_UID))

        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        assertNull(owner.customProjection.currentAuthoritative(DEVICE_UID))

        assertTrue(owner.recordStatus(DEVICE_UID, G2, status))
        assertNull(owner.customProjection.currentAuthoritative(DEVICE_UID))
        assertTrue(owner.customProjection.record(DEVICE_UID, G2, document))
        assertEquals(document, owner.customProjection.currentAuthoritative(DEVICE_UID))
    }

    @Test
    fun `status revision change invalidates the central custom projection`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val document = status.emptyCustomDocument()
        val advancedStatus = status.copy(
            custom = status.custom.copy(revision = status.custom.revision + 1L)
        )

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, status)
        owner.customProjection.record(DEVICE_UID, G1, document)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, advancedStatus))
        assertNull(owner.customProjection.currentAuthoritative(DEVICE_UID))
        assertFalse(owner.customProjection.record(DEVICE_UID, G1, document))
    }

    @Test
    fun `reconnect keeps the last dashboard frame while current graph rehydrates`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val graph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(),
            status.product
        )

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, status))
        assertTrue(owner.dashboardProjection.record(DEVICE_UID, G1, graph))
        assertEquals(graph, owner.authoritativeDashboard()?.graph)

        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        assertNull(owner.authoritativeDashboard())
        assertEquals(
            DeviceLightDashboardRuntimeState(status, graph),
            owner.presentationDashboard()
        )
        assertTrue(owner.recordStatus(DEVICE_UID, G2, status))
        assertNull(owner.authoritativeDashboard())
        assertEquals(
            DeviceLightDashboardRuntimeState(status, graph),
            owner.presentationDashboard()
        )
        assertTrue(owner.dashboardProjection.record(DEVICE_UID, G2, graph))
        assertEquals(graph, owner.authoritativeDashboard()?.graph)
        assertEquals(
            DeviceLightDashboardRuntimeState(status, graph),
            owner.authoritativeDashboard()
        )
    }

    @Test
    fun `status mode change invalidates an obsolete graph`() {
        val owner = DeviceLightRuntimeStateOwner()
        val manualStatus = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val manualGraph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(),
            manualStatus.product
        )
        val automaticStatus = manualStatus.copy(mode = DeviceLightMode.AUTO)

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, manualStatus)
        owner.dashboardProjection.record(DEVICE_UID, G1, manualGraph)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, automaticStatus))
        assertNull(owner.authoritativeDashboard())
        assertEquals(
            DeviceLightDashboardRuntimeState(manualStatus, manualGraph),
            owner.presentationDashboard()
        )
        assertFalse(owner.dashboardProjection.record(DEVICE_UID, G1, manualGraph))
        assertEquals(
            DeviceLightDashboardRuntimeState(manualStatus, manualGraph),
            owner.presentationDashboard()
        )
    }

    @Test
    fun `status refresh retains the old frame until fresh graph commits the new pair`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val refreshedStatus = status.copy(outputActive = !status.outputActive)
        val graph = DeviceLightMutationParser.Graph.parseGraph(
            DeviceLightRuntimeFixtures.graph(),
            status.product
        )

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, status)
        owner.dashboardProjection.record(DEVICE_UID, G1, graph)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, refreshedStatus))
        assertNull(owner.authoritativeDashboard())
        assertEquals(
            DeviceLightDashboardRuntimeState(status, graph),
            owner.presentationDashboard()
        )
        assertTrue(owner.dashboardProjection.record(DEVICE_UID, G1, graph))
        assertEquals(graph, owner.authoritativeDashboard()?.graph)
        assertEquals(
            DeviceLightDashboardRuntimeState(refreshedStatus, graph),
            owner.presentationDashboard()
        )
    }

    @Test
    fun `library retains its last complete frame until reconnect hydrates custom`() {
        val owner = DeviceLightRuntimeStateOwner()
        val firstStatus = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val firstDocument = firstStatus.emptyCustomDocument()
        val refreshedStatus = firstStatus.copy(outputActive = !firstStatus.outputActive)

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, firstStatus))
        assertTrue(owner.customProjection.record(DEVICE_UID, G1, firstDocument))
        assertEquals(
            DeviceLightLibraryRuntimeState(firstStatus, firstDocument),
            owner.presentationLibrary()
        )

        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        assertTrue(owner.recordStatus(DEVICE_UID, G2, refreshedStatus))

        assertNull(owner.authoritativeLibrary())
        assertEquals(
            DeviceLightLibraryRuntimeState(firstStatus, firstDocument),
            owner.presentationLibrary()
        )

        assertTrue(owner.customProjection.record(DEVICE_UID, G2, firstDocument))
        assertEquals(
            DeviceLightLibraryRuntimeState(refreshedStatus, firstDocument),
            owner.authoritativeLibrary()
        )
    }

    @Test
    fun `library custom revision change publishes status and document atomically`() {
        val owner = DeviceLightRuntimeStateOwner()
        val firstStatus = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val firstDocument = firstStatus.emptyCustomDocument()
        val nextStatus = firstStatus.copy(
            custom = firstStatus.custom.copy(revision = firstStatus.custom.revision + 1L)
        )
        val nextDocument = nextStatus.emptyCustomDocument()

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, firstStatus)
        owner.customProjection.record(DEVICE_UID, G1, firstDocument)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, nextStatus))
        assertNull(owner.authoritativeLibrary())
        assertEquals(
            DeviceLightLibraryRuntimeState(firstStatus, firstDocument),
            owner.presentationLibrary()
        )

        assertTrue(owner.customProjection.record(DEVICE_UID, G1, nextDocument))
        assertEquals(
            DeviceLightLibraryRuntimeState(nextStatus, nextDocument),
            owner.presentationLibrary()
        )
    }

    @Test
    fun `library reuses an authoritative coherent custom document for a status refresh`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val document = status.emptyCustomDocument()
        val refreshedStatus = status.copy(outputActive = !status.outputActive)

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, status)
        owner.customProjection.record(DEVICE_UID, G1, document)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, refreshedStatus))
        assertEquals(
            DeviceLightLibraryRuntimeState(refreshedStatus, document),
            owner.authoritativeLibrary()
        )
    }

    @Test
    fun `late old generation custom cannot replace the current library frame`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val document = status.emptyCustomDocument()

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, status)
        owner.customProjection.record(DEVICE_UID, G1, document)
        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        owner.recordStatus(DEVICE_UID, G2, status)
        owner.customProjection.record(DEVICE_UID, G2, document)

        assertFalse(
            owner.customProjection.record(
                DEVICE_UID,
                G1,
                document.copy(revision = document.revision + 1L)
            )
        )
        assertEquals(DeviceLightLibraryRuntimeState(status, document), owner.presentationLibrary())
    }

    @Test
    fun `automatic retains one complete frame while a reconnect rehydrates programs`() {
        val owner = DeviceLightRuntimeStateOwner()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val programs = status.emptyAutoPrograms()
        val refreshedStatus = status.copy(outputActive = !status.outputActive)

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, status))
        assertTrue(owner.automaticProjection.record(DEVICE_UID, G1, programs))
        assertEquals(
            DeviceLightAutomaticRuntimeState(status, programs),
            owner.presentationAutomatic()
        )

        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        assertTrue(owner.recordStatus(DEVICE_UID, G2, refreshedStatus))

        assertNull(owner.authoritativeAutomatic())
        assertEquals(
            DeviceLightAutomaticRuntimeState(status, programs),
            owner.presentationAutomatic()
        )

        assertTrue(owner.automaticProjection.record(DEVICE_UID, G2, programs))
        assertEquals(
            DeviceLightAutomaticRuntimeState(refreshedStatus, programs),
            owner.authoritativeAutomatic()
        )
    }

    @Test
    fun `automatic summary revision change cannot publish a mixed frame`() {
        val owner = DeviceLightRuntimeStateOwner()
        val firstStatus = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val firstPrograms = firstStatus.emptyAutoPrograms()
        val nextStatus = firstStatus.copy(
            auto = firstStatus.auto.copy(revision = firstStatus.auto.revision + 1L)
        )

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, firstStatus)
        owner.automaticProjection.record(DEVICE_UID, G1, firstPrograms)

        assertTrue(owner.recordStatus(DEVICE_UID, G1, nextStatus))
        assertNull(owner.authoritativeAutomatic())
        assertEquals(
            DeviceLightAutomaticRuntimeState(firstStatus, firstPrograms),
            owner.presentationAutomatic()
        )
        assertFalse(owner.automaticProjection.record(DEVICE_UID, G1, firstPrograms))
    }

    @Test
    fun `system retains its complete frame until both owning domains rehydrate`() {
        val owner = DeviceLightRuntimeStateOwner()
        val firstThermal = thermalStatus(uptimeMs = 100L, minimumC = 30.0)
        val firstProtection = protectionStatus(thresholdC = 60.0)
        val nextThermal = thermalStatus(uptimeMs = 200L, minimumC = 32.0)
        val nextProtection = protectionStatus(thresholdC = 62.0)

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordThermalStatus(DEVICE_UID, G1, firstThermal))
        assertNull(owner.presentationSystem())
        assertTrue(owner.recordTemperatureProtection(DEVICE_UID, G1, firstProtection))
        val firstFrame = DeviceLightSystemRuntimeState(
            DeviceLightThermalRuntimeState(status = firstThermal),
            firstProtection
        )
        assertEquals(firstFrame, owner.authoritativeSystem())

        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)
        assertTrue(owner.recordThermalStatus(DEVICE_UID, G2, nextThermal))
        assertNull(owner.authoritativeSystem())
        assertEquals(firstFrame, owner.presentationSystem())

        assertTrue(owner.recordTemperatureProtection(DEVICE_UID, G2, nextProtection))
        assertEquals(
            DeviceLightSystemRuntimeState(
                DeviceLightThermalRuntimeState(status = nextThermal),
                nextProtection
            ),
            owner.authoritativeSystem()
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}

private fun DeviceLightStatus.emptyCustomDocument() = DeviceLightCustomDocument(
    revision = custom.revision,
    installed = custom.installed,
    weekdaysMask = custom.weekdaysMask,
    pointCount = custom.pointCount,
    points = emptyList(),
    event = null
)

private fun DeviceLightStatus.emptyAutoPrograms() = DeviceLightAutoPrograms(
    revision = auto.revision,
    capacity = policy.auto.capacity,
    programCount = auto.programCount,
    enabledCount = auto.enabledCount,
    programs = emptyList()
)

private fun DeviceLightRuntimeStateOwner.authoritativeDashboard() = dashboardProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightDashboardReadAuthority.AUTHORITATIVE
)

private fun DeviceLightRuntimeStateOwner.presentationDashboard() = dashboardProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightDashboardReadAuthority.PRESENTATION
)

private fun DeviceLightRuntimeStateOwner.authoritativeLibrary() = libraryProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightLibraryReadAuthority.AUTHORITATIVE
)

private fun DeviceLightRuntimeStateOwner.presentationLibrary() = libraryProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightLibraryReadAuthority.PRESENTATION
)

private fun DeviceLightRuntimeStateOwner.authoritativeAutomatic() = automaticProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightAutomaticReadAuthority.AUTHORITATIVE
)

private fun DeviceLightRuntimeStateOwner.presentationAutomatic() = automaticProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightAutomaticReadAuthority.PRESENTATION
)

private fun DeviceLightRuntimeStateOwner.authoritativeSystem() = systemProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightSystemReadAuthority.AUTHORITATIVE
)

private fun DeviceLightRuntimeStateOwner.presentationSystem() = systemProjection.current(
    DeviceUid("AQL-LIGHT-GENERATION"),
    DeviceLightSystemReadAuthority.PRESENTATION
)

private fun thermalStatus(
    uptimeMs: Long,
    minimumC: Double
) = DeviceLightThermalStatus(
    schema = DeviceLightThermalV1Contract.SCHEMA,
    schemaVersion = DeviceLightThermalV1Contract.SCHEMA_VERSION,
    productKey = DeviceLightThermalV1Contract.PRODUCT_KEY,
    uptimeMs = uptimeMs,
    topology = DeviceLightThermalTopology(fanOutputCount = 2, temperatureSensorCount = 1),
    config = DeviceLightThermalConfig(
        mode = DeviceLightThermalMode.AUTO,
        minTemperatureC = minimumC,
        maxTemperatureC = 50.0
    ),
    temperature = DeviceLightThermalTemperature(
        sensorKey = DeviceLightThermalV1Contract.FIXTURE_SENSOR_KEY,
        sensorIndex = 0,
        readingValid = true,
        temperatureC = 42.0,
        sampledAtMs = uptimeMs
    ),
    lightProtection = DeviceLightThermalProtection(
        enabled = true,
        active = false,
        thresholdC = 60.0
    ),
    fans = listOf(thermalFan("fan1", 0), thermalFan("fan2", 1)),
    runtime = DeviceLightThermalRuntime(
        event = DeviceLightThermalV1Contract.Event.STATUS_CHANGED,
        statusEvent = DeviceLightThermalV1Contract.Event.STATUS_CHANGED,
        sensorFailSafeActive = false,
        automaticOutputCycleHealthy = true,
        hardwareEditable = false,
        fanMappingEditable = false,
        sensorMappingEditable = false
    )
)

private fun thermalFan(key: String, index: Int) = DeviceLightThermalFan(
    fanKey = key,
    index = index,
    name = key,
    regime = "AUTO",
    valueNow = 0.35,
    valueAuto = 0.35,
    percentNow = 35.0,
    percentAuto = 35.0,
    hardware = DeviceLightThermalFanHardware(
        editable = false,
        gpio = null,
        ledcChannel = null,
        pwmFrequencyHz = null,
        pwmResolutionBits = null,
        invert = null,
        pwmOutputHealth = "OK",
        health = "OK",
        physicalFeedbackAvailable = false
    )
)

private fun protectionStatus(thresholdC: Double) = DeviceLightTemperatureProtectionStatus(
    supported = true,
    temperatureProtection = DeviceLightTemperatureProtectionSnapshot(
        supported = true,
        active = false,
        thresholdEditable = true,
        thresholdC = thresholdC,
        minimumC = 50.0,
        maximumC = 70.0
    ),
    runtime = DeviceLightTemperatureProtectionRuntimeCapabilities(
        module = DeviceLightRuntimeContract.MODULE,
        readOnly = false,
        supportsStatusGet = true,
        supportsSet = true,
        event = DeviceLightThermalV1Contract.Event.STATUS_CHANGED
    )
)
