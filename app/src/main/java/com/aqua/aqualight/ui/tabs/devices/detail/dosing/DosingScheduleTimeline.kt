package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingScheduleTimeline(
    state: DosingTimelineUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.device_dosing_channel_timeline_description)
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_CANVAS_HEIGHT)
                .semantics { contentDescription = description }
        ) {
            val trackHeight = TIMELINE_TRACK_HEIGHT.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            val trackRadius = trackHeight / 2f
            val centerY = size.height / 2f

            drawRoundRect(
                color = colors.mediaSurface,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )

            if (state.visualState == DosingTimelineVisualState.ACTIVE) {
                val activeEnd = state.events
                    .filter(DosingTimelineEventUiState::active)
                    .maxOfOrNull(DosingTimelineEventUiState::fractionOfDay)
                    ?.coerceIn(0f, 1f)
                    ?: 0f
                if (activeEnd > 0f) {
                    drawRoundRect(
                        color = colors.accent.copy(alpha = ACTIVE_TRACK_ALPHA),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width * activeEnd, trackHeight),
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )
                }
            }

            TIMELINE_TICK_FRACTIONS.forEach { fraction ->
                val x = size.width * fraction
                drawLine(
                    color = colors.mediaOutline,
                    start = Offset(x, centerY - TICK_HALF_HEIGHT.toPx()),
                    end = Offset(x, centerY + TICK_HALF_HEIGHT.toPx()),
                    strokeWidth = TICK_WIDTH.toPx()
                )
            }

            state.events.forEach { event ->
                val eventColor = event.eventColor(colors)
                val x = size.width * event.fractionOfDay
                drawCircle(
                    color = colors.surface,
                    radius = EVENT_OUTER_RADIUS.toPx(),
                    center = Offset(x, centerY)
                )
                drawCircle(
                    color = eventColor,
                    radius = EVENT_INNER_RADIUS.toPx(),
                    center = Offset(x, centerY)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TIMELINE_LABEL_TOP_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TIMELINE_LABELS.forEach { label ->
                BasicText(text = label, style = typography.micro)
            }
        }
    }
}

private fun DosingTimelineEventUiState.eventColor(colors: AquaDeviceCardColors): Color = when {
    error -> colors.danger
    active -> colors.accent
    else -> colors.warning
}

private val TIMELINE_LABELS = listOf("00", "06", "12", "18", "24")
private val TIMELINE_TICK_FRACTIONS = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
private const val ACTIVE_TRACK_ALPHA = 0.30f
private val TIMELINE_CANVAS_HEIGHT = 14.dp
private val TIMELINE_TRACK_HEIGHT = 8.dp
private val TIMELINE_LABEL_TOP_PADDING = 2.dp
private val TICK_HALF_HEIGHT = 3.dp
private val TICK_WIDTH = 1.dp
private val EVENT_OUTER_RADIUS = 5.dp
private val EVENT_INNER_RADIUS = 3.dp
