package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantTagFragment
import com.aqua.aqualight.ui.tabs.aquarium.plants.PlantTagUiRenderer
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TankPhotoFragment : Fragment(R.layout.fragment_tank_photo), TankStepFragment {

    private var _binding: FragmentTankPhotoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)
    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = AppMediaScope.TANK,
            ownerToken = "draft",
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.TANK,
            mediaProcessor = container.boundedImageProcessor
        )
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_TANK_PHOTO) startCameraCapture()
    }

    private var isOpeningNextStep = false

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        lifecycleScope.launch {
            val uri = mediaFlow.currentCameraUri()
            if (_binding == null) {
                mediaFlow.cancelCamera()
                return@launch
            }
            if (success && uri != null) {
                openCropScreen(uri)
            } else {
                mediaFlow.cancelCamera()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (_binding != null && uri != null) {
            lifecycleScope.launch { openCropScreen(uri) }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        lifecycleScope.launch {
            if (_binding == null) {
                mediaFlow.cancelCrop()
                return@launch
            }
            val data = result.data
            when {
                result.resultCode == Activity.RESULT_OK && data != null -> {
                    val output = UCrop.getOutput(data)
                    val accepted = output?.let { mediaFlow.acceptCrop(it) }
                    if (accepted == null) {
                        mediaFlow.cancelCrop()
                        showInfoDialog(
                            title = getString(R.string.aquarium_photo_crop_error_title),
                            message = getString(R.string.aquarium_photo_crop_failed)
                        )
                    } else {
                        acceptDraftPhoto(accepted)
                    }
                }

                result.resultCode == UCrop.RESULT_ERROR && data != null -> {
                    val error = UCrop.getError(data)
                    mediaFlow.cancelCrop()
                    showInfoDialog(
                        title = getString(R.string.aquarium_photo_crop_error_title),
                        message = error?.localizedMessage
                            ?: getString(R.string.aquarium_photo_crop_failed)
                    )
                }

                else -> mediaFlow.cancelCrop()
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankPhotoBinding.bind(view)

        mediaFlow.initializeSelection(
            persistedUri = viewModel.tankDraft.photoUri,
            externalLifecycleOwner = true
        )
        renderPhoto(viewModel.tankDraft.photoUri)
        setupPhotoSourceResultListener()
        setupPlantTagResultListener()
        setupClickListeners()
        renderSelectedPlants()
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_CAMERA -> checkCameraPermissionAndOpen()
                PhotoSourceBottomSheet.RESULT_REMOVE -> lifecycleScope.launch {
                    removeSelectedPhoto()
                }
            }
        }
    }

    private fun setupPlantTagResultListener() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<Boolean?>(PlantTagFragment.RESULT_KEY)
            .observe(viewLifecycleOwner) { updated ->
                if (updated != true) return@observe
                savedStateHandle.set<Boolean?>(PlantTagFragment.RESULT_KEY, null)
                renderSelectedPlants()
            }
    }

    override fun onResume() {
        super.onResume()
        isOpeningNextStep = false
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            PhotoSourceBottomSheet.newInstance(
                title = getString(R.string.aquarium_photo_title),
                showRemove = !viewModel.tankDraft.photoUri.isNullOrBlank()
            ).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
        }

        binding.btnAddPlant.setOnClickListener {
            if (isOpeningNextStep) return@setOnClickListener
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.tankPhotoStepFragment) {
                isOpeningNextStep = true
                navController.navigate(
                    TankPhotoFragmentDirections
                        .actionTankPhotoStepFragmentToCreatePlantTagFragment()
                )
            }
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun checkCameraPermissionAndOpen() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_TANK_PHOTO
        )
    }

    private fun startCameraCapture() {
        lifecycleScope.launch {
            val uri = mediaFlow.createCameraUri()
            if (_binding == null) {
                mediaFlow.cancelCamera()
                return@launch
            }
            if (uri == null) {
                showInfoDialog(
                    title = getString(R.string.aquarium_photo_error_title),
                    message = getString(R.string.aquarium_photo_temp_file_failed)
                )
                return@launch
            }
            takePictureLauncher.launch(uri)
        }
    }

    private suspend fun openCropScreen(sourceUri: Uri) {
        if (_binding == null) {
            mediaFlow.cancelCamera()
            return
        }
        setFragmentGlobalLoading(true)
        try {
            when (
                val preparation = mediaFlow.prepareCropIntent(
                    sourceUri = sourceUri,
                    title = getString(R.string.aquarium_photo_crop_title)
                )
            ) {
                is MediaCropPreparationResult.Ready -> {
                    if (_binding != null) {
                        cropLauncher.launch(preparation.intent)
                    } else {
                        mediaFlow.cancelCrop()
                    }
                }

                is MediaCropPreparationResult.Failure,
                MediaCropPreparationResult.StorageFailure -> {
                    mediaFlow.cancelCamera()
                    showInfoDialog(
                        title = getString(R.string.aquarium_photo_error_title),
                        message = getString(R.string.aquarium_photo_crop_failed)
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            setFragmentGlobalLoading(false)
        }
    }

    private suspend fun acceptDraftPhoto(contentUri: Uri) {
        if (_binding == null) {
            mediaFlow.rollbackSelection()
            return
        }
        val previous = viewModel.tankDraft.photoUri
        val newValue = contentUri.toString()
        viewModel.updateTankPhoto(newValue)
        if (previous != newValue) mediaFlow.deleteInternalMedia(previous)
        mediaFlow.markExternallyOwnedSelection(newValue)
        renderPhoto(newValue)
    }

    private suspend fun removeSelectedPhoto() {
        val previous = viewModel.tankDraft.photoUri
        viewModel.updateTankPhoto(null)
        mediaFlow.deleteInternalMedia(previous)
        mediaFlow.markExternallyOwnedSelection(null)
        renderPhoto(null)
    }

    private fun renderPhoto(uriString: String?) {
        if (_binding == null) return
        if (uriString.isNullOrBlank()) {
            binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)
            return
        }
        binding.imgAquariumPhoto.load(uriString) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }
    }

    private fun renderSelectedPlants() {
        if (_binding == null) return
        PlantTagUiRenderer.renderSelectedPlantList(
            container = binding.selectedPlantsContainer,
            plants = viewModel.tankDraft.plants,
            onRemoveAt = { index ->
                val updated = viewModel.tankDraft.plants.toMutableList().apply {
                    removeAt(index)
                }
                viewModel.updateTankPlants(updated)
                renderSelectedPlants()
            }
        )
    }

    private fun showInfoDialog(
        title: String,
        message: String
    ) {
        if (!isAdded || _binding == null) return
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            buttonTextResId = android.R.string.ok
        )
    }

    override fun validateAndSave(): Boolean = true

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTION_CAPTURE_TANK_PHOTO = "capture_tank_photo"
    }
}
