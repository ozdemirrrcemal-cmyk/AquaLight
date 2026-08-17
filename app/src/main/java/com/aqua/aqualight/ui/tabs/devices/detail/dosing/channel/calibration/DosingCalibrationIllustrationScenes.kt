package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors

internal enum class CalibrationFluidDestination {
    WASTE,
    CYLINDER
}

internal data class CalibrationFluidAnimation(
    val flowPhase: Float,
    val active: Boolean,
    val fillProgress: Float
)

internal fun DrawScope.drawCalibrationScene(
    step: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors,
    animation: CalibrationFluidAnimation
) {
    when (step) {
        DeviceDosingCalibrationStep.NAME -> drawCalibrationNameScene(colors)
        DeviceDosingCalibrationStep.PRIME -> drawCalibrationFluidScene(
            colors = colors,
            animation = animation.copy(fillProgress = 0f),
            destination = CalibrationFluidDestination.WASTE
        )
        DeviceDosingCalibrationStep.CALIBRATION_RUN -> drawCalibrationFluidScene(
            colors = colors,
            animation = animation.copy(
                fillProgress = animation.fillProgress * CALIBRATION_FILL_RATIO
            ),
            destination = CalibrationFluidDestination.CYLINDER
        )
        DeviceDosingCalibrationStep.MEASUREMENT -> drawCalibrationMeasurementScene(colors)
        DeviceDosingCalibrationStep.VERIFICATION -> drawCalibrationFluidScene(
            colors = colors,
            animation = animation.copy(
                fillProgress = animation.fillProgress * VERIFICATION_FILL_RATIO
            ),
            destination = CalibrationFluidDestination.CYLINDER,
            showTarget = true
        )
        DeviceDosingCalibrationStep.CONFIRMATION -> drawCalibrationConfirmationScene(colors)
    }
}

private fun DrawScope.drawCalibrationNameScene(colors: AquaGuidedFlowColors) {
    val bottle = Rect(
        left = size.width * SCENE_LEFT,
        top = size.height * SCENE_TOP,
        right = size.width * (SCENE_LEFT + RESERVOIR_WIDTH),
        bottom = size.height * (SCENE_TOP + RESERVOIR_HEIGHT)
    )
    val tag = Rect(
        left = size.width * NAME_TAG_LEFT,
        top = size.height * NAME_TAG_TOP,
        right = size.width * NAME_TAG_RIGHT,
        bottom = size.height * NAME_TAG_BOTTOM
    )
    drawCalibrationReservoir(colors, bottle)
    drawCalibrationNameTag(colors, tag)
    drawLine(
        color = colors.accent,
        start = Offset(bottle.right, bottle.center.y),
        end = Offset(tag.left, tag.center.y),
        strokeWidth = size.minDimension * STROKE_NORMAL_RATIO,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawCalibrationFluidScene(
    colors: AquaGuidedFlowColors,
    animation: CalibrationFluidAnimation,
    destination: CalibrationFluidDestination,
    showTarget: Boolean = false
) {
    val bottle = fluidReservoirBounds()
    val pumpCenter = Offset(size.width * PUMP_CENTER_X, size.height * PUMP_CENTER_Y)
    val pumpRadius = size.minDimension * PUMP_RADIUS_RATIO
    val destinationBounds = fluidDestinationBounds(destination)
    val outletEnd = Offset(destinationBounds.center.x, destinationBounds.top)
    val inlet = inletPath(bottle, pumpCenter, pumpRadius)
    val outlet = outletPath(pumpCenter, pumpRadius, outletEnd)

    drawCalibrationTube(
        path = inlet,
        colors = colors,
        active = animation.active,
        flowPhase = animation.flowPhase
    )
    drawCalibrationTube(
        path = outlet,
        colors = colors,
        active = animation.active,
        flowPhase = animation.flowPhase
    )
    if (animation.active) {
        drawSolidCalibrationFlow(inlet, colors)
        drawSolidCalibrationFlow(outlet, colors)
    }
    drawCalibrationReservoir(colors, bottle)
    drawCalibrationPump(
        colors = colors,
        center = pumpCenter,
        radius = pumpRadius,
        rotorAngle = if (animation.active) {
            animation.flowPhase * FULL_ROTATION_DEGREES
        } else {
            CALIBRATION_RESTING_ROTOR_ANGLE
        },
        active = animation.active
    )
    drawCalibrationDestination(
        CalibrationDestinationRenderSpec(
            colors = colors,
            animation = animation,
            destination = destination,
            bounds = destinationBounds,
            outletEnd = outletEnd,
            showTarget = showTarget
        )
    )
}

private fun DrawScope.drawSolidCalibrationFlow(
    path: Path,
    colors: AquaGuidedFlowColors
) {
    val tubeWidth = size.minDimension * TUBE_WIDTH_RATIO
    drawPath(
        path = path,
        color = colors.accent.copy(alpha = TUBE_ACTIVE_ALPHA),
        style = Stroke(
            width = tubeWidth * TUBE_ACTIVE_SCALE,
            cap = StrokeCap.Round
        )
    )
}

private fun DrawScope.drawCalibrationMeasurementScene(colors: AquaGuidedFlowColors) {
    val cylinder = measurementCylinderBounds()
    val meniscusY = cylinder.bottom - cylinder.height * CALIBRATION_FILL_RATIO
    drawCalibrationCylinder(
        colors = colors,
        bounds = cylinder,
        liquidRatio = CALIBRATION_FILL_RATIO,
        showTarget = false
    )
    drawCalibrationEyeGuide(
        colors = colors,
        eyeCenter = Offset(size.width * EYE_CENTER_X, meniscusY),
        lineStartX = cylinder.right,
        meniscusY = meniscusY
    )
}

private fun DrawScope.drawCalibrationConfirmationScene(colors: AquaGuidedFlowColors) {
    val cylinder = measurementCylinderBounds()
    drawCalibrationCylinder(
        colors = colors,
        bounds = cylinder,
        liquidRatio = VERIFICATION_FILL_RATIO,
        showTarget = true
    )
    drawCalibrationToleranceBadge(
        colors = colors,
        center = Offset(size.width * BADGE_CENTER_X, size.height * BADGE_CENTER_Y)
    )
}

private fun DrawScope.fluidReservoirBounds() = Rect(
    left = size.width * SCENE_LEFT,
    top = size.height * (SCENE_BOTTOM - RESERVOIR_HEIGHT),
    right = size.width * (SCENE_LEFT + RESERVOIR_WIDTH),
    bottom = size.height * SCENE_BOTTOM
)

private fun DrawScope.fluidDestinationBounds(destination: CalibrationFluidDestination) = Rect(
    left = size.width * DESTINATION_LEFT,
    top = size.height * if (destination == CalibrationFluidDestination.WASTE) {
        WASTE_TOP
    } else {
        DESTINATION_TOP
    },
    right = size.width * DESTINATION_RIGHT,
    bottom = size.height * DESTINATION_BOTTOM
)

private fun DrawScope.measurementCylinderBounds() = Rect(
    left = size.width * MEASUREMENT_CYLINDER_LEFT,
    top = size.height * SCENE_TOP,
    right = size.width * MEASUREMENT_CYLINDER_RIGHT,
    bottom = size.height * SCENE_BOTTOM
)

private fun inletPath(
    bottle: Rect,
    pumpCenter: Offset,
    pumpRadius: Float
) = Path().apply {
    moveTo(bottle.center.x, bottle.bottom)
    lineTo(bottle.center.x, pumpCenter.y)
    lineTo(pumpCenter.x - pumpRadius, pumpCenter.y)
}

private fun outletPath(
    pumpCenter: Offset,
    pumpRadius: Float,
    outletEnd: Offset
) = Path().apply {
    val approachX = outletEnd.x - pumpRadius * 0.65f
    val entryY = outletEnd.y - pumpRadius * 0.30f
    moveTo(pumpCenter.x + pumpRadius, pumpCenter.y)
    lineTo(approachX, pumpCenter.y)
    lineTo(approachX, entryY)
    lineTo(outletEnd.x, entryY)
    lineTo(outletEnd.x, outletEnd.y)
}
