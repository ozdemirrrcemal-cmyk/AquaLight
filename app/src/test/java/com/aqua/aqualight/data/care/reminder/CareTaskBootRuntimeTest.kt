package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareTaskBootRuntimeTest {

    @Test
    fun authenticatedBootRestoresOnlyOwnerScopedMaintenanceWithoutDeviceRuntime() = runBlocking {
        var activeOwnerUid: String? = "owner-a"
        val maintenanceOwners = mutableListOf<String>()
        val loadedOwners = mutableListOf<String>()
        val scheduledTasks = mutableListOf<CareTask>()
        val runtime = CareTaskBootRuntime(
            currentOwnerUid = { activeOwnerUid },
            startOwnerMaintenance = { ownerUid -> maintenanceOwners += ownerUid },
            loadPendingTasks = { ownerUid ->
                loadedOwners += ownerUid
                listOf(task(id = 1L, ownerUid = ""), task(id = 2L, ownerUid = ownerUid))
            },
            scheduleReminder = { task -> scheduledTasks += task }
        )

        runtime.restore()

        assertEquals(listOf("owner-a"), maintenanceOwners)
        assertEquals(listOf("owner-a"), loadedOwners)
        assertEquals(listOf(1L, 2L), scheduledTasks.map(CareTask::id))
        assertTrue(scheduledTasks.all { task -> task.ownerUid == "owner-a" })
    }

    @Test
    fun accountSwitchDuringBootDropsOldOwnerReminders() = runBlocking {
        var activeOwnerUid: String? = "owner-a"
        val maintenanceOwners = mutableListOf<String>()
        val scheduledTasks = mutableListOf<CareTask>()
        val runtime = CareTaskBootRuntime(
            currentOwnerUid = { activeOwnerUid },
            startOwnerMaintenance = { ownerUid -> maintenanceOwners += ownerUid },
            loadPendingTasks = {
                activeOwnerUid = "owner-b"
                listOf(task(id = 3L, ownerUid = "owner-a"))
            },
            scheduleReminder = { task -> scheduledTasks += task }
        )

        runtime.restore()

        assertEquals(listOf("owner-a"), maintenanceOwners)
        assertTrue(scheduledTasks.isEmpty())
    }

    @Test
    fun unauthenticatedBootDoesNotStartMaintenanceOrReadStores() = runBlocking {
        var loadCount = 0
        val maintenanceOwners = mutableListOf<String>()
        val scheduledTasks = mutableListOf<CareTask>()
        val runtime = CareTaskBootRuntime(
            currentOwnerUid = { null },
            startOwnerMaintenance = { ownerUid -> maintenanceOwners += ownerUid },
            loadPendingTasks = {
                loadCount += 1
                emptyList()
            },
            scheduleReminder = { task -> scheduledTasks += task }
        )

        runtime.restore()

        assertTrue(maintenanceOwners.isEmpty())
        assertEquals(0, loadCount)
        assertTrue(scheduledTasks.isEmpty())
    }

    private fun task(
        id: Long,
        ownerUid: String
    ): CareTask {
        return CareTask(
            id = id,
            ownerUid = ownerUid,
            tankId = 1L,
            title = "Task $id",
            description = "",
            type = CareTaskType.CUSTOM,
            source = CareTaskSource.MANUAL,
            status = CareTaskStatus.PENDING,
            dueAtMillis = 10_000L,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = 0,
            reminderEnabled = true,
            missedReminderEnabled = false,
            missedReminderDays = 0,
            waterChangePercent = null,
            note = "",
            generatedRuleKey = "",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }
}
