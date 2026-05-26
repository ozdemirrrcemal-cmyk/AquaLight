package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class DosingCalibrationIllustrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(
    context,
    attrs
) {

    var stepIndex: Int = 0
        set(value) {
            field = value.coerceIn(
                minimumValue = 0,
                maximumValue = 5
            )

            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#46536D")
    }

    private val softLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#2D3850")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111A35")
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
    }

    private val dangerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F43F5E")
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
        alpha = 210
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E1EF")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        val safeWidth = width.toFloat()
        val safeHeight = height.toFloat()

        val size = min(
            safeWidth,
            safeHeight
        )

        val centerX = safeWidth / 2f
        val centerY = safeHeight / 2f

        when (stepIndex) {
            0 -> drawNameStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            1 -> drawPrimeStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            2 -> drawStartCalibrationStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            3 -> drawMeasureInputStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            4 -> drawDoseFourMlStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            else -> drawConfirmStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )
        }
    }

    private fun drawNameStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawBottle(
            canvas = canvas,
            x = centerX - size * 0.20f,
            y = centerY + size * 0.03f,
            scale = size / 180f
        )

        drawTag(
            canvas = canvas,
            centerX = centerX + size * 0.15f,
            centerY = centerY - size * 0.10f,
            width = size * 0.32f,
            height = size * 0.18f,
            label = "NPK"
        )

        drawDrop(
            canvas = canvas,
            centerX = centerX + size * 0.02f,
            centerY = centerY + size * 0.12f,
            scale = size / 180f
        )
    }

    private fun drawPrimeStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawPump(
            canvas = canvas,
            centerX = centerX,
            topY = centerY - size * 0.30f,
            scale = size / 180f
        )

        drawBottle(
            canvas = canvas,
            x = centerX - size * 0.20f,
            y = centerY + size * 0.18f,
            scale = size / 180f
        )

        drawTube(
            canvas = canvas,
            startX = centerX - size * 0.10f,
            startY = centerY - size * 0.02f,
            endX = centerX - size * 0.18f,
            endY = centerY + size * 0.07f
        )

        drawDrop(
            canvas = canvas,
            centerX = centerX + size * 0.12f,
            centerY = centerY + size * 0.08f,
            scale = size / 180f
        )

        drawArrowDown(
            canvas = canvas,
            x = centerX + size * 0.23f,
            y = centerY + size * 0.05f,
            height = size * 0.20f
        )
    }

    private fun drawStartCalibrationStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawPump(
            canvas = canvas,
            centerX = centerX,
            topY = centerY - size * 0.30f,
            scale = size / 180f
        )

        drawCylinder(
            canvas = canvas,
            centerX = centerX + size * 0.14f,
            bottomY = centerY + size * 0.28f,
            scale = size / 180f,
            liquidPercent = 0.18f
        )

        drawBottle(
            canvas = canvas,
            x = centerX - size * 0.22f,
            y = centerY + size * 0.18f,
            scale = size / 180f
        )

        drawArrowDown(
            canvas = canvas,
            x = centerX + size * 0.25f,
            y = centerY + size * 0.02f,
            height = size * 0.22f
        )
    }

    private fun drawMeasureInputStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawCylinder(
            canvas = canvas,
            centerX = centerX,
            bottomY = centerY + size * 0.32f,
            scale = size / 135f,
            liquidPercent = 0.58f
        )

        val lineY = centerY + size * 0.04f

        canvas.drawLine(
            centerX + size * 0.08f,
            lineY,
            centerX + size * 0.28f,
            lineY,
            linePaint
        )

        canvas.drawCircle(
            centerX + size * 0.31f,
            lineY,
            dp(3f),
            accentPaint
        )

        drawTag(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY + size * 0.42f,
            width = size * 0.36f,
            height = size * 0.16f,
            label = "3.50 ml"
        )
    }

    private fun drawDoseFourMlStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawPump(
            canvas = canvas,
            centerX = centerX,
            topY = centerY - size * 0.30f,
            scale = size / 180f
        )

        drawCylinder(
            canvas = canvas,
            centerX = centerX + size * 0.13f,
            bottomY = centerY + size * 0.30f,
            scale = size / 180f,
            liquidPercent = 0.40f
        )

        textPaint.textSize = sp(13f)

        canvas.drawText(
            "4 ml",
            centerX,
            centerY + size * 0.42f,
            textPaint
        )
    }

    private fun drawConfirmStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        drawCylinder(
            canvas = canvas,
            centerX = centerX,
            bottomY = centerY + size * 0.30f,
            scale = size / 145f,
            liquidPercent = 0.62f
        )

        canvas.drawCircle(
            centerX + size * 0.25f,
            centerY - size * 0.10f,
            size * 0.11f,
            accentPaint
        )

        val check = Path().apply {
            moveTo(
                centerX + size * 0.20f,
                centerY - size * 0.10f
            )
            lineTo(
                centerX + size * 0.24f,
                centerY - size * 0.05f
            )
            lineTo(
                centerX + size * 0.31f,
                centerY - size * 0.16f
            )
        }

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#06111F")
        }

        canvas.drawPath(
            check,
            checkPaint
        )
    }

    private fun drawPump(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        scale: Float
    ) {
        val bodyWidth = 62f * scale
        val bodyHeight = 48f * scale

        val body = RectF(
            centerX - bodyWidth / 2f,
            topY,
            centerX + bodyWidth / 2f,
            topY + bodyHeight
        )

        canvas.drawRoundRect(
            body,
            10f * scale,
            10f * scale,
            fillPaint
        )

        canvas.drawRoundRect(
            body,
            10f * scale,
            10f * scale,
            linePaint
        )

        canvas.drawCircle(
            centerX,
            topY + bodyHeight / 2f,
            5f * scale,
            accentPaint
        )

        canvas.drawLine(
            centerX - 18f * scale,
            topY + bodyHeight,
            centerX - 18f * scale,
            topY + bodyHeight + 28f * scale,
            softLinePaint
        )

        canvas.drawLine(
            centerX + 18f * scale,
            topY + bodyHeight,
            centerX + 18f * scale,
            topY + bodyHeight + 28f * scale,
            softLinePaint
        )
    }

    private fun drawBottle(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float
    ) {
        val bottle = RectF(
            x - 10f * scale,
            y - 34f * scale,
            x + 10f * scale,
            y + 20f * scale
        )

        canvas.drawRoundRect(
            bottle,
            4f * scale,
            4f * scale,
            fillPaint
        )

        canvas.drawRoundRect(
            bottle,
            4f * scale,
            4f * scale,
            softLinePaint
        )

        val liquid = RectF(
            bottle.left + 3f * scale,
            bottle.centerY(),
            bottle.right - 3f * scale,
            bottle.bottom - 3f * scale
        )

        canvas.drawRoundRect(
            liquid,
            3f * scale,
            3f * scale,
            liquidPaint
        )
    }

    private fun drawCylinder(
        canvas: Canvas,
        centerX: Float,
        bottomY: Float,
        scale: Float,
        liquidPercent: Float
    ) {
        val width = 38f * scale
        val height = 95f * scale

        val left = centerX - width / 2f
        val top = bottomY - height

        val outline = Path().apply {
            moveTo(
                left + width * 0.25f,
                top
            )
            lineTo(
                left + width * 0.75f,
                top
            )
            lineTo(
                left + width * 0.62f,
                top + height * 0.83f
            )
            lineTo(
                left + width,
                bottomY
            )
            lineTo(
                left,
                bottomY
            )
            lineTo(
                left + width * 0.38f,
                top + height * 0.83f
            )
            close()
        }

        canvas.drawPath(
            outline,
            softLinePaint
        )

        val liquidHeight = height * liquidPercent.coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        ) * 0.52f

        val liquidRect = RectF(
            left + width * 0.22f,
            bottomY - liquidHeight - height * 0.11f,
            left + width * 0.78f,
            bottomY - height * 0.11f
        )

        canvas.drawRoundRect(
            liquidRect,
            4f * scale,
            4f * scale,
            liquidPaint
        )

        repeat(5) { index ->
            val tickY = top + height * 0.25f + index * height * 0.10f

            canvas.drawLine(
                left + width * 0.62f,
                tickY,
                left + width * 0.78f,
                tickY,
                softLinePaint
            )
        }
    }

    private fun drawDrop(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val path = Path().apply {
            moveTo(
                centerX,
                centerY - 16f * scale
            )
            cubicTo(
                centerX - 12f * scale,
                centerY - 2f * scale,
                centerX - 10f * scale,
                centerY + 12f * scale,
                centerX,
                centerY + 15f * scale
            )
            cubicTo(
                centerX + 10f * scale,
                centerY + 12f * scale,
                centerX + 12f * scale,
                centerY - 2f * scale,
                centerX,
                centerY - 16f * scale
            )
            close()
        }

        canvas.drawPath(
            path,
            dangerPaint
        )
    }

    private fun drawTube(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val path = Path().apply {
            moveTo(
                startX,
                startY
            )
            cubicTo(
                startX - dp(16f),
                startY + dp(12f),
                endX + dp(10f),
                endY - dp(16f),
                endX,
                endY
            )
        }

        canvas.drawPath(
            path,
            linePaint
        )
    }

    private fun drawArrowDown(
        canvas: Canvas,
        x: Float,
        y: Float,
        height: Float
    ) {
        canvas.drawLine(
            x,
            y,
            x,
            y + height,
            linePaint
        )

        canvas.drawLine(
            x,
            y + height,
            x - dp(7f),
            y + height - dp(8f),
            linePaint
        )

        canvas.drawLine(
            x,
            y + height,
            x + dp(7f),
            y + height - dp(8f),
            linePaint
        )
    }

    private fun drawTag(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        label: String
    ) {
        val rect = RectF(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )

        canvas.drawRoundRect(
            rect,
            dp(12f),
            dp(12f),
            fillPaint
        )

        canvas.drawRoundRect(
            rect,
            dp(12f),
            dp(12f),
            linePaint
        )

        textPaint.textSize = sp(11f)

        canvas.drawText(
            label,
            centerX,
            centerY + textPaint.textSize / 3f,
            textPaint
        )
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