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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

    // Sürekli UDP dinleme için job
    private var udpListenJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNetworkBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 📡 Bağlantı bilgisi
        bindConnectionStatus()

        // 🧩 UDP cihazlarını sürekli dinle
        startUdpScanLoop()
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
    // 🧩 Sürekli UDP tarama (ESP32: port 10888)
    // ----------------------------------------------------
    private fun startUdpScanLoop() {
        // İlk girişte "tarama" yazsın
        binding.deviceListContainer.removeAllViews()
        binding.tvNoDevices.visibility = View.VISIBLE
        binding.tvNoDevices.text = getString(R.string.network_devices_scanning)

        // Eski job varsa iptal et
        udpListenJob?.cancel()

        udpListenJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // IP bazlı cache, son görülen cihazlar
            val devicesMap = LinkedHashMap<String, UdpDeviceUi>()

            val port = 10888              // ESP32 UDPlocalPort
            val listenTimeoutMs = 5000    // her turda max 5 sn bekle
            val staleTimeoutMs = 3 * 60_000L // 3 dk görmediysek listeden düş

            while (isActive) {
                // Bir tur UDP dinle
                val newDevices = listenForUdpBroadcasts(
                    port = port,
                    timeoutMillis = listenTimeoutMs
                )

                val now = System.currentTimeMillis()

                // Yeni gelenleri map'e işle
                newDevices.forEach { dev ->
                    devicesMap[dev.ip] = dev.copy(lastSeenMillis = now)
                }

                // Zaman aşımına uğrayanları filtrele
                val visibleDevices = devicesMap.values
                    .filter { now - it.lastSeenMillis <= staleTimeoutMs }
                    .sortedBy { it.aquaName ?: it.name }

                withContext(Dispatchers.Main) {
                    bindDevicesToUi(visibleDevices)
                }
            }
        }
    }

    /**
     * Bir tur UDP dinler, o sürede gelen cihazları döner.
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
                        // Süre doldu → bu tur bitti
                        break
                    }

                    val senderIp = packet.address?.hostAddress ?: continue
                    val length = packet.length
                    val payload = buffer.copyOf(length).toString(Charsets.UTF_8).trim()

                    try {
                        val device = parseEsp32UdpJson(senderIp, payload)
                        if (device != null) {
                            result[senderIp] = device
                        } else {
                            // Beklenmeyen formatta paket gelirse bile en azından IP'yi göster
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

        devices.forEach { device ->
            val row = inflater.inflate(
                R.layout.simple_list_item_udp_device,
                container,
                false
            ) as ViewGroup

            val tvName = row.findViewById<TextView>(R.id.tvDeviceName)
            val tvInfo = row.findViewById<TextView>(R.id.tvDeviceInfo)

            // 1. satır: AquaName (varsa) yoksa Name
            val mainName = when {
                !device.aquaName.isNullOrBlank() -> device.aquaName
                device.name.isNotBlank() -> device.name
                else -> getString(R.string.network_device_default_name)
            }

            tvName.text = mainName

            // 2. satır: "IP • Name"
            tvInfo.text = buildString {
                append(device.ip)
                if (device.name.isNotBlank()) {
                    append(" • ")
                    append(device.name)
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
        // UDP dinlemeyi durdur
        udpListenJob?.cancel()
        udpListenJob = null
        _binding = null
    }
}