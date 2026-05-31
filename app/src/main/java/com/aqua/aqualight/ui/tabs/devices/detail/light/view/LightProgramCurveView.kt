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

class LightProgramCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AEB9C6")
        textSize = 10f.sp()
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E6ED")
        textSize = 10f.sp()
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 1f.dp()
        alpha = 95
        pathEffect = DashPathEffect(
            floatArrayOf(
                3f.dp(),
                7f.dp()
            ),
            0f
        )
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 1f.dp()
        alpha = 130
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(
            10f.dp(),
            BlurMaskFilter.Blur.NORMAL
        )
        alpha = 95
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sunrisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA32B")
        textSize = 14f.sp()
        textAlign = Paint.Align.CENTER
    }

    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E26FD7")
        textSize = 14f.sp()
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

        if (width <= 0 || height <= 0) {
            return
        }

        val left = 46f.dp()
        val right = width - 8f.dp()
        val top = 9f.dp()
        val bottom = height - 34f.dp()

        val chartWidth = right - left
        val chartHeight = bottom - top

        val y0 = bottom
        val y25 = bottom - chartHeight * 0.25f
        val y50 = bottom - chartHeight * 0.50f
        val y75 = bottom - chartHeight * 0.75f
        val y100 = top

        drawGrid(
            canvas = canvas,
            left = left,
            right = right,
            y0 = y0,
            y25 = y25,
            y50 = y50,
            y75 = y75,
            y100 = y100
        )

        val p0 = PointF(
            left + chartWidth * 0.02f,
            intensityToY(
                intensity = startIntensity,
                top = top,
                bottom = bottom
            )
        )

        val p1 = PointF(
            left + chartWidth * 0.23f,
            intensityToY(
                intensity = midIntensity(
                    first = startIntensity,
                    second = sunriseEndIntensity
                ),
                top = top,
                bottom = bottom
            )
        )

        val p2 = PointF(
            left + chartWidth * 0.36f,
            intensityToY(
                intensity = sunriseEndIntensity,
                top = top,
                bottom = bottom
            )
        )

        val p3 = PointF(
            left + chartWidth * 0.62f,
            intensityToY(
                intensity = peakEndIntensity,
                top = top,
                bottom = bottom
            )
        )

        val p4 = PointF(
            left + chartWidth * 0.75f,
            intensityToY(
                intensity = midIntensity(
                    first = peakEndIntensity,
                    second = endIntensity
                ),
                top = top,
                bottom = bottom
            )
        )

        val p5 = PointF(
            right - chartWidth * 0.02f,
            intensityToY(
                intensity = endIntensity,
                top = top,
                bottom = bottom
            )
        )

        val curvePath = Path().apply {
            moveTo(
                p0.x,
                p0.y
            )

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

            lineTo(
                p3.x,
                p3.y
            )

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
            lineTo(
                p5.x,
                y0
            )
            lineTo(
                p0.x,
                y0
            )
            close()
        }

        fillPaint.shader = LinearGradient(
            0f,
            y100,
            0f,
            y0,
            intArrayOf(
                Color.argb(
                    120,
                    40,
                    230,
                    240
                ),
                Color.argb(
                    14,
                    40,
                    230,
                    240
                )
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(
            fillPath,
            fillPaint
        )

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

        canvas.drawPath(
            curvePath,
            glowPaint
        )

        canvas.drawPath(
            curvePath,
            linePaint
        )

        drawPoint(
            canvas = canvas,
            point = p0,
            colorHex = "#FFA32B"
        )

        drawPoint(
            canvas = canvas,
            point = p1,
            colorHex = "#B9F57D"
        )

        drawPoint(
            canvas = canvas,
            point = p2,
            colorHex = "#28E6F0"
        )

        drawPoint(
            canvas = canvas,
            point = p3,
            colorHex = "#28E6F0"
        )

        drawPoint(
            canvas = canvas,
            point = p4,
            colorHex = "#9B86FF"
        )

        drawPoint(
            canvas = canvas,
            point = p5,
            colorHex = "#FF6D8C"
        )

        drawBottomLabels(
            canvas = canvas,
            left = left,
            right = right,
            bottom = bottom,
            p2 = p2,
            p3 = p3
        )
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
        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.textSize = 10f.sp()
        labelPaint.color = Color.parseColor("#AEB9C6")

        canvas.drawText(
            "100%",
            left - 8f.dp(),
            y100 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "75%",
            left - 8f.dp(),
            y75 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "50%",
            left - 8f.dp(),
            y50 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "25%",
            left - 8f.dp(),
            y25 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "0%",
            left - 8f.dp(),
            y0 + 4f.dp(),
            labelPaint
        )

        canvas.drawLine(
            left,
            y100,
            right,
            y100,
            gridPaint
        )

        canvas.drawLine(
            left,
            y75,
            right,
            y75,
            gridPaint
        )

        canvas.drawLine(
            left,
            y50,
            right,
            y50,
            gridPaint
        )

        canvas.drawLine(
            left,
            y25,
            right,
            y25,
            gridPaint
        )

        canvas.drawLine(
            left,
            y0,
            right,
            y0,
            axisPaint
        )

        canvas.drawLine(
            left,
            y100 - 6f.dp(),
            left,
            y0,
            axisPaint
        )
    }

    private fun drawBottomLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        bottom: Float,
        p2: PointF,
        p3: PointF
    ) {
        val labelY = bottom + 23f.dp()

        sunrisePaint.textSize = 14f.sp()
        sunsetPaint.textSize = 14f.sp()

        canvas.drawText(
            "☀",
            left - 19f.dp(),
            labelY,
            sunrisePaint
        )

        canvas.drawText(
            "☾",
            right - 47f.dp(),
            labelY,
            sunsetPaint
        )

        timePaint.textSize = 10f.sp()
        timePaint.color = Color.parseColor("#E0E6ED")

        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            startTime,
            left + 4f.dp(),
            labelY,
            timePaint
        )

        timePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            sunriseEndTime,
            p2.x,
            labelY,
            timePaint
        )

        canvas.drawText(
            peakEndTime,
            p3.x,
            labelY,
            timePaint
        )

        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            endTime,
            right,
            labelY,
            timePaint
        )
    }

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        colorHex: String
    ) {
        pointPaint.color = Color.parseColor(colorHex)

        canvas.drawCircle(
            point.x,
            point.y,
            4.7f.dp(),
            pointPaint
        )
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

        this.startIntensity = startIntensity.coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )

        this.sunriseEndIntensity = sunriseEndIntensity.coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )

        this.peakEndIntensity = peakEndIntensity.coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )

        this.endIntensity = endIntensity.coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )

        invalidate()
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

    private fun midIntensity(
        first: Int,
        second: Int
    ): Int {
        return ((first + second) / 2f).toInt().coerceIn(
            minimumValue = 0,
            maximumValue = 100
        )
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }
}