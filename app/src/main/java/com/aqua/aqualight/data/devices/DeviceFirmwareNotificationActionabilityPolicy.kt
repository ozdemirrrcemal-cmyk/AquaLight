package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareVersionComparator

/** Pure current-state policy used immediately before opening a firmware notification route. */
internal object DeviceFirmwareNotificationActionabilityPolicy {

    fun availability(
        currentVersion: String,
        targetVersion: String
    ): Boolean {
        val current = currentVersion.trim()
        val target = targetVersion.trim()
        if (current.isBlank() || target.isBlank()) return false
        return runCatching {
            DeviceFirmwareVersionComparator.compare(target, current) > 0
        }.getOrDefault(false)
    }

    fun operation(
        state: DeviceOtaState,
        expectedTargetVersion: String
    ): Boolean = when (state) {
        is DeviceOtaState.Starting -> targetMatches(
            expectedTargetVersion,
            state.plan.targetVersion
        )
        is DeviceOtaState.InProgress -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Recovering -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.RestartRequired -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Succeeded -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Failed ->
            state.failure.stage == DeviceOtaFailureStage.UPDATE_EXECUTION
        is DeviceOtaState.Idle,
        is DeviceOtaState.Checking,
        is DeviceOtaState.Unsupported,
        is DeviceOtaState.UpToDate,
        is DeviceOtaState.UpdateAvailable -> false
    }

    private fun targetMatches(expected: String, actual: String): Boolean {
        val normalizedExpected = expected.trim()
        return normalizedExpected.isBlank() || normalizedExpected == actual.trim()
    }
}
