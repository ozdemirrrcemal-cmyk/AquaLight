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
                start = Offset(
                    size.width * CHECK_START_X_FRACTION,
                    size.height * CHECK_START_Y_FRACTION
                ),
                end = Offset(
                    size.width * CHECK_MIDDLE_X_FRACTION,
                    size.height * CHECK_MIDDLE_Y_FRACTION
                ),
                strokeWidth = checkStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = AquaCoolingDashboardPalette.primaryText,
                start = Offset(
                    size.width * CHECK_MIDDLE_X_FRACTION,
                    size.height * CHECK_MIDDLE_Y_FRACTION
                ),
                end = Offset(
                    size.width * CHECK_END_X_FRACTION,
                    size.height * CHECK_END_Y_FRACTION
                ),
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

private const val CHECK_START_X_FRACTION = 0.29f
private const val CHECK_START_Y_FRACTION = 0.51f
private const val CHECK_MIDDLE_X_FRACTION = 0.45f
private const val CHECK_MIDDLE_Y_FRACTION = 0.67f
private const val CHECK_END_X_FRACTION = 0.72f
private const val CHECK_END_Y_FRACTION = 0.34f
