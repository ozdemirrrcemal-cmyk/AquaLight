package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceUi
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankLightChannelKey
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankLightChannelUi
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectFragment
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TankDetailDevicesFragment :
    Fragment(R.layout.fragment_tank_detail_devices) {

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
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

        devicesStore =
            DevicesDataStoreManager.create(
                requireContext()
            )

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupRecyclerView()
        setupClickListeners()
        observeTankDevices()
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
            openTankDeviceSelectScreen()
        }
    }

    private fun observeTankDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    devicesStore.devicesForTankFlow(
                        tankId
                    ),
                    DevicePresenceMonitor.statuses
                ) { devices, statuses ->
                    devices to statuses
                }.collect { pair ->

                    renderDevices(
                        devices = pair.first,
                        statuses = pair.second
                    )
                }
            }
        }
    }

    private fun renderDevices(
        devices: List<DevicesDataStoreManager.DeviceInfoUi>,
        statuses: Map<Long, DeviceStatusState>
    ) {
        if (_binding == null) {
            return
        }

        val now =
            System.currentTimeMillis()

        val items =
            devices.map { device ->
                device.toAssignedDeviceUi(
                    statuses = statuses,
                    now = now
                )
            }

        adapter.submitList(
            items
        )

        binding.cardDevicesEmpty.isVisible =
            items.isEmpty()

        binding.rvTankDevices.isVisible =
            items.isNotEmpty()
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.toAssignedDeviceUi(
        statuses: Map<Long, DeviceStatusState>,
        now: Long
    ): TankAssignedDeviceUi {
        val title =
            getDeviceTitle(
                device = this
            )

        val subtitle =
            getDeviceTypeText(
                device = this
            )

        val online =
            isDeviceOnline(
                device = this,
                statuses = statuses,
                now = now
            )

        val iconRes =
            DeviceIconMapper.iconFor(
                deviceType
            )

        return if (isLightDevice()) {
            TankAssignedDeviceUi.Light(
                deviceId = id,
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                isOnline = online,
                programName = "Program data pending",
                startTimeText = "--:--",
                endTimeText = "--:--",
                outputPercent = 0,
				timelineProgressPercent = 0,
                channels = buildLightChannels()
            )
        } else {
            TankAssignedDeviceUi.Generic(
                deviceId = id,
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                isOnline = online
            )
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.isLightDevice(): Boolean {
        val definition =
            AquaDeviceCatalog.findByType(
                type = deviceType
            )

        val rawText =
            listOf(
                deviceType.storageKey,
                definition?.displayName.orEmpty(),
                definition?.family?.displayName.orEmpty(),
                name,
                productModel,
                productFamily,
                aquaName
            )
                .joinToString(
                    separator = " "
                )
                .lowercase()

        return rawText.contains(
            "light"
        ) || rawText.contains(
            "wrgb"
        ) || rawText.contains(
            "rgb"
        )
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.buildLightChannels(): List<TankLightChannelUi> {
        val rawText =
            listOf(
                deviceType.storageKey,
                name,
                productModel,
                productFamily,
                aquaName
            )
                .joinToString(
                    separator = " "
                )
                .lowercase()

        return when {
            rawText.contains("wrgb") -> {
                listOf(
                    createWhiteChannel(),
                    createRedChannel(),
                    createGreenChannel(),
                    createBlueChannel()
                )
            }

            rawText.contains("rgb") -> {
                listOf(
                    createRedChannel(),
                    createGreenChannel(),
                    createBlueChannel()
                )
            }

            else -> {
                listOf(
                    createIntensityChannel()
                )
            }
        }
    }

    private fun createWhiteChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = TankLightChannelKey.WHITE,
            label = "White",
            currentPercent = 0,
            targetPercent = 0,
            colorInt = Color.parseColor("#D8DDE4")
        )
    }

    private fun createRedChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = TankLightChannelKey.RED,
            label = "Red",
            currentPercent = 0,
            targetPercent = 0,
            colorInt = Color.parseColor("#D16D6D")
        )
    }

    private fun createGreenChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = TankLightChannelKey.GREEN,
            label = "Green",
            currentPercent = 0,
            targetPercent = 0,
            colorInt = Color.parseColor("#72B77D")
        )
    }

    private fun createBlueChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = TankLightChannelKey.BLUE,
            label = "Blue",
            currentPercent = 0,
            targetPercent = 0,
            colorInt = Color.parseColor("#6D97D1")
        )
    }

    private fun createIntensityChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = TankLightChannelKey.INTENSITY,
            label = "Intensity",
            currentPercent = 0,
            targetPercent = 0,
            colorInt = Color.parseColor("#8EB8FF")
        )
    }

    private fun handleDeviceClick(
        device: TankAssignedDeviceUi
    ) {
        // Sonraki adımda cihaz detayına/router'a açacağız.
    }

    private fun handleDeviceLongClick(
        device: TankAssignedDeviceUi
    ) {
        // Sonraki adımda Remove / Settings bottom sheet burada açılacak.
    }

    private fun openTankDeviceSelectScreen() {
        val args =
            Bundle().apply {
                putLong(
                    TankDeviceSelectFragment.ARG_TANK_ID,
                    tankId
                )
            }

        findNavController().navigate(
            R.id.action_tankDetailFragment_to_tankDeviceSelectFragment,
            args
        )
    }

    private fun getDeviceTitle(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

        return definition?.displayName
            ?: device.name.ifBlank {
                device.productModel.ifBlank {
                    "Device"
                }
            }
    }

    private fun getDeviceTypeText(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

        return definition?.family?.displayName
            ?: device.productFamily.ifBlank {
                device.aquaName.ifBlank {
                    "Device"
                }
            }
    }

    private fun isDeviceOnline(
        device: DevicesDataStoreManager.DeviceInfoUi,
        statuses: Map<Long, DeviceStatusState>,
        now: Long
    ): Boolean {
        val statusState =
            statuses[device.id]

        return statusState?.isOnline ?: (
            device.lastSeenMillis > 0L &&
                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
            )
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

        private const val ONLINE_TIMEOUT_MS =
            90_000L

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