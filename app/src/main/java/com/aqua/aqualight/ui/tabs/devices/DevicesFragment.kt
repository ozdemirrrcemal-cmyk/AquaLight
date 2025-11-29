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

        userPrefs = UserPreferencesManager.create(requireContext())

        binding.btnScanDevices.setOnClickListener {
            findNavController().navigate(
                R.id.action_devicesFragment_to_scanDevicesFragment
            )
        }

        observeSelectedDevice()

        binding.tvSelectedDevice.setOnClickListener {
            // TODO: device menu
        }
    }

    private fun observeSelectedDevice() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.selectedDeviceFlow.collect { device ->
                    // İlk valid emit geldiği anda görünür yap
                    binding.tvSelectedDevice.visibility = View.VISIBLE

                    if (device == null) {
                        // Hiç cihaz seçilmemiş
                        binding.tvSelectedDevice.text =
                            getString(R.string.devices_no_selected)

                        binding.tvSelectedDevice.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.settings_text_secondary
                            )
                        )
                    } else {
                        val name = device.name.ifBlank { "Device" }

                        // 1️⃣ Önce sadece temel satırı hemen göster
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

                        binding.tvSelectedDevice.text = baseLine
                        binding.tvSelectedDevice.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.md_theme_dark_onSurface
                            )
                        )

                        // 2️⃣ Online / offline kontrolünü ayrı coroutine’de yap
                        viewLifecycleOwner.lifecycleScope.launch {
                            val isOnline = try {
                                val devices = discoverDevices(
                                    requireContext(),
                                    timeoutMs = 1500L
                                )
                                devices.any { it.id == device.id || it.ip == device.ip }
                            } catch (e: Exception) {
                                false
                            }

                            val lineWithStatus = if (isOnline) {
                                "$baseLine  • Online"
                            } else {
                                "$baseLine  • Offline"
                            }

                            binding.tvSelectedDevice.text = lineWithStatus

                            val colorRes = if (isOnline) {
                                R.color.md_theme_dark_onSurface   // online → parlak
                            } else {
                                R.color.settings_text_secondary    // offline → soluk
                            }

                            binding.tvSelectedDevice.setTextColor(
                                ContextCompat.getColor(requireContext(), colorRes)
                            )
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