package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

@Composable
internal fun AquaLightManualPercentSlider(
    state: AquaLightManualPercentSliderState,
    actions: AquaLightManualPercentSliderActions,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val value = state.percent.coerceIn(
        AquaLightManualControlSpec.minimumPercent,
        AquaLightManualControlSpec.maximumPercent
    )
    val currentOnValueChanged = rememberUpdatedState(actions.onValueChanged)
    val currentOnValueChangeFinished = rememberUpdatedState(actions.onValueChangeFinished)
    val interaction = ManualSliderInteraction(
        enabled = state.enabled,
        widthPx = widthPx,
        onValueChanged = { changed -> currentOnValueChanged.value(changed) },
        onValueChangeFinished = { currentOnValueChangeFinished.value() }
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaLightManualGeometry.channelSliderTouchHeight)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .manualSliderTapInput(interaction)
            .manualSliderDragInput(interaction)
            .semantics {
                contentDescription = state.accessibilityDescription
                stateDescription = state.stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = AquaLightManualControlSpec.minimumPercent.toFloat()..
                        AquaLightManualControlSpec.maximumPercent.toFloat(),
                    steps = AquaLightManualControlSpec.maximumPercent -
                        AquaLightManualControlSpec.minimumPercent - 1
                )
                if (state.enabled) {
                    setProgress { requested ->
                        interaction.onValueChanged(requested.roundToInt())
                        interaction.finishChange()
                        true
                    }
                } else {
                    disabled()
                }
            }
    ) {
        drawManualPercentSlider(value, state.channelColor, state.enabled)
    }
}

@Immutable
internal data class AquaLightManualPercentSliderState(
    val percent: Int,
    val enabled: Boolean,
    val channelColor: Color,
    val stateText: String,
    val accessibilityDescription: String
)

internal data class AquaLightManualPercentSliderActions(
    val onValueChanged: (Int) -> Unit,
    val onValueChangeFinished: () -> Unit
)

private fun Modifier.manualSliderTapInput(
    interaction: ManualSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.widthPx) {
    if (interaction.enabled) {
        detectTapGestures { offset ->
            interaction.updateFromPosition(offset.x)
            interaction.finishChange()
        }
    }
}

private fun Modifier.manualSliderDragInput(
    interaction: ManualSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.widthPx) {
    if (interaction.enabled) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> interaction.updateFromPosition(offset.x) },
            onDragEnd = interaction::finishChange,
            onDragCancel = interaction::finishChange,
            onHorizontalDrag = { change, _ -> interaction.updateFromPosition(change.position.x) }
        )
    }
}

private fun DrawScope.drawManualPercentSlider(
    percent: Int,
    channelColor: Color,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else AquaLightManualAlpha.disabledControl
    val centerY = size.height / 2f
    val startX = AquaLightManualGeometry.channelSliderThumbRadius.toPx()
    val endX = (size.width - startX).coerceAtLeast(startX)
    val fraction = percent.toFloat() / AquaLightManualControlSpec.maximumPercent
    val thumbX = startX + (endX - startX) * fraction
    val trackStroke = AquaLightManualGeometry.channelSliderTrackHeight.toPx()

    drawLine(
        color = channelColor.copy(alpha = AquaLightManualAlpha.channelTrack * alpha),
        start = Offset(startX, centerY),
        end = Offset(endX, centerY),
        strokeWidth = trackStroke,
        cap = StrokeCap.Round
    )
    if (percent > AquaLightManualControlSpec.minimumPercent) {
        drawLine(
            color = channelColor.copy(alpha = alpha),
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        color = channelColor.copy(alpha = alpha),
        radius = AquaLightManualGeometry.channelSliderThumbRadius.toPx(),
        center = Offset(thumbX, centerY)
    )
}

private data class ManualSliderInteraction(
    val enabled: Boolean,
    val widthPx: Float,
    val onValueChanged: (Int) -> Unit,
    val onValueChangeFinished: () -> Unit
) {
    fun updateFromPosition(positionX: Float) {
        if (widthPx <= 0f) return
        val value = (
            (positionX / widthPx).coerceIn(0f, 1f) *
                AquaLightManualControlSpec.maximumPercent
            ).roundToInt()
        onValueChanged(value)
    }

    fun finishChange() {
        onValueChangeFinished()
    }
}
