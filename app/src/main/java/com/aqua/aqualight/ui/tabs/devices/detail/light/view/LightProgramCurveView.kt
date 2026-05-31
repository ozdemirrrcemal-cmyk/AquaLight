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

    enum class CurveDisplayMode {
        SIMPLE,
        PRO_RED,
        PRO_GREEN,
        PRO_BLUE,
        PRO_WHITE
    }

    data class CurvePoint(
        val time: String,
        val intensity: Int
    )

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

    private var displayMode: CurveDisplayMode = CurveDisplayMode.SIMPLE

    private var curvePoints: List<CurvePoint> =
        listOf(
            CurvePoint(
                time = "08:00",
                intensity = 0
            ),
            CurvePoint(
                time = "12:00",
                intensity = 100
            ),
            CurvePoint(
                time = "16:00",
                intensity = 100
            ),
            CurvePoint(
                time = "20:00",
                intensity = 0
            )
        )

    private val simpleGradientColors =
        intArrayOf(
            Color.parseColor("#FF9F2D"),
            Color.parseColor("#C9F36B"),
            Color.parseColor("#28E6F0"),
            Color.parseColor("#28E6F0"),
            Color.parseColor("#8D7CFF"),
            Color.parseColor("#FF6D8C")
        )

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val sortedPoints =
            curvePoints
                .sortedBy {
                    timeToMinutes(
                        time = it.time
                    )
                }
                .ifEmpty {
                    return
                }

        val left = 46f.dp()
        val right = width - 8f.dp()
        val top = 9f.dp()
        val bottom = height - 34f.dp()

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

        val drawablePoints =
            buildDrawablePoints(
                points = sortedPoints,
                left = left,
                right = right,
                top = top,
                bottom = bottom
            )

        if (drawablePoints.isEmpty()) {
            return
        }

        val curvePath =
            buildSmoothPath(
                points = drawablePoints
            )

        drawCurveFill(
            canvas = canvas,
            curvePath = curvePath,
            drawablePoints = drawablePoints,
            y0 = y0,
            y100 = y100
        )

        drawCurveLine(
            canvas = canvas,
            curvePath = curvePath,
            left = left,
            right = right
        )

        drawablePoints.forEachIndexed { index, point ->
            drawPoint(
                canvas = canvas,
                point = point,
                color = pointColor(
                    index = index,
                    lastIndex = drawablePoints.lastIndex
                )
            )
        }

        drawBottomLabels(
            canvas = canvas,
            left = left,
            right = right,
            bottom = bottom,
            points = sortedPoints,
            drawablePoints = drawablePoints
        )
    }

    private fun buildDrawablePoints(
        points: List<CurvePoint>,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ): List<PointF> {
        val startMinutes =
            timeToMinutes(
                time = points.first().time
            )

        val endMinutes =
            timeToMinutes(
                time = points.last().time
            )

        val rangeMinutes =
            (endMinutes - startMinutes)
                .coerceAtLeast(1)

        val chartWidth = right - left

        return points.map { point ->
            val pointMinutes =
                timeToMinutes(
                    time = point.time
                )

            val xProgress =
                ((pointMinutes - startMinutes).toFloat() / rangeMinutes.toFloat())
                    .coerceIn(
                        minimumValue = 0f,
                        maximumValue = 1f
                    )

            PointF(
                left + chartWidth * xProgress,
                intensityToY(
                    intensity = point.intensity,
                    top = top,
                    bottom = bottom
                )
            )
        }
    }

    private fun buildSmoothPath(
        points: List<PointF>
    ): Path {
        return Path().apply {
            val firstPoint = points.first()

            moveTo(
                firstPoint.x,
                firstPoint.y
            )

            if (points.size == 1) {
                return@apply
            }

            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midX = (previous.x + current.x) / 2f

                cubicTo(
                    midX,
                    previous.y,
                    midX,
                    current.y,
                    current.x,
                    current.y
                )
            }
        }
    }

    private fun drawCurveFill(
        canvas: Canvas,
        curvePath: Path,
        drawablePoints: List<PointF>,
        y0: Float,
        y100: Float
    ) {
        if (drawablePoints.size < 2) {
            return
        }

        val firstPoint = drawablePoints.first()
        val lastPoint = drawablePoints.last()
        val accentColor = currentAccentColor()

        fillPaint.shader = LinearGradient(
            0f,
            y100,
            0f,
            y0,
            intArrayOf(
                Color.argb(
                    120,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                ),
                Color.argb(
                    14,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                )
            ),
            null,
            Shader.TileMode.CLAMP
        )

        val fillPath =
            Path(curvePath).apply {
                lineTo(
                    lastPoint.x,
                    y0
                )
                lineTo(
                    firstPoint.x,
                    y0
                )
                close()
            }

        canvas.drawPath(
            fillPath,
            fillPaint
        )
    }

    private fun drawCurveLine(
        canvas: Canvas,
        curvePath: Path,
        left: Float,
        right: Float
    ) {
        if (displayMode == CurveDisplayMode.SIMPLE) {
            val gradient =
                LinearGradient(
                    left,
                    0f,
                    right,
                    0f,
                    simpleGradientColors,
                    null,
                    Shader.TileMode.CLAMP
                )

            linePaint.shader = gradient
            glowPaint.shader = gradient
        } else {
            val accentColor = currentAccentColor()

            linePaint.shader = null
            glowPaint.shader = null

            linePaint.color = accentColor
            glowPaint.color = accentColor
        }

        canvas.drawPath(
            curvePath,
            glowPaint
        )

        canvas.drawPath(
            curvePath,
            linePaint
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
        points: List<CurvePoint>,
        drawablePoints: List<PointF>
    ) {
        val labelY = bottom + 23f.dp()
        val accentColor = currentAccentColor()

        sunrisePaint.textSize = 14f.sp()
        sunsetPaint.textSize = 14f.sp()

        sunrisePaint.color =
            if (displayMode == CurveDisplayMode.SIMPLE) {
                Color.parseColor("#FFA32B")
            } else {
                accentColor
            }

        sunsetPaint.color =
            if (displayMode == CurveDisplayMode.SIMPLE) {
                Color.parseColor("#E26FD7")
            } else {
                accentColor
            }

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
            points.first().time,
            left + 4f.dp(),
            labelY,
            timePaint
        )

        if (points.size <= 6) {
            for (index in 1 until points.lastIndex) {
                timePaint.textAlign = Paint.Align.CENTER

                canvas.drawText(
                    points[index].time,
                    drawablePoints[index].x,
                    labelY,
                    timePaint
                )
            }
        }

        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            points.last().time,
            right,
            labelY,
            timePaint
        )
    }

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        color: Int
    ) {
        pointPaint.color = color

        canvas.drawCircle(
            point.x,
            point.y,
            4.7f.dp(),
            pointPaint
        )
    }

    private fun pointColor(
        index: Int,
        lastIndex: Int
    ): Int {
        if (displayMode != CurveDisplayMode.SIMPLE) {
            return currentAccentColor()
        }

        if (lastIndex <= 0) {
            return simpleGradientColors.first()
        }

        val fraction =
            index.toFloat() / lastIndex.toFloat()

        return interpolateGradientColor(
            colors = simpleGradientColors,
            fraction = fraction
        )
    }

    private fun interpolateGradientColor(
        colors: IntArray,
        fraction: Float
    ): Int {
        val safeFraction =
            fraction.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        val scaled =
            safeFraction * (colors.size - 1)

        val startIndex =
            scaled.toInt()
                .coerceIn(
                    minimumValue = 0,
                    maximumValue = colors.lastIndex
                )

        val endIndex =
            (startIndex + 1)
                .coerceAtMost(colors.lastIndex)

        val localFraction =
            scaled - startIndex

        val startColor = colors[startIndex]
        val endColor = colors[endIndex]

        return Color.rgb(
            lerpColorChannel(
                start = Color.red(startColor),
                end = Color.red(endColor),
                fraction = localFraction
            ),
            lerpColorChannel(
                start = Color.green(startColor),
                end = Color.green(endColor),
                fraction = localFraction
            ),
            lerpColorChannel(
                start = Color.blue(startColor),
                end = Color.blue(endColor),
                fraction = localFraction
            )
        )
    }

    private fun lerpColorChannel(
        start: Int,
        end: Int,
        fraction: Float
    ): Int {
        return (start + ((end - start) * fraction))
            .toInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = 255
            )
    }

    fun setCurveDisplayMode(
        mode: CurveDisplayMode
    ) {
        displayMode = mode
        invalidate()
    }

    fun setCurvePoints(
        points: List<CurvePoint>
    ) {
        curvePoints =
            points
                .map { point ->
                    point.copy(
                        intensity = point.intensity.coerceIn(
                            minimumValue = 0,
                            maximumValue = 100
                        )
                    )
                }
                .sortedBy {
                    timeToMinutes(
                        time = it.time
                    )
                }

        invalidate()
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
        setCurvePoints(
            points =
                listOf(
                    CurvePoint(
                        time = start,
                        intensity = startIntensity
                    ),
                    CurvePoint(
                        time = sunriseEnd,
                        intensity = sunriseEndIntensity
                    ),
                    CurvePoint(
                        time = peakEnd,
                        intensity = peakEndIntensity
                    ),
                    CurvePoint(
                        time = end,
                        intensity = endIntensity
                    )
                )
        )
    }

    private fun currentAccentColor(): Int {
        return when (displayMode) {
            CurveDisplayMode.SIMPLE -> {
                Color.parseColor("#28E6F0")
            }

            CurveDisplayMode.PRO_RED -> {
                Color.parseColor("#F04D4D")
            }

            CurveDisplayMode.PRO_GREEN -> {
                Color.parseColor("#6CC56C")
            }

            CurveDisplayMode.PRO_BLUE -> {
                Color.parseColor("#48A9F8")
            }

            CurveDisplayMode.PRO_WHITE -> {
                Color.parseColor("#E0E6ED")
            }
        }
    }

    private fun intensityToY(
        intensity: Int,
        top: Float,
        bottom: Float
    ): Float {
        val safeIntensity =
            intensity.coerceIn(
                minimumValue = 0,
                maximumValue = 100
            )

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

        return (hour * 60 + minute)
            .coerceIn(
                minimumValue = 0,
                maximumValue = 24 * 60 - 1
            )
    }

    private fun Float.dp(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Float.sp(): Float {
        return this * resources.displayMetrics.scaledDensity
    }
}