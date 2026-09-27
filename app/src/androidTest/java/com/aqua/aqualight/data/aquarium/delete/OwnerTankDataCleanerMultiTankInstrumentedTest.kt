package com.aqua.aqualight.data.aquarium.delete

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDataStoreManager
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDraftRecord
import com.aqua.aqualight.data.aquarium.health.WaterMeasurementRecord
import com.aqua.aqualight.data.auth.OwnerSessionTestFixture
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityRecovery
import com.aqua.aqualight.data.care.integrity.restoreTaskSnapshotsForIntegrity
import com.aqua.aqualight.data.care.integrity.snapshotTasksForIntegrity
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerTankDataCleanerMultiTankInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun queuedAnalysisRechecksTankAfterDeletionReleasesGate() = runBlocking {
        withTimeout(GATE_TIMEOUT_MILLIS) {
            withIsolatedTank { fixture ->
                coroutineScope {
                    val queued = OwnerTankMutationGate.shared.withTanks(fixture.owner, listOf(fixture.tankId)) {
                        val write = async(start = CoroutineStart.UNDISPATCHED) {
                            runCatching { fixture.analyses.addAnalysis(validAnalysis(fixture.tankId), fixture.session) }
                        }
                        assertFalse(write.isCompleted)
                        fixture.tanks.deleteTanks(listOf(fixture.tankId))
                        write
                    }
                    assertTrue(queued.await().exceptionOrNull() is StoreInvariantViolation)
                    assertTrue(fixture.analyses.analysesForOwnerFlow(fixture.owner).first().isEmpty())
                }
            }
        }
    }

    @Test
    fun recoveryRechecksTankAfterWaitingForLiveDeletionGate() = runBlocking {
        withTimeout(GATE_TIMEOUT_MILLIS) {
            withIsolatedTank { fixture ->
                addTask(fixture.care, fixture.tankId, "Pending cleanup")
                fixture.analyses.addAnalysis(validAnalysis(fixture.tankId), fixture.session)
                val snapshots = fixture.care.snapshotTasksForIntegrity(fixture.tankId)
                TankCareIntegrityJournal.begin(fixture.owner, listOf(fixture.tankId))
                TankCareIntegrityJournal.captureSnapshots(fixture.owner, mapOf(fixture.tankId to snapshots))
                coroutineScope {
                    val queued = OwnerTankMutationGate.shared.withTanks(fixture.owner, listOf(fixture.tankId)) {
                        val recovery = async(start = CoroutineStart.UNDISPATCHED) {
                            TankCareIntegrityRecovery.create(context).recover(fixture.owner)
                        }
                        assertFalse(recovery.isCompleted)
                        fixture.care.deleteTasksForTank(fixture.tankId)
                        fixture.tanks.deleteTanks(listOf(fixture.tankId))
                        recovery
                    }
                    assertEquals(0, queued.await().restoredTaskCount)
                    assertTrue(fixture.care.tasksForTankFlow(fixture.tankId).first().isEmpty())
                    assertTrue(fixture.analyses.analysesForOwnerFlow(fixture.owner).first().isEmpty())
                    assertTrue(TankCareIntegrityJournal.pendingForOwner(fixture.owner).isEmpty())
                }
            }
        }
    }

    private suspend fun withIsolatedTank(block: suspend (GateFixture) -> Unit) {
        val owner = "tank-gate-${UUID.randomUUID()}"
        val tanks = AquariumTankDataStoreManager(context)
        val analyses = WaterAnalysisDataStoreManager(context)
        val care = CareTaskDataStoreManager.create(context)
        UserDataScope.withOwnerUid(owner) {
            try {
                val tankId = tanks.addTankFromDraft(validTankDraft("Gate Tank"))
                block(GateFixture(owner, tankId, tanks, analyses, care))
            } finally {
                withContext(NonCancellable) {
                    care.clearAllTasks(owner)
                    analyses.clearAllAnalyses(owner)
                    tanks.clearAllTanks(owner)
                    TankCareIntegrityJournal.clearOwner(owner)
                }
            }
        }
    }

    private data class GateFixture(
        val owner: String,
        val tankId: Long,
        val tanks: AquariumTankDataStoreManager,
        val analyses: WaterAnalysisDataStoreManager,
        val care: CareTaskDataStoreManager
    ) {
        val session = OwnerSessionTestFixture(owner).lease
    }

    @Test
    fun failedTankWriteRestoresExactAnalysisAndCareSnapshots() = runBlocking {
        withIsolatedTank { fixture ->
            addTask(fixture.care, fixture.tankId, "Rollback filter")
            fixture.analyses.addAnalysis(validAnalysis(fixture.tankId), fixture.session)
            val original = fixture.analyses.analysesForOwnerFlow(fixture.owner).first()
            val care = fixture.care.tasksForTankFlow(fixture.tankId).first()
            val cleaner = OwnerTankDataCleaner(
                stores = OwnerTankDeletionStores(
                    deleteTankRecords = {
                        assertTrue(fixture.analyses.analysesForOwnerFlow(fixture.owner).first().isEmpty())
                        error("Injected tank commit failure")
                    },
                    snapshotCareTasksForTank = fixture.care::snapshotTasksForIntegrity,
                    deleteCareTasksForTank = fixture.care::deleteTasksForTank,
                    restoreCareTasksForTank = fixture.care::restoreTaskSnapshotsForIntegrity,
                    deleteWaterAnalysesForTank = fixture.analyses::deleteAnalysesForTank,
                    waterIntegrity = fixture.analyses.deletionIntegrity
                ),
                removeDeviceAssignmentsForTank = { error("No assignment cleanup before tank commit") },
                cancelCareTaskReminder = { _, _ -> },
                reconcileCareReminders = {}
            )
            assertTrue(cleaner.deleteTanks(listOf(fixture.tankId)) is OwnerTankDataCleaner.Result.DeleteFailed)
            assertEquals(original, fixture.analyses.analysesForOwnerFlow(fixture.owner).first())
            assertEquals(care, fixture.care.tasksForTankFlow(fixture.tankId).first())
            assertTrue(TankCareIntegrityJournal.pendingForOwner(fixture.owner).isEmpty())
        }
    }

    @Test
    fun interruptedDeletionRestoresHistoryWhenTankSurvivesAndDoesNotResurrectWhenTankIsGone() = runBlocking {
        withIsolatedTank { fixture ->
            fixture.analyses.addAnalysis(validAnalysis(fixture.tankId), fixture.session)
            val original = fixture.analyses.analysesForOwnerFlow(fixture.owner).first()
            prepareInterruptedDeletion(fixture)
            assertTrue(fixture.analyses.analysesForOwnerFlow(fixture.owner).first().isEmpty())
            TankCareIntegrityRecovery.create(context).recover(fixture.owner)
            assertEquals(original, fixture.analyses.analysesForOwnerFlow(fixture.owner).first())
            prepareInterruptedDeletion(fixture)
            fixture.tanks.deleteTanks(listOf(fixture.tankId))
            TankCareIntegrityRecovery.create(context).recover(fixture.owner)
            assertTrue(fixture.analyses.analysesForOwnerFlow(fixture.owner).first().isEmpty())
            assertTrue(TankCareIntegrityJournal.pendingForOwner(fixture.owner).isEmpty())
        }
    }

    private suspend fun prepareInterruptedDeletion(fixture: GateFixture) {
        OwnerTankMutationGate.shared.withTanks(fixture.owner, listOf(fixture.tankId)) {
            TankCareIntegrityJournal.begin(fixture.owner, listOf(fixture.tankId))
            val transaction = fixture.analyses.deletionIntegrity.prepare(fixture.tankId)
            TankCareIntegrityJournal.captureSnapshots(fixture.owner, mapOf(fixture.tankId to emptyList()),
                mapOf(fixture.tankId to transaction))
            fixture.analyses.deleteAnalysesForTank(fixture.tankId)
        }
    }

    @Test
    fun twoTanksWithCareTasksAreDeletedThroughOneCrashSafeOperation() = runBlocking {
        val ownerUid = "bulk-delete-${UUID.randomUUID()}"
        val session = OwnerSessionTestFixture(ownerUid).lease
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        val analysisStore = WaterAnalysisDataStoreManager(context)
        TankCareIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                val firstTankId = tankStore.addTankFromDraft(validTankDraft("First Tank"))
                val secondTankId = tankStore.addTankFromDraft(validTankDraft("Second Tank"))
                addTask(careStore, firstTankId, "Inspect first filter")
                addTask(careStore, secondTankId, "Inspect second filter")
                listOf(firstTankId, secondTankId).forEach { tankId ->
                    analysisStore.addAnalysis(validAnalysis(tankId), session)
                }

                val cleaner = OwnerTankDataCleaner(
                    stores = OwnerTankDeletionStores(
                        deleteTankRecords = tankStore::deleteTanks,
                        snapshotCareTasksForTank = { tankId ->
                            careStore.snapshotTasksForIntegrity(tankId)
                        },
                        deleteCareTasksForTank = careStore::deleteTasksForTank,
                        deleteWaterAnalysesForTank = analysisStore::deleteAnalysesForTank,
                        waterIntegrity = analysisStore.deletionIntegrity,
                        restoreCareTasksForTank = { tankId, snapshots ->
                            careStore.restoreTaskSnapshotsForIntegrity(
                                tankId = tankId,
                                snapshots = snapshots
                            )
                        }
                    ),
                    removeDeviceAssignmentsForTank = {
                        TankAssignmentCleanupResult.Completed(0)
                    },
                    cancelCareTaskReminder = { _, _ -> },
                    reconcileCareReminders = {}
                )

                val result = cleaner.deleteTanks(listOf(firstTankId, secondTankId))
                    as OwnerTankDataCleaner.Result.Deleted

                assertEquals(listOf(firstTankId, secondTankId), result.tankIds)
                assertFalse(result.hasCleanupIssues)
                assertTrue(tankStore.tanksSnapshotForOwner(ownerUid).isEmpty())
                assertTrue(careStore.tasksForTankFlow(firstTankId).first().isEmpty())
                assertTrue(careStore.tasksForTankFlow(secondTankId).first().isEmpty())
                assertTrue(analysisStore.analysesForTankFlow(ownerUid, firstTankId).first().isEmpty())
                assertTrue(analysisStore.analysesForTankFlow(ownerUid, secondTankId).first().isEmpty())
                assertTrue(TankCareIntegrityJournal.pendingForOwner(ownerUid).isEmpty())
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                careStore.clearAllTasks(ownerUid)
                analysisStore.clearAllAnalyses(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankCareIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    @Test
    fun failedAnalysisDeletionPreservesTankAndHistory() = runBlocking {
        val ownerUid = "analysis-recovery-${UUID.randomUUID()}"
        val session = OwnerSessionTestFixture(ownerUid).lease
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        val analysisStore = WaterAnalysisDataStoreManager(context)
        TankCareIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                val tankId = tankStore.addTankFromDraft(validTankDraft("Recovery Tank"))
                analysisStore.addAnalysis(validAnalysis(tankId), session)
                val cleaner = OwnerTankDataCleaner(
                    stores = OwnerTankDeletionStores(
                        deleteTankRecords = tankStore::deleteTanks,
                        snapshotCareTasksForTank = { id -> careStore.snapshotTasksForIntegrity(id) },
                        deleteCareTasksForTank = careStore::deleteTasksForTank,
                        restoreCareTasksForTank = { id, snapshots ->
                            careStore.restoreTaskSnapshotsForIntegrity(id, snapshots)
                        },
                        deleteWaterAnalysesForTank = { error("Injected analysis cleanup failure") },
                        waterIntegrity = analysisStore.deletionIntegrity
                    ),
                    removeDeviceAssignmentsForTank = {
                        TankAssignmentCleanupResult.Completed(0)
                    },
                    cancelCareTaskReminder = { _, _ -> },
                    reconcileCareReminders = {}
                )

                val result = cleaner.deleteTanks(listOf(tankId))
                assertTrue(result is OwnerTankDataCleaner.Result.DeleteFailed)
                assertEquals(1, analysisStore.analysesForTankFlow(ownerUid, tankId).first().size)
                assertTrue(tankStore.tanksSnapshotForOwner(ownerUid).any { it.id == tankId })
                assertTrue(TankCareIntegrityJournal.pendingForOwner(ownerUid).isEmpty())

                TankCareIntegrityRecovery.create(context).recover(ownerUid)

                assertEquals(1, analysisStore.analysesForTankFlow(ownerUid, tankId).first().size)
                assertTrue(TankCareIntegrityJournal.pendingForOwner(ownerUid).isEmpty())
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                analysisStore.clearAllAnalyses(ownerUid)
                careStore.clearAllTasks(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankCareIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    private fun validAnalysis(tankId: Long): WaterAnalysisDraftRecord =
        WaterAnalysisDraftRecord(
            tankId = tankId,
            measuredAtMillis = System.currentTimeMillis(),
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(
                WaterMeasurementRecord(
                    parameter = WaterParameter.PH,
                    value = 7.0,
                    method = WaterMeasurementMethod.MANUAL,
                    testKitId = null,
                    basis = WaterMeasurementBasis.PH,
                    unit = WaterMeasurementUnit.NONE
                )
            ),
            requestId = UUID.randomUUID().toString()
        )

    private suspend fun addTask(
        careStore: CareTaskDataStoreManager,
        tankId: Long,
        title: String
    ) {
        careStore.addManualTask(
            tankId = tankId,
            title = title,
            description = "",
            type = CareTaskType.FILTER_MAINTENANCE,
            dueAtMillis = DUE_MILLIS,
            repeatEnabled = false,
            repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
            reminderEnabled = false,
            missedReminderEnabled = false,
            missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
            waterChangePercent = null,
            note = ""
        )
    }

    private fun validTankDraft(name: String): TankDraft = TankDraft(
        name = name,
        description = "",
        photoUri = null,
        plants = emptyList(),
        materials = emptyList(),
        setupDateEpochDay = SETUP_EPOCH_DAY,
        widthCm = 60,
        lengthCm = 40,
        heightCm = 40,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Planted",
        tankStyle = ""
    )

    private companion object {
        const val GATE_TIMEOUT_MILLIS = 10_000L
        const val SETUP_EPOCH_DAY = 20_454L
        const val DUE_MILLIS = 1_767_312_000_000L
    }
}
