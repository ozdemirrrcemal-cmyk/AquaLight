package com.aqua.aqualight.ui.tabs.devices

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var adapter: DevicesListAdapter

    private var latestDevices: List<DevicesDataStoreManager.DeviceInfo> = emptyList()
    private var latestTanks: List<SavedAquariumTank> = emptyList()
    private var latestStatuses: Map<Long, DeviceStatusState> = emptyMap()

    private var selectionMode: Boolean = false
    private var isDeletingDevices: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDevicesBinding.bind(view)

        devicesStore =
            DevicesDataStoreManager.create(
                requireContext()
            )

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupHeader()
        setupRecyclerView()
        observeTanks()
        observeDevicesList()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = if (selectionMode) {
                        "Delete"
                    } else {
                        "+ Add"
                    },
                    contentDescription = if (selectionMode) {
                        "Delete selected devices"
                    } else {
                        getString(
                            R.string.devices_scan_button_desc
                        )
                    },
                    onClick = {
                        if (selectionMode) {
                            showDeleteConfirmDialog()
                        } else {
                            openAddDeviceScreen()
                        }
                    }
                )
            )
        )

        applyPrimaryActionStyle()
    }

    private fun setupRecyclerView() {
        adapter =
            DevicesListAdapter(
                onSelectionModeStart = {
                    enterSelectionMode()
                },
                onSelectionChanged = { count ->
                    if (count == 0) {
                        exitSelectionMode()
                    }
                },
                onDeviceClick = { device ->
                    openDeviceMenu(
                        device = device
                    )
                }
            )

        binding.rvSelectedDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvSelectedDevices.adapter =
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

    private fun observeDevicesList() {
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

        if (latestDevices.isEmpty()) {
            exitSelectionMode()

            binding.tvEmptyState.visibility =
                View.VISIBLE

            binding.rvSelectedDevices.visibility =
                View.GONE

            adapter.submitList(
                emptyList()
            )

            return
        }

        binding.tvEmptyState.visibility =
            View.GONE

        binding.rvSelectedDevices.visibility =
            View.VISIBLE

        val uiList =
            latestDevices.map { device ->
                val presenceState =
                    latestStatuses[device.id]

                val online =
                    presenceState?.isOnline == true

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
                        device = device
                    ),
                    ip = presenceState?.ip ?: device.ip,
                    serial = device.serial,
                    firmwareBuild = device.firmwareBuild,
                    isOnline = online,
                    lastSeenText = "",
                    deviceType = device.deviceType
                )
            }

        adapter.submitList(
            uiList
        )
    }

    private fun getTankNameForDevice(
        device: DevicesDataStoreManager.DeviceInfo
    ): String {
        val connectedTankId =
            device.tankId ?: return ""

        return latestTanks.firstOrNull { tank ->
            tank.id == connectedTankId
        }?.name ?: "Unknown aquarium"
    }

    private fun openAddDeviceScreen() {
        findNavController().navigate(
            DevicesFragmentDirections.actionDevicesFragmentToDeviceAddFragment()
        )
    }

    private fun openDeviceMenu(
        device: DeviceCardUi
    ) {
        findNavController().navigate(
            DevicesFragmentDirections.actionDevicesFragmentToDeviceRouterFragment(
                deviceId = device.id,
                deviceIp = device.ip
            )
        )
    }

    private fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode =
                true

            updateActionButtonUi()
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode =
                false

            adapter.exitSelectionMode()

            updateActionButtonUi()
        }
    }

    private fun updateActionButtonUi() {
        if (_binding == null) {
            return
        }

        setupHeader()
    }

    private fun applyPrimaryActionStyle() {
        val button =
            binding.appHeader.btnPrimaryAction

        if (selectionMode) {
            button.setTextColor(
                Color.parseColor("#FF8A8A")
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#321E2A")
                )

            button.strokeWidth =
                1.dp()

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.parseColor("#7A3344")
                )
        } else {
            button.setTextColor(
                Color.WHITE
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#1C3252")
                )

            button.strokeWidth =
                0

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.TRANSPARENT
                )
        }
    }

    private fun showDeleteConfirmDialog() {
        if (isDeletingDevices) {
            return
        }

        val ids =
            adapter.getSelectedIds()

        if (ids.isEmpty()) {
            exitSelectionMode()
            return
        }

        val count =
            ids.size

        val title =
            if (count == 1) {
                getString(
                    R.string.devices_delete_title_single
                )
            } else {
                getString(
                    R.string.devices_delete_title_multi,
                    count
                )
            }

        val message =
            if (count == 1) {
                getString(
                    R.string.devices_delete_message_single
                )
            } else {
                getString(
                    R.string.devices_delete_message_multi,
                    count
                )
            }

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            onConfirm = {
                deleteSelectedDevices(
                    ids = ids
                )
            },
            onCancel = {
                exitSelectionMode()
            }
        )
    }

    private fun deleteSelectedDevices(
        ids: Set<Long>
    ) {
        if (isDeletingDevices) {
            return
        }

        if (ids.isEmpty()) {
            exitSelectionMode()
            return
        }

        isDeletingDevices =
            true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(
                    show = true
                )

                devicesStore.deleteDevices(
                    ids = ids
                )

                exitSelectionMode()
            } catch (exception: Exception) {
                exception.printStackTrace()

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Delete Failed",
                    message = "Selected devices could not be deleted."
                )
            } finally {
                isDeletingDevices =
                    false

                showGlobalLoading(
                    show = false
                )
            }
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(
            show
        )
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroyView() {
        binding.rvSelectedDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}