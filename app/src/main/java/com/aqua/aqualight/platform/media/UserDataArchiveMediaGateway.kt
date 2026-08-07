package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

/**
 * Backup-only adapter around AquaLight's canonical app-owned media lifecycle.
 *
 * It never exposes filesystem paths to presentation and never restores external content URIs.
 */
internal class UserDataArchiveMediaGateway(
    context: Context
) {
    private val appContext = context.applicationContext

    fun snapshotTankPhoto(uriString: String?): ByteArray? {
        val source = AppMediaStorage.resolveInternalMediaFile(
            context = appContext,
            uriString = uriString,
            expectedScope = AppMediaScope.TANK
        )
        return source
            ?.takeIf { file ->
                file.isFile && file.length() in 1L..MAX_TANK_PHOTO_BYTES.toLong()
            }
            ?.let { file -> runCatching(file::readBytes).getOrNull() }
            ?.takeIf(::isSupportedImage)
    }

    fun prepareRestoredTankPhoto(
        ownerUid: String,
        ownerToken: String,
        bytes: ByteArray
    ): String {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        require(bytes.size in 1..MAX_TANK_PHOTO_BYTES) {
            "Restored tank photo exceeds the supported size."
        }
        require(isSupportedImage(bytes)) {
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
            temporaryFile.writeBytes(bytes)
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

    private fun isSupportedImage(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val supportedMime = options.outMimeType in SUPPORTED_MIME_TYPES
        return supportedMime &&
            options.outWidth in 1..MAX_IMAGE_DIMENSION &&
            options.outHeight in 1..MAX_IMAGE_DIMENSION
    }

    private companion object {
        const val MAX_TANK_PHOTO_BYTES = 8 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 8_192
        val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
