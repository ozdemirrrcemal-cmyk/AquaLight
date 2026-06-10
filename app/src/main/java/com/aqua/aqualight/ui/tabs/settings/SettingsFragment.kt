package com.aqua.aqualight.ui.tabs.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.R
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val devicesStore by lazy {
        DevicesDataStoreManager.create(requireContext())
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentSettingsBinding.bind(view)

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        observeUserInfo()
        observeActiveDevices()
        setupClickListeners()
        setupSocialLinks()
        setupFooterVersion()
    }

    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.userPrefsFlow.collectLatest { prefs ->
                    val username = prefs.username.ifBlank {
                        getString(R.string.settings_default_username)
                    }

                    val email = prefs.email.ifBlank {
                        getString(R.string.settings_default_email)
                    }

                    binding.tvUsername.text = username
                    binding.tvEmail.text = email

                    if (prefs.profilePhotoUrl.isNotBlank()) {
                        binding.ivProfilePhoto.load(prefs.profilePhotoUrl) {
                            placeholder(R.drawable.ic_profile_placeholder)
                            error(R.drawable.ic_profile_placeholder)
                            crossfade(true)
                        }
                    } else {
                        binding.ivProfilePhoto.setImageResource(
                            R.drawable.ic_profile_placeholder
                        )
                    }
                }
            }
        }
    }

    private fun observeActiveDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    devicesStore.devicesFlow,
                    DevicePresenceMonitor.statuses
                ) { devices, statuses ->
                    devices to statuses
                }.collectLatest { pair ->
                    val devices = pair.first
                    val statuses = pair.second
                    val now = System.currentTimeMillis()

                    val activeDeviceCount = devices.count { device ->
                        val statusState = statuses[device.id]

                        statusState?.isOnline ?: (
                            device.lastSeenMillis > 0L &&
                                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
                            )
                    }

                    updateActiveDevices(
                        activeDevices = activeDeviceCount
                    )
                }
            }
        }
    }

    private fun updateActiveDevices(
        activeDevices: Int
    ) {
        if (activeDevices > 0) {
            binding.tvActiveDevices.text = getString(
                R.string.settings_active_devices,
                activeDevices
            )

            binding.viewDeviceDot.setBackgroundResource(
                R.drawable.bg_online_dot
            )
        } else {
            binding.tvActiveDevices.text = getString(
                R.string.settings_no_active_devices
            )

            binding.viewDeviceDot.setBackgroundResource(
                R.drawable.bg_offline_dot
            )
        }
    }

    private fun setupClickListeners() = with(binding) {
    ivProfilePhoto.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_editProfileFragment
        )
    }

    rowUserInfo.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_userInfoFragment
        )
    }

    rowDeviceStatus.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_deviceStatusFragment
        )
    }

    rowNetwork.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_networkFragment
        )
    }

    rowSettings.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_appSettingsFragment
        )
    }

    rowUsage.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_usageFragment
        )
    }

    rowPrivacy.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_privacyFragment
        )
    }

    rowFeedback.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_feedbackFragment
        )
    }

    rowLogout.setOnClickListener {
        findNavController().navigate(
            R.id.action_settingsFragment_to_logoutFragment
        )
    }
}

    private fun setupSocialLinks() = with(binding) {
        ivSocialWebsite.setOnClickListener {
            openUrlInBrowser(URL_WEBSITE)
        }

        ivSocialFacebook.setOnClickListener {
            openUrlPreferApp(
                url = URL_FACEBOOK,
                appPackage = PKG_FACEBOOK
            )
        }

        ivSocialInstagram.setOnClickListener {
            openUrlPreferApp(
                url = URL_INSTAGRAM,
                appPackage = PKG_INSTAGRAM
            )
        }

        ivSocialYoutube.setOnClickListener {
            openYoutubeChannel()
        }
    }

    private fun setupFooterVersion() {
        binding.tvFooterInfo.text = getString(
            R.string.settings_footer_info,
            BuildConfig.VERSION_NAME
        )
    }

    private fun openUrlInBrowser(
        url: String
    ) {
        if (url.isBlank()) {
            return
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        try {
            startActivity(webIntent)
        } catch (_: Exception) {
            // Browser yoksa sessiz geç.
        }
    }

    private fun openUrlPreferApp(
        url: String,
        appPackage: String
    ) {
        if (url.isBlank()) {
            return
        }

        val packageManager: PackageManager = requireContext().packageManager

        val appIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply {
            setPackage(appPackage)
        }

        val resolveInfo = packageManager.resolveActivity(
            appIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        if (resolveInfo != null) {
            try {
                startActivity(appIntent)
                return
            } catch (_: Exception) {
                // Browser fallback.
            }
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        try {
            startActivity(webIntent)
        } catch (_: Exception) {
            // Browser yoksa sessiz geç.
        }
    }

    private fun openYoutubeChannel() {
        if (URL_YOUTUBE.isBlank()) {
            return
        }

        val appIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(URL_YOUTUBE)
        ).apply {
            setPackage(PKG_YOUTUBE)
        }

        try {
            startActivity(appIntent)
            return
        } catch (_: ActivityNotFoundException) {
            try {
                val storeIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$PKG_YOUTUBE")
                )

                startActivity(storeIntent)
            } catch (_: Exception) {
                // Play Store yoksa sessiz geç.
            }
        } catch (_: Exception) {
            // Sessiz geç.
        }
    }

    override fun onDestroyView() {
        binding.ivProfilePhoto.setImageDrawable(null)
        _binding = null

        super.onDestroyView()
    }

    private companion object {
        const val ONLINE_TIMEOUT_MS = 60_000L

        const val URL_WEBSITE = "https://aqualight.example.com"
        const val URL_FACEBOOK = "https://www.facebook.com/aqualight"
        const val URL_INSTAGRAM = "https://www.instagram.com/aqualight"
        const val URL_YOUTUBE = "https://youtube.com/@aqualight"

        const val PKG_FACEBOOK = "com.facebook.katana"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
    }
}