package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionStageEntity
import java.nio.ByteBuffer
import java.security.MessageDigest

internal class WaterDeletionStageDigest(owner: String, tankId: Long, transactionId: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    var count = 0L
        private set

    init {
        addBytes("AquaLight.WaterDeletion.v1".toByteArray())
        addBytes(owner.toByteArray())
        addBytes(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(tankId).array())
        addBytes(transactionId.toByteArray())
    }

    fun add(row: WaterDeletionStageEntity) {
        addBytes(row.rawProto)
        count += 1L
    }

    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it) }

    private fun addBytes(bytes: ByteArray) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
}

internal fun WaterDeletionStageEntity.validatedRecord(): StoredWaterAnalysis {
    val record = WaterAnalysisStoreRules.validateStoredAnalysis(StoredWaterAnalysis.parseFrom(rawProto))
    check(record.ownerUid == ownerUid && record.tankId == tankId && record.id == analysisId) {
        "Water deletion stage identity does not match its payload."
    }
    return record
}
