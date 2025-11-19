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
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    private var changingNotificationSwitchProgrammatically = false


    // ---------------------------------------------------------
    // 🔥 ANDROID 13+ RUNTIME PERMISSION – %100 ÇALIŞAN FINAL
    // ---------------------------------------------------------
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            // 1) DataStore güncelle
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(granted)
            }

            // 2) Switch'i ANINDA güncelle (GECİKME YOK!)
            setNotificationSwitchChecked(granted)

            // 3) Samsung/Xiaomi "permission dismiss delay" fix
            binding?.root?.postDelayed({
                refreshNotificationSwitchState()
            }, 150)
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        setupClicks()
        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()

        // İlk yüklemede switch'i göster
        refreshNotificationSwitchState()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationSwitchState()
    }


    // ---------------------------------------------------------
    // 🔥 Sistem + Kullanıcı tercihi birleşimi
    // ---------------------------------------------------------
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


    // ---------------------------------------------------------
    // 🔥 Tüm tıklama işlemleri
    // ---------------------------------------------------------
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
            ThemeBottomSheet().show(parentFragmentManager, "theme_sheet")
        }

        cardLanguage.setOnClickListener {
            findNavController().navigate(R.id.languageSettingsFragment)
        }

        cardAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutAppFragment)
        }
    }


    // ---------------------------------------------------------
    // 🔥 Modern Notification Permission Akışı – %100 Final
    // ---------------------------------------------------------
    private fun handleNotificationToggle(enable: Boolean) {
        val ctx = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(ctx)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

        if (enable) {

            // 1) Android 13+ runtime izin yoksa
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {

                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    openNotificationSheet()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                // Kullanıcı izin verene kadar switch kapalı kalsın
                setNotificationSwitchChecked(false)
                return
            }

            // 2) Sistem bildirimleri kapalıysa
            if (hasPermission && !systemEnabled) {
                setNotificationSwitchChecked(false)
                openNotificationSheet()
                return
            }

            // 3) Gerçekten aç
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(true)
            }
            setNotificationSwitchChecked(true)
            return
        }

        // Kapatma
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(false)
        }
        setNotificationSwitchChecked(false)
    }


    // ---------------------------------------------------------
    // 🔥 BottomSheet açıldığında ANINDA güncelleme fix
    // ---------------------------------------------------------
    private fun openNotificationSheet() {
        val sheet = NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)

        sheet.onSettingsOpened = {
            binding?.root?.post {
                refreshNotificationSwitchState()
            }
        }

        sheet.show(parentFragmentManager, "notifications_sheet")
    }


    // ---------------------------------------------------------
    // Özetler
    // ---------------------------------------------------------
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