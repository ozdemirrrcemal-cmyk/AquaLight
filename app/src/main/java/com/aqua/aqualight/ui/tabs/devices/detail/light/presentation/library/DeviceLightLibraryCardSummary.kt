package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload

internal fun DeviceLightLibraryPayload.Custom.channelRange(
    channel: DeviceLightLibraryChannel
): IntRange {
    val values = points.map { point -> point.scene.channels.getValue(channel) }
    return values.min()..values.max()
}

/**
 * Counts intervals where the authored linear curve produces light on at least one channel.
 * The card displays the nearest whole hour to keep the Auto-style summary compact.
 */
internal fun DeviceLightLibraryPayload.Custom.activeLightDurationHours(): Int {
    val activeDurationMillis = points.zipWithNext().sumOf { (start, end) ->
        if (start.hasLight() || end.hasLight()) end.timeMs - start.timeMs else 0L
    }
    return ((activeDurationMillis + HALF_HOUR_MILLIS) / HOUR_MILLIS).toInt()
}

private fun DeviceLightLibraryCustomPoint.hasLight(): Boolean =
    scene.channels.values.any { percent -> percent > 0 }

private const val HOUR_MILLIS = 3_600_000L
private const val HALF_HOUR_MILLIS = HOUR_MILLIS / 2L
