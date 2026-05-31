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
import kotlin.math.max
import kotlin.math.min

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
        textSize = 23f.sp()
        textAlign = Paint.Align.CENTER
    }

    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E26FD7")
        textSize = 23f.sp()
        textAlign = Paint.Align.CENTER
    }

    private var startTime = "09:00"
    private var sunriseEndTime = "12:00"
    private var peakEndTime = "16:00"
    private var endTime = "19:15"

    private var startIntensity = 0
    private var sunriseEndIntensity = 100
    private var peakEndIntensity = 100
    private var endIntensity = 0

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

        if (width <= 0 || height <= 0) return

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

        val p0 = PointF(left + chartWidth * 0.02f, intensityToY(startIntensity, top, bottom))
        val p1 = PointF(left + chartWidth * 0.23f, intensityToY(midIntensity(startIntensity, sunriseEndIntensity), top, bottom))
        val p2 = PointF(left + chartWidth * 0.36f, intensityToY(sunriseEndIntensity, top, bottom))
        val p3 = PointF(left + chartWidth * 0.62f, intensityToY(peakEndIntensity, top, bottom))
        val p4 = PointF(left + chartWidth * 0.75f, intensityToY(midIntensity(peakEndIntensity, endIntensity), top, bottom))
        val p5 = PointF(right - chartWidth * 0.02f, intensityToY(endIntensity, top, bottom))

        val curvePath = Path().apply {
            moveTo(p0.x, p0.y)

            cubicTo(
                left + chartWidth * 0.10f,
                p0.y,
                left + chartWidth * 0.18f,
                p1.y,
                p1.x,
                p1.y
            )

            cubicTo(
                left + chartWidth * 0.30f,
                p1.y,
                left + chartWidth * 0.32f,
                p2.y,
                p2.x,
                p2.y
            )

            lineTo(p3.x, p3.y)

            cubicTo(
                left + chartWidth * 0.66f,
                p3.y,
                left + chartWidth * 0.69f,
                p4.y,
                p4.x,
                p4.y
            )

            cubicTo(
                left + chartWidth * 0.84f,
                p4.y,
                left + chartWidth * 0.91f,
                p5.y,
                p5.x,
                p5.y
            )
        }

        val fillPath = Path(curvePath).apply {
            lineTo(p5.x, y0)
            lineTo(p0.x, y0)
            close()
        }

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

        val gradient = LinearGradient(
            left,
            0f,
            right,
            0f,
            colors,
            null,
            Shader.TileMode.CLAMP
        )

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

        drawBottomLabels(canvas, left, right, bottom, p2, p3)
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
        textPaint.textSize = 12f.sp()
        textPaint.color = Color.parseColor("#E0E6ED")

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
        p2: PointF,
        p3: PointF
    ) {
        val labelY = bottom + 31f.dp()

        canvas.drawText("☀", left - 27f.dp(), labelY, sunrisePaint)
        canvas.drawText("☾", right - 45f.dp(), labelY, sunsetPaint)

        textPaint.textSize = 18f.sp()
        textPaint.color = Color.parseColor("#F1F6FF")

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(startTime, left + 4f.dp(), labelY, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(sunriseEndTime, p2.x, labelY, textPaint)
        canvas.drawText(peakEndTime, p3.x, labelY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(endTime, right, labelY, textPaint)
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
        sunriseEnd: String,
        peakEnd: String,
        end: String,
        startIntensity: Int,
        sunriseEndIntensity: Int,
        peakEndIntensity: Int,
        endIntensity: Int
    ) {
        startTime = start
        sunriseEndTime = sunriseEnd
        peakEndTime = peakEnd
        endTime = end

        this.startIntensity = startIntensity.coerceIn(0, 100)
        this.sunriseEndIntensity = sunriseEndIntensity.coerceIn(0, 100)
        this.peakEndIntensity = peakEndIntensity.coerceIn(0, 100)
        this.endIntensity = endIntensity.coerceIn(0, 100)

        invalidate()
    }

    private fun intensityToY(
        intensity: Int,
        top: Float,
        bottom: Float
    ): Float {
        val safeIntensity = intensity.coerceIn(0, 100)
        return bottom - ((bottom - top) * safeIntensity / 100f)
    }

    private fun midIntensity(
        first: Int,
        second: Int
    ): Int {
        return ((first + second) / 2f).toInt().coerceIn(0, 100)
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }
}