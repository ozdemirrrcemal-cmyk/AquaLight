package com.aqua.aqualight.ui.tabs.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import coil3.load
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
                val username = prefs.username.ifBlank { getString(R.string.settings_default_username) }
                val email = prefs.email.ifBlank { getString(R.string.settings_default_email) }

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

                // Aktif cihaz sayısını ileride gerçek dataya bağlayabilirsin
                // şimdilik string resource üzerinden gidiyor.
            }
        }
    }

    // 🔹 Satır click'lerini ayarla
    private fun setupClickListeners() = with(binding) {

        // Profil foto tıklaması – ileride "foto değiştir" ekranına gidebilir
        ivProfilePhoto.setOnClickListener {
            // TODO: Profil fotoğrafı değiştirme ekranı / bottom sheet
        }

        rowUserInfo.setOnClickListener {
            // TODO: Kullanıcı bilgilerinin düzenlendiği ekrana navigate et
            // findNavController().navigate(R.id.action_settings_to_userInfo)
        }

        rowDeviceStatus.setOnClickListener {
            // Zaten bottom nav'de Devices tab'in var, istersen direk oraya gidebilirsin:
            // findNavController().navigate(R.id.devicesFragment)
        }

        rowNetwork.setOnClickListener {
            // TODO: Network / WiFi ayarları ekranın olunca buraya bağla
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

    // 🔹 Gerçek logout işlemi: DataStore temizle + auth ekranına dön
    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1) DataStore'daki user bilgilerini temizle
            userPrefs.clearUserData()

            // 2) Root nav graph'te authContainerFragment'e dön
            //    nav_app backstack'ini temizle ki back ile app'e dönemesin
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