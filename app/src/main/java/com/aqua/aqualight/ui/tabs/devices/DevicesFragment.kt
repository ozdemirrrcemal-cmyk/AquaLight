package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
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
                // 🗑 Seçim modundayken: önce confirm dialog
                showDeleteConfirmDialog()
            } else {
                // Normal mod: tarama ekranına git
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
                    val onlineTimeout = 60_000L // 60 sn içinde görüldüyse online say

                    val uiList = list.map { dev ->
                        val isOnline = dev.lastSeenMillis != 0L &&
                                (now - dev.lastSeenMillis) <= onlineTimeout

                        DeviceCardUi(
                            id = dev.id,
                            aquaName = dev.aquaName,
                            name = dev.name.ifBlank { "Device" },
                            ip = dev.ip,
                            serial = dev.serial,
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

    // ✅ TOPLU SİLME İÇİN CONFIRM DIALOG
    private fun showDeleteConfirmDialog() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        val count = ids.size
        val title = if (count == 1) {
            getString(R.string.devices_delete_title_single)  // yoksa aşağıdaki hardcode’ları kullanabilirsin
        } else {
            getString(R.string.devices_delete_title_multi, count)
        }

        val message = if (count == 1) {
            getString(R.string.devices_delete_message_single)
        } else {
            getString(R.string.devices_delete_message_multi, count)
        }

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            confirmTextResId = R.string.delete,   // buton yazıları için string’in varsa
            cancelTextResId = R.string.cancel,
            onConfirm = {
                // Onaylarsa gerçekten sil
                deleteSelectedDevices(ids)
            },
            onCancel = {
                // İstersen buraya bir şey yapma, seçim kalsın
            }
        )
    }

    // Asıl silme işi
    private fun deleteSelectedDevices(ids: Set<Long>) {
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