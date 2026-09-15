@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun DeviceLightFanIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val blade = min(size.width, size.height) * 0.26f
        repeat(3) { index ->
            rotate(index * 120f, center) {
                drawOval(
                    color = tint,
                    topLeft = Offset(center.x - blade * 0.58f, center.y - blade * 1.8f),
                    size = Size(blade * 1.16f, blade * 2f)
                )
            }
        }
        drawCircle(tint, radius = blade * 0.34f, center = center)
        drawCircle(contrastingColor(tint), radius = blade * 0.12f, center = center)
    }
}

@Composable
internal fun DeviceLightInfoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = max(1f, size.minDimension * 0.08f)
        drawCircle(tint)
        drawCircle(Color.Transparent, radius = size.minDimension * 0.38f)
        drawLine(
            color = contrastingColor(tint),
            start = Offset(size.width / 2f, size.height * 0.44f),
            end = Offset(size.width / 2f, size.height * 0.73f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = contrastingColor(tint),
            radius = stroke / 2f,
            center = Offset(size.width / 2f, size.height * 0.28f)
        )
    }
}

@Composable
internal fun DeviceLightProtectionIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.065f
        val shield = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.06f)
            lineTo(size.width * 0.84f, size.height * 0.19f)
            lineTo(size.width * 0.81f, size.height * 0.62f)
            quadraticTo(
                size.width * 0.76f,
                size.height * 0.82f,
                size.width * 0.50f,
                size.height * 0.95f
            )
            quadraticTo(
                size.width * 0.24f,
                size.height * 0.82f,
                size.width * 0.19f,
                size.height * 0.62f
            )
            lineTo(size.width * 0.16f, size.height * 0.19f)
            close()
        }
        drawPath(shield, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.50f, size.height * 0.61f),
            style = Stroke(stroke)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.50f, size.height * 0.26f),
            end = Offset(size.width * 0.50f, size.height * 0.53f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun DeviceLightLockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.10f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
            size = Size(size.width * 0.46f, size.height * 0.58f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.18f, size.height * 0.43f),
            size = Size(size.width * 0.64f, size.height * 0.49f),
            cornerRadius = CornerRadius(stroke)
        )
    }
}

private fun contrastingColor(tint: Color): Color =
    if (tint.luminance() > 0.5f) Color.Black else Color.White

private fun Color.luminance(): Float = red * 0.299f + green * 0.587f + blue * 0.114f
