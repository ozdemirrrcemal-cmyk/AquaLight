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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.DevicesLegacyMigrationManager
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.ui.tabs.devices.model.DeviceType
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var legacyUserPrefs: UserPreferencesManager
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

        legacyUserPrefs = UserPreferencesManager.create(requireContext())
        devicesStore = DevicesDataStoreManager.create(requireContext())

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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DevicesLegacyMigrationManager.migrateIfNeeded(
                    legacyUserPrefs = legacyUserPrefs,
                    devicesStore = devicesStore
                )

                devicesStore.devicesFlow.collect { devices ->
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
                        val online = device.lastSeenMillis != 0L &&
                            now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS

                        DeviceCardUi(
                            id = device.id,
                            name = device.name.ifBlank {
                                "Device"
                            },
                            aquaName = device.aquaName,
                            ip = device.ip,
                            serial = device.serial,
                            firmwareBuild = device.firmwareBuild,
                            isOnline = online,
                            type = DeviceType.fromName(device.aquaName)
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
        if (!device.isOnline) {
            DialogManager.showInfoDialog(
                context = requireContext(),
                type = DialogType.WARNING,
                title = getString(R.string.device_offline_title),
                message = getString(R.string.device_offline_message)
            )
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            showGlobalLoading(true)

            val isActuallyOnline = try {
                withTimeoutOrNull(LIVE_CHECK_TIMEOUT_MS + 500L) {
                    val discovered = discoverDevices(
                        context = requireContext(),
                        timeoutMs = LIVE_CHECK_TIMEOUT_MS
                    )

                    discovered.any { discoveredDevice ->
                        discoveredDevice.id == device.id ||
                            discoveredDevice.ip == device.ip
                    }
                } ?: false
            } finally {
                showGlobalLoading(false)
            }

            if (!isActuallyOnline) {
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
                putString("deviceName", device.name)
                putString("deviceAquaName", device.aquaName)
                putString("deviceIp", device.ip)
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
            binding.btnScanDevices.contentDescription =
                getString(R.string.devices_scan_button_desc)

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
        const val ONLINE_TIMEOUT_MS = 60_000L
        const val LIVE_CHECK_TIMEOUT_MS = 800L
    }
}