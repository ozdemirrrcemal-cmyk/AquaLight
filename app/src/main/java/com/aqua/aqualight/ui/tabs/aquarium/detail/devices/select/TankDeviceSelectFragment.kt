package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class TankDeviceSelectFragment :
    Fragment(R.layout.fragment_tank_device_select) {

    private val args: TankDeviceSelectFragmentArgs by navArgs()

    private var _binding: FragmentTankDeviceSelectBinding? = null
    private val binding get() = _binding!!

    private val tankId: Long
        get() = args.tankId

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDeviceSelectBinding.bind(view)

        setupHeader()
        renderShellState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.tank_device_select_title)
            )
        )
    }

    private fun renderShellState() {
        binding.tvEmptyState.text = getString(
            R.string.tank_device_select_empty_subtitle
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {

        const val ARG_TANK_ID = "tankId"

        const val RESULT_SELECTED_DEVICE_ID = "tankDeviceSelectResultDeviceId"

        const val RESULT_SELECTED_TANK_ID = "tankDeviceSelectResultTankId"
    }
}
