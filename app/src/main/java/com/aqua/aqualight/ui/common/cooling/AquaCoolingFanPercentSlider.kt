package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.roundToInt

/** Shared Cooling fan-percent slider using the dashboard's manual-control interaction contract. */
internal data class AquaCoolingFanPercentSliderState(
    val percent: Int,
    val enabled: Boolean,
    val stepPercent: Int = 1
)

@Composable
internal fun AquaCoolingFanPercentSlider(
    state: AquaCoolingFanPercentSliderState,
    colors: AquaDeviceCardColors,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(state.stepPercent > 0)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val range = FanPercentSliderRange(stepPercent = state.stepPercent)
    val clamped = range.snap(state.percent)
    val stateText = stringResource(R.string.device_cooling_percent_value_format, clamped)
    val interaction = FanPercentSliderInteraction(
        enabled = state.enabled,
        widthPx = widthPx,
        range = range,
        onValueChanged = onValueChanged
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.sliderTouchHeight)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .fanSliderTapInput(interaction)
            .fanSliderDragInput(interaction)
            .fanSliderSemantics(interaction, clamped, stateText)
    ) {
        drawFanPercentSlider(clamped = clamped, range = range, colors = colors)
    }
}

private fun Modifier.fanSliderTapInput(
    interaction: FanPercentSliderInteraction
): Modifier = pointerInput(
    interaction.enabled,
    interaction.widthPx,
    interaction.range,
    interaction.onValueChanged
) {
    if (interaction.enabled) {
        detectTapGestures { offset -> interaction.updateFromPosition(offset.x) }
    }
}

private fun Modifier.fanSliderDragInput(
    interaction: FanPercentSliderInteraction
): Modifier = pointerInput(
    interaction.enabled,
    interaction.widthPx,
    interaction.range,
    interaction.onValueChanged
) {
    if (interaction.enabled) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> interaction.updateFromPosition(offset.x) },
            onHorizontalDrag = { change, _ -> interaction.updateFromPosition(change.position.x) }
        )
    }
}

private fun Modifier.fanSliderSemantics(
    interaction: FanPercentSliderInteraction,
    clamped: Int,
    stateText: String
): Modifier = semantics {
    stateDescription = stateText
    progressBarRangeInfo = ProgressBarRangeInfo(
        current = clamped.toFloat(),
        range = interaction.range.minimum.toFloat()..interaction.range.maximum.toFloat(),
        steps = interaction.range.semanticsSteps
    )
    if (interaction.enabled) {
        setProgress { requested ->
            interaction.onValueChanged(interaction.range.snap(requested.roundToInt()))
            true
        }
    } else {
        disabled()
    }
}

private fun DrawScope.drawFanPercentSlider(
    clamped: Int,
    range: FanPercentSliderRange,
    colors: AquaDeviceCardColors
) {
    val centerY = size.height / 2f
    val startX = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx()
    val endX = (size.width - startX).coerceAtLeast(startX)
    val trackWidth = (endX - startX).coerceAtLeast(1f)
    val normalized = (clamped - range.minimum).toFloat() / range.span
    val thumbX = startX + trackWidth * normalized
    val trackStroke = AquaCoolingDashboardGeometry.sliderTrackHeight.toPx()

    drawLine(
        color = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.trackInactive),
        start = Offset(startX, centerY),
        end = Offset(endX, centerY),
        strokeWidth = trackStroke,
        cap = StrokeCap.Round
    )
    if (normalized > 0f) {
        drawLine(
            color = colors.accent,
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        color = colors.primaryText,
        radius = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx() +
            AquaCoolingDashboardGeometry.sliderThumbOutlineWidth.toPx(),
        center = Offset(thumbX, centerY)
    )
    drawCircle(
        color = colors.accent,
        radius = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx(),
        center = Offset(thumbX, centerY)
    )
}

private data class FanPercentSliderInteraction(
    val enabled: Boolean,
    val widthPx: Float,
    val range: FanPercentSliderRange,
    val onValueChanged: (Int) -> Unit
) {
    fun updateFromPosition(positionX: Float) {
        onValueChanged(range.percentAt(positionX = positionX, widthPx = widthPx))
    }
}

private data class FanPercentSliderRange(
    val minimum: Int = AquaCoolingGaugeSpec.minimumPercent,
    val maximum: Int = AquaCoolingGaugeSpec.maximumPercent,
    val stepPercent: Int
) {
    val span: Int = (maximum - minimum).coerceAtLeast(1)
    val semanticsSteps: Int = (span / stepPercent - 1).coerceAtLeast(0)

    fun percentAt(positionX: Float, widthPx: Float): Int {
        if (widthPx <= 0f) return minimum
        val fraction = (positionX / widthPx).coerceIn(0f, 1f)
        return snap((minimum + fraction * span).roundToInt())
    }

    fun snap(value: Int): Int {
        val bounded = value.coerceIn(minimum, maximum)
        val offset = bounded - minimum
        val snappedOffset = ((offset + stepPercent / 2) / stepPercent) * stepPercent
        return (minimum + snappedOffset).coerceIn(minimum, maximum)
    }
}
