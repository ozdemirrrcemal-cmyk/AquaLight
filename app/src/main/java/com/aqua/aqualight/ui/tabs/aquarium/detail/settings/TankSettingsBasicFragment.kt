package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.aquarium.photo.TankPhotoStorage
import com.aqua.aqualight.databinding.ContentSheetIdeaBinding
import com.aqua.aqualight.databinding.ContentSheetTankNameBinding
import com.aqua.aqualight.databinding.FragmentTankSettingsBasicBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SettingsContentBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SetupDateBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSizeBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankStyleBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankTypeBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yalantis.ucrop.UCrop
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class TankSettingsBasicFragment : Fragment(R.layout.fragment_tank_settings_basic) {

    private var _binding: FragmentTankSettingsBasicBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var currentTank: SavedAquariumTank? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCropSourceUri: Uri? = null

    private val sizeFormatter = DecimalFormat(
        "#0.##",
        DecimalFormatSymbols(Locale.US)
    )

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            startImageCrop(uri)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            showSnackBar(
                message = "Camera permission is required to take an aquarium photo.",
                type = BaseActivity.SnackType.WARNING
            )
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                startImageCrop(uri)
            }
        } else {
            cleanupPendingCameraImage()
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
                cleanupPendingCropSource()
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { intent ->
                UCrop.getError(intent)
            }

            showSnackBar(
                message = error?.message ?: "Photo could not be cropped.",
                type = BaseActivity.SnackType.ERROR
            )

            cleanupPendingCropSource()
        } else {
            cleanupPendingCropSource()
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
        tank: SavedAquariumTank
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
            "-"
        }

        binding.tvSettingSizeTitle.text = getSizeTitleText(tank)
        binding.tvSettingSize.text = getSizeText(tank)

        binding.tvSettingVolume.text = getVolumeText(tank)

        binding.tvSettingSetupDate.text = getSetupDateText(
            tank.setupDateMillis
        )

        binding.tvSettingStyle.text = tank.tankStyle.ifBlank {
            "-"
        }

        binding.tvSettingIdea.text = tank.description.ifBlank {
            "No idea added"
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
                title = "Aquarium Photo",
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
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val cameraUri = TankPhotoStorage.createCameraCaptureUri(
            context = requireContext(),
            ownerToken = tankId.toString()
        )

        if (cameraUri == null) {
            showSnackBar(
                message = "Temporary image file could not be created.",
                type = BaseActivity.SnackType.ERROR
            )
            return
        }

        pendingCameraUri = cameraUri
        cameraLauncher.launch(cameraUri)
    }

    private fun startImageCrop(
        sourceUri: Uri
    ) {
        val destinationUri = TankPhotoStorage.createCropOutputUri(
            context = requireContext(),
            ownerToken = tankId.toString()
        )

        if (destinationUri == null) {
            cleanupPendingCameraImage()
            showSnackBar(
                message = "Temporary crop file could not be created.",
                type = BaseActivity.SnackType.ERROR
            )
            return
        }

        pendingCropSourceUri = sourceUri

        val options = UCrop.Options().apply {
            setToolbarTitle("Crop aquarium photo")
            setToolbarColor(Color.parseColor("#081B31"))
            setToolbarWidgetColor(Color.WHITE)
            setRootViewBackgroundColor(Color.parseColor("#081B31"))
            setActiveControlsWidgetColor(Color.parseColor("#2196F3"))
            setCompressionQuality(90)
            setFreeStyleCropEnabled(false)
            setHideBottomControls(true)
        }

        val cropIntent = UCrop.of(
            sourceUri,
            destinationUri
        )
            .withAspectRatio(
                16f,
                9f
            )
            .withMaxResultSize(
                1600,
                900
            )
            .withOptions(options)
            .getIntent(requireContext())
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

        cropLauncher.launch(cropIntent)
    }

    private fun saveTankPhoto(
        photoUri: Uri
    ) {
        val context = requireContext()
        val contentUri = TankPhotoStorage.toContentUriIfInternalFile(
            context = context,
            uri = photoUri
        ) ?: photoUri

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
                    message = "Photo updated.",
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                TankPhotoStorage.deleteInternalPhoto(
                    context = context,
                    uriString = contentUri.toString()
                )

                currentTank?.let { tank ->
                    bindTank(tank)
                }

                showSnackBar(
                    message = "Photo could not be saved.",
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                cleanupPendingCropSource()
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
                    message = "Photo removed.",
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                showSnackBar(
                    message = "Photo could not be removed.",
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun cleanupPendingCameraImage() {
        TankPhotoStorage.deleteInternalPhoto(
            context = requireContext(),
            uriString = pendingCameraUri?.toString()
        )

        pendingCameraUri = null
    }

    private fun cleanupPendingCropSource() {
        val sourceUri = pendingCropSourceUri

        if (sourceUri != null) {
            TankPhotoStorage.deleteInternalPhoto(
                context = requireContext(),
                uriString = sourceUri.toString()
            )
        }

        if (sourceUri == pendingCameraUri) {
            pendingCameraUri = null
        }

        pendingCropSourceUri = null
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
            title = "Tank Name",
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
                        message = "Tank name must be at least 2 characters.",
                        type = BaseActivity.SnackType.WARNING
                    )
                    return@setOnClickListener
                }

                runTankUpdate(
                    errorMessage = "Tank name could not be saved.",
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
                    errorMessage = "Tank type could not be saved.",
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
            title = "Size",
            onInvalidInput = {
                showSnackBar(
                    message = "Please enter valid tank size.",
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onSave = { result, dismiss ->
                runTankUpdate(
                    errorMessage = "Tank size could not be saved.",
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
            errorMessage = "Volume unit could not be saved."
        ) {
            aquariumTankViewModel.updateTankVolumeUnit(
                tankId = tankId,
                volumeUnit = newUnit
            )
        }
    }

    private fun showSetupDateSheet() {
        val tank = currentTank ?: return

        val currentYear = Calendar.getInstance().get(
            Calendar.YEAR
        )

        SetupDateBottomSheet.show(
            fragment = this,
            currentMillis = tank.setupDateMillis,
            minYear = currentYear - 10,
            maxYear = currentYear + 10,
            monthLocale = Locale.getDefault(),
            onSave = { selectedMillis, dismiss ->
                runTankUpdate(
                    errorMessage = "Setup date could not be saved.",
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
                    errorMessage = "Tank style could not be saved.",
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
            title = "Idea",
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
                    errorMessage = "Idea could not be saved.",
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
        tank: SavedAquariumTank
    ): String {
        return if (tank.sizeUnit.equals("in", ignoreCase = true)) {
            "Size (in)"
        } else {
            "Size (cm)"
        }
    }

    private fun getSizeText(
        tank: SavedAquariumTank
    ): String {
        return if (tank.sizeUnit.equals("in", ignoreCase = true)) {
            val widthIn = tank.widthCm / 2.54
            val lengthIn = tank.lengthCm / 2.54
            val heightIn = tank.heightCm / 2.54

            "${sizeFormatter.format(widthIn)} W x ${sizeFormatter.format(lengthIn)} L x ${sizeFormatter.format(heightIn)} H"
        } else {
            "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
        }
    }

    private fun getVolumeText(
        tank: SavedAquariumTank
    ): String {
        val liter = (tank.widthCm * tank.lengthCm * tank.heightCm) / 1000.0

        return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
            val gallon = liter * 0.264172
            "${gallon.roundToInt()} gal"
        } else {
            "${liter.roundToInt()} L"
        }
    }

    private fun getSetupDateText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        return formatter.format(Date(setupDateMillis))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

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