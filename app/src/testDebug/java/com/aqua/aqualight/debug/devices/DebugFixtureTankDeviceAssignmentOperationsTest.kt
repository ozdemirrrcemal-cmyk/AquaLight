package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureTankDeviceAssignmentOperationsTest {

    @Test
    fun fixturesCanBeAssignedAndRemovedWithoutEnteringTheRealDeviceBoundary() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixtureUid = fixtures.snapshots.first().deviceUid.value
        val delegate = RecordingTankAssignmentOperations()
        val tankIds = MutableStateFlow(setOf(TANK_ID, OTHER_TANK_ID))
        val operations = DebugFixtureTankDeviceAssignmentOperations(
            delegate = delegate,
            fixtures = fixtures,
            fixtureAssignments = DebugFixtureTankAssignments(),
            validTankIds = tankIds,
            validTankIdsSnapshot = { tankIds.value }
        )

        val initial = operations.availableDevices(TANK_ID).first()
        assertTrue(initial.hasRegisteredDevices)
        assertTrue(initial.devices.any { device -> device.deviceUid == fixtureUid })

        assertSame(
            AssignDeviceToTankResult.Assigned,
            operations.assignDevice(TANK_ID, fixtureUid)
        )
        assertTrue(
            operations.assignedDevices(TANK_ID).first()
                .any { device -> device.deviceUid == fixtureUid }
        )
        assertTrue(
            operations.availableDevices(TANK_ID).first().devices
                .none { device -> device.deviceUid == fixtureUid }
        )
        assertEquals(
            AssignDeviceToTankResult.Conflict(TANK_ID),
            operations.assignDevice(OTHER_TANK_ID, fixtureUid)
        )
        assertSame(
            RemoveDeviceFromTankResult.REMOVED,
            operations.removeDevice(TANK_ID, fixtureUid)
        )
        assertTrue(
            operations.availableDevices(TANK_ID).first().devices
                .any { device -> device.deviceUid == fixtureUid }
        )
        assertEquals(0, delegate.fixtureBoundaryCalls)
    }

    @Test
    fun realDevicesStillDelegateAndUnknownTankFailsClosedForFixtures() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixtureUid = fixtures.snapshots.first().deviceUid.value
        val delegate = RecordingTankAssignmentOperations()
        val tankIds = MutableStateFlow(setOf(TANK_ID))
        val operations = DebugFixtureTankDeviceAssignmentOperations(
            delegate = delegate,
            fixtures = fixtures,
            fixtureAssignments = DebugFixtureTankAssignments(),
            validTankIds = tankIds,
            validTankIdsSnapshot = { tankIds.value }
        )

        assertSame(
            AssignDeviceToTankResult.TankNotFound,
            operations.assignDevice(OTHER_TANK_ID, fixtureUid)
        )
        assertSame(
            delegate.realAssignResult,
            operations.assignDevice(TANK_ID, REAL_DEVICE_UID)
        )
        assertSame(
            delegate.realRemoveResult,
            operations.removeDevice(TANK_ID, REAL_DEVICE_UID)
        )
        assertEquals(2, delegate.realBoundaryCalls)
    }

    private class RecordingTankAssignmentOperations : TankDeviceAssignmentOperations {
        var fixtureBoundaryCalls = 0
        var realBoundaryCalls = 0
        val realAssignResult = AssignDeviceToTankResult.Conflict(OTHER_TANK_ID)
        val realRemoveResult = RemoveDeviceFromTankResult.NOT_ASSIGNED

        override fun start(scope: CoroutineScope): Job = Job()

        override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> =
            MutableStateFlow(emptyList())

        override fun availableDevices(
            tankId: Long
        ): Flow<AvailableTankDevicesSnapshot> = MutableStateFlow(
            AvailableTankDevicesSnapshot(
                devices = listOf(
                    TankDeviceListItem(
                        deviceUid = REAL_DEVICE_UID,
                        displayName = "Real device",
                        serialText = "REAL-001",
                        family = OwnerDeviceFamily.LIGHT,
                        availability = OwnerDeviceAvailability.REACHABLE
                    )
                ),
                hasRegisteredDevices = true
            )
        )

        override suspend fun assignDevice(
            tankId: Long,
            deviceUid: String
        ): AssignDeviceToTankResult {
            if (deviceUid.startsWith("DEBUG-FIXTURE-")) fixtureBoundaryCalls += 1
            else realBoundaryCalls += 1
            return realAssignResult
        }

        override suspend fun removeDevice(
            tankId: Long,
            deviceUid: String
        ): RemoveDeviceFromTankResult {
            if (deviceUid.startsWith("DEBUG-FIXTURE-")) fixtureBoundaryCalls += 1
            else realBoundaryCalls += 1
            return realRemoveResult
        }
    }

    private companion object {
        const val TANK_ID = 41L
        const val OTHER_TANK_ID = 42L
        const val REAL_DEVICE_UID = "REAL-DEVICE-001"
    }
}
