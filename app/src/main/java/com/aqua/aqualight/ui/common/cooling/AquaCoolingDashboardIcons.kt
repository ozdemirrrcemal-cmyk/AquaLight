@file:Suppress("LongMethod", "MagicNumber")

package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp

enum class AquaCoolingDashboardIconKind {
    WATER,
    ROOM,
    HUMIDITY,
    POWER,
    AUTOMATIC,
    MANUAL,
    PROGRAM,
    CHEVRON
}

@Composable
fun AquaCoolingDashboardIcon(
    kind: AquaCoolingDashboardIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = AquaCoolingDashboardGeometry.dashboardIconStrokeWidth
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (kind) {
            AquaCoolingDashboardIconKind.WATER -> drawWaterIcon(tint, stroke)
            AquaCoolingDashboardIconKind.ROOM -> drawRoomIcon(tint, stroke)
            AquaCoolingDashboardIconKind.HUMIDITY -> drawHumidityIcon(tint, stroke)
            AquaCoolingDashboardIconKind.POWER -> drawPowerIcon(tint)
            AquaCoolingDashboardIconKind.AUTOMATIC -> drawAutomaticIcon(tint, stroke)
            AquaCoolingDashboardIconKind.MANUAL -> drawManualIcon(tint, stroke)
            AquaCoolingDashboardIconKind.PROGRAM -> drawProgramIcon(tint, stroke)
            AquaCoolingDashboardIconKind.CHEVRON -> drawChevronIcon(tint, stroke)
        }
    }
}

@Composable
fun AquaCoolingSelectionIndicator(
    selected: Boolean,
    selectedColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val outline = AquaCoolingDashboardGeometry.radioStrokeWidth.toPx()
        if (selected) {
            drawCircle(color = selectedColor)
            val checkStroke = AquaCoolingDashboardGeometry.radioCheckStrokeWidth.toPx()
            drawLine(
                color = AquaCoolingDashboardPalette.primaryText,
                start = Offset(size.width * 0.29f, size.height * 0.51f),
                end = Offset(size.width * 0.45f, size.height * 0.67f),
                strokeWidth = checkStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = AquaCoolingDashboardPalette.primaryText,
                start = Offset(size.width * 0.45f, size.height * 0.67f),
                end = Offset(size.width * 0.72f, size.height * 0.34f),
                strokeWidth = checkStroke,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(
                color = idleColor,
                style = Stroke(width = outline)
            )
        }
    }
}

private fun DrawScope.drawWaterIcon(color: Color, stroke: Stroke) {
    val drop = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.06f)
        cubicTo(
            size.width * 0.43f,
            size.height * 0.23f,
            size.width * 0.22f,
            size.height * 0.47f,
            size.width * 0.22f,
            size.height * 0.65f
        )
        cubicTo(
            size.width * 0.22f,
            size.height * 0.85f,
            size.width * 0.34f,
            size.height * 0.95f,
            size.width * 0.50f,
            size.height * 0.95f
        )
        cubicTo(
            size.width * 0.67f,
            size.height * 0.95f,
            size.width * 0.78f,
            size.height * 0.84f,
            size.width * 0.78f,
            size.height * 0.65f
        )
        cubicTo(
            size.width * 0.78f,
            size.height * 0.47f,
            size.width * 0.57f,
            size.height * 0.23f,
            size.width * 0.50f,
            size.height * 0.06f
        )
        close()
    }
    drawPath(path = drop, color = color, style = stroke)
}

private fun DrawScope.drawRoomIcon(color: Color, stroke: Stroke) {
    val roof = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.46f)
        lineTo(size.width * 0.50f, size.height * 0.10f)
        lineTo(size.width * 0.92f, size.height * 0.46f)
    }
    drawPath(path = roof, color = color, style = stroke)
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.18f, size.height * 0.39f),
        end = Offset(size.width * 0.18f, size.height * 0.90f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.82f, size.height * 0.39f),
        end = Offset(size.width * 0.82f, size.height * 0.90f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.18f, size.height * 0.90f),
        end = Offset(size.width * 0.82f, size.height * 0.90f),
        stroke = stroke
    )
    drawRect(
        color = color,
        topLeft = Offset(size.width * 0.43f, size.height * 0.61f),
        size = Size(size.width * 0.20f, size.height * 0.29f),
        style = stroke
    )
}

private fun DrawScope.drawHumidityIcon(color: Color, stroke: Stroke) {
    val dropBounds = Size(size.width * 0.67f, size.height)
    withTransform({ scale(dropBounds.width / size.width, dropBounds.height / size.height) }) {
        drawWaterIcon(color, stroke)
    }
    drawCircle(
        color = color,
        radius = size.minDimension * 0.07f,
        center = Offset(size.width * 0.72f, size.height * 0.55f),
        style = stroke
    )
    drawCircle(
        color = color,
        radius = size.minDimension * 0.07f,
        center = Offset(size.width * 0.88f, size.height * 0.78f),
        style = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.86f, size.height * 0.52f),
        end = Offset(size.width * 0.73f, size.height * 0.82f),
        stroke = stroke
    )
}

private fun DrawScope.drawPowerIcon(color: Color) {
    val bolt = Path().apply {
        moveTo(size.width * 0.58f, size.height * 0.03f)
        lineTo(size.width * 0.20f, size.height * 0.57f)
        lineTo(size.width * 0.47f, size.height * 0.57f)
        lineTo(size.width * 0.39f, size.height * 0.97f)
        lineTo(size.width * 0.82f, size.height * 0.40f)
        lineTo(size.width * 0.54f, size.height * 0.40f)
        close()
    }
    drawPath(path = bolt, color = color)
}

private fun DrawScope.drawAutomaticIcon(color: Color, stroke: Stroke) {
    drawCircle(
        color = color,
        radius = size.minDimension * 0.36f,
        center = center,
        style = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.37f, size.height * 0.68f),
        end = Offset(size.width * 0.50f, size.height * 0.31f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.50f, size.height * 0.31f),
        end = Offset(size.width * 0.63f, size.height * 0.68f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.42f, size.height * 0.53f),
        end = Offset(size.width * 0.58f, size.height * 0.53f),
        stroke = stroke
    )
    drawArc(
        color = color,
        startAngle = 205f,
        sweepAngle = 72f,
        useCenter = false,
        topLeft = Offset.Zero,
        size = size,
        style = stroke
    )
}

private fun DrawScope.drawManualIcon(color: Color, stroke: Stroke) {
    val hand = Path().apply {
        moveTo(size.width * 0.34f, size.height * 0.54f)
        lineTo(size.width * 0.34f, size.height * 0.22f)
        cubicTo(
            size.width * 0.34f,
            size.height * 0.14f,
            size.width * 0.45f,
            size.height * 0.14f,
            size.width * 0.45f,
            size.height * 0.23f
        )
        lineTo(size.width * 0.45f, size.height * 0.12f)
        cubicTo(
            size.width * 0.45f,
            size.height * 0.04f,
            size.width * 0.56f,
            size.height * 0.04f,
            size.width * 0.56f,
            size.height * 0.13f
        )
        lineTo(size.width * 0.56f, size.height * 0.21f)
        cubicTo(
            size.width * 0.56f,
            size.height * 0.13f,
            size.width * 0.67f,
            size.height * 0.13f,
            size.width * 0.67f,
            size.height * 0.22f
        )
        lineTo(size.width * 0.67f, size.height * 0.32f)
        cubicTo(
            size.width * 0.67f,
            size.height * 0.24f,
            size.width * 0.78f,
            size.height * 0.24f,
            size.width * 0.78f,
            size.height * 0.34f
        )
        lineTo(size.width * 0.78f, size.height * 0.61f)
        cubicTo(
            size.width * 0.78f,
            size.height * 0.82f,
            size.width * 0.66f,
            size.height * 0.94f,
            size.width * 0.49f,
            size.height * 0.94f
        )
        cubicTo(
            size.width * 0.36f,
            size.height * 0.94f,
            size.width * 0.29f,
            size.height * 0.85f,
            size.width * 0.23f,
            size.height * 0.73f
        )
        lineTo(size.width * 0.12f, size.height * 0.51f)
        cubicTo(
            size.width * 0.09f,
            size.height * 0.43f,
            size.width * 0.19f,
            size.height * 0.37f,
            size.width * 0.25f,
            size.height * 0.44f
        )
        close()
    }
    drawPath(path = hand, color = color, style = stroke)
}

private fun DrawScope.drawProgramIcon(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.10f, size.height * 0.18f),
        size = Size(size.width * 0.80f, size.height * 0.70f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            size.width * 0.10f,
            size.height * 0.10f
        ),
        style = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.10f, size.height * 0.38f),
        end = Offset(size.width * 0.90f, size.height * 0.38f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.30f, size.height * 0.08f),
        end = Offset(size.width * 0.30f, size.height * 0.28f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.69f, size.height * 0.08f),
        end = Offset(size.width * 0.69f, size.height * 0.28f),
        stroke = stroke
    )
    val clockCenter = Offset(size.width * 0.67f, size.height * 0.67f)
    drawCircle(
        color = color,
        radius = size.minDimension * 0.18f,
        center = clockCenter,
        style = stroke
    )
    drawIconLine(
        color = color,
        start = clockCenter,
        end = Offset(clockCenter.x, clockCenter.y - size.height * 0.10f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = clockCenter,
        end = Offset(clockCenter.x + size.width * 0.09f, clockCenter.y),
        stroke = stroke
    )
}

private fun DrawScope.drawChevronIcon(color: Color, stroke: Stroke) {
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.32f, size.height * 0.16f),
        end = Offset(size.width * 0.68f, size.height * 0.50f),
        stroke = stroke
    )
    drawIconLine(
        color = color,
        start = Offset(size.width * 0.68f, size.height * 0.50f),
        end = Offset(size.width * 0.32f, size.height * 0.84f),
        stroke = stroke
    )
}

private fun DrawScope.drawIconLine(
    color: Color,
    start: Offset,
    end: Offset,
    stroke: Stroke
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = stroke.width,
        cap = stroke.cap
    )
}
