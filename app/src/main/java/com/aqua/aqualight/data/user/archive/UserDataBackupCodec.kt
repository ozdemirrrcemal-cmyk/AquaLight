package com.aqua.aqualight.data.user.archive

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
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
        mediaByEntryName: Map<String, File>,
        destination: File
    ) {
        validator.validate(manifest, mediaByEntryName)
        destination.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Backup staging directory could not be created."
            }
        }
        var completed = false
        try {
            val rawOutput = destination.outputStream().buffered()
            LimitedOutputStream(rawOutput, UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()).use {
                limitedOutput ->
                ZipOutputStream(limitedOutput).use { zip ->
                    val manifestBytes = gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8)
                    require(manifestBytes.size <= UserDataBackupLimits.MAX_MANIFEST_BYTES) {
                        "Backup manifest exceeds the supported size."
                    }
                    writeBytesEntry(
                        zip = zip,
                        entryName = UserDataBackupLimits.MANIFEST_ENTRY,
                        content = manifestBytes
                    )
                    mediaByEntryName.toSortedMap().forEach { (entryName, file) ->
                        validator.requireValidMediaEntryName(entryName)
                        writeFileEntry(zip, entryName, file)
                    }
                }
            }
            require(destination.length() in 1L..UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()) {
                "Backup archive size is invalid."
            }
            completed = true
        } finally {
            if (!completed) destination.delete()
        }
    }

    fun decode(
        source: File,
        mediaDirectory: File
    ): DecodedUserDataBackup {
        require(source.isFile && source.length() in 1L..UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()) {
            "Backup size is invalid."
        }
        require(mediaDirectory.isDirectory || mediaDirectory.mkdirs()) {
            "Backup media staging directory could not be created."
        }
        require(mediaDirectory.listFiles().isNullOrEmpty()) {
            "Backup media staging directory must be empty."
        }
        val entries = readEntries(source, mediaDirectory)
        val manifest = decodeManifest(entries.manifestJson)
        validator.validate(manifest, entries.media)
        return DecodedUserDataBackup(
            manifest = manifest,
            mediaByEntryName = entries.media
        )
    }

    fun encodePortableExport(
        export: PortableUserDataExport,
        destination: File
    ) {
        require(export.format == USER_DATA_EXPORT_FORMAT)
        require(export.schemaVersion == USER_DATA_EXPORT_SCHEMA_VERSION)
        require(export.exportedAtMillis > 0L)
        destination.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Export staging directory could not be created."
            }
        }
        var completed = false
        try {
            val rawOutput = destination.outputStream().buffered()
            LimitedOutputStream(rawOutput, UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()).use {
                limitedOutput ->
                OutputStreamWriter(limitedOutput, StandardCharsets.UTF_8).use { writer ->
                    GsonBuilder()
                        .setPrettyPrinting()
                        .disableHtmlEscaping()
                        .create()
                        .toJson(export, writer)
                    writer.flush()
                }
            }
            require(destination.length() in 1L..UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()) {
                "Portable export size is invalid."
            }
            completed = true
        } finally {
            if (!completed) destination.delete()
        }
    }

    private fun readEntries(
        source: File,
        mediaDirectory: File
    ): ArchiveEntries {
        var manifestJson: String? = null
        val media = linkedMapOf<String, File>()
        var entryCount = 0
        var totalUncompressedBytes = 0L

        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount = validateEntryCount(entryCount + 1)
                validator.requireSafeEntryName(entry.name)
                require(!entry.isDirectory) { "Backup contains an unsupported directory entry." }
                val remaining = maxUncompressedArchiveBytes.toLong() - totalUncompressedBytes
                require(remaining > 0L) {
                    "Backup exceeds the supported uncompressed archive size."
                }
                val parsed = readArchiveEntry(
                    zip = zip,
                    entryName = entry.name,
                    currentManifestJson = manifestJson,
                    media = media,
                    mediaDirectory = mediaDirectory,
                    remainingArchiveBytes = remaining
                )
                manifestJson = parsed.manifestJson
                totalUncompressedBytes += parsed.uncompressedBytes
                require(totalUncompressedBytes <= maxUncompressedArchiveBytes.toLong()) {
                    "Backup exceeds the supported uncompressed archive size."
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
        media: MutableMap<String, File>,
        mediaDirectory: File,
        remainingArchiveBytes: Long
    ): ArchiveEntryResult {
        return when {
            entryName == UserDataBackupLimits.MANIFEST_ENTRY -> {
                require(currentManifestJson == null) {
                    "Backup contains more than one manifest."
                }
                val bytes = readLimited(
                    input = zip,
                    maximumBytes = minOf(
                        UserDataBackupLimits.MAX_MANIFEST_BYTES.toLong(),
                        remainingArchiveBytes
                    )
                )
                ArchiveEntryResult(
                    manifestJson = bytes.toString(StandardCharsets.UTF_8),
                    uncompressedBytes = bytes.size.toLong()
                )
            }

            entryName.startsWith(UserDataBackupLimits.MEDIA_PREFIX) -> {
                validator.requireValidMediaEntryName(entryName)
                require(media[entryName] == null) {
                    "Backup contains a duplicate media entry."
                }
                val target = File(mediaDirectory, entryName.substringAfterLast('/'))
                require(target.parentFile == mediaDirectory) {
                    "Backup media staging path is invalid."
                }
                val count = copyLimitedToFile(
                    input = zip,
                    target = target,
                    maximumBytes = minOf(
                        UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES.toLong(),
                        remainingArchiveBytes
                    )
                )
                media[entryName] = target
                ArchiveEntryResult(
                    manifestJson = currentManifestJson,
                    uncompressedBytes = count
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

    private fun readLimited(input: InputStream, maximumBytes: Long): ByteArray {
        require(maximumBytes > 0L)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(UserDataBackupLimits.BUFFER_SIZE)
        var total = 0L
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

    private fun copyLimitedToFile(
        input: InputStream,
        target: File,
        maximumBytes: Long
    ): Long {
        require(maximumBytes > 0L)
        var completed = false
        var total = 0L
        try {
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(UserDataBackupLimits.BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maximumBytes) {
                        "Backup archive entry exceeds its supported size."
                    }
                    output.write(buffer, 0, read)
                }
            }
            require(total > 0L) { "Backup media entry is empty." }
            completed = true
            return total
        } finally {
            if (!completed) target.delete()
        }
    }

    private fun writeBytesEntry(
        zip: ZipOutputStream,
        entryName: String,
        content: ByteArray
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content)
        zip.closeEntry()
    }

    private fun writeFileEntry(
        zip: ZipOutputStream,
        entryName: String,
        source: File
    ) {
        require(source.isFile && source.length() in 1L..UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES.toLong()) {
            "Backup media source size is invalid."
        }
        zip.putNextEntry(ZipEntry(entryName))
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(UserDataBackupLimits.BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
            }
        }
        zip.closeEntry()
    }

    private data class ArchiveEntries(
        val manifestJson: String,
        val media: Map<String, File>
    )

    private data class ArchiveEntryResult(
        val manifestJson: String?,
        val uncompressedBytes: Long
    )
}

private class LimitedOutputStream(
    output: OutputStream,
    private val maximumBytes: Long
) : FilterOutputStream(output) {
    private var written = 0L

    override fun write(value: Int) {
        requireCapacity(1)
        out.write(value)
        written += 1L
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        requireCapacity(length)
        out.write(buffer, offset, length)
        written += length.toLong()
    }

    private fun requireCapacity(nextBytes: Int) {
        require(written + nextBytes.toLong() <= maximumBytes) {
            "Generated document exceeds the supported size."
        }
    }
}
