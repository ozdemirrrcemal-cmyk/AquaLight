package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.user.archive.HealthHistoryArchive
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthObservationArchiveInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "health-archive-${UUID.randomUUID()}.db"
    private lateinit var database: HealthObservationDatabase
    private lateinit var file: File

    @Before
    fun open() {
        database = Room.databaseBuilder(context, HealthObservationDatabase::class.java, name).build()
        file = File.createTempFile("health-history", ".bin", context.cacheDir)
    }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
        file.delete()
    }

    @Test
    fun restoreRemapsFollowUpsRetainsFrozenContextAndIsIdempotentAfterReopen() {
        val parent = row(20)
        val child = row(21, previous = 20)
        val request = request(listOf(parent, child))
        val commits = HealthObservationArchiveCommit(database)
        assertEquals(2, commits.restore(request, emptyMap()) {})
        val imported = database.observations().ownerPage(OWNER, 0).map { it.toStored() }
        assertEquals(listOf(100L, 100L), imported.map { it.input.tankId })
        assertEquals(imported[0].id, imported[1].input.previousObservationId)
        assertEquals(parent.evidence, imported[0].evidence)
        assertEquals(parent, HealthObservationImportIdentity.original(imported[0]))
        assertEquals(child, HealthObservationImportIdentity.original(imported[1]))
        database.close()
        database = Room.databaseBuilder(context, HealthObservationDatabase::class.java, name).build()
        assertEquals(0, HealthObservationArchiveCommit(database).restore(request, emptyMap()) {})
        assertEquals(imported, database.observations().ownerPage(OWNER, 0).map { it.toStored() })
        val followUp = HealthRoomFixture.prepared(tank = 100).let { value ->
            value.copy(input = value.input.copy(notes = value.input.notes.copy(followUp =
                ObservationFollowUp(ObservationPhase.FOLLOW_UP, imported[0].id))))
        }
        HealthObservationRoomCommit(database).create(OWNER, followUp, HealthRoomFixture.TIME) {}
        val exported = database.observations().ownerPage(OWNER, 0).map { it.toStored() }
        val reexport = request(exported).copy(ownerUid = "third-owner", tankIdMap = mapOf(100L to 200L))
        val reopened = HealthObservationArchiveCommit(database)
        assertEquals(3, reopened.restore(reexport, emptyMap()) {})
        val third = database.observations().ownerPage("third-owner", 0).map { it.toStored() }
        assertEquals(third[0].id, third[1].input.previousObservationId)
        assertEquals(third[0].id, third[2].input.previousObservationId)
        assertEquals(child, HealthObservationImportIdentity.original(third[1]))
        assertEquals(exported[2], HealthObservationImportIdentity.original(third[2]))
    }

    @Test
    fun failedCommitIsAtomicAndRollbackOnlyRemovesItsOwnerTransaction() {
        val request = request(listOf(row(20)))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_health_archive BEFORE INSERT ON health_observation_request " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        val commits = HealthObservationArchiveCommit(database)
        assertThrows(RuntimeException::class.java) { commits.restore(request, emptyMap()) {} }
        assertTrue(database.observations().ownerPage(OWNER, 0).isEmpty())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_health_archive")
        assertEquals(1, commits.restore(request, emptyMap()) {})
        assertEquals(1, commits.restore(request.copy(ownerUid = "other-owner"), emptyMap()) {})
        val liveId = HealthObservationRoomCommit(database)
            .create(OWNER, HealthRoomFixture.prepared(), HealthRoomFixture.TIME) {}
        commits.rollback(OWNER, request.transactionId)
        assertNull(commits.previous(OWNER, row(20)))
        assertEquals(listOf(liveId), database.observations().ownerPage(OWNER, 0).map { it.observationId })
        assertEquals(1, database.observations().ownerPage("other-owner", 0).size)
        assertEquals(1, commits.restore(request, emptyMap()) {})
    }

    @Test
    fun alteredEvidenceAndDifferentTargetTankCannotReuseAnImportedIdentity() {
        val source = row(20)
        val request = request(listOf(source))
        val commits = HealthObservationArchiveCommit(database)
        commits.restore(request, emptyMap()) {}
        val changed = source.toBuilder().setInput(source.input.toBuilder().setNote("changed")).build()
        assertThrows(IllegalArgumentException::class.java) { commits.previous(OWNER, changed) }
        assertThrows(IllegalArgumentException::class.java) {
            commits.restore(request.copy(tankIdMap = mapOf(HealthRoomFixture.TANK to 101)), emptyMap()) {}
        }
        assertEquals(1, database.observations().ownerPage(OWNER, 0).size)
    }

    private fun request(rows: List<StoredHealthObservation>): HealthHistoryRestoreRequest {
        val reference = HealthHistoryArchive.write(rows.asSequence(), rows.size, file, emptyList())
        return HealthHistoryRestoreRequest(OWNER, UUID.randomUUID().toString(),
            mapOf(HealthRoomFixture.TANK to 100), reference, file, emptyMap())
    }

    private fun row(id: Long, previous: Long? = null): StoredHealthObservation {
        val prepared = HealthRoomFixture.prepared().let { value ->
            value.copy(input = value.input.copy(notes = value.input.notes.copy(followUp = ObservationFollowUp(
                if (previous == null) ObservationPhase.OBSERVATION else ObservationPhase.FOLLOW_UP, previous))))
        }
        return HealthObservationRecordCodec.encode("source-owner", id, HealthRoomFixture.TIME, prepared)
    }

    private companion object { const val OWNER = "health-restore-target" }
}
