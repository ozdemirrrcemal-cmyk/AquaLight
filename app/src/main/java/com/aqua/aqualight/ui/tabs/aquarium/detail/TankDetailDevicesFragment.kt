package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding

class TankDetailDevicesFragment :
    Fragment(R.layout.fragment_tank_detail_devices) {

    interface Host {
        fun onTankDetailAddDeviceClicked(
            tankId: Long
        )
    }

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private var tankId: Long = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailDevicesBinding.bind(view)

        setupClickListeners()
        renderShellState()
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            parentHost()?.onTankDetailAddDeviceClicked(
                tankId = tankId
            )
        }
    }

    private fun renderShellState() {
        binding.cardDevicesEmpty.isVisible = true
    }

    private fun parentHost(): Host? {
        return parentFragment as? Host
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {

        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
