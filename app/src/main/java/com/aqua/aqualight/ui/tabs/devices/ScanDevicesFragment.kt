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

    private lateinit var adapter: ScanDevicesAdapter

    private var currentDevices: List<DiscoveredDevice> = emptyList()
    private var isScanning: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanDevicesBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRescan.setOnClickListener {
            if (!isScanning) {
                startScan()
            }
        }

        setupRecyclerView()
        startScan()
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device ->
            saveSelectedDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        isScanning = true
        currentDevices = emptyList()
        adapter.submitList(emptyList())

        // Başlık
        binding.tvTitle.text = getString(R.string.device_scan_header_scanning)

        // UI state
        binding.btnRescan.visibility = View.GONE        // tarama sırasında gizle
        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.GONE

        // Radar aç
        binding.scanAnimation.visibility = View.VISIBLE
        binding.scanAnimation.progress = 0f
        binding.scanAnimation.playAnimation()

        lifecycleScope.launch {
            val devices: List<DiscoveredDevice> =
                discoverDevices(requireContext(), timeoutMs = 3000L)

            currentDevices = devices
            adapter.submitList(devices)

            isScanning = false

            // Radar kapat
            binding.scanAnimation.cancelAnimation()
            binding.scanAnimation.visibility = View.GONE

            // Butonu tekrar göster
            binding.btnRescan.visibility = View.VISIBLE
            binding.tvTitle.text = getString(R.string.device_scan_header_list)

            if (devices.isEmpty()) {
                binding.tvNoDevices.visibility = View.VISIBLE
                binding.rvDevices.visibility = View.GONE
            } else {
                binding.tvNoDevices.visibility = View.GONE
                binding.rvDevices.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSelectedDevice(device: DiscoveredDevice) {
        // Burada DataStore’a kaydedebilirsin, şimdilik sadece geri dönüyoruz
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}