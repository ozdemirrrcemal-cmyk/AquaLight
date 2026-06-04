package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
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
        "Lighting"
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightBinding.bind(view)

        setupHeader()
        setupClicks()
    }

    private fun setupHeader() {
    binding.appHeader.setupAquaHeader(
        AquaHeaderConfig(
            title = "WRGB Pro",
            showBackButton = true,
            onBackClick = {
                findNavController().popBackStack()
            },
            actions = listOf(
                AquaHeaderAction(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "Light settings",
                    onClick = {
                        findNavController().navigate(
                            R.id.action_deviceLightFragment_to_deviceLightSettingsFragment
                        )
                    }
                ),
                AquaHeaderAction(
                    iconRes = R.drawable.ic_more_vert,
                    contentDescription = "More options",
                    onClick = {
                        Toast.makeText(
                            requireContext(),
                            "More options",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            )
        )
    )
}

    private fun setupClicks() {
        binding.cardManual.setOnClickListener {
            findNavController().navigate(
                R.id.action_deviceLightFragment_to_deviceLightManualFragment
            )
        }

        binding.cardPrograms.setOnClickListener {
            findNavController().navigate(
                R.id.action_deviceLightFragment_to_deviceLightProgramsFragment
            )
        }

        binding.cardQuickSetup.setOnClickListener {
            Toast.makeText(requireContext(), "Quick Setup", Toast.LENGTH_SHORT).show()
        }

        binding.cardPresets.setOnClickListener {
            findNavController().navigate(
                R.id.action_deviceLightFragment_to_deviceLightPresetsFragment
            )
        }
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