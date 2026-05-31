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
        pathEffect =
            DashPathEffect(
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
        maskFilter =
            BlurMaskFilter(
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

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val sortedPoints =
            curvePoints
                .map { point ->
                    point.copy(
                        intensity = point.intensity.coerceIn(
                            minimumValue = 0,
                            maximumValue = MAX_PERCENT
                        )
                    )
                }
                .sortedBy { point ->
                    timeToMinutes(
                        time = point.time
                    )
                }

        if (sortedPoints.isEmpty()) {
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
            curvePath = curvePath
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
            adjustedMinutes(
                time = points.last().time,
                startMinutes = startMinutes
            )

        val rangeMinutes =
            max(
                MIN_VISIBLE_MINUTES,
                endMinutes - startMinutes
            ).toFloat()

        val chartWidth = right - left

        return points.map { point ->
            val pointMinutes =
                adjustedMinutes(
                    time = point.time,
                    startMinutes = startMinutes
                )

            val xProgress =
                ((pointMinutes - startMinutes) / rangeMinutes)
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
                val distance = current.x - previous.x
                val controlOffset = distance * 0.45f

                cubicTo(
                    previous.x + controlOffset,
                    previous.y,
                    current.x - controlOffset,
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

        fillPaint.shader =
            LinearGradient(
                0f,
                y100,
                0f,
                y0,
                intArrayOf(
                    Color.argb(
                        100,
                        Color.red(accentColor),
                        Color.green(accentColor),
                        Color.blue(accentColor)
                    ),
                    Color.argb(
                        12,
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
        curvePath: Path
    ) {
        val accentColor = currentAccentColor()

        linePaint.shader = null
        glowPaint.shader = null

        linePaint.color = accentColor
        glowPaint.color = accentColor

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
        if (points.isEmpty() || drawablePoints.isEmpty()) {
            return
        }

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

        points.forEachIndexed { index, point ->
            val drawablePoint = drawablePoints[index]

            if (index == 0) {
                timePaint.textAlign = Paint.Align.LEFT

                canvas.drawText(
                    point.time,
                    drawablePoint.x + 4f.dp(),
                    labelY,
                    timePaint
                )
            } else if (index == points.lastIndex) {
                timePaint.textAlign = Paint.Align.RIGHT

                canvas.drawText(
                    point.time,
                    drawablePoint.x,
                    labelY,
                    timePaint
                )
            } else if (points.size <= MAX_VISIBLE_TIME_LABELS) {
                timePaint.textAlign = Paint.Align.CENTER

                canvas.drawText(
                    point.time,
                    drawablePoint.x,
                    labelY,
                    timePaint
                )
            }
        }
    }

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        color: Int
    ) {
        pointPaint.shader = null
        pointPaint.style = Paint.Style.FILL
        pointPaint.color = color
        pointPaint.alpha = 235

        canvas.drawCircle(
            point.x,
            point.y,
            4.7f.dp(),
            pointPaint
        )

        pointPaint.alpha = 255
    }

    private fun pointColor(
        index: Int,
        lastIndex: Int
    ): Int {
        return when (displayMode) {
            CurveDisplayMode.SIMPLE -> {
                when (index) {
                    0 -> Color.parseColor("#FFA32B")
                    lastIndex -> Color.parseColor("#FF8A65")
                    else -> currentAccentColor()
                }
            }

            CurveDisplayMode.PRO_RED,
            CurveDisplayMode.PRO_GREEN,
            CurveDisplayMode.PRO_BLUE,
            CurveDisplayMode.PRO_WHITE -> {
                currentAccentColor()
            }
        }
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
                    CurvePoint(
                        time = point.time,
                        intensity = point.intensity.coerceIn(
                            minimumValue = 0,
                            maximumValue = MAX_PERCENT
                        )
                    )
                }
                .sortedBy { point ->
                    timeToMinutes(
                        time = point.time
                    )
                }

        invalidate()
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
                maximumValue = MAX_PERCENT
            )

        return bottom - ((bottom - top) * safeIntensity / MAX_PERCENT.toFloat())
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
                maximumValue = MINUTES_IN_DAY - 1
            )
    }

    private fun adjustedMinutes(
        time: String,
        startMinutes: Int
    ): Int {
        var minutes =
            timeToMinutes(
                time = time
            )

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
        private const val MAX_PERCENT = 100
        private const val MINUTES_IN_DAY = 24 * 60
        private const val MIN_VISIBLE_MINUTES = 60
        private const val MAX_VISIBLE_TIME_LABELS = 6
    }
}