package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceLightFragment : Fragment(R.layout.fragment_device_light) {

    private var _binding: FragmentDeviceLightBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                getString(R.string.light_default_device_title)
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightBinding.bind(view)

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
            "Device ID: $deviceId\nLight controller screen will be built here."
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