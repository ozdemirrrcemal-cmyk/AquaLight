package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding

class SecuritySettingsFragment : Fragment(R.layout.fragment_security_settings) {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSecuritySettingsBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // İleride buraya güvenlik ayarları (2FA, login history vs.) eklersin:
        // binding.rowTwoFactor.setOnClickListener { ... }
        // binding.rowLoginAlerts.setOnClickListener { ... }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}