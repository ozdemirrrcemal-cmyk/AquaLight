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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var userPrefs: UserPreferencesManager
    private lateinit var adapter: DevicesListAdapter

    // Seçim modu durumu
    private var selectionMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        userPrefs = UserPreferencesManager.create(requireContext())

        adapter = DevicesListAdapter(
            onSelectionModeStart = { enterSelectionMode() },
            onSelectionChanged = { count ->
                if (count == 0) {
                    exitSelectionMode()
                }
            }
        )

        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedDevices.adapter = adapter

        binding.btnScanDevices.setOnClickListener {
            if (selectionMode) {
                deleteSelectedDevices()
            } else {
                findNavController().navigate(
                    R.id.action_devicesFragment_to_scanDevicesFragment
                )
            }
        }

        observeDevicesList()
    }

    // MULTI DEVICE DESTEK
    private fun observeDevicesList() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                    val onlineTimeout = 30_000L // 30 sn içinde görüldüyse online say

                    val uiList = list.map { dev ->
                        val isOnline = dev.lastSeenMillis != 0L &&
                                (now - dev.lastSeenMillis) <= onlineTimeout

                        DeviceCardUi(
                            id = dev.id,
                            aquaName = dev.aquaName,
                            name = dev.name.ifBlank { "Device" },
                            isOnline = isOnline
                        )
                    }

                    adapter.submitList(uiList)
                }
            }
        }
    }

    // SEÇİM MODU
    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        binding.btnScanDevices.setImageResource(R.drawable.ic_delete)
    }

    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        adapter.exitSelectionMode()
        binding.btnScanDevices.setImageResource(R.drawable.ic_radar)
    }

    // TOPLU SİLME
    private fun deleteSelectedDevices() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.deleteDevices(ids)
            exitSelectionMode()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}