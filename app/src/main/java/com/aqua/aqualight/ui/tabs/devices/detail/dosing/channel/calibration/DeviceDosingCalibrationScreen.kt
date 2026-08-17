package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors

@Composable
internal fun DeviceDosingCalibrationScreen(
    state: DeviceDosingCalibrationUiState,
    onAction: (DeviceDosingCalibrationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaGuidedFlowColors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = AquaGuidedFlowGeometry.screenHorizontalPadding,
            top = CALIBRATION_SCREEN_TOP_PADDING,
            end = AquaGuidedFlowGeometry.screenHorizontalPadding,
            bottom = AquaGuidedFlowGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.sectionGap),
        userScrollEnabled = state.step != DeviceDosingCalibrationStep.PRIME || !state.isPumpActive
    ) {
        item(key = CALIBRATION_PUMP_KEY) {
            CalibrationPump(
                pumpCount = state.pumpCount,
                channelNumber = state.channelNumber,
                active = state.isPumpActive
            )
        }
        item(key = CALIBRATION_PROGRESS_KEY) {
            CalibrationProgressLabel(step = state.step, colors = colors)
        }
        item(key = CALIBRATION_ILLUSTRATION_KEY) {
            CalibrationIllustrationPanel(state = state, colors = colors)
        }
        item(key = CALIBRATION_COPY_KEY) {
            CalibrationStepCopy(state)
        }
        item(key = "$CALIBRATION_CONTROLS_KEY-${state.step.name}") {
            CalibrationStepControls(
                state = state,
                colors = colors,
                onAction = onAction
            )
        }
        state.error?.let { error ->
            item(key = CALIBRATION_ERROR_KEY) {
                CalibrationErrorMessage(error = error, colors = colors)
            }
        }
    }
}

private const val CALIBRATION_PUMP_KEY = "pump"
private const val CALIBRATION_PROGRESS_KEY = "progress"
private const val CALIBRATION_ILLUSTRATION_KEY = "illustration"
private const val CALIBRATION_COPY_KEY = "copy"
private const val CALIBRATION_CONTROLS_KEY = "controls"
private const val CALIBRATION_ERROR_KEY = "error"
