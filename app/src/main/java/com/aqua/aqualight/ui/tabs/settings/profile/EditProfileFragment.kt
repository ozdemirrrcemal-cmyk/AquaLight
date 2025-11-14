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
import java.io.FileOutputStream

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Kamera ile çekilecek fotoğraf için geçici URI
    private var cameraImageUri: Uri? = null

    // Bu ekranda seçilmiş ama henüz kaydedilmemiş foto
    private var selectedPhotoUri: Uri? = null

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
                // Ekranda yeni bir foto seçtiysek, eski veriye göre görüntüyü değiştirme
                if (selectedPhotoUri != null) return@collectLatest

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

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        val openChooser: (View) -> Unit = {
            PhotoSourceBottomSheet.newInstance()
                .show(parentFragmentManager, PhotoSourceBottomSheet.TAG)
        }
        ivEditProfilePhoto.setOnClickListener(openChooser)
        ivCameraIcon.setOnClickListener(openChooser)

        // 💾 Kaydet: sadece seçilmiş fotoğrafı DataStore'a yaz ve geri dön
        btnSave.setOnClickListener {
            val uriToSave = selectedPhotoUri

            if (uriToSave == null) {
                // İstersen burada "Değişiklik yok" uyarısı gösterebilirsin
                findNavController().popBackStack()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    userPrefs.updateProfilePhoto(uriToSave.toString())
                    findNavController().popBackStack()
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

    // 📂 FileProvider ile cache altında geçici dosya oluştur (kamera için)
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

    // ✅ Fotoğraf seçildiğinde: önce kendi klasörümüze kopyala, sonra ekranda göster
    private fun onPhotoSelected(sourceUri: Uri) {
        val finalUri = copyImageToAppStorage(sourceUri) ?: run {
            showInfoDialog(
                title = getString(R.string.edit_profile_error_title),
                message = getString(R.string.edit_profile_save_photo_error)
            )
            return
        }

        // Ekranda göster
        binding.ivEditProfilePhoto.load(finalUri) {
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
            crossfade(true)
        }

        // Henüz DataStore'a yazmıyoruz, sadece hafızada tutuyoruz
        selectedPhotoUri = finalUri
    }

    /**
     * Seçilen resmi (kamera veya galeri) app'in kendi cache klasörüne kopyalar
     * ve FileProvider URI'si döner.
     */
    private fun copyImageToAppStorage(sourceUri: Uri): Uri? {
        return try {
            val context = requireContext()
            val cacheDir = File(context.cacheDir, "images").apply {
                if (!exists()) mkdirs()
            }
            val file = File.createTempFile("profile_", ".jpg", cacheDir)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
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