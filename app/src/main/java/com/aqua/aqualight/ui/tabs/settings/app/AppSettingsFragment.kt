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

    // Switch’i programatik set ederken listener tetiklenmesin diye
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

    override fun onResume() {
        super.onResume()
        // 🔄 Ayarlardan geri dönüldüğünde veya sistemde bir şey değiştiğinde
        // DataStore’daki notification flag’i sistemle senkronla
        syncNotificationWithSystem()
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

    // 🔔 Bildirim switch’i — sadece DataStore’daki *efektif* değeri gösteriyor
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

        // 🌐 Auto Update toggle (bağımsız)
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
     * 🔁 Sistem ile DataStore’u senkronlar:
     * - Hem izin VAR hem sistem bildirimleri AÇIK → DataStore = true
     * - Aksi halde → DataStore = false
     */
    private fun syncNotificationWithSystem() {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        val effectiveEnabled = hasPermission && systemEnabled

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateNotificationsEnabled(effectiveEnabled)
        }
    }

    /**
     * 🔔 Switch davranışı:
     *
     * - ON:
     *   - Android 13+ ve izin yok → izin iste / rationale / bottom sheet
     *   - İzin var ama sistem bildirimleri kapalı → bottom sheet + switch’i tekrar OFF yap
     *   - Her şey yolundaysa → DataStore = true
     *
     * - OFF:
     *   - DataStore = false (kullanıcı bizim tarafta kapattı)
     */
    private fun handleNotificationToggle(enable: Boolean) {
        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(context)

        if (enable) {
            when {
                // Android 13+ ve POST_NOTIFICATIONS izni yok
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission -> {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // Daha önce reddedip "bir daha sorma" demediyse
                        showNotificationsBottomSheet()
                    } else {
                        // Direkt izin iste
                        NotificationHelper.requestPermission(requireActivity())
                    }
                }

                // İzin var ama sistem bildirimleri app için kapalı
                hasPermission && !systemEnabled -> {
                    showNotificationsBottomSheet()
                    // Şu an fiilen kapalı olduğu için switch’i tekrar kapat
                    updatingNotificationSwitch = true
                    binding.switchNotifications.isChecked = false
                    updatingNotificationSwitch = false
                    // DataStore’u da false’ta bırakıyoruz, syncNotificationWithSystem zaten bunu korur
                }

                // Her şey zaten açık → direkt DataStore = true
                else -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.updateNotificationsEnabled(true)
                    }
                }
            }
        } else {
            // Kullanıcı bizim içerden kapattı → DataStore = false
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(false)
            }
        }
    }

    private fun showNotificationsBottomSheet() {
        NotificationsBottomSheet(NotificationsBottomSheet.PermissionType.NOTIFICATION)
            .show(parentFragmentManager, "notifications_bottom_sheet")
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

            if (granted) {
                // İzin verildiyse bir de sistem bildirim ayarına bakalım
                syncNotificationWithSystem()
            } else {
                // Reddedildiyse switch’i kapat + DataStore = false
                updatingNotificationSwitch = true
                binding.switchNotifications.isChecked = false
                updatingNotificationSwitch = false

                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.updateNotificationsEnabled(false)
                }

                showNotificationsBottomSheet()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}