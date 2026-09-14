package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightAutomaticCycleCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val sceneColor = state.draft.channels.cycleSceneColor(visuals)
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticEditorGeometry.cycleCardHeight),
        contentPadding = PaddingValues()
    ) {
        CycleAmbientBackdrop(sceneColor, visuals)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DeviceLightAutomaticEditorGeometry.cardPadding),
            verticalArrangement = Arrangement.spacedBy(
                DeviceLightAutomaticEditorGeometry.cardContentGap
            )
        ) {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_cycle),
                subtitle = stringResource(R.string.device_light_auto_editor_cycle_drag_hint),
                visuals = visuals,
                icon = { color ->
                    AutomaticClockIcon(
                        color = color,
                        modifier = Modifier.size(
                            DeviceLightAutomaticEditorGeometry.sectionIconSize
                        )
                    )
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CYCLE_DIAL_WEIGHT),
                contentAlignment = Alignment.Center
            ) {
                DeviceLightAutomaticCycleDial(
                    state = AutomaticCycleDialState(
                        startTimeMs = state.draft.startTimeMs,
                        endTimeMs = state.draft.endTimeMs,
                        timeStepMs = state.source?.policy?.timeStepMs ?: DEFAULT_TIME_STEP_MS,
                        enabled = state.contentEnabled
                    ),
                    onTimeChanged = actions.onTimeChanged,
                    visuals = visuals,
                    modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.cycleDialSize)
                )
            }
            CycleTimeFields(state, actions, visuals)
        }
    }
}

@Composable
private fun CycleAmbientBackdrop(
    sceneColor: Color,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Canvas(Modifier.fillMaxSize()) {
        val glowRadius = size.minDimension * AMBIENT_GLOW_RADIUS_MULTIPLIER
        val glows = listOf(
            visuals.colors.red to Offset(0f, 0f),
            visuals.colors.green to Offset(size.width * HALF_FRACTION, 0f),
            visuals.colors.blue to Offset(size.width, 0f),
            sceneColor to center
        )
        glows.forEach { (color, glowCenter) ->
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = DeviceLightAutomaticEditorAlpha.ambientGlow),
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = glowRadius
                )
            )
        }
    }
}

@Composable
private fun CycleTimeFields(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticScheduleActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            DeviceLightAutomaticEditorGeometry.cycleTimeFieldGap
        )
    ) {
        CycleTimeField(
            content = AutomaticCycleTimeFieldContent(
                label = stringResource(R.string.device_light_auto_editor_start_time_compact),
                timeMs = state.draft.startTimeMs,
                kind = AutomaticCycleEventKind.SUNRISE,
                accent = visuals.colors.card.warning,
                accessibilityLabel = stringResource(
                    R.string.device_light_auto_editor_start_time_picker
                )
            ),
            enabled = state.contentEnabled,
            onClick = actions.onStartTimeClick,
            visuals = visuals,
            modifier = Modifier.weight(TIME_FIELD_WEIGHT)
        )
        CycleTimeField(
            content = AutomaticCycleTimeFieldContent(
                label = stringResource(R.string.device_light_auto_editor_end_time_compact),
                timeMs = state.draft.endTimeMs,
                kind = AutomaticCycleEventKind.SUNSET,
                accent = visuals.colors.shrimp,
                accessibilityLabel = stringResource(
                    R.string.device_light_auto_editor_end_time_picker
                )
            ),
            enabled = state.contentEnabled,
            onClick = actions.onEndTimeClick,
            visuals = visuals,
            modifier = Modifier.weight(TIME_FIELD_WEIGHT)
        )
    }
}

@Composable
private fun CycleTimeField(
    content: AutomaticCycleTimeFieldContent,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    val alpha = if (enabled) ENABLED_ALPHA else DeviceLightAutomaticEditorAlpha.disabled
    val timeText = content.timeMs?.let { time -> automaticEditorTimeText(time) }
        ?: stringResource(R.string.device_light_auto_editor_time_placeholder)
    val timeDescription = stringResource(
        R.string.device_light_auto_editor_time_field_description,
        content.accessibilityLabel,
        timeText
    )
    Row(
        modifier = modifier
            .height(DeviceLightAutomaticEditorGeometry.cycleTimeFieldHeight)
            .clip(DeviceLightAutomaticEditorGeometry.cycleTimeFieldShape)
            .border(
                width = DeviceLightAutomaticEditorGeometry.actionBorderWidth,
                color = visuals.colors.card.mediaOutline.copy(alpha = alpha),
                shape = DeviceLightAutomaticEditorGeometry.cycleTimeFieldShape
            )
            .clearAndSetSemantics {
                contentDescription = timeDescription
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = TIME_FIELD_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticCycleEventIcon(
            kind = content.kind,
            color = content.accent.copy(alpha = alpha),
            modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.dialEventIconSize)
        )
        Spacer(Modifier.size(DeviceLightAutomaticEditorGeometry.dialEventIconGap))
        BasicText(
            text = content.label,
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha)
            ),
            modifier = Modifier.weight(TIME_LABEL_WEIGHT)
        )
        BasicText(
            text = timeText,
            style = visuals.typography.body.copy(
                color = content.accent.copy(alpha = alpha)
            )
        )
        Spacer(Modifier.size(TIME_FIELD_CHEVRON_GAP))
        CycleDownChevron(
            color = visuals.colors.card.secondaryText.copy(alpha = alpha),
            modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.cycleTimeChevronSize)
        )
    }
}

@Composable
private fun CycleDownChevron(color: Color, modifier: Modifier) {
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

private fun Map<DeviceLightAutomaticChannel, Int>.cycleSceneColor(
    visuals: DeviceLightAutomaticEditorVisuals
): Color {
    if (values.all { percent -> percent == ZERO_PERCENT }) return visuals.colors.blue
    val red = this[DeviceLightAutomaticChannel.RED].orZeroPercent()
    val green = this[DeviceLightAutomaticChannel.GREEN].orZeroPercent()
    val blue = this[DeviceLightAutomaticChannel.BLUE].orZeroPercent()
    val white = this[DeviceLightAutomaticChannel.WHITE].orZeroPercent()
    return Color(
        red = mixCycleChannel(red, white),
        green = mixCycleChannel(green, white),
        blue = mixCycleChannel(blue, white)
    )
}

private fun Int?.orZeroPercent(): Float =
    (this ?: ZERO_PERCENT).toFloat() / MAX_PERCENT

private fun mixCycleChannel(channel: Float, white: Float): Float =
    (channel * DeviceLightAutomaticDialSpec.colorMixWeight +
        white * DeviceLightAutomaticDialSpec.whiteMixWeight).coerceIn(
        DeviceLightAutomaticDialSpec.minimumOverlayChannel,
        DeviceLightAutomaticDialSpec.maximumOverlayChannel
    )

private data class AutomaticCycleTimeFieldContent(
    val label: String,
    val timeMs: Long?,
    val kind: AutomaticCycleEventKind,
    val accent: Color,
    val accessibilityLabel: String
)

private const val DEFAULT_TIME_STEP_MS = 60_000L
private const val ZERO_PERCENT = 0
private const val MAX_PERCENT = 100f
private const val ENABLED_ALPHA = 1f
private const val HALF_FRACTION = 0.5f
private const val AMBIENT_GLOW_RADIUS_MULTIPLIER = 1.45f
private const val CYCLE_DIAL_WEIGHT = 1f
private const val TIME_FIELD_WEIGHT = 1f
private const val TIME_LABEL_WEIGHT = 1f
private val TIME_FIELD_HORIZONTAL_PADDING = 9.dp
private val TIME_FIELD_CHEVRON_GAP = 5.dp
private const val CHEVRON_LEFT_X = 0.18f
private const val CHEVRON_CENTER_X = 0.50f
private const val CHEVRON_RIGHT_X = 0.82f
private const val CHEVRON_TOP_Y = 0.32f
private const val CHEVRON_BOTTOM_Y = 0.68f
