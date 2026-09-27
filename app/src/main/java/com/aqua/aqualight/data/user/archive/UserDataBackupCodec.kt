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
        destination: File,
        waterHistoryFile: File? = null
    ) {
        validateEntryCount(mediaByEntryName.size + 2)
        validateWaterHistory(manifest, waterHistoryFile, writing = true)
        validator.validate(manifest, mediaByEntryName)
        requireStagingDirectory(destination, "Backup staging directory could not be created.")
        val manifestBytes = gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8)
        require(manifestBytes.size <= UserDataBackupLimits.MAX_MANIFEST_BYTES) {
            "Backup manifest exceeds the supported size."
        }
        require(manifestBytes.size.toLong() + mediaByEntryName.values.sumOf(File::length) +
            requireNotNull(manifest.waterHistory).byteSize <=
            maxUncompressedArchiveBytes.toLong()) {
            "Backup exceeds the supported uncompressed archive size."
        }
        var completed = false
        try {
            writeBackupArchive(destination, manifestBytes, mediaByEntryName, waterHistoryFile)
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
        validateWaterHistory(manifest, entries.history, writing = false)
        return DecodedUserDataBackup(
            manifest = manifest,
            mediaByEntryName = entries.media,
            waterHistoryFile = entries.history
        )
    }

    fun encodePortableExport(
        export: PortableUserDataExport,
        destination: File,
        history: Pair<WaterHistoryArchiveReference, File>? = null
    ) {
        require(export.format == USER_DATA_EXPORT_FORMAT)
        require(export.schemaVersion == USER_DATA_EXPORT_SCHEMA_VERSION)
        require(export.exportedAtMillis > 0L)
        requireStagingDirectory(destination, "Export staging directory could not be created.")
        var completed = false
        try {
            writePortableExport(export, destination, history)
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
                    zip,
                    ArchiveEntryRequest(
                        entryName = entry.name,
                        currentManifestJson = manifestJson,
                        media = media,
                        mediaDirectory = mediaDirectory,
                        remainingArchiveBytes = remaining
                    )
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
            history = media.remove(WaterHistoryArchive.ENTRY),
            media = media.toMap()
        )
    }

    private fun readArchiveEntry(
        zip: ZipInputStream,
        request: ArchiveEntryRequest
    ): ArchiveEntryResult {
        return when {
            request.entryName == UserDataBackupLimits.MANIFEST_ENTRY -> {
                require(request.currentManifestJson == null) {
                    "Backup contains more than one manifest."
                }
                val bytes = readLimited(
                    input = zip,
                    maximumBytes = minOf(
                        UserDataBackupLimits.MAX_MANIFEST_BYTES.toLong(),
                        request.remainingArchiveBytes
                    )
                )
                ArchiveEntryResult(
                    manifestJson = bytes.toString(StandardCharsets.UTF_8),
                    uncompressedBytes = bytes.size.toLong()
                )
            }

            request.entryName == WaterHistoryArchive.ENTRY ||
                request.entryName.startsWith(UserDataBackupLimits.MEDIA_PREFIX) -> {
                val isHistory = request.entryName == WaterHistoryArchive.ENTRY
                if (!isHistory) validator.requireValidMediaEntryName(request.entryName)
                require(request.media[request.entryName] == null) {
                    "Backup contains a duplicate media entry."
                }
                val target = File.createTempFile(
                    "restore-media-",
                    ".bin",
                    request.mediaDirectory
                )
                val count = copyLimitedToFile(
                    input = zip,
                    target = target,
                    maximumBytes = minOf(
                        if (isHistory) UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES.toLong()
                        else UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES.toLong(),
                        request.remainingArchiveBytes
                    )
                )
                request.media[request.entryName] = target
                ArchiveEntryResult(
                    manifestJson = request.currentManifestJson,
                    uncompressedBytes = count
                )
            }

            else -> error("Backup contains an unsupported archive entry.")
        }
    }

    private fun decodeManifest(json: String): UserDataBackupManifest {
        return runCatching {
            UserDataBackupManifestReader(gson).read(json)
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
        copyLimited(input, output, maximumBytes)
        return output.toByteArray()
    }

    private fun copyLimitedToFile(
        input: InputStream,
        target: File,
        maximumBytes: Long
    ): Long {
        require(maximumBytes > 0L)
        var completed = false
        return try {
            val total = target.outputStream().buffered().use { output ->
                copyLimited(input, output, maximumBytes)
            }
            require(total > 0L) { "Backup media entry is empty." }
            completed = true
            total
        } finally {
            if (!completed) target.delete()
        }
    }

    private data class ArchiveEntries(
        val manifestJson: String,
        val media: Map<String, File>,
        val history: File?
    )

    private data class ArchiveEntryRequest(
        val entryName: String,
        val currentManifestJson: String?,
        val media: MutableMap<String, File>,
        val mediaDirectory: File,
        val remainingArchiveBytes: Long
    )

    private data class ArchiveEntryResult(
        val manifestJson: String?,
        val uncompressedBytes: Long
    )
}

private fun requireStagingDirectory(destination: File, failureMessage: String) {
    destination.parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) { failureMessage }
    }
}

private fun writeBackupArchive(
    destination: File,
    manifestBytes: ByteArray,
    mediaByEntryName: Map<String, File>,
    waterHistoryFile: File?
) {
    val rawOutput = destination.outputStream().buffered()
    LimitedOutputStream(rawOutput, UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()).use {
        limitedOutput -> writeBackupZip(limitedOutput, manifestBytes, mediaByEntryName, waterHistoryFile)
    }
}

private fun writeBackupZip(
    output: OutputStream,
    manifestBytes: ByteArray,
    mediaByEntryName: Map<String, File>,
    waterHistoryFile: File?
) {
    ZipOutputStream(output).use { zip ->
        writeBytesEntry(zip, UserDataBackupLimits.MANIFEST_ENTRY, manifestBytes)
        if (waterHistoryFile == null) {
            writeBytesEntry(zip, WaterHistoryArchive.ENTRY, WaterHistoryArchive.emptyBytes)
        } else {
            writeFileEntry(zip, WaterHistoryArchive.ENTRY, waterHistoryFile)
        }
        mediaByEntryName.toSortedMap().forEach { (entryName, file) ->
            UserDataBackupValidator().requireValidMediaEntryName(entryName)
            writeFileEntry(zip, entryName, file)
        }
    }
}

private fun writePortableExport(export: PortableUserDataExport, destination: File,
    history: Pair<WaterHistoryArchiveReference, File>?) {
    val rawOutput = destination.outputStream().buffered()
    LimitedOutputStream(rawOutput, UserDataBackupLimits.MAX_ARCHIVE_BYTES.toLong()).use {
        limitedOutput ->
        OutputStreamWriter(limitedOutput, StandardCharsets.UTF_8).use { writer ->
            WaterHistoryPortableWriter.write(export, writer, history)
            writer.flush()
        }
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
    val limit = if (entryName == WaterHistoryArchive.ENTRY) UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES
    else UserDataBackupLimits.MAX_MEDIA_ENTRY_BYTES
    require(source.isFile && source.length() in 1L..limit.toLong()) {
        "Backup media source size is invalid."
    }
    zip.putNextEntry(ZipEntry(entryName))
    source.inputStream().buffered().use { input ->
        input.copyTo(zip, bufferSize = UserDataBackupLimits.BUFFER_SIZE)
    }
    zip.closeEntry()
}

private fun copyLimited(
    input: InputStream,
    output: OutputStream,
    maximumBytes: Long
): Long {
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
    return total
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

private fun validateWaterHistory(manifest: UserDataBackupManifest, history: File?, writing: Boolean) {
    val reference = manifest.waterHistory
    if (reference == null) {
        require(!writing && history == null) { "Legacy backup cannot contain analysis history." }
    } else if (history == null) {
        require(writing && reference == WaterHistoryArchive.emptyReference) {
            "Declared analysis history is missing."
        }
    } else {
        WaterHistoryArchive.validate(reference, history, manifest.aquariums.map { it.id }.toSet())
    }
}
