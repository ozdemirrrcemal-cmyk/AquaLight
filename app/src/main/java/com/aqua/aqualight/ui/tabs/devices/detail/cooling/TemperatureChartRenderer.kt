package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.aqua.aqualight.ui.tabs.devices.detail.DeviceVisualSpec
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.MPPointF
import java.util.Locale

class TemperatureChartRenderer(
    private val chart: LineChart,
    private val visualSpec: DeviceVisualSpec
) {

    fun setup() {
        chart.description.isEnabled = false
        chart.setNoDataText("No temperature data")
        chart.setNoDataTextColor(visualSpec.chartTextColor)

        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.setBackgroundColor(Color.TRANSPARENT)

        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.setHighlightPerTapEnabled(true)
        chart.setHighlightPerDragEnabled(true)

        chart.marker = TemperatureMarkerView(
            context = chart.context
        )

        chart.legend.apply {
            isEnabled = true
            textColor = visualSpec.chartTextColor
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
            textColor = visualSpec.chartTextColor
            textSize = 10f
            gridColor = visualSpec.chartGridColor
            axisLineColor = visualSpec.cardStrokeColor

            setDrawZeroLine(false)
            setDrawGridLines(true)
            setDrawAxisLine(false)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(
                    value: Float
                ): String {
                    return String.format(
                        Locale.US,
                        "%.0f°",
                        value
                    )
                }
            }
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM

            textColor = visualSpec.chartTextColor
            textSize = 9f

            gridColor = visualSpec.chartGridColor
            axisLineColor = visualSpec.cardStrokeColor

            setDrawAxisLine(false)
            setDrawGridLines(true)
            setAvoidFirstLastClipping(true)

            axisMinimum = 0f
            axisMaximum = 288f

            // ESP32: 1 point = 5 min. 12 point = 1 hour.
            // This gives hourly vertical grid positions.
            granularity = 12f

            setLabelCount(
                25,
                true
            )

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(
                    value: Float
                ): String {
                    return when (value.toInt()) {
                        0 -> "00:00"
                        48 -> "04:00"
                        96 -> "08:00"
                        144 -> "12:00"
                        192 -> "16:00"
                        240 -> "20:00"
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

    fun render(
        sensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ) {
        val dataSets = mutableListOf<ILineDataSet>()
        val legendEntries = mutableListOf<LegendEntry>()

        sensors.forEach { sensor ->
            val segments = buildContinuousSegments(
                history = sensor.history,
                sensorName = sensor.name
            )

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
                    lineWidth = 1.8f

                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(false)

                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.08f

                    highLightColor = visualSpec.accentColor
                    highlightLineWidth = 0.8f

                    setDrawHorizontalHighlightIndicator(false)
                    setDrawVerticalHighlightIndicator(true)
                }

                dataSets.add(dataSet)
            }
        }

        if (dataSets.isEmpty()) {
            clear()
            return
        }

        chart.legend.setCustom(
            legendEntries
        )

        chart.data = LineData(
            dataSets
        ).apply {
            setValueTextColor(Color.TRANSPARENT)
        }

        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    fun clear() {
        chart.clear()
        chart.legend.resetCustom()
        chart.invalidate()
    }

    private fun buildContinuousSegments(
        history: List<Float?>,
        sensorName: String
    ): List<List<Entry>> {
        val segments = mutableListOf<List<Entry>>()
        val currentSegment = mutableListOf<Entry>()

        history.forEachIndexed { index, value ->
            val validValue = value != null && value > -100f && value < 200f

            if (validValue) {
                currentSegment.add(
                    Entry(
                        index.toFloat(),
                        value ?: return@forEachIndexed,
                        sensorName
                    )
                )
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(
                        currentSegment.toList()
                    )
                    currentSegment.clear()
                }
            }
        }

        if (currentSegment.isNotEmpty()) {
            segments.add(
                currentSegment.toList()
            )
        }

        return segments
    }

    private class TemperatureMarkerView(
        context: Context
    ) : MarkerView(
        context,
        android.R.layout.simple_list_item_1
    ) {

        private val textView: TextView = findViewById(
            android.R.id.text1
        )

        init {
            textView.setTextColor(Color.WHITE)
            textView.textSize = 12f
            textView.setPadding(
                18,
                12,
                18,
                12
            )

            background = GradientDrawable().apply {
                setColor(
                    Color.parseColor("#182A43")
                )
                cornerRadius = 18f
                setStroke(
                    1,
                    Color.parseColor("#2D4567")
                )
            }
        }

        override fun refreshContent(
            e: Entry?,
            highlight: Highlight?
        ) {
            if (e == null) {
                super.refreshContent(
                    e,
                    highlight
                )
                return
            }

            val timeText = formatTimeFromIndex(
                index = e.x.toInt()
            )

            val temperatureText = String.format(
                Locale.US,
                "%.1f °C",
                e.y
            )

            val sensorName = e.data as? String ?: ""

            textView.text = if (sensorName.isBlank()) {
                "$timeText\n$temperatureText"
            } else {
                "$timeText\n$temperatureText\n$sensorName"
            }

            super.refreshContent(
                e,
                highlight
            )
        }

        override fun getOffset(): MPPointF {
            return MPPointF(
                -(width / 2f),
                -height.toFloat() - 18f
            )
        }

        private fun formatTimeFromIndex(
            index: Int
        ): String {
            val totalMinutes = (index * 5).coerceIn(
                0,
                24 * 60
            )

            val hour = totalMinutes / 60
            val minute = totalMinutes % 60

            return String.format(
                Locale.US,
                "%02d:%02d",
                hour,
                minute
            )
        }
    }
}