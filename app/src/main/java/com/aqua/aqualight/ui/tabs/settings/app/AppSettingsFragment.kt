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

            // 🔔 Notifications – SAYFA AÇMIYOR
            // Burayı sonradan DataStore / ViewModel ile bağlayacaksın
            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                // TODO: notifications ayarını kaydet
            }

            // 🌐 Auto Update – SAYFA AÇMIYOR
            switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
                // TODO: auto update ayarını kaydet
            }

            // 🌙 Theme Mode – ayrı ekrana gider
            cardThemeMode.setOnClickListener {
                findNavController().navigate(R.id.themeModeFragment)
            }

            // 🌍 Language – ayrı ekrana gider
            cardLanguage.setOnClickListener {
                findNavController().navigate(R.id.languageSettingsFragment)
            }

            // ℹ️ About – ayrı ekrana gider
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