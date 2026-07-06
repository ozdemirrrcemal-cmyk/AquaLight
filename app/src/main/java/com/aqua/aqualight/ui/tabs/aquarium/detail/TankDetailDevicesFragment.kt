package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.AlertDialog
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
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceItem
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesEvent
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesUiState
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmBottomSheet
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmTone
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import kotlinx.coroutines.launch

class TankDetailDevicesFragment : Fragment(R.layout.fragment_tank_detail_devices) {

    interface Host {
        fun onTankDetailAddDeviceClicked(
            tankId: Long
        )

        fun onTankDetailDeviceClicked(
            route: DeviceRoute
        )
    }

    private val viewModel: TankDetailDevicesViewModel by viewModels()

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankAssignedDevicesAdapter

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

        setupRecycler()
        setupClickListeners()
        observeViewModel()

        viewModel.bind(tankId)
    }

    private fun setupRecycler() {
        adapter = TankAssignedDevicesAdapter(
            onDeviceClick = { item ->
                viewModel.onDeviceClicked(item.deviceUid)
            },
            onDeviceLongClick = { item ->
                confirmRemoveDevice(item)
            }
        )

        binding.rvAssignedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssignedDevices.adapter = adapter
        binding.rvAssignedDevices.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            parentHost()?.onTankDetailAddDeviceClicked(
                tankId = tankId
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is TankDetailDevicesEvent.OpenDeviceRoute -> {
                                parentHost()?.onTankDetailDeviceClicked(event.route)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(
        state: TankDetailDevicesUiState
    ) {
        adapter.submitList(state.devices)
        binding.rvAssignedDevices.isVisible = state.isEmpty.not()
        binding.cardDevicesEmpty.isVisible = state.isEmpty
    }

    private fun confirmRemoveDevice(
        item: TankAssignedDeviceItem
    ) {
        DeviceConfirmBottomSheet
            .create(requireContext())
            .show(
                title = getString(R.string.aquarium_remove_device_title),
                message = getString(R.string.aquarium_remove_device_message, item.title),
                confirmText = getString(R.string.aquarium_remove_action),
                cancelText = getString(R.string.common_cancel),
                tone = DeviceConfirmTone.DANGER,
                onConfirm = {
                    viewModel.removeDeviceFromTank(item.deviceUid)
                }
            )
    }

    private fun parentHost(): Host? {
        return parentFragment as? Host
    }

    override fun onDestroyView() {
        binding.rvAssignedDevices.adapter = null
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
