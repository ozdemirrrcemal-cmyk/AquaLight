package com.aqua.aqualight.data.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrphanedCareTaskRepairInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sessionRepairRemovesTasksWhoseAuthoritativeTankWasDeleted() = runBlocking {
        val ownerUid = "orphan-repair-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val careStore = CareTaskDataStoreManager.create(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                val tankId = tankStore.addTankFromDraft(validTankDraft())
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

                assertEquals(1, careStore.tasksFlow.first().size)

                // Simulate process interruption after the authoritative tank write
                // but before dependent care-task cleanup.
                tankStore.deleteTanks(listOf(tankId))
                assertTrue(tankStore.tanksFlow.first().isEmpty())
                assertEquals(1, careStore.tasksFlow.first().size)

                assertEquals(
                    1,
                    careStore.repairOrphanedTankTasks(ownerUid)
                )
                assertTrue(careStore.tasksFlow.first().isEmpty())
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                careStore.clearAllTasks(ownerUid = ownerUid)
                tankStore.clearAllTanks(ownerUid)
            }
        }
    }

    private fun validTankDraft(): TankDraft = TankDraft(
        name = "Repair Tank",
        description = "",
        photoUri = null,
        plants = emptyList(),
        materials = emptyList(),
        setupDateMillis = CREATED_MILLIS,
        widthCm = 60,
        lengthCm = 40,
        heightCm = 40,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Planted",
        tankStyle = ""
    )

    private companion object {
        const val CREATED_MILLIS = 1_767_225_600_000L
        const val DUE_MILLIS = 1_767_312_000_000L
    }
}
