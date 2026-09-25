package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankDetailPlantsBinding
import com.aqua.aqualight.databinding.ItemTankPlantPhotoBinding
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TankDetailPlantsFragment : Fragment(R.layout.fragment_tank_detail_plants) {
    private var _binding: FragmentTankDetailPlantsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
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

    private var tankId: Long = 0L
    private var currentPlants: List<AquariumPlantTag> = emptyList()
    private var activePhotoPlantId: Long? = null
    private var isOpeningPlantTagScreen: Boolean = false
    private var isOpeningPlantHealth: Boolean = false
    private var isPhotoMutationInProgress: Boolean = false

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_PLANT_PHOTO) openCamera()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (_binding != null && uri != null) {
            lifecycleScope.launch { startImageCrop(uri) }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        lifecycleScope.launch {
            val cameraUri = mediaFlow.currentCameraUri()
            if (_binding == null) {
                mediaFlow.cancelCamera()
                return@launch
            }
            if (success && cameraUri != null) {
                startImageCrop(cameraUri)
            } else {
                mediaFlow.cancelCamera()
            }
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
            when {
                result.resultCode == Activity.RESULT_OK -> {
                    val outputUri = result.data?.let(UCrop::getOutput)
                    val accepted = outputUri?.let { mediaFlow.acceptCrop(it) }
                    if (accepted != null) {
                        savePlantPhoto(accepted)
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
        activePhotoPlantId = savedInstanceState
            ?.getLong(STATE_ACTIVE_PHOTO_PLANT_ID)
            ?.takeIf { plantId -> plantId > 0L }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        activePhotoPlantId?.let { plantId ->
            outState.putLong(STATE_ACTIVE_PHOTO_PLANT_ID, plantId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankDetailPlantsBinding.bind(view)

        binding.plantHealthEntry.ivHealthEntryIcon.setImageResource(
            R.drawable.ic_health_plant_24
        )
        binding.plantHealthEntry.tvHealthEntryTitle.setText(
            R.string.plant_health_entry_title
        )
        binding.plantHealthEntry.tvHealthEntrySummary.setText(
            R.string.plant_health_entry_summary
        )
        binding.plantHealthEntry.tvHealthEntryLastCheck.setText(
            R.string.plant_health_entry_last_check
        )
        binding.plantHealthEntry.root.setOnClickListener {
            openPlantHealth()
        }

        binding.btnAddPlant.setOnClickListener { openPlantTagScreen() }
        setupPhotoSourceResultListener()

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId } ?: return@observe
            currentPlants = tank.plants
            renderPlants(currentPlants)
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantTagScreen = false
        isOpeningPlantHealth = false
    }

    private fun openPlantHealth() {
        if (isOpeningPlantHealth) return

        val navController = findNavController()
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                TankDetailTabArgs.PLANTS
            )

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections
                .actionTankDetailFragmentToPlantHealthFragment(tankId)
        )

        isOpeningPlantHealth = didNavigate
    }

    private fun openPlantTagScreen() {
        if (isOpeningPlantTagScreen) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.tankDetailFragment) return
        isOpeningPlantTagScreen = true
        navController.navigate(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailPlantTagFragment(
                tankId = tankId
            )
        )
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> checkCameraPermissionAndOpen()
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_REMOVE -> removePlantPhoto()
            }
        }
    }

    private fun renderPlants(plants: List<AquariumPlantTag>) {
        if (_binding == null) return
        binding.plantListContainer.removeAllViews()
        plants.forEachIndexed { index, plant ->
            binding.plantListContainer.addView(createPlantCard(index, plant))
        }
    }

    private fun createPlantCard(index: Int, plant: AquariumPlantTag): View {
        val item = ItemTankPlantPhotoBinding.inflate(
            layoutInflater,
            binding.plantListContainer,
            false
        )
        item.tvPlantOrderBadge.text = (index + 1).toString()
        item.tvPlantCategory.text = plant.category
        item.tvPlantName.text = plant.plantName
        item.plantCard.contentDescription = getString(
            R.string.aquarium_plant_photo_action_description,
            plant.plantName
        )
        bindPlantPhoto(item.imgPlantPhoto, plant.photoUri)
        item.plantCard.setOnClickListener {
            showPlantPhotoSource(plant)
        }
        return item.root
    }

    private fun bindPlantPhoto(imageView: ImageView, photoUri: String?) {
        if (photoUri.isNullOrBlank()) {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.ic_camera_24)
            return
        }

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.load(Uri.parse(photoUri)) {
            error(R.drawable.ic_camera_24)
            crossfade(true)
        }
    }

    private fun showPlantPhotoSource(plant: AquariumPlantTag) {
        if (isPhotoMutationInProgress) return
        if (childFragmentManager.findFragmentByTag(PhotoSourceBottomSheet.TAG) != null) return

        activePhotoPlantId = plant.id
        mediaFlow.markExternallyOwnedSelection(plant.photoUri)
        PhotoSourceBottomSheet.newInstance(
            title = getString(R.string.aquarium_plant_photo_title),
            showRemove = !plant.photoUri.isNullOrBlank()
        ).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
    }

    private fun openGallery() {
        if (activePhotoPlantId == null) return
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun checkCameraPermissionAndOpen() {
        if (activePhotoPlantId == null) return
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_PLANT_PHOTO
        )
    }

    private fun openCamera() {
        if (activePhotoPlantId == null) return
        lifecycleScope.launch {
            val cameraUri = mediaFlow.createCameraUri()
            if (_binding == null) {
                mediaFlow.cancelCamera()
                return@launch
            }
            if (cameraUri == null) {
                showSnackBar(
                    getString(R.string.aquarium_photo_temp_file_failed),
                    BaseActivity.SnackType.ERROR
                )
                return@launch
            }
            cameraLauncher.launch(cameraUri)
        }
    }

    private suspend fun startImageCrop(sourceUri: Uri) {
        if (_binding == null || activePhotoPlantId == null) {
            mediaFlow.cancelCamera()
            return
        }
        setFragmentGlobalLoading(true)
        try {
            when (
                val preparation = mediaFlow.prepareCropIntent(
                    sourceUri = sourceUri,
                    title = getString(R.string.aquarium_plant_photo_crop_title)
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
                    showSnackBar(
                        getString(R.string.aquarium_photo_crop_failed),
                        BaseActivity.SnackType.ERROR
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            setFragmentGlobalLoading(false)
        }
    }

    private suspend fun savePlantPhoto(contentUri: Uri) {
        val plantId = activePhotoPlantId
        if (_binding == null || plantId == null || isPhotoMutationInProgress) {
            mediaFlow.rollbackSelection()
            return
        }

        isPhotoMutationInProgress = true
        try {
            aquariumTankViewModel.updatePlantPhoto(
                tankId = tankId,
                plantId = plantId,
                photoUri = contentUri.toString()
            )
            mediaFlow.commitSelection(deletePersistedMedia = false)
            showSnackBar(
                getString(R.string.aquarium_plant_photo_updated),
                BaseActivity.SnackType.SUCCESS
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.printStackTrace()
            mediaFlow.rollbackSelection()
            showSnackBar(
                getString(R.string.aquarium_plant_photo_save_failed),
                BaseActivity.SnackType.ERROR
            )
        } finally {
            isPhotoMutationInProgress = false
        }
    }

    private fun removePlantPhoto() {
        val plant = activePhotoPlant() ?: return
        if (plant.photoUri.isNullOrBlank() || isPhotoMutationInProgress) return

        isPhotoMutationInProgress = true
        lifecycleScope.launch {
            try {
                mediaFlow.selectRemoval()
                aquariumTankViewModel.updatePlantPhoto(
                    tankId = tankId,
                    plantId = plant.id,
                    photoUri = null
                )
                mediaFlow.commitSelection(deletePersistedMedia = false)
                showSnackBar(
                    getString(R.string.aquarium_plant_photo_removed),
                    BaseActivity.SnackType.SUCCESS
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                error.printStackTrace()
                mediaFlow.rollbackSelection()
                showSnackBar(
                    getString(R.string.aquarium_plant_photo_remove_failed),
                    BaseActivity.SnackType.ERROR
                )
            } finally {
                isPhotoMutationInProgress = false
            }
        }
    }

    private fun activePhotoPlant(): AquariumPlantTag? {
        val plantId = activePhotoPlantId ?: return null
        return currentPlants.firstOrNull { plant -> plant.id == plantId }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        if (_binding == null) return
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val STATE_ACTIVE_PHOTO_PLANT_ID = "activePhotoPlantId"
        private const val ACTION_CAPTURE_PLANT_PHOTO = "capture_plant_photo"

        fun newInstance(tankId: Long): TankDetailPlantsFragment {
            return TankDetailPlantsFragment().apply {
                arguments = Bundle().apply { putLong(ARG_TANK_ID, tankId) }
            }
        }
    }
}
