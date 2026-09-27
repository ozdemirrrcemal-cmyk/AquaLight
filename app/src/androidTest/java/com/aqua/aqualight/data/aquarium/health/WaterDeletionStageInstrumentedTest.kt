package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionManifestEntity
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterDeletionStageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "water-deletion-${UUID.randomUUID()}.db"
    private lateinit var database: WaterAnalysisDatabase

    @Before
    fun open() { database = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name).build() }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun restartAfterRemovalRestoresExactBytesOnceAndRetainsOtherTanks() {
        val rows = (1L..101L).map(WaterRoomFixture::record)
        val other = WaterRoomFixture.record(102).toBuilder().setTankId(TANK + 1).build()
        WaterAnalysisRoomActivation(database).activate(WaterRoomFixture.source(rows + other))
        val stage = WaterAnalysisRoomDeletionStage(database)
        val manifest = stage.capture(OWNER, TANK)
        assertEquals(101L, manifest.recordCount)
        stage.remove(OWNER, TANK, manifest.transactionId)
        assertEquals(0L, database.analyses().countForTank(OWNER, TANK))
        assertEquals(1L, database.analyses().countForTank(OWNER, TANK + 1))
        database.close()
        open()
        val resumed = WaterAnalysisRoomDeletionStage(database)
        resumed.restore(OWNER, TANK, manifest.transactionId)
        resumed.restore(OWNER, TANK, manifest.transactionId)
        assertEquals(101L, database.analyses().countForTank(OWNER, TANK))
        rows.forEach { row ->
            assertArrayEquals(row.toByteArray(), database.analyses().record(OWNER, TANK, row.id)?.rawProto)
        }
        resumed.complete(OWNER, TANK, manifest.transactionId)
        resumed.complete(OWNER, TANK, manifest.transactionId)
        assertNull(database.deletions().manifest(OWNER, TANK))
        assertEquals(0L, database.deletions().count(OWNER, TANK))
    }

    @Test
    fun fullDiskAtManifestWriteLeavesNoStageAndDoesNotBeginDestruction() {
        activateSingle()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_stage BEFORE INSERT ON " +
            "water_analysis_delete_manifest BEGIN SELECT RAISE(ABORT, 'disk full injection'); END")
        assertThrows(RuntimeException::class.java) { WaterAnalysisRoomDeletionStage(database).capture(OWNER, TANK) }
        assertEquals(1L, database.analyses().countForTank(OWNER, TANK))
        assertEquals(0L, database.deletions().count(OWNER, TANK))
        assertNull(database.deletions().manifest(OWNER, TANK))
    }

    @Test
    fun checksumCorruptionCannotDeleteLiveRowsOrRestorePartialHistory() {
        activateSingle()
        val stage = WaterAnalysisRoomDeletionStage(database)
        val manifest = stage.capture(OWNER, TANK)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE water_analysis_delete_manifest SET sha256 = 'corrupt'")
        assertThrows(IllegalStateException::class.java) { stage.remove(OWNER, TANK, manifest.transactionId) }
        assertThrows(IllegalStateException::class.java) { stage.restore(OWNER, TANK, manifest.transactionId) }
        assertEquals(1L, database.analyses().countForTank(OWNER, TANK))
    }

    @Test
    fun removalAndMarkerFailureRollBackTogetherAndChangedTransactionCannotCleanStage() {
        activateSingle()
        val stage = WaterAnalysisRoomDeletionStage(database)
        val manifest = stage.capture(OWNER, TANK)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_removed BEFORE UPDATE ON " +
            "water_analysis_delete_manifest BEGIN SELECT RAISE(ABORT, 'marker failure'); END")
        assertThrows(RuntimeException::class.java) { stage.remove(OWNER, TANK, manifest.transactionId) }
        assertEquals(1L, database.analyses().countForTank(OWNER, TANK))
        assertEquals(WaterDeletionManifestEntity.PREPARED, database.deletions().manifest(OWNER, TANK)?.state)
        assertThrows(IllegalStateException::class.java) { stage.complete(OWNER, TANK, "different-transaction") }
        assertEquals(1L, database.deletions().count(OWNER, TANK))
    }

    @Test
    fun rollbackRefusesConflictingLiveIdentityWithoutOverwritingEitherSide() {
        activateSingle()
        val stage = WaterAnalysisRoomDeletionStage(database)
        val manifest = stage.capture(OWNER, TANK)
        stage.remove(OWNER, TANK, manifest.transactionId)
        val conflicting = WaterRoomFixture.record(1).toBuilder().setTankId(TANK + 1).build()
        database.analyses().insert(listOf(conflicting.toMigrationEntity()))
        assertThrows(IllegalStateException::class.java) { stage.restore(OWNER, TANK, manifest.transactionId) }
        assertEquals(0L, database.analyses().countForTank(OWNER, TANK))
        assertEquals(1L, database.analyses().countForTank(OWNER, TANK + 1))
        assertEquals(1L, database.deletions().count(OWNER, TANK))
    }

    private fun activateSingle() = WaterAnalysisRoomActivation(database)
        .activate(WaterRoomFixture.source(listOf(WaterRoomFixture.record(1))))

    private companion object {
        const val OWNER = WaterRoomFixture.OWNER
        const val TANK = WaterRoomFixture.TANK_ID
    }
}
