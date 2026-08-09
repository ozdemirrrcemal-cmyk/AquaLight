package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

@Composable
internal fun DosingCalibrationStepControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)
    ) {
        when (state.step) {
            DosingCalibrationStep.NAME -> CalibrationNameControls(state, onAction)
            DosingCalibrationStep.PRIME -> CalibrationPrimeControls(state, onAction)
            DosingCalibrationStep.CALIBRATION_DOSE -> CalibrationDoseControls(state, onAction)
            DosingCalibrationStep.MEASURE -> CalibrationMeasurementControls(state, onAction)
            DosingCalibrationStep.VERIFY_DOSE -> CalibrationVerificationControls(state, onAction)
            DosingCalibrationStep.CONFIRM -> CalibrationConfirmationControls(state, onAction)
        }
    }
}

@Composable
private fun CalibrationNameControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationTextField(
        label = stringResource(R.string.device_dosing_calibration_liquid_name_label),
        value = state.displayNameInput,
        placeholder = stringResource(R.string.device_dosing_calibration_liquid_name_placeholder),
        keyboardType = KeyboardType.Text,
        enabled = !state.busy,
        onValueChange = { value -> onAction(DosingCalibrationAction.NameChanged(value)) }
    )
    CalibrationActionButton(
        text = stringResource(R.string.device_dosing_calibration_continue),
        enabled = !state.busy && state.displayNameInput.isNotBlank(),
        onClick = { onAction(DosingCalibrationAction.ContinueName) }
    )
}

@Composable
private fun CalibrationPrimeControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationHoldButton(
        text = if (state.primeActive) {
            stringResource(R.string.device_dosing_calibration_priming_active)
        } else {
            stringResource(R.string.device_dosing_calibration_hold_to_prime)
        },
        enabled = state.loaded && !state.busy,
        onPress = { onAction(DosingCalibrationAction.PrimePressed) },
        onRelease = { onAction(DosingCalibrationAction.PrimeReleased) }
    )
    CalibrationActionButton(
        text = stringResource(R.string.device_dosing_calibration_continue),
        enabled = !state.busy && !state.primeActive,
        onClick = { onAction(DosingCalibrationAction.ContinuePrime) }
    )
}

@Composable
private fun CalibrationDoseControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationActionButton(
        text = if (state.operation == DosingCalibrationOperation.CALIBRATION_DOSING) {
            stringResource(R.string.device_dosing_calibration_dose_running)
        } else {
            stringResource(R.string.device_dosing_calibration_start_dose)
        },
        enabled = !state.busy,
        onClick = { onAction(DosingCalibrationAction.StartCalibrationDose) }
    )
}

@Composable
private fun CalibrationMeasurementControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationTextField(
        label = stringResource(R.string.device_dosing_calibration_measured_volume_label),
        value = state.measuredMlInput,
        placeholder = stringResource(R.string.device_dosing_calibration_volume_placeholder),
        suffix = stringResource(R.string.device_dosing_calibration_ml_suffix),
        keyboardType = KeyboardType.Decimal,
        enabled = !state.busy,
        onValueChange = { value ->
            onAction(DosingCalibrationAction.MeasuredVolumeChanged(value))
        }
    )
    CalibrationActionButton(
        text = stringResource(R.string.device_dosing_calibration_calculate),
        enabled = !state.busy && state.measuredMlInput.isNotBlank(),
        onClick = { onAction(DosingCalibrationAction.SubmitMeasuredVolume) }
    )
}

@Composable
private fun CalibrationVerificationControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationTextField(
        label = stringResource(R.string.device_dosing_calibration_verification_volume_label),
        value = state.verificationMlInput,
        placeholder = stringResource(R.string.device_dosing_calibration_volume_placeholder),
        suffix = stringResource(R.string.device_dosing_calibration_ml_suffix),
        keyboardType = KeyboardType.Decimal,
        enabled = !state.busy,
        onValueChange = { value ->
            onAction(DosingCalibrationAction.VerificationVolumeChanged(value))
        }
    )
    CalibrationActionButton(
        text = if (state.operation == DosingCalibrationOperation.VERIFYING) {
            stringResource(R.string.device_dosing_calibration_verification_running)
        } else {
            stringResource(R.string.device_dosing_calibration_run_verification)
        },
        enabled = !state.busy && state.verificationMlInput.isNotBlank(),
        onClick = { onAction(DosingCalibrationAction.StartVerificationDose) }
    )
}

@Composable
private fun CalibrationConfirmationControls(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit
) {
    CalibrationActionButton(
        text = stringResource(R.string.device_dosing_calibration_confirm_action),
        enabled = !state.busy,
        onClick = { onAction(DosingCalibrationAction.ConfirmCalibration) }
    )
    CalibrationActionButton(
        text = stringResource(R.string.device_dosing_calibration_recalibrate_action),
        enabled = !state.busy,
        secondary = true,
        onClick = { onAction(DosingCalibrationAction.Recalibrate) }
    )
}

@Composable
private fun CalibrationTextField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FIELD_LABEL_GAP)
    ) {
        BasicText(text = label, style = typography.caption.copy(color = colors.secondaryText))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FIELD_SHAPE)
                .background(colors.mediaSurface)
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.mediaOutline,
                    shape = FIELD_SHAPE
                )
                .padding(horizontal = FIELD_HORIZONTAL_PADDING, vertical = FIELD_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                textStyle = typography.body.copy(color = colors.primaryText),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            BasicText(
                                text = placeholder,
                                style = typography.body.copy(color = colors.secondaryText)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            suffix?.let {
                BasicText(
                    text = it,
                    modifier = Modifier.padding(start = FIELD_SUFFIX_GAP),
                    style = typography.body.copy(color = colors.secondaryText)
                )
            }
        }
    }
}

@Composable
private fun CalibrationActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (secondary) colors.mediaSurface else colors.accent
    val foreground = if (secondary) colors.primaryText else colors.surface
    val outline = if (secondary) colors.mediaOutline else colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = BUTTON_MIN_WIDTH)
            .clip(BUTTON_SHAPE)
            .background(background.copy(alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = outline.copy(alpha = if (enabled) ENABLED_ALPHA else DISABLED_OUTLINE_ALPHA),
                shape = BUTTON_SHAPE
            )
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = BUTTON_VERTICAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = typography.body.copy(
                color = foreground.copy(alpha = if (enabled) ENABLED_ALPHA else DISABLED_TEXT_ALPHA)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CalibrationHoldButton(
    text: String,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BUTTON_SHAPE)
            .background(
                colors.accent.copy(alpha = if (enabled) HOLD_BUTTON_ALPHA else DISABLED_ALPHA)
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.accent.copy(alpha = if (enabled) ENABLED_ALPHA else DISABLED_OUTLINE_ALPHA),
                shape = BUTTON_SHAPE
            )
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                onRelease()
                            }
                        }
                    )
                }
            }
            .padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = HOLD_BUTTON_VERTICAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = typography.body.copy(
                color = colors.primaryText.copy(
                    alpha = if (enabled) ENABLED_ALPHA else DISABLED_TEXT_ALPHA
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.28f
private const val DISABLED_OUTLINE_ALPHA = 0.22f
private const val DISABLED_TEXT_ALPHA = 0.46f
private const val HOLD_BUTTON_ALPHA = 0.18f
private const val FIELD_CORNER_RADIUS_DP = 12
private const val BUTTON_CORNER_RADIUS_DP = 13
private const val FIELD_HORIZONTAL_PADDING_DP = 14
private const val FIELD_VERTICAL_PADDING_DP = 12
private const val BUTTON_HORIZONTAL_PADDING_DP = 16
private const val BUTTON_VERTICAL_PADDING_DP = 13
private const val HOLD_BUTTON_VERTICAL_PADDING_DP = 15
private const val FIELD_SUFFIX_GAP_DP = 8
private const val FIELD_LABEL_GAP_DP = 6
private const val CONTROL_GAP_DP = 12
private const val BUTTON_MIN_WIDTH_DP = 120
private val FIELD_SHAPE = RoundedCornerShape(FIELD_CORNER_RADIUS_DP.dp)
private val BUTTON_SHAPE = RoundedCornerShape(BUTTON_CORNER_RADIUS_DP.dp)
private val FIELD_HORIZONTAL_PADDING = FIELD_HORIZONTAL_PADDING_DP.dp
private val FIELD_VERTICAL_PADDING = FIELD_VERTICAL_PADDING_DP.dp
private val BUTTON_HORIZONTAL_PADDING = BUTTON_HORIZONTAL_PADDING_DP.dp
private val BUTTON_VERTICAL_PADDING = BUTTON_VERTICAL_PADDING_DP.dp
private val HOLD_BUTTON_VERTICAL_PADDING = HOLD_BUTTON_VERTICAL_PADDING_DP.dp
private val FIELD_SUFFIX_GAP = FIELD_SUFFIX_GAP_DP.dp
private val FIELD_LABEL_GAP = FIELD_LABEL_GAP_DP.dp
private val CONTROL_GAP = CONTROL_GAP_DP.dp
private val BUTTON_MIN_WIDTH = BUTTON_MIN_WIDTH_DP.dp
