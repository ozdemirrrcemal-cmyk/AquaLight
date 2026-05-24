package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.Locale

class TemperatureChartRenderer(
    private val chart: LineChart
) {

    fun setup() {
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
            setLabelCount(
                5,
                true
            )

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(
                    value: Float
                ): String {
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

    fun render(
        sensors: List<CoolingDeviceRepository.TemperatureSensorData>
    ) {
        val dataSets = mutableListOf<ILineDataSet>()
        val legendEntries = mutableListOf<LegendEntry>()

        sensors.forEach { sensor ->
            val segments = buildContinuousSegments(
                history = sensor.history
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
        history: List<Float?>
    ): List<List<Entry>> {
        val segments = mutableListOf<List<Entry>>()
        val currentSegment = mutableListOf<Entry>()

        history.forEachIndexed { index, value ->
            val validValue = value != null && value > -100f && value < 200f

            if (validValue) {
                currentSegment.add(
                    Entry(
                        index.toFloat(),
                        value ?: return@forEachIndexed
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
}