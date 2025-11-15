package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAppSettingsBinding

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppSettingsBinding.bind(view)

        with(binding) {
            // 🔙 Geri
            btnBack.setOnClickListener {
                findNavController().popBackStack()
            }

            // 🔔 Notifications
            cardNotifications.setOnClickListener {
                findNavController().navigate(R.id.notificationsSettingsFragment)
            }

            // 🌐 Auto Update
            cardAutoUpdate.setOnClickListener {
                findNavController().navigate(R.id.autoUpdateSettingsFragment)
            }

            // 🌙 Theme Mode
            cardThemeMode.setOnClickListener {
                findNavController().navigate(R.id.themeModeFragment)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}