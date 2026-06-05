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
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphSegment
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphState

class TodayLightPlanGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var state: TodayLightPlanGraphState =
    TodayLightPlanGraphState.empty(
        LightCurvePoint.of(0, 0)
    )

    private val graphRect = RectF()
    private val badgeRect = RectF()
    private val pausedOverlayRect = RectF()

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
        alpha = 70
    }

    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
        color = color(R.color.light_stroke)
        alpha = 120
    }

    private val baseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = color(R.color.light_stroke)
        alpha = 150
    }

    private val outputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = color(R.color.light_accent)
        alpha = 220
    }

    private val outputSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = color(R.color.light_accent)
        alpha = 130
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_accent)
        alpha = 26
    }

    private val segmentBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_accent)
        alpha = 14
    }

    private val currentSegmentBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_accent)
        alpha = 25
    }

    private val segmentBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = color(R.color.light_text_tertiary)
        alpha = 140
    }

    private val segmentBoundaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_secondary)
        textSize = sp(9.5f)
        textAlign = Paint.Align.CENTER
    }

    private val segmentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_accent)
        alpha = 190
    }

    private val timeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = color(R.color.light_accent)
        alpha = 220
    }

    private val timeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_bg_deep)
        alpha = 240
    }

    private val timeBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.9f)
        color = color(R.color.light_accent)
        alpha = 210
    }

    private val timeBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_accent)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_tertiary)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_secondary)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_tertiary)
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }

    private val pausedOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.color.light_bg_deep)
        alpha = 235
    }

    private val pausedOverlayStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.9f)
        color = color(R.color.light_accent)
        alpha = 150
    }

    private val pausedOverlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_primary)
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pausedOverlaySubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.light_text_secondary)
        textSize = sp(10.5f)
        textAlign = Paint.Align.CENTER
    }

    fun setState(
        newState: TodayLightPlanGraphState
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
        drawBaseLine(canvas)

        if (state.segments.isEmpty()) {
            drawEmptyState(canvas)
        } else {
            drawSegments(canvas)
        }

        drawCurrentTime(canvas)

        if (state.showPausedOverlay) {
            drawPausedOverlay(canvas)
        }

        drawAxisLabels(canvas)
    }

    private fun calculateGraphRect() {
        graphRect.set(
            dp(18f),
            dp(32f),
            width - dp(18f),
            height - dp(28f)
        )
    }

    private fun drawGrid(
        canvas: Canvas
    ) {
        listOf(25, 50, 75, 100).forEach {
            percent ->
            val y = yForPercent(percent)
            val paint = if (percent == 50 || percent == 100) {
                majorGridPaint
            } else {
                gridPaint
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

    private fun drawBaseLine(
        canvas: Canvas
    ) {
        canvas.drawLine(
            graphRect.left,
            yForPercent(0),
            graphRect.right,
            yForPercent(0),
            baseLinePaint
        )
    }

    private fun drawSegments(
        canvas: Canvas
    ) {
        state.segments
        .sortedBy {
            segment ->
            segment.start.totalMinutes
        }
        .forEach {
            segment ->
            drawSegmentBackground(canvas, segment)
            drawSegmentCurve(canvas, segment)
            drawSegmentBoundaryMarkers(canvas, segment)
            drawSegmentLabel(canvas, segment)
        }
    }

    private fun drawSegmentBackground(
        canvas: Canvas,
        segment: TodayLightPlanGraphSegment
    ) {
        val left = xForMinute(segment.start.totalMinutes)
        val right = xForMinute(segment.end.totalMinutes)

        if (right <= left) return

        val rect = RectF(
            left,
            graphRect.top,
            right,
            graphRect.bottom
        )

        val paint = when {
            state.showPausedOverlay -> segmentBackgroundPaint.copyWithAlpha(8)
            segment.isCurrent -> currentSegmentBackgroundPaint
            else -> segmentBackgroundPaint
        }

        canvas.drawRoundRect(
            rect,
            dp(10f),
            dp(10f),
            paint
        )
    }

    private fun drawSegmentCurve(
        canvas: Canvas,
        segment: TodayLightPlanGraphSegment
    ) {
        val safeOutput = segment.outputPercent.coerceIn(0, 100)

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = segment.start.totalMinutes,
            peakStartMinute = segment.peakStart.totalMinutes,
            peakEndMinute = segment.peakEnd.totalMinutes,
            endMinute = segment.end.totalMinutes,
            peakPercent = safeOutput,
            transitionMode = segment.transitionMode
        )

        if (points.isEmpty()) return

        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed {
            index, point ->
            val x = xForMinute(point.x.toInt())
            val y = yForPercent(point.y.toInt())

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, yForPercent(0))
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val endX = xForMinute(segment.end.totalMinutes)

        fillPath.lineTo(endX, yForPercent(0))
        fillPath.close()

        if (segment.isCurrent) {
            canvas.drawPath(fillPath, fillPaint)
        }

        val paint = when {
            state.showPausedOverlay -> outputSoftPaint.copyWithAlpha(80)
            segment.isCurrent -> outputPaint
            segment.isNext -> outputSoftPaint.copyWithAlpha(165)
            else -> outputSoftPaint
        }

        canvas.drawPath(linePath, paint)
    }

    private fun drawSegmentBoundaryMarkers(
        canvas: Canvas,
        segment: TodayLightPlanGraphSegment
    ) {
        val startX = xForMinute(segment.start.totalMinutes)
        val endX = xForMinute(segment.end.totalMinutes)

        if (endX <= startX) return

        val top = graphRect.top + dp(6f)
        val bottom = graphRect.bottom

        val boundaryPaint = Paint(segmentBoundaryPaint).apply {
            alpha = if (segment.isCurrent) {
                175
            } else {
                115
            }
        }

        canvas.drawLine(
            startX,
            top,
            startX,
            bottom,
            boundaryPaint
        )

        canvas.drawLine(
            endX,
            top,
            endX,
            bottom,
            boundaryPaint
        )

        val dotPaint = Paint(segmentDotPaint).apply {
            alpha = if (segment.isCurrent) {
                220
            } else {
                160
            }
        }

        canvas.drawCircle(
            startX,
            yForPercent(0),
            dp(2.4f),
            dotPaint
        )

        canvas.drawCircle(
            endX,
            yForPercent(0),
            dp(2.4f),
            dotPaint
        )

        val availableWidth = endX - startX

        if (availableWidth < dp(52f)) {
            return
        }

        val labelY = graphRect.bottom - dp(7f)

        val startLabelX = (startX + dp(18f))
        .coerceAtMost(endX - dp(18f))

        val endLabelX = (endX - dp(18f))
        .coerceAtLeast(startX + dp(18f))

        val textPaint = Paint(segmentBoundaryTextPaint).apply {
            alpha = if (segment.isCurrent) {
                230
            } else {
                170
            }
        }

        canvas.drawText(
            segment.start.label,
            startLabelX,
            labelY,
            textPaint
        )

        canvas.drawText(
            segment.end.label,
            endLabelX,
            labelY,
            textPaint
        )
    }

    private fun drawSegmentLabel(
        canvas: Canvas,
        segment: TodayLightPlanGraphSegment
    ) {

        if (state.showPausedOverlay) return
        val left = xForMinute(segment.start.totalMinutes)
        val right = xForMinute(segment.end.totalMinutes)
        val availableWidth = right - left

        if (availableWidth < dp(54f)) return

        val label = segment.name
        .ifBlank {
            "Program"
        }
        .shortenForWidth(
            paint = labelTextPaint,
            maxWidth = availableWidth - dp(14f)
        )

        val labelWidth = labelTextPaint.measureText(label) + dp(16f)
        val labelHeight = dp(22f)

        val badgeLeft = (left + dp(6f))
        .coerceAtMost(right - labelWidth - dp(4f))

        val badgeTop = graphRect.top + dp(7f)

        badgeRect.set(
            badgeLeft,
            badgeTop,
            badgeLeft + labelWidth,
            badgeTop + labelHeight
        )

        val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color(R.color.light_bg_deep)
            alpha = if (segment.isCurrent) {
                235
            } else {
                190
            }
        }

        val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f)
            color = if (segment.isCurrent) {
                color(R.color.light_accent)
            } else {
                color(R.color.light_stroke)
            }
            alpha = 180
        }

        val textPaint = Paint(labelTextPaint).apply {
            color = if (segment.isCurrent) {
                color(R.color.light_accent)
            } else {
                color(R.color.light_text_secondary)
            }
        }

        canvas.drawRoundRect(
            badgeRect,
            dp(8f),
            dp(8f),
            badgeFill
        )

        canvas.drawRoundRect(
            badgeRect,
            dp(8f),
            dp(8f),
            badgeStroke
        )

        canvas.drawText(
            label,
            badgeRect.centerX(),
            badgeRect.centerY() + dp(3.5f),
            textPaint
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
            if (state.showPausedOverlay) {
                timeLinePaint.copyWithAlpha(135)
            } else {
                timeLinePaint
            }
        )

        val label = "NOW ${state.currentTime.label}"
        val badgeWidth = dp(72f)
        val badgeHeight = dp(24f)

        val left = (currentX - badgeWidth / 2f)
        .coerceIn(graphRect.left, graphRect.right - badgeWidth)

        val top = graphRect.top - dp(26f)

        badgeRect.set(
            left,
            top,
            left + badgeWidth,
            top + badgeHeight
        )

        canvas.drawRoundRect(
            badgeRect,
            dp(8f),
            dp(8f),
            timeBadgePaint
        )

        canvas.drawRoundRect(
            badgeRect,
            dp(8f),
            dp(8f),
            timeBadgeStrokePaint
        )

        canvas.drawText(
            label,
            badgeRect.centerX(),
            badgeRect.centerY() + dp(3.5f),
            timeBadgeTextPaint
        )
    }

    private fun drawAxisLabels(
        canvas: Canvas
    ) {
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

    private fun drawEmptyState(
        canvas: Canvas
    ) {
        canvas.drawText(
            "No active plan today",
            graphRect.centerX(),
            graphRect.centerY() + dp(4f),
            emptyTextPaint
        )
    }

    private fun drawPausedOverlay(
        canvas: Canvas
    ) {
        val overlayWidth = minOf(
            graphRect.width() * 0.66f,
            dp(230f)
        )

        val overlayHeight = dp(58f)

        val left = graphRect.centerX() - overlayWidth / 2f
        val top = graphRect.centerY() - overlayHeight / 2f

        pausedOverlayRect.set(
            left,
            top,
            left + overlayWidth,
            top + overlayHeight
        )

        canvas.drawRoundRect(
            pausedOverlayRect,
            dp(14f),
            dp(14f),
            pausedOverlayPaint
        )

        canvas.drawRoundRect(
            pausedOverlayRect,
            dp(14f),
            dp(14f),
            pausedOverlayStrokePaint
        )

        val title = state.pausedOverlayTitle
        .shortenForWidth(
            paint = pausedOverlayTitlePaint,
            maxWidth = overlayWidth - dp(24f)
        )

        val subtitle = state.pausedOverlaySubtitle
        .shortenForWidth(
            paint = pausedOverlaySubtitlePaint,
            maxWidth = overlayWidth - dp(24f)
        )

        canvas.drawText(
            title,
            pausedOverlayRect.centerX(),
            pausedOverlayRect.centerY() - dp(5f),
            pausedOverlayTitlePaint
        )

        canvas.drawText(
            subtitle,
            pausedOverlayRect.centerX(),
            pausedOverlayRect.centerY() + dp(15f),
            pausedOverlaySubtitlePaint
        )
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

    private fun Paint.copyWithAlpha(
        alphaValue: Int
    ): Paint {
        return Paint(this).apply {
            alpha = alphaValue.coerceIn(0, 255)
        }
    }

    private fun String.shortenForWidth(
        paint: Paint,
        maxWidth: Float
    ): String {
        if (paint.measureText(this) <= maxWidth) {
            return this
        }

        var result = this

        while (
            result.length > 4 &&
            paint.measureText("$result…") > maxWidth
        ) {
            result = result.dropLast(1)
        }

        return "$result…"
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