package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose

/** Validated schedule-editor update applied atomically to the hoisted Dosing Plan draft. */
internal sealed interface DosingPlanScheduleUpdate {
    data class Single(val startTimeMs: Long) : DosingPlanScheduleUpdate
    data class Hourly(val startTimeMs: Long) : DosingPlanScheduleUpdate
    data class Custom(val periods: List<DeviceDosingCustomPeriod>) : DosingPlanScheduleUpdate
    data class Timer(val doses: List<DeviceDosingTimerDose>) : DosingPlanScheduleUpdate
}
