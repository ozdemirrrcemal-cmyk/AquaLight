package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawCoolingFanRotor(
    rotationPhase: Float,
    motionIntensity: Float
) {
    val rotor = CoolingFanRotorGeometry(
        center = Offset(size.width * FAN_CENTER_X, size.height * FAN_CENTER_Y),
        radiusX = size.width * FAN_RADIUS_X,
        radiusY = size.height * FAN_RADIUS_Y
    )
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                AquaCoolingDashboardPalette.insetSurface,
                Color.Black
            ),
            center = rotor.center,
            radius = rotor.radiusX
        ),
        topLeft = rotor.topLeft,
        size = rotor.size
    )

    val rotation = rotationPhase * FULL_CIRCLE_RADIANS
    repeat(FAN_BLADE_COUNT) { index ->
        val angle = rotation + index * FAN_BLADE_SPACING
        drawFanBlade(rotor, angle, motionIntensity)
    }
    drawFanHub(rotor)
}

private fun DrawScope.drawFanBlade(
    rotor: CoolingFanRotorGeometry,
    angle: Float,
    motionIntensity: Float
) {
    val rootLeading = rotor.point(angle - ROOT_LEADING_ANGLE, ROOT_LEADING_RADIUS)
    val controlLeading = rotor.point(angle - CONTROL_LEADING_ANGLE, CONTROL_LEADING_RADIUS)
    val controlTip = rotor.point(angle + CONTROL_TIP_ANGLE, CONTROL_TIP_RADIUS)
    val tipLeading = rotor.point(angle + TIP_LEADING_ANGLE, TIP_LEADING_RADIUS)
    val tipTrailing = rotor.point(angle + TIP_TRAILING_ANGLE, TIP_TRAILING_RADIUS)
    val controlTrailing = rotor.point(angle + CONTROL_TRAILING_ANGLE, CONTROL_TRAILING_RADIUS)
    val rootTrailing = rotor.point(angle + ROOT_TRAILING_ANGLE, ROOT_TRAILING_RADIUS)
    val blade = Path().apply {
        moveTo(rootLeading.x, rootLeading.y)
        cubicTo(
            controlLeading.x,
            controlLeading.y,
            controlTip.x,
            controlTip.y,
            tipLeading.x,
            tipLeading.y
        )
        cubicTo(
            tipTrailing.x,
            tipTrailing.y,
            controlTrailing.x,
            controlTrailing.y,
            rootTrailing.x,
            rootTrailing.y
        )
        close()
    }
    val bladeAlpha = BLADE_BASE_ALPHA - BLADE_MOTION_ALPHA_RANGE * motionIntensity
    drawPath(
        path = blade,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = bladeAlpha),
                AquaCoolingDashboardPalette.secondaryText.copy(alpha = BLADE_HIGHLIGHT_ALPHA),
                AquaCoolingDashboardPalette.insetSurface.copy(alpha = bladeAlpha)
            ),
            start = rootLeading,
            end = tipTrailing
        )
    )
    drawPath(
        path = blade,
        color = AquaCoolingDashboardPalette.primaryText.copy(alpha = BLADE_EDGE_ALPHA),
        style = Stroke(width = BLADE_EDGE_STROKE)
    )
}

private fun DrawScope.drawFanHub(rotor: CoolingFanRotorGeometry) {
    val hubSize = Size(
        width = size.width * FAN_HUB_RADIUS_X * DIAMETER_MULTIPLIER,
        height = size.height * FAN_HUB_RADIUS_Y * DIAMETER_MULTIPLIER
    )
    val hubTopLeft = Offset(
        x = rotor.center.x - hubSize.width / DIAMETER_MULTIPLIER,
        y = rotor.center.y - hubSize.height / DIAMETER_MULTIPLIER
    )
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                AquaCoolingDashboardPalette.primaryText.copy(alpha = HUB_LIGHT_ALPHA),
                AquaCoolingDashboardPalette.secondaryText.copy(alpha = HUB_MID_ALPHA),
                Color.Black.copy(alpha = HUB_SHADOW_ALPHA)
            ),
            start = hubTopLeft,
            end = Offset(hubTopLeft.x + hubSize.width, hubTopLeft.y + hubSize.height)
        ),
        topLeft = hubTopLeft,
        size = hubSize
    )
    drawOval(
        color = AquaCoolingDashboardPalette.primaryText.copy(alpha = HUB_EDGE_ALPHA),
        topLeft = hubTopLeft,
        size = hubSize,
        style = Stroke(width = HUB_EDGE_STROKE)
    )
}

private data class CoolingFanRotorGeometry(
    val center: Offset,
    val radiusX: Float,
    val radiusY: Float
) {
    val topLeft: Offset
        get() = Offset(center.x - radiusX, center.y - radiusY)
    val size: Size
        get() = Size(radiusX * DIAMETER_MULTIPLIER, radiusY * DIAMETER_MULTIPLIER)

    fun point(angle: Float, radius: Float): Offset = Offset(
        x = center.x + cos(angle.toDouble()).toFloat() * radiusX * radius,
        y = center.y + sin(angle.toDouble()).toFloat() * radiusY * radius
    )
}

private const val FAN_CENTER_X = 0.459f
private const val FAN_CENTER_Y = 0.296f
private const val FAN_RADIUS_X = 0.178f
private const val FAN_RADIUS_Y = 0.099f
private const val FAN_HUB_RADIUS_X = 0.052f
private const val FAN_HUB_RADIUS_Y = 0.029f
private const val FAN_BLADE_COUNT = 7
private const val FULL_CIRCLE_RADIANS = 6.2831855f
private const val FAN_BLADE_SPACING = FULL_CIRCLE_RADIANS / FAN_BLADE_COUNT
private const val ROOT_LEADING_ANGLE = 0.22f
private const val ROOT_LEADING_RADIUS = 0.18f
private const val CONTROL_LEADING_ANGLE = 0.10f
private const val CONTROL_LEADING_RADIUS = 0.42f
private const val CONTROL_TIP_ANGLE = 0.03f
private const val CONTROL_TIP_RADIUS = 0.76f
private const val TIP_LEADING_ANGLE = 0.18f
private const val TIP_LEADING_RADIUS = 0.88f
private const val TIP_TRAILING_ANGLE = 0.52f
private const val TIP_TRAILING_RADIUS = 0.76f
private const val CONTROL_TRAILING_ANGLE = 0.48f
private const val CONTROL_TRAILING_RADIUS = 0.44f
private const val ROOT_TRAILING_ANGLE = 0.25f
private const val ROOT_TRAILING_RADIUS = 0.20f
private const val BLADE_BASE_ALPHA = 0.92f
private const val BLADE_MOTION_ALPHA_RANGE = 0.16f
private const val BLADE_HIGHLIGHT_ALPHA = 0.34f
private const val BLADE_EDGE_ALPHA = 0.08f
private const val BLADE_EDGE_STROKE = 0.65f
private const val HUB_LIGHT_ALPHA = 0.82f
private const val HUB_MID_ALPHA = 0.64f
private const val HUB_SHADOW_ALPHA = 0.94f
private const val HUB_EDGE_ALPHA = 0.34f
private const val HUB_EDGE_STROKE = 0.75f
private const val DIAMETER_MULTIPLIER = 2f
