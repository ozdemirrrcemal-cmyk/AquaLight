package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.application.notifications.NotificationPreferenceSnapshot
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }
    private val settingsOperations by lazy {
        appContainer.userSettingsOperations
    }
    private val notificationPreferences by lazy {
        appContainer.notificationPreferenceUseCase
    }
    private val ownerIdentity by lazy {
        appContainer.authenticatedOwnerIdentity
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_ENABLE_NOTIFICATIONS) {
            repairAndEnableNotifications()
        }
    }

    private var changingNotificationSwitchProgrammatically = false
    private var notificationSnapshot: NotificationPreferenceSnapshot? = null
    private var preciseReminderAccessGranted = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                if (isChecked) {
                    setNotificationSwitchChecked(false)
                    repairAndEnableNotifications()
                } else {
                    disableNotifications()
                }
            }
        }

        cardNotifications.setOnClickListener {
            val snapshot = notificationSnapshot
            if (
                snapshot?.ownerPreferenceEnabled == true &&
                (!snapshot.allCategoriesDeliverable || !preciseReminderAccessGranted)
            ) {
                repairAndEnableNotifications()
            }
        }

        switchAutoUpdate.setOnCheckedChangeListener { _, enabled ->
            viewLifecycleOwner.lifecycleScope.launch {
                settingsOperations.updateAutoUpdateEnabled(enabled)
            }
        }

        cardThemeMode.setOnClickListener { openThemeSheet() }
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

    private fun refreshNotificationState() {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = notificationPreferences.snapshot(ownerIdentity.requireOwnerUid())
            val preciseGranted = permissionCoordinator.isGranted(
                AppCapability.PRECISE_REMINDERS
            )
            notificationSnapshot = snapshot
            preciseReminderAccessGranted = preciseGranted
            setNotificationSwitchChecked(snapshot.ownerPreferenceEnabled)
            binding.tvNotificationsSubtitle.setText(
                when {
                    !snapshot.ownerPreferenceEnabled -> {
                        R.string.settings_notifications_subtitle_disabled
                    }
                    snapshot.delivery.values.any {
                        !it.runtimePermissionGranted || !it.appNotificationsEnabled
                    } -> {
                        R.string.settings_notifications_subtitle_system_blocked
                    }
                    snapshot.delivery.values.any {
                        it.channelState == NotificationChannelState.BLOCKED ||
                            it.channelState == NotificationChannelState.MISSING
                    } -> {
                        R.string.settings_notifications_subtitle_channel_blocked
                    }
                    !preciseGranted -> {
                        R.string.settings_notifications_subtitle_precise_reminders_blocked
                    }
                    else -> R.string.settings_notifications_subtitle_enabled
                }
            )
        }
    }

    private fun repairAndEnableNotifications() {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val ownerUid = ownerIdentity.requireOwnerUid()
            val snapshot = notificationPreferences.snapshot(ownerUid)
            notificationSnapshot = snapshot

            val anyRuntimePermissionMissing = snapshot.delivery.values.any {
                !it.runtimePermissionGranted
            }
            if (anyRuntimePermissionMissing) {
                permissionCoordinator.runWhenGranted(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = ACTION_ENABLE_NOTIFICATIONS
                )
                return@launch
            }

            if (snapshot.delivery.values.any { !it.appNotificationsEnabled }) {
                permissionCoordinator.openSettingsFor(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = ACTION_ENABLE_NOTIFICATIONS
                )
                return@launch
            }

            val blockedCategory = NotificationCategory.entries.firstOrNull { category ->
                val state = snapshot.readiness(category).channelState
                state == NotificationChannelState.BLOCKED ||
                    state == NotificationChannelState.MISSING
            }
            if (blockedCategory != null) {
                permissionCoordinator.openNotificationChannelSettingsFor(
                    channelId = notificationPreferences.channelId(blockedCategory),
                    actionToken = ACTION_ENABLE_NOTIFICATIONS
                )
                return@launch
            }

            if (!permissionCoordinator.isGranted(AppCapability.PRECISE_REMINDERS)) {
                permissionCoordinator.runWhenGranted(
                    capability = AppCapability.PRECISE_REMINDERS,
                    actionToken = ACTION_ENABLE_NOTIFICATIONS
                )
                return@launch
            }

            notificationPreferences.setEnabled(ownerUid, true)
            refreshNotificationState()
        }
    }

    private fun disableNotifications() {
        setNotificationSwitchChecked(false)
        viewLifecycleOwner.lifecycleScope.launch {
            notificationPreferences.setEnabled(ownerIdentity.requireOwnerUid(), false)
            refreshNotificationState()
        }
    }

    private fun setNotificationSwitchChecked(value: Boolean) {
        changingNotificationSwitchProgrammatically = true
        binding.switchNotifications.isChecked = value
        changingNotificationSwitchProgrammatically = false
    }

    private fun openThemeSheet() {
        val sheet = ThemeBottomSheet().apply {
            onBeforeThemeApplied = {
                (activity as? MainActivity)?.markSettingsRootRestoreAfterThemeChange()
                runCatching {
                    findNavController().popBackStack(R.id.settingsFragment, false)
                }
            }
        }
        sheet.show(parentFragmentManager, "theme_sheet")
    }

    private fun safeNavigate(directions: NavDirections) {
        val navController = runCatching { findNavController() }.getOrNull() ?: return
        if (navController.currentDestination?.id != R.id.appSettingsFragment) return
        runCatching { navController.navigate(directions) }
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
    }
}
