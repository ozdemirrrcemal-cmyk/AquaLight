package com.aqua.aqualight.platform.media

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.application.media.MediaFlowOperations
import com.aqua.aqualight.application.media.MediaPreparationFailureKind
import com.aqua.aqualight.application.media.MediaPreparationOperations
import com.aqua.aqualight.application.media.MediaScope
import com.aqua.aqualight.application.media.MediaSourcePreparationResult
import com.aqua.aqualight.application.media.MediaStorageOperations
import com.aqua.aqualight.application.media.PreparedImageMedia

/** Android adapter for the presentation-facing media boundary. */
internal class AndroidMediaFlowOperations(
    context: Context,
    imageMediaProcessor: ImageMediaProcessor
) : MediaFlowOperations {
    override val storage: MediaStorageOperations =
        AndroidMediaStorageOperations(context)
    override val preparation: MediaPreparationOperations =
        AndroidMediaPreparationOperations(imageMediaProcessor)
}

private class AndroidMediaStorageOperations(
    context: Context
) : MediaStorageOperations {
    private val appContext = context.applicationContext

    override fun createCameraCaptureUri(scope: MediaScope, ownerToken: String): String? =
        AppMediaStorage.createCameraCaptureUri(
            context = appContext,
            scope = scope.toPlatformScope(),
            ownerToken = ownerToken
        )?.toString()

    override fun createCropOutputUri(scope: MediaScope, ownerToken: String): String? =
        AppMediaStorage.createCropOutputUri(
            context = appContext,
            scope = scope.toPlatformScope(),
            ownerToken = ownerToken
        )?.toString()

    override fun promoteCropOutput(
        scope: MediaScope,
        ownerToken: String,
        ownerUid: String,
        outputUri: String
    ): String? {
        if (outputUri.isBlank()) return null
        return AppMediaStorage.promoteCropOutput(
            context = appContext,
            scope = scope.toPlatformScope(),
            ownerToken = ownerToken,
            ownerUid = ownerUid,
            outputUri = Uri.parse(outputUri)
        )?.toString()
    }

    override fun toContentUriForOwnedPath(path: String?): String? =
        AppMediaStorage.toContentUriForOwnedPath(appContext, path)?.toString()

    override fun commitPendingMedia(uriString: String?) {
        AppMediaStorage.commitPendingMedia(appContext, uriString)
    }

    override fun rollbackPendingMedia(uriString: String?): Boolean =
        AppMediaStorage.rollbackPendingMedia(appContext, uriString)

    override fun deleteInternalMedia(uriString: String?): Boolean =
        AppMediaStorage.deleteInternalMedia(appContext, uriString)

    override fun cleanupStaleTemporaryFiles() {
        AppMediaStorage.cleanupStaleTemporaryFiles(appContext)
    }
}

private class AndroidMediaPreparationOperations(
    private val imageMediaProcessor: ImageMediaProcessor
) : MediaPreparationOperations {

    override suspend fun prepareSource(sourceUri: String): MediaSourcePreparationResult {
        if (sourceUri.isBlank()) {
            return MediaSourcePreparationResult.Failure(
                MediaPreparationFailureKind.INVALID_IMAGE
            )
        }
        return when (val result = imageMediaProcessor.process(Uri.parse(sourceUri))) {
            is ImageMediaProcessingResult.Success -> MediaSourcePreparationResult.Success(
                PreparedImageMedia(
                    path = result.media.path,
                    displayName = result.media.displayName,
                    width = result.media.width,
                    height = result.media.height,
                    byteCount = result.media.byteCount
                )
            )
            is ImageMediaProcessingResult.Failure -> MediaSourcePreparationResult.Failure(
                result.kind.toApplicationKind()
            )
        }
    }

    override suspend fun deletePreparedSource(path: String?) {
        imageMediaProcessor.delete(path)
    }

    override suspend fun cleanupExpiredPreparedMedia() {
        imageMediaProcessor.cleanupExpired()
    }
}

private fun MediaScope.toPlatformScope(): AppMediaScope = when (this) {
    MediaScope.PROFILE -> AppMediaScope.PROFILE
    MediaScope.TANK -> AppMediaScope.TANK
    MediaScope.PLANT -> AppMediaScope.PLANT
    MediaScope.LIVESTOCK -> AppMediaScope.LIVESTOCK
}

private fun ImageMediaFailureKind.toApplicationKind(): MediaPreparationFailureKind =
    when (this) {
        ImageMediaFailureKind.UNSUPPORTED_TYPE -> MediaPreparationFailureKind.UNSUPPORTED_TYPE
        ImageMediaFailureKind.SOURCE_TOO_LARGE -> MediaPreparationFailureKind.SOURCE_TOO_LARGE
        ImageMediaFailureKind.TOO_MANY_PIXELS -> MediaPreparationFailureKind.TOO_MANY_PIXELS
        ImageMediaFailureKind.INVALID_IMAGE -> MediaPreparationFailureKind.INVALID_IMAGE
        ImageMediaFailureKind.OUTPUT_TOO_LARGE -> MediaPreparationFailureKind.OUTPUT_TOO_LARGE
        ImageMediaFailureKind.OUT_OF_MEMORY -> MediaPreparationFailureKind.OUT_OF_MEMORY
        ImageMediaFailureKind.IO -> MediaPreparationFailureKind.IO
    }
