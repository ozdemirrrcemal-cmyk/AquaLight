package com.aqua.aqualight.ui.tabs.settings.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentNetworkBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class NetworkFragment : Fragment(R.layout.fragment_network) {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    private var udpListenJob: Job? = null

    private val tag = "NetworkFragment"

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
        startUdpScanLoop()
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

    data class UdpDeviceUi(
        val ip: String,
        val name: String,
        val aquaName: String?,
        val cloneName: String?,
        val firmware: String?,
        val hasLight: Boolean,
        val hasTimer: Boolean,
        val hasTemperature: Boolean,
        val lastSeenMillis: Long
    )

    private fun startUdpScanLoop() {
        binding.deviceListContainer.removeAllViews()

        binding.tvNoDevices.visibility =
            View.VISIBLE

        binding.tvNoDevices.text =
            getString(
                R.string.network_devices_scanning
            )

        udpListenJob?.cancel()

        udpListenJob =
            viewLifecycleOwner.lifecycleScope.launch(
                Dispatchers.IO
            ) {
                val devicesMap =
                    LinkedHashMap<String, UdpDeviceUi>()

                val port =
                    10888

                val listenTimeoutMs =
                    1000

                val staleTimeoutMs =
                    3 * 60_000L

                var lastRefreshSend =
                    0L

                while (isActive) {
                    val nowLoop =
                        System.currentTimeMillis()

                    if (
                        nowLoop - lastRefreshSend >
                        5000
                    ) {
                        sendUdpRefreshBroadcast(
                            port
                        )

                        lastRefreshSend =
                            nowLoop
                    }

                    val newDevices =
                        listenForUdpBroadcasts(
                            port = port,
                            timeoutMillis = listenTimeoutMs
                        )

                    val now =
                        System.currentTimeMillis()

                    newDevices.forEach { device ->
                        devicesMap[device.ip] =
                            device.copy(
                                lastSeenMillis = now
                            )
                    }

                    val visibleDevices =
                        devicesMap.values
                            .filter {
                                now - it.lastSeenMillis <= staleTimeoutMs
                            }
                            .sortedBy {
                                it.aquaName ?: it.name
                            }

                    withContext(
                        Dispatchers.Main
                    ) {
                        bindDevicesToUi(
                            visibleDevices
                        )
                    }
                }
            }
    }

    private fun sendUdpRefreshBroadcast(
        port: Int
    ) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast =
                    true

                val data =
                    """{"Command":"RefreshUDP"}"""
                        .toByteArray(
                            Charsets.UTF_8
                        )

                val packet =
                    DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("255.255.255.255"),
                        port
                    )

                socket.send(
                    packet
                )
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "UDP RefreshUDP send failed",
                e
            )
        }
    }

    private fun listenForUdpBroadcasts(
        port: Int,
        timeoutMillis: Int
    ): List<UdpDeviceUi> {
        val result =
            LinkedHashMap<String, UdpDeviceUi>()

        try {
            DatagramSocket(port).use { socket ->
                socket.soTimeout =
                    timeoutMillis

                val buffer =
                    ByteArray(4096)

                while (true) {
                    val packet =
                        DatagramPacket(
                            buffer,
                            buffer.size
                        )

                    try {
                        socket.receive(
                            packet
                        )
                    } catch (_: SocketTimeoutException) {
                        break
                    }

                    val senderIp =
                        packet.address
                            ?.hostAddress
                            ?: continue

                    val length =
                        packet.length

                    val payload =
                        buffer.copyOf(
                            length
                        )
                            .toString(
                                Charsets.UTF_8
                            )
                            .trim()

                    try {
                        val device =
                            parseEsp32UdpJson(
                                senderIp,
                                payload
                            )

                        if (device != null) {
                            result[senderIp] =
                                device
                        } else {
                            result[senderIp] =
                                UdpDeviceUi(
                                    ip = senderIp,
                                    name = "Aqua device",
                                    aquaName = null,
                                    cloneName = null,
                                    firmware = null,
                                    hasLight = false,
                                    hasTimer = false,
                                    hasTemperature = false,
                                    lastSeenMillis = System.currentTimeMillis()
                                )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            tag,
                            "UDP JSON parse failed: $payload",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "UDP listen failed",
                e
            )
        }

        return result.values.toList()
    }

    private fun parseEsp32UdpJson(
        senderIp: String,
        payload: String
    ): UdpDeviceUi? {
        val root =
            try {
                JSONObject(
                    payload
                )
            } catch (_: Exception) {
                return null
            }

        val dataObj =
            root.optJSONObject(
                "Data"
            ) ?: return null

        val dev0 =
            dataObj.optJSONObject(
                "0"
            ) ?: return null

        val name =
            dev0.optString(
                "Name",
                ""
            )

        val aquaName =
            dev0.optString(
                "AquaName",
                null
            )

        val cloneName =
            dev0.optString(
                "CloneName",
                null
            )

        val firmware =
            dev0.optString(
                "FirmwareBuild",
                null
            )

        val ipFromJson =
            dev0.optString(
                "IP",
                null
            )

        val finalIp =
            if (
                !ipFromJson.isNullOrBlank()
            ) {
                ipFromJson
            } else {
                senderIp
            }

        val hasLight =
            dev0.optInt(
                "TabLight",
                0
            ) != 0

        val hasTimer =
            dev0.optInt(
                "TabTimer",
                0
            ) != 0

        val hasTemperature =
            dev0.optInt(
                "TabTemperature",
                0
            ) != 0

        return UdpDeviceUi(
            ip = finalIp,
            name = name.ifBlank {
                "Aqua device"
            },
            aquaName = aquaName,
            cloneName = cloneName,
            firmware = firmware,
            hasLight = hasLight,
            hasTimer = hasTimer,
            hasTemperature = hasTemperature,
            lastSeenMillis = System.currentTimeMillis()
        )
    }

    private fun bindDevicesToUi(
        devices: List<UdpDeviceUi>
    ) {
        val container =
            binding.deviceListContainer

        container.removeAllViews()

        if (
            devices.isEmpty()
        ) {
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

            val mainName =
                when {
                    !device.aquaName.isNullOrBlank() -> {
                        device.aquaName
                    }

                    device.name.isNotBlank() -> {
                        device.name
                    }

                    else -> {
                        getString(
                            R.string.network_device_default_name
                        )
                    }
                }

            tvName.text =
                mainName

            tvInfo.text =
                buildString {
                    append(
                        device.ip
                    )

                    if (
                        device.name.isNotBlank()
                    ) {
                        append(
                            " • "
                        )

                        append(
                            device.name
                        )
                    }
                }

            container.addView(
                row
            )
        }
    }

    @Suppress("unused")
    private fun getLocalIpv4(): String? {
        return try {
            val interfaces =
                NetworkInterface.getNetworkInterfaces()

            for (
                networkInterface in interfaces
            ) {
                val addresses =
                    networkInterface.inetAddresses

                for (
                    address in addresses
                ) {
                    if (
                        !address.isLoopbackAddress &&
                        address is Inet4Address
                    ) {
                        return address.hostAddress
                    }
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        udpListenJob?.cancel()
        udpListenJob =
            null

        _binding =
            null

        super.onDestroyView()
    }
}