package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.photo.TankPhotoStorage
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantTagFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalantis.ucrop.UCrop

class TankPhotoFragment : Fragment(R.layout.fragment_tank_photo), TankStepFragment {

    private var _binding: FragmentTankPhotoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    private var cameraImageUri: Uri? = null
    private var cropSourceUri: Uri? = null
    private var selectedPhotoUri: String? = null
    private var isOpeningNextStep: Boolean = false

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            showInfoDialog(
                title = "Permission required",
                message = "Camera permission is required to take an aquarium photo."
            )
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraImageUri

        if (success && uri != null) {
            openCropScreen(uri)
        } else {
            cleanupCameraImage()
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
                    cleanupCropSource()
                    return@registerForActivityResult
                }

                handleCroppedImage(croppedUri)
                cleanupCropSource()
            }

            result.resultCode == UCrop.RESULT_ERROR && data != null -> {
                val error = UCrop.getError(data)
                error?.printStackTrace()

                showInfoDialog(
                    title = "Crop error",
                    message = error?.localizedMessage
                        ?: "The aquarium photo could not be cropped."
                )

                cleanupCropSource()
            }

            result.resultCode != Activity.RESULT_OK -> {
                cleanupCropSource()
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

        _binding =
            FragmentTankPhotoBinding.bind(view)

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
                    title = "Aquarium Photo",
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
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startCameraCapture()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraCapture() {
        val uri = TankPhotoStorage.createCameraCaptureUri(
            context = requireContext(),
            ownerToken = "draft"
        )

        if (uri == null) {
            showInfoDialog(
                title = "Photo error",
                message = "Temporary image file could not be created."
            )
            return
        }

        cameraImageUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun openCropScreen(sourceUri: Uri) {
        val context = requireContext()
        val destUri = TankPhotoStorage.createCropOutputUri(
            context = context,
            ownerToken = "draft"
        )

        if (destUri == null) {
            cleanupCameraImage()
            showInfoDialog(
                title = "Photo error",
                message = "Temporary crop file could not be created."
            )
            return
        }

        cropSourceUri = sourceUri

        val options = UCrop.Options().apply {
            setCircleDimmedLayer(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setHideBottomControls(true)

            setToolbarTitle("Crop aquarium photo")

            val toolbarColor = ContextCompat.getColor(
                context,
                R.color.crop_toolbar_bg
            )

            setToolbarColor(toolbarColor)
            setToolbarWidgetColor(Color.WHITE)
            setToolbarCancelDrawable(R.drawable.ic_back)
        }

        val cropIntent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1600, 900)
            .withOptions(options)
            .getIntent(context)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

        uCropLauncher.launch(cropIntent)
    }

    private fun handleCroppedImage(croppedFileUri: Uri) {
        val context = requireContext()
        val contentUri = TankPhotoStorage.toContentUriIfInternalFile(
            context = context,
            uri = croppedFileUri
        ) ?: croppedFileUri

        val previousPhotoUri = selectedPhotoUri

        binding.imgAquariumPhoto.load(contentUri) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }

        selectedPhotoUri = contentUri.toString()
        viewModel.updateTankPhoto(selectedPhotoUri)

        if (previousPhotoUri != selectedPhotoUri) {
            TankPhotoStorage.deleteInternalPhoto(
                context = context,
                uriString = previousPhotoUri
            )
        }
    }

    private fun removeSelectedPhoto() {
        val previousPhotoUri = selectedPhotoUri

        selectedPhotoUri = null
        viewModel.updateTankPhoto(null)
        binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)

        TankPhotoStorage.deleteInternalPhoto(
            context = requireContext(),
            uriString = previousPhotoUri
        )
    }

    private fun renderSelectedPlants() {
        val plants = viewModel.tankDraft.plants

        binding.selectedPlantsContainer.removeAllViews()

        if (plants.isEmpty()) {
            return
        }

        plants.forEachIndexed { index, plant ->

            val card = MaterialCardView(requireContext()).apply {
                radius = 16.dp().toFloat()
                strokeWidth = 1.dp()
                strokeColor = Color.parseColor("#223A57")
                setCardBackgroundColor(Color.parseColor("#10233A"))
                useCompatPadding = false

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 10.dp()
                layoutParams = params
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    14.dp(),
                    11.dp(),
                    12.dp(),
                    11.dp()
                )
            }

            val number = TextView(requireContext()).apply {
                text = "${index + 1}"
                gravity = Gravity.CENTER
                textSize = 13f
                includeFontPadding = false
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_plant_number_circle)

                layoutParams = LinearLayout.LayoutParams(
                    34.dp(),
                    34.dp()
                )
            }

            val textBox = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL

                val params = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                params.marginStart = 14.dp()
                layoutParams = params
            }

            val categoryText = TextView(requireContext()).apply {
                text = plant.category
                textSize = 12f
                includeFontPadding = false
                setTextColor(Color.parseColor("#8FA4BE"))
            }

            val nameText = TextView(requireContext()).apply {
                text = plant.plantName
                textSize = 14f
                includeFontPadding = false
                maxLines = 2
                setTextColor(Color.WHITE)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 5.dp()
                layoutParams = params
            }

            val delete = TextView(requireContext()).apply {
                text = "×"
                textSize = 23f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.parseColor("#8FA4BE"))

                setOnClickListener {
                    val updatedPlants = viewModel.tankDraft.plants
                        .toMutableList()
                        .apply {
                            removeAt(index)
                        }

                    viewModel.updateTankPlants(updatedPlants)
                    renderSelectedPlants()
                }

                layoutParams = LinearLayout.LayoutParams(
                    34.dp(),
                    34.dp()
                )
            }

            textBox.addView(categoryText)
            textBox.addView(nameText)

            row.addView(number)
            row.addView(textBox)
            row.addView(delete)

            card.addView(row)
            binding.selectedPlantsContainer.addView(card)
        }
    }

    private fun cleanupCameraImage() {
        TankPhotoStorage.deleteInternalPhoto(
            context = requireContext(),
            uriString = cameraImageUri?.toString()
        )

        cameraImageUri = null
    }

    private fun cleanupCropSource() {
        val sourceUri = cropSourceUri

        if (sourceUri != null && sourceUri != Uri.parse(selectedPhotoUri.orEmpty())) {
            TankPhotoStorage.deleteInternalPhoto(
                context = requireContext(),
                uriString = sourceUri.toString()
            )
        }

        if (sourceUri == cameraImageUri) {
            cameraImageUri = null
        }

        cropSourceUri = null
    }

    private fun showInfoDialog(
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun validateAndSave(): Boolean {
        viewModel.updateTankPhoto(selectedPhotoUri)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}