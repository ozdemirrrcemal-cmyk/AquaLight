package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataRestoreOwnershipTest {

    @Test
    fun `exact recovery preserves unrelated tank created during pending restore`() = runBlocking {
        val preexisting = RestoreFixture.createSavedTank(
            id = RestoreFixture.PREEXISTING_TANK_ID,
            name = "Existing",
            createdAtMillis = RestoreFixture.PREEXISTING_TANK_CREATED_AT_MILLIS
        )
        val restored = RestoreFixture.createSavedTank(
            id = RestoreFixture.FIRST_LOCAL_TANK_ID,
            name = "Restored",
            createdAtMillis = RestoreFixture.FIRST_LOCAL_TANK_CREATED_AT_MILLIS
        )
        val unrelated = RestoreFixture.createSavedTank(
            id = RestoreFixture.FIRST_LOCAL_TANK_ID + 1,
            name = "Independent",
            createdAtMillis = RestoreFixture.FIRST_LOCAL_TANK_CREATED_AT_MILLIS + 1
        )
        val harness = ExactRecoveryHarness(
            tanks = mutableListOf(preexisting, restored, unrelated),
            pending = exactPending(
                tanks = listOf(
                    RestoreCreatedTank(restored.id, restored.createdAtMillis)
                )
            )
        )

        withOwner {
            val result = harness.recovery.recover(RestoreFixture.OWNER_UID)

            assertEquals(1, result.rolledBackTankCount)
            assertEquals(setOf(preexisting.id, unrelated.id), harness.tanks.map { it.id }.toSet())
            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    @Test
    fun `exact recovery preserves task and assignment whose identity changed`() = runBlocking {
        val scenario = changedIdentityScenario()

        withOwner {
            val result = scenario.harness.recovery.recover(RestoreFixture.OWNER_UID)

            assertEquals(1, result.rolledBackTankCount)
            assertEquals(0, result.rolledBackTaskCount)
            assertEquals(0, result.rolledBackAssignmentCount)
            assertEquals("Independent replacement", scenario.harness.tasks.single().title)
            assertEquals(
                scenario.preexistingTankId,
                scenario.harness.assignments.getValue(scenario.deviceUid).tankId
            )
            assertTrue(
                scenario.harness.tanks.any { tank -> tank.id == scenario.preexistingTankId }
            )
            assertNull(scenario.harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    private fun changedIdentityScenario(): ChangedIdentityScenario {
        val preexisting = RestoreFixture.createSavedTank(
            id = RestoreFixture.PREEXISTING_TANK_ID,
            name = "Existing",
            createdAtMillis = RestoreFixture.PREEXISTING_TANK_CREATED_AT_MILLIS
        )
        val restored = RestoreFixture.createSavedTank(
            id = RestoreFixture.FIRST_LOCAL_TANK_ID,
            name = "Restored",
            createdAtMillis = RestoreFixture.FIRST_LOCAL_TANK_CREATED_AT_MILLIS
        )
        val originalTask = RestoreFixture.localTask(
            id = RestoreFixture.INTERRUPTED_TASK_ID,
            tankId = restored.id,
            title = "Restore-created task"
        )
        val replacementTask = originalTask.copy(
            tankId = preexisting.id,
            title = "Independent replacement",
            createdAtMillis = originalTask.createdAtMillis + 1,
            updatedAtMillis = originalTask.updatedAtMillis + 1
        )
        val deviceUid = DeviceUid(RestoreFixture.INTERRUPTED_DEVICE_UID)
        val replacementAssignment = TankDeviceAssignment(
            ownerUid = RestoreFixture.OWNER_UID,
            tankId = preexisting.id,
            deviceUid = deviceUid,
            assignedAtMillis = RestoreFixture.ASSIGNED_AT_MILLIS + 1
        )
        val pending = exactPending(
            tanks = listOf(RestoreCreatedTank(restored.id, restored.createdAtMillis)),
            tasks = listOf(
                RestoreCreatedTask(originalTask.id, originalTask.tankId, originalTask.createdAtMillis)
            ),
            assignments = listOf(
                RestoreCreatedAssignment(
                    tankId = restored.id,
                    deviceUid = deviceUid,
                    assignedAtMillis = RestoreFixture.ASSIGNED_AT_MILLIS
                )
            )
        )
        return ChangedIdentityScenario(
            harness = ExactRecoveryHarness(
                tanks = mutableListOf(preexisting, restored),
                tasks = mutableListOf(replacementTask),
                assignments = linkedMapOf(deviceUid to replacementAssignment),
                pending = pending
            ),
            deviceUid = deviceUid,
            preexistingTankId = preexisting.id
        )
    }

    private suspend fun <T> withOwner(block: suspend () -> T): T {
        return UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID, block)
    }

    private fun exactPending(
        tanks: List<RestoreCreatedTank> = emptyList(),
        tasks: List<RestoreCreatedTask> = emptyList(),
        assignments: List<RestoreCreatedAssignment> = emptyList()
    ): PendingUserDataRestore {
        return PendingUserDataRestore(
            ownerUid = RestoreFixture.OWNER_UID,
            state = UserDataRestoreTransactionState.ACTIVE,
            existingTankIds = emptySet(),
            plannedTaskIds = emptyList(),
            plannedAssignments = emptyList(),
            createdTanks = tanks,
            createdTasks = tasks,
            createdAssignments = assignments,
            exactMutationTracking = true
        )
    }

    private data class ChangedIdentityScenario(
        val harness: ExactRecoveryHarness,
        val deviceUid: DeviceUid,
        val preexistingTankId: Long
    )
}

private class ExactRecoveryHarness(
    val tanks: MutableList<SavedAquariumTank>,
    val tasks: MutableList<CareTask> = mutableListOf(),
    val assignments: LinkedHashMap<DeviceUid, TankDeviceAssignment> = linkedMapOf(),
    pending: PendingUserDataRestore
) {
    val transactions = ExactRecoveryTransactions(pending)

    private val sources = UserDataRestoreDataSources(
        tanks = UserDataRestoreDataSources.TankDataSource(
            snapshotForOwner = { owner -> tanks.filter { tank -> tank.ownerUid == owner } },
            addFromDraft = { _, _ -> error("Not used") },
            updateSmartCareEnabled = { _, _ -> error("Not used") },
            updateCareRemindersEnabled = { _, _ -> error("Not used") },
            addLivestockToTank = { _, _ -> error("Not used") },
            deleteTanks = { ids -> tanks.removeAll { tank -> tank.id in ids } }
        ),
        careTasks = UserDataRestoreDataSources.CareTaskDataSource(
            snapshot = { tasks.toList() },
            addTask = { error("Not used") },
            deleteTask = { id -> tasks.removeAll { task -> task.id == id } }
        ),
        assignments = UserDataRestoreDataSources.AssignmentDataSource(
            assignmentForDevice = { uid -> assignments[uid] },
            assignDeviceToTank = { _, _ -> TankDeviceAssignmentResult.InvalidRequest },
            removeDeviceFromTank = { tankId, uid ->
                val current = assignments[uid]
                if (current?.tankId == tankId) {
                    assignments.remove(uid)
                    TankDeviceRemovalResult.Removed
                } else {
                    TankDeviceRemovalResult.NotAssigned
                }
            }
        )
    )

    val recovery = UserDataRestoreRecovery(
        dataSources = sources,
        transactions = transactions,
        provenance = InMemoryRestoreProvenance()
    )
}

private class ExactRecoveryTransactions(
    pending: PendingUserDataRestore
) : UserDataRestoreTransactions {
    private var current: PendingUserDataRestore? = pending

    override fun pending(ownerUid: String): PendingUserDataRestore? {
        return current?.takeIf { transaction -> transaction.ownerUid == ownerUid }
    }

    override fun begin(ownerUid: String, existingTankIds: Set<Long>) = error("Not used")

    override fun planTasks(ownerUid: String, taskIds: Collection<Long>) = error("Not used")

    override fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    ) = error("Not used")

    override fun markCommitted(ownerUid: String) = error("Not used")

    override fun clearOwner(ownerUid: String) {
        if (current?.ownerUid == ownerUid) current = null
    }
}
