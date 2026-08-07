package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask

/** Matches backup records to current owner data so restore can be safely repeated. */
internal class UserDataRestoreDeduplicator(
    existingAquariums: List<SavedAquariumTank>,
    existingCareTasks: List<CareTask>,
    private val ownerUid: String,
    private val snapshotTankPhoto: (String?) -> ByteArray?
) {
    private val unmatchedAquariums = existingAquariums.toMutableList()
    private val unmatchedCareTasks = existingCareTasks.toMutableList()

    fun takeMatchingAquarium(archived: ArchiveAquarium): SavedAquariumTank? {
        val match = unmatchedAquariums.firstOrNull { tank ->
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
        val match = unmatchedCareTasks.firstOrNull { task ->
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
        val normalizedCurrent = toArchiveAquarium(archived.photo).copy(
            id = archived.id,
            createdAtMillis = archived.createdAtMillis
        )
        return normalizedCurrent == archived && matchesArchivedPhoto(archived.photo)
    }

    private fun SavedAquariumTank.matchesArchivedPhoto(
        reference: ArchiveMediaReference?
    ): Boolean {
        if (reference == null) return photoUri.isNullOrBlank()
        val bytes = snapshotTankPhoto(photoUri) ?: return false
        return bytes.size == reference.byteSize &&
            sha256(bytes).equals(reference.sha256, ignoreCase = true)
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
