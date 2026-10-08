package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

@Composable
internal fun DeviceLightAutomaticCycleDial(
    state: AutomaticCycleDialState,
    onTimeChanged: (DeviceLightAutomaticTimeField, Long) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    val currentOnTimeChanged = rememberUpdatedState(onTimeChanged)
    val currentState = rememberUpdatedState(state)
    val dragDescription = stringResource(R.string.device_light_auto_editor_cycle_drag_description)
    val timePlaceholder = stringResource(R.string.device_light_auto_editor_time_placeholder)
    val stateText = stringResource(
        R.string.device_light_auto_time_range_format,
        state.startTimeMs?.let { automaticEditorTimeText(it) } ?: timePlaceholder,
        state.endTimeMs?.let { automaticEditorTimeText(it) } ?: timePlaceholder
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .automaticCycleDialInput(
                    enabled = state.enabled,
                    timeStepMs = state.timeStepMs,
                    currentState = { currentState.value }
                ) { field, timeMs ->
                    currentOnTimeChanged.value(field, timeMs)
                }
                .semantics {
                    contentDescription = dragDescription
                    stateDescription = stateText
                    if (!state.enabled) disabled()
                }
        ) {
            drawAutomaticCycleDial(state, visuals)
        }
        AutomaticCycleDialCenter(state, visuals)
    }
}

@Composable
private fun AutomaticCycleDialCenter(
    state: AutomaticCycleDialState,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Column(
        modifier = Modifier.width(DeviceLightAutomaticEditorGeometry.dialCenterWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutomaticCycleDialValue(
            timeMs = state.startTimeMs,
            kind = AutomaticCycleEventKind.SUNRISE,
            accent = visuals.colors.card.warning,
            visuals = visuals
        )
        Spacer(Modifier.height(CENTER_DIVIDER_GAP))
        Box(
            Modifier
                .width(CENTER_DIVIDER_WIDTH)
                .height(CENTER_DIVIDER_HEIGHT)
                .background(visuals.colors.card.mediaOutline)
        )
        Spacer(Modifier.height(CENTER_DIVIDER_GAP))
        AutomaticCycleDialValue(
            timeMs = state.endTimeMs,
            kind = AutomaticCycleEventKind.SUNSET,
            accent = visuals.colors.shrimp,
            visuals = visuals
        )
    }
}

@Composable
private fun AutomaticCycleDialValue(
    timeMs: Long?,
    kind: AutomaticCycleEventKind,
    accent: Color,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AutomaticCycleEventIcon(
            kind = kind,
            color = accent,
            modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.dialEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.dialEventIconGap))
        BasicText(
            text = timeMs?.let { automaticEditorTimeText(it) }
                ?: stringResource(R.string.device_light_auto_editor_time_placeholder),
            style = visuals.typography.title.copy(
                color = visuals.colors.card.primaryText,
                textAlign = TextAlign.Center
            )
        )
    }
}

internal data class AutomaticCycleDialState(
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val timeStepMs: Long,
    val enabled: Boolean
)

private val CENTER_DIVIDER_WIDTH = 72.dp
private val CENTER_DIVIDER_HEIGHT = 1.dp
private val CENTER_DIVIDER_GAP = 5.dp
