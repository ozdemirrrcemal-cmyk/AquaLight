package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
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

        setupTemperatureChart(
            chart = binding.temperatureChartView
        )

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

    private fun setupTemperatureChart(
        chart: LineChart
    ) {
        chart.description.isEnabled = false
        chart.setNoDataText("No temperature data")
        chart.setNoDataTextColor(Color.parseColor("#AAB6C5"))

        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.setBackgroundColor(Color.TRANSPARENT)

        chart.legend.isEnabled = true
        chart.legend.textColor = Color.parseColor("#D7E1EF")
        chart.legend.textSize = 11f
        chart.legend.formSize = 10f

        chart.axisRight.isEnabled = false

        chart.axisLeft.apply {
            textColor = Color.parseColor("#AAB6C5")
            textSize = 10f
            gridColor = Color.parseColor("#243A57")
            axisLineColor = Color.parseColor("#3B5578")
            setDrawZeroLine(false)
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.parseColor("#AAB6C5")
            textSize = 10f
            gridColor = Color.parseColor("#243A57")
            axisLineColor = Color.parseColor("#3B5578")
            granularity = 72f
            setDrawAxisLine(true)
            setDrawGridLines(true)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(
                    value: Float
                ): String {
                    val totalMinutes = value.toInt() * 5
                    val hour = (totalMinutes / 60).coerceIn(0, 24)

                    return when (hour) {
                        0 -> "00"
                        6 -> "06"
                        12 -> "12"
                        18 -> "18"
                        24 -> "24"
                        else -> ""
                    }
                }
            }
        }

        chart.extraBottomOffset = 8f
        chart.extraLeftOffset = 4f
        chart.extraRightOffset = 12f
        chart.invalidate()
    }

    private fun loadTemperatureGraph() {
        val currentBinding = _binding ?: return

        if (deviceIp.isBlank()) {
            currentBinding.tvGraphStatus.text = "Device IP is missing."
            currentBinding.tvCurrentTemperature.text = "Current: -- °C"
            clearTemperatureChart()
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

    private fun renderTemperatureChart(
        sensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ) {
        val dataSets = sensors.mapNotNull { sensor ->
            val entries = sensor.history
                .mapIndexedNotNull { index, value ->
                    if (value != null && value > -100f && value < 200f) {
                        Entry(
                            index.toFloat(),
                            value
                        )
                    } else {
                        null
                    }
                }

            if (entries.isEmpty()) {
                return@mapNotNull null
            }

            LineDataSet(
                entries,
                sensor.name
            ).apply {
                color = sensor.color
                lineWidth = 2.4f

                setDrawCircles(false)
                setDrawValues(false)

                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.18f

                highLightColor = Color.WHITE
                highlightLineWidth = 1f

                setDrawFilled(false)
            }
        }

        if (dataSets.isEmpty()) {
            clearTemperatureChart()
            return
        }

        binding.temperatureChartView.data = LineData(
            dataSets
        ).apply {
            setValueTextColor(Color.TRANSPARENT)
        }

        binding.temperatureChartView.animateX(450)
        binding.temperatureChartView.invalidate()
    }

    private fun clearTemperatureChart() {
        val safeBinding = _binding ?: return

        safeBinding.temperatureChartView.clear()
        safeBinding.temperatureChartView.invalidate()
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