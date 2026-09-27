package com.aqua.aqualight.data.aquarium.health

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Collections

internal data class WaterAnalysisMigrationManifest(
    val ownerUid: String,
    val sourceSha256: String,
    val recordCount: Int,
    val recordsSha256: String
)

internal class WaterAnalysisMigrationMismatch : IOException("Water-analysis migration verification failed.")

/** Read-only migration input. Keep the original file until durable cutover has been verified. */
internal class WaterAnalysisMigrationSource private constructor(
    val manifest: WaterAnalysisMigrationManifest,
    private val records: List<StoredWaterAnalysis>
) {
    /** An inclusive saved checkpoint is skipped; each retry yields the same immutable rows. */
    fun batchAfter(analysisId: Long): List<StoredWaterAnalysis> {
        require(analysisId >= 0L) { "Migration checkpoint must be nonnegative." }
        val position = records.binarySearchBy(analysisId, selector = StoredWaterAnalysis::getId)
        val start = if (position >= 0) position + 1 else -position - 1
        return Collections.unmodifiableList(records.subList(start, minOf(start + BATCH_SIZE, records.size)))
    }

    /** Destination must stream this owner's raw rows in ascending ID order from one read transaction. */
    fun verify(destination: Sequence<StoredWaterAnalysis>) {
        val digest = WaterAnalysisMigrationDigest(manifest.ownerUid)
        destination.forEach { record ->
            if (digest.count >= manifest.recordCount) throw WaterAnalysisMigrationMismatch()
            digest.add(record)
        }
        if (digest.count != manifest.recordCount || digest.finish() != manifest.recordsSha256) {
            throw WaterAnalysisMigrationMismatch()
        }
    }

    companion object {
        const val BATCH_SIZE = 50

        /** Caller owns the input stream. Strict validation covers all owners before any owner is selected. */
        fun readFrom(input: InputStream, ownerUid: String): WaterAnalysisMigrationSource {
            require(ownerUid.isNotBlank() && ownerUid == ownerUid.trim()) { "A canonical owner is required." }
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            val store = WaterAnalysisLegacyReader.readFrom(DigestInputStream(input, sourceDigest))
            val records = store.analysesList.filter { it.ownerUid == ownerUid }.sortedBy { it.id }
            val digest = WaterAnalysisMigrationDigest(ownerUid)
            records.forEach(digest::add)
            val manifest = WaterAnalysisMigrationManifest(
                ownerUid, sourceDigest.digest().hexString(), digest.count, digest.finish()
            )
            return WaterAnalysisMigrationSource(manifest, records)
        }
    }
}

/** Length-framed exact Proto bytes retain optional fields, source precision and unknown wire fields. */
private class WaterAnalysisMigrationDigest(private val ownerUid: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var lastId = 0L
    var count: Int = 0
        private set

    init {
        digest.update("AquaLight.WaterAnalysisMigration.v1\n".toByteArray(Charsets.UTF_8))
        addBytes(ownerUid.toByteArray(Charsets.UTF_8))
    }

    fun add(record: StoredWaterAnalysis) {
        if (record.ownerUid != ownerUid || record.id <= lastId) throw WaterAnalysisMigrationMismatch()
        addBytes(record.toByteArray())
        lastId = record.id
        count += 1
    }

    fun finish(): String = digest.digest().hexString()

    private fun addBytes(bytes: ByteArray) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
}

private fun ByteArray.hexString(): String = joinToString("") { "%02x".format(it) }
