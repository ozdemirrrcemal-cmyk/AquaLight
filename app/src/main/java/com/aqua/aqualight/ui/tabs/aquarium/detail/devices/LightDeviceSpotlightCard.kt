package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.AutomaticCycleEventIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.AutomaticCycleEventKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.automaticEditorTimeText
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightTankCardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightTankCardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Immutable
private data class LightDeviceCardVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

@Immutable
private data class LightScheduleMetricUi(
    val kind: AutomaticCycleEventKind,
    val timeMs: Long?,
    val labelRes: Int,
    val accent: Color
)

@Composable
internal fun LightDeviceSpotlightCard(item: LightDeviceSpotlightCardUi) {
    val colors = aquaLightManualColors()
    val visuals = LightDeviceCardVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    val online = item.header.statusStyle == DeviceConnectionVisualState.ONLINE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightTankCardGeometry.cardMinimumHeight)
            .padding(
                horizontal = AquaLightTankCardGeometry.contentPadding,
                vertical = AquaLightTankCardGeometry.verticalPadding
            )
    ) {
        LightDeviceHeader(item, online, visuals)
        Spacer(Modifier.height(AquaLightTankCardGeometry.dividerTopGap))
        Box(
            Modifier
                .fillMaxWidth()
                .height(AquaDeviceCardGeometry.outlineWidth)
                .background(visuals.colors.card.outline)
        )
        Spacer(Modifier.height(AquaLightTankCardGeometry.dividerBottomGap))
        LightChannelSection(item, online, visuals)
    }
}

@Composable
private fun LightDeviceHeader(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        LightDeviceThumbnail(item, online, visuals)
        Spacer(Modifier.width(AquaLightTankCardGeometry.headerGap))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lampActive = online && item.snapshot?.hero?.outputActive == true
                Image(
                    painter = painterResource(R.drawable.ic_care_light_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (lampActive) {
                            visuals.colors.card.warning
                        } else {
                            colorResource(R.color.aqua_device_connection_offline)
                        }
                    ),
                    modifier = Modifier.size(AquaLightTankCardGeometry.titleIconSize)
                )
                Spacer(Modifier.width(AquaLightTankCardGeometry.titleIconGap))
                BasicText(
                    text = item.header.displayName,
                    style = visuals.typography.title.copy(
                        color = visuals.colors.card.primaryText
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(AquaLightTankCardGeometry.headerRowGap))
            LightStatusModeRow(item, online, visuals)
            Spacer(Modifier.height(AquaLightTankCardGeometry.scheduleTopGap))
            LightScheduleRow(item, online, visuals)
        }
    }
}

@Composable
private fun LightDeviceThumbnail(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    Box(
        modifier = Modifier
            .size(AquaLightTankCardGeometry.mediaSize)
            .alpha(if (online) 1f else AquaLightTankCardAlpha.offlineMedia)
            .clip(AquaLightTankCardGeometry.mediaCornerRadius)
            .background(visuals.colors.card.mediaSurface)
            .border(
                AquaDeviceCardGeometry.outlineWidth,
                visuals.colors.card.mediaOutline,
                AquaLightTankCardGeometry.mediaCornerRadius
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(item.header.iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaLightTankCardGeometry.mediaImageSize)
        )
    }
}

@Composable
private fun LightStatusModeRow(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AquaLightTankCardGeometry.statusChipGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightConnectionChip(item, visuals)
        LightModeChip(item, online, visuals)
    }
}

@Composable
private fun LightConnectionChip(
    item: LightDeviceSpotlightCardUi,
    visuals: LightDeviceCardVisuals
) {
    val statusColor = colorResource(item.header.statusStyle.tintColorRes)
    val chipShape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(statusColor.copy(alpha = AquaLightTankCardAlpha.statusSurface))
            .padding(
                horizontal = AquaDeviceCardGeometry.statusHorizontalPadding,
                vertical = AquaDeviceCardGeometry.statusVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(AquaLightTankCardGeometry.statusDotSize)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(AquaLightTankCardGeometry.statusContentGap))
        BasicText(
            text = stringResource(item.header.statusStyle.statusLabelRes),
            style = visuals.typography.body.copy(color = statusColor),
            maxLines = 1
        )
    }
}

@Composable
private fun LightModeChip(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    val chipShape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    val detailAlpha = if (online) 1f else AquaLightTankCardAlpha.offlineDetails
    val mode = item.snapshot?.hero?.mode
    Row(
        modifier = Modifier
            .alpha(detailAlpha)
            .clip(chipShape)
            .background(
                visuals.colors.card.mediaSurface.copy(
                    alpha = AquaLightTankCardAlpha.modeSurface
                )
            )
            .border(
                AquaDeviceCardGeometry.outlineWidth,
                visuals.colors.card.mediaOutline,
                chipShape
            )
            .padding(
                horizontal = AquaDeviceCardGeometry.statusHorizontalPadding,
                vertical = AquaDeviceCardGeometry.statusVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightTankCardGeometry.modeGlyphSize)
                .border(
                    AquaLightTankCardGeometry.modeGlyphBorderWidth,
                    visuals.colors.action,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = mode.lightCardModeGlyph(),
                style = visuals.typography.micro.copy(
                    color = visuals.colors.action,
                    textAlign = TextAlign.Center
                )
            )
        }
        Spacer(Modifier.width(AquaLightTankCardGeometry.modeGlyphGap))
        BasicText(
            text = stringResource(mode.lightCardModeLabelRes()),
            style = visuals.typography.body.copy(color = visuals.colors.action),
            maxLines = 1
        )
    }
}

@Composable
private fun LightScheduleRow(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    val alpha = if (online) 1f else AquaLightTankCardAlpha.offlineDetails
    val sunrise = LightScheduleMetricUi(
        kind = AutomaticCycleEventKind.SUNRISE,
        timeMs = item.sunriseTimeMs,
        labelRes = R.string.device_light_card_sunrise,
        accent = visuals.colors.card.warning
    )
    val sunset = LightScheduleMetricUi(
        kind = AutomaticCycleEventKind.SUNSET,
        timeMs = item.sunsetTimeMs,
        labelRes = R.string.device_light_card_sunset,
        accent = visuals.colors.shrimp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightScheduleMetric(sunrise, visuals, Modifier.weight(1f))
        Box(
            Modifier
                .width(AquaLightTankCardGeometry.scheduleDividerWidth)
                .height(AquaLightTankCardGeometry.scheduleDividerHeight)
                .background(visuals.colors.card.outline)
        )
        Spacer(Modifier.width(AquaLightTankCardGeometry.scheduleGap))
        LightScheduleMetric(sunset, visuals, Modifier.weight(1f))
    }
}

@Composable
private fun LightScheduleMetric(
    metric: LightScheduleMetricUi,
    visuals: LightDeviceCardVisuals,
    modifier: Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AutomaticCycleEventIcon(
            kind = metric.kind,
            color = metric.accent,
            modifier = Modifier.size(AquaLightTankCardGeometry.scheduleIconSize)
        )
        Spacer(Modifier.width(AquaLightTankCardGeometry.scheduleIconGap))
        Column {
            BasicText(
                text = metric.timeMs?.let { automaticEditorTimeText(it) }
                    ?: stringResource(R.string.device_light_auto_editor_time_placeholder),
                style = visuals.typography.body.copy(
                    color = visuals.colors.card.primaryText
                )
            )
            BasicText(
                text = stringResource(metric.labelRes),
                style = visuals.typography.caption.copy(
                    color = visuals.colors.card.secondaryText
                )
            )
        }
    }
}

@Composable
private fun LightChannelSection(
    item: LightDeviceSpotlightCardUi,
    online: Boolean,
    visuals: LightDeviceCardVisuals
) {
    val alpha = if (online) 1f else AquaLightTankCardAlpha.offlineDetails
    Column(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        BasicText(
            text = stringResource(R.string.device_light_card_channel_intensities),
            style = visuals.typography.title.copy(
                color = visuals.colors.card.primaryText
            )
        )
        Spacer(Modifier.height(AquaLightTankCardGeometry.sectionTitleBottomGap))
        val channels = item.snapshot?.channels.orEmpty()
        if (channels.isEmpty()) {
            BasicText(
                text = stringResource(
                    if (item.contentState == LightDeviceSpotlightContentState.PREPARING) {
                        R.string.device_light_card_loading
                    } else {
                        R.string.device_light_card_unavailable
                    }
                ),
                style = visuals.typography.caption.copy(
                    color = visuals.colors.card.secondaryText
                )
            )
        } else {
            channels.forEachIndexed { index, channel ->
                LightChannelRow(channel, visuals)
                if (index != channels.lastIndex) {
                    Spacer(Modifier.height(AquaLightTankCardGeometry.channelRowGap))
                }
            }
        }
    }
}

@Composable
private fun LightChannelRow(
    channel: DeviceLightChannelOutputSnapshot,
    visuals: LightDeviceCardVisuals
) {
    val percent = channel.effectivePercent.coerceIn(
        AquaLightPlanChartSpec.minimumPercent,
        AquaLightPlanChartSpec.maximumPercent
    )
    val fill = Color(OPAQUE_COLOR_MASK or channel.displayColorRgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightTankCardGeometry.channelRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = channel.lightCardLabelRes()?.let { stringResource(it) } ?: channel.displayName,
            style = visuals.typography.body.copy(
                color = visuals.colors.card.secondaryText
            ),
            modifier = Modifier.width(AquaLightTankCardGeometry.channelLabelWidth)
        )
        Spacer(Modifier.width(AquaLightTankCardGeometry.channelLabelGap))
        LightChannelTrack(
            percent = percent,
            fill = fill,
            rail = visuals.colors.card.secondaryText,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(AquaLightTankCardGeometry.channelValueGap))
        BasicText(
            text = stringResource(R.string.device_light_card_channel_value_format, percent),
            style = visuals.typography.body.copy(
                color = visuals.colors.card.primaryText,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(AquaLightTankCardGeometry.channelValueWidth)
        )
    }
}

@Composable
private fun LightChannelTrack(
    percent: Int,
    fill: Color,
    rail: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(AquaLightDashboardGeometry.liveOutputTrackHeight)
            .clip(AquaLightDashboardGeometry.liveOutputTrackShape)
            .background(
                rail.copy(alpha = AquaLightDashboardAlpha.liveOutputRail)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    percent.toFloat() / AquaLightPlanChartSpec.maximumPercent.toFloat()
                )
                .clip(AquaLightDashboardGeometry.liveOutputTrackShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            fill.copy(alpha = AquaLightDashboardAlpha.liveOutputFillStart),
                            fill
                        )
                    )
                )
        )
    }
}

private const val OPAQUE_COLOR_MASK = 0xFF000000.toInt()
