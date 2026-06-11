package com.aqua.aqualight.ui.tabs.settings.device

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentDeviceStatusBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceStatusFragment : Fragment(R.layout.fragment_device_status) {

    private var _binding: FragmentDeviceStatusBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var adapter: DeviceStatusAdapter

    private val deviceCardStateMapper =
        DeviceCardStateMapper()

    private var latestDevices: List<DevicesDataStoreManager.DeviceInfo> = emptyList()
    private var latestTanks: List<SavedAquariumTank> = emptyList()
    private var latestStatuses: Map<Long, DeviceStatusState> = emptyMap()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceStatusBinding.bind(view)

        devicesStore =
            DevicesDataStoreManager.create(
                requireContext()
            )

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupHeader()
        setupRecycler()
        observeTanks()
        observeDevices()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupRecycler() {
        adapter =
            DeviceStatusAdapter()

        binding.rvDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvDevices.adapter =
            adapter
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(
            viewLifecycleOwner
        ) { tanks ->
            latestTanks =
                tanks

            renderDevices()
        }
    }

    private fun observeDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    devicesStore.devicesFlow,
                    DevicePresenceMonitor.statuses
                ) { devices, statuses ->
                    devices to statuses
                }.collect { pair ->
                    latestDevices =
                        pair.first

                    latestStatuses =
                        pair.second

                    renderDevices()
                }
            }
        }
    }

    private fun renderDevices() {
        if (_binding == null) {
            return
        }

        binding.rvDevices.isVisible =
            latestDevices.isNotEmpty()

        if (latestDevices.isEmpty()) {
            adapter.submitList(
                emptyList()
            )

            return
        }

        val uiList =
            deviceCardStateMapper.mapAll(
                devices = latestDevices,
                statuses = latestStatuses,
                tanks = latestTanks,
                unassignedTankText = "Not connected",
                unknownTankText = "Unknown aquarium",
                includeLastSeenText = true
            ).map { cardState ->
                DeviceCardUi(
                    id = cardState.deviceId,
                    displayName = cardState.title,
                    familyName = cardState.familyName,
                    tankName = cardState.tankName,
                    ip = cardState.ip,
                    serial = cardState.serial,
                    firmwareBuild = cardState.firmwareBuild,
                    isOnline = cardState.isOnline,
                    lastSeenText = cardState.lastSeenText,
                    deviceType = cardState.deviceType
                )
            }

        adapter.submitList(
            uiList
        )
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}