package com.aqua.aqualight.ui.tabs.devices.detail.dosing.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class DosingTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    private val trackPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.parseColor("#3A1422")
            style =
                Paint.Style.FILL
        }

    private val fillPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.parseColor("#F43F5E")
            style =
                Paint.Style.FILL
        }

    private val activePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.parseColor("#FB7185")
            style =
                Paint.Style.FILL
        }

    private val softPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.parseColor("#7F1D2D")
            style =
                Paint.Style.FILL
        }

    private var mode: TimelineMode =
        TimelineMode.EMPTY

    private var completedCount: Int =
        0

    private var totalCount: Int =
        0

    private var isRunning: Boolean =
        false

    fun renderEmpty() {
        mode =
            TimelineMode.EMPTY

        completedCount =
            0

        totalCount =
            0

        isRunning =
            false

        invalidate()
    }

    fun renderSingle(
        completed: Boolean,
        running: Boolean
    ) {
        mode =
            TimelineMode.SINGLE

        completedCount =
            if (completed) {
                1
            } else {
                0
            }

        totalCount =
            1

        isRunning =
            running

        invalidate()
    }

    fun renderHourly24(
        completedRuns: Int,
        running: Boolean
    ) {
        mode =
            TimelineMode.HOURLY_24

        completedCount =
            completedRuns.coerceIn(
                minimumValue = 0,
                maximumValue = 24
            )

        totalCount =
            24

        isRunning =
            running

        invalidate()
    }

    fun renderCustomPeriods(
        completedPeriods: Int,
        totalPeriods: Int,
        running: Boolean
    ) {
        mode =
            TimelineMode.CUSTOM_PERIODS

        totalCount =
            totalPeriods.coerceIn(
                minimumValue = 0,
                maximumValue = 8
            )

        completedCount =
            completedPeriods.coerceIn(
                minimumValue = 0,
                maximumValue = totalCount
            )

        isRunning =
            running

        invalidate()
    }

    fun renderTimer(
        completedDoses: Int,
        totalDoses: Int,
        running: Boolean
    ) {
        mode =
            TimelineMode.TIMER

        totalCount =
            totalDoses.coerceIn(
                minimumValue = 0,
                maximumValue = 24
            )

        completedCount =
            completedDoses.coerceIn(
                minimumValue = 0,
                maximumValue = totalCount
            )

        isRunning =
            running

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        when (mode) {
            TimelineMode.EMPTY -> {
                drawEmpty(
                    canvas = canvas
                )
            }

            TimelineMode.SINGLE -> {
                drawSingle(
                    canvas = canvas
                )
            }

            TimelineMode.HOURLY_24 -> {
                drawSegmented(
                    canvas = canvas,
                    segmentCount = 24
                )
            }

            TimelineMode.CUSTOM_PERIODS -> {
                drawSegmented(
                    canvas = canvas,
                    segmentCount = totalCount.coerceAtLeast(
                        minimumValue = 1
                    )
                )
            }

            TimelineMode.TIMER -> {
                drawTimerDots(
                    canvas = canvas
                )
            }
        }
    }

    private fun drawEmpty(
        canvas: Canvas
    ) {
        val centerY =
            height / 2f

        val radius =
            dp(
                value = 3.5f
            )

        val rect =
            RectF(
                0f,
                centerY - radius,
                width.toFloat(),
                centerY + radius
            )

        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            trackPaint
        )
    }

    private fun drawSingle(
        canvas: Canvas
    ) {
        val centerY =
            height / 2f

        val trackHeight =
            dp(
                value = 7f
            )

        val radius =
            trackHeight / 2f

        val dotRadius =
            dp(
                value = if (isRunning) {
                    6.5f
                } else {
                    5.5f
                }
            )

        val startX =
            dotRadius

        val endX =
            width.toFloat()

        val trackRect =
            RectF(
                startX,
                centerY - trackHeight / 2f,
                endX,
                centerY + trackHeight / 2f
            )

        canvas.drawRoundRect(
            trackRect,
            radius,
            radius,
            trackPaint
        )

        if (
            completedCount > 0 ||
            isRunning
        ) {
            canvas.drawRoundRect(
                trackRect,
                radius,
                radius,
                if (isRunning) {
                    activePaint
                } else {
                    fillPaint
                }
            )
        }

        canvas.drawCircle(
            dotRadius,
            centerY,
            dotRadius,
            if (
                completedCount > 0 ||
                isRunning
            ) {
                activePaint
            } else {
                softPaint
            }
        )
    }

    private fun drawSegmented(
        canvas: Canvas,
        segmentCount: Int
    ) {
        val safeSegmentCount =
            segmentCount.coerceAtLeast(
                minimumValue = 1
            )

        val gap =
            dp(
                value = 3f
            )

        val centerY =
            height / 2f

        val segmentHeight =
            dp(
                value = 8f
            )

        val radius =
            dp(
                value = 4f
            )

        val availableWidth =
            width.toFloat() - gap * (safeSegmentCount - 1)

        val segmentWidth =
            max(
                dp(
                    value = 4f
                ),
                availableWidth / safeSegmentCount.toFloat()
            )

        for (index in 0 until safeSegmentCount) {
            val left =
                index * (segmentWidth + gap)

            val right =
                min(
                    width.toFloat(),
                    left + segmentWidth
                )

            val rect =
                RectF(
                    left,
                    centerY - segmentHeight / 2f,
                    right,
                    centerY + segmentHeight / 2f
                )

            val paint =
                when {
                    index < completedCount -> {
                        fillPaint
                    }

                    index == completedCount && isRunning -> {
                        activePaint
                    }

                    else -> {
                        trackPaint
                    }
                }

            canvas.drawRoundRect(
                rect,
                radius,
                radius,
                paint
            )
        }
    }

    private fun drawTimerDots(
        canvas: Canvas
    ) {
        val safeTotal =
            totalCount.coerceAtLeast(
                minimumValue = 1
            )

        val centerY =
            height / 2f

        val lineHeight =
            dp(
                value = 3f
            )

        val dotRadius =
            dp(
                value = 4.7f
            )

        val lineRect =
            RectF(
                dotRadius,
                centerY - lineHeight / 2f,
                width.toFloat() - dotRadius,
                centerY + lineHeight / 2f
            )

        canvas.drawRoundRect(
            lineRect,
            lineHeight,
            lineHeight,
            trackPaint
        )

        val progressRatio =
            if (safeTotal <= 1) {
                if (completedCount > 0) {
                    1f
                } else {
                    0f
                }
            } else {
                completedCount.toFloat() / (safeTotal - 1).toFloat()
            }.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        val fillRight =
            dotRadius + (width.toFloat() - dotRadius * 2f) * progressRatio

        val fillRect =
            RectF(
                dotRadius,
                centerY - lineHeight / 2f,
                fillRight,
                centerY + lineHeight / 2f
            )

        canvas.drawRoundRect(
            fillRect,
            lineHeight,
            lineHeight,
            fillPaint
        )

        for (index in 0 until safeTotal) {
            val x =
                if (safeTotal == 1) {
                    dotRadius
                } else {
                    dotRadius +
                        (width.toFloat() - dotRadius * 2f) *
                        index.toFloat() /
                        (safeTotal - 1).toFloat()
                }

            val paint =
                when {
                    index < completedCount -> {
                        fillPaint
                    }

                    index == completedCount && isRunning -> {
                        activePaint
                    }

                    else -> {
                        softPaint
                    }
                }

            canvas.drawCircle(
                x,
                centerY,
                dotRadius,
                paint
            )
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources.displayMetrics.density
    }

    enum class TimelineMode {
        EMPTY,
        SINGLE,
        HOURLY_24,
        CUSTOM_PERIODS,
        TIMER
    }
}