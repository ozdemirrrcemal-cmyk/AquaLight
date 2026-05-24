package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class TemperatureSeries(
        val name: String,
        val values: List<Float?>,
        val color: Int
    )

    private val series = mutableListOf<TemperatureSeries>()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        strokeWidth = 1f
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        textSize = 28f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    fun setTemperatureSeries(
        newSeries: List<TemperatureSeries>
    ) {
        series.clear()
        series.addAll(newSeries)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 58f
        val top = 24f
        val right = width - 18f
        val bottom = height - 54f

        if (series.isEmpty() || series.all { item -> item.values.all { it == null } }) {
            canvas.drawText(
                "No temperature data",
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val allValues = series
            .flatMap { it.values }
            .filterNotNull()
            .filter { it > -100f && it < 200f }

        if (allValues.isEmpty()) {
            canvas.drawText(
                "No valid temperature data",
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        var minTemp = floor(allValues.minOrNull() ?: 20f)
        var maxTemp = ceil(allValues.maxOrNull() ?: 30f)

        if (maxTemp - minTemp < 4f) {
            minTemp -= 2f
            maxTemp += 2f
        }

        minTemp = max(-50f, minTemp)
        maxTemp = min(100f, maxTemp)

        drawGrid(
            canvas = canvas,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            minTemp = minTemp,
            maxTemp = maxTemp
        )

        drawLines(
            canvas = canvas,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            minTemp = minTemp,
            maxTemp = maxTemp
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minTemp: Float,
        maxTemp: Float
    ) {
        val graphWidth = right - left
        val graphHeight = bottom - top

        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        canvas.drawLine(right, top, right, bottom, axisPaint)

        val horizontalLines = 6
        for (i in 0..horizontalLines) {
            val y = top + graphHeight * i / horizontalLines
            canvas.drawLine(left, y, right, y, gridPaint)

            val temp = maxTemp - ((maxTemp - minTemp) * i / horizontalLines)
            canvas.drawText(
                temp.toInt().toString(),
                right - 34f,
                y - 6f,
                labelPaint
            )
        }

        val verticalLines = 12
        for (i in 0..verticalLines) {
            val x = left + graphWidth * i / verticalLines
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }

        val labels = listOf(
            0 to "0:00",
            6 to "6:00",
            12 to "12:00",
            18 to "18:00",
            24 to "24:00"
        )

        labels.forEach { item ->
            val x = left + graphWidth * item.first / 24f
            canvas.save()
            canvas.rotate(-45f, x, bottom + 38f)
            canvas.drawText(item.second, x - 28f, bottom + 38f, labelPaint)
            canvas.restore()
        }
    }

    private fun drawLines(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minTemp: Float,
        maxTemp: Float
    ) {
        val graphWidth = right - left
        val graphHeight = bottom - top

        series.forEach { item ->
            if (item.values.isEmpty()) return@forEach

            val path = Path()
            var pathStarted = false
            val maxIndex = item.values.size - 1

            item.values.forEachIndexed { index, value ->
                if (value == null || value <= -100f || value >= 200f) {
                    pathStarted = false
                    return@forEachIndexed
                }

                val x = left + graphWidth * index / maxIndex.coerceAtLeast(1)
                val y = bottom - graphHeight * ((value - minTemp) / (maxTemp - minTemp))

                if (!pathStarted) {
                    path.moveTo(x, y)
                    pathStarted = true
                } else {
                    path.lineTo(x, y)
                }
            }

            linePaint.color = item.color
            canvas.drawPath(path, linePaint)
        }
    }
}