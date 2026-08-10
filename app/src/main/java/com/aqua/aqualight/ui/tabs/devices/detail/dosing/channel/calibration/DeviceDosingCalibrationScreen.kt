@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "TooManyFunctions"
)

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpHeadUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpSection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.exactDosingPumpCountOrNull
import kotlin.math.ceil

@Composable
internal fun DeviceDosingCalibrationScreen(
    state: DeviceDosingCalibrationUiState,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onPrimePressed: () -> Unit,
    onPrimeReleased: () -> Unit,
    onPrimeContinue: () -> Unit,
    onStartCalibration: () -> Unit,
    onMeasuredMlChange: (String) -> Unit,
    onSaveMeasurement: () -> Unit,
    onStartVerification: () -> Unit,
    onAcceptVerification: () -> Unit,
    onRejectVerification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val title = stringResource(state.step.titleRes)
    val description = stringResource(state.step.descriptionRes)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = AquaGuidedFlowGeometry.screenHorizontalPadding,
            top = 12.dp,
            end = AquaGuidedFlowGeometry.screenHorizontalPadding,
            bottom = AquaGuidedFlowGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.sectionGap)
    ) {
        item(key = "pump") {
            CalibrationPump(
                pumpCount = state.pumpCount,
                channelNumber = state.channelNumber,
                active = state.isPumpActive
            )
        }
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(
                    text = stringResource(
                        R.string.device_dosing_calibration_step_progress,
                        state.step.ordinal + 1,
                        DeviceDosingCalibrationStep.entries.size
                    ),
                    style = typography.eyebrow
                )
                CalibrationProgress(currentStep = state.step, colors = colors)
            }
        }
        item(key = "illustration") {
            AquaGuidedFlowSurface(modifier = Modifier.fillMaxWidth()) {
                DosingCalibrationIllustration(
                    step = state.step,
                    colors = colors,
                    active = state.isPumpActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(178.dp)
                )
            }
        }
        item(key = "copy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(text = title, style = typography.title)
                BasicText(text = description, style = typography.body)
            }
        }
        item(key = "controls-${state.step.name}") {
            CalibrationStepControls(
                state = state,
                colors = colors,
                onDisplayNameChange = onDisplayNameChange,
                onSaveDisplayName = onSaveDisplayName,
                onPrimePressed = onPrimePressed,
                onPrimeReleased = onPrimeReleased,
                onPrimeContinue = onPrimeContinue,
                onStartCalibration = onStartCalibration,
                onMeasuredMlChange = onMeasuredMlChange,
                onSaveMeasurement = onSaveMeasurement,
                onStartVerification = onStartVerification,
                onAcceptVerification = onAcceptVerification,
                onRejectVerification = onRejectVerification
            )
        }
        state.error?.let { error ->
            item(key = "error") {
                BasicText(
                    text = stringResource(error.messageRes),
                    style = typography.body.copy(color = colors.danger)
                )
            }
        }
    }
}

@Composable
private fun CalibrationPump(
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
private fun CalibrationProgress(
    currentStep: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DeviceDosingCalibrationStep.entries.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= currentStep.ordinal) colors.accent else colors.outline
                    )
            )
        }
    }
}

@Composable
private fun CalibrationStepControls(
    state: DeviceDosingCalibrationUiState,
    colors: AquaGuidedFlowColors,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onPrimePressed: () -> Unit,
    onPrimeReleased: () -> Unit,
    onPrimeContinue: () -> Unit,
    onStartCalibration: () -> Unit,
    onMeasuredMlChange: (String) -> Unit,
    onSaveMeasurement: () -> Unit,
    onStartVerification: () -> Unit,
    onAcceptVerification: () -> Unit,
    onRejectVerification: () -> Unit
) {
    val typography = aquaGuidedFlowTypography(colors)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.step) {
            DeviceDosingCalibrationStep.NAME -> {
                CalibrationTextField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChange,
                    placeholder = stringResource(
                        R.string.device_dosing_calibration_name_placeholder
                    ),
                    colors = colors,
                    enabled = !state.isBusy,
                    keyboardType = KeyboardType.Text
                )
                AquaGuidedFlowButton(
                    text = actionText(state, R.string.device_dosing_calibration_continue),
                    onClick = onSaveDisplayName,
                    enabled = !state.isLoading && !state.isBusy &&
                        state.displayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DeviceDosingCalibrationStep.PRIME -> {
                PressAndHoldPrimeButton(
                    pressed = state.isPumpActive,
                    enabled = !state.isLoading && !state.isBusy,
                    colors = colors,
                    onPressStart = onPrimePressed,
                    onPressEnd = onPrimeReleased
                )
                AquaGuidedFlowButton(
                    text = stringResource(R.string.device_dosing_calibration_tubing_ready),
                    onClick = onPrimeContinue,
                    enabled = !state.isLoading && !state.isBusy,
                    secondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DeviceDosingCalibrationStep.CALIBRATION_RUN -> {
                if (state.isBusy) CountdownMetric(state.remainingMs, colors)
                AquaGuidedFlowButton(
                    text = actionText(
                        state,
                        R.string.device_dosing_calibration_start_collection
                    ),
                    onClick = onStartCalibration,
                    enabled = !state.isLoading && !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DeviceDosingCalibrationStep.MEASUREMENT -> {
                CalibrationTextField(
                    value = state.measuredMl,
                    onValueChange = onMeasuredMlChange,
                    placeholder = stringResource(
                        R.string.device_dosing_calibration_measurement_placeholder
                    ),
                    suffix = stringResource(R.string.device_dosing_calibration_ml_unit),
                    colors = colors,
                    enabled = !state.isBusy,
                    keyboardType = KeyboardType.Decimal
                )
                AquaGuidedFlowButton(
                    text = actionText(
                        state,
                        R.string.device_dosing_calibration_save_measurement
                    ),
                    onClick = onSaveMeasurement,
                    enabled = !state.isLoading && !state.isBusy &&
                        state.measuredMl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DeviceDosingCalibrationStep.VERIFICATION -> {
                if (state.isBusy) CountdownMetric(state.remainingMs, colors)
                AquaGuidedFlowButton(
                    text = actionText(
                        state,
                        R.string.device_dosing_calibration_dispense_test
                    ),
                    onClick = onStartVerification,
                    enabled = !state.isLoading && !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DeviceDosingCalibrationStep.CONFIRMATION -> {
                state.candidateDoseMsPerMl?.let { candidate ->
                    BasicText(
                        text = stringResource(
                            R.string.device_dosing_calibration_candidate_rate,
                            candidate
                        ),
                        style = typography.body
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AquaGuidedFlowButton(
                        text = stringResource(R.string.device_dosing_calibration_no_retry),
                        onClick = onRejectVerification,
                        enabled = !state.isLoading && !state.isBusy,
                        secondary = true,
                        modifier = Modifier.weight(1f)
                    )
                    AquaGuidedFlowButton(
                        text = actionText(
                            state,
                            R.string.device_dosing_calibration_yes_confirm
                        ),
                        onClick = onAcceptVerification,
                        enabled = !state.isLoading && !state.isBusy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (state.isLoading) {
            BasicText(
                text = stringResource(R.string.device_dosing_calibration_loading),
                style = typography.body,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CalibrationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    colors: AquaGuidedFlowColors,
    enabled: Boolean,
    keyboardType: KeyboardType,
    suffix: String = ""
) {
    val typography = aquaGuidedFlowTypography(colors)
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                BasicText(text = placeholder, style = typography.body)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = typography.label,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true
            )
        }
        if (suffix.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            BasicText(text = suffix, style = typography.label.copy(color = colors.textSecondary))
        }
    }
}

@Composable
private fun PressAndHoldPrimeButton(
    pressed: Boolean,
    enabled: Boolean,
    colors: AquaGuidedFlowColors,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val typography = aquaGuidedFlowTypography(colors)
    val description = stringResource(R.string.device_dosing_calibration_prime_accessibility)
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.buttonRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaGuidedFlowGeometry.buttonMinHeight)
            .clip(shape)
            .background(
                when {
                    !enabled -> colors.disabled
                    pressed -> colors.accent
                    else -> colors.secondaryButton
                }
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressStart()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onPressEnd()
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (pressed) colors.onAccent else colors.accent)
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = stringResource(
                    if (pressed) {
                        R.string.device_dosing_calibration_priming
                    } else {
                        R.string.device_dosing_calibration_hold_to_prime
                    }
                ),
                style = typography.button.copy(
                    color = if (pressed) colors.onAccent else colors.onSecondaryButton
                )
            )
        }
    }
}

@Composable
private fun CountdownMetric(remainingMs: Long, colors: AquaGuidedFlowColors) {
    val typography = aquaGuidedFlowTypography(colors)
    val seconds = ceil(remainingMs / 1_000.0).toInt().coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BasicText(
            text = stringResource(R.string.device_dosing_calibration_seconds, seconds),
            style = typography.metric,
            modifier = Modifier.fillMaxWidth()
        )
        BasicText(
            text = stringResource(R.string.device_dosing_calibration_pump_running),
            style = typography.body.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun actionText(state: DeviceDosingCalibrationUiState, @StringRes idleRes: Int): String =
    stringResource(
        if (state.isBusy) R.string.device_dosing_calibration_working else idleRes
    )

private val DeviceDosingCalibrationStep.titleRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME -> R.string.device_dosing_calibration_name_title
        DeviceDosingCalibrationStep.PRIME -> R.string.device_dosing_calibration_prime_title
        DeviceDosingCalibrationStep.CALIBRATION_RUN ->
            R.string.device_dosing_calibration_run_title
        DeviceDosingCalibrationStep.MEASUREMENT ->
            R.string.device_dosing_calibration_measure_title
        DeviceDosingCalibrationStep.VERIFICATION ->
            R.string.device_dosing_calibration_verify_title
        DeviceDosingCalibrationStep.CONFIRMATION ->
            R.string.device_dosing_calibration_confirm_title
    }

private val DeviceDosingCalibrationStep.descriptionRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME -> R.string.device_dosing_calibration_name_description
        DeviceDosingCalibrationStep.PRIME -> R.string.device_dosing_calibration_prime_description
        DeviceDosingCalibrationStep.CALIBRATION_RUN ->
            R.string.device_dosing_calibration_run_description
        DeviceDosingCalibrationStep.MEASUREMENT ->
            R.string.device_dosing_calibration_measure_description
        DeviceDosingCalibrationStep.VERIFICATION ->
            R.string.device_dosing_calibration_verify_description
        DeviceDosingCalibrationStep.CONFIRMATION ->
            R.string.device_dosing_calibration_confirm_description
    }

private val DeviceDosingCalibrationError.messageRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationError.INVALID_NAME ->
            R.string.device_dosing_calibration_invalid_name
        DeviceDosingCalibrationError.INVALID_MEASUREMENT ->
            R.string.device_dosing_calibration_invalid_measurement
        DeviceDosingCalibrationError.CONNECTION ->
            R.string.device_dosing_calibration_connection_error
        DeviceDosingCalibrationError.UNAVAILABLE ->
            R.string.device_dosing_calibration_unavailable
    }
