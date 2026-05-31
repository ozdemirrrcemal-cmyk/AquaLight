package com.aqua.aqualight.ui.tabs.devices.detail.light.view

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import kotlin.math.roundToInt

class LightProgramCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E6ED")
        textSize = 12f.sp()
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 1f.dp()
        alpha = 130
        pathEffect = DashPathEffect(floatArrayOf(3f.dp(), 7f.dp()), 0f)
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 1f.dp()
        alpha = 160
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(14f.dp(), BlurMaskFilter.Blur.NORMAL)
        alpha = 120
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sunrisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA32B")
        textSize = 25f.sp()
        textAlign = Paint.Align.CENTER
    }

    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E26FD7")
        textSize = 25f.sp()
        textAlign = Paint.Align.CENTER
    }

    private var startTime = "09:00"
    private var peakStartTime = "12:00"
    private var peakEndTime = "16:00"
    private var endTime = "19:15"

    private val colors = intArrayOf(
        Color.parseColor("#FF9F2D"),
        Color.parseColor("#C9F36B"),
        Color.parseColor("#28E6F0"),
        Color.parseColor("#28E6F0"),
        Color.parseColor("#8D7CFF"),
        Color.parseColor("#FF6D8C")
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 62f.dp()
        val right = width - 14f.dp()
        val top = 14f.dp()
        val bottom = height - 48f.dp()

        val chartWidth = right - left
        val chartHeight = bottom - top

        val y0 = bottom
        val y25 = bottom - chartHeight * 0.25f
        val y50 = bottom - chartHeight * 0.50f
        val y75 = bottom - chartHeight * 0.75f
        val y100 = top

        drawGrid(canvas, left, right, y0, y25, y50, y75, y100)

        val p0 = PointF(left + chartWidth * 0.02f, y0)
        val p1 = PointF(left + chartWidth * 0.23f, bottom - chartHeight * 0.45f)
        val p2 = PointF(left + chartWidth * 0.36f, y100)
        val p3 = PointF(left + chartWidth * 0.62f, y100)
        val p4 = PointF(left + chartWidth * 0.75f, bottom - chartHeight * 0.45f)
        val p5 = PointF(right - chartWidth * 0.02f, y0)

        val curvePath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(
                left + chartWidth * 0.10f, y0,
                left + chartWidth * 0.18f, bottom - chartHeight * 0.22f,
                p1.x,
                p1.y
            )
            cubicTo(
                left + chartWidth * 0.30f, bottom - chartHeight * 0.70f,
                left + chartWidth * 0.32f, y100,
                p2.x,
                p2.y
            )
            lineTo(p3.x, p3.y)
            cubicTo(
                left + chartWidth * 0.66f, y100,
                left + chartWidth * 0.69f, bottom - chartHeight * 0.25f,
                p4.x,
                p4.y
            )
            cubicTo(
                left + chartWidth * 0.84f, bottom - chartHeight * 0.08f,
                left + chartWidth * 0.91f, y0,
                p5.x,
                p5.y
            )
        }

        val fillPath = Path(curvePath).apply {
            lineTo(p5.x, y0)
            lineTo(p0.x, y0)
            close()
        }

        val gradient = LinearGradient(
            left,
            0f,
            right,
            0f,
            colors,
            null,
            Shader.TileMode.CLAMP
        )

        fillPaint.shader = LinearGradient(
            0f,
            y100,
            0f,
            y0,
            intArrayOf(
                Color.argb(145, 40, 230, 240),
                Color.argb(18, 40, 230, 240)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        linePaint.shader = gradient
        glowPaint.shader = gradient

        canvas.drawPath(curvePath, glowPaint)
        canvas.drawPath(curvePath, linePaint)

        drawPoint(canvas, p0, "#FFA32B")
        drawPoint(canvas, p1, "#B9F57D")
        drawPoint(canvas, p2, "#28E6F0")
        drawPoint(canvas, p3, "#28E6F0")
        drawPoint(canvas, p4, "#9B86FF")
        drawPoint(canvas, p5, "#FF6D8C")

        drawBottomLabels(canvas, left, right, bottom, chartWidth, p2, p3)
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        y0: Float,
        y25: Float,
        y50: Float,
        y75: Float,
        y100: Float
    ) {
        textPaint.textAlign = Paint.Align.RIGHT

        canvas.drawText("100%", left - 10f.dp(), y100 + 4f.dp(), textPaint)
        canvas.drawText("75%", left - 10f.dp(), y75 + 4f.dp(), textPaint)
        canvas.drawText("50%", left - 10f.dp(), y50 + 4f.dp(), textPaint)
        canvas.drawText("25%", left - 10f.dp(), y25 + 4f.dp(), textPaint)
        canvas.drawText("0%", left - 10f.dp(), y0 + 4f.dp(), textPaint)

        canvas.drawLine(left, y100, right, y100, gridPaint)
        canvas.drawLine(left, y75, right, y75, gridPaint)
        canvas.drawLine(left, y50, right, y50, gridPaint)
        canvas.drawLine(left, y25, right, y25, gridPaint)
        canvas.drawLine(left, y0, right, y0, axisPaint)
        canvas.drawLine(left, y100 - 8f.dp(), left, y0, axisPaint)
    }

    private fun drawBottomLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        bottom: Float,
        chartWidth: Float,
        p2: PointF,
        p3: PointF
    ) {
        val labelY = bottom + 31f.dp()

        sunrisePaint.textSize = 23f.sp()
        sunsetPaint.textSize = 23f.sp()

        canvas.drawText("☀", left - 27f.dp(), labelY, sunrisePaint)
        canvas.drawText("☾", right - 45f.dp(), labelY, sunsetPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 18f.sp()
        textPaint.color = Color.parseColor("#F1F6FF")

        canvas.drawText(startTime, left + 4f.dp(), labelY, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(peakStartTime, p2.x, labelY, textPaint)
        canvas.drawText(peakEndTime, p3.x, labelY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(endTime, right, labelY, textPaint)

        textPaint.textSize = 12f.sp()
        textPaint.color = Color.parseColor("#E0E6ED")
    }

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        colorHex: String
    ) {
        pointPaint.color = Color.parseColor(colorHex)
        canvas.drawCircle(point.x, point.y, 6f.dp(), pointPaint)
    }

    fun setProgramCurve(
        start: String,
        peakStart: String,
        peakEnd: String,
        end: String
    ) {
        startTime = start
        peakStartTime = peakStart
        peakEndTime = peakEnd
        endTime = end
        invalidate()
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }
}