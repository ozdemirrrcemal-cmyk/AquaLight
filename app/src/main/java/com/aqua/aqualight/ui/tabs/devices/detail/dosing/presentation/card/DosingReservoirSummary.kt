package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingReservoirSummary(
    state: DosingReservoirUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val color = state.tone.color(colors)
    val label = when {
        !state.remainingAvailable ->
            stringResource(R.string.device_dosing_card_reservoir_unavailable)
        state.estimatedRemainingDays != null -> pluralStringResource(
            R.plurals.device_dosing_card_reservoir_days_format,
            state.estimatedRemainingDays,
            state.estimatedRemainingDays,
            state.remainingMl
        )
        else -> stringResource(
            R.string.device_dosing_card_reservoir_amount_format,
            state.remainingMl
        )
    }
    val description = stringResource(
        R.string.device_dosing_card_reservoir_description,
        label
    )
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(RESERVOIR_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingReservoirGlyph(
            fillFraction = state.fillFraction,
            fillAvailable = state.fillAvailable,
            color = color,
            outlineColor = colors.secondaryText,
            modifier = Modifier.size(RESERVOIR_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingReservoirGlyph(
    fillFraction: Float,
    fillAvailable: Boolean,
    color: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = RESERVOIR_STROKE.toPx()
        val bodyLeft = size.width * BODY_LEFT
        val bodyTop = size.height * BODY_TOP
        val bodyWidth = size.width * BODY_WIDTH
        val bodyHeight = size.height * BODY_HEIGHT
        val innerInset = stroke * INNER_INSET_MULTIPLIER
        val innerHeight = (bodyHeight - innerInset * 2f).coerceAtLeast(0f)
        val resolvedFill = fillFraction.coerceIn(0f, 1f)
        val fillHeight = innerHeight * resolvedFill

        if (fillAvailable) {
            drawRoundRect(
                color = color.copy(alpha = FILL_ALPHA),
                topLeft = Offset(
                    bodyLeft + innerInset,
                    bodyTop + innerInset + innerHeight - fillHeight
                ),
                size = Size(bodyWidth - innerInset * 2f, fillHeight),
                cornerRadius = CornerRadius(FILL_CORNER_RADIUS.toPx())
            )
        }
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(BODY_CORNER_RADIUS.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(size.width * CAP_LEFT, size.height * CAP_TOP),
            size = Size(size.width * CAP_WIDTH, size.height * CAP_HEIGHT),
            cornerRadius = CornerRadius(CAP_CORNER_RADIUS.toPx()),
            style = Stroke(width = stroke)
        )
        if (fillAvailable) {
            drawLine(
                color = color.copy(alpha = LEVEL_LINE_ALPHA),
                start = Offset(bodyLeft + innerInset, bodyTop + bodyHeight * LEVEL_LINE_Y),
                end = Offset(
                    bodyLeft + bodyWidth - innerInset,
                    bodyTop + bodyHeight * LEVEL_LINE_Y
                ),
                strokeWidth = LEVEL_LINE_WIDTH.toPx()
            )
        }
    }
}

private fun DosingReservoirTone.color(colors: AquaDeviceCardColors): Color = when (this) {
    DosingReservoirTone.NORMAL -> colors.accent
    DosingReservoirTone.WARNING -> colors.warning
    DosingReservoirTone.CRITICAL,
    DosingReservoirTone.UNCERTAIN -> colors.danger
}

private const val BODY_LEFT = 0.18f
private const val BODY_TOP = 0.25f
private const val BODY_WIDTH = 0.64f
private const val BODY_HEIGHT = 0.68f
private const val CAP_LEFT = 0.33f
private const val CAP_TOP = 0.06f
private const val CAP_WIDTH = 0.34f
private const val CAP_HEIGHT = 0.22f
private const val LEVEL_LINE_Y = 0.45f
private const val INNER_INSET_MULTIPLIER = 1.6f
private const val FILL_ALPHA = 0.88f
private const val LEVEL_LINE_ALPHA = 0.85f
private val RESERVOIR_GAP = 6.dp
private val RESERVOIR_ICON_SIZE = 16.dp
private val RESERVOIR_STROKE = 1.35.dp
private val LEVEL_LINE_WIDTH = 1.dp
private val BODY_CORNER_RADIUS = 2.5.dp
private val FILL_CORNER_RADIUS = 1.5.dp
private val CAP_CORNER_RADIUS = 1.dp
