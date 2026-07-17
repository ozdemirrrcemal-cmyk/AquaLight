package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.data.notifications.NotificationChannelState
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
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

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_ENABLE_NOTIFICATIONS -> completeNotificationEnableFlow()
        }
    }

    private var changingNotificationSwitchProgrammatically = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        setupHeader()
        setupClicks()
        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()
        refreshNotificationState()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_app_settings)
            )
        )
    }

    private fun setupClicks() = with(binding) {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!changingNotificationSwitchProgrammatically) {
                handleNotificationToggle(isChecked)
            }
        }

        switchAutoUpdate.setOnCheckedChangeListener { _, enabled ->
            viewLifecycleOwner.lifecycleScope.launch {
                settingsOperations.updateAutoUpdateEnabled(enabled)
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

        sheet.show(parentFragmentManager, "theme_sheet")
    }

    private fun safeNavigate(directions: NavDirections) {
        val navController = runCatching {
            findNavController()
        }.getOrNull() ?: return

        if (navController.currentDestination?.id != R.id.appSettingsFragment) {
            return
        }

        runCatching { navController.navigate(directions) }
    }

    private fun refreshNotificationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            NotificationHelper.createNotificationChannel(context)

            val ownerPreferenceEnabled = settingsOperations.notificationsEnabled.first()
            val runtimePermissionGranted = permissionCoordinator.isGranted(
                AppCapability.NOTIFICATIONS
            )
            val systemState = NotificationHelper.notificationSystemState(context)

            setNotificationSwitchChecked(ownerPreferenceEnabled)
            binding.tvNotificationsSubtitle.setText(
                when {
                    !ownerPreferenceEnabled -> {
                        R.string.settings_notifications_subtitle_disabled
                    }
                    !runtimePermissionGranted || !systemState.appNotificationsEnabled -> {
                        R.string.settings_notifications_subtitle_system_blocked
                    }
                    systemState.careReminderChannelState == NotificationChannelState.BLOCKED ||
                        systemState.careReminderChannelState == NotificationChannelState.MISSING -> {
                        R.string.settings_notifications_subtitle_channel_blocked
                    }
                    else -> R.string.settings_notifications_subtitle_enabled
                }
            )
        }
    }

    private fun setNotificationSwitchChecked(value: Boolean) {
        changingNotificationSwitchProgrammatically = true
        binding.switchNotifications.isChecked = value
        changingNotificationSwitchProgrammatically = false
    }

    private fun handleNotificationToggle(enable: Boolean) {
        if (!enable) {
            disableNotifications()
            return
        }

        setNotificationSwitchChecked(false)

        if (!permissionCoordinator.isGranted(AppCapability.NOTIFICATIONS)) {
            permissionCoordinator.runWhenGranted(
                capability = AppCapability.NOTIFICATIONS,
                actionToken = ACTION_ENABLE_NOTIFICATIONS
            )
            return
        }

        NotificationHelper.createNotificationChannel(requireContext())
        val systemState = NotificationHelper.notificationSystemState(requireContext())
        if (!systemState.canDeliverCareReminders) {
            permissionCoordinator.openSettingsFor(
                capability = AppCapability.NOTIFICATIONS,
                actionToken = ACTION_ENABLE_NOTIFICATIONS
            )
            return
        }

        completeNotificationEnableFlow()
    }

    private fun completeNotificationEnableFlow() {
        if (_binding == null) return

        val context = requireContext()
        NotificationHelper.createNotificationChannel(context)
        val canDeliver = permissionCoordinator.isGranted(AppCapability.NOTIFICATIONS) &&
            NotificationHelper.notificationSystemState(context).canDeliverCareReminders

        if (!canDeliver) {
            setNotificationSwitchChecked(false)
            refreshNotificationState()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateNotificationsEnabled(true)
            setNotificationSwitchChecked(true)

            _binding?.root?.postDelayed(
                ::refreshNotificationState,
                NOTIFICATION_STATE_REFRESH_DELAY_MS
            )
        }
    }

    private fun disableNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateNotificationsEnabled(false)
            refreshNotificationState()
        }
        setNotificationSwitchChecked(false)
    }

    private fun observeThemeSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.themeMode.collectLatest { mode ->
                binding.tvThemeSummary.text = when (mode) {
                    "dark" -> getString(R.string.app_settings_theme_dark)
                    "system" -> getString(R.string.app_settings_theme_system)
                    else -> getString(R.string.app_settings_theme_light)
                }
            }
        }
    }

    private fun observeLanguageSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest { code ->
                binding.tvLanguageSubtitle.text = when (code) {
                    "tr" -> getString(R.string.language_turkish)
                    "de" -> getString(R.string.language_german)
                    "fr" -> getString(R.string.language_french)
                    "ru" -> getString(R.string.language_russian)
                    "zh" -> getString(R.string.language_chinese)
                    else -> getString(R.string.language_english)
                }
            }
        }
    }

    private fun observeAutoUpdateState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.autoUpdateEnabled.collectLatest { enabled ->
                binding.switchAutoUpdate.isChecked = enabled
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val ACTION_ENABLE_NOTIFICATIONS = "enable_notifications"
        const val NOTIFICATION_STATE_REFRESH_DELAY_MS = 150L
    }
}
