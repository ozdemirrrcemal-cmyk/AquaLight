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
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentDeviceStatusBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DeviceStatusFragment : Fragment(R.layout.fragment_device_status) {

    private var _binding: FragmentDeviceStatusBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var adapter: DeviceStatusAdapter

    private var latestDevices: List<DevicesDataStoreManager.DeviceInfoUi> = emptyList()
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

        val now =
            System.currentTimeMillis()

        val uiList =
            latestDevices.map { device ->
                val statusState =
                    latestStatuses[device.id]

                val lastSeenMillis =
                    statusState?.lastSeenMillis
                        ?: device.lastSeenMillis

                val online =
                    statusState?.isOnline == true

                val lastSeenText =
                    if (lastSeenMillis > 0L) {
                        formatElapsedTime(
                            deltaMs = now - lastSeenMillis
                        )
                    } else {
                        "Never"
                    }

                val definition =
                    AquaDeviceCatalog.findByType(
                        type = device.deviceType
                    )

                val displayName =
                    definition?.displayName
                        ?: device.name.ifBlank {
                            device.productModel.ifBlank {
                                "Device"
                            }
                        }

                val familyName =
                    definition?.family?.displayName
                        ?: device.productFamily.ifBlank {
                            device.aquaName.ifBlank {
                                "Unknown"
                            }
                        }

                DeviceCardUi(
                    id = device.id,
                    displayName = displayName,
                    familyName = familyName,
                    tankName = getTankNameForDevice(
                        device
                    ),
                    ip = statusState?.ip ?: device.ip,
                    serial = device.serial,
                    firmwareBuild = device.firmwareBuild,
                    isOnline = online,
                    lastSeenText = lastSeenText,
                    deviceType = device.deviceType
                )
            }

        adapter.submitList(
            uiList
        )
    }

    private fun getTankNameForDevice(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val connectedTankId =
            device.tankId ?: return "Not connected"

        return latestTanks.firstOrNull { tank ->
            tank.id == connectedTankId
        }?.name ?: "Unknown aquarium"
    }

    private fun formatElapsedTime(
        deltaMs: Long
    ): String {
        val minutes =
            TimeUnit.MILLISECONDS.toMinutes(
                deltaMs
            )

        val hours =
            TimeUnit.MILLISECONDS.toHours(
                deltaMs
            )

        val days =
            TimeUnit.MILLISECONDS.toDays(
                deltaMs
            )

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            else -> "$days d ago"
        }
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}