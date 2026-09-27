package com.aqua.aqualight.data.aquarium.health

import androidx.room.InvalidationTracker
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import com.aqua.aqualight.application.aquarium.health.WaterHistoryPage
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import java.util.concurrent.Callable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** All disk access is dispatched; observers are removed when their route collector is cancelled. */
internal class WaterAnalysisRoomQueries(
    private val database: WaterAnalysisDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val dao = database.analyses()

    fun latest(owner: String, tankId: Long): Flow<WaterAnalysisSnapshot?> = observe(owner) {
        require(tankId > 0L)
        dao.latest(owner, tankId)?.toMigrationRecord()?.validatedSnapshot()
    }

    fun record(owner: String, tankId: Long, analysisId: Long): Flow<WaterAnalysisSnapshot?> = observe(owner) {
        require(tankId > 0L && analysisId > 0L)
        dao.record(owner, tankId, analysisId)?.toMigrationRecord()?.validatedSnapshot()
    }

    fun atOrBefore(owner: String, tankId: Long, observedAtMillis: Long): Flow<WaterAnalysisSnapshot?> = observe(owner) {
        require(tankId > 0L && observedAtMillis > 0L)
        dao.atOrBefore(owner, tankId, observedAtMillis)?.toMigrationRecord()?.validatedSnapshot()
    }

    fun page(owner: String, tankId: Long, after: WaterHistoryCursor?, newer: Boolean = false): Flow<WaterHistoryPage> =
        observe(owner) {
        require(tankId > 0L && (after == null || after.tankId == tankId))
        require(after != null || !newer)
        val rows = when {
            after == null -> dao.firstPage(owner, tankId)
            newer -> dao.pageBefore(owner, tankId, after.observedAtMillis, after.createdAtMillis,
                after.analysisId).asReversed()
            else -> dao.pageAfter(owner, tankId, after.observedAtMillis, after.createdAtMillis, after.analysisId)
        }.ifEmpty { dao.firstPage(owner, tankId) }.map { it.toMigrationRecord().validatedSnapshot() }
        val first = rows.firstOrNull()?.cursor()
        val last = rows.lastOrNull()?.cursor()
        WaterHistoryPage(rows, dao.countForTank(owner, tankId),
            last?.takeIf { dao.hasOlder(owner, tankId, it.observedAtMillis, it.createdAtMillis, it.analysisId) },
            first?.takeIf { dao.hasNewer(owner, tankId, it.observedAtMillis, it.createdAtMillis, it.analysisId) })
    }

    private fun WaterAnalysisSnapshot.cursor() = WaterHistoryCursor(tankId, measuredAtMillis, createdAtMillis, id)

    private fun <T> observe(owner: String, query: () -> T): Flow<T> = invalidations().map {
        withContext(dispatcher) {
            database.runInTransaction(Callable {
                WaterAnalysisRoomCommit(database).requireActive(owner)
                query()
            })
        }
    }

    private fun invalidations(): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer("water_analysis", "water_analysis_migration") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        try {
            withContext(dispatcher) { database.invalidationTracker.addObserver(observer) }
            trySend(Unit)
            awaitClose()
        } finally {
            // Also covers cancellation after registration but before dispatch returns to this builder.
            database.invalidationTracker.removeObserver(observer)
        }
    }.buffer(Channel.CONFLATED)

    private fun StoredWaterAnalysis.validatedSnapshot(): WaterAnalysisSnapshot =
        WaterAnalysisStoreRules.validateStoredAnalysis(this).toRecordStrict().toApplicationSnapshot()
}
