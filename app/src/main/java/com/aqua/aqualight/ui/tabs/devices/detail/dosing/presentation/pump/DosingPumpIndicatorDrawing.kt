package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingPumpHeadVisualState
import com.aqua.aqualight.ui.common.devicevisual.dosing.drawDosingPumpIndicator

/** Feature facade retained for the architecture contract; drawing is owned by shared primitives. */
internal fun DrawScope.drawPumpIndicator(visualState: DosingPumpVisualState) {
    drawDosingPumpIndicator(visualState.toSharedVisualState())
}

internal fun DosingPumpVisualState.toSharedVisualState(): DosingPumpHeadVisualState = when (this) {
    DosingPumpVisualState.IDLE -> DosingPumpHeadVisualState.IDLE
    DosingPumpVisualState.SELECTED -> DosingPumpHeadVisualState.SELECTED
    DosingPumpVisualState.RUNNING -> DosingPumpHeadVisualState.RUNNING
    DosingPumpVisualState.ERROR -> DosingPumpHeadVisualState.ERROR
}
