package com.aqua.aqualight.ui.common.media

import android.app.Activity
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Central camera/gallery/crop lifecycle for owner-scoped plant and livestock record photos. */
abstract class TankRecordPhotoFragment(
    @LayoutRes layoutRes: Int,
    private val photoScope: AppMediaScope,
    @StringRes private val photoTitleRes: Int,
    @StringRes private val cropTitleRes: Int
) : Fragment(layoutRes) {
    protected val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = photoScope,
            ownerToken = photoTankId.toString(),
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.RECORD,
            mediaProcessor = container.imageMediaProcessor
        )
    }

    protected abstract val photoTankId: Long
    protected abstract val hasPhotoView: Boolean
    protected val photoTarget: RecordPhotoTargetViewModel by viewModels()
    protected open val photoActionsBlocked: Boolean get() = false
    protected abstract suspend fun onPhotoSelected(photoUri: String?)

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_RECORD_PHOTO) openCamera()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (hasPhotoView && uri != null && photoTarget.isInProgress) {
            lifecycleScope.launch { startImageCrop(uri) }
        } else {
            photoTarget.finish()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        lifecycleScope.launch {
            val cameraUri = mediaFlow.currentCameraUri()
            if (!hasPhotoView) {
                mediaFlow.cancelCamera()
                photoTarget.finish()
                return@launch
            }
            if (success && cameraUri != null) {
                startImageCrop(cameraUri)
            } else {
                mediaFlow.cancelCamera()
                photoTarget.finish()
            }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        lifecycleScope.launch {
            try {
                if (!hasPhotoView || !photoTarget.isInProgress) {
                    mediaFlow.cancelCrop()
                    return@launch
                }
                when {
                    result.resultCode == Activity.RESULT_OK -> {
                        val outputUri = result.data?.let(UCrop::getOutput)
                        val accepted = outputUri?.let { mediaFlow.acceptCrop(it) }
                        if (accepted != null) {
                            onPhotoSelected(accepted.toString())
                        } else {
                            mediaFlow.cancelCrop()
                            showPhotoSnackBar(
                                getString(R.string.aquarium_photo_crop_failed),
                                BaseActivity.SnackType.ERROR
                            )
                        }
                    }

                    result.resultCode == UCrop.RESULT_ERROR -> {
                        val error = result.data?.let(UCrop::getError)
                        mediaFlow.cancelCrop()
                        showPhotoSnackBar(
                            error?.message ?: getString(R.string.aquarium_photo_crop_failed),
                            BaseActivity.SnackType.ERROR
                        )
                    }

                    else -> mediaFlow.cancelCrop()
                }
            } finally {
                photoTarget.finish()
            }
        }
    }

    protected fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> permissionCoordinator.runWhenGranted(
                    capability = AppCapability.CAMERA_PHOTO,
                    actionToken = ACTION_CAPTURE_RECORD_PHOTO
                )
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_REMOVE -> {
                    if (photoTarget.begin()) lifecycleScope.launch {
                        try {
                            mediaFlow.selectRemoval()
                            onPhotoSelected(null)
                        } finally {
                            photoTarget.finish()
                        }
                    }
                }
            }
        }
    }

    protected fun showRecordPhotoSource(
        recordId: Long, ownerUid: String, persistedUri: String? = null, resetSelection: Boolean = false
    ) {
        val sourceIsOpen = childFragmentManager.findFragmentByTag(PhotoSourceBottomSheet.TAG) != null
        if (photoActionsBlocked || photoTarget.isInProgress || sourceIsOpen) return
        if (!photoTarget.select(ownerUid, recordId)) return
        permissionCoordinator.cancelPending()
        if (resetSelection) mediaFlow.markExternallyOwnedSelection(persistedUri)
        PhotoSourceBottomSheet.newInstance(
            title = getString(photoTitleRes),
            showRemove = !mediaFlow.selection.value.selectedUri.isNullOrBlank()
        ).showNow(childFragmentManager, PhotoSourceBottomSheet.TAG)
    }

    private fun openGallery() {
        if (!photoTarget.begin()) return
        runCatching {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            photoTarget.finish()
            showPhotoSnackBar(getString(R.string.aquarium_photo_crop_failed), BaseActivity.SnackType.ERROR)
        }
    }

    private fun openCamera() {
        if (!photoTarget.begin()) return
        lifecycleScope.launch {
            var launched = false
            try {
                runCatching {
                    val cameraUri = mediaFlow.createCameraUri()
                    if (hasPhotoView && cameraUri != null) {
                        cameraLauncher.launch(cameraUri)
                        launched = true
                    } else if (hasPhotoView) {
                        showPhotoSnackBar(
                            getString(R.string.aquarium_photo_temp_file_failed), BaseActivity.SnackType.ERROR
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    showPhotoSnackBar(
                        getString(R.string.aquarium_photo_temp_file_failed), BaseActivity.SnackType.ERROR
                    )
                }
            } finally {
                if (!launched) {
                    withContext(NonCancellable) { mediaFlow.cancelCamera() }
                    photoTarget.finish()
                }
            }
        }
    }

    private suspend fun startImageCrop(sourceUri: Uri) {
        if (!hasPhotoView || !photoTarget.isInProgress) {
            mediaFlow.cancelCamera()
            photoTarget.finish()
            return
        }
        setFragmentGlobalLoading(true)
        try {
            runCatching {
                when (
                    val preparation = mediaFlow.prepareCropIntent(
                        sourceUri = sourceUri,
                        title = getString(cropTitleRes)
                    )
                ) {
                    is MediaCropPreparationResult.Ready -> {
                        if (hasPhotoView) {
                            cropLauncher.launch(preparation.intent)
                        } else {
                            mediaFlow.cancelCrop()
                            photoTarget.finish()
                        }
                    }

                    is MediaCropPreparationResult.Failure,
                    MediaCropPreparationResult.StorageFailure -> {
                        mediaFlow.cancelCamera()
                        photoTarget.finish()
                        showPhotoSnackBar(
                            getString(R.string.aquarium_photo_crop_failed),
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }.onFailure { error ->
                withContext(NonCancellable) { mediaFlow.cancelCrop() }
                photoTarget.finish()
                if (error is CancellationException) throw error
                showPhotoSnackBar(getString(R.string.aquarium_photo_crop_failed), BaseActivity.SnackType.ERROR)
            }
        } finally {
            setFragmentGlobalLoading(false)
        }
    }

    protected fun showPhotoSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        if (!hasPhotoView) return
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    private companion object {
        const val ACTION_CAPTURE_RECORD_PHOTO = "capture_record_photo"
    }
}
