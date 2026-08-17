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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import kotlin.math.ceil

internal data class CalibrationTextFieldModel(
    val value: String,
    val placeholder: String,
    val enabled: Boolean,
    val keyboardType: KeyboardType,
    val suffix: String = ""
)

@Composable
internal fun CalibrationTextField(
    model: CalibrationTextFieldModel,
    colors: AquaGuidedFlowColors,
    onValueChange: (String) -> Unit
) {
    val typography = aquaGuidedFlowTypography(colors)
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(
                horizontal = CALIBRATION_TEXT_FIELD_HORIZONTAL_PADDING,
                vertical = CALIBRATION_TEXT_FIELD_VERTICAL_PADDING
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (model.value.isEmpty()) {
                BasicText(text = model.placeholder, style = typography.body)
            }
            BasicTextField(
                value = model.value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = model.enabled,
                textStyle = typography.label,
                keyboardOptions = KeyboardOptions(keyboardType = model.keyboardType),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true
            )
        }
        if (model.suffix.isNotBlank()) {
            Spacer(Modifier.width(CALIBRATION_INLINE_GAP))
            BasicText(
                text = model.suffix,
                style = typography.label.copy(color = colors.textSecondary)
            )
        }
    }
}

@Composable
internal fun PressAndHoldPrimeButton(
    pressed: Boolean,
    enabled: Boolean,
    colors: AquaGuidedFlowColors,
    onAction: (DeviceDosingCalibrationAction) -> Unit
) {
    val description = stringResource(R.string.device_dosing_calibration_prime_accessibility)
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.buttonRadius)
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnAction = rememberUpdatedState(onAction)
    var gesturePressed by remember { mutableStateOf(false) }
    val visualPressed = gesturePressed || pressed
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaGuidedFlowGeometry.buttonMinHeight)
            .clip(shape)
            .background(primeButtonBackground(visualPressed, enabled, colors))
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (currentEnabled.value) {
                        gesturePressed = true
                        currentOnAction.value(DeviceDosingCalibrationAction.PrimePressed)
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            gesturePressed = false
                            currentOnAction.value(DeviceDosingCalibrationAction.PrimeReleased)
                        }
                    } else {
                        waitForUpOrCancellation()
                    }
                }
            }
            .padding(
                horizontal = CALIBRATION_PRIME_HORIZONTAL_PADDING,
                vertical = CALIBRATION_PRIME_VERTICAL_PADDING
            ),
        contentAlignment = Alignment.Center
    ) {
        PrimeButtonContent(visualPressed = visualPressed, colors = colors)
    }
}

@Composable
private fun PrimeButtonContent(
    visualPressed: Boolean,
    colors: AquaGuidedFlowColors
) {
    val typography = aquaGuidedFlowTypography(colors)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(CALIBRATION_PRIME_DOT_SIZE)
                .clip(CircleShape)
                .background(if (visualPressed) colors.onAccent else colors.accent)
        )
        Spacer(Modifier.width(CALIBRATION_INLINE_GAP))
        BasicText(
            text = stringResource(
                if (visualPressed) {
                    R.string.device_dosing_calibration_priming
                } else {
                    R.string.device_dosing_calibration_hold_to_prime
                }
            ),
            style = typography.button.copy(
                color = if (visualPressed) colors.onAccent else colors.onSecondaryButton
            )
        )
    }
}

private fun primeButtonBackground(
    pressed: Boolean,
    enabled: Boolean,
    colors: AquaGuidedFlowColors
) = when {
    pressed -> colors.accent
    !enabled -> colors.disabled
    else -> colors.secondaryButton
}

@Composable
internal fun CountdownMetric(remainingMs: Long, colors: AquaGuidedFlowColors) {
    val typography = aquaGuidedFlowTypography(colors)
    val seconds = ceil(remainingMs / CALIBRATION_MILLIS_PER_SECOND).toInt().coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CALIBRATION_COUNTDOWN_GAP)
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
internal fun calibrationActionText(
    state: DeviceDosingCalibrationUiState,
    @StringRes idleRes: Int
): String = stringResource(
    if (state.isBusy) R.string.device_dosing_calibration_working else idleRes
)
