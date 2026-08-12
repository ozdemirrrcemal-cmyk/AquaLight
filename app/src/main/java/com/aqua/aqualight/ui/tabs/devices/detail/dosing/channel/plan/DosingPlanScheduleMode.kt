package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.annotation.StringRes
import com.aqua.aqualight.R

internal enum class DosingPlanScheduleMode {
    SINGLE,
    HOURLY,
    CUSTOM,
    TIMER
}

internal data class DosingPlanScheduleOption(
    val mode: DosingPlanScheduleMode,
    @StringRes val labelRes: Int
)

internal val DOSING_PLAN_SCHEDULE_OPTIONS = listOf(
    DosingPlanScheduleOption(
        mode = DosingPlanScheduleMode.SINGLE,
        labelRes = R.string.device_dosing_detail_schedule_single
    ),
    DosingPlanScheduleOption(
        mode = DosingPlanScheduleMode.HOURLY,
        labelRes = R.string.device_dosing_detail_schedule_hourly
    ),
    DosingPlanScheduleOption(
        mode = DosingPlanScheduleMode.CUSTOM,
        labelRes = R.string.device_dosing_detail_schedule_custom
    ),
    DosingPlanScheduleOption(
        mode = DosingPlanScheduleMode.TIMER,
        labelRes = R.string.device_dosing_detail_schedule_timer
    )
)
