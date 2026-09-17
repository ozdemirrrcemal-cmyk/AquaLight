package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelStepButton
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelStepButtonState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticRampIcon

@Composable
internal fun DeviceLightAutomaticEditorRampCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val control = state.toRampControlState()
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(
                DeviceLightAutomaticEditorGeometry.cardContentGap
            )
        ) {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_ramp),
                subtitle = stringResource(R.string.device_light_auto_editor_ramp_compact_summary),
                visuals = visuals,
                icon = { color ->
                    AutomaticRampIcon(
                        color = color,
                        modifier = Modifier.size(
                            DeviceLightAutomaticEditorGeometry.sectionIconSize
                        )
                    )
                }
            )
            RampControl(control, actions.onRampClick, visuals)
            if (control.invalid) {
                BasicText(
                    text = stringResource(R.string.device_light_auto_editor_ramp_invalid),
                    style = visuals.typography.micro.copy(color = visuals.colors.card.danger)
                )
            }
        }
    }
}

@Composable
private fun RampControl(
    state: RampControlState,
    onDurationChanged: (Long) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val previous = state.selectedDurationMs?.let { current ->
        previousRampDuration(current, state.validDurationsMs)
    }
    val next = nextRampDuration(state.selectedDurationMs, state.validDurationsMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticEditorGeometry.rampControlHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RampStepButton(
            increase = false,
            enabled = state.enabled && previous != null,
            onClick = { previous?.let(onDurationChanged) },
            visuals = visuals
        )
        RampSliderValue(
            state = state,
            onDurationChanged = onDurationChanged,
            visuals = visuals,
            modifier = Modifier.weight(RAMP_SLIDER_WEIGHT)
        )
        RampStepButton(
            increase = true,
            enabled = state.enabled && next != null,
            onClick = { next?.let(onDurationChanged) },
            visuals = visuals
        )
    }
}

@Composable
private fun RampSliderValue(
    state: RampControlState,
    onDurationChanged: (Long) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    val valueText = state.selectedDurationMs?.let { duration ->
        stringResource(R.string.device_light_auto_minutes, duration.toAutomaticMinutes())
    } ?: stringResource(R.string.device_light_auto_editor_time_placeholder)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        DeviceLightAutomaticRampSlider(
            state = AutomaticRampSliderState(
                durationsMs = state.durationsMs,
                selectedDurationMs = state.selectedDurationMs,
                enabled = state.enabled,
                stateText = valueText,
                accessibilityDescription = stringResource(
                    R.string.device_light_auto_editor_ramp_description
                )
            ),
            onDurationChanged = onDurationChanged,
            visuals = visuals,
            modifier = Modifier.fillMaxWidth()
        )
        BasicText(
            text = valueText,
            style = visuals.typography.title.copy(
                color = if (state.invalid) {
                    visuals.colors.card.danger
                } else {
                    visuals.colors.card.primaryText
                },
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.width(DeviceLightAutomaticEditorGeometry.rampValueWidth)
        )
    }
}

@Composable
private fun RampStepButton(
    increase: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    AquaLightChannelStepButton(
        state = AquaLightChannelStepButtonState(
            symbol = stringResource(
                if (increase) R.string.device_light_manual_plus_symbol
                else R.string.device_light_manual_minus_symbol
            ),
            contentDescription = stringResource(
                if (increase) R.string.device_light_auto_editor_increase_ramp_description
                else R.string.device_light_auto_editor_decrease_ramp_description
            ),
            enabled = enabled
        ),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = onClick
    )
}

private fun previousRampDuration(current: Long, validDurations: List<Long>): Long? =
    validDurations.lastOrNull { duration -> duration < current }

private fun nextRampDuration(current: Long?, validDurations: List<Long>): Long? =
    if (current == null) validDurations.firstOrNull()
    else validDurations.firstOrNull { duration -> duration > current }

internal fun DeviceLightAutomaticEditorDraft.rampFits(rampDurationMs: Long): Boolean {
    val start = startTimeMs
    val end = endTimeMs
    return when {
        start == null || end == null -> true
        start == end -> rampDurationMs == NO_RAMP_DURATION
        else -> occupiedAutomaticEditorDuration(start, end) >= rampDurationMs * RAMP_EDGE_COUNT
    }
}

private fun occupiedAutomaticEditorDuration(startTimeMs: Long, endTimeMs: Long): Long =
    if (endTimeMs > startTimeMs) endTimeMs - startTimeMs
    else MILLIS_PER_DAY - startTimeMs + endTimeMs

private fun Long.toAutomaticMinutes(): Int = (this / MILLIS_PER_MINUTE).toInt()

private fun DeviceLightAutomaticProgramEditorUiState.toRampControlState(): RampControlState {
    val durations = source?.policy?.rampDurationsMs.orEmpty()
    val selected = draft.rampDurationMs
    return RampControlState(
        durationsMs = durations,
        validDurationsMs = durations.filter(draft::rampFits),
        selectedDurationMs = selected,
        enabled = contentEnabled,
        invalid = selected != null && !draft.rampFits(selected)
    )
}

private data class RampControlState(
    val durationsMs: List<Long>,
    val validDurationsMs: List<Long>,
    val selectedDurationMs: Long?,
    val enabled: Boolean,
    val invalid: Boolean
)

private const val RAMP_SLIDER_WEIGHT = 1f
private const val NO_RAMP_DURATION = 0L
private const val RAMP_EDGE_COUNT = 2L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
