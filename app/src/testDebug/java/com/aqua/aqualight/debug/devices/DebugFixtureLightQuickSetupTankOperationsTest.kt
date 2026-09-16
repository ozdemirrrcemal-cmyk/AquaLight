package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureLightQuickSetupTankOperationsTest {

    @Test
    fun assignedLightFixtureResolvesRealTankDataWithoutProductionDeviceLookup() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixture = fixtures.snapshots
            .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
        val assignments = DebugFixtureTankAssignmentRuntime()
        val operations = DebugFixtureLightQuickSetupTankOperations(
            delegate = FailingQuickSetupTankOperations,
            fixtures = fixtures,
            assignments = assignments,
            tankById = { tankId -> testTank().takeIf { tank -> tank.id == tankId } }
        )

        val unassigned = operations.readForDevice(fixture.deviceUid.value)
        assertEquals(
            DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED,
            (unassigned as DeviceLightQuickSetupTankReadResult.Failed).failure
        )

        assignments.assign(fixture.deviceUid.value, TANK_ID)
        val result = operations.readForDevice(fixture.deviceUid.value)

        assertTrue(result is DeviceLightQuickSetupTankReadResult.Available)
        val tank = (result as DeviceLightQuickSetupTankReadResult.Available).tank
        assertEquals(TANK_ID, tank.tankId)
        assertEquals(TANK_NAME, tank.tankName)
        assertEquals(fixture.product.productKey, tank.productKey)
        assertEquals(fixture.product.displayName, tank.productDisplayName)
    }

    private companion object {
        const val TANK_ID = 909L
        const val TANK_NAME = "Debug planted tank"
    }
}

private object FailingQuickSetupTankOperations : DeviceLightQuickSetupTankOperations {
    override suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult =
        error("Fixture quick setup must not call production lookup: $deviceUid")
}

private fun testTank(): SavedAquariumTank = SavedAquariumTank(
    id = 909L,
    ownerUid = "debug-owner",
    name = "Debug planted tank",
    description = "",
    photoUri = null,
    setupDateEpochDay = 20_000L,
    widthCm = 60,
    lengthCm = 35,
    heightCm = 40,
    volumeUnit = "L",
    tankType = "Freshwater",
    tankStyle = "Planted",
    createdAtMillis = 1L,
    plants = listOf(
        SavedAquariumPlant(
            id = 1L,
            plantName = "Anubias",
            category = "Epiphyte",
            markerX = 0.5f,
            markerY = 0.5f
        )
    ),
    materials = emptyList()
)
