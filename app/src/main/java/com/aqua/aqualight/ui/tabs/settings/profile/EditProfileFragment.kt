package com.aqua.aqualight.ui.tabs.settings.profile

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentEditProfileBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private var cameraImageUri: Uri? = null
    private var selectedPhotoUri: Uri? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            showInfoDialog(
                title = getString(R.string.edit_profile_permission_denied_title),
                message = getString(R.string.edit_profile_camera_permission_denied_message)
            )
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            onPhotoSelected(cameraImageUri!!)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
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
                    title = getString(R.string.edit_profile_error_title),
                    message = error?.localizedMessage
                        ?: getString(R.string.edit_profile_save_photo_error)
                )
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
            FragmentEditProfileBinding.bind(view)

        setupHeader()
        observeCurrentPhoto()
        setupResultListener()
        setupClickListeners()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun observeCurrentPhoto() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.userPrefsFlow.collectLatest { prefs ->
                if (selectedPhotoUri != null) return@collectLatest

                val url =
                    prefs.profilePhotoUrl

                if (url.isNotBlank()) {
                    binding.ivEditProfilePhoto.load(url) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivEditProfilePhoto.setImageResource(
                        R.drawable.ic_profile_placeholder
                    )
                }
            }
        }
    }

    private fun setupResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (
                bundle.getString(
                    PhotoSourceBottomSheet.RESULT_KEY
                )
            ) {
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_CAMERA -> checkCameraPermissionAndOpen()
            }
        }
    }

    private fun setupClickListeners() =
        with(binding) {

            val openChooser: (View) -> Unit = {
                PhotoSourceBottomSheet
                    .newInstance(
                        title = "Profile Photo"
                    )
                    .show(
                        childFragmentManager,
                        PhotoSourceBottomSheet.TAG
                    )
            }

            ivEditProfilePhoto.setOnClickListener(
                openChooser
            )

            ivCameraIcon.setOnClickListener(
                openChooser
            )

            btnSave.setOnClickListener {
                val uriToSave =
                    selectedPhotoUri

                if (uriToSave == null) {
                    findNavController()
                        .popBackStack()

                    return@setOnClickListener
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        userPrefs.updateProfilePhoto(
                            uriToSave.toString()
                        )

                        findNavController()
                            .popBackStack()

                    } catch (e: Exception) {
                        e.printStackTrace()

                        showInfoDialog(
                            title = getString(R.string.edit_profile_error_title),
                            message = e.localizedMessage
                                ?: getString(R.string.edit_profile_save_photo_error)
                        )
                    }
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
        requestCameraPermission.launch(
            Manifest.permission.CAMERA
        )
    }

    private fun startCameraCapture() {
        val uri =
            createImageUri()
                ?: run {
                    showInfoDialog(
                        title = getString(R.string.edit_profile_error_title),
                        message = getString(R.string.edit_profile_temp_file_error)
                    )

                    return
                }

        cameraImageUri =
            uri

        takePictureLauncher.launch(
            uri
        )
    }

    private fun getProfilePhotosDir(): File {
        val context =
            requireContext()

        return File(
            context.filesDir,
            "profile_photos"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun createImageUri(): Uri? {
        return try {
            val dir =
                getProfilePhotosDir()

            val file =
                File.createTempFile(
                    "profile_",
                    ".jpg",
                    dir
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

    private fun onPhotoSelected(
        sourceUri: Uri
    ) {
        val context =
            requireContext()

        val destFile =
            File(
                getProfilePhotosDir(),
                "profile_cropped_${System.currentTimeMillis()}.jpg"
            )

        val destUri =
            Uri.fromFile(
                destFile
            )

        val options =
            UCrop.Options().apply {
                setCircleDimmedLayer(true)
                withAspectRatio(1f, 1f)

                setShowCropGrid(true)
                setShowCropFrame(false)
                setHideBottomControls(true)

                setToolbarTitle(
                    getString(R.string.edit_profile_crop_title)
                )

                val toolbarColor =
                    ContextCompat.getColor(
                        context,
                        R.color.crop_toolbar_bg
                    )

                setToolbarColor(
                    toolbarColor
                )

                setToolbarWidgetColor(
                    Color.WHITE
                )

                setToolbarCancelDrawable(
                    R.drawable.ic_back
                )
            }

        UCrop.of(
            sourceUri,
            destUri
        )
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .start(
                context,
                uCropLauncher
            )
    }

    private fun handleCroppedImage(
        croppedFileUri: Uri
    ) {
        val context =
            requireContext()

        val file =
            File(
                croppedFileUri.path ?: return
            )

        val contentUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        binding.ivEditProfilePhoto.load(
            contentUri
        ) {
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
            crossfade(true)
        }

        selectedPhotoUri =
            contentUri
    }

    private fun showInfoDialog(
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                android.R.string.ok,
                null
            )
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}