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
        val intensity: Int,
        val isMajor: Boolean = true
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
        alpha = ACTIVE_GLOW_ALPHA
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f.dp()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sunrisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SUNRISE_COLOR
        textSize = 14f.sp()
        textAlign = Paint.Align.CENTER
    }

    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SUNSET_COLOR
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

    private var proChannelCurves: Map<CurveDisplayMode, List<CurvePoint>> =
        emptyMap()

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val activePoints = activeCurvePoints()
            .sortedBy {
                timeToMinutes(
                    time = it.time
                )
            }

        if (activePoints.isEmpty()) {
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

        if (displayMode == CurveDisplayMode.SIMPLE) {
            drawCurve(
                canvas = canvas,
                points = activePoints,
                mode = CurveDisplayMode.SIMPLE,
                left = left,
                right = right,
                top = top,
                bottom = bottom,
                y0 = y0,
                y100 = y100,
                isActive = true,
                drawFill = true,
                drawPoints = true,
                drawLabels = true
            )

            return
        }

        val proModes =
            listOf(
                CurveDisplayMode.PRO_RED,
                CurveDisplayMode.PRO_GREEN,
                CurveDisplayMode.PRO_BLUE,
                CurveDisplayMode.PRO_WHITE
            )

        proModes
            .filter {
                it != displayMode
            }
            .forEach { mode ->
                val points =
                    proChannelCurves[mode]
                        ?.sortedBy {
                            timeToMinutes(
                                time = it.time
                            )
                        }
                        .orEmpty()

                if (points.isNotEmpty()) {
                    drawCurve(
                        canvas = canvas,
                        points = points,
                        mode = mode,
                        left = left,
                        right = right,
                        top = top,
                        bottom = bottom,
                        y0 = y0,
                        y100 = y100,
                        isActive = false,
                        drawFill = false,
                        drawPoints = false,
                        drawLabels = false
                    )
                }
            }

        drawCurve(
            canvas = canvas,
            points = activePoints,
            mode = displayMode,
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            y0 = y0,
            y100 = y100,
            isActive = true,
            drawFill = true,
            drawPoints = true,
            drawLabels = true
        )
    }

    private fun drawCurve(
        canvas: Canvas,
        points: List<CurvePoint>,
        mode: CurveDisplayMode,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        y0: Float,
        y100: Float,
        isActive: Boolean,
        drawFill: Boolean,
        drawPoints: Boolean,
        drawLabels: Boolean
    ) {
        val drawablePoints =
            buildDrawablePoints(
                points = points,
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

        if (drawFill) {
            drawCurveFill(
                canvas = canvas,
                curvePath = curvePath,
                drawablePoints = drawablePoints,
                y0 = y0,
                y100 = y100,
                mode = mode
            )
        }

        drawCurveLine(
            canvas = canvas,
            curvePath = curvePath,
            left = left,
            right = right,
            mode = mode,
            isActive = isActive
        )

        if (drawPoints) {
            drawablePoints.forEachIndexed { index, point ->
                drawPoint(
                    canvas = canvas,
                    point = point,
                    color = pointColor(
                        mode = mode,
                        index = index,
                        lastIndex = drawablePoints.lastIndex
                    )
                )
            }
        }

        if (drawLabels) {
            drawBottomLabels(
                canvas = canvas,
                left = left,
                right = right,
                bottom = bottom,
                points = points,
                drawablePoints = drawablePoints
            )
        }
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
        y100: Float,
        mode: CurveDisplayMode
    ) {
        if (drawablePoints.size < 2) {
            return
        }

        val firstPoint = drawablePoints.first()
        val lastPoint = drawablePoints.last()
        val accentColor = currentAccentColor(mode)

        fillPaint.shader =
            LinearGradient(
                0f,
                y100,
                0f,
                y0,
                intArrayOf(
                    Color.argb(
                        110,
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
        right: Float,
        mode: CurveDisplayMode,
        isActive: Boolean
    ) {
        linePaint.strokeWidth =
            if (isActive) {
                2.6f.dp()
            } else {
                1.35f.dp()
            }

        linePaint.alpha =
            if (isActive) {
                ACTIVE_LINE_ALPHA
            } else {
                PASSIVE_LINE_ALPHA
            }

        if (mode == CurveDisplayMode.SIMPLE) {
            val gradient =
                LinearGradient(
                    left,
                    0f,
                    right,
                    0f,
                    SIMPLE_GRADIENT_COLORS,
                    null,
                    Shader.TileMode.CLAMP
                )

            linePaint.shader = gradient
            glowPaint.shader = gradient
        } else {
            val accentColor = currentAccentColor(mode)

            linePaint.shader = null
            glowPaint.shader = null

            linePaint.color = accentColor
            glowPaint.color = accentColor
        }

        if (isActive) {
            glowPaint.alpha = ACTIVE_GLOW_ALPHA

            canvas.drawPath(
                curvePath,
                glowPaint
            )
        }

        canvas.drawPath(
            curvePath,
            linePaint
        )

        linePaint.alpha = ACTIVE_LINE_ALPHA
        linePaint.strokeWidth = 2.4f.dp()
        glowPaint.alpha = ACTIVE_GLOW_ALPHA
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

        sunrisePaint.textSize = 14f.sp()
        sunsetPaint.textSize = 14f.sp()

        sunrisePaint.color = SUNRISE_COLOR
        sunsetPaint.color = SUNSET_COLOR

        canvas.drawText(
            "☀",
            left - 19f.dp(),
            labelY,
            sunrisePaint
        )

        canvas.drawText(
            "☾",
            right - 7f.dp(),
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

        val labelIndexes =
            if (points.size <= DEFAULT_MAJOR_POINT_COUNT) {
                points.indices.toList()
            } else {
                points.mapIndexedNotNull { index, point ->
                    when {
                        index == 0 -> index
                        index == points.lastIndex -> index
                        point.isMajor -> index
                        else -> null
                    }
                }
            }

        var lastDrawnX = left + 46f.dp()
        val endGuardX = right - 64f.dp()
        val minDistance = 46f.dp()

        labelIndexes
            .filter {
                it != 0 && it != points.lastIndex
            }
            .forEach { index ->
                val x = drawablePoints[index].x

                val canDraw =
                    x - lastDrawnX >= minDistance &&
                        x <= endGuardX

                if (canDraw) {
                    timePaint.textAlign = Paint.Align.CENTER

                    canvas.drawText(
                        points[index].time,
                        x,
                        labelY,
                        timePaint
                    )

                    lastDrawnX = x
                }
            }

        timePaint.textAlign = Paint.Align.RIGHT

        canvas.drawText(
            points.last().time,
            right - 28f.dp(),
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
        pointPaint.alpha = ACTIVE_LINE_ALPHA

        canvas.drawCircle(
            point.x,
            point.y,
            4.8f.dp(),
            pointPaint
        )
    }

    private fun pointColor(
        mode: CurveDisplayMode,
        index: Int,
        lastIndex: Int
    ): Int {
        if (mode != CurveDisplayMode.SIMPLE) {
            return currentAccentColor(mode)
        }

        if (lastIndex <= 0) {
            return SIMPLE_GRADIENT_COLORS.first()
        }

        val fraction =
            index.toFloat() / lastIndex.toFloat()

        return interpolateGradientColor(
            colors = SIMPLE_GRADIENT_COLORS,
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
        proChannelCurves = emptyMap()

        curvePoints =
            normalizePoints(
                points = points
            )

        invalidate()
    }

    fun setProChannelCurves(
        activeMode: CurveDisplayMode,
        redPoints: List<CurvePoint>,
        greenPoints: List<CurvePoint>,
        bluePoints: List<CurvePoint>,
        whitePoints: List<CurvePoint>
    ) {
        displayMode = activeMode

        proChannelCurves =
            mapOf(
                CurveDisplayMode.PRO_RED to normalizePoints(
                    points = redPoints
                ),
                CurveDisplayMode.PRO_GREEN to normalizePoints(
                    points = greenPoints
                ),
                CurveDisplayMode.PRO_BLUE to normalizePoints(
                    points = bluePoints
                ),
                CurveDisplayMode.PRO_WHITE to normalizePoints(
                    points = whitePoints
                )
            )

        curvePoints =
            proChannelCurves[activeMode].orEmpty()

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
        setCurveDisplayMode(
            mode = CurveDisplayMode.SIMPLE
        )

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

    private fun normalizePoints(
        points: List<CurvePoint>
    ): List<CurvePoint> {
        return points
            .map { point ->
                point.copy(
                    intensity =
                        point.intensity.coerceIn(
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
    }

    private fun activeCurvePoints(): List<CurvePoint> {
        return if (displayMode == CurveDisplayMode.SIMPLE) {
            curvePoints
        } else {
            proChannelCurves[displayMode] ?: curvePoints
        }
    }

    private fun currentAccentColor(
        mode: CurveDisplayMode
    ): Int {
        return when (mode) {
            CurveDisplayMode.SIMPLE -> {
                SIMPLE_ACCENT_COLOR
            }

            CurveDisplayMode.PRO_RED -> {
                RED_COLOR
            }

            CurveDisplayMode.PRO_GREEN -> {
                GREEN_COLOR
            }

            CurveDisplayMode.PRO_BLUE -> {
                BLUE_COLOR
            }

            CurveDisplayMode.PRO_WHITE -> {
                WHITE_COLOR
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

    private companion object {
        private const val ACTIVE_LINE_ALPHA = 255
        private const val ACTIVE_GLOW_ALPHA = 95
        private const val PASSIVE_LINE_ALPHA = 78
        private const val DEFAULT_MAJOR_POINT_COUNT = 4

        private val SUNRISE_COLOR = Color.parseColor("#FFA32B")
        private val SUNSET_COLOR = Color.parseColor("#E26FD7")

        private val SIMPLE_ACCENT_COLOR = Color.parseColor("#28E6F0")
        private val RED_COLOR = Color.parseColor("#F04D4D")
        private val GREEN_COLOR = Color.parseColor("#6CC56C")
        private val BLUE_COLOR = Color.parseColor("#48A9F8")
        private val WHITE_COLOR = Color.parseColor("#E0E6ED")

        private val SIMPLE_GRADIENT_COLORS =
            intArrayOf(
                Color.parseColor("#FF9F2D"),
                Color.parseColor("#C9F36B"),
                Color.parseColor("#28E6F0"),
                Color.parseColor("#28E6F0"),
                Color.parseColor("#8D7CFF"),
                Color.parseColor("#FF6D8C")
            )
    }
}