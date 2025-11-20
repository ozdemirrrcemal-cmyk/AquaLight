package com.aqua.aqualight.ui.tabs.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
        private const val URL_WEBSITE   = "https://aqualight.example.com"
        private const val URL_FACEBOOK  = "https://www.facebook.com/aqualight"
        private const val URL_INSTAGRAM = "https://www.instagram.com/aqualight"
        private const val URL_YOUTUBE   = "https://www.youtube.com/@aqualight"
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
        ivSocialWebsite.setOnClickListener { openWebsite() }
        ivSocialFacebook.setOnClickListener { openFacebook() }
        ivSocialInstagram.setOnClickListener { openInstagram() }
        ivSocialYoutube.setOnClickListener { openYouTube() }
    }

    // 🔹 Footer’da versiyonu otomatik yaz
    private fun setupFooterVersion() {
        // Örn: "Copyright © 2025\nAquaLight All rights reserved.\nVersion %1$s"
        binding.tvFooterInfo.text = getString(
            R.string.settings_footer_info,
            BuildConfig.VERSION_NAME
        )
    }

    // -------------------------------------------------------------------
    // 🔻 Sosyal link helper’ları
    // -------------------------------------------------------------------

    private fun openWebsite() {
        openUrlGeneric(URL_WEBSITE)
    }

    private fun openFacebook() {
        val context = requireContext()
        val pm: PackageManager = context.packageManager
        val fbPackage = "com.facebook.katana"

        val intent = try {
            pm.getPackageInfo(fbPackage, 0)
            // Facebook app yüklü → aynı URL'yi app ile açmayı dene
            Intent(Intent.ACTION_VIEW, Uri.parse(URL_FACEBOOK)).apply {
                setPackage(fbPackage)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // Yüklü değil → tarayıcıya düş
            Intent(Intent.ACTION_VIEW, Uri.parse(URL_FACEBOOK))
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            // En son çare
            openUrlGeneric(URL_FACEBOOK)
        }
    }

    private fun openInstagram() {
        val username = "aqualight" // URL’de kullandığın kullanıcı adıyla aynı olsun
        val appUri = Uri.parse("http://instagram.com/_u/$username")
        val webUri = Uri.parse(URL_INSTAGRAM)

        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            setPackage("com.instagram.android")
        }

        try {
            startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            openUrlGeneric(webUri.toString())
        }
    }

    private fun openYouTube() {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_YOUTUBE)).apply {
            setPackage("com.google.android.youtube")
        }

        try {
            startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            openUrlGeneric(URL_YOUTUBE)
        }
    }

    // En genel fallback – tarayıcıyla aç
    private fun openUrlGeneric(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Browser bile yoksa yapacak bir şey yok, sessiz geçiyoruz
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}