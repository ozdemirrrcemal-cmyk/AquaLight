@file:Suppress("FunctionNaming", "MagicNumber", "TooManyFunctions")

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            DosingChannelHeader(
                state = state,
                colors = colors,
                typography = typography,
                statusLabel = statusLabel
            )
            DosingChannelSummary(
                state = state,
                colors = colors,
                typography = typography
            )
            DosingDoseProgressBar(
                state = state.doseProgress,
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
private fun DosingChannelSummary(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val scheduleSummary = state.scheduleDays.summaryLabel()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingSummaryItem(
            icon = DosingSummaryIcon.DOSE,
            label = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                state.doseProgress.dailyDoseMl
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        DosingSummaryItem(
            icon = DosingSummaryIcon.DAYS,
            label = scheduleSummary,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DosingScheduleDaysUiState.summaryLabel(): String = when {
    selectedDays.isEmpty() -> stringResource(R.string.device_dosing_channel_no_days_selected)
    isEveryDay -> stringResource(R.string.device_dosing_channel_every_day)
    else -> selectedDays
        .map { day -> stringResource(day.shortLabelRes) }
        .joinToString(separator = DAY_SEPARATOR)
}

@Composable
private fun DosingSummaryItem(
    icon: DosingSummaryIcon,
    label: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val iconTint = when (icon) {
        DosingSummaryIcon.DOSE -> colors.accent
        DosingSummaryIcon.DAYS -> colors.secondaryText
    }
    val textColor = when (icon) {
        DosingSummaryIcon.DOSE -> colors.primaryText
        DosingSummaryIcon.DAYS -> colors.secondaryText
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingSummaryGlyph(
            icon = icon,
            tint = iconTint,
            modifier = Modifier.size(SUMMARY_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingSummaryGlyph(
    icon: DosingSummaryIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (icon) {
            DosingSummaryIcon.DOSE -> drawProfessionalDoseGlyph(tint)
            DosingSummaryIcon.DAYS -> drawScheduleDaysGlyph(tint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProfessionalDoseGlyph(color: Color) {
    val path = Path().apply {
        moveTo(size.width * DOSE_CENTER_X, size.height * DOSE_TOP_Y)
        cubicTo(
            size.width * DOSE_RIGHT_UPPER_X,
            size.height * DOSE_UPPER_CONTROL_Y,
            size.width * DOSE_RIGHT_X,
            size.height * DOSE_MIDDLE_CONTROL_Y,
            size.width * DOSE_RIGHT_X,
            size.height * DOSE_BODY_Y
        )
        cubicTo(
            size.width * DOSE_RIGHT_X,
            size.height * DOSE_BOTTOM_CONTROL_Y,
            size.width * DOSE_LEFT_X,
            size.height * DOSE_BOTTOM_CONTROL_Y,
            size.width * DOSE_LEFT_X,
            size.height * DOSE_BODY_Y
        )
        cubicTo(
            size.width * DOSE_LEFT_X,
            size.height * DOSE_MIDDLE_CONTROL_Y,
            size.width * DOSE_LEFT_UPPER_X,
            size.height * DOSE_UPPER_CONTROL_Y,
            size.width * DOSE_CENTER_X,
            size.height * DOSE_TOP_Y
        )
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = DOSE_GLYPH_STROKE.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawCircle(
        color = color.copy(alpha = DOSE_HIGHLIGHT_ALPHA),
        radius = size.minDimension * DOSE_HIGHLIGHT_RADIUS,
        center = Offset(
            x = size.width * DOSE_HIGHLIGHT_X,
            y = size.height * DOSE_HIGHLIGHT_Y
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScheduleDaysGlyph(color: Color) {
    val strokeWidth = SCHEDULE_GLYPH_STROKE.toPx()
    val bodyTop = size.height * SCHEDULE_BODY_TOP_Y
    val bodyLeft = size.width * SCHEDULE_BODY_LEFT_X
    val bodyWidth = size.width * SCHEDULE_BODY_WIDTH
    val bodyHeight = size.height * SCHEDULE_BODY_HEIGHT

    drawRoundRect(
        color = color,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(SCHEDULE_CORNER_RADIUS.toPx()),
        style = Stroke(width = strokeWidth)
    )
    drawLine(
        color = color,
        start = Offset(bodyLeft, size.height * SCHEDULE_HEADER_Y),
        end = Offset(bodyLeft + bodyWidth, size.height * SCHEDULE_HEADER_Y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    SCHEDULE_BINDER_X.forEach { xFraction ->
        drawLine(
            color = color,
            start = Offset(size.width * xFraction, size.height * SCHEDULE_BINDER_TOP_Y),
            end = Offset(size.width * xFraction, size.height * SCHEDULE_BINDER_BOTTOM_Y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
    SCHEDULE_DOT_X.forEach { xFraction ->
        drawCircle(
            color = color,
            radius = SCHEDULE_DOT_RADIUS.toPx(),
            center = Offset(size.width * xFraction, size.height * SCHEDULE_DOT_Y)
        )
    }
}

private fun DosingChannelVisualState.statusColor(colors: AquaDeviceCardColors): Color = when (this) {
    DosingChannelVisualState.NOT_CONFIGURED -> colors.warning
    DosingChannelVisualState.READY,
    DosingChannelVisualState.SCHEDULED,
    DosingChannelVisualState.DOSING -> colors.accent
    DosingChannelVisualState.ERROR -> colors.danger
}

private enum class DosingSummaryIcon {
    DOSE,
    DAYS
}

private const val DAY_SEPARATOR = " · "
private const val STATUS_BACKGROUND_ALPHA = 0.10f
private const val STATUS_OUTLINE_ALPHA = 0.38f
private const val DOSE_CENTER_X = 0.50f
private const val DOSE_TOP_Y = 0.05f
private const val DOSE_RIGHT_UPPER_X = 0.63f
private const val DOSE_LEFT_UPPER_X = 0.37f
private const val DOSE_RIGHT_X = 0.84f
private const val DOSE_LEFT_X = 0.16f
private const val DOSE_UPPER_CONTROL_Y = 0.22f
private const val DOSE_MIDDLE_CONTROL_Y = 0.48f
private const val DOSE_BODY_Y = 0.66f
private const val DOSE_BOTTOM_CONTROL_Y = 0.93f
private const val DOSE_HIGHLIGHT_ALPHA = 0.72f
private const val DOSE_HIGHLIGHT_RADIUS = 0.065f
private const val DOSE_HIGHLIGHT_X = 0.39f
private const val DOSE_HIGHLIGHT_Y = 0.61f
private const val SCHEDULE_BODY_TOP_Y = 0.18f
private const val SCHEDULE_BODY_LEFT_X = 0.10f
private const val SCHEDULE_BODY_WIDTH = 0.80f
private const val SCHEDULE_BODY_HEIGHT = 0.70f
private const val SCHEDULE_HEADER_Y = 0.40f
private const val SCHEDULE_BINDER_TOP_Y = 0.08f
private const val SCHEDULE_BINDER_BOTTOM_Y = 0.28f
private const val SCHEDULE_DOT_Y = 0.62f
private val SCHEDULE_BINDER_X = listOf(0.32f, 0.68f)
private val SCHEDULE_DOT_X = listOf(0.30f, 0.50f, 0.70f)
private val CHANNEL_CARD_MIN_HEIGHT = 104.dp
private val SUMMARY_GAP = 18.dp
private val SUMMARY_ICON_GAP = 6.dp
private val SUMMARY_ICON_SIZE = 16.dp
private val DOSE_GLYPH_STROKE = 1.45.dp
private val SCHEDULE_GLYPH_STROKE = 1.35.dp
private val SCHEDULE_CORNER_RADIUS = 2.5.dp
private val SCHEDULE_DOT_RADIUS = 1.1.dp
