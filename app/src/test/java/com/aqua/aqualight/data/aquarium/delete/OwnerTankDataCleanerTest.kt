package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTankDataCleanerTest {

    @Test
    fun `invalid and duplicate ids are normalized before primary deletion`() = runBlocking {
        var deletedIds: List<Long> = emptyList()
        val cleaner = cleaner(
            deleteTankRecords = { ids -> deletedIds = ids }
        )

        val result = cleaner.deleteTanks(listOf(-1L, 7L, 7L, 8L, 0L))

        assertEquals(listOf(7L, 8L), deletedIds)
        assertTrue(result is OwnerTankDataCleaner.Result.Deleted)
    }

    @Test
    fun `primary deletion failure does not run dependent cleanup`() = runBlocking {
        var cleanupCalls = 0
        val primaryError = IllegalStateException("tank write failed")
        val cleaner = cleaner(
            deleteTankRecords = { throw primaryError },
            deleteCareTasksForTank = { cleanupCalls += 1 },
            removeAssignmentsForTank = {
                cleanupCalls += 1
                TankAssignmentCleanupResult.Completed(0)
            }
        )

        val result = cleaner.deleteTanks(listOf(7L))

        assertEquals(0, cleanupCalls)
        assertEquals(
            primaryError,
            (result as OwnerTankDataCleaner.Result.DeleteFailed).error
        )
    }

    @Test
    fun `cleanup failure is reported after authoritative deletion`() = runBlocking {
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
    }

    @Test
    fun `all dependent cleanups are attempted after individual failures`() = runBlocking {
        val careCalls = mutableListOf<Long>()
        val assignmentCalls = mutableListOf<Long>()
        val cleaner = cleaner(
            deleteCareTasksForTank = { tankId ->
                careCalls += tankId
                if (tankId == 7L) throw IllegalStateException("care cleanup failed")
            },
            removeAssignmentsForTank = { tankId ->
                assignmentCalls += tankId
                TankAssignmentCleanupResult.Completed(1)
            }
        )

        val result = cleaner.deleteTanks(listOf(7L, 8L))
            as OwnerTankDataCleaner.Result.Deleted

        assertEquals(listOf(7L, 8L), careCalls)
        assertEquals(listOf(7L, 8L), assignmentCalls)
        assertEquals(1, result.cleanupIssues.size)
        assertFalse(result.cleanupIssues.any { issue ->
            issue.stage == OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS
        })
    }

    @Test
    fun `cancellation is never converted into a deletion result`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                cleaner(
                    deleteCareTasksForTank = {
                        throw CancellationException("screen closed")
                    }
                ).deleteTanks(listOf(7L))
            }
        }
    }

    private fun cleaner(
        deleteTankRecords: suspend (List<Long>) -> Unit = {},
        deleteCareTasksForTank: suspend (Long) -> Unit = {},
        removeAssignmentsForTank: suspend (Long) -> TankAssignmentCleanupResult = {
            TankAssignmentCleanupResult.Completed(0)
        }
    ): OwnerTankDataCleaner {
        return OwnerTankDataCleaner(
            deleteTankRecords = deleteTankRecords,
            deleteCareTasksForTank = deleteCareTasksForTank,
            removeDeviceAssignmentsForTank = removeAssignmentsForTank
        )
    }
}
