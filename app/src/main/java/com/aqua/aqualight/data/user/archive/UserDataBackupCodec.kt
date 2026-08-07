package com.aqua.aqualight.data.user.archive

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class UserDataBackupCodec(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create(),
    private val validator: UserDataBackupValidator = UserDataBackupValidator(),
    private val maxUncompressedArchiveBytes: Int =
        UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES
) {

    init {
        require(maxUncompressedArchiveBytes > 0) {
            "Uncompressed archive limit must be positive."
        }
    }

    fun encode(
        manifest: UserDataBackupManifest,
        mediaByEntryName: Map<String, ByteArray>
    ): ByteArray {
        validator.validate(manifest, mediaByEntryName)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(
                zip = zip,
                entryName = UserDataBackupLimits.MANIFEST_ENTRY,
                content = gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8)
            )
            mediaByEntryName.toSortedMap().forEach { (entryName, bytes) ->
                validator.requireValidMediaEntryName(entryName)
                writeEntry(zip, entryName, bytes)
            }
        }
        return output.toByteArray().also { encoded ->
            require(encoded.size <= UserDataBackupLimits.MAX_ARCHIVE_BYTES) {
                "Backup exceeds the supported archive size."
            }
        }
    }

    fun decode(content: ByteArray): DecodedUserDataBackup {
        require(content.isNotEmpty()) { "Backup is empty." }
        require(content.size <= UserDataBackupLimits.MAX_ARCHIVE_BYTES) {
            "Backup exceeds the supported archive size."
        }
        val entries = readEntries(content)
        val manifest = decodeManifest(entries.manifestJson)
        validator.validate(manifest, entries.media)
        return DecodedUserDataBackup(
            manifest = manifest,
            mediaByEntryName = entries.media
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

    private fun readEntries(content: ByteArray): ArchiveEntries {
        var manifestJson: String? = null
        val media = linkedMapOf<String, ByteArray>()
        var entryCount = 0
        var totalUncompressedBytes = 0

        ZipInputStream(ByteArrayInputStream(content)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount = validateEntryCount(entryCount + 1)
                if (!entry.isDirectory) {
                    val remaining = maxUncompressedArchiveBytes - totalUncompressedBytes
                    require(remaining > 0) {
                        "Backup exceeds the supported uncompressed archive size."
                    }
                    val parsed = readArchiveEntry(
                        zip = zip,
                        entryName = entry.name,
                        currentManifestJson = manifestJson,
                        media = media,
                        remainingArchiveBytes = remaining
                    )
                    manifestJson = parsed.manifestJson
                    totalUncompressedBytes += parsed.uncompressedBytes
                    require(totalUncompressedBytes <= maxUncompressedArchiveBytes) {
                        "Backup exceeds the supported uncompressed archive size."
                    }
                }
                zip.closeEntry()
            }
        }
        return ArchiveEntries(
            manifestJson = requireNotNull(manifestJson) { "Backup manifest is missing." },
            media = media.toMap()
        )
    }

    private fun readArchiveEntry(
        zip: ZipInputStream,
        entryName: String,
        currentManifestJson: String?,
        media: MutableMap<String, ByteArray>,
        remainingArchiveBytes: Int
    ): ArchiveEntryResult {
        validator.requireSafeEntryName(entryName)
        return when {
            entryName == UserDataBackupLimits.MANIFEST_ENTRY -> {
                require(currentManifestJson == null) {
                    "Backup contains more than one manifest."
                }
                val bytes = readLimited(
                    input = zip,
                    maximumBytes = minOf(
                        UserDataBackupLimits.MAX_MANIFEST_BYTES,
                        remainingArchiveBytes
                    )
                )
                ArchiveEntryResult(
                    manifestJson = bytes.toString(StandardCharsets.UTF_8),
                    uncompressedBytes = bytes.size
                )
            }

            entryName.startsWith(UserDataBackupLimits.MEDIA_PREFIX) -> {
                validator.requireValidMediaEntryName(entryName)
                require(media[entryName] == null) {
                    "Backup contains a duplicate media entry."
                }
                val bytes = readLimited(
                    input = zip,
                    maximumBytes = minOf(
                        UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES,
                        remainingArchiveBytes
                    )
                )
                media[entryName] = bytes
                ArchiveEntryResult(
                    manifestJson = currentManifestJson,
                    uncompressedBytes = bytes.size
                )
            }

            else -> error("Backup contains an unsupported archive entry.")
        }
    }

    private fun decodeManifest(json: String): UserDataBackupManifest {
        return runCatching {
            gson.fromJson(json, UserDataBackupManifest::class.java)
        }.getOrElse { error ->
            throw IllegalArgumentException("Backup manifest is invalid.", error)
        }
    }

    private fun validateEntryCount(count: Int): Int {
        require(count <= UserDataBackupLimits.MAX_ZIP_ENTRIES) {
            "Backup contains too many archive entries."
        }
        return count
    }

    private fun readLimited(input: ZipInputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(UserDataBackupLimits.BUFFER_SIZE)
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

    private fun writeEntry(zip: ZipOutputStream, entryName: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content)
        zip.closeEntry()
    }

    private data class ArchiveEntries(
        val manifestJson: String,
        val media: Map<String, ByteArray>
    )

    private data class ArchiveEntryResult(
        val manifestJson: String?,
        val uncompressedBytes: Int
    )
}
