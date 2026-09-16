package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UserDataBackupRestorerTest {

    @Test
    fun `same backup reuses a restored aquarium after the local version is edited`() = runBlocking {
        val harness = RestoreHarness()
        val restorer = harness.restorer()

        withOwner {
            val first = restorer.restore(RestoreFixture.backup())
            assertEquals(1, first.restoredAquariumCount)
            harness.editOnlyTankName("Edited after restore")

            val second = restorer.restore(RestoreFixture.backup())

            assertEquals(0, second.restoredAquariumCount)
            assertEquals(1, harness.tanks.size)
            assertEquals("Edited after restore", harness.tanks.single().name)
        }
    }

    @Test
    fun `care task id collision is remapped before mutation`() = runBlocking {
        val harness = RestoreHarness()
        harness.tasks += RestoreFixture.localTask(
            id = RestoreFixture.SOURCE_TASK_ID,
            tankId = RestoreFixture.UNRELATED_TANK_ID,
            title = "Existing unrelated task"
        )

        withOwner {
            val result = harness.restorer().restore(
                RestoreFixture.backup(
                    careTasks = listOf(RestoreFixture.archivedCareTask())
                )
            )

            assertEquals(1, result.restoredCareTaskCount)
            assertEquals(2, harness.tasks.size)
            assertTrue(
                harness.tasks.any { task ->
                    task.id == RestoreFixture.RESTORE_TASK_ID_FALLBACK &&
                        task.title == "Test water"
                }
            )
        }
    }

    @Test
    fun `mutation failure rolls back tanks tasks and the durable transaction`() = runBlocking {
        val harness = RestoreHarness().apply {
            assignmentBehavior = { _, _ ->
                TankDeviceAssignmentResult.Failure(IllegalStateException("assignment failed"))
            }
        }

        withOwner {
            val failure = runCatching {
                harness.restorer().restore(
                    RestoreFixture.backup(
                        careTasks = listOf(RestoreFixture.archivedCareTask()),
                        assignments = listOf(RestoreFixture.archiveAssignment("device-fail"))
                    )
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(harness.tanks.isEmpty())
            assertTrue(harness.tasks.isEmpty())
            assertTrue(harness.assignments.isEmpty())
            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    @Test
    fun `active journal recovery rolls back an interrupted restore after process restart`() =
        runBlocking {
            val harness = RestoreHarness()

            withOwner {
                harness.seedExistingTank()
                val existingId = harness.tanks.single().id
                harness.simulateInterruptedRestore()

                val result = harness.recovery.recover(RestoreFixture.OWNER_UID)

                assertEquals(1, result.rolledBackTankCount)
                assertEquals(1, result.rolledBackTaskCount)
                assertEquals(1, result.rolledBackAssignmentCount)
                assertEquals(listOf(existingId), harness.tanks.map { tank -> tank.id })
                assertTrue(harness.tasks.isEmpty())
                assertTrue(harness.assignments.isEmpty())
                assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            }
        }

    @Test
    fun `device conflict and missing device are skipped without failing restore`() = runBlocking {
        val harness = RestoreHarness().apply {
            assignmentBehavior = ::skippedAssignmentResult
        }

        withOwner {
            val result = harness.restorer().restore(
                RestoreFixture.backup(
                    assignments = listOf(
                        RestoreFixture.archiveAssignment("device-conflict"),
                        RestoreFixture.archiveAssignment("device-missing")
                    )
                )
            )

            assertEquals(0, result.restoredDeviceAssignmentCount)
            assertEquals(2, result.skippedDeviceAssignmentCount)
            assertEquals(1, harness.tanks.size)
        }
    }

    @Test
    fun `reminder reconciliation failure is reported as warning after committed restore`() =
        runBlocking {
            val harness = RestoreHarness().apply {
                reminderFailure = IllegalStateException("scheduler unavailable")
            }

            withOwner {
                val result = harness.restorer().restore(RestoreFixture.backup())

                assertTrue(result.reminderReconciliationWarning)
                assertEquals(1, harness.tanks.size)
                assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            }
        }

    @Test
    fun `cancellation is rethrown after rollback`() = runBlocking {
        val harness = RestoreHarness().apply {
            assignmentBehavior = { _, _ -> throw CancellationException("cancelled") }
        }

        withOwner {
            try {
                harness.restorer().restore(
                    RestoreFixture.backup(
                        assignments = listOf(RestoreFixture.archiveAssignment("device-cancel"))
                    )
                )
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
                assertTrue(harness.tanks.isEmpty())
                assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            }
        }
    }

    private suspend fun <T> withOwner(block: suspend () -> T): T {
        return UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID, block)
    }

    private fun skippedAssignmentResult(
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentResult {
        return if (deviceUid.value == "device-conflict") {
            TankDeviceAssignmentResult.Conflict(
                existingAssignment = TankDeviceAssignment(
                    ownerUid = RestoreFixture.OWNER_UID,
                    tankId = tankId + RestoreFixture.CONFLICT_TANK_OFFSET,
                    deviceUid = deviceUid,
                    assignedAtMillis = RestoreFixture.ASSIGNED_AT_MILLIS
                )
            )
        } else {
            TankDeviceAssignmentResult.DeviceNotFound
        }
    }
}
