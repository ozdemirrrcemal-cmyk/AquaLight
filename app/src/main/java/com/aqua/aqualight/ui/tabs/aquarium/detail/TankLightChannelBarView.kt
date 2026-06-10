package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class TankLightChannelBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val trackRect =
        RectF()

    private val fillRect =
        RectF()

    private var progressPercent: Int =
        0

    private var fillColor: Int =
        Color.WHITE

    private var trackColor: Int =
        Color.parseColor("#3A4656")

    private val desiredHeightPx =
        (6f * resources.displayMetrics.density).roundToInt()

    fun bind(
        progressPercent: Int,
        fillColor: Int,
        trackColor: Int
    ) {
        this.progressPercent =
            progressPercent.coerceIn(
                0,
                100
            )

        this.fillColor =
            fillColor

        this.trackColor =
            trackColor

        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val width =
            MeasureSpec.getSize(
                widthMeasureSpec
            )

        val height =
            resolveSize(
                desiredHeightPx,
                heightMeasureSpec
            )

        setMeasuredDimension(
            width,
            height
        )
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val barHeight =
            height.toFloat()

        val radius =
            barHeight / 2f

        trackPaint.color =
            trackColor

        fillPaint.color =
            fillColor

        trackRect.set(
            0f,
            0f,
            width.toFloat(),
            barHeight
        )

        canvas.drawRoundRect(
            trackRect,
            radius,
            radius,
            trackPaint
        )

        if (progressPercent <= 0) {
            return
        }

        val fillWidth =
            when {
                progressPercent >= 100 -> {
                    width.toFloat()
                }

                else -> {
                    width.toFloat() * (progressPercent / 100f)
                }
            }

        fillRect.set(
            0f,
            0f,
            fillWidth,
            barHeight
        )

        canvas.drawRoundRect(
            fillRect,
            radius,
            radius,
            fillPaint
        )
    }
}