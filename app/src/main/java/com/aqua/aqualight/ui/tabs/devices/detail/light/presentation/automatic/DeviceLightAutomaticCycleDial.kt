package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

private fun Modifier.automaticCycleDialInput(
    enabled: Boolean,
    timeStepMs: Long,
    currentState: () -> AutomaticCycleDialState,
    onTimeChanged: (DeviceLightAutomaticTimeField, Long) -> Unit
): Modifier = pointerInput(enabled, timeStepMs) {
    if (!enabled) return@pointerInput
    var selectedField: DeviceLightAutomaticTimeField? = null
    detectDragGestures(
        onDragStart = { position ->
            val state = currentState()
            val touchedTime = position.toAutomaticCycleTime(size.width, size.height, timeStepMs)
            selectedField = nearestAutomaticCycleField(
                touchedTime,
                state.startTimeMs,
                state.endTimeMs
            )
        },
        onDragEnd = { selectedField = null },
        onDragCancel = { selectedField = null },
        onDrag = { change, _ ->
            selectedField?.let { field ->
                change.consume()
                onTimeChanged(
                    field,
                    change.position.toAutomaticCycleTime(
                        size.width,
                        size.height,
                        timeStepMs
                    )
                )
            }
        }
    )
}

private fun Offset.toAutomaticCycleTime(width: Int, height: Int, stepMs: Long): Long {
    val center = Offset(width / HALF_DIVISOR, height / HALF_DIVISOR)
    val rawDegrees = Math.toDegrees(
        kotlin.math.atan2((y - center.y).toDouble(), (x - center.x).toDouble())
    ) + QUARTER_TURN_DEGREES
    val normalizedDegrees = (rawDegrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    return automaticCycleTimeFromClockDegrees(normalizedDegrees, stepMs)
}

internal fun automaticCycleTimeFromClockDegrees(degrees: Double, stepMs: Long): Long {
    val normalizedDegrees = (degrees % FULL_CIRCLE_DEGREES + FULL_CIRCLE_DEGREES) %
        FULL_CIRCLE_DEGREES
    val rawTime = normalizedDegrees / FULL_CIRCLE_DEGREES * MILLIS_PER_DAY
    return snapAutomaticCycleTime(rawTime.toLong(), stepMs)
}

internal fun snapAutomaticCycleTime(timeMs: Long, stepMs: Long): Long {
    require(stepMs > 0L)
    val snapped = ((timeMs + stepMs / HALF_LONG_DIVISOR) / stepMs) * stepMs
    return snapped % MILLIS_PER_DAY
}

internal fun nearestAutomaticCycleField(
    touchedTimeMs: Long,
    startTimeMs: Long?,
    endTimeMs: Long?
): DeviceLightAutomaticTimeField? = when {
    startTimeMs == null -> DeviceLightAutomaticTimeField.START
    endTimeMs == null -> DeviceLightAutomaticTimeField.END
    circularAutomaticCycleDistance(touchedTimeMs, startTimeMs) <=
        circularAutomaticCycleDistance(touchedTimeMs, endTimeMs) ->
        DeviceLightAutomaticTimeField.START
    else -> DeviceLightAutomaticTimeField.END
}

private fun circularAutomaticCycleDistance(first: Long, second: Long): Long {
    val direct = kotlin.math.abs(first - second)
    return minOf(direct, MILLIS_PER_DAY - direct)
}

internal data class AutomaticCycleDialState(
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val timeStepMs: Long,
    val enabled: Boolean
)

private const val HALF_DIVISOR = 2f
private const val HALF_LONG_DIVISOR = 2L
private const val QUARTER_TURN_DEGREES = 90.0
private const val FULL_CIRCLE_DEGREES = 360.0
private const val MILLIS_PER_DAY = 86_400_000L
private val CENTER_DIVIDER_WIDTH = 72.dp
private val CENTER_DIVIDER_HEIGHT = 1.dp
private val CENTER_DIVIDER_GAP = 5.dp
