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
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentNetworkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

        // 🧩 UDP cihaz taraması (ESP32 uyumlu: port 10888)
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
    // 🧩 UDP cihaz modeli (JSON’dan ayrıştırılmış)
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // 🧩 UDP tarama (ESP32: port 10888)
    // ----------------------------------------------------
    private fun scanUdpDevices() {
        // UI: önce "Scanning..." yazsın
        binding.deviceListContainer.removeAllViews()
        binding.tvNoDevices.visibility = View.VISIBLE
        binding.tvNoDevices.text = getString(R.string.network_devices_scanning)

        viewLifecycleOwner.lifecycleScope.launch {
            val devices = withContext(Dispatchers.IO) {
                listenForUdpBroadcasts(
                    port = 10888,      // 🔴 ESP32 MNetUdp.UDPlocalPort = 10888
                    timeoutMillis = 10000 // Biraz daha uzun dinle (10 sn)
                )
            }
            bindDevicesToUi(devices)
        }
    }

    /**
     * Verilen port üzerinde gelen UDP paketlerini kısa bir süre dinler.
     * ESP32, JSON formatında şu tip paketler gönderiyor:
     *
     * {
     *   "Data": {
     *     "0": {
     *       "ID": ...,
     *       "IndexNet": ...,
     *       "Name": "Aqua_123456",
     *       "AquaName": "Salon Akvaryumu",
     *       "CloneName": "",
     *       "FirmwareBuild": "v5.1.4 (08.09.2024)",
     *       "IP": "192.168.1.50",
     *       "TabLight": 1,
     *       "TabTimer": 1,
     *       "TabTemperature": 0
     *     }
     *   },
     *   "VerUdp": 20240813
     * }
     */
    private fun listenForUdpBroadcasts(
        port: Int,
        timeoutMillis: Int
    ): List<UdpDeviceUi> {
        val result = LinkedHashMap<String, UdpDeviceUi>() // IP bazlı uniq
        val tag = "NetworkFragment"

        try {
            DatagramSocket(port).use { socket ->
                socket.soTimeout = timeoutMillis

                val buffer = ByteArray(4096)

                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        // Süre doldu → dinlemeyi bırak
                        break
                    }

                    val senderIp = packet.address?.hostAddress ?: continue
                    val length = packet.length
                    val payload = buffer.copyOf(length).toString(Charsets.UTF_8).trim()

                    try {
                        val device = parseEsp32UdpJson(senderIp, payload)
                        if (device != null) {
                            // Aynı IP'den birden fazla paket gelse bile son görüleni yaz
                            result[senderIp] = device
                        } else {
                            // JSON değilse / beklenen formatta değilse fallback
                            result[senderIp] = UdpDeviceUi(
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
                        Log.e(tag, "UDP JSON parse hatası: $payload", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "UDP dinleme hatası", e)
        }

        return result.values.toList()
    }

    /**
     * ESP32'nin gönderdiği JSON'u parse edip UdpDeviceUi nesnesine çevirir.
     */
    private fun parseEsp32UdpJson(
        senderIp: String,
        payload: String
    ): UdpDeviceUi? {
        val root = try {
            JSONObject(payload)
        } catch (e: Exception) {
            return null
        }

        val dataObj = root.optJSONObject("Data") ?: return null
        val dev0 = dataObj.optJSONObject("0") ?: return null

        val name = dev0.optString("Name", "")
        val aquaName = dev0.optString("AquaName", null)
        val cloneName = dev0.optString("CloneName", null)
        val firmware = dev0.optString("FirmwareBuild", null)

        // IP hem JSON içinden hem de gönderen soketten gelebilir;
        // JSON'da yoksa senderIp'yi kullan
        val ipFromJson = dev0.optString("IP", null)
        val finalIp = if (!ipFromJson.isNullOrBlank()) ipFromJson else senderIp

        // TabLight / TabTimer / TabTemperature ESP32 tarafında int olarak gönderiliyor (0/1)
        val hasLight = dev0.optInt("TabLight", 0) != 0
        val hasTimer = dev0.optInt("TabTimer", 0) != 0
        val hasTemp = dev0.optInt("TabTemperature", 0) != 0

        return UdpDeviceUi(
            ip = finalIp,
            name = name.ifBlank { "Aqua device" },
            aquaName = aquaName,
            cloneName = cloneName,
            firmware = firmware,
            hasLight = hasLight,
            hasTimer = hasTimer,
            hasTemperature = hasTemp,
            lastSeenMillis = System.currentTimeMillis()
        )
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

            // Birincil başlık: mümkünse AquaName, yoksa Name
            val mainName = when {
                !device.aquaName.isNullOrBlank() -> device.aquaName
                device.name.isNotBlank() -> device.name
                else -> getString(R.string.network_device_default_name) // "Aqua device" gibi bir string tanımlarsın
            }

            // Alt başlığa CloneName ve özellik etiketlerini ekleyebiliriz
            val tags = buildList {
                if (device.hasLight) add(getString(R.string.network_tag_light))         // "Light"
                if (device.hasTimer) add(getString(R.string.network_tag_timer))         // "Timer"
                if (device.hasTemperature) add(getString(R.string.network_tag_temp))    // "Temp"
            }.joinToString(separator = " · ")

            val timeText = timeFormatter.format(Date(device.lastSeenMillis))

            tvName.text = buildString {
                append(mainName)
                if (!device.cloneName.isNullOrBlank()) {
                    append(" • ")
                    append(device.cloneName)
                }
            }

            // Örn: "192.168.1.20 • 21:35 • v5.1.4 (08.09.2024) • Light · Timer"
            tvInfo.text = buildString {
                append(device.ip)
                append(" • ")
                append(timeText)

                if (!device.firmware.isNullOrBlank()) {
                    append(" • ")
                    append(device.firmware)
                }

                if (tags.isNotBlank()) {
                    append(" • ")
                    append(tags)
                }
            }

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