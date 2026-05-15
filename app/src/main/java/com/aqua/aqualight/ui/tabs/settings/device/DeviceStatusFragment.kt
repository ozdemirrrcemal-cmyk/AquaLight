package com.aqua.aqualight.ui.tabs.settings.device

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.ui.tabs.devices.model.DeviceType
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

class DeviceStatusFragment : Fragment(R.layout.fragment_device_status) {

    private var _binding: FragmentDeviceStatusBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceStatusAdapter
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    companion object {
        private const val ONLINE_TIMEOUT_MS = 60_000L
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceStatusBinding.bind(view)

        setupRecycler()
        observeDevices()

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupRecycler() {
        adapter = DeviceStatusAdapter()
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun observeDevices() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.devicesFlow.collectLatest { list ->
                val now = System.currentTimeMillis()

                // Boş cihaz listesi kontrolü
                if (list.isEmpty()) {
                    binding.rvDevices.visibility = View.GONE
                    return@collectLatest
                } else {
                    binding.rvDevices.visibility = View.VISIBLE
                }

                val uiList = list.map { dev ->
                    val online = dev.lastSeenMillis != 0L &&
                                 (now - dev.lastSeenMillis <= ONLINE_TIMEOUT_MS)

                    val lastSeenText = if (dev.lastSeenMillis != 0L)
                        formatElapsedTime(now - dev.lastSeenMillis)
                    else
                        "Never"

                    DeviceCardUi(
                        id = dev.id,
                        name = dev.name.ifBlank { "Device" },
                        aquaName = dev.aquaName,
                        ip = dev.ip,
                        serial = dev.serial,
                        firmwareBuild = dev.firmwareBuild,
                        isOnline = online,
                        lastSeenText = lastSeenText,
                        type = DeviceType.fromName(dev.aquaName)
                    )
                }

                adapter.submitList(uiList)
            }
        }
    }

    // ---------------------------------------------------
    // Last seen hesaplama fonksiyonu
    // ---------------------------------------------------
    private fun formatElapsedTime(deltaMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
        val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
        val days = TimeUnit.MILLISECONDS.toDays(deltaMs)

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            else -> "$days d ago"
        }
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }
}