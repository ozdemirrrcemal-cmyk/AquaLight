package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography

@Composable
internal fun QuickSetupProgress(state: DeviceLightQuickSetupUiState) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val fraction = state.stepNumber.toFloat() / state.totalSteps.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_progress_label),
                style = typography.eyebrow
            )
            BasicText(
                text = stringResource(
                    R.string.device_light_quick_setup_progress_value,
                    state.stepNumber,
                    state.totalSteps
                ),
                style = typography.body
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(DeviceLightQuickSetupGeometry.progressHeight)
                .clip(DeviceLightQuickSetupGeometry.progressRadius)
                .background(colors.outline.copy(alpha = DeviceLightQuickSetupAlpha.progressTrack))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(DeviceLightQuickSetupGeometry.progressHeight)
                    .clip(DeviceLightQuickSetupGeometry.progressRadius)
                    .background(colors.accent)
            )
        }
    }
}

@Composable
internal fun QuickSetupHeading(title: String, description: String) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = title, style = typography.title)
        BasicText(text = description, style = typography.body)
    }
}

@Composable
internal fun QuickSetupNumberField(
    value: String,
    placeholder: String,
    suffix: String,
    onValueChange: (String) -> Unit
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val focusManager = LocalFocusManager.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        AquaGuidedFlowGeometry.controlRadius
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.inputHeight)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(horizontal = DeviceLightQuickSetupGeometry.inputHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isBlank()) {
                BasicText(text = placeholder, style = typography.body)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = typography.label,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        BasicText(text = suffix, style = typography.label.copy(color = colors.textSecondary))
    }
}

@Composable
internal fun QuickSetupTimeControl(
    minuteOfDay: Int,
    onChanged: (Int) -> Unit
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_time_minus),
                onClick = { onChanged((minuteOfDay - TIME_STEP).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f),
                secondary = true,
                singleLineCompact = true
            )
            BasicText(
                text = minuteOfDay.toClockText(),
                style = typography.metric,
                modifier = Modifier.weight(1.3f)
            )
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_time_plus),
                onClick = { onChanged((minuteOfDay + TIME_STEP).coerceAtMost(LAST_TIME_STEP)) },
                modifier = Modifier.weight(1f),
                secondary = true,
                singleLineCompact = true
            )
        }
    }
}

@Composable
internal fun QuickSetupSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    AquaGuidedFlowSurface(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(text = title, style = typography.label)
                BasicText(text = summary, style = typography.body)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(DeviceLightQuickSetupGeometry.switchWidth)
                    .height(DeviceLightQuickSetupGeometry.switchHeight)
                    .clip(DeviceLightQuickSetupGeometry.switchShape)
                    .background(if (checked) colors.accent else colors.secondaryButton)
                    .padding(DeviceLightQuickSetupGeometry.switchInset)
            ) {
                Box(
                    Modifier
                        .size(DeviceLightQuickSetupGeometry.switchThumbSize)
                        .offset(
                            x = if (checked) {
                                DeviceLightQuickSetupGeometry.switchWidth -
                                    DeviceLightQuickSetupGeometry.switchThumbSize -
                                    DeviceLightQuickSetupGeometry.switchInset * 2
                            } else {
                                0.dp
                            }
                        )
                        .clip(CircleShape)
                        .background(if (checked) colors.onAccent else colors.textSecondary)
                )
            }
        }
    }
}

@Composable
internal fun QuickSetupErrorBanner(reason: com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.danger.copy(alpha = DeviceLightQuickSetupAlpha.dangerSurface))
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.danger, shape)
            .padding(DeviceLightQuickSetupGeometry.errorPadding)
    ) {
        BasicText(
            text = stringResource(reason.messageResource()),
            style = typography.body.copy(color = colors.textPrimary)
        )
    }
}

@Composable
internal fun QuickSetupFooter(
    showBack: Boolean,
    primaryText: String,
    primaryEnabled: Boolean = true,
    onBack: () -> Unit,
    onPrimary: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DeviceLightQuickSetupGeometry.actionBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.footerGap)
    ) {
        if (showBack) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
                secondary = true,
                singleLineCompact = true
            )
        }
        AquaGuidedFlowButton(
            text = primaryText,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(if (showBack) 1.6f else 1f),
            singleLineCompact = true
        )
    }
}

@Composable
internal fun QuickSetupInfoCard(text: String) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        BasicText(
            text = text,
            style = typography.body.copy(
                color = colors.textSecondary,
                textAlign = TextAlign.Start
            )
        )
    }
}


private const val TIME_STEP = 5
private const val LAST_TIME_STEP = 23 * 60 + 55
