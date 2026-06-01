package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R

class LightCurveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var chartData: LightCurveChartData? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp()
        pathEffect = DashPathEffect(
            floatArrayOf(
                3f.dp(),
                7f.dp()
            ),
            0f
        )
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp()
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f.sp()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val currentTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f.dp()
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

        if (width <= 0 || height <= 0) {
            return
        }

        val left = 38f.dp()
        val right = width - 4f.dp()
        val top = 24f.dp()
        val bottom = height - 34f.dp()

        if (right <= left || bottom <= top) {
            return
        }

        drawGrid(
            canvas = canvas,
            left = left,
            right = right,
            top = top,
            bottom = bottom
        )

        val data = chartData ?: return
        if (!data.hasData) {
            return
        }

        data.series.forEach { series ->
            drawSeries(
                canvas = canvas,
                series = series,
                left = left,
                right = right,
                top = top,
                bottom = bottom,
                drawFill = series.isActive
            )
        }

        drawCurrentTime(
            canvas = canvas,
            currentTimeMinutes = data.currentTimeMinutes,
            left = left,
            right = right,
            top = top,
            bottom = bottom
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val strokeColor = ContextCompat.getColor(
            context,
            R.color.light_stroke
        )

        val labelColor = ContextCompat.getColor(
            context,
            R.color.settings_text_secondary
        )

        gridPaint.color = strokeColor
        gridPaint.alpha = GRID_ALPHA

        axisPaint.color = strokeColor
        axisPaint.alpha = AXIS_ALPHA

        labelPaint.color = labelColor
        labelPaint.textAlign = Paint.Align.RIGHT

        val chartHeight = bottom - top

        val levels = listOf(
            100,
            75,
            50,
            25,
            0
        )

        levels.forEach { level ->
            val y = bottom - (chartHeight * level / 100f)

            canvas.drawLine(
                left,
                y,
                right,
                y,
                if (level == 0) {
                    axisPaint
                } else {
                    gridPaint
                }
            )

            canvas.drawText(
                context.getString(
                    R.string.common_percent_value,
                    level
                ),
                left - 7f.dp(),
                y + 4f.dp(),
                labelPaint
            )
        }

        canvas.drawLine(
            left,
            top,
            left,
            bottom,
            axisPaint
        )

        drawTimeLabels(
            canvas = canvas,
            left = left,
            right = right,
            y = bottom + 23f.dp()
        )
    }

    private fun drawTimeLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float
    ) {
        labelPaint.textAlign = Paint.Align.CENTER

        val labelColor = ContextCompat.getColor(
            context,
            R.color.settings_text_secondary
        )

        labelPaint.color = labelColor

        for (hour in 0..24 step 4) {
            val minute = hour * 60
            val x = minuteToX(
                minute = minute,
                left = left,
                right = right
            )

            labelPaint.textAlign =
                when (hour) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }

            val displayHour =
                if (hour == 24) {
                    0
                } else {
                    hour
                }

            canvas.drawText(
                context.getString(
                    R.string.common_time_hour_label,
                    displayHour
                ),
                x,
                y,
                labelPaint
            )
        }
    }

    private fun drawSeries(
        canvas: Canvas,
        series: LightCurveSeries,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        drawFill: Boolean
    ) {
        val points =
            series.points
                .sortedBy { point ->
                    point.minuteOfDay
                }

        if (points.isEmpty()) {
            return
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

        val color = channelColor(series.channel)

        if (drawFill && points.size >= 2) {
            drawFill(
                canvas = canvas,
                path = path,
                points = points,
                color = color,
                left = left,
                right = right,
                top = top,
                bottom = bottom
            )
        }

        linePaint.color = color
        linePaint.alpha =
            if (series.isActive) {
                255
            } else {
                78
            }

        canvas.drawPath(
            path,
            linePaint
        )

        if (series.isActive) {
            points.forEach { point ->
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

                pointPaint.color = color

                canvas.drawCircle(
                    x,
                    y,
                    if (point.isMajor) {
                        4.8f.dp()
                    } else {
                        3.6f.dp()
                    },
                    pointPaint
                )
            }
        }
    }

    private fun drawFill(
        canvas: Canvas,
        path: Path,
        points: List<LightCurvePoint>,
        color: Int,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val first = points.first()
        val last = points.last()

        val firstX = minuteToX(
            minute = first.minuteOfDay,
            left = left,
            right = right
        )

        val lastX = minuteToX(
            minute = last.minuteOfDay,
            left = left,
            right = right
        )

        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            color.withAlpha(52),
            color.withAlpha(12),
            Shader.TileMode.CLAMP
        )

        val fillPath = Path(path).apply {
            lineTo(
                lastX,
                bottom
            )

            lineTo(
                firstX,
                bottom
            )

            close()
        }

        canvas.drawPath(
            fillPath,
            fillPaint
        )

        fillPaint.shader = null
    }

    private fun drawCurrentTime(
        canvas: Canvas,
        currentTimeMinutes: Int?,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val minute = currentTimeMinutes ?: return

        currentTimePaint.color = ContextCompat.getColor(
            context,
            R.color.light_accent
        )

        val x = minuteToX(
            minute = minute,
            left = left,
            right = right
        )

        canvas.drawLine(
            x,
            top,
            x,
            bottom,
            currentTimePaint
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

    private fun Int.withAlpha(
        alpha: Int
    ): Int {
        return android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(this),
            android.graphics.Color.green(this),
            android.graphics.Color.blue(this)
        )
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }

    private companion object {
        private const val MINUTES_IN_DAY = 24 * 60
        private const val GRID_ALPHA = 70
        private const val AXIS_ALPHA = 135
    }
}