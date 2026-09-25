package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.Activity
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Central camera/gallery/crop lifecycle for photos attached to tank plant records. */
abstract class TankPlantPhotoFragment : Fragment(R.layout.fragment_tank_detail_plants) {
    protected val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = AppMediaScope.PLANT,
            ownerToken = tankId.toString(),
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.PLANT,
            mediaProcessor = container.imageMediaProcessor
        )
    }

    protected val tankId: Long get() = requireArguments().getLong(TankDetailPlantsFragment.ARG_TANK_ID)
    protected abstract val hasPhotoView: Boolean
    protected val photoTarget: PlantPhotoTargetViewModel by viewModels()
    private var isPhotoMutationInProgress: Boolean = false

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_PLANT_PHOTO) openCamera()
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
                            persistPlantPhoto(accepted.toString())
                        } else {
                            mediaFlow.cancelCrop()
                            showSnackBar(
                                getString(R.string.aquarium_photo_crop_failed),
                                BaseActivity.SnackType.ERROR
                            )
                        }
                    }

                    result.resultCode == UCrop.RESULT_ERROR -> {
                        val error = result.data?.let(UCrop::getError)
                        mediaFlow.cancelCrop()
                        showSnackBar(
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
                    actionToken = ACTION_CAPTURE_PLANT_PHOTO
                )
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_REMOVE -> {
                    if (photoTarget.begin()) lifecycleScope.launch { persistPlantPhoto(null) }
                }
            }
        }
    }

    protected fun showPlantPhotoSource(plant: AquariumPlantTag) {
        val sourceIsOpen = childFragmentManager.findFragmentByTag(PhotoSourceBottomSheet.TAG) != null
        if (isPhotoMutationInProgress || photoTarget.isInProgress || sourceIsOpen) return

        val ownerUid = requireContext().requireAppContainer()
            .authenticatedOwnerIdentity.requireOwnerUid()
        if (!photoTarget.select(ownerUid, plant.id)) return
        permissionCoordinator.cancelPending()
        mediaFlow.markExternallyOwnedSelection(plant.photoUri)
        PhotoSourceBottomSheet.newInstance(
            title = getString(R.string.aquarium_plant_photo_title),
            showRemove = !plant.photoUri.isNullOrBlank()
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
            showSnackBar(getString(R.string.aquarium_photo_crop_failed), BaseActivity.SnackType.ERROR)
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
                        showSnackBar(
                            getString(R.string.aquarium_photo_temp_file_failed), BaseActivity.SnackType.ERROR
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    showSnackBar(
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
                        title = getString(R.string.aquarium_plant_photo_crop_title)
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
                        showSnackBar(
                            getString(R.string.aquarium_photo_crop_failed),
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }.onFailure { error ->
                withContext(NonCancellable) { mediaFlow.cancelCrop() }
                photoTarget.finish()
                if (error is CancellationException) throw error
                showSnackBar(getString(R.string.aquarium_photo_crop_failed), BaseActivity.SnackType.ERROR)
            }
        } finally {
            setFragmentGlobalLoading(false)
        }
    }

    private suspend fun persistPlantPhoto(photoUri: String?) {
        val plantId = photoTarget.plantId
        val ownerUid = photoTarget.ownerUid
        val hasTarget = plantId != null && ownerUid != null
        if (!hasPhotoView || !hasTarget || isPhotoMutationInProgress) {
            mediaFlow.rollbackSelection()
            photoTarget.finish()
            return
        }
        isPhotoMutationInProgress = true
        try {
            runCatching {
                if (photoUri == null) mediaFlow.selectRemoval()
                aquariumTankViewModel.updatePlantPhoto(
                    tankId, requireNotNull(plantId), photoUri, requireNotNull(ownerUid)
                )
                mediaFlow.commitSelection(deletePersistedMedia = false)
                val message = if (photoUri == null) R.string.aquarium_plant_photo_removed
                    else R.string.aquarium_plant_photo_updated
                showSnackBar(getString(message), BaseActivity.SnackType.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mediaFlow.rollbackSelection()
                val message = if (photoUri == null) R.string.aquarium_plant_photo_remove_failed
                    else R.string.aquarium_plant_photo_save_failed
                showSnackBar(getString(message), BaseActivity.SnackType.ERROR)
            }
        } finally {
            isPhotoMutationInProgress = false
            photoTarget.finish()
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        if (!hasPhotoView) return
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    private companion object {
        const val ACTION_CAPTURE_PLANT_PHOTO = "capture_plant_photo"
    }
}
