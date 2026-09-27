package com.aqua.aqualight.data.user.archive

import androidx.datastore.core.DataStoreFactory
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.health.StoredWaterAnalysis
import com.aqua.aqualight.data.aquarium.health.StoredWaterMeasurement
import com.aqua.aqualight.data.aquarium.health.WaterAnalysesSerializer
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisArchiveStore
import com.aqua.aqualight.data.aquarium.health.WaterHistoryRestoreRequest
import com.aqua.aqualight.data.user.UserDataScope
import java.io.File
import java.nio.file.Files
import com.aqua.aqualight.data.aquarium.OwnerArchiveMutationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterHistoryRestoreRecoveryTest {
    @Test
    fun `backup decode restore and repeated restore preserve history while failure rolls it back`() = runBlocking {
        val root = Files.createTempDirectory("water-restore").toFile()
        val job = SupervisorJob()
        val store = DataStoreFactory.create(WaterAnalysesSerializer,
            scope = CoroutineScope(Dispatchers.IO + job)) { File(root, "live.pb") }
        lateinit var harness: RestoreHarness
        val archive = WaterAnalysisArchiveStore(store) { harness.tanks.map { it.id }.toSet() }
        harness = RestoreHarness(waterOverride = UserDataRestoreDataSources.WaterHistoryDataSource(
            restore = archive::restore, rollback = archive::rollback))
        try {
            UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
                val backup = backup(root)
                val first = harness.restorer().restore(backup)
                assertEquals(1, first.restoredWaterAnalysisCount)
                val saved = store.data.first().analysesList.single()
                assertEquals(harness.tanks.single().id, saved.tankId)
                assertEquals(TIME, saved.measuredAtMillis)
                assertEquals(0, harness.restorer().restore(backup).restoredWaterAnalysisCount)
                assertEquals(saved, store.data.first().analysesList.single())
                harness.assignmentBehavior = { _, _ ->
                    TankDeviceAssignmentResult.Failure(IllegalStateException("fail"))
                }
                val failed = backup(root, event(2), listOf(RestoreFixture.archiveAssignment("device-fail")))
                assertTrue(runCatching { harness.restorer().restore(failed) }.isFailure)
                assertEquals(listOf(saved), store.data.first().analysesList)
                assertEquals(1, harness.tanks.size)
                assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            }
        } finally { job.cancelAndJoin() }
    }

    @Test
    fun `disk reopen retains rollback identity and rollback affects only its owner and transaction`() = runBlocking {
        val root = Files.createTempDirectory("water-restart").toFile()
        val file = File(root, "live.pb")
        val job = SupervisorJob()
        val store = DataStoreFactory.create(WaterAnalysesSerializer,
            scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val archive = WaterAnalysisArchiveStore(store) { setOf(90L) }
        val backup = backup(root)
        val tx = "4e343e40-d04d-40f7-86df-b3fdf3854e62"
        try {
            store.updateData { it.toBuilder().addAnalyses(event(99).toBuilder().setOwnerUid("other")).build() }
            UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
                archive.restore(WaterHistoryRestoreRequest(RestoreFixture.OWNER_UID, tx, mapOf(7L to 90L),
                    requireNotNull(backup.manifest.waterHistory), requireNotNull(backup.waterHistoryFile)))
            }
        } finally { job.cancelAndJoin() }
        val reopenedJob = SupervisorJob()
        val reopened = DataStoreFactory.create(WaterAnalysesSerializer,
            scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
        try {
            assertEquals(2, reopened.data.first().analysesCount)
            UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
                WaterAnalysisArchiveStore(reopened) { setOf(90L) }.rollback(RestoreFixture.OWNER_UID, tx)
            }
            assertEquals("other", reopened.data.first().analysesList.single().ownerUid)
        } finally { reopenedJob.cancelAndJoin() }
    }

    @Test
    fun `failed history rollback keeps the journal and tank until recovery succeeds`() = runBlocking {
        val root = Files.createTempDirectory("water-rollback-failure").toFile()
        val job = SupervisorJob()
        val store = DataStoreFactory.create(WaterAnalysesSerializer,
            scope = CoroutineScope(Dispatchers.IO + job)) { File(root, "live.pb") }
        lateinit var harness: RestoreHarness
        val archive = WaterAnalysisArchiveStore(store) { harness.tanks.map { it.id }.toSet() }
        var failRollback = true
        harness = RestoreHarness(waterOverride = UserDataRestoreDataSources.WaterHistoryDataSource(
            restore = archive::restore, rollback = { owner, transaction ->
                if (failRollback) throw java.io.IOException("injected rollback failure")
                archive.rollback(owner, transaction)
            }))
        harness.assignmentBehavior = { _, _ ->
                    TankDeviceAssignmentResult.Failure(IllegalStateException("fail"))
                }
        try {
            UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
                val source = backup(root, assignments = listOf(RestoreFixture.archiveAssignment("device-fail")))
                assertTrue(runCatching { harness.restorer().restore(source) }.isFailure)
                assertEquals(1, store.data.first().analysesCount)
                assertEquals(1, harness.tanks.size)
                assertTrue(harness.transactions.pending(RestoreFixture.OWNER_UID) != null)
                failRollback = false
                harness.recovery.recover(RestoreFixture.OWNER_UID)
                assertEquals(0, store.data.first().analysesCount)
                assertTrue(harness.tanks.isEmpty())
                assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
            }
        } finally { job.cancelAndJoin() }
    }

    @Test
    fun `owner gate remains held until the failed restore has completed rollback`() = runBlocking {
        withTimeout(5_000) {
            val root = Files.createTempDirectory("archive-coordinator-gate").toFile()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val harness = RestoreHarness(waterOverride = UserDataRestoreDataSources.WaterHistoryDataSource(
                restore = { entered.complete(Unit); release.await(); error("injected history failure") },
                rollback = { _, _ -> }))
            UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
                coroutineScope {
                    val restore = async { runCatching { harness.restorer().restore(backup(root)) } }
                    entered.await()
                    val competitor = async(start = CoroutineStart.UNDISPATCHED) {
                        OwnerArchiveMutationGate.shared.withOwner(RestoreFixture.OWNER_UID) {
                            assertTrue(harness.tanks.isEmpty())
                            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
                        }
                    }
                    assertFalse(competitor.isCompleted)
                    release.complete(Unit)
                    assertTrue(restore.await().isFailure)
                    competitor.await()
                }
            }
        }
    }

    @Test
    fun `pending tank deletion rejects restore before creating a journal or aquarium`() = runBlocking {
        val harness = RestoreHarness(deletionGuard = { error("Pending deletion") })
        UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
            assertTrue(runCatching { harness.restorer().restore(RestoreFixture.backup()) }.isFailure)
            assertTrue(harness.tanks.isEmpty())
            assertNull(harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    private fun backup(root: File, record: StoredWaterAnalysis = event(),
        assignments: List<ArchiveDeviceAssignment> = emptyList()): DecodedUserDataBackup {
        val file = File.createTempFile("history", ".bin", root)
        val reference = WaterHistoryArchive.write(sequenceOf(record), 1, file)
        val source = RestoreFixture.backup(assignments = assignments)
        val manifest = source.manifest.copy(waterHistory = reference)
        val zip = File.createTempFile("backup", ".zip", root)
        UserDataBackupCodec().encode(manifest, emptyMap(), zip, file)
        return UserDataBackupCodec().decode(zip, Files.createTempDirectory(root.toPath(), "decoded").toFile())
    }

    private fun event(id: Long = 1): StoredWaterAnalysis = StoredWaterAnalysis.newBuilder()
        .setId(id).setOwnerUid("source").setTankId(7).setMeasuredAtMillis(TIME).setCreatedAtMillis(TIME)
        .addMeasurements(StoredWaterMeasurement.newBuilder().setParameter("PH").setValue(7.0)
            .setMethod("MANUAL").setBasis("PH").setUnit("NONE")).build()

    private companion object { const val TIME = 1_800_000_000_000L }
}
