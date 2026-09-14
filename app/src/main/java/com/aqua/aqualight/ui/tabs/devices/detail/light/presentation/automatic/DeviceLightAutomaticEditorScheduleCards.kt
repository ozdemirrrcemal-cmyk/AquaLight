package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightAutomaticEditorTimeRow(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticEditorGeometry.timeCardGap)
    ) {
        EditorTimeCard(
            content = EditorTimeContent(
                label = stringResource(R.string.device_light_auto_editor_start_time),
                summary = stringResource(R.string.device_light_auto_editor_start_time_summary),
                timeMs = state.draft.startTimeMs,
                accent = visuals.colors.red,
                onClick = actions.onStartTimeClick
            ),
            enabled = state.contentEnabled,
            visuals = visuals,
            modifier = Modifier.weight(TIME_CARD_WEIGHT)
        )
        EditorTimeCard(
            content = EditorTimeContent(
                label = stringResource(R.string.device_light_auto_editor_end_time),
                summary = stringResource(R.string.device_light_auto_editor_end_time_summary),
                timeMs = state.draft.endTimeMs,
                accent = visuals.colors.blue,
                onClick = actions.onEndTimeClick
            ),
            enabled = state.contentEnabled,
            visuals = visuals,
            modifier = Modifier.weight(TIME_CARD_WEIGHT)
        )
    }
}

@Composable
private fun EditorTimeCard(
    content: EditorTimeContent,
    enabled: Boolean,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier,
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Row(
            modifier = Modifier.height(DeviceLightAutomaticEditorGeometry.timeCardHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutomaticClockIcon(
                color = content.accent,
                modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.sectionIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.sectionIconGap))
            Column(Modifier.weight(TIME_COPY_WEIGHT)) {
                BasicText(
                    text = content.label,
                    style = visuals.typography.title.copy(color = visuals.colors.card.primaryText),
                    maxLines = SINGLE_LINE
                )
                BasicText(
                    text = content.summary,
                    style = visuals.typography.micro.copy(color = visuals.colors.card.secondaryText),
                    maxLines = SINGLE_LINE
                )
            }
            Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.timeCardGap))
            TimeValueButton(content.timeMs, enabled, content.onClick, visuals)
        }
    }
}

@Composable
private fun TimeValueButton(
    timeMs: Long?,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val alpha = if (enabled) ENABLED_ALPHA else DeviceLightAutomaticEditorAlpha.disabled
    Row(
        modifier = Modifier
            .height(DeviceLightAutomaticEditorGeometry.timeValueHeight)
            .clip(DeviceLightAutomaticEditorGeometry.timeValueShape)
            .border(
                DeviceLightAutomaticEditorGeometry.actionBorderWidth,
                visuals.colors.card.mediaOutline.copy(alpha = alpha),
                DeviceLightAutomaticEditorGeometry.timeValueShape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DeviceLightAutomaticEditorGeometry.timeCardGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = timeMs?.let { value -> automaticEditorTimeText(value) }
                ?: stringResource(R.string.device_light_auto_editor_time_placeholder),
            style = visuals.typography.body.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            ),
            maxLines = SINGLE_LINE
        )
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.timeCardGap))
        DownChevron(
            color = visuals.colors.card.secondaryText.copy(alpha = alpha),
            modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.timeChevronSize)
        )
    }
}

@Composable
internal fun DeviceLightAutomaticEditorRampCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(
            DeviceLightAutomaticEditorGeometry.cardContentGap
        )) {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_ramp),
                subtitle = stringResource(R.string.device_light_auto_editor_ramp_summary),
                visuals = visuals,
                icon = { color ->
                    AutomaticRampIcon(
                        color = color,
                        modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.sectionIconSize)
                    )
                }
            )
            RampButtons(state, actions, visuals)
            RampInformation(state, visuals)
        }
    }
}

@Composable
private fun RampButtons(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticEditorGeometry.rampButtonGap)
    ) {
        state.source?.policy?.rampDurationsMs.orEmpty().forEach { duration ->
            EditorSelectionButton(
                label = stringResource(
                    R.string.device_light_auto_minutes,
                    duration.toAutomaticMinutes()
                ),
                selected = state.draft.rampDurationMs == duration,
                enabled = state.contentEnabled && state.draft.rampFits(duration),
                onClick = { actions.onRampClick(duration) },
                modifier = Modifier
                    .weight(RAMP_BUTTON_WEIGHT)
                    .height(DeviceLightAutomaticEditorGeometry.rampButtonHeight),
                visuals = visuals
            )
        }
    }
}

@Composable
private fun RampInformation(
    state: DeviceLightAutomaticProgramEditorUiState,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val selectedRamp = state.draft.rampDurationMs
    val invalid = selectedRamp != null && !state.draft.rampFits(selectedRamp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.informationIconSize),
            colorFilter = ColorFilter.tint(
                if (invalid) visuals.colors.card.warning else visuals.colors.card.secondaryText
            )
        )
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.informationGap))
        BasicText(
            text = stringResource(
                if (invalid) R.string.device_light_auto_editor_ramp_invalid
                else R.string.device_light_auto_editor_ramp_information
            ),
            style = visuals.typography.micro.copy(
                color = if (invalid) {
                    visuals.colors.card.warning
                } else {
                    visuals.colors.card.secondaryText
                }
            )
        )
    }
}

@Composable
private fun DownChevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(
            color = color,
            start = Offset(size.width * CHEVRON_LEFT_X, size.height * CHEVRON_TOP_Y),
            end = Offset(size.width * CHEVRON_CENTER_X, size.height * CHEVRON_BOTTOM_Y),
            strokeWidth = DeviceLightAutomaticEditorGeometry.actionBorderWidth.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * CHEVRON_CENTER_X, size.height * CHEVRON_BOTTOM_Y),
            end = Offset(size.width * CHEVRON_RIGHT_X, size.height * CHEVRON_TOP_Y),
            strokeWidth = DeviceLightAutomaticEditorGeometry.actionBorderWidth.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun automaticEditorTimeText(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    return stringResource(
        R.string.device_light_auto_time_format,
        (totalMinutes / MINUTES_PER_HOUR).toInt(),
        (totalMinutes % MINUTES_PER_HOUR).toInt()
    )
}

private fun DeviceLightAutomaticEditorDraft.rampFits(rampDurationMs: Long): Boolean {
    val start = startTimeMs
    val end = endTimeMs
    return when {
        start == null || end == null -> rampDurationMs == NO_RAMP_DURATION
        start == end -> false
        else -> {
            val duration = if (end > start) end - start else MILLIS_PER_DAY - start + end
            rampDurationMs * RAMP_EDGE_COUNT <= duration
        }
    }
}

private fun Long.toAutomaticMinutes(): Int = (this / MILLIS_PER_MINUTE).toInt()

private data class EditorTimeContent(
    val label: String,
    val summary: String,
    val timeMs: Long?,
    val accent: Color,
    val onClick: () -> Unit
)

private const val TIME_CARD_WEIGHT = 1f
private const val TIME_COPY_WEIGHT = 1f
private const val RAMP_BUTTON_WEIGHT = 1f
private const val SINGLE_LINE = 1
private const val ENABLED_ALPHA = 1f
private const val NO_RAMP_DURATION = 0L
private const val RAMP_EDGE_COUNT = 2L
private const val CHEVRON_LEFT_X = 0.18f
private const val CHEVRON_CENTER_X = 0.50f
private const val CHEVRON_RIGHT_X = 0.82f
private const val CHEVRON_TOP_Y = 0.32f
private const val CHEVRON_BOTTOM_Y = 0.68f
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_DAY = 86_400_000L
