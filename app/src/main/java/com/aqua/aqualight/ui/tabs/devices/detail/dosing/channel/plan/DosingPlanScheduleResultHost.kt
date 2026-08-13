package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly.DeviceDosingHourlyScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single.DeviceDosingSingleScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract

internal data class DosingPlanScheduleResultHost(
    val fragment: Fragment,
    val slotId: String,
    val updateSchedule: (DosingPlanScheduleUpdate) -> Unit
)

internal fun bindDosingPlanScheduleResults(
    host: DosingPlanScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    bindSingleScheduleResult(host, lifecycleOwner)
    bindHourlyScheduleResult(host, lifecycleOwner)
    bindCustomScheduleResult(host, lifecycleOwner)
    bindTimerScheduleResult(host, lifecycleOwner)
}

private fun bindSingleScheduleResult(
    host: DosingPlanScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingSingleScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val expected = result.getString(DeviceDosingSingleScheduleContract.RESULT_KEY) ==
            DeviceDosingSingleScheduleContract.RESULT_SAVED &&
            result.getString(DeviceDosingSingleScheduleContract.RESULT_SLOT_ID) == host.slotId
        val startTimeMs = result.getLong(
            DeviceDosingSingleScheduleContract.RESULT_START_TIME_MS,
            INVALID_START_TIME_MS
        )
        if (expected && DeviceDosingSingleScheduleContract.isValidStartTime(startTimeMs)) {
            host.updateSchedule(
                DosingPlanScheduleUpdate.Single(
                    DeviceDosingSingleScheduleContract.minuteAlignedStartTime(startTimeMs)
                )
            )
        }
    }
}

private fun bindHourlyScheduleResult(
    host: DosingPlanScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingHourlyScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val expected = result.getString(DeviceDosingHourlyScheduleContract.RESULT_KEY) ==
            DeviceDosingHourlyScheduleContract.RESULT_SAVED &&
            result.getString(DeviceDosingHourlyScheduleContract.RESULT_SLOT_ID) == host.slotId
        val startTimeMs = result.getLong(
            DeviceDosingHourlyScheduleContract.RESULT_START_TIME_MS,
            INVALID_START_TIME_MS
        )
        if (expected && DeviceDosingHourlyScheduleContract.isValidStartTime(startTimeMs)) {
            host.updateSchedule(
                DosingPlanScheduleUpdate.Hourly(
                    DeviceDosingHourlyScheduleContract.minuteAlignedStartTime(startTimeMs)
                )
            )
        }
    }
}

private fun bindCustomScheduleResult(
    host: DosingPlanScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingCustomScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val expected = result.getString(DeviceDosingCustomScheduleContract.RESULT_KEY) ==
            DeviceDosingCustomScheduleContract.RESULT_SAVED &&
            result.getString(DeviceDosingCustomScheduleContract.RESULT_SLOT_ID) == host.slotId
        if (expected) {
            DeviceDosingCustomScheduleContract.decodeDraft(
                result.getString(DeviceDosingCustomScheduleContract.RESULT_PERIODS_DRAFT).orEmpty()
            )?.takeIf(List<DeviceDosingCustomPeriod>::isNotEmpty)?.let { periods ->
                host.updateSchedule(DosingPlanScheduleUpdate.Custom(periods))
            }
        }
    }
}

private fun bindTimerScheduleResult(
    host: DosingPlanScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingTimerScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val expected = result.getString(DeviceDosingTimerScheduleContract.RESULT_KEY) ==
            DeviceDosingTimerScheduleContract.RESULT_SAVED &&
            result.getString(DeviceDosingTimerScheduleContract.RESULT_SLOT_ID) == host.slotId
        if (expected) {
            DeviceDosingTimerScheduleContract.decodeDraft(
                result.getString(DeviceDosingTimerScheduleContract.RESULT_DOSES_DRAFT).orEmpty()
            )?.takeIf(List<DeviceDosingTimerDose>::isNotEmpty)?.let { doses ->
                host.updateSchedule(DosingPlanScheduleUpdate.Timer(doses))
            }
        }
    }
}

private const val INVALID_START_TIME_MS = -1L
