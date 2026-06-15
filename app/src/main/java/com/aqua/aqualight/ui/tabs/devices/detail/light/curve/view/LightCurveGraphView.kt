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
import com.aqua.aqualight.data.devices.light.programs.compiler.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.data.devices.light.programs.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveMoonlightGraphSegment

class LightCurveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var state: LightCurveGraphState = LightCurveGraphState.preview()

    private val graphRect = RectF()
    private val timeBadgeRect = RectF()

    private val hourlyGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        color = color(R.color.light_stroke)
        alpha = 45
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = color(R.color.light_stroke)
        alpha = 75
    }

    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
        color = color(R.color.light_stroke)
        alpha = 125
    }

    private val baseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = color(R.color.light_stroke)
        alpha = 165
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
        strokeWidth = dp(1.4f)
        style = Paint.Style.STROKE
        alpha = 220
    }

    private val timeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_bg_deep)
        style = Paint.Style.FILL
        alpha = 240
    }

    private val timeBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        strokeWidth = dp(0.9f)
        style = Paint.Style.STROKE
        alpha = 210
    }

    private val timeBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val redPaint = channelPaint(
        colorRes = R.color.light_channel_red,
        alpha = 210
    )

    private val greenPaint = channelPaint(
        colorRes = R.color.light_channel_green,
        alpha = 210
    )

    private val bluePaint = channelPaint(
        colorRes = R.color.light_channel_blue,
        alpha = 220
    )

    private val whitePaint = channelPaint(
        colorRes = R.color.light_text_primary,
        alpha = 220
    )

    private val moonlightGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = color(R.color.light_channel_blue)
        alpha = 55
    }

    private val moonlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = color(R.color.light_channel_blue)
        alpha = 230
    }

    private val moonlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_channel_blue)
        alpha = 20
    }

    private val moonlightBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_bg_deep)
        alpha = 230
    }

    private val moonlightBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = color(R.color.light_channel_blue)
        alpha = 180
    }

    private val moonlightTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_channel_blue)
        textSize = sp(9.5f)
        textAlign = Paint.Align.CENTER
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = color(R.color.light_bg_deep)
        alpha = 230
    }

    fun setState(
        newState: LightCurveGraphState
    ) {
        state = newState
        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        calculateGraphRect()

        drawGrid(canvas)
        drawChannelCurves(canvas)
        drawMoonlightSegments(canvas)
        drawCurrentTime(canvas)
        drawAxisLabels(canvas)
    }

    private fun calculateGraphRect() {
        graphRect.set(
            dp(44f),
            dp(34f),
            width - dp(18f),
            height - dp(32f)
        )
    }

    private fun drawGrid(
        canvas: Canvas
    ) {
        listOf(0, 25, 50, 75, 100).forEach {
            percent ->
            val y = yForPercent(percent)

            val paint = when (percent) {
                0 -> baseLinePaint
                50, 100 -> majorGridPaint
                else -> gridPaint
            }

            canvas.drawLine(
                graphRect.left,
                y,
                graphRect.right,
                y,
                paint
            )
        }

        for (hour in 0..24) {
            val x = xForMinute(hour * 60)

            val paint = when {
                hour % 4 == 0 -> majorGridPaint
                else -> hourlyGridPaint
            }

            canvas.drawLine(
                x,
                graphRect.top,
                x,
                graphRect.bottom,
                paint
            )
        }
    }

    private fun drawAxisLabels(
        canvas: Canvas
    ) {
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

            canvas.drawText(
                hour.toString(),
                x.coerceIn(
                    graphRect.left + dp(4f),
                    graphRect.right - dp(4f)
                ),
                height - dp(9f),
                axisTextPaint
            )
        }
    }

    private fun drawChannelCurves(
        canvas: Canvas
    ) {
        if (state.compiledPoints.isNotEmpty()) {
            drawCompiledCurve(
                canvas = canvas,
                paint = redPaint,
                channelValue = { point -> point.channels.red }
            )
            drawCompiledCurve(
                canvas = canvas,
                paint = greenPaint,
                channelValue = { point -> point.channels.green }
            )
            drawCompiledCurve(
                canvas = canvas,
                paint = bluePaint,
                channelValue = { point -> point.channels.blue }
            )
            drawCompiledCurve(
                canvas = canvas,
                paint = whitePaint,
                channelValue = { point -> point.channels.white }
            )
            drawCompiledAnchorPoints(canvas)
            return
        }

        val channels = state.channelValues.normalized()

        drawCurve(
            canvas = canvas,
            start = state.start,
            peakStart = state.peakStart,
            peakEnd = state.peakEnd,
            end = state.end,
            peakPercent = channels.red,
            transitionMode = state.transitionMode,
            paint = redPaint
        )

        drawCurve(
            canvas = canvas,
            start = state.start,
            peakStart = state.peakStart,
            peakEnd = state.peakEnd,
            end = state.end,
            peakPercent = channels.green,
            transitionMode = state.transitionMode,
            paint = greenPaint
        )

        drawCurve(
            canvas = canvas,
            start = state.start,
            peakStart = state.peakStart,
            peakEnd = state.peakEnd,
            end = state.end,
            peakPercent = channels.blue,
            transitionMode = state.transitionMode,
            paint = bluePaint
        )

        drawCurve(
            canvas = canvas,
            start = state.start,
            peakStart = state.peakStart,
            peakEnd = state.peakEnd,
            end = state.end,
            peakPercent = channels.white,
            transitionMode = state.transitionMode,
            paint = whitePaint
        )
    }

    private fun drawCompiledCurve(
        canvas: Canvas,
        paint: Paint,
        channelValue: (com.aqua.aqualight.data.devices.light.programs.compiler.CompiledLightProgramPoint) -> Int
    ) {
        val path = Path()
        state.compiledPoints.forEachIndexed { index, point ->
            val x = xForMinute(point.minuteOfDay)
            val y = yForPercent(channelValue(point))

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawCompiledAnchorPoints(
        canvas: Canvas
    ) {
        val channels = state.channelValues.normalized()
        drawCurvePoints(
            canvas = canvas,
            start = state.start,
            peakStart = state.peakStart,
            peakEnd = state.peakEnd,
            end = state.end,
            peakPercent = maxOf(channels.red, channels.green, channels.blue, channels.white),
            paint = bluePaint
        )
    }

    private fun drawMoonlightSegments(
        canvas: Canvas
    ) {
        state.moonlightSegments.forEach {
            segment ->
            drawMoonlightSegment(
                canvas = canvas,
                segment = segment
            )
        }
    }

    private fun drawMoonlightSegment(
        canvas: Canvas,
        segment: LightCurveMoonlightGraphSegment
    ) {
        val startMinute = segment.startMinute
        .coerceIn(0, LightCurveMoonlightGraphSegment.MINUTES_PER_DAY)

        val endMinute = segment.endMinute
        .coerceIn(0, LightCurveMoonlightGraphSegment.MINUTES_PER_DAY)

        if (endMinute <= startMinute) {
            return
        }

        val output = segment.outputPercent
        .coerceIn(1, 30)

        val startX = xForMinute(startMinute)
        val endX = xForMinute(endMinute)
        val y = yForPercent(output)
        val zeroY = yForPercent(0)

        val fillPath = Path().apply {
            moveTo(startX, zeroY)
            lineTo(startX, y)
            lineTo(endX, y)
            lineTo(endX, zeroY)
            close()
        }

        canvas.drawPath(
            fillPath,
            moonlightFillPaint
        )

        canvas.drawLine(
            startX,
            y,
            endX,
            y,
            moonlightGlowPaint
        )

        canvas.drawLine(
            startX,
            y,
            endX,
            y,
            moonlightPaint
        )

        canvas.drawCircle(
            startX,
            y,
            dp(2.8f),
            moonlightPaint
        )

        canvas.drawCircle(
            endX,
            y,
            dp(2.8f),
            moonlightPaint
        )

        drawMoonlightLabel(
            canvas = canvas,
            segment = segment,
            startX = startX,
            endX = endX,
            y = y
        )
    }

    private fun drawMoonlightLabel(
        canvas: Canvas,
        segment: LightCurveMoonlightGraphSegment,
        startX: Float,
        endX: Float,
        y: Float
    ) {
        val availableWidth = endX - startX

        if (availableWidth < dp(70f)) {
            return
        }

        val label = segment.label
        val labelWidth = moonlightTextPaint.measureText(label) + dp(16f)
        val labelHeight = dp(21f)

        val left = (startX + dp(8f))
        .coerceAtMost(endX - labelWidth - dp(4f))

        val top = (y - dp(32f))
        .coerceAtLeast(graphRect.top + dp(4f))

        val rect = RectF(
            left,
            top,
            left + labelWidth,
            top + labelHeight
        )

        canvas.drawRoundRect(
            rect,
            dp(8f),
            dp(8f),
            moonlightBadgePaint
        )

        canvas.drawRoundRect(
            rect,
            dp(8f),
            dp(8f),
            moonlightBadgeStrokePaint
        )

        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() + dp(3.4f),
            moonlightTextPaint
        )
    }

    private fun drawCurve(
        canvas: Canvas,
        start: LightCurvePoint,
        peakStart: LightCurvePoint,
        peakEnd: LightCurvePoint,
        end: LightCurvePoint,
        peakPercent: Int,
        transitionMode: LightCurveTransitionMode,
        paint: Paint
    ) {
        val safePeak = peakPercent.coerceIn(0, 100)

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = start.totalMinutes,
            peakStartMinute = peakStart.totalMinutes,
            peakEndMinute = peakEnd.totalMinutes,
            endMinute = endMinutesForGraph(end),
            peakPercent = safePeak,
            transitionMode = transitionMode
        )

        if (points.isEmpty()) {
            return
        }

        val path = Path()

        points.forEachIndexed {
            index, point ->
            val x = xForMinute(point.x.toInt())
            val y = yForPercent(point.y.toInt())

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, paint)

        drawCurvePoints(
            canvas = canvas,
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            peakPercent = safePeak,
            paint = paint
        )
    }

    private fun drawCurvePoints(
        canvas: Canvas,
        start: LightCurvePoint,
        peakStart: LightCurvePoint,
        peakEnd: LightCurvePoint,
        end: LightCurvePoint,
        peakPercent: Int,
        paint: Paint
    ) {
        pointPaint.color = paint.color
        pointPaint.alpha = 225

        val startX = xForMinute(start.totalMinutes)
        val peakStartX = xForMinute(peakStart.totalMinutes)
        val peakEndX = xForMinute(peakEnd.totalMinutes)
        val endX = xForMinute(endMinutesForGraph(end))

        val zeroY = yForPercent(0)
        val peakY = yForPercent(peakPercent)

        drawPoint(canvas, startX, zeroY)
        drawPoint(canvas, peakStartX, peakY)
        drawPoint(canvas, peakEndX, peakY)
        drawPoint(canvas, endX, zeroY)
    }

    private fun drawPoint(
        canvas: Canvas,
        x: Float,
        y: Float
    ) {
        canvas.drawCircle(
            x,
            y,
            dp(3.2f),
            pointStrokePaint
        )

        canvas.drawCircle(
            x,
            y,
            dp(2.5f),
            pointPaint
        )
    }

    private fun drawCurrentTime(
        canvas: Canvas
    ) {
        val currentX = xForMinute(
            state.currentTime.totalMinutes
        )

        canvas.drawLine(
            currentX,
            graphRect.top,
            currentX,
            graphRect.bottom,
            currentLinePaint
        )

        val label = state.currentTime.label
        val badgeWidth = dp(52f)
        val badgeHeight = dp(24f)

        val left = (currentX - badgeWidth / 2f)
        .coerceIn(
            graphRect.left,
            graphRect.right - badgeWidth
        )

        val top = graphRect.top - dp(26f)

        timeBadgeRect.set(
            left,
            top,
            left + badgeWidth,
            top + badgeHeight
        )

        canvas.drawRoundRect(
            timeBadgeRect,
            dp(8f),
            dp(8f),
            timeBadgePaint
        )

        canvas.drawRoundRect(
            timeBadgeRect,
            dp(8f),
            dp(8f),
            timeBadgeStrokePaint
        )

        canvas.drawText(
            label,
            timeBadgeRect.centerX(),
            timeBadgeRect.centerY() + dp(3.5f),
            timeBadgeTextPaint
        )
    }

    private fun endMinutesForGraph(
        point: LightCurvePoint
    ): Int {
        return if (point.hour == 0 && point.minute == 0) {
            24 * 60
        } else {
            point.totalMinutes
        }
    }

    private fun xForMinute(
        minute: Int
    ): Float {
        val clamped = minute.coerceIn(0, 24 * 60)
        val ratio = clamped / (24f * 60f)

        return graphRect.left + graphRect.width() * ratio
    }

    private fun yForPercent(
        percent: Int
    ): Float {
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
            strokeWidth = dp(2.2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = color(colorRes)
            this.alpha = alpha
        }
    }

    private fun color(
        resId: Int
    ): Int {
        return try {
            ContextCompat.getColor(context, resId)
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value * resources.displayMetrics.density
    }

    private fun sp(
        value: Float
    ): Float {
        return value * resources.displayMetrics.scaledDensity
    }
}