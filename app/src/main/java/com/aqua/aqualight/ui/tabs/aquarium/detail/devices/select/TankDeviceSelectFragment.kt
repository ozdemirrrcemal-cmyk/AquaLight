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
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import kotlinx.coroutines.launch

class TankDeviceSelectFragment :
    Fragment(R.layout.fragment_tank_device_select) {

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
        get() = requireArguments().getLong(
            ARG_TANK_ID
        )

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

        setupHeader()
        setupRecycler()
        observeSavedDevices()
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

    private fun observeSavedDevices() {
        renderDevices(
            items = emptyList()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                devicesStore.unassignedDevicesFlow.collect { devices ->

                    val items =
                        devices.map { device ->
                            device.toSelectItem()
                        }

                    renderDevices(
                        items = items
                    )
                }
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.toSelectItem(): TankDeviceSelectItem {
        return TankDeviceSelectItem(
            deviceId = id,
            title = buildDeviceTitle(),
            serialNumber = serial.trim(),
            iconRes = DeviceIconMapper.iconFor(
                deviceType
            ),
            isOnline = buildDeviceOnlineState()
        )
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.buildDeviceTitle(): String {
        return aquaName
            .ifBlank {
                name
            }
            .ifBlank {
                productModel
            }
            .ifBlank {
                productFamily
            }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.buildDeviceOnlineState(): Boolean {
        return ip.isNotBlank()
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