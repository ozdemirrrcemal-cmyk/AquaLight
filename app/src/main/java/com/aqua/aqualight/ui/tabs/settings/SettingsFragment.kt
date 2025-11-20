package com.aqua.aqualight.ui.tabs.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    companion object {
        // 🔗 Sosyal linkler – kendi linklerini buraya yaz
        private const val URL_WEBSITE  = "https://aqualight.example.com"
        private const val URL_FACEBOOK = "https://www.facebook.com/aqualight"
        private const val URL_INSTAGRAM = "https://www.instagram.com/aqualight"
        private const val URL_YOUTUBE  = "https://www.youtube.com/@aqualight"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        observeUserInfo()
        setupClickListeners()
        setupSocialLinks()
        setupFooterVersion()
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

    // 🔹 Menü satır click'leri
    private fun setupClickListeners() = with(binding) {

        // Profil foto tıklaması – EditProfileFragment'e git
        ivProfilePhoto.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }

        // 1️⃣ User Info
        rowUserInfo.setOnClickListener {
            findNavController().navigate(R.id.userInfoFragment)
        }

        // 2️⃣ Device Status
        rowDeviceStatus.setOnClickListener {
            findNavController().navigate(R.id.deviceStatusFragment)
        }

        // 3️⃣ Network
        rowNetwork.setOnClickListener {
            findNavController().navigate(R.id.networkFragment)
        }

        // 4️⃣ App Settings
        rowSettings.setOnClickListener {
            findNavController().navigate(R.id.appSettingsFragment)
        }

        // 5️⃣ Usage Statistics
        rowUsage.setOnClickListener {
            findNavController().navigate(R.id.usageFragment)
        }

        // 6️⃣ Privacy Policy
        rowPrivacy.setOnClickListener {
            findNavController().navigate(R.id.privacyFragment)
        }

        // 7️⃣ Feedback / Support
        rowFeedback.setOnClickListener {
            findNavController().navigate(R.id.feedbackFragment)
        }

        // 8️⃣ Logout – logout ekranına git
        rowLogout.setOnClickListener {
            findNavController().navigate(R.id.logoutFragment)
        }
    }

    // 🔹 Sosyal medya ikonlarını bağla
    private fun setupSocialLinks() = with(binding) {
        ivSocialWebsite.setOnClickListener { openUrl(URL_WEBSITE) }
        ivSocialFacebook.setOnClickListener { openUrl(URL_FACEBOOK) }
        ivSocialInstagram.setOnClickListener { openUrl(URL_INSTAGRAM) }
        ivSocialYoutube.setOnClickListener { openUrl(URL_YOUTUBE) }
    }

    // 🔹 Footer’da versiyonu otomatik yaz
    private fun setupFooterVersion() {
        // strings.xml: "© 2024 AquaLight • Version %1$s"
        binding.tvFooterInfo.text = getString(
            R.string.settings_footer_info,
            BuildConfig.VERSION_NAME
        )
    }

    // 🔹 Ortak URL açma fonksiyonu
    private fun openUrl(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        // browser yoksa crash olmasın diye try/catch
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}