package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.security.MessageDigest

internal object UserDataBackupLimits {
    const val MANIFEST_ENTRY = "manifest.json"
    const val MEDIA_PREFIX = "media/tanks/"
    const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
    const val MAX_UNCOMPRESSED_ARCHIVE_BYTES = 64 * 1024 * 1024
    const val MAX_MEDIA_ENTRY_BYTES = 8 * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 256
    const val MAX_AQUARIUMS = 100
    const val MAX_CARE_TASKS = 10_000
    const val MAX_DEVICE_ASSIGNMENTS = 500
    const val MAX_ITEMS_PER_AQUARIUM = 2_000
    const val BUFFER_SIZE = 8 * 1024

    val mediaEntryPattern = Regex("media/tanks/[1-9][0-9]*\\.jpg")
    val sha256Pattern = Regex("[0-9a-fA-F]{64}")
}

internal class UserDataBackupValidator {

    fun validate(
        manifest: UserDataBackupManifest,
        mediaByEntryName: Map<String, ByteArray>
    ) {
        validateEnvelope(manifest)
        val tankIds = validateAquariums(manifest.aquariums)
        validateCareTasks(manifest.careTasks, tankIds)
        validateAssignments(manifest.deviceAssignments, tankIds)
        validateMedia(manifest.aquariums, mediaByEntryName)
    }

    fun requireSafeEntryName(entryName: String) {
        requireSafeArchiveEntryName(entryName)
    }

    fun requireValidMediaEntryName(entryName: String) {
        requireValidArchiveMediaEntryName(entryName)
    }

    private fun validateEnvelope(manifest: UserDataBackupManifest) {
        require(manifest.format == USER_DATA_BACKUP_FORMAT) {
            "Unsupported backup format."
        }
        require(manifest.schemaVersion == USER_DATA_BACKUP_SCHEMA_VERSION) {
            "Unsupported backup schema version."
        }
        require(manifest.createdAtMillis > 0L) {
            "Backup creation time is invalid."
        }
        require(manifest.sourceAppVersion.isNotBlank()) {
            "Backup source application version is missing."
        }
        require(manifest.aquariums.size <= UserDataBackupLimits.MAX_AQUARIUMS) {
            "Backup contains too many aquariums."
        }
        require(manifest.careTasks.size <= UserDataBackupLimits.MAX_CARE_TASKS) {
            "Backup contains too many care tasks."
        }
        require(manifest.deviceAssignments.size <= UserDataBackupLimits.MAX_DEVICE_ASSIGNMENTS) {
            "Backup contains too many device assignments."
        }
    }

    private fun validateAquariums(aquariums: List<ArchiveAquarium>): Set<Long> {
        val tankIds = aquariums.map(ArchiveAquarium::id)
        require(tankIds.all { tankId -> tankId > 0L }) {
            "Backup contains an invalid aquarium id."
        }
        require(tankIds.distinct().size == tankIds.size) {
            "Backup contains duplicate aquarium ids."
        }
        aquariums.forEach(::validateAquarium)
        return tankIds.toSet()
    }

    private fun validateAquarium(aquarium: ArchiveAquarium) {
        require(aquarium.name.isNotBlank()) { "Backup aquarium name is blank." }
        require(aquarium.widthCm > 0 && aquarium.lengthCm > 0 && aquarium.heightCm > 0) {
            "Backup aquarium dimensions are invalid."
        }
        require(aquarium.sizeUnit.isNotBlank() && aquarium.volumeUnit.isNotBlank()) {
            "Backup aquarium units are invalid."
        }
        require(aquarium.createdAtMillis > 0L) {
            "Backup aquarium creation time is invalid."
        }
        validateArchiveItemIds(aquarium.plants.map(ArchivePlant::id))
        validateArchiveItemIds(aquarium.materials.map(ArchiveMaterial::id))
        validateArchiveItemIds(aquarium.livestock.map(ArchiveLivestock::id))
        aquarium.livestock.forEach { livestock ->
            require(livestock.quantity > 0) {
                "Backup livestock quantity is invalid."
            }
        }
    }

    private fun validateCareTasks(
        tasks: List<ArchiveCareTask>,
        tankIds: Set<Long>
    ) {
        val taskIds = tasks.map(ArchiveCareTask::id)
        require(taskIds.all { taskId -> taskId > 0L }) {
            "Backup contains an invalid care task id."
        }
        require(taskIds.distinct().size == taskIds.size) {
            "Backup contains duplicate care task ids."
        }
        tasks.forEach { task -> validateCareTask(task, tankIds) }
    }

    private fun validateCareTask(task: ArchiveCareTask, tankIds: Set<Long>) {
        require(task.tankId in tankIds) {
            "Backup care task references an unknown aquarium."
        }
        require(task.title.isNotBlank()) { "Backup care task title is blank." }
        require(task.dueAtMillis > 0L) { "Backup care task due time is invalid." }
        require(task.createdAtMillis > 0L && task.updatedAtMillis > 0L) {
            "Backup care task timestamps are invalid."
        }
        requireArchiveEnumValue<CareTaskType>(task.type, "type")
        requireArchiveEnumValue<CareTaskSource>(task.source, "source")
        requireArchiveEnumValue<CareTaskStatus>(task.status, "status")
    }

    private fun validateAssignments(
        assignments: List<ArchiveDeviceAssignment>,
        tankIds: Set<Long>
    ) {
        val devices = mutableSetOf<String>()
        assignments.forEach { assignment ->
            require(assignment.tankId in tankIds) {
                "Backup device assignment references an unknown aquarium."
            }
            require(assignment.deviceUid.isNotBlank() && assignment.deviceUid == assignment.deviceUid.trim()) {
                "Backup device assignment device id is invalid."
            }
            require(assignment.assignedAtMillis > 0L) {
                "Backup device assignment time is invalid."
            }
            require(devices.add(assignment.deviceUid)) {
                "Backup assigns one device to more than one aquarium."
            }
        }
    }

    private fun validateMedia(
        aquariums: List<ArchiveAquarium>,
        mediaByEntryName: Map<String, ByteArray>
    ) {
        val referenced = aquariums.mapNotNull { aquarium ->
            aquarium.photo?.also { reference ->
                validateMediaReference(reference, aquarium.id, mediaByEntryName)
            }?.entryName
        }.toSet()
        require(referenced.size == aquariums.count { aquarium -> aquarium.photo != null }) {
            "Backup reuses a media entry for multiple aquariums."
        }
        require(mediaByEntryName.keys == referenced) {
            "Backup contains unreferenced or missing media entries."
        }
    }

}

private fun requireSafeArchiveEntryName(entryName: String) {
    require(entryName.isNotBlank())
    require(!entryName.startsWith('/'))
    require('\\' !in entryName)
    require(entryName.split('/').none { segment -> segment == ".." || segment.isBlank() })
}

private fun requireValidArchiveMediaEntryName(entryName: String) {
    requireSafeArchiveEntryName(entryName)
    require(UserDataBackupLimits.mediaEntryPattern.matches(entryName)) {
        "Backup media entry name is invalid."
    }
}

private fun validateMediaReference(
    reference: ArchiveMediaReference,
    tankId: Long,
    mediaByEntryName: Map<String, ByteArray>
) {
    requireValidArchiveMediaEntryName(reference.entryName)
    require(reference.entryName == "${UserDataBackupLimits.MEDIA_PREFIX}$tankId.jpg") {
        "Backup aquarium photo entry does not match its aquarium."
    }
    require(reference.byteSize in 1..UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES) {
        "Backup aquarium photo size is invalid."
    }
    require(UserDataBackupLimits.sha256Pattern.matches(reference.sha256)) {
        "Backup aquarium photo digest is invalid."
    }
    val bytes = requireNotNull(mediaByEntryName[reference.entryName]) {
        "Backup aquarium photo is missing."
    }
    require(bytes.size == reference.byteSize) {
        "Backup aquarium photo size does not match its manifest."
    }
    require(sha256(bytes).equals(reference.sha256, ignoreCase = true)) {
        "Backup aquarium photo integrity check failed."
    }
}

private fun validateArchiveItemIds(ids: List<Long>) {
    require(ids.size <= UserDataBackupLimits.MAX_ITEMS_PER_AQUARIUM) {
        "Backup aquarium contains too many inventory items."
    }
    require(ids.all { id -> id > 0L }) {
        "Backup aquarium contains an invalid inventory id."
    }
    require(ids.distinct().size == ids.size) {
        "Backup aquarium contains duplicate inventory ids."
    }
}

private inline fun <reified T : Enum<T>> requireArchiveEnumValue(value: String, field: String) {
    runCatching { enumValueOf<T>(value) }.getOrElse { error ->
        throw IllegalArgumentException("Backup care task $field is invalid.", error)
    }
}

internal fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte ->
            (byte.toInt() and UNSIGNED_BYTE_MASK)
                .toString(HEX_RADIX)
                .padStart(2, '0')
        }
}

private const val UNSIGNED_BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
