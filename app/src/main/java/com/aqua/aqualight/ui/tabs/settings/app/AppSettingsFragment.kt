package com.aqua.aqualight.ui.tabs.settings.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    private var changingNotificationSwitchProgrammatically = false

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            viewLifecycleOwner.lifecycleScope.launch {
                val context =
                    requireContext()

                val systemEnabled =
                    NotificationHelper.areSystemNotificationsEnabled(
                        context
                    )

                val shouldEnableNotifications =
                    granted && systemEnabled

                settingsOperations.updateNotificationsEnabled(
                    shouldEnableNotifications
                )

                if (shouldEnableNotifications) {
                    settingsOperations.reschedulePendingCareTaskReminders()
                } else {
                    settingsOperations.cancelPendingCareTaskReminders()
                }

                setNotificationSwitchChecked(
                    shouldEnableNotifications
                )

                _binding?.root?.postDelayed(
                    {
                        refreshNotificationSwitchState()
                    },
                    150
                )
            }
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
            FragmentAppSettingsBinding.bind(view)

        setupHeader()
        setupClicks()
        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()
        refreshNotificationSwitchState()
    }

    override fun onResume() {
        super.onResume()

        refreshNotificationSwitchState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_app_settings)
            )
        )
    }

    private fun setupClicks() =
        with(binding) {

            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                if (!changingNotificationSwitchProgrammatically) {
                    handleNotificationToggle(
                        isChecked
                    )
                }
            }

            switchAutoUpdate.setOnCheckedChangeListener { _, enabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    settingsOperations.updateAutoUpdateEnabled(
                        enabled
                    )
                }
            }

            cardThemeMode.setOnClickListener {
                openThemeSheet()
            }

            cardLanguage.setOnClickListener {
                safeNavigate(
                    AppSettingsFragmentDirections.actionAppSettingsFragmentToLanguageSettingsFragment()
                )
            }

            cardAbout.setOnClickListener {
                safeNavigate(
                    AppSettingsFragmentDirections.actionAppSettingsFragmentToAboutAppFragment()
                )
            }
        }

    private fun openThemeSheet() {
        val sheet = ThemeBottomSheet().apply {
            onBeforeThemeApplied = {
                (activity as? MainActivity)
                    ?.markSettingsRootRestoreAfterThemeChange()

                runCatching {
                    findNavController().popBackStack(
                        R.id.settingsFragment,
                        false
                    )
                }
            }
        }

        sheet.show(
            parentFragmentManager,
            "theme_sheet"
        )
    }

    private fun safeNavigate(
        directions: NavDirections
    ) {
        val navController = runCatching {
            findNavController()
        }.getOrNull() ?: return

        if (navController.currentDestination?.id != R.id.appSettingsFragment) {
            return
        }

        runCatching {
            navController.navigate(directions)
        }
    }

    private fun refreshNotificationSwitchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context =
                requireContext()

            val appEnabled =
                settingsOperations.notificationsEnabled.first()

            val hasPermission =
                NotificationHelper.hasSystemPermission(
                    context
                )

            val systemEnabled =
                NotificationHelper.areSystemNotificationsEnabled(
                    context
                )

            val finalState =
                appEnabled && hasPermission && systemEnabled

            setNotificationSwitchChecked(
                finalState
            )
        }
    }

    private fun setNotificationSwitchChecked(
        value: Boolean
    ) {
        changingNotificationSwitchProgrammatically =
            true

        binding.switchNotifications.isChecked =
            value

        changingNotificationSwitchProgrammatically =
            false
    }

    private fun handleNotificationToggle(
        enable: Boolean
    ) {
        val context =
            requireContext()

        val hasPermission =
            NotificationHelper.hasSystemPermission(
                context
            )

        val systemEnabled =
            NotificationHelper.areSystemNotificationsEnabled(
                context
            )

        if (enable) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission
            ) {
                if (
                    shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    openNotificationSheet()
                } else {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }

                setNotificationSwitchChecked(
                    false
                )

                return
            }

            if (
                hasPermission &&
                !systemEnabled
            ) {
                setNotificationSwitchChecked(
                    false
                )

                openNotificationSheet()

                return
            }

            viewLifecycleOwner.lifecycleScope.launch {
                settingsOperations.updateNotificationsEnabled(
                    true
                )

                settingsOperations.reschedulePendingCareTaskReminders()
            }

            setNotificationSwitchChecked(
                true
            )

            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateNotificationsEnabled(
                false
            )

            settingsOperations.cancelPendingCareTaskReminders()
        }

        setNotificationSwitchChecked(
            false
        )
    }

    private fun openNotificationSheet() {
        val sheet =
            NotificationsBottomSheet(
                NotificationsBottomSheet.PermissionType.NOTIFICATION
            )

        sheet.onSettingsOpened = {
            _binding?.root?.post {
                refreshNotificationSwitchState()
            }
        }

        sheet.show(
            parentFragmentManager,
            "notifications_sheet"
        )
    }

    private fun observeThemeSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.themeMode.collectLatest { mode ->
                binding.tvThemeSummary.text =
                    when (mode) {
                        "dark" -> getString(
                            R.string.app_settings_theme_dark
                        )

                        "system" -> getString(
                            R.string.app_settings_theme_system
                        )

                        else -> getString(
                            R.string.app_settings_theme_light
                        )
                    }
            }
        }
    }

    private fun observeLanguageSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest { code ->
                binding.tvLanguageSubtitle.text =
                    when (code) {
                        "tr" -> getString(
                            R.string.language_turkish
                        )

                        "de" -> getString(
                            R.string.language_german
                        )

                        "fr" -> getString(
                            R.string.language_french
                        )

                        "ru" -> getString(
                            R.string.language_russian
                        )

                        "zh" -> getString(
                            R.string.language_chinese
                        )

                        else -> getString(
                            R.string.language_english
                        )
                    }
            }
        }
    }

    private fun observeAutoUpdateState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.autoUpdateEnabled.collectLatest { enabled ->
                binding.switchAutoUpdate.isChecked =
                    enabled
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
