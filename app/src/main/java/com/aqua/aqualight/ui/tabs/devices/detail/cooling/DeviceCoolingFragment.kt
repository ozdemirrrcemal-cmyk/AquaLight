package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
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
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceCoolingBinding.bind(view)

        setupTemperatureChart(binding.temperatureChartView)

        binding.tvControllerTitle.text = "Cooling Controller"

        binding.btnRefreshTemperature.setOnClickListener {
            loadTemperatureGraph()
        }

        loadTemperatureGraph()
    }

    private fun setupTemperatureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("No temperature data")
        chart.setNoDataTextColor(Color.parseColor("#AAB6C5"))

        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.setBackgroundColor(Color.TRANSPARENT)

        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.setHighlightPerTapEnabled(false)
        chart.setHighlightPerDragEnabled(false)

        chart.legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#D7E1EF")
            textSize = 11f
            form = Legend.LegendForm.SQUARE
            formSize = 10f
            xEntrySpace = 10f
            yEntrySpace = 6f
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
        }

        chart.axisRight.isEnabled = false

        chart.axisLeft.apply {
            textColor = Color.parseColor("#AAB6C5")
            textSize = 10f
            gridColor = Color.parseColor("#243A57")
            axisLineColor = Color.parseColor("#3B5578")
            setDrawZeroLine(false)
            setDrawGridLines(true)
            setDrawAxisLine(false)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format(Locale.US, "%.0f°", value)
                }
            }
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM

            textColor = Color.parseColor("#AAB6C5")
            textSize = 10f

            gridColor = Color.parseColor("#243A57")
            axisLineColor = Color.parseColor("#3B5578")

            setDrawAxisLine(false)
            setDrawGridLines(true)
            setAvoidFirstLastClipping(true)

            axisMinimum = 0f
            axisMaximum = 288f

            granularity = 72f
            setLabelCount(5, true)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (value.toInt()) {
                        0 -> "00:00"
                        72 -> "06:00"
                        144 -> "12:00"
                        216 -> "18:00"
                        288 -> "24:00"
                        else -> ""
                    }
                }
            }
        }

        chart.minOffset = 0f
        chart.extraTopOffset = 8f
        chart.extraBottomOffset = 12f
        chart.extraLeftOffset = 4f
        chart.extraRightOffset = 10f

        chart.invalidate()
    }

    private fun loadTemperatureGraph() {
        val currentBinding = _binding ?: return

        if (deviceIp.isBlank()) {
            clearTemperatureChart()
            return
        }

        currentBinding.btnRefreshTemperature.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.fetchTemperatureData(deviceIp)
            }

            val safeBinding = _binding ?: return@launch

            result.onSuccess { data ->
                renderTemperatureChart(
                    sensors = data.sensors
                )
            }.onFailure {
                clearTemperatureChart()
            }

            safeBinding.btnRefreshTemperature.isEnabled = true
        }
    }

    private fun renderTemperatureChart(
        sensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ) {
        val safeBinding = _binding ?: return

        val dataSets = mutableListOf<LineDataSet>()
        val legendEntries = mutableListOf<LegendEntry>()

        sensors.forEach { sensor ->
            val segments = buildContinuousSegments(sensor.history)

            if (segments.isEmpty()) {
                return@forEach
            }

            legendEntries.add(
                LegendEntry(
                    sensor.name,
                    Legend.LegendForm.SQUARE,
                    10f,
                    2f,
                    null,
                    sensor.color
                )
            )

            segments.forEach { entries ->
                if (entries.isEmpty()) {
                    return@forEach
                }

                val dataSet = LineDataSet(
                    entries,
                    sensor.name
                ).apply {
                    color = sensor.color
                    lineWidth = 2.6f

                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(false)

                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.15f

                    setHighlightEnabled(false)
                    setDrawHorizontalHighlightIndicator(false)
                    setDrawVerticalHighlightIndicator(false)
                }

                dataSets.add(dataSet)
            }
        }

        if (dataSets.isEmpty()) {
            clearTemperatureChart()
            return
        }

        safeBinding.temperatureChartView.legend.setCustom(legendEntries)

        safeBinding.temperatureChartView.data = LineData(dataSets).apply {
            setValueTextColor(Color.TRANSPARENT)
        }

        safeBinding.temperatureChartView.notifyDataSetChanged()
        safeBinding.temperatureChartView.invalidate()
    }

    private fun buildContinuousSegments(
        history: List<Float?>
    ): List<List<Entry>> {
        val segments = mutableListOf<List<Entry>>()
        val currentSegment = mutableListOf<Entry>()

        history.forEachIndexed { index, value ->
            val validValue = value != null && value > -100f && value < 200f

            if (validValue) {
                currentSegment.add(
                    Entry(index.toFloat(), value ?: return@forEachIndexed)
                )
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.toList())
                    currentSegment.clear()
                }
            }
        }

        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toList())
        }

        return segments
    }

    private fun clearTemperatureChart() {
        val safeBinding = _binding ?: return

        safeBinding.temperatureChartView.clear()
        safeBinding.temperatureChartView.legend.resetCustom()
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