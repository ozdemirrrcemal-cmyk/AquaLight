package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentOpenSourceLicensesBinding

class OpenSourceLicensesFragment : Fragment(R.layout.fragment_open_source_licenses) {

    private var _binding: FragmentOpenSourceLicensesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOpenSourceLicensesBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        // Şimdilik içerik statik; ileride istersen dinamik liste yaparız.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}