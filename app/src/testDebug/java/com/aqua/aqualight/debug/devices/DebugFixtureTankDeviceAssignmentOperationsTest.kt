package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureTankDeviceAssignmentOperationsTest {

    @Test
    fun fixturesAreVisibleAssignableAndRemovableWithoutEnteringProductionDelegate() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val runtime = DebugFixtureTankAssignmentRuntime()
        val delegate = RecordingAssignmentOperations()
        val operations = DebugFixtureTankDeviceAssignmentOperations(
            delegate = delegate,
            fixtures = fixtures,
            runtime = runtime,
            tankExists = { tankId -> tankId in setOf(FIRST_TANK_ID, SECOND_TANK_ID) }
        )
        val fixtureUid = fixtures.snapshots
            .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
            .deviceUid
            .value

        val initial = operations.availableDevices(FIRST_TANK_ID).first()
        assertTrue(initial.hasRegisteredDevices)
        assertTrue(initial.devices.any { item -> item.deviceUid == fixtureUid })
        assertTrue(
            initial.devices
                .first { item -> item.deviceUid == fixtureUid }
                .displayName
                .endsWith("[TEST]")
        )

        assertEquals(
            AssignDeviceToTankResult.Assigned,
            operations.assignDevice(FIRST_TANK_ID, fixtureUid)
        )
        assertTrue(
            operations.assignedDevices(FIRST_TANK_ID).first()
                .any { item -> item.deviceUid == fixtureUid }
        )
        assertFalse(
            operations.availableDevices(FIRST_TANK_ID).first()
                .any { item -> item.deviceUid == fixtureUid }
        )
        assertEquals(
            AssignDeviceToTankResult.Conflict(FIRST_TANK_ID),
            operations.assignDevice(SECOND_TANK_ID, fixtureUid)
        )
        assertEquals(
            RemoveDeviceFromTankResult.REMOVED,
            operations.removeDevice(FIRST_TANK_ID, fixtureUid)
        )
        assertTrue(
            operations.availableDevices(FIRST_TANK_ID).first()
                .any { item -> item.deviceUid == fixtureUid }
        )
        assertEquals(0, delegate.fixtureMutationCalls)
    }

    @Test
    fun fixtureAssignmentRejectsUnknownTank() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val operations = DebugFixtureTankDeviceAssignmentOperations(
            delegate = RecordingAssignmentOperations(),
            fixtures = fixtures,
            runtime = DebugFixtureTankAssignmentRuntime(),
            tankExists = { false }
        )
        val fixtureUid = fixtures.snapshots.first().deviceUid.value

        assertEquals(
            AssignDeviceToTankResult.TankNotFound,
            operations.assignDevice(FIRST_TANK_ID, fixtureUid)
        )
    }

    private companion object {
        const val FIRST_TANK_ID = 101L
        const val SECOND_TANK_ID = 202L
    }
}

private class RecordingAssignmentOperations : TankDeviceAssignmentOperations {
    var fixtureMutationCalls: Int = 0
        private set

    override fun start(scope: CoroutineScope): Job = Job().apply { complete() }

    override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> =
        flowOf(emptyList())

    override fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot> =
        flowOf(AvailableTankDevicesSnapshot(devices = emptyList(), hasRegisteredDevices = false))

    override suspend fun assignDevice(
        tankId: Long,
        deviceUid: String
    ): AssignDeviceToTankResult {
        if (deviceUid.startsWith("DEBUG-FIXTURE-")) fixtureMutationCalls += 1
        return AssignDeviceToTankResult.DeviceNotFound
    }

    override suspend fun removeDevice(
        tankId: Long,
        deviceUid: String
    ): RemoveDeviceFromTankResult {
        if (deviceUid.startsWith("DEBUG-FIXTURE-")) fixtureMutationCalls += 1
        return RemoveDeviceFromTankResult.NOT_ASSIGNED
    }
}
