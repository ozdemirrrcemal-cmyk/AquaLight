package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly.DeviceDosingHourlyScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single.DeviceDosingSingleScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract

internal data class DeviceDosingScheduleResultHost(
    val fragment: Fragment,
    val slotId: String,
    val updateSingle: (Long) -> Unit,
    val updateHourly: (Long) -> Unit,
    val updateCustom: (List<DeviceDosingCustomPeriod>) -> Unit,
    val updateTimer: (List<DeviceDosingTimerDose>) -> Unit
)

internal fun bindDosingScheduleResults(
    host: DeviceDosingScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    bindSingleScheduleResult(host, lifecycleOwner)
    bindHourlyScheduleResult(host, lifecycleOwner)
    bindCustomScheduleResult(host, lifecycleOwner)
    bindTimerScheduleResult(host, lifecycleOwner)
}

private fun bindSingleScheduleResult(
    host: DeviceDosingScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingSingleScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val isExpectedResult =
            result.getString(DeviceDosingSingleScheduleContract.RESULT_KEY) ==
            DeviceDosingSingleScheduleContract.RESULT_SAVED &&
                result.getString(DeviceDosingSingleScheduleContract.RESULT_SLOT_ID) == host.slotId
        val startTimeMs = result.getLong(
            DeviceDosingSingleScheduleContract.RESULT_START_TIME_MS,
            INVALID_START_TIME_MS
        )
        if (isExpectedResult && DeviceDosingSingleScheduleContract.isValidStartTime(startTimeMs)) {
            host.updateSingle(DeviceDosingSingleScheduleContract.minuteAlignedStartTime(startTimeMs))
        }
    }
}

private fun bindHourlyScheduleResult(
    host: DeviceDosingScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingHourlyScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val isExpectedResult =
            result.getString(DeviceDosingHourlyScheduleContract.RESULT_KEY) ==
            DeviceDosingHourlyScheduleContract.RESULT_SAVED &&
                result.getString(DeviceDosingHourlyScheduleContract.RESULT_SLOT_ID) == host.slotId
        val startTimeMs = result.getLong(
            DeviceDosingHourlyScheduleContract.RESULT_START_TIME_MS,
            INVALID_START_TIME_MS
        )
        if (isExpectedResult && DeviceDosingHourlyScheduleContract.isValidStartTime(startTimeMs)) {
            host.updateHourly(DeviceDosingHourlyScheduleContract.minuteAlignedStartTime(startTimeMs))
        }
    }
}

private fun bindCustomScheduleResult(
    host: DeviceDosingScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingCustomScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val isExpectedResult =
            result.getString(DeviceDosingCustomScheduleContract.RESULT_KEY) ==
            DeviceDosingCustomScheduleContract.RESULT_SAVED &&
                result.getString(DeviceDosingCustomScheduleContract.RESULT_SLOT_ID) == host.slotId
        if (isExpectedResult) {
            DeviceDosingCustomScheduleContract.decodeDraft(
                result.getString(DeviceDosingCustomScheduleContract.RESULT_PERIODS_DRAFT).orEmpty()
            )?.takeIf(List<DeviceDosingCustomPeriod>::isNotEmpty)?.let(host.updateCustom)
        }
    }
}

private fun bindTimerScheduleResult(
    host: DeviceDosingScheduleResultHost,
    lifecycleOwner: LifecycleOwner
) {
    host.fragment.parentFragmentManager.setFragmentResultListener(
        DeviceDosingTimerScheduleContract.RESULT_REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        val isExpectedResult =
            result.getString(DeviceDosingTimerScheduleContract.RESULT_KEY) ==
            DeviceDosingTimerScheduleContract.RESULT_SAVED &&
                result.getString(DeviceDosingTimerScheduleContract.RESULT_SLOT_ID) == host.slotId
        if (isExpectedResult) {
            DeviceDosingTimerScheduleContract.decodeDraft(
                result.getString(DeviceDosingTimerScheduleContract.RESULT_DOSES_DRAFT).orEmpty()
            )?.takeIf(List<DeviceDosingTimerDose>::isNotEmpty)?.let(host.updateTimer)
        }
    }
}

private const val INVALID_START_TIME_MS = -1L
