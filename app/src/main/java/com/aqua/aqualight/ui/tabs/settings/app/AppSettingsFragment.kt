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

    // Programatik set sırasında listener tetiklenmesin diye
    private var changingNotificationSwitchProgrammatically = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        observeThemeSummary()
        observeLanguageSummary()
        observeAutoUpdateState()
        setupClicks()

        // İlk açılışta: DataStore + sistem durumuna göre switch’i ayarla
        refreshNotificationSwitchState()
    }

    override fun onResume() {
        super.onResume()
        // Sistem bildirim ayarından dönünce duruma göre switch’i senkronla
        refreshNotificationSwitchState()
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

        // 🔔 Notifications toggle
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (changingNotificationSwitchProgrammatically) return@setOnCheckedChangeListener
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
     * Eski `updateNotificationSwitchState`'in DataStore versiyonu:
     * switch = appEnabled && hasPermission && systemEnabled
     */
    private fun refreshNotificationSwitchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()

            // DataStore’daki app içi tercih
            val appEnabled = userPrefs.notificationsEnabled.first()

            // Sistem durumu
            val hasPermission = NotificationHelper.hasSystemPermission(ctx)
            val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

            val effectiveChecked = appEnabled && hasPermission && systemEnabled
            setNotificationSwitchChecked(effectiveChecked)
        }
    }

    // Switch’i programatik değiştirirken listener tetiklenmesin diye helper
    private fun setNotificationSwitchChecked(checked: Boolean) {
        changingNotificationSwitchProgrammatically = true
        binding.switchNotifications.isChecked = checked
        changingNotificationSwitchProgrammatically = false
    }

    /**
     * Eski mantığa bire bir yakın akış:
     *
     * - Kullanıcı switch'i AÇARSA:
     *   1) İzin yoksa (Android 13+):
     *      - İlk defa → direkt runtime permission iste
     *      - Rationale gerekiyorsa → bottom sheet göster (sen ayarlardan aç)
     *      → Bu adımda DataStore'a dokunmuyoruz, switch anlık kullanıcı tıklamasıyla ON kalabilir;
     *        izin sonucu onRequestPermissionsResult ile senkronlanır.
     *
     *   2) İzin var ama sistem bildirimi kapalıysa:
     *      - BottomSheet → bildirim ayarlarına yönlendir
     *      - DataStore’a dokunmuyoruz. Dönüşte onResume + refresh ile
     *        systemEnabled’e göre switch güncellenecek.
     *
     *   3) Her şey yolundaysa (izin + sistem açık):
     *      - notificationsEnabled = true
     *      - switch = ON
     *
     * - Kullanıcı switch'i KAPATIRSA:
     *   - notificationsEnabled = false
     *   - switch = OFF
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
                        // Kullanıcı daha önce reddetmiş, açıklama göster
                        showNotificationsBottomSheet()
                    } else {
                        // İlk kez izin iste
                        NotificationHelper.requestPermission(requireActivity())
                    }
                    // ❗ Burada DataStore’a dokunmuyoruz, switch'i de elle geri almıyoruz.
                    // İzin sonucu onRequestPermissionsResult + refreshNotificationSwitchState ile
                    // gerçek duruma göre senkronlanacak.
                }

                // İzin var ama sistem bildirimi kapalı
                hasPermission && !systemEnabled -> {
                    // Eski kodda olduğu gibi sadece bottom sheet aç
                    showNotificationsBottomSheet()
                    // DataStore’a dokunma, switch de kullanıcı tıklamasıyla şimdilik ON kalabilir.
                    // Kullanıcı ayarlardan açmazsa, geri dönünce onResume + refresh ile OFF yapılacak.
                }

                // Her şey yolunda → kullanıcı gerçekten açtı
                else -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.updateNotificationsEnabled(true)
                    }
                    setNotificationSwitchChecked(true)
                }
            }
        } else {
            // Kullanıcı app içinden KAPATTI → bu net tercih
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.updateNotificationsEnabled(false)
            }
            setNotificationSwitchChecked(false)
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

            viewLifecycleOwner.lifecycleScope.launch {
                // Eski mantık: izin verildiyse otomatik aç kabul et
                userPrefs.updateNotificationsEnabled(granted)
            }
            // Sistem + DataStore’a göre switch’i tekrar senkronla
            refreshNotificationSwitchState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}