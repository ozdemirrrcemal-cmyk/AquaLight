package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankSettingsBasicBinding
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSettingsEditorBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDatePolicy
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TankSettingsBasicFragment : Fragment(R.layout.fragment_tank_settings_basic) {

    private var _binding: FragmentTankSettingsBasicBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var currentTank: AquariumTankSnapshot? = null

    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = AppMediaScope.TANK,
            ownerToken = tankId.toString(),
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.TANK,
            mediaProcessor = container.feedbackMediaProcessor
        )
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_TANK_SETTINGS_PHOTO) openCamera()
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
                        saveTankPhoto(accepted)
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
                        message = error?.message ?: getString(R.string.aquarium_photo_crop_failed),
                        type = BaseActivity.SnackType.ERROR
                    )
                }

                else -> mediaFlow.cancelCrop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankSettingsBasicBinding.bind(view)

        setupClickListeners()
        setupPhotoSourceResultListener()
        setupTankEditorResultListener()
        observeTank()
    }

    private fun setupClickListeners() = with(binding) {
        btnChangePhoto.setOnClickListener { showPhotoSourceSheet() }
        rowTankName.setOnClickListener { showTankNameSheet() }
        rowTankType.setOnClickListener { showTankTypeSheet() }
        rowSize.setOnClickListener { showTankSizeSheet() }
        rowVolume.setOnClickListener { toggleVolumeUnit() }
        rowSetupDate.setOnClickListener { showSetupDateSheet() }
        rowStyle.setOnClickListener { showStyleSheet() }
        rowIdea.setOnClickListener { showIdeaSheet() }
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> checkCameraPermissionAndOpen()
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_REMOVE -> removeTankPhoto()
            }
        }
    }

    private fun setupTankEditorResultListener() {
        childFragmentManager.setFragmentResultListener(
            TankSettingsEditorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TankSettingsEditorBottomSheet.RESULT_STATUS) !=
                TankSettingsEditorBottomSheet.RESULT_SAVED
            ) {
                return@setFragmentResultListener
            }

            val mode = result.getString(TankSettingsEditorBottomSheet.RESULT_MODE)
                ?.let { runCatching { TankSettingsEditorBottomSheet.Mode.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener

            when (mode) {
                TankSettingsEditorBottomSheet.Mode.NAME -> {
                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_name_save_failed)) {
                        aquariumTankViewModel.updateTankName(tankId, value)
                    }
                }

                TankSettingsEditorBottomSheet.Mode.TYPE -> {
                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_type_save_failed)) {
                        aquariumTankViewModel.updateTankType(tankId, value)
                    }
                }

                TankSettingsEditorBottomSheet.Mode.SIZE -> {
                    val widthCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_WIDTH_CM)
                    val lengthCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_LENGTH_CM)
                    val heightCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_HEIGHT_CM)
                    val unit = result.getString(TankSettingsEditorBottomSheet.RESULT_UNIT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    if (widthCm <= 0 || lengthCm <= 0 || heightCm <= 0) {
                        showSnackBar(
                            getString(R.string.aquarium_validation_invalid_tank_size),
                            BaseActivity.SnackType.WARNING
                        )
                        return@setFragmentResultListener
                    }
                    runTankUpdate(getString(R.string.aquarium_error_tank_size_save_failed)) {
                        aquariumTankViewModel.updateTankSize(
                            tankId = tankId,
                            widthCm = widthCm,
                            lengthCm = lengthCm,
                            heightCm = heightCm,
                            sizeUnit = unit
                        )
                    }
                }

                TankSettingsEditorBottomSheet.Mode.SETUP_DATE -> {
                    val millis = result.getLong(TankSettingsEditorBottomSheet.RESULT_MILLIS)
                    runTankUpdate(getString(R.string.aquarium_error_setup_date_save_failed)) {
                        aquariumTankViewModel.updateTankSetupDate(tankId, millis)
                    }
                }

                TankSettingsEditorBottomSheet.Mode.STYLE -> {
                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_style_save_failed)) {
                        aquariumTankViewModel.updateTankStyle(tankId, value)
                    }
                }

                TankSettingsEditorBottomSheet.Mode.IDEA -> {
                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT).orEmpty()
                    runTankUpdate(getString(R.string.aquarium_error_idea_save_failed)) {
                        aquariumTankViewModel.updateTankDescription(tankId, value)
                    }
                }
            }
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            tanks.firstOrNull { it.id == tankId }?.let(::bindTank)
        }
    }

    private fun bindTank(tank: AquariumTankSnapshot) {
        if (_binding == null) return
        currentTank = tank
        mediaFlow.initializeSelection(tank.photoUri)

        if (!tank.photoUri.isNullOrBlank()) {
            binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                placeholder(R.drawable.nature_aquarium)
                error(R.drawable.nature_aquarium)
                crossfade(true)
            }
        } else {
            binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
        }

        binding.tvSettingTankName.text = tank.name
        binding.tvSettingTankType.text = tank.tankType.ifBlank {
            getString(R.string.aquarium_no_value_placeholder)
        }
        binding.tvSettingSizeTitle.text = getSizeTitleText(tank)
        binding.tvSettingSize.text = getSizeText(tank)
        binding.tvSettingVolume.text = getVolumeText(tank)
        binding.tvSettingSetupDate.text = getSetupDateText(tank.setupDateMillis)
        binding.tvSettingStyle.text = tank.tankStyle.ifBlank {
            getString(R.string.aquarium_no_value_placeholder)
        }
        binding.tvSettingIdea.text = tank.description.ifBlank {
            getString(R.string.aquarium_no_idea_added)
        }
    }

    fun openCareProfileAction(action: String) {
        when (action) {
            ACTION_TANK_NAME -> showTankNameSheet()
            ACTION_TANK_TYPE -> showTankTypeSheet()
            ACTION_TANK_SIZE -> showTankSizeSheet()
            ACTION_SETUP_DATE -> showSetupDateSheet()
            ACTION_STYLE -> showStyleSheet()
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        if (_binding == null) return
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    private fun showPhotoSourceSheet() {
        PhotoSourceBottomSheet.newInstance(
            title = getString(R.string.aquarium_photo_title),
            showRemove = !currentTank?.photoUri.isNullOrBlank()
        ).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
    }

    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun checkCameraPermissionAndOpen() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_TANK_SETTINGS_PHOTO
        )
    }

    private fun openCamera() {
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

    private suspend fun saveTankPhoto(contentUri: Uri) {
        if (_binding == null) {
            mediaFlow.rollbackSelection()
            return
        }
        binding.imgTankPhoto.load(contentUri) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }

        try {
            aquariumTankViewModel.updateTankPhoto(tankId, contentUri.toString())
            mediaFlow.commitSelection(deletePersistedMedia = false)
            showSnackBar(
                getString(R.string.aquarium_photo_updated),
                BaseActivity.SnackType.SUCCESS
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            exception.printStackTrace()
            mediaFlow.rollbackSelection()
            currentTank?.let(::bindTank)
            showSnackBar(
                getString(R.string.aquarium_photo_save_failed),
                BaseActivity.SnackType.ERROR
            )
        }
    }

    private fun removeTankPhoto() {
        val tank = currentTank ?: return
        if (_binding == null) return
        lifecycleScope.launch {
            if (tank.photoUri.isNullOrBlank()) {
                _binding?.imgTankPhoto?.setImageResource(R.drawable.nature_aquarium)
                return@launch
            }

            mediaFlow.selectRemoval()
            _binding?.imgTankPhoto?.setImageResource(R.drawable.nature_aquarium)
            try {
                aquariumTankViewModel.updateTankPhoto(tankId, null)
                mediaFlow.commitSelection(deletePersistedMedia = false)
                showSnackBar(
                    getString(R.string.aquarium_photo_removed),
                    BaseActivity.SnackType.SUCCESS
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                exception.printStackTrace()
                mediaFlow.rollbackSelection()
                currentTank?.let(::bindTank)
                showSnackBar(
                    getString(R.string.aquarium_photo_remove_failed),
                    BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun runTankUpdate(
        errorMessage: String,
        update: suspend () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                update()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                exception.printStackTrace()
                showSnackBar(errorMessage, BaseActivity.SnackType.ERROR)
            }
        }
    }

    private fun showTankNameSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.NAME,
            title = getString(R.string.aquarium_tank_name_title),
            currentText = tank.name,
            validationMessage = getString(R.string.aquarium_validation_tank_name_min)
        )
    }

    private fun showTankTypeSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.TYPE,
            title = "Tank Type",
            currentText = tank.tankType
        )
    }

    private fun showTankSizeSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.SIZE,
            title = getString(R.string.aquarium_tank_size_title),
            validationMessage = getString(R.string.aquarium_validation_invalid_tank_size),
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            currentUnit = tank.sizeUnit
        )
    }

    private fun toggleVolumeUnit() {
        val tank = currentTank ?: return
        val newUnit = if (tank.volumeUnit.equals("gal", ignoreCase = true)) "L" else "gal"
        runTankUpdate(getString(R.string.aquarium_error_volume_unit_save_failed)) {
            aquariumTankViewModel.updateTankVolumeUnit(tankId, newUnit)
        }
    }

    private fun showSetupDateSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.SETUP_DATE,
            title = "Setup Date",
            currentMillis = tank.setupDateMillis,
            minYear = AquariumDatePolicy.minSetupYear(),
            maxYear = AquariumDatePolicy.maxSetupYear(),
            locale = AquariumDatePolicy.setupDateLocale
        )
    }

    private fun showStyleSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.STYLE,
            title = "Tank Style",
            currentText = tank.tankStyle,
            validationMessage = getString(R.string.aquarium_error_tank_style_save_failed)
        )
    }

    private fun showIdeaSheet() {
        val tank = currentTank ?: return
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.IDEA,
            title = getString(R.string.aquarium_tank_concept_title),
            currentText = tank.description
        )
    }

    private fun getSizeTitleText(tank: AquariumTankSnapshot): String {
        return AquariumDimensionFormatter.sizeTitle(requireContext(), tank.sizeUnit)
    }

    private fun getSizeText(tank: AquariumTankSnapshot): String {
        return AquariumDimensionFormatter.sizeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            sizeUnit = tank.sizeUnit,
            separator = " x "
        )
    }

    private fun getVolumeText(tank: AquariumTankSnapshot): String {
        return AquariumDimensionFormatter.volumeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            volumeUnit = tank.volumeUnit,
            rounded = true
        )
    }

    private fun getSetupDateText(setupDateMillis: Long?): String {
        return AquariumDatePolicy.formatSetupDate(
            millis = setupDateMillis,
            emptyText = getString(R.string.aquarium_no_value_placeholder)
        )
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ACTION_CAPTURE_TANK_SETTINGS_PHOTO = "capture_tank_settings_photo"

        const val ACTION_TANK_NAME = "tank_name"
        const val ACTION_TANK_TYPE = "tank_type"
        const val ACTION_TANK_SIZE = "tank_size"
        const val ACTION_SETUP_DATE = "setup_date"
        const val ACTION_STYLE = "style"

        fun newInstance(tankId: Long): TankSettingsBasicFragment {
            return TankSettingsBasicFragment().apply {
                arguments = Bundle().apply { putLong(ARG_TANK_ID, tankId) }
            }
        }
    }
}
