package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceDosingBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceDosingFragment : Fragment(R.layout.fragment_device_dosing) {

    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                "Dosing"
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceDosingBinding.bind(view)

        setupHeader()
        bindEmptyState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = deviceTitle,
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun bindEmptyState() {
        binding.tvEmptyTitle.text = deviceTitle
        binding.tvEmptyMessage.text =
            "Device ID: $deviceId\nDosing controller screen will be built here."
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_TITLE = "deviceTitle"
    }
}