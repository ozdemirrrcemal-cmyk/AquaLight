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

class LightProgramCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class CurveRenderStyle {
        SPECTRUM,
        SIMPLE_INTENSITY,
        CHANNEL
    }

    private var renderStyle: CurveRenderStyle = CurveRenderStyle.SPECTRUM
    private var singleCurveColor: Int = Color.parseColor("#3FD1D0")

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

    private var startTime = "08:00"
    private var peakStartTime = "12:00"
    private var peakEndTime = "16:00"
    private var endTime = "20:00"

    private var startIntensity = 0
    private var peakStartIntensity = 100
    private var peakEndIntensity = 100
    private var endIntensity = 0

    private val spectrumColors = intArrayOf(
        Color.parseColor("#FF9F2D"),
        Color.parseColor("#C9F36B"),
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

        val startMinutes = timeToMinutes(startTime)
        val endMinutes = adjustedMinutes(
            time = endTime,
            startMinutes = startMinutes
        )

        val rangeMinutes =
            max(
                MIN_VISIBLE_MINUTES,
                endMinutes - startMinutes
            ).toFloat()

        fun xForTime(
            time: String
        ): Float {
            val minutes =
                adjustedMinutes(
                    time = time,
                    startMinutes = startMinutes
                )

            val progress =
                ((minutes - startMinutes) / rangeMinutes)
                    .coerceIn(0f, 1f)

            return left + chartWidth * progress
        }

        val startPoint =
            PointF(
                xForTime(startTime),
                intensityToY(
                    intensity = startIntensity,
                    top = top,
                    bottom = bottom
                )
            )

        val peakStartPoint =
            PointF(
                xForTime(peakStartTime),
                intensityToY(
                    intensity = peakStartIntensity,
                    top = top,
                    bottom = bottom
                )
            )

        val peakEndPoint =
            PointF(
                xForTime(peakEndTime),
                intensityToY(
                    intensity = peakEndIntensity,
                    top = top,
                    bottom = bottom
                )
            )

        val endPoint =
            PointF(
                xForTime(endTime),
                intensityToY(
                    intensity = endIntensity,
                    top = top,
                    bottom = bottom
                )
            )

        val curvePath =
            createCurvePath(
                startPoint = startPoint,
                peakStartPoint = peakStartPoint,
                peakEndPoint = peakEndPoint,
                endPoint = endPoint,
                chartWidth = chartWidth
            )

        val fillPath =
            Path(curvePath).apply {
                lineTo(
                    endPoint.x,
                    y0
                )
                lineTo(
                    startPoint.x,
                    y0
                )
                close()
            }

        configurePaints(
            left = left,
            right = right,
            top = y100,
            bottom = y0
        )

        canvas.drawPath(
            fillPath,
            fillPaint
        )

        canvas.drawPath(
            curvePath,
            glowPaint
        )

        canvas.drawPath(
            curvePath,
            linePaint
        )

        drawCurvePoints(
            canvas = canvas,
            startPoint = startPoint,
            peakStartPoint = peakStartPoint,
            peakEndPoint = peakEndPoint,
            endPoint = endPoint
        )

        drawBottomLabels(
            canvas = canvas,
            left = left,
            right = right,
            bottom = bottom,
            startPoint = startPoint,
            peakStartPoint = peakStartPoint,
            peakEndPoint = peakEndPoint,
            endPoint = endPoint
        )
    }

    private fun createCurvePath(
        startPoint: PointF,
        peakStartPoint: PointF,
        peakEndPoint: PointF,
        endPoint: PointF,
        chartWidth: Float
    ): Path {
        return Path().apply {
            moveTo(
                startPoint.x,
                startPoint.y
            )

            cubicTo(
                startPoint.x + chartWidth * 0.14f,
                startPoint.y,
                peakStartPoint.x - chartWidth * 0.10f,
                peakStartPoint.y,
                peakStartPoint.x,
                peakStartPoint.y
            )

            lineTo(
                peakEndPoint.x,
                peakEndPoint.y
            )

            cubicTo(
                peakEndPoint.x + chartWidth * 0.10f,
                peakEndPoint.y,
                endPoint.x - chartWidth * 0.14f,
                endPoint.y,
                endPoint.x,
                endPoint.y
            )
        }
    }

    private fun configurePaints(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        when (renderStyle) {
            CurveRenderStyle.SPECTRUM -> {
                val gradient =
                    LinearGradient(
                        left,
                        0f,
                        right,
                        0f,
                        spectrumColors,
                        null,
                        Shader.TileMode.CLAMP
                    )

                linePaint.shader = gradient
                glowPaint.shader = gradient

                fillPaint.shader =
                    LinearGradient(
                        0f,
                        top,
                        0f,
                        bottom,
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
            }

            CurveRenderStyle.SIMPLE_INTENSITY,
            CurveRenderStyle.CHANNEL -> {
                linePaint.shader = null
                glowPaint.shader = null

                linePaint.color = singleCurveColor
                glowPaint.color = singleCurveColor

                fillPaint.shader =
                    LinearGradient(
                        0f,
                        top,
                        0f,
                        bottom,
                        intArrayOf(
                            Color.argb(
                                110,
                                Color.red(singleCurveColor),
                                Color.green(singleCurveColor),
                                Color.blue(singleCurveColor)
                            ),
                            Color.argb(
                                12,
                                Color.red(singleCurveColor),
                                Color.green(singleCurveColor),
                                Color.blue(singleCurveColor)
                            )
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
            }
        }
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

        canvas.drawText("100%", left - 8f.dp(), y100 + 4f.dp(), labelPaint)
        canvas.drawText("75%", left - 8f.dp(), y75 + 4f.dp(), labelPaint)
        canvas.drawText("50%", left - 8f.dp(), y50 + 4f.dp(), labelPaint)
        canvas.drawText("25%", left - 8f.dp(), y25 + 4f.dp(), labelPaint)
        canvas.drawText("0%", left - 8f.dp(), y0 + 4f.dp(), labelPaint)

        canvas.drawLine(left, y100, right, y100, gridPaint)
        canvas.drawLine(left, y75, right, y75, gridPaint)
        canvas.drawLine(left, y50, right, y50, gridPaint)
        canvas.drawLine(left, y25, right, y25, gridPaint)
        canvas.drawLine(left, y0, right, y0, axisPaint)
        canvas.drawLine(left, y100 - 6f.dp(), left, y0, axisPaint)
    }

    private fun drawCurvePoints(
        canvas: Canvas,
        startPoint: PointF,
        peakStartPoint: PointF,
        peakEndPoint: PointF,
        endPoint: PointF
    ) {
        when (renderStyle) {
            CurveRenderStyle.SPECTRUM -> {
                drawPoint(canvas, startPoint, Color.parseColor("#FFA32B"))
                drawPoint(canvas, peakStartPoint, Color.parseColor("#28E6F0"))
                drawPoint(canvas, peakEndPoint, Color.parseColor("#28E6F0"))
                drawPoint(canvas, endPoint, Color.parseColor("#FF6D8C"))
            }

            CurveRenderStyle.SIMPLE_INTENSITY -> {
                drawPoint(canvas, startPoint, Color.parseColor("#FFA32B"))
                drawPoint(canvas, peakStartPoint, singleCurveColor)
                drawPoint(canvas, peakEndPoint, singleCurveColor)
                drawPoint(canvas, endPoint, Color.parseColor("#FF8A65"))
            }

            CurveRenderStyle.CHANNEL -> {
                drawPoint(canvas, startPoint, singleCurveColor)
                drawPoint(canvas, peakStartPoint, singleCurveColor)
                drawPoint(canvas, peakEndPoint, singleCurveColor)
                drawPoint(canvas, endPoint, singleCurveColor)
            }
        }
    }

    private fun drawBottomLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        bottom: Float,
        startPoint: PointF,
        peakStartPoint: PointF,
        peakEndPoint: PointF,
        endPoint: PointF
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
            startPoint.x + 4f.dp(),
            labelY,
            timePaint
        )

        timePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            peakStartTime,
            peakStartPoint.x,
            labelY,
            timePaint
        )

        canvas.drawText(
            peakEndTime,
            peakEndPoint.x,
            labelY,
            timePaint
        )

        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            endTime,
            endPoint.x,
            labelY,
            timePaint
        )
    }

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        color: Int
    ) {
        pointPaint.shader = null
        pointPaint.style = Paint.Style.FILL
        pointPaint.color = color
        pointPaint.alpha = 230

        canvas.drawCircle(
            point.x,
            point.y,
            4.7f.dp(),
            pointPaint
        )

        pointPaint.alpha = 255
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
        renderStyle = CurveRenderStyle.SPECTRUM

        setInternalCurve(
            start = start,
            peakStart = sunriseEnd,
            peakEnd = peakEnd,
            end = end,
            startIntensity = startIntensity,
            peakStartIntensity = sunriseEndIntensity,
            peakEndIntensity = peakEndIntensity,
            endIntensity = endIntensity
        )
    }

    fun setSimpleIntensityCurve(
        start: String,
        peakStart: String,
        peakEnd: String,
        end: String,
        startIntensity: Int,
        peakStartIntensity: Int,
        peakEndIntensity: Int,
        endIntensity: Int
    ) {
        renderStyle = CurveRenderStyle.SIMPLE_INTENSITY
        singleCurveColor = Color.parseColor("#3FD1D0")

        setInternalCurve(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            startIntensity = startIntensity,
            peakStartIntensity = peakStartIntensity,
            peakEndIntensity = peakEndIntensity,
            endIntensity = endIntensity
        )
    }

    fun setChannelCurve(
        start: String,
        peakStart: String,
        peakEnd: String,
        end: String,
        startIntensity: Int,
        peakStartIntensity: Int,
        peakEndIntensity: Int,
        endIntensity: Int,
        curveColor: Int
    ) {
        renderStyle = CurveRenderStyle.CHANNEL
        singleCurveColor = curveColor

        setInternalCurve(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            startIntensity = startIntensity,
            peakStartIntensity = peakStartIntensity,
            peakEndIntensity = peakEndIntensity,
            endIntensity = endIntensity
        )
    }

    private fun setInternalCurve(
        start: String,
        peakStart: String,
        peakEnd: String,
        end: String,
        startIntensity: Int,
        peakStartIntensity: Int,
        peakEndIntensity: Int,
        endIntensity: Int
    ) {
        startTime = start
        peakStartTime = peakStart
        peakEndTime = peakEnd
        endTime = end

        this.startIntensity = startIntensity.coerceIn(0, 100)
        this.peakStartIntensity = peakStartIntensity.coerceIn(0, 100)
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

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute).coerceIn(0, MINUTES_IN_DAY - 1)
    }

    private fun adjustedMinutes(
        time: String,
        startMinutes: Int
    ): Int {
        var minutes = timeToMinutes(time)

        if (minutes < startMinutes) {
            minutes += MINUTES_IN_DAY
        }

        return minutes
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }

    private companion object {
        private const val MINUTES_IN_DAY = 24 * 60
        private const val MIN_VISIBLE_MINUTES = 60
    }
}