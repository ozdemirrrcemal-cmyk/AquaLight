package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Session -> tank -> Room, retaining both gates until an admitted disk transaction has settled. */
internal class WaterAnalysisRoomWriter(
    private val database: WaterAnalysisDatabase,
    private val session: OwnerSessionWriteLease,
    private val requireTank: suspend (String, Long) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val commits = WaterAnalysisRoomCommit(database)

    suspend fun create(draft: WaterAnalysisDraftRecord, prepare: suspend () -> StoredWaterEvaluation): Long =
        session.withWrite {
            val frozen = draft.copy(measurements = draft.measurements.toList())
            OwnerTankMutationGate.shared.withTanks(session.ownerUid, listOf(frozen.tankId)) {
                requireTank(session.ownerUid, frozen.tankId)
                val previous = withContext(dispatcher) { commits.replay(session.ownerUid, frozen) }
                previous ?: createUnderGate(frozen, prepare)
            }
        }

    suspend fun delete(tankId: Long, analysisId: Long) = session.withWrite {
        require(tankId > 0L && analysisId > 0L)
        OwnerTankMutationGate.shared.withTanks(session.ownerUid, listOf(tankId)) {
            awaitCommit { commits.delete(session.ownerUid, tankId, analysisId, session::requireCurrent) }
        }
    }

    private suspend fun createUnderGate(draft: WaterAnalysisDraftRecord,
        prepare: suspend () -> StoredWaterEvaluation): Long {
        val evaluation = prepare()
        return awaitCommit {
            commits.create(session.ownerUid, draft, evaluation, nowMillis()) {
                session.requireCurrent()
                check(!TankCareIntegrityJournal.isWriteBlocked(session.ownerUid, draft.tankId)) {
                    "Water analysis targets a tank with an active deletion transaction."
                }
            }
        }
    }

    private suspend fun <T> awaitCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + dispatcher) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }
}
