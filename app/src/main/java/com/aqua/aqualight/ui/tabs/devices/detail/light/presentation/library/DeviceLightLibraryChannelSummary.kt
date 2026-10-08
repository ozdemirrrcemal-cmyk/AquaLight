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
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightAutomaticGeometry

@Composable
internal fun ManualChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    descriptors: List<DeviceLightLibraryChannelDescriptor>,
    scene: DeviceLightLibraryScene,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = descriptors.filter { descriptor -> descriptor.channel in channels }
    val labels = visibleChannels.associateWith { descriptor ->
        stringResource(
            R.string.device_light_library_channel_summary_format,
            descriptor.displayName,
            scene.channels.getValue(descriptor.channel)
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
internal fun CustomChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    descriptors: List<DeviceLightLibraryChannelDescriptor>,
    payload: DeviceLightLibraryPayload.Custom,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = descriptors.filter { descriptor -> descriptor.channel in channels }
    val labels = visibleChannels.associateWith { descriptor ->
        val range = payload.channelRange(descriptor.channel)
        stringResource(
            R.string.device_light_library_channel_range_format,
            descriptor.displayName,
            range.first,
            range.last
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
private fun LibraryChannelSummary(
    channels: List<DeviceLightLibraryChannelDescriptor>,
    labels: Map<DeviceLightLibraryChannelDescriptor, String>,
    visuals: DeviceLightLibraryVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        channels.forEach { descriptor ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticGeometry.channelTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(DeviceLightAutomaticGeometry.channelDotSize)) {
                    drawCircle(color = Color(descriptor.displayColorRgb or OPAQUE_ALPHA))
                }
                BasicText(
                    text = labels.getValue(descriptor),
                    style = visuals.typography.body,
                    maxLines = 1
                )
            }
        }
    }
}

private const val OPAQUE_ALPHA = -0x1000000
