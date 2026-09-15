package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

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
internal fun DeviceLightAdaptationSlider(
    state: DeviceLightAdaptationSliderState,
    onValueChanged: (Int) -> Unit,
    visuals: DeviceLightAdaptationVisuals,
    modifier: Modifier = Modifier
) {
    val currentOnValueChanged = rememberUpdatedState(onValueChanged)
    val interaction = DeviceLightAdaptationSliderInteraction(
        minimum = state.minimum,
        maximum = state.maximum,
        step = state.step,
        enabled = state.enabled,
        onValueChanged = { value -> currentOnValueChanged.value(value) }
    )
    Canvas(
        modifier = modifier
            .height(DeviceLightAdaptationGeometry.sliderHeight)
            .adaptationSliderTapInput(interaction)
            .adaptationSliderDragInput(interaction)
            .adaptationSliderSemantics(state, interaction)
    ) {
        drawAdaptationSlider(state, visuals)
    }
}

private fun Modifier.adaptationSliderTapInput(
    interaction: DeviceLightAdaptationSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.minimum, interaction.maximum) {
    if (interaction.enabled) {
        detectTapGestures { offset -> interaction.update(offset.x, size.width) }
    }
}

private fun Modifier.adaptationSliderDragInput(
    interaction: DeviceLightAdaptationSliderInteraction
): Modifier = pointerInput(interaction.enabled, interaction.minimum, interaction.maximum) {
    if (interaction.enabled) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> interaction.update(offset.x, size.width) },
            onHorizontalDrag = { change, _ ->
                change.consume()
                interaction.update(change.position.x, size.width)
            }
        )
    }
}

private fun Modifier.adaptationSliderSemantics(
    state: DeviceLightAdaptationSliderState,
    interaction: DeviceLightAdaptationSliderInteraction
): Modifier = semantics {
    contentDescription = state.contentDescription
    stateDescription = state.stateDescription
    progressBarRangeInfo = ProgressBarRangeInfo(
        current = state.value.toFloat(),
        range = state.minimum.toFloat()..state.maximum.toFloat(),
        steps = ((state.maximum - state.minimum) / state.step - 1).coerceAtLeast(0)
    )
    if (state.enabled) {
        setProgress { requested ->
            interaction.select(requested.roundToInt())
            true
        }
    } else {
        disabled()
    }
}

private fun DrawScope.drawAdaptationSlider(
    state: DeviceLightAdaptationSliderState,
    visuals: DeviceLightAdaptationVisuals
) {
    val alpha = if (state.enabled) 1f else DeviceLightAdaptationAlpha.disabled
    val startX = DeviceLightAdaptationGeometry.sliderInset.toPx()
    val endX = size.width - startX
    val centerY = size.height / 2f
    val fraction = (state.value - state.minimum).toFloat() / (state.maximum - state.minimum)
    val selectedX = startX + (endX - startX) * fraction.coerceIn(0f, 1f)
    drawLine(
        color = visuals.colors.card.mediaOutline.copy(alpha = alpha),
        start = Offset(startX, centerY),
        end = Offset(endX, centerY),
        strokeWidth = DeviceLightAdaptationGeometry.sliderTrackHeight.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = visuals.colors.action.copy(alpha = alpha),
        start = Offset(startX, centerY),
        end = Offset(selectedX, centerY),
        strokeWidth = DeviceLightAdaptationGeometry.sliderTrackHeight.toPx(),
        cap = StrokeCap.Round
    )
    drawSliderTicks(
        state = state,
        coordinates = AdaptationSliderCoordinates(startX, endX, centerY),
        visuals = visuals,
        alpha = alpha
    )
    drawCircle(
        color = visuals.colors.action.copy(alpha = alpha),
        radius = DeviceLightAdaptationGeometry.sliderThumbRadius.toPx(),
        center = Offset(selectedX, centerY)
    )
}

private fun DrawScope.drawSliderTicks(
    state: DeviceLightAdaptationSliderState,
    coordinates: AdaptationSliderCoordinates,
    visuals: DeviceLightAdaptationVisuals,
    alpha: Float
) {
    val count = (state.maximum - state.minimum) / state.step
    repeat(count + 1) { index ->
        val x = coordinates.startX +
            (coordinates.endX - coordinates.startX) * index / count.coerceAtLeast(1)
        drawCircle(
            color = visuals.colors.card.secondaryText.copy(alpha = alpha),
            radius = DeviceLightAdaptationGeometry.sliderTickRadius.toPx(),
            center = Offset(x, coordinates.centerY)
        )
    }
}

@Immutable
internal data class DeviceLightAdaptationSliderState(
    val value: Int,
    val minimum: Int,
    val maximum: Int,
    val step: Int,
    val enabled: Boolean,
    val contentDescription: String,
    val stateDescription: String
)

private data class DeviceLightAdaptationSliderInteraction(
    val minimum: Int,
    val maximum: Int,
    val step: Int,
    val enabled: Boolean,
    val onValueChanged: (Int) -> Unit
) {
    fun update(positionX: Float, width: Int) {
        if (width <= 0) return
        val fraction = positionX.coerceIn(0f, width.toFloat()) / width
        val stepCount = (maximum - minimum) / step
        select(minimum + (fraction * stepCount).roundToInt() * step)
    }

    fun select(value: Int) {
        val clamped = value.coerceIn(minimum, maximum)
        val stepped = minimum + ((clamped - minimum + step / 2) / step) * step
        onValueChanged(stepped.coerceIn(minimum, maximum))
    }
}

private data class AdaptationSliderCoordinates(
    val startX: Float,
    val endX: Float,
    val centerY: Float
)
