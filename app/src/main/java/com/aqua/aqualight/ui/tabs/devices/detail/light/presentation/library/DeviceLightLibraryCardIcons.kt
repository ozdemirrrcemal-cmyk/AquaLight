@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload

@Composable
internal fun LibraryEntryIcon(
    payload: DeviceLightLibraryPayload,
    color: Color,
    modifier: Modifier = Modifier
) {
    when (payload) {
        is DeviceLightLibraryPayload.Manual -> LibrarySlidersIcon(color, modifier)
        is DeviceLightLibraryPayload.Custom -> LibraryCurveIcon(color, modifier)
    }
}

@Composable
private fun LibrarySlidersIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = LIBRARY_ICON_STROKE.dp.toPx()
        val knobRadius = size.minDimension * 0.085f
        val rows = listOf(
            0.25f to 0.38f,
            0.50f to 0.67f,
            0.75f to 0.45f
        )
        rows.forEach { (yFraction, knobFraction) ->
            val y = size.height * yFraction
            drawLine(
                color = color,
                start = Offset(size.width * 0.12f, y),
                end = Offset(size.width * 0.88f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = knobRadius,
                center = Offset(size.width * knobFraction, y)
            )
        }
    }
}

@Composable
internal fun LibraryBulbIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = LIBRARY_ICON_STROKE.dp.toPx()
        val bulb = Path().apply {
            moveTo(size.width * 0.36f, size.height * 0.65f)
            cubicTo(
                size.width * 0.18f,
                size.height * 0.52f,
                size.width * 0.20f,
                size.height * 0.20f,
                size.width * 0.50f,
                size.height * 0.16f
            )
            cubicTo(
                size.width * 0.80f,
                size.height * 0.20f,
                size.width * 0.82f,
                size.height * 0.52f,
                size.width * 0.64f,
                size.height * 0.65f
            )
            lineTo(size.width * 0.59f, size.height * 0.73f)
            lineTo(size.width * 0.41f, size.height * 0.73f)
            close()
        }
        drawPath(
            path = bulb,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.39f, size.height * 0.81f),
            end = Offset(size.width * 0.61f, size.height * 0.81f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.43f, size.height * 0.90f),
            end = Offset(size.width * 0.57f, size.height * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun LibraryLightningIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.57f, size.height * 0.08f)
            lineTo(size.width * 0.23f, size.height * 0.54f)
            lineTo(size.width * 0.47f, size.height * 0.54f)
            lineTo(size.width * 0.39f, size.height * 0.92f)
            lineTo(size.width * 0.78f, size.height * 0.43f)
            lineTo(size.width * 0.54f, size.height * 0.43f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = LIBRARY_ICON_STROKE.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
internal fun LibraryCurveIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = LIBRARY_ICON_STROKE.dp.toPx()
        val radius = size.minDimension * 0.085f
        val points = listOf(
            Offset(size.width * 0.14f, size.height * 0.72f),
            Offset(size.width * 0.42f, size.height * 0.49f),
            Offset(size.width * 0.64f, size.height * 0.25f),
            Offset(size.width * 0.84f, size.height * 0.69f)
        )
        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        points.forEach { point ->
            drawCircle(
                color = color,
                radius = radius,
                center = point,
                style = Stroke(width = stroke)
            )
        }
    }
}

private const val LIBRARY_ICON_STROKE = 1.5f
