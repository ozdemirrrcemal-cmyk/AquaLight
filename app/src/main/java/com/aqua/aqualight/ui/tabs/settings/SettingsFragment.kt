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

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    companion object {

        // 🔗 Sosyal linkler
        private const val URL_WEBSITE =
            "https://aqualight.example.com"

        private const val URL_FACEBOOK =
            "https://www.facebook.com/aqualight"

        private const val URL_INSTAGRAM =
            "https://www.instagram.com/aqualight"

        private const val URL_YOUTUBE =
            "https://youtube.com/@aqualight"

        private const val PKG_FACEBOOK =
            "com.facebook.katana"

        private const val PKG_INSTAGRAM =
            "com.instagram.android"

        private const val PKG_YOUTUBE =
            "com.google.android.youtube"
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
            FragmentSettingsBinding.bind(view)

        observeUserInfo()
		
		observeActiveDevices()

        setupClickListeners()

        setupSocialLinks()

        setupFooterVersion()

    }

    // ---------------------------------------------------
    // USER INFO
    // ---------------------------------------------------

    private fun observeUserInfo() {

        viewLifecycleOwner.lifecycleScope
            .launchWhenStarted {

                userPrefs.userPrefsFlow
                    .collectLatest { prefs ->

                        val username =
                            prefs.username.ifBlank {

                                getString(
                                    R.string.settings_default_username
                                )
                            }

                        val email =
                            prefs.email.ifBlank {

                                getString(
                                    R.string.settings_default_email
                                )
                            }

                        binding.tvUsername.text =
                            username

                        binding.tvEmail.text =
                            email

                        // 🔹 Profil fotoğrafı
                        if (
                            prefs.profilePhotoUrl.isNotBlank()
                        ) {

                            binding.ivProfilePhoto.load(
                                prefs.profilePhotoUrl
                            ) {

                                placeholder(
                                    R.drawable.ic_profile_placeholder
                                )

                                error(
                                    R.drawable.ic_profile_placeholder
                                )

                                crossfade(true)
                            }

                        } else {

                            binding.ivProfilePhoto
                                .setImageResource(
                                    R.drawable.ic_profile_placeholder
                                )
                        }
                    }
            }
    }
	
	// ---------------------------------------------------
// ACTIVE DEVICES OBSERVER
// ---------------------------------------------------

private fun observeActiveDevices() {

    viewLifecycleOwner.lifecycleScope
        .launchWhenStarted {

            userPrefs.devicesFlow
                .collectLatest { list ->

                    val now =
                        System.currentTimeMillis()

                    val onlineCount =
                        list.count { dev ->

                            dev.lastSeenMillis != 0L &&
                            (now - dev.lastSeenMillis) <= 60_000L
                        }

                    updateActiveDevices(
                        onlineCount
                    )
                }
        }
}
    // ---------------------------------------------------
    // ACTIVE DEVICES
    // ---------------------------------------------------

    private fun updateActiveDevices(
        activeDevices: Int
    ) {

        if (activeDevices > 0) {

            binding.tvActiveDevices.text =
                getString(
                    R.string.settings_active_devices,
                    activeDevices
                )

            binding.viewDeviceDot
                .setBackgroundResource(
                    R.drawable.bg_online_dot
                )

        } else {

            binding.tvActiveDevices.text =
                getString(
                    R.string.settings_no_active_devices
                )

            binding.viewDeviceDot
                .setBackgroundResource(
                    R.drawable.bg_offline_dot
                )
        }
    }

    // ---------------------------------------------------
    // CLICK LISTENERS
    // ---------------------------------------------------

    private fun setupClickListeners() =
        with(binding) {

            ivProfilePhoto.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.editProfileFragment
                    )
            }

            rowUserInfo.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.userInfoFragment
                    )
            }

            rowDeviceStatus.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.deviceStatusFragment
                    )
            }

            rowNetwork.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.networkFragment
                    )
            }

            rowSettings.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.appSettingsFragment
                    )
            }

            rowUsage.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.usageFragment
                    )
            }

            rowPrivacy.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.privacyFragment
                    )
            }

            rowFeedback.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.feedbackFragment
                    )
            }

            rowLogout.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.logoutFragment
                    )
            }
        }

    // ---------------------------------------------------
    // SOCIAL LINKS
    // ---------------------------------------------------

    private fun setupSocialLinks() =
        with(binding) {

            // 🌐 Website
            ivSocialWebsite.setOnClickListener {

                openUrlInBrowser(
                    URL_WEBSITE
                )
            }

            // 📘 Facebook
            ivSocialFacebook.setOnClickListener {

                openUrlPreferApp(
                    URL_FACEBOOK,
                    PKG_FACEBOOK
                )
            }

            // 📸 Instagram
            ivSocialInstagram.setOnClickListener {

                openUrlPreferApp(
                    URL_INSTAGRAM,
                    PKG_INSTAGRAM
                )
            }

            // ▶️ YouTube
            ivSocialYoutube.setOnClickListener {

                openYoutubeChannel()
            }
        }

    // ---------------------------------------------------
    // FOOTER VERSION
    // ---------------------------------------------------

    private fun setupFooterVersion() {

        binding.tvFooterInfo.text =
            getString(
                R.string.settings_footer_info,
                BuildConfig.VERSION_NAME
            )
    }

    // ---------------------------------------------------
    // BROWSER
    // ---------------------------------------------------

    private fun openUrlInBrowser(
        url: String
    ) {

        if (url.isBlank()) return

        val webIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

        try {

            startActivity(webIntent)

        } catch (_: Exception) {

            // Browser yoksa sessiz geç
        }
    }

    // ---------------------------------------------------
    // OPEN URL WITH APP
    // ---------------------------------------------------

    private fun openUrlPreferApp(
        url: String,
        appPackage: String
    ) {

        if (url.isBlank()) return

        val pm: PackageManager =
            requireContext().packageManager

        // 🔹 App intent
        val appIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ).apply {

                setPackage(appPackage)
            }

        val resolveInfo =
            pm.resolveActivity(
                appIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

        if (resolveInfo != null) {

            try {

                startActivity(appIntent)

                return

            } catch (_: Exception) {

                // Browser fallback
            }
        }

        // 🔹 Browser fallback
        val webIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

        try {

            startActivity(webIntent)

        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------
    // YOUTUBE
    // ---------------------------------------------------

    private fun openYoutubeChannel() {

        val url = URL_YOUTUBE

        if (url.isBlank()) return

        val appIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ).apply {

                setPackage(PKG_YOUTUBE)
            }

        try {

            startActivity(appIntent)

            return

        } catch (
            e: ActivityNotFoundException
        ) {

            // 🔹 Play Store fallback
            try {

                val storeIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "market://details?id=$PKG_YOUTUBE"
                        )
                    )

                startActivity(storeIntent)

            } catch (_: Exception) {
            }

        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}