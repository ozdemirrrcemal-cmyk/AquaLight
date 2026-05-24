package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceCoolingFragment : Fragment(R.layout.fragment_device_cooling) {

    private var _binding: FragmentDeviceCoolingBinding? = null
    private val binding get() = _binding!!

    private val repository = CoolingDeviceRepository()

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceCoolingBinding.bind(view)

        binding.tvControllerTitle.text = "Cooling Controller"
        binding.tvControllerDescription.text = "Device ID: $deviceId"

        binding.btnRefreshTemperature.setOnClickListener {
            loadTemperatureGraph()
        }

        loadTemperatureGraph()
    }

    private fun loadTemperatureGraph() {
        if (deviceIp.isBlank()) {
            binding.tvGraphStatus.text = "Device IP is missing."
            return
        }

        binding.tvGraphStatus.text = "Loading temperature data..."
        binding.btnRefreshTemperature.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.fetchTemperatureData(deviceIp)
            }.onSuccess { data ->
                val sensors = data.sensors

                if (sensors.isEmpty()) {
                    binding.tvCurrentTemperature.text = "Current: -- °C"
                    binding.tvGraphStatus.text = "No temperature sensor found."
                    binding.temperatureChartView.setTemperatureSeries(emptyList())
                    return@onSuccess
                }

                val hottestSensor = sensors
                    .filter { it.currentTemperature != null }
                    .maxByOrNull { it.currentTemperature ?: -999f }

                val currentText = hottestSensor?.currentTemperature?.let { value ->
                    String.format(
                        Locale.US,
                        "Current: %.1f °C • %s",
                        value,
                        hottestSensor.name
                    )
                } ?: "Current: -- °C"

                binding.tvCurrentTemperature.text = currentText

                binding.temperatureChartView.setTemperatureSeries(
                    sensors.map { sensor ->
                        TemperatureChartView.TemperatureSeries(
                            name = sensor.name,
                            values = sensor.history,
                            color = sensor.color
                        )
                    }
                )

                binding.tvGraphStatus.text =
                    "Loaded ${sensors.size} sensor(s) from ${data.ip ?: deviceIp}"
            }.onFailure { error ->
                binding.tvGraphStatus.text =
                    "Could not read temperature data: ${error.message}"
            }

            binding.btnRefreshTemperature.isEnabled = true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"

        fun newInstance(
            deviceId: Long,
            deviceIp: String
        ): DeviceCoolingFragment {
            return DeviceCoolingFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                    putString(ARG_DEVICE_IP, deviceIp)
                }
            }
        }
    }
}