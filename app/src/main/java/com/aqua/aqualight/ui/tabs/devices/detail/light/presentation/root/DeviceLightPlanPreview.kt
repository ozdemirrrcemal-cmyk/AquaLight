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
        previewSeries(DeviceLightPlanChannel.RED, peakPercent = RED_PEAK_PERCENT),
        previewSeries(DeviceLightPlanChannel.GREEN, peakPercent = GREEN_PEAK_PERCENT),
        previewSeries(DeviceLightPlanChannel.BLUE, peakPercent = BLUE_PEAK_PERCENT),
        previewSeries(DeviceLightPlanChannel.WHITE, peakPercent = WHITE_PEAK_PERCENT)
    )
)

private fun previewSeries(
    channel: DeviceLightPlanChannel,
    peakPercent: Float
) = DeviceLightPlanSeries(
    channel = channel,
    points = PREVIEW_PROFILE.map { point ->
        DeviceLightPlanPoint(
            hour = point.hour,
            percent = peakPercent * point.intensityFactor
        )
    }
)

private data class DeviceLightPlanProfilePoint(
    val hour: Float,
    val intensityFactor: Float
)

private val PREVIEW_PROFILE = listOf(
    DeviceLightPlanProfilePoint(hour = 0f, intensityFactor = 0f),
    DeviceLightPlanProfilePoint(hour = 3.5f, intensityFactor = 0f),
    DeviceLightPlanProfilePoint(hour = 5f, intensityFactor = 0.18f),
    DeviceLightPlanProfilePoint(hour = 6f, intensityFactor = 0.42f),
    DeviceLightPlanProfilePoint(hour = 7f, intensityFactor = 0.72f),
    DeviceLightPlanProfilePoint(hour = 8.2f, intensityFactor = 1f),
    DeviceLightPlanProfilePoint(hour = 16.4f, intensityFactor = 1f),
    DeviceLightPlanProfilePoint(hour = 17.4f, intensityFactor = 0.66f),
    DeviceLightPlanProfilePoint(hour = 18.3f, intensityFactor = 0.54f),
    DeviceLightPlanProfilePoint(hour = 19.2f, intensityFactor = 0.34f),
    DeviceLightPlanProfilePoint(hour = 20.3f, intensityFactor = 0.16f),
    DeviceLightPlanProfilePoint(hour = 22f, intensityFactor = 0f),
    DeviceLightPlanProfilePoint(hour = 24f, intensityFactor = 0f)
)

private const val RED_PEAK_PERCENT = 20f
private const val GREEN_PEAK_PERCENT = 40f
private const val BLUE_PEAK_PERCENT = 60f
private const val WHITE_PEAK_PERCENT = 82f
