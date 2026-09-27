package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.user.UserDataScope
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthHistoryRestoreRecoveryTest {
    @Test
    fun archiveCoordinatorUsesTheSameJournalAndCompensatesHealthBeforeRemovingTanks() = runBlocking {
        lateinit var harness: RestoreHarness
        var importedTransaction: String? = null
        var rolledBack = false
        val health = UserDataRestoreDataSources.HealthHistoryDataSource(restore = { request ->
            assertEquals(RestoreFixture.OWNER_UID, request.ownerUid)
            assertEquals(harness.transactions.pending(request.ownerUid)?.waterTransactionId, request.transactionId)
            assertEquals(setOf(harness.tanks.single().id), request.tankIdMap.values.toSet())
            assertTrue(request.file.isFile)
            importedTransaction = request.transactionId
            1
        }, rollback = { owner, transaction ->
            assertEquals(RestoreFixture.OWNER_UID, owner)
            assertEquals(importedTransaction, transaction)
            assertEquals(1, harness.tanks.size)
            rolledBack = true
        })
        harness = RestoreHarness(healthOverride = health)
        harness.assignmentBehavior = { _, _ -> TankDeviceAssignmentResult.Failure(IllegalStateException("injected")) }
        UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
            val backup = backup()
            assertTrue(runCatching { harness.restorer().restore(backup) }.isFailure)
            assertTrue(rolledBack)
            assertTrue(harness.tanks.isEmpty())
            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            harness.assignmentBehavior = null
            assertEquals(1, harness.restorer().restore(backup).restoredHealthObservationCount)
            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    private fun backup(): DecodedUserDataBackup {
        val file = File(Files.createTempDirectory("health-recovery").toFile(), "health.bin")
        val reference = HealthHistoryArchive.write(sequenceOf(HealthArchiveTestRows.row()), 1, file, emptyList())
        val base = RestoreFixture.backup(assignments = listOf(RestoreFixture.archiveAssignment("test-device")))
        return base.copy(manifest = base.manifest.copy(healthHistory = reference), healthHistoryFile = file)
    }
}
