package com.aqua.aqualight.application.media

/**
 * Android-free media boundary consumed by presentation code.
 *
 * URIs cross this boundary as strings so application contracts never depend on android.net.Uri.
 * Concrete file ownership, FileProvider access and image decoding remain platform responsibilities.
 */
enum class MediaScope {
    PROFILE,
    TANK,
    PLANT,
    LIVESTOCK
}

data class PreparedImageMedia(
    val path: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val byteCount: Long
)

enum class MediaPreparationFailureKind {
    UNSUPPORTED_TYPE,
    SOURCE_TOO_LARGE,
    TOO_MANY_PIXELS,
    INVALID_IMAGE,
    OUTPUT_TOO_LARGE,
    OUT_OF_MEMORY,
    IO
}

sealed interface MediaSourcePreparationResult {
    data class Success(
        val media: PreparedImageMedia
    ) : MediaSourcePreparationResult

    data class Failure(
        val kind: MediaPreparationFailureKind
    ) : MediaSourcePreparationResult
}

interface MediaFlowOperations {
    val storage: MediaStorageOperations
    val preparation: MediaPreparationOperations
}

interface MediaStorageOperations {
    fun createCameraCaptureUri(scope: MediaScope, ownerToken: String): String?

    fun createCropOutputUri(scope: MediaScope, ownerToken: String): String?

    fun promoteCropOutput(
        scope: MediaScope,
        ownerToken: String,
        ownerUid: String,
        outputUri: String
    ): String?

    fun toContentUriForOwnedPath(path: String?): String?

    fun commitPendingMedia(uriString: String?)

    fun rollbackPendingMedia(uriString: String?): Boolean

    fun deleteInternalMedia(uriString: String?): Boolean

    fun cleanupStaleTemporaryFiles()
}

interface MediaPreparationOperations {
    suspend fun prepareSource(sourceUri: String): MediaSourcePreparationResult

    suspend fun deletePreparedSource(path: String?)

    suspend fun cleanupExpiredPreparedMedia()
}
