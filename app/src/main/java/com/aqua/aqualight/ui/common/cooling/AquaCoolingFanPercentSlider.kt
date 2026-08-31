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
@Composable
fun AquaCoolingFanPercentSlider(
    percent: Int,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    stepPercent: Int = 1,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(stepPercent > 0)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val minimum = AquaCoolingGaugeSpec.minimumPercent
    val maximum = AquaCoolingGaugeSpec.maximumPercent
    val clamped = snapPercent(percent, minimum, maximum, stepPercent)
    val stateText = stringResource(R.string.device_cooling_percent_value_format, clamped)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.sliderTouchHeight)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .pointerInput(enabled, widthPx, stepPercent) {
                if (enabled) {
                    detectTapGestures { offset ->
                        onValueChanged(
                            percentFromPosition(
                                positionX = offset.x,
                                widthPx = widthPx,
                                minimum = minimum,
                                maximum = maximum,
                                stepPercent = stepPercent
                            )
                        )
                    }
                }
            }
            .pointerInput(enabled, widthPx, stepPercent) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onValueChanged(
                                percentFromPosition(
                                    positionX = offset.x,
                                    widthPx = widthPx,
                                    minimum = minimum,
                                    maximum = maximum,
                                    stepPercent = stepPercent
                                )
                            )
                        },
                        onHorizontalDrag = { change, _ ->
                            onValueChanged(
                                percentFromPosition(
                                    positionX = change.position.x,
                                    widthPx = widthPx,
                                    minimum = minimum,
                                    maximum = maximum,
                                    stepPercent = stepPercent
                                )
                            )
                        }
                    )
                }
            }
            .semantics {
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped.toFloat(),
                    range = minimum.toFloat()..maximum.toFloat(),
                    steps = ((maximum - minimum) / stepPercent - 1).coerceAtLeast(0)
                )
                if (enabled) {
                    setProgress { requested ->
                        onValueChanged(
                            snapPercent(
                                requested.roundToInt(),
                                minimum,
                                maximum,
                                stepPercent
                            )
                        )
                        true
                    }
                } else {
                    disabled()
                }
            }
    ) {
        val centerY = size.height / 2f
        val startX = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx()
        val endX = (size.width - startX).coerceAtLeast(startX)
        val trackWidth = (endX - startX).coerceAtLeast(1f)
        val range = (maximum - minimum).coerceAtLeast(1)
        val normalized = (clamped - minimum).toFloat() / range
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
}

private fun percentFromPosition(
    positionX: Float,
    widthPx: Float,
    minimum: Int,
    maximum: Int,
    stepPercent: Int
): Int {
    if (widthPx <= 0f) return minimum
    val fraction = (positionX / widthPx).coerceIn(0f, 1f)
    val raw = minimum + fraction * (maximum - minimum)
    return snapPercent(raw.roundToInt(), minimum, maximum, stepPercent)
}

private fun snapPercent(
    value: Int,
    minimum: Int,
    maximum: Int,
    stepPercent: Int
): Int {
    val bounded = value.coerceIn(minimum, maximum)
    val offset = bounded - minimum
    val snappedOffset = ((offset + stepPercent / 2) / stepPercent) * stepPercent
    return (minimum + snappedOffset).coerceIn(minimum, maximum)
}
