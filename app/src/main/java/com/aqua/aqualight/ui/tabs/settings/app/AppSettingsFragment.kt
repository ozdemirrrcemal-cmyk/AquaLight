package com.aqua.aqualight.ui.tabs.settings.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    private var changingNotificationSwitchProgrammatically = false

    /**
     * 🔥 Android 13+ izin akışı için garanti eden launcher
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            // Kullanıcının evet/hayır cevabı burada %100 garantili
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(granted)
            }

            // 🔥 UI'nın next-frame'inde switch'i güncelle (Samsung/Xiaomi fix)
            binding?.root?.post {
                refreshNotificationSwitchState()
            }
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()
        setupClicks()

        refreshNotificationSwitchState()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationSwitchState()
    }

    // 🔥 Sistem + DataStore → switch durumu
    private fun refreshNotificationSwitchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()

            val appEnabled = userPrefs.notificationsEnabled.first()
            val hasPermission = NotificationHelper.hasSystemPermission(ctx)
            val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

            val finalState = appEnabled && hasPermission && systemEnabled
            setNotificationSwitchChecked(finalState)
        }
    }

    private fun setNotificationSwitchChecked(value: Boolean) {
        changingNotificationSwitchProgrammatically = true
        binding.switchNotifications.isChecked = value
        changingNotificationSwitchProgrammatically = false
    }

    private fun setupClicks() = with(binding) {

        btnBack.setOnClickListener { findNavController().popBackStack() }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!changingNotificationSwitchProgrammatically) {
                handleNotificationToggle(isChecked)
            }
        }

        switchAutoUpdate.setOnCheckedChangeListener { _, enabled ->
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateAutoUpdateEnabled(enabled)
            }
        }

        cardThemeMode.setOnClickListener {
            ThemeBottomSheet().show(parentFragmentManager, "theme_bottom_sheet")
        }

        cardLanguage.setOnClickListener {
            findNavController().navigate(R.id.languageSettingsFragment)
        }

        cardAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutAppFragment)
        }
    }

    /**
     * 🔥 FINAL – Eski davranış ile birebir aynı modern akış:
     * - Android 13 permission → launcher ile
     * - System notification OFF → bottomsheet + switch OFF
     * - Switch anında güncellenir (next-frame trick)
     */
    private fun handleNotificationToggle(enable: Boolean) {
        val ctx = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(ctx)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

        if (enable) {

            // 1) Android 13+ ve runtime permission YOK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {

                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    openNotificationSheet()
                } else {
                    // 🔥 Yeni modern yöntem
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                // Kullanıcı izin verene kadar switch açma
                setNotificationSwitchChecked(false)
                return
            }

            // 2) Sistem bildirimleri kapalı
            if (hasPermission && !systemEnabled) {
                setNotificationSwitchChecked(false)
                openNotificationSheet()
                return
            }

            // 3) Her şey tamam → gerçekten aç
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(true)
            }
            setNotificationSwitchChecked(true)
            return
        }

        // OFF durumu → kullanıcı tercihi
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(false)
        }
        setNotificationSwitchChecked(false)
    }

    // 🔥 Settings'e gidildiği anda switch güncelleniyor (UI next-frame + manuel refresh)
    private fun openNotificationSheet() {
        val sheet = NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)

        sheet.onSettingsOpened = {
            binding?.root?.post {
                refreshNotificationSwitchState()
            }
        }

        sheet.show(parentFragmentManager, "notifications_bottom_sheet")
    }


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

    private fun observeAutoUpdateState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.autoUpdateEnabled.collectLatest { enabled ->
                binding.switchAutoUpdate.isChecked = enabled
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}