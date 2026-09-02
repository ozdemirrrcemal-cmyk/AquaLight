package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

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
