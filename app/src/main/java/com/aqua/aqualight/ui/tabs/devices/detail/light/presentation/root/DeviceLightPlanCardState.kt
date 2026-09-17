package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot

internal data class DeviceLightPlanCardData(
    val mode: DeviceLightControlMode?,
    val plan: DeviceLightPlanSnapshot?,
    val channels: List<DeviceLightChannelOutputSnapshot>
)

internal data class DeviceLightPlanCardState(
    val mode: DeviceLightControlMode?,
    val enabled: Boolean,
    val plan: DeviceLightPlanSnapshot?,
    val presentation: DeviceLightPlanPresentation?,
    val currentTime: String?
) {
    val manualMode: Boolean
        get() = mode == DeviceLightControlMode.MANUAL

    val showProgramAction: Boolean
        get() = mode == DeviceLightControlMode.AUTOMATIC ||
            mode == DeviceLightControlMode.CUSTOM

    val hasRenderableSchedule: Boolean
        get() = plan?.hasRenderableSchedule() == true && presentation.hasPoints()
}

private fun DeviceLightPlanSnapshot.hasRenderableSchedule(): Boolean =
    available && reason == DeviceLightPlanReason.OK && hasScheduleToday

private fun DeviceLightPlanPresentation?.hasPoints(): Boolean =
    this?.series?.any { series -> series.points.isNotEmpty() } == true
