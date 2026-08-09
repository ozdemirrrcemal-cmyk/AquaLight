@file:Suppress("FunctionNaming", "MagicNumber", "TooManyFunctions", "UnusedPrivateMember")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

@Composable
internal fun DosingChannelCard(
    state: DosingChannelCardUiState,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val statusLabel = stringResource(state.visualState.labelRes)
    val contentDescriptionText = stringResource(
        R.string.device_dosing_channel_card_content_description,
        state.channelNumber,
        state.displayName,
        statusLabel
    )

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CHANNEL_CARD_MIN_HEIGHT)
            .semantics { contentDescription = contentDescriptionText }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
        ) {
            DosingChannelHeader(
                state = state,
                colors = colors,
                typography = typography,
                statusLabel = statusLabel
            )
            DosingChannelMetadata(
                state = state,
                colors = colors,
                typography = typography
            )
            DosingScheduleTimeline(
                state = state.timeline,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun DosingChannelHeader(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    statusLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelMarker(
            channelNumber = state.channelNumber,
            colors = colors,
            typography = typography
        )
        BasicText(
            text = state.displayName,
            modifier = Modifier
                .weight(1f)
                .padding(start = AquaDeviceCardGeometry.compactGap),
            style = typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        DosingStatusPill(
            label = statusLabel,
            color = state.visualState.statusColor(colors),
            typography = typography,
            modifier = Modifier.padding(start = AquaDeviceCardGeometry.compactGap)
        )
    }
}

@Composable
private fun ChannelMarker(
    channelNumber: Int,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.markerCornerRadius)
    Box(
        modifier = Modifier
            .size(AquaDeviceCardGeometry.markerSize)
            .clip(shape)
            .background(colors.mediaSurface)
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.mediaOutline,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = channelNumber.toString(),
            style = typography.body.copy(color = colors.accent)
        )
    }
}

@Composable
private fun DosingStatusPill(
    label: String,
    color: Color,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = STATUS_BACKGROUND_ALPHA))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = color.copy(alpha = STATUS_OUTLINE_ALPHA),
                shape = shape
            )
            .padding(
                horizontal = AquaDeviceCardGeometry.statusHorizontalPadding,
                vertical = AquaDeviceCardGeometry.statusVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.micro.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingChannelMetadata(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(METADATA_GAP),
        verticalAlignment = Alignment.Top
    ) {
        DosingMetadataItem(
            icon = DosingMetadataIcon.DOSE,
            label = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                state.dailyDoseMl
            ),
            tint = colors.secondaryText,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        DosingMetadataItem(
            icon = DosingMetadataIcon.CALIBRATION,
            label = stringResource(state.calibrationState.labelRes),
            tint = state.calibrationState.tint(colors),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        DosingMetadataItem(
            icon = DosingMetadataIcon.SETUP,
            label = stringResource(state.setupState.labelRes),
            tint = state.setupState.tint(colors),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DosingMetadataItem(
    icon: DosingMetadataIcon,
    label: String,
    tint: Color,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(METADATA_ICON_GAP),
        verticalAlignment = Alignment.Top
    ) {
        DosingMetadataGlyph(
            icon = icon,
            tint = tint,
            modifier = Modifier
                .padding(top = METADATA_ICON_TOP_PADDING)
                .size(METADATA_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(
                color = if (tint == colors.secondaryText) colors.secondaryText else tint
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingMetadataGlyph(
    icon: DosingMetadataIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (icon) {
            DosingMetadataIcon.DOSE -> drawDoseGlyph(tint)
            DosingMetadataIcon.CALIBRATION -> drawCalibrationGlyph(tint)
            DosingMetadataIcon.SETUP -> drawSetupGlyph(tint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoseGlyph(color: Color) {
    val path = Path().apply {
        moveTo(size.width * DOSE_CENTER_X, size.height * DOSE_TOP_Y)
        cubicTo(
            size.width * DOSE_RIGHT_CONTROL_X,
            size.height * DOSE_FIRST_CONTROL_Y,
            size.width * DOSE_RIGHT_CONTROL_X,
            size.height * DOSE_SECOND_CONTROL_Y,
            size.width * DOSE_CENTER_X,
            size.height * DOSE_BOTTOM_Y
        )
        cubicTo(
            size.width * DOSE_LEFT_CONTROL_X,
            size.height * DOSE_SECOND_CONTROL_Y,
            size.width * DOSE_LEFT_CONTROL_X,
            size.height * DOSE_FIRST_CONTROL_Y,
            size.width * DOSE_CENTER_X,
            size.height * DOSE_TOP_Y
        )
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = GLYPH_STROKE.toPx()))
    drawLine(
        color = color,
        start = Offset(size.width * TICK_LEFT_X, size.height * TICK_Y),
        end = Offset(size.width * TICK_RIGHT_X, size.height * TICK_Y),
        strokeWidth = GLYPH_STROKE.toPx(),
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCalibrationGlyph(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
        color = color,
        radius = size.minDimension * CALIBRATION_OUTER_RADIUS,
        center = center,
        style = Stroke(width = GLYPH_STROKE.toPx())
    )
    drawCircle(
        color = color,
        radius = size.minDimension * CALIBRATION_INNER_RADIUS,
        center = center
    )
    drawLine(
        color = color,
        start = Offset(center.x, 0f),
        end = Offset(center.x, size.height * CALIBRATION_TICK_END),
        strokeWidth = GLYPH_STROKE.toPx(),
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSetupGlyph(color: Color) {
    SETUP_LINE_Y.forEachIndexed { index, yFraction ->
        val y = size.height * yFraction
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = GLYPH_STROKE.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = SETUP_KNOB_RADIUS.toPx(),
            center = Offset(size.width * SETUP_KNOB_X[index], y)
        )
    }
}

private fun DosingChannelVisualState.statusColor(colors: AquaDeviceCardColors): Color = when (this) {
    DosingChannelVisualState.SETUP_REQUIRED -> colors.warning
    DosingChannelVisualState.READY,
    DosingChannelVisualState.SCHEDULED,
    DosingChannelVisualState.DOSING -> colors.accent
    DosingChannelVisualState.ERROR -> colors.danger
}

private fun DosingCalibrationUiState.tint(colors: AquaDeviceCardColors): Color = when (this) {
    DosingCalibrationUiState.REQUIRED -> colors.warning
    DosingCalibrationUiState.CALIBRATED -> colors.accent
}

private fun DosingSetupUiState.tint(colors: AquaDeviceCardColors): Color = when (this) {
    DosingSetupUiState.NOT_CONFIGURED -> colors.warning
    DosingSetupUiState.CONFIGURED -> colors.accent
}

private enum class DosingMetadataIcon {
    DOSE,
    CALIBRATION,
    SETUP
}

private const val STATUS_BACKGROUND_ALPHA = 0.12f
private const val STATUS_OUTLINE_ALPHA = 0.42f
private const val DOSE_CENTER_X = 0.50f
private const val DOSE_TOP_Y = 0.04f
private const val DOSE_RIGHT_CONTROL_X = 0.88f
private const val DOSE_LEFT_CONTROL_X = 0.12f
private const val DOSE_FIRST_CONTROL_Y = 0.40f
private const val DOSE_SECOND_CONTROL_Y = 0.72f
private const val DOSE_BOTTOM_Y = 0.92f
private const val TICK_LEFT_X = 0.35f
private const val TICK_RIGHT_X = 0.65f
private const val TICK_Y = 0.67f
private const val CALIBRATION_OUTER_RADIUS = 0.36f
private const val CALIBRATION_INNER_RADIUS = 0.10f
private const val CALIBRATION_TICK_END = 0.22f
private val SETUP_LINE_Y = listOf(0.22f, 0.50f, 0.78f)
private val SETUP_KNOB_X = listOf(0.32f, 0.68f, 0.44f)
private val CHANNEL_CARD_MIN_HEIGHT = 116.dp
private val METADATA_GAP = 8.dp
private val METADATA_ICON_GAP = 5.dp
private val METADATA_ICON_TOP_PADDING = 1.dp
private val METADATA_ICON_SIZE = 14.dp
private val GLYPH_STROKE = 1.4.dp
private val SETUP_KNOB_RADIUS = 1.7.dp

@Preview(name = "Dosing channel - setup", showBackground = true, backgroundColor = 0xFF0A192F)
@Composable
private fun DosingChannelSetupPreview() {
    DosingChannelCard(
        state = DosingChannelCardUiState(
            slotId = "dosing:channel1",
            channelNumber = 1,
            wireKey = "channel1",
            displayName = "Channel 1"
        ),
        modifier = Modifier.padding(16.dp)
    )
}

@Preview(name = "Dosing channel - active", showBackground = true, backgroundColor = 0xFF0A192F)
@Composable
private fun DosingChannelActivePreview() {
    DosingChannelCard(
        state = DosingChannelCardUiState(
            slotId = "dosing:channel2",
            channelNumber = 2,
            wireKey = "channel2",
            displayName = "Micro Elements & Iron",
            dailyDoseMl = 4.5,
            calibrationState = DosingCalibrationUiState.CALIBRATED,
            setupState = DosingSetupUiState.CONFIGURED,
            visualState = DosingChannelVisualState.DOSING,
            timeline = DosingTimelineUiState(
                events = listOf(
                    DosingTimelineEventUiState(0.25f, 1.5),
                    DosingTimelineEventUiState(0.50f, 1.5, active = true),
                    DosingTimelineEventUiState(0.75f, 1.5)
                ),
                visualState = DosingTimelineVisualState.ACTIVE
            )
        ),
        modifier = Modifier.padding(16.dp)
    )
}
