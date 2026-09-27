package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterImportEntity

/** Caller owns the surrounding Room transaction, so rows and their mappings commit together. */
internal class WaterAnalysisRoomImportIndex(private val database: WaterAnalysisDatabase) {
    fun insert(record: StoredWaterAnalysis) {
        if (!record.hasImportOrigin()) return
        WaterAnalysisImportIdentity.validate(record)
        val origin = record.importOrigin
        val previous = database.imports().original(record.ownerUid, origin.sourceOwnerUid, origin.sourceAnalysisId)
        if (previous != null) {
            requireMapping(record, previous)
            return
        }
        database.imports().insert(WaterImportEntity(record.ownerUid, origin.sourceOwnerUid,
            origin.sourceAnalysisId, record.id, origin.restoreTransactionId, origin.sourceRecordSha256))
    }

    fun previous(owner: String, source: StoredWaterAnalysis): StoredWaterAnalysis? {
        val key = WaterAnalysisImportIdentity.key(source)
        val mapping = database.imports().original(owner, key.first, key.second)
        val imported = mapping?.let { index ->
            checkNotNull(database.analyses().recordForOwner(owner, index.analysisId)).toMigrationRecord().also {
                requireMapping(it, index)
            }
        }
        val native = if (key.first == owner) database.analyses().recordForOwner(owner, key.second)
            ?.toMigrationRecord()?.takeIf { WaterAnalysisImportIdentity.key(it) == key } else null
        check(imported == null || native == null || imported == native) { "Duplicate original analysis identity." }
        return imported ?: native
    }

    fun requireMapping(record: StoredWaterAnalysis, mapping: WaterImportEntity) {
        WaterAnalysisStoreRules.validateStoredAnalysis(record)
        val origin = record.importOrigin
        check(record.hasImportOrigin() && record.ownerUid == mapping.ownerUid && record.id == mapping.analysisId &&
            origin.sourceOwnerUid == mapping.sourceOwnerUid && origin.sourceAnalysisId == mapping.sourceAnalysisId &&
            origin.sourceRecordSha256 == mapping.sourceRecordSha256 &&
            origin.restoreTransactionId == mapping.restoreTransactionId) {
            "Analysis import index differs from its event."
        }
    }
}
