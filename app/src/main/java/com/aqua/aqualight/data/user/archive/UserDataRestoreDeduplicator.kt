package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.platform.media.UserDataArchiveMediaFingerprint

/** Matches backup records to current owner data so restore can be safely repeated. */
internal class UserDataRestoreDeduplicator(
    existingAquariums: List<SavedAquariumTank>,
    existingCareTasks: List<CareTask>,
    private val ownerUid: String,
    private val snapshotTankPhoto: (String?) -> UserDataArchiveMediaFingerprint?,
    private val provenance: UserDataRestoreProvenanceSnapshot =
        UserDataRestoreProvenanceSnapshot.Empty
) {
    private val unmatchedAquariums = existingAquariums.toMutableList()
    private val unmatchedCareTasks = existingCareTasks.toMutableList()

    fun takeMatchingAquarium(archived: ArchiveAquarium): SavedAquariumTank? {
        val provenanceMatch = provenance.aquarium(archived)?.let { record ->
            unmatchedAquariums.firstOrNull { tank ->
                tank.id == record.localTankId &&
                    tank.createdAtMillis == record.localCreatedAtMillis
            }
        }
        val match = provenanceMatch ?: unmatchedAquariums.firstOrNull { tank ->
            tank.id == archived.id && tank.createdAtMillis == archived.createdAtMillis
        } ?: unmatchedAquariums.firstOrNull { tank ->
            tank.matchesArchivedContent(archived)
        }
        if (match != null) unmatchedAquariums.remove(match)
        return match
    }

    fun takeMatchingCareTask(
        archived: ArchiveCareTask,
        restoredTankId: Long
    ): CareTask? {
        val provenanceMatch = provenance.careTask(archived)?.let { record ->
            unmatchedCareTasks.firstOrNull { task ->
                task.id == record.localTaskId &&
                    task.tankId == restoredTankId &&
                    task.tankId == record.localTankId &&
                    task.createdAtMillis == record.localCreatedAtMillis
            }
        }
        val match = provenanceMatch ?: unmatchedCareTasks.firstOrNull { task ->
            task.tankId == restoredTankId &&
                task.id == archived.id &&
                task.createdAtMillis == archived.createdAtMillis
        } ?: unmatchedCareTasks.firstOrNull { task ->
            task.matchesArchivedOrigin(archived, restoredTankId)
        }
        if (match != null) unmatchedCareTasks.remove(match)
        return match
    }

    private fun SavedAquariumTank.matchesArchivedContent(archived: ArchiveAquarium): Boolean {
        val references = buildMap {
            archived.healthObservations.orEmpty().forEach { observation ->
                val current = healthObservations.firstOrNull { it.id == observation.id }
                observation.photo?.let { reference ->
                    current?.photoUri?.let { uri ->
                        if (matchesFingerprint(uri, reference)) put(reference.entryName, reference)
                    }
                }
                observation.checks.orEmpty().forEach { check ->
                    val uri = current?.checks?.firstOrNull { it.id == check.id }?.photoUri
                    if (uri != null && check.photo != null && matchesFingerprint(uri, check.photo)) {
                        put(check.photo.entryName, check.photo)
                    }
                }
            }
        }
        val currentArchive = toArchiveAquarium(archived.photo, references)
        val normalizedCurrent = currentArchive.copy(
            id = archived.id,
            createdAtMillis = archived.createdAtMillis,
            healthObservations = if (archived.healthObservations == null && healthObservations.isEmpty())
                null else currentArchive.healthObservations
        )
        return normalizedCurrent == archived && matchesArchivedPhoto(archived.photo)
    }

    private fun matchesFingerprint(uri: String?, reference: ArchiveMediaReference): Boolean {
        val fingerprint = snapshotTankPhoto(uri) ?: return false
        return fingerprint.byteSize == reference.byteSize &&
            fingerprint.sha256.equals(reference.sha256, ignoreCase = true)
    }

    private fun SavedAquariumTank.matchesArchivedPhoto(
        reference: ArchiveMediaReference?
    ): Boolean {
        return if (reference == null) {
            photoUri.isNullOrBlank()
        } else {
            matchesFingerprint(photoUri, reference)
        }
    }

    private fun CareTask.matchesArchivedOrigin(
        archived: ArchiveCareTask,
        restoredTankId: Long
    ): Boolean {
        val restored = archived.toCareTask(
            ownerUid = ownerUid,
            restoredTankId = restoredTankId,
            restoredTaskId = id
        )
        return tankId == restoredTankId &&
            createdAtMillis == restored.createdAtMillis &&
            type == restored.type &&
            source == restored.source &&
            title == restored.title &&
            generatedRuleKey == restored.generatedRuleKey
    }
}
