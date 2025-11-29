package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var userPrefs: UserPreferencesManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        // 🔹 DataStore manager
        userPrefs = UserPreferencesManager.create(requireContext())

        // Cihaz tara / cihaz ekle
        binding.btnScanDevices.setOnClickListener {
            findNavController().navigate(
                R.id.action_devicesFragment_to_scanDevicesFragment
            )
        }

        // Seçili cihazı ekrana bas + online/offline kontrol et
        observeSelectedDevice()

        // İleride: seçili cihaza tıklayınca cihaz menüsüne git
        binding.tvSelectedDevice.setOnClickListener {
            // TODO: device menu fragment / bottom sheet
            // findNavController().navigate(R.id.action_devicesFragment_to_deviceMenuFragment)
        }
    }

    private fun observeSelectedDevice() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.selectedDeviceFlow.collect { device ->
                    if (device == null || device.id == 0L) {
                        // Hiç cihaz seçilmemiş
                        binding.tvSelectedDevice.text =
                            getString(R.string.devices_no_selected)

                        // renk normal
                        binding.tvSelectedDevice.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.settings_text_secondary
                            )
                        )
                    } else {
                        val name = device.name.ifBlank { "Device" }

                        // Temel satır: DS-637128968 • SuperStar Pump (192.168.4.10)
                        val baseLine = buildString {
                            append(device.serial)
                            append(" • ")
                            append(name)
                            if (device.ip.isNotBlank()) {
                                append(" (")
                                append(device.ip)
                                append(")")
                            }
                        }

                        // 🔍 Kısa bir tarama ile online mı diye bak
                        val isOnline = try {
                            val devices = discoverDevices(
                                requireContext(),
                                timeoutMs = 1500L
                            )
                            devices.any { it.id == device.id || it.ip == device.ip }
                        } catch (e: Exception) {
                            // Tarama hata verirse offline/unknown say
                            false
                        }

                        val lineWithStatus = if (isOnline) {
                            "$baseLine  • Online"
                        } else {
                            "$baseLine  • Offline"
                        }

                        binding.tvSelectedDevice.text = lineWithStatus

                        val colorRes = if (isOnline) {
                            R.color.settings_text_primary   // online → normal/parlak
                        } else {
                            R.color.settings_text_secondary // offline → biraz soluk
                        }

                        binding.tvSelectedDevice.setTextColor(
                            ContextCompat.getColor(requireContext(), colorRes)
                        )
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