package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationRecordRules
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationImportIdentity
import com.aqua.aqualight.data.aquarium.health.observation.StoredHealthObservation
import com.google.gson.annotations.SerializedName
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

internal data class HealthHistoryArchiveReference(
    @field:SerializedName("entryName") val entryName: String,
    @field:SerializedName("formatVersion") val formatVersion: Int,
    @field:SerializedName("recordCount") val recordCount: Int,
    @field:SerializedName("byteSize") val byteSize: Long,
    @field:SerializedName("sha256") val sha256: String,
    @field:SerializedName("photos") val photos: List<ArchivedObservationPhoto>
)

internal data class ArchivedObservationPhoto(
    @field:SerializedName("observationId") val observationId: Long,
    @field:SerializedName("tankId") val tankId: Long,
    @field:SerializedName("media") val media: ArchiveMediaReference
)

/** Independent observation stream; water events and their original evidence are never rewritten. */
internal object HealthHistoryArchive {
    const val ENTRY = "history/observations-v1.bin"
    const val FORMAT_VERSION = 1
    const val MAX_RECORDS = 100_000
    const val MAX_RECORD_BYTES = 2 * 1024 * 1024
    private const val MAGIC = "AQLHLTH1"
    val emptyBytes: ByteArray get() = MAGIC.toByteArray(Charsets.US_ASCII) + ByteArray(Int.SIZE_BYTES)
    val emptyReference get() = HealthHistoryArchiveReference(ENTRY, FORMAT_VERSION, 0,
        emptyBytes.size.toLong(), sha256(emptyBytes), emptyList())

    fun write(records: Sequence<StoredHealthObservation>, count: Int, destination: File,
        photos: List<ArchivedObservationPhoto>): HealthHistoryArchiveReference {
        require(count in 0..MAX_RECORDS)
        var complete = false
        try {
            DataOutputStream(destination.outputStream().buffered()).use { output ->
                output.writeBytes(MAGIC)
                output.writeInt(count)
                var written = 0
                var size = MAGIC.length.toLong() + Int.SIZE_BYTES
                records.forEach { record ->
                    require(++written <= count)
                    HealthObservationRecordRules.validate(record)
                    val bytes = record.toByteArray()
                    require(bytes.size in 1..MAX_RECORD_BYTES)
                    size += Int.SIZE_BYTES + bytes.size
                    require(size <= UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES)
                    output.writeInt(bytes.size)
                    output.write(bytes)
                }
                require(written == count) { "Observation snapshot changed during archive creation." }
            }
            val reference = HealthHistoryArchiveReference(ENTRY, FORMAT_VERSION, count,
                destination.length(), sha256(destination), photos.toList())
            complete = true
            return reference
        } finally {
            if (!complete) destination.delete()
        }
    }

    fun validate(reference: HealthHistoryArchiveReference, file: File, tankIds: Set<Long>) {
        require(reference.entryName == ENTRY && reference.formatVersion == FORMAT_VERSION)
        require(reference.recordCount in 0..MAX_RECORDS)
        require(reference.byteSize in 1L..UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES)
        require(file.isFile && file.length() == reference.byteSize && sha256(file) == reference.sha256)
        val photos = reference.photos.associateBy { it.observationId }
        require(photos.size == reference.photos.size)
        val identities = hashSetOf<Long>()
        val origins = hashSetOf<Pair<String, Long>>()
        val requests = hashSetOf<String>()
        val photoIds = hashSetOf<Long>()
        var owner: String? = null
        var previousId = 0L
        visit(file, reference.recordCount) { row ->
            require(row.input.tankId in tankIds)
            require(row.id > previousId) { "Observation archive identity order is invalid." }
            previousId = row.id
            require(!row.input.hasPreviousObservationId() || row.input.previousObservationId < row.id)
            require(origins.add(HealthObservationImportIdentity.key(row))) { "Duplicate observation origin." }
            if (owner == null) owner = row.ownerUid
            require(owner == row.ownerUid && identities.add(row.id) && requests.add(row.input.requestId))
            val photo = photos[row.id]
            require(row.input.photoUri.isNotEmpty() == (photo != null))
            photo?.let {
                require(it.tankId == row.input.tankId && it.media.entryName == photoEntry(it.tankId, row.id))
                photoIds += row.id
            }
        }
        require(photoIds == photos.keys) { "Observation archive has unreferenced photos." }
    }

    fun visit(file: File, count: Int, consume: (StoredHealthObservation) -> Unit) {
        require(count in 0..MAX_RECORDS)
        DataInputStream(file.inputStream().buffered()).use { input ->
            val magic = ByteArray(MAGIC.length).also(input::readFully)
            require(magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII)) && input.readInt() == count)
            repeat(count) {
                val length = input.readInt()
                require(length in 1..MAX_RECORD_BYTES)
                val row = StoredHealthObservation.parseFrom(ByteArray(length).also(input::readFully))
                HealthObservationRecordRules.validate(row)
                consume(row)
            }
            require(input.read() == -1) { "Observation archive has trailing data." }
        }
    }

    fun photoEntry(tank: Long, observation: Long) = "media/tanks/${tank}_health_$observation.jpg"
}
