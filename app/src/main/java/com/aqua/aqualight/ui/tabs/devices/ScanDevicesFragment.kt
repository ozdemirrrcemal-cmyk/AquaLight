package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentScanDevicesBinding
import kotlinx.coroutines.launch

class ScanDevicesFragment : Fragment(R.layout.fragment_scan_devices) {

    private var _binding: FragmentScanDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanDevicesAdapter
    private lateinit var userPrefs: UserPreferencesManager   // DataStore manager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanDevicesBinding.bind(view)

        userPrefs = UserPreferencesManager.create(requireContext())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRescan.setOnClickListener {
            startScan()
        }

        setupRecyclerView()
        startScan()
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device: DiscoveredDevice ->
            addDiscoveredDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        binding.tvTitle.text = getString(R.string.device_scan_header_scanning)

        binding.btnRescan.isEnabled = false
        binding.btnRescan.alpha = 0.4f

        binding.scanAnimation.visibility = View.VISIBLE
        binding.scanAnimation.playAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.GONE

        lifecycleScope.launch {
            val devices = discoverDevices(requireContext(), timeoutMs = 3000L)

            binding.tvTitle.text = getString(R.string.device_scan_header_list)

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

            binding.btnRescan.isEnabled = true
            binding.btnRescan.alpha = 1f
        }
    }

    // 🔹 Cihazı DataStore'a EKLE (çoklu liste)
    private fun addDiscoveredDevice(device: DiscoveredDevice) {
        val aquaName = device.aquaName?.ifBlank { "-" } ?: "-"
        val name = device.name.ifBlank { "Device" }

        // Seri numarası üret
        val serial = buildSerial(aquaName, name, device.id)

        lifecycleScope.launch {
            userPrefs.addDevice(
                id = device.id,
                aquaName = aquaName,
                name = name,
                ip = device.ip,
                serial = serial
            )

            findNavController().popBackStack()
        }
    }

    // AN + NN + - + ID → Örn: DS-637128968
    private fun buildSerial(aquaName: String, name: String, id: Long): String {
        val a = aquaName.firstOrNull()?.uppercaseChar() ?: 'X'
        val n = name.firstOrNull()?.uppercaseChar() ?: 'X'
        val core = id.toString()
        return "$a$n-$core"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}