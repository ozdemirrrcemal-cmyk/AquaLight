package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.ContentSheetIdeaBinding
import com.aqua.aqualight.databinding.ContentSheetTankNameBinding
import com.aqua.aqualight.databinding.FragmentTankSettingsBasicBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SettingsContentBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SetupDateBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSizeBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankStyleBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankTypeBottomSheet
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDatePolicy
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import com.aqua.aqualight.ui.tabs.aquarium.photo.TankPhotoFlowCoordinator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

class TankSettingsBasicFragment : Fragment(R.layout.fragment_tank_settings_basic) {

    private var _binding: FragmentTankSettingsBasicBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var currentTank: AquariumTankSnapshot? = null

    private val photoFlowCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        TankPhotoFlowCoordinator(
            contextProvider = { requireContext() },
            ownerTokenProvider = { tankId.toString() }
        )
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_CAPTURE_TANK_SETTINGS_PHOTO -> openCamera()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            startImageCrop(uri)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFlowCoordinator.currentCameraUri()?.let { uri ->
                startImageCrop(uri)
            }
        } else {
            photoFlowCoordinator.cleanupPendingCameraImage()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = result.data?.let { intent ->
                UCrop.getOutput(intent)
            }

            if (outputUri != null) {
                saveTankPhoto(outputUri)
            } else {
                photoFlowCoordinator.cleanupPendingCropSource()
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { intent ->
                UCrop.getError(intent)
            }

            showSnackBar(
                message = error?.message ?: getString(R.string.aquarium_photo_crop_failed),
                type = BaseActivity.SnackType.ERROR
            )

            photoFlowCoordinator.cleanupPendingCropSource()
        } else {
            photoFlowCoordinator.cleanupPendingCropSource()
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankSettingsBasicBinding.bind(view)

        setupClickListeners()
        setupPhotoSourceResultListener()
        observeTank()
    }

    private fun setupClickListeners() {
        binding.btnChangePhoto.setOnClickListener {
            showPhotoSourceSheet()
        }

        binding.rowTankName.setOnClickListener {
            showTankNameSheet()
        }

        binding.rowTankType.setOnClickListener {
            showTankTypeSheet()
        }

        binding.rowSize.setOnClickListener {
            showTankSizeSheet()
        }

        binding.rowVolume.setOnClickListener {
            toggleVolumeUnit()
        }

        binding.rowSetupDate.setOnClickListener {
            showSetupDateSheet()
        }

        binding.rowStyle.setOnClickListener {
            showStyleSheet()
        }

        binding.rowIdea.setOnClickListener {
            showIdeaSheet()
        }
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> {
                    checkCameraPermissionAndOpen()
                }

                PhotoSourceBottomSheet.RESULT_GALLERY -> {
                    openGallery()
                }

                PhotoSourceBottomSheet.RESULT_REMOVE -> {
                    removeTankPhoto()
                }
            }
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { savedTank ->
                savedTank.id == tankId
            } ?: return@observe

            bindTank(tank)
        }
    }

    private fun bindTank(
        tank: AquariumTankSnapshot
    ) {
        currentTank = tank

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

        binding.tvSettingSetupDate.text = getSetupDateText(
            tank.setupDateMillis
        )

        binding.tvSettingStyle.text = tank.tankStyle.ifBlank {
            getString(R.string.aquarium_no_value_placeholder)
        }

        binding.tvSettingIdea.text = tank.description.ifBlank {
            getString(R.string.aquarium_no_idea_added)
        }
    }

    fun openCareProfileAction(
        action: String
    ) {
        when (action) {
            ACTION_TANK_NAME -> {
                showTankNameSheet()
            }

            ACTION_TANK_TYPE -> {
                showTankTypeSheet()
            }

            ACTION_TANK_SIZE -> {
                showTankSizeSheet()
            }

            ACTION_SETUP_DATE -> {
                showSetupDateSheet()
            }

            ACTION_STYLE -> {
                showStyleSheet()
            }
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    private fun showSettingsBottomSheet(
        title: String,
        contentView: View,
        onDialogReady: ((BottomSheetDialog) -> Unit)? = null
    ) {
        SettingsContentBottomSheet.show(
            fragment = this,
            title = title,
            contentView = contentView,
            onDialogReady = onDialogReady
        )
    }

    private fun showPhotoSourceSheet() {
        PhotoSourceBottomSheet
            .newInstance(
                title = getString(R.string.aquarium_photo_title),
                showRemove = !currentTank?.photoUri.isNullOrBlank()
            )
            .show(
                childFragmentManager,
                PhotoSourceBottomSheet.TAG
            )
    }

    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    private fun checkCameraPermissionAndOpen() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_TANK_SETTINGS_PHOTO
        )
    }

    private fun openCamera() {
        val cameraUri = photoFlowCoordinator.createCameraUri()

        if (cameraUri == null) {
            showSnackBar(
                message = getString(R.string.aquarium_photo_temp_file_failed),
                type = BaseActivity.SnackType.ERROR
            )
            return
        }

        cameraLauncher.launch(cameraUri)
    }

    private fun startImageCrop(
        sourceUri: Uri
    ) {
        val destinationUri = photoFlowCoordinator.createCropOutputUri()

        if (destinationUri == null) {
            photoFlowCoordinator.cleanupPendingCameraImage()
            showSnackBar(
                message = getString(R.string.aquarium_photo_temp_crop_failed),
                type = BaseActivity.SnackType.ERROR
            )
            return
        }

        photoFlowCoordinator.markCropSource(sourceUri)

        cropLauncher.launch(
            photoFlowCoordinator.buildCropIntent(
                sourceUri = sourceUri,
                destinationUri = destinationUri,
                title = getString(R.string.aquarium_photo_crop_title)
            )
        )
    }

    private fun saveTankPhoto(
        photoUri: Uri
    ) {
        val contentUri = photoFlowCoordinator.toContentUri(photoUri)

        binding.imgTankPhoto.load(contentUri) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankPhoto(
                    tankId = tankId,
                    photoUri = contentUri.toString()
                )

                showSnackBar(
                    message = getString(R.string.aquarium_photo_updated),
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                photoFlowCoordinator.deleteInternalPhoto(contentUri.toString())

                currentTank?.let { tank ->
                    bindTank(tank)
                }

                showSnackBar(
                    message = getString(R.string.aquarium_photo_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                photoFlowCoordinator.cleanupPendingCropSource(
                    keepUriString = contentUri.toString()
                )
            }
        }
    }

    private fun removeTankPhoto() {
        val tank = currentTank ?: return

        if (tank.photoUri.isNullOrBlank()) {
            binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankPhoto(
                    tankId = tankId,
                    photoUri = null
                )

                binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)

                showSnackBar(
                    message = getString(R.string.aquarium_photo_removed),
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                showSnackBar(
                    message = getString(R.string.aquarium_photo_remove_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun runTankUpdate(
        errorMessage: String,
        onSuccess: (() -> Unit)? = null,
        update: suspend () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                update()
                onSuccess?.invoke()
            } catch (exception: Exception) {
                exception.printStackTrace()

                showSnackBar(
                    message = errorMessage,
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun showTankNameSheet() {
        val tank = currentTank ?: return

        val contentBinding = ContentSheetTankNameBinding.inflate(
            layoutInflater
        )

        contentBinding.inputTankName.setText(tank.name)

        showSettingsBottomSheet(
            title = getString(R.string.aquarium_tank_name_title),
            contentView = contentBinding.root
        ) { dialog ->
            contentBinding.btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            contentBinding.btnSave.setOnClickListener {
                val newName = contentBinding.inputTankName.text
                    .toString()
                    .trim()

                if (newName.length < 2) {
                    showSnackBar(
                        message = getString(R.string.aquarium_validation_tank_name_min),
                        type = BaseActivity.SnackType.WARNING
                    )
                    return@setOnClickListener
                }

                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_tank_name_save_failed),
                    onSuccess = {
                        dialog.dismiss()
                    }
                ) {
                    aquariumTankViewModel.updateTankName(
                        tankId = tankId,
                        name = newName
                    )
                }
            }
        }
    }

    private fun showTankTypeSheet() {
        val tank = currentTank ?: return

        TankTypeBottomSheet.show(
            fragment = this,
            currentType = tank.tankType,
            onSave = { selectedType, dismiss ->
                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_tank_type_save_failed),
                    onSuccess = dismiss
                ) {
                    aquariumTankViewModel.updateTankType(
                        tankId = tankId,
                        tankType = selectedType
                    )
                }
            }
        )
    }

    private fun showTankSizeSheet() {
        val tank = currentTank ?: return

        TankSizeBottomSheet.show(
            fragment = this,
            currentWidthCm = tank.widthCm,
            currentLengthCm = tank.lengthCm,
            currentHeightCm = tank.heightCm,
            currentUnit = tank.sizeUnit,
            title = getString(R.string.aquarium_tank_size_title),
            onInvalidInput = {
                showSnackBar(
                    message = getString(R.string.aquarium_validation_invalid_tank_size),
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onSave = { result, dismiss ->
                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_tank_size_save_failed),
                    onSuccess = dismiss
                ) {
                    aquariumTankViewModel.updateTankSize(
                        tankId = tankId,
                        widthCm = result.widthCm,
                        lengthCm = result.lengthCm,
                        heightCm = result.heightCm,
                        sizeUnit = result.sizeUnit
                    )
                }
            }
        )
    }

    private fun toggleVolumeUnit() {
        val tank = currentTank ?: return

        val newUnit = if (
            tank.volumeUnit.equals(
                "gal",
                ignoreCase = true
            )
        ) {
            "L"
        } else {
            "gal"
        }

        runTankUpdate(
            errorMessage = getString(R.string.aquarium_error_volume_unit_save_failed)
        ) {
            aquariumTankViewModel.updateTankVolumeUnit(
                tankId = tankId,
                volumeUnit = newUnit
            )
        }
    }

    private fun showSetupDateSheet() {
        val tank = currentTank ?: return

        SetupDateBottomSheet.show(
            fragment = this,
            currentMillis = tank.setupDateMillis,
            minYear = AquariumDatePolicy.minSetupYear(),
            maxYear = AquariumDatePolicy.maxSetupYear(),
            monthLocale = AquariumDatePolicy.setupDateLocale,
            onSave = { selectedMillis, dismiss ->
                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_setup_date_save_failed),
                    onSuccess = dismiss
                ) {
                    aquariumTankViewModel.updateTankSetupDate(
                        tankId = tankId,
                        setupDateMillis = selectedMillis
                    )
                }
            }
        )
    }

    private fun showStyleSheet() {
        val tank = currentTank ?: return

        TankStyleBottomSheet.show(
            fragment = this,
            currentStyle = tank.tankStyle,
            onSave = { selectedStyle, dismiss ->
                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_tank_style_save_failed),
                    onSuccess = dismiss
                ) {
                    aquariumTankViewModel.updateTankStyle(
                        tankId = tankId,
                        tankStyle = selectedStyle
                    )
                }
            }
        )
    }

    private fun showIdeaSheet() {
        val tank = currentTank ?: return

        val contentBinding = ContentSheetIdeaBinding.inflate(
            layoutInflater
        )

        contentBinding.inputIdea.setText(tank.description)

        showSettingsBottomSheet(
            title = getString(R.string.aquarium_tank_concept_title),
            contentView = contentBinding.root
        ) { dialog ->
            contentBinding.btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            contentBinding.btnSave.setOnClickListener {
                val newIdea = contentBinding.inputIdea.text
                    .toString()
                    .trim()

                runTankUpdate(
                    errorMessage = getString(R.string.aquarium_error_idea_save_failed),
                    onSuccess = {
                        dialog.dismiss()
                    }
                ) {
                    aquariumTankViewModel.updateTankDescription(
                        tankId = tankId,
                        description = newIdea
                    )
                }
            }
        }
    }

    private fun getSizeTitleText(
        tank: AquariumTankSnapshot
    ): String {
        return AquariumDimensionFormatter.sizeTitle(
            context = requireContext(),
            sizeUnit = tank.sizeUnit
        )
    }

    private fun getSizeText(
        tank: AquariumTankSnapshot
    ): String {
        return AquariumDimensionFormatter.sizeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            sizeUnit = tank.sizeUnit,
            separator = " x "
        )
    }

    private fun getVolumeText(
        tank: AquariumTankSnapshot
    ): String {
        return AquariumDimensionFormatter.volumeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            volumeUnit = tank.volumeUnit,
            rounded = true
        )
    }

    private fun getSetupDateText(
        setupDateMillis: Long?
    ): String {
        return AquariumDatePolicy.formatSetupDate(
            millis = setupDateMillis,
            emptyText = getString(R.string.aquarium_no_value_placeholder)
        )
    }

    override fun onDestroyView() {
        photoFlowCoordinator.cleanupAllPending()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ACTION_CAPTURE_TANK_SETTINGS_PHOTO =
            "capture_tank_settings_photo"

        const val ACTION_TANK_NAME = "tank_name"
        const val ACTION_TANK_TYPE = "tank_type"
        const val ACTION_TANK_SIZE = "tank_size"
        const val ACTION_SETUP_DATE = "setup_date"
        const val ACTION_STYLE = "style"

        fun newInstance(
            tankId: Long
        ): TankSettingsBasicFragment {
            return TankSettingsBasicFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
