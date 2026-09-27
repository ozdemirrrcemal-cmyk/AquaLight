package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationRequestException
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthObservationRoomInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "health-test-${UUID.randomUUID()}.db"
    private lateinit var database: HealthObservationDatabase

    @Before
    fun open() {
        database = Room.databaseBuilder(context, HealthObservationDatabase::class.java, name).build()
    }

    @After
    fun clean() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun retriesAndDeletionTombstonesSurviveDatabaseReopen() {
        val prepared = HealthRoomFixture.prepared()
        val commits = HealthObservationRoomCommit(database)
        val id = commits.create(OWNER, prepared, TIME) { }
        assertEquals(id, commits.create(OWNER, prepared, TIME + 1) { })
        val changed = prepared.copy(input = prepared.input.copy(notes = prepared.input.notes.copy(text = "changed")))
        assertThrows(HealthObservationRequestException::class.java) { commits.create(OWNER, changed, TIME) { } }
        commits.delete(OWNER, TANK, id) { }
        database.close()
        open()
        assertNotNull(database.observations().request(OWNER, prepared.input.identity.requestId))
        assertThrows(HealthObservationRequestException::class.java) {
            HealthObservationRoomCommit(database).create(OWNER, prepared, TIME) { }
        }
    }

    @Test
    fun requestFailureRollsBackTheObservationAndAllowsTheOriginalRetry() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_health_request BEFORE INSERT ON health_observation_request " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        val prepared = HealthRoomFixture.prepared()
        val commits = HealthObservationRoomCommit(database)
        assertThrows(RuntimeException::class.java) { commits.create(OWNER, prepared, TIME) { } }
        assertTrue(database.observations().ownerPage(OWNER, 0).isEmpty())
        assertNull(database.observations().request(OWNER, prepared.input.identity.requestId))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_health_request")
        assertEquals(TIME, commits.create(OWNER, prepared, TIME) { })
    }

    @Test
    fun wrongTankAndOtherOwnerCannotReadOrDeleteAnObservation() = runBlocking {
        val id = HealthObservationRoomCommit(database).create(OWNER, HealthRoomFixture.prepared(), TIME) { }
        val queries = HealthObservationRoomQueries(database)
        assertNull(queries.record("other-owner", TANK, id).first())
        assertNull(queries.record(OWNER, TANK + 1, id).first())
        HealthObservationRoomCommit(database).delete("other-owner", TANK, id) { }
        HealthObservationRoomCommit(database).delete(OWNER, TANK + 1, id) { }
        assertEquals(id, queries.record(OWNER, TANK, id).first()?.id)
    }

    @Test
    fun prepareAbortAndRemoveRollbackPreserveExactHistory() {
        val id = HealthObservationRoomCommit(database).create(OWNER, HealthRoomFixture.prepared(), TIME) { }
        val raw = database.observations().record(OWNER, TANK, id)!!.payload
        val stage = HealthObservationRoomMaintenance(database)
        val transaction = UUID.randomUUID().toString()
        stage.prepare(OWNER, TANK, transaction)
        stage.finish(OWNER, TANK, transaction)
        assertArrayEquals(raw, database.observations().record(OWNER, TANK, id)!!.payload)
        stage.prepare(OWNER, TANK, transaction)
        stage.remove(OWNER, TANK, allowUnstaged = false)
        assertNull(database.observations().record(OWNER, TANK, id))
        database.close()
        open()
        HealthObservationRoomMaintenance(database).restore(OWNER, TANK, transaction)
        HealthObservationRoomMaintenance(database).finish(OWNER, TANK, transaction)
        assertArrayEquals(raw, database.observations().record(OWNER, TANK, id)!!.payload)
    }

    @Test
    fun committedTankDeletionRetainsRetryIdentityAndOtherOwners() {
        val prepared = HealthRoomFixture.prepared()
        HealthObservationRoomCommit(database).create(OWNER, prepared, TIME) { }
        HealthObservationRoomCommit(database).create("other-owner", prepared, TIME) { }
        val transaction = UUID.randomUUID().toString()
        val stage = HealthObservationRoomMaintenance(database)
        stage.prepare(OWNER, TANK, transaction)
        stage.remove(OWNER, TANK, allowUnstaged = false)
        stage.finish(OWNER, TANK, transaction)
        assertTrue(database.observations().ownerPage(OWNER, 0).isEmpty())
        assertNotNull(database.observations().request(OWNER, prepared.input.identity.requestId))
        assertEquals(1, database.observations().ownerPage("other-owner", 0).size)
    }

    @Test
    fun keysetPagingRetainsEveryRowAndFiltersExactLocalPlantIdentity() = runBlocking {
        val commits = HealthObservationRoomCommit(database)
        repeat(125) { index ->
            commits.create(OWNER, HealthRoomFixture.prepared(plant = if (index % 2 == 0) 3 else 4,
                observedAt = TIME - index), TIME) { }
        }
        val queries = HealthObservationRoomQueries(database)
        val query = HealthObservationQuery(TANK, HealthObservationKind.PLANT)
        val first = queries.history(OWNER, query).first()
        val second = queries.history(OWNER, query.copy(cursor = first.next)).first()
        val third = queries.history(OWNER, query.copy(cursor = second.next)).first()
        assertEquals(listOf(50, 50, 25), listOf(first, second, third).map { it.records.size })
        assertEquals(125, (first.records + second.records + third.records).map { it.id }.distinct().size)
        assertNull(third.next)
        assertEquals(63L, queries.history(OWNER, query.copy(subjectId = 3)).first().totalCount)
        assertEquals(62L, queries.history(OWNER, query.copy(subjectId = 4)).first().totalCount)
    }

    private companion object {
        const val OWNER = HealthRoomFixture.OWNER
        const val TANK = HealthRoomFixture.TANK
        const val TIME = HealthRoomFixture.TIME
    }
}
