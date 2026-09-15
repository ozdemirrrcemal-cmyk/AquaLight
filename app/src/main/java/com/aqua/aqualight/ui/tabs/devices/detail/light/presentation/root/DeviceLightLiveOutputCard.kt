package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
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
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.AquaLightLiveOutputColors
import com.aqua.aqualight.ui.common.light.AquaLightLiveOutputPreviewSpec
import com.aqua.aqualight.ui.common.light.AquaLightLiveOutputTypography
import com.aqua.aqualight.ui.common.light.AquaLightPlanChartSpec
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightLiveOutputColors
import com.aqua.aqualight.ui.common.light.aquaLightLiveOutputTypography

@Composable
internal fun DeviceLightLiveOutputCard(modifier: Modifier = Modifier) {
    val dashboardColors = aquaLightDashboardColors()
    val colors = aquaLightLiveOutputColors(dashboardColors)
    val typography = aquaLightLiveOutputTypography(dashboardColors)
    val description = deviceLightLiveOutputDescription()

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.liveOutputCardMinimumHeight)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        DeviceLightLiveOutputContent(
            channels = deviceLightLiveOutputChannels(colors),
            colors = colors,
            typography = typography,
            textColor = dashboardColors.primaryText
        )
    }
}

private fun deviceLightLiveOutputChannels(
    colors: AquaLightLiveOutputColors
): List<DeviceLightLiveOutputChannel> =
    listOf(
        DeviceLightLiveOutputChannel(
            labelRes = R.string.device_light_live_output_red,
            percent = AquaLightLiveOutputPreviewSpec.redPercent,
            color = colors.red
        ),
        DeviceLightLiveOutputChannel(
            labelRes = R.string.device_light_live_output_green,
            percent = AquaLightLiveOutputPreviewSpec.greenPercent,
            color = colors.green
        ),
        DeviceLightLiveOutputChannel(
            labelRes = R.string.device_light_live_output_blue,
            percent = AquaLightLiveOutputPreviewSpec.bluePercent,
            color = colors.blue
        ),
        DeviceLightLiveOutputChannel(
            labelRes = R.string.device_light_live_output_white,
            percent = AquaLightLiveOutputPreviewSpec.whitePercent,
            color = colors.white
        )
    )

@Composable
private fun deviceLightLiveOutputDescription(): String =
    stringResource(
        R.string.device_light_live_output_content_description,
        stringResource(
            R.string.device_light_live_output_percent_format,
            AquaLightLiveOutputPreviewSpec.redPercent
        ),
        stringResource(
            R.string.device_light_live_output_percent_format,
            AquaLightLiveOutputPreviewSpec.greenPercent
        ),
        stringResource(
            R.string.device_light_live_output_percent_format,
            AquaLightLiveOutputPreviewSpec.bluePercent
        ),
        stringResource(
            R.string.device_light_live_output_percent_format,
            AquaLightLiveOutputPreviewSpec.whitePercent
        )
    )

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
            text = stringResource(channel.labelRes),
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
    @StringRes val labelRes: Int,
    val percent: Int,
    val color: Color
)
