package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAutoUpdateSettingsBinding

class AutoUpdateSettingsFragment : Fragment(R.layout.fragment_auto_update_settings) {

    private var _binding: FragmentAutoUpdateSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAutoUpdateSettingsBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Otomatik güncelleme seçeneklerini ileride buraya bağlarsın
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}