package com.aqua.aqualight.ui.tabs.settings.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.databinding.FragmentNetworkBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkFragment : Fragment(R.layout.fragment_network) {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentNetworkBinding.bind(view)

        setupHeader()
        bindConnectionStatus()
        observeDiscoveredDevices()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun bindConnectionStatus() {
        val connectivityManager =
            requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network =
            connectivityManager.activeNetwork

        val capabilities =
            connectivityManager.getNetworkCapabilities(
                network
            )

        val isOnline =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true

        val statusText =
            if (isOnline) {
                getString(
                    R.string.network_status_online
                )
            } else {
                getString(
                    R.string.network_status_offline
                )
            }

        val typeText =
            when {
                capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                ) == true -> {
                    getString(
                        R.string.network_type_wifi
                    )
                }

                capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ) == true -> {
                    getString(
                        R.string.network_type_mobile
                    )
                }

                else -> {
                    getString(
                        R.string.network_type_unknown
                    )
                }
            }

        binding.tvStatusValue.text =
            statusText

        binding.tvTypeValue.text =
            typeText
    }

    private fun observeDiscoveredDevices() {
        val appContext =
            requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                var isFirstScan =
                    true

                while (isActive) {
                    if (isFirstScan) {
                        showScanMessage(
                            getString(
                                R.string.network_devices_scanning
                            )
                        )
                    }

                    val result =
                        DeviceDiscoveryService.scan(
                            context = appContext,
                            timeoutMs = NETWORK_SCAN_TIMEOUT_MS,
                            reason = DeviceScanReason.MANUAL_SCAN
                        )

                    if (result.error != null) {
                        showScanMessage(
                            getString(
                                R.string.network_devices_empty
                            )
                        )
                    } else {
                        bindDevicesToUi(
                            result.devices.sortedWith(
                                compareBy<DiscoveredAquaDevice> {
                                    it.productModel.orEmpty().ifBlank {
                                        it.aquaName.ifBlank {
                                            it.name
                                        }
                                    }
                                }.thenBy {
                                    it.id
                                }
                            )
                        )
                    }

                    isFirstScan =
                        false

                    delay(
                        NETWORK_SCAN_INTERVAL_MS
                    )
                }
            }
        }
    }

    private fun showScanMessage(
        message: String
    ) {
        binding.deviceListContainer.removeAllViews()

        binding.tvNoDevices.visibility =
            View.VISIBLE

        binding.tvNoDevices.text =
            message
    }

    private fun bindDevicesToUi(
        devices: List<DiscoveredAquaDevice>
    ) {
        val container =
            binding.deviceListContainer

        container.removeAllViews()

        if (devices.isEmpty()) {
            binding.tvNoDevices.visibility =
                View.VISIBLE

            binding.tvNoDevices.text =
                getString(
                    R.string.network_devices_empty
                )

            return
        }

        binding.tvNoDevices.visibility =
            View.GONE

        val inflater =
            LayoutInflater.from(
                container.context
            )

        devices.forEach { device ->
            val row =
                inflater.inflate(
                    R.layout.simple_list_item_udp_device,
                    container,
                    false
                ) as ViewGroup

            val tvName =
                row.findViewById<TextView>(
                    R.id.tvDeviceName
                )

            val tvInfo =
                row.findViewById<TextView>(
                    R.id.tvDeviceInfo
                )

            tvName.text =
                resolveDeviceName(device)

            tvInfo.text =
                buildDeviceInfo(device)

            container.addView(
                row
            )
        }
    }

    private fun resolveDeviceName(
        device: DiscoveredAquaDevice
    ): String {
        return device.productModel
            .orEmpty()
            .ifBlank {
                device.aquaName.ifBlank {
                    device.name.ifBlank {
                        getString(
                            R.string.network_device_default_name
                        )
                    }
                }
            }
    }

    private fun buildDeviceInfo(
        device: DiscoveredAquaDevice
    ): String {
        return buildString {
            append(
                device.ip
            )

            append(
                " • ID: "
            )
            append(
                device.id
            )

            device.macAddress
                ?.takeIf { it.isNotBlank() }
                ?.let { macAddress ->
                    append(
                        " • MAC: "
                    )
                    append(
                        macAddress
                    )
                }

            device.firmwareVersion
                ?.takeIf { it.isNotBlank() }
                ?.let { firmwareVersion ->
                    append(
                        " • FW: "
                    )
                    append(
                        firmwareVersion
                    )
                }
                ?: device.firmwareBuild
                    .takeIf { it.isNotBlank() }
                    ?.let { firmwareBuild ->
                        append(
                            " • FW: "
                        )
                        append(
                            firmwareBuild
                        )
                    }
        }
    }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        private const val NETWORK_SCAN_TIMEOUT_MS = 5_000L
        private const val NETWORK_SCAN_INTERVAL_MS = 5_000L
    }
}
