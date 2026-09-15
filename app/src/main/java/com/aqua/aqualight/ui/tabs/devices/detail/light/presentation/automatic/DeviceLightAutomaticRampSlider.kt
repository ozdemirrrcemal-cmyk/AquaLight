package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

@Composable
internal fun DeviceLightAutomaticRampSlider(
    state: AutomaticRampSliderState,
    onDurationChanged: (Long) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    val currentOnDurationChanged = rememberUpdatedState(onDurationChanged)
    val selectedIndex = state.durationsMs.indexOf(state.selectedDurationMs)
    val interaction = AutomaticRampSliderInteraction(
        durationsMs = state.durationsMs,
        enabled = state.enabled,
        onDurationChanged = { duration -> currentOnDurationChanged.value(duration) }
    )
    Canvas(
        modifier = modifier
            .height(DeviceLightAutomaticEditorGeometry.rampSliderTouchHeight)
            .automaticRampTapInput(interaction)
            .automaticRampDragInput(interaction)
            .semantics {
                contentDescription = state.accessibilityDescription
                stateDescription = state.stateText
                if (state.durationsMs.isNotEmpty()) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = selectedIndex.coerceAtLeast(FIRST_INDEX).toFloat(),
                        range = FIRST_INDEX.toFloat()..state.durationsMs.lastIndex.toFloat(),
                        steps = (state.durationsMs.size - RANGE_ENDPOINT_COUNT).coerceAtLeast(0)
                    )
                    if (state.enabled) {
                        setProgress { requested ->
                            val index = requested.roundToInt().coerceIn(
                                FIRST_INDEX,
                                state.durationsMs.lastIndex
                            )
                            interaction.selectIndex(index)
                            true
                        }
                    }
                }
                if (!state.enabled) disabled()
            }
    ) {
        drawAutomaticRampSlider(
            selectedIndex = selectedIndex,
            durationCount = state.durationsMs.size,
            enabled = state.enabled,
            visuals = visuals
        )
    }
}

private fun Modifier.automaticRampTapInput(
    interaction: AutomaticRampSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.durationsMs) {
    if (interaction.enabled) {
        detectTapGestures { offset -> interaction.updateFromPosition(offset.x, size.width) }
    }
}

private fun Modifier.automaticRampDragInput(
    interaction: AutomaticRampSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.durationsMs) {
    if (interaction.enabled) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> interaction.updateFromPosition(offset.x, size.width) },
            onHorizontalDrag = { change, _ ->
                change.consume()
                interaction.updateFromPosition(change.position.x, size.width)
            }
        )
    }
}

private fun DrawScope.drawAutomaticRampSlider(
    selectedIndex: Int,
    durationCount: Int,
    enabled: Boolean,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val alpha = if (enabled) ENABLED_ALPHA else DeviceLightAutomaticEditorAlpha.disabled
    val startX = DeviceLightAutomaticEditorGeometry.rampSliderThumbRadius.toPx()
    val endX = size.width - startX
    val centerY = size.height / HALF_DIVISOR
    val intervals = (durationCount - 1).coerceAtLeast(1)
    drawLine(
        color = visuals.colors.card.mediaOutline.copy(alpha = alpha),
        start = Offset(startX, centerY),
        end = Offset(endX, centerY),
        strokeWidth = DeviceLightAutomaticEditorGeometry.rampSliderTrackWidth.toPx(),
        cap = StrokeCap.Round
    )
    repeat(durationCount) { index ->
        val x = startX + (endX - startX) * index / intervals
        drawCircle(
            color = visuals.colors.card.secondaryText.copy(alpha = alpha),
            radius = DeviceLightAutomaticEditorGeometry.rampSliderStepRadius.toPx(),
            center = Offset(x, centerY)
        )
    }
    if (selectedIndex >= FIRST_INDEX) {
        val selectedX = startX + (endX - startX) * selectedIndex / intervals
        drawLine(
            color = visuals.colors.action.copy(alpha = alpha),
            start = Offset(startX, centerY),
            end = Offset(selectedX, centerY),
            strokeWidth = DeviceLightAutomaticEditorGeometry.rampSliderTrackWidth.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = visuals.colors.action.copy(alpha = alpha),
            radius = DeviceLightAutomaticEditorGeometry.rampSliderThumbRadius.toPx(),
            center = Offset(selectedX, centerY)
        )
    }
}

@Immutable
internal data class AutomaticRampSliderState(
    val durationsMs: List<Long>,
    val selectedDurationMs: Long?,
    val enabled: Boolean,
    val stateText: String,
    val accessibilityDescription: String
)

private data class AutomaticRampSliderInteraction(
    val durationsMs: List<Long>,
    val enabled: Boolean,
    val onDurationChanged: (Long) -> Unit
) {
    fun updateFromPosition(positionX: Float, width: Int) {
        if (durationsMs.isEmpty() || width <= 0) return
        val index = (
            positionX.coerceIn(0f, width.toFloat()) / width * durationsMs.lastIndex
            ).roundToInt()
        selectIndex(index)
    }

    fun selectIndex(index: Int) {
        durationsMs.getOrNull(index)?.let(onDurationChanged)
    }
}

private const val FIRST_INDEX = 0
private const val RANGE_ENDPOINT_COUNT = 2
private const val HALF_DIVISOR = 2f
private const val ENABLED_ALPHA = 1f
