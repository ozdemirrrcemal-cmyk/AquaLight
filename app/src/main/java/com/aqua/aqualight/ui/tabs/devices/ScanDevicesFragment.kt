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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScanDevicesFragment : Fragment(R.layout.fragment_scan_devices) {

    private var _binding: FragmentScanDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanDevicesAdapter
    private lateinit var userPrefs: UserPreferencesManager

    private var scanJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentScanDevicesBinding.bind(view)
        userPrefs = UserPreferencesManager.create(requireContext())

        setupRecyclerView()
        setupClickListeners()

        startScan()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRescan.setOnClickListener {
            startScan()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device: DiscoveredDevice ->
            saveSelectedDevice(device)
        }

        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        if (scanJob?.isActive == true) return

        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            showScanningState()

            try {
                val devices = discoverDevices(
                    context = requireContext(),
                    timeoutMs = 3000L
                )

                if (_binding == null) return@launch

                showResultState(devices)

            } catch (e: Exception) {
                e.printStackTrace()

                if (_binding != null) {
                    showErrorState()
                }
            }
        }
    }

    private fun showScanningState() {
        binding.tvTitle.text = getString(R.string.device_scan_header_scanning)

        binding.btnRescan.isEnabled = false
        binding.btnRescan.alpha = 0.4f

        binding.scanAnimation.visibility = View.VISIBLE
        binding.scanAnimation.playAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.GONE

        adapter.submitList(emptyList())
    }

    private fun showResultState(devices: List<DiscoveredDevice>) {
        binding.tvTitle.text = getString(R.string.device_scan_header_list)

        binding.scanAnimation.cancelAnimation()
        binding.scanAnimation.visibility = View.GONE

        binding.btnRescan.isEnabled = true
        binding.btnRescan.alpha = 1f

        if (devices.isEmpty()) {
            binding.rvDevices.visibility = View.GONE
            binding.tvNoDevices.visibility = View.VISIBLE
            adapter.submitList(emptyList())
        } else {
            binding.rvDevices.visibility = View.VISIBLE
            binding.tvNoDevices.visibility = View.GONE
            adapter.submitList(devices)
        }
    }

    private fun showErrorState() {
        binding.scanAnimation.cancelAnimation()
        binding.scanAnimation.visibility = View.GONE

        binding.btnRescan.isEnabled = true
        binding.btnRescan.alpha = 1f

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.VISIBLE

        adapter.submitList(emptyList())
    }

    private fun saveSelectedDevice(device: DiscoveredDevice) {
        val aquaName = device.aquaName?.ifBlank { "-" } ?: "-"
        val name = device.name.ifBlank { "Device" }
        val serial = buildSerial(aquaName, name, device.id)

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.addDevice(
                id = device.id,
                aquaName = aquaName,
                name = name,
                ip = device.ip,
                serial = serial
            )

            if (_binding != null) {
                findNavController().popBackStack()
            }
        }
    }

    private fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {
        val a = aquaName.firstOrNull()?.uppercaseChar() ?: 'X'
        val n = name.firstOrNull()?.uppercaseChar() ?: 'X'
        val core = if (id != 0L) id.toString() else ""

        return if (core.isNotEmpty()) {
            "$a$n-$core"
        } else {
            "$a$n"
        }
    }

    override fun onDestroyView() {
        scanJob?.cancel()
        scanJob = null

        _binding?.scanAnimation?.cancelAnimation()
        binding.rvDevices.adapter = null

        _binding = null

        super.onDestroyView()
    }
}