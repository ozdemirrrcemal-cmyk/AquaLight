package com.aqua.aqualight.data.aquarium.health.observation

import androidx.room.InvalidationTracker
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationCursor
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPage
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationEntity
import java.util.Collections
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class HealthObservationRoomQueries(private val database: HealthObservationDatabase) {
    private val dao = database.observations()

    fun record(owner: String, tank: Long, id: Long): Flow<HealthObservationSnapshot?> = observe(owner) {
        require(tank > 0L && id > 0L)
        dao.record(owner, tank, id)?.toStored()?.let(HealthObservationRecordCodec::decode)
    }

    fun history(owner: String, query: HealthObservationQuery): Flow<HealthObservationPage> = observe(owner) {
        require(query.tankId > 0L && (query.subjectId == null || query.subjectId > 0L))
        val cursor = query.cursor ?: HealthObservationCursor(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        require(cursor.observedAtMillis > 0L && cursor.createdAtMillis > 0L && cursor.observationId > 0L)
        val rows = page(owner, query, cursor).map { HealthObservationRecordCodec.decode(it.toStored()) }
        val count = query.subjectId?.let { dao.countForSubject(owner, query.tankId, query.kind.name, it) }
            ?: dao.count(owner, query.tankId, query.kind.name)
        val next = rows.lastOrNull()?.let {
            HealthObservationCursor(it.input.identity.observedAtMillis, it.createdAtMillis, it.id)
        }?.takeIf { hasOlder(owner, query, it) }
        HealthObservationPage(Collections.unmodifiableList(rows), count, next)
    }

    private fun page(owner: String, query: HealthObservationQuery,
        cursor: HealthObservationCursor): List<HealthObservationEntity> = query.subjectId?.let {
        dao.pageForSubject(owner, query.tankId, query.kind.name, it,
            cursor.observedAtMillis, cursor.createdAtMillis, cursor.observationId)
    } ?: dao.page(owner, query.tankId, query.kind.name,
        cursor.observedAtMillis, cursor.createdAtMillis, cursor.observationId)

    private fun hasOlder(owner: String, query: HealthObservationQuery, cursor: HealthObservationCursor): Boolean =
        query.subjectId?.let { dao.hasOlderForSubject(owner, query.tankId, query.kind.name, it,
            cursor.observedAtMillis, cursor.createdAtMillis, cursor.observationId)
        } ?: dao.hasOlder(owner, query.tankId, query.kind.name,
            cursor.observedAtMillis, cursor.createdAtMillis, cursor.observationId)

    private fun <T> observe(owner: String, query: () -> T): Flow<T> = invalidations().map {
        require(owner.isNotBlank() && owner == owner.trim())
        withContext(Dispatchers.IO) { database.runInTransaction(Callable { query() }) }
    }

    private fun invalidations(): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer("health_observation") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        try {
            withContext(Dispatchers.IO) { database.invalidationTracker.addObserver(observer) }
            trySend(Unit)
            awaitClose()
        } finally {
            database.invalidationTracker.removeObserver(observer)
        }
    }.buffer(Channel.CONFLATED)
}
