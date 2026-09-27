package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.user.archive.ArchiveMediaReference
import com.aqua.aqualight.data.user.archive.ArchivedObservationPhoto
import com.aqua.aqualight.data.user.archive.HealthHistoryArchive
import com.aqua.aqualight.data.user.archive.HealthHistoryArchiveReference
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import java.io.File
import java.util.concurrent.Callable

internal data class HealthHistorySnapshot(
    val reference: HealthHistoryArchiveReference,
    val mediaByEntryName: Map<String, File>
)

internal class HealthObservationArchiveSnapshot(
    private val database: HealthObservationDatabase,
    private val media: UserDataArchiveMediaGateway
) {
    fun snapshot(owner: String, tankIds: Set<Long>, destination: File, mediaDirectory: File?): HealthHistorySnapshot =
        database.runInTransaction(Callable {
            val count = database.observations().countForOwner(owner)
            require(count in 0L..HealthHistoryArchive.MAX_RECORDS.toLong())
            val files = linkedMapOf<String, File>()
            val photos = mutableListOf<ArchivedObservationPhoto>()
            val records = records(owner).onEach { row ->
                require(row.input.tankId in tankIds) { "Health history contains an unarchived aquarium." }
                if (row.input.photoUri.isNotEmpty()) {
                    photos += snapshotPhoto(row, mediaDirectory, files)
                }
            }
            HealthHistorySnapshot(HealthHistoryArchive.write(records, count.toInt(), destination, photos), files)
        })

    private fun snapshotPhoto(row: StoredHealthObservation, directory: File?,
        files: MutableMap<String, File>): ArchivedObservationPhoto {
        val fingerprint = requireNotNull(media.fingerprintPhoto(row.input.photoUri, AppMediaScope.HEALTH)) {
            "A referenced observation photo could not be archived."
        }
        val entry = HealthHistoryArchive.photoEntry(row.input.tankId, row.id)
        if (directory != null) {
            val destination = File(directory, "health_${row.id}.media")
            files[entry] = requireNotNull(media.snapshotPhoto(row.input.photoUri, destination, AppMediaScope.HEALTH))
        }
        return ArchivedObservationPhoto(row.id, row.input.tankId,
            ArchiveMediaReference(entry, fingerprint.byteSize, fingerprint.sha256))
    }

    private fun records(owner: String): Sequence<StoredHealthObservation> = sequence {
        var after = 0L
        var page = database.observations().ownerPage(owner, after)
        while (page.isNotEmpty()) {
            page.forEach { row ->
                check(row.deleteState == 0) { "Observation deletion recovery must settle before archive creation." }
                yield(row.toStored())
            }
            after = page.last().observationId
            page = database.observations().ownerPage(owner, after)
        }
    }
}
