package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun AutomaticCalendarIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        val left = size.width * 0.16f
        val right = size.width * 0.84f
        val top = size.height * 0.23f
        val bottom = size.height * 0.86f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(left, size.height * 0.40f),
            end = Offset(right, size.height * 0.40f),
            strokeWidth = stroke
        )
        listOf(0.34f, 0.66f).forEach { x ->
            drawLine(
                color = color,
                start = Offset(size.width * x, size.height * 0.12f),
                end = Offset(size.width * x, size.height * 0.31f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun AutomaticClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        val radius = size.minDimension * 0.38f
        val center = center
        drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * 0.47f, center.y + radius * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun AutomaticRampIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        val left = Offset(size.width * 0.13f, size.height * 0.79f)
        val top = Offset(size.width * 0.80f, size.height * 0.21f)
        val bottom = Offset(size.width * 0.80f, size.height * 0.79f)
        drawLine(color, left, top, stroke, StrokeCap.Round)
        drawLine(color, top, bottom, stroke, StrokeCap.Round)
        drawLine(color, bottom, left, stroke, StrokeCap.Round)
    }
}

@Composable
internal fun AutomaticMoreButton(
    color: Color,
    contentDescriptionText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .size(DeviceLightAutomaticGeometry.moreTouchSize)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDescriptionText }
    ) {
        val gap = DeviceLightAutomaticGeometry.moreDotGap.toPx()
        repeat(3) { index ->
            drawCircle(
                color = color,
                radius = DeviceLightAutomaticGeometry.moreDotRadius.toPx(),
                center = center.copy(y = center.y + (index - 1) * gap)
            )
        }
    }
}

@Composable
internal fun AutomaticPlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = DeviceLightAutomaticGeometry.addButtonIconStrokeWidth.toPx()
        val inset = size.minDimension * 0.18f
        drawLine(
            color = color,
            start = Offset(center.x, inset),
            end = Offset(center.x, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(inset, center.y),
            end = Offset(size.width - inset, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
