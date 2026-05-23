package com.aqua.aqualight.ui.tabs.settings.profile

import android.Manifest
import android.app.Activity
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
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentEditProfileBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalantis.ucrop.UCrop
import android.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    // Kamera ile çekilecek fotoğraf için geçici URI
    private var cameraImageUri: Uri? = null

    // Bu ekranda seçilmiş ama henüz kaydedilmemiş foto
    private var selectedPhotoUri: Uri? = null

    // 📸 Kamera izni isteyici
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        granted ->
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
    ) {
        success ->
        if (success && cameraImageUri != null) {
            // Kamera’dan gelen resmi kırpmaya gönder
            onPhotoSelected(cameraImageUri!!)
        }
    }

    // 🖼️ Android Photo Picker: yalnızca resim
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) {
        uri: Uri? ->
        if (uri != null) {
            // Galeri’den gelen resmi kırpmaya gönder
            onPhotoSelected(uri)
        }
    }

    // ✂️ uCrop sonucu için launcher
    private val uCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        result ->
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
            userPrefs.userPrefsFlow.collectLatest {
                prefs ->
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
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) {
            _, bundle ->
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
            PhotoSourceBottomSheet
            .newInstance(
                title = "Profile Photo"
            )
            .show(
                childFragmentManager,
                PhotoSourceBottomSheet.TAG
            )
        }
        ivEditProfilePhoto.setOnClickListener(openChooser)
        ivCameraIcon.setOnClickListener(openChooser)

        // 💾 Kaydet: sadece seçilmiş fotoğrafı DataStore'a yaz ve geri dön
        btnSave.setOnClickListener {
            val uriToSave = selectedPhotoUri

            if (uriToSave == null) {
                // Değişiklik yoksa direkt geri dön
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

    // 📂 Profil foto klasörü (filesDir/profile_photos)
    private fun getProfilePhotosDir(): File {
        val context = requireContext()
        return File(context.filesDir, "profile_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    // 📂 FileProvider ile filesDir altında profil foto dosyası oluştur (kamera için)
    private fun createImageUri(): Uri? {
        return try {
            val dir = getProfilePhotosDir()
            val file = File.createTempFile("profile_", ".jpg", dir)
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

    // 🔁 Foto seçildiğinde: uCrop ekranını başlat
    private fun onPhotoSelected(sourceUri: Uri) {
        val context = requireContext()

        // uCrop çıktı dosyası: app'in kendi klasöründe
        val destFile = File(
            getProfilePhotosDir(),
            "profile_cropped_${System.currentTimeMillis()}.jpg"
        )
        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            // 1:1 kare + dairesel avatar
            setCircleDimmedLayer(true)
            withAspectRatio(1f, 1f)

            // Grid açık, dış kare çerçeve kapalı
            setShowCropGrid(true)
            setShowCropFrame(false)

            // 🔻 Alt bardaki Ölçek/Döndür kontrollerini gizle
            setHideBottomControls(true)

            // 🔹 Üst bar başlığı
            setToolbarTitle(getString(R.string.edit_profile_crop_title))

            // 🔹 Üst bar & status bar rengi (#0A192F -> colors.xml: crop_toolbar_bg)
            val toolbarColor = ContextCompat.getColor(context, R.color.crop_toolbar_bg)
            setToolbarColor(toolbarColor)

            // 🔹 Üst bardaki text + ikon rengi (beyaz)
            setToolbarWidgetColor(Color.WHITE)

            // 🔹 Soldaki cancel ikonunu geri butonu yap
            setToolbarCancelDrawable(R.drawable.ic_back)
            // ✅ Sağdaki check ikonunu DEĞİŞTİRMİYORUZ (orijinal kalsın)
        }

        UCrop.of(sourceUri, destUri)
        .withAspectRatio(1f, 1f)
        .withOptions(options)
        .start(context, uCropLauncher)
    }

    // ✅ uCrop'tan gelen sonucu işler: ImageView'de göster + URI'yi sakla
    private fun handleCroppedImage(croppedFileUri: Uri) {
        val context = requireContext()

        // UCrop bize file:// Uri döner; FileProvider ile content:// yapıp saklayalım
        val file = File(croppedFileUri.path ?: return)

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        binding.ivEditProfilePhoto.load(contentUri) {
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
            crossfade(true)
        }

        // Save butonuna basınca DataStore'a yazılacak olan URI
        selectedPhotoUri = contentUri
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