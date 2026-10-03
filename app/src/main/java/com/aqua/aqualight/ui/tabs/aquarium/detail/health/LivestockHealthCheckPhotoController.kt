package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.app.Activity
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

internal class LivestockHealthCheckPhotoController(
    private val fragment: Fragment,
    private val mediaFlow: MediaFlowCoordinatorViewModel,
    private val hasView: () -> Boolean,
    private val onPhotoChanged: (String?) -> Unit
) {
    private val permissionCoordinator = CapabilityPermissionCoordinator(fragment) { action ->
        if (action == ACTION_CAPTURE_PHOTO) openCamera()
    }

    private val galleryLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (hasView() && uri != null) {
            fragment.lifecycleScope.launch { startCrop(uri) }
        }
    }

    private val cameraLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        fragment.lifecycleScope.launch {
            val cameraUri = mediaFlow.currentCameraUri()
            if (hasView() && success && cameraUri != null) {
                startCrop(cameraUri)
            } else {
                mediaFlow.cancelCamera()
            }
        }
    }

    private val cropLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fragment.lifecycleScope.launch {
            if (!hasView()) {
                mediaFlow.cancelCrop()
                return@launch
            }
            when {
                result.resultCode == Activity.RESULT_OK -> {
                    val output = result.data?.let(UCrop::getOutput)
                    val accepted = output?.let { uri -> mediaFlow.acceptCrop(uri) }
                    if (accepted != null) {
                        onPhotoChanged(accepted.toString())
                    } else {
                        mediaFlow.cancelCrop()
                        showError()
                    }
                }

                result.resultCode == UCrop.RESULT_ERROR -> {
                    mediaFlow.cancelCrop()
                    showError()
                }

                else -> mediaFlow.cancelCrop()
            }
        }
    }

    fun bind(owner: LifecycleOwner) {
        fragment.childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            owner
        ) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> permissionCoordinator.runWhenGranted(
                    capability = AppCapability.CAMERA_PHOTO,
                    actionToken = ACTION_CAPTURE_PHOTO
                )
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_REMOVE -> removePhoto()
            }
        }
    }

    fun showSource(title: String) {
        val manager = fragment.childFragmentManager
        if (manager.isStateSaved || manager.findFragmentByTag(PhotoSourceBottomSheet.TAG) != null) {
            return
        }
        PhotoSourceBottomSheet.newInstance(
            title = title,
            showRemove = !selectedUri().isNullOrBlank()
        ).show(manager, PhotoSourceBottomSheet.TAG)
    }

    fun selectedUri(): String? = mediaFlow.selection.value.selectedUri

    fun removePhoto() {
        fragment.lifecycleScope.launch {
            mediaFlow.selectRemoval()
            if (hasView()) onPhotoChanged(null)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openCamera() {
        fragment.lifecycleScope.launch {
            val cameraUri = mediaFlow.createCameraUri()
            if (hasView() && cameraUri != null) {
                cameraLauncher.launch(cameraUri)
            } else {
                mediaFlow.cancelCamera()
                showError()
            }
        }
    }

    private suspend fun startCrop(sourceUri: Uri) {
        if (!hasView()) return
        fragment.setFragmentGlobalLoading(true)
        try {
            when (
                val preparation = mediaFlow.prepareCropIntent(
                    sourceUri = sourceUri,
                    title = fragment.getString(R.string.aquarium_livestock_photo_crop_title)
                )
            ) {
                is MediaCropPreparationResult.Ready -> cropLauncher.launch(preparation.intent)
                is MediaCropPreparationResult.Failure,
                MediaCropPreparationResult.StorageFailure -> {
                    mediaFlow.cancelCamera()
                    showError()
                }
            }
        } finally {
            fragment.setFragmentGlobalLoading(false)
        }
    }

    private fun showError() {
        (fragment.activity as? BaseActivity)?.showSnackBar(
            fragment.getString(R.string.aquarium_photo_crop_failed),
            BaseActivity.SnackType.ERROR
        )
    }

    private companion object {
        const val ACTION_CAPTURE_PHOTO = "livestock_health_check_capture_photo"
    }
}
