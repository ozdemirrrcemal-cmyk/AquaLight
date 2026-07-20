package com.aqua.aqualight.data.care.integrity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankCareIntegrityMultiTankWriteInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun multiTankDeletionAllowsUntouchedSnapshotsAndAlreadyDeletedTaskSets() = runBlocking {
        val ownerUid = "care-multi-delete-${UUID.randomUUID()}"
        val firstTankId = 701L
        val secondTankId = 702L
        val firstTask = validTask(ownerUid, firstTankId, 1001L)
        val secondTask = validTask(ownerUid, secondTankId, 1002L)
        TankCareIntegrityJournal.initialize(context)

        try {
            TankCareIntegrityJournal.clearOwner(ownerUid)
            TankCareIntegrityJournal.begin(ownerUid, listOf(firstTankId, secondTankId))
            TankCareIntegrityJournal.captureSnapshots(
                ownerUid = ownerUid,
                snapshotsByTank = mapOf(
                    firstTankId to listOf(firstTask),
                    secondTankId to listOf(secondTask)
                )
            )

            val afterFirstTankCareDelete = CareTaskStoreRules.defaultStore()
                .toBuilder()
                .addTasks(secondTask.toStoredTask())
                .build()
            val firstOutput = ByteArrayOutputStream()

            CareTasksCommercialSerializer.writeTo(
                afterFirstTankCareDelete,
                firstOutput
            )

            assertTrue(firstOutput.size() > 0)

            val afterAllCareDeletes = CareTaskStoreRules.defaultStore()
            val secondOutput = ByteArrayOutputStream()

            CareTasksCommercialSerializer.writeTo(
                afterAllCareDeletes,
                secondOutput
            )

            assertTrue(secondOutput.size() > 0)
        } finally {
            TankCareIntegrityJournal.clearOwner(ownerUid)
        }
    }

    @Test
    fun multiTankDeletionRejectsPartialOrModifiedBlockedTaskState() = runBlocking {
        val ownerUid = "care-multi-reject-${UUID.randomUUID()}"
        val firstTankId = 711L
        val secondTankId = 712L
        val firstTask = validTask(ownerUid, firstTankId, 1101L)
        val secondTask = validTask(ownerUid, secondTankId, 1102L)
        val secondCompanionTask = validTask(ownerUid, secondTankId, 1103L)
        TankCareIntegrityJournal.initialize(context)

        try {
            TankCareIntegrityJournal.clearOwner(ownerUid)
            TankCareIntegrityJournal.begin(ownerUid, listOf(firstTankId, secondTankId))
            TankCareIntegrityJournal.captureSnapshots(
                ownerUid = ownerUid,
                snapshotsByTank = mapOf(
                    firstTankId to listOf(firstTask),
                    secondTankId to listOf(secondTask, secondCompanionTask)
                )
            )

            val partialSecondTankState = CareTaskStoreRules.defaultStore()
                .toBuilder()
                .addTasks(secondTask.toStoredTask())
                .build()
            val partialFailure = runCatching {
                CareTasksCommercialSerializer.writeTo(
                    partialSecondTankState,
                    ByteArrayOutputStream()
                )
            }.exceptionOrNull()

            assertTrue(partialFailure is StoreInvariantViolation)

            val modifiedSecondTankState = CareTaskStoreRules.defaultStore()
                .toBuilder()
                .addTasks(secondTask.copy(title = "Unexpected mutation").toStoredTask())
                .addTasks(secondCompanionTask.toStoredTask())
                .build()
            val modifiedFailure = runCatching {
                CareTasksCommercialSerializer.writeTo(
                    modifiedSecondTankState,
                    ByteArrayOutputStream()
                )
            }.exceptionOrNull()

            assertTrue(modifiedFailure is StoreInvariantViolation)
        } finally {
            TankCareIntegrityJournal.clearOwner(ownerUid)
        }
    }

    private fun validTask(
        ownerUid: String,
        tankId: Long,
        taskId: Long
    ): CareTask = CareTask(
        id = taskId,
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
        const val CREATED_MILLIS = 1_767_225_600_000L
        const val DUE_MILLIS = 1_767_312_000_000L
    }
}
