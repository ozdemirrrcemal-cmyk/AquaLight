package com.aqua.aqualight.data.devices.remove

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerDeviceDataCleanerTest {

    @Test
    fun `all successful deletes are reported once`() = runBlocking {
        val forgotten = mutableListOf<DeviceUid>()
        val cleaner = cleaner(
            forgetDevice = { deviceUid ->
                forgotten += deviceUid
            }
        )

        val result = cleaner.deleteDevices(
            listOf(DEVICE_A, DEVICE_A, DEVICE_B)
        )

        assertTrue(result.isCompleteSuccess)
        assertEquals(setOf(DEVICE_A, DEVICE_B), result.succeededDeviceUids)
        assertEquals(listOf(DEVICE_A, DEVICE_B), forgotten)
    }

    @Test
    fun `assignment removal failure prevents device deletion`() = runBlocking {
        var forgetCalled = false
        val cleaner = cleaner(
            assignmentForDevice = { assignment(it) },
            removeAssignment = {
                TankDeviceRemovalResult.Failure(IllegalStateException("remove failed"))
            },
            forgetDevice = {
                forgetCalled = true
            }
        )

        val result = cleaner.deleteDevices(listOf(DEVICE_A))

        assertTrue(result.isCompleteFailure)
        assertFalse(forgetCalled)
        assertEquals(
            OwnerDeviceDataCleaner.FailureStage.REMOVE_ASSIGNMENT,
            result.failures.single().stage
        )
    }

    @Test
    fun `device failure restores previous assignment`() = runBlocking {
        val restored = mutableListOf<TankDeviceAssignment>()
        val previousAssignment = assignment(DEVICE_A)
        val cleaner = cleaner(
            assignmentForDevice = { previousAssignment },
            restoreAssignment = { assignment ->
                restored += assignment
                TankDeviceAssignmentResult.Assigned(assignment)
            },
            forgetDevice = {
                throw IllegalStateException("forget failed")
            }
        )

        val result = cleaner.deleteDevices(listOf(DEVICE_A))

        assertTrue(result.isCompleteFailure)
        assertEquals(listOf(previousAssignment), restored)
        assertNull(result.failures.single().rollbackError)
    }

    @Test
    fun `rollback failure is preserved for recovery diagnostics`() = runBlocking {
        val rollbackError = IllegalStateException("rollback failed")
        val cleaner = cleaner(
            assignmentForDevice = { assignment(it) },
            restoreAssignment = {
                TankDeviceAssignmentResult.Failure(rollbackError)
            },
            forgetDevice = {
                throw IllegalStateException("forget failed")
            }
        )

        val result = cleaner.deleteDevices(listOf(DEVICE_A))

        assertEquals(rollbackError, result.failures.single().rollbackError)
    }

    @Test
    fun `partial success keeps only failed device in failure set`() = runBlocking {
        val cleaner = cleaner(
            forgetDevice = { deviceUid ->
                if (deviceUid == DEVICE_B) {
                    throw IllegalStateException("B failed")
                }
            }
        )

        val result = cleaner.deleteDevices(listOf(DEVICE_A, DEVICE_B))

        assertEquals(setOf(DEVICE_A), result.succeededDeviceUids)
        assertEquals(listOf(DEVICE_B), result.failures.map { it.deviceUid })
        assertFalse(result.isCompleteSuccess)
        assertFalse(result.isCompleteFailure)
    }

    private fun cleaner(
        assignmentForDevice: suspend (DeviceUid) -> TankDeviceAssignment? = { null },
        removeAssignment: suspend (DeviceUid) -> TankDeviceRemovalResult = {
            TankDeviceRemovalResult.Removed
        },
        restoreAssignment: suspend (TankDeviceAssignment) -> TankDeviceAssignmentResult = {
            TankDeviceAssignmentResult.Assigned(it)
        },
        forgetDevice: suspend (DeviceUid) -> Unit = {}
    ): OwnerDeviceDataCleaner {
        return OwnerDeviceDataCleaner(
            assignmentForDevice = assignmentForDevice,
            removeAssignment = removeAssignment,
            restoreAssignment = restoreAssignment,
            forgetDevice = forgetDevice
        )
    }

    private fun assignment(
        deviceUid: DeviceUid
    ): TankDeviceAssignment {
        return TankDeviceAssignment(
            ownerUid = "owner-a",
            tankId = 10L,
            deviceUid = deviceUid,
            assignedAtMillis = 100L
        )
    }

    private companion object {
        val DEVICE_A = DeviceUid("device-a")
        val DEVICE_B = DeviceUid("device-b")
    }
}
