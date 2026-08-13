package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpHeadUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpSection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.exactDosingPumpCountOrNull

@Composable
internal fun CalibrationPump(
    pumpCount: Int,
    channelNumber: Int,
    active: Boolean
) {
    val exactCount = exactDosingPumpCountOrNull(pumpCount) ?: return
    val pumpHeads = remember(exactCount, channelNumber, active) {
        List(exactCount) { index ->
            DosingPumpHeadUiState(
                channelNumber = index + 1,
                visualState = when {
                    index + 1 != channelNumber -> DosingPumpVisualState.IDLE
                    active -> DosingPumpVisualState.RUNNING
                    else -> DosingPumpVisualState.SELECTED
                }
            )
        }
    }
    DosingPumpSection(
        pumpCount = exactCount,
        pumpHeads = pumpHeads,
        onPumpClick = null
    )
}

@Composable
internal fun CalibrationProgress(
    currentStep: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CALIBRATION_PROGRESS_SEGMENT_GAP)
    ) {
        DeviceDosingCalibrationStep.entries.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(CALIBRATION_PROGRESS_SEGMENT_HEIGHT)
                    .clip(CircleShape)
                    .background(if (index <= currentStep.ordinal) colors.accent else colors.outline)
            )
        }
    }
}

@Composable
internal fun CalibrationIllustrationPanel(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors
) {
    AquaGuidedFlowSurface(modifier = Modifier.fillMaxWidth()) {
        Crossfade(
            targetState = state.step,
            animationSpec = tween(durationMillis = ILLUSTRATION_TRANSITION_MILLIS),
            modifier = Modifier
                .fillMaxWidth()
                .height(CALIBRATION_ILLUSTRATION_HEIGHT),
            label = "dosing-calibration-step-transition"
        ) { illustrationStep ->
            DosingCalibrationIllustration(
                step = illustrationStep,
                colors = colors,
                active = state.isPumpActive,
                operationDurationMillis = state.illustrationOperationDurationMillis(),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun CalibrationStepCopy(state: DeviceDosingCalibrationUiState) {
    val colors = com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(verticalArrangement = Arrangement.spacedBy(CALIBRATION_COPY_GAP)) {
        BasicText(text = stringResource(state.step.titleRes), style = typography.title)
        BasicText(text = stringResource(state.step.descriptionRes), style = typography.body)
    }
}

@Composable
internal fun CalibrationErrorMessage(
    error: DeviceDosingCalibrationError,
    colors: AquaGuidedFlowColors
) {
    val typography = aquaGuidedFlowTypography(colors)
    BasicText(
        text = stringResource(error.messageRes),
        style = typography.body.copy(color = colors.danger)
    )
}

@Composable
internal fun CalibrationProgressLabel(
    step: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors
) {
    val typography = aquaGuidedFlowTypography(colors)
    Column(verticalArrangement = Arrangement.spacedBy(CALIBRATION_PROGRESS_CONTENT_GAP)) {
        BasicText(
            text = stringResource(
                R.string.device_dosing_calibration_step_progress,
                step.ordinal + 1,
                DeviceDosingCalibrationStep.entries.size
            ),
            style = typography.eyebrow
        )
        CalibrationProgress(currentStep = step, colors = colors)
    }
}
