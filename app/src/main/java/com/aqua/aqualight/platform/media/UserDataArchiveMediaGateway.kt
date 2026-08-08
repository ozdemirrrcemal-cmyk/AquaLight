package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class UserDataArchiveMediaFingerprint(
    val byteSize: Int,
    val sha256: String
)

/**
 * Backup-only adapter around AquaLight's canonical app-owned media lifecycle.
 *
 * It never exposes filesystem paths to presentation and streams archive media instead of
 * materializing full-size photos in heap memory.
 */
internal class UserDataArchiveMediaGateway(
    context: Context
) {
    private val appContext = context.applicationContext

    fun snapshotTankPhoto(
        uriString: String?,
        destination: File
    ): File? {
        val source = resolveSupportedTankPhoto(uriString) ?: return null
        destination.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Tank-photo staging directory could not be created."
            }
        }
        var completed = false
        return try {
            source.inputStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    copyLimited(input, output, MAX_TANK_PHOTO_BYTES)
                }
            }
            require(destination.length() == source.length()) {
                "Staged tank photo size changed during copy."
            }
            require(isSupportedImage(destination)) {
                "Staged tank photo is not a supported image."
            }
            completed = true
            destination
        } finally {
            if (!completed) destination.delete()
        }
    }

    fun canSnapshotTankPhoto(uriString: String?): Boolean {
        return resolveSupportedTankPhoto(uriString) != null
    }

    fun fingerprintTankPhoto(uriString: String?): UserDataArchiveMediaFingerprint? {
        val source = resolveSupportedTankPhoto(uriString) ?: return null
        return UserDataArchiveMediaFingerprint(
            byteSize = source.length().toInt(),
            sha256 = sha256(source)
        )
    }

    fun prepareRestoredTankPhoto(
        ownerUid: String,
        ownerToken: String,
        source: File
    ): String {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        require(
            source.isFile && source.length() in 1L..MAX_TANK_PHOTO_BYTES.toLong()
        ) {
            "Restored tank photo exceeds the supported size."
        }
        require(isSupportedImage(source)) {
            "Restored tank photo is not a supported image."
        }

        val temporaryUri = requireNotNull(
            AppMediaStorage.createCropOutputUri(
                context = appContext,
                scope = AppMediaScope.TANK,
                ownerToken = ownerToken
            )
        ) {
            "A temporary tank-photo file could not be created."
        }
        val temporaryFile = requireNotNull(temporaryUri.path?.let(::File)) {
            "The temporary tank-photo file is unavailable."
        }

        var promoted = false
        return try {
            source.inputStream().buffered().use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    copyLimited(input, output, MAX_TANK_PHOTO_BYTES)
                }
            }
            require(temporaryFile.length() == source.length()) {
                "Restored tank photo copy was incomplete."
            }
            requireNotNull(
                AppMediaStorage.promoteCropOutput(
                    context = appContext,
                    scope = AppMediaScope.TANK,
                    ownerToken = ownerToken,
                    ownerUid = ownerUid,
                    outputUri = temporaryUri
                )
            ) {
                "The restored tank photo could not be promoted."
            }.toString().also { promoted = true }
        } finally {
            if (!promoted) temporaryFile.delete()
        }
    }

    fun commit(uriString: String?) {
        AppMediaStorage.commitPendingMedia(appContext, uriString)
    }

    fun rollback(uriString: String?) {
        AppMediaStorage.rollbackPendingMedia(appContext, uriString)
    }

    private fun resolveSupportedTankPhoto(uriString: String?): File? {
        val source = AppMediaStorage.resolveInternalMediaFile(
            context = appContext,
            uriString = uriString,
            expectedScope = AppMediaScope.TANK
        )
        return source?.takeIf { file ->
            file.isFile &&
                file.length() in 1L..MAX_TANK_PHOTO_BYTES.toLong() &&
                isSupportedImage(file)
        }
    }

    private fun isSupportedImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val supportedMime = options.outMimeType in SUPPORTED_MIME_TYPES
        return supportedMime &&
            options.outWidth in 1..MAX_IMAGE_DIMENSION &&
            options.outHeight in 1..MAX_IMAGE_DIMENSION
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Int
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes.toLong()) {
                "Tank photo exceeds the supported size."
            }
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and UNSIGNED_BYTE_MASK)
                .toString(HEX_RADIX)
                .padStart(2, '0')
        }
    }

    private companion object {
        const val MAX_TANK_PHOTO_BYTES = 8 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 8_192
        const val BUFFER_SIZE = 8 * 1024
        const val UNSIGNED_BYTE_MASK = 0xFF
        const val HEX_RADIX = 16
        val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
