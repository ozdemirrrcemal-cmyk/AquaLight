package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentScanDevicesBinding
import kotlinx.coroutines.launch

class ScanDevicesFragment : Fragment(R.layout.fragment_scan_devices) {

    private var _binding: FragmentScanDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanDevicesAdapter   // RV adapter (henüz yazmadık)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanDevicesBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupRecyclerView()
        startScan()
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device ->
            // 🔹 Cihaz seçildiğinde:
            saveSelectedDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        // Animasyon zaten autoPlay
        binding.tvScanning.visibility = View.VISIBLE
        binding.tvNoDevices.visibility = View.GONE

        lifecycleScope.launch {
            // Buraya UDP discovery fonksiyonunu bağlayacağız
            // val devices = discoverDevices()

            val devices = emptyList<DiscoveredDevice>() // placeholder

            if (devices.isEmpty()) {
                binding.tvNoDevices.visibility = View.VISIBLE
            } else {
                binding.tvNoDevices.visibility = View.GONE
                adapter.submitList(devices)
            }
        }
    }

    private fun saveSelectedDevice(device: DiscoveredDevice) {
        // 🔹 Burada DataStore’a yazacaksın
        // Örn:
        /*
        lifecycleScope.launch {
            requireContext().dataStore.edit { prefs ->
                prefs[KEY_DEVICE_IP] = device.ip
                prefs[KEY_DEVICE_NAME] = device.name
            }
            findNavController().popBackStack()
        }
        */

        findNavController().popBackStack() // şimdilik sadece geri dönelim
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}