package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisRoomMigrationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "water-migration-${UUID.randomUUID()}.db"
    private lateinit var database: WaterAnalysisDatabase

    @Before
    fun open() {
        database = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name).build()
    }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun restartAfterOneBatchResumesWithoutDuplicateOrRawChanges() {
        val rows = (1..ROW_COUNT).map { WaterRoomFixture.record(it.toLong()) }
        val source = WaterRoomFixture.source(rows)
        assertTrue(WaterAnalysisRoomMigration(database).copyNextBatch(source))
        assertEquals(50L, database.analyses().countForOwner(WaterRoomFixture.OWNER))
        database.close()
        open()
        migrate(source)
        assertEquals(ROW_COUNT.toLong(), database.analyses().countForOwner(WaterRoomFixture.OWNER))
        val journal = checkNotNull(database.analyses().migration(WaterRoomFixture.OWNER))
        assertEquals(WaterMigrationEntity.VERIFIED, journal.state)
        assertEquals(source.manifest.sourceSha256, journal.sourceSha256)
        migrate(source)
        assertEquals(ROW_COUNT.toLong(), database.analyses().countForOwner(WaterRoomFixture.OWNER))
    }

    @Test
    fun checkpointFailureRollsBackTheWholeBatch() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_checkpoint BEFORE INSERT ON water_analysis_migration " +
                "BEGIN SELECT RAISE(ABORT, 'injected checkpoint failure'); END"
        )
        val source = WaterRoomFixture.source(listOf(WaterRoomFixture.record(1)))
        assertThrows(RuntimeException::class.java) { WaterAnalysisRoomMigration(database).copyNextBatch(source) }
        assertEquals(0L, database.analyses().countForOwner(WaterRoomFixture.OWNER))
        assertEquals(null, database.analyses().migration(WaterRoomFixture.OWNER))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_checkpoint")
        migrate(source)
    }

    @Test
    fun changedSourceCannotResumeAndIncompleteCopyCannotVerify() {
        val rows = (1..ROW_COUNT).map { WaterRoomFixture.record(it.toLong()) }
        val source = WaterRoomFixture.source(rows)
        val migration = WaterAnalysisRoomMigration(database)
        migration.copyNextBatch(source)
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { migration.verify(source) }
        val changed = WaterRoomFixture.source(rows.dropLast(1))
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { migration.copyNextBatch(changed) }
        assertEquals(50L, database.analyses().countForOwner(WaterRoomFixture.OWNER))
        migrate(source)
    }

    @Test
    fun changedIndexedColumnsCannotAcquireVerifiedMarker() {
        val source = WaterRoomFixture.source(listOf(WaterRoomFixture.record(1)))
        val migration = WaterAnalysisRoomMigration(database)
        migration.copyNextBatch(source)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE water_analysis SET observedAtMillis = observedAtMillis + 1"
        )
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { migration.verify(source) }
        assertEquals(WaterMigrationEntity.COPYING, database.analyses().migration(WaterRoomFixture.OWNER)?.state)
    }

    @Test
    fun changedRawPayloadCannotAcquireVerifiedMarker() {
        val record = WaterRoomFixture.record(1)
        val source = WaterRoomFixture.source(listOf(record))
        val migration = WaterAnalysisRoomMigration(database)
        migration.copyNextBatch(source)
        val changed = record.toBuilder().setMeasurements(0, record.getMeasurements(0).toBuilder().setValue(3.0))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE water_analysis SET rawProto = ?", arrayOf<Any>(changed.build().toByteArray())
        )
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { migration.verify(source) }
        assertEquals(WaterMigrationEntity.COPYING, database.analyses().migration(WaterRoomFixture.OWNER)?.state)
    }

    @Test
    fun ownerWithNoHistoryVerifiesWithoutAdoptingAnotherOwner() {
        val other = WaterRoomFixture.record(1).toBuilder().setOwnerUid("other-owner").build()
        val source = WaterRoomFixture.source(listOf(other))
        migrate(source)
        assertEquals(0L, database.analyses().countForOwner(WaterRoomFixture.OWNER))
        assertEquals(0L, database.analyses().countForOwner("other-owner"))
        assertEquals(WaterMigrationEntity.VERIFIED, database.analyses().migration(WaterRoomFixture.OWNER)?.state)
    }

    private fun migrate(source: WaterAnalysisMigrationSource) {
        val migration = WaterAnalysisRoomMigration(database)
        while (migration.copyNextBatch(source)) Unit
        migration.verify(source)
    }

    private companion object {
        const val ROW_COUNT = 101
    }
}
