package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalantis.ucrop.UCrop
import android.graphics.Color
import java.io.File

class TankPhotoFragment : Fragment(R.layout.fragment_tank_photo), TankStepFragment {

    private var _binding: FragmentTankPhotoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var cameraImageUri: Uri? = null
    private var selectedPhotoUri: String? = null

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
                val croppedUri = UCrop.getOutput(data) ?: return@registerForActivityResult
                handleCroppedImage(croppedUri)
            }

            result.resultCode == UCrop.RESULT_ERROR && data != null -> {
                val error = UCrop.getError(data)
                error?.printStackTrace()

                showInfoDialog(
                    title = "Crop error",
                    message = error?.localizedMessage
                        ?: "The aquarium photo could not be cropped."
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankPhotoBinding.bind(view)

        setupPhotoSourceResultListener()
        setupClickListeners()
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
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            PhotoSourceBottomSheet
                .newInstance(
                    title = "Aquarium photo"
                )
                .show(
                    childFragmentManager,
                    PhotoSourceBottomSheet.TAG
                )
        }

        binding.btnAddPlant.setOnClickListener {
            // Sonra Add Plant ekranına bağlanacak.
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
        val uri = createCameraImageUri()

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

        val destFile = File(
            getTankPhotosDir(),
            "tank_cropped_${System.currentTimeMillis()}.jpg"
        )

        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            // Akvaryum fotoğrafı yatay olacak.
            setCircleDimmedLayer(false)
            setShowCropGrid(true)
            setShowCropFrame(true)

            // Alt scale/rotate kontrolleri kapalı.
            setHideBottomControls(true)

            // Üst bar
            setToolbarTitle("Crop aquarium photo")

            val toolbarColor = ContextCompat.getColor(
                context,
                R.color.crop_toolbar_bg
            )

            setToolbarColor(toolbarColor)
            setStatusBarColor(toolbarColor)
            setToolbarWidgetColor(Color.WHITE)

            setToolbarCancelDrawable(R.drawable.ic_back)
        }

        UCrop.of(sourceUri, destUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1600, 900)
            .withOptions(options)
            .start(context, uCropLauncher)
    }

    private fun handleCroppedImage(croppedFileUri: Uri) {
        val context = requireContext()

        val file = File(croppedFileUri.path ?: return)

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        binding.imgAquariumPhoto.load(contentUri) {
            placeholder(R.drawable.nature_aquarium)
            error(R.drawable.nature_aquarium)
            crossfade(true)
        }

        selectedPhotoUri = contentUri.toString()
    }

    private fun getTankPhotosDir(): File {
        return File(requireContext().filesDir, "tank_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun createCameraImageUri(): Uri? {
        return try {
            val file = File.createTempFile(
                "tank_camera_",
                ".jpg",
                getTankPhotosDir()
            )

            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    override fun validateAndSave(): Boolean {
        viewModel.updateTankPhoto(selectedPhotoUri)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}