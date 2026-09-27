package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisCutoverInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val owner = "cutover-${UUID.randomUUID()}"
    private val other = "$owner-other"
    private val name = "$owner.db"
    private lateinit var database: WaterAnalysisDatabase
    private val legacy = CutoverLegacyStore()
    private lateinit var cutover: WaterAnalysisRoomCutover

    @Before
    fun open() {
        TankCareIntegrityJournal.initialize(context)
        database = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name).build()
        cutover = WaterAnalysisRoomCutover(context, legacy, database)
    }

    @After
    fun clean() {
        listOf(owner, other).forEach {
            cutover.sources.clear(it)
            TankCareIntegrityJournal.clearOwner(it)
            UserDataRestoreJournal(context).clearOwner(it)
        }
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun resumesFrozenOwnerAfterAnotherOwnersLegacyHistoryChanges() = runBlocking {
        val rows = (1..125).map { row(it.toLong()) }
        legacy.value = store(rows + row(200, other))
        val source = cutover.sources.retain(owner, legacy.value)
        assertTrue(cutover.requiresResume(owner))
        assertTrue(runCatching { cutover.requireSettledAuthority(owner) }.isFailure)
        WaterAnalysisRoomMigration(database).copyNextBatch(source)
        database.close()
        open()
        legacy.value = store(rows + row(201, other))
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        assertEquals(WaterMigrationEntity.ACTIVE, database.analyses().migration(owner)?.state)
        assertTrue(cutover.requireSettledAuthority(owner))
        assertEquals(125L, database.analyses().countForOwner(owner))
        assertEquals(0L, database.analyses().countForOwner(other))
        rows.forEach { expected ->
            assertArrayEquals(expected.toByteArray(), database.analyses().recordForOwner(owner, expected.id)?.rawProto)
        }
        assertEquals(126, legacy.value.analysesCount)
    }

    @Test
    fun activeAuthorityDoesNotReadLegacyOrResurrectDeletedRows() = runBlocking {
        legacy.value = store(listOf(row(1).toBuilder().setRequestId(UUID.randomUUID().toString()).build()))
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        val request = legacy.value.getAnalyses(0).requestId
        WaterAnalysisRoomCommit(database).delete(owner, WaterRoomFixture.TANK_ID, 1L) { }
        legacy.failReads = true
        database.close()
        open()
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        assertEquals(0L, database.analyses().countForOwner(owner))
        assertNotNull(database.analyses().request(owner, request))
        assertEquals(1, legacy.reads)
    }

    @Test
    fun changedOwnerSourceCannotOverwriteFrozenEvidenceOrPartialRows() = runBlocking {
        legacy.value = store((1..75).map { row(it.toLong()) })
        val retained = cutover.sources.retain(owner, legacy.value)
        WaterAnalysisRoomMigration(database).copyNextBatch(retained)
        legacy.value = store(listOf(row(999)))
        assertTrue(runCatching { UserDataScope.withOwnerUid(owner) { cutover.activate(owner) } }.isFailure)
        assertEquals(50L, database.analyses().countForOwner(owner))
        assertEquals(WaterMigrationEntity.COPYING, database.analyses().migration(owner)?.state)
        assertTrue(runCatching { cutover.requireSettledAuthority(owner) }.isFailure)
        legacy.value = store((1..75).map { row(it.toLong()) })
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        assertEquals(75L, database.analyses().countForOwner(owner))
    }

    @Test
    fun unfinishedRestoreAndTankDeletionBothBlockFirstActivation() = runBlocking {
        legacy.value = store(listOf(row(1)))
        val restores = UserDataRestoreJournal(context)
        restores.begin(owner, emptySet())
        assertTrue(runCatching { UserDataScope.withOwnerUid(owner) { cutover.activate(owner) } }.isFailure)
        restores.clearOwner(owner)
        TankCareIntegrityJournal.begin(owner, listOf(WaterRoomFixture.TANK_ID))
        assertTrue(runCatching { UserDataScope.withOwnerUid(owner) { cutover.activate(owner) } }.isFailure)
        assertNull(database.analyses().migration(owner))
        assertEquals(0, legacy.reads)
        TankCareIntegrityJournal.abort(owner, WaterRoomFixture.TANK_ID)
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        assertEquals(1L, database.analyses().countForOwner(owner))
    }

    @Test
    fun boundedOrphanRepairRetainsOtherOwnerAndRetryTombstones() = runBlocking {
        val rows = (1..125).map { id -> row(id.toLong()).toBuilder()
            .setTankId(if (id % 2 == 0) WaterRoomFixture.TANK_ID else 3L)
            .setRequestId(UUID.randomUUID().toString()).build() }
        legacy.value = store(rows + row(200, other))
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        UserDataScope.withOwnerUid(other) { cutover.activate(other) }
        assertEquals(63, WaterAnalysisRoomOrphanRepair(database).repair(owner, setOf(WaterRoomFixture.TANK_ID)))
        assertEquals(62L, database.analyses().countForOwner(owner))
        assertEquals(1L, database.analyses().countForOwner(other))
        rows.forEach { assertNotNull(database.analyses().request(owner, it.requestId)) }
        assertEquals(0, WaterAnalysisRoomOrphanRepair(database).repair(owner, setOf(WaterRoomFixture.TANK_ID)))
    }

    @Test
    fun removedLegacyAndFrozenSourceCannotResurrectAClearedOwner() = runBlocking {
        legacy.value = store(listOf(row(1), row(2, other)))
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        UserDataScope.withOwnerUid(other) { cutover.activate(other) }
        legacy.updateData { store(it.analysesList.filter { record -> record.ownerUid != owner }) }
        cutover.sources.clear(owner)
        WaterAnalysisOwnerCleanup(database).clear(owner)
        database.close()
        open()
        UserDataScope.withOwnerUid(owner) { cutover.activate(owner) }
        assertEquals(0L, database.analyses().countForOwner(owner))
        assertEquals(1L, database.analyses().countForOwner(other))
        assertEquals(WaterMigrationEntity.ACTIVE, database.analyses().migration(other)?.state)
    }

    private fun row(id: Long, uid: String = owner): StoredWaterAnalysis =
        WaterRoomFixture.record(id).toBuilder().setOwnerUid(uid).build()

    private fun store(rows: List<StoredWaterAnalysis>): WaterAnalysesStore =
        WaterAnalysesStore.newBuilder().setSchemaVersion(3).addAllAnalyses(rows).build()
}

private class CutoverLegacyStore : DataStore<WaterAnalysesStore> {
    var value: WaterAnalysesStore = WaterAnalysesStore.newBuilder().setSchemaVersion(3).build()
    var failReads = false
    var reads = 0
    override val data: Flow<WaterAnalysesStore> = flow {
        check(!failReads) { "ACTIVE owners must never read the old source." }
        reads++
        emit(value)
    }
    override suspend fun updateData(transform: suspend (WaterAnalysesStore) -> WaterAnalysesStore): WaterAnalysesStore =
        transform(value).also { value = it }
}
