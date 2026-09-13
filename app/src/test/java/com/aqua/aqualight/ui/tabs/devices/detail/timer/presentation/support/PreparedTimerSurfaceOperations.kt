package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.support

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal object PreparedTimerSurfaceOperations : DeviceControlSurfacePreparationOperations {
    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = DeviceControlSurfacePreparationResult.Ready

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = family == OwnerDeviceFamily.TIMER
}

internal object UnavailableTimerControlOperations : DeviceTimerControlOperations {
    private val unavailable = DeviceTimerControlResult.Failed(
        DeviceTimerControlFailure.Unavailable
    )

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> =
        flowOf(unavailable)

    override fun currentControl(deviceUid: String): DeviceTimerControlResult = unavailable

    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult = unavailable

    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = unavailable

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = unavailable

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = unavailable

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = unavailable

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = unavailable
}
