package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class TankDeviceSelectFragment :
    Fragment(R.layout.fragment_tank_device_select) {

    private val args: TankDeviceSelectFragmentArgs by navArgs()


    private var _binding: FragmentTankDeviceSelectBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankDeviceSelectAdapter

    private val devicesStore by lazy {
        DevicesDataStoreManager.create(
            requireContext()
        )
    }

    private var isAssigning =
        false

    private val tankId: Long
        get() = args.tankId

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankDeviceSelectBinding.bind(view)

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupHeader()
        setupRecycler()
        observeAvailableDevices()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(
                    R.string.tank_device_select_title
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter =
            TankDeviceSelectAdapter { item ->
                assignDeviceToTank(
                    item = item
                )
            }

        binding.rvDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvDevices.adapter =
            adapter
    }

    private fun observeAvailableDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    devicesStore.unassignedDevicesFlow,
                    DevicePresenceMonitor.statuses
                ) { devices, statuses ->
                    devices to statuses
                }.collect { pair ->

                    val devices =
                        pair.first

                    val statuses =
                        pair.second

                    val items =
                        devices.map { device ->
                            device.toSelectItem(
                                statuses = statuses
                            )
                        }

                    renderDevices(
                        items = items
                    )
                }
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfo.toSelectItem(
        statuses: Map<Long, DeviceStatusState>
    ): TankDeviceSelectItem {
        val online =
            statuses[id]?.isOnline == true

        return TankDeviceSelectItem(
            deviceId = id,
            title = buildDeviceTitle(),
            serialNumber = serial.trim(),
            iconRes = DeviceIconMapper.iconFor(
                deviceType
            ),
            isOnline = online
        )
    }

    private fun DevicesDataStoreManager.DeviceInfo.buildDeviceTitle(): String {
        val catalogName =
            AquaDeviceCatalog.findByType(
                type = deviceType
            )?.displayName.orEmpty()

        return name
            .ifBlank {
                productModel
            }
            .ifBlank {
                catalogName
            }
            .ifBlank {
                aquaName
            }
            .ifBlank {
                productFamily
            }
    }

    private fun renderDevices(
        items: List<TankDeviceSelectItem>
    ) {
        adapter.submitList(
            items
        )

        binding.rvDevices.isVisible =
            items.isNotEmpty()

        binding.tvEmptyState.isVisible =
            items.isEmpty()
    }

    private fun assignDeviceToTank(
        item: TankDeviceSelectItem
    ) {
        if (isAssigning) {
            return
        }

        isAssigning =
            true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                devicesStore.assignDeviceToTank(
                    deviceId = item.deviceId,
                    tankId = tankId
                )

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        RESULT_SELECTED_DEVICE_ID,
                        item.deviceId
                    )

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        RESULT_SELECTED_TANK_ID,
                        tankId
                    )

                findNavController()
                    .popBackStack()
            } catch (exception: Exception) {
                exception.printStackTrace()

                isAssigning =
                    false

                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(
                        R.string.tank_device_select_assign_error
                    ),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }

    companion object {

        const val ARG_TANK_ID =
            "tankId"

        const val RESULT_SELECTED_DEVICE_ID =
            "tankDeviceSelectResultDeviceId"

        const val RESULT_SELECTED_TANK_ID =
            "tankDeviceSelectResultTankId"
    }
}
