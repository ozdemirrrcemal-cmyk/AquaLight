package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.OwnerArchiveMutationGate
import com.aqua.aqualight.data.user.archive.InMemoryRestoreTransactions
import com.aqua.aqualight.data.user.archive.requireNoActiveRestore
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDeletionIntegrity
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityTransactions
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTankDataCleanerTest {

    @Test
    fun `deletion waits before opening journal when a writer holds the tank`() = runBlocking {
        withTimeout(5_000L) {
            val integrity = RecordingIntegrityTransactions()
            val cleaner = cleaner(integrity = integrity)
            val deletion = OwnerTankMutationGate.shared.withTanks(OWNER_UID, listOf(7L)) {
                async(start = CoroutineStart.UNDISPATCHED) { cleaner.deleteTanks(listOf(7L)) }
                    .also {
                        assertFalse(it.isCompleted)
                        assertTrue(integrity.begunTankIds.isEmpty())
                    }
            }
            assertTrue(deletion.await() is OwnerTankDataCleaner.Result.Deleted)
            assertEquals(listOf(7L), integrity.completedTankIds)
        }
    }

    @Test
    fun `deletion retains the gate until analysis cleanup and journal completion`() = runBlocking {
        withTimeout(5_000L) {
            val integrity = RecordingIntegrityTransactions()
            val cleanupEntered = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val cleaner = waterCleaner(integrity) {
                cleanupEntered.complete(Unit)
                releaseCleanup.await()
            }
            val deletion = async { cleaner.deleteTanks(listOf(7L)) }
            cleanupEntered.await()
            val writer = async(start = CoroutineStart.UNDISPATCHED) {
                OwnerTankMutationGate.shared.withTanks(OWNER_UID, listOf(7L)) {
                    assertEquals(listOf(7L), integrity.completedTankIds)
                }
            }
            assertFalse(writer.isCompleted)
            releaseCleanup.complete(Unit)
            assertTrue(deletion.await() is OwnerTankDataCleaner.Result.Deleted)
            writer.await()
        }
    }

    @Test
    fun `cancelled deletion holds the gate until non cancellable rollback finishes`() = runBlocking {
        withTimeout(5_000L) {
            val integrity = RecordingIntegrityTransactions()
            val rollbackEntered = CompletableDeferred<Unit>()
            val releaseRollback = CompletableDeferred<Unit>()
            val cleaner = cleaner(
                integrity = integrity,
                snapshotCareTasksForTank = { listOf(validTask(it)) },
                deleteTankRecords = { throw CancellationException("cancelled") },
                restoreCareTasksForTank = { _, _ ->
                    rollbackEntered.complete(Unit)
                    releaseRollback.await()
                }
            )
            val deletion = launch { cleaner.deleteTanks(listOf(7L)) }
            rollbackEntered.await()
            val writer = async(start = CoroutineStart.UNDISPATCHED) {
                OwnerTankMutationGate.shared.withTanks(OWNER_UID, listOf(7L)) {
                    assertEquals(listOf(7L), integrity.abortedTankIds)
                }
            }
            assertFalse(writer.isCompleted)
            releaseRollback.complete(Unit)
            deletion.join()
            assertTrue(deletion.isCancelled)
            writer.await()
        }
    }

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
    fun `successful deletion cancels every deleted task reminder for the same owner`() = runBlocking {
        val cancelled = mutableListOf<String>()
        val cleaner = cleaner(
            snapshotCareTasksForTank = { tankId ->
                listOf(
                    validTask(tankId = tankId).copy(id = 101L),
                    validTask(tankId = tankId).copy(id = 102L)
                )
            },
            cancelCareTaskReminder = { ownerUid, taskId ->
                cancelled += "$ownerUid:$taskId"
            }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
        assertEquals(
            listOf("$OWNER_UID:101", "$OWNER_UID:102"),
            cancelled
        )
    }

    @Test
    fun `tank deletion failure restores care snapshots and reconciles owner reminders`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        val restored = mutableListOf<CareTask>()
        val reconciledOwners = mutableListOf<String>()
        val primaryError = IllegalStateException("tank write failed")
        val cleaner = cleaner(
            integrity = integrity,
            snapshotCareTasksForTank = { tankId ->
                listOf(validTask(tankId = tankId))
            },
            deleteTankRecords = { throw primaryError },
            restoreCareTasksForTank = { _, tasks -> restored += tasks },
            reconcileCareReminders = { ownerUid -> reconciledOwners += ownerUid }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertEquals(
            primaryError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
        assertEquals(1, restored.size)
        assertEquals(listOf(OWNER_UID), reconciledOwners)
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
    fun `analysis deletion completes before journal and failure rolls back the tank transaction`() = runBlocking {
        val events = mutableListOf<String>()
        val integrity = RecordingIntegrityTransactions(events)
        val successfulCleaner = waterCleaner(
            integrity = integrity,
            deleteWaterAnalysesForTank = { tankId -> events += "analysis:$tankId" }
        )

        val result = successfulCleaner.deleteTanks(listOf(7L))
        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
        assertTrue(events.indexOf("analysis:7") < events.indexOf("complete:7"))

        val failureIntegrity = RecordingIntegrityTransactions()
        val failed = waterCleaner(
            integrity = failureIntegrity,
            deleteWaterAnalysesForTank = { error("analysis store unavailable") }
        ).deleteTanks(listOf(8L)) as OwnerTankDataCleaner.Result.DeleteFailed

        assertEquals("analysis store unavailable", failed.error.message)
        assertTrue(failureIntegrity.completedTankIds.isEmpty())
        assertEquals(listOf(8L), failureIntegrity.abortedTankIds)
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

    @Test
    fun `water snapshot failure starts no destructive store operation`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        var destructiveCalls = 0
        val result = waterFailureCleaner(
            integrity = integrity,
            deleteCareTasksForTank = { destructiveCalls++ },
            deleteTankRecords = { destructiveCalls++ },
            waterIntegrity = RecordingWaterIntegrity(onPrepare = { error("staging full") })
        ).deleteTanks(listOf(7L))
        assertEquals("staging full", (result as OwnerTankDataCleaner.Result.DeleteFailed).error.message)
        assertEquals(0, destructiveCalls)
        assertEquals(listOf(7L), integrity.abortedTankIds)
    }

    @Test
    fun `water restoration completes before journal abort and staging cleanup`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        var restored = false
        var cleaned = false
        val result = waterFailureCleaner(
            integrity = integrity,
            deleteTankRecords = { error("tank commit failed") },
            waterIntegrity = RecordingWaterIntegrity(onRestore = {
                assertTrue(integrity.abortedTankIds.isEmpty())
                restored = true
            }, onFinish = {
                assertTrue(restored)
                assertEquals(listOf(7L), integrity.abortedTankIds)
                cleaned = true
            })
        ).deleteTanks(listOf(7L))
        assertTrue(result is OwnerTankDataCleaner.Result.DeleteFailed)
        assertTrue(restored && cleaned)
    }

    @Test
    fun `failed water restoration retains the journal and snapshot for recovery`() = runBlocking {
        val integrity = RecordingIntegrityTransactions()
        var cleaned = false
        val result = waterFailureCleaner(
            integrity = integrity,
            deleteTankRecords = { error("tank commit failed") },
            waterIntegrity = RecordingWaterIntegrity(onRestore = { error("rollback I/O") },
                onFinish = { cleaned = true })
        ).deleteTanks(listOf(7L)) as OwnerTankDataCleaner.Result.DeleteFailed
        assertEquals("tank commit failed", result.error.message)
        assertEquals("rollback I/O", result.error.suppressed.single().message)
        assertTrue(integrity.abortedTankIds.isEmpty())
        assertFalse(cleaned)
    }

    @Test
    fun `deletion waits for the complete owner archive transaction before capturing`() = runBlocking {
        withTimeout(5_000) {
            var captured = false
            val cleaner = cleaner(snapshotCareTasksForTank = { captured = true; emptyList() })
            val waiting = OwnerArchiveMutationGate.shared.withOwner(OWNER_UID) {
                async(start = CoroutineStart.UNDISPATCHED) { cleaner.deleteTanks(listOf(1L)) }
                    .also { assertFalse(captured) }
            }
            assertTrue(waiting.await() is OwnerTankDataCleaner.Result.Deleted)
            assertTrue(captured)
        }
    }

    @Test
    fun `unresolved restore rejects deletion before journal or snapshot mutation`() = runBlocking {
        val archive = InMemoryRestoreTransactions()
        archive.begin(OWNER_UID, emptySet())
        var touched = false
        val cleaner = waterFailureCleaner(RecordingIntegrityTransactions(), RecordingWaterIntegrity(),
            deleteCareTasksForTank = { touched = true }, archiveGuard = archive::requireNoActiveRestore)
        assertTrue(cleaner.deleteTanks(listOf(1L)) is OwnerTankDataCleaner.Result.DeleteFailed)
        assertFalse(touched)
        archive.markCommitted(OWNER_UID)
        assertTrue(cleaner.deleteTanks(listOf(1L)) is OwnerTankDataCleaner.Result.Deleted)
    }

    private fun cleaner(
        integrity: RecordingIntegrityTransactions = RecordingIntegrityTransactions(),
        deleteTankRecords: suspend (List<Long>) -> Unit = {},
        snapshotCareTasksForTank: suspend (Long) -> List<CareTask> = { emptyList() },
        deleteCareTasksForTank: suspend (Long) -> Unit = {},
        restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit = { _, _ -> },
        removeAssignmentsForTank: suspend (Long) -> TankAssignmentCleanupResult = {
            TankAssignmentCleanupResult.Completed(0)
        },
        cancelCareTaskReminder: suspend (String, Long) -> Unit = { _, _ -> },
        reconcileCareReminders: suspend (String) -> Unit = {}
    ): OwnerTankDataCleaner {
        return OwnerTankDataCleaner(
            stores = OwnerTankDeletionStores(
                    requireArchiveSettled = {},
                deleteTankRecords = deleteTankRecords,
                snapshotCareTasksForTank = snapshotCareTasksForTank,
                deleteCareTasksForTank = deleteCareTasksForTank,
                restoreCareTasksForTank = restoreCareTasksForTank,
                deleteWaterAnalysesForTank = {},
                waterIntegrity = RecordingWaterIntegrity()
            ),
            removeDeviceAssignmentsForTank = removeAssignmentsForTank,
            cancelCareTaskReminder = cancelCareTaskReminder,
            reconcileCareReminders = reconcileCareReminders,
            integrityTransactions = integrity,
            ownerUidProvider = { OWNER_UID }
        )
    }

    private fun waterFailureCleaner(
        integrity: RecordingIntegrityTransactions,
        waterIntegrity: WaterAnalysisDeletionIntegrity,
        deleteCareTasksForTank: suspend (Long) -> Unit = {},
        deleteTankRecords: suspend (List<Long>) -> Unit = {},
        archiveGuard: suspend (String) -> Unit = {}
    ) = OwnerTankDataCleaner(
        stores = OwnerTankDeletionStores(deleteTankRecords, { emptyList() }, deleteCareTasksForTank,
            { _, _ -> }, {}, waterIntegrity, archiveGuard),
        removeDeviceAssignmentsForTank = { TankAssignmentCleanupResult.Completed(0) },
        cancelCareTaskReminder = { _, _ -> },
        reconcileCareReminders = {},
        integrityTransactions = integrity,
        ownerUidProvider = { OWNER_UID }
    )

    private fun waterCleaner(
        integrity: RecordingIntegrityTransactions,
        deleteWaterAnalysesForTank: suspend (Long) -> Unit
    ) = OwnerTankDataCleaner(
        stores = OwnerTankDeletionStores(
                    requireArchiveSettled = {},
            deleteTankRecords = {},
            snapshotCareTasksForTank = { emptyList() },
            deleteCareTasksForTank = {},
            restoreCareTasksForTank = { _, _ -> },
            deleteWaterAnalysesForTank = deleteWaterAnalysesForTank,
            waterIntegrity = RecordingWaterIntegrity()
        ),
        removeDeviceAssignmentsForTank = { TankAssignmentCleanupResult.Completed(0) },
        cancelCareTaskReminder = { _, _ -> },
        reconcileCareReminders = {},
        integrityTransactions = integrity,
        ownerUidProvider = { OWNER_UID }
    )

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
            snapshotsByTank: Map<Long, List<CareTask>>,
            waterTransactionsByTank: Map<Long, String>
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

private class RecordingWaterIntegrity(
    private val onPrepare: () -> Unit = {},
    private val onRestore: () -> Unit = {},
    private val onFinish: () -> Unit = {}
) : WaterAnalysisDeletionIntegrity {
    override suspend fun prepare(tankId: Long): String {
        onPrepare()
        return java.util.UUID.randomUUID().toString()
    }
    override suspend fun restore(tankId: Long, transactionId: String) = onRestore()
    override suspend fun finish(tankId: Long, transactionId: String?) = onFinish()
}
