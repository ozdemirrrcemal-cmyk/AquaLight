package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot

internal data class DeviceLightPlanPresentation(
    val nowTimeMs: Long?,
    val channelScale: Int,
    val series: List<DeviceLightPlanSeries>
)

internal data class DeviceLightPlanSeries(
    val channel: DeviceLightChannelOutputSnapshot,
    val points: List<DeviceLightPlanPoint>
)

internal data class DeviceLightPlanPoint(
    val timeMs: Long,
    val level: Int
)

/** Changes firmware tuples into channel-oriented drawing data without deriving a new schedule. */
internal fun DeviceLightPlanSnapshot.toPresentation(
    channels: List<DeviceLightChannelOutputSnapshot>
): DeviceLightPlanPresentation? {
    if (points.any { point -> point.channelLevels.size != channels.size }) return null
    return DeviceLightPlanPresentation(
        nowTimeMs = nowTimeMs,
        channelScale = channelScale,
        series = channels.mapIndexed { channelIndex, channel ->
            DeviceLightPlanSeries(
                channel = channel,
                points = points.map { point ->
                    DeviceLightPlanPoint(
                        timeMs = point.timeMs,
                        level = point.channelLevels[channelIndex]
                    )
                }
            )
        }
    )
}

internal fun DeviceLightChannelOutputSnapshot.toComposeColor(): Color =
    Color(OPAQUE_COLOR_MASK or displayColorRgb)

@Composable
internal fun DeviceLightChannelOutputSnapshot.shortLabel(): String = when (key) {
    "red" -> stringResource(R.string.device_light_plan_channel_red)
    "green" -> stringResource(R.string.device_light_plan_channel_green)
    "blue" -> stringResource(R.string.device_light_plan_channel_blue)
    "white" -> stringResource(R.string.device_light_plan_channel_white)
    else -> displayName.take(1)
}

private const val OPAQUE_COLOR_MASK = -0x1000000
