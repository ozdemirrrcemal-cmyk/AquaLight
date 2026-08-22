package com.aqua.aqualight.ui.tabs.settings.profile

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentEditProfileBinding
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val profileOperations by lazy {
        requireContext().requireAppContainer().userProfileOperations
    }

    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = AppMediaScope.PROFILE,
            ownerToken = "profile",
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.PROFILE,
            mediaProcessor = container.imageMediaProcessor
        )
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE_PROFILE_PHOTO) startCameraCapture()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        lifecycleScope.launch {
            val cameraUri = mediaFlow.currentCameraUri()
            if (_binding == null) {
                mediaFlow.cancelCamera()
                return@launch
            }
            if (success && cameraUri != null) {
                openCrop(cameraUri)
            } else {
                mediaFlow.cancelCamera()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (_binding != null && uri != null) {
            lifecycleScope.launch { openCrop(uri) }
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
                            title = getString(R.string.edit_profile_error_title),
                            message = getString(R.string.edit_profile_save_photo_error)
                        )
                    }
                }

                result.resultCode == UCrop.RESULT_ERROR && data != null -> {
                    val error = UCrop.getError(data)
                    mediaFlow.cancelCrop()
                    showInfoDialog(
                        title = getString(R.string.edit_profile_error_title),
                        message = error?.localizedMessage
                            ?: getString(R.string.edit_profile_save_photo_error)
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
        _binding = FragmentEditProfileBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_edit_profile)
            )
        )
        setupPhotoSourceResultListener()
        setupClickListeners()
        observeProfileAndSelection()
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
                    mediaFlow.selectRemoval()
                }
            }
        }
    }

    private fun setupClickListeners() = with(binding) {
        val openChooser: (View) -> Unit = { showPhotoSourceSheet() }
        ivEditProfilePhoto.setOnClickListener(openChooser)
        ivCameraIcon.setOnClickListener(openChooser)
        btnSave.setOnClickListener { saveSelection() }
    }

    private fun observeProfileAndSelection() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    profileOperations.profile.collect { profile ->
                        mediaFlow.initializeSelection(profile.profilePhotoUrl)
                    }
                }
                launch {
                    mediaFlow.selection.collect { state ->
                        renderPhoto(state.selectedUri)
                    }
                }
            }
        }
    }

    private fun showPhotoSourceSheet() {
        PhotoSourceBottomSheet.newInstance(
            title = getString(R.string.edit_profile_choose_source_title),
            showRemove = !mediaFlow.selection.value.selectedUri.isNullOrBlank()
        ).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
    }

    private fun openGallery() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun checkCameraPermissionAndOpen() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.CAMERA_PHOTO,
            actionToken = ACTION_CAPTURE_PROFILE_PHOTO
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
                    title = getString(R.string.edit_profile_error_title),
                    message = getString(R.string.edit_profile_temp_file_error)
                )
                return@launch
            }
            takePictureLauncher.launch(uri)
        }
    }

    private suspend fun openCrop(sourceUri: Uri) {
        if (_binding == null) {
            mediaFlow.cancelCamera()
            return
        }
        setFragmentGlobalLoading(true)
        try {
            when (
                val preparation = mediaFlow.prepareCropIntent(
                    sourceUri = sourceUri,
                    title = getString(R.string.edit_profile_crop_title)
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
                        title = getString(R.string.edit_profile_error_title),
                        message = getString(R.string.edit_profile_save_photo_error)
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            setFragmentGlobalLoading(false)
        }
    }

    private fun saveSelection() {
        val selection = mediaFlow.selection.value
        if (!selection.hasPendingChange) {
            findNavController().popBackStack()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            setFragmentGlobalLoading(true)
            binding.btnSave.isEnabled = false
            try {
                profileOperations.updateProfilePhoto(selection.selectedUri.orEmpty())
                mediaFlow.commitSelection(deletePersistedMedia = false)
                findNavController().popBackStack()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mediaFlow.rollbackSelection()
                showInfoDialog(
                    title = getString(R.string.edit_profile_error_title),
                    message = error.localizedMessage
                        ?: getString(R.string.edit_profile_save_photo_error)
                )
            } finally {
                _binding?.btnSave?.isEnabled = true
                setFragmentGlobalLoading(false)
            }
        }
    }

    private fun renderPhoto(uriString: String?) {
        if (_binding == null) return
        if (uriString.isNullOrBlank()) {
            binding.ivEditProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder)
            return
        }
        binding.ivEditProfilePhoto.load(uriString) {
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
            crossfade(true)
        }
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

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTION_CAPTURE_PROFILE_PHOTO = "capture_profile_photo"
    }
}
