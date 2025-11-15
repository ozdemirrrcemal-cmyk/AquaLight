package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentThemeModeBinding

class ThemeModeFragment : Fragment(R.layout.fragment_theme_mode) {

    private var _binding: FragmentThemeModeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentThemeModeBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Buraya: light / dark / system seçimlerini vs. ekleyebilirsin
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}