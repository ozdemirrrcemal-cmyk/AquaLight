package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceUi
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmBottomSheet
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmTone
import kotlinx.coroutines.launch

class TankDetailDevicesFragment :
    Fragment(R.layout.fragment_tank_detail_devices) {

    interface Host {
        fun onTankDetailAddDeviceClicked(
            tankId: Long
        )

        fun onTankDetailDeviceClicked(
            device: TankAssignedDeviceUi
        )
    }

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TankDetailDevicesViewModel by viewModels()

    private lateinit var adapter: TankDetailDevicesAdapter

    private var tankId: Long =
        0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        tankId =
            requireArguments().getLong(
                ARG_TANK_ID
            )
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
            FragmentTankDetailDevicesBinding.bind(view)

        setupRecyclerView()
        setupClickListeners()
        observeUiState()

        viewModel.initialize(
            tankId = tankId
        )
    }

    private fun setupRecyclerView() {
        adapter =
            TankDetailDevicesAdapter(
                onDeviceClick = { device ->
                    handleDeviceClick(
                        device = device
                    )
                },
                onDeviceLongClick = { device ->
                    handleDeviceLongClick(
                        device = device
                    )
                }
            )

        binding.rvTankDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvTankDevices.adapter =
            adapter
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            parentHost()?.onTankDetailAddDeviceClicked(
                tankId = tankId
            )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(
                        state.devices
                    )

                    binding.cardDevicesEmpty.isVisible =
                        state.devices.isEmpty()

                    binding.rvTankDevices.isVisible =
                        state.devices.isNotEmpty()
                }
            }
        }
    }

    private fun handleDeviceClick(
        device: TankAssignedDeviceUi
    ) {
        parentHost()?.onTankDetailDeviceClicked(
            device = device
        )
    }

    private fun handleDeviceLongClick(
        device: TankAssignedDeviceUi
    ) {
        showRemoveDeviceFromTankSheet(
            device = device
        )
    }

    private fun showRemoveDeviceFromTankSheet(
        device: TankAssignedDeviceUi
    ) {
        DeviceConfirmBottomSheet
            .create(requireContext())
            .show(
                title = "Remove device from tank?",
                message = "${device.title} will remain saved, but it will no longer be assigned to this tank.",
                confirmText = "Remove",
                cancelText = "Cancel",
                tone = DeviceConfirmTone.DANGER,
                onConfirm = {
                    viewModel.removeDeviceFromTank(
                        deviceId = device.deviceId
                    )
                }
            )
    }

    private fun parentHost(): Host? {
        return parentFragment as? Host
    }

    override fun onDestroyView() {
        binding.rvTankDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }

    companion object {

        private const val ARG_TANK_ID =
            "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong(
                            ARG_TANK_ID,
                            tankId
                        )
                    }
            }
        }
    }
}