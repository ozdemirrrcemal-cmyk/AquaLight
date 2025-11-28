package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentScanDevicesBinding
import kotlinx.coroutines.launch
import kotlin.random.Random

class ScanDevicesFragment : Fragment(R.layout.fragment_scan_devices) {

    private var _binding: FragmentScanDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanDevicesAdapter

    // Son bulunan cihazlar
    private var currentDevices: List<DiscoveredDevice> = emptyList()

    // false = radar görünümü, true = liste görünümü
    private var isListMode: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanDevicesBinding.bind(view)

        // Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Sağ üst buton:
        // - hiç cihaz yoksa: tarama başlat
        // - cihaz varsa: radar <-> liste arasında geçiş
        binding.btnRescan.setOnClickListener {
            if (currentDevices.isEmpty()) {
                startScan()
            } else {
                isListMode = !isListMode
                updateUiMode()
            }
        }

        setupRecyclerView()
        startScan()
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device ->
            saveSelectedDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    // 🔄 TARAMA BAŞLAT
    private fun startScan() {
        isListMode = false
        currentDevices = emptyList()
        adapter.submitList(emptyList())
        updateUiMode(isScanning = true)

        lifecycleScope.launch {
            val devices: List<DiscoveredDevice> =
                discoverDevices(requireContext(), timeoutMs = 3000L)

            currentDevices = devices
            adapter.submitList(devices)
            updateUiMode()
        }
    }

    // 🔁 EKRAN MODLARI
    // isScanning = true -> "Searching..." + radar açık, liste kapalı
    // isScanning = false -> cihaz durumuna göre radar/liste/empty
    private fun updateUiMode(isScanning: Boolean = false) {

        if (isScanning) {
            // Başlık: Searching...
            binding.tvTitle.text = getString(R.string.device_scan_header_scanning)

            binding.radarContainer.visibility = View.VISIBLE
            binding.scanAnimation.visibility = View.VISIBLE
            binding.scanAnimation.playAnimation()
            binding.deviceMarkers.removeAllViews()

            binding.tvScanning.visibility = View.VISIBLE
            binding.rvDevices.visibility = View.GONE
            binding.tvNoDevices.visibility = View.GONE

            // buton: radar (tara)
            binding.btnRescan.setImageResource(R.drawable.ic_radar)
            return
        }

        // Tarama bitti
        binding.tvScanning.visibility = View.GONE

        if (currentDevices.isEmpty()) {
            // ❌ Cihaz yok
            binding.tvTitle.text = getString(R.string.device_scan_header_list)

            binding.radarContainer.visibility = View.GONE
            binding.rvDevices.visibility = View.GONE
            binding.tvNoDevices.visibility = View.VISIBLE
            binding.deviceMarkers.removeAllViews()

            binding.btnRescan.setImageResource(R.drawable.ic_radar) // tekrar tara
        } else {
            // ✅ Cihaz var
            binding.tvTitle.text = getString(R.string.device_scan_header_list)
            binding.tvNoDevices.visibility = View.GONE

            if (isListMode) {
                // 📋 Liste görünümü
                binding.radarContainer.visibility = View.GONE
                binding.rvDevices.visibility = View.VISIBLE
                binding.btnRescan.setImageResource(R.drawable.ic_radar) // geri radar
            } else {
                // 🛰 Radar görünümü + baloncuklar
                binding.radarContainer.visibility = View.VISIBLE
                binding.rvDevices.visibility = View.GONE
                binding.btnRescan.setImageResource(R.drawable.ic_list) // listeye geç

                binding.scanAnimation.visibility = View.VISIBLE
                if (!binding.scanAnimation.isAnimating) {
                    binding.scanAnimation.playAnimation()
                }
                showDeviceMarkers(currentDevices)
            }
        }
    }

    // Radar üstüne cihaz balonlarını koy
    private fun showDeviceMarkers(devices: List<DiscoveredDevice>) {
        val container = binding.deviceMarkers
        container.removeAllViews()

        if (devices.isEmpty()) return

        val ctx = requireContext()
        val radiusPx = binding.scanAnimation.width / 2f
        val centerX = radiusPx
        val centerY = radiusPx

        // En fazla 4 cihazı göster
        devices.take(4).forEachIndexed { index, dev ->
            val tv = TextView(ctx).apply {
                text = dev.aquaName ?: dev.name ?: "Device"
                setTextAppearance(R.style.TextAppearance_Aqua_Body)
                setBackgroundResource(R.drawable.bg_device_marker)
                setPadding(16, 6, 16, 6)
            }

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            // Basit: farklı açılara dağıt (0, 90, 180, 270 derece)
            val angleDeg = 90f * index
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val r = radiusPx * 0.6f

            val dx = (r * Math.cos(angleRad)).toFloat()
            val dy = (r * Math.sin(angleRad)).toFloat()

            lp.leftMargin = (centerX + dx).toInt()
            lp.topMargin = (centerY + dy).toInt()

            tv.layoutParams = lp
            container.addView(tv)
        }
    }

    private fun saveSelectedDevice(device: DiscoveredDevice) {
        // Burada DataStore’a yazacaksın (şimdilik sadece geri dön)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}