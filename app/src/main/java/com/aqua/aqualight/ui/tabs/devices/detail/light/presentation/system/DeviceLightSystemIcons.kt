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
        val blade = min(size.width, size.height) * FanIconSpec.bladeScale
        repeat(FanIconSpec.bladeCount) { index ->
            rotate(index * FanIconSpec.rotationDegrees, center) {
                drawOval(
                    color = tint,
                    topLeft = Offset(
                        center.x - blade * FanIconSpec.horizontalOffset,
                        center.y - blade * FanIconSpec.verticalOffset
                    ),
                    size = Size(blade * FanIconSpec.widthScale, blade * 2f)
                )
            }
        }
        drawCircle(tint, radius = blade * FanIconSpec.hubScale, center = center)
        drawCircle(
            contrastingColor(tint),
            radius = blade * FanIconSpec.innerHubScale,
            center = center
        )
    }
}

@Composable
internal fun DeviceLightInfoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = max(1f, size.minDimension * InfoIconSpec.strokeScale)
        drawCircle(tint)
        drawCircle(Color.Transparent, radius = size.minDimension * InfoIconSpec.innerRadiusScale)
        drawLine(
            color = contrastingColor(tint),
            start = Offset(size.width / 2f, size.height * InfoIconSpec.lineStartY),
            end = Offset(size.width / 2f, size.height * InfoIconSpec.lineEndY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = contrastingColor(tint),
            radius = stroke / 2f,
            center = Offset(size.width / 2f, size.height * InfoIconSpec.dotCenterY)
        )
    }
}

@Composable
internal fun DeviceLightProtectionIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * ProtectionIconSpec.strokeScale
        val shield = Path().apply {
            moveTo(size.width * ProtectionIconSpec.centerX, size.height * ProtectionIconSpec.topY)
            lineTo(size.width * ProtectionIconSpec.upperRightX, size.height * ProtectionIconSpec.sideTopY)
            lineTo(size.width * ProtectionIconSpec.lowerRightX, size.height * ProtectionIconSpec.sideBottomY)
            quadraticTo(
                size.width * ProtectionIconSpec.curveRightX,
                size.height * ProtectionIconSpec.curveY,
                size.width * ProtectionIconSpec.centerX,
                size.height * ProtectionIconSpec.bottomY
            )
            quadraticTo(
                size.width * ProtectionIconSpec.curveLeftX,
                size.height * ProtectionIconSpec.curveY,
                size.width * ProtectionIconSpec.lowerLeftX,
                size.height * ProtectionIconSpec.sideBottomY
            )
            lineTo(size.width * ProtectionIconSpec.upperLeftX, size.height * ProtectionIconSpec.sideTopY)
            close()
        }
        drawPath(shield, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(
            color = tint,
            radius = size.minDimension * ProtectionIconSpec.thermometerBulbScale,
            center = Offset(
                size.width * ProtectionIconSpec.centerX,
                size.height * ProtectionIconSpec.thermometerBulbY
            ),
            style = Stroke(stroke)
        )
        drawLine(
            color = tint,
            start = Offset(
                size.width * ProtectionIconSpec.centerX,
                size.height * ProtectionIconSpec.thermometerTopY
            ),
            end = Offset(
                size.width * ProtectionIconSpec.centerX,
                size.height * ProtectionIconSpec.thermometerBottomY
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun DeviceLightLockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * LockIconSpec.strokeScale
        drawArc(
            color = tint,
            startAngle = LockIconSpec.arcDegrees,
            sweepAngle = LockIconSpec.arcDegrees,
            useCenter = false,
            topLeft = Offset(
                size.width * LockIconSpec.arcLeftX,
                size.height * LockIconSpec.arcTopY
            ),
            size = Size(
                size.width * LockIconSpec.arcWidth,
                size.height * LockIconSpec.arcHeight
            ),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(
                size.width * LockIconSpec.bodyLeftX,
                size.height * LockIconSpec.bodyTopY
            ),
            size = Size(
                size.width * LockIconSpec.bodyWidth,
                size.height * LockIconSpec.bodyHeight
            ),
            cornerRadius = CornerRadius(stroke)
        )
    }
}

private fun contrastingColor(tint: Color): Color =
    if (tint.luminance() > LuminanceSpec.contrastThreshold) Color.Black else Color.White

private fun Color.luminance(): Float =
    red * LuminanceSpec.redWeight +
        green * LuminanceSpec.greenWeight +
        blue * LuminanceSpec.blueWeight

private object FanIconSpec {
    const val bladeCount = 3
    const val rotationDegrees = 120f
    const val bladeScale = 0.26f
    const val horizontalOffset = 0.58f
    const val verticalOffset = 1.8f
    const val widthScale = 1.16f
    const val hubScale = 0.34f
    const val innerHubScale = 0.12f
}

private object InfoIconSpec {
    const val strokeScale = 0.08f
    const val innerRadiusScale = 0.38f
    const val lineStartY = 0.44f
    const val lineEndY = 0.73f
    const val dotCenterY = 0.28f
}

private object ProtectionIconSpec {
    const val strokeScale = 0.065f
    const val centerX = 0.50f
    const val topY = 0.06f
    const val upperRightX = 0.84f
    const val lowerRightX = 0.81f
    const val curveRightX = 0.76f
    const val curveLeftX = 0.24f
    const val lowerLeftX = 0.19f
    const val upperLeftX = 0.16f
    const val sideTopY = 0.19f
    const val sideBottomY = 0.62f
    const val curveY = 0.82f
    const val bottomY = 0.95f
    const val thermometerBulbScale = 0.12f
    const val thermometerBulbY = 0.61f
    const val thermometerTopY = 0.26f
    const val thermometerBottomY = 0.53f
}

private object LockIconSpec {
    const val strokeScale = 0.10f
    const val arcDegrees = 180f
    const val arcLeftX = 0.27f
    const val arcTopY = 0.08f
    const val arcWidth = 0.46f
    const val arcHeight = 0.58f
    const val bodyLeftX = 0.18f
    const val bodyTopY = 0.43f
    const val bodyWidth = 0.64f
    const val bodyHeight = 0.49f
}

private object LuminanceSpec {
    const val contrastThreshold = 0.5f
    const val redWeight = 0.299f
    const val greenWeight = 0.587f
    const val blueWeight = 0.114f
}
