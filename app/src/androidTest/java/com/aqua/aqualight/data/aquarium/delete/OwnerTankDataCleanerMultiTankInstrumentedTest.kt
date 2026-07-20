package com.aqua.aqualight.data.aquarium.delete

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
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
    fun twoTanksWithCareTasksAreDeletedThroughOneCrashSafeOperation() = runBlocking {
        val ownerUid = "bulk-delete-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        TankCareIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                val firstTankId = tankStore.addTankFromDraft(validTankDraft("First Tank"))
                val secondTankId = tankStore.addTankFromDraft(validTankDraft("Second Tank"))
                addTask(careStore, firstTankId, "Inspect first filter")
                addTask(careStore, secondTankId, "Inspect second filter")

                val cleaner = OwnerTankDataCleaner(
                    deleteTankRecords = tankStore::deleteTanks,
                    snapshotCareTasksForTank = { tankId ->
                        careStore.snapshotTasksForIntegrity(tankId)
                    },
                    deleteCareTasksForTank = careStore::deleteTasksForTank,
                    restoreCareTasksForTank = { tankId, snapshots ->
                        careStore.restoreTaskSnapshotsForIntegrity(
                            tankId = tankId,
                            snapshots = snapshots
                        )
                    },
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
                assertTrue(TankCareIntegrityJournal.pendingForOwner(ownerUid).isEmpty())
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                careStore.clearAllTasks(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankCareIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

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
        const val SETUP_EPOCH_DAY = 20_454L
        const val DUE_MILLIS = 1_767_312_000_000L
    }
}
