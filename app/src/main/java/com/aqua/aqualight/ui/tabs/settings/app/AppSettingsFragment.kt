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

    // Switch programatik değişirken listener tetiklenmesin
    private var changingNotificationSwitchProgrammatically = false

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
        refreshNotificationSwitchState()   // Settings ekranından dönünce tetiklenir
    }

    // 🔥 FINAL: Switch’in gerçek sistem + user pref değerine göre ayarlanması
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

    // Switch programatik değişimi helper
    private fun setNotificationSwitchChecked(checked: Boolean) {
        changingNotificationSwitchProgrammatically = true
        binding.switchNotifications.isChecked = checked
        changingNotificationSwitchProgrammatically = false
    }

    private fun setupClicks() = with(binding) {

        btnBack.setOnClickListener { findNavController().popBackStack() }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!changingNotificationSwitchProgrammatically) {
                handleNotificationToggle(isChecked)
            }
        }

        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateAutoUpdateEnabled(isChecked)
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
     * 🔥 FINAL – Eski davranış ile %100 aynı çalışan akış:
     */
    private fun handleNotificationToggle(enable: Boolean) {
        val ctx = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(ctx)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

        if (enable) {

            // 1) Android 13+ ve izin yok
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {

                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Daha önce reddedilmiş → bottomsheet
                    openNotificationSheet()
                } else {
                    // İlk kez izin iste
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NotificationHelper.REQUEST_CODE_NOTIFICATIONS
                    )
                }

                // İzin sonucu netleşene kadar switch OFF kalsın
                setNotificationSwitchChecked(false)
                return
            }

            // 2) İzin var ama sistem bildirimleri kapalı
            if (hasPermission && !systemEnabled) {
                setNotificationSwitchChecked(false)
                openNotificationSheet()
                return
            }

            // 3) Her şey tamam → switch gerçekten AÇIK
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(true)
            }
            setNotificationSwitchChecked(true)
            return
        }

        // Kullanıcı kapattı
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(false)
        }
        setNotificationSwitchChecked(false)
    }

    // 🔥 FINAL – Settings’den dönünce switch'i ANINDA güncelleyen sistem
    private fun openNotificationSheet() {
        val sheet = NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)

        sheet.onSettingsOpened = {
            // Kullanıcı ayarlara gider gitmez → switch senkronlanır
            refreshNotificationSwitchState()
        }

        sheet.show(parentFragmentManager, "notifications_bottom_sheet")
    }

    // Android 13 izin sonucu
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NotificationHelper.REQUEST_CODE_NOTIFICATIONS) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(granted)
            }

            refreshNotificationSwitchState()
        }
    }

    private fun observeThemeSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.themeMode.collectLatest { mode ->
                binding.tvThemeSummary.text =
                    when (mode) {
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
                binding.tvLanguageSubtitle.text =
                    when (code) {
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