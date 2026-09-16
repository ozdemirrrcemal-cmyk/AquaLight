package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticGeometry

@Composable
internal fun ManualChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    scene: DeviceLightLibraryScene,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = LIBRARY_CHANNEL_ORDER.filter(channels::contains)
    val labels = visibleChannels.associateWith { channel ->
        stringResource(
            R.string.device_light_library_channel_summary_format,
            channel.shortLabel(),
            scene.channels.getValue(channel)
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
internal fun CustomChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    payload: DeviceLightLibraryPayload.Custom,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = LIBRARY_CHANNEL_ORDER.filter(channels::contains)
    val labels = visibleChannels.associateWith { channel ->
        val range = payload.channelRange(channel)
        stringResource(
            R.string.device_light_library_channel_range_format,
            channel.shortLabel(),
            range.first,
            range.last
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
private fun LibraryChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    labels: Map<DeviceLightLibraryChannel, String>,
    visuals: DeviceLightLibraryVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        channels.forEach { channel ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticGeometry.channelTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(DeviceLightAutomaticGeometry.channelDotSize)) {
                    drawCircle(color = channel.libraryColor(visuals.colors))
                }
                BasicText(
                    text = labels.getValue(channel),
                    style = visuals.typography.body,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun DeviceLightLibraryChannel.shortLabel(): String = stringResource(
    when (this) {
        DeviceLightLibraryChannel.RED -> R.string.device_light_plan_channel_red
        DeviceLightLibraryChannel.GREEN -> R.string.device_light_plan_channel_green
        DeviceLightLibraryChannel.BLUE -> R.string.device_light_plan_channel_blue
        DeviceLightLibraryChannel.WHITE -> R.string.device_light_plan_channel_white
    }
)

internal fun DeviceLightLibraryChannel.libraryColor(colors: AquaLightManualColors): Color =
    when (this) {
        DeviceLightLibraryChannel.RED -> colors.red
        DeviceLightLibraryChannel.GREEN -> colors.green
        DeviceLightLibraryChannel.BLUE -> colors.blue
        DeviceLightLibraryChannel.WHITE -> colors.white
    }

private val LIBRARY_CHANNEL_ORDER = listOf(
    DeviceLightLibraryChannel.WHITE,
    DeviceLightLibraryChannel.RED,
    DeviceLightLibraryChannel.GREEN,
    DeviceLightLibraryChannel.BLUE
)
