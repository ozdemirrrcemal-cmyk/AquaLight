package com.aqua.aqualight.ui.tabs.settings.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Switch’i programatik set ederken listener’ın tetiklenmemesi için
    private var updatingNotificationSwitch = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        observeThemeSummary()
        observeLanguageSummary()
        observeNotificationState()
        observeAutoUpdateState()
        setupClicks()
    }

    // 🌙 Tema yazısı
    private fun observeThemeSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.themeMode.collectLatest { mode ->
                binding.tvThemeSummary.text = when (mode) {
                    "dark" -> getString(R.string.app_settings_theme_dark)
                    "system" -> getString(R.string.app_settings_theme_system)
                    else -> getString(R.string.app_settings_theme_light)
                }
            }
        }
    }

    // 🌍 Dil yazısı
    private fun observeLanguageSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.languageCode.collectLatest { code ->
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

    // 🔔 Bildirim switch’i
    private fun observeNotificationState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.notificationsEnabled.collectLatest { enabled ->
                updatingNotificationSwitch = true
                binding.switchNotifications.isChecked = enabled
                updatingNotificationSwitch = false
            }
        }
    }

    // 🌐 Auto-update switch’i
    private fun observeAutoUpdateState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.autoUpdateEnabled.collectLatest { enabled ->
                binding.switchAutoUpdate.isChecked = enabled
            }
        }
    }

    private fun setupClicks() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔔 Notifications toggle
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (updatingNotificationSwitch) return@setOnCheckedChangeListener
            handleNotificationToggle(isChecked)
        }

        // 🌐 Auto Update toggle
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateAutoUpdateEnabled(isChecked)
            }
        }

        // 🌙 Theme – bottom sheet
        cardThemeMode.setOnClickListener {
            ThemeBottomSheet().show(parentFragmentManager, "theme_bottom_sheet")
        }

        // 🌍 Language
        cardLanguage.setOnClickListener {
            findNavController().navigate(R.id.languageSettingsFragment)
        }

        // ℹ️ About
        cardAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutAppFragment)
        }
    }

    // Eski handleNotificationToggle’ın DataStore + yeni helper’a uyarlanmış hali
    private fun handleNotificationToggle(enable: Boolean) {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        if (enable) {
            when {
                // Android 13+ ve izin yok
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission -> {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationsBottomSheet()
                    } else {
                        NotificationHelper.requestPermission(requireActivity())
                    }
                }

                // Sistem bildirimi kapalı
                hasPermission && !systemEnabled -> {
                    showNotificationsBottomSheet()
                }

                // Her şey yolunda → DataStore’a kaydet
                else -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.updateNotificationsEnabled(true)
                    }
                }
            }
        } else {
            // Kullanıcı app içinden kapattı
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(false)
            }
        }
    }

    private fun showNotificationsBottomSheet() {
        NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)
            .show(parentFragmentManager, "notifications_bottom_sheet")
    }

    // Android 13 izin sonucu (NotificationHelper.requestPermission sonrası)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NotificationHelper.REQUEST_CODE_NOTIFICATIONS) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted) {
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.updateNotificationsEnabled(true)
                }
            } else {
                updatingNotificationSwitch = true
                binding.switchNotifications.isChecked = false
                updatingNotificationSwitch = false
                showNotificationsBottomSheet()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}