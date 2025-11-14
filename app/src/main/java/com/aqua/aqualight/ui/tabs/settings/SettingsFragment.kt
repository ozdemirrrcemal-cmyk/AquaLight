package com.aqua.aqualight.ui.tabs.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import coil3.load
import coil3.request.placeholder
import coil3.request.error
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        observeUserInfo()
        setupClickListeners()
    }

    // 🔹 DataStore'dan kullanıcı bilgilerini oku ve UI'ya bas
    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.userPrefsFlow.collectLatest { prefs ->
                val username =
                    prefs.username.ifBlank { getString(R.string.settings_default_username) }
                val email =
                    prefs.email.ifBlank { getString(R.string.settings_default_email) }

                binding.tvUsername.text = username
                binding.tvEmail.text = email

                // Profil fotoğrafı URL'i varsa Coil ile yükle
                if (prefs.profilePhotoUrl.isNotBlank()) {
                    binding.ivProfilePhoto.load(prefs.profilePhotoUrl) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
        }
    }

    // 🔹 Satır click'lerini ayarla
    private fun setupClickListeners() = with(binding) {

        // Profil foto tıklaması – EditProfileFragment'e git
        ivProfilePhoto.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }

        rowUserInfo.setOnClickListener {
            // TODO: Kullanıcı bilgilerinin düzenlendiği ekrana navigate et
        }

        rowDeviceStatus.setOnClickListener {
            // TODO: Cihaz listesi ekranı
        }

        rowNetwork.setOnClickListener {
            // TODO: Network / WiFi ayarları ekranı
        }

        rowSettings.setOnClickListener {
            // TODO: Uygulama ayarları (tema, dil vs.) ekranına gidiş
        }

        rowUsage.setOnClickListener {
            // TODO: Kullanım istatistikleri ekranı
        }

        rowPrivacy.setOnClickListener {
            // TODO: Gizlilik politikası sayfasını aç (WebView veya browser Intent)
        }

        rowFeedback.setOnClickListener {
            // TODO: Feedback ekranı veya mail intent
        }

        rowLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    // 🔹 Çıkış yap diyaloğu
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_logout)
            .setMessage(R.string.settings_logout_confirm_message)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // 🔹 Gerçek logout işlemi: sadece oturumu kapat + auth ekranına dön
    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1) Oturumu kapat (idToken temizlenir, isLoggedIn = false olur)
            userPrefs.logout()

            // 2) Root nav graph'te authContainerFragment'e dön
            val navController = findNavController()
            navController.navigate(
                R.id.authContainerFragment,
                null,
                navOptions {
                    popUpTo(R.id.nav_app) {
                        inclusive = true
                    }
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}