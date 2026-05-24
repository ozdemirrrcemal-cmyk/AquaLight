package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.lineSeries
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceCoolingFragment : Fragment(R.layout.fragment_device_cooling) {

    private var _binding: FragmentDeviceCoolingBinding? = null
    private val binding get() = _binding!!

    private val repository = CoolingDeviceRepository()
    private val chartModelProducer = CartesianChartModelProducer()

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

        binding.temperatureChartView.modelProducer = chartModelProducer

        binding.tvControllerTitle.text = "Cooling Controller"

        binding.tvControllerDescription.text = if (deviceIp.isBlank()) {
            "Device ID: $deviceId"
        } else {
            "Device ID: $deviceId • IP: $deviceIp"
        }

        binding.btnRefreshTemperature.setOnClickListener {
            loadTemperatureGraph()
        }

        loadTemperatureGraph()
    }

    private fun loadTemperatureGraph() {
        val currentBinding = _binding ?: return

        if (deviceIp.isBlank()) {
            currentBinding.tvGraphStatus.text = "Device IP is missing."
            currentBinding.tvCurrentTemperature.text = "Current: -- °C"

            viewLifecycleOwner.lifecycleScope.launch {
                clearTemperatureChart()
            }

            return
        }

        currentBinding.tvGraphStatus.text = "Loading temperature data..."
        currentBinding.btnRefreshTemperature.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.fetchTemperatureData(deviceIp)
            }

            val safeBinding = _binding ?: return@launch

            result.onSuccess { data ->
                val sensors = data.sensors

                if (sensors.isEmpty()) {
                    safeBinding.tvCurrentTemperature.text = "Current: -- °C"
                    safeBinding.tvGraphStatus.text = "No temperature sensor found."
                    clearTemperatureChart()
                    safeBinding.btnRefreshTemperature.isEnabled = true
                    return@onSuccess
                }

                val hottestSensor = sensors
                    .filter { sensor ->
                        sensor.currentTemperature != null
                    }
                    .maxByOrNull { sensor ->
                        sensor.currentTemperature ?: -999f
                    }

                val currentText = hottestSensor?.currentTemperature?.let { value ->
                    String.format(
                        Locale.US,
                        "Current: %.1f °C • %s",
                        value,
                        hottestSensor.name
                    )
                } ?: "Current: -- °C"

                safeBinding.tvCurrentTemperature.text = currentText

                renderTemperatureChart(
                    sensors = sensors
                )

                safeBinding.tvGraphStatus.text =
                    "Loaded ${sensors.size} sensor(s) from ${data.ip ?: deviceIp}"
            }.onFailure { error ->
                safeBinding.tvCurrentTemperature.text = "Current: -- °C"
                safeBinding.tvGraphStatus.text =
                    "Could not read temperature data: ${error.message}"

                clearTemperatureChart()
            }

            safeBinding.btnRefreshTemperature.isEnabled = true
        }
    }

    private suspend fun renderTemperatureChart(
        sensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ) {
        val validSensorSeries = sensors.mapNotNull { sensor ->
            val points = sensor.history
                .mapIndexedNotNull { index, value ->
                    if (value != null && value > -100f && value < 200f) {
                        index to value
                    } else {
                        null
                    }
                }

            if (points.isEmpty()) {
                null
            } else {
                points
            }
        }

        if (validSensorSeries.isEmpty()) {
            clearTemperatureChart()
            return
        }

        chartModelProducer.runTransaction {
            lineSeries {
                validSensorSeries.forEach { points ->
                    series(
                        points.map { point -> point.first },
                        points.map { point -> point.second }
                    )
                }
            }
        }
    }

    private suspend fun clearTemperatureChart() {
        chartModelProducer.runTransaction {
            // Empty transaction clears the chart model.
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