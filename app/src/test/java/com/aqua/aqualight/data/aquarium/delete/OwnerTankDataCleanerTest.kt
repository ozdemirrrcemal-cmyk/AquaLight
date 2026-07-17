package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
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
    fun `invalid and duplicate ids are normalized before the transaction begins`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        var deletedIds: List<Long> = emptyList()
        val cleaner = cleaner(
            integrity = integrity,
            deleteTankRecords = { ids -> deletedIds = ids }
        )

        val result = cleaner.deleteTanks(listOf(-1L, 7L, 7L, 8L, 0L))

        assertEquals(listOf(7L, 8L), deletedIds)
        assertEquals(listOf(7L, 8L), integrity.begunTankIds)
        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
    }

    @Test
    fun `care tasks are snapshotted and deleted before the tank record`() = runBlocking {
        val calls = mutableListOf<String>()
        val cleaner = cleaner(
            snapshotCareTasksForTank = { tankId ->
                calls += "snapshot:$tankId"
                listOf(validTask(tankId = tankId))
            },
            deleteCareTasksForTank = { tankId ->
                calls += "care-delete:$tankId"
            },
            deleteTankRecords = { ids ->
                calls += "tank-delete:${ids.joinToString()}"
            },
            removeAssignmentsForTank = { tankId ->
                calls += "assignment-delete:$tankId"
                TankAssignmentCleanupResult.Completed(1)
            }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
        assertEquals(
            listOf(
                "snapshot:7",
                "care-delete:7",
                "tank-delete:7",
                "assignment-delete:7"
            ),
            calls
        )
    }

    @Test
    fun `tank deletion failure restores care snapshots and aborts the journal`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        val restored = mutableListOf<CareTask>()
        val primaryError = IllegalStateException("tank write failed")
        val cleaner = cleaner(
            integrity = integrity,
            snapshotCareTasksForTank = { tankId ->
                listOf(validTask(tankId = tankId))
            },
            deleteTankRecords = { throw primaryError },
            restoreCareTasksForTank = { _, tasks -> restored += tasks }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertEquals(
            primaryError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
        assertEquals(1, restored.size)
        assertEquals(listOf(7L), integrity.rollbackAllowedTankIds)
        assertEquals(listOf(7L), integrity.abortedTankIds)
        assertTrue(integrity.completedTankIds.isEmpty())
    }

    @Test
    fun `care deletion failure rolls back and never deletes the tank`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        var tankDeleteCalls = 0
        var restoreCalls = 0
        val careError = IllegalStateException("care write failed")
        val cleaner = cleaner(
            integrity = integrity,
            snapshotCareTasksForTank = { tankId ->
                listOf(validTask(tankId = tankId))
            },
            deleteCareTasksForTank = { throw careError },
            deleteTankRecords = { tankDeleteCalls += 1 },
            restoreCareTasksForTank = { _, _ -> restoreCalls += 1 }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertEquals(
            careError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
        assertEquals(0, tankDeleteCalls)
        assertEquals(1, restoreCalls)
        assertEquals(listOf(7L), integrity.abortedTankIds)
    }

    @Test
    fun `successful deletion completes the journal before assignment cleanup`() = runBlocking {
        val events = mutableListOf<String>()
        val integrity = RecordingIntegrityTransactions(events)
        val cleaner = cleaner(
            integrity = integrity,
            removeAssignmentsForTank = { tankId ->
                events += "assignment:$tankId"
                TankAssignmentCleanupResult.Completed(1)
            }
        )

        val result = cleaner.deleteTanks(listOf(7L))
            as OwnerTankDataCleaner.Result.Deleted

        assertFalse(result.hasCleanupIssues)
        assertTrue(
            events.indexOf("complete:7") < events.indexOf("assignment:7")
        )
    }

    @Test
    fun `assignment cleanup failure is reported after authoritative deletion`() = runBlocking {
        val cleanupError = IllegalStateException("assignment cleanup failed")
        val cleaner = cleaner(
            removeAssignmentsForTank = {
                TankAssignmentCleanupResult.Failure(cleanupError)
            }
        )

        val result = cleaner.deleteTanks(listOf(7L))
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
    fun `cancellation rolls back care snapshots before it is rethrown`() {
        val integrity = RecordingIntegrityTransactions()
        var restoreCalls = 0

        assertThrows(CancellationException::class.java) {
            runBlocking {
                cleaner(
                    integrity = integrity,
                    snapshotCareTasksForTank = { tankId ->
                        listOf(validTask(tankId = tankId))
                    },
                    deleteCareTasksForTank = {
                        throw CancellationException("screen closed")
                    },
                    restoreCareTasksForTank = { _, _ -> restoreCalls += 1 }
                ).deleteTanks(listOf(7L))
            }
        }

        assertEquals(1, restoreCalls)
        assertEquals(listOf(7L), integrity.abortedTankIds)
    }

    private fun cleaner(
        integrity: RecordingIntegrityTransactions = RecordingIntegrityTransactions(),
        deleteTankRecords: suspend (List<Long>) -> Unit = {},
        snapshotCareTasksForTank: suspend (Long) -> List<CareTask> = { emptyList() },
        deleteCareTasksForTank: suspend (Long) -> Unit = {},
        restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit = { _, _ -> },
        removeAssignmentsForTank: suspend (Long) -> TankAssignmentCleanupResult = {
            TankAssignmentCleanupResult.Completed(0)
        }
    ): OwnerTankDataCleaner {
        return OwnerTankDataCleaner(
            deleteTankRecords = deleteTankRecords,
            snapshotCareTasksForTank = snapshotCareTasksForTank,
            deleteCareTasksForTank = deleteCareTasksForTank,
            restoreCareTasksForTank = restoreCareTasksForTank,
            removeDeviceAssignmentsForTank = removeAssignmentsForTank,
            integrityTransactions = integrity,
            ownerUidProvider = { OWNER_UID }
        )
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

    private class RecordingIntegrityTransactions(
        private val events: MutableList<String> = mutableListOf()
    ) : TankCareIntegrityTransactions {
        var begunTankIds: List<Long> = emptyList()
        val rollbackAllowedTankIds = mutableListOf<Long>()
        val completedTankIds = mutableListOf<Long>()
        val abortedTankIds = mutableListOf<Long>()

        override fun begin(ownerUid: String, tankIds: Collection<Long>) {
            begunTankIds = tankIds.toList()
            events += "begin:${tankIds.joinToString()}"
        }

        override fun captureSnapshots(
            ownerUid: String,
            snapshotsByTank: Map<Long, List<CareTask>>
        ) {
            events += "capture:${snapshotsByTank.keys.joinToString()}"
        }

        override suspend fun <T> withRollbackWritesAllowed(
            ownerUid: String,
            tankId: Long,
            block: suspend () -> T
        ): T {
            rollbackAllowedTankIds += tankId
            events += "rollback:$tankId"
            return block()
        }

        override fun complete(ownerUid: String, tankId: Long) {
            completedTankIds += tankId
            events += "complete:$tankId"
        }

        override fun abort(ownerUid: String, tankId: Long) {
            abortedTankIds += tankId
            events += "abort:$tankId"
        }
    }

    private companion object {
        const val OWNER_UID = "owner-test"
    }
}