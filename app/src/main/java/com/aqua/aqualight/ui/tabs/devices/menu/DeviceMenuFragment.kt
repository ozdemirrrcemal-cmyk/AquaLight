package com.aqua.aqualight.ui.tabs.devices.menu

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceMenuBinding

class DeviceMenuFragment : Fragment(R.layout.fragment_device_menu) {

    private var _binding: FragmentDeviceMenuBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceMenuBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvTitle.text = "Device Menu"
        binding.tvDeviceInfo.text = "Selected device screen"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}