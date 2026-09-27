package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.user.archive.WaterHistoryArchive
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterRoomArchiveInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "water-archive-${UUID.randomUUID()}.db"
    private val directory = File(context.cacheDir, name)
    private lateinit var database: WaterAnalysisDatabase

    @Before
    fun open() {
        check(directory.isDirectory || directory.mkdirs())
        database = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name).build()
    }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
        directory.deleteRecursively()
    }

    @Test
    fun boundedArchiveRoundTripRetainsExactEventsAndRepeatImportIsIdempotent() {
        activate(OWNER)
        val originals = (1L..125L).map(::source)
        val request = request(originals)
        val commit = WaterAnalysisRoomArchiveCommit(database)
        assertEquals(125, commit.restore(request) {})
        assertEquals(0, commit.restore(request.copy(transactionId = UUID.randomUUID().toString())) {})
        val file = File(directory, "export.bin")
        val reference = commit.snapshot(OWNER, setOf(TANK), file)
        assertEquals(125, reference.recordCount)
        WaterHistoryArchive.validate(reference, file, setOf(TANK))
        var index = 0
        WaterHistoryArchive.visit(file, reference.recordCount) { record ->
            assertArrayEquals(originals[index++].toByteArray(),
                WaterAnalysisImportIdentity.original(record).toByteArray())
            assertEquals(OWNER, record.ownerUid)
            assertEquals(TANK, record.tankId)
            assertNotNull(database.imports().original(OWNER, SOURCE_OWNER, record.importOrigin.sourceAnalysisId))
        }
        assertEquals(125, index)
    }

    @Test
    fun mappingFailureAndLateConflictRollbackTheWholeImport() {
        activate(OWNER)
        val request = request(listOf(source(1)))
        val commit = WaterAnalysisRoomArchiveCommit(database)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON " +
            "water_analysis_import BEGIN SELECT RAISE(ABORT, 'injected mapping failure'); END")
        assertThrows(RuntimeException::class.java) { commit.restore(request) {} }
        assertEquals(0L, database.analyses().countForOwner(OWNER))
        assertEquals(0L, database.analyses().lastAllocatedId(OWNER))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        assertEquals(1, commit.restore(request) {})
        val conflict = request(listOf(source(2), source(1).toBuilder().setMeasuredAtMillis(TIME - 1).build()))
        assertThrows(IllegalArgumentException::class.java) { commit.restore(conflict) {} }
        assertEquals(1L, database.analyses().countForOwner(OWNER))
        assertEquals(1L, database.analyses().lastAllocatedId(OWNER))
        assertNull(database.imports().original(OWNER, SOURCE_OWNER, 2))
    }

    @Test
    fun reopenedRollbackUsesExactOwnerAndTransactionAndKeepsRequestTombstones() {
        activate(OWNER)
        activate(OTHER_OWNER)
        val first = request(listOf(source(1)))
        val second = request(listOf(source(2)))
        val commit = WaterAnalysisRoomArchiveCommit(database)
        commit.restore(first) {}
        commit.restore(second) {}
        commit.restore(first.copy(ownerUid = OTHER_OWNER)) {}
        val original = checkNotNull(database.analyses().record(OWNER, TANK, 1)).toMigrationRecord()
        database.close()
        open()
        WaterAnalysisRoomArchiveCommit(database).rollback(OWNER, first.transactionId) {}
        assertNull(database.analyses().record(OWNER, TANK, 1))
        assertNull(database.imports().original(OWNER, SOURCE_OWNER, 1))
        assertNotNull(database.analyses().request(OWNER, original.requestId))
        assertEquals(1L, database.analyses().countForOwner(OWNER))
        assertEquals(1L, database.analyses().countForOwner(OTHER_OWNER))
        assertEquals(2L, database.analyses().lastAllocatedId(OWNER))
    }

    @Test
    fun tankRollbackRestoresImportMappingAndOwnerCleanupCascadesOnlyItsMappings() {
        activate(OWNER)
        activate(OTHER_OWNER)
        val request = request(listOf(source(1)))
        val commit = WaterAnalysisRoomArchiveCommit(database)
        commit.restore(request) {}
        commit.restore(request.copy(ownerUid = OTHER_OWNER)) {}
        val stage = WaterAnalysisRoomDeletionStage(database)
        val manifest = stage.capture(OWNER, TANK)
        stage.remove(OWNER, TANK, manifest.transactionId)
        assertNull(database.imports().original(OWNER, SOURCE_OWNER, 1))
        stage.restore(OWNER, TANK, manifest.transactionId)
        assertNotNull(database.imports().original(OWNER, SOURCE_OWNER, 1))
        assertEquals(0, commit.restore(request) {})
        stage.complete(OWNER, TANK, manifest.transactionId)
        WaterAnalysisOwnerCleanup(database).clear(OWNER)
        assertNull(database.imports().original(OWNER, SOURCE_OWNER, 1))
        assertNotNull(database.imports().original(OTHER_OWNER, SOURCE_OWNER, 1))
    }

    private fun activate(owner: String) {
        val bytes = WaterAnalysesStore.newBuilder().setSchemaVersion(3).build().toByteArray()
        WaterAnalysisRoomActivation(database).activate(
            WaterAnalysisMigrationSource.readFrom(ByteArrayInputStream(bytes), owner))
    }

    private fun request(records: List<StoredWaterAnalysis>): WaterHistoryRestoreRequest {
        val file = File(directory, "${UUID.randomUUID()}.bin")
        val reference = WaterHistoryArchive.write(records.asSequence(), records.size, file)
        WaterHistoryArchive.validate(reference, file, setOf(WaterRoomFixture.TANK_ID))
        return WaterHistoryRestoreRequest(OWNER, UUID.randomUUID().toString(),
            mapOf(WaterRoomFixture.TANK_ID to TANK), reference, file)
    }

    private fun source(id: Long) = WaterRoomFixture.record(id).toBuilder().setOwnerUid(SOURCE_OWNER).build()

    private companion object {
        const val OWNER = "water-room-archive-owner"
        const val OTHER_OWNER = "water-room-archive-other"
        const val SOURCE_OWNER = "water-room-archive-source"
        const val TANK = 77L
        const val TIME = WaterRoomFixture.TIME
    }
}
