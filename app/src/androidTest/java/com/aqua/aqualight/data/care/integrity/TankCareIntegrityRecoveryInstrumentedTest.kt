package com.aqua.aqualight.data.care.integrity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankCareIntegrityRecoveryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun processDeathBeforeTankDeleteRestoresCapturedTasks() = runBlocking {
        val ownerUid = "care-rollback-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        TankCareIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                val tankId = tankStore.addTankFromDraft(validTankDraft("Rollback Tank"))
                addTask(careStore, tankId)
                val snapshots = careStore.snapshotTasksForIntegrity(tankId)

                TankCareIntegrityJournal.begin(ownerUid, listOf(tankId))
                TankCareIntegrityJournal.captureSnapshots(
                    ownerUid = ownerUid,
                    snapshotsByTank = mapOf(tankId to snapshots)
                )
                careStore.deleteTasksForTank(tankId)
                assertTrue(careStore.tasksForTankFlow(tankId).first().isEmpty())

                val recovery = TankCareIntegrityRecovery.create(context).recover(ownerUid)

                assertEquals(1, recovery.restoredTaskCount)
                assertEquals(1, recovery.recoveredTransactionCount)
                assertEquals(snapshots, careStore.tasksForTankFlow(tankId).first())
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

    @Test
    fun processDeathAfterTankDeleteFinishesCleanupWithoutRestoringTasks() = runBlocking {
        val ownerUid = "care-complete-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)
        TankCareIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                val tankId = tankStore.addTankFromDraft(validTankDraft("Delete Tank"))
                addTask(careStore, tankId)
                val snapshots = careStore.snapshotTasksForIntegrity(tankId)

                TankCareIntegrityJournal.begin(ownerUid, listOf(tankId))
                TankCareIntegrityJournal.captureSnapshots(
                    ownerUid = ownerUid,
                    snapshotsByTank = mapOf(tankId to snapshots)
                )
                careStore.deleteTasksForTank(tankId)
                tankStore.deleteTanks(listOf(tankId))

                val recovery = TankCareIntegrityRecovery.create(context).recover(ownerUid)

                assertEquals(0, recovery.restoredTaskCount)
                assertEquals(1, recovery.recoveredTransactionCount)
                assertTrue(careStore.tasksForTankFlow(tankId).first().isEmpty())
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

    @Test
    fun completedDeletionRejectsAStaleCareWriterForTheRestOfTheProcess() {
        runBlocking {
            val ownerUid = "care-stale-${UUID.randomUUID()}"
            val tankId = 700L
            val task = validDomainTask(ownerUid, tankId)
            TankCareIntegrityJournal.initialize(context)

            try {
                TankCareIntegrityJournal.clearOwner(ownerUid)
                TankCareIntegrityJournal.begin(ownerUid, listOf(tankId))
                TankCareIntegrityJournal.captureSnapshots(
                    ownerUid = ownerUid,
                    snapshotsByTank = mapOf(tankId to listOf(task))
                )
                TankCareIntegrityJournal.complete(ownerUid, tankId)

                val store = CareTaskStoreRules.defaultStore().toBuilder()
                    .addTasks(task.toStoredTask())
                    .build()

                assertThrows(StoreInvariantViolation::class.java) {
                    runBlocking {
                        CareTasksCommercialSerializer.writeTo(
                            store,
                            ByteArrayOutputStream()
                        )
                    }
                }
            } finally {
                TankCareIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    private suspend fun addTask(
        careStore: CareTaskDataStoreManager,
        tankId: Long
    ) {
        careStore.addManualTask(
            tankId = tankId,
            title = "Inspect filter",
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

    private fun validDomainTask(ownerUid: String, tankId: Long): CareTask = CareTask(
        id = 900L,
        ownerUid = ownerUid,
        tankId = tankId,
        title = "Inspect filter",
        description = "",
        type = CareTaskType.FILTER_MAINTENANCE,
        source = CareTaskSource.MANUAL,
        status = CareTaskStatus.PENDING,
        dueAtMillis = DUE_MILLIS,
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
        waterChangePercent = null,
        note = "",
        generatedRuleKey = "",
        createdAtMillis = CREATED_MILLIS,
        updatedAtMillis = CREATED_MILLIS
    )

    private fun CareTask.toStoredTask(): StoredCareTask = StoredCareTask.newBuilder()
        .setId(id)
        .setOwnerUid(ownerUid)
        .setTankId(tankId)
        .setTitle(title)
        .setDescription(description)
        .setType(type.name)
        .setSource(source.name)
        .setStatus(status.name)
        .setDueAtMillis(dueAtMillis)
        .setCompletedAtMillis(completedAtMillis ?: 0L)
        .setRepeatEnabled(repeatEnabled)
        .setRepeatIntervalDays(repeatIntervalDays)
        .setReminderEnabled(reminderEnabled)
        .setMissedReminderEnabled(missedReminderEnabled)
        .setMissedReminderDays(missedReminderDays)
        .setWaterChangePercent(waterChangePercent ?: 0)
        .setNote(note)
        .setGeneratedRuleKey(generatedRuleKey)
        .setCreatedAtMillis(createdAtMillis)
        .setUpdatedAtMillis(updatedAtMillis)
        .build()

    private companion object {
        const val SETUP_EPOCH_DAY = 20_454L
        const val CREATED_MILLIS = 1_767_225_600_000L
        const val DUE_MILLIS = 1_767_312_000_000L
    }
}
