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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanDevicesBinding.bind(view)

        // Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Yeniden tara
        binding.btnRescan.setOnClickListener {
            startScan()
        }

        setupRecyclerView()
        startScan()
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device: DiscoveredDevice ->
            // Cihaz seçildiğinde:
            saveSelectedDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        // Başlık: Scanning...
        binding.tvTitle.text = getString(R.string.device_scan_header_scanning)

        // 🔒 Taramadayken butonu kilitle
        binding.btnRescan.isEnabled = false
        binding.btnRescan.alpha = 0.4f

        // UI state: tarama
        binding.scanAnimation.visibility = View.VISIBLE
        binding.scanAnimation.playAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.GONE

        lifecycleScope.launch {
            val devices: List<DiscoveredDevice> =
                discoverDevices(requireContext(), timeoutMs = 3000L)

            // Tarama bitti → başlık tekrar Device list
            binding.tvTitle.text = getString(R.string.device_scan_header_list)

            // Animasyon durdur + gizle
            binding.scanAnimation.cancelAnimation()
            binding.scanAnimation.visibility = View.GONE

            if (devices.isEmpty()) {
                binding.rvDevices.visibility = View.GONE
                binding.tvNoDevices.visibility = View.VISIBLE
            } else {
                binding.rvDevices.visibility = View.VISIBLE
                binding.tvNoDevices.visibility = View.GONE
                adapter.submitList(devices)
            }

            // 🔓 Butonu tekrar aç
            binding.btnRescan.isEnabled = true
            binding.btnRescan.alpha = 1f
        }
    }

    private fun saveSelectedDevice(device: DiscoveredDevice) {
        // Sonra DataStore / Navigation ekleyeceksin, şimdilik geri dönüyor
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}