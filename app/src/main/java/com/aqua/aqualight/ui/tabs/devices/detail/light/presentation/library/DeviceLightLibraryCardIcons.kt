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
        val knobRadius = size.minDimension * SliderIconSpec.knobRadiusScale
        SliderIconSpec.rows.forEach { (yFraction, knobFraction) ->
            val y = size.height * yFraction
            drawLine(
                color = color,
                start = Offset(size.width * SliderIconSpec.lineStartX, y),
                end = Offset(size.width * SliderIconSpec.lineEndX, y),
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
            moveTo(size.width * BulbIconSpec.leftNeckX, size.height * BulbIconSpec.neckY)
            cubicTo(
                size.width * BulbIconSpec.leftOuterX,
                size.height * BulbIconSpec.outerControlY,
                size.width * BulbIconSpec.leftUpperX,
                size.height * BulbIconSpec.upperControlY,
                size.width * BulbIconSpec.centerX,
                size.height * BulbIconSpec.topY
            )
            cubicTo(
                size.width * BulbIconSpec.rightUpperX,
                size.height * BulbIconSpec.upperControlY,
                size.width * BulbIconSpec.rightOuterX,
                size.height * BulbIconSpec.outerControlY,
                size.width * BulbIconSpec.rightNeckX,
                size.height * BulbIconSpec.neckY
            )
            lineTo(size.width * BulbIconSpec.baseRightX, size.height * BulbIconSpec.baseY)
            lineTo(size.width * BulbIconSpec.baseLeftX, size.height * BulbIconSpec.baseY)
            close()
        }
        drawPath(
            path = bulb,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(size.width * BulbIconSpec.upperBaseLeftX, size.height * BulbIconSpec.upperBaseY),
            end = Offset(size.width * BulbIconSpec.upperBaseRightX, size.height * BulbIconSpec.upperBaseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * BulbIconSpec.lowerBaseLeftX, size.height * BulbIconSpec.lowerBaseY),
            end = Offset(size.width * BulbIconSpec.lowerBaseRightX, size.height * BulbIconSpec.lowerBaseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun LibraryLightningIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * LightningIconSpec.topX, size.height * LightningIconSpec.topY)
            lineTo(size.width * LightningIconSpec.leftX, size.height * LightningIconSpec.middleY)
            lineTo(size.width * LightningIconSpec.innerLeftX, size.height * LightningIconSpec.middleY)
            lineTo(size.width * LightningIconSpec.bottomX, size.height * LightningIconSpec.bottomY)
            lineTo(size.width * LightningIconSpec.rightX, size.height * LightningIconSpec.shoulderY)
            lineTo(size.width * LightningIconSpec.innerRightX, size.height * LightningIconSpec.shoulderY)
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
        val radius = size.minDimension * CurveIconSpec.pointRadiusScale
        val points = listOf(
            CurveIconSpec.first.toOffset(size.width, size.height),
            CurveIconSpec.second.toOffset(size.width, size.height),
            CurveIconSpec.third.toOffset(size.width, size.height),
            CurveIconSpec.fourth.toOffset(size.width, size.height)
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

private object SliderIconSpec {
    const val knobRadiusScale = 0.085f
    const val lineStartX = 0.12f
    const val lineEndX = 0.88f
    val rows = listOf(
        firstRowY to firstKnobX,
        secondRowY to secondKnobX,
        thirdRowY to thirdKnobX
    )
    private const val firstRowY = 0.25f
    private const val firstKnobX = 0.38f
    private const val secondRowY = 0.50f
    private const val secondKnobX = 0.67f
    private const val thirdRowY = 0.75f
    private const val thirdKnobX = 0.45f
}

private object BulbIconSpec {
    const val leftNeckX = 0.36f
    const val rightNeckX = 0.64f
    const val neckY = 0.65f
    const val leftOuterX = 0.18f
    const val rightOuterX = 0.82f
    const val outerControlY = 0.52f
    const val leftUpperX = 0.20f
    const val rightUpperX = 0.80f
    const val upperControlY = 0.20f
    const val centerX = 0.50f
    const val topY = 0.16f
    const val baseRightX = 0.59f
    const val baseLeftX = 0.41f
    const val baseY = 0.73f
    const val upperBaseLeftX = 0.39f
    const val upperBaseRightX = 0.61f
    const val upperBaseY = 0.81f
    const val lowerBaseLeftX = 0.43f
    const val lowerBaseRightX = 0.57f
    const val lowerBaseY = 0.90f
}

private object LightningIconSpec {
    const val topX = 0.57f
    const val topY = 0.08f
    const val leftX = 0.23f
    const val middleY = 0.54f
    const val innerLeftX = 0.47f
    const val bottomX = 0.39f
    const val bottomY = 0.92f
    const val rightX = 0.78f
    const val shoulderY = 0.43f
    const val innerRightX = 0.54f
}

private object CurveIconSpec {
    const val pointRadiusScale = 0.085f
    val first = NormalizedIconPoint(x = 0.14f, y = 0.72f)
    val second = NormalizedIconPoint(x = 0.42f, y = 0.49f)
    val third = NormalizedIconPoint(x = 0.64f, y = 0.25f)
    val fourth = NormalizedIconPoint(x = 0.84f, y = 0.69f)
}

private data class NormalizedIconPoint(val x: Float, val y: Float)

private fun NormalizedIconPoint.toOffset(width: Float, height: Float) =
    Offset(width * x, height * y)
