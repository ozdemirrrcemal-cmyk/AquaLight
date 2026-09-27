package com.aqua.aqualight.data.user.archive

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File

/** Versioned envelope checks shared by backup, inspection and restore, before any mutation. */
internal object HealthHistoryBackupIntegration {
    fun validateHistory(manifest: UserDataBackupManifest, file: File?, writing: Boolean) {
        val reference = manifest.healthHistory
        when {
            reference == null -> require(!writing && file == null) {
                "Legacy backup cannot contain observation history."
            }
            file == null -> require(writing && reference == HealthHistoryArchive.emptyReference) {
                "Declared observation history is missing."
            }
            else -> HealthHistoryArchive.validate(reference, file, manifest.aquariums.map { it.id }.toSet())
        }
    }

    fun validateMedia(manifest: UserDataBackupManifest, media: Map<String, File>): Set<String> {
        val tankIds = manifest.aquariums.map { it.id }.toSet()
        val references = manifest.healthHistory?.photos.orEmpty()
        val entries = references.map { photo ->
            require(photo.observationId > 0L && photo.tankId in tankIds)
            validateMediaReference(photo.media,
                HealthHistoryArchive.photoEntry(photo.tankId, photo.observationId), media)
            photo.media.entryName
        }
        require(entries.distinct().size == entries.size) { "Observation archive reuses a photo entry." }
        return entries.toSet()
    }

    fun validateDeclaration(element: JsonElement?) {
        require(element?.isJsonObject == true) { "Current backup is missing its observation history declaration." }
        val history = element.asJsonObject
        require(history.get("entryName")?.asString == HealthHistoryArchive.ENTRY)
        require(exactLong(history, "formatVersion") == HealthHistoryArchive.FORMAT_VERSION.toLong())
        require(exactLong(history, "recordCount") in 0L..HealthHistoryArchive.MAX_RECORDS.toLong())
        require(exactLong(history, "byteSize") in 1L..UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES.toLong())
        requireHash(history)
        val photos = history.get("photos")
        require(photos?.isJsonArray == true)
        require(photos.asJsonArray.size() <= UserDataBackupLimits.MAX_ZIP_ENTRIES)
        photos.asJsonArray.forEach { value ->
            require(value.isJsonObject)
            val photo = value.asJsonObject
            require(exactLong(photo, "observationId") > 0L && exactLong(photo, "tankId") > 0L)
            val media = photo.get("media")
            require(media?.isJsonObject == true)
            require(exactLong(media.asJsonObject, "byteSize") in
                1L..UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES.toLong())
            requireHash(media.asJsonObject)
        }
    }

    private fun exactLong(document: JsonObject, key: String): Long {
        val value = document.get(key)
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber)
        return requireNotNull(value.asString.toLongOrNull()) { "Observation archive integer is invalid." }
    }

    private fun requireHash(document: JsonObject) {
        val hash = document.get("sha256")
        require(hash?.isJsonPrimitive == true && hash.asJsonPrimitive.isString)
        require(UserDataBackupLimits.sha256Pattern.matches(hash.asString))
    }
}
