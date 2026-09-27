package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisRequestException
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisRequestFailure
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisRoomCommitInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "water-commit-${UUID.randomUUID()}.db"
    private lateinit var database: WaterAnalysisDatabase

    @Before
    fun open() { database = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name).build() }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun activationRequiresExactVerificationAndIsDurableWithoutReimportAfterDeletion() {
        val row = WaterRoomFixture.record(1).toBuilder().setRequestId(UUID.randomUUID().toString()).build()
        val source = WaterRoomFixture.source(listOf(row))
        val draft = draft(row)
        val commit = WaterAnalysisRoomCommit(database)
        assertThrows(IllegalStateException::class.java) { commit.replay(OWNER, draft) }
        WaterAnalysisRoomActivation(database).activate(source)
        assertEquals(1L, commit.replay(OWNER, draft))
        commit.delete(OWNER, row.tankId, row.id) {}
        database.close()
        open()
        WaterAnalysisRoomActivation(database).activate(source)
        assertEquals(0L, database.analyses().countForOwner(OWNER))
        val failure = assertThrows(WaterAnalysisRequestException::class.java) {
            WaterAnalysisRoomCommit(database).replay(OWNER, draft)
        }
        assertEquals(WaterAnalysisRequestFailure.RECORD_DELETED, failure.failure)
    }

    @Test
    fun eventAndRequestInsertAreOneTransactionAndRetryIsExact() {
        activateEmpty()
        val draft = draft()
        val commit = WaterAnalysisRoomCommit(database)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_request BEFORE INSERT ON " +
            "water_analysis_request BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertThrows(RuntimeException::class.java) { commit.create(OWNER, draft, null, TIME) {} }
        assertEquals(0L, database.analyses().countForOwner(OWNER))
        assertNull(database.analyses().request(OWNER, draft.requestId))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_request")
        val id = commit.create(OWNER, draft, null, TIME) {}
        assertEquals(id, commit.create(OWNER, draft, null, TIME + 1) {})
        assertEquals(1L, database.analyses().countForOwner(OWNER))
        val failure = assertThrows(WaterAnalysisRequestException::class.java) {
            commit.create(OWNER, draft.copy(measuredAtMillis = TIME - 1), null, TIME) {}
        }
        assertEquals(WaterAnalysisRequestFailure.PAYLOAD_CHANGED, failure.failure)
    }

    @Test
    fun activationRequestFailureLeavesVerifiedCheckpointAndRetriesAtomically() {
        val original = WaterRoomFixture.record(1).toBuilder().setOwnerUid("archived-owner").build()
        val row = WaterAnalysisImportIdentity.remap(original,
            WaterAnalysisImportTarget(OWNER, original.tankId, 1L, UUID.randomUUID().toString()))
        val source = WaterRoomFixture.source(listOf(row))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_activation BEFORE INSERT ON " +
            "water_analysis_migration WHEN NEW.state = 3 BEGIN SELECT RAISE(ABORT, 'activation failed'); END")
        assertThrows(RuntimeException::class.java) { WaterAnalysisRoomActivation(database).activate(source) }
        assertEquals(WaterMigrationEntity.VERIFIED, database.analyses().migration(OWNER)?.state)
        assertNull(database.analyses().request(OWNER, row.requestId))
        assertNull(database.imports().original(OWNER, original.ownerUid, original.id))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_activation")
        WaterAnalysisRoomActivation(database).activate(source)
        assertEquals(row.id, database.analyses().request(OWNER, row.requestId)?.analysisId)
        assertEquals(row.id, database.imports().original(OWNER, original.ownerUid, original.id)?.analysisId)
    }

    @Test
    fun deletedIdentityIsNotReusedAndForeignDeleteCannotRemoveEvent() {
        activateEmpty()
        val commit = WaterAnalysisRoomCommit(database)
        val first = commit.create(OWNER, draft(), null, TIME) {}
        commit.delete(OWNER, WaterRoomFixture.TANK_ID + 1, first) {}
        assertEquals(1L, database.analyses().countForOwner(OWNER))
        commit.delete(OWNER, WaterRoomFixture.TANK_ID, first) {}
        val second = commit.create(OWNER, draft(), null, TIME) {}
        assertNotEquals(first, second)
    }

    @Test
    fun pageReadsAreBoundedAndExposeTotalWithoutEmptyTailPage() = runBlocking {
        val source = WaterRoomFixture.source((1L..100L).map(WaterRoomFixture::record))
        WaterAnalysisRoomActivation(database).activate(source)
        val queries = WaterAnalysisRoomQueries(database)
        val first = queries.page(OWNER, WaterRoomFixture.TANK_ID, null).first()
        assertEquals(100L, first.totalCount)
        assertEquals((100L downTo 51L).toList(), first.records.map { it.id })
        val last = queries.page(OWNER, WaterRoomFixture.TANK_ID, first.next).first()
        assertEquals((50L downTo 1L).toList(), last.records.map { it.id })
        assertNull(last.next)
        assertEquals(100L, queries.latest(OWNER, WaterRoomFixture.TANK_ID).first()?.id)
        assertNull(queries.record(OWNER, WaterRoomFixture.TANK_ID + 1, 100L).first())
    }

    private fun activateEmpty() = WaterAnalysisRoomActivation(database).activate(WaterRoomFixture.source(emptyList()))

    private fun draft(row: StoredWaterAnalysis = WaterRoomFixture.record(1)
        .toBuilder().setRequestId(UUID.randomUUID().toString()).build()): WaterAnalysisDraftRecord {
        val record = row.toRecordStrict()
        return WaterAnalysisDraftRecord(record.tankId, record.measuredAtMillis, record.temperatureCelsius,
            record.temperatureSource, record.measurements, record.requestId)
    }

    private companion object {
        const val OWNER = WaterRoomFixture.OWNER
        const val TIME = WaterRoomFixture.TIME
    }
}
