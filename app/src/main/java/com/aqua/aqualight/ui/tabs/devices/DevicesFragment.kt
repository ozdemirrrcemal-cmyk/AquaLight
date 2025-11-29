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

    // 🔹 Seçim modu durumu
    private var selectionMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        // DataStore
        userPrefs = UserPreferencesManager.create(requireContext())

        // RecyclerView + adapter (multi select destekli)
        adapter = DevicesListAdapter(
            onSelectionModeStart = { enterSelectionMode() },
            onSelectionChanged = { count ->
                // Hiç seçili kalmadıysa seçim modundan çık
                if (count == 0) {
                    exitSelectionMode()
                }
            }
        )

        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedDevices.adapter = adapter

        // Tara / cihaz ekle veya seçim modunda sil
        binding.btnScanDevices.setOnClickListener {
            if (selectionMode) {
                // 🗑 Seçim modundayken: seçili cihaz(lar)ı sil
                deleteSelectedDevices()
            } else {
                // Normal mod: tarama ekranına git
                findNavController().navigate(
                    R.id.action_devicesFragment_to_scanDevicesFragment
                )
            }
        }

        observeDevicesList()
    }

    // ---------------------------
    // MULTI DEVICE DESTEK
    // ---------------------------
    private fun observeDevicesList() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.devicesFlow.collect { list ->

                    if (list.isEmpty()) {
                        // Hiç cihaz kayıtlı değil
                        exitSelectionMode()
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvSelectedDevices.visibility = View.GONE
                        adapter.submitList(emptyList())
                        return@collect
                    }

                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSelectedDevices.visibility = View.VISIBLE

                    // 1️⃣ İlk gösterim → hepsi offline
                    val uiList = list.map {
                        DeviceCardUi(
                            id = it.id,
                            aquaName = it.aquaName,
                            name = it.name,
                            ip = it.ip,          // 🔹 EKLENDİ
                            isOnline = false
                        )
                    }

                    adapter.submitList(uiList)

                    // 2️⃣ Online / offline kontrolünü ARKA PLANDA yap
                    viewLifecycleOwner.lifecycleScope.launch {
                        val discovered = try {
                            discoverDevices(requireContext(), timeoutMs = 1500L)
                        } catch (_: Exception) {
                            emptyList()
                        }

                        val updated = uiList.map { d ->
                            val isOn = discovered.any { disc ->
                                disc.id == d.id || disc.ip == d.ip
                            }
                            d.copy(isOnline = isOn)
                        }

                        adapter.submitList(updated)
                    }
                }
            }
        }
    }

    // ---------------------------
    // SEÇİM MODU
    // ---------------------------
    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        binding.btnScanDevices.setImageResource(R.drawable.ic_delete) // çöp icon
    }

    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        adapter.exitSelectionMode()
        binding.btnScanDevices.setImageResource(R.drawable.ic_radar) // radar icon
    }

    // ---------------------------
    // TOPLU SİLME
    // ---------------------------
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