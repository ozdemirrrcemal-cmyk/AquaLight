package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding
import kotlinx.coroutines.flow.collectLatest

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        observeThemeSummary()
        setupClicks()
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

    private fun setupClicks() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔔 Notifications
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            // TODO: DataStore ile kendi notification ayarını bağlayacaksın
        }

        // 🌐 Auto Update
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            // TODO: DataStore ile auto-update ayarını bağlayacaksın
        }

        // 🌙 Theme – bottom sheet aç
        cardThemeMode.setOnClickListener {
            ThemeBottomSheet().apply {
                onThemeChanged = {
                    // Flow ile zaten tvThemeSummary güncelleniyor,
                    // istersen ekstra bir şey yapmana gerek yok.
                }
            }.show(parentFragmentManager, "theme_bottom_sheet")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}