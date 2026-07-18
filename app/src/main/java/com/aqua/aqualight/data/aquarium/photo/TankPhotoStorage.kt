package com.aqua.aqualight.data.aquarium.photo

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

/**
 * Temporary data-layer adapter while aquarium persistence migrates to AppMediaStorage.
 * It owns no file, URI or security logic.
 */
internal object TankPhotoStorage {

    fun createCameraCaptureUri(
        context: Context,
        ownerToken: String = "draft"
    ): Uri? {
        return AppMediaStorage.createCameraCaptureUri(
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = ownerToken
        )
    }

    fun createCropOutputUri(
        context: Context,
        ownerToken: String = "draft"
    ): Uri? {
        return AppMediaStorage.createCropOutputUri(
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = ownerToken
        )
    }

    fun toContentUriIfInternalFile(
        context: Context,
        uri: Uri
    ): Uri? {
        return AppMediaStorage.toContentUriIfInternalFile(context, uri)
    }

    fun copyInternalPhotoForTank(
        context: Context,
        sourceUriString: String?,
        tankId: Long
    ): String? {
        return AppMediaStorage.copyInternalMedia(
            context = context,
            sourceUriString = sourceUriString,
            targetScope = AppMediaScope.TANK,
            ownerToken = tankId.toString()
        )
    }

    fun deleteInternalPhoto(
        context: Context,
        uriString: String?
    ) {
        AppMediaStorage.deleteInternalMedia(context, uriString)
    }

    fun deleteInternalPhotos(
        context: Context,
        uriStrings: Collection<String?>
    ) {
        AppMediaStorage.deleteInternalMedia(context, uriStrings)
    }

    fun deleteTankOwnedTemporaryFiles(
        context: Context,
        tankId: Long
    ) {
        if (tankId <= 0L) return
        AppMediaStorage.deleteOwnerTemporaryFiles(
            context = context,
            scope = AppMediaScope.TANK,
            ownerToken = tankId.toString()
        )
    }
}
