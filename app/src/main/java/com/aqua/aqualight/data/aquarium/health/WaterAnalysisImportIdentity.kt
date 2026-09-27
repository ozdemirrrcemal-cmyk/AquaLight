package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import java.security.MessageDigest
import java.util.UUID

/** The archive's source identity and frozen evidence survive owner/tank/request ID remapping. */
internal object WaterAnalysisImportIdentity {
    fun validate(record: StoredWaterAnalysis) {
        if (!record.hasImportOrigin()) return
        val origin = record.importOrigin
        if (origin.schemaVersion != 1) {
            throw WaterAnalysisReadFailure.UnsupportedValue(
                "importOrigin.schemaVersion", origin.schemaVersion.toString())
        }
        require(origin.sourceOwnerUid.isNotBlank() && origin.sourceOwnerUid == origin.sourceOwnerUid.trim())
        require(origin.sourceOwnerUid.length <= MAX_OWNER_LENGTH)
        require(origin.sourceAnalysisId > 0 && origin.sourceTankId > 0)
        require(origin.sourceRequestId.isEmpty() || WaterAnalysisPolicy.isValidRequestId(origin.sourceRequestId))
        require(WaterAnalysisPolicy.isValidRequestId(origin.restoreTransactionId))
        require(digest(original(record)) == origin.sourceRecordSha256) { "Imported analysis evidence changed." }
    }

    fun key(record: StoredWaterAnalysis): Pair<String, Long> = if (record.hasImportOrigin()) {
        record.importOrigin.sourceOwnerUid to record.importOrigin.sourceAnalysisId
    } else record.ownerUid to record.id

    fun original(record: StoredWaterAnalysis): StoredWaterAnalysis {
        if (!record.hasImportOrigin()) return record
        val origin = record.importOrigin
        return record.toBuilder().clearImportOrigin().setOwnerUid(origin.sourceOwnerUid)
            .setId(origin.sourceAnalysisId).setTankId(origin.sourceTankId)
            .setRequestId(origin.sourceRequestId).build()
    }

    fun remap(record: StoredWaterAnalysis, target: WaterAnalysisImportTarget): StoredWaterAnalysis {
        WaterAnalysisStoreRules.validateStoredAnalysis(record)
        val source = original(record)
        val origin = StoredWaterImportOrigin.newBuilder().setSchemaVersion(1)
            .setSourceOwnerUid(source.ownerUid).setSourceAnalysisId(source.id).setSourceTankId(source.tankId)
            .setSourceRequestId(source.requestId).setSourceRecordSha256(digest(source))
            .setRestoreTransactionId(target.transactionId).build()
        return record.toBuilder().setOwnerUid(target.ownerUid).setTankId(target.tankId)
            .setId(target.analysisId).setRequestId(UUID.randomUUID().toString()).setImportOrigin(origin)
            .build().also(WaterAnalysisStoreRules::validateStoredAnalysis)
    }

    fun sameEvidence(first: StoredWaterAnalysis, second: StoredWaterAnalysis): Boolean =
        original(first) == original(second)

    private fun digest(record: StoredWaterAnalysis): String = MessageDigest.getInstance("SHA-256")
        .digest(record.toByteArray()).joinToString("") { "%02x".format(it) }

    private const val MAX_OWNER_LENGTH = 128
}

internal data class WaterAnalysisImportTarget(
    val ownerUid: String,
    val tankId: Long,
    val analysisId: Long,
    val transactionId: String
)
