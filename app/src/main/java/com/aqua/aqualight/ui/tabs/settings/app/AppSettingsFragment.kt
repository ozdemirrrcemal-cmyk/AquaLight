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
     * ✔ Android 13+ izin akışı için garantili launcher
     * ✔ Tüm cihazlarda çalışır
     * ✔ Fragment'a döner (onRequestPermissionsResult kullanılmaz)
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            // Kullanıcı izin verdi/vermedi → net sonuç burada
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(granted)
            }

            // Switch'i anında doğru duruma getir
            refreshNotificationSwitchState()
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

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

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
     * ✔ Android 13+ izin akışı
     * ✔ Sistem bildirimleri kapalı → bottomsheet + switch geri OFF
     * ✔ Eski davranışla birebir aynı
     */
    private fun handleNotificationToggle(enable: Boolean) {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        if (enable) {

            // 1) Android 13+ ve izin yok
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {

                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    openNotificationSheet()
                } else {
                    // 🔥 Guaranteed: izin sonucu launcher ile net döner
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                // İzin netleşene kadar kullanıcıya switch on gösterme
                setNotificationSwitchChecked(false)
                return
            }

            // 2) İzin var ama sistem bildirimleri kapalı
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

        // Kullanıcı kapatıyor
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(false)
        }
        setNotificationSwitchChecked(false)
    }

    // 🔥 Settings'e gidildiği anda switch güncelleniyor (lifecycle beklenmez)
    private fun openNotificationSheet() {
        val sheet = NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)

        sheet.onSettingsOpened = {
            // Kullanıcı ayarlara gider gitmez switch senkron
            refreshNotificationSwitchState()
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
                    "ru" -> getString(Rstring.language_russian)
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