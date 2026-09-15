package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        val geometry = DeviceLightAutomaticIconGeometry
        val left = size.width * geometry.calendarLeftFraction
        val right = size.width * geometry.calendarRightFraction
        val top = size.height * geometry.calendarTopFraction
        val bottom = size.height * geometry.calendarBottomFraction
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(size.width * geometry.calendarCornerRadiusFraction),
            style = Stroke(width = stroke)
        )
        val dividerY = size.height * geometry.calendarDividerFraction
        drawLine(
            color = color,
            start = Offset(left, dividerY),
            end = Offset(right, dividerY),
            strokeWidth = stroke
        )
        geometry.calendarBindingFractions.forEach { xFraction ->
            drawLine(
                color = color,
                start = Offset(
                    size.width * xFraction,
                    size.height * geometry.calendarBindingTopFraction
                ),
                end = Offset(
                    size.width * xFraction,
                    size.height * geometry.calendarBindingBottomFraction
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun AutomaticClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val geometry = DeviceLightAutomaticIconGeometry
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        val radius = size.minDimension * geometry.clockRadiusFraction
        drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = center,
            end = Offset(
                center.x,
                center.y - radius * geometry.clockHourHandFraction
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(
                center.x + radius * geometry.clockMinuteHandXFraction,
                center.y + radius * geometry.clockMinuteHandYFraction
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun AutomaticRampIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val geometry = DeviceLightAutomaticIconGeometry
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        val left = Offset(
            size.width * geometry.rampLeftXFraction,
            size.height * geometry.rampLeftYFraction
        )
        val top = Offset(
            size.width * geometry.rampRightXFraction,
            size.height * geometry.rampTopYFraction
        )
        val bottom = Offset(
            size.width * geometry.rampRightXFraction,
            size.height * geometry.rampBottomYFraction
        )
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
        val geometry = DeviceLightAutomaticIconGeometry
        val gap = DeviceLightAutomaticGeometry.moreDotGap.toPx()
        repeat(geometry.moreDotCount) { index ->
            drawCircle(
                color = color,
                radius = DeviceLightAutomaticGeometry.moreDotRadius.toPx(),
                center = center.copy(
                    y = center.y + (index - geometry.moreDotCenterIndex) * gap
                )
            )
        }
    }
}

@Composable
internal fun AutomaticPlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = DeviceLightAutomaticGeometry.addButtonIconStrokeWidth.toPx()
        val inset = size.minDimension * DeviceLightAutomaticIconGeometry.plusInsetFraction
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
