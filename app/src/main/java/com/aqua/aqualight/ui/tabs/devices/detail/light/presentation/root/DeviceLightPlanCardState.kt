package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
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

@Composable
internal fun DeviceLightPlanCardData.toPlanCardState(
    enabled: Boolean
): DeviceLightPlanCardState {
    val presentation = plan?.toPresentation(channels)
    val currentTime = presentation?.nowTimeMs?.let { nowTimeMs ->
        stringResource(
            R.string.device_light_plan_time_format,
            (nowTimeMs / PLAN_MILLIS_PER_HOUR).toInt(),
            ((nowTimeMs % PLAN_MILLIS_PER_HOUR) / PLAN_MILLIS_PER_MINUTE).toInt()
        )
    }
    return DeviceLightPlanCardState(
        mode = mode,
        enabled = enabled,
        plan = plan,
        presentation = presentation,
        currentTime = currentTime
    )
}

@Composable
internal fun DeviceLightPlanCardState.planContentDescription(): String =
    if (manualMode) {
        stringResource(R.string.device_light_plan_manual_content_description)
    } else {
        stringResource(
            R.string.device_light_plan_content_description,
            currentTime ?: stringResource(R.string.device_light_plan_unavailable)
        )
    }

private fun DeviceLightPlanSnapshot.hasRenderableSchedule(): Boolean =
    available && reason == DeviceLightPlanReason.OK && hasScheduleToday

private fun DeviceLightPlanPresentation?.hasPoints(): Boolean =
    this?.series?.any { series -> series.points.isNotEmpty() } == true

private const val PLAN_MILLIS_PER_HOUR = 3_600_000L
private const val PLAN_MILLIS_PER_MINUTE = 60_000L
