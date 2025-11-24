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
        private const val URL_YOUTUBE   = "https://youtube.com/@xendil1099"

        private const val PKG_FACEBOOK  = "com.facebook.katana"
        private const val PKG_INSTAGRAM = "com.instagram.android"
        private const val PKG_YOUTUBE   = "com.google.android.youtube"
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

        ivProfilePhoto.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }

        rowUserInfo.setOnClickListener {
            findNavController().navigate(R.id.userInfoFragment)
        }

        rowDeviceStatus.setOnClickListener {
            findNavController().navigate(R.id.deviceStatusFragment)
        }

        rowNetwork.setOnClickListener {
            findNavController().navigate(R.id.networkFragment)
        }

        rowSettings.setOnClickListener {
            findNavController().navigate(R.id.appSettingsFragment)
        }

        rowUsage.setOnClickListener {
            findNavController().navigate(R.id.usageFragment)
        }

        rowPrivacy.setOnClickListener {
            findNavController().navigate(R.id.privacyFragment)
        }

        rowFeedback.setOnClickListener {
            findNavController().navigate(R.id.feedbackFragment)
        }

        rowLogout.setOnClickListener {
            findNavController().navigate(R.id.logoutFragment)
        }
    }

    // 🔹 Sosyal medya ikonlarını bağla
    private fun setupSocialLinks() = with(binding) {
        // Website her zaman tarayıcıda
        ivSocialWebsite.setOnClickListener {
            openUrlInBrowser(URL_WEBSITE)
        }

        // Facebook -> önce app dene, yoksa tarayıcı
        ivSocialFacebook.setOnClickListener {
            openUrlPreferApp(URL_FACEBOOK, PKG_FACEBOOK)
        }

        // Instagram
        ivSocialInstagram.setOnClickListener {
            openUrlPreferApp(URL_INSTAGRAM, PKG_INSTAGRAM)
        }

        // YouTube -> sadece uygulama (gerekirse Play Store), TARAYICI YOK
        ivSocialYoutube.setOnClickListener {
            openYoutubeChannel()
        }
    }

    // 🔹 Footer’da versiyonu otomatik yaz
    private fun setupFooterVersion() {
        // "Copyright © 2025\nAquaLight All rights reserved.\nVersion %1$s"
        binding.tvFooterInfo.text = getString(
            R.string.settings_footer_info,
            BuildConfig.VERSION_NAME
        )
    }

    /**
     * Sadece tarayıcıda aç
     */
    private fun openUrlInBrowser(url: String) {
        if (url.isBlank()) return
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(webIntent)
        } catch (_: Exception) {
            // hiç browser yoksa sessiz geç
        }
    }

    /**
     * Önce ilgili uygulamayı dene, yüklü değilse tarayıcıya düş
     */
    private fun openUrlPreferApp(url: String, appPackage: String) {
        if (url.isBlank()) return

        val pm: PackageManager = requireContext().packageManager

        // 1) App yüklü mü?
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(appPackage)
        }

        val resolveInfo = pm.resolveActivity(appIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo != null) {
            // App VAR → direkt onu aç ve FONKSİYONDAN ÇIK
            try {
                startActivity(appIntent)
                return
            } catch (_: Exception) {
                // yine de açamazsa aşağıdaki browser'a düşer
            }
        }

        // 2) App yoksa / açamadıysa → tarayıcı
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(webIntent)
        } catch (_: Exception) {
        }
    }

    /**
     * YouTube ikonu için: sadece YouTube app + Play Store fallback
     * Tarayıcı kesinlikle açılmaz.
     */
    private fun openYoutubeChannel() {
        val url = URL_YOUTUBE
        if (url.isBlank()) return

        // 1) YouTube uygulamasını dene
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(PKG_YOUTUBE)
        }

        try {
            startActivity(appIntent)
            return
        } catch (e: ActivityNotFoundException) {
            // YouTube app yok → Play Store'a yönlendir
            try {
                val storeIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$PKG_YOUTUBE")
                )
                startActivity(storeIntent)
            } catch (_: Exception) {
                // Play Store da yoksa tamamen sessiz kal
            }
        } catch (_: Exception) {
            // başka bir hata olursa da sessiz geç
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}