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
        UserDataRestoreProvenanceSnapshot.Empty,
    private val snapshotPlantPhoto: (String?) -> UserDataArchiveMediaFingerprint? = { null }
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
        val plantPhotoReferences = archived.plants.mapNotNull { plant ->
            plant.photo?.let { reference -> plant.id to reference }
        }.toMap()
        val normalizedCurrent = toArchiveAquarium(
            photoReference = archived.photo,
            plantPhotoReferences = plantPhotoReferences
        ).copy(
            id = archived.id,
            createdAtMillis = archived.createdAtMillis
        )
        return normalizedCurrent == archived &&
            matchesArchivedTankPhoto(archived.photo) &&
            matchesArchivedPlantPhotos(archived)
    }

    private fun SavedAquariumTank.matchesArchivedTankPhoto(
        reference: ArchiveMediaReference?
    ): Boolean {
        return if (reference == null) {
            photoUri.isNullOrBlank()
        } else {
            snapshotTankPhoto(photoUri).matches(reference)
        }
    }

    private fun SavedAquariumTank.matchesArchivedPlantPhotos(
        archived: ArchiveAquarium
    ): Boolean {
        val currentPlantsById = plants.associateBy { plant -> plant.id }
        return archived.plants.all { archivedPlant ->
            val currentPlant = currentPlantsById[archivedPlant.id] ?: return@all false
            val reference = archivedPlant.photo
            if (reference == null) {
                currentPlant.photoUri.isNullOrBlank()
            } else {
                snapshotPlantPhoto(currentPlant.photoUri).matches(reference)
            }
        }
    }

    private fun UserDataArchiveMediaFingerprint?.matches(
        reference: ArchiveMediaReference
    ): Boolean {
        return this != null &&
            byteSize == reference.byteSize &&
            sha256.equals(reference.sha256, ignoreCase = true)
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
