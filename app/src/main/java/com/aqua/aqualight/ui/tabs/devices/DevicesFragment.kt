package com.aqua.aqualight.ui.tabs.devices

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
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
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var adapter: DevicesListAdapter

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

        _binding = FragmentDevicesBinding.bind(view)
        devicesStore = DevicesDataStoreManager.create(requireContext())

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupRecyclerView()
        setupClickListeners()
        observeDevicesList()
        updateActionButtonUi()
    }

    private fun setupRecyclerView() {
        adapter = DevicesListAdapter(
            onSelectionModeStart = {
                enterSelectionMode()
            },
            onSelectionChanged = { count ->
                if (count == 0) {
                    exitSelectionMode()
                }
            },
            onDeviceClick = { device ->
                openDeviceMenu(device)
            }
        )

        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvSelectedDevices.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnScanDevices.setOnClickListener {
            if (selectionMode) {
                showDeleteConfirmDialog()
            } else {
                findNavController().navigate(
                    R.id.action_devicesFragment_to_scanDevicesFragment
                )
            }
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
                    val devices = pair.first
                    val statuses = pair.second

                    if (devices.isEmpty()) {
                        exitSelectionMode()

                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvSelectedDevices.visibility = View.GONE

                        adapter.submitList(emptyList())
                        return@collect
                    }

                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSelectedDevices.visibility = View.VISIBLE

                    val now = System.currentTimeMillis()

                    val uiList = devices.map { device ->
                        val presenceState = statuses[device.id]

                        val online = presenceState?.isOnline ?: (
                            device.lastSeenMillis > 0L &&
                                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
                            )

                        val definition = AquaDeviceCatalog.findByType(
                            type = device.deviceType
                        )

                        val displayName = definition?.displayName
                            ?: device.name.ifBlank { "Device" }

                        val familyName = definition?.family?.displayName
                            ?: device.aquaName.ifBlank { "Unknown" }

                        DeviceCardUi(
                            id = device.id,
                            displayName = displayName,
                            familyName = familyName,
                            tankName = "",
                            ip = presenceState?.ip ?: device.ip,
                            serial = device.serial,
                            firmwareBuild = device.firmwareBuild,
                            isOnline = online,
                            lastSeenText = "",
                            deviceType = device.deviceType
                        )
                    }

                    adapter.submitList(uiList)
                }
            }
        }
    }

    private fun openDeviceMenu(
        device: DeviceCardUi
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            showGlobalLoading(true)

            val status = try {
                DevicePresenceMonitor.checkDeviceNow(
                    context = requireContext(),
                    deviceId = device.id,
                    knownIp = device.ip
                )
            } finally {
                showGlobalLoading(false)
            }

            if (status?.isOnline != true) {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.WARNING,
                    title = getString(R.string.device_offline_title),
                    message = getString(R.string.device_offline_message)
                )
                return@launch
            }

            val args = Bundle().apply {
                putLong("deviceId", device.id)
                putString("deviceName", device.displayName)
                putString("deviceAquaName", device.familyName)
                putString("deviceIp", status.ip)
                putString("deviceSerial", device.serial)
                putBoolean("deviceOnline", true)
            }

            findNavController().navigate(
                R.id.action_devicesFragment_to_deviceMenuFragment,
                args
            )
        }
    }

    private fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true
            updateActionButtonUi()
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            adapter.exitSelectionMode()
            updateActionButtonUi()
        }
    }

    private fun updateActionButtonUi() {
        if (_binding == null) {
            return
        }

        if (selectionMode) {
            binding.btnScanDevices.text = "Delete"
            binding.btnScanDevices.contentDescription = "Delete selected devices"

            binding.btnScanDevices.setTextColor(
                Color.parseColor("#FF8A8A")
            )

            binding.btnScanDevices.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#321E2A")
            )

            binding.btnScanDevices.strokeWidth = 1.dp()
            binding.btnScanDevices.strokeColor = ColorStateList.valueOf(
                Color.parseColor("#7A3344")
            )
        } else {
            binding.btnScanDevices.text = "+ Add"
            binding.btnScanDevices.contentDescription = getString(
                R.string.devices_scan_button_desc
            )

            binding.btnScanDevices.setTextColor(
                Color.WHITE
            )

            binding.btnScanDevices.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#1C3252")
            )

            binding.btnScanDevices.strokeWidth = 0
            binding.btnScanDevices.strokeColor = ColorStateList.valueOf(
                Color.TRANSPARENT
            )
        }
    }

    private fun showDeleteConfirmDialog() {
        if (isDeletingDevices) {
            return
        }

        val ids = adapter.getSelectedIds()

        if (ids.isEmpty()) {
            exitSelectionMode()
            return
        }

        val count = ids.size

        val title = if (count == 1) {
            getString(R.string.devices_delete_title_single)
        } else {
            getString(
                R.string.devices_delete_title_multi,
                count
            )
        }

        val message = if (count == 1) {
            getString(R.string.devices_delete_message_single)
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
                deleteSelectedDevices(ids)
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

        isDeletingDevices = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)

                devicesStore.deleteDevices(ids)

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
                isDeletingDevices = false
                showGlobalLoading(false)
            }
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(show)
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        binding.rvSelectedDevices.adapter = null
        _binding = null

        super.onDestroyView()
    }

    private companion object {
        const val ONLINE_TIMEOUT_MS = 90_000L
    }
}