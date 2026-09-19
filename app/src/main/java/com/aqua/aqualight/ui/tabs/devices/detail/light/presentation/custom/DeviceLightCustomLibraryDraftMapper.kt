package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload

internal fun DeviceLightLibraryEntry.toCustomEditorDraft(
    editorChannels: List<DeviceLightCustomChannelId>,
    maxPoints: Int
): DeviceLightCustomDraft? {
    val custom = payload as? DeviceLightLibraryPayload.Custom
    val mappedChannels = channels.map(DeviceLightLibraryChannel::toCustomEditorChannel)
    val compatible = custom != null &&
        mappedChannels == editorChannels &&
        custom.points.size <= maxPoints
    return if (compatible) {
        runCatching {
            DeviceLightCustomDraft(
                weekdaysMask = requireNotNull(custom).weekdaysMask,
                points = custom.points.map { point ->
                    DeviceLightCustomPointUiState(
                        timeMs = point.timeMs,
                        channels = point.scene.channels.mapKeys { (channel, _) ->
                            channel.toCustomEditorChannel()
                        }
                    )
                }
            )
        }.getOrNull()
    } else {
        null
    }
}

private fun DeviceLightLibraryChannel.toCustomEditorChannel(): DeviceLightCustomChannelId =
    when (this) {
        DeviceLightLibraryChannel.RED -> DeviceLightCustomChannelId.RED
        DeviceLightLibraryChannel.GREEN -> DeviceLightCustomChannelId.GREEN
        DeviceLightLibraryChannel.BLUE -> DeviceLightCustomChannelId.BLUE
        DeviceLightLibraryChannel.WHITE -> DeviceLightCustomChannelId.WHITE
    }
