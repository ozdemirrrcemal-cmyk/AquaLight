package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry

@Composable
internal fun CalibrationStepControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val submitName = {
        focusManager.clearFocus()
        if (!state.isLoading && !state.isBusy && state.displayName.isNotBlank()) {
            onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        }
    }
    val submitMeasurement = {
        focusManager.clearFocus()
        if (!state.isLoading && !state.isBusy && state.measuredMl.isNotBlank()) {
            onAction(DeviceDosingCalibrationAction.SaveMeasurement)
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CALIBRATION_CONTROL_GAP)
    ) {
        when (state.step) {
            DeviceDosingCalibrationStep.NAME ->
                CalibrationNameControls(state, colors, onAction, submitName)
            DeviceDosingCalibrationStep.PRIME -> CalibrationPrimeControls(state, colors, onAction)
            DeviceDosingCalibrationStep.CALIBRATION_RUN ->
                CalibrationRunControls(state, colors, onAction)
            DeviceDosingCalibrationStep.MEASUREMENT ->
                CalibrationMeasurementControls(state, colors, onAction, submitMeasurement)
            DeviceDosingCalibrationStep.VERIFICATION ->
                CalibrationVerificationControls(state, colors, onAction)
            DeviceDosingCalibrationStep.CONFIRMATION ->
                CalibrationConfirmationControls(state, onAction)
        }
    }
}

@Composable
private fun CalibrationNameControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit,
    onSubmit: () -> Unit
) {
    CalibrationTextField(
        model = CalibrationTextFieldModel(
            value = state.displayName,
            placeholder = stringResource(R.string.device_dosing_calibration_name_placeholder),
            enabled = !state.isBusy,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
        ),
        colors = colors,
        onValueChange = { value ->
            onAction(DeviceDosingCalibrationAction.DisplayNameChanged(value))
        }
    )
    AquaGuidedFlowButton(
        text = calibrationActionText(state, R.string.device_dosing_calibration_continue),
        onClick = onSubmit,
        enabled = !state.isLoading && !state.isBusy && state.displayName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CalibrationPrimeControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit
) {
    PressAndHoldPrimeButton(
        pressed = state.isPumpActive,
        enabled = !state.isLoading && !state.isBusy,
        colors = colors,
        onAction = onAction
    )
    AquaGuidedFlowButton(
        text = stringResource(R.string.device_dosing_calibration_tubing_ready),
        onClick = { onAction(DeviceDosingCalibrationAction.PrimeContinue) },
        enabled = !state.isLoading && !state.isBusy,
        secondary = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CalibrationRunControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit
) {
    if (state.isBusy && state.remainingMs > 0L) CountdownMetric(state.remainingMs, colors)
    AquaGuidedFlowButton(
        text = calibrationActionText(state, R.string.device_dosing_calibration_start_collection),
        onClick = { onAction(DeviceDosingCalibrationAction.StartCalibration) },
        enabled = !state.isLoading && !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CalibrationMeasurementControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit,
    onSubmit: () -> Unit
) {
    CalibrationTextField(
        model = CalibrationTextFieldModel(
            value = state.measuredMl,
            placeholder = stringResource(R.string.device_dosing_calibration_measurement_placeholder),
            suffix = stringResource(R.string.device_dosing_calibration_ml_unit),
            enabled = !state.isBusy,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        ),
        colors = colors,
        onValueChange = { value ->
            onAction(DeviceDosingCalibrationAction.MeasuredMlChanged(value))
        }
    )
    AquaGuidedFlowButton(
        text = calibrationActionText(state, R.string.device_dosing_calibration_save_measurement),
        onClick = onSubmit,
        enabled = !state.isLoading && !state.isBusy && state.measuredMl.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CalibrationVerificationControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit
) {
    if (state.isBusy && state.remainingMs > 0L) CountdownMetric(state.remainingMs, colors)
    AquaGuidedFlowButton(
        text = calibrationActionText(state, R.string.device_dosing_calibration_dispense_test),
        onClick = { onAction(DeviceDosingCalibrationAction.StartVerification) },
        enabled = !state.isLoading && !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CalibrationConfirmationControls(
    state: DeviceDosingCalibrationUiState,
    onAction: (DeviceDosingCalibrationAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CALIBRATION_CONFIRMATION_GAP)
    ) {
        AquaGuidedFlowButton(
            text = stringResource(R.string.device_dosing_calibration_no_retry),
            onClick = { onAction(DeviceDosingCalibrationAction.RejectVerification) },
            enabled = !state.isLoading && !state.isBusy,
            secondary = true,
            singleLineCompact = true,
            modifier = Modifier
                .weight(1f)
                .height(AquaGuidedFlowGeometry.buttonMinHeight)
        )
        AquaGuidedFlowButton(
            text = calibrationActionText(state, R.string.device_dosing_calibration_yes_confirm),
            onClick = { onAction(DeviceDosingCalibrationAction.AcceptVerification) },
            enabled = !state.isLoading && !state.isBusy,
            singleLineCompact = true,
            modifier = Modifier
                .weight(1f)
                .height(AquaGuidedFlowGeometry.buttonMinHeight)
        )
    }
}
