package com.aqua.aqualight.ui.tabs.devices.setup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceSetupBinding

class DeviceSetupFragment : Fragment(R.layout.fragment_device_setup) {

    private var _binding: FragmentDeviceSetupBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceSetupBinding.bind(view)

        val displayName = requireArguments().getString(
            "displayName",
            "Device"
        )

        val familyName = requireArguments().getString(
            "familyName",
            "Aqua device"
        )

        val setupSsid = requireArguments().getString(
            "setupSsid",
            ""
        )

        binding.tvTitle.text = displayName
        binding.tvSubtitle.text = familyName
        binding.tvDescription.text = "Ready to connect to $setupSsid"

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}