package com.aqua.aqualight.ui.tabs.aquarium.photo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.photo.TankPhotoStorage
import com.yalantis.ucrop.UCrop

class TankPhotoFlowCoordinator(
    private val contextProvider: () -> Context,
    private val ownerTokenProvider: () -> String
) {
    private var pendingCameraUri: Uri? = null
    private var pendingCropSourceUri: Uri? = null

    fun currentCameraUri(): Uri? = pendingCameraUri

    fun createCameraUri(): Uri? {
        val uri = TankPhotoStorage.createCameraCaptureUri(
            context = contextProvider(),
            ownerToken = ownerTokenProvider()
        )

        pendingCameraUri = uri
        return uri
    }

    fun markCropSource(sourceUri: Uri) {
        pendingCropSourceUri = sourceUri
    }

    fun createCropOutputUri(): Uri? {
        return TankPhotoStorage.createCropOutputUri(
            context = contextProvider(),
            ownerToken = ownerTokenProvider()
        )
    }

    fun buildCropIntent(
        sourceUri: Uri,
        destinationUri: Uri,
        title: String
    ): Intent {
        val context = contextProvider()
        val options = UCrop.Options().apply {
            setToolbarTitle(title)
            setCircleDimmedLayer(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setHideBottomControls(true)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(false)

            val toolbarColor = ContextCompat.getColor(
                context,
                R.color.crop_toolbar_bg
            )
            setToolbarColor(toolbarColor)
            setToolbarWidgetColor(Color.WHITE)
            setRootViewBackgroundColor(toolbarColor)
            setActiveControlsWidgetColor(toolbarColor)
            setToolbarCancelDrawable(R.drawable.ic_back)
        }

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1600, 900)
            .withOptions(options)
            .getIntent(context)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
    }

    fun toContentUri(uri: Uri): Uri {
        return TankPhotoStorage.toContentUriIfInternalFile(
            context = contextProvider(),
            uri = uri
        ) ?: uri
    }

    fun deleteInternalPhoto(uriString: String?) {
        TankPhotoStorage.deleteInternalPhoto(
            context = contextProvider(),
            uriString = uriString
        )
    }

    fun cleanupPendingCameraImage() {
        deleteInternalPhoto(pendingCameraUri?.toString())
        pendingCameraUri = null
    }

    fun cleanupPendingCropSource(
        keepUriString: String? = null
    ) {
        val sourceUri = pendingCropSourceUri

        if (sourceUri != null && sourceUri.toString() != keepUriString) {
            deleteInternalPhoto(sourceUri.toString())
        }

        if (sourceUri == pendingCameraUri) {
            pendingCameraUri = null
        }

        pendingCropSourceUri = null
    }

    fun cleanupAllPending() {
        cleanupPendingCropSource()
        cleanupPendingCameraImage()
    }
}
