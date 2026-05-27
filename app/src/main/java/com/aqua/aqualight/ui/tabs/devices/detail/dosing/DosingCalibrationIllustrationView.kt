package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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

    private var animationProgress: Float = 0f

    private val animator: ValueAnimator =
        ValueAnimator.ofFloat(
            0f,
            1f
        ).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()

            addUpdateListener { valueAnimator ->
                animationProgress =
                    valueAnimator.animatedValue as Float

                invalidate()
            }
        }

    private val bodyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111A35")
    }

    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#46536D")
    }

    private val softStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#2F3A55")
    }

    private val tubeBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#2B344D")
    }

    private val tubeLiquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#38BDF8")
    }

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
    }

    private val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F43F5E")
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
        alpha = 215
    }

    private val liquidSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
        alpha = 70
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
        alpha = 38
    }

    private val tagFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#17253C")
    }

    private val tagStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#46536D")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E1EF")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!isInEditMode && !animator.isStarted) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()

        super.onDetachedFromWindow()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        val safeWidth = width.toFloat()
        val safeHeight = height.toFloat()

        if (
            safeWidth <= 0f ||
            safeHeight <= 0f
        ) {
            return
        }

        val size =
            min(
                safeWidth,
                safeHeight
            )

        val centerX =
            safeWidth / 2f

        val centerY =
            safeHeight / 2f

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

            3 -> drawMeasureStep(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                size = size
            )

            4 -> drawTestDoseStep(
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
        val scale =
            size / 180f

        drawReservoirBottle(
            canvas = canvas,
            centerX = centerX - size * 0.18f,
            bottomY = centerY + size * 0.20f,
            scale = scale,
            liquidPercent = animatedLiquidPercent(
                base = 0.52f,
                range = 0.08f
            )
        )

        drawLiquidDrop(
            canvas = canvas,
            centerX = centerX + size * 0.03f,
            centerY = centerY + size * 0.12f + bob(
                amount = dp(3f)
            ),
            scale = scale,
            colorPaint = rosePaint
        )

        drawTag(
            canvas = canvas,
            centerX = centerX + size * 0.22f,
            centerY = centerY - size * 0.06f,
            width = size * 0.34f,
            height = size * 0.18f,
            label = "NPK"
        )
    }

    private fun drawPrimeStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val scale =
            size / 180f

        val pumpCenterX =
            centerX

        val pumpTopY =
            centerY - size * 0.30f

        drawPumpHead(
            canvas = canvas,
            centerX = pumpCenterX,
            topY = pumpTopY,
            scale = scale,
            active = true
        )

        drawReservoirBottle(
            canvas = canvas,
            centerX = centerX - size * 0.29f,
            bottomY = centerY + size * 0.30f,
            scale = scale,
            liquidPercent = 0.62f
        )

        val tubeToPump = Path().apply {
            moveTo(
                centerX - size * 0.29f,
                centerY + size * 0.02f
            )
            cubicTo(
                centerX - size * 0.26f,
                centerY - size * 0.06f,
                centerX - size * 0.16f,
                centerY - size * 0.16f,
                centerX - size * 0.11f,
                centerY - size * 0.08f
            )
        }

        drawTubePath(
            canvas = canvas,
            path = tubeToPump,
            animated = true
        )

        val outletTube = Path().apply {
            moveTo(
                centerX + size * 0.16f,
                centerY - size * 0.03f
            )
            cubicTo(
                centerX + size * 0.18f,
                centerY + size * 0.05f,
                centerX + size * 0.18f,
                centerY + size * 0.13f,
                centerX + size * 0.18f,
                centerY + size * 0.22f
            )
        }

        drawTubePath(
            canvas = canvas,
            path = outletTube,
            animated = true
        )

        drawLiquidDrop(
            canvas = canvas,
            centerX = centerX + size * 0.18f,
            centerY = centerY + size * 0.16f + dripMotion(
                distance = size * 0.10f
            ),
            scale = scale * 0.95f,
            colorPaint = rosePaint
        )

        drawArrowDown(
            canvas = canvas,
            x = centerX + size * 0.31f,
            y = centerY + size * 0.04f,
            height = size * 0.22f
        )
    }

    private fun drawStartCalibrationStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val scale =
            size / 180f

        drawPumpHead(
            canvas = canvas,
            centerX = centerX - size * 0.03f,
            topY = centerY - size * 0.31f,
            scale = scale,
            active = true
        )

        drawReservoirBottle(
            canvas = canvas,
            centerX = centerX - size * 0.34f,
            bottomY = centerY + size * 0.26f,
            scale = scale * 0.88f,
            liquidPercent = 0.58f
        )

        val tube = Path().apply {
            moveTo(
                centerX + size * 0.12f,
                centerY - size * 0.05f
            )
            cubicTo(
                centerX + size * 0.18f,
                centerY + size * 0.02f,
                centerX + size * 0.20f,
                centerY + size * 0.11f,
                centerX + size * 0.20f,
                centerY + size * 0.20f
            )
        }

        drawTubePath(
            canvas = canvas,
            path = tube,
            animated = true
        )

        drawMeasuringCylinder(
            canvas = canvas,
            centerX = centerX + size * 0.20f,
            bottomY = centerY + size * 0.32f,
            scale = scale,
            liquidPercent = animatedLiquidPercent(
                base = 0.20f,
                range = 0.06f
            ),
            showWave = true
        )

        drawArrowDown(
            canvas = canvas,
            x = centerX + size * 0.34f,
            y = centerY + size * 0.02f,
            height = size * 0.22f
        )
    }

    private fun drawMeasureStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val scale =
            size / 135f

        drawMeasuringCylinder(
            canvas = canvas,
            centerX = centerX,
            bottomY = centerY + size * 0.31f,
            scale = scale,
            liquidPercent = 0.56f,
            showWave = true
        )

        val pointerY =
            centerY + size * 0.02f

        canvas.drawLine(
            centerX + size * 0.08f,
            pointerY,
            centerX + size * 0.30f,
            pointerY,
            bodyStrokePaint
        )

        canvas.drawCircle(
            centerX + size * 0.33f + bob(
                amount = dp(2f)
            ),
            pointerY,
            dp(3.4f),
            cyanPaint
        )

        drawTag(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY + size * 0.41f,
            width = size * 0.38f,
            height = size * 0.15f,
            label = "3.50 ml"
        )
    }

    private fun drawTestDoseStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val scale =
            size / 180f

        drawPumpHead(
            canvas = canvas,
            centerX = centerX - size * 0.06f,
            topY = centerY - size * 0.31f,
            scale = scale,
            active = true
        )

        val tube = Path().apply {
            moveTo(
                centerX + size * 0.10f,
                centerY - size * 0.05f
            )
            cubicTo(
                centerX + size * 0.16f,
                centerY + size * 0.02f,
                centerX + size * 0.17f,
                centerY + size * 0.12f,
                centerX + size * 0.17f,
                centerY + size * 0.20f
            )
        }

        drawTubePath(
            canvas = canvas,
            path = tube,
            animated = true
        )

        drawMeasuringCylinder(
            canvas = canvas,
            centerX = centerX + size * 0.17f,
            bottomY = centerY + size * 0.32f,
            scale = scale,
            liquidPercent = animatedLiquidPercent(
                base = 0.42f,
                range = 0.05f
            ),
            showWave = true
        )

        textPaint.textSize =
            sp(13f)

        canvas.drawText(
            "4 ml",
            centerX,
            centerY + size * 0.44f,
            textPaint
        )
    }

    private fun drawConfirmStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val scale =
            size / 145f

        drawMeasuringCylinder(
            canvas = canvas,
            centerX = centerX - size * 0.04f,
            bottomY = centerY + size * 0.31f,
            scale = scale,
            liquidPercent = 0.62f,
            showWave = true
        )

        drawCheckBadge(
            canvas = canvas,
            centerX = centerX + size * 0.27f,
            centerY = centerY - size * 0.05f,
            radius = size * 0.115f
        )
    }

    private fun drawPumpHead(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        scale: Float,
        active: Boolean
    ) {
        val bodyWidth =
            66f * scale

        val bodyHeight =
            50f * scale

        val pulse =
            if (active) {
                1f + sin(animationProgress * PI * 2f).toFloat() * 0.018f
            } else {
                1f
            }

        canvas.save()
        canvas.scale(
            pulse,
            pulse,
            centerX,
            topY + bodyHeight / 2f
        )

        val body = RectF(
            centerX - bodyWidth / 2f,
            topY,
            centerX + bodyWidth / 2f,
            topY + bodyHeight
        )

        bodyFillPaint.shader =
            LinearGradient(
                body.left,
                body.top,
                body.right,
                body.bottom,
                Color.parseColor("#17213A"),
                Color.parseColor("#0D1325"),
                Shader.TileMode.CLAMP
            )

        canvas.drawRoundRect(
            body,
            12f * scale,
            12f * scale,
            bodyFillPaint
        )

        bodyFillPaint.shader =
            null

        canvas.drawRoundRect(
            body,
            12f * scale,
            12f * scale,
            bodyStrokePaint
        )

        val ledRadius =
            if (active) {
                5.4f * scale + sin(animationProgress * PI * 2f).toFloat() * 1.2f * scale
            } else {
                4.8f * scale
            }

        withPaintAlpha(
            glowPaint,
            if (active) 70 else 32
        ) {
            canvas.drawCircle(
                centerX,
                topY + bodyHeight / 2f,
                ledRadius * 3.1f,
                glowPaint
            )
        }

        canvas.drawCircle(
            centerX,
            topY + bodyHeight / 2f,
            ledRadius,
            cyanPaint
        )

        canvas.drawLine(
            centerX - 20f * scale,
            topY + bodyHeight,
            centerX - 20f * scale,
            topY + bodyHeight + 28f * scale,
            softStrokePaint
        )

        canvas.drawLine(
            centerX + 20f * scale,
            topY + bodyHeight,
            centerX + 20f * scale,
            topY + bodyHeight + 28f * scale,
            softStrokePaint
        )

        canvas.restore()
    }

    private fun drawReservoirBottle(
        canvas: Canvas,
        centerX: Float,
        bottomY: Float,
        scale: Float,
        liquidPercent: Float
    ) {
        val width =
            26f * scale

        val height =
            66f * scale

        val bottle = RectF(
            centerX - width / 2f,
            bottomY - height,
            centerX + width / 2f,
            bottomY
        )

        canvas.drawRoundRect(
            bottle,
            6f * scale,
            6f * scale,
            bodyFillPaint
        )

        canvas.drawRoundRect(
            bottle,
            6f * scale,
            6f * scale,
            softStrokePaint
        )

        val innerPadding =
            4f * scale

        val innerHeight =
            height - innerPadding * 2f

        val liquidHeight =
            innerHeight * liquidPercent.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        val liquidRect = RectF(
            bottle.left + innerPadding,
            bottle.bottom - innerPadding - liquidHeight,
            bottle.right - innerPadding,
            bottle.bottom - innerPadding
        )

        canvas.drawRoundRect(
            liquidRect,
            4f * scale,
            4f * scale,
            liquidPaint
        )

        drawLiquidHighlight(
            canvas = canvas,
            rect = liquidRect
        )
    }

    private fun drawMeasuringCylinder(
        canvas: Canvas,
        centerX: Float,
        bottomY: Float,
        scale: Float,
        liquidPercent: Float,
        showWave: Boolean
    ) {
        val width =
            42f * scale

        val height =
            100f * scale

        val left =
            centerX - width / 2f

        val top =
            bottomY - height

        val outline = Path().apply {
            moveTo(
                left + width * 0.26f,
                top
            )
            lineTo(
                left + width * 0.78f,
                top
            )
            lineTo(
                left + width * 0.66f,
                top + height * 0.82f
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
                top + height * 0.82f
            )
            close()
        }

        canvas.drawPath(
            outline,
            softStrokePaint
        )

        val liquidHeight =
            height * 0.54f * liquidPercent.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        val liquidTop =
            bottomY - height * 0.11f - liquidHeight

        val liquidBottom =
            bottomY - height * 0.11f

        val liquidRect = RectF(
            left + width * 0.22f,
            liquidTop,
            left + width * 0.80f,
            liquidBottom
        )

        if (showWave) {
            val wavePath =
                createWaveLiquidPath(
                    rect = liquidRect,
                    waveHeight = 2.5f * scale
                )

            canvas.drawPath(
                wavePath,
                liquidPaint
            )
        } else {
            canvas.drawRoundRect(
                liquidRect,
                4f * scale,
                4f * scale,
                liquidPaint
            )
        }

        drawLiquidHighlight(
            canvas = canvas,
            rect = liquidRect
        )

        repeat(6) { index ->
            val tickY =
                top + height * 0.23f + index * height * 0.095f

            val tickLength =
                if (index % 2 == 0) {
                    width * 0.20f
                } else {
                    width * 0.13f
                }

            canvas.drawLine(
                left + width * 0.66f,
                tickY,
                left + width * 0.66f + tickLength,
                tickY,
                softStrokePaint
            )
        }

        canvas.drawLine(
            left + width * 0.24f,
            top,
            left + width * 0.80f,
            top,
            bodyStrokePaint
        )
    }

    private fun drawTubePath(
        canvas: Canvas,
        path: Path,
        animated: Boolean
    ) {
        canvas.drawPath(
            path,
            tubeBasePaint
        )

        if (animated) {
            drawMovingTubeSegment(
                canvas = canvas,
                path = path
            )
        }
    }

    private fun drawMovingTubeSegment(
        canvas: Canvas,
        path: Path
    ) {
        val measure =
            PathMeasure(
                path,
                false
            )

        val length =
            measure.length

        if (length <= 0f) {
            return
        }

        val segmentLength =
            length * 0.36f

        val start =
            ((animationProgress * length * 1.25f) % length)

        val end =
            start + segmentLength

        val segment = Path()

        if (end <= length) {
            measure.getSegment(
                start,
                end,
                segment,
                true
            )
        } else {
            measure.getSegment(
                start,
                length,
                segment,
                true
            )

            measure.getSegment(
                0f,
                end - length,
                segment,
                true
            )
        }

        withPaintAlpha(
            tubeLiquidPaint,
            210
        ) {
            canvas.drawPath(
                segment,
                tubeLiquidPaint
            )
        }
    }

    private fun drawLiquidDrop(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        colorPaint: Paint
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
                centerY + 16f * scale
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
            colorPaint
        )

        withPaintAlpha(
            glowPaint,
            45
        ) {
            canvas.drawCircle(
                centerX,
                centerY + 3f * scale,
                18f * scale,
                glowPaint
            )
        }
    }

    private fun drawArrowDown(
        canvas: Canvas,
        x: Float,
        y: Float,
        height: Float
    ) {
        val offset =
            dripMotion(
                distance = dp(5f)
            )

        canvas.drawLine(
            x,
            y + offset,
            x,
            y + height + offset,
            bodyStrokePaint
        )

        canvas.drawLine(
            x,
            y + height + offset,
            x - dp(8f),
            y + height - dp(9f) + offset,
            bodyStrokePaint
        )

        canvas.drawLine(
            x,
            y + height + offset,
            x + dp(8f),
            y + height - dp(9f) + offset,
            bodyStrokePaint
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
            height / 2f,
            height / 2f,
            tagFillPaint
        )

        canvas.drawRoundRect(
            rect,
            height / 2f,
            height / 2f,
            tagStrokePaint
        )

        textPaint.textSize =
            sp(11f)

        canvas.drawText(
            label,
            centerX,
            centerY + textPaint.textSize / 3f,
            textPaint
        )
    }

    private fun drawCheckBadge(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val pulse =
            1f + sin(animationProgress * PI * 2f).toFloat() * 0.04f

        canvas.save()

        canvas.scale(
            pulse,
            pulse,
            centerX,
            centerY
        )

        withPaintAlpha(
            glowPaint,
            85
        ) {
            canvas.drawCircle(
                centerX,
                centerY,
                radius * 1.75f,
                glowPaint
            )
        }

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            cyanPaint
        )

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3.2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#06111F")
        }

        val checkPath = Path().apply {
            moveTo(
                centerX - radius * 0.42f,
                centerY - radius * 0.02f
            )

            lineTo(
                centerX - radius * 0.12f,
                centerY + radius * 0.30f
            )

            lineTo(
                centerX + radius * 0.48f,
                centerY - radius * 0.42f
            )
        }

        canvas.drawPath(
            checkPath,
            checkPaint
        )

        canvas.restore()
    }

    private fun createWaveLiquidPath(
        rect: RectF,
        waveHeight: Float
    ): Path {
        val phase =
            animationProgress * PI.toFloat() * 2f

        val path = Path()

        path.moveTo(
            rect.left,
            rect.top + waveHeight
        )

        val step =
            rect.width() / 5f

        var x =
            rect.left

        while (x <= rect.right + step) {
            val normalized =
                (x - rect.left) / rect.width()

            val y =
                rect.top +
                    waveHeight +
                    sin(
                        normalized * PI.toFloat() * 2f + phase
                    ) * waveHeight

            path.lineTo(
                x,
                y
            )

            x += step
        }

        path.lineTo(
            rect.right,
            rect.bottom
        )

        path.lineTo(
            rect.left,
            rect.bottom
        )

        path.close()

        return path
    }

    private fun drawLiquidHighlight(
        canvas: Canvas,
        rect: RectF
    ) {
        if (
            rect.width() <= 0f ||
            rect.height() <= 0f
        ) {
            return
        }

        val highlight = RectF(
            rect.left,
            rect.top,
            rect.left + rect.width() * 0.32f,
            rect.bottom
        )

        canvas.drawRoundRect(
            highlight,
            dp(4f),
            dp(4f),
            liquidSoftPaint
        )
    }

    private fun animatedLiquidPercent(
        base: Float,
        range: Float
    ): Float {
        return (
            base +
                sin(animationProgress * PI * 2f).toFloat() * range
            ).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        )
    }

    private fun bob(
        amount: Float
    ): Float {
        return sin(animationProgress * PI * 2f).toFloat() * amount
    }

    private fun dripMotion(
        distance: Float
    ): Float {
        return animationProgress * distance
    }

    private inline fun withPaintAlpha(
        paint: Paint,
        alpha: Int,
        block: () -> Unit
    ) {
        val oldAlpha =
            paint.alpha

        paint.alpha =
            alpha.coerceIn(
                minimumValue = 0,
                maximumValue = 255
            )

        block()

        paint.alpha =
            oldAlpha
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