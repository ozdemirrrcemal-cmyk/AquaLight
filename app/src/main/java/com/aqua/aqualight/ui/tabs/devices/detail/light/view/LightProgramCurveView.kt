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
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

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

    enum class CurveSmoothingMode {
        LINEAR,
        SOFT,
        NATURAL
    }

    data class CurvePoint(
        val time: String,
        val intensity: Int,
        val isMajor: Boolean = true
    )

    private data class DrawableCurvePoint(
        val point: CurvePoint,
        val screenPoint: PointF,
        val isSynthetic: Boolean
    )

    private var smoothingMode: CurveSmoothingMode = CurveSmoothingMode.LINEAR
    private var displayMode: CurveDisplayMode = CurveDisplayMode.SIMPLE

    private var currentTimeMinutes: Int? = null
    private var currentOutputLabel: String? = null

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

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AEB9C6")
        textSize = 10f.sp()
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E6ED")
        textSize = 10f.sp()
    }

    private val horizontalGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 1f.dp()
        alpha = 90
        pathEffect = DashPathEffect(
            floatArrayOf(
                3f.dp(),
                7f.dp()
            ),
            0f
        )
    }

    private val verticalGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52708F")
        strokeWidth = 0.7f.dp()
        alpha = 55
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

    private val currentTimeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CURRENT_TIME_COLOR
        strokeWidth = 1.2f.dp()
        alpha = 210
    }

    private val currentTimeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2DD6D6")
        alpha = 205
        style = Paint.Style.FILL
    }

    private val currentTimeBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#092238")
        textSize = 9.5f.sp()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val currentOutputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6E4EF")
        textSize = 9f.sp()
        textAlign = Paint.Align.CENTER
        alpha = 210
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val activePoints =
            activeCurvePoints()
                .sortedBy {
                    timeToMinutes(
                        time = it.time
                    )
                }

        if (activePoints.isEmpty()) {
            return
        }

        val left = 38f.dp()
        val right = width - 4f.dp()
        val top = 24f.dp()
        val bottom = height - 34f.dp()

        if (right <= left || bottom <= top) {
            return
        }

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
                drawPoints = true
            )

            drawCurrentTimeIndicator(
                canvas = canvas,
                points = activePoints,
                left = left,
                right = right,
                top = top,
                bottom = bottom
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
            .filter { mode ->
                mode != displayMode
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
                        drawPoints = false
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
            drawPoints = true
        )

        drawCurrentTimeIndicator(
            canvas = canvas,
            points = activePoints,
            left = left,
            right = right,
            top = top,
            bottom = bottom
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
        drawVerticalGrid(
            canvas = canvas,
            left = left,
            right = right,
            y0 = y0,
            y100 = y100
        )

        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.textSize = 10f.sp()
        labelPaint.color = Color.parseColor("#AEB9C6")

        canvas.drawText(
            "100%",
            left - 7f.dp(),
            y100 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "75%",
            left - 7f.dp(),
            y75 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "50%",
            left - 7f.dp(),
            y50 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "25%",
            left - 7f.dp(),
            y25 + 4f.dp(),
            labelPaint
        )

        canvas.drawText(
            "0%",
            left - 7f.dp(),
            y0 + 4f.dp(),
            labelPaint
        )

        canvas.drawLine(
            left,
            y100,
            right,
            y100,
            horizontalGridPaint
        )

        canvas.drawLine(
            left,
            y75,
            right,
            y75,
            horizontalGridPaint
        )

        canvas.drawLine(
            left,
            y50,
            right,
            y50,
            horizontalGridPaint
        )

        canvas.drawLine(
            left,
            y25,
            right,
            y25,
            horizontalGridPaint
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

        drawTimeAxisLabels(
            canvas = canvas,
            left = left,
            right = right,
            y = y0 + 23f.dp()
        )
    }

    private fun drawVerticalGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        y0: Float,
        y100: Float
    ) {
        for (hour in 0..24 step 2) {
            val x =
                minuteToX(
                    minute = hour * 60,
                    left = left,
                    right = right
                )

            canvas.drawLine(
                x,
                y100,
                x,
                y0,
                verticalGridPaint
            )
        }
    }

    private fun drawTimeAxisLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float
    ) {
        timePaint.textSize = 9.5f.sp()
        timePaint.color = Color.parseColor("#D6E4EF")

        for (hour in 0..24 step 4) {
            val x =
                minuteToX(
                    minute = hour * 60,
                    left = left,
                    right = right
                )

            timePaint.textAlign =
                when (hour) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }

            canvas.drawText(
                timeAxisLabel(
                    hour = hour
                ),
                x,
                y,
                timePaint
            )
        }
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
        drawPoints: Boolean
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
                points =
                    drawablePoints.map { item ->
                        item.screenPoint
                    }
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
            mode = mode,
            isActive = isActive
        )

        if (drawPoints) {
            drawablePoints
                .filter { item ->
                    !item.isSynthetic
                }
                .forEach { item ->
                    drawPoint(
                        canvas = canvas,
                        point = item.screenPoint,
                        color = pointColor(
                            mode = mode
                        ),
                        isMajor = item.point.isMajor
                    )
                }
        }
    }

    private fun buildDrawablePoints(
        points: List<CurvePoint>,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ): List<DrawableCurvePoint> {
        val normalized =
            normalizePoints(
                points = points
            )

        if (normalized.isEmpty()) {
            return emptyList()
        }

        val renderItems = mutableListOf<Pair<CurvePoint, Boolean>>()

        val firstMinute =
            timeToMinutes(
                time = normalized.first().time
            )

        val lastMinute =
            timeToMinutes(
                time = normalized.last().time
            )

        if (firstMinute > 0) {
            renderItems.add(
                CurvePoint(
                    time = "00:00",
                    intensity = 0,
                    isMajor = false
                ) to true
            )
        }

        normalized.forEach { point ->
            renderItems.add(
                point to false
            )
        }

        if (lastMinute < MINUTES_IN_DAY) {
            renderItems.add(
                CurvePoint(
                    time = "24:00",
                    intensity = 0,
                    isMajor = false
                ) to true
            )
        }

        return renderItems.map { item ->
            val point = item.first
            val isSynthetic = item.second

            val pointMinutes =
                timeToMinutes(
                    time = point.time
                )

            DrawableCurvePoint(
                point = point,
                screenPoint =
                    PointF(
                        minuteToX(
                            minute = pointMinutes,
                            left = left,
                            right = right
                        ),
                        intensityToY(
                            intensity = point.intensity,
                            top = top,
                            bottom = bottom
                        )
                    ),
                isSynthetic = isSynthetic
            )
        }
    }

    private fun buildSmoothPath(
        points: List<PointF>
    ): Path {
        return when (smoothingMode) {
            CurveSmoothingMode.LINEAR -> {
                buildLinearPath(
                    points = points
                )
            }

            CurveSmoothingMode.SOFT -> {
                buildSoftPath(
                    points = points,
                    strength = 0.50f
                )
            }

            CurveSmoothingMode.NATURAL -> {
                buildSoftPath(
                    points = points,
                    strength = 0.72f
                )
            }
        }
    }

    private fun buildLinearPath(
        points: List<PointF>
    ): Path {
        return Path().apply {
            val firstPoint = points.first()

            moveTo(
                firstPoint.x,
                firstPoint.y
            )

            for (index in 1 until points.size) {
                val point = points[index]

                lineTo(
                    point.x,
                    point.y
                )
            }
        }
    }

    private fun buildSoftPath(
        points: List<PointF>,
        strength: Float
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

                val distanceX = current.x - previous.x
                val controlOffset = distanceX * strength * 0.5f

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
        drawablePoints: List<DrawableCurvePoint>,
        y0: Float,
        y100: Float,
        mode: CurveDisplayMode
    ) {
        if (drawablePoints.size < 2) {
            return
        }

        val firstPoint = drawablePoints.first().screenPoint
        val lastPoint = drawablePoints.last().screenPoint
        val accentColor =
            currentAccentColor(
                mode = mode
            )

        val topAlpha =
            if (mode == CurveDisplayMode.SIMPLE) {
                SIMPLE_FILL_TOP_ALPHA
            } else {
                PRO_FILL_TOP_ALPHA
            }

        fillPaint.shader =
            LinearGradient(
                0f,
                y100,
                0f,
                y0,
                intArrayOf(
                    Color.argb(
                        topAlpha,
                        Color.red(accentColor),
                        Color.green(accentColor),
                        Color.blue(accentColor)
                    ),
                    Color.argb(
                        FILL_BOTTOM_ALPHA,
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

        val accentColor =
            currentAccentColor(
                mode = mode
            )

        linePaint.shader = null
        glowPaint.shader = null

        linePaint.color = accentColor
        glowPaint.color = accentColor

        if (isActive) {
            glowPaint.alpha =
                if (mode == CurveDisplayMode.SIMPLE) {
                    SIMPLE_GLOW_ALPHA
                } else {
                    ACTIVE_GLOW_ALPHA
                }

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

    private fun drawPoint(
        canvas: Canvas,
        point: PointF,
        color: Int,
        isMajor: Boolean
    ) {
        pointPaint.color = color
        pointPaint.alpha = ACTIVE_LINE_ALPHA

        canvas.drawCircle(
            point.x,
            point.y,
            if (isMajor) {
                4.8f.dp()
            } else {
                3.7f.dp()
            },
            pointPaint
        )
    }

    private fun drawCurrentTimeIndicator(
        canvas: Canvas,
        points: List<CurvePoint>,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val nowMinutes =
            currentTimeMinutes ?: return

        val x =
            minuteToX(
                minute = nowMinutes,
                left = left,
                right = right
            )

        canvas.drawLine(
            x,
            top,
            x,
            bottom,
            currentTimeLinePaint
        )

        drawCurrentTimeBadge(
            canvas = canvas,
            x = x,
            chartLeft = left,
            chartRight = right,
            chartTop = top,
            text = minutesToTime(
                minutes = nowMinutes
            )
        )

        val outputText =
            currentOutputLabel
                ?: "${intensityAtMinute(points = points, minute = nowMinutes).roundToInt()}%"

        if ((right - left) >= 230f.dp()) {
            drawCurrentOutputLabel(
                canvas = canvas,
                x = x,
                chartLeft = left,
                chartRight = right,
                chartTop = top,
                text = outputText
            )
        }
    }

    private fun drawCurrentTimeBadge(
        canvas: Canvas,
        x: Float,
        chartLeft: Float,
        chartRight: Float,
        chartTop: Float,
        text: String
    ) {
        currentTimeBadgeTextPaint.textSize = 9.5f.sp()

        val horizontalPadding = 8f.dp()
        val badgeHeight = 18f.dp()
        val badgeWidth =
            currentTimeBadgeTextPaint.measureText(text) + (horizontalPadding * 2f)

        val badgeTop = chartTop - badgeHeight - 4f.dp()
        val badgeBottom = chartTop - 4f.dp()

        val badgeLeft =
            (x - (badgeWidth / 2f)).coerceIn(
                chartLeft,
                chartRight - badgeWidth
            )

        val badgeRight = badgeLeft + badgeWidth

        val rect =
            RectF(
                badgeLeft,
                badgeTop,
                badgeRight,
                badgeBottom
            )

        canvas.drawRoundRect(
            rect,
            5f.dp(),
            5f.dp(),
            currentTimeBadgePaint
        )

        val textBaseline =
            rect.centerY() -
                ((currentTimeBadgeTextPaint.descent() + currentTimeBadgeTextPaint.ascent()) / 2f)

        canvas.drawText(
            text,
            rect.centerX(),
            textBaseline,
            currentTimeBadgeTextPaint
        )
    }

    private fun drawCurrentOutputLabel(
        canvas: Canvas,
        x: Float,
        chartLeft: Float,
        chartRight: Float,
        chartTop: Float,
        text: String
    ) {
        currentOutputPaint.textSize = 9f.sp()

        val textWidth =
            currentOutputPaint.measureText(
                text
            )

        val safeX =
            x.coerceIn(
                chartLeft + (textWidth / 2f),
                chartRight - (textWidth / 2f)
            )

        canvas.drawText(
            text,
            safeX,
            chartTop + 17f.dp(),
            currentOutputPaint
        )
    }

    fun setCurrentTimeMinutes(
        minutes: Int,
        outputLabel: String? = null
    ) {
        currentTimeMinutes =
            minutes.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )

        currentOutputLabel = outputLabel

        invalidate()
    }

    fun setCurrentTime(
        time: String,
        outputLabel: String? = null
    ) {
        setCurrentTimeMinutes(
            minutes = timeToMinutes(
                time = time
            ).coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            ),
            outputLabel = outputLabel
        )
    }

    fun clearCurrentTimeIndicator() {
        currentTimeMinutes = null
        currentOutputLabel = null
        invalidate()
    }

    fun setCurveSmoothingMode(
        mode: CurveSmoothingMode
    ) {
        smoothingMode = mode
        invalidate()
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
                SIMPLE_LINE_COLOR
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

    private fun pointColor(
        mode: CurveDisplayMode
    ): Int {
        return currentAccentColor(
            mode = mode
        )
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

    private fun intensityAtMinute(
        points: List<CurvePoint>,
        minute: Int
    ): Float {
        val normalized =
            normalizePoints(
                points = points
            )

        if (normalized.isEmpty()) {
            return 0f
        }

        val renderPoints = mutableListOf<CurvePoint>()

        val firstMinute =
            timeToMinutes(
                time = normalized.first().time
            )

        val lastMinute =
            timeToMinutes(
                time = normalized.last().time
            )

        if (firstMinute > 0) {
            renderPoints.add(
                CurvePoint(
                    time = "00:00",
                    intensity = 0
                )
            )
        }

        renderPoints.addAll(
            normalized
        )

        if (lastMinute < MINUTES_IN_DAY) {
            renderPoints.add(
                CurvePoint(
                    time = "24:00",
                    intensity = 0
                )
            )
        }

        val safeMinute =
            minute.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY
            )

        val firstPoint = renderPoints.first()
        val lastPoint = renderPoints.last()

        if (safeMinute <= timeToMinutes(firstPoint.time)) {
            return firstPoint.intensity.toFloat()
        }

        if (safeMinute >= timeToMinutes(lastPoint.time)) {
            return lastPoint.intensity.toFloat()
        }

        renderPoints
            .zipWithNext()
            .forEach { pair ->
                val start = pair.first
                val end = pair.second

                val startMinute =
                    timeToMinutes(
                        time = start.time
                    )

                val endMinute =
                    timeToMinutes(
                        time = end.time
                    )

                if (safeMinute in startMinute..endMinute) {
                    val range =
                        (endMinute - startMinute)
                            .coerceAtLeast(1)

                    val progress =
                        (safeMinute - startMinute).toFloat() / range.toFloat()

                    return start.intensity +
                        ((end.intensity - start.intensity) * progress)
                }
            }

        return 0f
    }

    private fun minuteToX(
        minute: Int,
        left: Float,
        right: Float
    ): Float {
        val safeMinute =
            minute.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY
            )

        val progress =
            safeMinute.toFloat() / MINUTES_IN_DAY.toFloat()

        return left + ((right - left) * progress)
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

        if (hour == 24 && minute == 0) {
            return MINUTES_IN_DAY
        }

        return (hour * 60 + minute)
            .coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            minutes.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )

        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private fun timeAxisLabel(
        hour: Int
    ): String {
        return if (hour == 24) {
            "00:00"
        } else {
            "%02d:00".format(
                hour
            )
        }
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
        private const val SIMPLE_GLOW_ALPHA = 45
        private const val PASSIVE_LINE_ALPHA = 78

        private const val SIMPLE_FILL_TOP_ALPHA = 48
        private const val PRO_FILL_TOP_ALPHA = 110
        private const val FILL_BOTTOM_ALPHA = 14

        private const val MINUTES_IN_DAY = 24 * 60

        private val CURRENT_TIME_COLOR = Color.parseColor("#37D7D7")

        private val SIMPLE_LINE_COLOR = Color.parseColor("#E0E6ED")

        private val RED_COLOR = Color.parseColor("#F04D4D")
        private val GREEN_COLOR = Color.parseColor("#6CC56C")
        private val BLUE_COLOR = Color.parseColor("#48A9F8")
        private val WHITE_COLOR = Color.parseColor("#E0E6ED")
    }
}