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

    // 🔹 Seçim modu state
    private var selectionMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        // DataStore
        userPrefs = UserPreferencesManager.create(requireContext())

        // RecyclerView + adapter (multi select destekli)
        adapter = DevicesListAdapter(
            onSelectionModeStart = {
                enterSelectionMode()
            },
            onSelectionChanged = { count ->
                // Şimdilik: hiç seçili kalmadıysa selection mode'dan çık
                if (count == 0 && selectionMode) {
                    exitSelectionMode()
                }
                // İstersen burada title'a "(3)" falan ekleyebilirsin
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

        observeSelectedDevice()
    }

    // 🔹 Seçim moduna giriş: radar → çöp kovası
    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        binding.btnScanDevices.setImageResource(R.drawable.ic_delete) // kendi çöp icon’un
    }

    // 🔹 Seçim modundan çıkış: çöp kovası → radar
    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        adapter.exitSelectionMode()
        binding.btnScanDevices.setImageResource(R.drawable.ic_radar)
    }

    // 🔹 Şimdilik DataStore’da tek cihaz tuttuğun için:
    // delete = seçili cihazı DataStore’dan sil + listeyi boşalt
    private fun deleteSelectedDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Şu an DataStore tarafında sadece 1 cihaz saklıyoruz,
            // o yüzden direkt clearSelectedDevice çağırmak yeterli.
            userPrefs.clearSelectedDevice()
            exitSelectionMode()
        }
    }

    private fun observeSelectedDevice() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.selectedDeviceFlow.collect { device ->
                    if (device == null || device.id == 0L) {
                        // ❌ Hiç kayıtlı cihaz yok
                        exitSelectionMode() // güvenlik: iconu da eski haline getir
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvSelectedDevices.visibility = View.GONE
                        adapter.submitList(emptyList())
                    } else {
                        // ✅ Cihaz var → kartı HEMEN göster (status = offline varsayılan)
                        val baseItem = DeviceCardUi(
                            id = device.id,
                            aquaName = device.aquaName,
                            name = device.name.ifBlank { "Device" },
                            isOnline = false          // önce offline varsay
                        )

                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvSelectedDevices.visibility = View.VISIBLE
                        adapter.submitList(listOf(baseItem))   // kart ANINDA görünür

                        // 🔄 Online / offline kontrolünü ARKA PLANDA yap
                        viewLifecycleOwner.lifecycleScope.launch {
                            val isOnline = try {
                                val devices = discoverDevices(
                                    requireContext(),
                                    timeoutMs = 1500L
                                )
                                devices.any { it.id == device.id || it.ip == device.ip }
                            } catch (_: Exception) {
                                false
                            }

                            val updatedItem = baseItem.copy(isOnline = isOnline)
                            adapter.submitList(listOf(updatedItem))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}