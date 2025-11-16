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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()
        setupClicks()

        // İlk açılışta da switch state’i hesapla
        updateNotificationSwitchState()
    }

    override fun onResume() {
        super.onResume()
        // Ayarlardan dönünce her seferinde tekrar hesapla
        updateNotificationSwitchState()
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

        // 🔔 Notifications toggle (listener burada bağlanıyor,
        // updateNotificationSwitchState içinde yeniden set edilecek)
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            handleNotificationToggle(isChecked)
        }

        // 🌐 Auto Update toggle (tamamen bağımsız)
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

    /**
     * 🔄 Eski koddaki `updateNotificationSwitchState`’in DataStore versiyonu
     *
     * appEnabled: DataStore’da tuttuğumuz kullanıcı tercihi
     * hasPermission: Android 13+ notification izni
     * systemEnabled: OS genel bildirim durumu
     *
     * Switch = appEnabled && hasPermission && systemEnabled
     */
    private fun updateNotificationSwitchState() {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        viewLifecycleOwner.lifecycleScope.launch {
            val appEnabled = userPrefs.notificationsEnabled.first()
            val isChecked = appEnabled && hasPermission && systemEnabled

            // Listener tetiklemeden UI’yi güncelle
            binding.switchNotifications.setOnCheckedChangeListener(null)
            binding.switchNotifications.isChecked = isChecked
            binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
                handleNotificationToggle(checked)
            }
        }
    }

    /**
     * Eski `handleNotificationToggle`’ın bire bir DataStore uyarlaması
     */
    private fun handleNotificationToggle(enable: Boolean) {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        if (enable) {
            when {
                // Android 13+ ve izin yok
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission -> {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // Kullanıcı daha önce reddetti → bottom sheet
                        showNotificationsBottomSheet()
                    } else {
                        // Direkt izin iste
                        NotificationHelper.requestPermission(requireActivity())
                    }
                }

                // İzin var ama sistem bildirimleri kapalı
                hasPermission && !systemEnabled -> {
                    // Eski koddaki gibi bottom sheet
                    showNotificationsBottomSheet()
                }

                // Her şey yolunda → app flag true
                else -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.updateNotificationsEnabled(true)
                        updateNotificationSwitchState()
                    }
                }
            }
        } else {
            // Uygulama içinden kapattı → app flag false
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(false)
                updateNotificationSwitchState()
            }
        }
    }

    private fun showNotificationsBottomSheet() {
        NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION).apply {
            // Eski koddaki gibi: bottom sheet kapanınca switch state yeniden hesapla
            onDismissListener = { updateNotificationSwitchState() }
        }.show(parentFragmentManager, "notifications_bottom_sheet")
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
                // Kullanıcı izin verdi → app flag’i true yap, sonra state’i hesapla
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.updateNotificationsEnabled(true)
                    updateNotificationSwitchState()
                }
            } else {
                // Reddetti → app flag false, switch kapat, bottom sheet göster
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.updateNotificationsEnabled(false)
                }
                binding.switchNotifications.isChecked = false
                showNotificationsBottomSheet()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}