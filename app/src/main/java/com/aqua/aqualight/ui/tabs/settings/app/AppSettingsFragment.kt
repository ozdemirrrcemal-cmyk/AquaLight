package com.aqua.aqualight.ui.tabs.settings.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.maintenance.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
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

                userPrefs.updateNotificationsEnabled(
                    shouldEnableNotifications
                )

                if (shouldEnableNotifications) {
                    reschedulePendingCareTaskReminders()
                } else {
                    cancelPendingCareTaskReminders()
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
            fragment = this
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
                    userPrefs.updateAutoUpdateEnabled(
                        enabled
                    )
                }
            }

            cardThemeMode.setOnClickListener {
                ThemeBottomSheet().show(
                    parentFragmentManager,
                    "theme_sheet"
                )
            }

            cardLanguage.setOnClickListener {
                findNavController().navigate(
                    R.id.action_appSettingsFragment_to_languageSettingsFragment
                )
            }

            cardAbout.setOnClickListener {
                findNavController().navigate(
                    R.id.action_appSettingsFragment_to_aboutAppFragment
                )
            }
        }

    private fun refreshNotificationSwitchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context =
                requireContext()

            val appEnabled =
                userPrefs.notificationsEnabled.first()

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
                userPrefs.updateNotificationsEnabled(
                    true
                )

                reschedulePendingCareTaskReminders()
            }

            setNotificationSwitchChecked(
                true
            )

            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(
                false
            )

            cancelPendingCareTaskReminders()
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

    private suspend fun reschedulePendingCareTaskReminders() {
        val context =
            context?.applicationContext ?: return

        val now =
            System.currentTimeMillis()

        val pendingTasks =
            CareTaskDataStoreManager.create(
                context
            )
                .pendingTasksFlow
                .first()

        pendingTasks
            .filter { task ->
                task.dueAtMillis > now
            }
            .forEach { task ->
                CareTaskReminderScheduler.schedule(
                    context = context,
                    task = task
                )
            }
    }

    private suspend fun cancelPendingCareTaskReminders() {
        val context =
            context?.applicationContext ?: return

        val pendingTasks =
            CareTaskDataStoreManager.create(
                context
            )
                .pendingTasksFlow
                .first()

        pendingTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = task.id
            )
        }
    }

    private fun observeThemeSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.themeMode.collectLatest { mode ->
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
            userPrefs.languageCode.collectLatest { code ->
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
            userPrefs.autoUpdateEnabled.collectLatest { enabled ->
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