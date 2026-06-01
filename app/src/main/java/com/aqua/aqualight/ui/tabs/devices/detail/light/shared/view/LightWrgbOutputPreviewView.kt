package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IntRange

class LightWrgbOutputPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 255, 255, 255)
    }

    private val outputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp()
        color = Color.argb(70, 120, 150, 175)
    }

    private val rect = RectF()

    private var masterPercent: Int? = null
    private var redPercent: Int? = null
    private var greenPercent: Int? = null
    private var bluePercent: Int? = null
    private var whitePercent: Int? = null

    fun setOutput(
        @IntRange(from = 0, to = 100) masterPercent: Int?,
        @IntRange(from = 0, to = 100) redPercent: Int?,
        @IntRange(from = 0, to = 100) greenPercent: Int?,
        @IntRange(from = 0, to = 100) bluePercent: Int?,
        @IntRange(from = 0, to = 100) whitePercent: Int?
    ) {
        this.masterPercent = masterPercent?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        this.redPercent = redPercent?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        this.greenPercent = greenPercent?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        this.bluePercent = bluePercent?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        this.whitePercent = whitePercent?.coerceIn(MIN_PERCENT, MAX_PERCENT)

        invalidate()
    }

    fun clear() {
        masterPercent = null
        redPercent = null
        greenPercent = null
        bluePercent = null
        whitePercent = null

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val radius = 18f.dp()

        rect.set(
            0f,
            0f,
            width.toFloat(),
            height.toFloat()
        )

        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            backgroundPaint
        )

        if (!hasOutput()) {
            canvas.drawRoundRect(
                rect.insetCopy(
                    inset = 0.5f.dp()
                ),
                radius,
                radius,
                strokePaint
            )
            return
        }

        val previewColor = calculatePreviewColor()

        outputPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.argb(70, 255, 255, 255),
                previewColor,
                Color.argb(36, 0, 0, 0)
            ),
            floatArrayOf(
                0f,
                0.52f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            outputPaint
        )

        outputPaint.shader = null

        canvas.drawRoundRect(
            rect.insetCopy(
                inset = 0.5f.dp()
            ),
            radius,
            radius,
            strokePaint
        )
    }

    private fun hasOutput(): Boolean {
        return masterPercent != null ||
            redPercent != null ||
            greenPercent != null ||
            bluePercent != null ||
            whitePercent != null
    }

    private fun calculatePreviewColor(): Int {
        val master = (masterPercent ?: MAX_PERCENT)
            .coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f

        val red = ((redPercent ?: MIN_PERCENT) / 100f) * master
        val green = ((greenPercent ?: MIN_PERCENT) / 100f) * master
        val blue = ((bluePercent ?: MIN_PERCENT) / 100f) * master
        val white = ((whitePercent ?: MIN_PERCENT) / 100f) * master

        val r = ((red * 255f) + (white * 230f))
            .coerceIn(0f, 255f)
            .toInt()

        val g = ((green * 255f) + (white * 235f))
            .coerceIn(0f, 255f)
            .toInt()

        val b = ((blue * 255f) + (white * 245f))
            .coerceIn(0f, 255f)
            .toInt()

        val alpha = if (master <= 0f) {
            42
        } else {
            190
        }

        return Color.argb(
            alpha,
            r,
            g,
            b
        )
    }

    private fun RectF.insetCopy(
        inset: Float
    ): RectF {
        return RectF(
            left + inset,
            top + inset,
            right - inset,
            bottom - inset
        )
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private companion object {
        private const val MIN_PERCENT = 0
        private const val MAX_PERCENT = 100
    }
}