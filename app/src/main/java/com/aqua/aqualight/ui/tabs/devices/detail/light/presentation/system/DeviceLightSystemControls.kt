@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import kotlin.math.roundToInt

@Composable
internal fun DeviceLightTemperatureControlRow(
    @StringRes labelRes: Int,
    value: Int,
    minimum: Int,
    maximum: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    visuals: DeviceLightSystemVisuals
) {
    val label = stringResource(labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.controlRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = visuals.typography.body,
            modifier = Modifier.width(DeviceLightSystemGeometry.controlLabelWidth)
        )
        TemperatureStepButton(
            symbol = stringResource(R.string.device_light_system_minus_symbol),
            description = stringResource(
                R.string.device_light_system_decrease_temperature_description,
                label
            ),
            enabled = enabled && value > minimum,
            onClick = { onValueChanged(value - 1) },
            visuals = visuals
        )
        DeviceLightTemperatureSlider(
            value = value,
            minimum = minimum,
            maximum = maximum,
            enabled = enabled,
            description = label,
            stateText = stringResource(R.string.device_light_system_temperature_format, value),
            onValueChanged = onValueChanged,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        TemperatureStepButton(
            symbol = stringResource(R.string.device_light_system_plus_symbol),
            description = stringResource(
                R.string.device_light_system_increase_temperature_description,
                label
            ),
            enabled = enabled && value < maximum,
            onClick = { onValueChanged(value + 1) },
            visuals = visuals
        )
        BasicText(
            text = stringResource(R.string.device_light_system_temperature_format, value),
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(DeviceLightSystemGeometry.controlValueWidth)
        )
    }
}

@Composable
private fun TemperatureStepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightSystemVisuals
) {
    val alpha = if (enabled) 1f else DeviceLightSystemAlpha.disabled
    Box(
        modifier = Modifier
            .size(DeviceLightSystemGeometry.controlStepTouchSize)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(DeviceLightSystemGeometry.controlStepVisualSize)
                .clip(CircleShape)
                .border(
                    DeviceLightSystemGeometry.controlStepBorderWidth,
                    visuals.colors.card.mediaOutline.copy(alpha = alpha),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = symbol,
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.primaryText.copy(alpha = alpha),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun DeviceLightTemperatureSlider(
    value: Int,
    minimum: Int,
    maximum: Int,
    enabled: Boolean,
    description: String,
    stateText: String,
    onValueChanged: (Int) -> Unit,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    val currentOnValueChanged = rememberUpdatedState(onValueChanged)
    val state = TemperatureSliderState(
        value = value,
        minimum = minimum,
        maximum = maximum,
        enabled = enabled,
        description = description,
        stateText = stateText,
        onValueChanged = { selected -> currentOnValueChanged.value(selected) }
    )
    Canvas(
        modifier = modifier
            .height(DeviceLightSystemGeometry.controlSliderHeight)
            .temperatureSliderInput(state)
            .temperatureSliderSemantics(state)
    ) {
        val alpha = if (enabled) 1f else DeviceLightSystemAlpha.disabled
        val startX = DeviceLightSystemGeometry.controlSliderInset.toPx()
        val endX = size.width - startX
        val centerY = size.height / 2f
        val range = (maximum - minimum).coerceAtLeast(1)
        val fraction = (value - minimum).toFloat() / range
        val selectedX = startX + (endX - startX) * fraction.coerceIn(0f, 1f)
        drawLine(
            color = visuals.colors.card.mediaOutline.copy(
                alpha = alpha * DeviceLightSystemAlpha.track
            ),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = DeviceLightSystemGeometry.controlSliderTrackHeight.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = visuals.colors.action.copy(alpha = alpha),
            start = Offset(startX, centerY),
            end = Offset(selectedX, centerY),
            strokeWidth = DeviceLightSystemGeometry.controlSliderTrackHeight.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = visuals.colors.action.copy(alpha = alpha),
            radius = DeviceLightSystemGeometry.controlSliderThumbRadius.toPx(),
            center = Offset(selectedX, centerY)
        )
    }
}

private fun Modifier.temperatureSliderInput(state: TemperatureSliderState): Modifier =
    pointerInput(state.enabled, state.minimum, state.maximum) {
        if (state.enabled) {
            detectTapGestures { offset -> state.select(offset.x, size.width) }
        }
    }.pointerInput(state.enabled, state.minimum, state.maximum) {
        if (state.enabled) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> state.select(offset.x, size.width) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    state.select(change.position.x, size.width)
                }
            )
        }
    }

private fun Modifier.temperatureSliderSemantics(state: TemperatureSliderState): Modifier =
    semantics {
        contentDescription = state.description
        stateDescription = state.stateText
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = state.value.toFloat(),
            range = state.minimum.toFloat()..state.maximum.toFloat(),
            steps = (state.maximum - state.minimum - 1).coerceAtLeast(0)
        )
        if (state.enabled) {
            setProgress { requested ->
                state.onValueChanged(requested.roundToInt().coerceIn(state.minimum, state.maximum))
                true
            }
        } else {
            disabled()
        }
    }

@Immutable
private data class TemperatureSliderState(
    val value: Int,
    val minimum: Int,
    val maximum: Int,
    val enabled: Boolean,
    val description: String,
    val stateText: String,
    val onValueChanged: (Int) -> Unit
) {
    fun select(positionX: Float, width: Int) {
        if (width <= 0 || maximum <= minimum) return
        val fraction = positionX.coerceIn(0f, width.toFloat()) / width
        onValueChanged(minimum + (fraction * (maximum - minimum)).roundToInt())
    }
}
