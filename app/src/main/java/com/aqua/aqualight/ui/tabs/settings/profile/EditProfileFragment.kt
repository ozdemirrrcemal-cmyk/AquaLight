package com.aqua.aqualight.ui.tabs.settings.profile

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentEditProfileBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Kamera ile çekilecek fotoğraf için geçici URI
    private var cameraImageUri: Uri? = null

    // 📸 Kamera izni isteyici
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

    // 📸 Kamera ile fotoğraf çekme
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            onPhotoSelected(cameraImageUri!!)
        }
    }

    // 🖼️ Android Photo Picker: yalnızca resim
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditProfileBinding.bind(view)

        observeCurrentPhoto()
        setupResultListener()
        setupClickListeners()
    }

    // 🔄 Mevcut profil fotoğrafını DataStore'dan oku ve göster
    private fun observeCurrentPhoto() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.userPrefsFlow.collectLatest { prefs ->
                val url = prefs.profilePhotoUrl
                if (url.isNotBlank()) {
                    binding.ivEditProfilePhoto.load(url) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivEditProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
        }
    }

    // ⬇️ BottomSheet’ten gelen sonucu dinle
    private fun setupResultListener() {
        parentFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_GALLERY -> openGallery()
                PhotoSourceBottomSheet.RESULT_CAMERA -> checkCameraPermissionAndOpen()
            }
        }
    }

    private fun setupClickListeners() = with(binding) {

        // Geri butonu
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Profil foto veya kamera ikonuna tıklayınca bottom sheet aç
        val openChooser: (View) -> Unit = {
            PhotoSourceBottomSheet.newInstance()
                .show(parentFragmentManager, PhotoSourceBottomSheet.TAG)
        }
        ivEditProfilePhoto.setOnClickListener(openChooser)
        ivCameraIcon.setOnClickListener(openChooser)

        // Şimdilik kaydet ekstra iş yapmıyor
        btnSave.setOnClickListener {
            showInfoDialog(
                title = getString(R.string.edit_profile_save_info_title),
                message = getString(R.string.edit_profile_save_info_message)
            )
        }
    }

    // 🖼️ Galeriden fotoğraf seç (Android Photo Picker)
    private fun openGallery() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    // 📸 Kamera iznini kontrol et
    private fun checkCameraPermissionAndOpen() {
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    // 📸 Kamera ile fotoğraf çekmeyi başlat
    private fun startCameraCapture() {
        val uri = createImageUri() ?: run {
            showInfoDialog(
                title = getString(R.string.edit_profile_error_title),
                message = getString(R.string.edit_profile_temp_file_error)
            )
            return
        }
        cameraImageUri = uri
        takePictureLauncher.launch(uri)
    }

    // 📂 FileProvider ile cache altında geçici dosya oluştur
    private fun createImageUri(): Uri? {
        return try {
            val cacheDir = File(requireContext().cacheDir, "images").apply {
                if (!exists()) mkdirs()
            }
            val file = File.createTempFile("profile_", ".jpg", cacheDir)
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

    // ✅ Fotoğraf seçildiğinde: ekranda göster + DataStore'a kaydet
    private fun onPhotoSelected(uri: Uri) {
        // Önce UI'da göster
        binding.ivEditProfilePhoto.load(uri) {
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
            crossfade(true)
        }

        // Sonra DataStore'a yaz
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userPrefs.updateProfilePhoto(uri.toString())
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

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}