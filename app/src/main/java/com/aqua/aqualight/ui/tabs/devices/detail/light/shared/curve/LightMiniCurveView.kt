package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R

class LightMiniCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var chartData: LightCurveChartData? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp()
    }

    fun submitData(
        data: LightCurveChartData?
    ) {
        chartData = data
        invalidate()
    }

    fun clear() {
        chartData = null
        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val data = chartData ?: return
        if (!data.hasData || width <= 0 || height <= 0) {
            return
        }

        val left = 0f
        val right = width.toFloat()
        val top = 3f.dp()
        val bottom = height - 3f.dp()

        baselinePaint.color = ContextCompat.getColor(
            context,
            R.color.light_stroke
        )

        canvas.drawLine(
            left,
            bottom,
            right,
            bottom,
            baselinePaint
        )

        data.series.forEach { series ->
            drawSeries(
                canvas = canvas,
                series = series,
                left = left,
                right = right,
                top = top,
                bottom = bottom
            )
        }
    }

    private fun drawSeries(
        canvas: Canvas,
        series: LightCurveSeries,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val points =
            series.points
                .sortedBy { point ->
                    point.minuteOfDay
                }

        if (points.isEmpty()) {
            return
        }

        linePaint.color = channelColor(series.channel)
        linePaint.alpha =
            if (series.isActive) {
                255
            } else {
                80
            }

        val path = Path()

        points.forEachIndexed { index, point ->
            val x = minuteToX(
                minute = point.minuteOfDay,
                left = left,
                right = right
            )

            val y = intensityToY(
                intensity = point.intensityPercent,
                top = top,
                bottom = bottom
            )

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(
            path,
            linePaint
        )
    }

    private fun channelColor(
        channel: LightCurveChannel
    ): Int {
        val colorRes =
            when (channel) {
                LightCurveChannel.MASTER -> R.color.light_accent
                LightCurveChannel.RED -> R.color.light_red
                LightCurveChannel.GREEN -> R.color.light_green
                LightCurveChannel.BLUE -> R.color.light_blue
                LightCurveChannel.WHITE -> R.color.light_white
            }

        return ContextCompat.getColor(
            context,
            colorRes
        )
    }

    private fun minuteToX(
        minute: Int,
        left: Float,
        right: Float
    ): Float {
        val safeMinute = minute.coerceIn(
            minimumValue = 0,
            maximumValue = MINUTES_IN_DAY
        )

        return left + ((right - left) * safeMinute / MINUTES_IN_DAY.toFloat())
    }

    private fun intensityToY(
        intensity: Int,
        top: Float,
        bottom: Float
    ): Float {
        val safeIntensity = intensity.coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )

        return bottom - ((bottom - top) * safeIntensity / 100f)
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private companion object {
        private const val MINUTES_IN_DAY = 24 * 60
    }
}