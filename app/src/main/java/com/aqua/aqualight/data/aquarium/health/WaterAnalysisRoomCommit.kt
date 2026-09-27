package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisRequestException
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisRequestFailure
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import com.aqua.aqualight.data.aquarium.health.room.WaterRequestEntity
import java.util.concurrent.Callable

/** Blocking Room transaction primitives. The session/tank owner must hold its gates through completion. */
internal class WaterAnalysisRoomCommit(private val database: WaterAnalysisDatabase) {
    private val dao = database.analyses()

    fun replay(owner: String, draft: WaterAnalysisDraftRecord): Long? {
        requireActive(owner)
        val request = dao.request(owner, draft.requestId) ?: return null
        if (request.payloadSha256 != WaterAnalysisRequestFingerprint.of(draft)) {
            throw WaterAnalysisRequestException(WaterAnalysisRequestFailure.PAYLOAD_CHANGED)
        }
        if (dao.record(owner, draft.tankId, request.analysisId) == null) {
            throw WaterAnalysisRequestException(WaterAnalysisRequestFailure.RECORD_DELETED)
        }
        return request.analysisId
    }

    fun create(owner: String, draft: WaterAnalysisDraftRecord, evaluation: StoredWaterEvaluation?,
        nowMillis: Long, requireAuthority: () -> Unit): Long = database.runInTransaction(Callable {
        requireAuthority()
        require(WaterAnalysisPolicy.isValidRequestId(draft.requestId))
        replay(owner, draft) ?: insert(owner, draft, evaluation, nowMillis)
    })

    fun delete(owner: String, tankId: Long, analysisId: Long, requireAuthority: () -> Unit) {
        database.runInTransaction {
            requireAuthority()
            requireActive(owner)
            dao.delete(owner, tankId, analysisId)
            // The request survives deletion. Both identical and modified delayed retries are rejected.
        }
    }

    fun requireActive(owner: String) {
        require(owner.isNotBlank() && owner == owner.trim())
        check(dao.migration(owner)?.state == WaterMigrationEntity.ACTIVE) {
            "Water history has not completed verified cutover."
        }
    }

    private fun insert(owner: String, draft: WaterAnalysisDraftRecord,
        evaluation: StoredWaterEvaluation?, nowMillis: Long): Long {
        val previousId = dao.lastAllocatedId(owner)
        check(previousId < Long.MAX_VALUE) { "Water analysis identities are exhausted." }
        val record = WaterAnalysisRecord(maxOf(nowMillis, previousId + 1L), owner, draft.tankId,
            draft.measuredAtMillis, draft.temperatureCelsius, draft.temperatureSource,
            draft.measurements, nowMillis, draft.requestId, evaluation)
        WaterAnalysisStoreRules.validateRecord(record, owner)
        dao.insert(listOf(record.toStoredStrict().toMigrationEntity()))
        dao.insertRequests(listOf(WaterRequestEntity(owner, draft.requestId, record.id,
            WaterAnalysisRequestFingerprint.of(draft))))
        return record.id
    }
}
