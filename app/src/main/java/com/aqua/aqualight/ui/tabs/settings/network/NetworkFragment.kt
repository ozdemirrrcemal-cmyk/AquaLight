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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentNetworkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkFragment : Fragment(R.layout.fragment_network) {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNetworkBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 📡 Bağlantı bilgisi
        bindConnectionStatus()

        // 🧩 UDP cihaz taraması (port 10880)
        scanUdpDevices()
    }

    // ----------------------------------------------------
    // 📡 Bağlantı durumu kartı
    // ----------------------------------------------------
    private fun bindConnectionStatus() {
        val cm = requireContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val statusText = if (isOnline) {
            getString(R.string.network_status_online)
        } else {
            getString(R.string.network_status_offline)
        }

        val typeText = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                getString(R.string.network_type_wifi)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ->
                getString(R.string.network_type_mobile)
            else ->
                getString(R.string.network_type_unknown)
        }

        binding.tvStatusValue.text = statusText
        binding.tvTypeValue.text = typeText
    }

    // ----------------------------------------------------
    // 🧩 UDP cihaz modeli
    // ----------------------------------------------------
    data class UdpDeviceUi(
        val ip: String,
        val payload: String,
        val lastSeenMillis: Long
    )

    // ----------------------------------------------------
    // 🧩 UDP tarama (port 10880)
    // ----------------------------------------------------
    private fun scanUdpDevices() {
        // UI: önce "Scanning..." yazsın
        binding.deviceListContainer.removeAllViews()
        binding.tvNoDevices.visibility = View.VISIBLE
        binding.tvNoDevices.text = getString(R.string.network_devices_scanning)

        viewLifecycleOwner.lifecycleScope.launch {
            val devices = withContext(Dispatchers.IO) {
                listenForUdpBroadcasts(
                    port = 10880,
                    timeoutMillis = 3000 // 3 sn dinle
                )
            }
            bindDevicesToUi(devices)
        }
    }

    /**
     * Port 10880 üzerinde gelen UDP paketlerini kısa bir süre dinler.
     * Cihazlar bu porta yayın yapıyorsa, gönderici IP + payload metnini toplar.
     */
    private fun listenForUdpBroadcasts(
        port: Int,
        timeoutMillis: Int
    ): List<UdpDeviceUi> {
        val result = LinkedHashMap<String, UdpDeviceUi>() // IP bazlı uniq

        try {
            DatagramSocket(port).use { socket ->
                socket.soTimeout = timeoutMillis

                val buffer = ByteArray(2048)

                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        // Süre doldu → dinlemeyi bırak
                        break
                    }

                    val ip = packet.address.hostAddress ?: continue
                    val length = packet.length
                    val payload = buffer.copyOf(length).toString(Charsets.UTF_8).trim()

                    result[ip] = UdpDeviceUi(
                        ip = ip,
                        payload = payload.ifBlank { "Aqua device" },
                        lastSeenMillis = System.currentTimeMillis()
                    )
                }
            }
        } catch (_: Exception) {
            // Bu aşamada hata gösterme, ekranda "no devices" göstermek yeterli
        }

        return result.values.toList()
    }

    // ----------------------------------------------------
    // 🧩 Cihaz listesini UI'ye bas
    // ----------------------------------------------------
    private fun bindDevicesToUi(devices: List<UdpDeviceUi>) {
        val container = binding.deviceListContainer
        container.removeAllViews()

        if (devices.isEmpty()) {
            binding.tvNoDevices.visibility = View.VISIBLE
            binding.tvNoDevices.text = getString(R.string.network_devices_empty)
            return
        }

        binding.tvNoDevices.visibility = View.GONE

        val inflater = LayoutInflater.from(container.context)
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        devices.forEach { device ->
            val row = inflater.inflate(
                R.layout.simple_list_item_udp_device,
                container,
                false
            ) as ViewGroup

            val tvName = row.findViewById<TextView>(R.id.tvDeviceName)
            val tvInfo = row.findViewById<TextView>(R.id.tvDeviceInfo)

            tvName.text = device.payload
            val timeText = timeFormatter.format(Date(device.lastSeenMillis))
            // Örn: "192.168.1.20 • 21:35"
            tvInfo.text = "${device.ip} • $timeText"

            container.addView(row)
        }
    }

    // (İstersen ileride IP de göstermek için kullanırsın)
    @Suppress("unused")
    private fun getLocalIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (ni in interfaces) {
                val addrs = ni.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}