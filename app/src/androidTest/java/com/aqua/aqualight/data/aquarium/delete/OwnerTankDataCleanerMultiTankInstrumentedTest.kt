package com.aqua.aqualight.data.aquarium.delete

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.care.integrity.restoreTaskSnapshotsForIntegrity
import com.aqua.aqualight.data.care.integrity.snapshotTasksForIntegrity
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerTankDataCleanerMultiTankInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun twoTanksWithDependentRecordsAreDeletedThroughOneCrashSafeOperation() = runBlocking {
        val ownerUid = "bulk-delete-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        val healthStore = AquariumHealthDataStoreManager.create(context)
        TankCareIntegrityJournal.initialize(context)
        TankHealthIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                TankHealthIntegrityJournal.clearOwner(ownerUid)

                val firstTankId =
                    tankStore.addTankFromDraft(validTankDraft("First Tank"))
                val secondTankId =
                    tankStore.addTankFromDraft(validTankDraft("Second Tank"))

                addTask(careStore, firstTankId, "Inspect first filter")
                addTask(careStore, secondTankId, "Inspect second filter")
                addWaterTest(healthStore, ownerUid, firstTankId)
                addWaterTest(healthStore, ownerUid, secondTankId)

                val cleaner = createCleaner(
                    tankStore = tankStore,
                    careStore = careStore,
                    healthStore = healthStore
                )

                val result = cleaner
                    .deleteTanks(listOf(firstTankId, secondTankId))
                    as OwnerTankDataCleaner.Result.Deleted

                assertDeletedState(
                    DeletedStateFixture(
                        ownerUid = ownerUid,
                        tankIds = listOf(firstTankId, secondTankId),
                        result = result,
                        tankStore = tankStore,
                        careStore = careStore,
                        healthStore = healthStore
                    )
                )
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                careStore.clearAllTasks(ownerUid)
                healthStore.integrity.clearAllRecords(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankCareIntegrityJournal.clearOwner(ownerUid)
                TankHealthIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    private fun createCleaner(
        tankStore: AquariumTankDataStoreManager,
        careStore: CareTaskDataStoreManager,
        healthStore: AquariumHealthDataStoreManager
    ): OwnerTankDataCleaner = OwnerTankDataCleaner(
        OwnerTankDeletionDependencies(
            deleteTankRecords = tankStore::deleteTanks,
            care = TankCareDeletionDependencies(
                snapshotForTank = { tankId ->
                    careStore.snapshotTasksForIntegrity(tankId)
                },
                deleteForTank = careStore::deleteTasksForTank,
                restoreForTank = { tankId, snapshots ->
                    careStore.restoreTaskSnapshotsForIntegrity(
                        tankId = tankId,
                        snapshots = snapshots
                    )
                },
                cancelCareTaskReminder = { _, _ -> },
                reconcileReminders = {}
            ),
            health = TankHealthDeletionDependencies(
                snapshotForTank = healthStore.integrity::snapshotForTank,
                deleteForTank = healthStore.integrity::deleteRecordsForTank,
                restoreForTank =
                    healthStore.integrity::restoreSnapshotForIntegrity
            ),
            removeDeviceAssignmentsForTank = {
                TankAssignmentCleanupResult.Completed(0)
            }
        )
    )

    private suspend fun assertDeletedState(
        fixture: DeletedStateFixture
    ) {
        assertEquals(fixture.tankIds, fixture.result.tankIds)
        assertFalse(fixture.result.hasCleanupIssues)
        assertTrue(
            fixture.tankStore
                .tanksSnapshotForOwner(fixture.ownerUid)
                .isEmpty()
        )
        fixture.tankIds.forEach { tankId ->
            assertTrue(
                fixture.careStore
                    .tasksForTankFlow(tankId)
                    .first()
                    .isEmpty()
            )
            assertTrue(
                fixture.healthStore.waterTests
                    .observe(fixture.ownerUid, tankId)
                    .first()
                    .isEmpty()
            )
        }
        assertTrue(
            TankCareIntegrityJournal
                .pendingForOwner(fixture.ownerUid)
                .isEmpty()
        )
        assertTrue(
            TankHealthIntegrityJournal
                .pendingForOwner(fixture.ownerUid)
                .isEmpty()
        )
    }

    private data class DeletedStateFixture(
        val ownerUid: String,
        val tankIds: List<Long>,
        val result: OwnerTankDataCleaner.Result.Deleted,
        val tankStore: AquariumTankDataStoreManager,
        val careStore: CareTaskDataStoreManager,
        val healthStore: AquariumHealthDataStoreManager
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

    private suspend fun addWaterTest(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        tankId: Long
    ) {
        healthStore.waterTests.add(
            ownerUid = ownerUid,
            input = AquariumWaterTestInput(
                tankId = tankId,
                measuredAtMillis = HEALTH_MEASURED_MILLIS,
                readings = listOf(
                    AquariumWaterReading(
                        parameter = HealthWaterParameter.PH,
                        value = 7.0
                    )
                )
            ),
            nowMillis = HEALTH_CREATED_MILLIS
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
        const val SETUP_EPOCH_DAY = 20_454L
        const val DUE_MILLIS = 1_767_312_000_000L
        const val HEALTH_MEASURED_MILLIS = 1_767_225_600_000L
        const val HEALTH_CREATED_MILLIS = 1_767_229_200_000L
    }
}
