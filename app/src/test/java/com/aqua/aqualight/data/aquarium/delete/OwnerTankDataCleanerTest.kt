package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityTransactions
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityTransactions
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTankDataCleanerTest {

    @Test
    fun `invalid and duplicate ids are normalized before transactions begin`() = runBlocking {
        val fixture = CleanerFixture()
        var deletedIds: List<Long> = emptyList()
        fixture.deleteTankRecords = { ids -> deletedIds = ids }

        val result = fixture.create()
            .deleteTanks(listOf(-1L, 7L, 7L, 8L, 0L))

        assertEquals(listOf(7L, 8L), deletedIds)
        assertEquals(listOf(7L, 8L), fixture.careIntegrity.begunTankIds)
        assertEquals(listOf(7L, 8L), fixture.healthIntegrity.begunTankIds)
        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
    }

    @Test
    fun `dependent data is snapshotted and deleted before the tank record`() = runBlocking {
        val fixture = CleanerFixture()
        val calls = mutableListOf<String>()

        fixture.snapshotCareTasksForTank = { tankId ->
            calls += "care-snapshot:$tankId"
            listOf(validTask(tankId = tankId))
        }
        fixture.snapshotHealthRecordsForTank = { _, tankId ->
            calls += "health-snapshot:$tankId"
            emptyHealthSnapshot()
        }
        fixture.deleteCareTasksForTank = { tankId ->
            calls += "care-delete:$tankId"
        }
        fixture.deleteHealthRecordsForTank = { _, tankId ->
            calls += "health-delete:$tankId"
        }
        fixture.deleteTankRecords = { ids ->
            calls += "tank-delete:${ids.joinToString()}"
        }
        fixture.removeAssignmentsForTank = { tankId ->
            calls += "assignment-delete:$tankId"
            TankAssignmentCleanupResult.Completed(1)
        }

        val result = fixture.create().deleteTanks(listOf(7L))

        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
        assertEquals(
            listOf(
                "care-snapshot:7",
                "health-snapshot:7",
                "care-delete:7",
                "health-delete:7",
                "tank-delete:7",
                "assignment-delete:7"
            ),
            calls
        )
    }

    @Test
    fun `successful deletion cancels deleted task reminders for captured owner`() = runBlocking {
        val fixture = CleanerFixture()
        val cancelled = mutableListOf<String>()
        fixture.snapshotCareTasksForTank = { tankId ->
            listOf(
                validTask(tankId = tankId).copy(id = 101L),
                validTask(tankId = tankId).copy(id = 102L)
            )
        }
        fixture.cancelCareTaskReminder = { ownerUid, taskId ->
            cancelled += "$ownerUid:$taskId"
        }

        val result = fixture.create().deleteTanks(listOf(7L))

        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
        assertEquals(
            listOf("$OWNER_UID:101", "$OWNER_UID:102"),
            cancelled
        )
    }

    @Test
    fun `tank deletion failure restores care and health snapshots`() = runBlocking {
        val fixture = CleanerFixture()
        val restoredCare = mutableListOf<CareTask>()
        var restoredHealth = 0
        val reconciledOwners = mutableListOf<String>()
        val primaryError = IllegalStateException("tank write failed")

        fixture.snapshotCareTasksForTank = { tankId ->
            listOf(validTask(tankId = tankId))
        }
        fixture.deleteTankRecords = { throw primaryError }
        fixture.restoreCareTasksForTank = { _, tasks ->
            restoredCare += tasks
        }
        fixture.restoreHealthRecordsForTank = { _, _, _ ->
            restoredHealth += 1
        }
        fixture.reconcileCareReminders = { ownerUid ->
            reconciledOwners += ownerUid
        }

        val result = fixture.create().deleteTanks(listOf(7L))

        assertEquals(
            primaryError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
        assertEquals(1, restoredCare.size)
        assertEquals(1, restoredHealth)
        assertEquals(listOf(OWNER_UID), reconciledOwners)
        assertEquals(listOf(7L), fixture.careIntegrity.rollbackAllowedTankIds)
        assertEquals(listOf(7L), fixture.healthIntegrity.rollbackAllowedTankIds)
        assertEquals(listOf(7L), fixture.careIntegrity.abortedTankIds)
        assertEquals(listOf(7L), fixture.healthIntegrity.abortedTankIds)
    }

    @Test
    fun `care deletion failure rolls back dependencies and never deletes tank`() = runBlocking {
        val fixture = CleanerFixture()
        var tankDeleteCalls = 0
        var careRestoreCalls = 0
        var healthRestoreCalls = 0
        val careError = IllegalStateException("care write failed")

        fixture.snapshotCareTasksForTank = { tankId ->
            listOf(validTask(tankId = tankId))
        }
        fixture.deleteCareTasksForTank = { throw careError }
        fixture.deleteTankRecords = { tankDeleteCalls += 1 }
        fixture.restoreCareTasksForTank = { _, _ -> careRestoreCalls += 1 }
        fixture.restoreHealthRecordsForTank = { _, _, _ ->
            healthRestoreCalls += 1
        }

        val result = fixture.create().deleteTanks(listOf(7L))

        assertEquals(
            careError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
        assertEquals(0, tankDeleteCalls)
        assertEquals(1, careRestoreCalls)
        assertEquals(1, healthRestoreCalls)
        assertEquals(listOf(7L), fixture.careIntegrity.abortedTankIds)
        assertEquals(listOf(7L), fixture.healthIntegrity.abortedTankIds)
    }

    @Test
    fun `successful deletion completes journals before assignment cleanup`() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = CleanerFixture(
            careIntegrity = RecordingCareIntegrityTransactions(events, "care"),
            healthIntegrity = RecordingHealthIntegrityTransactions(events, "health")
        )
        fixture.removeAssignmentsForTank = { tankId ->
            events += "assignment:$tankId"
            TankAssignmentCleanupResult.Completed(1)
        }

        val result = fixture.create().deleteTanks(listOf(7L))
            as OwnerTankDataCleaner.Result.Deleted

        assertFalse(result.hasCleanupIssues)
        val assignmentIndex = events.indexOf("assignment:7")
        assertTrue(events.indexOf("care-complete:7") < assignmentIndex)
        assertTrue(events.indexOf("health-complete:7") < assignmentIndex)
    }

    @Test
    fun `assignment cleanup failure is reported after authoritative deletion`() = runBlocking {
        val fixture = CleanerFixture()
        val cleanupError = IllegalStateException("assignment cleanup failed")
        fixture.removeAssignmentsForTank = {
            TankAssignmentCleanupResult.Failure(cleanupError)
        }

        val result = fixture.create().deleteTanks(listOf(7L))
            as OwnerTankDataCleaner.Result.Deleted

        assertEquals(listOf(7L), result.tankIds)
        assertTrue(result.hasCleanupIssues)
        assertEquals(cleanupError, result.cleanupIssues.single().error)
        assertEquals(
            OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS,
            result.cleanupIssues.single().stage
        )
    }

    @Test
    fun `cancellation restores snapshots before it is rethrown`() {
        val fixture = CleanerFixture()
        var careRestoreCalls = 0
        var healthRestoreCalls = 0
        fixture.snapshotCareTasksForTank = { tankId ->
            listOf(validTask(tankId = tankId))
        }
        fixture.deleteCareTasksForTank = {
            throw CancellationException("screen closed")
        }
        fixture.restoreCareTasksForTank = { _, _ -> careRestoreCalls += 1 }
        fixture.restoreHealthRecordsForTank = { _, _, _ ->
            healthRestoreCalls += 1
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                fixture.create().deleteTanks(listOf(7L))
            }
        }

        assertEquals(1, careRestoreCalls)
        assertEquals(1, healthRestoreCalls)
        assertEquals(listOf(7L), fixture.careIntegrity.abortedTankIds)
        assertEquals(listOf(7L), fixture.healthIntegrity.abortedTankIds)
    }

    private fun validTask(tankId: Long): CareTask = CareTask(
        id = 100L + tankId,
        ownerUid = OWNER_UID,
        tankId = tankId,
        title = "Inspect filter",
        description = "",
        type = CareTaskType.FILTER_MAINTENANCE,
        source = CareTaskSource.MANUAL,
        status = CareTaskStatus.PENDING,
        dueAtMillis = 1_767_312_000_000L,
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = null,
        note = "",
        generatedRuleKey = "",
        createdAtMillis = 1_767_225_600_000L,
        updatedAtMillis = 1_767_225_600_000L
    )

    private class CleanerFixture(
        val careIntegrity: RecordingCareIntegrityTransactions =
            RecordingCareIntegrityTransactions(),
        val healthIntegrity: RecordingHealthIntegrityTransactions =
            RecordingHealthIntegrityTransactions()
    ) {
        var deleteTankRecords: suspend (List<Long>) -> Unit = {}
        var snapshotCareTasksForTank: suspend (Long) -> List<CareTask> =
            { emptyList() }
        var deleteCareTasksForTank: suspend (Long) -> Unit = {}
        var restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit =
            { _, _ -> }
        var snapshotHealthRecordsForTank:
            suspend (String, Long) -> TankHealthIntegritySnapshot =
            { _, _ -> emptyHealthSnapshot() }
        var deleteHealthRecordsForTank: suspend (String, Long) -> Unit =
            { _, _ -> }
        var restoreHealthRecordsForTank:
            suspend (String, Long, TankHealthIntegritySnapshot) -> Unit =
            { _, _, _ -> }
        var removeAssignmentsForTank:
            suspend (Long) -> TankAssignmentCleanupResult =
            { TankAssignmentCleanupResult.Completed(0) }
        var cancelCareTaskReminder: suspend (String, Long) -> Unit =
            { _, _ -> }
        var reconcileCareReminders: suspend (String) -> Unit = {}

        fun create(): OwnerTankDataCleaner = OwnerTankDataCleaner(
            OwnerTankDeletionDependencies(
                deleteTankRecords = deleteTankRecords,
                care = TankCareDeletionDependencies(
                    snapshotForTank = snapshotCareTasksForTank,
                    deleteForTank = deleteCareTasksForTank,
                    restoreForTank = restoreCareTasksForTank,
                    cancelCareTaskReminder = cancelCareTaskReminder,
                    reconcileReminders = reconcileCareReminders,
                    integrity = careIntegrity
                ),
                health = TankHealthDeletionDependencies(
                    snapshotForTank = snapshotHealthRecordsForTank,
                    deleteForTank = deleteHealthRecordsForTank,
                    restoreForTank = restoreHealthRecordsForTank,
                    integrity = healthIntegrity
                ),
                removeDeviceAssignmentsForTank = removeAssignmentsForTank,
                ownerUidProvider = { OWNER_UID }
            )
        )
    }

    private class RecordingCareIntegrityTransactions(
        private val events: MutableList<String> = mutableListOf(),
        private val prefix: String = "care"
    ) : TankCareIntegrityTransactions {
        var begunTankIds: List<Long> = emptyList()
        val rollbackAllowedTankIds = mutableListOf<Long>()
        val completedTankIds = mutableListOf<Long>()
        val abortedTankIds = mutableListOf<Long>()

        override fun begin(ownerUid: String, tankIds: Collection<Long>) {
            begunTankIds = tankIds.toList()
            events += "$prefix-begin:${tankIds.joinToString()}"
        }

        override fun captureSnapshots(
            ownerUid: String,
            snapshotsByTank: Map<Long, List<CareTask>>
        ) {
            events += "$prefix-capture:${snapshotsByTank.keys.joinToString()}"
        }

        override suspend fun <T> withRollbackWritesAllowed(
            ownerUid: String,
            tankId: Long,
            block: suspend () -> T
        ): T {
            rollbackAllowedTankIds += tankId
            events += "$prefix-rollback:$tankId"
            return block()
        }

        override fun complete(ownerUid: String, tankId: Long) {
            completedTankIds += tankId
            events += "$prefix-complete:$tankId"
        }

        override fun abort(ownerUid: String, tankId: Long) {
            abortedTankIds += tankId
            events += "$prefix-abort:$tankId"
        }
    }

    private class RecordingHealthIntegrityTransactions(
        private val events: MutableList<String> = mutableListOf(),
        private val prefix: String = "health"
    ) : TankHealthIntegrityTransactions {
        var begunTankIds: List<Long> = emptyList()
        val rollbackAllowedTankIds = mutableListOf<Long>()
        val completedTankIds = mutableListOf<Long>()
        val abortedTankIds = mutableListOf<Long>()

        override fun begin(ownerUid: String, tankIds: Collection<Long>) {
            begunTankIds = tankIds.toList()
            events += "$prefix-begin:${tankIds.joinToString()}"
        }

        override fun captureSnapshots(
            ownerUid: String,
            snapshotsByTank: Map<Long, TankHealthIntegritySnapshot>
        ) {
            events += "$prefix-capture:${snapshotsByTank.keys.joinToString()}"
        }

        override suspend fun <T> withRollbackWritesAllowed(
            ownerUid: String,
            tankId: Long,
            block: suspend () -> T
        ): T {
            rollbackAllowedTankIds += tankId
            events += "$prefix-rollback:$tankId"
            return block()
        }

        override fun complete(ownerUid: String, tankId: Long) {
            completedTankIds += tankId
            events += "$prefix-complete:$tankId"
        }

        override fun abort(ownerUid: String, tankId: Long) {
            abortedTankIds += tankId
            events += "$prefix-abort:$tankId"
        }
    }

    private companion object {
        const val OWNER_UID = "owner-test"

        fun emptyHealthSnapshot() = TankHealthIntegritySnapshot(
            waterTests = emptyList(),
            observations = emptyList()
        )
    }
}
