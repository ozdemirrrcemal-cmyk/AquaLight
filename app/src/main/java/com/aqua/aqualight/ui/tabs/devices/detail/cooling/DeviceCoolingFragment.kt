package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceCoolingFragment : Fragment(R.layout.fragment_device_cooling) {

    private var _binding: FragmentDeviceCoolingBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                "Cooling"
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceCoolingBinding.bind(view)

        setupHeader()
        bindEmptyState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = deviceTitle
            )
        )
    }

    private fun bindEmptyState() {
        binding.tvEmptyTitle.text =
            deviceTitle

        binding.tvEmptyMessage.text =
            "Device ID: $deviceId\nCooling controller screen will be built here."
    }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_TITLE = "deviceTitle"
    }
}