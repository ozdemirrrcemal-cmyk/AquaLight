package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisRoomPagingInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, WaterAnalysisDatabase::class.java).build()
    private val dao = database.analyses()

    @After
    fun close() = database.close()

    @Test
    fun tenThousandTiedRowsPageWithoutGapsAcrossOwnersAndTanks() {
        val source = WaterRoomFixture.source((1..ROW_COUNT).map { WaterRoomFixture.record(it.toLong()) })
        val migration = WaterAnalysisRoomMigration(database)
        while (migration.copyNextBatch(source)) Unit
        migration.verify(source)
        dao.insert(listOf(
            WaterRoomFixture.record(1).toBuilder().setOwnerUid("other").build().toMigrationEntity(),
            WaterRoomFixture.record(ROW_COUNT + 1L).toBuilder().setTankId(3).build().toMigrationEntity()
        ))
        var expectedId = ROW_COUNT.toLong()
        var page = dao.firstPage(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID)
        while (page.isNotEmpty()) {
            assertTrue(page.size <= WaterAnalysisMigrationSource.BATCH_SIZE)
            page.forEach { assertEquals(expectedId--, it.analysisId) }
            page = after(page.last())
        }
        assertEquals(0L, expectedId)
        assertEquals(ROW_COUNT.toLong(), dao.latest(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID)?.analysisId)
        assertNull(dao.record("other", WaterRoomFixture.TANK_ID, ROW_COUNT.toLong()))
        assertNull(dao.record(WaterRoomFixture.OWNER, 3, 1))
    }

    @Test
    fun backdatingAndCreatedTimeTiesUseStableOrderAndExactDeletion() {
        val first = WaterRoomFixture.record(1)
        val second = WaterRoomFixture.record(2).toBuilder().setCreatedAtMillis(WaterRoomFixture.TIME + 1).build()
        val backdated = WaterRoomFixture.record(3).toBuilder().setMeasuredAtMillis(WaterRoomFixture.TIME - 1).build()
        dao.insert(listOf(first, second, backdated).map(StoredWaterAnalysis::toMigrationEntity))
        val page = dao.firstPage(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID)
        assertEquals(listOf(2L, 1L, 3L), page.map { it.analysisId })
        assertEquals(0, dao.delete("other", WaterRoomFixture.TANK_ID, 2))
        assertEquals(0, dao.delete(WaterRoomFixture.OWNER, 3, 2))
        assertEquals(1, dao.delete(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID, 2))
        assertEquals(0, dao.delete(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID, 2))
        assertEquals(1L, dao.latest(WaterRoomFixture.OWNER, WaterRoomFixture.TANK_ID)?.analysisId)
    }

    @Test
    fun requestAndEventIdentityAreUniqueOnlyWithinTheirOwner() {
        val requestId = "123e4567-e89b-12d3-a456-426614174000"
        val first = WaterRoomFixture.record(1).toBuilder().setRequestId(requestId).build()
        dao.insert(listOf(first.toMigrationEntity()))
        assertThrows(RuntimeException::class.java) { dao.insert(listOf(first.toMigrationEntity())) }
        assertThrows(RuntimeException::class.java) {
            dao.insert(listOf(first.toBuilder().setId(2).build().toMigrationEntity()))
        }
        dao.insert(listOf(first.toBuilder().setOwnerUid("other").build().toMigrationEntity()))
        val legacy = listOf(WaterRoomFixture.record(2), WaterRoomFixture.record(3))
        dao.insert(legacy.map(StoredWaterAnalysis::toMigrationEntity))
        assertEquals(3L, dao.countForOwner(WaterRoomFixture.OWNER))
    }

    @Test
    fun pageQueryUsesTimeIndexWithoutTemporarySort() {
        val sql = "EXPLAIN QUERY PLAN SELECT * FROM water_analysis WHERE ownerUid = ? AND tankId = ? " +
            "AND (observedAtMillis, createdAtMillis, analysisId) < (?, ?, ?) " +
            "ORDER BY observedAtMillis DESC, createdAtMillis DESC, analysisId DESC LIMIT 50"
        val arguments = arrayOf<Any>(WaterRoomFixture.OWNER, 2L, 1L, 1L, 1L)
        database.openHelper.readableDatabase.query(sql, arguments).use { cursor ->
            val plans = mutableListOf<String>()
            while (cursor.moveToNext()) plans.add(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
            assertTrue(plans.any { it.contains("USING INDEX index_water_analysis_ownerUid_tankId") })
            assertTrue(plans.none { it.contains("TEMP B-TREE") })
        }
    }

    private fun after(row: WaterAnalysisEntity): List<WaterAnalysisEntity> = dao.pageAfter(
        row.ownerUid, row.tankId, row.observedAtMillis, row.createdAtMillis, row.analysisId
    )

    private companion object {
        const val ROW_COUNT = 10_000
    }
}
