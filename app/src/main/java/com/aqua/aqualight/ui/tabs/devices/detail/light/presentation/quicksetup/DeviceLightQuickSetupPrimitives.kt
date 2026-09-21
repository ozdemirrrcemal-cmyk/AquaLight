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
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowAlpha
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
    Column(verticalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.labelGap)) {
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
                .height(AquaGuidedFlowGeometry.progressHeight)
                .clip(AquaGuidedFlowGeometry.progressRadius)
                .background(colors.outline.copy(alpha = AquaGuidedFlowAlpha.progressTrack))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(AquaGuidedFlowGeometry.progressHeight)
                    .clip(AquaGuidedFlowGeometry.progressRadius)
                    .background(colors.accent)
            )
        }
    }
}

@Composable
internal fun QuickSetupHeading(title: String, description: String) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(verticalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.labelGap)) {
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
            .height(AquaGuidedFlowGeometry.inputHeight)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(horizontal = AquaGuidedFlowGeometry.inputHorizontalPadding),
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
        Spacer(Modifier.width(AquaGuidedFlowGeometry.footerGap))
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
            horizontalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.denseSectionGap)
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
                modifier = Modifier.weight(AquaGuidedFlowGeometry.timeValueWeight)
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    AquaGuidedFlowGeometry.microTextGap
                )
            ) {
                BasicText(text = title, style = typography.label)
                BasicText(text = summary, style = typography.body)
            }
            Spacer(Modifier.width(AquaGuidedFlowGeometry.denseSectionGap))
            Box(
                modifier = Modifier
                    .width(AquaGuidedFlowGeometry.switchWidth)
                    .height(AquaGuidedFlowGeometry.switchHeight)
                    .clip(AquaGuidedFlowGeometry.switchShape)
                    .background(if (checked) colors.accent else colors.secondaryButton)
                    .padding(AquaGuidedFlowGeometry.switchInset)
            ) {
                Box(
                    Modifier
                        .size(AquaGuidedFlowGeometry.switchThumbSize)
                        .offset(
                            x = if (checked) {
                                AquaGuidedFlowGeometry.switchThumbTravel
                            } else {
                                AquaGuidedFlowGeometry.switchInactiveOffset
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
internal fun QuickSetupErrorBanner(
    reason: DeviceLightQuickSetupBlockReason
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        AquaGuidedFlowGeometry.controlRadius
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.danger.copy(alpha = AquaGuidedFlowAlpha.dangerSurface))
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.danger, shape)
            .padding(AquaGuidedFlowGeometry.errorPadding)
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
            .padding(bottom = AquaGuidedFlowGeometry.actionBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.footerGap)
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
            modifier = Modifier.weight(
                if (showBack) {
                    AquaGuidedFlowGeometry.footerPrimaryWeightWithBack
                } else {
                    1f
                }
            ),
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
private const val LAST_TIME_STEP = QUICK_SETUP_MINUTES_PER_DAY - TIME_STEP
