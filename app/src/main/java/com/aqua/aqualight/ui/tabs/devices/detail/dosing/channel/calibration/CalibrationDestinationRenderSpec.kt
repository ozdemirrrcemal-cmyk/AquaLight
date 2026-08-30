package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors

internal data class CalibrationDestinationRenderSpec(
    val colors: AquaGuidedFlowColors,
    val animation: CalibrationFluidAnimation,
    val destination: CalibrationFluidDestination,
    val bounds: Rect,
    val outletEnd: Offset,
    val showTarget: Boolean
)

internal fun DrawScope.drawCalibrationDestination(spec: CalibrationDestinationRenderSpec) {
    when (spec.destination) {
        CalibrationFluidDestination.WASTE -> drawCalibrationWasteCup(
            colors = spec.colors,
            bounds = spec.bounds,
            active = spec.animation.active,
            flowPhase = spec.animation.flowPhase,
            outletEnd = spec.outletEnd
        )
        CalibrationFluidDestination.CYLINDER -> {
            drawCalibrationCylinder(
                colors = spec.colors,
                bounds = spec.bounds,
                liquidRatio = spec.animation.fillProgress,
                showTarget = spec.showTarget
            )
            if (spec.animation.active) {
                drawCalibrationDrops(
                    colors = spec.colors,
                    flowPhase = spec.animation.flowPhase,
                    outletEnd = spec.outletEnd,
                    destinationY = spec.bounds.top
                )
            }
        }
    }
}
