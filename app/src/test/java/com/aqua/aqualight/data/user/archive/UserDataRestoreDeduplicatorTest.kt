package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.platform.media.UserDataArchiveMediaFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserDataRestoreDeduplicatorTest {

    @Test
    fun `same archive identity keeps the current aquarium version`() {
        val current = savedTank(name = "Edited current name")
        val archived = archivedTank(name = "Original backup name")
        val deduplicator = deduplicator(existingAquariums = listOf(current))

        assertEquals(current, deduplicator.takeMatchingAquarium(archived))
    }

    @Test
    fun `same restored aquarium content is consumed only once`() {
        val current = savedTank(id = 99L, createdAtMillis = 2_000L)
        val archived = archivedTank()
        val deduplicator = deduplicator(existingAquariums = listOf(current))

        assertEquals(current, deduplicator.takeMatchingAquarium(archived))
        assertNull(deduplicator.takeMatchingAquarium(archived))
    }

    @Test
    fun `content fallback does not merge tanks with different photos`() {
        val backupPhoto = "backup-photo".toByteArray()
        val archived = archivedTank().copy(
            photo = ArchiveMediaReference(
                entryName = "media/tanks/7.jpg",
                byteSize = backupPhoto.size,
                sha256 = sha256(backupPhoto)
            )
        )
        val current = savedTank(
            id = 99L,
            createdAtMillis = 2_000L,
            photoUri = "current-photo"
        )
        val differentPhoto = "different-photo".toByteArray()
        val deduplicator = UserDataRestoreDeduplicator(
            existingAquariums = listOf(current),
            existingCareTasks = emptyList(),
            ownerUid = OWNER_UID,
            snapshotTankPhoto = {
                UserDataArchiveMediaFingerprint(
                    byteSize = differentPhoto.size,
                    sha256 = sha256(differentPhoto)
                )
            }
        )

        assertNull(deduplicator.takeMatchingAquarium(archived))
    }

    @Test
    fun `restored care task origin is reused after its mutable state changes`() {
        val archived = archivedCareTask()
        val current = CareTask(
            id = 777L,
            ownerUid = OWNER_UID,
            tankId = 99L,
            title = archived.title,
            description = archived.description,
            type = CareTaskType.valueOf(archived.type),
            source = CareTaskSource.valueOf(archived.source),
            status = CareTaskStatus.COMPLETED,
            dueAtMillis = archived.dueAtMillis,
            completedAtMillis = 2_100L,
            repeatEnabled = archived.repeatEnabled,
            repeatIntervalDays = archived.repeatIntervalDays,
            reminderEnabled = archived.reminderEnabled,
            missedReminderEnabled = archived.missedReminderEnabled,
            missedReminderDays = archived.missedReminderDays,
            waterChangePercent = archived.waterChangePercent,
            note = archived.note,
            generatedRuleKey = archived.generatedRuleKey,
            createdAtMillis = archived.createdAtMillis,
            updatedAtMillis = 2_100L
        )
        val deduplicator = deduplicator(existingCareTasks = listOf(current))

        assertEquals(
            current,
            deduplicator.takeMatchingCareTask(archived, restoredTankId = 99L)
        )
    }

    private fun deduplicator(
        existingAquariums: List<SavedAquariumTank> = emptyList(),
        existingCareTasks: List<CareTask> = emptyList()
    ): UserDataRestoreDeduplicator {
        return UserDataRestoreDeduplicator(
            existingAquariums = existingAquariums,
            existingCareTasks = existingCareTasks,
            ownerUid = OWNER_UID,
            snapshotTankPhoto = { null }
        )
    }

    private fun savedTank(
        id: Long = 7L,
        createdAtMillis: Long = 900L,
        name: String = "Display Tank",
        photoUri: String? = null
    ): SavedAquariumTank {
        return SavedAquariumTank(
            id = id,
            ownerUid = OWNER_UID,
            name = name,
            description = "",
            photoUri = photoUri,
            setupDateEpochDay = null,
            widthCm = 60,
            lengthCm = 30,
            heightCm = 36,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "freshwater",
            tankStyle = "nature",
            createdAtMillis = createdAtMillis,
            smartCareEnabled = true,
            careRemindersEnabled = true,
            plants = emptyList(),
            materials = emptyList(),
            livestock = emptyList()
        )
    }

    private fun archivedTank(
        id: Long = 7L,
        createdAtMillis: Long = 900L,
        name: String = "Display Tank"
    ): ArchiveAquarium {
        return ArchiveAquarium(
            id = id,
            name = name,
            description = "",
            photo = null,
            setupDateEpochDay = null,
            widthCm = 60,
            lengthCm = 30,
            heightCm = 36,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "freshwater",
            tankStyle = "nature",
            createdAtMillis = createdAtMillis,
            smartCareEnabled = true,
            careRemindersEnabled = true,
            plants = emptyList(),
            materials = emptyList(),
            livestock = emptyList()
        )
    }

    private fun archivedCareTask(): ArchiveCareTask {
        return ArchiveCareTask(
            id = 41L,
            tankId = 7L,
            title = "Test water",
            description = "",
            type = CareTaskType.WATER_TEST.name,
            source = CareTaskSource.MANUAL.name,
            status = CareTaskStatus.PENDING.name,
            dueAtMillis = 2_000L,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = 0,
            reminderEnabled = true,
            missedReminderEnabled = false,
            missedReminderDays = 0,
            waterChangePercent = null,
            note = "",
            generatedRuleKey = "",
            createdAtMillis = 800L,
            updatedAtMillis = 900L
        )
    }

    private companion object {
        const val OWNER_UID = "owner-1"
    }
}
