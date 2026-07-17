package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantTagFragment
import com.aqua.aqualight.ui.tabs.aquarium.photo.TankPhotoFlowCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.plants.PlantTagUiRenderer
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.yalantis.ucrop.UCrop

class TankPhotoFragment : Fragment(R.layout.fragment_tank_photo), TankStepFragment {

    private var _binding: FragmentTankPhotoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    private var selectedPhotoUri: String? = null

    private val photoFlowCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        TankPhotoFlowCoordinator(
            contextProvider = { requireContext() },
            ownerTokenProvider = { "draft" }
        )
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_CAPTURE_TANK_PHOTO -> startCameraCapture()
        }
    }

    private var isOpeningNextStep: Boolean = false

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = photoFlowCoordinator.currentCameraUri()

        if (success && uri != null) {
            openCropScreen(uri)
        } else {
            photoFlowCoordinator.cleanupPendingCameraImage()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            openCropScreen(uri)
        }
    }

    private val uCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data

        when {
            result.resultCode == Activity.RESULT_OK && data != null -> {
                val croppedUri = UCrop.getOutput(data)

                if (croppedUri == null) {
                    photoFlowCoordinator.cleanupPendingCropSource()
                    return@registerForActivityResult
                }

                handleCroppedImage(croppedUri)
                photoFlowCoordinator.cleanupPendingCropSource()
            }

            result.resultCode == UCrop.RESULT_ERROR && data != null -> {
                val error = UCrop.getError(data)
                error?.printStackTrace()

                showInfoDialog(
                    title = getString(R.string.aquarium_photo_crop_error_title),
                    message = error?.localizedMessage
                        ?: getString(R.string.aquarium_photo_crop_failed)
                )

                photoFlowCoordinator.cleanupPendingCropSource()
            }

            result.resultCode != Activity.RESULT_OK -> {
                photoFlowCoordinator.cleanupPendingCropSource()
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankPhotoBinding.bind(view)

        setupExistingPhoto()
        setupPhotoSourceResultListener()
        setupPlantTagResultListener()
        setupClickListeners()
        renderSelectedPlants()
    }

    private fun setupExistingPhoto() {
        val currentPhotoUri = viewModel.tankDraft.photoUri

        if (!currentPhotoUri.isNullOrBlank()) {
            selectedPhotoUri = currentPhotoUri

            binding.imgAquariumPhoto.load(currentPhotoUri) {
                placeholder(R.drawable.nature_aquarium)
                error(R.drawable.nature_aquarium)
                crossfade(true)
            }
        } else {
            binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)
        }
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_GALLERY -> {
                    openGallery()
                }

                PhotoSourceBottomSheet.RESULT_CAMERA -> {
                    checkCameraPermissionAndOpen()
                }

                PhotoSourceBottomSheet.RESULT_REMOVE -> {
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

        savedStateHandle.getLiveData<Boolean?>(
            PlantTagFragment.RESULT_KEY
        ).observe(viewLifecycleOwner) { updated ->
            if (updated != true) {
                return@observe
            }

            savedStateHandle.set<Boolean?>(
                PlantTagFragment.RESULT_KEY,
                null
            )

            renderSelectedPlants()
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningNextStep = false
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            PhotoSourceBottomSheet
                .newInstance(
                    title = getString(R.string.aquarium_photo_title),
                    showRemove = !selectedPhotoUri.isNullOrBlank()
                )
                .show(
                    childFragmentManager,
                    PhotoSourceBottomSheet.TAG
                )
        }

        binding.btnAddPlant.setOnClickListener {
            if (isOpeningNextStep) {
                return@setOnClickListener
            }

            viewModel.updateTankPhoto(selectedPhotoUri)

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
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    private fun checkCameraPermissionAndOpen() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_TANK_PHOTO
        )
    }

    private fun startCameraCapture() {
        val uri = photoFlowCoordinator.createCameraUri()

        if (uri == null) {
            showInfoDialog(
                title = getString(R.string.aquarium_photo_error_title),
                message = getString(R.string.aquarium_photo_temp_file_failed)
            )
            return
        }

        takePictureLauncher.launch(uri)
    }

    private fun openCropScreen(sourceUri: Uri) {
        val destUri = photoFlowCoordinator.createCropOutputUri()

        if (destUri == null) {
            photoFlowCoordinator.cleanupPendingCameraImage()
            showInfoDialog(
                title = getString(R.string.aquarium_photo_error_title),
                message = getString(R.string.aquarium_photo_temp_crop_failed)
            )
            return
        }

        photoFlowCoordinator.markCropSource(sourceUri)

        uCropLauncher.launch(
            photoFlowCoordinator.buildCropIntent(
                sourceUri = sourceUri,
                destinationUri = destUri,
                title = getString(R.string.aquarium_photo_crop_title)
            )
        )
    }

    private fun handleCroppedImage(croppedFileUri: Uri) {
        val contentUri = photoFlowCoordinator.toContentUri(croppedFileUri)
        val previousPhotoUri = selectedPhotoUri

        binding.imgAquariumPhoto.load(contentUri) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }

        selectedPhotoUri = contentUri.toString()
        viewModel.updateTankPhoto(selectedPhotoUri)

        if (previousPhotoUri != selectedPhotoUri) {
            photoFlowCoordinator.deleteInternalPhoto(previousPhotoUri)
        }
    }

    private fun removeSelectedPhoto() {
        val previousPhotoUri = selectedPhotoUri

        selectedPhotoUri = null
        viewModel.updateTankPhoto(null)
        binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)

        photoFlowCoordinator.deleteInternalPhoto(previousPhotoUri)
    }

    private fun renderSelectedPlants() {
        PlantTagUiRenderer.renderSelectedPlantList(
            container = binding.selectedPlantsContainer,
            plants = viewModel.tankDraft.plants,
            onRemoveAt = { index ->
                val updatedPlants = viewModel.tankDraft.plants
                    .toMutableList()
                    .apply {
                        removeAt(index)
                    }

                viewModel.updateTankPlants(updatedPlants)
                renderSelectedPlants()
            }
        )
    }

    private fun showInfoDialog(
        title: String,
        message: String
    ) {
        if (!isAdded) {
            return
        }

        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            buttonTextResId = android.R.string.ok
        )
    }

    override fun validateAndSave(): Boolean {
        viewModel.updateTankPhoto(selectedPhotoUri)
        return true
    }

    override fun onDestroyView() {
        photoFlowCoordinator.cleanupAllPending()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val ACTION_CAPTURE_TANK_PHOTO = "capture_tank_photo"
    }
}
