package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class UserDataBackupCodec(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {

    fun encode(
        manifest: UserDataBackupManifest,
        mediaByEntryName: Map<String, ByteArray>
    ): ByteArray {
        validateManifest(manifest, mediaByEntryName)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            mediaByEntryName.toSortedMap().forEach { (entryName, bytes) ->
                requireValidMediaEntryName(entryName)
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val encoded = output.toByteArray()
        require(encoded.size <= MAX_ARCHIVE_BYTES) {
            "Backup exceeds the supported archive size."
        }
        return encoded
    }

    fun decode(content: ByteArray): DecodedUserDataBackup {
        require(content.isNotEmpty()) { "Backup is empty." }
        require(content.size <= MAX_ARCHIVE_BYTES) {
            "Backup exceeds the supported archive size."
        }

        var manifestJson: String? = null
        val media = linkedMapOf<String, ByteArray>()
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(content)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ZIP_ENTRIES) {
                    "Backup contains too many archive entries."
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val entryName = entry.name
                requireSafeEntryName(entryName)
                when {
                    entryName == MANIFEST_ENTRY -> {
                        require(manifestJson == null) {
                            "Backup contains more than one manifest."
                        }
                        val bytes = readLimited(zip, MAX_MANIFEST_BYTES)
                        manifestJson = bytes.toString(StandardCharsets.UTF_8)
                    }

                    entryName.startsWith(MEDIA_PREFIX) -> {
                        requireValidMediaEntryName(entryName)
                        require(media[entryName] == null) {
                            "Backup contains a duplicate media entry."
                        }
                        media[entryName] = readLimited(zip, MAX_MEDIA_ENTRY_BYTES)
                    }

                    else -> error("Backup contains an unsupported archive entry.")
                }
                zip.closeEntry()
            }
        }

        val json = requireNotNull(manifestJson) {
            "Backup manifest is missing."
        }
        val manifest = runCatching {
            gson.fromJson(json, UserDataBackupManifest::class.java)
        }.getOrElse { error ->
            throw IllegalArgumentException("Backup manifest is invalid.", error)
        }
        validateManifest(manifest, media)
        return DecodedUserDataBackup(
            manifest = manifest,
            mediaByEntryName = media.toMap()
        )
    }

    fun encodePortableExport(export: PortableUserDataExport): ByteArray {
        require(export.format == USER_DATA_EXPORT_FORMAT)
        require(export.schemaVersion == USER_DATA_EXPORT_SCHEMA_VERSION)
        require(export.exportedAtMillis > 0L)
        return GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
            .toJson(export)
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun validateManifest(
        manifest: UserDataBackupManifest,
        mediaByEntryName: Map<String, ByteArray>
    ) {
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
        require(manifest.aquariums.size <= MAX_AQUARIUMS) {
            "Backup contains too many aquariums."
        }
        require(manifest.careTasks.size <= MAX_CARE_TASKS) {
            "Backup contains too many care tasks."
        }
        require(manifest.deviceAssignments.size <= MAX_DEVICE_ASSIGNMENTS) {
            "Backup contains too many device assignments."
        }

        val tankIds = manifest.aquariums.map { aquarium -> aquarium.id }
        require(tankIds.all { tankId -> tankId > 0L }) {
            "Backup contains an invalid aquarium id."
        }
        require(tankIds.distinct().size == tankIds.size) {
            "Backup contains duplicate aquarium ids."
        }
        val tankIdSet = tankIds.toSet()
        manifest.aquariums.forEach(::validateAquarium)

        val taskIds = manifest.careTasks.map { task -> task.id }
        require(taskIds.all { taskId -> taskId > 0L }) {
            "Backup contains an invalid care task id."
        }
        require(taskIds.distinct().size == taskIds.size) {
            "Backup contains duplicate care task ids."
        }
        manifest.careTasks.forEach { task ->
            validateCareTask(task, tankIdSet)
        }

        val assignmentDevices = mutableSetOf<String>()
        manifest.deviceAssignments.forEach { assignment ->
            require(assignment.tankId in tankIdSet) {
                "Backup device assignment references an unknown aquarium."
            }
            require(assignment.deviceUid.isNotBlank()) {
                "Backup device assignment has a blank device id."
            }
            require(assignment.deviceUid == assignment.deviceUid.trim()) {
                "Backup device assignment device id is not normalized."
            }
            require(assignment.assignedAtMillis > 0L) {
                "Backup device assignment time is invalid."
            }
            require(assignmentDevices.add(assignment.deviceUid)) {
                "Backup assigns one device to more than one aquarium."
            }
        }

        val referencedMedia = manifest.aquariums.mapNotNull { aquarium ->
            aquarium.photo?.also { reference ->
                validateMediaReference(reference, aquarium.id, mediaByEntryName)
            }?.entryName
        }.toSet()
        require(referencedMedia.size == manifest.aquariums.count { it.photo != null }) {
            "Backup reuses a media entry for multiple aquariums."
        }
        require(mediaByEntryName.keys == referencedMedia) {
            "Backup contains unreferenced or missing media entries."
        }
    }

    private fun validateAquarium(aquarium: ArchiveAquarium) {
        require(aquarium.name.isNotBlank()) {
            "Backup aquarium name is blank."
        }
        require(aquarium.widthCm > 0 && aquarium.lengthCm > 0 && aquarium.heightCm > 0) {
            "Backup aquarium dimensions are invalid."
        }
        require(aquarium.sizeUnit.isNotBlank() && aquarium.volumeUnit.isNotBlank()) {
            "Backup aquarium units are invalid."
        }
        require(aquarium.createdAtMillis > 0L) {
            "Backup aquarium creation time is invalid."
        }
        require(aquarium.plants.size <= MAX_ITEMS_PER_AQUARIUM)
        require(aquarium.materials.size <= MAX_ITEMS_PER_AQUARIUM)
        require(aquarium.livestock.size <= MAX_ITEMS_PER_AQUARIUM)
        require(aquarium.plants.map { it.id }.distinct().size == aquarium.plants.size)
        require(aquarium.materials.map { it.id }.distinct().size == aquarium.materials.size)
        require(aquarium.livestock.map { it.id }.distinct().size == aquarium.livestock.size)
        aquarium.livestock.forEach { livestock ->
            require(livestock.id > 0L)
            require(livestock.quantity > 0)
        }
    }

    private fun validateCareTask(
        task: ArchiveCareTask,
        tankIds: Set<Long>
    ) {
        require(task.tankId in tankIds) {
            "Backup care task references an unknown aquarium."
        }
        require(task.title.isNotBlank()) {
            "Backup care task title is blank."
        }
        require(task.dueAtMillis > 0L)
        require(task.createdAtMillis > 0L)
        require(task.updatedAtMillis > 0L)
        runCatching { CareTaskType.valueOf(task.type) }.getOrElse {
            throw IllegalArgumentException("Backup care task type is invalid.", it)
        }
        runCatching { CareTaskSource.valueOf(task.source) }.getOrElse {
            throw IllegalArgumentException("Backup care task source is invalid.", it)
        }
        runCatching { CareTaskStatus.valueOf(task.status) }.getOrElse {
            throw IllegalArgumentException("Backup care task status is invalid.", it)
        }
    }

    private fun validateMediaReference(
        reference: ArchiveMediaReference,
        tankId: Long,
        mediaByEntryName: Map<String, ByteArray>
    ) {
        requireValidMediaEntryName(reference.entryName)
        require(reference.entryName == "$MEDIA_PREFIX$tankId.jpg") {
            "Backup aquarium photo entry does not match its aquarium."
        }
        require(reference.byteSize in 1..MAX_MEDIA_ENTRY_BYTES)
        require(reference.sha256.matches(SHA256_PATTERN)) {
            "Backup aquarium photo digest is invalid."
        }
        val bytes = requireNotNull(mediaByEntryName[reference.entryName]) {
            "Backup aquarium photo is missing."
        }
        require(bytes.size == reference.byteSize) {
            "Backup aquarium photo size does not match its manifest."
        }
        require(sha256(bytes) == reference.sha256.lowercase(Locale.ROOT)) {
            "Backup aquarium photo integrity check failed."
        }
    }

    private fun requireSafeEntryName(entryName: String) {
        require(entryName.isNotBlank())
        require(!entryName.startsWith('/'))
        require('\\' !in entryName)
        require(entryName.split('/').none { segment -> segment == ".." || segment.isBlank() })
    }

    private fun requireValidMediaEntryName(entryName: String) {
        requireSafeEntryName(entryName)
        require(MEDIA_ENTRY_PATTERN.matches(entryName)) {
            "Backup media entry name is invalid."
        }
    }

    private fun readLimited(
        input: ZipInputStream,
        maximumBytes: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) {
                "Backup archive entry exceeds its supported size."
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val MEDIA_PREFIX = "media/tanks/"
        const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
        const val MAX_MEDIA_ENTRY_BYTES = 8 * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 256
        private const val MAX_AQUARIUMS = 100
        private const val MAX_CARE_TASKS = 10_000
        private const val MAX_DEVICE_ASSIGNMENTS = 500
        private const val MAX_ITEMS_PER_AQUARIUM = 2_000
        private const val BUFFER_SIZE = 8 * 1024
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
        private val MEDIA_ENTRY_PATTERN = Regex("media/tanks/[1-9][0-9]*\\.jpg")

        fun sha256(bytes: ByteArray): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
