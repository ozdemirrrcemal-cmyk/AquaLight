@file:Suppress("LongMethod", "LongParameterList", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun DosingCalibrationIllustration(
    step: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    operationDurationMillis: Int = DEFAULT_OPERATION_DURATION_MS
) {
    val transition = rememberInfiniteTransition(label = "dosing-calibration-illustration")
    val flowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(FLOW_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dosing-calibration-flow"
    )
    val ambientPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AMBIENT_CYCLE_MILLIS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dosing-calibration-ambient"
    )
    val fillProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = operationDurationMillis.coerceIn(
                MIN_OPERATION_DURATION_MS,
                MAX_OPERATION_DURATION_MS
            ),
            easing = LinearEasing
        ),
        label = "dosing-calibration-fill"
    )
    val description = stringResource(step.illustrationDescriptionRes)

    Canvas(
        modifier = modifier.semantics {
            contentDescription = description
        }
    ) {
        when (step) {
            DeviceDosingCalibrationStep.NAME -> drawNameScene(colors, ambientPhase)
            DeviceDosingCalibrationStep.PRIME -> drawFluidSystem(
                colors = colors,
                flowPhase = flowPhase,
                ambientPhase = ambientPhase,
                active = active,
                fillProgress = 0f,
                destination = FluidDestination.WASTE
            )
            DeviceDosingCalibrationStep.CALIBRATION_RUN -> drawFluidSystem(
                colors = colors,
                flowPhase = flowPhase,
                ambientPhase = ambientPhase,
                active = active,
                fillProgress = fillProgress * CALIBRATION_FILL_RATIO,
                destination = FluidDestination.CYLINDER
            )
            DeviceDosingCalibrationStep.MEASUREMENT -> drawMeasurementScene(
                colors = colors,
                ambientPhase = ambientPhase
            )
            DeviceDosingCalibrationStep.VERIFICATION -> drawFluidSystem(
                colors = colors,
                flowPhase = flowPhase,
                ambientPhase = ambientPhase,
                active = active,
                fillProgress = fillProgress * FOUR_ML_FILL_RATIO,
                destination = FluidDestination.CYLINDER,
                showFourMlTarget = true
            )
            DeviceDosingCalibrationStep.CONFIRMATION -> drawConfirmationScene(
                colors = colors,
                ambientPhase = ambientPhase
            )
        }
    }
}

private fun DrawScope.drawNameScene(colors: AquaGuidedFlowColors, ambientPhase: Float) {
    val lift = sin(ambientPhase * PI).toFloat() * size.height * .018f
    val bottle = Rect(
        left = size.width * .20f,
        top = size.height * .20f,
        right = size.width * .49f,
        bottom = size.height * .90f
    )
    drawBottleShadow(bottle)
    drawRealisticReservoir(
        colors = colors,
        bounds = bottle,
        liquidRatio = .58f,
        ambientPhase = ambientPhase,
        showLabel = true
    )

    val tag = Rect(
        left = size.width * .55f,
        top = size.height * .31f - lift,
        right = size.width * .86f,
        bottom = size.height * .70f - lift
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(colors.surfaceRaised, colors.surface)
        ),
        topLeft = tag.topLeft,
        size = tag.size,
        cornerRadius = CornerRadius(tag.height * .13f)
    )
    drawRoundRect(
        color = colors.outline.copy(alpha = .9f),
        topLeft = tag.topLeft,
        size = tag.size,
        cornerRadius = CornerRadius(tag.height * .13f),
        style = Stroke(width = size.minDimension * .009f)
    )
    drawCircle(
        color = colors.accent.copy(alpha = .30f + ambientPhase * .18f),
        radius = tag.height * .09f,
        center = Offset(tag.left + tag.width * .14f, tag.top + tag.height * .22f)
    )
    repeat(3) { index ->
        val y = tag.top + tag.height * (.35f + index * .18f)
        drawLine(
            color = if (index == 0) colors.accent else colors.textSecondary.copy(alpha = .58f),
            start = Offset(tag.left + tag.width * .13f, y),
            end = Offset(tag.right - tag.width * (.12f + index * .08f), y),
            strokeWidth = size.minDimension * if (index == 0) .020f else .014f,
            cap = StrokeCap.Round
        )
    }
    drawLine(
        color = colors.accent.copy(alpha = .45f + ambientPhase * .35f),
        start = Offset(bottle.right + size.width * .025f, bottle.center.y),
        end = Offset(tag.left - size.width * .025f, tag.center.y),
        strokeWidth = size.minDimension * .010f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(size.minDimension * .05f, size.minDimension * .035f),
            phase = ambientPhase * size.minDimension * .08f
        )
    )
}

private fun DrawScope.drawFluidSystem(
    colors: AquaGuidedFlowColors,
    flowPhase: Float,
    ambientPhase: Float,
    active: Boolean,
    fillProgress: Float,
    destination: FluidDestination,
    showFourMlTarget: Boolean = false
) {
    val bottle = Rect(
        left = size.width * .025f,
        top = size.height * .42f,
        right = size.width * .21f,
        bottom = size.height * .93f
    )
    val pumpCenter = Offset(size.width * .47f, size.height * .48f)
    val pumpRadius = size.height * .245f
    val outletEnd = Offset(size.width * .79f, size.height * .30f)
    val inletPath = Path().apply {
        moveTo(bottle.left + bottle.width * .54f, bottle.bottom - bottle.height * .10f)
        cubicTo(
            bottle.left + bottle.width * .50f,
            bottle.top - size.height * .12f,
            size.width * .31f,
            size.height * .34f,
            pumpCenter.x - pumpRadius * .92f,
            pumpCenter.y
        )
    }
    val outletPath = Path().apply {
        moveTo(pumpCenter.x + pumpRadius * .92f, pumpCenter.y)
        cubicTo(
            size.width * .62f,
            size.height * .48f,
            size.width * .69f,
            size.height * .27f,
            outletEnd.x,
            outletEnd.y
        )
    }

    drawTube(
        path = inletPath,
        colors = colors,
        active = active,
        flowPhase = flowPhase,
        reverseHighlights = false
    )
    drawTube(
        path = outletPath,
        colors = colors,
        active = active,
        flowPhase = flowPhase,
        reverseHighlights = true
    )
    drawBottleShadow(bottle)
    drawRealisticReservoir(
        colors = colors,
        bounds = bottle,
        liquidRatio = .66f,
        ambientPhase = ambientPhase,
        showLabel = false
    )
    drawPeristalticPump(
        colors = colors,
        center = pumpCenter,
        radius = pumpRadius,
        rotorAngle = if (active) flowPhase * 360f else RESTING_ROTOR_ANGLE,
        active = active,
        ambientPhase = ambientPhase
    )

    when (destination) {
        FluidDestination.WASTE -> drawWasteCup(
            colors = colors,
            bounds = Rect(
                left = size.width * .75f,
                top = size.height * .55f,
                right = size.width * .94f,
                bottom = size.height * .92f
            ),
            active = active,
            flowPhase = flowPhase,
            outletEnd = outletEnd
        )
        FluidDestination.CYLINDER -> {
            val cylinder = Rect(
                left = size.width * .76f,
                top = size.height * .28f,
                right = size.width * .91f,
                bottom = size.height * .92f
            )
            drawGraduatedCylinder(
                colors = colors,
                bounds = cylinder,
                liquidRatio = fillProgress,
                showFourMlTarget = showFourMlTarget,
                emphasizeMeniscus = false,
                ambientPhase = ambientPhase
            )
            if (active) {
                drawFallingDrops(
                    colors = colors,
                    flowPhase = flowPhase,
                    outletEnd = outletEnd,
                    destinationY = cylinder.top + cylinder.height * .07f
                )
            }
        }
    }
}

private fun DrawScope.drawMeasurementScene(
    colors: AquaGuidedFlowColors,
    ambientPhase: Float
) {
    val cylinder = Rect(
        left = size.width * .35f,
        top = size.height * .08f,
        right = size.width * .60f,
        bottom = size.height * .94f
    )
    val liquidRatio = CALIBRATION_FILL_RATIO
    val meniscusY = cylinder.bottom - cylinder.height * liquidRatio
    drawGraduatedCylinder(
        colors = colors,
        bounds = cylinder,
        liquidRatio = liquidRatio,
        showFourMlTarget = false,
        emphasizeMeniscus = true,
        ambientPhase = ambientPhase
    )
    drawEyeLevelGuide(
        colors = colors,
        eyeCenter = Offset(size.width * .78f, meniscusY),
        lineStartX = cylinder.right + size.width * .02f,
        meniscusY = meniscusY,
        ambientPhase = ambientPhase
    )
}

private fun DrawScope.drawConfirmationScene(
    colors: AquaGuidedFlowColors,
    ambientPhase: Float
) {
    val cylinder = Rect(
        left = size.width * .34f,
        top = size.height * .08f,
        right = size.width * .59f,
        bottom = size.height * .94f
    )
    drawGraduatedCylinder(
        colors = colors,
        bounds = cylinder,
        liquidRatio = FOUR_ML_FILL_RATIO,
        showFourMlTarget = true,
        emphasizeMeniscus = true,
        ambientPhase = ambientPhase
    )
    drawToleranceBadge(
        colors = colors,
        center = Offset(size.width * .76f, size.height * .48f),
        ambientPhase = ambientPhase
    )
}

private fun DrawScope.drawBottleShadow(bounds: Rect) {
    drawOval(
        color = Color.Black.copy(alpha = .28f),
        topLeft = Offset(bounds.left - bounds.width * .06f, bounds.bottom - bounds.height * .035f),
        size = Size(bounds.width * 1.12f, bounds.height * .12f)
    )
}

private fun DrawScope.drawRealisticReservoir(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    liquidRatio: Float,
    ambientPhase: Float,
    showLabel: Boolean
) {
    val neckWidth = bounds.width * .42f
    val neck = Rect(
        left = bounds.center.x - neckWidth / 2f,
        top = bounds.top,
        right = bounds.center.x + neckWidth / 2f,
        bottom = bounds.top + bounds.height * .16f
    )
    val body = Rect(
        left = bounds.left,
        top = bounds.top + bounds.height * .12f,
        right = bounds.right,
        bottom = bounds.bottom
    )
    val cap = Rect(
        left = neck.left - neckWidth * .06f,
        top = bounds.top - bounds.height * .045f,
        right = neck.right + neckWidth * .06f,
        bottom = neck.bottom - bounds.height * .07f
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF455260), Color(0xFFB7C3CF), Color(0xFF394653))
        ),
        topLeft = cap.topLeft,
        size = cap.size,
        cornerRadius = CornerRadius(cap.height * .22f)
    )
    repeat(4) { index ->
        val x = cap.left + cap.width * (.18f + index * .21f)
        drawLine(
            color = Color.White.copy(alpha = .22f),
            start = Offset(x, cap.top + cap.height * .14f),
            end = Offset(x, cap.bottom - cap.height * .14f),
            strokeWidth = size.minDimension * .004f
        )
    }
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color(0xFF0B1622).copy(alpha = .70f),
                Color(0xFF6D8397).copy(alpha = .28f),
                Color(0xFF0B1622).copy(alpha = .72f)
            )
        ),
        topLeft = neck.topLeft,
        size = neck.size,
        cornerRadius = CornerRadius(neck.width * .08f)
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color(0xFF07131F).copy(alpha = .86f),
                colors.surfaceRaised.copy(alpha = .38f),
                Color(0xFF07131F).copy(alpha = .82f)
            )
        ),
        topLeft = body.topLeft,
        size = body.size,
        cornerRadius = CornerRadius(body.width * .18f)
    )

    val inset = body.width * .075f
    val inner = Rect(
        left = body.left + inset,
        top = body.top + inset,
        right = body.right - inset,
        bottom = body.bottom - inset
    )
    val liquidTop = inner.bottom - inner.height * liquidRatio.coerceIn(0f, 1f)
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    colors.accent.copy(alpha = .88f),
                    Color(0xFF0877B9).copy(alpha = .92f),
                    Color(0xFF063D69).copy(alpha = .96f)
                ),
                startY = liquidTop,
                endY = inner.bottom
            ),
            topLeft = Offset(inner.left, liquidTop),
            size = Size(inner.width, inner.bottom - liquidTop)
        )
        val wave = sin(ambientPhase * PI * 2).toFloat() * inner.height * .012f
        drawOval(
            color = colors.accent.copy(alpha = .92f),
            topLeft = Offset(inner.left, liquidTop - inner.height * .018f + wave),
            size = Size(inner.width, inner.height * .04f)
        )
    }
    drawRoundRect(
        color = Color.White.copy(alpha = .20f),
        topLeft = Offset(body.left + body.width * .11f, body.top + body.height * .10f),
        size = Size(body.width * .075f, body.height * .67f),
        cornerRadius = CornerRadius(body.width * .04f)
    )
    drawRoundRect(
        color = colors.outline.copy(alpha = .90f),
        topLeft = body.topLeft,
        size = body.size,
        cornerRadius = CornerRadius(body.width * .18f),
        style = Stroke(width = size.minDimension * .009f)
    )
    if (showLabel) {
        val label = Rect(
            left = body.left + body.width * .18f,
            top = body.top + body.height * .34f,
            right = body.right - body.width * .18f,
            bottom = body.top + body.height * .66f
        )
        drawRoundRect(
            color = Color(0xFFE8EEF3).copy(alpha = .88f),
            topLeft = label.topLeft,
            size = label.size,
            cornerRadius = CornerRadius(label.height * .10f)
        )
        repeat(2) { index ->
            val y = label.top + label.height * (.38f + index * .28f)
            drawLine(
                color = Color(0xFF4C5C69).copy(alpha = .72f),
                start = Offset(label.left + label.width * .14f, y),
                end = Offset(label.right - label.width * (.14f + index * .13f), y),
                strokeWidth = size.minDimension * .009f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawTube(
    path: Path,
    colors: AquaGuidedFlowColors,
    active: Boolean,
    flowPhase: Float,
    reverseHighlights: Boolean
) {
    val tubeWidth = size.minDimension * .070f
    drawPath(
        path = path,
        color = Color.Black.copy(alpha = .42f),
        style = Stroke(width = tubeWidth * 1.22f, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            listOf(
                Color(0xFF9FC8DE).copy(alpha = .42f),
                Color(0xFF203D53).copy(alpha = .54f),
                Color(0xFFC5E9F7).copy(alpha = .30f)
            )
        ),
        style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = colors.surface.copy(alpha = .94f),
        style = Stroke(width = tubeWidth * .54f, cap = StrokeCap.Round)
    )
    if (active) {
        drawPath(
            path = path,
            color = colors.accent.copy(alpha = .94f),
            style = Stroke(
                width = tubeWidth * .48f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(tubeWidth * 2.4f, tubeWidth * .75f),
                    phase = -flowPhase * tubeWidth * 3.15f
                )
            )
        )
        val measure = PathMeasure().apply { setPath(path, false) }
        repeat(3) { index ->
            val offset = if (reverseHighlights) index * .28f + .12f else index * .28f
            val position = measure.getPosition(
                measure.length * ((flowPhase + offset) % 1f)
            )
            drawCircle(
                color = Color.White.copy(alpha = .76f),
                radius = tubeWidth * .11f,
                center = position
            )
        }
    } else {
        drawPath(
            path = path,
            color = Color.White.copy(alpha = .24f),
            style = Stroke(width = tubeWidth * .08f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawPeristalticPump(
    colors: AquaGuidedFlowColors,
    center: Offset,
    radius: Float,
    rotorAngle: Float,
    active: Boolean,
    ambientPhase: Float
) {
    val body = Rect(
        left = center.x - radius * 1.16f,
        top = center.y - radius * 1.14f,
        right = center.x + radius * 1.16f,
        bottom = center.y + radius * 1.14f
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = .34f),
        topLeft = Offset(body.left + radius * .10f, body.top + radius * .15f),
        size = body.size,
        cornerRadius = CornerRadius(radius * .34f)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color(0xFF65717C),
                Color(0xFFE0E5E9),
                Color(0xFF6D7882),
                Color(0xFF252D34)
            )
        ),
        topLeft = body.topLeft,
        size = body.size,
        cornerRadius = CornerRadius(radius * .34f)
    )
    val face = Rect(
        left = body.left + radius * .14f,
        top = body.top + radius * .14f,
        right = body.right - radius * .14f,
        bottom = body.bottom - radius * .14f
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(Color(0xFF27313A), Color(0xFF0A0E12), Color(0xFF020405)),
            center = center,
            radius = radius
        ),
        topLeft = face.topLeft,
        size = face.size,
        cornerRadius = CornerRadius(radius * .27f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = .18f),
        topLeft = face.topLeft,
        size = face.size,
        cornerRadius = CornerRadius(radius * .27f),
        style = Stroke(width = size.minDimension * .008f)
    )
    drawCircle(
        color = Color(0xFF111A21),
        radius = radius * .70f,
        center = center
    )
    drawCircle(
        color = Color(0xFF9FB5C4).copy(alpha = .55f),
        radius = radius * .70f,
        center = center,
        style = Stroke(width = radius * .08f)
    )
    drawArc(
        color = if (active) colors.accent.copy(alpha = .92f) else colors.outline,
        startAngle = 205f,
        sweepAngle = 310f,
        useCenter = false,
        topLeft = Offset(center.x - radius * .56f, center.y - radius * .56f),
        size = Size(radius * 1.12f, radius * 1.12f),
        style = Stroke(width = radius * .12f, cap = StrokeCap.Round)
    )
    repeat(3) { index ->
        val angle = (rotorAngle + index * 120f) * PI / 180.0
        val rollerCenter = Offset(
            x = center.x + cos(angle).toFloat() * radius * .40f,
            y = center.y + sin(angle).toFloat() * radius * .40f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF5F7F8), Color(0xFF8B98A4), Color(0xFF303A43)),
                center = rollerCenter,
                radius = radius * .17f
            ),
            radius = radius * .17f,
            center = rollerCenter
        )
        drawCircle(
            color = Color.Black.copy(alpha = .55f),
            radius = radius * .17f,
            center = rollerCenter,
            style = Stroke(width = radius * .025f)
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFD7DEE3), Color(0xFF515E69), Color(0xFF10161B)),
            center = center,
            radius = radius * .25f
        ),
        radius = radius * .25f,
        center = center
    )
    drawCircle(
        color = if (active) {
            colors.accent.copy(alpha = .24f + ambientPhase * .28f)
        } else {
            Color.White.copy(alpha = .08f)
        },
        radius = radius * .15f,
        center = center
    )
    val ledCenter = Offset(body.right - radius * .20f, body.top + radius * .22f)
    if (active) {
        drawCircle(
            color = Color(0xFF54F69B).copy(alpha = .16f + ambientPhase * .22f),
            radius = radius * .15f,
            center = ledCenter
        )
    }
    drawCircle(
        color = if (active) Color(0xFF5CFF9E) else Color(0xFF687783),
        radius = radius * .055f,
        center = ledCenter
    )
}

private fun DrawScope.drawWasteCup(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    active: Boolean,
    flowPhase: Float,
    outletEnd: Offset
) {
    val liquidRatio = if (active) .34f + flowPhase * .08f else .12f
    drawOval(
        color = Color.Black.copy(alpha = .25f),
        topLeft = Offset(bounds.left, bounds.bottom - bounds.height * .035f),
        size = Size(bounds.width, bounds.height * .12f)
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color(0xFF9FC8DE).copy(alpha = .18f),
                Color(0xFFE9FAFF).copy(alpha = .10f),
                Color(0xFF55758B).copy(alpha = .25f)
            )
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * .10f)
    )
    val liquidTop = bounds.bottom - bounds.height * liquidRatio
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(colors.accent.copy(alpha = .74f), Color(0xFF075F9C).copy(alpha = .82f)),
                startY = liquidTop,
                endY = bounds.bottom
            ),
            topLeft = Offset(bounds.left, liquidTop),
            size = Size(bounds.width, bounds.bottom - liquidTop)
        )
        drawOval(
            color = colors.accent.copy(alpha = .86f),
            topLeft = Offset(bounds.left, liquidTop - bounds.height * .025f),
            size = Size(bounds.width, bounds.height * .05f)
        )
    }
    drawRoundRect(
        color = Color(0xFFB9D4E4).copy(alpha = .60f),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * .10f),
        style = Stroke(width = size.minDimension * .011f)
    )
    drawOval(
        color = Color(0xFFD5ECF6).copy(alpha = .72f),
        topLeft = Offset(bounds.left - bounds.width * .02f, bounds.top - bounds.height * .025f),
        size = Size(bounds.width * 1.04f, bounds.height * .10f),
        style = Stroke(width = size.minDimension * .012f)
    )
    if (active) {
        drawFallingDrops(
            colors = colors,
            flowPhase = flowPhase,
            outletEnd = outletEnd,
            destinationY = bounds.top + bounds.height * .03f
        )
    }
}

private fun DrawScope.drawFallingDrops(
    colors: AquaGuidedFlowColors,
    flowPhase: Float,
    outletEnd: Offset,
    destinationY: Float
) {
    val distance = (destinationY - outletEnd.y).coerceAtLeast(size.height * .08f)
    repeat(2) { index ->
        val progress = (flowPhase + index * .52f) % 1f
        val center = Offset(outletEnd.x, outletEnd.y + distance * progress)
        val dropRadius = size.minDimension * (.022f + progress * .010f)
        drawDrop(
            color = colors.accent.copy(alpha = .95f - progress * .18f),
            center = center,
            radius = dropRadius
        )
    }
}

private fun DrawScope.drawDrop(color: Color, center: Offset, radius: Float) {
    val drop = Path().apply {
        moveTo(center.x, center.y - radius * 1.7f)
        cubicTo(
            center.x - radius * .35f,
            center.y - radius * .85f,
            center.x - radius,
            center.y - radius * .20f,
            center.x - radius,
            center.y + radius * .20f
        )
        cubicTo(
            center.x - radius,
            center.y + radius,
            center.x - radius * .45f,
            center.y + radius * 1.35f,
            center.x,
            center.y + radius * 1.35f
        )
        cubicTo(
            center.x + radius * .45f,
            center.y + radius * 1.35f,
            center.x + radius,
            center.y + radius,
            center.x + radius,
            center.y + radius * .20f
        )
        cubicTo(
            center.x + radius,
            center.y - radius * .20f,
            center.x + radius * .35f,
            center.y - radius * .85f,
            center.x,
            center.y - radius * 1.7f
        )
        close()
    }
    drawPath(
        path = drop,
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = .86f), color),
            center = Offset(center.x - radius * .35f, center.y - radius * .40f),
            radius = radius * 1.8f
        )
    )
}

private fun DrawScope.drawGraduatedCylinder(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    liquidRatio: Float,
    showFourMlTarget: Boolean,
    emphasizeMeniscus: Boolean,
    ambientPhase: Float
) {
    val stem = Rect(
        left = bounds.left + bounds.width * .16f,
        top = bounds.top + bounds.height * .04f,
        right = bounds.right - bounds.width * .16f,
        bottom = bounds.bottom - bounds.height * .12f
    )
    val inner = Rect(
        left = stem.left + stem.width * .10f,
        top = stem.top + stem.height * .035f,
        right = stem.right - stem.width * .10f,
        bottom = stem.bottom - stem.height * .035f
    )
    drawOval(
        color = Color.Black.copy(alpha = .26f),
        topLeft = Offset(bounds.left, bounds.bottom - bounds.height * .035f),
        size = Size(bounds.width, bounds.height * .085f)
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color(0xFF6B8798).copy(alpha = .24f),
                Color(0xFFEAF9FF).copy(alpha = .08f),
                Color(0xFF8AA7B8).copy(alpha = .18f)
            )
        ),
        topLeft = stem.topLeft,
        size = stem.size,
        cornerRadius = CornerRadius(stem.width * .16f)
    )
    val safeRatio = liquidRatio.coerceIn(0f, .96f)
    val liquidY = inner.bottom - inner.height * safeRatio
    if (safeRatio > 0f) {
        clipRect(inner.left, inner.top, inner.right, inner.bottom) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        colors.accent.copy(alpha = .92f),
                        Color(0xFF087CC1).copy(alpha = .94f),
                        Color(0xFF064B80).copy(alpha = .96f)
                    ),
                    startY = liquidY,
                    endY = inner.bottom
                ),
                topLeft = Offset(inner.left, liquidY),
                size = Size(inner.width, inner.bottom - liquidY)
            )
            drawOval(
                color = colors.accent.copy(alpha = .90f),
                topLeft = Offset(inner.left, liquidY - inner.height * .014f),
                size = Size(inner.width, inner.height * .028f)
            )
        }
        val meniscus = Path().apply {
            moveTo(inner.left, liquidY)
            cubicTo(
                inner.left + inner.width * .28f,
                liquidY + inner.height * .024f,
                inner.right - inner.width * .28f,
                liquidY + inner.height * .024f,
                inner.right,
                liquidY
            )
        }
        drawPath(
            path = meniscus,
            color = Color.White.copy(alpha = if (emphasizeMeniscus) .94f else .56f),
            style = Stroke(
                width = size.minDimension * if (emphasizeMeniscus) .012f else .007f,
                cap = StrokeCap.Round
            )
        )
    }
    if (showFourMlTarget) {
        val targetY = inner.bottom - inner.height * FOUR_ML_FILL_RATIO
        val pulse = .08f + ambientPhase * .10f
        drawRoundRect(
            color = Color(0xFF4EE890).copy(alpha = pulse),
            topLeft = Offset(stem.left - stem.width * .14f, targetY - inner.height * .025f),
            size = Size(stem.width * 1.28f, inner.height * .05f),
            cornerRadius = CornerRadius(inner.height * .025f)
        )
        drawLine(
            color = Color(0xFF65F2A0).copy(alpha = .92f),
            start = Offset(stem.left - stem.width * .12f, targetY),
            end = Offset(stem.right + stem.width * .12f, targetY),
            strokeWidth = size.minDimension * .010f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(size.minDimension * .035f, size.minDimension * .022f)
            )
        )
    }
    repeat(CYLINDER_TICK_COUNT) { index ->
        val progress = index / (CYLINDER_TICK_COUNT - 1f)
        val y = inner.bottom - inner.height * progress
        val major = index % 4 == 0
        val tickLength = stem.width * if (major) .33f else .20f
        drawLine(
            color = if (major) {
                Color(0xFFDCEBF3).copy(alpha = .86f)
            } else {
                Color(0xFFA9C0CE).copy(alpha = .62f)
            },
            start = Offset(stem.right - tickLength, y),
            end = Offset(stem.right + stem.width * .03f, y),
            strokeWidth = size.minDimension * if (major) .008f else .005f,
            cap = StrokeCap.Round
        )
    }
    drawRoundRect(
        color = Color(0xFFC6DFEC).copy(alpha = .72f),
        topLeft = stem.topLeft,
        size = stem.size,
        cornerRadius = CornerRadius(stem.width * .16f),
        style = Stroke(width = size.minDimension * .010f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = .28f),
        topLeft = Offset(stem.left + stem.width * .08f, stem.top + stem.height * .07f),
        size = Size(stem.width * .07f, stem.height * .72f),
        cornerRadius = CornerRadius(stem.width * .04f)
    )
    drawOval(
        color = Color(0xFFD9EEF6).copy(alpha = .76f),
        topLeft = Offset(stem.left - stem.width * .07f, stem.top - stem.height * .025f),
        size = Size(stem.width * 1.14f, stem.height * .065f),
        style = Stroke(width = size.minDimension * .012f)
    )
    val baseStem = Rect(
        left = bounds.center.x - bounds.width * .10f,
        top = stem.bottom,
        right = bounds.center.x + bounds.width * .10f,
        bottom = bounds.bottom - bounds.height * .055f
    )
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF5E7A8C).copy(alpha = .45f), Color.White.copy(alpha = .20f)),
        ),
        topLeft = baseStem.topLeft,
        size = baseStem.size
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFCBE2ED).copy(alpha = .65f), Color(0xFF607B8C).copy(alpha = .55f))
        ),
        topLeft = Offset(bounds.left, bounds.bottom - bounds.height * .065f),
        size = Size(bounds.width, bounds.height * .065f),
        cornerRadius = CornerRadius(bounds.width * .10f)
    )
}

private fun DrawScope.drawEyeLevelGuide(
    colors: AquaGuidedFlowColors,
    eyeCenter: Offset,
    lineStartX: Float,
    meniscusY: Float,
    ambientPhase: Float
) {
    val eyeWidth = size.width * .13f
    val eyeHeight = size.height * .16f
    drawLine(
        color = colors.accent.copy(alpha = .58f + ambientPhase * .30f),
        start = Offset(lineStartX, meniscusY),
        end = Offset(eyeCenter.x - eyeWidth * .62f, meniscusY),
        strokeWidth = size.minDimension * .009f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(size.minDimension * .045f, size.minDimension * .025f),
            phase = ambientPhase * size.minDimension * .07f
        )
    )
    val eye = Path().apply {
        moveTo(eyeCenter.x - eyeWidth / 2f, eyeCenter.y)
        cubicTo(
            eyeCenter.x - eyeWidth * .22f,
            eyeCenter.y - eyeHeight / 2f,
            eyeCenter.x + eyeWidth * .22f,
            eyeCenter.y - eyeHeight / 2f,
            eyeCenter.x + eyeWidth / 2f,
            eyeCenter.y
        )
        cubicTo(
            eyeCenter.x + eyeWidth * .22f,
            eyeCenter.y + eyeHeight / 2f,
            eyeCenter.x - eyeWidth * .22f,
            eyeCenter.y + eyeHeight / 2f,
            eyeCenter.x - eyeWidth / 2f,
            eyeCenter.y
        )
        close()
    }
    drawPath(
        path = eye,
        color = colors.surfaceRaised,
    )
    drawPath(
        path = eye,
        color = Color(0xFFD7E9F1).copy(alpha = .82f),
        style = Stroke(width = size.minDimension * .010f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White,
                colors.accent,
                Color(0xFF062D4A)
            ),
            center = eyeCenter,
            radius = eyeHeight * .30f
        ),
        radius = eyeHeight * .30f,
        center = eyeCenter
    )
    drawCircle(
        color = Color(0xFF02070B),
        radius = eyeHeight * .12f,
        center = eyeCenter
    )
    drawCircle(
        color = Color.White.copy(alpha = .88f),
        radius = eyeHeight * .045f,
        center = Offset(eyeCenter.x - eyeHeight * .06f, eyeCenter.y - eyeHeight * .07f)
    )
}

private fun DrawScope.drawToleranceBadge(
    colors: AquaGuidedFlowColors,
    center: Offset,
    ambientPhase: Float
) {
    val radius = size.minDimension * .18f
    drawCircle(
        color = Color(0xFF4EE890).copy(alpha = .08f + ambientPhase * .10f),
        radius = radius * (1.16f + ambientPhase * .08f),
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF5DFAA2), Color(0xFF19A75D), Color(0xFF07562E)),
            center = Offset(center.x - radius * .20f, center.y - radius * .25f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = .25f),
        radius = radius,
        center = center,
        style = Stroke(width = size.minDimension * .010f)
    )
    val check = Path().apply {
        moveTo(center.x - radius * .48f, center.y)
        lineTo(center.x - radius * .12f, center.y + radius * .31f)
        lineTo(center.x + radius * .54f, center.y - radius * .39f)
    }
    drawPath(
        path = check,
        color = colors.onAccent,
        style = Stroke(
            width = size.minDimension * .027f,
            cap = StrokeCap.Round
        )
    )
}

private enum class FluidDestination {
    WASTE,
    CYLINDER
}

private val DeviceDosingCalibrationStep.illustrationDescriptionRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME ->
            R.string.device_dosing_calibration_name_illustration_description
        DeviceDosingCalibrationStep.PRIME ->
            R.string.device_dosing_calibration_prime_illustration_description
        DeviceDosingCalibrationStep.CALIBRATION_RUN ->
            R.string.device_dosing_calibration_run_illustration_description
        DeviceDosingCalibrationStep.MEASUREMENT ->
            R.string.device_dosing_calibration_measure_illustration_description
        DeviceDosingCalibrationStep.VERIFICATION ->
            R.string.device_dosing_calibration_verify_illustration_description
        DeviceDosingCalibrationStep.CONFIRMATION ->
            R.string.device_dosing_calibration_confirm_illustration_description
    }

private const val FLOW_CYCLE_MILLIS = 1_050
private const val AMBIENT_CYCLE_MILLIS = 1_900
private const val DEFAULT_OPERATION_DURATION_MS = 4_000
private const val MIN_OPERATION_DURATION_MS = 700
private const val MAX_OPERATION_DURATION_MS = 15_000
private const val CYLINDER_TICK_COUNT = 21
private const val CALIBRATION_FILL_RATIO = .58f
private const val FOUR_ML_FILL_RATIO = .80f
private const val RESTING_ROTOR_ANGLE = 18f
