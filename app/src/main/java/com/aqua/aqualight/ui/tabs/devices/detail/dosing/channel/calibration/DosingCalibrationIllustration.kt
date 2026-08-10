@file:Suppress("LongMethod", "LongParameterList", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors

@Composable
internal fun DosingCalibrationIllustration(
    step: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "dosing-calibration-illustration")
    val motion by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400),
            repeatMode = RepeatMode.Restart
        ),
        label = "dosing-calibration-motion"
    )
    Canvas(modifier = modifier) {
        when (step) {
            DeviceDosingCalibrationStep.NAME -> drawNameScene(colors, motion)
            DeviceDosingCalibrationStep.PRIME -> drawPrimeScene(colors, motion, active)
            DeviceDosingCalibrationStep.CALIBRATION_RUN ->
                drawCylinderScene(colors, motion, active, calibration = true)
            DeviceDosingCalibrationStep.MEASUREMENT -> drawMeasurementScene(colors, motion)
            DeviceDosingCalibrationStep.VERIFICATION ->
                drawCylinderScene(colors, motion, active, calibration = false)
            DeviceDosingCalibrationStep.CONFIRMATION -> drawConfirmationScene(colors, motion)
        }
    }
}

private fun DrawScope.drawNameScene(colors: AquaGuidedFlowColors, motion: Float) {
    val bottle = Rect(size.width * .18f, size.height * .24f, size.width * .53f, size.height * .86f)
    drawRoundRect(
        color = colors.surfaceRaised,
        topLeft = bottle.topLeft,
        size = bottle.size,
        cornerRadius = CornerRadius(size.minDimension * .08f)
    )
    drawRoundRect(
        color = colors.outline,
        topLeft = bottle.topLeft,
        size = bottle.size,
        cornerRadius = CornerRadius(size.minDimension * .08f),
        style = Stroke(width = size.minDimension * .012f)
    )
    drawRoundRect(
        color = colors.accent.copy(alpha = .68f),
        topLeft = Offset(size.width * .23f, size.height * .57f),
        size = Size(size.width * .25f, size.height * .20f),
        cornerRadius = CornerRadius(size.minDimension * .035f)
    )
    drawRoundRect(
        color = colors.textSecondary,
        topLeft = Offset(size.width * .28f, size.height * .13f),
        size = Size(size.width * .15f, size.height * .15f),
        cornerRadius = CornerRadius(size.minDimension * .025f)
    )
    val tagOffset = kotlin.math.sin(motion * Math.PI * 2).toFloat() * size.height * .012f
    drawRoundRect(
        color = colors.surface,
        topLeft = Offset(size.width * .56f, size.height * .34f + tagOffset),
        size = Size(size.width * .28f, size.height * .27f),
        cornerRadius = CornerRadius(size.minDimension * .04f)
    )
    drawRoundRect(
        color = colors.outline,
        topLeft = Offset(size.width * .56f, size.height * .34f + tagOffset),
        size = Size(size.width * .28f, size.height * .27f),
        cornerRadius = CornerRadius(size.minDimension * .04f),
        style = Stroke(width = size.minDimension * .012f)
    )
    repeat(3) { index ->
        drawLine(
            color = if (index == 0) colors.accent else colors.textSecondary.copy(alpha = .55f),
            start = Offset(size.width * .61f, size.height * (.41f + index * .06f) + tagOffset),
            end = Offset(size.width * (.78f - index * .025f), size.height * (.41f + index * .06f) + tagOffset),
            strokeWidth = size.minDimension * .018f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawPrimeScene(
    colors: AquaGuidedFlowColors,
    motion: Float,
    active: Boolean
) {
    drawTube(colors, motion, active)
    val center = Offset(size.width * .29f, size.height * .50f)
    drawCircle(colors.surface, size.minDimension * .19f, center)
    drawCircle(colors.outline, size.minDimension * .19f, center, style = Stroke(size.minDimension * .015f))
    drawCircle(colors.accent, size.minDimension * .078f, center)
    repeat(3) { index ->
        val angle = (motion * 360f + index * 120f) * (Math.PI / 180.0)
        val orbit = size.minDimension * .125f
        drawCircle(
            color = colors.textSecondary,
            radius = size.minDimension * .018f,
            center = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * orbit,
                center.y + kotlin.math.sin(angle).toFloat() * orbit
            )
        )
    }
}

private fun DrawScope.drawTube(
    colors: AquaGuidedFlowColors,
    motion: Float,
    active: Boolean
) {
    val path = Path().apply {
        moveTo(size.width * .39f, size.height * .50f)
        cubicTo(
            size.width * .56f,
            size.height * .18f,
            size.width * .72f,
            size.height * .76f,
            size.width * .87f,
            size.height * .42f
        )
    }
    drawPath(path, colors.outline, style = Stroke(size.minDimension * .045f, cap = StrokeCap.Round))
    drawPath(
        path,
        colors.accent.copy(alpha = if (active) .9f else .35f),
        style = Stroke(size.minDimension * .022f, cap = StrokeCap.Round)
    )
    if (active) {
        val dropX = size.width * (.48f + motion * .34f)
        val dropY = size.height * (.42f + kotlin.math.sin(motion * Math.PI).toFloat() * .12f)
        drawCircle(colors.onAccent, size.minDimension * .024f, Offset(dropX, dropY))
    }
}

private fun DrawScope.drawCylinderScene(
    colors: AquaGuidedFlowColors,
    motion: Float,
    active: Boolean,
    calibration: Boolean
) {
    val left = size.width * .33f
    val top = size.height * .13f
    val cylinderWidth = size.width * .34f
    val cylinderHeight = size.height * .72f
    val fillRatio = if (calibration) (.22f + motion * .36f) else .34f
    val liquidHeight = cylinderHeight * fillRatio
    drawRoundRect(
        color = colors.surface,
        topLeft = Offset(left, top),
        size = Size(cylinderWidth, cylinderHeight),
        cornerRadius = CornerRadius(size.minDimension * .04f)
    )
    drawRoundRect(
        color = colors.outline,
        topLeft = Offset(left, top),
        size = Size(cylinderWidth, cylinderHeight),
        cornerRadius = CornerRadius(size.minDimension * .04f),
        style = Stroke(size.minDimension * .014f)
    )
    drawRoundRect(
        color = colors.accent.copy(alpha = .72f),
        topLeft = Offset(left + size.minDimension * .018f, top + cylinderHeight - liquidHeight),
        size = Size(cylinderWidth - size.minDimension * .036f, liquidHeight),
        cornerRadius = CornerRadius(size.minDimension * .025f)
    )
    repeat(7) { index ->
        val y = top + cylinderHeight * (.14f + index * .105f)
        val tickWidth = if (index % 2 == 0) cylinderWidth * .22f else cylinderWidth * .13f
        drawLine(
            colors.textSecondary,
            Offset(left + cylinderWidth - tickWidth, y),
            Offset(left + cylinderWidth, y),
            size.minDimension * .009f,
            StrokeCap.Round
        )
    }
    if (active) {
        val dropY = top + ((motion + .18f) % 1f) * cylinderHeight * .34f
        drawCircle(
            colors.accent,
            size.minDimension * .026f,
            Offset(size.width * .50f, dropY)
        )
    }
}

private fun DrawScope.drawMeasurementScene(colors: AquaGuidedFlowColors, motion: Float) {
    drawCylinderScene(colors, motion = .46f, active = false, calibration = true)
    val meniscusY = size.height * .48f
    drawLine(
        colors.onAccent,
        Offset(size.width * .37f, meniscusY),
        Offset(size.width * .63f, meniscusY),
        size.minDimension * .012f,
        StrokeCap.Round
    )
    drawCircle(
        colors.accent.copy(alpha = .18f + motion * .18f),
        size.minDimension * (.08f + motion * .035f),
        Offset(size.width * .72f, meniscusY)
    )
    drawCircle(colors.accent, size.minDimension * .024f, Offset(size.width * .72f, meniscusY))
}

private fun DrawScope.drawConfirmationScene(colors: AquaGuidedFlowColors, motion: Float) {
    val center = Offset(size.width * .50f, size.height * .49f)
    val pulse = size.minDimension * (.25f + motion * .025f)
    drawCircle(colors.accent.copy(alpha = .10f), pulse, center)
    drawCircle(colors.accent.copy(alpha = .20f), size.minDimension * .20f, center)
    drawCircle(colors.accent, size.minDimension * .14f, center)
    val path = Path().apply {
        moveTo(center.x - size.width * .065f, center.y)
        lineTo(center.x - size.width * .015f, center.y + size.height * .055f)
        lineTo(center.x + size.width * .09f, center.y - size.height * .075f)
    }
    drawPath(
        path,
        colors.onAccent,
        style = Stroke(size.minDimension * .026f, cap = StrokeCap.Round)
    )
}
