package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightLiveOutputColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightLiveOutputTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightLiveOutputColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightLiveOutputTypography

@Composable
internal fun DeviceLightLiveOutputCard(
    channels: List<DeviceLightChannelOutputSnapshot>,
    modifier: Modifier = Modifier
) {
    val dashboardColors = aquaLightDashboardColors()
    val colors = aquaLightLiveOutputColors(dashboardColors)
    val typography = aquaLightLiveOutputTypography(dashboardColors)
    val outputChannels = deviceLightLiveOutputChannels(channels)
    val description = deviceLightLiveOutputDescription(outputChannels)

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.liveOutputCardMinimumHeight)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        DeviceLightLiveOutputContent(
            channels = outputChannels,
            colors = colors,
            typography = typography,
            textColor = dashboardColors.primaryText
        )
    }
}

@Composable
private fun deviceLightLiveOutputChannels(
    channels: List<DeviceLightChannelOutputSnapshot>
): List<DeviceLightLiveOutputChannel> = channels.map { channel ->
    DeviceLightLiveOutputChannel(
        label = channel.localizedLabel(),
        percent = channel.effectivePercent,
        color = channel.toComposeColor()
    )
}

@Composable
private fun deviceLightLiveOutputDescription(
    channels: List<DeviceLightLiveOutputChannel>
): String =
    stringResource(
        R.string.device_light_live_output_content_description,
        channels.joinToString { channel -> "${channel.label} ${channel.percent}%" }
    )

@Composable
private fun DeviceLightChannelOutputSnapshot.localizedLabel(): String = when (key) {
    "red" -> stringResource(R.string.device_light_live_output_red)
    "green" -> stringResource(R.string.device_light_live_output_green)
    "blue" -> stringResource(R.string.device_light_live_output_blue)
    "white" -> stringResource(R.string.device_light_live_output_white)
    else -> displayName
}

@Composable
private fun DeviceLightLiveOutputContent(
    channels: List<DeviceLightLiveOutputChannel>,
    colors: AquaLightLiveOutputColors,
    typography: AquaLightLiveOutputTypography,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(R.string.device_light_live_output_title),
            style = typography.title.copy(color = textColor)
        )
        Spacer(
            modifier = Modifier.height(AquaLightDashboardGeometry.liveOutputTitleBottomGap)
        )
        channels.forEachIndexed { index, channel ->
            DeviceLightLiveOutputRow(
                channel = channel,
                colors = colors,
                typography = typography,
                textColor = textColor
            )
            if (index != channels.lastIndex) {
                Spacer(
                    modifier = Modifier.height(AquaLightDashboardGeometry.liveOutputRowGap)
                )
            }
        }
    }
}

@Composable
private fun DeviceLightLiveOutputRow(
    channel: DeviceLightLiveOutputChannel,
    colors: AquaLightLiveOutputColors,
    typography: AquaLightLiveOutputTypography,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.liveOutputRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = channel.label,
            style = typography.label.copy(color = textColor),
            modifier = Modifier.width(AquaLightDashboardGeometry.liveOutputLabelWidth)
        )
        Spacer(modifier = Modifier.width(AquaLightDashboardGeometry.liveOutputLabelTrackGap))
        DeviceLightLiveOutputTrack(
            percent = channel.percent,
            fillColor = channel.color,
            railColor = colors.rail,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(AquaLightDashboardGeometry.liveOutputTrackValueGap))
        BasicText(
            text = stringResource(
                R.string.device_light_live_output_percent_format,
                channel.percent
            ),
            style = typography.value.copy(
                color = textColor,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(AquaLightDashboardGeometry.liveOutputValueWidth)
        )
    }
}

@Composable
private fun DeviceLightLiveOutputTrack(
    percent: Int,
    fillColor: Color,
    railColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(AquaLightDashboardGeometry.liveOutputTrackHeight)
            .clip(AquaLightDashboardGeometry.liveOutputTrackShape)
            .background(railColor)
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
                        colors = listOf(
                            fillColor.copy(alpha = AquaLightDashboardAlpha.liveOutputFillStart),
                            fillColor
                        )
                    )
                )
        )
    }
}

private data class DeviceLightLiveOutputChannel(
    val label: String,
    val percent: Int,
    val color: Color
)
