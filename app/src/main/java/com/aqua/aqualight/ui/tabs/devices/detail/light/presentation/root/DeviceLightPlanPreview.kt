@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec

internal data class DeviceLightPlanPreview(
    val currentHour: Int,
    val currentMinute: Int,
    val series: List<DeviceLightPlanSeries>
)

internal data class DeviceLightPlanSeries(
    val channel: DeviceLightPlanChannel,
    val points: List<DeviceLightPlanPoint>
)

internal data class DeviceLightPlanPoint(
    val hour: Float,
    val percent: Float
)

internal enum class DeviceLightPlanChannel {
    RED,
    GREEN,
    BLUE,
    WHITE
}

/** UI-only preview contract. Runtime plan data will replace this fixture in a later integration. */
internal fun deviceLightPlanPreview() = DeviceLightPlanPreview(
    currentHour = AquaLightPlanChartSpec.previewHour,
    currentMinute = AquaLightPlanChartSpec.previewMinute,
    series = listOf(
        previewSeries(DeviceLightPlanChannel.RED, peakPercent = 20f),
        previewSeries(DeviceLightPlanChannel.GREEN, peakPercent = 40f),
        previewSeries(DeviceLightPlanChannel.BLUE, peakPercent = 60f),
        previewSeries(DeviceLightPlanChannel.WHITE, peakPercent = 82f)
    )
)

private fun previewSeries(
    channel: DeviceLightPlanChannel,
    peakPercent: Float
) = DeviceLightPlanSeries(
    channel = channel,
    points = listOf(
        DeviceLightPlanPoint(hour = 0f, percent = 0f),
        DeviceLightPlanPoint(hour = 3.5f, percent = 0f),
        DeviceLightPlanPoint(hour = 5f, percent = peakPercent * 0.18f),
        DeviceLightPlanPoint(hour = 6f, percent = peakPercent * 0.42f),
        DeviceLightPlanPoint(hour = 7f, percent = peakPercent * 0.72f),
        DeviceLightPlanPoint(hour = 8.2f, percent = peakPercent),
        DeviceLightPlanPoint(hour = 16.4f, percent = peakPercent),
        DeviceLightPlanPoint(hour = 17.4f, percent = peakPercent * 0.66f),
        DeviceLightPlanPoint(hour = 18.3f, percent = peakPercent * 0.54f),
        DeviceLightPlanPoint(hour = 19.2f, percent = peakPercent * 0.34f),
        DeviceLightPlanPoint(hour = 20.3f, percent = peakPercent * 0.16f),
        DeviceLightPlanPoint(hour = 22f, percent = 0f),
        DeviceLightPlanPoint(hour = 24f, percent = 0f)
    )
)
