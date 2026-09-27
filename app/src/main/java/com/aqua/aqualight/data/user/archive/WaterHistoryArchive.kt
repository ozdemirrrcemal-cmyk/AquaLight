package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.StoredWaterAnalysis
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisStoreRules
import com.google.gson.annotations.SerializedName
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

internal data class WaterHistoryArchiveReference(
    @field:SerializedName("entryName") val entryName: String,
    @field:SerializedName("formatVersion") val formatVersion: Int,
    @field:SerializedName("recordCount") val recordCount: Int,
    @field:SerializedName("byteSize") val byteSize: Long,
    @field:SerializedName("sha256") val sha256: String
)

/** Length-framed, exact Proto events; only one bounded event is decoded at a time. */
internal object WaterHistoryArchive {
    const val ENTRY = "history/water-v1.bin"
    const val FORMAT_VERSION = 1
    const val MAX_RECORDS = 100_000
    const val MAX_RECORD_BYTES = 2 * 1024 * 1024
    private const val MAGIC = "AQLWATR1"

    val emptyBytes: ByteArray get() = MAGIC.toByteArray(Charsets.US_ASCII) + ByteArray(Int.SIZE_BYTES)
    val emptyReference: WaterHistoryArchiveReference get() = WaterHistoryArchiveReference(
        ENTRY, FORMAT_VERSION, 0, emptyBytes.size.toLong(), sha256(emptyBytes))

    fun write(records: Sequence<StoredWaterAnalysis>, count: Int, destination: File): WaterHistoryArchiveReference {
        require(count in 0..MAX_RECORDS)
        var completed = false
        try {
            DataOutputStream(destination.outputStream().buffered()).use { output ->
                output.writeBytes(MAGIC)
                output.writeInt(count)
                var written = 0
                var bytes = MAGIC.length.toLong() + Int.SIZE_BYTES
                records.forEach { record ->
                    require(++written <= count)
                    WaterAnalysisStoreRules.validateStoredAnalysis(record)
                    val raw = record.toByteArray()
                    require(raw.size in 1..MAX_RECORD_BYTES)
                    bytes += Int.SIZE_BYTES + raw.size
                    require(bytes <= UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES)
                    output.writeInt(raw.size)
                    output.write(raw)
                }
                require(written == count) { "Analysis snapshot changed while archiving." }
            }
            val reference = WaterHistoryArchiveReference(ENTRY, FORMAT_VERSION, count,
                destination.length(), sha256(destination))
            completed = true
            return reference
        } finally {
            if (!completed) destination.delete()
        }
    }

    fun validate(reference: WaterHistoryArchiveReference, file: File, tankIds: Set<Long>) {
        require(reference.entryName == ENTRY && reference.formatVersion == FORMAT_VERSION)
        require(reference.recordCount in 0..MAX_RECORDS)
        require(reference.byteSize in 1L..UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES)
        require(file.isFile && file.length() == reference.byteSize)
        require(sha256(file) == reference.sha256) { "Analysis archive checksum mismatch." }
        val ids = hashSetOf<Long>()
        val requests = hashSetOf<String>()
        var owner: String? = null
        visit(file, reference.recordCount) { record ->
            require(record.tankId in tankIds) { "Archived analysis references a missing aquarium." }
            if (owner == null) owner = record.ownerUid
            require(owner == record.ownerUid) { "Analysis archive mixes owners." }
            require(ids.add(record.id)) { "Duplicate archived analysis identity." }
            require(record.requestId.isEmpty() || requests.add(record.requestId)) {
                "Duplicate archived analysis request identity."
            }
        }
    }

    fun visit(file: File, count: Int, consume: (StoredWaterAnalysis) -> Unit) {
        require(count in 0..MAX_RECORDS)
        DataInputStream(file.inputStream().buffered()).use { input ->
            val magic = ByteArray(MAGIC.length).also(input::readFully)
            require(magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII)))
            require(input.readInt() == count) { "Analysis archive count mismatch." }
            repeat(count) {
                val length = input.readInt()
                require(length in 1..MAX_RECORD_BYTES) { "Analysis archive record size is invalid." }
                val bytes = ByteArray(length).also(input::readFully)
                val record = StoredWaterAnalysis.parseFrom(bytes)
                WaterAnalysisStoreRules.validateStoredAnalysis(record)
                consume(record)
            }
            require(input.read() == -1) { "Analysis archive has trailing data." }
        }
    }
}
