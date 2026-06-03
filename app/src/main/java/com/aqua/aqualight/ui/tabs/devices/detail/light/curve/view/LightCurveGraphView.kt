package com.aqua.aqualight.ui.tabs.devices.detail.light.curve.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import kotlin.math.roundToInt

class LightCurveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var state: LightCurveGraphState = LightCurveGraphState.preview()

    private val graphRect = RectF()
    private val timeLabelRect = RectF()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = color(R.color.light_stroke)
        alpha = 80
    }

    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = color(R.color.light_stroke)
        alpha = 130
    }

    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_tertiary)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val yAxisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_tertiary)
        textSize = sp(10f)
        textAlign = Paint.Align.RIGHT
    }

    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
        alpha = 210
    }

    private val timeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_bg_deep)
        style = Paint.Style.FILL
    }

    private val timeBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }

    private val timeBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val redPaint = channelPaint(R.color.light_channel_red, alpha = 175)
    private val greenPaint = channelPaint(R.color.light_channel_green, alpha = 175)
    private val bluePaint = channelPaint(R.color.light_channel_blue, alpha = 185)
    private val whitePaint = channelPaint(R.color.light_text_primary, alpha = 190)

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setState(newState: LightCurveGraphState) {
        state = newState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        calculateGraphRect()
        drawGrid(canvas)
        drawChannelCurves(canvas)
        drawCurrentTime(canvas)
        drawAxisLabels(canvas)
    }

    private fun calculateGraphRect() {
        graphRect.set(
            dp(42f),
            dp(36f),
            width - dp(18f),
            height - dp(34f)
        )
    }

    private fun drawGrid(canvas: Canvas) {
        // horizontal percentage grid: 0 / 25 / 50 / 75 / 100
        listOf(0, 25, 50, 75, 100).forEach {
            percent ->
            val y = yForPercent(percent)
            val paint = if (percent == 0 || percent == 50 || percent == 100) {
                majorGridPaint
            } else {
                gridPaint
            }
            canvas.drawLine(graphRect.left, y, graphRect.right, y, paint)
        }

        // vertical hourly grid, stronger every 4 hours
        for (hour in 0..24) {
            val x = xForMinute(hour * 60)
            val paint = if (hour % 4 == 0) majorGridPaint else gridPaint
            canvas.drawLine(x, graphRect.top, x, graphRect.bottom, paint)
        }
    }

    private fun drawAxisLabels(canvas: Canvas) {
        listOf(0, 25, 50, 75, 100).forEach {
            percent ->
            canvas.drawText(
                "$percent%",
                graphRect.left - dp(8f),
                yForPercent(percent) + dp(3.5f),
                yAxisTextPaint
            )
        }

        for (hour in 0..24 step 4) {
            val x = xForMinute(hour * 60)
            val label = hour.toString()
            canvas.drawText(
                label,
                x.coerceIn(graphRect.left + dp(4f), graphRect.right - dp(4f)),
                height - dp(12f),
                axisTextPaint
            )
        }
    }

    private fun drawChannelCurves(canvas: Canvas) {
        val channels = state.channelValues.normalized()

        drawCurve(canvas, channels.red, redPaint)
        drawCurve(canvas, channels.green, greenPaint)
        drawCurve(canvas, channels.blue, bluePaint)
        drawCurve(canvas, channels.white, whitePaint)
    }

    private fun drawCurve(
        canvas: Canvas,
        peakPercent: Int,
        paint: Paint
    ) {
        val path = Path()

        val startX = xForMinute(state.start.totalMinutes)
        val peakStartX = xForMinute(state.peakStart.totalMinutes)
        val peakEndX = xForMinute(state.peakEnd.totalMinutes)
        val endX = xForMinute(state.end.totalMinutes)

        val zeroY = yForPercent(0)
        val peakY = yForPercent(peakPercent)

        path.moveTo(graphRect.left, zeroY)
        path.lineTo(startX, zeroY)
        path.lineTo(peakStartX, peakY)
        path.lineTo(peakEndX, peakY)
        path.lineTo(endX, zeroY)
        path.lineTo(graphRect.right, zeroY)

        canvas.drawPath(path, paint)

        pointPaint.color = paint.color
        pointPaint.alpha = 220

        canvas.drawCircle(startX, zeroY, dp(2.6f), pointPaint)
        canvas.drawCircle(peakStartX, peakY, dp(2.6f), pointPaint)
        canvas.drawCircle(peakEndX, peakY, dp(2.6f), pointPaint)
        canvas.drawCircle(endX, zeroY, dp(2.6f), pointPaint)
    }

    private fun drawCurrentTime(canvas: Canvas) {
        val currentX = xForMinute(state.currentTime.totalMinutes)

        canvas.drawLine(
            currentX,
            graphRect.top,
            currentX,
            graphRect.bottom,
            currentLinePaint
        )

        val label = state.currentTime.label
        val badgeWidth = dp(48f)
        val badgeHeight = dp(24f)

        val left = (currentX - badgeWidth / 2f)
        .coerceIn(graphRect.left, graphRect.right - badgeWidth)
        val top = graphRect.top - dp(28f)

        timeLabelRect.set(
            left,
            top,
            left + badgeWidth,
            top + badgeHeight
        )

        canvas.drawRoundRect(timeLabelRect, dp(8f), dp(8f), timeBadgePaint)
        canvas.drawRoundRect(timeLabelRect, dp(8f), dp(8f), timeBadgeStrokePaint)

        canvas.drawText(
            label,
            timeLabelRect.centerX(),
            timeLabelRect.centerY() + dp(3.5f),
            timeBadgeTextPaint
        )
    }

    private fun xForMinute(minute: Int): Float {
        val clamped = minute.coerceIn(0, 24 * 60)
        val ratio = clamped / (24f * 60f)
        return graphRect.left + graphRect.width() * ratio
    }

    private fun yForPercent(percent: Int): Float {
        val clamped = percent.coerceIn(0, 100)
        val ratio = clamped / 100f
        return graphRect.bottom - graphRect.height() * ratio
    }

    private fun channelPaint(
        colorRes: Int,
        alpha: Int
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = color(colorRes)
            this.alpha = alpha
        }
    }

    private fun color(resId: Int): Int {
        return try {
            ContextCompat.getColor(context, resId)
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun sp(value: Float): Float {
        return value * resources.displayMetrics.scaledDensity
    }
}