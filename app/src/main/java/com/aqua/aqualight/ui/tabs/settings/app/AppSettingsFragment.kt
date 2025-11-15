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

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔔 Notifications
        // binding.switchNotifications.setOnCheckedChangeListener { _, isChecked -> ... }

        // 🌐 Auto update
        // binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked -> ... }

        // 🌙 Theme card
        // binding.cardThemeMode.setOnClickListener { ... }

        // 🌍 Language card
        // binding.cardLanguage.setOnClickListener { ... }

        // ℹ️ About card
        // binding.cardAbout.setOnClickListener { ... }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}