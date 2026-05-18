package com.aqua.aqualight.ui.tabs.devices

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

    private lateinit var userPrefs: UserPreferencesManager
    private lateinit var adapter: DevicesListAdapter

    private var selectionMode = false
    private var isDeletingDevices = false

    private companion object {
        const val ONLINE_TIMEOUT_MS = 60_000L
        const val LIVE_CHECK_TIMEOUT_MS = 800L
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDevicesBinding.bind(view)
        userPrefs = UserPreferencesManager.create(requireContext())

        setupRecyclerView()
        setupClickListeners()
        observeDevicesList()
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

        binding.rvSelectedDevices.layoutManager =
            LinearLayoutManager(requireContext())

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
                userPrefs.devicesFlow.collect { list ->
                    if (list.isEmpty()) {
                        exitSelectionMode()

                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvSelectedDevices.visibility = View.GONE

                        adapter.submitList(emptyList())
                        return@collect
                    }

                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSelectedDevices.visibility = View.VISIBLE

                    val now = System.currentTimeMillis()

                    val uiList = list.map { device ->
                        val online =
                            device.lastSeenMillis != 0L &&
                                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS

                        DeviceCardUi(
                            id = device.id,
                            name = device.name.ifBlank { "Device" },
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
            val baseActivity = activity as? BaseActivity

            baseActivity?.showLoading(true)

            val isActuallyOnline = try {
                withTimeoutOrNull(LIVE_CHECK_TIMEOUT_MS + 500L) {
                    val discovered = discoverDevices(
                        context = requireContext(),
                        timeoutMs = LIVE_CHECK_TIMEOUT_MS
                    )

                    discovered.any {
                        it.id == device.id || it.ip == device.ip
                    }
                } ?: false
            } finally {
                baseActivity?.showLoading(false)
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

            binding.btnScanDevices.setImageResource(
                R.drawable.ic_delete
            )
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false

            adapter.exitSelectionMode()

            binding.btnScanDevices.setImageResource(
                R.drawable.ic_radar
            )
        }
    }

    private fun showDeleteConfirmDialog() {
        if (isDeletingDevices) {
            return
        }

        val ids = adapter.getSelectedIds()

        if (ids.isEmpty()) {
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

        isDeletingDevices = true

        val baseActivity = activity as? BaseActivity
        baseActivity?.showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userPrefs.deleteDevices(ids)

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
                baseActivity?.showLoading(false)
            }
        }
    }

    override fun onDestroyView() {
        binding.rvSelectedDevices.adapter = null
        _binding = null

        super.onDestroyView()
    }
}