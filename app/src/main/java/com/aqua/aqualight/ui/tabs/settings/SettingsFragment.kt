package com.aqua.aqualight.ui.tabs.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import com.aqua.aqualight.ui.tabs.settings.device.DeviceSettingsDeviceOverviewUi
import com.aqua.aqualight.ui.common.text.resolve
import java.util.Calendar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
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
        binding.rowAbout.contentDescription =
            getString(R.string.settings_about_desc_app_only)

        observeUiState()
        setupClickListeners()
        setupSocialLinks()
        setupFooterVersion()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    renderUserInfo(
                        state = state
                    )

                    renderDeviceStatus(
                        overview = state.deviceOverview
                    )
                }
            }
        }
    }

    private fun renderUserInfo(
        state: SettingsUiState
    ) {
        binding.tvUsername.text = state.username.ifBlank {
            getString(R.string.settings_default_username)
        }

        binding.tvEmail.text = state.email.ifBlank {
            getString(R.string.settings_default_email)
        }

        if (state.profilePhotoUrl.isNotBlank()) {
            binding.ivProfilePhoto.load(state.profilePhotoUrl) {
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

    private fun renderDeviceStatus(
        overview: DeviceSettingsDeviceOverviewUi
    ) {
        binding.tvActiveDevices.text = requireContext().resolve(overview.activeDeviceCountText)

        binding.viewDeviceDot.setBackgroundResource(
            if (overview.hasOnlineDevices) {
                R.drawable.bg_online_dot
            } else {
                R.drawable.bg_offline_dot
            }
        )
    }

    private fun setupClickListeners() = with(binding) {
        ivProfilePhoto.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToEditProfileFragment()
            )
        }

        rowUserInfo.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToUserInfoFragment()
            )
        }

        rowDeviceStatus.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToDeviceStatusFragment()
            )
        }


        rowSettings.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToAppSettingsFragment()
            )
        }

        rowUsage.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToUsageFragment()
            )
        }

        rowFeedback.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToFeedbackFragment()
            )
        }

        rowLegal.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToLegalCenterFragment()
            )
        }

        rowAbout.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToAboutAppFragment()
            )
        }

        rowLogout.setOnClickListener {
            safeNavigate(
                SettingsFragmentDirections.actionSettingsFragmentToLogoutFragment()
            )
        }
    }

    private fun safeNavigate(
        directions: NavDirections
    ) {
        val navController = runCatching {
            findNavController()
        }.getOrNull() ?: return

        if (navController.currentDestination?.id != R.id.settingsFragment) {
            return
        }

        runCatching {
            navController.navigate(directions)
        }
    }

    private fun setupSocialLinks() = with(binding) {
        ivSocialWebsite.visibility = if (URL_WEBSITE.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        ivSocialFacebook.visibility = if (URL_FACEBOOK.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        ivSocialInstagram.visibility = if (URL_INSTAGRAM.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        ivSocialYoutube.visibility = if (URL_YOUTUBE.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        rowSocialLinks.visibility = if (
            URL_WEBSITE.isBlank() &&
            URL_FACEBOOK.isBlank() &&
            URL_INSTAGRAM.isBlank() &&
            URL_YOUTUBE.isBlank()
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }

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
            Calendar.getInstance().get(Calendar.YEAR),
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
        const val URL_WEBSITE = ""
        const val URL_FACEBOOK = ""
        const val URL_INSTAGRAM = ""
        const val URL_YOUTUBE = ""

        const val PKG_FACEBOOK = "com.facebook.katana"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
    }
}
