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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        // DataStore
        userPrefs = UserPreferencesManager.create(requireContext())

        // RecyclerView + adapter
        adapter = DevicesListAdapter { deviceCard ->
            // TODO: kart tıklanınca cihaz menüsü açılacak
            // findNavController().navigate(...)
        }
        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedDevices.adapter = adapter

        // Tara / cihaz ekle
        binding.btnScanDevices.setOnClickListener {
            findNavController().navigate(
                R.id.action_devicesFragment_to_scanDevicesFragment
            )
        }

        observeSelectedDevice()
    }

    private fun observeSelectedDevice() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.selectedDeviceFlow.collect { device ->
                    if (device == null || device.id == 0L) {
                        // Hiç kayıtlı cihaz yok
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvSelectedDevices.visibility = View.GONE
                        adapter.submitList(emptyList())
                    } else {
                        // Cihaz var → online/offline kontrol et
                        val isOnline = try {
                            val devices = discoverDevices(
                                requireContext(),
                                timeoutMs = 1500L
                            )
                            devices.any { it.id == device.id || it.ip == device.ip }
                        } catch (e: Exception) {
                            false
                        }

                        val uiItem = DeviceCardUi(
                            id = device.id,
                            aquaName = device.aquaName,
                            name = device.name.ifBlank { "Device" },
                            isOnline = isOnline
                        )

                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvSelectedDevices.visibility = View.VISIBLE
                        adapter.submitList(listOf(uiItem))   // şimdilik tek cihaz
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